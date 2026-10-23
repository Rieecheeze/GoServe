package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.core.view.ViewCompat;
import android.text.Editable;
import android.text.TextWatcher;
import java.util.Set;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.RealtimeDataSource;
import ph.edu.tip.goserve.databinding.FragmentTellerPaymentBinding;
import ph.edu.tip.goserve.domain.PaymentData;
import ph.edu.tip.goserve.domain.PaymentStatus;

public final class TellerPaymentFragment extends Fragment {
    public static final String ARG_PAYMENT_ID="payment_id";
    private static final String ARG_PENDING="pending";
    public static TellerPaymentFragment pending() {
        TellerPaymentFragment fragment=new TellerPaymentFragment(); Bundle args=new Bundle(); args.putBoolean(ARG_PENDING,true); fragment.setArguments(args); return fragment;
    }
    public static TellerPaymentFragment forPayment(String paymentId) {
        TellerPaymentFragment fragment=new TellerPaymentFragment(); Bundle args=new Bundle(); args.putString(ARG_PAYMENT_ID,paymentId); fragment.setArguments(args); return fragment;
    }
    private FragmentTellerPaymentBinding binding;
    private ViewModelProvider.Factory factory;
    private TellerPaymentViewModel model;
    private RealtimeDataSource.Subscription subscription;
    private RealtimeDataSource.Session subscribedSession;
    private boolean rendering,liveUnavailable;
    public TellerPaymentFragment() {}
    TellerPaymentFragment(ViewModelProvider.Factory factory) { this.factory=factory; }
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved) {
        binding=FragmentTellerPaymentBinding.inflate(inflater,parent,false); return binding.getRoot();
    }
    @Override public void onViewCreated(View view,Bundle saved) {
        androidx.lifecycle.ViewModelStoreOwner owner=detail()?this:requireActivity();
        model=new ViewModelProvider(owner,factory==null?requireActivity().getDefaultViewModelProviderFactory():factory).get(TellerPaymentViewModel.class);
        if(detail())model.ensurePayment(requireArguments().getString(ARG_PAYMENT_ID));
        ViewCompat.setAccessibilityHeading(binding.cashHeading,true);
        binding.cashReferenceInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence value,int start,int count,int after) {}
            public void onTextChanged(CharSequence value,int start,int before,int count) { if(!rendering)model.reference(value.toString()); }
            public void afterTextChanged(Editable value) {}
        });
        binding.cashSearchButton.setOnClickListener(v->search());
        binding.cashCheckStatusButton.setOnClickListener(v->search());
        binding.cashReceivedCheck.setOnCheckedChangeListener((button,checked)->{ if(!rendering)model.received(checked); });
        binding.cashReviewButton.setOnClickListener(v->model.reviewConfirmation());
        binding.cashConfirmButton.setOnClickListener(v->model.confirm());
        binding.cashBack.setOnClickListener(v->back()); binding.cashEditButton.setOnClickListener(v->edit());
        SharedMotion.install(view);
        androidx.activity.OnBackPressedCallback back=new androidx.activity.OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() {
                if(model.state().getValue().mode!=TellerPaymentViewModel.Mode.CONFIRMING)back();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),back);
        model.state().observe(getViewLifecycleOwner(),state->{ render(state); back.setEnabled(state.mode!=TellerPaymentViewModel.Mode.SEARCH); });
        ((GoServeApplication)requireActivity().getApplication()).accountInvalidations().observe(getViewLifecycleOwner(),ignored->model.checkCurrentAccount());
    }
    private void search() {
        binding.cashReferenceInput.clearFocus();
        android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if(keyboard!=null)keyboard.hideSoftInputFromWindow(binding.getRoot().getWindowToken(),0);
        model.search();
    }
    private void edit() {
        if(detail()) { getParentFragmentManager().popBackStack(); return; }
        if(emptyPending(model.state().getValue())) { ((MainActivity)requireActivity()).showTellerSearch(); return; }
        TellerPaymentViewModel.State state=model.state().getValue();
        if(state.mode==TellerPaymentViewModel.Mode.UNAVAILABLE) {
            MainActivity host=(MainActivity)requireActivity();
            if("STALE_ACCOUNT".equals(state.error))host.returnToSignIn(); else host.navigate(new MoreFragment(),false);
        } else {
            model.editReference();
            if(state.review!=null && state.review.status!=PaymentStatus.PENDING)model.reference("");
        }
    }
    private void back() {
        if(detail()) { getParentFragmentManager().popBackStack(); return; }
        if(model.state().getValue().mode==TellerPaymentViewModel.Mode.REVIEW)model.backToPayment();
        else edit();
    }
    private void render(TellerPaymentViewModel.State state) {
        if(binding==null)return; rendering=true;
        try {
            boolean unavailable=state.mode==TellerPaymentViewModel.Mode.UNAVAILABLE, busy=state.busy();
            PaymentData.CashReview review=state.review; boolean hasReview=review!=null;
            boolean pending=hasReview && review.status==PaymentStatus.PENDING, paid=hasReview && review.status==PaymentStatus.PAID;
            boolean confirming=state.mode==TellerPaymentViewModel.Mode.CONFIRMING, confirmed=state.mode==TellerPaymentViewModel.Mode.CONFIRMED;
            boolean found=pending && state.mode==TellerPaymentViewModel.Mode.FOUND;
            boolean compactReview=pending && !found;
            boolean emptyPending=emptyPending(state);
            boolean search=!hasReview && !unavailable && !emptyPending;
            int heading=confirming?R.string.cash_confirming:confirmed?R.string.cash_confirmed_title:paid?R.string.cash_already_paid:
                    found?R.string.cash_found:pending?R.string.cash_confirm_title:hasReview?R.string.cash_terminal_title:
                    "NOT_FOUND".equals(state.error)?R.string.cash_not_found:state.error!=null?R.string.cash_failed_title:R.string.cash_find;
            binding.cashHeading.setText(heading);
            if(emptyPending)binding.cashHeading.setText(R.string.cash_no_selection);
            binding.cashHeading.setTextAppearance("NOT_FOUND".equals(state.error)?R.style.TextAppearance_GoServe_Heading:R.style.TextAppearance_GoServe_Heading_Section);
            binding.cashToolbar.setText(found?R.string.cash_pending_toolbar:pending?R.string.cash_confirm_title:paid || state.error!=null?R.string.cash_payment_toolbar:R.string.cash_toolbar);
            visible(binding.cashHeading,!compactReview); visible(binding.cashBody,!compactReview);
            binding.cashBody.setTextAppearance(paid || state.error!=null?R.style.TextAppearance_GoServe_Body:R.style.TextAppearance_GoServe_Body_Small);
            binding.cashBody.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(),R.color.gs_secondary));
            binding.cashBody.setText(confirming?R.string.cash_confirming_body:confirmed?R.string.cash_confirmed_body:paid?R.string.cash_already_paid_body:
                    pending?R.string.cash_received_body:hasReview?R.string.cash_terminal_body:"NOT_FOUND".equals(state.error)?R.string.cash_not_found_body:
                    state.error!=null?R.string.cash_failed_body:R.string.cash_find_body);
            if(found)binding.cashBody.setText(getString(R.string.cash_service_office,review.reference,review.officeId));
            if(emptyPending)binding.cashBody.setText(R.string.cash_no_selection_body);
            if(emptyPending)binding.cashToolbar.setText(R.string.cash_pending_toolbar);
            visible(binding.cashReferenceLabel,search); visible(binding.cashReferenceInput,search); visible(binding.cashSearchButton,search);
            binding.cashReferenceInput.setEnabled(!busy && !unavailable); binding.cashSearchButton.setEnabled(!busy && !unavailable);
            if(!state.reference.contentEquals(binding.cashReferenceInput.getText())) {
                binding.cashReferenceInput.setText(state.reference); binding.cashReferenceInput.setSelection(state.reference.length());
            }
            visible(binding.cashAccessNotice,!hasReview && state.error==null && !emptyPending); visible(binding.cashReviewCard,hasReview);
            binding.cashReviewTitle.setText(found?R.string.cash_authoritative_amount:paid?R.string.cash_receipt_details:R.string.cash_review_title);
            binding.cashReviewTitle.setTextAppearance(found?R.style.TextAppearance_GoServe_Caption:R.style.TextAppearance_GoServe_Heading_Office);
            visible(binding.cashReceiptNotice,confirmed); visible(binding.cashReviewTitle,!compactReview); visible(binding.cashReviewReferenceLabel,!pending);
            visible(binding.cashReviewReference,!found);
            binding.cashReviewCard.setBackgroundResource(found?R.drawable.bg_cash_summary:compactReview?R.drawable.bg_teller_review:R.drawable.bg_teller_receipt);
            int padding=Math.round((found?18:compactReview?16:22)*getResources().getDisplayMetrics().density);
            binding.cashReviewCard.setPadding(padding,padding,padding,padding);
            binding.cashReviewReference.setTextAppearance(compactReview?R.style.TextAppearance_GoServe_Label:R.style.TextAppearance_GoServe_Body);
            int foreground=androidx.core.content.ContextCompat.getColor(requireContext(),found?R.color.cash_summary_ink:compactReview?android.R.color.white:R.color.gs_text);
            binding.cashReviewTitle.setTextColor(foreground);
            binding.cashReviewReference.setTextColor(foreground); binding.cashReviewAmount.setTextColor(foreground);
            binding.cashReviewAmount.setTextAppearance(found?R.style.TextAppearance_GoServe_Heading_Section:R.style.TextAppearance_GoServe_Body);
            binding.cashReviewAmount.setTextColor(foreground);
            int support=pending?foreground:androidx.core.content.ContextCompat.getColor(requireContext(),R.color.gs_secondary);
            binding.cashReviewService.setTextColor(support); binding.cashReviewStatus.setTextColor(support);
            if(hasReview) {
                binding.cashReviewReference.setText(review.reference);
                binding.cashReviewService.setText(getString(R.string.cash_service_office,review.serviceName,review.officeId));
                binding.cashReviewAmount.setText(getString(R.string.cash_money,review.currency,review.amount.toPlainString()));
                binding.cashReviewStatus.setText(getString(R.string.cash_payment_status,review.status.wireValue));
                if(found)binding.cashReviewStatus.setText(getString(R.string.cash_pending_request,review.requestId));
                binding.cashReviewTime.setText(paid?getString(R.string.cash_confirmed_at,review.confirmedAt):"");
                binding.cashReviewId.setText(paid?getString(R.string.cash_payment_identifier,review.paymentId):"");
            } else {
                binding.cashReviewReference.setText(""); binding.cashReviewService.setText(""); binding.cashReviewAmount.setText("");
                binding.cashReviewStatus.setText(""); binding.cashReviewTime.setText(""); binding.cashReviewId.setText("");
            }
            visible(binding.cashReviewActor,paid); visible(binding.cashReviewTime,paid); visible(binding.cashReviewId,paid);
            visible(binding.cashReviewService,!pending);
            visible(binding.cashReceivedCheck,pending); binding.cashReceivedCheck.setText(found?R.string.cash_received_pending:R.string.cash_received_full);
            binding.cashReceivedCheck.setChecked(state.received); binding.cashReceivedCheck.setEnabled(found && !busy);
            if(pending && !found)binding.cashReceivedCheck.jumpDrawablesToCurrentState();
            visible(binding.cashReviewButton,found); binding.cashReviewButton.setEnabled(state.canReview());
            visible(binding.cashDuplicateWarning,pending); visible(binding.cashConfirmHost,pending && !found);
            binding.cashDuplicateWarning.setBackgroundResource(found?R.drawable.bg_muted:R.drawable.bg_teller_receipt);
            binding.cashDuplicateTitle.setText(found?R.string.cash_no_duplicate_title:R.string.cash_duplicate_title);
            binding.cashDuplicateBody.setText(found?R.string.cash_no_duplicate_body:R.string.cash_duplicate_body);
            visible(binding.cashDuplicateIcon,!found);
            binding.cashDuplicateTitle.setTextAppearance(found?R.style.TextAppearance_GoServe_Label:R.style.TextAppearance_GoServe_Heading_Section);
            int noticePadding=Math.round((found?16:22)*getResources().getDisplayMetrics().density);
            binding.cashDuplicateWarning.setPadding(noticePadding,noticePadding,noticePadding,noticePadding);
            binding.cashConfirmButton.setEnabled(state.canConfirm()); binding.cashConfirmButton.setText(confirming?R.string.cash_confirming:R.string.cash_confirm);
            visible(binding.cashProgress,busy); binding.cashProgress.setText(confirming?R.string.cash_confirming:R.string.cash_searching);
            visible(binding.cashError,state.error!=null); binding.cashError.setText(errorMessage(state.error));
            visible(binding.cashConnection,liveUnavailable); visible(binding.cashCheckStatusButton,!unavailable && !busy && (state.error!=null || liveUnavailable && hasReview));
            visible(binding.cashEditButton,!busy && (hasReview && !pending || state.error!=null || emptyPending)); binding.cashEditButton.setEnabled(!busy);
            binding.cashEditButton.setText(unavailable?"STALE_ACCOUNT".equals(state.error)?R.string.request_sign_in:R.string.more:paid?R.string.cash_search_another:R.string.cash_edit_reference);
            if(emptyPending)binding.cashEditButton.setText(R.string.cash_search);
            if(detail() && paid)binding.cashEditButton.setText(R.string.cash_history_back);
            visible(binding.cashBack,hasReview && !found || state.error!=null); binding.cashBack.setEnabled(!confirming);
            if(requireActivity() instanceof MainActivity)((MainActivity)requireActivity()).setRoleNavigationVisible(!pending || found);
            if(found && !detail())((MainActivity)requireActivity()).markTellerPending();
        } finally { rendering=false; }
    }
    private int errorMessage(String code) {
        if(code==null)return R.string.cash_failed_body;
        switch(code) {
            case "VALIDATION_FAILED": return R.string.cash_reference_error;
            case "NOT_FOUND": return R.string.cash_not_found_body;
            case "CONFIGURATION_REQUIRED": case "POLICY_BLOCKED": return R.string.cash_configuration_error;
            case "FORBIDDEN": return R.string.cash_forbidden_error;
            case "STALE_ACCOUNT": return R.string.cash_stale_account_error;
            case "NETWORK_ERROR": return R.string.cash_network_error;
            default: return R.string.cash_changed_error;
        }
    }
    private boolean detail() { return getArguments()!=null && getArguments().containsKey(ARG_PAYMENT_ID); }
    private boolean emptyPending(TellerPaymentViewModel.State state) {
        return getArguments()!=null && getArguments().getBoolean(ARG_PENDING,false) && state.mode==TellerPaymentViewModel.Mode.SEARCH;
    }
    private static void visible(View view,boolean show) { view.setVisibility(show?View.VISIBLE:View.GONE); }
    @Override public void onStart() {
        super.onStart();
        RealtimeDataSource.Session session=((GoServeApplication)requireActivity().getApplication()).realtimeSession();
        if(session==null || subscription!=null)return; subscribedSession=session;
        subscription=session.subscribe(Set.of("payments"),new RealtimeDataSource.Listener() {
            public void onInvalidated(RealtimeDataSource.Invalidation event) {
                if(binding!=null && subscribedSession==session && event!=null && event.generation==session.generation())model.refreshAfterChange();
            }
            public void onStateChanged(RealtimeDataSource.State state) {
                if(binding==null || subscribedSession!=session)return;
                liveUnavailable=state==RealtimeDataSource.State.RECONNECTING || state==RealtimeDataSource.State.DEGRADED
                        || state==RealtimeDataSource.State.FAILED || state==RealtimeDataSource.State.AUTH_REQUIRED;
                render(model.state().getValue());
            }
        });
    }
    @Override public void onStop() {
        subscribedSession=null; if(subscription!=null)subscription.close(); subscription=null; super.onStop();
    }
    @Override public void onDestroyView() {
        if(requireActivity() instanceof MainActivity)((MainActivity)requireActivity()).setRoleNavigationVisible(true);
        binding=null; super.onDestroyView();
    }
}
