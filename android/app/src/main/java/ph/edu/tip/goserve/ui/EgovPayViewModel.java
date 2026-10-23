package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.AccountGateway;
import ph.edu.tip.goserve.data.PaymentRepository;
import ph.edu.tip.goserve.data.RequestRepository;
import ph.edu.tip.goserve.domain.PaymentData;
import ph.edu.tip.goserve.domain.PaymentMethod;
import ph.edu.tip.goserve.domain.PaymentStatus;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.RequestStatus;
import ph.edu.tip.goserve.domain.Role;
import ph.edu.tip.goserve.domain.SessionProfile;

public final class EgovPayViewModel extends AndroidViewModel {
    interface Actions {
        boolean allowed();
        void requireCurrent();
        void invalidate();
        PaymentData.Record load(String requestId) throws Exception;
        PaymentData.Checkout draft(PaymentData.Record payment,PaymentData.Checkout previous) throws Exception;
        String start(PaymentData.Checkout input) throws Exception;
        String refresh(PaymentData.Record payment) throws Exception;
    }
    public enum Mode { EMPTY, LOADING, READY, STARTING, ERROR, UNAVAILABLE }
    public static final class State {
        public final Mode mode;
        public final PaymentData.Record payment;
        public final String checkoutUrl,providerStatus,error;
        public final boolean uncertain;
        State(Mode mode,PaymentData.Record payment,String url,String providerStatus,String error,boolean uncertain) {
            this.mode=mode; this.payment=payment; this.checkoutUrl=url; this.providerStatus=providerStatus; this.error=error; this.uncertain=uncertain;
        }
        public boolean busy() { return mode==Mode.LOADING || mode==Mode.STARTING; }
        public boolean paid() { return mode==Mode.READY && error==null && payment!=null && payment.status==PaymentStatus.PAID; }
        public boolean canStart() { return !busy() && mode!=Mode.UNAVAILABLE && payment!=null && payment.status==PaymentStatus.PENDING && checkoutUrl==null; }
        public boolean canLeave() { return mode!=Mode.STARTING && !uncertain; }
        public boolean canReturnToRequest() { return uncertain && !busy() && mode!=Mode.UNAVAILABLE; }
    }
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(Mode.EMPTY,null,null,null,null,false));
    private final Actions actions;
    private final Executor worker,publication;
    private final AtomicLong generation=new AtomicLong();
    private volatile boolean cleared;
    private boolean blocked,handled;
    private String target;
    private PaymentData.Checkout input;
    public EgovPayViewModel(Application app) {
        this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post);
    }
    EgovPayViewModel(Application app,Actions actions,Executor worker,Executor publication) {
        super(app); this.actions=actions; this.worker=worker; this.publication=publication;
        if(!actions.allowed())clear("FORBIDDEN");
    }
    public LiveData<State> state() { return state; }
    public void ensureRequest(String requestId) {
        if(cleared || handled || !access())return; handled=true;
        if(requestId==null || !requestId.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")) {
            state.setValue(new State(Mode.ERROR,null,null,null,"VALIDATION_FAILED",false)); return;
        }
        target=requestId; refresh();
    }
    public void refresh() {
        if(cleared || !access() || target==null || state.getValue().busy())return;
        State held=state.getValue(); long request=generation.incrementAndGet();
        state.setValue(new State(Mode.LOADING,null,null,held.providerStatus,null,held.uncertain));
        dispatch(request,()->{
            String provider=held.providerStatus,failure=null;
            PaymentData.Record before=held.payment;
            if(before==null) { before=actions.load(target); validatePayment(before); }
            if(before.status!=PaymentStatus.PAID) {
                try { provider=actions.refresh(before); }
                catch(Exception error) { actions.requireCurrent(); failure=code(error); if("FORBIDDEN".equals(failure))throw error; }
            }
            PaymentData.Record current=actions.load(target);
            validatePayment(current);
            return new State(failure==null?Mode.READY:Mode.ERROR,current,held.checkoutUrl,provider,failure,held.uncertain);
        },value->{
            boolean resolved=value.payment.status!=PaymentStatus.PENDING;
            if(resolved)input=null;
            state.setValue(new State(value.mode,value.payment,resolved?null:value.checkoutUrl,value.providerStatus,value.error,resolved?false:value.uncertain));
        },error->state.setValue(new State(Mode.ERROR,null,null,held.providerStatus,code(error),held.uncertain)));
    }
    public void start() {
        if(cleared || !access() || !state.getValue().canStart())return;
        State held=state.getValue();
        try { if(input==null)input=actions.draft(held.payment,null); }
        catch(Exception error) { state.setValue(new State(Mode.ERROR,held.payment,null,held.providerStatus,code(error),false)); return; }
        PaymentData.Checkout operation=input; long request=generation.incrementAndGet();
        state.setValue(new State(Mode.STARTING,held.payment,null,held.providerStatus,null,held.uncertain));
        dispatch(request,()->PaymentRepository.validatedCheckoutUrl(actions.start(operation)),url->{
            state.setValue(new State(Mode.READY,held.payment,url,"pending",null,false));
        },error->{
            String code=code(error);
            boolean definite=List.of("PROVIDER_UNAVAILABLE","CONFIGURATION_REQUIRED","INVALID_STATE","NOT_FOUND","ALREADY_PAID","POLICY_BLOCKED").contains(code);
            state.setValue(new State(Mode.ERROR,held.payment,null,held.providerStatus,code,!definite));
        });
    }
    public String browserUrl() {
        if(cleared || !access())return null; State current=state.getValue();
        return current.busy() || current.paid()?null:current.checkoutUrl;
    }
    public void browserUnavailable() {
        if(cleared || !access())return; State held=state.getValue();
        state.setValue(new State(held.mode,held.payment,held.checkoutUrl,held.providerStatus,"BROWSER_UNAVAILABLE",held.uncertain));
    }
    public void checkCurrentAccount() { if(!cleared)access(); }
    private void validatePayment(PaymentData.Record payment) throws PaymentRepository.WorkflowException {
        if(payment==null)throw new PaymentRepository.WorkflowException("NOT_FOUND");
        if(!target.equals(payment.requestId) || payment.method!=PaymentMethod.EGOVPAY || !"PHP".equals(payment.currency))throw new PaymentRepository.WorkflowException("FORBIDDEN");
    }
    private boolean access() {
        if(blocked)return false;
        if(!actions.allowed()) { clear("FORBIDDEN"); return false; }
        try { actions.requireCurrent(); return true; }
        catch(IllegalStateException changed) { clear("STALE_ACCOUNT"); return false; }
    }
    private void clear(String code) {
        blocked=true; generation.incrementAndGet(); actions.invalidate(); input=null; target=null;
        state.setValue(new State(Mode.UNAVAILABLE,null,null,null,code,false));
    }
    private interface Work<T> { T run() throws Exception; }
    private interface Result<T> { void accept(T value); }
    private <T> void dispatch(long request,Work<T> operation,Result<T> success,Result<Exception> failure) {
        AtomicBoolean completed=new AtomicBoolean();
        worker.execute(()->{
            if(cleared || generation.get()!=request || !completed.compareAndSet(false,true))return;
            try {
                actions.requireCurrent(); T value=operation.run(); actions.requireCurrent();
                publication.execute(()->{ if(!cleared && generation.get()==request && access())success.accept(value); });
            } catch(Exception error) {
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    if("FORBIDDEN".equals(code(error)))clear("FORBIDDEN"); else failure.accept(error);
                });
            }
        });
    }
    private static String code(Exception error) {
        if(error instanceof PaymentRepository.WorkflowException)return ((PaymentRepository.WorkflowException)error).code;
        if(error instanceof RequestRepository.WorkflowException)return ((RequestRepository.WorkflowException)error).code;
        return "NETWORK_ERROR";
    }
    private static Actions production(GoServeApplication app) {
        SessionProfile profile=app.auth().profile(); AccountGateway candidate=null;
        if(profile!=null && profile.ready() && profile.role==Role.STUDENT)try {
            AccountGateway current=app.gateway(); if(profile.id.equals(current.lease().accountId))candidate=current;
        } catch(IllegalStateException changed) { candidate=null; }
        AccountGateway gateway=candidate;
        RequestRepository requests=gateway==null?null:new RequestRepository(gateway);
        PaymentRepository payments=gateway==null?null:new PaymentRepository(gateway);
        return new Actions() {
            public boolean allowed() { return gateway!=null; }
            public void requireCurrent() { if(gateway==null)throw new IllegalStateException("FORBIDDEN"); gateway.requireCurrent(); }
            public void invalidate() { if(payments!=null)payments.invalidate(); }
            public PaymentData.Record load(String requestId) throws Exception {
                RequestData.Record request=requests.request(requestId,null);
                PaymentData.Record payment=payments.payment(requestId);
                if(payment!=null && payment.status==PaymentStatus.PENDING && request.status!=RequestStatus.AWAITING_PAYMENT)throw new PaymentRepository.WorkflowException("INVALID_STATE");
                return payment;
            }
            public PaymentData.Checkout draft(PaymentData.Record payment,PaymentData.Checkout previous) throws Exception { return payments.checkout(payment,previous); }
            public String start(PaymentData.Checkout input) throws Exception { return payments.startEgovPay(input); }
            public String refresh(PaymentData.Record payment) throws Exception { return payments.refreshEgovPay(payment); }
        };
    }
    @Override protected void onCleared() {
        cleared=true; clear("STALE_ACCOUNT"); if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow(); super.onCleared();
    }
}
