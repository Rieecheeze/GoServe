package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentTermsBinding;

public final class TermsFragment extends AccountFragment {
    private FragmentTermsBinding binding;
    public static TermsFragment readOnly() { TermsFragment fragment=new TermsFragment(); Bundle args=new Bundle(); args.putBoolean("read_only",true); fragment.setArguments(args); return fragment; }
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved) { binding=FragmentTermsBinding.inflate(inflater,parent,false); return binding.getRoot(); }
    @Override public void onViewCreated(View view,Bundle saved) {
        super.onViewCreated(view,saved);
        if (getArguments()!=null && getArguments().getBoolean("read_only") && model.state().getValue().route!=AuthViewModel.Route.TERMS) model.showTerms(true);
        else model.ensureTerms();
        binding.termsAgreement.setChecked(false);
        binding.termsAgreement.setOnCheckedChangeListener((button,checked) -> {
            AuthViewModel.State state=model.state().getValue();
            binding.acceptButton.setEnabled(checked && state.terms!=null && !state.loading && !model.readOnlyTerms());
        });
        binding.acceptButton.setOnClickListener(v -> { if (binding.termsAgreement.isChecked()) model.acceptTerms(); }); binding.retryButton.setOnClickListener(v -> model.loadTerms());
        binding.backButton.setOnClickListener(v -> { if (requireActivity() instanceof AuthActivity) model.back(); else requireActivity().getOnBackPressedDispatcher().onBackPressed(); });
        model.state().observe(getViewLifecycleOwner(),state -> {
            common(view,state); boolean available=state.terms!=null;
            binding.termsContent.setVisibility(available?View.VISIBLE:View.GONE); binding.termsVersion.setVisibility(available?View.VISIBLE:View.GONE);
            if (available) { binding.termsContent.setText(LegalTextRenderer.render(state.terms.content)); binding.termsVersion.setText(state.terms.version); }
            binding.termsStatus.setVisibility(available?View.GONE:View.VISIBLE);
            binding.termsStatus.setText(state.loading?R.string.account_terms_loading:R.string.account_terms_unavailable);
            binding.retryButton.setVisibility(!available&&!state.loading?View.VISIBLE:View.GONE);
            binding.acceptButton.setVisibility(model.readOnlyTerms()?View.GONE:View.VISIBLE);
            binding.termsAgreement.setVisibility(model.readOnlyTerms()?View.GONE:View.VISIBLE);
            binding.termsAgreement.setEnabled(available&&!state.loading&&!model.readOnlyTerms());
            binding.acceptButton.setEnabled(binding.termsAgreement.isChecked()&&available&&!state.loading&&!model.readOnlyTerms());
            binding.backButton.setText(model.readOnlyTerms()?R.string.account_back:R.string.account_terms_back);
            binding.subtitle.setText(model.readOnlyTerms()?R.string.account_terms_view_intro:R.string.account_terms_intro);
        });
    }
    @Override public void onDestroyView() { super.onDestroyView(); binding=null; }
}
