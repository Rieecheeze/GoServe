package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.os.Looper;
import android.widget.EditText;
import androidx.lifecycle.ViewModelProvider;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.AccountIntegrationTest;
import ph.edu.tip.goserve.data.SupabaseClient;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,shadows={AccountIntegrationTest.Store.class,RecoveryHostHandoffTest.Server.class},instrumentedPackages="ph.edu.tip.goserve.data")
@LooperMode(LooperMode.Mode.PAUSED)
public final class RecoveryHostHandoffTest {
    private static final String EMAIL="ana@example.com",PASSWORD="New 2! password",CODE="AAAA-AAAA-AAAA-AAAA-AAAA-AAAA-AAAA-AAAA";
    private ActivityController<MainActivity> mainController;private ActivityController<AuthActivity> authController;
    private MainActivity main;private BaseActivity flow;private GoServeApplication app;
    @Before public void before()throws Exception{
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class,"_instance",null);new AccountIntegrationTest().setup();
        ReflectionHelpers.setStaticField(AccountIntegrationTest.Server.class,"recoveryAmr",false);
        Server.delayLogin=false;Server.entered=new CountDownLatch(1);Server.release=new CountDownLatch(1);Server.resetCalls=0;
        app=(GoServeApplication)RuntimeEnvironment.getApplication();assertTrue(app.auth().signInPassword(EMAIL,"Existing 1!").ready());idle();
        mainController=Robolectric.buildActivity(MainActivity.class).setup().visible();main=mainController.get();idle();
    }
    @After public void after(){Server.release.countDown();if(authController!=null)authController.close();if(mainController!=null)mainController.close();flow=null;main=null;idle();}
    @Test public void accountPasswordFlowSurvivesItsOwnAuthenticationInvalidationUntilCanonicalSignin()throws Exception{
        completeAccountPasswordFlow(false);
    }
    @Test public void rotationDuringCanonicalSigninRetainsItsPendingRecoveryUntilSigninReturns()throws Exception{
        completeAccountPasswordFlow(true);
    }
    @Test public void accountRecoveryHandoffCannotStartForAnotherOwner()throws Exception{
        Intent stale=new Intent(app,AuthActivity.class).putExtra(AuthActivity.EXTRA_ACCOUNT_RECOVERY_OWNER,"another-owner");
        authController=Robolectric.buildActivity(AuthActivity.class,stale).setup().visible();flow=authController.get();idle();
        assertFalse(new ViewModelProvider(flow).get(AuthViewModel.class).accountPasswordRecovery());
        assertFalse(flow.getIntent().hasExtra(AuthActivity.EXTRA_ACCOUNT_RECOVERY_OWNER));
        assertNull(flow.findViewById(R.id.recoveryProofInput));assertEquals(0,Server.resetCalls);
    }
    private void completeAccountPasswordFlow(boolean rotate)throws Exception{
        main.navigate(SettingsGroupFragment.account(),true);idle();assertTrue(main.findViewById(R.id.changePasswordButton).performClick());idle();
        Intent handoff=Shadows.shadowOf(main).getNextStartedActivity();
        if(handoff!=null){
            assertEquals(AuthActivity.class.getName(),handoff.getComponent().getClassName());
            for(String key:handoff.getExtras().keySet())assertFalse("Recovery credentials must stay in memory.",key.toLowerCase().matches(".*(password|grant|verifier|code).*"));
            authController=Robolectric.buildActivity(AuthActivity.class,handoff).setup().visible();flow=authController.get();
        }else flow=main;
        await(()->flow.findViewById(R.id.recoveryProofInput)!=null,"The actual account password entry must open recovery proof.");
        ((EditText)flow.findViewById(R.id.recoveryProofInput)).setText(CODE);assertTrue(flow.findViewById(R.id.recoveryPrimary).performClick());
        await(()->flow.findViewById(R.id.recoveryPasswordPanel).isShown(),"A valid saved code must reach the new-password step.");
        ((EditText)flow.findViewById(R.id.recoveryNewPassword)).setText(PASSWORD);((EditText)flow.findViewById(R.id.recoveryConfirmPassword)).setText(PASSWORD);
        Server.delayLogin=true;assertTrue(flow.findViewById(R.id.recoveryPrimary).performClick());
        try{
            await(()->Server.entered.getCount()==0,"The real post-reset normal sign-in must begin.");idle();
            assertFalse("Own authentication invalidation must not finish the recovery host before canonical sign-in returns.",flow.isFinishing());
            assertNull("No CLEAR_TASK sign-in redirect may interrupt a confirmed reset.",Shadows.shadowOf(flow).getNextStartedActivity());
            if(rotate){
                assertNotNull("Account password recovery belongs to the auth host.",authController);authController.recreate();flow=authController.get();idle();
                assertTrue(new ViewModelProvider(flow).get(AuthViewModel.class).accountPasswordRecovery());
                androidx.fragment.app.Fragment fragment=flow.getSupportFragmentManager().findFragmentById(R.id.authContainer);
                assertTrue(fragment instanceof RecoverySecurityFragment);
                RecoverySecurityViewModel.State pending=new ViewModelProvider(fragment).get(RecoverySecurityViewModel.class).state().getValue();
                assertTrue("Rotation must retain pending normal sign-in rather than discard it as an unrelated account change. page="+pending.page+" error="+pending.error,pending.busy);
                assertFalse(flow.findViewById(R.id.recoveryProofPanel).isShown());assertFalse(flow.findViewById(R.id.recoveryPasswordPanel).isShown());assertFalse(flow.findViewById(R.id.recoveryCodesPanel).isShown());
                assertFalse(flow.isFinishing());assertNull(Shadows.shadowOf(flow).getNextStartedActivity());
            }
        }finally{Server.release.countDown();}
        await(()->app.auth().profile()!=null&&flow.isFinishing(),"Canonical password sign-in must finish and route to the protected application.");
        Intent destination=Shadows.shadowOf(flow).getNextStartedActivity();assertNotNull(destination);assertEquals(MainActivity.class.getName(),destination.getComponent().getClassName());
        assertEquals(0,destination.getFlags()&Intent.FLAG_ACTIVITY_CLEAR_TASK);assertEquals(EMAIL,app.auth().profile().email);assertTrue(app.auth().profile().ready());assertEquals(1,Server.resetCalls);
    }
    private void await(BooleanSupplier condition,String message)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);while(System.nanoTime()<deadline){idle();if(condition.getAsBoolean())return;Thread.sleep(10);}if(flow!=null){androidx.fragment.app.Fragment fragment=flow.getSupportFragmentManager().findFragmentById(flow instanceof AuthActivity?R.id.authContainer:R.id.mainContainer);if(fragment instanceof RecoverySecurityFragment){RecoverySecurityViewModel.State state=new ViewModelProvider(fragment).get(RecoverySecurityViewModel.class).state().getValue();System.out.println("RECOVERY_HOST page="+state.page+" error="+state.error+" busy="+state.busy+" resets="+Server.resetCalls+" signinEntered="+(Server.entered.getCount()==0));}}fail(message);}
    private void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();if(main!=null)main.getSupportFragmentManager().executePendingTransactions();if(flow!=null&&flow!=main)flow.getSupportFragmentManager().executePendingTransactions();}
    @Implements(value=SupabaseClient.class,isInAndroidSdk=false)
    public static final class Server {
        static volatile boolean delayLogin;static CountDownLatch entered,release;static int resetCalls;
        @Implementation protected void __constructor__(String url,String key){}
        @Implementation protected String baseUrl(){return "https://example.supabase.co";}
        @Implementation protected JSONArray array(String path,String token){return new JSONArray();}
        @Implementation protected JSONObject object(String method,String path,JSONObject body,String token)throws Exception{
            if("/functions/v1/account-recovery".equals(path)){
                if("verify".equals(body.getString("action")))return new JSONObject().put("reset_grant","a".repeat(43)).put("expires_at",java.time.Instant.ofEpochMilli(org.robolectric.shadows.ShadowSystem.currentTimeMillis()+240000).toString());
                if("reset".equals(body.getString("action")))resetCalls++;
                return new JSONObject().put("state","completed").put("operation_id",body.getString("operation_id"));
            }
            if("/auth/v1/token?grant_type=password".equals(path)&&delayLogin){entered.countDown();assertTrue(release.await(10,TimeUnit.SECONDS));assertEquals(PASSWORD,body.getString("password"));}
            return ReflectionHelpers.callInstanceMethod(new AccountIntegrationTest.Server(),"object",ReflectionHelpers.ClassParameter.from(String.class,method),ReflectionHelpers.ClassParameter.from(String.class,path),ReflectionHelpers.ClassParameter.from(JSONObject.class,body),ReflectionHelpers.ClassParameter.from(String.class,token));
        }
    }
}
