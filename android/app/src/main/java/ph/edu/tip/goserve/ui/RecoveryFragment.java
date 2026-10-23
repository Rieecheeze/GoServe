package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentRecoveryBinding;

public final class RecoveryFragment extends AccountFragment {
    private FragmentRecoveryBinding binding;
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved) { binding=FragmentRecoveryBinding.inflate(inflater,parent,false); return binding.getRoot(); }
    @Override public void onViewCreated(View view,Bundle saved) {
        super.onViewCreated(view,saved); binding.emailInput.setText(model.draft().recoveryEmail);
        binding.passwordInput.setText(model.resetPasswordDraft()); binding.confirmationInput.setText(model.resetConfirmationDraft());
        binding.passwordInput.setSaveFromParentEnabled(false); binding.confirmationInput.setSaveFromParentEnabled(false);
        watch(binding.emailInput,model::setRecoveryEmail);
        watch(binding.passwordInput,value -> model.setResetPasswords(value,binding.confirmationInput.getText().toString()));
        watch(binding.confirmationInput,value -> model.setResetPasswords(binding.passwordInput.getText().toString(),value));
        binding.primaryButton.setOnClickListener(v -> {
            AuthViewModel.Route route=model.state().getValue().route;
            if (route==AuthViewModel.Route.RESET_EMAIL) model.requestRecovery();
            else if (route==AuthViewModel.Route.RESET_PASSWORD) model.resetPassword(binding.passwordInput.getText().toString(),binding.confirmationInput.getText().toString());
            else if (route==AuthViewModel.Route.RESET_UNAVAILABLE) model.recoveryEmail(); else model.signInScreen();
        });
        binding.backButton.setOnClickListener(v -> model.signInScreen()); binding.differentEmailButton.setOnClickListener(v -> model.recoveryEmail());
        model.state().observe(getViewLifecycleOwner(),state -> {
            common(view,state); AuthViewModel.Route route=state.route;
            boolean email=route==AuthViewModel.Route.RESET_EMAIL,password=route==AuthViewModel.Route.RESET_PASSWORD,sent=route==AuthViewModel.Route.RESET_SENT,complete=route==AuthViewModel.Route.RESET_COMPLETE;
            binding.emailFields.setVisibility(email?View.VISIBLE:View.GONE); binding.passwordFields.setVisibility(password?View.VISIBLE:View.GONE);
            binding.title.setText(email?R.string.account_recovery_title:password?R.string.account_reset_title:sent?R.string.account_recovery_sent_title:complete?R.string.account_reset_complete_title:R.string.account_reset_unavailable_title);
            binding.subtitle.setText(email?R.string.account_recovery_intro:password?R.string.account_reset_intro:sent?R.string.account_recovery_sent_intro:complete?R.string.account_reset_complete_intro:R.string.account_reset_unavailable_intro);
            binding.primaryButton.setText(email?R.string.account_recovery_send:password?R.string.account_reset_save:sent||complete?R.string.account_go_to_sign_in:R.string.account_reset_request_new);
            binding.differentEmailButton.setVisibility(sent?View.VISIBLE:View.GONE); binding.resultHelp.setVisibility(sent?View.VISIBLE:View.GONE);
            if (complete || route==AuthViewModel.Route.RESET_UNAVAILABLE) { binding.passwordInput.setText(""); binding.confirmationInput.setText(""); }
            error(binding.emailError,state.fieldErrors.get("email")); error(binding.passwordError,state.fieldErrors.get("password")); error(binding.confirmationError,state.fieldErrors.get("confirmation"));
        });
    }
    @Override public void onDestroyView() { super.onDestroyView(); binding=null; }
}
