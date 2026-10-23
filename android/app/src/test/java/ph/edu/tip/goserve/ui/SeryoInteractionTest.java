package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
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
import java.time.Duration;
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
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.shadows.ShadowChoreographer;
import org.robolectric.shadows.ShadowPopupMenu;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.domain.ServiceRequest;
import ph.edu.tip.goserve.ui.common.SeryoMascotView;
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
        RuntimeEnvironment.setFontScale(1f);
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
        assertEquals("Typing is not processing.", SeryoMascotView.State.IDLE,
                ((SeryoMascotView) activity.findViewById(R.id.seryoIcon)).getState());
        click(R.id.privacyButton);
        assertEquals("consent", current().requireArguments().getString("panel"));
        assertTrue(activity.findViewById(R.id.consentPanel).isShown());
        assertEquals(SeryoMascotView.State.IDLE, ((SeryoMascotView) activity.findViewById(R.id.seryoIcon)).getState());
        assertEquals(activity.getString(R.string.seryo_consent_pending), ((TextView) activity.findViewById(R.id.consentPending)).getText().toString());
        click(R.id.returnToChatButton);
        assertTrue(activity.findViewById(R.id.chatPanel).isShown());
        chooseTool(0);
        assertEquals("attachment", current().requireArguments().getString("panel"));
        assertFalse("An unconfigured provider cannot select or upload private files.", activity.findViewById(R.id.chooseFileButton).isEnabled());
        assertEquals(SeryoMascotView.State.IDLE, ((SeryoMascotView) activity.findViewById(R.id.seryoIcon)).getState());
        click(R.id.backButton);
        assertTrue(activity.findViewById(R.id.chatPanel).isShown());
        assertNull("Pending AI panels must not launch a provider or file picker.", Shadows.shadowOf(activity).getNextStartedActivity());
        click(R.id.nav_home);
        assertFalse(current() instanceof AssistantFragment);
    }

    @Test public void headerAndBubbleShareStateWithoutDuplicateLoopsAndStopOnViewDestruction() throws IOException {
        String previousWindowVisibility = System.getProperty("robolectric.areWindowsMarkedVisible");
        System.setProperty("robolectric.areWindowsMarkedVisible", "true");
        ShadowChoreographer.setPaused(true);
        try {
            for (boolean night : new boolean[]{false, true}) {
                launch(night ? 390 : 320, night ? 1f : 2f, night);
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32));
                controller.visible().windowFocusChanged(true);
                activity.findViewById(R.id.nav_assistant).performClick();
                idleAndLayout();
                AssistantFragment fragment = (AssistantFragment) current();
                SeryoMascotView header = activity.findViewById(R.id.seryoIcon);
                SeryoMascotView bubble = activity.findViewById(R.id.seryoBubbleAvatar);
                assertTrue("The fixture must attach the real activity window.", header.isAttachedToWindow());
                assertEquals(View.VISIBLE, header.getWindowVisibility());
                assertEquals(SeryoMascotView.State.IDLE, header.getState());
                assertFalse("The default chat header has no mascot loop.", header.isRunning());
                assertEquals(View.GONE, header.getVisibility());
                assertTrue("The approved default keeps Seryo beside its message.", bubble.isRunning());

                fragment.setMascotPlacement(AssistantFragment.MascotPlacement.BUBBLE);
                idleAndLayout();
                assertEquals(View.GONE, header.getVisibility());
                assertTrue(bubble.isShown());
                assertTrue(bubble.isRunning());
                assertFalse("Only the selected placement may animate.", header.isRunning());
                TextView title = activity.findViewById(R.id.panelTitle);
                assertTrue(activity.findViewById(R.id.providerCaption).getTop() >= title.getBottom());
                ValueAnimator loop = ReflectionHelpers.getField(bubble, "animation");
                loop.setCurrentPlayTime(173);
                int[] firstPixels = avatarPixels(bubble);
                checkScreen("seryo-chat-bubble", "seryo-bubble-" + (night ? "dark" : "font2") + "-phase1", activity.findViewById(R.id.sendButton));
                findScroll(fragment.requireView()).scrollTo(0, 0);
                idleAndLayout();
                loop.setCurrentPlayTime(547);
                assertFalse("The compact portrait must visibly move between authored frames.",
                        java.util.Arrays.equals(firstPixels, avatarPixels(bubble)));
                capture(activity.findViewById(R.id.activityRoot), "seryo-bubble-" + (night ? "dark" : "font2") + "-phase2.png");

                fragment.setProcessing(true);
                assertEquals(SeryoMascotView.State.THINKING, bubble.getState());
                assertEquals(SeryoMascotView.State.THINKING, header.getState());
                assertFalse(activity.findViewById(R.id.sendButton).isEnabled());
                assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
                fragment.setProcessing(false);
                assertEquals(SeryoMascotView.State.IDLE, bubble.getState());
                fragment.setMascotPlacement(AssistantFragment.MascotPlacement.HEADER);
                assertTrue(header.isRunning());
                assertFalse(bubble.isRunning());
                click(R.id.nav_home);
                assertFalse("Destroyed fragment views must release motion.", header.isRunning());
                assertFalse(bubble.isRunning());
            }
        } finally {
            ShadowChoreographer.setPaused(false);
            if (previousWindowVisibility == null) System.clearProperty("robolectric.areWindowsMarkedVisible");
            else System.setProperty("robolectric.areWindowsMarkedVisible", previousWindowVisibility);
        }
    }

    @Test public void unsubmittedQuestionSurvivesSameSessionActivityRecreationWithoutViewSaving() {
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
        assertEquals("The active memory-only draft survives rotation.", "Draft for lifecycle verification", ((EditText) activity.findViewById(R.id.questionInput)).getText().toString());
        assertFalse(activity.findViewById(R.id.sendButton).isEnabled());
    }

    @Test public void welcomeScrollsWithoutMovingTheComposerAtLargeText() {
        launch(320, 2f, false);
        activity.findViewById(R.id.nav_assistant).performClick();
        idleAndLayout();
        View input = activity.findViewById(R.id.questionInput);
        View send = activity.findViewById(R.id.sendButton);
        int[] before = new int[2];
        input.getLocationOnScreen(before);
        NestedScrollView scroll = findScroll(current().requireView());
        assertNotNull(scroll);
        scroll.fullScroll(View.FOCUS_DOWN);
        idleAndLayout();
        int[] after = new int[2];
        input.getLocationOnScreen(after);
        assertArrayEquals("Reading chat must leave the composer pinned.", before, after);
        assertTrue("Large text must leave a usable chat viewport.", scroll.getHeight() >= 80);
        assertTrue(input.isShown());
        Rect visible = new Rect();
        assertTrue(send.getGlobalVisibleRect(visible));
        assertEquals("The send target must remain fully above navigation.", send.getHeight(), visible.height());
        verifyText(current().requireView(), "seryo-pinned-font2");
    }

    @Config(shadows = Api26FontScaling.class)
    @Test public void shortLargeTextScreenLeavesTwoReadableBodyLinesWithHintAndLongDraft() {
        height = 640;
        launch(320, 2f, true);
        // API26's content window excludes the 24dp status and 48dp system navigation bars.
        height = 640 - 24 - 48;
        activity.findViewById(R.id.nav_assistant).performClick();
        idleAndLayout();
        View row = activity.getLayoutInflater().inflate(R.layout.item_seryo_response, null);
        TextView responseBody = row.findViewById(R.id.responseBody);
        int twoBodyLines = 2 * responseBody.getLineHeight();
        NestedScrollView scroll = activity.findViewById(R.id.assistantScroll);
        EditText input = activity.findViewById(R.id.questionInput);
        TextView lastContent = activity.findViewById(R.id.chatIntro);
        View send = activity.findViewById(R.id.sendButton);
        float density = activity.getResources().getDisplayMetrics().density;
        float expectedBodyPixels = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                16f, activity.getResources().getDisplayMetrics());
        System.out.println("SERYO_SHORT_FONT_SETUP activityFontScale=" + activity.getResources().getConfiguration().fontScale
                + " expected16spPixels=" + expectedBodyPixels + " actualInputPixels=" + input.getTextSize()
                + " actualBodyPixels=" + responseBody.getTextSize() + " density=" + density);
        assertEquals("The actual activity must use the requested 2x font configuration.",
                2f, activity.getResources().getConfiguration().fontScale, 0.001f);
        assertTrue("The fixture must render enlarged text, including API-specific SP conversion.", expectedBodyPixels > 16f * density);
        assertEquals("The draft field must render the configured 16sp at the actual 2x scale.", expectedBodyPixels, input.getTextSize(), 0.1f);
        assertEquals("The reading threshold must use an actual enlarged 16sp body.", expectedBodyPixels, responseBody.getTextSize(), 0.1f);
        for (String draft : new String[]{"", "Which campus office handles this request?\nWhat documents should I bring?\nHow do I find the office?\nWhen can I return?\nIs payment required?"}) {
            input.setText(draft);
            idleAndLayout();
            String scenario = draft.isEmpty() ? "hint" : "long draft";
            System.out.println("SERYO_SHORT_FONT_METRICS " + scenario
                    + " activityFontScale=" + activity.getResources().getConfiguration().fontScale
                    + " appFontScale=" + activity.getApplication().getResources().getConfiguration().fontScale
                    + " density=" + density + " scaledDensity=" + activity.getResources().getDisplayMetrics().scaledDensity
                    + " responseBodyTextDp=" + responseBody.getTextSize() / density
                    + " inputTextDp=" + input.getTextSize() / density + " introTextDp=" + lastContent.getTextSize() / density
                    + " rootHeight=" + activity.findViewById(R.id.activityRoot).getHeight()
                    + " navigationHeight=" + activity.findViewById(R.id.bottomNavigation).getHeight()
                    + " viewport=" + scroll.getHeight() + " twoBodyLines=" + twoBodyLines
                    + " inputHeight=" + input.getHeight() + " inputPadding=" + input.getPaddingTop() + "," + input.getPaddingBottom()
                    + " inputMinLines=" + input.getMinLines() + " inputMaxLines=" + input.getMaxLines()
                    + " availableHeightDp=" + activity.getResources().getConfiguration().screenHeightDp
                    + " configuredMaxLines=" + activity.getResources().getInteger(R.integer.seryo_draft_max_lines)
                    + " headerHeight=" + activity.findViewById(R.id.assistantHeader).getHeight()
                    + " composerHeight=" + activity.findViewById(R.id.composerHost).getHeight());
            assertTrue("At 320dp/2x/640dp, " + scenario + " must leave two actual body lines readable; viewport="
                            + scroll.getHeight() + ", two lines=" + twoBodyLines + ", input=" + input.getHeight()
                            + ", header=" + activity.findViewById(R.id.assistantHeader).getHeight()
                            + ", composer=" + activity.findViewById(R.id.composerHost).getHeight(),
                    scroll.getHeight() >= twoBodyLines);
            int[] composerBefore = new int[2];
            input.getLocationOnScreen(composerBefore);
            lastContent.requestRectangleOnScreen(new Rect(0, Math.max(0, lastContent.getHeight() - twoBodyLines),
                    lastContent.getWidth(), lastContent.getHeight()), true);
            idleAndLayout();
            Rect readable = new Rect();
            assertTrue("The final welcome guidance must remain reachable with " + scenario + ".", lastContent.getLocalVisibleRect(readable));
            assertEquals("Scrolling must reach the end of the welcome guidance.", lastContent.getHeight(), readable.bottom);
            assertTrue("The reading window must show two body lines at the end of the guidance.", readable.height() >= twoBodyLines);
            int[] composerAfter = new int[2];
            input.getLocationOnScreen(composerAfter);
            assertArrayEquals("Reading guidance must keep the composer pinned.", composerBefore, composerAfter);
            Rect sendVisible = new Rect();
            assertTrue(send.getGlobalVisibleRect(sendVisible));
            assertEquals(48, sendVisible.height());
            assertEquals(48, sendVisible.width());
            assertEquals(draft, input.getText().toString());
            assertFalse(send.isEnabled());
        }
    }

    @Implements(android.util.TypedValue.class)
    public static class Api26FontScaling {
        @Implementation protected static float applyDimension(int unit, float value, android.util.DisplayMetrics metrics) {
            // API26 scales SP linearly; API34's nonlinear converter would hide the minimum-version regression.
            if (unit == android.util.TypedValue.COMPLEX_UNIT_SP) return value * metrics.scaledDensity;
            return Shadow.directlyOn(android.util.TypedValue.class, "applyDimension",
                    ReflectionHelpers.ClassParameter.from(int.class, unit),
                    ReflectionHelpers.ClassParameter.from(float.class, value),
                    ReflectionHelpers.ClassParameter.from(android.util.DisplayMetrics.class, metrics));
        }
    }

    @Test public void plusOffersAttachmentPrivacySettingsAndHelpWithoutSendingTheDraft() {
        launch(390, 1f, false);
        activity.findViewById(R.id.nav_assistant).performClick();
        idleAndLayout();
        ((EditText) activity.findViewById(R.id.questionInput)).setText("An unsent campus service question");
        click(R.id.attachmentButton);
        android.widget.PopupMenu menu = ShadowPopupMenu.getLatestPopupMenu();
        assertNotNull("The plus action must open its tools menu.", menu);
        assertEquals(4, menu.getMenu().size());
        assertEquals("Attach a file", menu.getMenu().getItem(0).getTitle().toString());
        assertEquals("AI privacy", menu.getMenu().getItem(1).getTitle().toString());
        assertEquals(activity.getString(R.string.seryo_settings_title),menu.getMenu().getItem(2).getTitle().toString());
        assertEquals(activity.getString(R.string.seryo_help_title),menu.getMenu().getItem(3).getTitle().toString());
        assertTrue(menu.getMenu().performIdentifierAction(menu.getMenu().getItem(1).getItemId(), 0));
        idleAndLayout();
        assertTrue(activity.findViewById(R.id.consentPanel).isShown());
        click(R.id.returnToChatButton);
        chooseTool(0);
        assertTrue(activity.findViewById(R.id.attachmentPanel).isShown());
        assertFalse(activity.findViewById(R.id.chooseFileButton).isEnabled());
        assertNull("Opening tools cannot launch a provider or a file picker.", Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test public void compactAvatarRendersTheApprovedOriginalArtworkCrop() {
        launch(390, 1f, false);
        activity.findViewById(R.id.nav_assistant).performClick();
        idleAndLayout();
        SeryoMascotView avatar = activity.findViewById(R.id.seryoBubbleAvatar);
        assertTrue("The welcome message carries its avatar by default.", avatar.isShown());
        assertEquals(36, avatar.getWidth());
        assertEquals(40, avatar.getHeight());
        avatar.stop();
        Bitmap actual = Bitmap.createBitmap(36, 40, Bitmap.Config.ARGB_8888);
        Bitmap expected = Bitmap.createBitmap(36, 40, Bitmap.Config.ARGB_8888);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap original = BitmapFactory.decodeResource(activity.getResources(), R.drawable.seryo_idle, options);
        try {
            avatar.draw(new Canvas(actual));
            float fit = 64f / Math.max(original.getWidth(), original.getHeight());
            float left = -14f + (64f - original.getWidth() * fit) / 2f;
            float top = -9f + (64f - original.getHeight() * fit) / 2f;
            android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
            new Canvas(expected).drawBitmap(original, null,
                    new RectF(left, top, left + original.getWidth() * fit, top + original.getHeight() * fit), paint);
            long alphaError = 0;
            int opaque = 0;
            for (int y = 0; y < 40; y++) for (int x = 0; x < 36; x++) {
                int target = android.graphics.Color.alpha(expected.getPixel(x, y));
                if (target > 128) opaque++;
                alphaError += Math.abs(target - android.graphics.Color.alpha(actual.getPixel(x, y)));
            }
            assertTrue("The reference crop must contain the real portrait.", opaque > 300);
            assertTrue("The compact render must preserve the approved portrait size and crop; alpha error=" + alphaError,
                    alphaError / (36f * 40f) < 5f);
        } finally {
            original.recycle();
            actual.recycle();
            expected.recycle();
        }
    }

    @Test public void conversationRowsWrapLargeTextAndKeepTheAvatarBesideTheReply() {
        launch(320, 2f, false);
        int responseLayout = activity.getResources().getIdentifier("item_seryo_response", "layout", activity.getPackageName());
        int outgoingLayout = activity.getResources().getIdentifier("item_seryo_outgoing", "layout", activity.getPackageName());
        assertTrue("The approved assistant response must have a native row.", responseLayout != 0);
        assertTrue("The approved outgoing message must have a native row.", outgoingLayout != 0);
        View reply = activity.getLayoutInflater().inflate(responseLayout, null);
        View outgoing = activity.getLayoutInflater().inflate(outgoingLayout, null);
        TextView replyBody = reply.findViewById(activity.getResources().getIdentifier("responseBody", "id", activity.getPackageName()));
        TextView outgoingBody = outgoing.findViewById(activity.getResources().getIdentifier("outgoingBody", "id", activity.getPackageName()));
        replyBody.setText("Cashiering handles payments. The service office declares whether a payment is required before you pay. Confirm requirements with the responsible office.");
        outgoingBody.setText("Which office handles payments for a request, and what happens when the office declares that the service is free?");
        for (View row : new View[]{reply, outgoing}) {
            row.measure(View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            row.layout(0, 0, row.getMeasuredWidth(), row.getMeasuredHeight());
            verifyText(row, "seryo-conversation-font2");
        }
        assertTrue("A long reply must wrap without truncating its guidance.", replyBody.getLineCount() > 3);
        assertTrue("A long question must wrap inside its bubble.", outgoingBody.getLineCount() > 3);
        View avatar = reply.findViewById(activity.getResources().getIdentifier("responseAvatar", "id", activity.getPackageName()));
        View card = reply.findViewById(activity.getResources().getIdentifier("responseCard", "id", activity.getPackageName()));
        assertEquals("Seryo belongs beside the end of its reply.", card.getBottom(), avatar.getBottom());
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
        checkScreen("seryo-chat", capture ? "main-seryo-chat-" + suffix : null, activity.findViewById(R.id.sendButton));
        click(R.id.privacyButton);
        checkScreen("seryo-privacy", capture ? "main-seryo-privacy-" + suffix : null, activity.findViewById(R.id.returnToChatButton));
        click(R.id.returnToChatButton);
        chooseTool(0);
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
        RuntimeEnvironment.setFontScale(fontScale);
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
    private void chooseTool(int index) {
        click(R.id.attachmentButton);
        android.widget.PopupMenu popup = ShadowPopupMenu.getLatestPopupMenu();
        assertNotNull("The tools action must open an anchored menu.", popup);
        assertTrue(popup.getMenu().performIdentifierAction(popup.getMenu().getItem(index).getItemId(), 0));
        idleAndLayout();
    }
    private int[] avatarPixels(View avatar) {
        Bitmap bitmap = Bitmap.createBitmap(avatar.getWidth(), avatar.getHeight(), Bitmap.Config.ARGB_8888);
        try {
            avatar.draw(new Canvas(bitmap));
            int[] pixels = new int[bitmap.getWidth() * bitmap.getHeight()];
            bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
            return pixels;
        } finally { bitmap.recycle(); }
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
