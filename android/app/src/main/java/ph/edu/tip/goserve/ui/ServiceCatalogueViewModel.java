package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.AccountGateway;
import ph.edu.tip.goserve.data.RequestRepository;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.Role;
import ph.edu.tip.goserve.domain.SessionProfile;

public final class ServiceCatalogueViewModel extends AndroidViewModel {
    interface Actions {
        boolean allowed();
        void requireCurrent();
        RequestData.ServicePage services(String officeId,String afterId) throws Exception;
    }
    public enum Mode { EMPTY, LOADING, CONTENT, UNAVAILABLE }
    public static final class State {
        public final Mode mode;
        public final String officeId,nextId,error;
        public final List<RequestData.Service> items;
        public final boolean loadingMore;
        State(Mode mode,String officeId,List<RequestData.Service> items,String nextId,String error,boolean loadingMore) {
            this.mode=mode; this.officeId=officeId; this.items=List.copyOf(items); this.nextId=nextId; this.error=error; this.loadingMore=loadingMore;
        }
        public boolean busy() { return mode==Mode.LOADING || loadingMore; }
    }
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(Mode.EMPTY,null,List.of(),null,null,false));
    private final Actions actions;
    private final Executor worker,publication;
    private final AtomicLong generation=new AtomicLong();
    private volatile boolean cleared;
    private boolean handled,blocked,refreshPending,failed;
    private String officeId,failedCursor;
    public ServiceCatalogueViewModel(Application app) {
        this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post);
    }
    ServiceCatalogueViewModel(Application app,Actions actions,Executor worker,Executor publication) {
        super(app); this.actions=actions; this.worker=worker; this.publication=publication;
        if(!actions.allowed())clear("FORBIDDEN");
    }
    public LiveData<State> state() { return state; }
    public void ensureOffice(String id) {
        if(cleared || handled || !access())return; handled=true;
        if(id==null || id.trim().isEmpty()) { state.setValue(new State(Mode.CONTENT,null,List.of(),null,"VALIDATION_FAILED",false)); return; }
        officeId=id; refresh();
    }
    public void refresh() {
        if(cleared || !access() || officeId==null)return;
        if(state.getValue().mode==Mode.LOADING) { refreshPending=true; return; }
        load(null,false);
    }
    public void loadMore() {
        if(cleared || !access())return; State current=state.getValue();
        if(current.busy() || current.error!=null || current.nextId==null)return;
        load(current.nextId,true);
    }
    public void retry() {
        if(cleared || !access() || !failed || state.getValue().busy())return;
        if(failedCursor==null)refresh(); else load(failedCursor,true);
    }
    private void load(String cursor,boolean append) {
        State previous=state.getValue(); long request=generation.incrementAndGet(); String office=officeId;
        failed=false; refreshPending=false;
        state.setValue(new State(append?Mode.CONTENT:Mode.LOADING,office,previous.items,previous.nextId,null,append));
        worker.execute(()->{
            if(cleared || generation.get()!=request)return;
            try {
                actions.requireCurrent(); RequestData.ServicePage page=actions.services(office,cursor);
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    LinkedHashMap<String,RequestData.Service> values=new LinkedHashMap<>();
                    if(append)for(RequestData.Service item:previous.items)values.put(item.id,item);
                    for(RequestData.Service item:page.items) {
                        RequestData.Service prior=values.get(item.id);
                        if(prior==null || item.version>=prior.version)values.put(item.id,item);
                    }
                    state.setValue(new State(Mode.CONTENT,office,new java.util.ArrayList<>(values.values()),page.nextId,null,false));
                    if(refreshPending) { refreshPending=false; refresh(); }
                });
            } catch(Exception error) {
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    String code=error instanceof RequestRepository.WorkflowException?((RequestRepository.WorkflowException)error).code:"NETWORK_ERROR";
                    if("FORBIDDEN".equals(code)) { clear(code); return; }
                    failed=true; failedCursor=cursor; refreshPending=false;
                    state.setValue(new State(Mode.CONTENT,office,previous.items,previous.nextId,code,false));
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
    private void clear(String code) {
        blocked=true; generation.incrementAndGet(); officeId=null; failed=false; failedCursor=null; refreshPending=false;
        state.setValue(new State(Mode.UNAVAILABLE,null,List.of(),null,code,false));
    }
    private static Actions production(GoServeApplication app) {
        SessionProfile profile=app.auth().profile(); AccountGateway candidate=null;
        if(profile!=null && profile.ready() && profile.role==Role.STUDENT)try {
            AccountGateway current=app.gateway(); if(profile.id.equals(current.lease().accountId))candidate=current;
        } catch(IllegalStateException changed) { candidate=null; }
        AccountGateway gateway=candidate;
        RequestRepository repository=gateway==null?null:new RequestRepository(gateway);
        return new Actions() {
            public boolean allowed() { return gateway!=null; }
            public void requireCurrent() { if(gateway==null)throw new IllegalStateException("FORBIDDEN"); gateway.requireCurrent(); }
            public RequestData.ServicePage services(String office,String cursor) throws Exception { return repository.services(office,cursor); }
        };
    }
    @Override protected void onCleared() {
        cleared=true; clear("STALE_ACCOUNT"); if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow(); super.onCleared();
    }
}
