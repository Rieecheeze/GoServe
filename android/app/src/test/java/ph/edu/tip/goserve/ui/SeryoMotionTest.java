package ph.edu.tip.goserve.ui;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.FrameLayout;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.ViewTreeLifecycleOwner;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowChoreographer;
import org.robolectric.util.ReflectionHelpers;
import ph.edu.tip.goserve.ui.common.SeryoMascotView;
import ph.edu.tip.goserve.R;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class SeryoMotionTest {
    @Before public void useFrameClock() {
        durationScale(1f);
        ShadowChoreographer.setPaused(true);
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16));
    }

    @After public void restoreFrameClock() {
        durationScale(1f);
        ShadowChoreographer.setPaused(false);
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(1));
    }

    @Test public void everyStateMovesAndLoadingBarAndThinkingDotsAdvance() {
        try (var controller = Robolectric.buildActivity(Activity.class).setup().visible()) {
            Activity activity = controller.get();
            SeryoMascotView mascot = install(activity);
            for (SeryoMascotView.State state : SeryoMascotView.State.values()) {
                mascot.setState(state);
                assertTrue(mascot.isRunning());
                ValueAnimator loop = animator(mascot);
                loop.setCurrentPlayTime(173);
                int before = frameHash(mascot);
                int[] statusBefore = state == SeryoMascotView.State.IDLE ? null : statusPixels(mascot);
                loop.setCurrentPlayTime(547);
                assertNotEquals(state + " needs visible transform progress.", before, frameHash(mascot));
                if (state != SeryoMascotView.State.IDLE) {
                    int[] statusAfter = statusPixels(mascot);
                    assertFalse(state + " status marks must animate too.", Arrays.equals(statusBefore, statusAfter));
                    int changed = 0;
                    for (int i = 0; i < statusBefore.length; i++) if (statusBefore[i] != statusAfter[i]) changed++;
                    assertTrue(state + " status strip needs changed pixels; actual=" + changed, changed > 0);
                }
                mascot.setState(state);
                assertSame("Setting the same state must not start another loop.", loop, animator(mascot));
            }
        }
    }

    @Test public void hidingStoppingDetachingAndLifecycleStopCancelTheirFrameCallbacks() {
        try (var controller = Robolectric.buildActivity(Activity.class).setup().visible()) {
            Activity activity = controller.get();
            Owner owner = new Owner();
            owner.lifecycle.setCurrentState(Lifecycle.State.STARTED);
            FrameLayout host = new FrameLayout(activity);
            ViewTreeLifecycleOwner.set(host, owner);
            SeryoMascotView mascot = new SeryoMascotView(activity);
            host.addView(mascot, new FrameLayout.LayoutParams(240, 240));
            activity.setContentView(host);
            showWindow(activity);
            mascot.layout(0, 0, 240, 240);
            ValueAnimator idleLoop = animator(mascot);
            assertTrue(mascot.isRunning());
            mascot.setState(SeryoMascotView.State.THINKING);
            assertCancelled(idleLoop);
            assertNotSame(idleLoop, animator(mascot));

            ValueAnimator thinkingLoop = animator(mascot);
            mascot.setHostActive(false);
            assertFalse(mascot.isRunning());
            assertCancelled(thinkingLoop);
            int stopped = frameHash(mascot);
            idle(416);
            assertEquals("Stopped motion cannot keep changing frames.", stopped, frameHash(mascot));
            mascot.setHostActive(true);
            assertTrue(mascot.isRunning());

            host.setVisibility(View.GONE);
            assertFalse(mascot.isRunning());
            host.setVisibility(View.VISIBLE);
            assertTrue(mascot.isRunning());
            setWindowVisibility(activity, View.INVISIBLE);
            assertFalse(mascot.isRunning());
            setWindowVisibility(activity, View.VISIBLE);
            assertTrue(mascot.isRunning());

            owner.lifecycle.setCurrentState(Lifecycle.State.CREATED);
            assertFalse("A visible stopped host must not animate.", mascot.isRunning());
            owner.lifecycle.setCurrentState(Lifecycle.State.STARTED);
            assertTrue(mascot.isRunning());
            host.removeView(mascot);
            assertFalse(mascot.isRunning());
            host.addView(mascot);
            assertTrue(mascot.isRunning());
            ValueAnimator resumed = animator(mascot);
            mascot.start();
            assertSame(resumed, animator(mascot));
            mascot.stop();
            assertCancelled(resumed);
            assertFalse(mascot.isRunning());
            mascot.start();
            assertTrue(mascot.isRunning());
        }
    }

    @Test public void reducedMotionKeepsEachPoseStaticAndLiveScaleChangesStopAndResume() {
        try (var controller = Robolectric.buildActivity(Activity.class).setup().visible()) {
            Activity activity = controller.get();
            SeryoMascotView mascot = install(activity);
            durationScale(0f);
            Set<Integer> poses = new HashSet<>();
            for (SeryoMascotView.State state : SeryoMascotView.State.values()) {
                mascot.setState(state);
                assertFalse(mascot.isRunning());
                int still = frameHash(mascot);
                poses.add(still);
                idle(416);
                assertEquals(still, frameHash(mascot));
            }
            assertEquals("Reduced motion must retain the three meaningful pose identities.", 3, poses.size());
            durationScale(1f);
            notifyDurationScale(activity);
            assertTrue("Re-enabling OS motion resumes an eligible visible mascot.", mascot.isRunning());
            ValueAnimator loop = animator(mascot);
            durationScale(0f);
            notifyDurationScale(activity);
            assertFalse(mascot.isRunning());
            assertCancelled(loop);
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, mascot.getImportantForAccessibility());
        }
    }

    private SeryoMascotView install(Activity activity) {
        FrameLayout host = new FrameLayout(activity);
        SeryoMascotView mascot = new SeryoMascotView(activity);
        host.addView(mascot, new FrameLayout.LayoutParams(240, 240));
        activity.setContentView(host);
        showWindow(activity);
        mascot.layout(0, 0, 240, 240);
        return mascot;
    }

    private ValueAnimator animator(SeryoMascotView mascot) {
        return ReflectionHelpers.getField(mascot, "animation");
    }

    private void assertCancelled(ValueAnimator animator) {
        assertNotNull(animator);
        assertFalse(animator.isRunning());
        List<?> listeners = ReflectionHelpers.getField(animator, "mUpdateListeners");
        assertTrue("Cancelled loops must release their view callback.", listeners == null || listeners.isEmpty());
    }

    @Test public void idleArtworkActuallyChangesAcrossFrameClockPositions() {
        try (var controller = Robolectric.buildActivity(Activity.class).setup().visible()) {
            Activity activity = controller.get();
            FrameLayout host = new FrameLayout(activity);
            SeryoMascotView mascot = new SeryoMascotView(activity, null);
            host.addView(mascot, new FrameLayout.LayoutParams(240, 240));
            activity.setContentView(host);
            showWindow(activity);
            mascot.layout(0, 0, 240, 240);
            idle(176);
            int first = frameHash(mascot);
            idle(416);
            assertNotEquals("An attached visible Seryo must visibly animate; swapping a static pose is insufficient.",
                    first, frameHash(mascot));
        }
    }

    private int frameHash(View view) {
        return frameHash(view, 0, 240);
    }

    private int frameHash(View view, int top, int height) {
        return Arrays.hashCode(framePixels(view, top, height));
    }

    private int[] framePixels(View view, int top, int height) {
        return framePixels(view, top, height, android.graphics.Color.TRANSPARENT);
    }

    private int[] statusPixels(SeryoMascotView view) {
        int background = ContextCompat.getColor(view.getContext(), view.getState() == SeryoMascotView.State.LOADING
                ? R.color.gs_primary : R.color.gs_background);
        int[] pixels = framePixels(view, 217, 23, background);
        boolean marks = false;
        for (int pixel : pixels) if (pixel != background) { marks = true; break; }
        assertTrue(view.getState() + " must render visible status marks against its theme background.", marks);
        return pixels;
    }

    private int[] framePixels(View view, int top, int height, int background) {
        Bitmap frame = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(frame);
            canvas.drawColor(background);
            view.draw(canvas);
            int[] pixels = new int[240 * height];
            frame.getPixels(pixels, 0, 240, 0, top, 240, height);
            if (top == 0 && height == 240) {
                for (int edge = 0; edge < 240; edge++) {
                    assertEquals("Motion must retain space at the top edge.", 0, pixels[edge] >>> 24);
                    assertEquals("Motion must retain space at the bottom edge.", 0, pixels[239 * 240 + edge] >>> 24);
                    assertEquals("Motion must retain space at the left edge.", 0, pixels[edge * 240] >>> 24);
                    assertEquals("Motion must retain space at the right edge.", 0, pixels[edge * 240 + 239] >>> 24);
                }
                boolean visible = false;
                for (int pixel : pixels) if ((pixel >>> 24) != 0) { visible = true; break; }
                assertTrue("The actual transparent mascot artwork must be rendered.", visible);
            }
            return pixels;
        } finally { frame.recycle(); }
    }

    private void showWindow(Activity activity) {
        idle(32);
        setWindowVisibility(activity, View.VISIBLE);
    }

    private void setWindowVisibility(Activity activity, int visibility) {
        View decor = activity.getWindow().getDecorView();
        Object attachInfo = ReflectionHelpers.getField(decor, "mAttachInfo");
        ReflectionHelpers.setField(attachInfo, "mWindowVisibility", visibility);
        decor.dispatchWindowVisibilityChanged(visibility);
    }

    private void durationScale(float scale) {
        ReflectionHelpers.callStaticMethod(ValueAnimator.class, "setDurationScale",
                ReflectionHelpers.ClassParameter.from(float.class, scale));
    }

    private void notifyDurationScale(Activity activity) {
        activity.getContentResolver().notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null);
        idle(32);
    }

    private static final class Owner implements LifecycleOwner {
        final LifecycleRegistry lifecycle = new LifecycleRegistry(this);
        @Override public Lifecycle getLifecycle() { return lifecycle; }
    }

    private void idle(long milliseconds) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(milliseconds));
    }
}
