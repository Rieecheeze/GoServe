import { createClient, type User } from "npm:@supabase/supabase-js@2.117.3";

const profileFields = "id,email,display_name,role,office_id,staff_capability,is_demo,consent_accepted_at";
const allowedRoles = new Set(["student", "staff", "teller"]);
const responseHeaders = {
  "Content-Type": "application/json",
  "Cache-Control": "no-store",
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function respond(status: number, value: unknown): Response {
  return new Response(JSON.stringify(value), { status, headers: responseHeaders });
}

function verifiedClaims(token: string, user: User, url: string): Record<string, unknown> | null {
  // Only decode claims after getUser(token) verifies this exact token with Supabase Auth.
  try {
    const pieces = token.split(".");
    if (pieces.length !== 3) return null;
    const encoded = pieces[1].replace(/-/g, "+").replace(/_/g, "/");
    const padded = encoded.padEnd(Math.ceil(encoded.length / 4) * 4, "=");
    const bytes = Uint8Array.from(atob(padded), (character) => character.charCodeAt(0));
    const claims = JSON.parse(new TextDecoder().decode(bytes));
    const audiences = Array.isArray(claims.aud) ? claims.aud : [claims.aud];
    if (claims.sub !== user.id || claims.iss !== `${url}/auth/v1`
      || claims.role !== "authenticated" || !audiences.includes("authenticated")
      || claims.is_anonymous !== false || typeof claims.exp !== "number"
      || claims.exp <= Date.now() / 1000) return null;
    return claims;
  } catch {
    return null;
  }
}

function signInMethod(claims: Record<string, unknown>): "google" | "password" | null {
  if (!Array.isArray(claims.amr)) return null;
  const methods = claims.amr.map((entry) => entry?.method);
  if (methods.includes("oauth")) return "google";
  return methods.includes("password") ? "password" : null;
}

function verifiedGoogleEmail(user: User): string | null {
  if (!user.email || !user.email_confirmed_at || user.is_anonymous) return null;
  const email = user.email;
  const separator = email.indexOf("@");
  if (separator <= 0 || separator > 64 || separator !== email.lastIndexOf("@")) return null;
  const local = email.slice(0, separator);
  const domain = email.slice(separator + 1).toLowerCase();
  if (domain !== "tip.edu.ph"
    || !/^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*$/.test(local)) return null;
  const identity = user.identities?.find((item) => item.provider === "google"
    && item.user_id === user.id && item.identity_data?.email_verified === true
    && typeof item.identity_data?.email === "string"
    && item.identity_data.email.toLowerCase() === email.toLowerCase());
  return identity ? email : null;
}

Deno.serve(async (request: Request): Promise<Response> => {
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: responseHeaders });
  if (request.method !== "POST") return respond(405, { error: "Use POST for GoServe sign-in." });
  const authorization = request.headers.get("Authorization");
  const match = authorization?.match(/^Bearer ([^\s]+)$/i);
  if (!match) return respond(401, { error: "Sign in to GoServe to continue." });

  const url = Deno.env.get("SUPABASE_URL")?.replace(/\/+$/, "");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url?.startsWith("https://") || !serviceKey) {
    return respond(503, { error: "GoServe sign-in has not been configured." });
  }

  try {
    const admin = createClient(url, serviceKey, {
      auth: { autoRefreshToken: false, persistSession: false, detectSessionInUrl: false },
    });
    const token = match[1];
    const { data: auth, error: authError } = await admin.auth.getUser(token);
    if (authError || !auth.user) {
      const invalid = !authError || [400, 401, 403].includes(authError.status ?? 0);
      return invalid
        ? respond(401, { error: "Your sign-in expired. Sign in again." })
        : respond(503, { error: "GoServe could not verify sign-in. Retry shortly." });
    }

    const user = auth.user;
    const claims = verifiedClaims(token, user, url);
    if (!claims) return respond(401, { error: "This sign-in cannot be verified." });
    const method = signInMethod(claims);
    if (!method) return respond(403, { error: "Use the assigned password or a verified TIP Google account." });
    const googleEmail = method === "google" ? verifiedGoogleEmail(user) : null;
    if (method === "google" && !googleEmail) {
      return respond(403, { error: "Use a verified @tip.edu.ph Google account." });
    }

    const lookup = await admin.from("profiles").select(profileFields).eq("id", user.id).maybeSingle();
    if (lookup.error) return respond(503, { error: "GoServe could not load your profile. Retry shortly." });
    let profile = lookup.data;

    if (!profile && method === "password") {
      return respond(403, { error: "This account has not been assigned a GoServe profile." });
    }

    if (!profile) {
      const inserted = await admin.from("profiles").insert({
        id: user.id,
        email: googleEmail,
        display_name: googleEmail!.slice(0, googleEmail!.indexOf("@")),
        role: "student",
        office_id: null,
        staff_capability: null,
        is_demo: false,
      }).select(profileFields).single();
      if (inserted.error?.code === "23505") {
        const concurrent = await admin.from("profiles").select(profileFields).eq("id", user.id).maybeSingle();
        if (concurrent.error) return respond(503, { error: "GoServe could not load your profile. Retry shortly." });
        profile = concurrent.data;
      } else if (inserted.error) {
        return respond(503, { error: "GoServe could not create your student profile. Retry shortly." });
      } else {
        profile = inserted.data;
      }
    }

    if (!profile || profile.id !== user.id || !allowedRoles.has(profile.role)
      || typeof profile.is_demo !== "boolean") {
      return respond(403, { error: "This GoServe profile is not authorized." });
    }
    if (method === "google" && profile.role !== "student") {
      return respond(403, { error: "Office accounts must use their assigned email and password." });
    }
    const scoped = createClient(url, serviceKey, {
      auth: { autoRefreshToken: false, persistSession: false, detectSessionInUrl: false },
      global: { headers: { Authorization: `Bearer ${token}` } },
    });
    const account = await scoped.rpc("account_view");
    if (account.error || !account.data || account.data.id !== user.id) {
      return respond(503, { error: "GoServe could not load account readiness. Retry shortly." });
    }
    return respond(200, { ...profile, ...account.data });
  } catch {
    return respond(503, { error: "GoServe could not complete sign-in. Retry shortly." });
  }
});
