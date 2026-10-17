package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.domain.ServiceRequest;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = GoServeApplication.class, qualifiers = "w390dp-h844dp-port-notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class SeryoInteractionTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private int width = 390;
    private int height = 844;

    @Before public void resetFactory() {
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class, "_instance", null);
    }

    @After public void closeActivity() {
        if (controller != null) controller.close();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    @Test public void directAiChatOpensPrivacyAndAttachmentsWhileProviderActionsStayDisabled() {
        launch(390, 1f, false);
        activity.findViewById(R.id.nav_assistant).performClick();
        idleAndLayout();
        assertTrue(current() instanceof AssistantFragment);
        assertFalse(activity.findViewById(R.id.sendButton).isEnabled());
        ((EditText) activity.findViewById(R.id.questionInput)).setText("Which office handles a document request?");
        assertFalse("Typing must not activate an unconfigured provider.", activity.findViewById(R.id.sendButton).isEnabled());
        click(R.id.privacyButton);
        assertEquals("consent", current().requireArguments().getString("panel"));
        assertTrue(activity.findViewById(R.id.consentPanel).isShown());
        assertEquals(activity.getString(R.string.seryo_consent_pending), ((TextView) activity.findViewById(R.id.consentPending)).getText().toString());
        click(R.id.returnToChatButton);
        assertTrue(activity.findViewById(R.id.chatPanel).isShown());
        click(R.id.attachmentButton);
        assertEquals("attachment", current().requireArguments().getString("panel"));
        assertFalse("An unconfigured provider cannot select or upload private files.", activity.findViewById(R.id.chooseFileButton).isEnabled());
        click(R.id.backButton);
        assertTrue(activity.findViewById(R.id.chatPanel).isShown());
        assertNull("Pending AI panels must not launch a provider or file picker.", Shadows.shadowOf(activity).getNextStartedActivity());
        click(R.id.officeButton);
        assertTrue(current() instanceof OfficesFragment);
    }

    @Test public void unsubmittedQuestionIsNotRestoredAfterActivityRecreation() {
        launch(390, 1f, false);
        activity.findViewById(R.id.nav_assistant).performClick();
        idleAndLayout();
        EditText draft = activity.findViewById(R.id.questionInput);
        draft.setText("Draft for lifecycle verification");
        assertFalse(draft.isSaveEnabled());
        controller.recreate();
        activity = controller.get();
        idleAndLayout();
        assertTrue(current() instanceof AssistantFragment);
        assertEquals("An unsubmitted AI draft must not be persisted.", "", ((EditText) activity.findViewById(R.id.questionInput)).getText().toString());
        assertFalse(activity.findViewById(R.id.sendButton).isEnabled());
    }

    @Test public void homePreviewAndOpenedRequestUseTheSameCatalogIdentity() {
        launch(390, 1f, false);
        ServiceRequest expected = RequestPreviewCatalog.find(activity, RequestPreviewCatalog.REQUIRED_ID);
        assertEquals(expected.title, ((TextView) activity.findViewById(R.id.requestTitle)).getText().toString());
        assertEquals(expected.officeName + " · " + expected.reference, ((TextView) activity.findViewById(R.id.requestMeta)).getText().toString());
        click(R.id.requestDetailsButton);
        assertTrue(current() instanceof RequestDetailFragment);
        assertEquals(expected.title, ((TextView) activity.findViewById(R.id.requestDetailName)).getText().toString());
        assertEquals("Reference: " + expected.reference, ((TextView) activity.findViewById(R.id.requestDetailReference)).getText().toString());
    }

    @Test public void nativePreviewScreensWrapAndReachTheirLastContentAboveNavigation() throws IOException {
        int scenarios = 0;
        for (int requestedWidth : new int[]{320, 390}) {
            for (float fontScale : new float[]{1f, 2f}) {
                scenarios += verifyPreviewScreens(requestedWidth, fontScale, false, requestedWidth == 390 && fontScale == 1f || requestedWidth == 320 && fontScale == 2f);
            }
        }
        scenarios += verifyPreviewScreens(390, 1f, true, true);
        assertEquals(35, scenarios);
        System.out.println("PASS: " + scenarios + " populated native preview scenarios; 36 PNG captures");
    }

    private int verifyPreviewScreens(int requestedWidth, float fontScale, boolean night, boolean capture) throws IOException {
        launch(requestedWidth, fontScale, night);
        String suffix = requestedWidth + (fontScale == 2f ? "-font2" : "") + (night ? "-dark" : "-light");
        checkScreen("home", capture ? "main-home-" + suffix : null, null);
        activity.findViewById(R.id.nav_requests).performClick();
        idleAndLayout();
        ViewGroup cards = activity.findViewById(R.id.requestCards);
        assertEquals(3, cards.getChildCount());
        checkScreen("requests", capture ? "main-requests-" + suffix : null, cards.getChildAt(2).findViewById(R.id.requestCardDemo));
        activity.navigate(RequestDetailFragment.create(RequestPreviewCatalog.REQUIRED_ID), true);
        idleAndLayout();
        checkScreen("request-detail", capture ? "main-request-detail-" + suffix : null, activity.findViewById(R.id.requestPaymentNote));
        activity.navigate(VerifiedFileFragment.create(RequestPreviewCatalog.FREE_ID), true);
        idleAndLayout();
        activity.findViewById(R.id.verifiedFileDownloadInfo).performClick();
        idleAndLayout();
        checkScreen("verified-file", capture ? "main-verified-file-" + suffix : null, activity.findViewById(R.id.verifiedFileDownloadExplanation));
        activity.findViewById(R.id.nav_assistant).performClick();
        idleAndLayout();
        checkScreen("seryo-chat", capture ? "main-seryo-chat-" + suffix : null, activity.findViewById(R.id.officeButton));
        click(R.id.privacyButton);
        checkScreen("seryo-privacy", capture ? "main-seryo-privacy-" + suffix : null, activity.findViewById(R.id.returnToChatButton));
        click(R.id.returnToChatButton);
        click(R.id.attachmentButton);
        checkScreen("seryo-attachment", capture ? "main-seryo-attachment-" + suffix : null, activity.findViewById(R.id.cleanupText));
        TextView title = activity.findViewById(R.id.panelTitle);
        View caption = activity.findViewById(R.id.providerCaption);
        assertTrue("The wrapped panel title must stay below the content top.", title.getTop() >= 0);
        assertTrue("Provider caption overlaps the wrapped panel title.", caption.getTop() >= title.getBottom());
        return 7;
    }

    private void checkScreen(String label, String captureName, View finalContent) throws IOException {
        idleAndLayout();
        View root = activity.findViewById(R.id.activityRoot);
        verifyText(root, label);
        if (captureName != null) capture(root, captureName + ".png");
        if (finalContent == null) return;
        NestedScrollView scroll = findScroll(current().requireView());
        assertNotNull(label + " needs a scroll container.", scroll);
        scroll.setSmoothScrollingEnabled(false);
        scroll.fullScroll(View.FOCUS_DOWN);
        idleAndLayout();
        Rect visible = new Rect();
        assertTrue(label + " final content cannot be reached.", finalContent.getGlobalVisibleRect(visible));
        assertEquals(label + " final text is vertically clipped.", finalContent.getHeight(), visible.height());
        int[] navigationPosition = new int[2];
        activity.findViewById(R.id.bottomNavigation).getLocationOnScreen(navigationPosition);
        assertTrue(label + " final content is covered by navigation.", visible.bottom <= navigationPosition[1]);
        if (captureName != null && (label.equals("request-detail") || label.startsWith("seryo"))) capture(root, captureName + "-bottom.png");
    }

    private void launch(int requestedWidth, float fontScale, boolean night) {
        if (controller != null) { controller.close(); controller = null; }
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class, "_instance", null);
        width = requestedWidth;
        RuntimeEnvironment.setQualifiers("w" + width + "dp-h" + height + "dp-port-" + (night ? "night" : "notnight") + "-mdpi");
        GoServeApplication app = (GoServeApplication) RuntimeEnvironment.getApplication();
        AppCompatDelegate.setDefaultNightMode(night ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        Configuration config = new Configuration(app.getResources().getConfiguration());
        config.fontScale = fontScale;
        app.getResources().updateConfiguration(config, app.getResources().getDisplayMetrics());
        controller = Robolectric.buildActivity(MainActivity.class,
                new Intent(app, MainActivity.class).putExtra(MainActivity.EXTRA_PREVIEW, true)).setup().visible();
        activity = controller.get();
        idleAndLayout();
    }

    private void click(int id) {
        View target = activity.findViewById(id);
        assertNotNull(target);
        assertTrue(target.isEnabled());
        assertTrue(target.performClick());
        idleAndLayout();
    }
    private Fragment current() { return activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer); }
    private void idleAndLayout() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        activity.getSupportFragmentManager().executePendingTransactions();
        View root = activity.findViewById(R.id.activityRoot);
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }
    private void verifyText(View view, String scenario) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView && !(view instanceof EditText)) {
            TextView text = (TextView) view;
            if (text.getLayout() != null && text.length() > 0) {
                String id = text.getId() == View.NO_ID ? "text" : text.getResources().getResourceEntryName(text.getId());
                assertTrue(scenario + " clips " + id, text.getLayout().getHeight() <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom() + 2);
                for (int line = 0; line < text.getLineCount(); line++) assertEquals(scenario + " truncates " + id, 0, text.getLayout().getEllipsisCount(line));
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.getVisibility() == View.VISIBLE && !(group instanceof android.widget.HorizontalScrollView)) {
                    assertTrue(scenario + " overflows horizontally.", child.getLeft() >= 0 && child.getRight() <= group.getWidth());
                }
                verifyText(child, scenario);
            }
        }
    }
    private NestedScrollView findScroll(View view) {
        if (view instanceof NestedScrollView) return (NestedScrollView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                NestedScrollView found = findScroll(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
    private void capture(View root, String filename) throws IOException {
        root.jumpDrawablesToCurrentState();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(ContextCompat.getColor(activity, R.color.gs_background));
            root.draw(canvas);
            File directory = new File("build/outputs/screenshots");
            assertTrue(directory.isDirectory() || directory.mkdirs());
            try (FileOutputStream output = new FileOutputStream(new File(directory, filename))) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
            }
        } finally { bitmap.recycle(); }
    }
}
