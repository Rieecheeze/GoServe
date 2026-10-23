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
        model.initialize(consumeCallback(getIntent()));
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
            case CREDENTIALS: next=new RegistrationFragment(); break;
            case PROFILE: next=new ConsentFragment(); break;
            case ACCOUNT_CREATED: next=new AccountCreatedFragment(); break;
            case RESET_EMAIL: case RESET_SENT: case RESET_PASSWORD: case RESET_COMPLETE: case RESET_UNAVAILABLE: next=new RecoveryFragment(); break;
            default: next=new SignInFragment();
        }
        Fragment current=getSupportFragmentManager().findFragmentById(binding.authContainer.getId());
        if (current==null || current.getClass()!=next.getClass()) show(next,false);
    }
    @Override protected void onResume() { super.onResume(); if (model!=null && model.state().getValue()!=null) render(model.state().getValue()); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); model.finishGoogle(consumeCallback(intent)); }
    private Uri consumeCallback(Intent intent) { Uri callback=intent.getData(); if (callback!=null) intent.setData(null); return callback; }
    public void show(Fragment fragment,boolean backStack) {
        androidx.fragment.app.FragmentTransaction transaction=getSupportFragmentManager().beginTransaction().replace(binding.authContainer.getId(),fragment);
        if (backStack) { transaction.addToBackStack(null); transaction.commit(); } else transaction.commitNow();
    }
    public void openPreview() { if (BuildConfig.DEBUG) openMain(true); }
    private void openMain(boolean preview) { startActivity(new Intent(this,MainActivity.class).putExtra(MainActivity.EXTRA_PREVIEW,preview)); finish(); }
}
