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
import ph.edu.tip.goserve.data.PaymentRepository;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.PaymentData;
import ph.edu.tip.goserve.domain.PaymentStatus;
import ph.edu.tip.goserve.domain.Role;
import ph.edu.tip.goserve.domain.SessionProfile;

public final class RequestDetailViewModel extends AndroidViewModel {
    interface Actions {
        boolean allowed();
        void requireCurrent();
        RequestData.Record request(String id) throws Exception;
        PaymentData.Record payment(String requestId) throws Exception;
    }
    public enum Mode { EMPTY, LOADING, CONTENT, ERROR, UNAVAILABLE }
    public static final class State {
        public final Mode mode;
        public final String requestId,error,paymentError;
        public final RequestData.Record request;
        public final PaymentData.Record payment;
        State(Mode mode,String requestId,RequestData.Record request,PaymentData.Record payment,String error,String paymentError) {
            this.mode=mode; this.requestId=requestId; this.request=request; this.payment=payment; this.error=error; this.paymentError=paymentError;
        }
        public boolean busy() { return mode==Mode.LOADING; }
        public boolean confirmedPayment() { return mode==Mode.CONTENT && paymentError==null && payment!=null && payment.status==PaymentStatus.PAID; }
    }
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(Mode.EMPTY,null,null,null,null,null));
    private final Actions actions;
    private final Executor worker,publication;
    private final AtomicLong generation=new AtomicLong();
    private volatile boolean cleared;
    private boolean blocked,handled,refreshPending;
    private String target;
    public RequestDetailViewModel(Application app) {
        this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post);
    }
    RequestDetailViewModel(Application app,Actions actions,Executor worker,Executor publication) {
        super(app); this.actions=actions; this.worker=worker; this.publication=publication;
        if(!actions.allowed())clear("FORBIDDEN");
    }
    public LiveData<State> state() { return state; }
    public void ensureRequest(String id) {
        if(cleared || handled || !access())return; handled=true;
        if(id==null || id.trim().isEmpty()) { state.setValue(new State(Mode.ERROR,null,null,null,"VALIDATION_FAILED",null)); return; }
        target=id; refresh();
    }
    public void refresh() {
        if(cleared || !access() || target==null)return;
        if(state.getValue().busy()) { refreshPending=true; return; }
        State previous=state.getValue(); String requestedId=target; long request=generation.incrementAndGet(); refreshPending=false;
        state.setValue(new State(Mode.LOADING,requestedId,previous.request,null,null,null));
        worker.execute(()->{
            if(cleared || generation.get()!=request)return;
            try {
                actions.requireCurrent(); RequestData.Record record=actions.request(requestedId); actions.requireCurrent();
                PaymentData.Record payment=null; String paymentError=null;
                try { payment=actions.payment(record.id); }
                catch(Exception unavailable) { if("FORBIDDEN".equals(code(unavailable)))throw unavailable; paymentError=code(unavailable); }
                actions.requireCurrent(); PaymentData.Record result=payment; String paymentFailure=paymentError;
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    state.setValue(new State(Mode.CONTENT,requestedId,record,result,null,paymentFailure));
                    if(refreshPending) { refreshPending=false; refresh(); }
                });
            } catch(Exception error) {
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    String failure=code(error); refreshPending=false;
                    if("FORBIDDEN".equals(failure)) { clear(failure); return; }
                    state.setValue(new State(Mode.ERROR,requestedId,"NOT_FOUND".equals(failure)?null:previous.request,null,failure,null));
                });
            }
        });
    }
    public void retry() {
        State current=state.getValue();
        if(!cleared && !current.busy() && (current.error!=null || current.paymentError!=null))refresh();
    }
    public void checkCurrentAccount() { if(!cleared)access(); }
    private boolean access() {
        if(blocked)return false;
        if(!actions.allowed()) { clear("FORBIDDEN"); return false; }
        try { actions.requireCurrent(); return true; }
        catch(IllegalStateException changed) { clear("STALE_ACCOUNT"); return false; }
    }
    private void clear(String code) {
        blocked=true; generation.incrementAndGet(); target=null; refreshPending=false;
        state.setValue(new State(Mode.UNAVAILABLE,null,null,null,code,null));
    }
    private static String code(Exception error) {
        if(error instanceof RequestRepository.WorkflowException)return ((RequestRepository.WorkflowException)error).code;
        if(error instanceof PaymentRepository.WorkflowException)return ((PaymentRepository.WorkflowException)error).code;
        return "NETWORK_ERROR";
    }
    private static Actions production(GoServeApplication app) {
        SessionProfile profile=app.auth().profile(); AccountGateway candidate=null;
        if(RequestListViewModel.permitted(profile))try {
            AccountGateway current=app.gateway();
            if(profile.id.equals(current.lease().accountId))candidate=current;
        } catch(IllegalStateException changed) { candidate=null; }
        AccountGateway gateway=candidate;
        String office=profile!=null && profile.role==Role.STAFF?profile.officeId:null;
        RequestRepository requests=gateway==null?null:new RequestRepository(gateway);
        PaymentRepository payments=gateway==null?null:new PaymentRepository(gateway);
        return new Actions() {
            public boolean allowed() { return gateway!=null; }
            public void requireCurrent() { if(gateway==null)throw new IllegalStateException("FORBIDDEN"); gateway.requireCurrent(); }
            public RequestData.Record request(String id) throws Exception { return requests.request(id,office); }
            public PaymentData.Record payment(String requestId) throws Exception { return payments.payment(requestId); }
        };
    }
    @Override protected void onCleared() {
        cleared=true; clear("STALE_ACCOUNT");
        if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow();
        super.onCleared();
    }
}
