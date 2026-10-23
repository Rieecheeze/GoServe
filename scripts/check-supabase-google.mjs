import { readFile } from 'node:fs/promises';
import { createHash, randomBytes } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const projectUrl = 'https://uuvexkewiuxdbuelwogy.supabase.co';
const callback = 'goserve://auth/callback';
const configPath = fileURLToPath(new URL('../android/app/build/generated/source/buildConfig/debug/ph/edu/tip/goserve/BuildConfig.java', import.meta.url));

async function check() {
  const config = await readFile(configPath, 'utf8');
  const configured = (name) => {
    const match = config.match(new RegExp(`String ${name} = ("[^"\\r\\n]*");`));
    if (!match) throw new Error('APP_CONFIG_MISSING');
    return JSON.parse(match[1]);
  };
  if (configured('SUPABASE_URL') !== projectUrl) throw new Error('APP_PROJECT_MISMATCH');
  const headers = { apikey: configured('SUPABASE_PUBLISHABLE_KEY'), Accept: 'application/json' };
  const settingsResponse = await fetch(`${projectUrl}/auth/v1/settings`, {
    headers, signal: AbortSignal.timeout(15000), redirect: 'manual',
  });
  const settings = await settingsResponse.json().catch(() => ({}));
  const restResponse = await fetch(`${projectUrl}/rest/v1/profiles?select=id&limit=0`, {
    headers, signal: AbortSignal.timeout(15000), redirect: 'manual',
  });
  const rest = await restResponse.json().catch(() => ({}));
  const profileReadDenied = restResponse.status === 401 && rest.code === '42501';
  const result = {
    projectUrl,
    configSource: 'generated debug BuildConfig; existing key withheld',
    authHttpStatus: settingsResponse.status,
    authReachable: settingsResponse.ok,
    databaseApiHttpStatus: restResponse.status,
    databaseApiReachable: restResponse.ok || profileReadDenied,
    anonymousProfileReadDenied: profileReadDenied,
    authenticatedDatabaseSessionVerified: false,
    googleEnabled: settingsResponse.ok && settings.external?.google === true,
    androidCallback: callback,
    redirectAllowlistVerified: false,
    endToEndGoogleSignInVerified: false,
  };
  if (settingsResponse.ok) {
    const authorization = new URL(`${projectUrl}/auth/v1/authorize`);
    const verifier = randomBytes(32).toString('base64url');
    const redirect = `${callback}?nonce=${randomBytes(32).toString('base64url')}`;
    const parameters = {
      provider: 'google',
      redirect_to: redirect,
      code_challenge: createHash('sha256').update(verifier).digest('base64url'),
      code_challenge_method: 's256', scopes: 'email profile', hd: 'tip.edu.ph',
    };
    for (const [name, value] of Object.entries(parameters)) authorization.searchParams.set(name, value);
    const response = await fetch(authorization, { headers, redirect: 'manual', signal: AbortSignal.timeout(15000) });
    result.authorizationHttpStatus = response.status;
    const location = response.headers.get('location');
    if (location) {
      const next = new URL(location);
      result.redirectsToGoogle = next.protocol === 'https:' && next.hostname === 'accounts.google.com';
      result.providerCallbackMatches = next.searchParams.get('redirect_uri') === `${projectUrl}/auth/v1/callback`;
      result.clientIdConfigured = Boolean(next.searchParams.get('client_id'));
      const state = next.searchParams.get('state');
      if (result.redirectsToGoogle && state) {
        const cancel = new URL(`${projectUrl}/auth/v1/callback`);
        cancel.searchParams.set('state', state);
        cancel.searchParams.set('error', 'access_denied');
        const cookie = response.headers.getSetCookie().map(value => value.split(';')[0]).join('; ');
        const returned = await fetch(cancel, {
          headers: { ...headers, ...(cookie ? { cookie } : {}) },
          redirect: 'manual', signal: AbortSignal.timeout(15000),
        });
        result.cancelCallbackHttpStatus = returned.status;
        const destination = returned.headers.get('location');
        if (destination) {
          const target = new URL(destination);
          result.cancelReturnBase = `${target.protocol}//${target.host}${target.pathname}`;
          result.noncePreserved = target.searchParams.get('nonce') === new URL(redirect).searchParams.get('nonce');
          result.redirectAllowlistVerified = result.cancelReturnBase === callback && result.noncePreserved;
        }
      }
    } else {
      const failure = await response.json().catch(() => ({}));
      result.authorizationErrorCode = typeof failure.error_code === 'string' ? failure.error_code : 'UNAVAILABLE';
    }
  }
  result.serverStartReady = result.googleEnabled && result.redirectsToGoogle === true
    && result.providerCallbackMatches === true && result.clientIdConfigured === true
    && result.redirectAllowlistVerified;
  console.log(JSON.stringify(result, null, 2));
  process.exitCode = result.serverStartReady ? 0 : 1;
}

check().catch((error) => {
  const known = ['APP_CONFIG_MISSING', 'APP_PROJECT_MISMATCH'];
  console.log(JSON.stringify({ serverStartReady: false, error: known.includes(error.message) ? error.message : 'CONFIG_OR_NETWORK_CHECK_FAILED' }));
  process.exitCode = 1;
});
