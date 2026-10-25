package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import ph.edu.tip.goserve.BuildConfig;
import ph.edu.tip.goserve.databinding.ActivityAuthBinding;

public final class AuthActivity extends BaseActivity {
    public static final String EXTRA_RETURN_RECOVERY_OWNER="return_recovery_owner";
    public static final String EXTRA_ACCOUNT_RECOVERY_OWNER="account_recovery_owner";
    private ActivityAuthBinding binding;
    private AuthViewModel model;
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved); binding=ActivityAuthBinding.inflate(getLayoutInflater()); setContentView(binding.getRoot()); applyInsets(binding.getRoot());
        model=new ViewModelProvider(this).get(AuthViewModel.class);
        model.state().observe(this,this::render);
        model.googleUrl().observe(this,event -> {
            Uri url=event.consume(); if (url==null) return;
            try { new CustomTabsIntent.Builder().build().launchUrl(this,url); }
            catch (android.content.ActivityNotFoundException unavailable) { android.widget.Toast.makeText(this,"Install a browser to continue with Google.",android.widget.Toast.LENGTH_LONG).show(); }
        });
        getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { if (!model.back()) { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); } }
        });
        String accountRecoveryOwner=getIntent().getStringExtra(EXTRA_ACCOUNT_RECOVERY_OWNER);getIntent().removeExtra(EXTRA_ACCOUNT_RECOVERY_OWNER);
        if(!model.accountPasswordRecovery()&&!model.startAccountPasswordRecovery(accountRecoveryOwner))model.initialize(consumeCallback(getIntent()));
    }
    private void render(AuthViewModel.State state) {
        binding.authLoading.setVisibility(state.loading?android.view.View.VISIBLE:android.view.View.GONE);
        binding.authContainer.setImportantForAccessibility(state.loading?android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS:android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        if (isFinishing() || getSupportFragmentManager().isStateSaved()) return;
        Fragment next;
        switch (state.route) {
            case RESTORING: return;
            case HOME: if (state.profile!=null && state.profile.ready()) openMain(false); return;
            case TERMS: next=new TermsFragment(); break;
            case REGISTER_GOOGLE: next=new GoogleRegistrationFragment(); break;
            case RECOVERY_SETUP: next=RecoverySecurityFragment.create(RecoverySecurityViewModel.Kind.SETUP); break;
            case CREDENTIALS: next=new RegistrationFragment(); break;
            case PROFILE: next=new ConsentFragment(); break;
            case ACCOUNT_CREATED: next=new AccountCreatedFragment(); break;
            case RESET_EMAIL: case RESET_SENT: case RESET_PASSWORD: case RESET_COMPLETE: case RESET_UNAVAILABLE: next=RecoverySecurityFragment.create(model.accountPasswordRecovery()?RecoverySecurityViewModel.Kind.PASSWORD_CHANGE:RecoverySecurityViewModel.Kind.FORGOT); break;
            default: next=new SignInFragment();
        }
        Fragment current=getSupportFragmentManager().findFragmentById(binding.authContainer.getId());
        boolean recoveryKindChanged=current instanceof RecoverySecurityFragment && next instanceof RecoverySecurityFragment && !java.util.Objects.equals(current.requireArguments().getString("kind"),next.requireArguments().getString("kind"));
        if (current==null || current.getClass()!=next.getClass() || recoveryKindChanged) show(next,false);
    }
    @Override protected void onResume() { super.onResume(); if (model!=null && model.state().getValue()!=null) render(model.state().getValue()); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); model.finishGoogle(consumeCallback(intent)); }
    private Uri consumeCallback(Intent intent) { Uri callback=intent.getData(); if (callback!=null) intent.setData(null); return callback; }
    public void show(Fragment fragment,boolean backStack) {
        androidx.fragment.app.FragmentTransaction transaction=getSupportFragmentManager().beginTransaction().replace(binding.authContainer.getId(),fragment);
        if (backStack) { transaction.addToBackStack(null); transaction.commit(); } else transaction.commitNow();
    }
    public void openPreview() { if (BuildConfig.DEBUG) openMain(true); }
    private void openMain(boolean preview) {
        String requestedOwner=getIntent().getStringExtra(EXTRA_RETURN_RECOVERY_OWNER);
        ph.edu.tip.goserve.domain.SessionProfile profile=model.state().getValue().profile;
        Intent destination=new Intent(this,MainActivity.class).putExtra(MainActivity.EXTRA_PREVIEW,preview);
        if(!preview&&profile!=null&&profile.id.equals(requestedOwner))destination.putExtra(MainActivity.EXTRA_OPEN_RECOVERY_CODES,true);
        startActivity(destination);finish();
    }
}
