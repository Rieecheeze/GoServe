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
    @Test public void passwordChangeSendsOnlyCurrentNewAndOptionalNonceThenSignsOut() throws Exception {
        auth.changePassword(lease,CURRENT,NEXT,NEXT,null);
        assertEquals(1,Server.updates); assertEquals(2,Server.body.length());
        assertEquals(CURRENT,Server.body.getString("current_password")); assertEquals(NEXT,Server.body.getString("password"));
        assertNotNull(Server.updateToken); assertEquals(1,Server.logouts);
        assertNull(auth.profile()); assertTrue(AccountIntegrationTest.Store.values.isEmpty());
    }
    @Test public void nonceIsSentOnlyWhenExplicitlyProvidedAndNoSecretIsSaved() throws Exception {
        auth.changePassword(lease,CURRENT,NEXT,NEXT,"123456");
        assertNotNull("A password update must be sent",Server.body);
        assertEquals(3,Server.body.length()); assertEquals("123456",Server.body.getString("nonce"));
        assertTrue(AccountIntegrationTest.Store.values.isEmpty());
    }
    @Test public void staleAccountCannotChangePasswordOrRequestAnEmail() throws Exception {
        auth.invalidateSession();
        assertThrows(IllegalStateException.class,()->auth.changePassword(lease,CURRENT,NEXT,NEXT,null));
        assertThrows(IllegalStateException.class,()->auth.requestPasswordReauthentication(lease));
        assertEquals(0,Server.updates); assertEquals(0,Server.reauthentications);
    }
    @Test public void invalidInputsCannotIssueARequest() throws Exception {
        for(String[] values:new String[][]{{"",NEXT,NEXT},{CURRENT,"weak","weak"},{CURRENT,NEXT,"mismatch"},{CURRENT,CURRENT,CURRENT}})
            assertThrows(IllegalArgumentException.class,()->auth.changePassword(lease,values[0],values[1],values[2],null));
        assertEquals(0,Server.updates); assertNotNull(auth.profile());
    }
    @Test public void rejectedCurrentPasswordPreservesSessionAndUsesSafeTypedError() throws Exception {
        Server.failure=new SupabaseClient.ApiException(400,"invalid_credentials","private server details");
        AuthRepository.PasswordChangeException failure=assertThrows(AuthRepository.PasswordChangeException.class,()->auth.changePassword(lease,CURRENT,NEXT,NEXT,null));
        assertEquals("CURRENT_PASSWORD_INVALID",failure.code); assertFalse(failure.getMessage().contains("private"));
        assertNotNull(auth.profile()); assertTrue(auth.hasSavedSession()); assertEquals(0,Server.logouts);
    }
    @Test public void requiredReauthenticationKeepsSessionForExplicitCodeRequest() throws Exception {
        Server.failure=new SupabaseClient.ApiException(422,"reauthentication_needed","private");
        AuthRepository.PasswordChangeException failure=assertThrows(AuthRepository.PasswordChangeException.class,()->auth.changePassword(lease,CURRENT,NEXT,NEXT,null));
        assertEquals("REAUTHENTICATION_REQUIRED",failure.code); assertEquals(0,Server.reauthentications); assertNotNull(auth.profile());
        Server.failure=null; auth.requestPasswordReauthentication(lease);
        assertEquals(1,Server.reauthentications); assertEquals("GET",Server.reauthenticationMethod); assertNotNull(Server.reauthenticationToken);
        assertNotNull(auth.profile()); assertEquals(1,Server.updates);
    }
    @Test public void transportFailureCannotClaimPasswordUnchangedOrRetainAuthority() throws Exception {
        Server.failure=new IOException("private lost-response detail");
        AuthRepository.PasswordChangeException failure=assertThrows(AuthRepository.PasswordChangeException.class,()->auth.changePassword(lease,CURRENT,NEXT,NEXT,null));
        assertEquals("OUTCOME_UNCERTAIN",failure.code); assertFalse(failure.getMessage().contains("private"));
        assertNull(auth.profile()); assertFalse(auth.hasSavedSession()); assertEquals(1,Server.updates);
    }
    @Test public void serverFailureCannotProveThatThePasswordWasNotChanged() throws Exception {
        Server.failure=new SupabaseClient.ApiException(503,"unexpected_failure","private upstream failure");
        AuthRepository.PasswordChangeException failure=assertThrows(AuthRepository.PasswordChangeException.class,()->auth.changePassword(lease,CURRENT,NEXT,NEXT,null));
        assertEquals("OUTCOME_UNCERTAIN",failure.code); assertNull(auth.profile()); assertFalse(auth.hasSavedSession());
        assertEquals(AuthRepository.PasswordChangeNotice.UNCERTAIN,auth.consumePasswordChangeNotice());
    }
    @Test public void expiredPasswordUpdateClearsOnlyTheCurrentAccount() throws Exception {
        Server.failure=new SupabaseClient.ApiException(401,"bad_jwt","private");
        assertThrows(Exception.class,()->auth.changePassword(lease,CURRENT,NEXT,NEXT,null));
        assertNull(auth.profile()); assertFalse(auth.hasSavedSession());
    }
    @Test public void confirmedPasswordChangeSurvivesRemoteLogoutFailure() throws Exception {
        Server.logoutFailure=true; auth.changePassword(lease,CURRENT,NEXT,NEXT,null);
        assertEquals(1,Server.updates); assertNull(auth.profile()); assertFalse(auth.hasSavedSession());
    }
    @Test public void oldPasswordResponseCannotClearOrPublishForReplacementAccount() throws Exception {
        Server.entered=new CountDownLatch(1); Server.release=new CountDownLatch(1);
        AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread worker=new Thread(()->{try{auth.changePassword(lease,CURRENT,NEXT,NEXT,null);}catch(Throwable error){failure.set(error);}});
        worker.start(); assertTrue(Server.entered.await(3,TimeUnit.SECONDS));
        auth.invalidateSession(); AccountIntegrationTest.Server.profileId="student-b"; AccountIntegrationTest.Server.email="second@example.test";
        auth.signInPassword("second@example.test","another password");
        Server.release.countDown(); worker.join(3000); assertFalse(worker.isAlive()); assertNotNull(failure.get());
        assertEquals("student-b",auth.profile().id); assertTrue(auth.hasSavedSession()); assertEquals(0,Server.logouts);
    }
    @Test public void unreadableUpdateResponseMustClearAuthorityAndReportAnUncertainOutcome() throws Exception {
        Server.failure=new org.json.JSONException("private malformed response");
        AuthRepository.PasswordChangeException failure=assertThrows(AuthRepository.PasswordChangeException.class,()->auth.changePassword(lease,CURRENT,NEXT,NEXT,null));
        assertEquals("OUTCOME_UNCERTAIN",failure.code); assertNull(auth.profile()); assertFalse(auth.hasSavedSession());
    }
    @Test public void responseForAnotherUserCannotConfirmAPasswordChange() throws Exception {
        Server.responseId="another-account";
        AuthRepository.PasswordChangeException failure=assertThrows(AuthRepository.PasswordChangeException.class,()->auth.changePassword(lease,CURRENT,NEXT,NEXT,null));
        assertEquals("OUTCOME_UNCERTAIN",failure.code); assertNull(auth.profile()); assertFalse(auth.hasSavedSession());
    }
    @Test public void confirmedChangeProvidesOneMemoryOnlySignOutNotice() throws Exception {
        auth.changePassword(lease,CURRENT,NEXT,NEXT,null);
        assertEquals(AuthRepository.PasswordChangeNotice.CHANGED,auth.consumePasswordChangeNotice());
        assertNull(auth.consumePasswordChangeNotice()); assertTrue(AccountIntegrationTest.Store.values.isEmpty());
    }
    @Test public void uncertainChangeCannotShowAConfirmedSuccessNotice() throws Exception {
        Server.failure=new IOException("private");
        assertThrows(AuthRepository.PasswordChangeException.class,()->auth.changePassword(lease,CURRENT,NEXT,NEXT,null));
        assertEquals(AuthRepository.PasswordChangeNotice.UNCERTAIN,auth.consumePasswordChangeNotice()); assertNull(auth.consumePasswordChangeNotice());
    }
    @Test public void replacementSignInClearsThePriorAccountsPasswordNotice() throws Exception {
        auth.changePassword(lease,CURRENT,NEXT,NEXT,null);
        AccountIntegrationTest.Server.profileId="student-b"; AccountIntegrationTest.Server.email="second@example.test";
        auth.signInPassword("second@example.test","different"); assertNull(auth.consumePasswordChangeNotice()); assertEquals("student-b",auth.profile().id);
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
            if("PUT".equals(method) && "/auth/v1/user".equals(endpoint)) {
                ++updates; body=value; updateToken=token;
                if(entered!=null)entered.countDown(); if(release!=null)assertTrue(release.await(3,TimeUnit.SECONDS));
                if(failure!=null)throw failure; return new JSONObject().put("id",responseId);
            }
            if(endpoint.startsWith("/auth/v1/logout")) { ++logouts; if(logoutFailure)throw new IOException("private cleanup failure"); return new JSONObject(); }
            if(endpoint.equals("/auth/v1/reauthenticate")) { ++reauthentications; reauthenticationToken=token; reauthenticationMethod=method; return new JSONObject(); }
            return new AccountIntegrationTest.Server().object(method,endpoint,value,token);
        }
    }
}
