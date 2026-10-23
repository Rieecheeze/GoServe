import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFile, readdir } from 'node:fs/promises';
import test from 'node:test';
import { fixtureSql } from './sql-fixture.mjs';

let runtime, crypto;
try { runtime = await import('@electric-sql/pglite'); } catch { runtime = await import('../../.tools/sql-validation/node_modules/@electric-sql/pglite/dist/index.js'); }
try { crypto = await import('@electric-sql/pglite/contrib/pgcrypto'); } catch { crypto = await import('../../.tools/sql-validation/node_modules/@electric-sql/pglite/dist/contrib/pgcrypto.js'); }
const db = await runtime.PGlite.create({ extensions: { pgcrypto: crypto.pgcrypto } });
await db.exec(fixtureSql);
await db.exec('create role supabase_auth_admin; grant usage on schema auth to supabase_auth_admin; grant select on auth.users,auth.identities to supabase_auth_admin;');
const directory = new URL('../auth-restrictions/supabase/migrations/', import.meta.url);
const files = (await readdir(directory)).filter(name => name.endsWith('_tip_google_auth_hooks.sql'));
assert.equal(files.length, 1, 'Use the single CLI-generated hook migration');
await db.exec(await readFile(new URL(files[0], directory), 'utf8'));
const id = randomUUID();
const claims = { sub: id, email: 'student@tip.edu.ph', amr: [{ method: 'oauth', timestamp: 1 }], role: 'authenticated', custom: { retained: true } };
const event = (method = 'oauth', overrides = {}) => ({ user_id: id, authentication_method: method, claims, ...overrides });
const signup = (email, provider = 'google', extra = {}) => ({ user: { email, app_metadata: { provider, providers: [provider] }, ...extra } });
const query = async (sql, args = []) => (await db.query(sql, args)).rows;
async function call(name, input, role = 'supabase_auth_admin') {
  await db.exec('begin');
  try {
    await db.exec(`set local role ${role}`);
    const result = (await query(`select goserve_auth.${name}($1::jsonb) result`, [JSON.stringify(input)]))[0].result;
    await db.exec('commit');
    return result;
  } catch (error) { await db.exec('rollback'); throw error; }
}
async function identity({ email = 'student@tip.edu.ph', verified = true, identityEmail = email, confirmed = true, anonymous = false, provider = 'google' } = {}) {
  await query('delete from auth.identities where user_id=$1', [id]);
  await query('delete from auth.users where id=$1', [id]);
  await query('insert into auth.users(id,email,email_confirmed_at,is_anonymous) values($1,$2,case when $3 then now() end,$4)', [id, email, confirmed, anonymous]);
  if (provider) await query('insert into auth.identities(user_id,provider,identity_data) values($1,$2,$3)', [id, provider, JSON.stringify({ email: identityEmail, email_verified: verified })]);
}
const denied = result => {
  assert.equal(result.error?.http_code, 403, 'Supabase Auth must reject this Google identity');
  assert.equal(result.error.message, 'Use a verified @tip.edu.ph Google account.');
  assert.equal(result.claims, undefined);
};

test('new Google signup admits exact TIP domain and ASCII uppercase', async () => {
  for (const email of ['student@tip.edu.ph', 'Student@TIP.EDU.PH', 'first.last+tag@tip.edu.ph']) assert.deepEqual(await call('before_user_created', signup(email)), {});
});
test('new Google signup rejects other domains, subdomains, lookalikes and malformed addresses', async () => {
  for (const email of ['student@gmail.com', 'student@sub.tip.edu.ph', 'student@tip.edu.ph.evil.test', 'student@attacker-tip.edu.ph', 'student@@tip.edu.ph', 'student@tip.edu.ph ', ' student@tip.edu.ph', 'stu..dent@tip.edu.ph', 'student@tİp.edu.ph', '@tip.edu.ph', null]) denied(await call('before_user_created', signup(email)));
});
test('editable user metadata cannot override Google signup domain', async () => {
  denied(await call('before_user_created', signup('student@gmail.com', 'google', { user_metadata: { email: 'student@tip.edu.ph', provider: 'email', email_verified: true } })));
});
test('email/password signup is preserved and unsupported hook envelopes fail closed', async () => {
  assert.deepEqual(await call('before_user_created', signup('assigned@example.test', 'email')), {});
  for (const input of [{}, { user: {} }, signup('student@tip.edu.ph', 'other')]) denied(await call('before_user_created', input));
});
test('verified Google OAuth returns every original claim unchanged', async () => {
  await identity(); assert.deepEqual(await call('access_token', event()), { claims });
});
test('OAuth checks Auth-owned email instead of forged TIP claims', async () => {
  await identity({ email: 'student@gmail.com' }); denied(await call('access_token', event()));
});
test('OAuth requires a confirmed non-anonymous user and matching verified Google identity', async () => {
  for (const setup of [{ confirmed: false }, { anonymous: true }, { verified: false }, { verified: 'true' }, { identityEmail: 'other@tip.edu.ph' }, { provider: null }, { provider: 'other' }]) {
    await identity(setup); denied(await call('access_token', event()));
  }
});
test('OAuth accepts matching case-insensitive TIP identity without normalizing claims', async () => {
  await identity({ email: 'Student@TIP.EDU.PH', identityEmail: 'student@tip.edu.ph' });
  assert.deepEqual(await call('access_token', event()), { claims });
});
test('additional social identities cannot supply ambiguous OAuth provenance', async () => {
  await identity(); await query("insert into auth.identities(user_id,provider,identity_data) values($1,'other','{}')", [id]);
  denied(await call('access_token', event()));
});
test('Google refresh is checked again after the Auth email changes', async () => {
  await identity(); assert.deepEqual(await call('access_token', event('token_refresh')), { claims });
  await query("update auth.users set email='student@gmail.com' where id=$1", [id]);
  denied(await call('access_token', event('token_refresh')));
});
test('OAuth AMR still enforces the rule if the current event is a different method', async () => {
  await identity({ email: 'student@gmail.com' }); denied(await call('access_token', event('email_change')));
});
test('password login and refresh preserve provisioned non-TIP accounts', async () => {
  await identity({ email: 'assigned@example.test', provider: 'email' });
  const password = { ...claims, email: 'assigned@example.test', amr: [{ method: 'password' }] };
  for (const method of ['password', 'token_refresh', 'recovery']) assert.deepEqual(await call('access_token', event(method, { claims: password })), { claims: password });
});
test('missing subject, mismatched subject and missing refresh provenance fail closed', async () => {
  await identity();
  for (const input of [event('oauth', { user_id: 'invalid' }), event('oauth', { user_id: randomUUID() }), event('oauth', { claims: null }), event('token_refresh', { claims: { sub: id } })]) denied(await call('access_token', input));
});
test('hooks are invoker-only and cannot be executed through application roles', async () => {
  for (const name of ['before_user_created', 'access_token']) {
    const [definition] = await query("select p.prosecdef, p.proconfig from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='goserve_auth' and p.proname=$1", [name]);
    assert.equal(definition.prosecdef, false); assert.ok(definition.proconfig.includes('search_path=""'));
    for (const role of ['anon', 'authenticated']) await assert.rejects(() => call(name, event(), role), error => error.code === '42501');
  }
});
test('hook calls cannot mutate users or identities', async () => {
  await identity(); const before = await query('select to_jsonb(u) row from auth.users u order by id'); const identities = await query('select to_jsonb(i) row from auth.identities i order by id');
  await call('before_user_created', signup('new@tip.edu.ph')); await call('before_user_created', signup('other@gmail.com'));
  await call('access_token', event());
  assert.deepEqual(await query('select to_jsonb(u) row from auth.users u order by id'), before);
  assert.deepEqual(await query('select to_jsonb(i) row from auth.identities i order by id'), identities);
});
test.after(async () => { await db.close(); });
