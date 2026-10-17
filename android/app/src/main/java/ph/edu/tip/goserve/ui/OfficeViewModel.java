package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.OfficeRepository;
import ph.edu.tip.goserve.domain.Office;

public final class OfficeViewModel extends AndroidViewModel {
    public static final class State {
        public final boolean loading;
        public final List<Office> offices;
        public final String error;
        State(boolean loading, List<Office> offices, String error) { this.loading = loading; this.offices = offices; this.error = error; }
    }
    private final OfficeRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(false, Collections.emptyList(), null));
    private boolean started;
    public OfficeViewModel(Application app) { super(app); repository = ((GoServeApplication) app).offices(); }
    public LiveData<State> state() { return state; }
    public void ensureLoaded(boolean preview) { if (!started) { started = true; reload(preview); } }
    public void reload(boolean preview) {
        if (state.getValue().loading) return;
        if (preview) { state.setValue(new State(false, repository.preview(), null)); return; }
        state.setValue(new State(true, Collections.emptyList(), null));
        executor.execute(() -> {
            try { state.postValue(new State(false, repository.load(), null)); }
            catch (Exception error) {
                String message = error instanceof ph.edu.tip.goserve.data.SupabaseClient.ApiException || error instanceof IllegalStateException
                        ? error.getMessage() : "Could not load offices. Check your connection and retry.";
                state.postValue(new State(false, Collections.emptyList(), message));
            }
        });
    }
    @Override protected void onCleared() { executor.shutdownNow(); }
}
