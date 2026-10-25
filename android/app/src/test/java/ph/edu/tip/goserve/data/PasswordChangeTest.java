package ph.edu.tip.goserve.data;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,shadows={AccountIntegrationTest.Store.class,PasswordChangeTest.Server.class},instrumentedPackages={"ph.edu.tip.goserve.data"})
public final class PasswordChangeTest {
    private AuthRepository auth;
    private AuthRepository.SessionLease lease;
    private static final String CURRENT="Current 1! password", NEXT="Next 2! password";
    @Before public void setup() throws Exception {
        new AccountIntegrationTest().setup(); Server.reset();
        auth=new AuthRepository(new SupabaseClient("https://example.supabase.co","fixture-key"),new SecureSessionStore(RuntimeEnvironment.getApplication()));
        auth.signInPassword("ana@example.com",CURRENT); lease=auth.sessionLease();
    }
    @Test public void legacyEmailPasswordChangeIsRetiredWithoutSendingSecrets() throws Exception {
        int calls=AccountIntegrationTest.Server.calls;
        IllegalStateException failure=assertThrows(IllegalStateException.class,()->auth.changePassword(lease,CURRENT,NEXT,NEXT,"123456"));
        assertTrue(failure.getMessage().contains("recovery code"));assertEquals(0,Server.updates);assertEquals(0,Server.reauthentications);
        assertNotNull(auth.profile());assertEquals(calls,AccountIntegrationTest.Server.calls);
    }
    @Test public void legacyEmailCodeRequestIsRetiredWithoutAnSmtpCall() throws Exception {
        assertThrows(IllegalStateException.class,()->auth.requestPasswordReauthentication(lease));
        assertEquals(0,Server.reauthentications);assertNotNull(auth.profile());
    }
    @Test public void replacedAccountCannotUseAnOldPasswordChangeLease() throws Exception {
        auth.invalidateSession();assertThrows(IllegalStateException.class,()->auth.changePassword(lease,CURRENT,NEXT,NEXT,"123456"));
        assertThrows(IllegalStateException.class,()->auth.requestPasswordReauthentication(lease));assertEquals(0,Server.updates);assertEquals(0,Server.reauthentications);
    }
    @Implements(SupabaseClient.class)
    public static final class Server {
        static int updates,logouts,reauthentications;
        static String updateToken,reauthenticationToken,reauthenticationMethod,responseId;
        static JSONObject body;
        static Exception failure;
        static boolean logoutFailure;
        static CountDownLatch entered,release;
        static void reset() { updates=logouts=reauthentications=0; updateToken=reauthenticationToken=reauthenticationMethod=null; responseId="student-a"; body=null; failure=null; logoutFailure=false; entered=release=null; }
        @Implementation protected void __constructor__(String url,String key) {}
        @Implementation protected String baseUrl() { return "https://example.supabase.co"; }
        @Implementation protected JSONObject object(String method,String endpoint,JSONObject value,String token) throws Exception {
            if("POST".equals(method) && "/functions/v1/password-change".equals(endpoint) && "change".equals(value.optString("action"))) {
                ++updates; body=value; updateToken=token;
                if(entered!=null)entered.countDown(); if(release!=null)assertTrue(release.await(3,TimeUnit.SECONDS));
                if(failure!=null)throw failure; return new JSONObject().put("id",responseId);
            }
            if(endpoint.startsWith("/auth/v1/logout")) { ++logouts; if(logoutFailure)throw new IOException("private cleanup failure"); return new JSONObject(); }
            if(endpoint.equals("/functions/v1/password-change") && "request_code".equals(value.optString("action"))) { ++reauthentications; reauthenticationToken=token; reauthenticationMethod=method; return new JSONObject().put("code_sent",true); }
            return new AccountIntegrationTest.Server().object(method,endpoint,value,token);
        }
    }
}
