package ph.edu.tip.goserve.data;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import ph.edu.tip.goserve.domain.AccountValidation;
import ph.edu.tip.goserve.domain.Role;
import ph.edu.tip.goserve.domain.SessionProfile;
import ph.edu.tip.goserve.domain.StudentProfile;
import ph.edu.tip.goserve.domain.TermsDocument;

public final class AuthRepository {
    public static final String CALLBACK = "goserve://auth/callback", RECOVERY_CALLBACK = "goserve://auth/recovery";
    private static final long ATTEMPT_TIMEOUT = 10 * 60 * 1000L;
    private final SupabaseClient client;
    private final SecureSessionStore store;
    private final Object refreshLock = new Object();
    private String accessToken, recoveryAccess;
    private long expiresAt, generation, recoveryExpiresAt, recoveryGeneration;
    private volatile SessionProfile profile;
    private PasswordChangeNotice passwordChangeNotice;
    private Runnable invalidationListener = () -> {};

    public static final class SessionLease {
        public final long generation;
        public final String accountId;
        SessionLease(long generation, String accountId) { this.generation = generation; this.accountId = accountId; }
    }
    public static final class RecoveryUnavailableException extends IllegalArgumentException {
        private RecoveryUnavailableException() { super("This password reset link cannot be used. Request a new link."); }
    }
    public static final class PasswordChangeException extends IllegalStateException {
        public final String code;
        public PasswordChangeException(String code,String message) { super(message); this.code=code; }
    }
    public enum PasswordChangeNotice { CHANGED, UNCERTAIN }
    public synchronized PasswordChangeNotice consumePasswordChangeNotice() {
        PasswordChangeNotice notice=passwordChangeNotice; passwordChangeNotice=null; return notice;
    }
    public AuthRepository(SupabaseClient client, SecureSessionStore store) { this.client = client; this.store = store; }
    public SessionProfile profile() { return profile; }
    public synchronized void setInvalidationListener(Runnable listener) { invalidationListener = listener; }
    public synchronized SessionLease sessionLease() {
        if (profile == null) throw new IllegalStateException("Sign in to GoServe to continue.");
        return new SessionLease(generation, profile.id);
    }
    public synchronized void requireCurrent(SessionLease lease) {
        if (lease == null || lease.generation != generation || profile == null || !profile.id.equals(lease.accountId)) throw changed();
    }
    private synchronized void requireGeneration(long expected) { if (expected != generation) throw changed(); }
    private static IllegalStateException changed() { return new IllegalStateException("This account session has changed. Sign in again."); }
    public String token(SessionLease lease) throws Exception {
        requireCurrent(lease); String token = tokenFor(lease.generation); requireCurrent(lease); return token;
    }
    public synchronized boolean hasSavedSession() throws Exception { return store.get("refresh_token") != null; }
    public synchronized long generation() { return generation; }
    public void invalidateSession() {
        try { synchronized (this) { advanceLocked(true); } } finally { notifyInvalidation(); }
    }
    /** Supersede ordinary work before queueing a browser callback, preserving its encrypted attempt. */
    public void supersedeSession() {
        try { synchronized (this) { advanceLocked(false); } } finally { notifyInvalidation(); }
    }
    public void prepareGoogleCallback() throws Exception {
        boolean changed=false;
        try { synchronized (this) { if (store.get("oauth_pending")!=null) { changed=true; advanceLocked(false); } } }
        finally { if (changed) notifyInvalidation(); }
    }
    private long beginAuthentication() {
        try { synchronized (this) { advanceLocked(false); store.remove("recovery_pending"); return generation; } }
        finally { notifyInvalidation(); }
    }
    private long advanceLocked(boolean all) {
        clearMemory(); ++generation;
        if (all) store.clear(); else store.remove("refresh_token");
        return generation;
    }
    private void notifyInvalidation() { Runnable listener; synchronized (this) { listener=invalidationListener; } listener.run(); }
    private void invalidateIfCurrent(long request,boolean recoveryOnly) {
        boolean changed=false;
        try { synchronized (this) {
            if (request==generation && (!recoveryOnly || recoveryGeneration==request)) { changed=true; advanceLocked(true); }
        } } finally { if (changed) notifyInvalidation(); }
    }
    private void clearMemory() { accessToken = null; expiresAt = 0; profile = null; recoveryAccess = null; recoveryExpiresAt = 0; recoveryGeneration = 0; passwordChangeNotice=null; }

    public Uri googleAuthorizationUrl() throws Exception {
        long request = beginAuthentication();
        JSONObject settings = client.object("GET", "/auth/v1/settings", null, null);
        requireGeneration(request);
        if (!settings.getJSONObject("external").optBoolean("google", false))
            throw new IllegalStateException("TIP Google sign-in needs provider setup on the server. Please retry later.");
        String verifier = random(), nonce = random();
        JSONObject pending = new JSONObject().put("verifier", verifier).put("nonce", nonce)
                .put("started_at", System.currentTimeMillis()).put("flow", "google");
        synchronized (this) { requireGeneration(request); store.put("oauth_pending", pending.toString()); }
        String redirect = Uri.parse(CALLBACK).buildUpon().appendQueryParameter("nonce", nonce).build().toString();
        return Uri.parse(client.baseUrl() + "/auth/v1/authorize").buildUpon()
                .appendQueryParameter("provider", "google").appendQueryParameter("redirect_to", redirect)
                .appendQueryParameter("code_challenge", challenge(verifier)).appendQueryParameter("code_challenge_method", "s256")
                .appendQueryParameter("scopes", "email profile").appendQueryParameter("hd", "tip.edu.ph").build();
    }
    public SessionProfile finishGoogle(Uri callback) throws Exception {
        JSONObject pending; long request;
        synchronized (this) {
            exactCallback(callback, CALLBACK);
            String saved = store.get("oauth_pending");
            if (saved == null) throw new IllegalArgumentException("Sign-in expired. Start again.");
            pending = new JSONObject(saved); matching(callback, "nonce", pending.getString("nonce"));
            store.remove("oauth_pending"); validAge(pending);
            if (!"google".equals(pending.optString("flow"))) throw unavailable();
            validCode(callback); request = advanceLocked(false); store.remove("recovery_pending");
        }
        notifyInvalidation();
        saveTokens(client.object("POST", "/auth/v1/token?grant_type=pkce", new JSONObject()
                .put("auth_code", callback.getQueryParameter("code")).put("code_verifier", pending.getString("verifier")), null), request);
        return bootstrap(request);
    }
    public SessionProfile signInPassword(String email, String password) throws Exception {
        if (email == null || email.trim().isEmpty() || password == null || password.isEmpty()) throw new IllegalArgumentException("Enter your email and password.");
        long request = beginAuthentication();
        synchronized (this) { store.remove("oauth_pending"); }
        try {
            saveTokens(client.object("POST", "/auth/v1/token?grant_type=password", new JSONObject().put("email", email.trim()).put("password", password), null), request);
        } catch (SupabaseClient.ApiException error) {
            requireGeneration(request);
            if (error.status == 400) throw new IllegalArgumentException("The email or password is incorrect.");
            throw error;
        }
        return bootstrap(request);
    }
    public SessionProfile restore() throws Exception { long request = generation(); tokenFor(request); return bootstrap(request); }
    public SessionProfile resumeSession(Uri callback) throws Exception {
        String saved; synchronized (this) { saved = store.get("oauth_pending"); }
        if (callback != null && saved != null) return finishGoogle(callback);
        if (saved != null) {
            try { validAge(new JSONObject(saved)); return null; }
            catch (IllegalArgumentException expired) { synchronized (this) { store.remove("oauth_pending"); } }
        }
        if (hasSavedSession()) return restore();
        if (callback != null) return finishGoogle(callback);
        return null;
    }
    private SessionProfile bootstrap(long request) throws Exception {
        try {
            SessionProfile next = parseProfile(client.object("POST", "/functions/v1/bootstrap-user", new JSONObject(), tokenFor(request)));
            synchronized (this) { requireGeneration(request); profile = next; }
            return next;
        } catch (Exception error) {
            if (error instanceof org.json.JSONException || error instanceof IllegalArgumentException
                        || error instanceof SupabaseClient.ApiException && (((SupabaseClient.ApiException) error).status == 401
                        || ((SupabaseClient.ApiException) error).status == 403)) invalidateIfCurrent(request,false);
            throw error;
        }
    }
    public TermsDocument loadTerms() throws Exception {
        JSONArray rows = client.array("/rest/v1/terms_documents?active=eq.true&select=version,content,content_digest,published_at&limit=1", null);
        if (rows.length() != 1) throw new IllegalStateException("Approved Terms need server setup. Please retry later.");
        JSONObject row = rows.getJSONObject(0); String version = row.getString("version"), content = row.getString("content");
        if (version.isEmpty() || content.trim().isEmpty()) throw new IllegalStateException("Approved Terms need server setup. Please retry later.");
        return new TermsDocument(version, content, row.getString("content_digest"), row.getString("published_at"));
    }
    public String registerStudent(String email, String password, StudentProfile student, String termsVersion, String operationKey) throws Exception {
        if (!AccountValidation.isValidEmail(email) || AccountValidation.passwordError(password,password) != null) throw new IllegalArgumentException("Check your email and password requirements.");
        if (!AccountValidation.profileErrors(student).isEmpty()) throw new IllegalArgumentException("Complete the highlighted student details.");
        if (termsVersion == null || termsVersion.isEmpty()) throw new IllegalArgumentException("Review and accept the approved Terms first.");
        long request = generation();
        JSONObject result = client.object("POST", "/functions/v1/register-student", new JSONObject().put("email",email).put("password",password)
                .put("profile",profileJson(student)).put("terms_version",termsVersion).put("idempotency_key",operationKey), null);
        requireGeneration(request);
        if (!result.getBoolean("account_created") || !"sign_in".equals(result.getString("next_action"))) throw new IllegalStateException("The server could not confirm account creation. Please retry.");
        return result.getString("email");
    }
    public SessionProfile updateAccount(long version, String termsVersion, StudentProfile student, String operationKey) throws Exception {
        SessionLease lease = sessionLease();
        if (student != null && !AccountValidation.profileErrors(student).isEmpty()) throw new IllegalArgumentException("Complete the highlighted student details.");
        JSONObject args = new JSONObject().put("p_expected_version",version).put("p_terms_version",value(termsVersion))
                .put("p_student_profile",student == null ? JSONObject.NULL : profileJson(student)).put("p_idempotency_key",operationKey);
        try {
            SessionProfile next = parseProfile(client.object("POST", "/rest/v1/rpc/update_account", args, token(lease)));
            synchronized (this) { requireCurrent(lease); if (!lease.accountId.equals(next.id)) throw changed(); profile = next; }
            return next;
        } catch (SupabaseClient.ApiException error) {
            if ("STALE_VERSION".equals(error.code)) {
                SessionProfile canonical = parseProfile(client.object("POST", "/rest/v1/rpc/account_view", new JSONObject(), token(lease)));
                synchronized (this) { requireCurrent(lease); if (lease.accountId.equals(canonical.id)) profile = canonical; }
            }
            throw error;
        }
    }
    /** Source compatibility only; this records versioned account Terms, never optional AI consent. */
    public SessionProfile acceptConsent() throws Exception {
        TermsDocument terms = loadTerms(); SessionProfile current = profile();
        if (current == null) throw new IllegalStateException("Sign in before accepting Terms.");
        return updateAccount(current.version,terms.version,null,java.util.UUID.randomUUID().toString());
    }
    public String token() throws Exception { return tokenFor(generation()); }
    private String tokenFor(long request) throws Exception {
        synchronized (refreshLock) {
            String refresh;
            synchronized (this) {
                requireGeneration(request);
                if (accessToken != null && expiresAt > System.currentTimeMillis() + 60000) return accessToken;
                refresh = store.get("refresh_token");
                if (refresh == null) throw new IllegalStateException("Sign in to GoServe to continue.");
            }
            try { saveTokens(client.object("POST", "/auth/v1/token?grant_type=refresh_token",new JSONObject().put("refresh_token",refresh),null),request); }
            catch (SupabaseClient.ApiException error) {
                if (error.status==400 || error.status==401 || error.status==403) invalidateIfCurrent(request,false);
                throw error;
            }
            synchronized (this) { requireGeneration(request); return accessToken; }
        }
    }
    public void signOut() throws Exception {
        String revoke=detachForSignOut();
        if (revoke != null) client.object("POST", "/auth/v1/logout?scope=local",null,revoke);
    }
    public void signOut(java.util.concurrent.Executor executor) {
        String revoke=detachForSignOut();
        if (revoke!=null) executor.execute(() -> { try { client.object("POST","/auth/v1/logout?scope=local",null,revoke); } catch (Exception ignored) {} });
    }
    private String detachForSignOut() {
        try { synchronized (this) { String revoke=accessToken; advanceLocked(true); return revoke; } }
        finally { notifyInvalidation(); }
    }
    private synchronized void saveTokens(JSONObject response,long request) throws Exception {
        requireGeneration(request);
        String access = response.getString("access_token"), refresh = response.getString("refresh_token"); long seconds = response.getLong("expires_in");
        if (access.isEmpty() || refresh.isEmpty() || seconds <= 0 || seconds > 31_536_000) throw new IllegalArgumentException("The sign-in response could not be verified.");
        store.put("refresh_token",refresh); accessToken = access; expiresAt = System.currentTimeMillis() + seconds * 1000L;
    }
    public void requestRecovery(String email) throws Exception {
        if (!AccountValidation.isValidEmail(email)) throw new IllegalArgumentException("Enter a valid email address.");
        long request = beginAuthentication(); String verifier = random(), state = random();
        JSONObject attempt = new JSONObject().put("verifier",verifier).put("state",state).put("flow","recovery")
                .put("redirect",RECOVERY_CALLBACK).put("started_at",System.currentTimeMillis()).put("generation",request);
        synchronized (this) { requireGeneration(request); store.remove("oauth_pending"); store.put("recovery_pending",attempt.toString()); }
        try {
            JSONObject response = client.object("POST", "/functions/v1/recovery-request",new JSONObject().put("email",email)
                    .put("code_challenge",challenge(verifier)).put("code_challenge_method","s256").put("state",state),null);
            requireGeneration(request);
            if (!response.getBoolean("request_received")) throw new IllegalStateException("Recovery request could not be confirmed. Please retry.");
        } catch (Exception error) { synchronized (this) { if (request == generation) store.remove("recovery_pending"); } throw error; }
    }
    public void finishRecovery(Uri callback) throws Exception {
        JSONObject attempt; long request;
        supersedeSession();
        synchronized (this) {
            String saved = store.get("recovery_pending");
            if (saved == null) throw unavailable();
            attempt = new JSONObject(saved); store.remove("recovery_pending");
            exactCallback(callback,RECOVERY_CALLBACK); matching(callback,"state",attempt.getString("state")); matching(callback,"flow","recovery");
            if (!"recovery".equals(attempt.getString("flow")) || !RECOVERY_CALLBACK.equals(attempt.getString("redirect"))) throw unavailable();
            validAge(attempt); validCode(callback);
            if (!attempt.getString("verifier").matches("[A-Za-z0-9_-]{43}")) throw unavailable();
            request = generation;
        }
        try {
            JSONObject tokens = client.object("POST", "/auth/v1/token?grant_type=pkce",new JSONObject().put("auth_code",callback.getQueryParameter("code"))
                    .put("code_verifier",attempt.getString("verifier")),null);
            requireGeneration(request); String access = tokens.getString("access_token");
            // Verify this exact bearer with Auth before inspecting its recovery AMR.
            JSONObject user = client.object("GET", "/auth/v1/user",null,access); requireGeneration(request); verifyRecovery(access,user);
            long seconds = tokens.getLong("expires_in"); if (seconds <= 0 || seconds > 31_536_000) throw unavailable();
            synchronized (this) { requireGeneration(request); recoveryAccess = access; recoveryExpiresAt = System.currentTimeMillis() + seconds * 1000L; recoveryGeneration = request; }
        } catch (Exception error) {
            synchronized (this) { if (request == generation) { recoveryAccess = null; recoveryExpiresAt = 0; } }
            throw error;
        }
    }
    public void changePassword(SessionLease lease,String currentPassword,String password,String confirmation,String nonce) throws Exception {
        requireCurrent(lease);
        if(currentPassword==null || currentPassword.isEmpty())throw new IllegalArgumentException("Enter your current password.");
        if(AccountValidation.passwordError(password,confirmation)!=null)throw new IllegalArgumentException("Check the password requirements and confirmation.");
        if(currentPassword.equals(password))throw new IllegalArgumentException("Choose a different password.");
        String access=token(lease);
        JSONObject body=new JSONObject().put("current_password",currentPassword).put("password",password);
        if(nonce!=null && !nonce.isEmpty())body.put("nonce",nonce);
        JSONObject user;
        try { user=client.object("PUT","/auth/v1/user",body,access); }
        catch(SupabaseClient.ApiException rejected) {
            requireCurrent(lease);
            if(rejected.status>=500) {
                finishPasswordChange(lease,PasswordChangeNotice.UNCERTAIN);
                throw new PasswordChangeException("OUTCOME_UNCERTAIN","The password change could not be confirmed. Sign in with your new password, or use Forgot password.");
            }
            if(rejected.status==401 || rejected.status==403)invalidateIfCurrent(lease.generation,false);
            throw passwordChangeError(rejected);
        } catch(java.io.IOException | org.json.JSONException uncertain) {
            finishPasswordChange(lease,PasswordChangeNotice.UNCERTAIN);
            throw new PasswordChangeException("OUTCOME_UNCERTAIN","The password change could not be confirmed. Sign in with your new password, or use Forgot password.");
        }
        requireCurrent(lease);
        if(user==null || !lease.accountId.equals(user.optString("id",null))) {
            finishPasswordChange(lease,PasswordChangeNotice.UNCERTAIN);
            throw new PasswordChangeException("OUTCOME_UNCERTAIN","The password change could not be confirmed. Sign in again or use Forgot password.");
        }
        try { client.object("POST","/auth/v1/logout?scope=local",null,access); }
        catch(Exception cleanupUnavailable) {}
        finishPasswordChange(lease,PasswordChangeNotice.CHANGED);
    }
    private void finishPasswordChange(SessionLease lease,PasswordChangeNotice notice) {
        synchronized(this) {
            requireCurrent(lease); advanceLocked(true); passwordChangeNotice=notice;
        }
        notifyInvalidation();
    }
    public void requestPasswordReauthentication(SessionLease lease) throws Exception {
        String access=token(lease);
        try { client.object("GET","/auth/v1/reauthenticate",null,access); }
        catch(SupabaseClient.ApiException rejected) {
            requireCurrent(lease);
            if(rejected.status==401 || rejected.status==403)invalidateIfCurrent(lease.generation,false);
            throw passwordChangeError(rejected);
        }
        requireCurrent(lease);
    }
    private static PasswordChangeException passwordChangeError(SupabaseClient.ApiException error) {
        if("invalid_credentials".equals(error.code) || "current_password_invalid".equals(error.code))
            return new PasswordChangeException("CURRENT_PASSWORD_INVALID","Your current password was not accepted.");
        if("reauthentication_needed".equals(error.code))
            return new PasswordChangeException("REAUTHENTICATION_REQUIRED","Request an email code to confirm this password change.");
        if("reauthentication_not_valid".equals(error.code))
            return new PasswordChangeException("CODE_INVALID","The code was not accepted. Request a new code and retry.");
        if("same_password".equals(error.code))return new PasswordChangeException("PASSWORD_REUSED","Choose a different password.");
        if("weak_password".equals(error.code))return new PasswordChangeException("PASSWORD_WEAK","Choose a stronger password.");
        if(error.status==401 || error.status==403)return new PasswordChangeException("SESSION_EXPIRED","Your session expired. Sign in again.");
        if(error.status==429)return new PasswordChangeException("RATE_LIMITED","Too many attempts. Wait a moment and retry.");
        return new PasswordChangeException("PASSWORD_CHANGE_FAILED","The password change was not accepted. Check the details and retry.");
    }
    public void resetPassword(String password,String confirmation) throws Exception {
        if (AccountValidation.passwordError(password,confirmation) != null) throw new IllegalArgumentException("Check the password requirements and confirmation.");
        String access; long request;
        synchronized (this) {
            request = generation;
            if (recoveryAccess == null || recoveryGeneration != generation || recoveryExpiresAt <= System.currentTimeMillis()) throw new RecoveryUnavailableException();
            access = recoveryAccess;
        }
        try { client.object("PUT", "/auth/v1/user",new JSONObject().put("password",password),access); }
        catch (SupabaseClient.ApiException error) {
            if (error.status==401 || error.status==403) {
                invalidateIfCurrent(request,true);
            }
            throw error;
        }
        requireGeneration(request);
        try { client.object("POST", "/auth/v1/logout?scope=local",null,access); }
        finally { invalidateIfCurrent(request,false); }
    }
    private void verifyRecovery(String access,JSONObject user) throws Exception {
        try {
            String[] parts = access.split("\\."); if (parts.length != 3) throw unavailable();
            JSONObject claims = new JSONObject(new String(Base64.getUrlDecoder().decode(parts[1]),StandardCharsets.UTF_8));
            Object audience = claims.get("aud"); boolean authenticated = "authenticated".equals(audience);
            if (audience instanceof JSONArray) for (int i=0;i<((JSONArray)audience).length();i++) authenticated |= "authenticated".equals(((JSONArray)audience).get(i));
            if (!user.getString("id").equals(claims.getString("sub")) || !(client.baseUrl()+"/auth/v1").equals(claims.getString("iss"))
                    || !"authenticated".equals(claims.getString("role")) || !authenticated || claims.getBoolean("is_anonymous")
                    || claims.getLong("exp") <= System.currentTimeMillis()/1000) throw unavailable();
            JSONArray methods = claims.getJSONArray("amr");
            for (int i=0;i<methods.length();i++) if ("recovery".equals(methods.getJSONObject(i).getString("method"))) return;
        } catch (Exception invalid) { throw unavailable(); }
        throw unavailable();
    }
    private SessionProfile parseProfile(JSONObject row) throws Exception {
        if (!(row.opt("version") instanceof Number) || !(row.opt("profile_complete") instanceof Boolean)
                || !(row.opt("terms") instanceof JSONObject) || !(row.opt("next_action") instanceof String))
            throw new IllegalStateException("This server needs the GoServe account update. Please contact support or retry after setup.");
        JSONObject terms = row.getJSONObject("terms");
        if (!terms.has("current_version")) throw new IllegalStateException("This server needs the GoServe account update.");
        String action = row.getString("next_action");
        if (!"home".equals(action) && !"accept_terms".equals(action) && !"complete_profile".equals(action)) throw new IllegalStateException("The server account state is unavailable.");
        Role role;
        try { role=Role.fromWire(row.getString("role")); } catch (IllegalArgumentException invalid) { throw new IllegalStateException("This account needs trusted server setup."); }
        boolean complete=row.getBoolean("profile_complete"); String accepted=nullable(terms,"accepted_version");
        if (role != Role.STUDENT && !complete || "home".equals(action) && (!complete || accepted == null)) throw new IllegalStateException("This account needs trusted server setup.");
        StudentProfile student=new StudentProfile(nullable(row,"first_name"),nullable(row,"middle_name"),row.optBoolean("no_middle_name",false),nullable(row,"last_name"),
                nullable(row,"student_id"),nullable(row,"course_program"),nullable(row,"year_level"),nullable(row,"mobile_number"),nullable(row,"gender"));
        String department=null;
        if(row.has("department_id") && !row.isNull("department_id")) {
            if(!(row.opt("department_id") instanceof String) || !row.getString("department_id").matches("^DEP-[A-Z0-9]+$"))
                throw new IllegalStateException("This account needs trusted department setup.");
            department=row.getString("department_id");
        }
        return new SessionProfile(row.getString("id"),row.getString("email"),row.optString("display_name",""),role,nullable(row,"office_id"),nullable(row,"staff_capability"),
                row.getBoolean("is_demo"),row.getLong("version"),complete,nullable(terms,"current_version"),accepted,action,student,department);
    }
    private JSONObject profileJson(StudentProfile p) throws Exception {
        return new JSONObject().put("first_name",value(p.firstName)).put("middle_name",value(p.middleName)).put("no_middle_name",p.noMiddleName)
                .put("last_name",value(p.lastName)).put("student_id",value(p.studentId)).put("course_program",value(p.courseProgram))
                .put("year_level",value(p.yearLevel)).put("mobile_number",value(p.mobileNumber)).put("gender",value(p.gender));
    }
    private static Object value(String value) { return value == null ? JSONObject.NULL : value; }
    private static String nullable(JSONObject row,String key) { return row.isNull(key) ? null : row.optString(key,null); }
    private static IllegalArgumentException unavailable() { return new IllegalArgumentException("This reset link is unavailable. Request a new link."); }
    private static void exactCallback(Uri callback,String expected) {
        Uri trusted=Uri.parse(expected);
        if (callback == null || !trusted.getScheme().equals(callback.getScheme()) || !trusted.getHost().equals(callback.getHost()) || !trusted.getPath().equals(callback.getPath())
                || callback.getPort()!=-1 || callback.getUserInfo()!=null || callback.getFragment()!=null || callback.getQueryParameter("access_token")!=null || callback.getQueryParameter("refresh_token")!=null) throw unavailable();
    }
    private static void matching(Uri callback,String name,String expected) {
        String actual=callback.getQueryParameter(name);
        if (actual==null || callback.getQueryParameters(name).size()!=1 || !MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8),expected.getBytes(StandardCharsets.UTF_8))) throw unavailable();
    }
    private static void validAge(JSONObject pending) throws Exception {
        long age=System.currentTimeMillis()-pending.getLong("started_at"); if (age<0 || age>ATTEMPT_TIMEOUT) throw unavailable();
    }
    private static void validCode(Uri callback) {
        String code=callback.getQueryParameter("code"); if (callback.getQueryParameter("error")!=null || code==null || code.isEmpty() || callback.getQueryParameters("code").size()!=1) throw unavailable();
    }
    private static String random() { byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String challenge(String verifier) throws Exception { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))); }
}
