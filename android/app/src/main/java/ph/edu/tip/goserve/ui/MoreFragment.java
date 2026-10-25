package ph.edu.tip.goserve.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.BuildConfig;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentMoreBinding;
import ph.edu.tip.goserve.domain.SessionProfile;

public final class MoreFragment extends Fragment {
    private FragmentMoreBinding binding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) { binding = FragmentMoreBinding.inflate(inflater, parent, false); return binding.getRoot(); }
    @Override public void onViewCreated(View view, Bundle saved) {
        MainActivity host = (MainActivity) requireActivity();
        SharedMotion.install(view);
        SessionProfile profile = host.profile();
        int mode = requireContext().getSharedPreferences("appearance", Context.MODE_PRIVATE).getInt("mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        binding.themeGroup.check(mode == AppCompatDelegate.MODE_NIGHT_YES ? R.id.radioDark : mode == AppCompatDelegate.MODE_NIGHT_NO ? R.id.radioLight : R.id.radioSystem);
        binding.themeGroup.setOnCheckedChangeListener((group, id) -> {
            int selected = id == R.id.radioDark ? AppCompatDelegate.MODE_NIGHT_YES : id == R.id.radioLight ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            requireContext().getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putInt("mode", selected).apply();
            AppCompatDelegate.setDefaultNightMode(selected);
        });
        binding.backButton.setOnClickListener(v -> host.getOnBackPressedDispatcher().onBackPressed());
        binding.accountButton.setOnClickListener(v -> host.navigate(SettingsGroupFragment.account(),true));
        boolean student=host.isPreview() || profile!=null && profile.role==ph.edu.tip.goserve.domain.Role.STUDENT;
        binding.seryoButton.setVisibility(student?View.VISIBLE:View.GONE);
        binding.seryoButton.setOnClickListener(v -> host.navigate(SettingsGroupFragment.seryo(),true));
        binding.aboutButton.setOnClickListener(v -> host.navigate(FeatureStatusFragment.create(getString(R.string.about_goserve),getString(R.string.settings_about_body,BuildConfig.VERSION_NAME)),true));
        binding.viewTermsButton.setOnClickListener(v -> {
            new androidx.lifecycle.ViewModelProvider(host).get(AuthViewModel.class).showTerms(true);
            host.navigate(TermsFragment.readOnly(),true);
        });
        binding.signOutButton.setText(host.isPreview() ? R.string.exit_preview_logic : R.string.sign_out_logic);
        binding.signOutButton.setOnClickListener(v -> {
            if (host.isPreview()) { host.returnToSignIn(); return; }
            binding.signOutButton.setEnabled(false);
            GoServeApplication app = (GoServeApplication) requireActivity().getApplication();
            try { app.auth().signOut(executor); host.returnToSignIn(); }
            catch (IllegalStateException error) {
                binding.signOutButton.setEnabled(true);
                android.widget.Toast.makeText(requireContext(),error.getMessage(),android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
    @Override public void onDestroy() { super.onDestroy(); executor.shutdown(); }
}
