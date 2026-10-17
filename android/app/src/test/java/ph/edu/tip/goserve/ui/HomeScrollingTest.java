package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
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
@Config(sdk = 34, application = GoServeApplication.class, qualifiers = "w390dp-h700dp-port-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public final class HomeScrollingTest {
    private ActivityController<MainActivity> controller;

    @Before public void resetFactory() {
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class, "_instance", null);
    }

    @After public void closeActivity() {
        if (controller != null) controller.close();
    }

    @Test public void homeScrollReachesEveryOfficeAboveBottomNavigation() {
        checkAllOfficesReachable(390, 700, 1f);
    }

    @Test @Config(qualifiers = "w320dp-h550dp-port-mdpi")
    public void compactHomeWithLargeTextReachesTheLastOffice() {
        checkAllOfficesReachable(320, 550, 2f);
    }

    private void checkAllOfficesReachable(int width, int height, float fontScale) {
        GoServeApplication app = (GoServeApplication) RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(app.getResources().getConfiguration());
        configuration.fontScale = fontScale;
        app.getResources().updateConfiguration(configuration, app.getResources().getDisplayMetrics());
        controller = Robolectric.buildActivity(MainActivity.class,
                new Intent(app, MainActivity.class).putExtra(MainActivity.EXTRA_PREVIEW, true)).setup().visible();
        MainActivity activity = controller.get();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        View root = activity.findViewById(R.id.activityRoot);
        layout(root, width, height);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        layout(root, width, height);
        RecyclerView offices = root.findViewById(R.id.officesRecycler);
        assertNotNull(offices);
        assertEquals("Home must keep all five offices.", 5, offices.getAdapter().getItemCount());
        NestedScrollView scroll = findScroll(root);
        assertNotNull(scroll);
        assertTrue("The page must scroll when its offices overflow the viewport.", scroll.canScrollVertically(1));
        scroll.setSmoothScrollingEnabled(false);
        scroll.fullScroll(View.FOCUS_DOWN);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue("Scrolling must move the page.", scroll.getScrollY() > 0);
        View last = offices.getLayoutManager().findViewByPosition(4);
        assertNotNull("The last office must be laid out in the scroll content.", last);
        assertEquals("College of Computer Studies", ((android.widget.TextView) last.findViewById(R.id.officeName)).getText().toString());
        Rect visible = new Rect();
        assertTrue("The last office must be visible after scrolling down.", last.getGlobalVisibleRect(visible));
        View name = last.findViewById(R.id.officeName);
        assertTrue(name.getGlobalVisibleRect(visible));
        assertEquals("The final office name must be fully readable.", name.getHeight(), visible.height());
        int[] navigationPosition = new int[2];
        root.findViewById(R.id.bottomNavigation).getLocationOnScreen(navigationPosition);
        assertTrue("The last office must finish above the bottom navigation.", visible.bottom <= navigationPosition[1]);
        View category = last.findViewById(R.id.officeCategory);
        assertTrue(category.getGlobalVisibleRect(visible));
        assertEquals("The final office category must be fully readable.", category.getHeight(), visible.height());
        assertTrue(visible.bottom <= navigationPosition[1]);
        scroll.scrollTo(0, Math.max(0, offices.getTop() - 30));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        View first = offices.getLayoutManager().findViewByPosition(0);
        Rect target = new Rect();
        assertTrue(first.getGlobalVisibleRect(target));
        int[] rootPosition = new int[2];
        root.getLocationOnScreen(rootPosition);
        float x = target.centerX() - rootPosition[0];
        float y = target.bottom - rootPosition[1] - 12;
        int beforeSwipe = scroll.getScrollY();
        long started = SystemClock.uptimeMillis();
        sendTouch(root, started, started, MotionEvent.ACTION_DOWN, x, y);
        for (int i = 1; i <= 8; i++) {
            sendTouch(root, started, started + i * 20, MotionEvent.ACTION_MOVE, x, y - i * 15);
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20));
        }
        sendTouch(root, started, started + 180, MotionEvent.ACTION_UP, x, y - 120);
        assertTrue("Swiping over an office must scroll the page, not trap the gesture inside the grid.", scroll.getScrollY() > beforeSwipe);
    }

    private void sendTouch(View root, long started, long now, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(started, now, action, x, y, 0);
        try { root.dispatchTouchEvent(event); } finally { event.recycle(); }
    }

    private void layout(View root, int width, int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }

    private NestedScrollView findScroll(View view) {
        if (view instanceof NestedScrollView) return (NestedScrollView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                NestedScrollView scroll = findScroll(group.getChildAt(i));
                if (scroll != null) return scroll;
            }
        }
        return null;
    }
}
