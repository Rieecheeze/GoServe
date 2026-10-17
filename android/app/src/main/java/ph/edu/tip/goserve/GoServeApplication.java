package ph.edu.tip.goserve;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import ph.edu.tip.goserve.data.AuthRepository;
import ph.edu.tip.goserve.data.OfficeRepository;
import ph.edu.tip.goserve.data.SecureSessionStore;
import ph.edu.tip.goserve.data.SupabaseClient;

public final class GoServeApplication extends Application {
    private AuthRepository auth;
    private OfficeRepository offices;

    @Override public void onCreate() {
        super.onCreate();
        int mode = getSharedPreferences("appearance", MODE_PRIVATE)
                .getInt("mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
        SupabaseClient client = new SupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY);
        auth = new AuthRepository(client, new SecureSessionStore(this));
        offices = new OfficeRepository(client, auth);
    }

    public AuthRepository auth() { return auth; }
    public OfficeRepository offices() { return offices; }
}
