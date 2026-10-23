package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.app.Activity;
import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;
import androidx.fragment.app.Fragment;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentAssistantBinding;
import ph.edu.tip.goserve.ui.common.SeryoMascotView;
import ph.edu.tip.goserve.domain.SeryoDraft;

public final class AssistantFragment extends Fragment {
    public enum MascotPlacement { HEADER, BUBBLE }
    private FragmentAssistantBinding binding;
    private MascotPlacement mascotPlacement = MascotPlacement.BUBBLE;
    private boolean processing;
    private SeryoViewModel model;
    private String panel;
    private boolean bindingDraft;
    private long decisionSequence;
    private SeryoViewModel.Message renderedLastMessage;
    private String renderedPending;
    private int renderedCount=-1;
    private final ActivityResultLauncher<Intent> filePicker=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),result -> {
        if(model!=null)model.finishPicker(result.getData(),result.getResultCode()==Activity.RESULT_OK);
    });

    public static AssistantFragment create(String panel) {
        AssistantFragment fragment = new AssistantFragment();
        Bundle args = new Bundle();
        args.putString("panel", panel);
        fragment.setArguments(args);
        return fragment;
    }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
        binding = FragmentAssistantBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(View view, Bundle state) {
        MainActivity host = (MainActivity) requireActivity();
        panel = getArguments() == null ? "chat" : getArguments().getString("panel", "chat");
        boolean consent = "consent".equals(panel);
        boolean attachment = "attachment".equals(panel);
        boolean support="settings".equals(panel) || "help".equals(panel);
        binding.chatPanel.setVisibility(consent || attachment || support ? View.GONE : View.VISIBLE);
        binding.consentPanel.setVisibility(consent ? View.VISIBLE : View.GONE);
        binding.attachmentPanel.setVisibility(attachment ? View.VISIBLE : View.GONE);
        binding.supportPanel.setVisibility(support?View.VISIBLE:View.GONE);
        binding.composerHost.setVisibility(consent || attachment || support ? View.GONE : View.VISIBLE);
        binding.accessNotice.setVisibility(View.VISIBLE);
        binding.privacyButton.setVisibility(consent || attachment || support ? View.GONE : View.VISIBLE);
        binding.backButton.setVisibility(consent || attachment || support ? View.VISIBLE : View.GONE);
        binding.panelTitle.setText(consent ? R.string.seryo_privacy_title : attachment ? R.string.seryo_attachment_title : support ? ("help".equals(panel)?R.string.seryo_help_title:R.string.seryo_settings_title):R.string.seryo_name);
        binding.supportHeading.setText("help".equals(panel)?R.string.seryo_help_title:R.string.seryo_settings_title);
        binding.supportBody.setText("help".equals(panel)?R.string.seryo_help_body:R.string.seryo_settings_body);
        binding.helpSettingsButton.setVisibility("help".equals(panel)?View.GONE:View.VISIBLE);
        binding.backButton.setOnClickListener(v -> host.getOnBackPressedDispatcher().onBackPressed());
        binding.privacyButton.setOnClickListener(v -> host.navigate(create("consent"), true));
        binding.attachmentButton.setOnClickListener(v -> {
            PopupMenu tools = new PopupMenu(requireContext(), v);
            tools.getMenu().add(0, 1, 0, R.string.seryo_attachment);
            tools.getMenu().add(0, 2, 1, R.string.seryo_tools_privacy);
            tools.getMenu().add(0,3,2,R.string.seryo_settings_title);
            tools.getMenu().add(0,4,3,R.string.seryo_help_title);
            tools.setOnMenuItemClickListener(item -> {
                host.navigate(create(item.getItemId() == 1 ? "attachment" : item.getItemId()==2?"consent":item.getItemId()==3?"settings":"help"), true);
                return true;
            });
            tools.show();
        });
        binding.returnToChatButton.setOnClickListener(v -> host.getOnBackPressedDispatcher().onBackPressed());
        model=new ViewModelProvider(requireActivity()).get(SeryoViewModel.class);
        model.start(host.isPreview());
        decisionSequence=model.snapshot().decisionSequence;
        binding.questionInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence value,int start,int count,int after) {}
            public void onTextChanged(CharSequence value,int start,int before,int count) {if(!bindingDraft)model.edit(value.toString());}
            public void afterTextChanged(Editable value) {}
        });
        binding.allowAiButton.setOnClickListener(v -> model.decide("allow"));
        binding.declineAiButton.setOnClickListener(v -> model.decide("decline"));
        binding.revokeAiButton.setOnClickListener(v -> model.decide("revoke"));
        binding.privacySettingsButton.setOnClickListener(v -> host.navigate(create("consent"),true));
        binding.helpSettingsButton.setOnClickListener(v -> host.navigate(create("help"),true));
        binding.refreshStateButton.setOnClickListener(v -> model.refresh());
        binding.retryReadinessButton.setOnClickListener(v -> {if(model.snapshot().consentPending)model.retryConsent();else model.refresh();});
        binding.chooseFileButton.setOnClickListener(v -> model.requestPicker(ignored -> {
            if(binding==null || !isResumed()) {model.finishPicker(null,false);return;}
            Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                    .putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","image/jpeg","image/png"}).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,false)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            filePicker.launch(pick);
        }));
        binding.removeAttachmentButton.setOnClickListener(v -> model.removeAttachment());
        binding.explainFileButton.setOnClickListener(v -> model.prepare(SeryoDraft.Operation.EXPLAIN));
        binding.extractFileButton.setOnClickListener(v -> model.prepare(SeryoDraft.Operation.EXTRACT));
        binding.sendButton.setOnClickListener(v -> model.checkSend());
        model.state().observe(getViewLifecycleOwner(),ignored -> bindState());
        binding.sendButton.setEnabled(false);
        SharedMotion.install(view);
        bindState();
        bindMascot();
    }

    private void bindState() {
        if(binding==null || model==null)return;
        SeryoViewModel.State state=model.snapshot();
        boolean known=state.readiness!=null;
        boolean allowed=known && "allow".equals(state.readiness.consent);
        boolean cleanup=known && "unresolved".equals(state.readiness.cleanup);
        binding.allowAiButton.setEnabled(!state.busy && !state.consentPending && known && !allowed);
        binding.declineAiButton.setEnabled(!state.busy && !state.consentPending && known && !allowed && !"decline".equals(state.readiness.consent));
        binding.revokeAiButton.setVisibility(allowed?View.VISIBLE:View.GONE);
        binding.revokeAiButton.setEnabled(!state.busy && !state.consentPending && allowed);
        binding.chooseFileButton.setEnabled(!state.busy && !state.consentPending && known && state.readiness.allowsAttachment());
        binding.sendButton.setEnabled(!state.busy && !state.consentPending && known && allowed && state.readiness.canSend && state.readiness.providerReady && !cleanup && state.draft.attachment==null && !state.draft.text.trim().isEmpty());
        binding.questionInput.setEnabled(!state.busy);
        binding.accessNotice.setVisibility("chat".equals(panel) && known && allowed && state.readiness.providerReady && !cleanup && state.error==null?View.GONE:View.VISIBLE);
        setProcessing(state.sendingQuestion!=null);
        bindMessages(state);
        binding.refreshStateButton.setEnabled(!state.busy);
        binding.retryReadinessButton.setEnabled(!state.busy);
        binding.retryReadinessButton.setText(state.consentPending?R.string.seryo_consent_retry:R.string.seryo_refresh);
        binding.retryReadinessButton.setVisibility(state.error!=null || state.consentPending?View.VISIBLE:View.GONE);
        binding.consentPending.setText(state.consentPending?R.string.seryo_consent_confirm_pending:known ? (allowed?R.string.seryo_consent_allowed:"decline".equals(state.readiness.consent)||"revoke".equals(state.readiness.consent)?R.string.seryo_consent_off:R.string.seryo_consent_unset):R.string.seryo_consent_pending);
        binding.accessTitle.setText(state.busy?R.string.seryo_checking:state.consentPending?R.string.seryo_consent_uncertain_title:cleanup?R.string.seryo_cleanup_blocked:known && !allowed?R.string.seryo_ai_off:R.string.seryo_pending_title);
        binding.accessBody.setText(state.consentPending && !state.busy?R.string.seryo_consent_uncertain_body:state.error!=null?errorText(state.error):cleanup?R.string.seryo_cleanup_blocked_body:known && !allowed?R.string.seryo_ai_off_body:R.string.seryo_pending_body);
        binding.cleanupText.setText(cleanup?R.string.seryo_cleanup_blocked_body:R.string.seryo_cleanup_plan);
        bindingDraft=true;
        if(!state.draft.text.contentEquals(binding.questionInput.getText())) {binding.questionInput.setText(state.draft.text);binding.questionInput.setSelection(binding.questionInput.length());}
        bindingDraft=false;
        boolean file=state.draft.attachment!=null;
        binding.inlineAttachment.setVisibility(file?View.VISIBLE:View.GONE);
        if(file) {
            binding.selectedFileText.setText(getString(R.string.seryo_selected_file,state.draft.attachment.name,state.draft.attachment.size));
            binding.operationText.setText(state.draft.operation==SeryoDraft.Operation.EXPLAIN?R.string.seryo_explain_draft:state.draft.operation==SeryoDraft.Operation.EXTRACT?R.string.seryo_extract_draft:R.string.seryo_selection_unsent);
        }
        binding.explainFileButton.setEnabled(file && !state.busy && !state.consentPending && !cleanup);
        binding.extractFileButton.setEnabled(file && !state.busy && !state.consentPending && !cleanup);
        binding.removeAttachmentButton.setEnabled(!state.busy);
        if(state.decisionSequence>decisionSequence && "consent".equals(panel)) {
            decisionSequence=state.decisionSequence;
            ((MainActivity)requireActivity()).navigate(create("chat"),false);
        }
    }

    private void bindMessages(SeryoViewModel.State state) {
        SeryoViewModel.Message last=state.messages.isEmpty()?null:state.messages.get(state.messages.size()-1);
        if(renderedLastMessage==last && renderedCount==state.messages.size() && java.util.Objects.equals(renderedPending,state.sendingQuestion))return;
        renderedLastMessage=last;renderedCount=state.messages.size();renderedPending=state.sendingQuestion;
        binding.chatTranscript.removeAllViews();
        for(SeryoViewModel.Message message:state.messages)addMessage(message.text,message.user,false);
        if(state.sendingQuestion!=null){addMessage(state.sendingQuestion,true,false);addMessage(getString(R.string.seryo_processing),false,true);}
        binding.assistantScroll.post(() -> {if(binding!=null)binding.assistantScroll.fullScroll(View.FOCUS_DOWN);});
    }

    private void addMessage(String text,boolean user,boolean thinking) {
        ph.edu.tip.goserve.databinding.RowSeryoMessageBinding row=ph.edu.tip.goserve.databinding.RowSeryoMessageBinding.inflate(getLayoutInflater(),binding.chatTranscript,false);
        row.messageText.setText(text);
        row.messageMascot.setVisibility(user || mascotPlacement==MascotPlacement.HEADER?View.GONE:View.VISIBLE);
        row.messageMascot.setState(thinking?SeryoMascotView.State.THINKING:SeryoMascotView.State.IDLE);
        if(user){row.getRoot().setPadding((int)(46*getResources().getDisplayMetrics().density),0,0,0);row.messageText.setBackgroundResource(R.drawable.bg_muted);}
        binding.chatTranscript.addView(row.getRoot());
    }

    private int errorText(String code) {
        switch(code) {
            case "CLEANUP_REQUIRED":return R.string.seryo_cleanup_blocked_body;
            case "TIMEOUT":return R.string.seryo_timeout;
            case "CONNECTION":return R.string.seryo_connection;
            case "CONSENT_CHANGED":case "STALE_VERSION":return R.string.seryo_consent_changed;
            case "ACCOUNT_CHANGED":case "AUTH_REQUIRED":case "AUTH_EXPIRED":return R.string.seryo_account_changed;
            case "FILE_UNREADABLE":return R.string.seryo_file_invalid;
            case "PICKER_UNAVAILABLE":return R.string.seryo_picker_unavailable;
            case "CONSENT_UNCERTAIN":return R.string.seryo_consent_uncertain_body;
            case "ONE_FILE":return R.string.seryo_one_file;
            case "FILE_UNAVAILABLE":case "PROVIDER_UNAVAILABLE":return R.string.seryo_pending_body;
            default:return R.string.seryo_readiness_error;
        }
    }

    @Override public void onResume() {
        super.onResume();if(model!=null) {model.verifyAccount();model.refresh();}
    }

    public void setMascotPlacement(MascotPlacement placement) {
        if (placement == null) throw new IllegalArgumentException("Choose a mascot placement.");
        mascotPlacement = placement;
        bindMascot();
    }

    /** Reflect only a user-initiated send waiting for its server response. */
    public void setProcessing(boolean inFlight) {
        processing = inFlight;
        bindMascot();
    }

    private void bindMascot() {
        if (binding == null) return;
        boolean chat = binding.chatPanel.getVisibility() == View.VISIBLE;
        boolean bubble = mascotPlacement == MascotPlacement.BUBBLE && chat;
        binding.seryoIcon.setVisibility(mascotPlacement == MascotPlacement.HEADER && chat ? View.VISIBLE : View.GONE);
        binding.seryoBubbleAvatar.setVisibility(bubble ? View.VISIBLE : View.GONE);
        SeryoMascotView.State state = processing ? SeryoMascotView.State.THINKING : SeryoMascotView.State.IDLE;
        binding.seryoIcon.setState(state);
        binding.seryoBubbleAvatar.setState(state);
        int status = processing ? R.string.seryo_processing : R.string.seryo_role;
        String text = getString(status);
        if (!text.contentEquals(binding.providerCaption.getText())) binding.providerCaption.setText(text);
    }

    @Override public void onStart() {
        super.onStart();
        if (binding != null) {
            binding.seryoIcon.setHostActive(true);
            binding.seryoBubbleAvatar.setHostActive(true);
        }
    }

    @Override public void onStop() {
        if(model!=null)model.cancelUnlaunchedPicker();
        if (binding != null) {
            binding.seryoIcon.setHostActive(false);
            binding.seryoBubbleAvatar.setHostActive(false);
        }
        super.onStop();
    }

    @Override public void onDestroyView() {
        if (binding != null) {
            binding.seryoIcon.setHostActive(false);
            binding.seryoBubbleAvatar.setHostActive(false);
        }
        processing = false;
        renderedLastMessage=null;renderedPending=null;renderedCount=-1;
        super.onDestroyView();
        binding = null;
    }
}
