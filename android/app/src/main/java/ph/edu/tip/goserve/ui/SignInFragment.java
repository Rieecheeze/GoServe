package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import ph.edu.tip.goserve.BuildConfig;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentSignInBinding;

public final class SignInFragment extends Fragment {
    private FragmentSignInBinding binding;
    private boolean signup;
    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) {
        binding = FragmentSignInBinding.inflate(inflater, parent, false);
        signup = saved != null && saved.getBoolean("signup");
        return binding.getRoot();
    }
    @Override public void onViewCreated(View view, Bundle saved) {
        AuthViewModel model = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        binding.loginTab.setOnClickListener(v -> setSignup(false));
        binding.signupTab.setOnClickListener(v -> setSignup(true));
        binding.googleButton.setOnClickListener(v -> model.google());
        binding.officeButton.setOnClickListener(v -> ((AuthActivity) requireActivity()).show(new OfficeLoginFragment(), true));
        binding.previewButton.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        binding.previewButton.setText(R.string.preview_design);
        binding.previewButton.setOnClickListener(v -> ((AuthActivity) requireActivity()).openPreview());
        setSignup(signup);
        model.state().observe(getViewLifecycleOwner(), state -> {
            binding.progress.setVisibility(state.loading ? View.VISIBLE : View.GONE);
            binding.googleButton.setEnabled(!state.loading);
            binding.officeButton.setEnabled(!state.loading);
            binding.errorText.setText(state.error);
            binding.errorText.setVisibility(state.error == null ? View.GONE : View.VISIBLE);
        });
    }
    private void setSignup(boolean signup) {
        this.signup = signup;
        binding.title.setText(signup ? R.string.signup_title_logic : R.string.login_title_logic);
        binding.subtitle.setText(signup ? R.string.signup_subtitle_logic : R.string.login_subtitle_logic);
        styleTab(binding.loginTab, !signup); styleTab(binding.signupTab, signup);
    }
    private void styleTab(com.google.android.material.button.MaterialButton button, boolean selected) {
        int background = androidx.core.content.ContextCompat.getColor(requireContext(), selected ? R.color.gs_primary : R.color.gs_surface);
        int text = androidx.core.content.ContextCompat.getColor(requireContext(), selected ? R.color.gs_on_primary : R.color.gs_text);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(background));
        button.setTextColor(text); button.setSelected(selected);
    }
    @Override public void onSaveInstanceState(Bundle saved) { super.onSaveInstanceState(saved); saved.putBoolean("signup", signup); }
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
