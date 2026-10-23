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
import java.util.Set;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.RealtimeDataSource;
import ph.edu.tip.goserve.databinding.DialogStudentDeliveryBinding;
import ph.edu.tip.goserve.domain.FeeDeclaration;
import ph.edu.tip.goserve.R;

public final class StudentDeliveryDialog extends DialogFragment {
    static final String TAG="student_delivery";
    private DialogStudentDeliveryBinding binding;
    private StudentDeliveryViewModel model;
    private RequestDetailViewModel detail;
    private RealtimeDataSource.Subscription subscription;
    private RealtimeDataSource.Session subscribedSession;
    private final ActivityResultLauncher<String> picker=registerForActivityResult(new ActivityResultContracts.CreateDocument("application/pdf"),uri->{
        if(model!=null)model.destinationResult(uri);
    });
    @Override public void onCreate(Bundle saved) { super.onCreate(saved); setStyle(STYLE_NO_TITLE,R.style.Theme_GoServe); }
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved) { binding=DialogStudentDeliveryBinding.inflate(inflater,parent,false); return binding.getRoot(); }
    @Override public void onViewCreated(View view,Bundle saved) {
        RequestDetailFragment parent=(RequestDetailFragment)requireParentFragment(); detail=new ViewModelProvider(parent).get(RequestDetailViewModel.class);
        model=new ViewModelProvider(this,parent.filesFactory()==null?getDefaultViewModelProviderFactory():parent.filesFactory()).get(StudentDeliveryViewModel.class);
        binding.deliveryBack.setOnClickListener(v->{
            StudentDeliveryViewModel.State state=model.state().getValue();
            if(state.mode==StudentDeliveryViewModel.Mode.RECEIPT_REVIEW && !state.uncertain)model.editReceipt();
            else if(!state.busy() && !state.uncertain && state.mode!=StudentDeliveryViewModel.Mode.PICKER)dismiss();
        });
        binding.deliveryPrimary.setOnClickListener(v->{
            model.checkCurrentAccount();
            switch(model.state().getValue().mode) {
                case AVAILABLE: model.prepareDownload(); break;
                case DESTINATION_READY:
                    String name=model.takeSaveName(); if(name==null)return;
                    try { picker.launch(name); } catch(android.content.ActivityNotFoundException | SecurityException unavailable) { model.pickerUnavailable(); } break;
                case LOCAL_SAVED: model.retryAcknowledgement(); break;
                case RECEIPT_REVIEW: model.confirmReceipt(); break;
                case COMPLETE: dismiss(); break;
                default: break;
            }
        });
        binding.deliveryReview.setOnClickListener(v->model.reviewReceipt()); binding.deliveryEdit.setOnClickListener(v->model.editReceipt()); binding.deliveryReload.setOnClickListener(v->model.refresh());
        ViewCompat.setAccessibilityHeading(binding.deliveryHeading,true); SharedMotion.install(view);
        ViewCompat.setOnApplyWindowInsetsListener(view,(root,insets)->{
            Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());
            root.setPadding(bars.left,bars.top,bars.right,bars.bottom); return WindowInsetsCompat.CONSUMED;
        });
        model.state().observe(getViewLifecycleOwner(),this::render);
        ((GoServeApplication)requireActivity().getApplication()).accountInvalidations().observe(getViewLifecycleOwner(),ignored->model.checkCurrentAccount());
        RequestDetailViewModel.State current=detail.state().getValue(); model.ensureRequest(current.error==null && current.request!=null?current.request.id:null);
    }
    private void render(StudentDeliveryViewModel.State state) {
        if(binding==null)return;
        boolean unavailable=state.mode==StudentDeliveryViewModel.Mode.UNAVAILABLE,complete=state.mode==StudentDeliveryViewModel.Mode.COMPLETE;
        boolean review=state.mode==StudentDeliveryViewModel.Mode.RECEIPT_REVIEW || state.mode==StudentDeliveryViewModel.Mode.CONFIRMING;
        boolean locked=state.busy() || state.uncertain || state.mode==StudentDeliveryViewModel.Mode.PICKER;
        setCancelable(!locked && !review); enabled(binding.deliveryBack,!locked);
        binding.deliveryHeading.setText(complete?R.string.delivery_complete:R.string.delivery_title);
        binding.deliveryReference.setText(state.request==null || unavailable?"":getString(R.string.request_reference,state.request.reference));
        boolean file=state.file!=null && !unavailable && state.mode!=StudentDeliveryViewModel.Mode.BLOCKED;
        visible(binding.deliveryFileCard,file);
        binding.deliveryService.setText(file?state.request.snapshot.name:"");
        binding.deliveryMetadata.setText(file?getString(R.string.delivery_metadata,android.text.format.Formatter.formatShortFileSize(requireContext(),state.file.sizeBytes),state.file.version):"");
        visible(binding.deliveryPayment,state.request!=null && !unavailable && !complete);
        binding.deliveryPayment.setText(state.request==null?"":getString(state.request.snapshot.fee==FeeDeclaration.FREE?R.string.delivery_payment_free:R.string.delivery_payment_paid));
        int body=R.string.delivery_available;
        switch(state.mode) {
            case EMPTY: case LOADING: body=R.string.delivery_loading; break;
            case TRANSFERRING: body=R.string.delivery_transfer; break;
            case DESTINATION_READY: body=R.string.delivery_choose_body; break;
            case PICKER: body=R.string.delivery_picker; break;
            case SAVING: body=R.string.delivery_copying; break;
            case ACKNOWLEDGING: body=R.string.delivery_acknowledging; break;
            case LOCAL_SAVED: body=R.string.delivery_local_saved; break;
            case SAVED: body=R.string.delivery_saved; break;
            case RECEIPT_REVIEW: body=state.uncertain?R.string.delivery_receipt_uncertain:R.string.delivery_receipt_body; break;
            case CONFIRMING: body=R.string.delivery_confirming; break;
            case COMPLETE: body=R.string.delivery_complete_body; break;
            case BLOCKED: body=state.localSaved?R.string.delivery_local_saved:state.error==null?R.string.delivery_changed:errorText(state); break;
            case UNAVAILABLE: body="STALE_ACCOUNT".equals(state.error)?R.string.delivery_account:R.string.request_live_forbidden; break;
        }
        binding.deliveryBody.setText(review || complete?getString(body,state.request==null?"":state.request.reference):getString(body));
        visible(binding.deliverySavedName,state.localSaved && state.savedName!=null && !unavailable); binding.deliverySavedName.setText(state.savedName==null?"":getString(R.string.delivery_saved_name,state.savedName));
        visible(binding.deliveryMessage,state.error!=null && !unavailable && (state.mode!=StudentDeliveryViewModel.Mode.BLOCKED || state.localSaved));
        binding.deliveryMessage.setText(state.error==null?"":getString(errorText(state)));
        visible(binding.deliveryPartial,state.partialMayRemain); visible(binding.deliveryLoading,state.busy());
        visible(binding.deliveryReview,state.mode==StudentDeliveryViewModel.Mode.AVAILABLE || state.mode==StudentDeliveryViewModel.Mode.DESTINATION_READY || state.mode==StudentDeliveryViewModel.Mode.SAVED);
        visible(binding.deliveryEdit,state.mode==StudentDeliveryViewModel.Mode.RECEIPT_REVIEW && !state.uncertain);
        visible(binding.deliveryReload,state.mode==StudentDeliveryViewModel.Mode.BLOCKED);
        int primary=complete?R.string.delivery_return:review?state.uncertain?R.string.delivery_receipt_retry:R.string.delivery_receipt_confirm:
            state.mode==StudentDeliveryViewModel.Mode.LOCAL_SAVED?R.string.delivery_ack_retry:state.mode==StudentDeliveryViewModel.Mode.DESTINATION_READY || state.mode==StudentDeliveryViewModel.Mode.PICKER?R.string.delivery_choose:R.string.delivery_prepare;
        binding.deliveryPrimary.setText(primary);
        visible(binding.deliveryPrimary,state.mode!=StudentDeliveryViewModel.Mode.SAVED);
        enabled(binding.deliveryPrimary,complete || state.mode==StudentDeliveryViewModel.Mode.AVAILABLE || state.mode==StudentDeliveryViewModel.Mode.DESTINATION_READY
            || state.mode==StudentDeliveryViewModel.Mode.LOCAL_SAVED || state.mode==StudentDeliveryViewModel.Mode.RECEIPT_REVIEW);
    }
    private static int errorText(StudentDeliveryViewModel.State state) {
        switch(state.error) {
            case "SAVE_CANCELLED": return R.string.delivery_cancelled;
            case "DOCUMENT_SAVE_FAILED": return R.string.delivery_save_failed;
            case "NO_DOCUMENT_PROVIDER": return R.string.delivery_provider_missing;
            case "DEVICE_STORAGE_LIMIT": return R.string.delivery_device_limit;
            case "PAYMENT_REQUIRED": return R.string.delivery_payment_required;
            case "CONFIGURATION_REQUIRED": case "POLICY_BLOCKED": return R.string.delivery_not_configured;
            case "NOT_FOUND": return R.string.delivery_missing;
            case "INVALID_STATE": case "STALE_VERSION": case "UPLOAD_INVALID": return R.string.delivery_changed;
            case "DOWNLOAD_UNCONFIRMED": return state.receipt!=null?R.string.delivery_unconfirmed:state.localSaved?R.string.delivery_ack_wait:R.string.delivery_integrity_failed;
            default: return R.string.delivery_failed;
        }
    }
    @Override public void onStart() {
        super.onStart(); model.checkCurrentAccount();
        if(getDialog()!=null && getDialog().getWindow()!=null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
            WindowCompat.setDecorFitsSystemWindows(getDialog().getWindow(),false); ViewCompat.requestApplyInsets(binding.getRoot());
        }
        RealtimeDataSource.Session session=((GoServeApplication)requireActivity().getApplication()).realtimeSession();
        if(session==null || subscription!=null)return; subscribedSession=session;
        subscription=session.subscribe(Set.of("requests","payments","request_files"),new RealtimeDataSource.Listener() {
            public void onInvalidated(RealtimeDataSource.Invalidation event) { if(binding!=null && subscribedSession==session && event!=null && event.generation==session.generation())model.refresh(); }
            public void onStateChanged(RealtimeDataSource.State value) {}
        });
    }
    @Override public void onStop() { subscribedSession=null; if(subscription!=null)subscription.close(); subscription=null; super.onStop(); }
    @Override public void onDismiss(android.content.DialogInterface dialog) {
        androidx.fragment.app.Fragment parent=getParentFragment();
        if(detail!=null && parent!=null && parent.getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))detail.refresh();
        super.onDismiss(dialog);
    }
    @Override public void onDestroyView() { binding=null; super.onDestroyView(); }
    private static void visible(View view,boolean show) { view.setVisibility(show?View.VISIBLE:View.GONE); }
    private static void enabled(View view,boolean enabled) { view.setEnabled(enabled); view.setAlpha(enabled?1f:0.45f); }
}
