package ph.edu.tip.goserve.data;

import android.net.Uri;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import ph.edu.tip.goserve.domain.Role;
import ph.edu.tip.goserve.domain.SessionProfile;

public final class AuthRepository {
    public static final String CALLBACK = "goserve://auth/callback";
    private static final long OAUTH_TIMEOUT_MILLIS = 10 * 60 * 1000L;
    private final SupabaseClient client;
    private final SecureSessionStore store;
    private String accessToken;
    private long expiresAt;
    private volatile SessionProfile profile;

    public AuthRepository(SupabaseClient client, SecureSessionStore store) { this.client = client; this.store = store; }
    public SessionProfile profile() { return profile; }
    public synchronized boolean hasSavedSession() throws Exception { return store.get("refresh_token") != null; }

    public synchronized Uri googleAuthorizationUrl() throws Exception {
        JSONObject settings = client.object("GET", "/auth/v1/settings", null, null);
        if (!settings.getJSONObject("external").optBoolean("google", false)) {
            throw new IllegalStateException("TIP Google sign-in needs provider setup in Supabase. Use your assigned account for now.");
        }
        String verifier = random();
        String nonce = random();
        JSONObject pending = new JSONObject().put("verifier", verifier).put("nonce", nonce)
                .put("started_at", System.currentTimeMillis());
        store.put("oauth_pending", pending.toString());
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String redirect = Uri.parse(CALLBACK).buildUpon().appendQueryParameter("nonce", nonce).build().toString();
        return Uri.parse(client.baseUrl() + "/auth/v1/authorize").buildUpon()
                .appendQueryParameter("provider", "google").appendQueryParameter("redirect_to", redirect)
                .appendQueryParameter("code_challenge", challenge).appendQueryParameter("code_challenge_method", "s256")
                .appendQueryParameter("scopes", "email profile").appendQueryParameter("hd", "tip.edu.ph").build();
    }

    public synchronized SessionProfile finishGoogle(Uri callback) throws Exception {
        if (!"goserve".equals(callback.getScheme()) || !"auth".equals(callback.getHost())
                || !"/callback".equals(callback.getPath())) throw new IllegalArgumentException("Invalid sign-in callback.");
        String saved = store.get("oauth_pending");
        if (saved == null) throw new IllegalArgumentException("Sign-in expired. Start again.");
        JSONObject pending = new JSONObject(saved);
        String nonce = callback.getQueryParameter("nonce");
        if (nonce == null || !MessageDigest.isEqual(nonce.getBytes(StandardCharsets.UTF_8),
                pending.getString("nonce").getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Sign-in callback did not match this login attempt.");
        }
        store.remove("oauth_pending");
        if (System.currentTimeMillis() - pending.getLong("started_at") > OAUTH_TIMEOUT_MILLIS) {
            throw new IllegalArgumentException("Sign-in expired. Start again.");
        }
        if (callback.getQueryParameter("error") != null) throw new IllegalArgumentException("Google sign-in was not completed.");
        String code = callback.getQueryParameter("code");
        if (code == null || code.isEmpty()) throw new IllegalArgumentException("Google did not return a sign-in code.");
        saveTokens(client.object("POST", "/auth/v1/token?grant_type=pkce", new JSONObject()
                .put("auth_code", code).put("code_verifier", pending.getString("verifier")), null));
        return bootstrap();
    }

    public synchronized SessionProfile signInPassword(String email, String password) throws Exception {
        if (email.trim().isEmpty() || password.isEmpty()) throw new IllegalArgumentException("Enter your assigned email and password.");
        store.remove("oauth_pending");
        try {
            saveTokens(client.object("POST", "/auth/v1/token?grant_type=password",
                    new JSONObject().put("email", email.trim()).put("password", password), null));
        } catch (SupabaseClient.ApiException error) {
            if (error.status == 400) throw new IllegalArgumentException("The email or password is incorrect.");
            throw error;
        }
        return bootstrap();
    }

    public synchronized SessionProfile restore() throws Exception { token(); return bootstrap(); }

    public synchronized SessionProfile resumeSession(Uri callback) throws Exception {
        String saved = store.get("oauth_pending");
        if (callback != null && saved != null) return finishGoogle(callback);
        if (saved != null) {
            JSONObject pending = new JSONObject(saved);
            if (System.currentTimeMillis() - pending.getLong("started_at") <= OAUTH_TIMEOUT_MILLIS) return null;
            store.remove("oauth_pending");
        }
        if (hasSavedSession()) return restore();
        if (callback != null) return finishGoogle(callback);
        return null;
    }

    private SessionProfile bootstrap() throws Exception {
        try {
            JSONObject row = client.object("POST", "/functions/v1/bootstrap-user", new JSONObject(), token());
            profile = parseProfile(row);
            return profile;
        } catch (Exception error) {
            profile = null;
            if (error instanceof org.json.JSONException || error instanceof IllegalArgumentException
                    || error instanceof SupabaseClient.ApiException
                    && (((SupabaseClient.ApiException) error).status == 401 || ((SupabaseClient.ApiException) error).status == 403)) {
                clearLocal();
            }
            throw error;
        }
    }

    public synchronized SessionProfile acceptConsent() throws Exception {
        if (profile == null) throw new IllegalStateException("Sign in before accepting privacy consent.");
        JSONObject row = client.object("POST", "/rest/v1/rpc/accept_privacy_consent", new JSONObject(), token());
        profile = parseProfile(row);
        return profile;
    }

    public synchronized String token() throws Exception {
        if (accessToken != null && expiresAt > System.currentTimeMillis() + 60000) return accessToken;
        String refresh = store.get("refresh_token");
        if (refresh == null) throw new IllegalStateException("Sign in to GoServe to continue.");
        try {
            saveTokens(client.object("POST", "/auth/v1/token?grant_type=refresh_token", new JSONObject().put("refresh_token", refresh), null));
        } catch (SupabaseClient.ApiException invalid) {
            if (invalid.status == 400 || invalid.status == 401 || invalid.status == 403) clearLocal();
            throw invalid;
        }
        return accessToken;
    }

    public synchronized void signOut() throws Exception {
        try {
            if (store.get("refresh_token") != null) client.object("POST", "/auth/v1/logout?scope=local", null, token());
        } finally { clearLocal(); }
    }

    private void saveTokens(JSONObject response) throws Exception {
        String access = response.getString("access_token");
        String refresh = response.getString("refresh_token");
        store.put("refresh_token", refresh);
        accessToken = access;
        expiresAt = System.currentTimeMillis() + response.getLong("expires_in") * 1000L;
    }

    private void clearLocal() { accessToken = null; expiresAt = 0; profile = null; store.clear(); }

    private SessionProfile parseProfile(JSONObject row) throws Exception {
        return new SessionProfile(row.getString("id"), row.getString("email"), row.optString("display_name", ""),
                Role.fromWire(row.getString("role")), nullable(row, "office_id"), nullable(row, "staff_capability"),
                row.getBoolean("is_demo"), !row.isNull("consent_accepted_at") && row.has("consent_accepted_at"));
    }

    private String nullable(JSONObject row, String key) { return row.isNull(key) ? null : row.optString(key, null); }
    private String random() { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
}
