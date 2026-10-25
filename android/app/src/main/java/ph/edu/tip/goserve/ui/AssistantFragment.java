package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.content.ClipData;
import android.net.Uri;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;
import androidx.fragment.app.Fragment;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentAssistantBinding;
import ph.edu.tip.goserve.ui.common.SeryoMascotView;

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
    private boolean compactComposer;
    private final ActivityResultLauncher<Intent> filePicker=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),result -> {
        if(model!=null)model.finishPicker(result.getData(),result.getResultCode()==Activity.RESULT_OK);
    });
    private final ActivityResultLauncher<Uri> camera=registerForActivityResult(new ActivityResultContracts.TakePicture(){
        @Override public Intent createIntent(Context context,Uri output){
            Intent intent=super.createIntent(context,output).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("GoServe local document",output));return intent;
        }
    },success->{if(model!=null)model.finishCamera(success);});

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
        boolean consent = "consent".equals(panel) || "privacy".equals(panel);
        boolean attachment = "attachment".equals(panel);
        boolean support="settings".equals(panel) || "help".equals(panel);
        binding.chatPanel.setVisibility(consent || attachment || support ? View.GONE : View.VISIBLE);
        binding.consentPanel.setVisibility(consent ? View.VISIBLE : View.GONE);
        binding.attachmentPanel.setVisibility(attachment ? View.VISIBLE : View.GONE);
        binding.supportPanel.setVisibility(support?View.VISIBLE:View.GONE);
        binding.composerHost.setVisibility(consent || attachment || support ? View.GONE : View.VISIBLE);
        if(consent || attachment || support)hideKeyboard();
        binding.accessNotice.setVisibility(View.VISIBLE);
        binding.privacyButton.setVisibility(consent || attachment || support ? View.GONE : View.VISIBLE);
        binding.backButton.setVisibility(consent || attachment || support ? View.VISIBLE : View.GONE);
        binding.panelTitle.setText(consent ? R.string.seryo_privacy_title : attachment ? R.string.seryo_attachment_title : support ? ("help".equals(panel)?R.string.seryo_help_title:R.string.seryo_settings_title):R.string.seryo_name);
        binding.supportHeading.setText("help".equals(panel)?R.string.seryo_help_title:R.string.seryo_settings_title);
        binding.supportBody.setText("help".equals(panel)?R.string.seryo_help_body:R.string.seryo_settings_body);
        binding.helpSettingsButton.setVisibility("help".equals(panel)?View.GONE:View.VISIBLE);
        binding.backButton.setOnClickListener(v -> host.getOnBackPressedDispatcher().onBackPressed());
        binding.privacyButton.setOnClickListener(v -> openPrivacy());
        binding.reviewPrivacyButton.setOnClickListener(v -> openPrivacy());
        binding.attachmentButton.setOnClickListener(v -> {
            PopupMenu tools = new PopupMenu(requireContext(), v);
            tools.getMenu().add(0, 1, 0, R.string.seryo_attachment);
            tools.getMenu().add(0, 2, 1, R.string.seryo_camera);
            tools.setOnMenuItemClickListener(item -> {
                if(!model.snapshot().allowsLocalAttachment()){openPrivacy();return true;}
                if(item.getItemId()==1)chooseFile();else openCamera();
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
        binding.withdrawAiButton.setOnClickListener(v -> model.decide("revoke"));
        binding.privacySettingsButton.setOnClickListener(v -> host.navigate(create("privacy"),true));
        binding.helpSettingsButton.setOnClickListener(v -> host.navigate(create("help"),true));
        binding.refreshStateButton.setOnClickListener(v -> model.refresh());
        binding.retryReadinessButton.setOnClickListener(v -> {if(model.snapshot().consentPending)model.retryConsent();else model.refresh();});
        binding.chooseFileButton.setOnClickListener(v -> chooseFile());
        binding.removeAttachmentButton.setOnClickListener(v -> model.removeAttachment());
        binding.serviceFileButton.setOnClickListener(v -> host.navigate(OfficesFragment.create("Documents"),true));
        binding.sendButton.setOnClickListener(v -> model.checkSend());
        model.state().observe(getViewLifecycleOwner(),ignored -> bindState());
        binding.sendButton.setEnabled(false);
        SharedMotion.install(view);
        view.addOnLayoutChangeListener((root,l,t,r,b,ol,ot,or,ob)->updateComposer());
        bindState();
        bindMascot();
    }

    private void openPrivacy(){
        if(model==null)return;
        String decision=model.snapshot().consent;
        hideKeyboard();((MainActivity)requireActivity()).navigate(create(decision==null || "unset".equals(decision)?"consent":"privacy"),true);
    }
    private void hideKeyboard(){
        if(binding==null)return;
        binding.questionInput.clearFocus();
        android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if(keyboard!=null)keyboard.hideSoftInputFromWindow(binding.getRoot().getWindowToken(),0);
    }
    private void chooseFile(){
        model.requestPicker(ignored->{
            if(binding==null || !isResumed()){model.finishPicker(null,false);return;}
            Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","image/jpeg","image/png"}).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,false)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            filePicker.launch(pick);
        });
    }
    private void openCamera(){
        model.requestCamera(uri->{
            if(binding==null || !isResumed()){model.finishCamera(false);return;}
            camera.launch(uri);
        });
    }
    private void updateComposer(){
        if(binding==null)return;
        WindowInsetsCompat insets=ViewCompat.getRootWindowInsets(binding.getRoot());
        boolean keyboard=insets!=null && insets.isVisible(WindowInsetsCompat.Type.ime());
        ((MainActivity)requireActivity()).setRoleNavigationVisible(!keyboard);
        boolean compact=keyboard || binding.getRoot().getHeight()<480*getResources().getDisplayMetrics().density;
        if(compact!=compactComposer){
            compactComposer=compact;
            int vertical=Math.round((compact?4:8)*getResources().getDisplayMetrics().density);
            binding.composerHost.setPadding(binding.composerHost.getPaddingLeft(),vertical,binding.composerHost.getPaddingRight(),vertical);
            binding.questionInput.setMinLines(compact?1:getResources().getInteger(R.integer.seryo_draft_min_lines));
            binding.questionInput.setMaxLines(compact?1:getResources().getInteger(R.integer.seryo_draft_max_lines));
            binding.composerDisclaimer.setVisibility(compact?View.GONE:View.VISIBLE);
        }else binding.composerDisclaimer.setVisibility(compact?View.GONE:View.VISIBLE);
    }

    private void bindState() {
        if(binding==null || model==null)return;
        SeryoViewModel.State state=model.snapshot();
        boolean known=state.preview || state.readiness!=null;
        boolean allowed="allow".equals(state.consent);
        boolean undecided="unset".equals(state.consent);
        boolean cleanup=state.readiness!=null && "unresolved".equals(state.readiness.cleanup);
        boolean decisionPanel="consent".equals(panel) && (undecided || !known || state.consentPending);
        binding.allowAiButton.setVisibility(decisionPanel?View.VISIBLE:View.GONE);
        binding.declineAiButton.setVisibility(decisionPanel?View.VISIBLE:View.GONE);
        binding.allowAiButton.setEnabled(!state.busy && !state.consentPending && known && undecided);
        binding.declineAiButton.setEnabled(!state.busy && !state.consentPending && known && undecided);
        binding.revokeAiButton.setVisibility(View.GONE);
        binding.withdrawAiButton.setVisibility(allowed && "settings".equals(panel)?View.VISIBLE:View.GONE);
        binding.withdrawAiButton.setEnabled(!state.busy && !state.consentPending);
        binding.chooseFileButton.setEnabled(state.allowsLocalAttachment());
        binding.attachmentButton.setEnabled(!state.busy && !state.consentPending && !cleanup);
        binding.sendButton.setEnabled(state.allowsSend() && state.draft.attachment==null && !state.draft.text.trim().isEmpty());
        binding.questionInput.setEnabled(!state.busy);
        boolean ready=allowed && (state.preview || state.readiness!=null && state.readiness.providerReady && state.readiness.canSend);
        binding.accessNotice.setVisibility("chat".equals(panel)?state.preview || !ready || cleanup || state.error!=null?View.VISIBLE:View.GONE:state.error!=null || state.consentPending || cleanup?View.VISIBLE:View.GONE);
        binding.reviewPrivacyButton.setVisibility("chat".equals(panel) && known && !allowed && !state.consentPending?View.VISIBLE:View.GONE);
        setProcessing(state.sendingQuestion!=null);
        bindMessages(state);
        binding.refreshStateButton.setEnabled(!state.busy);
        binding.retryReadinessButton.setEnabled(!state.busy);
        binding.retryReadinessButton.setText(state.consentPending?R.string.seryo_consent_retry:R.string.seryo_refresh);
        binding.retryReadinessButton.setVisibility(state.error!=null || state.consentPending?View.VISIBLE:View.GONE);
        binding.consentPending.setText(state.consentPending?R.string.seryo_consent_confirm_pending:known ? (allowed?R.string.seryo_consent_allowed:undecided?R.string.seryo_consent_unset:R.string.seryo_consent_off):R.string.seryo_consent_pending);
        if(state.preview)binding.consentPending.append("\n\n"+getString(R.string.seryo_preview_privacy));
        binding.accessTitle.setText(state.busy?R.string.seryo_checking:state.consentPending?R.string.seryo_consent_uncertain_title:cleanup?R.string.seryo_cleanup_blocked:state.preview?R.string.seryo_preview_title:undecided?R.string.seryo_first_privacy:known && !allowed?R.string.seryo_ai_off:R.string.seryo_pending_title);
        binding.accessBody.setText(state.consentPending && !state.busy?R.string.seryo_consent_uncertain_body:state.error!=null?errorText(state.error):cleanup?R.string.seryo_cleanup_blocked_body:state.preview?R.string.seryo_preview_body:undecided?R.string.seryo_first_privacy_body:known && !allowed?R.string.seryo_ai_off_body:R.string.seryo_pending_body);
        binding.cleanupText.setText(cleanup?R.string.seryo_cleanup_blocked_body:R.string.seryo_cleanup_plan);
        bindingDraft=true;
        if(!state.draft.text.contentEquals(binding.questionInput.getText())) {binding.questionInput.setText(state.draft.text);binding.questionInput.setSelection(binding.questionInput.length());}
        bindingDraft=false;
        boolean file=state.draft.attachment!=null;
        binding.inlineAttachment.setVisibility(file?View.VISIBLE:View.GONE);
        if(file) {
            binding.selectedFileText.setText(getString(R.string.seryo_selected_file,state.draft.attachment.name,android.text.format.Formatter.formatShortFileSize(requireContext(),state.draft.attachment.size)));
            binding.operationText.setText(R.string.seryo_selection_unsent);
        }
        binding.serviceFileButton.setEnabled(file && !state.busy);
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
            case "CAMERA_UNAVAILABLE":return R.string.seryo_camera_unavailable;
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
        ((MainActivity)requireActivity()).setRoleNavigationVisible(true);
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
