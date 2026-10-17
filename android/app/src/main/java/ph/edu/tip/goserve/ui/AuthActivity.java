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
        super.onCreate(saved);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyInsets(binding.getRoot());
        model = new ViewModelProvider(this).get(AuthViewModel.class);
        if (saved == null) show(new SignInFragment(), false);
        model.initialize(consumeCallback(getIntent()));
        model.state().observe(this, state -> {
            binding.authLoading.setVisibility(state.loading ? android.view.View.VISIBLE : android.view.View.GONE);
            binding.authContainer.setImportantForAccessibility(state.loading
                    ? android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    : android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            if (state.profile == null) {
                if (!state.loading && !model.hasAuthenticatedProfile()
                        && getSupportFragmentManager().findFragmentById(binding.authContainer.getId()) instanceof ConsentFragment) {
                    getSupportFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
                    show(new SignInFragment(), false);
                }
                return;
            }
            if (!state.profile.consentAccepted) {
                if (!(getSupportFragmentManager().findFragmentById(binding.authContainer.getId()) instanceof ConsentFragment)) {
                    show(new ConsentFragment(), false);
                }
            } else openMain(false);
        });
        model.googleUrl().observe(this, event -> {
            Uri url = event.consume();
            if (url != null) {
                try { new CustomTabsIntent.Builder().build().launchUrl(this, url); }
                catch (android.content.ActivityNotFoundException unavailable) {
                    android.widget.Toast.makeText(this, "Install a browser to continue with Google.", android.widget.Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        model.finishGoogle(consumeCallback(intent));
    }
    private Uri consumeCallback(Intent intent) {
        Uri callback = intent.getData();
        if (callback != null) intent.setData(null);
        return callback;
    }
    public void show(Fragment fragment, boolean backStack) {
        androidx.fragment.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction()
                .replace(binding.authContainer.getId(), fragment);
        if (backStack) transaction.addToBackStack(null);
        transaction.commit();
    }
    public void openPreview() { if (BuildConfig.DEBUG) openMain(true); }
    private void openMain(boolean preview) {
        startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_PREVIEW, preview));
        finish();
    }
}
