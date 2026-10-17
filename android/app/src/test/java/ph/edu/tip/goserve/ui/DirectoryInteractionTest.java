package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
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
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "w390dp-h844dp-port-notnight-mdpi", application = GoServeApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class DirectoryInteractionTest {
    private final List<ActivityController<MainActivity>> activities = new ArrayList<>();

    @Before public void useCurrentApplication() {
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class, "_instance", null);
    }

    @After public void closeActivities() {
        for (ActivityController<MainActivity> controller : activities) controller.close();
        activities.clear();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void clickingDocumentsUpdatesBothSelectedAndPreviousFilterColors() {
        for (boolean night : new boolean[]{false, true}) {
            MainActivity activity = launch("All services", night);
            MaterialButton all = activity.findViewById(R.id.allServicesButton);
            MaterialButton documents = activity.findViewById(R.id.documentsButton);
            assertTrue(documents.performClick());
            assertFilterColors(activity, documents, true);
            assertFilterColors(activity, all, false);
            assertEquals(1, offices(activity).getAdapter().getItemCount());
            assertTrue(all.performClick());
            assertFilterColors(activity, all, true);
            assertFilterColors(activity, documents, false);
            assertEquals(5, offices(activity).getAdapter().getItemCount());
        }
    }

    @Test public void documentsDestinationStartsWithDocumentsSelected() {
        for (boolean night : new boolean[]{false, true}) {
            MainActivity activity = launch("Documents", night);
            assertFilterColors(activity, activity.findViewById(R.id.documentsButton), true);
            assertFilterColors(activity, activity.findViewById(R.id.allServicesButton), false);
            assertEquals(1, offices(activity).getAdapter().getItemCount());
        }
    }

    @Test public void paymentsFilterIncludesCashieringAndExcludesAccounting() {
        for (boolean night : new boolean[]{false, true}) {
            MainActivity activity = launch("All services", night);
            assertTrue(activity.findViewById(R.id.paymentsButton).performClick());
            layout(activity);
            RecyclerView recycler = offices(activity);
            assertEquals("Payments must contain only Cashiering", 1, recycler.getAdapter().getItemCount());
            RecyclerView.ViewHolder row = recycler.findViewHolderForAdapterPosition(0);
            assertNotNull(row);
            assertEquals("Cashiering", ((TextView) row.itemView.findViewById(R.id.officeName)).getText().toString());
            assertFilterColors(activity, activity.findViewById(R.id.paymentsButton), true);
            assertFilterColors(activity, activity.findViewById(R.id.allServicesButton), false);
        }
    }

    @Test public void lastOfficeTextCanScrollFullyAboveBottomNavigation() {
        for (boolean night : new boolean[]{false, true}) {
            for (float fontScale : new float[]{1f, 2f}) {
            MainActivity activity = launch("All services", night, fontScale == 1f ? 390 : 320, fontScale);
            RecyclerView recycler = offices(activity);
            assertEquals(5, recycler.getAdapter().getItemCount());
            View fragment = activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer).requireView();
            NestedScrollView scroll = findScrollView(fragment);
            assertNotNull(scroll);
            assertTrue("The populated directory must provide vertical scroll", scroll.canScrollVertically(1));
            scroll.setSmoothScrollingEnabled(false);
            scroll.fullScroll(View.FOCUS_DOWN);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            RecyclerView.ViewHolder last = recycler.findViewHolderForAdapterPosition(4);
            assertNotNull("The final office must be reachable by directory scrolling", last);
            assertTextReachableAboveNavigation(activity, last.itemView.findViewById(R.id.officeName));
            assertTextReachableAboveNavigation(activity, last.itemView.findViewById(R.id.officeCategory));
            }
        }
    }

    private MainActivity launch(String category, boolean night) {
        return launch(category, night, 390, 1f);
    }

    private MainActivity launch(String category, boolean night, int width, float fontScale) {
        for (ActivityController<MainActivity> controller : activities) controller.close();
        activities.clear();
        RuntimeEnvironment.setQualifiers("w" + width + "dp-h844dp-port-" + (night ? "night" : "notnight") + "-mdpi");
        RuntimeEnvironment.setFontScale(fontScale);
        AppCompatDelegate.setDefaultNightMode(night ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), MainActivity.class)
                .putExtra(MainActivity.EXTRA_PREVIEW, true);
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class, intent).setup().visible();
        activities.add(controller);
        MainActivity activity = controller.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        activity.navigate(OfficesFragment.create(category), true);
        activity.getSupportFragmentManager().executePendingTransactions();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout(activity);
        return activity;
    }

    private void layout(MainActivity activity) {
        View root = activity.findViewById(R.id.activityRoot);
        int width = activity.getResources().getConfiguration().screenWidthDp;
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(844, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, 844);
    }

    private RecyclerView offices(MainActivity activity) {
        return activity.findViewById(R.id.officesRecycler);
    }

    private void assertTextReachableAboveNavigation(MainActivity activity, TextView text) {
        Rect requested = new Rect(0, 0, text.getWidth(), text.getHeight());
        text.requestRectangleOnScreen(requested, true);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        Rect visible = new Rect();
        assertTrue("The final office text must be reachable", text.getGlobalVisibleRect(visible));
        assertEquals("The final office text must be fully visible", text.getHeight(), visible.height());
        int[] navigationPosition = new int[2];
        activity.findViewById(R.id.bottomNavigation).getLocationOnScreen(navigationPosition);
        assertTrue("The final office text must remain above bottom navigation", visible.bottom <= navigationPosition[1]);
    }

    private void assertFilterColors(MainActivity activity, MaterialButton button, boolean selected) {
        assertEquals(selected, button.isSelected());
        int background = ContextCompat.getColor(activity, selected ? R.color.gs_primary : R.color.gs_surface);
        int text = ContextCompat.getColor(activity, selected ? R.color.gs_on_primary : R.color.gs_text);
        assertEquals("Filter background must follow its selection state", background,
                button.getBackgroundTintList().getColorForState(button.getDrawableState(), 0));
        assertEquals("Filter text must follow its selection state", text, button.getCurrentTextColor());
    }

    private NestedScrollView findScrollView(View view) {
        if (view instanceof NestedScrollView) return (NestedScrollView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                NestedScrollView result = findScrollView(group.getChildAt(index));
                if (result != null) return result;
            }
        }
        return null;
    }
}
