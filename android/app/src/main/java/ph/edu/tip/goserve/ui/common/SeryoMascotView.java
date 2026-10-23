package ph.edu.tip.goserve.ui.common;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.View;
import android.view.animation.LinearInterpolator;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewTreeLifecycleOwner;
import ph.edu.tip.goserve.R;

/** Decorative pose animation; its host supplies the real loading or processing status text. */
public final class SeryoMascotView extends View implements Animatable {
    public enum State { LOADING, IDLE, THINKING }

    private static final LruCache<Integer, Bitmap> POSES = new LruCache<Integer, Bitmap>(6 * 1024 * 1024) {
        @Override protected int sizeOf(Integer key, Bitmap value) { return value.getAllocationByteCount(); }
    };
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF artwork = new RectF();
    private final float density;
    private final boolean compactArtwork;
    private final ContentObserver durationObserver;
    private final LifecycleEventObserver lifecycleObserver = (owner, event) -> updateMotion();
    private LifecycleOwner lifecycleOwner;
    private ValueAnimator animation;
    private Bitmap pose;
    private State state = State.IDLE;
    private float phase;
    private int decodedKey = -1;
    private boolean initialized;
    private boolean hostActive = true;
    private boolean motionRequested = true;

    public SeryoMascotView(Context context) { this(context, null); }

    public SeryoMascotView(Context context, AttributeSet attributes) {
        super(context, attributes);
        density = getResources().getDisplayMetrics().density;
        TypedArray values = context.obtainStyledAttributes(attributes, R.styleable.SeryoMascotView);
        int initial = values.getInt(R.styleable.SeryoMascotView_seryoState, State.IDLE.ordinal());
        compactArtwork = values.getBoolean(R.styleable.SeryoMascotView_seryoCompact, false);
        values.recycle();
        state = State.values()[Math.max(0, Math.min(State.values().length - 1, initial))];
        durationObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange) { updateMotion(); }
        };
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(false);
        initialized = true;
        loadPose();
    }

    public State getState() { return state; }

    public void setState(State next) {
        if (next == null) throw new IllegalArgumentException("Seryo needs a state.");
        if (state != next) {
            cancelMotion();
            state = next;
            loadPose();
            invalidate();
        }
        updateMotion();
    }

    public void setHostActive(boolean active) {
        hostActive = active;
        updateMotion();
    }

    @Override public void start() { motionRequested = true; updateMotion(); }
    @Override public void stop() { motionRequested = false; cancelMotion(); }
    @Override public boolean isRunning() { return animation != null && animation.isRunning(); }

    private void updateMotion() {
        if (!initialized) return;
        boolean lifecycleActive = lifecycleOwner == null
                || lifecycleOwner.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
        if (!motionRequested || !hostActive || !lifecycleActive || !isAttachedToWindow() || !isShown()
                || getWindowVisibility() != VISIBLE || !ValueAnimator.areAnimatorsEnabled()) {
            cancelMotion();
            return;
        }
        if (animation != null) return;
        animation = ValueAnimator.ofFloat(0f, 1f);
        int duration = state == State.LOADING ? R.integer.seryo_loading_ms
                : state == State.THINKING ? R.integer.seryo_thinking_ms : R.integer.seryo_idle_ms;
        animation.setDuration(getResources().getInteger(duration));
        animation.setRepeatCount(ValueAnimator.INFINITE);
        animation.setInterpolator(new LinearInterpolator());
        animation.addUpdateListener(frame -> {
            if (!ValueAnimator.areAnimatorsEnabled()) { cancelMotion(); return; }
            phase = frame.getAnimatedFraction();
            invalidate();
        });
        animation.start();
    }

    private void cancelMotion() {
        if (animation != null) {
            animation.removeAllUpdateListeners();
            animation.cancel();
            animation = null;
        }
        if (phase != 0f) { phase = 0f; invalidate(); }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lifecycleOwner = ViewTreeLifecycleOwner.get(this);
        if (lifecycleOwner != null) lifecycleOwner.getLifecycle().addObserver(lifecycleObserver);
        getContext().getApplicationContext().getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, durationObserver);
        updateMotion();
    }

    @Override protected void onDetachedFromWindow() {
        cancelMotion();
        getContext().getApplicationContext().getContentResolver().unregisterContentObserver(durationObserver);
        if (lifecycleOwner != null) lifecycleOwner.getLifecycle().removeObserver(lifecycleObserver);
        lifecycleOwner = null;
        super.onDetachedFromWindow();
    }

    @Override protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        updateMotion();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateMotion();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        loadPose();
    }

    private void loadPose() {
        int resource = state == State.LOADING ? R.drawable.seryo_greeting
                : state == State.THINKING ? R.drawable.seryo_thinking : R.drawable.seryo_idle;
        int target = Math.max(128, Math.min(640, compactArtwork ? Math.round(64 * density) : Math.max(getWidth(), getHeight())));
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(getResources(), resource, options);
        int sample = 1;
        while (Math.max(options.outWidth, options.outHeight) / (sample * 2) >= target) sample *= 2;
        // The supplied 1254 px poses never decode above 627 px, including large-screen loaders.
        sample = Math.max(2, sample);
        int key = resource * 31 + sample;
        if (decodedKey == key && pose != null) return;
        Bitmap cached = POSES.get(key);
        if (cached == null) {
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            options.inScaled = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            cached = BitmapFactory.decodeResource(getContext().getApplicationContext().getResources(), resource, options);
            if (cached != null) POSES.put(key, cached);
        }
        pose = cached;
        decodedKey = key;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (pose == null) return;
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        if (width <= 0 || height <= 0) return;
        float statusHeight = state == State.IDLE ? 0 : Math.min(28 * density, height * .15f);
        float size = compactArtwork ? 64 * density : Math.min(width, height - statusHeight);
        float cx = getPaddingLeft() + width * .5f;
        float cy = getPaddingTop() + (compactArtwork ? 23 * density : (height - statusHeight) * .5f);
        double angle = phase * Math.PI * 2;
        float sine = (float) Math.sin(angle);
        float breath = (1f - (float) Math.cos(angle)) * .5f;
        float amplitude = state == State.LOADING ? 8f : state == State.THINKING ? 3f : 1.5f;
        float bob = -Math.min(amplitude * density, size * .035f) * sine;
        float tilt = state == State.LOADING ? 2f * sine : state == State.THINKING ? 3f * sine : 0f;
        float scale = state == State.IDLE ? 1f + .018f * breath : 1f;
        float fit = (compactArtwork ? 1f : .88f) * size / Math.max(pose.getWidth(), pose.getHeight());
        float halfWidth = pose.getWidth() * fit * .5f;
        float halfHeight = pose.getHeight() * fit * .5f;
        artwork.set(-halfWidth, -halfHeight, halfWidth, halfHeight);
        int save = canvas.save();
        canvas.clipRect(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        canvas.translate(cx, cy + bob);
        canvas.rotate(tilt);
        canvas.scale(scale, scale);
        paint.setAlpha(255);
        canvas.drawBitmap(pose, null, artwork, paint);
        canvas.restoreToCount(save);

        paint.setColor(ContextCompat.getColor(getContext(), state == State.LOADING
                ? R.color.gs_on_primary : R.color.gs_text));
        float statusY = getPaddingTop() + height - statusHeight * .5f;
        if (state == State.LOADING) {
            float barWidth = size * .52f;
            float radius = Math.min(2 * density, statusHeight * .1f);
            paint.setAlpha(55);
            canvas.drawRoundRect(cx - barWidth * .5f, statusY - radius,
                    cx + barWidth * .5f, statusY + radius, radius, radius, paint);
            float segment = barWidth * .28f;
            float left = cx - barWidth * .5f + (barWidth - segment) * breath;
            paint.setAlpha(255);
            canvas.drawRoundRect(left, statusY - radius, left + segment, statusY + radius, radius, radius, paint);
        } else if (state == State.THINKING) {
            float radius = Math.min(2.5f * density, size * .035f);
            float gap = radius * 3.5f;
            for (int i = 0; i < 3; i++) {
                float pulse = (1f + (float) Math.sin(angle - i * Math.PI * 2 / 3)) * .5f;
                paint.setAlpha(80 + Math.round(175 * pulse));
                canvas.drawCircle(cx + (i - 1) * gap, statusY, radius, paint);
            }
        }
        paint.setAlpha(255);
    }
}
