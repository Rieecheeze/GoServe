package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.content.ClipData;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.CameraEvidence;
import ph.edu.tip.goserve.data.ContentEvidenceSource;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.databinding.DialogRequestEvidenceBinding;

public final class RequestEvidenceDialog extends DialogFragment {
    static final String TAG="request_evidence";
    private DialogRequestEvidenceBinding binding;
    private RequestEvidenceViewModel model;
    private RequestComposeViewModel composer;
    private CameraCaptureViewModel captures;
    private String quoteId,pickerQuote;
    private int localError;
    private boolean cameraUnavailable;
    private boolean resultHandled;
    private final ActivityResultLauncher<String[]> document=registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{
        String selectedQuote=pickerQuote; pickerQuote=null;
        if(uri==null || binding==null || selectedQuote==null || !selectedQuote.equals(quoteId) || !currentContext())return;
        try { localError=0; model.select(new ContentEvidenceSource(requireContext(),uri)); }
        catch(IllegalArgumentException invalid) { localError=R.string.evidence_unreadable; render(model.state().getValue()); }
    });
    private final ActivityResultLauncher<Uri> camera=registerForActivityResult(new ActivityResultContracts.TakePicture() {
        @Override public Intent createIntent(Context context,Uri output) {
            Intent intent=super.createIntent(context,output).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("GoServe evidence",output)); return intent;
        }
    },success->{
        if(captures==null)return; captures.returned(); CameraEvidence capture=captures.current();
        if(success && capture!=null && binding!=null && currentContext()) { localError=0; model.select(capture.source()); }
        else { captures.clear(); if(binding!=null)render(model.state().getValue()); }
    });
    static RequestEvidenceDialog create(String requirement) {
        RequestEvidenceDialog dialog=new RequestEvidenceDialog(); Bundle args=new Bundle(); args.putString("requirement",requirement); dialog.setArguments(args); return dialog;
    }
    @Override public void onCreate(Bundle saved) { super.onCreate(saved); setStyle(STYLE_NO_TITLE,R.style.Theme_GoServe); }
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved) {
        binding=DialogRequestEvidenceBinding.inflate(inflater,parent,false); return binding.getRoot();
    }
    @Override public void onViewCreated(View view,Bundle saved) {
        RequestComposeFragment parent=(RequestComposeFragment)requireParentFragment();
        composer=new ViewModelProvider(parent).get(RequestComposeViewModel.class);
        model=new ViewModelProvider(this,parent.evidenceFactory()==null?getDefaultViewModelProviderFactory():parent.evidenceFactory()).get(RequestEvidenceViewModel.class);
        captures=new ViewModelProvider(this,getDefaultViewModelProviderFactory()).get(CameraCaptureViewModel.class);
        RequestComposeViewModel.State state=composer.state().getValue();
        if(quoteId==null)quoteId=model.state().getValue().prepared!=null?model.state().getValue().prepared.quote.id:state.quote==null?null:state.quote.id;
        binding.evidenceBack.setOnClickListener(v->dismiss());
        binding.evidenceChoose.setOnClickListener(v->{
            if(!currentContext())return;
            RequestData.FileOptions policy=composer.state().getValue().options.evidence;
            try { localError=0; pickerQuote=quoteId; document.launch(policy.mimeTypes.toArray(new String[0])); }
            catch(RuntimeException unavailable) { pickerQuote=null; localError=R.string.evidence_unreadable; render(model.state().getValue()); }
        });
        binding.evidenceCamera.setOnClickListener(v->{
            if(!currentContext() || !composer.state().getValue().options.evidence.mimeTypes.contains("image/jpeg"))return;
            try { localError=0; cameraUnavailable=false; camera.launch(captures.begin().uri()); render(model.state().getValue()); }
            catch(Exception unavailable) { captures.clear(); cameraUnavailable=true; render(model.state().getValue()); }
        });
        binding.evidenceUse.setOnClickListener(v->{ if(currentContext()) { localError=0; model.upload(); } });
        ViewCompat.setAccessibilityHeading(binding.evidenceHeading,true); SharedMotion.install(view);
        ViewCompat.setOnApplyWindowInsetsListener(view,(root,insets)->{
            Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());
            Insets keyboard=insets.getInsets(WindowInsetsCompat.Type.ime());
            root.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,keyboard.bottom)); return WindowInsetsCompat.CONSUMED;
        });
        model.state().observe(getViewLifecycleOwner(),this::render);
        composer.state().observe(getViewLifecycleOwner(),ignored->render(model.state().getValue()));
        ((GoServeApplication)requireActivity().getApplication()).accountInvalidations().observe(getViewLifecycleOwner(),ignored->model.checkCurrentAccount());
        model.ensureContext(state.quote,state.options,getArguments()==null?null:getArguments().getString("requirement"));
    }
    private boolean currentContext() {
        if(composer==null || model==null)return false;
        composer.checkCurrentAccount(); model.checkCurrentAccount(); RequestComposeViewModel.State parent=composer.state().getValue();
        return model.state().getValue().mode!=RequestEvidenceViewModel.Mode.UNAVAILABLE
            && parent.mode==RequestComposeViewModel.Mode.EDITING && parent.quote!=null && parent.quote.id.equals(quoteId)
            && parent.options!=null && parent.options.evidence.available;
    }
    private void render(RequestEvidenceViewModel.State state) {
        if(binding==null || captures==null)return;
        RequestComposeViewModel.State parent=composer.state().getValue();
        boolean same=parent.mode==RequestComposeViewModel.Mode.EDITING && parent.quote!=null && parent.quote.id.equals(quoteId)
            && parent.options!=null && parent.options.evidence.available;
        boolean unavailable=state.mode==RequestEvidenceViewModel.Mode.UNAVAILABLE || !same;
        boolean uploading=state.mode==RequestEvidenceViewModel.Mode.UPLOADING;
        if(unavailable || !captures.pending() && state.mode!=RequestEvidenceViewModel.Mode.READING)captures.clear();
        setCancelable(!uploading && !captures.pending());
        binding.evidenceBack.setEnabled(!uploading && !captures.pending());
        binding.evidenceChoose.setEnabled(!unavailable && !state.busy() && !captures.pending());
        boolean jpeg=same && parent.options.evidence.mimeTypes.contains("image/jpeg");
        visible(binding.evidenceCamera,jpeg); binding.evidenceCamera.setEnabled(!unavailable && !state.busy() && !captures.pending());
        if(same) {
            java.util.List<String> types=new java.util.ArrayList<>();
            for(String mime:parent.options.evidence.mimeTypes)types.add("application/pdf".equals(mime)?"PDF":"image/png".equals(mime)?"PNG":"JPEG");
            binding.evidencePolicy.setText(getString(R.string.evidence_policy,android.text.TextUtils.join(", ",types),
                android.text.format.Formatter.formatShortFileSize(requireContext(),parent.options.evidence.maxBytes)));
        } else binding.evidencePolicy.setText("");
        visible(binding.evidenceFile,state.name!=null && !unavailable);
        binding.evidenceName.setText(state.name==null || unavailable?"":getString(R.string.evidence_file_detail,state.name,
            android.text.format.Formatter.formatShortFileSize(requireContext(),state.sizeBytes)));
        visible(binding.evidenceLoading,state.busy() && !unavailable);
        visible(binding.evidenceStatus,state.busy() && !unavailable);
        binding.evidenceStatus.setText(uploading?R.string.evidence_uploading:R.string.evidence_reading);
        visible(binding.evidenceCameraNotice,cameraUnavailable);
        int error=unavailable?"STALE_ACCOUNT".equals(state.error) || "STALE_ACCOUNT".equals(parent.error)?R.string.request_live_account:
            !same?R.string.evidence_changed:R.string.evidence_unavailable:localError!=0?localError:state.error!=null?errorText(state.error):0;
        visible(binding.evidenceError,error!=0); binding.evidenceErrorText.setText(error==0?"":getString(error));
        binding.evidenceUse.setText(state.mode==RequestEvidenceViewModel.Mode.ERROR && state.name!=null?R.string.evidence_retry:R.string.evidence_use);
        binding.evidenceUse.setEnabled(!unavailable && !state.busy() && !captures.pending() && state.prepared!=null && state.name!=null);
        if(state.mode==RequestEvidenceViewModel.Mode.COMPLETE && state.result!=null && same && !resultHandled) {
            resultHandled=true;
            composer.addEvidence(state.result);
            for(RequestData.Evidence file:composer.state().getValue().evidence)if(file.id.equals(state.result.id)) { dismiss(); return; }
        }
    }
    private static int errorText(String code) {
        switch(code) {
            case "UPLOAD_TOO_LARGE": return R.string.evidence_too_large;
            case "DEVICE_FILE_LIMIT": return R.string.evidence_device_limit;
            case "FILE_UNAVAILABLE": return R.string.evidence_unreadable;
            case "CONFIGURATION_REQUIRED": case "POLICY_BLOCKED": return R.string.evidence_unavailable;
            case "UPLOAD_INVALID": case "VALIDATION_FAILED": return R.string.evidence_invalid;
            case "QUOTE_STALE": return R.string.evidence_changed;
            default: return R.string.evidence_network;
        }
    }
    private static void visible(View view,boolean show) { view.setVisibility(show?View.VISIBLE:View.GONE); }
    @Override public void onStart() {
        super.onStart();
        if(getDialog()!=null && getDialog().getWindow()!=null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
            WindowCompat.setDecorFitsSystemWindows(getDialog().getWindow(),false); ViewCompat.requestApplyInsets(binding.getRoot());
        }
    }
    @Override public void onDestroyView() { binding=null; super.onDestroyView(); }
}
