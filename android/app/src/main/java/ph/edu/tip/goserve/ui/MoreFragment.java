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
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentMoreBinding;
import ph.edu.tip.goserve.domain.SessionProfile;

public final class MoreFragment extends Fragment {
    private FragmentMoreBinding binding;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) { binding = FragmentMoreBinding.inflate(inflater, parent, false); return binding.getRoot(); }
    @Override public void onViewCreated(View view, Bundle saved) {
        MainActivity host = (MainActivity) requireActivity();
        SessionProfile profile = host.profile();
        binding.accountText.setText(host.isPreview() ? getString(R.string.preview_notice_logic) : profile.email + "\n" + profile.role.wireValue);
        int mode = requireContext().getSharedPreferences("appearance", Context.MODE_PRIVATE).getInt("mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        binding.themeGroup.check(mode == AppCompatDelegate.MODE_NIGHT_YES ? R.id.radioDark : mode == AppCompatDelegate.MODE_NIGHT_NO ? R.id.radioLight : R.id.radioSystem);
        binding.themeGroup.setOnCheckedChangeListener((group, id) -> {
            int selected = id == R.id.radioDark ? AppCompatDelegate.MODE_NIGHT_YES : id == R.id.radioLight ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            requireContext().getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putInt("mode", selected).apply();
            AppCompatDelegate.setDefaultNightMode(selected);
        });
        binding.backButton.setOnClickListener(v -> host.getOnBackPressedDispatcher().onBackPressed());
        binding.signOutButton.setText(host.isPreview() ? R.string.exit_preview_logic : R.string.sign_out_logic);
        binding.signOutButton.setOnClickListener(v -> {
            if (host.isPreview()) { host.returnToSignIn(); return; }
            binding.signOutButton.setEnabled(false);
            GoServeApplication app = (GoServeApplication) requireActivity().getApplication();
            executor.execute(() -> {
                try { app.auth().signOut(); } catch (Exception ignored) { /* Local credentials are cleared even if server revocation is unavailable. */ }
                host.runOnUiThread(() -> { if (!host.isFinishing()) host.returnToSignIn(); });
            });
        });
    }
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
    @Override public void onDestroy() { super.onDestroy(); executor.shutdownNow(); }
}
