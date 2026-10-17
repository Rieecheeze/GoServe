package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import ph.edu.tip.goserve.BuildConfig;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.ActivityMainBinding;
import ph.edu.tip.goserve.domain.Role;
import ph.edu.tip.goserve.domain.SessionProfile;

public final class MainActivity extends BaseActivity {
    public static final String EXTRA_PREVIEW = "design_preview";
    private ActivityMainBinding binding;
    private boolean preview;
    private SessionProfile profile;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        preview = BuildConfig.DEBUG && getIntent().getBooleanExtra(EXTRA_PREVIEW, false);
        profile = ((GoServeApplication) getApplication()).auth().profile();
        if (!preview && (profile == null || !profile.consentAccepted)) { returnToSignIn(); return; }
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot()); applyInsets(binding.getRoot());
        Role role = preview ? Role.STUDENT : profile.role;
        binding.bottomNavigation.getMenu().clear();
        binding.bottomNavigation.inflateMenu(role == Role.STUDENT ? R.menu.menu_student : role == Role.STAFF ? R.menu.menu_staff : R.menu.menu_teller);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                navigate(role == Role.STUDENT ? new HomeFragment() : FeatureStatusFragment.create(getString(R.string.staff_dashboard_logic), getString(R.string.staff_pending_logic)), false);
            } else if (id == R.id.nav_more) navigate(new MoreFragment(), false);
            else if (id == R.id.nav_requests) navigate(RequestListFragment.create(), false);
            else if (id == R.id.nav_consult) feature(R.string.consult_title_logic, R.string.consult_pending_logic, false);
            else if (id == R.id.nav_inbox) feature(R.string.inbox_title_logic, R.string.inbox_pending_logic, false);
            else if (id == R.id.nav_assistant) navigate(new AssistantFragment(), false);
            else if (id == R.id.nav_payments) feature(R.string.cash_title_logic, R.string.cash_pending_logic, false);
            else if (id == R.id.nav_history) feature(R.string.history_title_logic, R.string.cash_pending_logic, false);
            return true;
        });
        if (saved == null) {
            int first = role == Role.TELLER ? R.id.nav_payments : R.id.nav_home;
            binding.bottomNavigation.setSelectedItemId(first);
        }
        new ViewModelProvider(this).get(OfficeViewModel.class).ensureLoaded(preview);
    }
    public boolean isPreview() { return preview; }
    public SessionProfile profile() { return profile; }
    public void navigate(Fragment fragment, boolean backStack) {
        if (!backStack) getSupportFragmentManager().popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
        androidx.fragment.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction().replace(binding.mainContainer.getId(), fragment);
        if (backStack) transaction.addToBackStack(null);
        transaction.commit();
    }
    public void feature(int title, int body, boolean backStack) { navigate(FeatureStatusFragment.create(getString(title), getString(body)), backStack); }
    public void returnToSignIn() {
        startActivity(new Intent(this, AuthActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish();
    }
}
