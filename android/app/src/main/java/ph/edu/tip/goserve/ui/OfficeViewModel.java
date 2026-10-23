package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.OfficeRepository;
import ph.edu.tip.goserve.domain.Office;

public final class OfficeViewModel extends AndroidViewModel {
    interface Actions{void requireCurrent();List<Office> load()throws Exception;List<Office> preview();}
    public static final class State {
        public final boolean loading;
        public final List<Office> offices;
        public final String error;
        State(boolean loading, List<Office> offices, String error) { this.loading = loading; this.offices = List.copyOf(offices); this.error = error; }
    }
    private final Actions actions;
    private final Executor worker,publication;
    private volatile int generation;
    private volatile boolean cleared;
    private boolean pending,previewMode;
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(false, Collections.emptyList(), null));
    private boolean started;
    public OfficeViewModel(Application app) { this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post); }
    OfficeViewModel(Application app,Actions actions,Executor worker,Executor publication){super(app);this.actions=actions;this.worker=worker;this.publication=publication;}
    public LiveData<State> state() { return state; }
    public void ensureLoaded(boolean preview) { if (!started) { started = true; reload(preview); } }
    public void reload(boolean preview) {
        if(cleared)return;
        if(preview){previewMode=true;pending=false;++generation;state.setValue(new State(false,actions.preview(),null));return;}
        previewMode=false;if(!current())return;
        if(state.getValue().loading){pending=true;return;}
        List<Office> held=state.getValue().offices;state.setValue(new State(true,held,null));
        int request=++generation;pending=false;
        worker.execute(() -> {
            if(cleared || request!=generation)return;
            try {
                List<Office> offices=actions.load();
                publication.execute(() -> {
                    if (cleared || request!=generation) return;
                    if(!current())return;state.setValue(new State(false,offices,null));
                    if(pending){pending=false;reload(false);}
                });
            }
            catch (Exception error) {
                publication.execute(() -> { if(cleared || request!=generation || !current())return;pending=false;state.setValue(new State(false,held,"Could not load offices. Check your connection and retry.")); });
            }
        });
    }
    public void checkCurrentAccount(){if(!cleared && !previewMode)current();}
    private boolean current(){try{actions.requireCurrent();return true;}catch(IllegalStateException changed){++generation;pending=false;state.setValue(new State(false,List.of(),"Your account changed. Return to sign in."));return false;}}
    private static Actions production(GoServeApplication app){
        OfficeRepository repository=app.offices();ph.edu.tip.goserve.data.AuthRepository auth=app.auth();
        return new Actions(){private ph.edu.tip.goserve.data.AuthRepository.SessionLease lease;public synchronized void requireCurrent(){if(lease==null)lease=auth.sessionLease();auth.requireCurrent(lease);}public List<Office> load()throws Exception{requireCurrent();return repository.load(lease);}public List<Office> preview(){return repository.preview();}};
    }
    @Override protected void onCleared() { cleared=true;++generation;if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow();super.onCleared(); }
}
