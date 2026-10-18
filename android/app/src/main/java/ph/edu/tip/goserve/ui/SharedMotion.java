package ph.edu.tip.goserve.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import ph.edu.tip.goserve.R;

final class SharedMotion {
    private SharedMotion() {}

    static void install(View root) {
        apply(root);
        root.getViewTreeObserver().addOnPreDrawListener(() -> {
            apply(root);
            return true;
        });
    }

    private static void apply(View view) {
        boolean motionEnabled = ValueAnimator.areAnimatorsEnabled();
        if (view.isClickable() && !(view instanceof EditText)) {
            Object applied = view.getTag(R.id.pressMotionEnabled);
            if (!Boolean.valueOf(motionEnabled).equals(applied)) {
                if (motionEnabled) {
                    StateListAnimator states = new StateListAnimator();
                    states.addState(new int[]{-android.R.attr.state_enabled}, scale(1f, 0));
                    states.addState(new int[]{android.R.attr.state_pressed}, scale(.97f, 80));
                    states.addState(new int[]{}, scale(1f, 120));
                    view.setStateListAnimator(states);
                } else {
                    view.setStateListAnimator(null);
                    view.setScaleX(1f);
                    view.setScaleY(1f);
                }
                view.setTag(R.id.pressMotionEnabled, motionEnabled);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) apply(group.getChildAt(index));
        }
    }

    private static AnimatorSet scale(float target, long duration) {
        AnimatorSet animation = new AnimatorSet();
        animation.playTogether(ObjectAnimator.ofFloat(null, View.SCALE_X, target),
                ObjectAnimator.ofFloat(null, View.SCALE_Y, target));
        animation.setDuration(duration);
        return animation;
    }
}
