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
    private ph.edu.tip.goserve.data.AuthRepository.SessionLease accountLease;
    private ph.edu.tip.goserve.data.RealtimeDataSource.Subscription officeSubscription;
    private ph.edu.tip.goserve.data.RealtimeDataSource.Session officeSession;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        preview = BuildConfig.DEBUG && getIntent().getBooleanExtra(EXTRA_PREVIEW, false);
        profile = ((GoServeApplication) getApplication()).auth().profile();
        if (!preview && (profile == null || !profile.ready())) { returnToSignIn(); return; }
        if (!preview) accountLease=((GoServeApplication)getApplication()).auth().sessionLease();
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot()); applyInsets(binding.getRoot());
        Role role = preview ? Role.STUDENT : profile.role;
        configureRoleNavigation(role);
        if (saved == null) {
            int first = role == Role.TELLER ? R.id.nav_payments : R.id.nav_home;
            binding.bottomNavigation.setSelectedItemId(first);
        }
        new ViewModelProvider(this).get(OfficeViewModel.class).ensureLoaded(preview);
        if (!preview) ((GoServeApplication)getApplication()).accountInvalidations().observe(this,ignored -> checkAccountLease());
    }
    void configureRoleNavigation(Role role) {
        binding.bottomNavigation.getMenu().clear();
        binding.bottomNavigation.inflateMenu(role == Role.STUDENT ? R.menu.menu_student : role == Role.STAFF ? R.menu.menu_staff : R.menu.menu_teller);
        if(!preview && role==Role.STAFF && !ConsultationViewModel.permitted(profile))binding.bottomNavigation.removeMenuItem(R.id.nav_consult);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            if(navigationLocked())return false;
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                navigate(role == Role.STUDENT ? new HomeFragment() : new StaffHomeFragment(), false);
            } else if (id == R.id.nav_more) navigate(new MoreFragment(), false);
            else if (id == R.id.nav_requests) navigate(RequestListFragment.create(), false);
            else if (id == R.id.nav_consult) navigate(new ConsultationFragment(), false);
            else if (id == R.id.nav_inbox) navigate(InboxFragment.create(), false);
            else if (id == R.id.nav_assistant) navigate(new AssistantFragment(), false);
            else if (id == R.id.nav_payments && role == Role.TELLER) { new ViewModelProvider(this).get(TellerPaymentViewModel.class).editReference(); navigate(new TellerPaymentFragment(), false); }
            else if (id == R.id.nav_cash_pending && role == Role.TELLER) navigate(TellerPaymentFragment.pending(), false);
            else if (id == R.id.nav_history && role == Role.TELLER) navigate(new CashHistoryFragment(), false);
            return true;
        });
    }
    void markTellerPending() {
        if(binding!=null)binding.bottomNavigation.markSelectedItemId(R.id.nav_cash_pending);
    }
    void showTellerSearch() {
        if(binding!=null)binding.bottomNavigation.setSelectedItemId(R.id.nav_payments);
    }
    public boolean isPreview() { return preview; }
    @Override protected void onStart(){
        super.onStart();if(preview || binding==null || accountLease==null || officeSubscription!=null)return;
        GoServeApplication app=(GoServeApplication)getApplication();
        try{
            app.auth().requireCurrent(accountLease);ph.edu.tip.goserve.data.RealtimeDataSource.Session session=app.realtimeSession();app.auth().requireCurrent(accountLease);if(session==null)return;officeSession=session;
            OfficeViewModel offices=new ViewModelProvider(this).get(OfficeViewModel.class);if(!offices.state().getValue().loading)offices.reload(false);
            officeSubscription=session.subscribe(java.util.Set.of("offices","services"),new ph.edu.tip.goserve.data.RealtimeDataSource.Listener(){
                private void refresh(){if(binding==null || officeSession!=session || accountLease==null || isFinishing())return;try{app.auth().requireCurrent(accountLease);offices.reload(false);}catch(IllegalStateException changed){offices.checkCurrentAccount();}}
                public void onInvalidated(ph.edu.tip.goserve.data.RealtimeDataSource.Invalidation event){if(event!=null && event.generation==session.generation())refresh();}
                public void onStateChanged(ph.edu.tip.goserve.data.RealtimeDataSource.State state){if(state==ph.edu.tip.goserve.data.RealtimeDataSource.State.CONNECTED)refresh();}
            });
        }catch(IllegalStateException changed){checkAccountLease();}
    }
    @Override protected void onStop(){officeSession=null;if(officeSubscription!=null)officeSubscription.close();officeSubscription=null;super.onStop();}
    @Override protected void onResume() {
        super.onResume();
        checkAccountLease();
    }
    private void checkAccountLease() {
        if (!preview && accountLease!=null && !isFinishing()) {
            try { ((GoServeApplication)getApplication()).auth().requireCurrent(accountLease); }
            catch (IllegalStateException changed) { returnToSignIn(); }
        }
    }
    public SessionProfile profile() { return profile; }
    public void setRoleNavigationVisible(boolean visible) {
        if(binding!=null)binding.bottomNavigation.setVisibility(visible?android.view.View.VISIBLE:android.view.View.GONE);
    }
    public void navigate(Fragment fragment, boolean backStack) {
        if(binding==null || navigationLocked())return;
        if (!backStack) getSupportFragmentManager().popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
        androidx.fragment.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction().replace(binding.mainContainer.getId(), fragment);
        if (backStack) transaction.addToBackStack(null);
        transaction.commit();
    }
    private boolean navigationLocked() {
        Fragment current=getSupportFragmentManager().findFragmentById(R.id.mainContainer);
        return current instanceof ThreadFragment && ((ThreadFragment)current).mutationPending()
            || current instanceof OfficeManagementFragment && ((OfficeManagementFragment)current).mutationPending()
            || current instanceof PickupFragment && ((PickupFragment)current).mutationPending()
            || current instanceof ConsultationFragment && ((ConsultationFragment)current).mutationPending();
    }
    public void feature(int title, int body, boolean backStack) { navigate(FeatureStatusFragment.create(getString(title), getString(body)), backStack); }
    public void returnToSignIn() {
        if (isFinishing()) return;
        profile=null; accountLease=null;
        if (binding!=null) binding.getRoot().setVisibility(android.view.View.GONE);
        startActivity(new Intent(this, AuthActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)); finish();
    }
}
