package ph.edu.tip.goserve.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Looper;
import android.text.TextPaint;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.core.content.res.ResourcesCompat;
import androidx.lifecycle.ViewModelProvider;
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
public final class TypographyFontTest {
    private static final String SAMPLE = "HamburgefontsivWIM";

    @Before public void useCurrentApplication() {
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class, "_instance", null);
    }

    @Test public void liveHomeHeadlineUsesMontserratGlyphMetrics() {
        Intent intent = new Intent(RuntimeEnvironment.getApplication(), MainActivity.class)
                .putExtra(MainActivity.EXTRA_PREVIEW, true);
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class, intent).setup().visible()) {
            MainActivity activity = controller.get();
            activity.getSupportFragmentManager().executePendingTransactions();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            TextView headline = activity.findViewById(R.id.heroTitle);
            assertNotNull(headline);
            assertMontserratGlyphMetrics(headline, "live home headline");
        }
    }

    @Test public void plainInflaterHeadlineUsesMontserratGlyphMetrics() {
        Context context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_GoServe);
        View root = LayoutInflater.from(context).inflate(R.layout.fragment_sign_in, null, false);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertMontserratGlyphMetrics(root.findViewById(R.id.title), "plain sign-in headline");
    }

    private void assertMontserratGlyphMetrics(TextView headline, String label) {
        Typeface montserrat = ResourcesCompat.getFont(headline.getContext(), R.font.montserrat_extra_bold);
        Typeface karla = ResourcesCompat.getFont(headline.getContext(), R.font.karla_regular);
        assertNotNull(montserrat);
        assertNotNull(karla);
        TextPaint expected = new TextPaint(headline.getPaint());
        expected.setTypeface(montserrat);
        TextPaint regularKarla = new TextPaint(headline.getPaint());
        regularKarla.setTypeface(karla);
        TextPaint boldKarla = new TextPaint(headline.getPaint());
        boldKarla.setTypeface(Typeface.create(karla, Typeface.BOLD));
        float actualWidth = headline.getPaint().measureText(SAMPLE);
        float montserratWidth = expected.measureText(SAMPLE);
        float karlaWidth = regularKarla.measureText(SAMPLE);
        float boldKarlaWidth = boldKarla.measureText(SAMPLE);
        String diagnostic = label + " class=" + headline.getClass().getSimpleName()
                + " actual=" + actualWidth + " Montserrat800=" + montserratWidth
                + " Karla400=" + karlaWidth + " KarlaBold=" + boldKarlaWidth;
        System.out.println(diagnostic);
        assertTrue("Native metrics must distinguish the resource fonts: " + diagnostic,
                Math.abs(montserratWidth - karlaWidth) > 1f);
        assertEquals("The heading must use Montserrat glyphs: " + diagnostic,
                montserratWidth, actualWidth, 0.05f);
    }
}
