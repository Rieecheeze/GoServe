package ph.edu.tip.goserve.data;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import ph.edu.tip.goserve.domain.SessionProfile;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,shadows={AccountIntegrationTest.Store.class,RecoveryTransportIntegrationTest.Server.class},instrumentedPackages="ph.edu.tip.goserve.data")
public final class RecoveryTransportIntegrationTest {
    private AuthRepository auth;
    @Before public void setup(){new AccountIntegrationTest().setup();Server.required=false;Server.last=null;Server.passwordLogin=null;Server.mutations=0;auth=new AuthRepository(new SupabaseClient("https://example.supabase.co","fixture"),new SecureSessionStore(RuntimeEnvironment.getApplication()));}
    @Test public void codeGenerationUsesOnlyTheCurrentBearerAndFixedAction()throws Exception {
        auth.signInPassword("ana@example.com","Existing 1!");RecoveryRepository repository=new RecoveryRepository(auth);
        assertEquals(10,repository.generateCodes().codes.size());assertEquals("/functions/v1/recovery-control",Server.path);assertNotNull(Server.token);assertEquals(1,Server.last.length());assertEquals("generate_codes",Server.last.getString("action"));
        repository.acknowledgeCodes(RecoveryRepositoryTest.ID);assertEquals(2,Server.last.length());assertFalse(Server.last.has("owner_id"));
    }
    @Test public void confirmedPublicResetUsesNormalPasswordSignInAndRebindsItsCanonicalLease()throws Exception {
        RecoveryRepository repository=new RecoveryRepository(auth);repository.verifyProof("ana@example.com",RecoveryRepositoryTest.CODE);assertNull(Server.token);assertEquals(5,Server.last.length());assertFalse(Server.last.has("method"));
        repository.resetPassword(RecoveryRepositoryTest.PASSWORD);SessionProfile profile=repository.completePasswordSignIn(RecoveryRepositoryTest.PASSWORD);
        assertSame(auth.profile(),profile);assertTrue(profile.ready());repository.requireCurrent();assertEquals(1,Server.mutations);
        assertEquals(2,Server.passwordLogin.length());assertEquals("ana@example.com",Server.passwordLogin.getString("email"));assertEquals(RecoveryRepositoryTest.PASSWORD,Server.passwordLogin.getString("password"));
        assertEquals(1,AccountIntegrationTest.Store.values.size());assertTrue(AccountIntegrationTest.Store.values.containsKey("refresh_token"));
    }
    @Test public void replacedAccountCannotUseAnEarlierGrant()throws Exception {
        RecoveryRepository repository=new RecoveryRepository(auth);repository.verifyProof("ana@example.com",RecoveryRepositoryTest.CODE);auth.invalidateSession();
        assertThrows(IllegalStateException.class,()->repository.resetPassword(RecoveryRepositoryTest.PASSWORD));assertEquals(0,Server.mutations);
    }
    @Test public void resetAloneCannotAcceptAnotherAccountsBootstrapIdentity()throws Exception {
        RecoveryRepository repository=new RecoveryRepository(auth);repository.verifyProof("ana@example.com",RecoveryRepositoryTest.CODE);repository.resetPassword(RecoveryRepositoryTest.PASSWORD);AccountIntegrationTest.Server.email="another@tip.edu.ph";
        assertThrows(IllegalStateException.class,()->repository.completePasswordSignIn(RecoveryRepositoryTest.PASSWORD));assertNull(auth.profile());assertFalse(auth.hasSavedSession());
    }
    @Test public void setupRecoveryIsRecognizedAndPreventsHomeEvenBeforeServerGateActivation()throws Exception {
        Server.required=true;SessionProfile profile=auth.signInPassword("ana@example.com","Existing 1!");assertEquals("setup_recovery",profile.nextAction);assertFalse(profile.ready());
    }
    @Test public void accountSwitchAfterRecoveryCheckCannotBeSupersededByTheOldSignin()throws Exception {
        RecoveryRepository repository=new RecoveryRepository(auth);repository.verifyProof("ana@example.com",RecoveryRepositoryTest.CODE);repository.resetPassword(RecoveryRepositoryTest.PASSWORD);
        RecoveryRepository.Transport original=org.robolectric.util.ReflectionHelpers.getField(repository,"transport");
        java.util.concurrent.CountDownLatch checked=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        RecoveryRepository.Transport paused=new RecoveryRepository.Transport(){
            public void requireCurrent(){original.requireCurrent();}
            public JSONObject control(JSONObject value)throws Exception{return original.control(value);}
            public JSONObject recover(JSONObject value)throws Exception{return original.recover(value);}
            public SessionProfile signInPassword(String email,String password)throws Exception{checked.countDown();if(!release.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Recovery barrier timed out.");return original.signInPassword(email,password);}
            public void discardSignIn(SessionProfile profile){original.discardSignIn(profile);}
        };
        org.robolectric.util.ReflectionHelpers.setField(repository,"transport",paused);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        Thread oldSignin=new Thread(()->{try{repository.completePasswordSignIn(RecoveryRepositoryTest.PASSWORD);}catch(Throwable error){failure.set(error);}});
        oldSignin.start();assertTrue(checked.await(3,java.util.concurrent.TimeUnit.SECONDS));
        SessionProfile replacement;
        try{AccountIntegrationTest.Server.email="replacement@tip.edu.ph";AccountIntegrationTest.Server.profileId="student-b";replacement=auth.signInPassword("replacement@tip.edu.ph","Replacement 3!");}
        finally{release.countDown();}
        oldSignin.join(3000);assertFalse(oldSignin.isAlive());
        assertSame("An old recovery flow must not replace the newer signed-in account.",replacement,auth.profile());assertNotNull(failure.get());
        assertEquals("No stale recovery credentials may be dispatched after the replacement signin.","replacement@tip.edu.ph",Server.passwordLogin.getString("email"));
    }
    @Test public void supersededRecoverySigninCannotClearANewerGoogleAttempt()throws Exception {
        RecoveryRepository repository=new RecoveryRepository(auth);repository.verifyProof("ana@example.com",RecoveryRepositoryTest.CODE);repository.resetPassword(RecoveryRepositoryTest.PASSWORD);
        java.util.concurrent.CountDownLatch advanced=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean pauseNext=new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        auth.setInvalidationListener(()->{
            if(pauseNext.compareAndSet(true,false)){
                advanced.countDown();
                try{if(!release.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("Signin barrier timed out.");}
                catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}
            }
        });
        Thread oldSignin=new Thread(()->{try{repository.completePasswordSignIn(RecoveryRepositoryTest.PASSWORD);}catch(Throwable error){failure.set(error);}});
        oldSignin.start();assertTrue(advanced.await(3,java.util.concurrent.TimeUnit.SECONDS));
        String pending;
        try{auth.googleAuthorizationUrl();pending=AccountIntegrationTest.Store.values.get("oauth_pending");assertNotNull(pending);}
        finally{release.countDown();}
        oldSignin.join(3000);assertFalse(oldSignin.isAlive());assertNotNull(failure.get());
        assertTrue("An older password continuation must preserve the newer Google PKCE attempt.",pending.equals(AccountIntegrationTest.Store.values.get("oauth_pending")));
        assertNull("Superseded password credentials must not be dispatched.",Server.passwordLogin);
    }
    @Implements(value=SupabaseClient.class,isInAndroidSdk=false)
    public static final class Server {
        static boolean required;static String token,path;static JSONObject last,passwordLogin;static int mutations;
        @Implementation protected void __constructor__(String url,String key){}
        @Implementation protected String baseUrl(){return "https://example.supabase.co";}
        @Implementation protected JSONObject object(String method,String endpoint,JSONObject body,String bearer)throws Exception {
            token=bearer;path=endpoint;last=body;
            if(endpoint.equals("/functions/v1/recovery-control"))return "generate_codes".equals(body.getString("action"))?RecoveryRepositoryTest.codes():RecoveryRepositoryTest.status(true);
            if(endpoint.equals("/functions/v1/account-recovery")){
                if("verify".equals(body.getString("action"))){
                    long shadowNow=org.robolectric.shadows.ShadowSystem.currentTimeMillis(),rawNow=System.currentTimeMillis(),expires=shadowNow+240000;
                    System.out.println("RECOVERY_CLOCK raw="+rawNow+" instrumented="+shadowNow+" rawDelta="+(expires-rawNow)+" instrumentedDelta="+(expires-shadowNow));
                    return new JSONObject().put("reset_grant",RecoveryRepositoryTest.GRANT).put("expires_at",java.time.Instant.ofEpochMilli(expires).toString());
                }
                if("reset".equals(body.getString("action")))mutations++;
                return new JSONObject().put("state","completed").put("operation_id",body.getString("operation_id"));
            }
            if(endpoint.equals("/auth/v1/token?grant_type=password")){
                passwordLogin=new JSONObject(body.toString());
                if("ana@example.com".equals(body.getString("email")) && "student-b".equals(AccountIntegrationTest.Server.profileId)){AccountIntegrationTest.Server.email="ana@example.com";AccountIntegrationTest.Server.profileId="student-a";}
            }
            JSONObject result=new AccountIntegrationTest.Server().object(method,endpoint,body,bearer);
            if(endpoint.equals("/functions/v1/bootstrap-user") && required)result.put("recovery",RecoveryRepositoryTest.status(false));
            return result;
        }
    }
}
