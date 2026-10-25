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
    private String accessToken;
    private long expiresAt, generation;
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
    SupabaseClient recoveryClient(){return client;}
    SessionProfile signInAfterRecovery(String email,String password,long expectedGeneration)throws Exception {
        SessionProfile next=signInPassword(email,password,expectedGeneration);
        long request;
        synchronized(this){if(profile!=next)throw changed();request=generation;}
        if(next.email==null || !email.equalsIgnoreCase(next.email.trim())){invalidateIfCurrent(request,false);throw changed();}
        return next;
    }
    void discardRecoverySignIn(SessionProfile result){
        long request;synchronized(this){if(profile!=result)return;request=generation;}
        invalidateIfCurrent(request,false);
    }

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
    private long beginAuthenticationIfCurrent(long expected) {
        boolean advanced=false;
        try {synchronized(this){
            requireGeneration(expected);
            advanced=true;advanceLocked(false);store.remove("recovery_pending");return generation;
        }} finally {if(advanced)notifyInvalidation();}
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
            if (request==generation) { changed=true; advanceLocked(true); }
        } } finally { if (changed) notifyInvalidation(); }
    }
    private void clearMemory() { accessToken = null; expiresAt = 0; profile = null; passwordChangeNotice=null; }

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
        return signInPassword(email,password,null);
    }
    private SessionProfile signInPassword(String email,String password,Long expectedGeneration)throws Exception {
        if (email == null || email.trim().isEmpty() || password == null || password.isEmpty()) throw new IllegalArgumentException("Enter your email and password.");
        long request = expectedGeneration==null?beginAuthentication():beginAuthenticationIfCurrent(expectedGeneration);
        synchronized (this) { requireGeneration(request); store.remove("oauth_pending"); }
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
        String saved; long request; synchronized (this) { request=generation; saved = store.get("oauth_pending"); }
        if (callback != null && saved != null) return finishGoogle(callback);
        if (saved != null) {
            try { validAge(new JSONObject(saved)); return null; }
            catch (IllegalArgumentException expired) { synchronized (this) { if (request==generation && saved.equals(store.get("oauth_pending"))) store.remove("oauth_pending"); } }
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
        throw new IllegalStateException("New registration requires your verified TIP Google account. Continue with TIP Google.");
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
        synchronized(this){store.remove("recovery_pending");}
        throw new IllegalStateException("Email recovery is retired. Use your saved GoServe recovery code.");
    }
    public void finishRecovery(Uri callback) throws Exception {
        synchronized(this){store.remove("recovery_pending");}
        throw new IllegalStateException("Email recovery is retired. Use your saved GoServe recovery code.");
    }
    public void changePassword(SessionLease lease,String currentPassword,String password,String confirmation,String code) throws Exception {
        requireCurrent(lease);
        throw new IllegalStateException("Email password changes are retired. Use a saved GoServe recovery code to set your password.");
    }

    public void requestPasswordReauthentication(SessionLease lease) throws Exception {
        requireCurrent(lease);
        throw new IllegalStateException("Email password changes are retired. Use a saved GoServe recovery code to set your password.");
    }

    public void resetPassword(String password,String confirmation) throws Exception {
        synchronized(this){store.remove("recovery_pending");}
        throw new IllegalStateException("Email recovery is retired. Use your saved GoServe recovery code.");
    }

    private SessionProfile parseProfile(JSONObject row) throws Exception {
        if (!(row.opt("version") instanceof Number) || !(row.opt("profile_complete") instanceof Boolean)
                || !(row.opt("terms") instanceof JSONObject) || !(row.opt("next_action") instanceof String))
            throw new IllegalStateException("This server needs the GoServe account update. Please contact support or retry after setup.");
        JSONObject terms = row.getJSONObject("terms");
        if (!terms.has("current_version")) throw new IllegalStateException("This server needs the GoServe account update.");
        String action = row.getString("next_action");
        if (!"home".equals(action) && !"accept_terms".equals(action) && !"complete_profile".equals(action) && !"setup_recovery".equals(action)) throw new IllegalStateException("The server account state is unavailable.");
        Role role;
        try { role=Role.fromWire(row.getString("role")); } catch (IllegalArgumentException invalid) { throw new IllegalStateException("This account needs trusted server setup."); }
        boolean complete=row.getBoolean("profile_complete"); String accepted=nullable(terms,"accepted_version");
        if("home".equals(action) || "setup_recovery".equals(action)){
            ph.edu.tip.goserve.domain.RecoveryData.Status recovery=ph.edu.tip.goserve.domain.RecoveryData.status(row.getJSONObject("recovery"));
            if(!complete || accepted==null || "setup_recovery".equals(action) && !recovery.requiresSetup)throw new IllegalStateException("This account needs trusted recovery setup.");
            if(recovery.requiresSetup)action="setup_recovery";
        }
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
