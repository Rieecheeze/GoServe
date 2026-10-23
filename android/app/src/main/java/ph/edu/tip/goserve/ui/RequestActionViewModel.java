package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.AccountGateway;
import ph.edu.tip.goserve.data.RequestRepository;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.RequestStatus;
import ph.edu.tip.goserve.domain.SessionProfile;
import ph.edu.tip.goserve.domain.Role;

public final class RequestActionViewModel extends AndroidViewModel {
    interface Actions {
        boolean allowed();
        String officeId();
        void requireCurrent();
        RequestData.RequestOptions options(RequestData.Record request) throws Exception;
        RequestData.Transition draft(RequestData.Record request,RequestData.RequestOptions options,RequestStatus target,String reason,RequestData.Transition previous) throws Exception;
        RequestData.Record commit(RequestData.Transition input) throws Exception;
    }
    public enum Mode { EMPTY, LOADING, EDITING, REVIEW, SUBMITTING, COMPLETE, BLOCKED, UNAVAILABLE }
    public static final class State {
        public final Mode mode;
        public final RequestData.Record request,result;
        public final RequestData.RequestOptions options;
        public final RequestData.Transition intent;
        public final String reason,error;
        public final boolean uncertain;
        State(Mode mode,RequestData.Record request,RequestData.RequestOptions options,RequestData.Transition intent,
                RequestData.Record result,String reason,String error,boolean uncertain) {
            this.mode=mode; this.request=request; this.options=options; this.intent=intent; this.result=result;
            this.reason=reason; this.error=error; this.uncertain=uncertain;
        }
        public boolean busy() { return mode==Mode.LOADING || mode==Mode.SUBMITTING; }
    }
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(Mode.EMPTY,null,null,null,null,"",null,false));
    private final Actions actions;
    private final Executor worker,publication;
    private final AtomicLong generation=new AtomicLong();
    private volatile boolean cleared;
    private boolean handled,blocked;
    public RequestActionViewModel(Application app) {
        this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post);
    }
    RequestActionViewModel(Application app,Actions actions,Executor worker,Executor publication) {
        super(app); this.actions=actions; this.worker=worker; this.publication=publication;
        if(!actions.allowed())clear("FORBIDDEN");
    }
    public LiveData<State> state() { return state; }
    public String officeId() { return blocked?null:actions.officeId(); }
    public void ensureRequest(RequestData.Record request) {
        if(cleared || handled || !access())return; handled=true;
        if(request==null || request.snapshot==null || request.id==null || request.version<1) {
            state.setValue(new State(Mode.BLOCKED,null,null,null,null,"","VALIDATION_FAILED",false)); return;
        }
        if(actions.officeId()!=null && !actions.officeId().equals(request.officeId)) { clear("FORBIDDEN"); return; }
        loadOptions(request);
    }
    public void retryOptions() {
        if(cleared || !access())return; State held=state.getValue();
        if(held.mode==Mode.EDITING && held.options==null && held.request!=null)loadOptions(held.request);
    }
    private void loadOptions(RequestData.Record record) {
        State held=state.getValue(); long request=generation.incrementAndGet();
        state.setValue(new State(Mode.LOADING,record,null,null,null,held.reason,null,false));
        if(cleared || generation.get()!=request)return;
        worker.execute(()->{
            if(cleared || generation.get()!=request)return;
            try {
                actions.requireCurrent(); RequestData.RequestOptions options=actions.options(record); actions.requireCurrent();
                if(options==null || !record.id.equals(options.requestId))throw new RequestRepository.WorkflowException("VALIDATION_FAILED");
                if(record.version!=options.requestVersion)throw new RequestRepository.WorkflowException("STALE_VERSION");
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    state.setValue(new State(Mode.EDITING,record,options,null,null,state.getValue().reason,null,false));
                });
            } catch(Exception error) {
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    String failure=code(error); if("FORBIDDEN".equals(failure)) { clear(failure); return; }
                    Mode mode="STALE_VERSION".equals(failure) || "VALIDATION_FAILED".equals(failure)?Mode.BLOCKED:Mode.EDITING;
                    state.setValue(new State(mode,record,null,null,null,state.getValue().reason,failure,false));
                });
            }
        });
    }
    public void setReason(String value) {
        if(cleared || !access())return; State held=state.getValue();
        if(held.mode!=Mode.EDITING && held.mode!=Mode.LOADING)return;
        state.setValue(new State(held.mode,held.request,held.options,held.intent,null,value==null?"":value,held.options==null?held.error:null,false));
    }
    public void review(RequestStatus target) {
        if(cleared || !access())return; State held=state.getValue();
        if(held.mode!=Mode.EDITING || held.options==null || held.request==null)return;
        try {
            RequestData.Transition intent=actions.draft(held.request,held.options,target,held.reason,held.intent); actions.requireCurrent();
            if(!access())return;
            state.setValue(new State(Mode.REVIEW,held.request,held.options,intent,null,held.reason,null,false));
        } catch(Exception error) {
            if(!access())return; String failure=code(error);
            if("FORBIDDEN".equals(failure)) { clear(failure); return; }
            state.setValue(new State(Mode.EDITING,held.request,held.options,held.intent,null,held.reason,failure,false));
        }
    }
    public void edit() {
        if(cleared || !access())return; State held=state.getValue();
        if(held.mode==Mode.REVIEW && !held.uncertain)
            state.setValue(new State(Mode.EDITING,held.request,held.options,held.intent,null,held.reason,null,false));
    }
    public void submit() {
        if(cleared || !access())return; State held=state.getValue();
        if(held.mode!=Mode.REVIEW || held.intent==null)return;
        RequestData.Transition intent=held.intent; long request=generation.incrementAndGet();
        state.setValue(new State(Mode.SUBMITTING,held.request,held.options,intent,null,held.reason,null,held.uncertain));
        if(cleared || generation.get()!=request)return;
        worker.execute(()->{
            if(cleared || generation.get()!=request)return;
            try {
                actions.requireCurrent(); RequestData.Record result=actions.commit(intent); actions.requireCurrent();
                if(result==null || !intent.request.id.equals(result.id) || result.version<=intent.request.version)
                    throw new RequestRepository.WorkflowException("VALIDATION_FAILED");
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    state.setValue(new State(Mode.COMPLETE,result,null,null,result,"",null,false));
                });
            } catch(Exception error) {
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    String failure=code(error); if("FORBIDDEN".equals(failure)) { clear(failure); return; }
                    boolean definite=java.util.Arrays.asList("STALE_VERSION","INVALID_STATE","PAYMENT_REQUIRED","POLICY_BLOCKED","CONFIGURATION_REQUIRED","UPLOAD_INVALID").contains(failure);
                    state.setValue(new State(definite?Mode.BLOCKED:Mode.REVIEW,held.request,definite?null:held.options,
                        definite?null:intent,null,held.reason,failure,!definite));
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
        blocked=true; generation.incrementAndGet(); state.setValue(new State(Mode.UNAVAILABLE,null,null,null,null,"",error,false));
    }
    private static String code(Exception error) {
        return error instanceof RequestRepository.WorkflowException?((RequestRepository.WorkflowException)error).code:"NETWORK_ERROR";
    }
    private static Actions production(GoServeApplication app) {
        SessionProfile profile=app.auth().profile(); AccountGateway candidate=null;
        if(RequestListViewModel.permitted(profile))try {
            AccountGateway current=app.gateway(); if(profile.id.equals(current.lease().accountId))candidate=current;
        } catch(IllegalStateException changed) { candidate=null; }
        AccountGateway gateway=candidate; RequestRepository repo=gateway==null?null:new RequestRepository(gateway);
        String office=profile!=null && profile.role==Role.STAFF?profile.officeId:null;
        return new Actions() {
            public boolean allowed() { return gateway!=null; }
            public String officeId() { return office; }
            public void requireCurrent() { if(gateway==null)throw new IllegalStateException("FORBIDDEN"); gateway.requireCurrent(); }
            public RequestData.RequestOptions options(RequestData.Record request) throws Exception { return repo.requestOptions(request); }
            public RequestData.Transition draft(RequestData.Record request,RequestData.RequestOptions options,RequestStatus target,String reason,RequestData.Transition previous) throws Exception {
                return repo.transitionDraft(request,office,options,target,reason,previous);
            }
            public RequestData.Record commit(RequestData.Transition input) throws Exception { return repo.transition(input); }
        };
    }
    @Override protected void onCleared() {
        cleared=true; clear("STALE_ACCOUNT"); if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow(); super.onCleared();
    }
}
