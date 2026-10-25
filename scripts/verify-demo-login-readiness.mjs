import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const project = 'https://uuvexkewiuxdbuelwogy.supabase.co';
const expected = new Map([
  ['registrar@demo.goserve.test', ['staff', 'OFF-REG', null]],
  ['accounting@demo.goserve.test', ['staff', 'OFF-ACC', null]],
  ['teller@demo.goserve.test', ['teller', 'OFF-CASH', null]],
  ['guidance@demo.goserve.test', ['staff', 'OFF-GCC', null]],
  ['secretary.ccs@demo.goserve.test', ['staff', 'DEP-CCS', 'DEP-CCS']],
  ['student.ccs@demo.goserve.test', ['student', null, 'DEP-CCS']],
]);

const [privatePath, reportPath] = process.argv.slice(2);
const checkRevocation = process.argv.includes('--check-revocation');
if (!privatePath || !reportPath) throw new Error('CREDENTIAL_AND_REPORT_PATH_REQUIRED');
const record = JSON.parse(await readFile(privatePath, 'utf8'));
if (record.project_ref !== 'uuvexkewiuxdbuelwogy' || record.accounts?.length !== 6
    || new Set(record.accounts.map(account => account.email)).size !== 6
    || record.accounts.some(account => !expected.has(account.email) || !account.id || !account.password)) {
  throw new Error('DEMO_ROSTER_MISMATCH');
}
const config = await readFile(fileURLToPath(new URL(
  '../android/app/build/generated/source/buildConfig/debug/ph/edu/tip/goserve/BuildConfig.java', import.meta.url)), 'utf8');
const configured = name => {
  const value = config.match(new RegExp(`String ${name} = ("[^"\\r\\n]*");`));
  if (!value) throw new Error('APP_CONFIGURATION_MISSING');
  return JSON.parse(value[1]);
};
if (configured('SUPABASE_URL') !== project) throw new Error('PROJECT_MISMATCH');
const key = configured('SUPABASE_PUBLISHABLE_KEY');
async function request(path, body, token) {
  const response = await fetch(project + path, {
    method: 'POST',
    headers: { apikey: key, 'Content-Type': 'application/json', Accept: 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    redirect: 'error', signal: AbortSignal.timeout(25000),
  });
  return { ok: response.ok, status: response.status, data: await response.json().catch(() => null) };
}

const results = [];
for (const account of record.accounts) {
  let token;
  const result = { email: account.email, authenticated: false, trustedProfile: false, sessionClosed: false };
  try {
    const login = await request('/auth/v1/token?grant_type=password',
      { email: account.email, password: account.password });
    if (!login.ok || typeof login.data?.access_token !== 'string'
        || login.data.user?.id !== account.id || login.data.user?.email !== account.email) {
      throw new Error('LOGIN_FAILED');
    }
    token = login.data.access_token;
    result.authenticated = true;
    const boot = await request('/functions/v1/bootstrap-user', {}, token);
    const [role, office, department] = expected.get(account.email);
    if (!boot.ok || boot.data?.id !== account.id || boot.data.email !== account.email
        || boot.data.role !== role || boot.data.office_id !== office
        || boot.data.department_id !== department || boot.data.is_demo !== true
        || typeof boot.data.profile_complete !== 'boolean'
        || (role !== 'student' && !boot.data.profile_complete)) {
      throw new Error('TRUSTED_PROFILE_FAILED');
    }
    if (boot.data.next_action !== 'accept_terms' || !boot.data.terms?.current_version
        || boot.data.terms.accepted_at != null) throw new Error('ONBOARDING_STATE_UNEXPECTED');
    result.trustedProfile = true;
    result.profileComplete = boot.data.profile_complete;
    result.nextAction = boot.data.next_action;
    result.termsPresented = true;
  } catch (error) {
    result.error = ['LOGIN_FAILED', 'TRUSTED_PROFILE_FAILED', 'ONBOARDING_STATE_UNEXPECTED'].includes(error.message)
      ? error.message : 'VERIFICATION_REQUEST_FAILED';
  } finally {
    if (token) {
      const closed = await request('/auth/v1/logout?scope=local', undefined, token).catch(() => null);
      result.sessionClosed = closed?.ok === true;
      if (result.sessionClosed && checkRevocation) {
        const bootstrap = await request('/functions/v1/bootstrap-user', {}, token).catch(() => null);
        const recovery = await request('/functions/v1/recovery-control', { action: 'status' }, token).catch(() => null);
        result.revokedBootstrapDenied = bootstrap?.status === 401;
        result.revokedRecoveryDenied = recovery?.status === 401;
      }
    }
  }
  results.push(result);
  console.log(JSON.stringify(result));
}
const passed = results.length === 6 && results.every(result =>
  result.authenticated && result.trustedProfile && result.sessionClosed && !result.error
  && (!checkRevocation || result.revokedBootstrapDenied && result.revokedRecoveryDenied));
await writeFile(reportPath, JSON.stringify({ checkedAt: new Date().toISOString(), project: 'uuvexkewiuxdbuelwogy',
  passed, revocationChecked: checkRevocation, termsAcceptedByVerifier: false, credentialsModified: false, results }, null, 2) + '\n');
process.exitCode = passed ? 0 : 1;
