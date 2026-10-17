package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import ph.edu.tip.goserve.domain.Office;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "port-night-mdpi", application = GoServeApplication.class)
@LooperMode(LooperMode.Mode.PAUSED)
public final class OfficePaymentRoutingTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private OfficeViewModel offices;

    @Before public void launchPreview() {
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class, "_instance", null);
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), MainActivity.class)
                .putExtra(MainActivity.EXTRA_PREVIEW, true);
        controller = Robolectric.buildActivity(MainActivity.class, intent).setup().visible();
        activity = controller.get();
        offices = new ViewModelProvider(activity).get(OfficeViewModel.class);
        idle();
    }

    @After public void closePreview() {
        if (controller != null) controller.close();
        activity = null;
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void eachOtherOfficeRoutesPaymentsToCashieringAndBackReturnsToTheSource() {
        List<Office> directory = new ArrayList<>(offices.state().getValue().offices);
        assertEquals(5, directory.size());
        for (Office source : directory) {
            if ("OFF-CASH".equals(source.id)) continue;
            activity.navigate(OfficeDetailFragment.create(source), true);
            idle();
            View paymentLink = visibleText(current().requireView(), "Pay at Cashiering");
            assertNotNull("Office " + source.id + " needs a link to Cashiering.", paymentLink);
            assertTrue(paymentLink.isEnabled());
            assertTrue(paymentLink.performClick());
            idle();

            assertTrue(current() instanceof OfficeDetailFragment);
            assertEquals("OFF-CASH", current().requireArguments().getString("id"));
            assertEquals("Cashiering", ((TextView) current().requireView().findViewById(R.id.officeName)).getText().toString());
            assertNull("Cashiering must not offer a redirect back to itself.",
                    visibleText(current().requireView(), "Pay at Cashiering"));

            current().requireView().findViewById(R.id.backButton).performClick();
            idle();
            assertEquals(source.id, current().requireArguments().getString("id"));
            assertEquals(source.name, ((TextView) current().requireView().findViewById(R.id.officeName)).getText().toString());
            activity.getSupportFragmentManager().popBackStackImmediate();
            idle();
        }
    }

    @Test public void loadingTheDirectoryDisablesPaymentRoutingUntilCashieringIsResolved() {
        Office source = offices.state().getValue().offices.stream().filter(office -> "OFF-REG".equals(office.id)).findFirst().orElseThrow();
        activity.navigate(OfficeDetailFragment.create(source), true);
        idle();
        publish(new OfficeViewModel.State(true, Collections.emptyList(), null));
        View paymentLink = visibleText(current().requireView(), "Pay at Cashiering");
        assertNotNull(paymentLink);
        assertFalse(paymentLink.isEnabled());
        assertNotNull(visibleText(current().requireView(), "Loading Cashiering…"));
        assertEquals("OFF-REG", current().requireArguments().getString("id"));
    }

    @Test public void anUnavailableCashieringCanBeReloadedBeforeRoutingPayments() {
        Office source = offices.state().getValue().offices.stream().filter(office -> "OFF-ACC".equals(office.id)).findFirst().orElseThrow();
        activity.navigate(OfficeDetailFragment.create(source), true);
        idle();
        publish(new OfficeViewModel.State(false, List.of(source), "Could not load offices."));
        View paymentLink = visibleText(current().requireView(), "Pay at Cashiering");
        assertNotNull(paymentLink);
        assertFalse(paymentLink.isEnabled());
        View retry = visibleText(current().requireView(), "Reload offices");
        assertNotNull("An unavailable destination must provide a reload action.", retry);
        assertTrue(retry.performClick());
        idle();

        paymentLink = visibleText(current().requireView(), "Pay at Cashiering");
        assertTrue(paymentLink.isEnabled());
        assertTrue(paymentLink.performClick());
        idle();
        assertEquals("OFF-CASH", current().requireArguments().getString("id"));
    }

    private void publish(OfficeViewModel.State state) {
        ((MutableLiveData<OfficeViewModel.State>) offices.state()).setValue(state);
        idle();
    }

    private Fragment current() {
        return activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer);
    }

    private View visibleText(View root, String text) {
        if (root.getVisibility() != View.VISIBLE) return null;
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                View found = visibleText(group.getChildAt(index), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        if (activity != null) activity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
}
