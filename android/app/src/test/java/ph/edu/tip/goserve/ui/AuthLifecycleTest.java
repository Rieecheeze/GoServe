package ph.edu.tip.goserve.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.widget.CheckBox;
import androidx.lifecycle.ViewModelProvider;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
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
import ph.edu.tip.goserve.data.AuthRepository;
import ph.edu.tip.goserve.data.SecureSessionStore;
import ph.edu.tip.goserve.data.SupabaseClient;
import ph.edu.tip.goserve.ui.common.SeryoMascotView;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = GoServeApplication.class,
        shadows = {AuthLifecycleTest.ShadowSessionStore.class, AuthLifecycleTest.ShadowServer.class},
        instrumentedPackages = {"ph.edu.tip.goserve.data"})
@LooperMode(LooperMode.Mode.PAUSED)
public final class AuthLifecycleTest {
    private static final String STUDENT = "11111111-1111-4111-8111-111111111111";
    private static final String OLD_STUDENT = "22222222-2222-4222-8222-222222222222";
    private final List<ActivityController<AuthActivity>> activities = new ArrayList<>();
    private GoServeApplication application;

    @Before public void resetProcess() throws Exception {
        ShadowSessionStore.values.clear();
        ShadowServer.pkceCalls.set(0);
        ShadowServer.refreshCalls.set(0);
        ShadowServer.refreshDenied = false;
        ShadowServer.consentFails = false;
        ShadowServer.refreshConsentAccepted = false;
        ShadowServer.refreshProfileId = STUDENT;
        ShadowServer.refreshEntered = null;
        ShadowServer.refreshRelease = null;
        ShadowServer.settingsEntered = null;
        ShadowServer.settingsRelease = null;
        ShadowServer.termsEntered=null; ShadowServer.termsRelease=null;
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class, "_instance", null);
        application = (GoServeApplication) RuntimeEnvironment.getApplication();
        replaceProcessRepository();
    }

    @After public void closeActivities() {
        if (ShadowServer.refreshRelease != null) ShadowServer.refreshRelease.countDown();
        if (ShadowServer.settingsRelease != null) ShadowServer.settingsRelease.countDown();
        for (ActivityController<AuthActivity> controller : activities) controller.close();
        activities.clear();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void restoredConsentRebuildsSessionAndCanAcceptConsent() throws Exception {
        ShadowSessionStore.values.put("refresh_token", "saved-refresh-token");
        ActivityController<AuthActivity> first = launch(null, null);
        awaitAuthenticated(first.get(), STUDENT);
        assertTrue(first.get().getSupportFragmentManager().findFragmentById(R.id.authContainer) instanceof TermsFragment);
        Bundle saved = saveAndDestroy(first);

        replaceProcessRepository();
        assertNull(application.auth().profile());
        ActivityController<AuthActivity> restored = launch(null, saved);
        awaitAuthenticated(restored.get(), STUDENT);
        assertTrue(restored.get().getSupportFragmentManager().findFragmentById(R.id.authContainer) instanceof TermsFragment);

        assertNotNull(restored.get().findViewById(R.id.termsContent));
        assertFalse(restored.get().findViewById(R.id.acceptButton).isEnabled());
        agreeToTerms(restored.get()).setChecked(true);
        assertTrue(restored.get().findViewById(R.id.acceptButton).isEnabled());
        restored.get().findViewById(R.id.acceptButton).performClick();
        await(() -> application.auth().profile() != null && application.auth().profile().consentAccepted,
                "Restored consent must be accepted against an authenticated repository.");
        await(() -> restored.get().isFinishing(), "Accepted consent must route out of authentication.");
        Intent next = Shadows.shadowOf(restored.get()).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(MainActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test public void callbackAfterProcessRestartExchangesPendingPkceWithSavedActivityState() throws Exception {
        ActivityController<AuthActivity> first = launch(null, null);
        await(() -> !model(first.get()).state().getValue().loading, "Initial sign-in must finish loading.");
        Uri callback = prepareGoogleCallback();
        Bundle saved = saveAndDestroy(first);
        replaceProcessRepository();

        ActivityController<AuthActivity> restored = launch(callback, saved);
        awaitAuthenticated(restored.get(), STUDENT);
        assertTrue(restored.get().getSupportFragmentManager().findFragmentById(R.id.authContainer) instanceof TermsFragment);
        assertEquals("The one-time auth code must be exchanged once.", 1, ShadowServer.pkceCalls.get());
        assertNull(ShadowSessionStore.values.get("oauth_pending"));
    }

    @Test public void rotationRetainsAuthenticationWithoutExchangingTheCallbackTwice() throws Exception {
        Uri callback = prepareGoogleCallback();
        ActivityController<AuthActivity> controller = launch(callback, null);
        awaitAuthenticated(controller.get(), STUDENT);

        controller.recreate();
        awaitAuthenticated(controller.get(), STUDENT);
        assertEquals("Rotation must not reuse a one-time auth code.", 1, ShadowServer.pkceCalls.get());
        assertTrue(controller.get().getSupportFragmentManager().findFragmentById(R.id.authContainer) instanceof TermsFragment);
    }

    @Test public void staleOriginalCallbackRestoresTheSavedSessionAfterProcessRestart() throws Exception {
        Uri originalCallback = prepareGoogleCallback();
        ActivityController<AuthActivity> first = launch(originalCallback, null);
        awaitAuthenticated(first.get(), STUDENT);
        Bundle saved = saveAndDestroy(first);
        replaceProcessRepository();

        ActivityController<AuthActivity> restored = launch(originalCallback, saved);
        awaitAuthenticated(restored.get(), STUDENT);
        assertEquals("The consumed auth code must not be exchanged again.", 1, ShadowServer.pkceCalls.get());
        assertEquals("A fresh process must restore its saved refresh session.", 1, ShadowServer.refreshCalls.get());
        assertTrue(restored.get().getSupportFragmentManager().findFragmentById(R.id.authContainer) instanceof TermsFragment);
    }

    @Test public void newCallbackDuringRestoreIsProcessedBeforeTheOlderSessionCanRoute() throws Exception {
        ShadowSessionStore.values.put("refresh_token", "saved-refresh-token");
        ShadowServer.refreshProfileId = OLD_STUDENT;
        ShadowServer.refreshConsentAccepted = true;
        ShadowServer.refreshEntered = new CountDownLatch(1);
        ShadowServer.refreshRelease = new CountDownLatch(1);
        ActivityController<AuthActivity> controller = launch(null, null);
        assertTrue("Restore must reach the external server boundary.",
                ShadowServer.refreshEntered.await(5, TimeUnit.SECONDS));
        assertTrue(model(controller.get()).state().getValue().loading);

        // Model the persisted PKCE attempt belonging to the returning browser while refresh is in flight.
        ShadowSessionStore.values.put("oauth_pending", new JSONObject().put("nonce", "browser-nonce")
                .put("verifier", "A".repeat(43)).put("flow","google")
                .put("started_at", org.robolectric.shadows.ShadowSystem.currentTimeMillis()).toString());
        Uri callback = Uri.parse(AuthRepository.CALLBACK).buildUpon().appendQueryParameter("nonce", "browser-nonce")
                .appendQueryParameter("code", "test-auth-code").build();
        try {
            controller.newIntent(new Intent(application, AuthActivity.class).setAction(Intent.ACTION_VIEW).setData(callback));
        } finally {
            ShadowServer.refreshRelease.countDown();
        }

        awaitAuthenticated(controller.get(), STUDENT);
        assertEquals("A callback arriving during restore must not be dropped.", 1, ShadowServer.pkceCalls.get());
        assertNull("The superseded session must not route to the main activity.",
                Shadows.shadowOf(controller.get()).getNextStartedActivity());
        assertTrue(controller.get().getSupportFragmentManager().findFragmentById(R.id.authContainer) instanceof TermsFragment);
    }

    @Test public void restoredConsentReturnsToSignInWhenTheSavedSessionIsInvalid() throws Exception {
        ShadowSessionStore.values.put("refresh_token", "saved-refresh-token");
        ActivityController<AuthActivity> first = launch(null, null);
        awaitAuthenticated(first.get(), STUDENT);
        Bundle saved = saveAndDestroy(first);
        replaceProcessRepository();
        ShadowServer.refreshDenied = true;

        ActivityController<AuthActivity> restored = launch(null, saved);
        await(() -> {
            AuthViewModel.State state = model(restored.get()).state().getValue();
            return state != null && !state.loading && state.error != null
                    && restored.get().getSupportFragmentManager().findFragmentById(R.id.authContainer) instanceof SignInFragment;
        }, "A restored consent screen must return to sign-in when its saved session is invalid.");
        assertNull(application.auth().profile());
        assertNull(ShadowSessionStore.values.get("refresh_token"));
        assertNull(Shadows.shadowOf(restored.get()).getNextStartedActivity());
    }

    @Test public void transientConsentFailureKeepsTheAuthenticatedScreenAvailableForRetry() throws Exception {
        ShadowSessionStore.values.put("refresh_token", "saved-refresh-token");
        ActivityController<AuthActivity> controller = launch(null, null);
        awaitAuthenticated(controller.get(), STUDENT);
        assertNotNull(controller.get().findViewById(R.id.termsContent));
        assertFalse(controller.get().findViewById(R.id.acceptButton).isEnabled());
        agreeToTerms(controller.get()).setChecked(true);
        assertTrue(controller.get().findViewById(R.id.acceptButton).isEnabled());
        ShadowServer.consentFails = true;
        assertTrue(controller.get().findViewById(R.id.acceptButton).performClick());

        await(() -> {
            AuthViewModel.State state = model(controller.get()).state().getValue();
            return state != null && !state.loading && state.error != null;
        }, "A temporary consent service error must finish loading and offer a retry.");
        assertNotNull(application.auth().profile());
        assertEquals(STUDENT, application.auth().profile().id);
        assertFalse(application.auth().profile().consentAccepted);
        assertTrue(controller.get().getSupportFragmentManager().findFragmentById(R.id.authContainer) instanceof TermsFragment);
        assertTrue(controller.get().findViewById(R.id.acceptButton).isEnabled());
        assertNull(Shadows.shadowOf(controller.get()).getNextStartedActivity());

        ShadowServer.consentFails = false;
        assertTrue(controller.get().findViewById(R.id.acceptButton).performClick());
        await(() -> application.auth().profile() != null && application.auth().profile().consentAccepted,
                "Retrying consent must preserve the authenticated session.");
        await(() -> controller.get().isFinishing(), "Successful consent retry must route to the main activity.");
        Intent next = Shadows.shadowOf(controller.get()).getNextStartedActivity();
        assertNotNull(next);
        assertEquals(MainActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test public void loginShowsOnlyTheBrandWhileConnecting() throws Exception { checkBrandLoading(false); }

    @Test public void signupShowsOnlyTheBrandWhileConnecting() throws Exception { checkBrandLoading(true); }

    private void checkBrandLoading(boolean signup) throws Exception {
        ActivityController<AuthActivity> controller = launch(null, null);
        AuthActivity activity = controller.get();
        await(() -> !model(activity).state().getValue().loading, "Initial authentication must finish loading.");
        android.view.View decor = activity.getWindow().getDecorView();
        Object attachInfo = ReflectionHelpers.getField(decor, "mAttachInfo");
        ReflectionHelpers.setField(attachInfo, "mWindowVisibility", android.view.View.VISIBLE);
        decor.dispatchWindowVisibilityChanged(android.view.View.VISIBLE);
        if (signup) { ShadowServer.termsEntered=new CountDownLatch(1); ShadowServer.termsRelease=new CountDownLatch(1); }
        else { ShadowServer.settingsEntered=new CountDownLatch(1); ShadowServer.settingsRelease=new CountDownLatch(1); }
        try {
            activity.findViewById(signup?R.id.signupTab:R.id.googleButton).performClick();
            assertTrue((signup?ShadowServer.termsEntered:ShadowServer.settingsEntered).await(5, TimeUnit.SECONDS));
            android.view.View panel = activity.findViewById(R.id.activityRoot).findViewWithTag("authBrandLoading");
            assertNotNull("Authentication needs a branded loading state.", panel);
            assertEquals(android.view.View.VISIBLE, panel.getVisibility());
            assertNull("Authentication must show the GoServe brand without Seryo.",
                    panel.findViewWithTag("authSeryoMascot"));
            android.view.View logo = panel.findViewWithTag("authBrandLogo");
            assertNotNull("The held request must show the approved GoServe logo.", logo);
            assertTrue(logo.isShown());
            assertEquals(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                    activity.findViewById(R.id.authContainer).getImportantForAccessibility());
        } finally {
            if (signup) ShadowServer.termsRelease.countDown(); else ShadowServer.settingsRelease.countDown();
        }
        await(() -> !model(activity).state().getValue().loading, "The loading state must finish with the request.");
        assertEquals(android.view.View.GONE,
                activity.findViewById(R.id.activityRoot).findViewWithTag("authBrandLoading").getVisibility());
        assertEquals(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO,
                activity.findViewById(R.id.authContainer).getImportantForAccessibility());
    }

    private Uri prepareGoogleCallback() throws Exception {
        Uri authorization = application.auth().googleAuthorizationUrl();
        Uri redirect = Uri.parse(authorization.getQueryParameter("redirect_to"));
        return redirect.buildUpon().appendQueryParameter("code", "test-auth-code").build();
    }

    private ActivityController<AuthActivity> launch(Uri callback, Bundle saved) {
        Intent intent = new Intent(application, AuthActivity.class);
        if (callback != null) intent.setAction(Intent.ACTION_VIEW).setData(callback);
        ActivityController<AuthActivity> controller = Robolectric.buildActivity(AuthActivity.class, intent);
        activities.add(controller);
        if (saved == null) controller.setup();
        else controller.setup(saved);
        controller.visible();
        return controller;
    }

    private Bundle saveAndDestroy(ActivityController<AuthActivity> controller) {
        Bundle saved = new Bundle();
        controller.pause().saveInstanceState(saved).stop().destroy();
        activities.remove(controller);
        return saved;
    }

    private void replaceProcessRepository() throws Exception {
        Field field = GoServeApplication.class.getDeclaredField("auth");
        field.setAccessible(true);
        field.set(application, new AuthRepository(new SupabaseClient("https://example.invalid", "fake-publishable-key"),
                new SecureSessionStore(application)));
    }

    private AuthViewModel model(AuthActivity activity) {
        return new ViewModelProvider(activity).get(AuthViewModel.class);
    }

    private CheckBox agreeToTerms(AuthActivity activity) {
        int id=activity.getResources().getIdentifier("termsAgreement","id",activity.getPackageName());
        CheckBox agreement=activity.findViewById(id);
        assertNotNull("Terms acceptance needs an explicit agreement checkbox.",agreement);
        assertFalse("Agreement must start unchecked.",agreement.isChecked());
        return agreement;
    }

    private void awaitAuthenticated(AuthActivity activity, String id) throws Exception {
        assertSame("The activity must belong to the current test application.", application, activity.getApplication());
        assertSame("The view model factory must use the current test application.", application, model(activity).getApplication());
        try { await(() -> {
            AuthViewModel.State state = model(activity).state().getValue();
            return state != null && !state.loading && state.profile != null && id.equals(state.profile.id);
        }, "Authentication was not restored after activity startup."); }
        catch (AssertionError failure) {
            AuthViewModel.State state=model(activity).state().getValue();
            throw new AssertionError(failure.getMessage()+" Loading="+state.loading+", fixture profile="+(state.profile==null?"none":state.profile.id)
                    +", safe error="+state.error+", exchanges="+ShadowServer.pkceCalls.get(),failure);
        }
        assertNotNull(application.auth().profile());
        assertEquals(id, application.auth().profile().id);
        assertNull(model(activity).state().getValue().error);
    }

    private void await(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            if (condition.getAsBoolean()) return;
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(message, condition.getAsBoolean());
    }

    @Implements(value = SecureSessionStore.class, isInAndroidSdk = false)
    public static final class ShadowSessionStore {
        static final Map<String, String> values = new ConcurrentHashMap<>();
        @Implementation protected void __constructor__(Context context) {}
        @Implementation protected void put(String key, String value) { values.put(key, value); }
        @Implementation protected String get(String key) { return values.get(key); }
        @Implementation protected void remove(String key) { values.remove(key); }
        @Implementation protected void clear() { values.clear(); }
    }

    @Implements(value = SupabaseClient.class, isInAndroidSdk = false)
    public static final class ShadowServer {
        static final AtomicInteger pkceCalls = new AtomicInteger();
        static final AtomicInteger refreshCalls = new AtomicInteger();
        static volatile boolean refreshDenied;
        static volatile boolean consentFails;
        static volatile boolean refreshConsentAccepted;
        static volatile String refreshProfileId;
        static volatile CountDownLatch refreshEntered;
        static volatile CountDownLatch refreshRelease;
        static volatile CountDownLatch settingsEntered;
        static volatile CountDownLatch settingsRelease;
        static volatile CountDownLatch termsEntered,termsRelease;
        private String url;

        @Implementation protected void __constructor__(String baseUrl, String key) { url = baseUrl; }
        @Implementation protected String baseUrl() { return url; }
        @Implementation protected org.json.JSONArray array(String path,String token) throws Exception {
            if (!path.startsWith("/rest/v1/terms_documents?")) throw new IllegalArgumentException("Unexpected account read.");
            if (termsEntered!=null) termsEntered.countDown();
            if (termsRelease!=null && !termsRelease.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("Test Terms read was not released.");
            return new org.json.JSONArray().put(new JSONObject().put("version","approved-v1").put("content","Controlled approved Terms fixture")
                    .put("content_digest","fixture-digest").put("published_at","2026-10-14T00:00:00Z"));
        }

        @Implementation protected JSONObject object(String method, String path, JSONObject body, String token) throws Exception {
            if ("GET".equals(method) && "/auth/v1/settings".equals(path)) {
                if (settingsEntered != null) settingsEntered.countDown();
                if (settingsRelease != null && !settingsRelease.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Test authentication was not released.");
                }
                return new JSONObject().put("external", new JSONObject().put("google", true));
            }
            if (!"POST".equals(method)) throw new IllegalArgumentException("Unexpected authentication HTTP method.");
            if ("/auth/v1/token?grant_type=refresh_token".equals(path)) {
                if (!"saved-refresh-token".equals(body.getString("refresh_token"))) {
                    throw new IllegalArgumentException("Unexpected saved session credential.");
                }
                refreshCalls.incrementAndGet();
                if (refreshEntered != null) refreshEntered.countDown();
                if (refreshRelease != null && !refreshRelease.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Test restore was not released.");
                }
                if (refreshDenied) throw new SupabaseClient.ApiException(400, "Saved session expired.");
                return tokens("restore-access");
            }
            if ("/auth/v1/token?grant_type=pkce".equals(path)) {
                if (!"test-auth-code".equals(body.getString("auth_code")) || body.getString("code_verifier").length() < 43) {
                    throw new IllegalArgumentException("Unexpected PKCE exchange payload.");
                }
                pkceCalls.incrementAndGet();
                return tokens("pkce-access");
            }
            if ("/functions/v1/bootstrap-user".equals(path)) {
                if ("pkce-access".equals(token)) return profile(STUDENT, false);
                if ("restore-access".equals(token)) return profile(refreshProfileId, refreshConsentAccepted);
                throw new IllegalArgumentException("Bootstrap did not use an authenticated session.");
            }
            if ("/rest/v1/rpc/update_account".equals(path)) {
                if (!"restore-access".equals(token) && !"pkce-access".equals(token)) {
                    throw new IllegalArgumentException("Consent did not use an authenticated session.");
                }
                if (consentFails) throw new SupabaseClient.ApiException(503, "Temporary consent service failure.");
                if (!"approved-v1".equals(body.getString("p_terms_version")) || !body.isNull("p_student_profile"))
                    throw new IllegalArgumentException("Terms acceptance must use the approved version without altering profile.");
                return profile(STUDENT, true);
            }
            throw new IllegalArgumentException("Unexpected authentication endpoint.");
        }

        private JSONObject tokens(String access) throws Exception {
            return new JSONObject().put("access_token", access).put("refresh_token", "saved-refresh-token")
                    .put("token_type", "bearer").put("expires_in", 3600)
                    .put("expires_at", System.currentTimeMillis() / 1000 + 3600);
        }

        private JSONObject profile(String id, boolean consent) throws Exception {
            return new JSONObject().put("id", id).put("email", "student@tip.edu.ph").put("display_name", "Test student")
                    .put("role", "student").put("office_id", JSONObject.NULL).put("staff_capability", JSONObject.NULL)
                    .put("is_demo", true).put("consent_accepted_at", consent ? "2026-10-14T00:00:00Z" : JSONObject.NULL)
                    .put("version",consent?2:1).put("profile_complete",true)
                    .put("terms",new JSONObject().put("current_version","approved-v1")
                            .put("accepted_version",consent?"approved-v1":JSONObject.NULL))
                    .put("next_action",consent?"home":"accept_terms");
        }
    }
}
