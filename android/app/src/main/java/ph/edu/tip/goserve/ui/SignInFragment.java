package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import ph.edu.tip.goserve.BuildConfig;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.AuthRepository;
import ph.edu.tip.goserve.databinding.FragmentSignInBinding;
import ph.edu.tip.goserve.domain.AccountValidation;

public final class SignInFragment extends AccountFragment {
    private FragmentSignInBinding binding;
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved) { binding=FragmentSignInBinding.inflate(inflater,parent,false); return binding.getRoot(); }
    @Override public void onViewCreated(View view,Bundle saved) {
        super.onViewCreated(view,saved); binding.emailInput.setText(model.draft().email); binding.passwordInput.setText(model.signinPassword()); binding.passwordInput.setSaveFromParentEnabled(false);
        watch(binding.emailInput,model::setEmail); watch(binding.passwordInput,model::setSigninPassword);
        binding.googleButton.setOnClickListener(v -> model.google()); binding.signupTab.setOnClickListener(v -> model.startRegistration());
        binding.signInButton.setOnClickListener(v -> {
            String email=binding.emailInput.getText().toString(),password=binding.passwordInput.getText().toString();
            if (!AccountValidation.isValidEmail(email)) { binding.emailInput.setError("Enter a valid email address."); return; }
            if (password.isEmpty()) { binding.passwordInput.setError("Enter your password."); return; }
            model.password(email,password);
        });
        binding.forgotPasswordButton.setOnClickListener(v -> model.recoveryEmail()); binding.viewTermsButton.setOnClickListener(v -> model.showTerms(true)); binding.retryButton.setOnClickListener(v -> model.retrySession());
        binding.previewButton.setVisibility(BuildConfig.DEBUG?View.VISIBLE:View.GONE); binding.previewButton.setOnClickListener(v -> ((AuthActivity)requireActivity()).openPreview());
        model.state().observe(getViewLifecycleOwner(),state -> {
            common(view,state); binding.retryButton.setVisibility(state.route==AuthViewModel.Route.SESSION_RETRY?View.VISIBLE:View.GONE);
            binding.passwordNoticeText.setVisibility(state.passwordNotice==null?View.GONE:View.VISIBLE);
            if(state.passwordNotice!=null)binding.passwordNoticeText.setText(state.passwordNotice==AuthRepository.PasswordChangeNotice.CHANGED?R.string.password_change_notice:R.string.password_change_uncertain_notice);
        });
    }
    @Override public void onDestroyView() { super.onDestroyView(); binding=null; }
}
