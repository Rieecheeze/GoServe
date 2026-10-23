package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.ui.common.SeryoMascotView;

/** Debug source set only: records the production renderer without starting product work. */
public final class MascotPreviewActivity extends BaseActivity {
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(ContextCompat.getColor(this, R.color.gs_background));
        LinearLayout content = column();
        content.setPadding(dp(16), dp(16), dp(16), dp(16));
        scroll.addView(content);
        content.addView(label(R.string.mascot_preview_title, false));
        content.addView(label(R.string.mascot_preview_notice, false));
        LinearLayout loading = section(SeryoMascotView.State.LOADING, R.string.mascot_preview_loading, 220, 248);
        loading.setBackgroundColor(ContextCompat.getColor(this, R.color.gs_primary));
        content.addView(loading, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout row = new LinearLayout(this);
        row.addView(section(SeryoMascotView.State.IDLE, R.string.mascot_preview_idle, 128, 156),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(section(SeryoMascotView.State.THINKING, R.string.mascot_preview_thinking, 128, 156),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        content.addView(row);
        setContentView(scroll);
        applyInsets(scroll);
    }

    private LinearLayout section(SeryoMascotView.State state, int label, int width, int height) {
        LinearLayout section = column();
        section.setGravity(Gravity.CENTER_HORIZONTAL);
        section.setPadding(0, dp(8), 0, dp(8));
        section.addView(label(label, state == SeryoMascotView.State.LOADING));
        SeryoMascotView mascot = new SeryoMascotView(this);
        mascot.setState(state);
        section.addView(mascot, new LinearLayout.LayoutParams(dp(width), dp(height)));
        return section;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView label(int text, boolean onPrimary) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(16);
        label.setTextColor(ContextCompat.getColor(this, onPrimary ? R.color.gs_on_primary : R.color.gs_text));
        label.setPadding(0, dp(8), 0, dp(8));
        label.setGravity(Gravity.CENTER);
        return label;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
