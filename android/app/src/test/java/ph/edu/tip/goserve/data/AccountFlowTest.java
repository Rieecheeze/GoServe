package ph.edu.tip.goserve.data;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.widget.TextView;
import android.widget.CheckBox;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModelProvider;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
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
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.domain.StudentProfile;
import ph.edu.tip.goserve.ui.AuthActivity;
import ph.edu.tip.goserve.ui.AuthViewModel;
import ph.edu.tip.goserve.ui.GoogleRegistrationFragment;
import ph.edu.tip.goserve.ui.MainActivity;
import ph.edu.tip.goserve.ui.RecoverySecurityViewModel;
import ph.edu.tip.goserve.ui.SignInFragment;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,
        shadows={AccountIntegrationTest.Store.class,AccountIntegrationTest.Server.class},instrumentedPackages={"ph.edu.tip.goserve.data"})
@LooperMode(LooperMode.Mode.PAUSED)
public final class AccountFlowTest {
    private GoServeApplication app;
    private AuthRepository auth;
    private final List<ActivityController<AuthActivity>> activities=new ArrayList<>();
    @Before public void setup() throws Exception {
        new AccountIntegrationTest().setup();
        AccountIntegrationTest.Server.termsAvailable=true;
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class,"_instance",null);
        app=(GoServeApplication)RuntimeEnvironment.getApplication(); replaceAuth();
    }
    private void replaceAuth() throws Exception {
        auth=new AuthRepository(new SupabaseClient("https://example.supabase.co","fixture"),new SecureSessionStore(app));
        Field field=GoServeApplication.class.getDeclaredField("auth"); field.setAccessible(true); field.set(app,auth);
    }
    @After public void close() {
        for (ActivityController<AuthActivity> activity:activities) activity.close();
        activities.clear(); Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    private ActivityController<AuthActivity> launch(Uri callback,Bundle saved) {
        Intent intent=new Intent(app,AuthActivity.class);
        if (callback!=null) intent.setAction(Intent.ACTION_VIEW).setData(callback);
        ActivityController<AuthActivity> activity=Robolectric.buildActivity(AuthActivity.class,intent);
        activities.add(activity); if (saved==null) activity.setup(); else activity.setup(saved); activity.visible(); return activity;
    }
    private AuthViewModel model(AuthActivity activity) { return new ViewModelProvider(activity).get(AuthViewModel.class); }
    private void idle(AuthViewModel model) throws Exception { await(() -> !model.state().getValue().loading,"Account work did not finish."); }
    private void await(BooleanSupplier condition,String message) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        do { Shadows.shadowOf(Looper.getMainLooper()).idle(); if (condition.getAsBoolean()) return; Thread.sleep(10); } while (System.nanoTime()<until);
        assertTrue(message,condition.getAsBoolean());
    }
    private StudentProfile student() { return new StudentProfile("Ana","",true,"Cruz","Campus ID","Course","Year","Mobile","Self described"); }
    @Test public void provisionedPasswordSigninStillEntersHomeAndClearsPassword() throws Exception {
        AuthActivity activity=launch(null,null).get(); AuthViewModel model=model(activity); idle(model);
        model.password("ana@example.com","Current 1! password"); idle(model);
        await(activity::isFinishing,"Existing password credentials must still enter Home.");
        assertEquals(AuthViewModel.Route.HOME,model.state().getValue().route);
        assertEquals("ana@example.com",auth.profile().email); assertTrue(auth.profile().ready());
        assertEquals("",model.signinPassword()); assertNull(model.state().getValue().passwordNotice);
        assertEquals(0,AccountIntegrationTest.Server.registerCalls);
        assertNotNull(AccountIntegrationTest.Store.values.get("refresh_token"));
        assertEquals(MainActivity.class.getName(),Shadows.shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
    }
    @Test public void retiredPasswordChangeCannotDispatchOrClaimSuccess() throws Exception {
        auth.signInPassword("ana@example.com","Current 1! password");
        int calls=AccountIntegrationTest.Server.calls;
        AuthRepository.SessionLease lease=auth.sessionLease();
        assertThrows(IllegalStateException.class,()->auth.changePassword(lease,"Current 1! password","Next 2! password","Next 2! password",null));
        assertThrows(IllegalStateException.class,()->auth.requestPasswordReauthentication(lease));
        assertEquals(calls,AccountIntegrationTest.Server.calls); assertEquals(0,AccountIntegrationTest.Server.resetCalls);
        assertNull(auth.consumePasswordChangeNotice()); assertTrue(auth.profile().ready());
    }
    @Test public void intentExtrasCannotForgeAPasswordChangeNotice() throws Exception {
        Intent intent=new Intent(app,AuthActivity.class).putExtra("password_changed",true).putExtra("password_notice","CHANGED");
        ActivityController<AuthActivity> controller=Robolectric.buildActivity(AuthActivity.class,intent); activities.add(controller); controller.setup().visible(); idle(model(controller.get()));
        assertEquals(android.view.View.GONE,controller.get().findViewById(R.id.passwordNoticeText).getVisibility());
        assertNull(model(controller.get()).state().getValue().passwordNotice);
    }
    @Test public void registrationRequiresTipGoogleAndSurvivesRotationWithoutCredentials() throws Exception {
        ActivityController<AuthActivity> controller=launch(null,null); AuthViewModel model=model(controller.get()); idle(model);
        int calls=AccountIntegrationTest.Server.calls; model.startRegistration();
        assertEquals(AuthViewModel.Route.REGISTER_GOOGLE,model.state().getValue().route);
        assertTrue(controller.get().getSupportFragmentManager().findFragmentById(R.id.authContainer) instanceof GoogleRegistrationFragment);
        assertNotNull(controller.get().findViewById(R.id.registrationGoogleContinue));
        assertNull(controller.get().findViewById(R.id.passwordInput)); assertFalse(model.registering());
        controller.recreate(); model=model(controller.get()); idle(model);
        assertEquals(AuthViewModel.Route.REGISTER_GOOGLE,model.state().getValue().route);
        assertEquals(calls,AccountIntegrationTest.Server.calls); assertEquals(0,AccountIntegrationTest.Server.registerCalls);
        assertNull(auth.profile()); assertNull(AccountIntegrationTest.Store.values.get("refresh_token")); assertSecretsCleared(model);
        model.signInScreen();
        assertEquals("",((TextView)controller.get().findViewById(R.id.passwordInput)).getText().toString());
    }
    @Test public void legacyRegistrationCredentialsReturnToGoogleWithoutNetworkOrSecrets() throws Exception {
        AuthViewModel model=model(launch(null,null).get()); idle(model); int calls=AccountIntegrationTest.Server.calls;
        for (String email:new String[]{"ana@tip.edu.ph","ana@example.com","not an email"}) {
            model.setRegistrationPasswords("A1! abc","different"); model.setSigninPassword("Current 1! password"); model.setResetPasswords("B2! xyz","B2! xyz");
            model.credentials(email,"A1! abc","A1! abc");
            assertEquals(AuthViewModel.Route.REGISTER_GOOGLE,model.state().getValue().route); assertSecretsCleared(model);
            assertFalse(model.registering()); assertEquals(calls,AccountIntegrationTest.Server.calls);
        }
        assertEquals(0,AccountIntegrationTest.Server.registerCalls); assertNull(auth.profile());
        assertNull(AccountIntegrationTest.Store.values.get("refresh_token"));
    }
    @Test public void restoredLegacyRegistrationDraftRequiresGoogleAndNoPassword() throws Exception {
        for (String step:new String[]{"CREDENTIALS","PROFILE"}) {
            Map<String,Object> values=new HashMap<>(); values.put("step",step); values.put("email","ana@example.com"); values.put("first_name","Ana");
            AuthViewModel restored=new AuthViewModel(app,new SavedStateHandle(values));
            try {
                int calls=AccountIntegrationTest.Server.calls; restored.initialize(null); idle(restored);
                assertEquals(AuthViewModel.Route.REGISTER_GOOGLE,restored.state().getValue().route);
                assertEquals("ana@example.com",restored.draft().email); assertEquals("Ana",restored.draft().profile.firstName);
                assertFalse(restored.registering()); assertSecretsCleared(restored); assertEquals(calls,AccountIntegrationTest.Server.calls);
            } finally { ReflectionHelpers.callInstanceMethod(restored,"onCleared"); }
        }
    }
    @Test public void completeRestoredAccountRoutesWithoutSigninFlash() throws Exception {
        AccountIntegrationTest.Store.values.put("refresh_token","fixture-refresh");
        AuthActivity activity=launch(null,null).get();
        assertFalse("A complete restoration must not mount the sign-in form.",activity.getSupportFragmentManager().findFragmentById(R.id.authContainer) instanceof SignInFragment);
        await(activity::isFinishing,"Complete account must enter Home.");
        assertEquals(MainActivity.class.getName(),Shadows.shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
        assertTrue(auth.profile().ready());
    }
    @Test public void googleUsesReadonlyEmailProfileThenRequiresRecoverySetupBeforeHome() throws Exception {
        AccountIntegrationTest.Server.recoveryRequired=true;
        AuthActivity activity=launchGoogle(); AuthViewModel model=model(activity);
        assertEquals(AuthViewModel.Route.TERMS,model.state().getValue().route); model.acceptTerms(); idle(model);
        assertEquals(AuthViewModel.Route.PROFILE,model.state().getValue().route);
        assertEquals("student@tip.edu.ph",model.draft().email);
        assertEquals("student@tip.edu.ph",((TextView)activity.findViewById(R.id.profileEmail)).getText().toString());
        model.submitProfile(student()); idle(model);
        assertEquals(AuthViewModel.Route.RECOVERY_SETUP,model.state().getValue().route);
        RecoverySecurityViewModel recovery=recoveryModel(activity);
        await(()->recovery.state().getValue()!=null&&!recovery.state().getValue().busy,"Recovery setup status did not load.");
        assertEquals(RecoverySecurityViewModel.Kind.SETUP,recovery.state().getValue().kind);
        assertEquals(RecoverySecurityViewModel.Page.STATUS,recovery.state().getValue().page);
        assertTrue(recovery.state().getValue().status.requiresSetup); assertFalse(recovery.state().getValue().status.codesAcknowledged);
        assertFalse(auth.profile().ready()); assertEquals("setup_recovery",auth.profile().nextAction);
        assertFalse(activity.isFinishing()); assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        assertEquals(0,AccountIntegrationTest.Server.registerCalls); assertEquals(2,AccountIntegrationTest.Server.updateCalls);
    }
    @Test public void retiredRecoveryIntentCannotExchangeCredentialsOrOpenHome() throws Exception {
        AccountIntegrationTest.Store.values.put("refresh_token","fixture-refresh"); auth.restore(); assertTrue(auth.profile().ready());
        int calls=AccountIntegrationTest.Server.calls;
        Uri callback=Uri.parse(AuthRepository.RECOVERY_CALLBACK).buildUpon().appendQueryParameter("flow","recovery").appendQueryParameter("state","retired-state").appendQueryParameter("code","recovery-code").build();
        AuthActivity activity=launch(callback,null).get(); AuthViewModel model=model(activity); idle(model);
        assertEquals(AuthViewModel.Route.RESET_UNAVAILABLE,model.state().getValue().route); assertSecretsCleared(model);
        assertTrue(model.state().getValue().error.contains("retired"));
        assertEquals(calls,AccountIntegrationTest.Server.calls); assertEquals(0,AccountIntegrationTest.Server.resetCalls);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        assertFalse(activity.isFinishing()); assertTrue(auth.profile().ready());
    }
    @Test public void readonlyTermsNeverRecordsAcceptance() throws Exception {
        AuthActivity activity=launch(null,null).get(); AuthViewModel model=model(activity); idle(model); model.showTerms(true);
        assertEquals(AuthViewModel.Route.TERMS,model.state().getValue().route); idle(model); model.acceptTerms();
        assertEquals(0,AccountIntegrationTest.Server.updateCalls); assertEquals(0,AccountIntegrationTest.Server.registerCalls);
        assertEquals(android.view.View.GONE,activity.findViewById(R.id.acceptButton).getVisibility());
        assertEquals(android.view.View.GONE,agreement(activity).getVisibility());
    }
    @Test public void authenticatedGoogleRequiresUncheckedExplicitTermsAgreement() throws Exception {
        AccountIntegrationTest.Server.termsAvailable=false;
        ActivityController<AuthActivity> controller=launchGoogleController(); AuthActivity activity=controller.get(); AuthViewModel model=model(activity);
        assertEquals(AuthViewModel.Route.TERMS,model.state().getValue().route); assertNotNull(auth.profile());
        assertFalse(activity.findViewById(R.id.acceptButton).isEnabled());
        assertFalse(agreement(activity).isChecked());
        assertFalse("Unavailable Terms cannot be agreed to.",agreement(activity).isEnabled());
        AccountIntegrationTest.Server.termsAvailable=true; model.loadTerms(); idle(model);
        assertFalse("Loading approved Terms alone must not enable Continue.",activity.findViewById(R.id.acceptButton).isEnabled());
        assertFalse(agreement(activity).isChecked());
        agreement(activity).setChecked(true); controller.recreate(); activity=controller.get(); model=model(activity); idle(model);
        assertFalse("A recreated agreement attempt must begin unchecked.",agreement(activity).isChecked());
        assertFalse(activity.findViewById(R.id.acceptButton).isEnabled());
        agreement(activity).setChecked(true);
        assertTrue(activity.findViewById(R.id.acceptButton).isEnabled());
        agreement(activity).setChecked(false);
        assertFalse("Withdrawing the check must disable Continue again.",activity.findViewById(R.id.acceptButton).isEnabled());
        agreement(activity).setChecked(true); activity.findViewById(R.id.acceptButton).performClick();
        idle(model); assertEquals(AuthViewModel.Route.PROFILE,model.state().getValue().route);
        assertEquals(1,AccountIntegrationTest.Server.updateCalls); assertEquals(0,AccountIntegrationTest.Server.registerCalls);
    }
    @Test public void directResetWithoutSavedCodeProofClearsSecretsWithoutNetwork() throws Exception {
        AuthActivity activity=launch(null,null).get(); AuthViewModel model=model(activity); idle(model);
        model.setRegistrationPasswords("A1! abc","A1! abc"); model.setSigninPassword("Current 1! password"); model.setResetPasswords("B2! xyz","B2! xyz");
        int calls=AccountIntegrationTest.Server.calls;
        model.resetPassword("B2! xyz","B2! xyz"); idle(model);
        assertEquals(AuthViewModel.Route.RESET_UNAVAILABLE,model.state().getValue().route);
        assertSecretsCleared(model); assertEquals(calls,AccountIntegrationTest.Server.calls);
        assertEquals(0,AccountIntegrationTest.Server.resetCalls);
        assertNull(auth.profile()); assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        model.recoveryEmail();
        assertEquals(AuthViewModel.Route.RESET_EMAIL,model.state().getValue().route);
    }
    @Test public void retiredEmailRequestKeepsSavedCodeFormWithoutSendingEmail() throws Exception {
        AuthActivity activity=launch(null,null).get(); AuthViewModel model=model(activity); idle(model);
        model.setRegistrationPasswords("A1! abc","A1! abc"); model.setSigninPassword("Current 1! password"); model.setResetPasswords("B2! xyz","B2! xyz");
        model.recoveryEmail(); model.setRecoveryEmail("ana@example.com"); int calls=AccountIntegrationTest.Server.calls;
        model.requestRecovery(); idle(model);
        assertEquals(AuthViewModel.Route.RESET_EMAIL,model.state().getValue().route);
        assertTrue(model.state().getValue().error.contains("No recovery email is sent")); assertSecretsCleared(model);
        assertEquals(calls,AccountIntegrationTest.Server.calls); assertNull(AccountIntegrationTest.Store.values.get("recovery_pending"));
        RecoverySecurityViewModel recovery=recoveryModel(activity);
        assertEquals(RecoverySecurityViewModel.Kind.FORGOT,recovery.state().getValue().kind);
        assertEquals(RecoverySecurityViewModel.Page.PROOF,recovery.state().getValue().page);
        assertNull(auth.profile()); assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }
    @Test public void retiredResetCannotBecomeAnAuthorizedRetryAfterRotation() throws Exception {
        ActivityController<AuthActivity> controller=launch(null,null); AuthViewModel model=model(controller.get()); idle(model);
        int calls=AccountIntegrationTest.Server.calls; AccountIntegrationTest.Server.resetStatus=503;
        model.setResetPasswords("B2! xyz","B2! xyz"); model.resetPassword("B2! xyz","B2! xyz"); idle(model);
        controller.recreate(); model=model(controller.get()); idle(model);
        assertEquals(AuthViewModel.Route.RESET_UNAVAILABLE,model.state().getValue().route); assertSecretsCleared(model);
        AccountIntegrationTest.Server.resetStatus=0; model.resetPassword("B2! xyz","B2! xyz"); idle(model);
        assertEquals(AuthViewModel.Route.RESET_UNAVAILABLE,model.state().getValue().route); assertSecretsCleared(model);
        assertEquals(calls,AccountIntegrationTest.Server.calls); assertEquals(0,AccountIntegrationTest.Server.resetCalls);
        assertNull(auth.profile()); assertNull(Shadows.shadowOf(controller.get()).getNextStartedActivity());
    }
    private CheckBox agreement(AuthActivity activity) {
        int id=activity.getResources().getIdentifier("termsAgreement","id",activity.getPackageName());
        CheckBox agreement=activity.findViewById(id);
        assertNotNull("Terms acceptance needs an explicit agreement checkbox.",agreement);
        return agreement;
    }
    private void assertSecretsCleared(AuthViewModel model) {
        assertEquals("",model.registrationPassword()); assertEquals("",model.registrationConfirmation());
        assertEquals("",model.signinPassword()); assertEquals("",model.resetPasswordDraft()); assertEquals("",model.resetConfirmationDraft());
    }
    private RecoverySecurityViewModel recoveryModel(AuthActivity activity) {
        return new ViewModelProvider(activity.getSupportFragmentManager().findFragmentById(R.id.authContainer)).get(RecoverySecurityViewModel.class);
    }
    private ActivityController<AuthActivity> launchGoogleController() throws Exception {
        AccountIntegrationTest.Server.complete=false; AccountIntegrationTest.Server.accepted=false; AccountIntegrationTest.Server.email="student@tip.edu.ph";
        Uri authorization=auth.googleAuthorizationUrl();
        assertEquals("tip.edu.ph",authorization.getQueryParameter("hd"));
        Uri callback=Uri.parse(authorization.getQueryParameter("redirect_to")).buildUpon().appendQueryParameter("code","google-code").build();
        ActivityController<AuthActivity> controller=launch(callback,null); idle(model(controller.get())); return controller;
    }
    private AuthActivity launchGoogle() throws Exception {
        return launchGoogleController().get();
    }
    @Test public void provisionedStaffRetainsRoleAndDoesNotEnterStudentForm() throws Exception {
        AccountIntegrationTest.Server.role="staff"; AccountIntegrationTest.Store.values.put("refresh_token","fixture-refresh");
        AuthActivity activity=launch(null,null).get(); await(activity::isFinishing,"Provisioned complete Staff must retain its Home route.");
        assertEquals(ph.edu.tip.goserve.domain.Role.STAFF,auth.profile().role); assertTrue(auth.profile().ready());
        assertFalse(activity.getSupportFragmentManager().findFragmentById(R.id.authContainer) instanceof SignInFragment);
    }
}
