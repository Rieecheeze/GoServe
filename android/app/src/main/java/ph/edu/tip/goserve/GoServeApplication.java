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
    private SupabaseClient client;
    private ph.edu.tip.goserve.data.RealtimeDataSource realtime;
    private final Object realtimeLock=new Object();
    private ph.edu.tip.goserve.data.RealtimeDataSource.Session realtimeSession;
    private AuthRepository.SessionLease realtimeLease;
    private long bindingEpoch;
    private int startedActivities;
    private android.os.Handler mainThread;
    private final androidx.lifecycle.MutableLiveData<Long> accountInvalidations=new androidx.lifecycle.MutableLiveData<>();

    @Override public void onCreate() {
        super.onCreate();
        int mode = getSharedPreferences("appearance", MODE_PRIVATE)
                .getInt("mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
        client = new SupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY);
        auth = new AuthRepository(client, new SecureSessionStore(this));
        offices = new OfficeRepository(client, auth);
        android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
        mainThread=main;
        realtime=new ph.edu.tip.goserve.data.RealtimeDataSource(BuildConfig.SUPABASE_URL,BuildConfig.SUPABASE_PUBLISHABLE_KEY,main::post);
        wireInvalidation(auth);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            public void onActivityStarted(android.app.Activity activity) {
                ph.edu.tip.goserve.data.RealtimeDataSource.Session current;
                synchronized (realtimeLock) { ++startedActivities; current=realtimeSession; }
                if (current!=null) current.start();
            }
            public void onActivityStopped(android.app.Activity activity) {
                ph.edu.tip.goserve.data.RealtimeDataSource.Session current;
                synchronized (realtimeLock) { startedActivities=Math.max(0,startedActivities-1); current=startedActivities==0?realtimeSession:null; }
                if (current!=null) current.stop();
            }
            public void onActivityCreated(android.app.Activity activity,android.os.Bundle state) {}
            public void onActivityResumed(android.app.Activity activity) {}
            public void onActivityPaused(android.app.Activity activity) {}
            public void onActivitySaveInstanceState(android.app.Activity activity,android.os.Bundle state) {}
            public void onActivityDestroyed(android.app.Activity activity) {}
        });
    }

    public AuthRepository auth() { return auth; }
    public OfficeRepository offices() { return offices; }
    public ph.edu.tip.goserve.data.AccountGateway gateway() { return new ph.edu.tip.goserve.data.AccountGateway(client,auth); }
    public ph.edu.tip.goserve.data.RealtimeDataSource.Session realtimeSession() { synchronized (realtimeLock) { return realtimeSession; } }
    public androidx.lifecycle.LiveData<Long> accountInvalidations() { return accountInvalidations; }
    private void wireInvalidation(AuthRepository owner) {
        owner.setInvalidationListener(() -> {
            long generation=owner.generation(); clearStaleSession(owner,generation);
            mainThread.post(() -> { if (owner==auth) accountInvalidations.setValue(generation); });
        });
    }
    private void clearStaleSession(AuthRepository owner,long generation) {
        ph.edu.tip.goserve.data.RealtimeDataSource.Session old;
        synchronized (realtimeLock) {
            if (owner!=auth || realtimeLease!=null && realtimeLease.generation>=generation) return;
            ++bindingEpoch; old=realtimeSession; realtimeSession=null; realtimeLease=null;
        }
        if (old!=null) old.close();
    }
    public void bindCurrentAccount() {
        if (android.os.Looper.myLooper()!=android.os.Looper.getMainLooper()) { mainThread.post(this::bindCurrentAccount); return; }
        AuthRepository owner=auth;
        ph.edu.tip.goserve.domain.SessionProfile profile=owner.profile();
        if (profile==null || !profile.ready()) return;
        AuthRepository.SessionLease lease=owner.sessionLease(); wireInvalidation(owner);
        ph.edu.tip.goserve.data.RealtimeDataSource.Session old; long request;
        synchronized (realtimeLock) {
            if (realtimeLease!=null && realtimeLease.generation==lease.generation && realtimeLease.accountId.equals(lease.accountId)) return;
            request=++bindingEpoch; old=realtimeSession; realtimeSession=null; realtimeLease=null;
        }
        if (old!=null) old.close();
        ph.edu.tip.goserve.data.RealtimeDataSource.Session next=realtime.bindSession(lease.accountId,() -> owner.token(lease));
        try { owner.requireCurrent(lease); }
        catch (IllegalStateException changed) { next.close(); throw changed; }
        boolean accepted,foreground;
        synchronized (realtimeLock) {
            accepted=request==bindingEpoch && owner==auth; foreground=startedActivities>0;
            if (accepted) { realtimeSession=next; realtimeLease=lease; }
        }
        if (!accepted) { next.close(); return; }
        try { owner.requireCurrent(lease); if (foreground) next.start(); }
        catch (IllegalStateException changed) { clearStaleSession(owner,owner.generation()); throw changed; }
    }
    @Override public void onTerminate() { if (realtime!=null) realtime.close(); super.onTerminate(); }
}
