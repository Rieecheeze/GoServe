import { readFile, writeFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';

const project = 'https://uuvexkewiuxdbuelwogy.supabase.co';
const [reportPath] = process.argv.slice(2);
if (!reportPath) throw new Error('REPORT_PATH_REQUIRED');
const config = await readFile(new URL('../android/app/build/generated/source/buildConfig/debug/ph/edu/tip/goserve/BuildConfig.java', import.meta.url), 'utf8');
const configured = name => {
  const match = config.match(new RegExp(`String ${name} = ("[^"\\r\\n]*");`));
  if (!match) throw new Error('APP_CONFIGURATION_MISSING');
  return JSON.parse(match[1]);
};
if (configured('SUPABASE_URL') !== project) throw new Error('PROJECT_MISMATCH');
const key = configured('SUPABASE_PUBLISHABLE_KEY');
const probeId = randomUUID();
const variants = [
  ['baseline', {}],
  ['spoofed-forwarded', { 'X-Forwarded-For': '198.51.100.11' }],
  ['spoofed-real', { 'X-Real-IP': '203.0.113.13' }],
  ['spoofed-cloudflare', { 'CF-Connecting-IP': '203.0.113.14' }],
  ['spoofed-all', { 'X-Forwarded-For': '198.51.100.12, 203.0.113.12',
    'X-Real-IP': '203.0.113.13', 'CF-Connecting-IP': '203.0.113.14' }],
];
const requests = [];
for (let index = 0; index < variants.length; index++) {
  const [variant, spoofed] = variants[index];
  const requestTag = `${probeId}-${index}`;
  const response = await fetch(`${project}/functions/v1/account-recovery?ingress_probe=${requestTag}`, {
    method: 'POST', redirect: 'error', signal: AbortSignal.timeout(25000),
    headers: { apikey: key, 'Content-Type': 'application/json', ...spoofed },
    body: JSON.stringify({ action: 'verify', email: `ingress-probe+${requestTag}@invalid.example`,
      code: 'AAAA-AAAA-AAAA-AAAA-AAAA-AAAA-AAAA-AAAA',
      code_challenge: 'A'.repeat(43), code_challenge_method: 's256' }),
  });
  const body = await response.json().catch(() => null);
  const code = body?.error?.code;
  requests.push({ variant, requestTag, status: response.status,
    upstreamDenied: response.status === 403 && variant !== 'baseline',
    code: ['RECOVERY_PROOF_INVALID', 'CONFIGURATION_REQUIRED', 'TEMPORARILY_UNAVAILABLE', 'RATE_LIMITED'].includes(code)
      ? code : 'UNEXPECTED_RESPONSE' });
}
const report = { checkedAt: new Date().toISOString(), project: 'uuvexkewiuxdbuelwogy', probeId,
  trustedIngressVerified: false, note: 'Compare private rate-key digests with platform-observed ingress before enabling recovery.', requests };
await writeFile(reportPath, JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report));
process.exitCode = requests.every(item => item.status === 422 && item.code === 'RECOVERY_PROOF_INVALID'
  || item.upstreamDenied) ? 0 : 1;
