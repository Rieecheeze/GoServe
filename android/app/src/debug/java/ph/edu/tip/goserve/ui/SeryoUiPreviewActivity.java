package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.constraintlayout.widget.ConstraintLayout;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentAssistantBinding;

/** Debug-only Figma fixtures; this activity does not authenticate or contact a provider. */
public final class SeryoUiPreviewActivity extends BaseActivity {
    private String screen;
    private FragmentAssistantBinding assistant;

    @Override protected void onCreate(Bundle state) {
        String theme = getIntent().getStringExtra("theme");
        if (theme != null) getDelegate().setLocalNightMode("dark".equals(theme)
                ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(state);
        screen = getIntent().getStringExtra("screen");
        if (screen == null) screen = "welcome";
        setTitle("Debug Figma fixture · Seryo · " + screen);
        if ("loading".equals(screen)) {
            setContentView(R.layout.activity_auth);
            findViewById(R.id.authContainer).setVisibility(View.GONE);
            findViewById(R.id.authLoading).setVisibility(View.VISIBLE);
        } else {
            setContentView(R.layout.activity_main);
            ViewGroup host = findViewById(R.id.mainContainer);
            assistant = FragmentAssistantBinding.inflate(getLayoutInflater(), host, true);
            CapsuleNavigationView navigation = findViewById(R.id.bottomNavigation);
            navigation.setSelectedItemId(R.id.nav_assistant);
            navigation.setOnItemSelectedListener(item -> {
                Toast.makeText(this, "Debug Figma fixture", Toast.LENGTH_SHORT).show();
                return false;
            });
            assistant.privacyButton.setOnClickListener(view -> showPanel("consent"));
            assistant.backButton.setOnClickListener(view -> showPanel("chat"));
            assistant.returnToChatButton.setOnClickListener(view -> showPanel("chat"));
            assistant.attachmentButton.setOnClickListener(view -> {
                PopupMenu tools = new PopupMenu(this, view);
                tools.getMenu().add(0, 1, 0, R.string.seryo_attachment);
                tools.getMenu().add(0, 2, 1, R.string.seryo_tools_privacy);
                tools.setOnMenuItemClickListener(item -> {
                    showPanel(item.getItemId() == 1 ? "attachment" : "consent");
                    return true;
                });
                tools.show();
            });
            if ("conversation".equals(screen)) showConversation();
        }
        View root = findViewById(R.id.activityRoot);
        root.setContentDescription("Debug-only approved Figma fixture. Example content is not an AI response.");
        root.setFocusableInTouchMode(true);
        root.requestFocus();
        applyInsets(root);
    }

    private void showPanel(String panel) {
        boolean chat = "chat".equals(panel);
        assistant.chatPanel.setVisibility(chat ? View.VISIBLE : View.GONE);
        assistant.composerHost.setVisibility(chat ? View.VISIBLE : View.GONE);
        assistant.accessNotice.setVisibility(chat ? View.GONE : View.VISIBLE);
        assistant.consentPanel.setVisibility("consent".equals(panel) ? View.VISIBLE : View.GONE);
        assistant.attachmentPanel.setVisibility("attachment".equals(panel) ? View.VISIBLE : View.GONE);
        assistant.backButton.setVisibility(chat ? View.GONE : View.VISIBLE);
        assistant.privacyButton.setVisibility(chat ? View.VISIBLE : View.GONE);
        assistant.panelTitle.setText(chat ? R.string.seryo_name
                : "consent".equals(panel) ? R.string.seryo_privacy_title : R.string.seryo_attachment_title);
    }

    private void showConversation() {
        ConstraintLayout panel = assistant.chatPanel;
        panel.removeAllViews();
        panel.setMinimumHeight(0);
        LinearLayout messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        ConstraintLayout.LayoutParams bounds = new ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        bounds.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        bounds.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        bounds.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        bounds.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        panel.addView(messages, bounds);
        outgoing(messages, "Which office handles payments?");
        reply(messages, "Cashiering handles payments. The service office declares whether a payment is required before you pay.", "Open Cashiering");
        outgoing(messages, "What if my request is free?");
        reply(messages, "A request declared free skips payment. Follow the office’s processing and release steps.", null);
        outgoing(messages, "Take me to the Registrar.");
        reply(messages, "Open the Registrar to view its services and current availability.", "Open Registrar");
    }

    private void outgoing(LinearLayout messages, String text) {
        View row = getLayoutInflater().inflate(R.layout.item_seryo_outgoing, messages, false);
        ((TextView) row.findViewById(R.id.outgoingBody)).setText(text);
        addMessage(messages, row);
    }

    private void reply(LinearLayout messages, String text, String office) {
        View row = getLayoutInflater().inflate(R.layout.item_seryo_response, messages, false);
        ((TextView) row.findViewById(R.id.responseBody)).setText(text);
        if (office != null) {
            TextView action = row.findViewById(R.id.responseOffice);
            action.setText(office);
            action.setVisibility(View.VISIBLE);
            action.setOnClickListener(view -> new AlertDialog.Builder(this).setTitle("Debug Figma fixture")
                    .setMessage("This office action is approved example content for visual verification.")
                    .setPositiveButton(android.R.string.ok, null).show());
        }
        addMessage(messages, row);
    }

    private void addMessage(LinearLayout messages, View row) {
        LinearLayout.LayoutParams bounds = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (messages.getChildCount() > 0) bounds.topMargin = Math.round(24 * getResources().getDisplayMetrics().density);
        messages.addView(row, bounds);
    }
}
