package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import ph.edu.tip.goserve.databinding.FragmentConsentBinding;

public final class ConsentFragment extends Fragment {
    private FragmentConsentBinding binding;
    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) {
        binding = FragmentConsentBinding.inflate(inflater, parent, false); return binding.getRoot();
    }
    @Override public void onViewCreated(View view, Bundle saved) {
        AuthViewModel model = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        binding.privacyCheck.setChecked(false);
        binding.acceptButton.setEnabled(false);
        binding.privacyCheck.setOnCheckedChangeListener((button, checked) -> binding.acceptButton.setEnabled(checked));
        binding.acceptButton.setOnClickListener(v -> { if (binding.privacyCheck.isChecked()) model.acceptConsent(); });
        binding.backButton.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        model.state().observe(getViewLifecycleOwner(), state -> {
            binding.progress.setVisibility(state.loading ? View.VISIBLE : View.GONE);
            binding.acceptButton.setEnabled(binding.privacyCheck.isChecked() && !state.loading);
            binding.privacyCheck.setEnabled(!state.loading);
            binding.errorText.setText(state.error); binding.errorText.setVisibility(state.error == null ? View.GONE : View.VISIBLE);
        });
    }
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
