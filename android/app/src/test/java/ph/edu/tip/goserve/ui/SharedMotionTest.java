package ph.edu.tip.goserve.ui;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.drawable.Animatable;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import com.google.android.material.button.MaterialButton;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.shadows.ShadowChoreographer;
import ph.edu.tip.goserve.R;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@LooperMode(LooperMode.Mode.PAUSED)
public final class SharedMotionTest {
    @Before public void useManualAnimationClock() {
        ShadowChoreographer.setPaused(true);
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16));
    }
    @After public void restoreAnimationClock() {
        ShadowChoreographer.setPaused(false);
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(1));
        durationScale(1f);
    }
    @Test public void sharedButtonPressReleasesAndKeepsNativeActivationAndRipple() {
        try (var controller = Robolectric.buildActivity(MotionActivity.class).create()) {
            MotionActivity activity = controller.get();
            activity.setTheme(R.style.Theme_GoServe);
            View root = LayoutInflater.from(activity).inflate(R.layout.fragment_sign_in, null);
            activity.setContentView(root);
            activity.applyInsets(root);
            controller.start().resume().visible();
            showWindow(activity);
            MaterialButton button = root.findViewById(R.id.googleButton);
            AtomicInteger clicks = new AtomicInteger();
            button.setOnClickListener(v -> clicks.incrementAndGet());
            assertNotNull(button.getRippleColor());
            button.setPressed(true);
            idle(96);
            assertEquals(.97f, button.getScaleX(), .002f);
            assertEquals(.97f, button.getScaleY(), .002f);
            button.setPressed(false);
            idle(144);
            assertEquals(1f, button.getScaleX(), .002f);
            button.performClick();
            button.requestFocus();
            button.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            button.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            idle(144);
            assertEquals(2, clicks.get());
        }
    }

    @Test public void cancellationRestoresScaleAndDisabledButtonDoesNotShrink() {
        try (var controller = Robolectric.buildActivity(MotionActivity.class).create()) {
            MotionActivity activity = controller.get();
            activity.setTheme(R.style.Theme_GoServe);
            View root = LayoutInflater.from(activity).inflate(R.layout.fragment_sign_in, null);
            activity.setContentView(root);
            activity.applyInsets(root);
            controller.start().resume().visible();
            showWindow(activity);
            View button = root.findViewById(R.id.googleButton);
            button.layout(0, 0, 240, 56);
            button.setPressed(true);
            idle(96);
            assertEquals(.97f, button.getScaleX(), .002f);
            button.dispatchTouchEvent(MotionEvent.obtain(0, 100, MotionEvent.ACTION_CANCEL, 20, 20, 0));
            idle(144);
            assertEquals(1f, button.getScaleX(), .002f);
            button.setEnabled(false);
            button.setPressed(true);
            idle(96);
            assertEquals(1f, button.getScaleX(), .002f);
        }
    }

    @Test public void loadingRunsOnlyWhileVisibleAndAttached() {
        try (var controller = Robolectric.buildActivity(MotionActivity.class).create()) {
            MotionActivity activity = controller.get();
            activity.setTheme(R.style.Theme_GoServe);
            FrameLayout host = new FrameLayout(activity);
            activity.setContentView(host);
            View root = LayoutInflater.from(activity).inflate(R.layout.fragment_offices, host, false);
            host.addView(root);
            controller.start().resume().visible();
            showWindow(activity);
            idle(16);
            View progress = root.findViewById(R.id.progress);
            assertTrue(progress.isAttachedToWindow());
            assertTrue("Shared loading must own cancellable animation.", progress instanceof Animatable);
            Animatable animation = (Animatable) progress;
            assertFalse(animation.isRunning());
            progress.setVisibility(View.VISIBLE);
            assertTrue("Loading: attached=" + progress.isAttachedToWindow() + ", shown=" + progress.isShown()
                    + ", window=" + progress.getWindowVisibility() + ", enabled=" + ValueAnimator.areAnimatorsEnabled(), animation.isRunning());
            assertEquals("Loading campus offices", progress.getContentDescription());
            progress.setVisibility(View.GONE);
            assertFalse("Completion must stop immediately.", animation.isRunning());
            progress.setVisibility(View.VISIBLE);
            host.removeView(root);
            assertFalse("Detached loading must not retain a frame callback.", animation.isRunning());
            host.addView(root);
            assertTrue(animation.isRunning());
            root.setVisibility(View.GONE);
            assertFalse("Hidden ancestors must stop loading motion.", animation.isRunning());
        }
    }

    @Test public void zeroSystemAnimationScaleDisablesPressAndLoadingMotion() {
        durationScale(0f);
        try (var controller = Robolectric.buildActivity(MotionActivity.class).create()) {
            MotionActivity activity = controller.get();
            activity.setTheme(R.style.Theme_GoServe);
            View root = LayoutInflater.from(activity).inflate(R.layout.fragment_offices, null);
            activity.setContentView(root);
            activity.applyInsets(root);
            controller.start().resume().visible();
            showWindow(activity);
            View progress = root.findViewById(R.id.progress);
            assertTrue(progress instanceof Animatable);
            progress.setVisibility(View.VISIBLE);
            assertFalse(((Animatable) progress).isRunning());
            View button = root.findViewById(R.id.allServicesButton);
            button.setPressed(true);
            idle(96);
            assertEquals(1f, button.getScaleX(), .002f);
        } finally { durationScale(1f); }
    }

    public static class MotionActivity extends BaseActivity {}

    private void showWindow(Activity activity) {
        idle(32);
        // ActivityController attaches the decor but leaves API 34's synthetic window hidden.
        View decor = activity.getWindow().getDecorView();
        Object attachInfo = ReflectionHelpers.getField(decor, "mAttachInfo");
        ReflectionHelpers.setField(attachInfo, "mWindowVisibility", View.VISIBLE);
        decor.dispatchWindowVisibilityChanged(View.VISIBLE);
    }

    private void idle(long milliseconds) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(milliseconds));
    }
    private void durationScale(float scale) {
        ReflectionHelpers.callStaticMethod(ValueAnimator.class, "setDurationScale",
                ReflectionHelpers.ClassParameter.from(float.class, scale));
    }
}
