package ph.edu.tip.goserve.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.core.widget.NestedScrollView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.R;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "mdpi", application = android.app.Application.class)
public final class LayoutResponsivenessTest {
    @Test public void authAndContentReflowAcrossPhoneWidthsAndTextScales() {
        int[] layouts = {R.layout.fragment_sign_in, R.layout.fragment_office_login, R.layout.fragment_consent,
                R.layout.fragment_home, R.layout.fragment_offices, R.layout.fragment_office_detail, R.layout.fragment_more,
                R.layout.fragment_assistant, R.layout.fragment_request_list, R.layout.fragment_request_detail, R.layout.fragment_verified_file};
        for (int width : new int[]{320, 360, 390, 412, 480}) {
            for (float fontScale : new float[]{1f, 1.3f, 2f}) {
                RuntimeEnvironment.setQualifiers("w" + width + "dp-h700dp-port-mdpi");
                Context app = RuntimeEnvironment.getApplication();
                Configuration configuration = new Configuration(app.getResources().getConfiguration());
                configuration.fontScale = fontScale;
                Context context = new ContextThemeWrapper(app.createConfigurationContext(configuration), R.style.Theme_GoServe);
                for (int layout : layouts) {
                    View root = LayoutInflater.from(context).inflate(layout, null, false);
                    View active = root.findViewById(R.id.activeRequestCard);
                    if (active != null) active.setVisibility(View.VISIBLE);
                    TextView requestTitle = root.findViewById(R.id.requestTitle);
                    if (requestTitle != null) requestTitle.setText("Certificate of enrollment");
                    TextView requestStatus = root.findViewById(R.id.requestStatus);
                    if (requestStatus != null) requestStatus.setText("Processing");
                    View error = root.findViewById(R.id.errorText);
                    if (error instanceof TextView) { ((TextView) error).setText("Check your connection and retry."); error.setVisibility(View.VISIBLE); }
                    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY));
                    root.layout(0, 0, width, 700);
                    check(root, width, "layout=" + context.getResources().getResourceEntryName(layout) + " width=" + width + " font=" + fontScale);
                }
            }
        }
    }

    private void check(View view, int viewportWidth, String scenario) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (!(view.getParent() instanceof android.widget.HorizontalScrollView)) {
            assertTrue(scenario + " horizontal overflow: " + id(view), view.getMeasuredWidth() <= viewportWidth);
        }
        if (view instanceof TextView && !(view instanceof android.widget.EditText)) {
            TextView text = (TextView) view;
            if (text.getLayout() != null && text.getText().length() > 0) {
                int contentHeight = text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom();
                assertTrue(scenario + " clipped text: " + id(view), text.getLayout().getHeight() <= contentHeight + 2);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int available = view instanceof NestedScrollView ? viewportWidth : Math.min(view.getMeasuredWidth(), viewportWidth);
            for (int index = 0; index < group.getChildCount(); index++) check(group.getChildAt(index), available, scenario);
        }
    }
    private String id(View view) { return view.getId() == View.NO_ID ? view.getClass().getSimpleName() : view.getResources().getResourceEntryName(view.getId()); }
}
