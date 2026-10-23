package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.LinkedHashMap;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.AccountGateway;
import ph.edu.tip.goserve.data.DeliveryRepository;
import ph.edu.tip.goserve.data.RequestRepository;
import ph.edu.tip.goserve.domain.DeliveryData;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.SessionProfile;
import ph.edu.tip.goserve.domain.Role;

public final class RequestFilesViewModel extends AndroidViewModel {
    interface Actions {
        boolean allowed();
        void requireCurrent();
        RequestData.Record request(String id) throws Exception;
        DeliveryData.FilePage files(RequestData.Record request,DeliveryData.FileKind kind,String afterId) throws Exception;
    }
    public enum Mode { CONTENT, LOADING, UNAVAILABLE }
    public static final class State {
        public final Mode mode;
        public final RequestData.Record request;
        public final DeliveryData.FileKind kind;
        public final List<DeliveryData.File> items;
        public final String nextId,error;
        public final boolean loadingMore;
        State(Mode mode,RequestData.Record request,DeliveryData.FileKind kind,List<DeliveryData.File> items,String nextId,String error,boolean loadingMore) {
            this.mode=mode; this.request=request; this.kind=kind; this.items=java.util.Collections.unmodifiableList(new java.util.ArrayList<>(items));
            this.nextId=nextId; this.error=error; this.loadingMore=loadingMore;
        }
        public boolean busy() { return mode==Mode.LOADING || loadingMore; }
    }
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(Mode.CONTENT,null,null,List.of(),null,null,false));
    private final Actions actions;
    private final Executor worker,publication;
    private final AtomicLong generation=new AtomicLong();
    private volatile boolean cleared;
    private boolean handled,blocked,failed,refreshPending;
    private String target,failedAfter;
    private DeliveryData.FileKind kind;
    public RequestFilesViewModel(Application app) {
        this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post);
    }
    RequestFilesViewModel(Application app,Actions actions,Executor worker,Executor publication) {
        super(app); this.actions=actions; this.worker=worker; this.publication=publication;
        if(!actions.allowed())clear("FORBIDDEN");
    }
    public LiveData<State> state() { return state; }
    public void ensureRequest(String requestId,DeliveryData.FileKind kind) {
        if(cleared || handled || !access())return; handled=true;
        if(kind==null || requestId==null || !requestId.matches("[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}")) { clear("VALIDATION_FAILED"); return; }
        target=requestId; this.kind=kind; refresh();
    }
    public void refresh() {
        if(cleared || !access() || target==null)return;
        if(state.getValue().mode==Mode.LOADING) { refreshPending=true; return; }
        load(null,false);
    }
    public void loadMore() {
        if(cleared || !access())return; State held=state.getValue();
        if(held.busy() || held.error!=null || held.nextId==null || held.request==null)return;
        load(held.nextId,true);
    }
    public void retry() {
        if(cleared || !access() || !failed || state.getValue().busy())return;
        if(failedAfter==null)refresh(); else load(failedAfter,true);
    }
    private void load(String afterId,boolean append) {
        State previous=state.getValue(); String requestId=target; DeliveryData.FileKind requestedKind=kind;
        long request=generation.incrementAndGet(); failed=false; refreshPending=false;
        state.setValue(new State(append?Mode.CONTENT:Mode.LOADING,previous.request,requestedKind,previous.items,previous.nextId,null,append));
        if(cleared || generation.get()!=request)return;
        worker.execute(()->{
            if(cleared || generation.get()!=request)return;
            try {
                actions.requireCurrent(); RequestData.Record record=append?previous.request:actions.request(requestId); actions.requireCurrent();
                if(record==null)throw new RequestRepository.WorkflowException("NOT_FOUND");
                if(!requestId.equals(record.id))throw new RequestRepository.WorkflowException("FORBIDDEN");
                DeliveryData.FilePage page=actions.files(record,requestedKind,afterId); actions.requireCurrent();
                if(page==null)throw new RequestRepository.WorkflowException("VALIDATION_FAILED");
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    LinkedHashMap<String,DeliveryData.File> items=new LinkedHashMap<>();
                    if(append)for(DeliveryData.File value:previous.items)items.put(value.id,value);
                    for(DeliveryData.File value:page.items) {
                        DeliveryData.File prior=items.get(value.id); if(prior==null || value.version>=prior.version)items.put(value.id,value);
                    }
                    state.setValue(new State(Mode.CONTENT,record,requestedKind,new java.util.ArrayList<>(items.values()),page.nextId,null,false));
                    if(refreshPending) { refreshPending=false; refresh(); }
                });
            } catch(Exception error) {
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    String failure=error instanceof RequestRepository.WorkflowException?((RequestRepository.WorkflowException)error).code:"NETWORK_ERROR";
                    if("FORBIDDEN".equals(failure)) { clear(failure); return; }
                    boolean missing="NOT_FOUND".equals(failure); failed=true; failedAfter=missing?null:afterId; refreshPending=false;
                    state.setValue(new State(Mode.CONTENT,missing?null:previous.request,requestedKind,missing?List.of():previous.items,
                        missing?null:previous.nextId,failure,false));
                });
            }
        });
    }
    public void checkCurrentAccount() { if(!cleared)access(); }
    private boolean access() {
        if(blocked)return false;
        if(!actions.allowed()) { clear("FORBIDDEN"); return false; }
        try { actions.requireCurrent(); return true; }
        catch(IllegalStateException changed) { clear("STALE_ACCOUNT"); return false; }
    }
    private void clear(String error) {
        blocked=true; generation.incrementAndGet(); target=null; kind=null; failed=false; failedAfter=null; refreshPending=false;
        state.setValue(new State(Mode.UNAVAILABLE,null,null,List.of(),null,error,false));
    }
    private static Actions production(GoServeApplication app) {
        SessionProfile profile=app.auth().profile(); AccountGateway candidate=null;
        if(RequestListViewModel.permitted(profile))try {
            AccountGateway current=app.gateway(); if(profile.id.equals(current.lease().accountId))candidate=current;
        } catch(IllegalStateException changed) { candidate=null; }
        AccountGateway gateway=candidate; String office=profile!=null && profile.role==Role.STAFF?profile.officeId:null;
        RequestRepository requests=gateway==null?null:new RequestRepository(gateway);
        DeliveryRepository files=gateway==null?null:new DeliveryRepository(gateway);
        return new Actions() {
            public boolean allowed() { return gateway!=null; }
            public void requireCurrent() { if(gateway==null)throw new IllegalStateException("FORBIDDEN"); gateway.requireCurrent(); }
            public RequestData.Record request(String id) throws Exception { return requests.request(id,office); }
            public DeliveryData.FilePage files(RequestData.Record request,DeliveryData.FileKind kind,String afterId) throws Exception { return files.files(request,office,kind,afterId); }
        };
    }
    @Override protected void onCleared() {
        cleared=true; clear("STALE_ACCOUNT"); if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow(); super.onCleared();
    }
}
