package ph.edu.tip.goserve.data;

import android.content.Context;
import android.net.Uri;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import ph.edu.tip.goserve.domain.StudentProfile;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, shadows = {AccountIntegrationTest.Store.class, AccountIntegrationTest.Server.class},
        instrumentedPackages = {"ph.edu.tip.goserve.data"})
public final class AccountIntegrationTest {
    private AuthRepository auth;
    private SupabaseClient client;
    @Before public void setup() {
        Store.values.clear(); Server.lastBody = null; Server.calls = 0; Server.legacy = false;
        Server.entered = null; Server.release = null; Server.recoveryAmr = true;
        Server.resetStatus=0; Server.resetCalls=0; Server.resetEntered=null; Server.resetRelease=null; Server.refreshStatus=0;
        Server.termsAvailable=false; Server.complete=true; Server.accepted=true; Server.registrationFails=false; Server.recoveryRequired=false;
        Server.profileId="student-a"; Server.email="ana@example.com"; Server.role="student"; Server.registerCalls=0; Server.updateCalls=0;
        Server.department=null;
        client = new SupabaseClient("https://example.supabase.co", "fixture-key");
        auth = new AuthRepository(client, new SecureSessionStore(RuntimeEnvironment.getApplication()));
    }
    private StudentProfile student() { return new StudentProfile("Ana", "", true, "Cruz", "ID", "Program", "Year", "Mobile", "Gender"); }
    @Test public void trustedCcsDepartmentSurvivesBootstrapAndProfileUpdatesWithoutOfficeAuthority() throws Exception {
        Server.department="DEP-CCS";
        ph.edu.tip.goserve.domain.SessionProfile profile=auth.signInPassword("ana@example.com","verbatim");
        assertEquals("DEP-CCS",profile.departmentId); assertEquals(ph.edu.tip.goserve.domain.Role.STUDENT,profile.role);
        assertNull(profile.officeId); assertNull(profile.capability);
        profile=auth.updateAccount(1,null,student(),"b74907ce-d9c2-4d26-9abb-d24814b6042e");
        assertEquals("DEP-CCS",profile.departmentId); assertFalse(Server.lastBody.getJSONObject("p_student_profile").has("department_id"));
    }
    @Test public void earlierAccountContractWithNoDepartmentRemainsCompatible() throws Exception {
        assertNull(auth.signInPassword("ana@example.com","verbatim").departmentId);
    }
    @Test public void malformedDepartmentCannotBecomeAnAccountAttribute() throws Exception {
        Server.department=new JSONObject().put("role","staff");
        try { auth.signInPassword("ana@example.com","verbatim"); fail("Malformed affiliation must be rejected."); }
        catch(IllegalStateException expected) { assertNull(auth.profile()); }
    }
    @Test public void registrationRequiresTipGoogleWithoutCreatingAnEmailAccount() throws Exception {
        assertThrows(IllegalStateException.class,()->auth.registerStudent("ana@tip.edu.ph","A1! abc",student(),"v1","b74907ce-d9c2-4d26-9abb-d24814b6042e"));
        assertEquals(0,Server.registerCalls);assertNull(auth.profile());assertNull(Store.values.get("refresh_token"));
    }
    @Test public void missingApprovedTermsFailsTruthfully() throws Exception {
        try { auth.loadTerms(); fail("Missing approved content must block acceptance."); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("Terms")); }
    }
    @Test public void unmigratedBootstrapCannotFabricateReadiness() throws Exception {
        Server.legacy = true;
        try { auth.signInPassword("ana@example.com", "verbatim"); fail("Missing account contract must be rejected."); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("server")); }
    }
    @Test public void staleLeaseCannotReadAfterInvalidation() throws Exception {
        auth.signInPassword("ana@example.com", "verbatim");
        AccountGateway gateway = new AccountGateway(client, auth);
        auth.invalidateSession(); int calls = Server.calls;
        try { gateway.rpc("unread_counts", new JSONObject()); fail("Old account must not issue a request."); }
        catch (IllegalStateException expected) { assertEquals(calls, Server.calls); }
    }
    @Test public void lateRefreshCannotWriteCredentialsAfterInvalidation() throws Exception {
        Store.values.put("refresh_token", "fixture-refresh");
        Server.entered = new CountDownLatch(1); Server.release = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread request = new Thread(() -> { try { auth.restore(); } catch (Throwable error) { failure.set(error); } });
        request.start(); assertTrue(Server.entered.await(3, TimeUnit.SECONDS));
        auth.invalidateSession(); Server.release.countDown(); request.join(3000);
        assertFalse(request.isAlive()); assertNotNull(failure.get());
        assertNull(Store.values.get("refresh_token")); assertNull(auth.profile());
    }
    @Test public void emailRecoveryAndDeepLinksAreRetiredWithoutAnAuthExchange() throws Exception {
        int calls=Server.calls;
        assertThrows(IllegalStateException.class,()->auth.requestRecovery("ana@example.com"));
        assertThrows(IllegalStateException.class,()->auth.finishRecovery(Uri.parse("goserve://auth/recovery?code=old&flow=recovery")));
        assertThrows(IllegalStateException.class,()->auth.resetPassword("A1! abc","A1! abc"));
        assertEquals(calls,Server.calls);assertNull(Store.values.get("recovery_pending"));assertNull(auth.profile());
    }
    @Test public void invalidationCallbacksNeverRunUnderAnyAuthMonitorNesting() throws Exception {
        auth.setInvalidationListener(() -> assertFalse("External account cleanup cannot run while holding the auth monitor.",Thread.holdsLock(auth)));
        auth.invalidateSession();
        Uri google=auth.googleAuthorizationUrl(); auth.prepareGoogleCallback();
        auth.finishGoogle(Uri.parse(google.getQueryParameter("redirect_to")).buildUpon().appendQueryParameter("code","google-code").build());
        auth.signOut();
    }
    @Implements(value = SecureSessionStore.class, isInAndroidSdk = false)
    public static final class Store {
        static final Map<String,String> values = new ConcurrentHashMap<>();
        @Implementation protected void __constructor__(Context context) {}
        @Implementation protected void put(String name, String value) { values.put(name,value); }
        @Implementation protected String get(String name) { return values.get(name); }
        @Implementation protected void remove(String name) { values.remove(name); }
        @Implementation protected void clear() { values.clear(); }
    }
    @Implements(value = SupabaseClient.class, isInAndroidSdk = false)
    public static final class Server {
        static JSONObject lastBody; static String path; static int calls; static boolean legacy, recoveryAmr;
        static CountDownLatch entered, release;
        static volatile int resetStatus, resetCalls;
        static volatile int refreshStatus;
        static volatile CountDownLatch resetEntered,resetRelease;
        static boolean termsAvailable,complete,accepted,registrationFails,recoveryRequired;
        static String profileId,email,role;
        static Object department;
        static int registerCalls,updateCalls;
        @Implementation protected void __constructor__(String url,String key) {}
        @Implementation protected String baseUrl() { return "https://example.supabase.co"; }
        @Implementation protected JSONArray array(String path,String token) throws Exception {
            calls++;
            if (path.startsWith("/rest/v1/terms_documents?") && termsAvailable) return new JSONArray().put(new JSONObject()
                    .put("version","v1").put("content","Controlled approved Terms fixture").put("content_digest","fixture-digest").put("published_at","2026-10-18T00:00:00Z"));
            return new JSONArray();
        }
        @Implementation protected JSONObject object(String method,String endpoint,JSONObject body,String token) throws Exception {
            calls++; path = endpoint; lastBody = body;
            if ("GET".equals(method) && "/auth/v1/settings".equals(endpoint)) return new JSONObject().put("external",new JSONObject().put("google",true));
            if ("GET".equals(method) && "/auth/v1/user".equals(endpoint)) return new JSONObject().put("id",profileId);
            if ("/functions/v1/recovery-control".equals(endpoint) && "status".equals(body.optString("action"))) return recoveryStatus();
            if ("PUT".equals(method) && "/auth/v1/user".equals(endpoint)) {
                ++resetCalls; int status=resetStatus;
                if (resetEntered!=null) resetEntered.countDown();
                if (resetRelease!=null) assertTrue(resetRelease.await(3,TimeUnit.SECONDS));
                if (status!=0) throw new SupabaseClient.ApiException(status,"Controlled reset failure.");
                return new JSONObject().put("id","student-a");
            }
            if (endpoint.contains("register-student")) {
                registerCalls++;
                if (registrationFails) throw new SupabaseClient.ApiException(503,"Controlled registration failure.");
                return new JSONObject().put("account_created",true).put("email",body.getString("email")).put("next_action","sign_in");
            }
            if (endpoint.contains("recovery-request")) return new JSONObject().put("request_received",true);
            if (endpoint.contains("grant_type=refresh_token") && entered != null) { entered.countDown(); assertTrue(release.await(3,TimeUnit.SECONDS)); }
            if (endpoint.contains("grant_type=refresh_token") && refreshStatus!=0) throw new SupabaseClient.ApiException(refreshStatus,"Controlled terminal refresh rejection.");
            if (endpoint.contains("/token?")) {
                String claims = new JSONObject().put("sub",profileId).put("iss","https://example.supabase.co/auth/v1")
                        .put("aud","authenticated").put("role","authenticated").put("is_anonymous",false).put("exp",System.currentTimeMillis()/1000+3600)
                        .put("amr", new JSONArray().put(new JSONObject().put("method", recoveryAmr ? "recovery" : "password"))).toString();
                String access = "header." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(claims.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".signature";
                return new JSONObject().put("access_token",access).put("refresh_token","fixture-refresh").put("expires_in",3600);
            }
            if (endpoint.contains("/rpc/update_account")) { updateCalls++; if (!body.isNull("p_terms_version")) accepted=true; if (!body.isNull("p_student_profile")) complete=true; return account(); }
            if (endpoint.contains("bootstrap-user")) {
                return account();
            }
            return new JSONObject();
        }
        static JSONObject account() throws Exception {
                JSONObject row = new JSONObject().put("id",profileId).put("email",email).put("display_name","Ana Cruz").put("role",role)
                        .put("office_id",JSONObject.NULL).put("staff_capability",JSONObject.NULL).put("is_demo",true).put("consent_accepted_at",JSONObject.NULL);
                if (!legacy) row.put("version",1).put("profile_complete",complete).put("terms",new JSONObject().put("current_version","v1").put("accepted_version",accepted?"v1":JSONObject.NULL))
                        .put("next_action",!accepted?"accept_terms":!complete?"complete_profile":recoveryRequired?"setup_recovery":"home")
                        .put("recovery",recoveryStatus());
                if(department!=null)row.put("department_id",department);
                return row;
        }
        static JSONObject recoveryStatus() throws Exception {
            return new JSONObject().put("codes_configured",!recoveryRequired).put("codes_acknowledged",!recoveryRequired)
                    .put("remaining_codes",recoveryRequired?0:10).put("generation",recoveryRequired?JSONObject.NULL:"884cd499-a440-48ef-a6ef-b8ddfe1bc304")
                    .put("requires_setup",recoveryRequired);
        }
    }
}
