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
import ph.edu.tip.goserve.data.PaymentRepository;
import ph.edu.tip.goserve.domain.PaymentData;
import ph.edu.tip.goserve.domain.PaymentStatus;
import ph.edu.tip.goserve.domain.Role;
import ph.edu.tip.goserve.domain.SessionProfile;

public final class TellerPaymentViewModel extends AndroidViewModel {
    interface Actions {
        boolean allowed();
        void requireCurrent();
        void invalidate();
        PaymentData.CashReview lookup(String reference) throws Exception;
        PaymentData.CashReview lookupById(String paymentId) throws Exception;
        PaymentData.CashConfirmation draft(PaymentData.CashReview review,PaymentData.CashConfirmation previous) throws Exception;
        PaymentData.CashReview confirm(PaymentData.CashConfirmation input) throws Exception;
    }
    public enum Mode { SEARCH, LOOKING_UP, FOUND, REVIEW, CONFIRMING, CONFIRMED, ERROR, UNAVAILABLE }
    public static final class State {
        public final Mode mode;
        public final String reference,error;
        public final PaymentData.CashReview review;
        public final boolean received;
        State(Mode mode,String reference,PaymentData.CashReview review,boolean received,String error) {
            this.mode=mode; this.reference=reference; this.review=review; this.received=received; this.error=error;
        }
        public boolean busy() { return mode==Mode.LOOKING_UP || mode==Mode.CONFIRMING; }
        public boolean canReview() { return mode==Mode.FOUND && review!=null && review.status==PaymentStatus.PENDING && received; }
        public boolean canConfirm() { return mode==Mode.REVIEW && review!=null && review.status==PaymentStatus.PENDING && received; }
    }
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(Mode.SEARCH,"",null,false,null));
    private final Actions actions;
    private final Executor worker,publication;
    private final AtomicLong generation=new AtomicLong();
    private volatile boolean cleared;
    private boolean refreshPending,blocked,initialPaymentHandled;
    private String paymentTargetId;
    private PaymentData.CashConfirmation input;
    public TellerPaymentViewModel(Application app) {
        this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post);
    }
    TellerPaymentViewModel(Application app,Actions actions,Executor worker,Executor publication) {
        super(app); this.actions=actions; this.worker=worker; this.publication=publication;
        if(!actions.allowed())clear("FORBIDDEN");
    }
    public LiveData<State> state() { return state; }
    public void ensurePayment(String paymentId) {
        if(cleared || initialPaymentHandled || paymentId==null || paymentId.trim().isEmpty() || !access())return;
        initialPaymentHandled=true;
        if(state.getValue().mode!=Mode.SEARCH || !state.getValue().reference.isEmpty())return;
        paymentTargetId=paymentId;
        lookupPaymentId();
    }
    private void lookupPaymentId() {
        String target=paymentTargetId; refreshPending=false;
        long request=generation.incrementAndGet();
        state.setValue(new State(Mode.LOOKING_UP,"",null,false,null));
        dispatch(request,()->actions.lookupById(target),value->{
            paymentTargetId=null; input=null;
            state.setValue(new State(Mode.FOUND,value.reference,value,false,null));
            if(refreshPending) { refreshPending=false; search(); }
        },error->{
            if("FORBIDDEN".equals(code(error))) { clear("FORBIDDEN"); return; }
            refreshPending=false; state.setValue(new State(Mode.ERROR,"",null,false,code(error)));
        });
    }
    public void reference(String value) {
        if(cleared || state.getValue().mode==Mode.CONFIRMING || !access())return;
        String reference=value==null?"":value;
        if(reference.equals(state.getValue().reference))return;
        generation.incrementAndGet(); actions.invalidate(); input=null; refreshPending=false; paymentTargetId=null;
        state.setValue(new State(Mode.SEARCH,reference,null,false,null));
    }
    public void search() {
        if(cleared || state.getValue().busy() || !access())return;
        if(state.getValue().reference.isEmpty() && paymentTargetId!=null) { lookupPaymentId(); return; }
        String reference=state.getValue().reference.trim(); PaymentData.CashConfirmation previous=input; refreshPending=false;
        if(reference.isEmpty()) { state.setValue(new State(Mode.ERROR,reference,null,false,"VALIDATION_FAILED")); return; }
        long request=generation.incrementAndGet(); state.setValue(new State(Mode.LOOKING_UP,reference,null,false,null));
        dispatch(request,()->actions.lookup(reference),value->{
            input=null;
            if(previous!=null && value.status==PaymentStatus.PENDING) {
                try { input=actions.draft(value,previous); }
                catch(Exception rejected) { state.setValue(new State(Mode.ERROR,reference,null,false,code(rejected))); return; }
            }
            state.setValue(new State(Mode.FOUND,value.reference,value,false,null));
            if(refreshPending) { refreshPending=false; search(); }
        },error->{
            if("FORBIDDEN".equals(code(error))) { clear("FORBIDDEN"); return; }
            refreshPending=false; state.setValue(new State(Mode.ERROR,reference,null,false,code(error)));
        });
    }
    public void received(boolean value) {
        if(cleared || !access())return; State previous=state.getValue();
        if(previous.mode!=Mode.FOUND || previous.review==null || previous.review.status!=PaymentStatus.PENDING)return;
        state.setValue(new State(previous.mode,previous.reference,previous.review,value,previous.error));
    }
    public void reviewConfirmation() {
        if(cleared || !access() || !state.getValue().canReview())return;
        State held=state.getValue();
        state.setValue(new State(Mode.REVIEW,held.reference,held.review,true,null));
    }
    public void backToPayment() {
        if(cleared || !access() || state.getValue().mode!=Mode.REVIEW)return;
        State held=state.getValue();
        state.setValue(new State(Mode.FOUND,held.reference,held.review,false,null));
    }
    public void confirm() {
        if(cleared || !access() || !state.getValue().canConfirm())return;
        State held=state.getValue();
        try { if(input==null)input=actions.draft(held.review,null); }
        catch(Exception error) {
            if("FORBIDDEN".equals(code(error))) { clear("FORBIDDEN"); return; }
            input=null; state.setValue(new State(Mode.ERROR,held.reference,null,false,code(error))); return;
        }
        PaymentData.CashConfirmation operation=input; long request=generation.incrementAndGet();
        state.setValue(new State(Mode.CONFIRMING,held.reference,held.review,true,null));
        dispatch(request,()->actions.confirm(operation),value->{
            input=null; state.setValue(new State(Mode.CONFIRMED,held.reference,value,false,null));
        },error->{
            String code=code(error); boolean retry="NETWORK_ERROR".equals(code);
            if("FORBIDDEN".equals(code)) { clear("FORBIDDEN"); return; }
            if(!retry)input=null;
            state.setValue(new State(retry?Mode.REVIEW:Mode.ERROR,held.reference,retry?held.review:null,retry,code));
        });
    }
    public void editReference() {
        if(cleared || state.getValue().mode==Mode.CONFIRMING || !access())return;
        generation.incrementAndGet(); actions.invalidate(); input=null; refreshPending=false; paymentTargetId=null;
        state.setValue(new State(Mode.SEARCH,state.getValue().reference,null,false,null));
    }
    public void refreshAfterChange() {
        if(cleared || !access())return; State current=state.getValue();
        if(current.mode==Mode.LOOKING_UP)refreshPending=true;
        else if(current.mode!=Mode.CONFIRMING && current.review!=null)search();
    }
    public void invalidate() { if(!cleared)clear("STALE_ACCOUNT"); }
    public void checkCurrentAccount() { if(!cleared)access(); }
    private boolean access() {
        if(blocked)return false;
        if(!actions.allowed()) { clear("FORBIDDEN"); return false; }
        try { actions.requireCurrent(); return true; }
        catch(IllegalStateException changed) { clear("STALE_ACCOUNT"); return false; }
    }
    private void clear(String code) {
        blocked=true; generation.incrementAndGet(); actions.invalidate(); input=null; refreshPending=false; paymentTargetId=null;
        state.setValue(new State(Mode.UNAVAILABLE,"",null,false,code));
    }
    private interface Work<T> { T run() throws Exception; }
    private interface Result<T> { void accept(T value); }
    private <T> void dispatch(long request,Work<T> operation,Result<T> success,Result<Exception> failure) {
        worker.execute(()->{
            if(cleared || generation.get()!=request)return;
            try {
                actions.requireCurrent(); T value=operation.run();
                publication.execute(()->{ if(!cleared && generation.get()==request && access())success.accept(value); });
            } catch(Exception error) {
                publication.execute(()->{ if(!cleared && generation.get()==request && access())failure.accept(error); });
            }
        });
    }
    private static String code(Exception error) {
        return error instanceof PaymentRepository.WorkflowException?((PaymentRepository.WorkflowException)error).code:"NETWORK_ERROR";
    }
    private static Actions production(GoServeApplication app) {
        SessionProfile profile=app.auth().profile();
        boolean permitted=profile!=null && profile.ready() && profile.role==Role.TELLER && "OFF-CASH".equals(profile.officeId);
        PaymentRepository repository=permitted?new PaymentRepository(app.gateway()):null;
        return new Actions() {
            public boolean allowed() { return permitted; }
            public void requireCurrent() { if(repository==null)throw new IllegalStateException("FORBIDDEN"); repository.requireCurrent(); }
            public void invalidate() { if(repository!=null)repository.invalidate(); }
            public PaymentData.CashReview lookup(String reference) throws Exception { return repository.lookupCash(reference); }
            public PaymentData.CashReview lookupById(String paymentId) throws Exception { return repository.lookupCashById(paymentId); }
            public PaymentData.CashConfirmation draft(PaymentData.CashReview review,PaymentData.CashConfirmation previous) throws Exception { return repository.confirmation(review,previous); }
            public PaymentData.CashReview confirm(PaymentData.CashConfirmation input) throws Exception { return repository.confirmCash(input); }
        };
    }
    @Override protected void onCleared() {
        cleared=true; clear("STALE_ACCOUNT"); if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow(); super.onCleared();
    }
}
