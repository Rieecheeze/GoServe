package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
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
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = GoServeApplication.class, qualifiers = "w320dp-h700dp-port-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public final class CapsuleNavigationTest {
    private ActivityController<MainActivity> controller;

    @Before public void resetFactory() {
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class, "_instance", null);
    }
    @After public void closeActivity() { if (controller != null) controller.close(); }

    @Test public void allSixStudentDestinationsHaveTouchTargetsAtLargeText() {
        MainActivity activity = launch(2f);
        CapsuleNavigationView navigation = activity.findViewById(R.id.bottomNavigation);
        assertEquals(6, navigation.getMenu().size());
        View root = activity.findViewById(R.id.activityRoot);
        root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 320, 700);
        for (int i = 0; i < navigation.getMenu().size(); i++) {
            View target = navigation.findViewById(navigation.getMenu().getItem(i).getItemId());
            assertNotNull(target);
            assertTrue("Each destination needs a 48dp wide touch target.", target.getWidth() >= 48);
            assertTrue("Each destination needs a 48dp tall touch target.", target.getHeight() >= 48);
            assertNotNull(target.getContentDescription());
        }
        TextView selectedLabel = navigation.findViewById(R.id.navigationSelectedLabel);
        assertEquals(View.VISIBLE, selectedLabel.getVisibility());
        assertEquals(activity.getString(R.string.home), selectedLabel.getText().toString());
    }

    @Test public void aiChatIsOneTapAwayAndNavigationStaysSelectedAfterRecreation() {
        MainActivity activity = launch(1f);
        CapsuleNavigationView navigation = activity.findViewById(R.id.bottomNavigation);
        navigation.findViewById(R.id.nav_assistant).performClick();
        activity.getSupportFragmentManager().executePendingTransactions();
        assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer) instanceof AssistantFragment);
        assertEquals(R.id.nav_assistant, navigation.getSelectedItemId());
        controller.recreate();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        CapsuleNavigationView recreated = controller.get().findViewById(R.id.bottomNavigation);
        assertEquals(R.id.nav_assistant, recreated.getSelectedItemId());
        assertTrue(controller.get().getSupportFragmentManager().findFragmentById(R.id.mainContainer) instanceof AssistantFragment);
    }

    @Test public void officeRoleMenusKeepTheirExistingDestinations() {
        GoServeApplication app = (GoServeApplication) RuntimeEnvironment.getApplication();
        CapsuleNavigationView navigation = new CapsuleNavigationView(app);
        navigation.inflateMenu(R.menu.menu_staff);
        assertEquals(5, navigation.getMenu().size());
        assertNull(navigation.getMenu().findItem(R.id.nav_assistant));
        navigation.getMenu().clear();
        navigation.inflateMenu(R.menu.menu_teller);
        assertEquals(3, navigation.getMenu().size());
        assertNotNull(navigation.getMenu().findItem(R.id.nav_payments));
        assertNotNull(navigation.getMenu().findItem(R.id.nav_history));
        assertNull(navigation.getMenu().findItem(R.id.nav_assistant));
    }

    private MainActivity launch(float fontScale) {
        GoServeApplication app = (GoServeApplication) RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(app.getResources().getConfiguration());
        configuration.fontScale = fontScale;
        app.getResources().updateConfiguration(configuration, app.getResources().getDisplayMetrics());
        controller = Robolectric.buildActivity(MainActivity.class,
                new Intent(app, MainActivity.class).putExtra(MainActivity.EXTRA_PREVIEW, true)).setup().visible();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        return controller.get();
    }
}
