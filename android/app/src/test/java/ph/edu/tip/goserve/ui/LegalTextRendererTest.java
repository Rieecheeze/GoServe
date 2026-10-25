package ph.edu.tip.goserve.ui;

import android.graphics.Typeface;
import android.text.Spanned;
import android.text.style.StyleSpan;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public final class LegalTextRendererTest {
    @Test public void privacyTableBecomesReadableCategoriesWithRegularDescriptions() {
        String source="## Information\n\n| Category | Purpose And Limitation |\n| --- | --- |\n| Recovery Codes | Verify account recovery. |\n| Profile | Route the request. |\n\nOther terms remain unchanged.";
        Spanned rendered=(Spanned)LegalTextRenderer.render(source);
        String text=rendered.toString();
        assertFalse("Markdown table syntax must not appear in the notice.",text.contains("|"));
        assertFalse(text.contains("---"));assertFalse(text.contains("##"));
        assertTrue(text.contains("Recovery Codes\nVerify account recovery."));
        assertTrue(text.contains("Profile\nRoute the request."));
        int category=text.indexOf("Recovery Codes"),description=text.indexOf("Verify account recovery.");
        assertTrue(isBold(rendered,category,category+"Recovery Codes".length()));
        assertFalse("The explanation remains regular weight.",isBold(rendered,description,description+"Verify account recovery.".length()));
        assertTrue(text.endsWith("Other terms remain unchanged."));
        assertTrue("The approved source remains unchanged.",source.contains("| Recovery Codes |"));
    }

    @Test public void ordinaryReferencesAndPipesArePreserved() {
        String source="Keep reference #123 and A | B unchanged.\n\nBody text.";
        assertEquals(source,LegalTextRenderer.render(source).toString());
    }

    private static boolean isBold(Spanned text,int start,int end) {
        for(StyleSpan span:text.getSpans(start,end,StyleSpan.class))if(span.getStyle()==Typeface.BOLD)return true;
        return false;
    }
}
