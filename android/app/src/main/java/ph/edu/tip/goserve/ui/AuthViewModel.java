package ph.edu.tip.goserve.ui;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.HashSet;
import java.util.Set;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.AuthRepository;
import ph.edu.tip.goserve.domain.SessionProfile;

public final class AuthViewModel extends AndroidViewModel {
    public static final class State {
        public final boolean loading;
        public final String error;
        public final SessionProfile profile;
        State(boolean loading, String error, SessionProfile profile) { this.loading = loading; this.error = error; this.profile = profile; }
    }
    public static final class OneTime<T> {
        private final T value;
        private boolean consumed;
        OneTime(T value) { this.value = value; }
        public synchronized T consume() { if (consumed) return null; consumed = true; return value; }
    }
    private final AuthRepository auth;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainThread = new Handler(Looper.getMainLooper());
    private final Set<Uri> receivedCallbacks = new HashSet<>();
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(false, null, null));
    private final MutableLiveData<OneTime<Uri>> googleUrl = new MutableLiveData<>();
    private boolean initialized;
    private int generation;

    public AuthViewModel(Application app) { super(app); auth = ((GoServeApplication) app).auth(); }
    public LiveData<State> state() { return state; }
    public LiveData<OneTime<Uri>> googleUrl() { return googleUrl; }

    public void initialize(Uri callback) {
        if (initialized) return;
        initialized = true;
        if (callback != null) finishGoogle(callback);
        else execute(() -> auth.resumeSession(null));
    }
    public boolean hasAuthenticatedProfile() { return auth.profile() != null; }
    public void password(String email, String password) { execute(() -> auth.signInPassword(email, password)); }
    public void finishGoogle(Uri callback) {
        if (callback != null && receivedCallbacks.add(callback)) execute(() -> auth.resumeSession(callback), true);
    }
    public void acceptConsent() { execute(auth::acceptConsent); }
    public void google() {
        if (isLoading()) return;
        int request = ++generation;
        state.setValue(new State(true, null, null));
        executor.execute(() -> {
            try {
                Uri url = auth.googleAuthorizationUrl();
                mainThread.post(() -> {
                    if (request != generation) return;
                    state.setValue(new State(false, null, null));
                    googleUrl.setValue(new OneTime<>(url));
                });
            } catch (Exception error) { publish(request, new State(false, message(error), null)); }
        });
    }
    private boolean isLoading() { return state.getValue() != null && state.getValue().loading; }
    private void execute(Operation operation) { execute(operation, false); }
    private void execute(Operation operation, boolean queueWhileLoading) {
        if (!queueWhileLoading && isLoading()) return;
        int request = ++generation;
        state.setValue(new State(true, null, null));
        executor.execute(() -> {
            try { publish(request, new State(false, null, operation.run())); }
            catch (Exception error) { publish(request, new State(false, message(error), null)); }
        });
    }
    private void publish(int request, State result) {
        mainThread.post(() -> { if (request == generation) state.setValue(result); });
    }
    private String message(Exception error) {
        if (error instanceof IllegalArgumentException || error instanceof IllegalStateException
                || error instanceof ph.edu.tip.goserve.data.SupabaseClient.ApiException) return error.getMessage();
        return "Could not connect to GoServe. Check your connection and retry.";
    }
    @Override protected void onCleared() { ++generation; executor.shutdownNow(); }
    private interface Operation { SessionProfile run() throws Exception; }
}
