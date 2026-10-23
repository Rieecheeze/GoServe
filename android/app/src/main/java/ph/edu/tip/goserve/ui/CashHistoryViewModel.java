package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.PaymentRepository;
import ph.edu.tip.goserve.domain.PaymentData;
import ph.edu.tip.goserve.domain.PaymentStatus;
import ph.edu.tip.goserve.domain.Role;
import ph.edu.tip.goserve.domain.SessionProfile;

public final class CashHistoryViewModel extends AndroidViewModel {
    interface Actions {
        boolean allowed();
        void requireCurrent();
        void invalidate();
        PaymentData.Page page(String afterId) throws Exception;
    }
    public enum Mode { CONTENT, LOADING, UNAVAILABLE }
    public static final class State {
        public final Mode mode;
        public final List<PaymentData.Record> items;
        public final String nextId,error;
        public final boolean loadingMore;
        State(Mode mode,List<PaymentData.Record> items,String nextId,String error,boolean loadingMore) {
            this.mode=mode; this.items=Collections.unmodifiableList(new java.util.ArrayList<>(items));
            this.nextId=nextId; this.error=error; this.loadingMore=loadingMore;
        }
        public boolean busy() { return mode==Mode.LOADING || loadingMore; }
    }
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(Mode.CONTENT,List.of(),null,null,false));
    private final Actions actions;
    private final Executor worker,publication;
    private final AtomicLong generation=new AtomicLong();
    private volatile boolean cleared;
    private boolean loaded,blocked,refreshPending,failed;
    private String failedCursor;
    public CashHistoryViewModel(Application app) {
        this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post);
    }
    CashHistoryViewModel(Application app,Actions actions,Executor worker,Executor publication) {
        super(app); this.actions=actions; this.worker=worker; this.publication=publication;
        if(!actions.allowed())clear("FORBIDDEN");
    }
    public LiveData<State> state() { return state; }
    public void ensureLoaded() { if(!loaded && !cleared) { loaded=true; refresh(); } }
    public void refresh() {
        if(cleared || !access())return;
        if(state.getValue().mode==Mode.LOADING) { refreshPending=true; return; }
        actions.invalidate(); load(null,false);
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
        State previous=state.getValue(); long request=generation.incrementAndGet();
        failed=false; refreshPending=false;
        state.setValue(new State(append?Mode.CONTENT:Mode.LOADING,previous.items,previous.nextId,null,append));
        worker.execute(()->{
            if(cleared || generation.get()!=request)return;
            try {
                actions.requireCurrent(); PaymentData.Page page=actions.page(cursor);
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    LinkedHashMap<String,PaymentData.Record> rows=new LinkedHashMap<>();
                    if(append)for(PaymentData.Record item:previous.items)rows.put(item.id,item);
                    for(PaymentData.Record item:page.items) {
                        PaymentData.Record prior=rows.get(item.id);
                        if(prior==null || item.version>=prior.version)rows.put(item.id,item);
                    }
                    state.setValue(new State(Mode.CONTENT,new java.util.ArrayList<>(rows.values()),page.nextId,null,false));
                    if(refreshPending) { refreshPending=false; refresh(); }
                });
            } catch(Exception error) {
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    String code=error instanceof PaymentRepository.WorkflowException?((PaymentRepository.WorkflowException)error).code:"NETWORK_ERROR";
                    if("FORBIDDEN".equals(code)) { clear(code); return; }
                    failed=true; failedCursor=cursor; refreshPending=false;
                    state.setValue(new State(Mode.CONTENT,previous.items,previous.nextId,code,false));
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
        blocked=true; generation.incrementAndGet(); actions.invalidate(); refreshPending=false; failed=false; failedCursor=null;
        state.setValue(new State(Mode.UNAVAILABLE,List.of(),null,code,false));
    }
    private static Actions production(GoServeApplication app) {
        SessionProfile profile=app.auth().profile();
        boolean permitted=profile!=null && profile.ready() && profile.role==Role.TELLER && "OFF-CASH".equals(profile.officeId);
        PaymentRepository repository=permitted?new PaymentRepository(app.gateway()):null;
        return new Actions() {
            public boolean allowed() { return permitted; }
            public void requireCurrent() { if(repository==null)throw new IllegalStateException("FORBIDDEN"); repository.requireCurrent(); }
            public void invalidate() { if(repository!=null)repository.invalidate(); }
            public PaymentData.Page page(String after) throws Exception { return repository.cashPayments(PaymentStatus.PAID,after); }
        };
    }
    @Override protected void onCleared() {
        cleared=true; clear("STALE_ACCOUNT");
        if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow();
        super.onCleared();
    }
}
