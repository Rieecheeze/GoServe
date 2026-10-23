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
        assertEquals(56, navigation.getHeight());
        assertNoVisibleLabels(navigation);
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
        assertEquals(4, navigation.getMenu().size());
        assertEquals("Search",navigation.getMenu().getItem(0).getTitle().toString());
        assertNotNull(navigation.getMenu().findItem(R.id.nav_payments));
        assertNotNull(navigation.getMenu().findItem(R.id.nav_history));
        assertNull(navigation.getMenu().findItem(R.id.nav_assistant));
    }

    @Test public void tellerCapsuleHasFourAccessibleTargetsWithoutAVisibleCaption() {
        MainActivity activity=launch(1f); CapsuleNavigationView navigation=activity.findViewById(R.id.bottomNavigation);
        navigation.getMenu().clear(); navigation.inflateMenu(R.menu.menu_teller); navigation.setOnItemSelectedListener(null);
        View root=activity.findViewById(R.id.activityRoot);
        root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(700,View.MeasureSpec.EXACTLY)); root.layout(0,0,320,700);
        assertEquals(56,navigation.getHeight()); assertNoVisibleLabels(navigation);
        for(int n=0;n<4;n++) { View target=navigation.findViewById(navigation.getMenu().getItem(n).getItemId()); assertTrue(target.getHeight()>=48); assertTrue(target.getWidth()>=48); assertNotNull(target.getContentDescription()); }
        int[] calls={0}; navigation.setOnItemSelectedListener(item->{++calls[0]; return true;});
        navigation.markSelectedItemId(R.id.nav_cash_pending); assertEquals(0,calls[0]); assertEquals(R.id.nav_cash_pending,navigation.getSelectedItemId());
        navigation.setSelectedItemId(R.id.nav_history); assertEquals(1,calls[0]);
    }

    @Test public void iconsRemainCenteredAndEvenlySpacedForEveryRoleAndPhoneWidth() {
        MainActivity activity=launch(2f); CapsuleNavigationView navigation=activity.findViewById(R.id.bottomNavigation);
        navigation.setOnItemSelectedListener(null);
        View root=activity.findViewById(R.id.activityRoot);
        for(int menu:new int[]{R.menu.menu_student,R.menu.menu_staff,R.menu.menu_teller}) {
            navigation.getMenu().clear(); navigation.inflateMenu(menu);
            for(int width:new int[]{320,360,390,600}) {
                root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(844,View.MeasureSpec.EXACTLY));root.layout(0,0,width,844);
                assertEquals(56,navigation.getHeight());assertNoVisibleLabels(navigation);
                int previousWidth=0,firstCenterY=-1;
                for(int n=0;n<navigation.getMenu().size();n++) {
                    android.view.MenuItem item=navigation.getMenu().getItem(n);
                    View target=navigation.findViewById(item.getItemId()),icon=target.findViewById(R.id.navigationIcon);
                    assertTrue(target.getWidth()>=48);assertTrue(target.getHeight()>=48);
                    assertEquals(item.getTitle(),target.getContentDescription());assertEquals(item.getTitle(),target.getTooltipText());
                    if(previousWidth!=0)assertTrue(Math.abs(target.getWidth()-previousWidth)<=1);previousWidth=target.getWidth();
                    int[] targetPosition=new int[2],iconPosition=new int[2];target.getLocationOnScreen(targetPosition);icon.getLocationOnScreen(iconPosition);
                    assertEquals(targetPosition[0]+target.getWidth()/2f,iconPosition[0]+icon.getWidth()/2f,1f);
                    assertEquals(targetPosition[1]+target.getHeight()/2f,iconPosition[1]+icon.getHeight()/2f,1f);
                    int centerY=iconPosition[1]+icon.getHeight()/2;if(firstCenterY<0)firstCenterY=centerY;assertEquals(firstCenterY,centerY);
                    navigation.markSelectedItemId(item.getItemId());assertTrue(target.isSelected());assertTrue(item.isChecked());
                }
            }
        }
    }

    private static void assertNoVisibleLabels(View view) {
        if(view instanceof TextView)assertTrue("Navigation labels must not be visible.",view.getVisibility()!=View.VISIBLE);
        if(view instanceof android.view.ViewGroup)for(int n=0;n<((android.view.ViewGroup)view).getChildCount();n++)assertNoVisibleLabels(((android.view.ViewGroup)view).getChildAt(n));
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
