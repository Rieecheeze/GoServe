package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.ViewCompat;
import android.graphics.Bitmap;
import android.text.Editable;
import android.text.TextWatcher;
import java.util.Set;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.RealtimeDataSource;
import ph.edu.tip.goserve.databinding.FragmentPickupLiveBinding;
import ph.edu.tip.goserve.domain.FeeDeclaration;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import ph.edu.tip.goserve.R;

public final class PickupFragment extends Fragment {
    private ViewModelProvider.Factory factory;
    private PickupScanner scanner;
    private FragmentPickupLiveBinding binding;
    private PickupViewModel model;
    private OnBackPressedCallback backLock;
    private RealtimeDataSource.Subscription subscription;
    private RealtimeDataSource.Session subscribedSession;
    private boolean rendering;
    private Bitmap qr;
    private String payload;
    public PickupFragment() {}
    PickupFragment(ViewModelProvider.Factory factory,PickupScanner scanner) { this.factory=factory; this.scanner=scanner; }
    public static PickupFragment create(String id) { PickupFragment f=new PickupFragment(); Bundle args=new Bundle(); args.putString("request_id",id); f.setArguments(args); return f; }
    public boolean mutationPending() { return model!=null && model.mutationPending(); }
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle state) { binding=FragmentPickupLiveBinding.inflate(inflater,parent,false); return binding.getRoot(); }
    @Override public void onViewCreated(View view,Bundle saved) {
        model=new ViewModelProvider(this,factory==null?getDefaultViewModelProviderFactory():factory).get(PickupViewModel.class);
        if(scanner==null)scanner=(activity,result)->GmsBarcodeScanning.getClient(activity,new GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()).startScan()
            .addOnSuccessListener(barcode->result.found(barcode.getRawValue())).addOnCanceledListener(result::cancelled).addOnFailureListener(error->result.unavailable());
        binding.pickupBack.setOnClickListener(v->{ if(!mutationPending())requireActivity().getOnBackPressedDispatcher().onBackPressed(); });
        binding.pickupScan.setOnClickListener(v->{
            if(!model.beginScan())return; PickupViewModel current=model;
            try { scanner.scan(requireActivity(),new PickupScanner.Result() {
                public void found(String code) { current.scanResult(code); } public void cancelled() { current.scanCancelled(); } public void unavailable() { current.scannerUnavailable(); }
            }); } catch(RuntimeException unavailable) { current.scannerUnavailable(); }
        });
        binding.pickupInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence value,int start,int count,int after) {} public void afterTextChanged(Editable value) {}
            public void onTextChanged(CharSequence value,int start,int before,int count) { if(!rendering)model.editCode(value.toString()); }
        });
        binding.pickupPrimary.setOnClickListener(v->{
            switch(model.state().getValue().mode) {
                case AVAILABLE: case CLAIM: model.issue(); break;
                case INPUT: model.validateCode(); break;
                case REVIEW: model.confirmHandover(); break;
                case COMPLETE: requireActivity().getOnBackPressedDispatcher().onBackPressed(); break;
                default: break;
            }
        });
        binding.pickupEdit.setOnClickListener(v->model.editReview()); binding.pickupRefresh.setOnClickListener(v->model.refresh());
        backLock=new OnBackPressedCallback(false) { @Override public void handleOnBackPressed() { if(binding!=null)binding.pickupBody.announceForAccessibility(binding.pickupBody.getText()); } };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),backLock);
        SharedMotion.install(view); ViewCompat.setAccessibilityHeading(binding.pickupHeading,true);
        model.state().observe(getViewLifecycleOwner(),this::render);
        ((GoServeApplication)requireActivity().getApplication()).accountInvalidations().observe(getViewLifecycleOwner(),ignored->model.checkCurrentAccount());
        model.ensureRequest(getArguments()==null?null:getArguments().getString("request_id"));
    }
    private void render(PickupViewModel.State state) {
        if(binding==null)return; boolean student=model.student(),unavailable=state.mode==PickupViewModel.Mode.UNAVAILABLE,review=state.mode==PickupViewModel.Mode.REVIEW || state.mode==PickupViewModel.Mode.HANDING_OVER,complete=state.mode==PickupViewModel.Mode.COMPLETE;
        binding.pickupTitle.setText(student?R.string.pickup_title:R.string.pickup_staff_title);
        binding.pickupHeading.setText(complete?R.string.pickup_complete:review?R.string.pickup_review_heading:student?R.string.pickup_heading:R.string.pickup_staff_heading);
        boolean record=state.request!=null && !unavailable;
        binding.pickupReference.setText(record?getString(R.string.request_reference,state.request.reference):"");
        binding.pickupService.setText(record?state.request.snapshot.name:"");
        binding.pickupPayment.setText(record?getString(state.request.snapshot.fee==FeeDeclaration.FREE?R.string.pickup_payment_free:R.string.pickup_payment_paid):"");
        visible(binding.pickupPayment,record && !complete && state.mode!=PickupViewModel.Mode.BLOCKED);
        int body=student?R.string.pickup_available:R.string.pickup_scan_body;
        switch(state.mode) {
            case EMPTY: case LOADING: body=R.string.pickup_checking; break;
            case ISSUING: body=R.string.pickup_issuing; break;
            case CLAIM: body=R.string.pickup_claim_body; break;
            case SCANNING: body=R.string.pickup_scanning; break;
            case VALIDATING: body=R.string.pickup_validating; break;
            case REVIEW: body=state.uncertain?R.string.pickup_uncertain:R.string.pickup_review_body; break;
            case HANDING_OVER: body=R.string.pickup_handing_over; break;
            case COMPLETE: body=R.string.pickup_complete_body; break;
            case BLOCKED: body=state.error==null?R.string.pickup_changed:errorText(state.error); break;
            case UNAVAILABLE: body="STALE_ACCOUNT".equals(state.error)?R.string.pickup_account:R.string.pickup_forbidden; break;
            default: break;
        }
        binding.pickupBody.setText(review || complete?getString(body,record?state.request.reference:""):getString(body));
        boolean claim=state.mode==PickupViewModel.Mode.CLAIM && state.claim!=null && !unavailable;
        visible(binding.pickupQrCard,claim); binding.pickupCode.setText(claim?state.claim.displayCode:"");
        if(claim) {
            if(!state.claim.qrPayload.equals(payload)) { clearQr(); qr=PickupQr.render(state.claim.qrPayload,384); payload=state.claim.qrPayload; binding.pickupQrImage.setImageBitmap(qr); }
            String expires=java.text.DateFormat.getDateTimeInstance().format(java.util.Date.from(java.time.Instant.parse(state.claim.expiresAt)));
            binding.pickupExpiry.setText(getString(R.string.pickup_expires,expires));
        } else { binding.pickupExpiry.setText(""); clearQr(); }
        boolean input=!student && (state.mode==PickupViewModel.Mode.INPUT || state.mode==PickupViewModel.Mode.SCANNING || state.mode==PickupViewModel.Mode.VALIDATING);
        visible(binding.pickupInputCard,input); rendering=true;
        if(!state.code.contentEquals(binding.pickupInput.getText()))binding.pickupInput.setText(state.code); rendering=false;
        enabled(binding.pickupInput,state.mode==PickupViewModel.Mode.INPUT); enabled(binding.pickupScan,state.mode==PickupViewModel.Mode.INPUT);
        visible(binding.pickupEdit,state.mode==PickupViewModel.Mode.REVIEW && !state.uncertain);
        visible(binding.pickupRefresh,state.mode==PickupViewModel.Mode.BLOCKED);
        visible(binding.pickupMessage,state.error!=null && !unavailable); binding.pickupMessage.setText(state.error==null?"":getString(errorText(state.error)));
        visible(binding.pickupLoading,state.busy() && state.mode!=PickupViewModel.Mode.SCANNING);
        enabled(binding.pickupBack,!state.busy() && !state.uncertain); backLock.setEnabled(model.mutationPending());
        binding.pickupPrimary.setText(complete?R.string.pickup_return:review?state.uncertain?R.string.pickup_retry_handover:R.string.pickup_confirm:student?claim?R.string.pickup_recheck:R.string.pickup_issue:R.string.pickup_validate);
        enabled(binding.pickupPrimary,state.mode==PickupViewModel.Mode.AVAILABLE || claim || state.mode==PickupViewModel.Mode.INPUT || state.mode==PickupViewModel.Mode.REVIEW || complete);
    }
    private static int errorText(String error) {
        switch(error) {
            case "CODE_INVALID": return R.string.pickup_invalid; case "CODE_CONSUMED": return R.string.pickup_consumed; case "CODE_EXPIRED": return R.string.pickup_expired;
            case "INVALID_STATE": case "STALE_VERSION": case "NOT_FOUND": return R.string.pickup_changed;
            case "PAYMENT_REQUIRED": return R.string.pickup_payment_required; case "CONFIGURATION_REQUIRED": case "POLICY_BLOCKED": return R.string.pickup_policy;
            case "SCAN_CANCELLED": return R.string.pickup_scan_cancelled; case "SCANNER_UNAVAILABLE": return R.string.pickup_scan_unavailable;
            default: return R.string.pickup_network;
        }
    }
    private void clearQr() { if(binding!=null)binding.pickupQrImage.setImageDrawable(null); if(qr!=null)qr.recycle(); qr=null; payload=null; }
    @Override public void onStart() {
        super.onStart(); model.checkCurrentAccount(); RealtimeDataSource.Session session=((GoServeApplication)requireActivity().getApplication()).realtimeSession();
        if(session==null || subscription!=null)return; subscribedSession=session;
        subscription=session.subscribe(Set.of("requests","payments"),new RealtimeDataSource.Listener() {
            public void onInvalidated(RealtimeDataSource.Invalidation event) { if(binding!=null && subscribedSession==session && event!=null && event.generation==session.generation())model.refresh(); }
            public void onStateChanged(RealtimeDataSource.State state) {}
        });
    }
    @Override public void onStop() { subscribedSession=null; if(subscription!=null)subscription.close(); subscription=null; super.onStop(); }
    @Override public void onDestroyView() { clearQr(); binding=null; super.onDestroyView(); }
    private static void visible(View view,boolean show) { view.setVisibility(show?View.VISIBLE:View.GONE); }
    private static void enabled(View view,boolean enabled) { view.setEnabled(enabled); view.setAlpha(enabled?1f:0.45f); }
}
