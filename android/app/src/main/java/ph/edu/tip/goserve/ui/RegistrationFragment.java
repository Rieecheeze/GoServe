package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import ph.edu.tip.goserve.databinding.FragmentRegistrationBinding;

public final class RegistrationFragment extends AccountFragment {
    private FragmentRegistrationBinding binding;
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved) { binding=FragmentRegistrationBinding.inflate(inflater,parent,false); return binding.getRoot(); }
    @Override public void onViewCreated(View view,Bundle saved) {
        super.onViewCreated(view,saved); binding.emailInput.setText(model.draft().email);
        binding.passwordInput.setText(model.registrationPassword()); binding.confirmationInput.setText(model.registrationConfirmation());
        binding.passwordInput.setSaveFromParentEnabled(false); binding.confirmationInput.setSaveFromParentEnabled(false);
        watch(binding.emailInput,model::setEmail);
        watch(binding.passwordInput,value -> model.setRegistrationPasswords(value,binding.confirmationInput.getText().toString()));
        watch(binding.confirmationInput,value -> model.setRegistrationPasswords(binding.passwordInput.getText().toString(),value));
        binding.continueButton.setOnClickListener(v -> model.credentials(binding.emailInput.getText().toString(),binding.passwordInput.getText().toString(),binding.confirmationInput.getText().toString()));
        binding.backButton.setOnClickListener(v -> model.back()); binding.viewTermsButton.setOnClickListener(v -> model.showTerms(true));
        model.state().observe(getViewLifecycleOwner(),state -> {
            common(view,state); error(binding.emailError,state.fieldErrors.get("email")); error(binding.passwordError,state.fieldErrors.get("password")); error(binding.confirmationError,state.fieldErrors.get("confirmation"));
        });
    }
    @Override public void onDestroyView() { super.onDestroyView(); binding=null; }
}
