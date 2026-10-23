package ph.edu.tip.goserve.ui;

import android.app.Application;
import android.content.Intent;
import android.content.ClipData;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.AccountGateway;
import ph.edu.tip.goserve.data.DeliveryRepository;
import ph.edu.tip.goserve.data.PrivateDocument;
import ph.edu.tip.goserve.data.RequestRepository;
import ph.edu.tip.goserve.domain.DeliveryData;
import ph.edu.tip.goserve.domain.SessionProfile;

public final class EvidenceViewerViewModel extends AndroidViewModel {
    interface Actions {
        boolean allowed();
        void requireCurrent();
        PrivateDocument download(DeliveryData.File file) throws Exception;
    }
    public enum Mode { IDLE, DOWNLOADING, READY, OPEN, ERROR, UNAVAILABLE }
    public static final class State {
        public final Mode mode;
        public final String fileId,error;
        State(Mode mode,String fileId,String error) { this.mode=mode; this.fileId=fileId; this.error=error; }
        public boolean busy() { return mode==Mode.DOWNLOADING || mode==Mode.READY || mode==Mode.OPEN; }
    }
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(Mode.IDLE,null,null));
    private final Actions actions;
    private final Executor worker,publication;
    private final AtomicLong generation=new AtomicLong();
    private volatile boolean cleared;
    private boolean blocked;
    private DeliveryData.File selected;
    private PrivateDocument document;
    public EvidenceViewerViewModel(Application app) {
        this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post);
    }
    EvidenceViewerViewModel(Application app,Actions actions,Executor worker,Executor publication) {
        super(app); this.actions=actions; this.worker=worker; this.publication=publication;
        if(!actions.allowed())clear("FORBIDDEN");
    }
    public LiveData<State> state() { return state; }
    public void open(DeliveryData.File file) {
        if(cleared || !access() || state.getValue().busy())return;
        if(file==null || file.kind!=DeliveryData.FileKind.EVIDENCE) {
            selected=null; state.setValue(new State(Mode.ERROR,null,"VALIDATION_FAILED")); return;
        }
        release(); selected=file; long request=generation.incrementAndGet();
        state.setValue(new State(Mode.DOWNLOADING,file.id,null));
        if(cleared || generation.get()!=request)return;
        worker.execute(()->{
            if(cleared || generation.get()!=request)return;
            PrivateDocument copy=null;
            try {
                actions.requireCurrent(); copy=actions.download(file); actions.requireCurrent();
                if(copy==null)throw new RequestRepository.WorkflowException("VALIDATION_FAILED");
                if(cleared || generation.get()!=request) { copy.close(); return; }
                PrivateDocument completed=copy;
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access()) { completed.close(); return; }
                    document=completed; state.setValue(new State(Mode.READY,file.id,null));
                });
            } catch(Exception error) {
                if(copy!=null)copy.close();
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    String code=error instanceof RequestRepository.WorkflowException?((RequestRepository.WorkflowException)error).code:"NETWORK_ERROR";
                    if("FORBIDDEN".equals(code)) { clear(code); return; }
                    state.setValue(new State(Mode.ERROR,file.id,code));
                });
            }
        });
    }
    public Intent takeIntent() {
        if(cleared || !access() || state.getValue().mode!=Mode.READY || document==null)return null;
        long request=generation.get();
        Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(document.uri(),document.mimeType());
        intent.setClipData(ClipData.newRawUri("Request evidence",document.uri()));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        state.setValue(new State(Mode.OPEN,selected.id,null));
        return generation.get()==request && access()?intent:null;
    }
    public void returned() {
        if(cleared || !access())return;
        cancel();
    }
    public void viewerUnavailable() {
        if(cleared || !access() || state.getValue().mode!=Mode.OPEN)return;
        release(); state.setValue(new State(Mode.ERROR,selected==null?null:selected.id,"NO_VIEWER"));
    }
    public void retry() { if(state.getValue().mode==Mode.ERROR && selected!=null)open(selected); }
    public void cancel() {
        if(cleared || blocked)return;
        generation.incrementAndGet(); release(); selected=null; state.setValue(new State(Mode.IDLE,null,null));
    }
    public void checkCurrentAccount() { if(!cleared)access(); }
    private boolean access() {
        if(blocked)return false;
        if(!actions.allowed()) { clear("FORBIDDEN"); return false; }
        try { actions.requireCurrent(); return true; }
        catch(IllegalStateException changed) { clear("STALE_ACCOUNT"); return false; }
    }
    private void release() { if(document!=null) { document.close(); document=null; } }
    private void clear(String error) {
        blocked=true; generation.incrementAndGet(); release(); selected=null; state.setValue(new State(Mode.UNAVAILABLE,null,error));
    }
    private static Actions production(GoServeApplication app) {
        SessionProfile profile=app.auth().profile(); AccountGateway candidate=null;
        if(RequestListViewModel.permitted(profile))try {
            AccountGateway current=app.gateway(); if(profile.id.equals(current.lease().accountId))candidate=current;
        } catch(IllegalStateException changed) { candidate=null; }
        AccountGateway gateway=candidate; DeliveryRepository files=gateway==null?null:new DeliveryRepository(gateway);
        return new Actions() {
            public boolean allowed() { return gateway!=null; }
            public void requireCurrent() { if(gateway==null)throw new IllegalStateException("FORBIDDEN"); gateway.requireCurrent(); }
            public PrivateDocument download(DeliveryData.File file) throws Exception { return files.evidenceDocument(app,file); }
        };
    }
    @Override protected void onCleared() {
        cleared=true; clear("STALE_ACCOUNT"); if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow(); super.onCleared();
    }
}
