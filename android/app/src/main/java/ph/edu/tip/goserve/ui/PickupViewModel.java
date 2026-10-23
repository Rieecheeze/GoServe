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
import ph.edu.tip.goserve.domain.*;

public final class PickupViewModel extends AndroidViewModel {
    interface Actions {
        boolean allowed(); String accountId(); String officeId(); void requireCurrent();
        RequestData.Record request(String id) throws Exception;
        PaymentData.Record payment(String id) throws Exception;
        RequestData.RequestOptions options(RequestData.Record request) throws Exception;
        DeliveryData.PickupClaim claim(RequestData.Record request,RequestData.RequestOptions options) throws Exception;
        DeliveryData.PickupReview validate(String code,String office) throws Exception;
        DeliveryData.Handover draft(DeliveryData.PickupReview review,DeliveryData.Handover previous) throws Exception;
        RequestData.Record handover(DeliveryData.Handover command) throws Exception;
    }
    public enum Mode { EMPTY,LOADING,AVAILABLE,ISSUING,CLAIM,INPUT,SCANNING,VALIDATING,REVIEW,HANDING_OVER,COMPLETE,BLOCKED,UNAVAILABLE }
    public static final class State {
        public final Mode mode;
        public final RequestData.Record request;
        public final PaymentData.Record payment;
        public final RequestData.RequestOptions options;
        public final DeliveryData.PickupClaim claim;
        public final DeliveryData.PickupReview review;
        public final DeliveryData.Handover intent;
        public final String code,error;
        public final boolean uncertain;
        State(Mode mode,RequestData.Record request,PaymentData.Record payment,RequestData.RequestOptions options,DeliveryData.PickupClaim claim,
                DeliveryData.PickupReview review,DeliveryData.Handover intent,String code,String error,boolean uncertain) {
            this.mode=mode; this.request=request; this.payment=payment; this.options=options; this.claim=claim; this.review=review; this.intent=intent; this.code=code; this.error=error; this.uncertain=uncertain;
        }
        public boolean busy() { return mode==Mode.LOADING || mode==Mode.ISSUING || mode==Mode.SCANNING || mode==Mode.VALIDATING || mode==Mode.HANDING_OVER; }
    }
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(Mode.EMPTY,null,null,null,null,null,null,"",null,false));
    private final Actions actions;
    private final Executor worker,publication;
    private final String office;
    private final AtomicLong generation=new AtomicLong();
    private volatile boolean cleared;
    private boolean handled,blocked;
    private String target;
    public PickupViewModel(Application app) { this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post); }
    PickupViewModel(Application app,Actions actions,Executor worker,Executor publication) {
        super(app); this.actions=actions; this.worker=worker; this.publication=publication; office=actions.officeId(); if(!actions.allowed())clear("FORBIDDEN");
    }
    public LiveData<State> state() { return state; }
    public boolean student() { return office==null; }
    public void ensureRequest(String id) {
        if(cleared || handled || !access())return; handled=true;
        if(id!=null && !id.matches("[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}") || student() && id==null) { clear("VALIDATION_FAILED"); return; }
        target=id; if(student())refresh(); else state.setValue(empty(Mode.INPUT,null));
    }
    public void refresh() {
        if(cleared || !access() || !handled)return; State held=state.getValue(); if(held.busy() || held.uncertain || held.mode==Mode.COMPLETE)return;
        if(!student()) { state.setValue(new State(Mode.INPUT,null,null,null,null,null,null,held.code,null,false)); return; }
        long load=generation.incrementAndGet(); state.setValue(empty(Mode.LOADING,null));
        worker.execute(()->{
            if(cleared || generation.get()!=load)return;
            try {
                State result=eligibility();
                if(held.claim!=null && held.request!=null && held.request.version==result.request.version && same(held.request,result.request))
                    result=new State(Mode.CLAIM,result.request,result.payment,result.options,held.claim,null,null,"",null,false);
                State canonical=result; publication.execute(()->{ if(current(load))state.setValue(canonical); });
            } catch(Exception error) { failure(load,empty(Mode.BLOCKED,null),error,false); }
        });
    }
    private State eligibility() throws Exception {
        actions.requireCurrent(); RequestData.Record request=actions.request(target); actions.requireCurrent(); physical(request);
        if(!target.equals(request.id) || !actions.accountId().equals(request.ownerId))throw problem("FORBIDDEN");
        PaymentData.Record payment=actions.payment(request.id); actions.requireCurrent(); payment(request,payment);
        RequestData.RequestOptions options=actions.options(request); actions.requireCurrent();
        if(options==null)throw problem("CONFIGURATION_REQUIRED"); if(!request.id.equals(options.requestId))throw problem("FORBIDDEN");
        if(request.version!=options.requestVersion)throw problem("STALE_VERSION");
        RequestData.ActionOptions action=options.actions.get("pickup_claim"); if(action==null)throw problem("CONFIGURATION_REQUIRED");
        if(!action.available)throw problem(action.blocker==null?"CONFIGURATION_REQUIRED":action.blocker);
        if(action.blocker!=null)throw problem("VALIDATION_FAILED");
        return new State(Mode.AVAILABLE,request,payment,options,null,null,null,"",null,false);
    }
    private void payment(RequestData.Record request,PaymentData.Record payment) throws Exception {
        if(payment!=null && (!request.id.equals(payment.requestId) || !actions.accountId().equals(payment.accountId)))throw problem("FORBIDDEN");
        if(request.snapshot.fee==FeeDeclaration.REQUIRED && (payment==null || payment.status!=PaymentStatus.PAID))throw problem("PAYMENT_REQUIRED");
        if(request.snapshot.fee!=FeeDeclaration.REQUIRED && request.snapshot.fee!=FeeDeclaration.FREE)throw problem("POLICY_BLOCKED");
    }
    private static void physical(RequestData.Record request) throws Exception {
        if(request==null || request.snapshot==null || request.version<1)throw problem("VALIDATION_FAILED");
        if(!"physical".equals(request.deliveryMode) || request.status!=RequestStatus.READY_FOR_PICKUP_DOWNLOAD)throw problem("INVALID_STATE");
    }
    public void issue() {
        if(cleared || !access() || !student())return; State held=state.getValue(); if(held.mode!=Mode.AVAILABLE && held.mode!=Mode.CLAIM)return;
        long load=generation.incrementAndGet(); state.setValue(copy(Mode.ISSUING,held,null,false));
        worker.execute(()->{
            if(cleared || generation.get()!=load)return;
            try {
                State fresh=eligibility();
                if(!same(held.request,fresh.request) || held.request.version!=fresh.request.version)throw problem("STALE_VERSION");
                DeliveryData.PickupClaim claim=actions.claim(fresh.request,fresh.options); actions.requireCurrent();
                if(claim==null || !fresh.request.id.equals(claim.requestId) || claim.displayCode==null || !claim.displayCode.matches("[0-9a-f]{64}") || !claim.displayCode.equals(claim.qrPayload))throw problem("VALIDATION_FAILED");
                publication.execute(()->{ if(current(load))state.setValue(new State(Mode.CLAIM,fresh.request,fresh.payment,fresh.options,claim,null,null,"",null,false)); });
            } catch(Exception error) { failure(load,held,error,false); }
        });
    }
    public void editCode(String value) {
        if(cleared || !access() || student())return; State held=state.getValue();
        if(held.mode!=Mode.INPUT)return; state.setValue(new State(Mode.INPUT,null,null,null,null,null,null,value==null?"":value,null,false));
    }
    public boolean beginScan() {
        if(cleared || !access() || student())return false; State held=state.getValue(); if(held.mode!=Mode.INPUT)return false;
        state.setValue(copy(Mode.SCANNING,held,null,false)); return true;
    }
    public void scanResult(String value) {
        if(cleared || !access() || state.getValue().mode!=Mode.SCANNING)return; State held=state.getValue();
        if(value==null || !value.matches("[0-9a-f]{64}"))state.setValue(copy(Mode.INPUT,held,"CODE_INVALID",false));
        else state.setValue(new State(Mode.INPUT,null,null,null,null,null,null,value,null,false));
    }
    public void scanCancelled() { finishScan("SCAN_CANCELLED"); }
    public void scannerUnavailable() { finishScan("SCANNER_UNAVAILABLE"); }
    private void finishScan(String error) { if(!cleared && access() && state.getValue().mode==Mode.SCANNING)state.setValue(copy(Mode.INPUT,state.getValue(),error,false)); }
    public void validateCode() {
        if(cleared || !access() || student())return; State held=state.getValue(); if(held.mode!=Mode.INPUT)return;
        String code=held.code.trim();
        if(!code.matches("[0-9a-f]{64}")) { state.setValue(copy(Mode.INPUT,held,"CODE_INVALID",false)); return; }
        long load=generation.incrementAndGet(); state.setValue(copy(Mode.VALIDATING,held,null,false));
        worker.execute(()->{
            if(cleared || generation.get()!=load)return;
            try {
                actions.requireCurrent(); DeliveryData.PickupReview review=actions.validate(code,office); actions.requireCurrent();
                if(review==null || !actions.accountId().equals(review.actorId) || !office.equals(review.officeId) || review.request==null
                    || !office.equals(review.request.officeId) || target!=null && !target.equals(review.request.id))throw problem("FORBIDDEN");
                physical(review.request); PaymentData.Record paid=actions.payment(review.request.id); actions.requireCurrent(); payment(review.request,paid);
                DeliveryData.Handover intent=actions.draft(review,null); actions.requireCurrent(); if(intent==null)throw problem("VALIDATION_FAILED");
                publication.execute(()->{ if(current(load))state.setValue(new State(Mode.REVIEW,review.request,paid,null,null,review,intent,code,null,false)); });
            } catch(Exception error) { failure(load,held,error,false); }
        });
    }
    public void editReview() {
        if(cleared || !access())return; State held=state.getValue();
        if(held.mode==Mode.REVIEW && !held.uncertain)state.setValue(new State(Mode.INPUT,null,null,null,null,null,null,held.code,null,false));
    }
    public void confirmHandover() {
        if(cleared || !access() || student())return; State held=state.getValue(); if(held.mode!=Mode.REVIEW || held.intent==null)return;
        long load=generation.incrementAndGet(); state.setValue(copy(Mode.HANDING_OVER,held,null,held.uncertain));
        worker.execute(()->{
            if(cleared || generation.get()!=load)return;
            try {
                actions.requireCurrent(); RequestData.Record result=actions.handover(held.intent); actions.requireCurrent();
                if(result==null || !same(held.request,result) || result.status!=RequestStatus.COMPLETED || result.version<=held.request.version)throw problem("STALE_RESPONSE");
                publication.execute(()->{ if(current(load))state.setValue(new State(Mode.COMPLETE,result,held.payment,null,null,null,null,"",null,false)); });
            } catch(Exception error) { failure(load,held,error,true); }
        });
    }
    private void failure(long load,State held,Exception error,boolean afterHandover) {
        publication.execute(()->{
            if(!current(load))return; String code=code(error); if("FORBIDDEN".equals(code)) { clear(code); return; }
            boolean definite=java.util.Set.of("CODE_INVALID","CODE_CONSUMED","CODE_EXPIRED","STALE_VERSION","INVALID_STATE","PAYMENT_REQUIRED","POLICY_BLOCKED","CONFIGURATION_REQUIRED","NOT_FOUND").contains(code);
            if(afterHandover && !definite)state.setValue(copy(Mode.REVIEW,held,code,true));
            else if(student())state.setValue(new State(Mode.BLOCKED,held.request,null,null,null,null,null,"",code,false));
            else state.setValue(new State(Mode.INPUT,null,null,null,null,null,null,held.code,code,false));
        });
    }
    public boolean mutationPending() { State held=state.getValue(); return held.mode==Mode.HANDING_OVER || held.uncertain; }
    public void checkCurrentAccount() { if(!cleared)access(); }
    private boolean current(long load) { return !cleared && generation.get()==load && access(); }
    private boolean access() {
        if(blocked || cleared)return false; if(!actions.allowed()) { clear("FORBIDDEN"); return false; }
        try { actions.requireCurrent(); return true; } catch(IllegalStateException changed) { clear("STALE_ACCOUNT"); return false; }
    }
    private void clear(String error) { blocked=true; generation.incrementAndGet(); target=null; state.setValue(empty(Mode.UNAVAILABLE,error)); }
    private static State empty(Mode mode,String error) { return new State(mode,null,null,null,null,null,null,"",error,false); }
    private static State copy(Mode mode,State held,String error,boolean uncertain) { return new State(mode,held.request,held.payment,held.options,held.claim,held.review,held.intent,held.code,error,uncertain); }
    private static boolean same(RequestData.Record a,RequestData.Record b) { return a!=null && b!=null && a.snapshot!=null && b.snapshot!=null && a.id.equals(b.id) && a.ownerId.equals(b.ownerId) && a.officeId.equals(b.officeId) && a.deliveryMode.equals(b.deliveryMode) && a.snapshot.id.equals(b.snapshot.id); }
    private static String code(Exception error) {
        if(error instanceof RequestRepository.WorkflowException)return ((RequestRepository.WorkflowException)error).code;
        if(error instanceof PaymentRepository.WorkflowException)return ((PaymentRepository.WorkflowException)error).code;
        return "NETWORK_ERROR";
    }
    private static RequestRepository.WorkflowException problem(String code) { return new RequestRepository.WorkflowException(code); }
    private static Actions production(GoServeApplication app) {
        SessionProfile profile=app.auth().profile(); AccountGateway candidate=null;
        if(RequestListViewModel.permitted(profile) && (profile.role==Role.STUDENT || profile.role==Role.STAFF))try {
            AccountGateway gateway=app.gateway(); if(profile.id.equals(gateway.lease().accountId))candidate=gateway;
        } catch(IllegalStateException changed) { candidate=null; }
        AccountGateway gateway=candidate; String office=profile!=null && profile.role==Role.STAFF?profile.officeId:null;
        RequestRepository requests=gateway==null?null:new RequestRepository(gateway); PaymentRepository payments=gateway==null?null:new PaymentRepository(gateway);
        return new Actions() {
            public boolean allowed() { return gateway!=null; } public String officeId() { return office; } public String accountId() { return gateway==null?null:gateway.lease().accountId; }
            public void requireCurrent() { if(gateway==null)throw new IllegalStateException("FORBIDDEN"); gateway.requireCurrent(); }
            public RequestData.Record request(String id) throws Exception { return requests.request(id,office); }
            public PaymentData.Record payment(String id) throws Exception { return payments.payment(id); }
            public RequestData.RequestOptions options(RequestData.Record request) throws Exception { return requests.requestOptions(request); }
            public DeliveryData.PickupClaim claim(RequestData.Record request,RequestData.RequestOptions options) throws Exception { return requests.pickupClaim(request,options); }
            public DeliveryData.PickupReview validate(String code,String office) throws Exception { return requests.pickupValidate(code,office); }
            public DeliveryData.Handover draft(DeliveryData.PickupReview review,DeliveryData.Handover previous) throws Exception { return requests.handoverDraft(review,previous); }
            public RequestData.Record handover(DeliveryData.Handover command) throws Exception { return requests.pickupHandover(command); }
        };
    }
    @Override protected void onCleared() { cleared=true; clear("STALE_ACCOUNT"); if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow(); super.onCleared(); }
}
