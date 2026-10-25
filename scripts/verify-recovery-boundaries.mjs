import { readFile, writeFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';

const project = 'https://uuvexkewiuxdbuelwogy.supabase.co';
const [reportPath] = process.argv.slice(2);
if (!reportPath) throw new Error('REPORT_PATH_REQUIRED');
const config = await readFile(new URL('../android/app/build/generated/source/buildConfig/debug/ph/edu/tip/goserve/BuildConfig.java', import.meta.url), 'utf8');
const configured = name => JSON.parse(config.match(new RegExp(`String ${name} = ("[^"\\r\\n]*");`))?.[1] ?? 'null');
if (configured('SUPABASE_URL') !== project) throw new Error('PROJECT_MISMATCH');
const key = configured('SUPABASE_PUBLISHABLE_KEY');
if (typeof key !== 'string' || !(key.startsWith('sb_publishable_')
  || key.startsWith('eyJ') && JSON.parse(Buffer.from(key.split('.')[1], 'base64url').toString()).role === 'anon')) {
  throw new Error('PUBLIC_KEY_REQUIRED');
}
const probe = randomUUID();
const proof = { action: 'verify', email: `recovery-boundary+${probe}@invalid.example`,
  code: 'AAAA-AAAA-AAAA-AAAA-AAAA-AAAA-AAAA-AAAA', code_challenge: 'A'.repeat(43), code_challenge_method: 's256' };
const cases = [
  ['control-requires-session', 'recovery-control', { action: 'status' }, 401, 'AUTH_REQUIRED'],
  ['file-requires-session', 'request-file', { action: 'read' }, 401, 'AUTH_REQUIRED'],
  ['download-requires-session', 'download-deliverable', { action: 'transfer' }, 401, 'AUTH_REQUIRED'],
  ['public-email-registration-retired', 'register-student', {}, 409, 'GOOGLE_REGISTRATION_REQUIRED'],
  ['email-recovery-retired', 'recovery-request', {}, 409, 'RECOVERY_METHOD_REQUIRED'],
  ['email-password-change-retired', 'password-change', {}, 409, 'RECOVERY_METHOD_REQUIRED'],
  ['totp-method-rejected', 'account-recovery', { ...proof, method: 'totp' }, 422, 'VALIDATION_FAILED'],
  ['unknown-grant-rejected', 'account-recovery', { action: 'reset', reset_grant: 'A'.repeat(43),
    code_verifier: 'A'.repeat(43), password: 'Synthetic-Password-1!', operation_id: randomUUID() }, 422, 'RECOVERY_GRANT_INVALID'],
  ...Array.from({ length: 6 }, (_, index) => [`unknown-proof-attempt-${index + 1}`, 'account-recovery', proof,
    index < 5 ? 422 : 429, index < 5 ? 'RECOVERY_PROOF_INVALID' : 'RATE_LIMITED']),
];
const results = [];
for (const [name, endpoint, body, status, code] of cases) {
  const response = await fetch(`${project}/functions/v1/${endpoint}`, { method: 'POST', redirect: 'error',
    signal: AbortSignal.timeout(20000), headers: { apikey: key, 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  const data = await response.json().catch(() => null);
  const safeShape = data?.ok === false && JSON.stringify(Object.keys(data).sort()) === '["error","ok"]'
    && JSON.stringify(Object.keys(data.error ?? {})) === '["code"]';
  results.push({ name, status: response.status, expectedStatus: status, expectedCode: code,
    safeErrorShape: safeShape, passed: response.status === status && data?.error?.code === code && safeShape });
}
const report = { checkedAt: new Date().toISOString(), project: 'uuvexkewiuxdbuelwogy', probe,
  passed: results.every(result => result.passed), accountCreated: false, passwordMutationPerformed: false, results };
await writeFile(reportPath, JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify({ passed: report.passed, checks: results.length, failures: results.filter(result => !result.passed) }));
process.exitCode = report.passed ? 0 : 1;
