package ph.edu.tip.goserve.ui;

import android.os.Bundle;
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
import ph.edu.tip.goserve.data.ContentEvidenceSource;
import ph.edu.tip.goserve.databinding.DialogPdfUploadBinding;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.R;

public final class PdfUploadDialog extends DialogFragment {
    static final String TAG="pdf_upload";
    private DialogPdfUploadBinding binding;
    private PdfUploadViewModel model;
    private RequestDetailViewModel detail;
    private boolean resultHandled,pickerUnavailable;
    private final ActivityResultLauncher<String[]> picker=registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{
        if(uri==null || model==null)return; model.checkCurrentAccount();
        if(model.state().getValue().mode!=PdfUploadViewModel.Mode.EDITING || model.state().getValue().options==null)return;
        pickerUnavailable=false;
        try { model.select(new ContentEvidenceSource(requireContext(),uri)); }
        catch(IllegalArgumentException unreadable) { pickerUnavailable=true; render(model.state().getValue()); }
    });
    @Override public void onCreate(Bundle saved) { super.onCreate(saved); setStyle(STYLE_NO_TITLE,R.style.Theme_GoServe); }
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved) { binding=DialogPdfUploadBinding.inflate(inflater,parent,false); return binding.getRoot(); }
    @Override public void onViewCreated(View view,Bundle saved) {
        RequestDetailFragment parent=(RequestDetailFragment)requireParentFragment(); detail=new ViewModelProvider(parent).get(RequestDetailViewModel.class);
        model=new ViewModelProvider(this,parent.filesFactory()==null?getDefaultViewModelProviderFactory():parent.filesFactory()).get(PdfUploadViewModel.class);
        binding.pdfBack.setOnClickListener(v->{
            PdfUploadViewModel.State state=model.state().getValue();
            if(state.mode==PdfUploadViewModel.Mode.REVIEW && !state.uncertain)model.edit();
            else if(state.mode!=PdfUploadViewModel.Mode.UPLOADING && !state.uncertain)dismiss();
        });
        binding.pdfChoose.setOnClickListener(v->{
            model.checkCurrentAccount(); PdfUploadViewModel.State state=model.state().getValue();
            if(state.mode!=PdfUploadViewModel.Mode.EDITING || state.options==null)return;
            pickerUnavailable=false;
            try { picker.launch(new String[]{"application/pdf"}); }
            catch(android.content.ActivityNotFoundException | SecurityException unavailable) { pickerUnavailable=true; render(state); }
        });
        binding.pdfPrimary.setOnClickListener(v->{ if(model.state().getValue().mode==PdfUploadViewModel.Mode.REVIEW)model.confirm(); else model.review(); });
        binding.pdfEdit.setOnClickListener(v->model.edit()); binding.pdfReload.setOnClickListener(v->model.reload());
        ViewCompat.setAccessibilityHeading(binding.pdfSelectionTitle,true); SharedMotion.install(view);
        ViewCompat.setOnApplyWindowInsetsListener(view,(root,insets)->{
            Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());
            root.setPadding(bars.left,bars.top,bars.right,bars.bottom); return WindowInsetsCompat.CONSUMED;
        });
        model.state().observe(getViewLifecycleOwner(),this::render);
        ((GoServeApplication)requireActivity().getApplication()).accountInvalidations().observe(getViewLifecycleOwner(),ignored->model.checkCurrentAccount());
        RequestDetailViewModel.State current=detail.state().getValue(); model.ensureRequest(current.error==null && current.request!=null?current.request.id:null);
    }
    private void render(PdfUploadViewModel.State state) {
        if(binding==null)return;
        boolean unavailable=state.mode==PdfUploadViewModel.Mode.UNAVAILABLE;
        boolean review=state.mode==PdfUploadViewModel.Mode.REVIEW || state.mode==PdfUploadViewModel.Mode.UPLOADING;
        boolean uploading=state.mode==PdfUploadViewModel.Mode.UPLOADING;
        setCancelable(!review && !uploading && !state.uncertain); binding.pdfBack.setEnabled(!uploading && !state.uncertain);
        binding.pdfBack.setAlpha(binding.pdfBack.isEnabled()?1f:0.45f);
        binding.pdfChoose.setEnabled(state.mode==PdfUploadViewModel.Mode.EDITING && state.options!=null);
        binding.pdfChoose.setAlpha(binding.pdfChoose.isEnabled()?1f:0.45f);
        binding.pdfChoose.setText(state.name==null?R.string.pdf_choose:R.string.pdf_change);
        RequestData.ActionOptions option=state.options==null?null:state.options.actions.get("deliverable_upload");
        RequestData.FileOptions policy=option instanceof RequestData.FileOptions?(RequestData.FileOptions)option:null;
        binding.pdfPolicy.setText(policy!=null && policy.available && policy.maxBytes!=null?getString(R.string.pdf_policy,android.text.format.Formatter.formatShortFileSize(requireContext(),policy.maxBytes)):"");
        binding.pdfSelectionTitle.setText(review?R.string.pdf_review_title:state.name==null?R.string.pdf_empty_title:R.string.pdf_selected_title);
        binding.pdfSelectionBody.setText(state.name==null?R.string.pdf_empty_body:R.string.pdf_selected_body);
        binding.pdfName.setText(state.name==null || unavailable?"":getString(R.string.pdf_selected_name,state.name,android.text.format.Formatter.formatShortFileSize(requireContext(),state.sizeBytes)));
        visible(binding.pdfName,state.name!=null && !unavailable);
        visible(binding.pdfReview,review); binding.pdfReview.setText(getString(uploading?R.string.pdf_review_sent:state.uncertain?R.string.pdf_uncertain:R.string.pdf_review_body,state.request==null?"":state.request.reference));
        int message=unavailable?"STALE_ACCOUNT".equals(state.error)?R.string.request_live_account:R.string.request_live_forbidden:
            uploading?R.string.pdf_uploading:state.mode==PdfUploadViewModel.Mode.READING?R.string.pdf_reading:state.mode==PdfUploadViewModel.Mode.LOADING?R.string.pdf_loading:
            pickerUnavailable?R.string.pdf_picker_unavailable:state.error!=null?errorText(state.error):0;
        visible(binding.pdfMessage,message!=0); binding.pdfMessage.setText(message==0?"":getString(message)); visible(binding.pdfLoading,state.busy());
        visible(binding.pdfReload,state.mode==PdfUploadViewModel.Mode.BLOCKED || state.mode==PdfUploadViewModel.Mode.EDITING && state.options==null && state.error!=null);
        visible(binding.pdfEdit,review && !state.busy() && !state.uncertain);
        binding.pdfPrimary.setText(state.uncertain?R.string.pdf_retry:review?R.string.pdf_confirm:R.string.pdf_review_action);
        binding.pdfPrimary.setEnabled(state.intent!=null && (state.mode==PdfUploadViewModel.Mode.EDITING || state.mode==PdfUploadViewModel.Mode.REVIEW));
        binding.pdfPrimary.setAlpha(binding.pdfPrimary.isEnabled()?1f:0.45f);
        if(message!=0 && state.error!=null)binding.pdfMessage.post(()->{
            if(binding!=null)binding.pdfMessage.requestRectangleOnScreen(new android.graphics.Rect(0,0,binding.pdfMessage.getWidth(),binding.pdfMessage.getHeight()),false);
        });
        if(state.mode==PdfUploadViewModel.Mode.COMPLETE && state.result!=null && !resultHandled) { resultHandled=true; detail.refresh(); dismiss(); }
    }
    private static int errorText(String error) {
        switch(error) {
            case "CONFIGURATION_REQUIRED": case "POLICY_BLOCKED": return R.string.pdf_unavailable;
            case "STALE_VERSION": case "INVALID_STATE": case "NOT_FOUND": return R.string.pdf_changed;
            case "UPLOAD_INVALID": return R.string.pdf_invalid;
            case "VALIDATION_FAILED": return R.string.pdf_response_invalid;
            case "UPLOAD_TOO_LARGE": return R.string.pdf_too_large;
            case "DEVICE_FILE_LIMIT": return R.string.pdf_device_limit;
            case "FILE_UNAVAILABLE": return R.string.pdf_unreadable;
            default: return R.string.pdf_network;
        }
    }
    @Override public void onStart() {
        super.onStart(); model.checkCurrentAccount();
        if(getDialog()!=null && getDialog().getWindow()!=null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
            WindowCompat.setDecorFitsSystemWindows(getDialog().getWindow(),false); ViewCompat.requestApplyInsets(binding.getRoot());
        }
    }
    @Override public void onDismiss(android.content.DialogInterface dialog) {
        androidx.fragment.app.Fragment parent=getParentFragment();
        if(!resultHandled && detail!=null && parent!=null && parent.getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))detail.refresh();
        super.onDismiss(dialog);
    }
    @Override public void onDestroyView() { binding=null; super.onDestroyView(); }
    private static void visible(View view,boolean show) { view.setVisibility(show?View.VISIBLE:View.GONE); }
}
