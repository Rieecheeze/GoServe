export type EgovPayConfig = {
  integrationEnabled?: boolean;
  demoOnly?: boolean;
  baseUrl?: string;
  apiToken?: string;
  timeoutMs?: number;
};

export type PaymentInput = {
  amount: string;
  txnid: string;
  items: { name: string; amount: string }[];
  settlementTemplateUuid: string;
  callbackUrl: string;
  redirectUrl: string;
};

export type PaymentReceipt = { uuid: string; url: string; refno?: string };
export type TransactionStatus = {
  uuid: string;
  txnid: string;
  amount: string;
  currency: "PHP";
  paid_at?: string;
  environment_type: "TEST";
  payment_status: string;
  state: "pending" | "paid" | "unknown";
};
export type FetchTransport = (url: string, init: RequestInit) => Promise<Response>;

const messages = {
  integration_disabled: "eGovPay integration is disabled.",
  demo_only_required: "eGovPay requires an explicit demo-only configuration.",
  invalid_configuration: "eGovPay server configuration is invalid.",
  invalid_input: "eGovPay payment input is invalid.",
  provider_rejected: "eGovPay rejected the request.",
  provider_unavailable: "eGovPay could not be reached.",
  request_timeout: "eGovPay request timed out.",
  invalid_response: "eGovPay returned an invalid response.",
  non_test_environment: "eGovPay returned a transaction outside the test environment.",
};
type ErrorCode = keyof typeof messages;

export class EgovPayError extends Error {
  readonly code: ErrorCode;
  readonly status?: number;

  constructor(code: ErrorCode, status?: number) {
    super(messages[code]);
    this.name = "EgovPayError";
    this.code = code;
    this.status = status;
  }
}

function fail(code: ErrorCode, status?: number): never {
  throw new EgovPayError(code, status);
}

function object(value: unknown): Record<string, unknown> | undefined {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown> : undefined;
}

function isUuid(value: unknown): value is string {
  return typeof value === "string" && /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(value);
}

function isDecimal(value: unknown): value is string {
  return typeof value === "string" && /^[0-9]+(?:\.[0-9]+)?$/.test(value) && /[1-9]/.test(value);
}

function canonicalDecimal(value:string):string {
  const [whole,fraction='']=value.split('.');
  const tail=fraction.replace(/0+$/,'');
  return whole.replace(/^0+(?=\d)/,'')+(tail?'.'+tail:'');
}

function httpsUrl(value: unknown, code: ErrorCode, apiToken?: string): URL {
  if (typeof value !== "string" || value !== value.trim()) fail(code);
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    fail(code);
  }
  if (url.protocol !== "https:" || url.username || url.password) fail(code);
  let decoded = value;
  try {
    decoded = decodeURIComponent(value);
  } catch {
    fail(code);
  }
  if (apiToken && (value.includes(apiToken) || decoded.includes(apiToken))) fail(code);
  return url;
}

function transportConfig(config: EgovPayConfig) {
  if (config?.integrationEnabled !== true) fail("integration_disabled");
  if (config.demoOnly !== true) fail("demo_only_required");
  const token = config.apiToken;
  if (typeof token !== "string" || token.length === 0 || /[\s\u0000-\u001f\u007f]/.test(token)) {
    fail("invalid_configuration");
  }
  const signingToken=token.startsWith('test_')?token.slice(5):token;
  if(!signingToken)fail('invalid_configuration');
  const headerToken='test_'+signingToken;
  const base = httpsUrl(config.baseUrl, "invalid_configuration", signingToken);
  if (base.search || base.hash) fail("invalid_configuration");
  const timeoutMs = config.timeoutMs ?? 10_000;
  if (!Number.isFinite(timeoutMs) || timeoutMs <= 0 || timeoutMs > 60_000) fail("invalid_configuration");
  return { baseUrl: base.href.replace(/\/+$/, ""), apiToken: headerToken, signingToken, timeoutMs };
}

export async function buildPaymentPayload(input: PaymentInput, apiToken: string) {
  if (!object(input) || !isDecimal(input.amount) || !isUuid(input.settlementTemplateUuid)
    || typeof input.txnid !== "string" || !/^[^\s|]+$/.test(input.txnid)
    || !Array.isArray(input.items) || input.items.length === 0
    || typeof apiToken !== "string" || apiToken.length === 0) fail("invalid_input");
  const items = input.items.map((item) => {
    if (!object(item) || typeof item.name !== "string" || !item.name.trim() || !isDecimal(item.amount)) {
      fail("invalid_input");
    }
    return { name: item.name, amount: canonicalDecimal(item.amount) };
  });
  httpsUrl(input.callbackUrl, "invalid_input", apiToken);
  httpsUrl(input.redirectUrl, "invalid_input", apiToken);

  // Strip insignificant zeros as required by the tested gateway; do not convert money through a floating-point number.
  const amount=canonicalDecimal(input.amount);
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey("raw", encoder.encode(apiToken),
    { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const signature = await crypto.subtle.sign("HMAC", key, encoder.encode(`${amount}|${input.txnid}`));
  const digest = Array.from(new Uint8Array(signature), (byte) => byte.toString(16).padStart(2, "0")).join("");
  return {
    amount,
    items,
    currency: "PHP",
    settlement_template_uuid: input.settlementTemplateUuid,
    redirect_url: input.redirectUrl,
    callback_url: input.callbackUrl,
    txnid: input.txnid,
    digest,
  };
}

async function request(config: ReturnType<typeof transportConfig>, path: string,
  method: "POST" | "GET", expectedStatus: number, fetchImpl: FetchTransport, body?: string) {
  const controller = new AbortController();
  let timer: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<never>((_resolve, reject) => {
    timer = setTimeout(() => {
      controller.abort();
      reject(new EgovPayError("request_timeout"));
    }, config.timeoutMs);
  });
  const work = (async () => {
    const response = await fetchImpl(`${config.baseUrl}${path}`, {
      method,
      headers: {
        "X-eGovPay-Token": config.apiToken,
        "Content-Type": "application/json; charset=utf-8",
      },
      ...(body === undefined ? {} : { body }),
      cache: "no-store",
      credentials: "omit",
      redirect: "error",
      signal: controller.signal,
    });
    if (response.status !== expectedStatus) fail("provider_rejected", response.status);
    try {
      return await response.json() as unknown;
    } catch {
      fail("invalid_response");
    }
  })();
  try {
    return await Promise.race([work, timeout]);
  } catch (error) {
    if (controller.signal.aborted) fail("request_timeout");
    if (error instanceof EgovPayError) throw error;
    fail("provider_unavailable");
  } finally {
    clearTimeout(timer);
  }
}

export async function createPayment(config: EgovPayConfig, input: PaymentInput,
  fetchImpl: FetchTransport = fetch): Promise<PaymentReceipt> {
  const settings = transportConfig(config);
  // The live gateway uses test_ only on the header; HMAC uses the issued undecorated merchant key.
  const payload = await buildPaymentPayload(input, settings.signingToken);
  const response = object(await request(settings, "/api/v1/transaction", "POST", 201,
    fetchImpl, JSON.stringify(payload)));
  const data = object(response?.data);
  if (!data || !isUuid(data.uuid)) fail("invalid_response");
  if (data.environment_type !== undefined && data.environment_type !== "TEST") fail("non_test_environment");
  const checkout = httpsUrl(data.url, "invalid_response", settings.signingToken);
  if (checkout.href !== `https://egovpay-pgi-dev.oueg.info/${data.uuid}`) fail("invalid_response");
  const receipt: PaymentReceipt = { uuid: data.uuid, url: data.url as string };
  if (data.channel !== undefined) {
    const channel = object(data.channel);
    if (!channel) fail("invalid_response");
    if (channel.refno !== undefined) {
      if (typeof channel.refno !== "string" || !channel.refno.trim()) fail("invalid_response");
      receipt.refno = channel.refno;
    }
  }
  return receipt;
}

export async function getTransaction(config: EgovPayConfig, uuid: string,
  fetchImpl: FetchTransport = fetch): Promise<TransactionStatus> {
  const settings = transportConfig(config);
  if (!isUuid(uuid)) fail("invalid_input");
  const response = object(await request(settings, `/api/v1/transaction/${uuid}`, "GET", 200, fetchImpl));
  const data = object(response?.data);
  if (!data || !isUuid(data.uuid) || data.uuid.toLowerCase() !== uuid.toLowerCase()
    || typeof data.payment_status !== "string" || !data.payment_status.trim() || data.payment_status.length > 80
    || typeof data.txnid !== "string" || !data.txnid || data.txnid.length > 128
    || !isDecimal(data.amount) || data.amount.length > 80 || data.currency !== "PHP") fail("invalid_response");
  if (data.environment_type !== "TEST") fail("non_test_environment");
  return {
    uuid: data.uuid,
    txnid: data.txnid,
    amount: canonicalDecimal(data.amount),
    currency: "PHP",
    environment_type: "TEST",
    payment_status: data.payment_status,
    ...(typeof data.paid_at==='string'&&data.paid_at.trim()&&data.paid_at.length<=128?{paid_at:data.paid_at}:{}),
    // PAID with paid_at was verified by authenticated GET after the user approved the TEST checkout.
    state: data.payment_status === "INITIAL" ? "pending"
      :data.payment_status==='PAID'&&typeof data.paid_at==='string'&&data.paid_at.trim()&&data.paid_at.length<=128?'paid':'unknown',
  };
}
