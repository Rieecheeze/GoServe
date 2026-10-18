package ph.edu.tip.goserve.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.LinearInterpolator;
import androidx.core.content.ContextCompat;
import ph.edu.tip.goserve.R;

public final class BrandLoadingView extends View implements Animatable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable logo;
    private ValueAnimator animation;
    private float phase;

    public BrandLoadingView(Context context, AttributeSet attributes) {
        super(context, attributes);
        logo = ContextCompat.getDrawable(context, R.drawable.goserve_logo);
        paint.setColor(ContextCompat.getColor(context, R.color.gs_text));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        if (getContentDescription() == null) setContentDescription(context.getString(R.string.loading_work));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        float left = (getWidth() - size) / 2f;
        float top = (getHeight() - size) / 2f;
        int inset = Math.round(size * .23f);
        logo.setBounds(Math.round(left) + inset, Math.round(top) + inset,
                Math.round(left + size) - inset, Math.round(top + size) - inset);
        logo.draw(canvas);
        paint.setStrokeWidth(Math.max(2f, size * .055f));
        float edge = paint.getStrokeWidth();
        paint.setAlpha(45);
        canvas.drawArc(left + edge, top + edge, left + size - edge, top + size - edge, 0, 360, false, paint);
        paint.setAlpha(255);
        canvas.drawArc(left + edge, top + edge, left + size - edge, top + size - edge, phase * 360 - 90, 90, false, paint);
    }

    @Override public void start() {
        if (isRunning() || !isAttachedToWindow() || !isShown() || getWindowVisibility() != VISIBLE
                || !ValueAnimator.areAnimatorsEnabled()) return;
        animation = ValueAnimator.ofFloat(0f, 1f);
        animation.setDuration(1000);
        animation.setRepeatCount(ValueAnimator.INFINITE);
        animation.setInterpolator(new LinearInterpolator());
        animation.addUpdateListener(value -> {
            if (!ValueAnimator.areAnimatorsEnabled()) { stop(); return; }
            phase = (float) value.getAnimatedValue();
            invalidate();
        });
        animation.start();
    }

    @Override public void stop() {
        if (animation != null) {
            animation.cancel();
            animation.removeAllUpdateListeners();
            animation = null;
        }
        phase = 0;
        invalidate();
    }
    @Override public boolean isRunning() { return animation != null && animation.isRunning(); }
    private void updateAnimation() { if (isShown() && getWindowVisibility() == VISIBLE) start(); else stop(); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation(); }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
    @Override protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        updateAnimation();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateAnimation();
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.ProgressBar");
    }
}
