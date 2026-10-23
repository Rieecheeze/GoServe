package ph.edu.tip.goserve.data;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.widget.TextView;
import android.widget.CheckBox;
import androidx.lifecycle.ViewModelProvider;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.time.Duration;
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
import ph.edu.tip.goserve.ui.MainActivity;
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
    @Test public void confirmedPasswordNoticeSurvivesRotationWithBlankPassword() throws Exception {
        auth.signInPassword("ana@example.com","Current 1! password");
        auth.changePassword(auth.sessionLease(),"Current 1! password","Next 2! password","Next 2! password",null);
        ActivityController<AuthActivity> controller=launch(null,null); idle(model(controller.get()));
        TextView notice=controller.get().findViewById(R.id.passwordNoticeText);
        assertEquals(android.view.View.VISIBLE,notice.getVisibility());
        assertEquals(app.getString(R.string.password_change_notice),notice.getText().toString());
        assertEquals(AuthRepository.PasswordChangeNotice.CHANGED,model(controller.get()).state().getValue().passwordNotice);
        assertEquals("",((TextView)controller.get().findViewById(R.id.passwordInput)).getText().toString());
        controller.recreate(); idle(model(controller.get()));
        assertEquals(app.getString(R.string.password_change_notice),((TextView)controller.get().findViewById(R.id.passwordNoticeText)).getText().toString());
        assertNull(auth.consumePasswordChangeNotice()); assertTrue(AccountIntegrationTest.Store.values.isEmpty());
    }
    @Test public void uncertainPasswordNoticeNeverClaimsSuccess() throws Exception {
        AccountIntegrationTest.Server.profileId="student-b";
        auth.signInPassword("ana@example.com","Current 1! password");
        assertThrows(AuthRepository.PasswordChangeException.class,()->auth.changePassword(auth.sessionLease(),"Current 1! password","Next 2! password","Next 2! password",null));
        AuthActivity activity=launch(null,null).get(); idle(model(activity));
        TextView notice=activity.findViewById(R.id.passwordNoticeText);
        assertEquals(android.view.View.VISIBLE,notice.getVisibility());
        assertEquals(app.getString(R.string.password_change_uncertain_notice),notice.getText().toString());
        assertEquals(AuthRepository.PasswordChangeNotice.UNCERTAIN,model(activity).state().getValue().passwordNotice);
    }
    @Test public void intentExtrasCannotForgeAPasswordChangeNotice() throws Exception {
        Intent intent=new Intent(app,AuthActivity.class).putExtra("password_changed",true).putExtra("password_notice","CHANGED");
        ActivityController<AuthActivity> controller=Robolectric.buildActivity(AuthActivity.class,intent); activities.add(controller); controller.setup().visible(); idle(model(controller.get()));
        assertEquals(android.view.View.GONE,controller.get().findViewById(R.id.passwordNoticeText).getVisibility());
        assertNull(model(controller.get()).state().getValue().passwordNotice);
    }
    private void registration(AuthViewModel model) throws Exception {
        model.startRegistration(); assertEquals(AuthViewModel.Route.TERMS,model.state().getValue().route); idle(model);
        assertNotNull(model.state().getValue().terms); model.acceptTerms();
        assertEquals(AuthViewModel.Route.CREDENTIALS,model.state().getValue().route);
        model.credentials("ana@example.com","Å١! abc","Å١! abc");
        assertEquals(AuthViewModel.Route.PROFILE,model.state().getValue().route);
    }
    @Test public void trustedRegistrationEndsAtCreatedThenExplicitBlankSignin() throws Exception {
        AuthActivity activity=launch(null,null).get(); AuthViewModel model=model(activity); idle(model); registration(model);
        model.submitProfile(student()); idle(model);
        assertEquals(AuthViewModel.Route.ACCOUNT_CREATED,model.state().getValue().route);
        assertNull(auth.profile()); assertNull(AccountIntegrationTest.Store.values.get("refresh_token"));
        assertEquals(1,AccountIntegrationTest.Server.registerCalls);
        assertEquals("Å١! abc",AccountIntegrationTest.Server.lastBody.getString("password"));
        model.signInScreen(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(AuthViewModel.Route.SIGN_IN,model.state().getValue().route);
        assertEquals("",model.registrationPassword());
        assertNotNull(activity.findViewById(R.id.passwordInput));
        assertEquals("",((TextView)activity.findViewById(R.id.passwordInput)).getText().toString());
    }
    @Test public void invalidCredentialsStayOnCredentialsWithSpecificFieldErrors() throws Exception {
        AuthViewModel model=model(launch(null,null).get()); idle(model);
        model.startRegistration(); assertEquals(AuthViewModel.Route.TERMS,model.state().getValue().route); idle(model); model.acceptTerms();
        model.credentials("ana@example.com","A1! abc","different");
        assertEquals(AuthViewModel.Route.CREDENTIALS,model.state().getValue().route);
        assertTrue(model.state().getValue().fieldErrors.containsKey("confirmation"));
        model.credentials("not an email","A1! abc","A1! abc");
        assertTrue(model.state().getValue().fieldErrors.containsKey("email")); assertEquals(0,AccountIntegrationTest.Server.registerCalls);
    }
    @Test public void failedSaveRetainsDraftOnRotationButProcessRestoreRequiresPasswordAgain() throws Exception {
        ActivityController<AuthActivity> controller=launch(null,null); AuthViewModel model=model(controller.get()); idle(model); registration(model);
        AccountIntegrationTest.Server.registrationFails=true; model.submitProfile(student()); idle(model);
        assertEquals(AuthViewModel.Route.PROFILE,model.state().getValue().route); assertNotNull(model.state().getValue().error);
        controller.recreate(); model=model(controller.get());
        assertEquals("Ana",model.draft().profile.firstName); assertEquals("Å١! abc",model.registrationPassword());
        Bundle saved=new Bundle(); controller.saveInstanceState(saved).pause().stop().destroy(); activities.remove(controller);
        replaceAuth(); AuthViewModel restored=model(launch(null,saved).get()); idle(restored);
        assertEquals(AuthViewModel.Route.CREDENTIALS,restored.state().getValue().route);
        assertEquals("ana@example.com",restored.draft().email); assertEquals("Ana",restored.draft().profile.firstName);
        assertEquals("",restored.registrationPassword());
    }
    @Test public void completeRestoredAccountRoutesWithoutSigninFlash() throws Exception {
        AccountIntegrationTest.Store.values.put("refresh_token","fixture-refresh");
        AuthActivity activity=launch(null,null).get();
        assertFalse("A complete restoration must not mount the sign-in form.",activity.getSupportFragmentManager().findFragmentById(R.id.authContainer) instanceof SignInFragment);
        await(activity::isFinishing,"Complete account must enter Home.");
        assertEquals(MainActivity.class.getName(),Shadows.shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
        assertTrue(auth.profile().ready());
    }
    @Test public void googleUsesSameReadonlyEmailProfileAndThenHome() throws Exception {
        AccountIntegrationTest.Server.complete=false; AccountIntegrationTest.Server.accepted=false; AccountIntegrationTest.Server.email="student@tip.edu.ph";
        Uri authorization=auth.googleAuthorizationUrl(); Uri callback=Uri.parse(authorization.getQueryParameter("redirect_to")).buildUpon().appendQueryParameter("code","google-code").build();
        AuthActivity activity=launch(callback,null).get(); AuthViewModel model=model(activity); idle(model);
        assertEquals(AuthViewModel.Route.TERMS,model.state().getValue().route); model.acceptTerms(); idle(model);
        assertEquals(AuthViewModel.Route.PROFILE,model.state().getValue().route);
        assertEquals("student@tip.edu.ph",model.draft().email);
        assertEquals("student@tip.edu.ph",((TextView)activity.findViewById(R.id.profileEmail)).getText().toString());
        model.submitProfile(student()); idle(model); await(activity::isFinishing,"Completed Google account must enter Home.");
        assertTrue(auth.profile().ready()); assertEquals(0,AccountIntegrationTest.Server.registerCalls);
    }
    @Test public void recoveryIntentOverridesOrdinarySessionAndEndsAtBlankSignin() throws Exception {
        auth.requestRecovery("ana@example.com"); String state=AccountIntegrationTest.Server.lastBody.getString("state");
        AccountIntegrationTest.Store.values.put("refresh_token","fixture-refresh"); auth.restore(); assertTrue(auth.profile().ready());
        Uri callback=Uri.parse(AuthRepository.RECOVERY_CALLBACK).buildUpon().appendQueryParameter("flow","recovery").appendQueryParameter("state",state).appendQueryParameter("code","recovery-code").build();
        AuthActivity activity=launch(callback,null).get(); AuthViewModel model=model(activity); idle(model);
        assertEquals(AuthViewModel.Route.RESET_PASSWORD,model.state().getValue().route); assertNull(auth.profile());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        model.resetPassword("B2! xyz","B2! xyz"); idle(model);
        assertEquals(AuthViewModel.Route.RESET_COMPLETE,model.state().getValue().route); model.signInScreen(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals("",((TextView)activity.findViewById(R.id.passwordInput)).getText().toString());
        assertNull(AccountIntegrationTest.Store.values.get("refresh_token"));
    }
    @Test public void readonlyTermsNeverRecordsAcceptance() throws Exception {
        AuthActivity activity=launch(null,null).get(); AuthViewModel model=model(activity); idle(model); model.showTerms(true);
        assertEquals(AuthViewModel.Route.TERMS,model.state().getValue().route); idle(model); model.acceptTerms();
        assertEquals(0,AccountIntegrationTest.Server.updateCalls); assertEquals(0,AccountIntegrationTest.Server.registerCalls);
        assertEquals(android.view.View.GONE,activity.findViewById(R.id.acceptButton).getVisibility());
        assertEquals(android.view.View.GONE,agreement(activity).getVisibility());
    }
    @Test public void registrationRequiresUncheckedExplicitAgreementOnEachAttempt() throws Exception {
        AuthActivity activity=launch(null,null).get(); AuthViewModel model=model(activity); idle(model);
        AccountIntegrationTest.Server.termsAvailable=false;
        model.startRegistration(); idle(model);
        assertFalse(activity.findViewById(R.id.acceptButton).isEnabled());
        assertFalse(agreement(activity).isChecked());
        assertFalse("Unavailable Terms cannot be agreed to.",agreement(activity).isEnabled());
        AccountIntegrationTest.Server.termsAvailable=true; model.loadTerms(); idle(model);
        assertFalse("Loading approved Terms alone must not enable Continue.",activity.findViewById(R.id.acceptButton).isEnabled());
        assertFalse(agreement(activity).isChecked());
        agreement(activity).setChecked(true);
        assertTrue(activity.findViewById(R.id.acceptButton).isEnabled());
        agreement(activity).setChecked(false);
        assertFalse("Withdrawing the check must disable Continue again.",activity.findViewById(R.id.acceptButton).isEnabled());
        agreement(activity).setChecked(true); activity.findViewById(R.id.acceptButton).performClick();
        assertEquals(AuthViewModel.Route.CREDENTIALS,model.state().getValue().route);
        model.back(); Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse("A new agreement attempt must begin unchecked.",agreement(activity).isChecked());
        assertFalse(activity.findViewById(R.id.acceptButton).isEnabled());
        assertEquals(0,AccountIntegrationTest.Server.updateCalls);
    }
    @Test public void locallyExpiredRecoveryClearsSecretsAndLeavesEditableForm() throws Exception {
        AuthActivity activity=launchRecovery(); AuthViewModel model=model(activity);
        org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofHours(2));
        model.resetPassword("B2! xyz","B2! xyz"); idle(model);
        assertEquals(AuthViewModel.Route.RESET_UNAVAILABLE,model.state().getValue().route);
        assertEquals("",model.resetPasswordDraft()); assertEquals("",model.resetConfirmationDraft());
        assertEquals("An expired local credential must never dispatch a password update.",0,AccountIntegrationTest.Server.resetCalls);
        assertNull(auth.profile()); assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        model.recoveryEmail();
        assertEquals(AuthViewModel.Route.RESET_EMAIL,model.state().getValue().route);
    }
    @Test public void terminalRecoverySaveRejectionLeavesEditableFormAndClearsSecrets() throws Exception {
        for (int status:new int[]{401,403}) {
            AuthViewModel model=model(launchRecovery()); AccountIntegrationTest.Server.resetStatus=status;
            model.resetPassword("B2! xyz","B2! xyz"); idle(model);
            assertEquals(AuthViewModel.Route.RESET_UNAVAILABLE,model.state().getValue().route);
            assertEquals("",model.resetPasswordDraft()); assertEquals("",model.resetConfirmationDraft());
        }
    }
    @Test public void transientRecoverySaveFailureRetainsEditableDraftForRetry() throws Exception {
        AuthViewModel model=model(launchRecovery()); AccountIntegrationTest.Server.resetStatus=503;
        model.resetPassword("B2! xyz","B2! xyz"); idle(model);
        assertEquals(AuthViewModel.Route.RESET_PASSWORD,model.state().getValue().route);
        assertNotNull(model.state().getValue().error);
        assertEquals("B2! xyz",model.resetPasswordDraft()); assertEquals("B2! xyz",model.resetConfirmationDraft());
        AccountIntegrationTest.Server.resetStatus=0; model.resetPassword("B2! xyz","B2! xyz"); idle(model);
        assertEquals(AuthViewModel.Route.RESET_COMPLETE,model.state().getValue().route);
        assertEquals("",model.resetPasswordDraft()); assertEquals(2,AccountIntegrationTest.Server.resetCalls);
    }
    private CheckBox agreement(AuthActivity activity) {
        int id=activity.getResources().getIdentifier("termsAgreement","id",activity.getPackageName());
        CheckBox agreement=activity.findViewById(id);
        assertNotNull("Terms acceptance needs an explicit agreement checkbox.",agreement);
        return agreement;
    }
    private AuthActivity launchRecovery() throws Exception {
        auth.requestRecovery("ana@example.com");
        Uri callback=Uri.parse(AuthRepository.RECOVERY_CALLBACK).buildUpon().appendQueryParameter("flow","recovery")
                .appendQueryParameter("state",AccountIntegrationTest.Server.lastBody.getString("state"))
                .appendQueryParameter("code","recovery-code").build();
        AuthActivity activity=launch(callback,null).get(); idle(model(activity));
        assertEquals(AuthViewModel.Route.RESET_PASSWORD,model(activity).state().getValue().route);
        return activity;
    }
    @Test public void provisionedStaffRetainsRoleAndDoesNotEnterStudentForm() throws Exception {
        AccountIntegrationTest.Server.role="staff"; AccountIntegrationTest.Store.values.put("refresh_token","fixture-refresh");
        AuthActivity activity=launch(null,null).get(); await(activity::isFinishing,"Provisioned complete Staff must retain its Home route.");
        assertEquals(ph.edu.tip.goserve.domain.Role.STAFF,auth.profile().role); assertTrue(auth.profile().ready());
        assertFalse(activity.getSupportFragmentManager().findFragmentById(R.id.authContainer) instanceof SignInFragment);
    }
}
