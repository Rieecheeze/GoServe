package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;
import java.util.Set;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.RealtimeDataSource;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentRequestDetailBinding;
import ph.edu.tip.goserve.databinding.ItemRequestPhaseBinding;
import ph.edu.tip.goserve.databinding.FragmentRequestDetailLiveBinding;
import ph.edu.tip.goserve.databinding.ItemRequestPhaseLiveBinding;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.Office;
import ph.edu.tip.goserve.domain.FeeDeclaration;
import ph.edu.tip.goserve.domain.PaymentStatus;
import ph.edu.tip.goserve.domain.RequestStatus;
import ph.edu.tip.goserve.domain.RequestWorkflow;
import ph.edu.tip.goserve.domain.ServiceRequest;

public final class RequestDetailFragment extends Fragment {
    private androidx.lifecycle.ViewModelProvider.Factory factory;
    private androidx.lifecycle.ViewModelProvider.Factory actionFactory;
    private androidx.lifecycle.ViewModelProvider.Factory filesFactory;
    public RequestDetailFragment() {}
    RequestDetailFragment(androidx.lifecycle.ViewModelProvider.Factory factory) { this.factory=factory; }
    RequestDetailFragment(androidx.lifecycle.ViewModelProvider.Factory factory,androidx.lifecycle.ViewModelProvider.Factory actionFactory) { this(factory); this.actionFactory=actionFactory; }
    RequestDetailFragment(androidx.lifecycle.ViewModelProvider.Factory factory,androidx.lifecycle.ViewModelProvider.Factory actionFactory,androidx.lifecycle.ViewModelProvider.Factory filesFactory) { this(factory,actionFactory); this.filesFactory=filesFactory; }
    androidx.lifecycle.ViewModelProvider.Factory actionFactory() { return actionFactory; }
    androidx.lifecycle.ViewModelProvider.Factory filesFactory() { return filesFactory; }
    private static final String ARG_ID = "request_id";
    private FragmentRequestDetailBinding binding;
    private FragmentRequestDetailLiveBinding live;
    private RequestDetailViewModel model;
    private OfficeViewModel offices;
    private RealtimeDataSource.Subscription subscription;
    private RealtimeDataSource.Session subscribedSession;
    private boolean liveUnavailable;

    public static RequestDetailFragment create(String requestId) {
        RequestDetailFragment fragment = new RequestDetailFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_ID, requestId);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) {
        if(!((MainActivity)requireActivity()).isPreview()) {
            live=FragmentRequestDetailLiveBinding.inflate(inflater,parent,false);
            return live.getRoot();
        }
        binding = FragmentRequestDetailBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(View view, Bundle saved) {
        if(live!=null) { bindLive(view); return; }
        MainActivity host = (MainActivity) requireActivity();
        binding.requestDetailBack.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        String id = getArguments() == null ? null : getArguments().getString(ARG_ID);
        ServiceRequest request = host.isPreview() ? RequestPreviewCatalog.find(requireContext(), id) : null;
        if (request == null) { showUnavailable(); return; }
        binding.requestDetailName.setText(request.title);
        binding.requestDetailOffice.setText(request.officeName);
        binding.requestDetailReference.setText(getString(R.string.request_reference, request.reference));
        binding.requestDetailStatus.setText(request.status.wireValue);
        for (RequestStatus phase : RequestWorkflow.phases(request.feeDeclaration)) {
            ItemRequestPhaseBinding row = ItemRequestPhaseBinding.inflate(getLayoutInflater(), binding.requestPhases, false);
            RequestWorkflow.PhaseState state = RequestWorkflow.phaseState(request.feeDeclaration, request.status, phase);
            int stateText = state == RequestWorkflow.PhaseState.DONE ? R.string.request_phase_complete
                    : state == RequestWorkflow.PhaseState.CURRENT ? R.string.request_phase_current : R.string.request_phase_upcoming;
            int color = state == RequestWorkflow.PhaseState.DONE ? R.color.gs_request_complete
                    : state == RequestWorkflow.PhaseState.CURRENT ? R.color.gs_text : R.color.gs_secondary;
            row.phaseTitle.setText(phase.wireValue);
            row.phaseState.setText(stateText);
            row.phaseTitle.setTextColor(ContextCompat.getColor(requireContext(), color));
            row.phaseState.setTextColor(ContextCompat.getColor(requireContext(), color));
            row.phaseIcon.setImageResource(state == RequestWorkflow.PhaseState.DONE ? R.drawable.ic_request_done
                    : state == RequestWorkflow.PhaseState.CURRENT ? R.drawable.ic_request_current : R.drawable.ic_request_upcoming);
            row.getRoot().setContentDescription(getString(R.string.request_phase_description, phase.wireValue, getString(stateText)));
            row.getRoot().setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            row.phaseTitle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.phaseState.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            binding.requestPhases.addView(row.getRoot());
        }
        binding.requestFeeDeclaration.setText(RequestPreviewCatalog.feeLabel(request.feeDeclaration));
        binding.requestFeeNote.setText(request.feeDeclaration == FeeDeclaration.REQUIRED ? R.string.request_fee_required_note
                : request.feeDeclaration == FeeDeclaration.FREE ? R.string.request_fee_free_note : R.string.request_fee_pending_note);
        boolean paid = request.feeDeclaration == FeeDeclaration.REQUIRED && request.paymentStatus == PaymentStatus.PAID;
        binding.requestPaymentStatus.setVisibility(paid ? View.VISIBLE : View.GONE);
        binding.requestPaymentNote.setVisibility(paid ? View.VISIBLE : View.GONE);
        if (request.status == RequestStatus.READY_FOR_PICKUP_DOWNLOAD || request.status == RequestStatus.COMPLETED) {
            binding.requestFileAction.setVisibility(View.VISIBLE);
            binding.requestFileAction.setOnClickListener(v -> host.navigate(VerifiedFileFragment.create(request.id), true));
        }
    }

    private void bindLive(View view) {
        model=new ViewModelProvider(this,factory==null?getDefaultViewModelProviderFactory():factory).get(RequestDetailViewModel.class);
        offices=new ViewModelProvider(requireActivity()).get(OfficeViewModel.class);
        live.requestDetailBack.setOnClickListener(v->requireActivity().getOnBackPressedDispatcher().onBackPressed());
        live.requestDetailRefresh.setOnClickListener(v->model.refresh());
        live.requestEgovpayOpen.setOnClickListener(v->{
            model.checkCurrentAccount(); RequestDetailViewModel.State state=model.state().getValue();
            if(canCheckout(state))((MainActivity)requireActivity()).navigate(EgovPayFragment.create(state.request.id),true);
        });
        live.requestFileAction.setOnClickListener(v->{
            model.checkCurrentAccount(); RequestDetailViewModel.State state=model.state().getValue();
            if(state.mode!=RequestDetailViewModel.Mode.CONTENT || state.busy() || state.error!=null || state.request==null)return;
            if(canPickup(state.request))((MainActivity)requireActivity()).navigate(PickupFragment.create(state.request.id),true);
            else if(canReceivePdf(state.request) && getChildFragmentManager().findFragmentByTag(StudentDeliveryDialog.TAG)==null)new StudentDeliveryDialog().show(getChildFragmentManager(),StudentDeliveryDialog.TAG);
        });
        live.requestPdfOpen.setOnClickListener(v->{
            model.checkCurrentAccount(); RequestDetailViewModel.State state=model.state().getValue();
            if(state.mode!=RequestDetailViewModel.Mode.UNAVAILABLE && !state.busy() && state.error==null && state.request!=null && canUploadPdf(state.request)
                && getChildFragmentManager().findFragmentByTag(PdfUploadDialog.TAG)==null)new PdfUploadDialog().show(getChildFragmentManager(),PdfUploadDialog.TAG);
        });
        live.requestEvidenceOpen.setOnClickListener(v->{
            model.checkCurrentAccount(); RequestDetailViewModel.State state=model.state().getValue();
            if(state.mode==RequestDetailViewModel.Mode.UNAVAILABLE || state.busy() || state.error!=null || state.request==null)return;
            RequestFilesFragment destination=filesFactory==null?RequestFilesFragment.create(state.request.id):new RequestFilesFragment(filesFactory);
            if(destination.getArguments()==null) { Bundle args=new Bundle(); args.putString("request_id",state.request.id); destination.setArguments(args); }
            ((MainActivity)requireActivity()).navigate(destination,true);
        });
        live.requestActionOpen.setOnClickListener(v->{
            model.checkCurrentAccount(); RequestDetailViewModel.State state=model.state().getValue();
            if(state.mode!=RequestDetailViewModel.Mode.UNAVAILABLE && !state.busy() && state.error==null && state.request!=null
                && getChildFragmentManager().findFragmentByTag(RequestActionDialog.TAG)==null)
                new RequestActionDialog().show(getChildFragmentManager(),RequestActionDialog.TAG);
        });
        live.requestDetailRetry.setOnClickListener(v->{
            RequestDetailViewModel.State state=model.state().getValue();
            if(state.mode==RequestDetailViewModel.Mode.UNAVAILABLE) {
                MainActivity host=(MainActivity)requireActivity();
                if("STALE_ACCOUNT".equals(state.error))host.returnToSignIn(); else host.navigate(new MoreFragment(),false);
            } else model.retry();
        });
        ViewCompat.setAccessibilityHeading(live.requestDetailName,true);
        ViewCompat.setAccessibilityHeading(live.requestProgressTitle,true);
        ViewCompat.setAccessibilityHeading(live.requestFeeTitle,true);
        SharedMotion.install(view);
        model.state().observe(getViewLifecycleOwner(),this::renderLive);
        offices.state().observe(getViewLifecycleOwner(),ignored->renderLive(model.state().getValue()));
        ((GoServeApplication)requireActivity().getApplication()).accountInvalidations().observe(getViewLifecycleOwner(),ignored->model.checkCurrentAccount());
        model.ensureRequest(getArguments()==null?null:getArguments().getString(ARG_ID));
    }
    private void renderLive(RequestDetailViewModel.State state) {
        if(live==null)return;
        boolean unavailable=state.mode==RequestDetailViewModel.Mode.UNAVAILABLE;
        live.requestDetailRefresh.setEnabled(!unavailable && !state.busy() && state.requestId!=null);
        visible(live.requestDetailLoading,state.busy());
        boolean retry=state.error!=null || state.paymentError!=null;
        visible(live.requestDetailRetry,retry && !state.busy());
        live.requestDetailRetry.setText(unavailable?"STALE_ACCOUNT".equals(state.error)?R.string.request_sign_in:R.string.more:R.string.request_live_retry);
        int notice=state.error!=null?"STALE_ACCOUNT".equals(state.error)?R.string.request_live_account:
            "FORBIDDEN".equals(state.error)?R.string.request_live_forbidden:
            "NOT_FOUND".equals(state.error)?R.string.request_live_not_found:R.string.request_live_detail_failed:
            state.busy()?R.string.request_live_detail_loading:liveUnavailable?R.string.request_live_connection:0;
        visible(live.requestDetailNotice,notice!=0);
        live.requestDetailNotice.setText(notice==0?"":getString(notice));
        visible(live.requestDetailContent,state.request!=null && !unavailable);
        live.requestPhases.removeAllViews();
        if(state.request==null || unavailable) {
            live.requestDetailName.setText(""); live.requestDetailOffice.setText(""); live.requestDetailReference.setText("");
            live.requestDetailStatus.setText(""); live.requestFeeDeclaration.setText(""); live.requestFeeNote.setText("");
            live.requestPaymentStatus.setText(""); live.requestPaymentNote.setText("");
            visible(live.requestFileAction,false); visible(live.requestActionOpen,false); visible(live.requestEvidenceOpen,false); visible(live.requestPdfOpen,false); visible(live.requestEgovpayOpen,false); return;
        }
        RequestData.Record request=state.request;
        live.requestDetailName.setText(request.snapshot.name);
        String officeName=request.officeId;
        for(Office office:offices.state().getValue().offices)if(request.officeId.equals(office.id)) { officeName=office.name; break; }
        live.requestDetailOffice.setText(officeName);
        live.requestDetailReference.setText(getString(R.string.request_reference,request.reference));
        live.requestDetailStatus.setText(request.status.wireValue);
        java.util.List<RequestStatus> phases=RequestWorkflow.phases(request.snapshot.fee);
        for(RequestStatus phase:phases) {
            ItemRequestPhaseLiveBinding row=ItemRequestPhaseLiveBinding.inflate(getLayoutInflater(),live.requestPhases,false);
            RequestWorkflow.PhaseState progress=RequestWorkflow.phaseState(request.snapshot.fee,request.status,phase);
            int text=progress==RequestWorkflow.PhaseState.DONE?R.string.request_phase_complete:
                progress==RequestWorkflow.PhaseState.CURRENT?R.string.request_phase_current:R.string.request_phase_upcoming;
            int color=progress==RequestWorkflow.PhaseState.DONE?R.color.gs_request_complete:
                progress==RequestWorkflow.PhaseState.CURRENT?R.color.gs_text:R.color.gs_secondary;
            row.phaseTitle.setText(phase.wireValue); row.phaseState.setText(text);
            row.phaseTitle.setTextColor(ContextCompat.getColor(requireContext(),color));
            row.phaseState.setTextColor(ContextCompat.getColor(requireContext(),color));
            row.phaseIcon.setImageResource(progress==RequestWorkflow.PhaseState.DONE?R.drawable.ic_request_done:
                progress==RequestWorkflow.PhaseState.CURRENT?R.drawable.ic_request_current:R.drawable.ic_request_upcoming);
            row.getRoot().setContentDescription(getString(R.string.request_phase_description,phase.wireValue,getString(text)));
            row.getRoot().setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            if(phase==phases.get(phases.size()-1))((ViewGroup.MarginLayoutParams)row.getRoot().getLayoutParams()).bottomMargin=0;
            live.requestPhases.addView(row.getRoot());
        }
        live.requestFeeDeclaration.setText(RequestListFragment.feeLabel(requireContext(),request.snapshot));
        live.requestFeeNote.setText(request.snapshot.fee==FeeDeclaration.REQUIRED?R.string.request_fee_required_note:
            request.snapshot.fee==FeeDeclaration.FREE?R.string.request_live_fee_free_note:R.string.request_fee_pending_note);
        boolean paymentRelevant=request.snapshot.fee==FeeDeclaration.REQUIRED || state.payment!=null || state.paymentError!=null;
        visible(live.requestPaymentStatus,paymentRelevant); visible(live.requestPaymentNote,paymentRelevant);
        live.requestPaymentStatus.setText(state.confirmedPayment()?getString(R.string.request_live_payment_paid):
            state.busy() || state.error!=null || state.paymentError!=null?getString(R.string.request_live_payment_unknown):
            state.payment==null || state.payment.status==PaymentStatus.PENDING?getString(R.string.request_live_payment_pending):
            getString(R.string.request_live_payment_other,state.payment.status.wireValue));
        live.requestPaymentNote.setText(R.string.request_live_payment_note);
        visible(live.requestEgovpayOpen,canCheckout(state)); live.requestEgovpayOpen.setEnabled(canCheckout(state));
        visible(live.requestFileAction,canReceivePdf(request) || canPickup(request));
        live.requestFileAction.setText(canPickup(request)?((MainActivity)requireActivity()).profile().role==ph.edu.tip.goserve.domain.Role.STUDENT?R.string.pickup_view_code:R.string.pickup_review_handover:R.string.delivery_open);
        live.requestFileAction.setEnabled(!state.busy() && state.error==null);
        visible(live.requestEvidenceOpen,true); live.requestEvidenceOpen.setEnabled(!state.busy() && state.error==null);
        visible(live.requestPdfOpen,canUploadPdf(request)); live.requestPdfOpen.setEnabled(!state.busy() && state.error==null);
        visible(live.requestActionOpen,request.status!=RequestStatus.COMPLETED && request.status!=RequestStatus.CANCELLED && request.status!=RequestStatus.REJECTED);
        live.requestActionOpen.setEnabled(!state.busy() && state.error==null);
    }
    private static void visible(View view,boolean show) { view.setVisibility(show?View.VISIBLE:View.GONE); }
    private boolean canCheckout(RequestDetailViewModel.State state) {
        ph.edu.tip.goserve.domain.SessionProfile profile=((MainActivity)requireActivity()).profile();
        return state.mode==RequestDetailViewModel.Mode.CONTENT && state.error==null && state.paymentError==null
            && state.request!=null && state.request.status==RequestStatus.AWAITING_PAYMENT && state.payment!=null
            && state.payment.method==ph.edu.tip.goserve.domain.PaymentMethod.EGOVPAY && state.payment.status==PaymentStatus.PENDING
            && "PHP".equals(state.payment.currency)
            && profile!=null && profile.role==ph.edu.tip.goserve.domain.Role.STUDENT && profile.id.equals(state.request.ownerId);
    }
    private boolean canUploadPdf(RequestData.Record request) {
        ph.edu.tip.goserve.domain.SessionProfile profile=((MainActivity)requireActivity()).profile();
        return profile!=null && profile.role==ph.edu.tip.goserve.domain.Role.STAFF && request.officeId.equals(profile.officeId)
            && "digital".equals(request.deliveryMode) && (request.status==RequestStatus.PROCESSING || request.status==RequestStatus.READY_FOR_PICKUP_DOWNLOAD);
    }
    private boolean canReceivePdf(RequestData.Record request) {
        ph.edu.tip.goserve.domain.SessionProfile profile=((MainActivity)requireActivity()).profile();
        return profile!=null && profile.role==ph.edu.tip.goserve.domain.Role.STUDENT && profile.id.equals(request.ownerId)
            && "digital".equals(request.deliveryMode) && (request.status==RequestStatus.READY_FOR_PICKUP_DOWNLOAD || request.status==RequestStatus.COMPLETED);
    }
    private boolean canPickup(RequestData.Record request) {
        ph.edu.tip.goserve.domain.SessionProfile p=((MainActivity)requireActivity()).profile();
        return p!=null && "physical".equals(request.deliveryMode) && request.status==RequestStatus.READY_FOR_PICKUP_DOWNLOAD
            && (p.role==ph.edu.tip.goserve.domain.Role.STUDENT && p.id.equals(request.ownerId)
                || p.role==ph.edu.tip.goserve.domain.Role.STAFF && request.officeId.equals(p.officeId) && RequestListViewModel.permitted(p));
    }
    @Override public void onStart() {
        super.onStart(); if(live==null)return;
        if(!model.state().getValue().busy())model.refresh();
        RealtimeDataSource.Session session=((GoServeApplication)requireActivity().getApplication()).realtimeSession();
        if(session==null || subscription!=null)return; subscribedSession=session;
        subscription=session.subscribe(Set.of("requests","payments"),new RealtimeDataSource.Listener() {
            public void onInvalidated(RealtimeDataSource.Invalidation event) {
                if(live!=null && subscribedSession==session && event!=null && event.generation==session.generation())model.refresh();
            }
            public void onStateChanged(RealtimeDataSource.State value) {
                if(live==null || subscribedSession!=session)return;
                liveUnavailable=value==RealtimeDataSource.State.RECONNECTING || value==RealtimeDataSource.State.DEGRADED
                    || value==RealtimeDataSource.State.FAILED || value==RealtimeDataSource.State.AUTH_REQUIRED;
                renderLive(model.state().getValue());
            }
        });
    }
    @Override public void onStop() {
        subscribedSession=null; if(subscription!=null)subscription.close(); subscription=null; super.onStop();
    }

    private void showUnavailable() {
        binding.requestDetailHeading.setText(R.string.request_detail_unavailable);
        binding.requestDetailNotice.setText(R.string.request_detail_unavailable_body);
        binding.requestDetailName.setVisibility(View.GONE);
        binding.requestDetailOffice.setVisibility(View.GONE);
        binding.requestDetailReference.setVisibility(View.GONE);
        binding.requestDetailStatus.setVisibility(View.GONE);
        binding.requestProgressTitle.setVisibility(View.GONE);
        binding.requestFeeTitle.setVisibility(View.GONE);
        binding.requestFeeDeclaration.setVisibility(View.GONE);
        binding.requestFeeNote.setVisibility(View.GONE);
        binding.requestPaymentStatus.setVisibility(View.GONE);
        binding.requestPaymentNote.setVisibility(View.GONE);
    }

    @Override public void onDestroyView() { binding=null; live=null; super.onDestroyView(); }
}
