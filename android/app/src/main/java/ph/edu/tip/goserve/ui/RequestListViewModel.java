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
import ph.edu.tip.goserve.domain.RequestStatus;
import ph.edu.tip.goserve.domain.Role;
import ph.edu.tip.goserve.domain.SessionProfile;

public final class RequestListViewModel extends AndroidViewModel {
    interface Actions {
        boolean allowed();
        String officeId();
        void requireCurrent();
        RequestData.RequestPage page(RequestStatus filter,String createdAt,String id) throws Exception;
    }
    public enum Mode { CONTENT, LOADING, UNAVAILABLE }
    public static final class State {
        public final Mode mode;
        public final List<RequestData.Record> items;
        public final String nextCreatedAt,nextId,error;
        public final boolean loadingMore;
        State(Mode mode,List<RequestData.Record> items,String nextCreatedAt,String nextId,String error,boolean loadingMore) {
            this.mode=mode; this.items=java.util.Collections.unmodifiableList(new java.util.ArrayList<>(items));
            this.nextCreatedAt=nextCreatedAt; this.nextId=nextId; this.error=error; this.loadingMore=loadingMore;
        }
        public boolean busy() { return mode==Mode.LOADING || loadingMore; }
    }
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(Mode.CONTENT,List.of(),null,null,null,false));
    private final Actions actions;
    private final Executor worker,publication;
    private final AtomicLong generation=new AtomicLong();
    private volatile boolean cleared;
    private boolean loaded,blocked,refreshPending,failed;
    private String failedAt,failedId;
    private RequestStatus filter;
    public RequestListViewModel(Application app) {
        this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post);
    }
    RequestListViewModel(Application app,Actions actions,Executor worker,Executor publication) {
        super(app); this.actions=actions; this.worker=worker; this.publication=publication;
        if(!actions.allowed())clear("FORBIDDEN");
    }
    static boolean permitted(SessionProfile profile) {
        if(profile==null || !profile.ready())return false;
        if(profile.role==Role.STUDENT)return true;
        return profile.role==Role.STAFF && profile.officeId!=null
            && (!profile.officeId.startsWith("DEP-") || "secretary".equals(profile.capability));
    }
    public LiveData<State> state() { return state; }
    public String officeId() { return blocked?null:actions.officeId(); }
    public RequestStatus filter() { return blocked?null:filter; }
    public void selectFilter(RequestStatus value) {
        if(cleared || !access() || actions.officeId()==null || filter==value)return;
        if(value!=null && value!=RequestStatus.SUBMITTED && value!=RequestStatus.PROCESSING && value!=RequestStatus.READY_FOR_PICKUP_DOWNLOAD)return;
        filter=value; loaded=true; load(null,null,false,true);
    }
    public void ensureLoaded() { if(!loaded && !cleared) { loaded=true; refresh(); } }
    public void refresh() {
        if(cleared || !access())return;
        if(state.getValue().mode==Mode.LOADING) { refreshPending=true; return; }
        load(null,null,false);
    }
    public void loadMore() {
        if(cleared || !access())return; State held=state.getValue();
        if(held.busy() || held.error!=null || held.nextCreatedAt==null || held.nextId==null)return;
        load(held.nextCreatedAt,held.nextId,true);
    }
    public void retry() {
        if(cleared || !access() || !failed || state.getValue().busy())return;
        if(failedAt==null)refresh(); else load(failedAt,failedId,true);
    }
    private void load(String createdAt,String id,boolean append) {
        load(createdAt,id,append,false);
    }
    private void load(String createdAt,String id,boolean append,boolean clearItems) {
        State previous=clearItems?new State(Mode.CONTENT,List.of(),null,null,null,false):state.getValue();
        RequestStatus selectedFilter=filter; long request=generation.incrementAndGet();
        failed=false; refreshPending=false;
        state.setValue(new State(append?Mode.CONTENT:Mode.LOADING,previous.items,previous.nextCreatedAt,previous.nextId,null,append));
        worker.execute(()->{
            if(cleared || generation.get()!=request)return;
            try {
                actions.requireCurrent(); RequestData.RequestPage page=actions.page(selectedFilter,createdAt,id);
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    LinkedHashMap<String,RequestData.Record> records=new LinkedHashMap<>();
                    if(append)for(RequestData.Record value:previous.items)records.put(value.id,value);
                    for(RequestData.Record value:page.items) {
                        RequestData.Record prior=records.get(value.id);
                        if(prior==null || value.version>=prior.version)records.put(value.id,value);
                    }
                    state.setValue(new State(Mode.CONTENT,new java.util.ArrayList<>(records.values()),page.nextCreatedAt,page.nextId,null,false));
                    if(refreshPending) { refreshPending=false; refresh(); }
                });
            } catch(Exception error) {
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    String code=error instanceof RequestRepository.WorkflowException?((RequestRepository.WorkflowException)error).code:"NETWORK_ERROR";
                    if("FORBIDDEN".equals(code)) { clear(code); return; }
                    failed=true; failedAt=createdAt; failedId=id; refreshPending=false;
                    state.setValue(new State(Mode.CONTENT,previous.items,previous.nextCreatedAt,previous.nextId,code,false));
                });
            }
        });
    }
    public void checkCurrentAccount() { if(!cleared)access(); }
    private boolean access() {
        if(blocked)return false;
        if(!actions.allowed()) { clear("FORBIDDEN"); return false; }
        try { actions.requireCurrent(); return true; }
        catch(IllegalStateException replaced) { clear("STALE_ACCOUNT"); return false; }
    }
    private void clear(String code) {
        blocked=true; generation.incrementAndGet(); refreshPending=false; failed=false; failedAt=null; failedId=null;
        filter=null;
        state.setValue(new State(Mode.UNAVAILABLE,List.of(),null,null,code,false));
    }
    private static Actions production(GoServeApplication app) {
        SessionProfile profile=app.auth().profile(); AccountGateway candidate=null;
        if(permitted(profile))try {
            AccountGateway current=app.gateway();
            if(profile.id.equals(current.lease().accountId))candidate=current;
        } catch(IllegalStateException changed) { candidate=null; }
        AccountGateway gateway=candidate;
        String assignedOffice=profile!=null && profile.role==Role.STAFF?profile.officeId:null;
        RequestRepository repository=gateway==null?null:new RequestRepository(gateway);
        return new Actions() {
            public boolean allowed() { return gateway!=null; }
            public String officeId() { return assignedOffice; }
            public void requireCurrent() { if(repository==null)throw new IllegalStateException("FORBIDDEN"); repository.requireCurrent(); }
            public RequestData.RequestPage page(RequestStatus filter,String createdAt,String id) throws Exception { return repository.requests(assignedOffice,filter,createdAt,id); }
        };
    }
    @Override protected void onCleared() {
        cleared=true; clear("STALE_ACCOUNT");
        if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow();
        super.onCleared();
    }
}
