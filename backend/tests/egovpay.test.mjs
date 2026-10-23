import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { stripTypeScriptTypes } from "node:module";
import test from "node:test";

const uuid = "a23977c3-f2f2-4e5c-bf53-94bcff48e49c";
const templateUuid = "b23977c3-f2f2-4e5c-bf53-94bcff48e49c";
const apiToken = "test_fake-token";
const baseUrl = "https://platforms-api.e.gov.ph/egovpay";
const checkoutUrl = `https://egovpay-pgi-dev.oueg.info/${uuid}`;
const input = {
  amount: "1000",
  txnid: "TESTREF123",
  items: [{ name: "Item # 1", amount: "1000" }],
  settlementTemplateUuid: templateUuid,
  callbackUrl: "https://example.test/callback",
  redirectUrl: "https://example.test/return",
};
const enabledConfig = { integrationEnabled: true, demoOnly: true, baseUrl, apiToken };
let modulePromise;

function adapter() {
  modulePromise ??= (async () => {
    let source;
    try {
      source = await readFile(new URL("../lib/egovpay.ts", import.meta.url), "utf8");
    } catch (error) {
      if (error.code === "ENOENT") assert.fail("The eGovPay adapter is missing");
      throw error;
    }
    const javascript = stripTypeScriptTypes(source, { mode: "strip" });
    return import(`data:text/javascript;base64,${Buffer.from(javascript).toString("base64")}`);
  })();
  return modulePromise;
}

function transport(data, status = 200) {
  const calls = [];
  return {
    calls,
    fetch: async (url, init) => {
      calls.push({ url: String(url), init });
      return Response.json(data, { status });
    },
  };
}

function details(overrides = {}) {
  return { data: {
    uuid, refno: "0IOKUXQ5XX", txnid: "TESTREF123", environment_type: "TEST",
    items: [{ name: "Item # 1", amount: "1000" }], amount: "1000.0000",
    system_fee: "0.0000", channel_fee: "0.0000", partner_fee: "0.0000", currency: "PHP",
    payment_status: "INITIAL", payment_channel: null, payment_channel_uuid: null,
    payment_channel_branch: null, callback_url: input.callbackUrl, redirect_url: input.redirectUrl,
    paid_at: null, link_expires_at: "July 10, 2027 11:59:59 PM",
    expires_at: "July 10, 2027 11:59:59 PM", created_at: "July 10, 2026 05:51:01 PM",
    ...overrides,
  } };
}

function errorCode(code, status) {
  return (error) => {
    assert.equal(error.name, "EgovPayError");
    assert.equal(error.code, code);
    if (status !== undefined) assert.equal(error.status, status);
    assert.ok(!String(error).includes(apiToken));
    return true;
  };
}

test("payload signs a canonical decimal without changing its monetary value", async () => {
  const { buildPaymentPayload } = await adapter();
  for (const [amount, canonical] of [
    ["1000", "1000"],
    ["1000.00", "1000"],
    ["0.50", "0.5"],
    ["0001.2500", "1.25"],
  ]) {
    const payload = await buildPaymentPayload({ ...input, amount, items: [{ name: "Fee", amount }] }, apiToken);
    assert.equal(payload.amount, canonical);
    assert.equal(payload.items[0].amount, canonical);
    const {createHmac}=await import('node:crypto');
    assert.equal(payload.digest,createHmac('sha256',apiToken).update(canonical+'|TESTREF123').digest('hex'));
    assert.equal(payload.currency, "PHP");
    assert.equal(payload.settlement_template_uuid, templateUuid);
    assert.equal(payload.callback_url, input.callbackUrl);
    assert.equal(payload.redirect_url, input.redirectUrl);
    assert.ok(!JSON.stringify(payload).includes(apiToken));
  }
});

test("payload rejects malformed money and line items without changing their representation", async () => {
  const { buildPaymentPayload } = await adapter();
  for (const amount of [1000, "", " 1000", "1e3", "-1", "1,000", "NaN", "0.00"]) {
    await assert.rejects(() => buildPaymentPayload({ ...input, amount }, apiToken), errorCode("invalid_input"));
  }
  for (const items of [[], null, [{ name: "", amount: "1000" }], [{ name: "Fee", amount: 1000 }]]) {
    await assert.rejects(() => buildPaymentPayload({ ...input, items }, apiToken), errorCode("invalid_input"));
  }
});

test("payload rejects malformed identifiers and unsafe callback or redirect URLs", async () => {
  const { buildPaymentPayload } = await adapter();
  for (const patch of [
    { txnid: "" }, { txnid: "ref|forged" }, { settlementTemplateUuid: "bad-uuid" },
    { callbackUrl: "http://example.test/callback" }, { callbackUrl: "goserve://payment/callback" },
    { callbackUrl: `https://example.test/callback?token=${apiToken}` },
    { redirectUrl: "https://example.test/return?token=test%5Ffake%2Dtoken" },
    { redirectUrl: "javascript:alert(1)" }, { redirectUrl: "https://user:password@example.test/" },
  ]) {
    await assert.rejects(() => buildPaymentPayload({ ...input, ...patch }, apiToken), errorCode("invalid_input"));
  }
});

test("disabled and non-demo configurations make no provider request", async () => {
  const { createPayment, getTransaction } = await adapter();
  for (const [config, code] of [
    [{}, "integration_disabled"],
    [{ ...enabledConfig, integrationEnabled: undefined }, "integration_disabled"],
    [{ ...enabledConfig, integrationEnabled: false }, "integration_disabled"],
    [{ ...enabledConfig, demoOnly: false }, "demo_only_required"],
    [{ ...enabledConfig, demoOnly: undefined }, "demo_only_required"],
  ]) {
    const boundary = transport({}, 201);
    await assert.rejects(() => createPayment(config, input, boundary.fetch), errorCode(code));
    await assert.rejects(() => getTransaction(config, uuid, boundary.fetch), errorCode(code));
    assert.equal(boundary.calls.length, 0);
  }
});

test("invalid server configuration and unsafe header values make no request", async () => {
  const { createPayment } = await adapter();
  for (const patch of [
    { apiToken: undefined }, { apiToken: "" }, { apiToken: " whitespace " },
    { apiToken: "opaque\r\nX-Forged: true" }, { apiToken: "test_opaque\u0000value" },
    { baseUrl: "http://platforms-api.e.gov.ph/egovpay" },
    { baseUrl: "https://user:password@platforms-api.e.gov.ph/egovpay" },
    { baseUrl: `https://platforms-api.e.gov.ph/egovpay?token=${apiToken}` },
    { baseUrl: `${baseUrl}/${apiToken}` },
    { baseUrl: "https://platforms-api.e.gov.ph/egovpay#fragment" },
    { timeoutMs: 0 }, { timeoutMs: Infinity },
  ]) {
    const boundary = transport({}, 201);
    await assert.rejects(() => createPayment({ ...enabledConfig, ...patch }, input, boundary.fetch),
      errorCode("invalid_configuration"));
    assert.equal(boundary.calls.length, 0);
  }
});

test("an opaque hexadecimal token works only with explicit enabled demo configuration", async () => {
  const { createPayment, getTransaction } = await adapter();
  const opaqueToken = "0123456789abcdef".repeat(4);
  const config = { ...enabledConfig, apiToken: opaqueToken };
  const creation = transport({ data: { uuid, url: checkoutUrl } }, 201);
  assert.deepEqual(await createPayment(config, input, creation.fetch), { uuid, url: checkoutUrl });
  assert.equal(creation.calls[0].init.headers["X-eGovPay-Token"], 'test_'+opaqueToken);
  assert.ok(!creation.calls[0].url.includes(opaqueToken));
  assert.ok(!creation.calls[0].init.body.includes(opaqueToken));
  const lookup = transport(details());
  assert.equal((await getTransaction(config, uuid, lookup.fetch)).environment_type, "TEST");
  assert.equal(lookup.calls[0].init.headers["X-eGovPay-Token"], 'test_'+opaqueToken);

  const blocked = transport({ data: { uuid, url: checkoutUrl } }, 201);
  await assert.rejects(() => createPayment({ ...config, integrationEnabled: false }, input, blocked.fetch),
    errorCode("integration_disabled"));
  await assert.rejects(() => createPayment({ ...config, demoOnly: false }, input, blocked.fetch),
    errorCode("demo_only_required"));
  assert.equal(blocked.calls.length, 0);
});

test("create sends the documented POST with header-only credentials and returns hosted checkout", async () => {
  const { createPayment } = await adapter();
  const boundary = transport({ data: { uuid, url: checkoutUrl, channel: { refno: "0IOKUXQ5XX" } } }, 201);
  const receipt = await createPayment({ ...enabledConfig, baseUrl: `${baseUrl}/` }, input, boundary.fetch);
  assert.deepEqual(receipt, { uuid, url: checkoutUrl, refno: "0IOKUXQ5XX" });
  assert.equal(boundary.calls.length, 1);
  const { url, init } = boundary.calls[0];
  assert.equal(url, `${baseUrl}/api/v1/transaction`);
  assert.equal(init.method, "POST");
  assert.equal(init.headers["X-eGovPay-Token"], apiToken);
  assert.equal(init.headers["Content-Type"], "application/json; charset=utf-8");
  assert.equal(init.redirect, "error");
  assert.equal(init.cache, "no-store");
  assert.equal(init.credentials, "omit");
  assert.ok(!url.includes(apiToken));
  assert.ok(!init.body.includes(apiToken));
  const {createHmac}=await import('node:crypto');
  assert.equal(JSON.parse(init.body).digest,createHmac('sha256','fake-token').update('1000|TESTREF123').digest('hex'));
});

test("legacy checkout response may omit the channel reference", async () => {
  const { createPayment } = await adapter();
  const boundary = transport({ data: { uuid, url: checkoutUrl } }, 201);
  assert.deepEqual(await createPayment(enabledConfig, input, boundary.fetch), { uuid, url: checkoutUrl });
});

test("create rejects malformed receipts and insecure checkout links", async () => {
  const { createPayment } = await adapter();
  for (const data of [
    null, {}, { uuid: "bad", url: checkoutUrl }, { uuid, url: "http://example.test/checkout" },
    { uuid, url: "https://user:password@example.test/checkout" },
    { uuid, url: `${checkoutUrl}?api_token=${apiToken}` },
    { uuid, url: `${checkoutUrl}?api_token=test%5Ffake%2Dtoken` },
    { uuid, url: checkoutUrl, channel: { refno: 42 } },
  ]) {
    const boundary = transport({ data }, 201);
    await assert.rejects(() => createPayment(enabledConfig, input, boundary.fetch), errorCode("invalid_response"));
  }
  const nonTest = transport({ data: { uuid, url: checkoutUrl, environment_type: "LIVE" } }, 201);
  await assert.rejects(() => createPayment(enabledConfig, input, nonTest.fetch), errorCode("non_test_environment"));
});

test("get uses the documented UUID route and keeps INITIAL pending", async () => {
  const { getTransaction } = await adapter();
  const boundary = transport(details());
  const status = await getTransaction(enabledConfig, uuid, boundary.fetch);
  assert.deepEqual(status, { uuid, environment_type: "TEST", payment_status: "INITIAL", state: "pending",
    txnid: "TESTREF123", amount: "1000", currency: "PHP" });
  assert.equal(boundary.calls[0].url, `${baseUrl}/api/v1/transaction/${uuid}`);
  assert.equal(boundary.calls[0].init.method, "GET");
  assert.equal(boundary.calls[0].init.headers["X-eGovPay-Token"], apiToken);
  assert.equal(boundary.calls[0].init.body, undefined);
});

test("unverified statuses never become paid even when a paid timestamp is present", async () => {
  const { getTransaction } = await adapter();
  for (const payment_status of ["SUCCESS", "FAILED", "CANCELLED", "FUTURE_STATUS"]) {
    const boundary = transport(details({ payment_status, paid_at: "2026-10-14T00:00:00Z" }));
    const status = await getTransaction(enabledConfig, uuid, boundary.fetch);
    assert.equal(status.payment_status, payment_status);
    assert.equal(status.state, "unknown");
    assert.equal(status.environment_type, "TEST");
  }
});

test("PAID requires the paid_at field verified on the marked TEST checkout",async()=>{
  const {getTransaction}=await adapter();
  assert.equal((await getTransaction(enabledConfig,uuid,transport(details({payment_status:'PAID',paid_at:'October 23, 2026 06:38:24 AM'})).fetch)).state,'paid');
  for(const paid_at of [null,undefined,'',42])assert.equal((await getTransaction(enabledConfig,uuid,transport(details({payment_status:'PAID',paid_at})).fetch)).state,'unknown');
});

test("get rejects non-TEST or omitted environment markers", async () => {
  const { getTransaction } = await adapter();
  for (const environment_type of ["LIVE", "PRODUCTION", "test", null, undefined]) {
    const boundary = transport(details({ environment_type }));
    await assert.rejects(() => getTransaction(enabledConfig, uuid, boundary.fetch), errorCode("non_test_environment"));
  }
});

test("get validates the requested UUID and rejects mismatched or malformed status data", async () => {
  const { getTransaction } = await adapter();
  const noRequest = transport(details());
  await assert.rejects(() => getTransaction(enabledConfig, "../another-record", noRequest.fetch), errorCode("invalid_input"));
  assert.equal(noRequest.calls.length, 0);
  for (const data of [null, [], {}, { ...details().data, uuid: templateUuid },
    { ...details().data, payment_status: "" }, { ...details().data, payment_status: 42 }]) {
    const boundary = transport({ data });
    await assert.rejects(() => getTransaction(enabledConfig, uuid, boundary.fetch), errorCode("invalid_response"));
  }
});

test("provider errors are controlled and creation is never automatically retried", async () => {
  const { createPayment, getTransaction } = await adapter();
  for (const status of [301, 401, 422, 429, 500]) {
    const boundary = transport({ error: apiToken, message: `private diagnostic ${apiToken}` }, status);
    await assert.rejects(() => createPayment(enabledConfig, input, boundary.fetch), errorCode("provider_rejected", status));
    assert.equal(boundary.calls.length, 1);
  }
  await assert.rejects(() => getTransaction(enabledConfig, uuid, async () => { throw new Error(apiToken); }),
    errorCode("provider_unavailable"));
  await assert.rejects(() => getTransaction(enabledConfig, uuid,
    async () => new Response(apiToken, { status: 200 })), errorCode("invalid_response"));
});

test("timeouts bound fetch and response decoding and abort the request", async () => {
  const { getTransaction } = await adapter();
  for (const waitingForBody of [false, true]) {
    let signal;
    const boundary = async (_url, init) => {
      signal = init.signal;
      if (waitingForBody) return { status: 200, json: () => new Promise(() => {}) };
      return new Promise(() => {});
    };
    await assert.rejects(() => getTransaction({ ...enabledConfig, timeoutMs: 10 }, uuid, boundary),
      errorCode("request_timeout"));
    assert.equal(signal.aborted, true);
  }
});
