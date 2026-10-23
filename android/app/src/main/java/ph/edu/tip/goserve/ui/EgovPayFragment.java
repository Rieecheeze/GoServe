package ph.edu.tip.goserve.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.PaymentRepository;
import ph.edu.tip.goserve.databinding.FragmentEgovPayBinding;
import ph.edu.tip.goserve.domain.PaymentStatus;

public final class EgovPayFragment extends Fragment {
    private FragmentEgovPayBinding binding;
    private EgovPayViewModel model;
    private ViewModelProvider.Factory factory;
    private boolean browserOpened;
    public EgovPayFragment() {}
    EgovPayFragment(ViewModelProvider.Factory factory) { this.factory=factory; }
    public static EgovPayFragment create(String requestId) {
        EgovPayFragment fragment=new EgovPayFragment(); Bundle args=new Bundle(); args.putString("request_id",requestId); fragment.setArguments(args); return fragment;
    }
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved) {
        binding=FragmentEgovPayBinding.inflate(inflater,parent,false); return binding.getRoot();
    }
    @Override public void onViewCreated(View view,Bundle saved) {
        ((MainActivity)requireActivity()).setRoleNavigationVisible(false);
        model=new ViewModelProvider(this,factory==null?getDefaultViewModelProviderFactory():factory).get(EgovPayViewModel.class);
        OnBackPressedCallback back=new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                model.checkCurrentAccount(); if(!model.state().getValue().canLeave())return;
                setEnabled(false); requireActivity().getOnBackPressedDispatcher().onBackPressed();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),back);
        binding.egovpayBack.setOnClickListener(v->requireActivity().getOnBackPressedDispatcher().onBackPressed());
        binding.egovpayPrepare.setOnClickListener(v->model.start());
        binding.egovpayRefresh.setOnClickListener(v->model.refresh());
        binding.egovpayReturn.setOnClickListener(v->{
            model.checkCurrentAccount();
            if(model.state().getValue().canReturnToRequest())getParentFragmentManager().popBackStack();
        });
        binding.egovpayBrowser.setOnClickListener(v->openCheckout());
        ViewCompat.setAccessibilityHeading(binding.egovpayHeading,true); SharedMotion.install(view);
        model.state().observe(getViewLifecycleOwner(),this::render);
        ((GoServeApplication)requireActivity().getApplication()).accountInvalidations().observe(getViewLifecycleOwner(),ignored->model.checkCurrentAccount());
        model.ensureRequest(getArguments()==null?null:getArguments().getString("request_id"));
    }
    private void openCheckout() {
        String url=model.browserUrl(); if(url==null || browserOpened)return;
        try {
            PaymentRepository.validatedCheckoutUrl(url);
            Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(url)); intent.addCategory(Intent.CATEGORY_BROWSABLE);
            browserOpened=true; startActivity(intent);
        } catch(ActivityNotFoundException | SecurityException | PaymentRepository.WorkflowException unavailable) {
            browserOpened=false; model.browserUnavailable();
        }
    }
    private void render(EgovPayViewModel.State state) {
        if(binding==null)return;
        boolean unavailable=state.mode==EgovPayViewModel.Mode.UNAVAILABLE;
        int heading=state.paid()?R.string.egovpay_paid:state.payment==null?R.string.egovpay_title:
            state.payment.status==PaymentStatus.FAILED?R.string.egovpay_failed_title:
            state.payment.status==PaymentStatus.CANCELLED?R.string.egovpay_cancelled_title:
            state.payment.status==PaymentStatus.EXPIRED?R.string.egovpay_expired_title:R.string.egovpay_pending;
        binding.egovpayHeading.setText(heading);
        binding.egovpayBack.setEnabled(state.canLeave());
        binding.egovpayLoading.setVisibility(state.busy()?View.VISIBLE:View.GONE);
        binding.egovpaySummary.setVisibility(state.payment==null?View.GONE:View.VISIBLE);
        binding.egovpayAmount.setText(state.payment==null?"":getString(R.string.egovpay_amount,state.payment.amount.toPlainString()));
        int message=state.mode==EgovPayViewModel.Mode.STARTING?R.string.egovpay_starting:state.busy()?R.string.egovpay_loading:
            unavailable?"STALE_ACCOUNT".equals(state.error)?R.string.request_live_account:R.string.request_live_forbidden:
            state.uncertain?"CHECKOUT_UNCONFIRMED".equals(state.error)?R.string.egovpay_reconciliation:R.string.egovpay_uncertain:state.paid()?R.string.egovpay_paid_body:
            "BROWSER_UNAVAILABLE".equals(state.error)?R.string.egovpay_browser_error:
            "PROVIDER_UNAVAILABLE".equals(state.error) || "CONFIGURATION_REQUIRED".equals(state.error)?R.string.egovpay_configuration:
            state.error!=null?R.string.egovpay_error:
            state.payment!=null && state.payment.status==PaymentStatus.EXPIRED?R.string.egovpay_expired:
            state.payment!=null && state.payment.status==PaymentStatus.FAILED || "failed".equals(state.providerStatus)?R.string.egovpay_failed:
            state.payment!=null && state.payment.status==PaymentStatus.CANCELLED || "cancelled".equals(state.providerStatus)?R.string.egovpay_cancelled:
            "unknown".equals(state.providerStatus)?R.string.egovpay_unknown:
            state.checkoutUrl!=null?R.string.egovpay_ready:R.string.egovpay_pending_body;
        binding.egovpayStatus.setText(message);
        binding.egovpayPrepare.setVisibility(state.checkoutUrl==null && !state.paid() && !unavailable?View.VISIBLE:View.GONE);
        binding.egovpayPrepare.setEnabled(state.canStart()); binding.egovpayPrepare.setText(state.uncertain?R.string.egovpay_retry:R.string.egovpay_prepare);
        boolean browser=state.checkoutUrl!=null && state.payment!=null && state.payment.status==PaymentStatus.PENDING && !state.paid() && !unavailable;
        binding.egovpayBrowser.setVisibility(browser?View.VISIBLE:View.GONE); binding.egovpayBrowser.setEnabled(browser && !state.busy());
        binding.egovpayRefresh.setVisibility(unavailable?View.GONE:View.VISIBLE); binding.egovpayRefresh.setEnabled(!state.busy() && !unavailable);
        binding.egovpayReturn.setVisibility(state.canReturnToRequest()?View.VISIBLE:View.GONE); binding.egovpayReturn.setEnabled(state.canReturnToRequest());
    }
    @Override public void onResume() {
        super.onResume();
        browserOpened=false;
        if(model!=null) { model.checkCurrentAccount(); if(!model.state().getValue().busy())model.refresh(); }
    }
    @Override public void onDestroyView() {
        ((MainActivity)requireActivity()).setRoleNavigationVisible(true); binding=null; super.onDestroyView();
    }
}
