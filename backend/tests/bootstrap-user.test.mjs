import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { stripTypeScriptTypes } from "node:module";
import { runInNewContext } from "node:vm";

const projectUrl = "https://uuvexkewiuxdbuelwogy.supabase.co";
const userId = "0fd1d7c9-1e67-43d7-96a6-a16d4f0b891e";
const fields = "id,email,display_name,role,office_id,staff_capability,is_demo,consent_accepted_at";
let state;
let handler;
let scenarios = 0;

function token(method = "oauth", overrides = {}) {
  const claims = {
    sub: userId,
    iss: `${projectUrl}/auth/v1`,
    aud: "authenticated",
    role: "authenticated",
    is_anonymous: false,
    exp: Math.floor(Date.now() / 1000) + 3600,
    amr: [{ method, timestamp: Math.floor(Date.now() / 1000) }],
    ...overrides,
  };
  return ["e30", Buffer.from(JSON.stringify(claims)).toString("base64url"), "fake-signature"].join(".");
}

function user() {
  return {
    id: userId,
    aud: "authenticated",
    role: "authenticated",
    email: "student@tip.edu.ph",
    email_confirmed_at: "2026-10-13T00:00:00Z",
    confirmed_at: "2026-10-13T00:00:00Z",
    created_at: "2026-10-13T00:00:00Z",
    updated_at: "2026-10-13T00:00:00Z",
    is_anonymous: false,
    app_metadata: { provider: "google", providers: ["google"] },
    user_metadata: { role: "teller", full_name: "Client-forged administrator", email_verified: true },
    identities: [{
      id: "google-identity",
      user_id: userId,
      identity_id: "google-identity",
      provider: "google",
      identity_data: { email: "student@tip.edu.ph", email_verified: true },
      created_at: "2026-10-13T00:00:00Z",
      last_sign_in_at: "2026-10-13T00:00:00Z",
      updated_at: "2026-10-13T00:00:00Z",
    }],
  };
}

function profile(role = "student") {
  return {
    id: userId,
    email: "student@tip.edu.ph",
    display_name: "Assigned name",
    role,
    office_id: role === "staff" ? "OFF-REG" : null,
    staff_capability: null,
    is_demo: role !== "student",
    consent_accepted_at: null,
  };
}

// Only the external Auth and database boundaries are replaced; the handler below is real source.
function createClient(url, key, options) {
  assert.equal(url, projectUrl);
  assert.equal(key, "fake-server-key");
  assert.equal(options.auth.persistSession, false);
  assert.equal(options.auth.autoRefreshToken, false);
  return {
    auth: {
      async getUser(receivedToken) {
        state.authCalls++;
        assert.equal(receivedToken, state.expectedToken);
        return { data: { user: state.user }, error: state.authError ?? null };
      },
    },
    from(table) {
      assert.equal(table, "profiles");
      state.dbCalls++;
      let inserting = false;
      const query = {
        select(columns) {
          assert.equal(columns, fields);
          return query;
        },
        eq(column, value) {
          assert.equal(column, "id");
          assert.equal(value, userId);
          return query;
        },
        async maybeSingle() {
          return {
            data: state.lookupQueue ? state.lookupQueue.shift() : state.profile ?? null,
            error: state.lookupError ?? null,
          };
        },
        insert(values) {
          inserting = true;
          state.inserted = values;
          return query;
        },
        async single() {
          assert.ok(inserting);
          return state.insertError
            ? { data: null, error: state.insertError }
            : { data: { ...state.inserted, consent_accepted_at: null }, error: null };
        },
      };
      return query;
    },
  };
}

const sourceUrl = new URL("../functions/bootstrap-user/index.ts", import.meta.url);
const source = stripTypeScriptTypes(await readFile(sourceUrl, "utf8"), { mode: "strip" })
  .replace(/^import\s+[^\n]+\n/, "");
runInNewContext(source, {
  createClient,
  Deno: {
    env: {
      get(name) {
        if (state.configMissing) return undefined;
        if (name === "SUPABASE_URL") return projectUrl;
        if (name === "SUPABASE_SERVICE_ROLE_KEY") return "fake-server-key";
        return undefined;
      },
    },
    serve(callback) { handler = callback; },
  },
  Response, Request, Date, TextDecoder, Uint8Array, atob, Set, JSON,
}, { filename: sourceUrl.pathname });

async function check(overrides = {}, expected = 200, method = "POST", access = token(), body = "{}") {
  state = { user: user(), profile: null, authCalls: 0, dbCalls: 0, expectedToken: access, ...overrides };
  const headers = access ? { Authorization: `Bearer ${access}`, "Content-Type": "application/json" } : {};
  const response = await handler(new Request(`${projectUrl}/functions/v1/bootstrap-user`, {
    method, headers, ...(method === "POST" ? { body } : {}),
  }));
  assert.equal(response.status, expected, `Scenario ${scenarios + 1}`);
  scenarios++;
  return response;
}

await check({}, 401, "POST", null);
assert.equal(state.authCalls, 0);
await check({}, 405, "GET");
await check({}, 204, "OPTIONS");
await check({ configMissing: true }, 503);
await check({ authError: { status: 401 }, user: null }, 401);
assert.equal(state.dbCalls, 0);
await check({ authError: { status: 0 }, user: null }, 503);
await check({ authError: { status: 500 }, user: null }, 503);
await check({ authError: { status: 429 }, user: null }, 503);
await check({}, 401, "POST", "malformed-token");
await check({}, 401, "POST", token("oauth", { sub: "wrong-user" }));
await check({}, 401, "POST", token("oauth", { iss: "https://other.supabase.co/auth/v1" }));
await check({}, 401, "POST", token("oauth", { role: "service_role" }));
await check({}, 401, "POST", token("oauth", { is_anonymous: true }));
await check({}, 401, "POST", token("oauth", { exp: 1 }));
await check({}, 403, "POST", token("magiclink"));
await check({}, 403, "POST", token("password"));

let response = await check({ profile: profile("staff") }, 200, "POST", token("password"));
assert.equal((await response.json()).role, "staff");
assert.equal(state.inserted, undefined);
await check({ profile: profile("teller") }, 200, "POST", token("password"));
await check({ profile: { ...profile(), is_demo: true } }, 200, "POST", token("password"));
await check({ profile: profile("staff") }, 403);
await check({ profile: profile("teller") }, 403);

response = await check({}, 200, "POST", token(),
  '{"role":"teller","display_name":"Forged name","id":"someone-else"}');
const student = await response.json();
assert.equal(student.id, userId);
assert.equal(student.role, "student");
assert.equal(student.display_name, "student");
assert.equal(student.office_id, null);
assert.equal(student.staff_capability, null);
assert.equal(student.is_demo, false);
assert.equal(student.consent_accepted_at, null);
assert.equal(response.headers.get("Cache-Control"), "no-store");
await check({ profile: profile() }, 200);

let denied = user();
denied.email_confirmed_at = null;
await check({ user: denied }, 403);
denied = user();
denied.identities[0].identity_data.email_verified = false;
await check({ user: denied }, 403);
denied = user();
denied.identities[0].provider = "github";
await check({ user: denied }, 403);
denied = user();
denied.identities[0].user_id = "someone-else";
await check({ user: denied }, 403);

for (const email of ["student@tip.edu.ph.evil.test", "student@sub.tip.edu.ph", "student@tip.edu.ph ",
  "student@@tip.edu.ph", "student@t\u0130p.edu.ph", "stu..dent@tip.edu.ph"]) {
  denied = user();
  denied.email = email;
  denied.identities[0].identity_data.email = email;
  await check({ user: denied }, 403);
  assert.equal(state.dbCalls, 0);
}

await check({ lookupError: { code: "database_error" } }, 503);
await check({ insertError: { code: "database_error" } }, 503);
await check({ insertError: { code: "23505" }, lookupQueue: [null, profile()] }, 200);
await check({ insertError: { code: "23505" }, lookupQueue: [null, profile("staff")] }, 403);
await check({ profile: { ...profile(), role: "administrator" } }, 403);
await check({ profile: { ...profile(), id: "someone-else" } }, 403);

console.log(`PASS: ${scenarios} GoServe bootstrap endpoint scenarios`);
