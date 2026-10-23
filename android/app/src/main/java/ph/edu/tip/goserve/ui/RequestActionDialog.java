package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.DialogRequestActionBinding;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.RequestStatus;

public final class RequestActionDialog extends DialogFragment {
    static final String TAG="request_action";
    private DialogRequestActionBinding binding;
    private RequestActionViewModel model;
    private RequestDetailViewModel detail;
    private boolean rendering,resultHandled;
    @Override public void onCreate(Bundle saved) { super.onCreate(saved); setStyle(STYLE_NO_TITLE,R.style.Theme_GoServe); }
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved) {
        binding=DialogRequestActionBinding.inflate(inflater,parent,false); return binding.getRoot();
    }
    @Override public void onViewCreated(View view,Bundle saved) {
        RequestDetailFragment parent=(RequestDetailFragment)requireParentFragment();
        detail=new ViewModelProvider(parent).get(RequestDetailViewModel.class);
        model=new ViewModelProvider(this,parent.actionFactory()==null?getDefaultViewModelProviderFactory():parent.actionFactory()).get(RequestActionViewModel.class);
        binding.actionBack.setOnClickListener(v->{
            RequestActionViewModel.State state=model.state().getValue();
            if(state.mode==RequestActionViewModel.Mode.REVIEW && !state.uncertain)model.edit();
            else if(state.mode!=RequestActionViewModel.Mode.SUBMITTING && !state.uncertain)dismiss();
        });
        binding.actionRetryOptions.setOnClickListener(v->model.retryOptions());
        binding.actionReload.setOnClickListener(v->{
            model.checkCurrentAccount(); RequestActionViewModel.State state=model.state().getValue();
            if(state.mode==RequestActionViewModel.Mode.BLOCKED) { detail.refresh(); dismiss(); }
        });
        binding.actionEdit.setOnClickListener(v->model.edit());
        binding.actionConfirm.setOnClickListener(v->model.submit());
        binding.actionReason.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence text,int start,int count,int after) {}
            public void onTextChanged(CharSequence text,int start,int before,int count) { if(!rendering)model.setReason(text.toString()); }
            public void afterTextChanged(Editable value) {}
        });
        ViewCompat.setAccessibilityHeading(binding.actionService,true); SharedMotion.install(view);
        ViewCompat.setOnApplyWindowInsetsListener(view,(root,insets)->{
            Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());
            Insets keyboard=insets.getInsets(WindowInsetsCompat.Type.ime());
            root.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,keyboard.bottom)); return WindowInsetsCompat.CONSUMED;
        });
        model.state().observe(getViewLifecycleOwner(),this::render);
        ((GoServeApplication)requireActivity().getApplication()).accountInvalidations().observe(getViewLifecycleOwner(),ignored->model.checkCurrentAccount());
        RequestDetailViewModel.State current=detail.state().getValue();
        model.ensureRequest(current.error==null?current.request:null);
    }
    private List<RequestStatus> choices(RequestActionViewModel.State state) {
        List<RequestStatus> result=new ArrayList<>();
        if(state.mode!=RequestActionViewModel.Mode.EDITING || state.request==null || state.options==null)return result;
        RequestStatus status=state.request.status;
        if(model.officeId()!=null) {
            if(status==RequestStatus.SUBMITTED || status==RequestStatus.AWAITING_PAYMENT)result.add(RequestStatus.PROCESSING);
            if(status==RequestStatus.PROCESSING)result.add(RequestStatus.READY_FOR_PICKUP_DOWNLOAD);
            if(available(state,"reject"))result.add(RequestStatus.REJECTED);
        }
        if(available(state,"cancel"))result.add(RequestStatus.CANCELLED);
        return result;
    }
    private static boolean available(RequestActionViewModel.State state,String name) {
        RequestData.ActionOptions option=state.options.actions.get(name); return option!=null && option.available;
    }
    private void render(RequestActionViewModel.State state) {
        if(binding==null)return; rendering=true;
        boolean unavailable=state.mode==RequestActionViewModel.Mode.UNAVAILABLE;
        boolean review=state.mode==RequestActionViewModel.Mode.REVIEW || state.mode==RequestActionViewModel.Mode.SUBMITTING;
        boolean editable=state.mode==RequestActionViewModel.Mode.EDITING || state.mode==RequestActionViewModel.Mode.LOADING;
        setCancelable(!review); binding.actionBack.setEnabled(state.mode!=RequestActionViewModel.Mode.SUBMITTING && !state.uncertain);
        binding.actionReference.setText(state.request==null?"":state.request.reference);
        binding.actionService.setText(state.request==null?"":state.request.snapshot.name);
        binding.actionStatus.setText(state.request==null?"":state.request.status.wireValue);
        if(!binding.actionReason.getText().toString().equals(state.reason))binding.actionReason.setText(state.reason);
        binding.actionReason.setEnabled(editable); visible(binding.actionReasonLabel,!unavailable); visible(binding.actionReason,!unavailable);
        binding.actionChoices.removeAllViews(); List<RequestStatus> targets=choices(state);
        for(RequestStatus target:targets) {
            MaterialButton action=new MaterialButton(requireContext(),null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
            action.setText(target==RequestStatus.PROCESSING?R.string.action_process:target==RequestStatus.READY_FOR_PICKUP_DOWNLOAD?R.string.action_ready:
                target==RequestStatus.CANCELLED?R.string.action_cancel:R.string.action_reject);
            ViewGroup.MarginLayoutParams params=new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin=Math.round(8*getResources().getDisplayMetrics().density); action.setLayoutParams(params);
            action.setOnClickListener(v->model.review(target)); binding.actionChoices.addView(action);
        }
        int notice=unavailable?"STALE_ACCOUNT".equals(state.error)?R.string.request_live_account:R.string.request_live_forbidden:
            state.uncertain?R.string.action_uncertain:state.mode==RequestActionViewModel.Mode.SUBMITTING?R.string.action_saving:
            state.mode==RequestActionViewModel.Mode.LOADING?R.string.action_loading:state.error!=null?errorText(state.error):
            editable && state.options!=null && targets.isEmpty()?R.string.action_none:0;
        visible(binding.actionNotice,notice!=0); binding.actionNotice.setText(notice==0?"":getString(notice));
        visible(binding.actionLoading,state.busy());
        visible(binding.actionRetryOptions,state.mode==RequestActionViewModel.Mode.EDITING && state.options==null && state.error!=null);
        visible(binding.actionReload,state.mode==RequestActionViewModel.Mode.BLOCKED);
        visible(binding.actionEdit,review && !state.uncertain && !state.busy());
        visible(binding.actionReview,review && state.intent!=null);
        binding.actionReview.setText(!review || state.intent==null?"":getString(state.uncertain || state.mode==RequestActionViewModel.Mode.SUBMITTING?
            R.string.action_review_sent:R.string.action_review,state.request.status.wireValue,state.intent.target.wireValue));
        binding.actionConfirm.setText(state.uncertain?R.string.action_retry:R.string.action_confirm);
        binding.actionConfirm.setEnabled(state.mode==RequestActionViewModel.Mode.REVIEW && state.intent!=null);
        rendering=false;
        if(state.mode==RequestActionViewModel.Mode.COMPLETE && state.result!=null && !resultHandled) {
            resultHandled=true; detail.refresh(); dismiss();
        }
    }
    private static int errorText(String error) {
        switch(error) {
            case "VALIDATION_FAILED": return R.string.action_reason_required;
            case "STALE_VERSION": case "INVALID_STATE": return R.string.action_stale;
            case "PAYMENT_REQUIRED": return R.string.action_payment_blocked;
            case "UPLOAD_INVALID": return R.string.action_file_blocked;
            case "POLICY_BLOCKED": case "CONFIGURATION_REQUIRED": return R.string.action_policy_blocked;
            default: return R.string.action_read_failed;
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
