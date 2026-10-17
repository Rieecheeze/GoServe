package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
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
import ph.edu.tip.goserve.domain.Role;
import ph.edu.tip.goserve.domain.SessionProfile;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "port-night-mdpi", application = GoServeApplication.class)
@LooperMode(LooperMode.Mode.PAUSED)
public final class RequestPreviewTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;

    @Before public void launchPreview() {
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class, "_instance", null);
        controller = Robolectric.buildActivity(MainActivity.class,
                new Intent(RuntimeEnvironment.getApplication(), MainActivity.class).putExtra(MainActivity.EXTRA_PREVIEW, true)).setup().visible();
        activity = controller.get();
        idle();
    }

    @After public void closePreview() { if (controller != null) controller.close(); }

    @Test public void requiredRequestShowsCompletedChecksAndCurrentProcessing() throws Exception {
        openList();
        click("Enrollment certificate");
        assertEquals("RequestDetailFragment", current().getClass().getSimpleName());
        assertNotNull(described(current().requireView(), "Submitted: Complete"));
        assertNotNull(described(current().requireView(), "Awaiting Payment: Complete"));
        assertNotNull(described(current().requireView(), "Processing: Current"));
        assertNotNull(text(current().requireView(), "Paid · Demo payment record"));
        assertNull(text(current().requireView(), "Pay at Cashiering"));
    }

    @Test public void freeRequestSkipsPaymentAndVerifiedPreviewExplainsDownload() throws Exception {
        openList();
        click("Verified document copy");
        assertNull(described(current().requireView(), "Awaiting Payment: Complete"));
        assertNotNull(text(current().requireView(), "Free · Declared by the office in this example"));
        click("View verified file");
        assertEquals("VerifiedFileFragment", current().getClass().getSimpleName());
        assertNotNull(text(current().requireView(), "Preview verification complete"));
        assertNotNull(described(current().requireView(), "File verification: Complete"));
        click("About downloads");
        assertNotNull(text(current().requireView(), "This preview contains no private file. Download access will be available when an office issues a real document."));
        assertNull("A preview must not launch a file download.", Shadows.shadowOf(activity).getNextStartedActivity());
        click("Back");
        assertEquals("RequestDetailFragment", current().getClass().getSimpleName());
    }

    @Test public void liveAccountNeverReceivesPreviewRequests() throws Exception {
        ReflectionHelpers.setField(activity, "preview", false);
        ReflectionHelpers.setField(activity, "profile", new SessionProfile("live-user", "student@tip.edu.ph", "Student", Role.STUDENT, null, null, false, true));
        openList();
        assertNotNull(text(current().requireView(), "Requests are not available yet"));
        assertNull(text(current().requireView(), "Enrollment certificate"));
        assertNull(text(current().requireView(), "Verified document copy"));
    }

    private void openList() throws Exception {
        Class<?> type = Class.forName("ph.edu.tip.goserve.ui.RequestListFragment");
        activity.navigate((Fragment) type.getMethod("create").invoke(null), true);
        idle();
    }
    private void click(String label) {
        View target = text(current().requireView(), label);
        assertNotNull("Missing request action: " + label, target);
        while (!target.isClickable() && target.getParent() instanceof View) target = (View) target.getParent();
        assertTrue(target.performClick());
        idle();
    }
    private Fragment current() { return activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer); }
    private View text(View root, String value) {
        return find(root, view -> view instanceof TextView && value.contentEquals(((TextView) view).getText()));
    }
    private View described(View root, String value) { return find(root, view -> view.getContentDescription() != null && value.contentEquals(view.getContentDescription())); }
    private View find(View root, java.util.function.Predicate<View> predicate) {
        if (root.getVisibility() != View.VISIBLE) return null;
        if (predicate.test(root)) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = find(group.getChildAt(index), predicate);
                if (found != null) return found;
            }
        }
        return null;
    }
    private void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        activity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
}
