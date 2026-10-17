package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import ph.edu.tip.goserve.BuildConfig;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentOfficeLoginBinding;

public final class OfficeLoginFragment extends Fragment {
    private FragmentOfficeLoginBinding binding;
    private boolean passwordStep;
    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) {
        binding = FragmentOfficeLoginBinding.inflate(inflater, parent, false);
        passwordStep = saved != null && saved.getBoolean("password_step");
        return binding.getRoot();
    }
    @Override public void onViewCreated(View view, Bundle saved) {
        AuthViewModel model = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        binding.backButton.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        binding.previewButton.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        binding.previewButton.setText(R.string.preview_design);
        binding.previewButton.setOnClickListener(v -> ((AuthActivity) requireActivity()).openPreview());
        binding.continueButton.setOnClickListener(v -> {
            String email = binding.emailInput.getText().toString().trim();
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.emailInput.setError(getString(R.string.enter_email_logic)); return;
            }
            if (!passwordStep) { passwordStep = true; updateStep(); binding.passwordInput.requestFocus(); }
            else {
                String password = binding.passwordInput.getText().toString();
                if (password.isEmpty()) { binding.passwordInput.setError(getString(R.string.enter_password_logic)); return; }
                model.password(email, password);
                binding.passwordInput.setText("");
                androidx.core.view.WindowInsetsControllerCompat controller = androidx.core.view.ViewCompat.getWindowInsetsController(view);
                if (controller != null) controller.hide(androidx.core.view.WindowInsetsCompat.Type.ime());
            }
        });
        updateStep();
        model.state().observe(getViewLifecycleOwner(), state -> {
            binding.progress.setVisibility(state.loading ? View.VISIBLE : View.GONE);
            binding.continueButton.setEnabled(!state.loading);
            binding.emailInput.setEnabled(!state.loading); binding.passwordInput.setEnabled(!state.loading);
            binding.errorText.setText(state.error); binding.errorText.setVisibility(state.error == null ? View.GONE : View.VISIBLE);
        });
    }
    private void updateStep() {
        binding.passwordInput.setVisibility(passwordStep ? View.VISIBLE : View.GONE);
        binding.continueButton.setText(passwordStep ? R.string.sign_in_logic : R.string.continue_logic);
    }
    @Override public void onSaveInstanceState(Bundle saved) { super.onSaveInstanceState(saved); saved.putBoolean("password_step", passwordStep); }
    @Override public void onDestroyView() { binding.passwordInput.setText(""); super.onDestroyView(); binding = null; }
}
