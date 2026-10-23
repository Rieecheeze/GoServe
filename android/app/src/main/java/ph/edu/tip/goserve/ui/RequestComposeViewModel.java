package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.List;
import java.util.ArrayList;
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

public final class RequestComposeViewModel extends AndroidViewModel {
    interface Actions {
        boolean allowed();
        void requireCurrent();
        RequestData.Quote quote(String serviceId,String deliveryMode) throws Exception;
        RequestData.QuoteOptions options(RequestData.Quote quote) throws Exception;
        RequestData.Submission draft(RequestData.Quote quote,RequestData.QuoteOptions options,List<RequestData.Evidence> files,String notes,String method,RequestData.Submission previous) throws Exception;
        RequestData.Record submit(RequestData.Submission draft) throws Exception;
    }
    public enum Mode { EMPTY, LOADING, EDITING, REVIEW, SUBMITTING, COMPLETE, UNAVAILABLE }
    public static final class State {
        public final Mode mode;
        public final String officeId,notes,paymentMethod,error;
        public final RequestData.Quote quote;
        public final RequestData.QuoteOptions options;
        public final List<RequestData.Evidence> evidence;
        public final RequestData.Submission draft;
        public final RequestData.Record result;
        public final boolean uncertain;
        State(Mode mode,String officeId,RequestData.Quote quote,RequestData.QuoteOptions options,List<RequestData.Evidence> evidence,
              String notes,String method,RequestData.Submission draft,RequestData.Record result,String error,boolean uncertain) {
            this.mode=mode; this.officeId=officeId; this.quote=quote; this.options=options; this.evidence=List.copyOf(evidence);
            this.notes=notes; this.paymentMethod=method; this.draft=draft; this.result=result; this.error=error; this.uncertain=uncertain;
        }
        public boolean busy() { return mode==Mode.LOADING || mode==Mode.SUBMITTING; }
    }
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(Mode.EMPTY,null,null,null,List.of(),"",null,null,null,null,false));
    private final Actions actions;
    private final Executor worker,publication;
    private final AtomicLong generation=new AtomicLong();
    private volatile boolean cleared;
    private boolean handled,blocked;
    private String officeId,selectedDelivery;
    private RequestData.Service selectedService;
    public RequestComposeViewModel(Application app) {
        this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post);
    }
    RequestComposeViewModel(Application app,Actions actions,Executor worker,Executor publication) {
        super(app); this.actions=actions; this.worker=worker; this.publication=publication;
        if(!actions.allowed())clear("FORBIDDEN");
    }
    public LiveData<State> state() { return state; }
    public RequestData.Service selectedService() { return blocked?null:selectedService; }
    public String selectedDelivery() { return blocked?null:selectedDelivery; }
    public void ensureOffice(String id) {
        if(cleared || handled || !access())return; handled=true;
        if(id==null || id.trim().isEmpty()) { form("",null,List.of(),"VALIDATION_FAILED"); return; }
        officeId=id; state.setValue(new State(Mode.EMPTY,id,null,null,List.of(),"",null,null,null,null,false));
    }
    public void selectService(RequestData.Service service,String deliveryMode) {
        if(!editable())return;
        if(officeId==null || service==null || !service.active || !officeId.equals(service.officeId) || !service.deliveryModes.contains(deliveryMode)) {
            State held=state.getValue(); form(held.notes,held.paymentMethod,held.evidence,"VALIDATION_FAILED"); return;
        }
        selectedService=service; selectedDelivery=deliveryMode;
        loadQuote(false);
    }
    public void retryQuote() {
        if(!editable() || state.getValue().busy() || selectedService==null)return;
        loadQuote(true);
    }
    private void loadQuote(boolean preserveFiles) {
        State held=state.getValue(); RequestData.Service service=selectedService; String delivery=selectedDelivery;
        long request=generation.incrementAndGet(); List<RequestData.Evidence> files=preserveFiles?held.evidence:List.of();
        state.setValue(new State(Mode.LOADING,officeId,null,null,files,held.notes,null,null,null,null,false));
        worker.execute(()->{
            if(cleared || generation.get()!=request)return;
            RequestData.Quote fetched=null; boolean validated=false;
            try {
                actions.requireCurrent(); fetched=actions.quote(service.id,delivery); actions.requireCurrent();
                if(fetched==null || fetched.service==null || !service.id.equals(fetched.service.id)
                    || !officeId.equals(fetched.service.officeId) || !delivery.equals(fetched.deliveryMode))throw new RequestRepository.WorkflowException("VALIDATION_FAILED");
                validated=true; RequestData.QuoteOptions options=actions.options(fetched); actions.requireCurrent();
                RequestData.Quote quote=fetched;
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    State latest=state.getValue();
                    state.setValue(new State(Mode.EDITING,officeId,quote,options,files,latest.notes,null,null,null,null,false));
                });
            } catch(Exception error) {
                RequestData.Quote quote=validated?fetched:null;
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    String failure=code(error); if("FORBIDDEN".equals(failure)) { clear(failure); return; }
                    state.setValue(new State(Mode.EDITING,officeId,quote,null,files,state.getValue().notes,null,null,null,failure,false));
                });
            }
        });
    }
    public void setNotes(String notes) {
        if(!editable())return; State held=state.getValue();
        form(notes==null?"":notes,held.paymentMethod,held.evidence,null);
    }
    public void setPaymentMethod(String method) {
        if(!editable() || state.getValue().busy())return; State held=state.getValue();
        form(held.notes,method,held.evidence,null);
    }
    public void addEvidence(RequestData.Evidence file) {
        if(!editable() || state.getValue().busy())return; State held=state.getValue();
        if(held.quote==null || file==null || !held.quote.ownerId.equals(file.ownerId) || !held.quote.service.id.equals(file.serviceId)
            || file.requestId!=null || !file.current || !"verified".equals(file.verification)
            || file.requirement!=null && !held.quote.service.requirementIds.contains(file.requirement)) {
            form(held.notes,held.paymentMethod,held.evidence,"UPLOAD_INVALID"); return;
        }
        List<RequestData.Evidence> files=new ArrayList<>(held.evidence);
        files.removeIf(prior->prior.id.equals(file.id) || file.requirement!=null && file.requirement.equals(prior.requirement));
        files.add(file); form(held.notes,held.paymentMethod,files,null);
    }
    public void removeEvidence(String fileId) {
        if(!editable() || state.getValue().busy())return; State held=state.getValue();
        List<RequestData.Evidence> files=new ArrayList<>(held.evidence); files.removeIf(file->file.id.equals(fileId));
        form(held.notes,held.paymentMethod,files,null);
    }
    private void form(String notes,String method,List<RequestData.Evidence> files,String error) {
        State held=state.getValue();
        state.setValue(new State(held.mode==Mode.LOADING?Mode.LOADING:Mode.EDITING,officeId,held.quote,held.options,files,notes,method,held.draft,null,error,false));
    }
    public void review() {
        if(!editable() || state.getValue().busy())return; State held=state.getValue();
        if(held.quote==null || held.options==null) {
            form(held.notes,held.paymentMethod,held.evidence,held.error==null?"QUOTE_REQUIRED":held.error); return;
        }
        try {
            actions.requireCurrent(); RequestData.Submission draft=actions.draft(held.quote,held.options,held.evidence,held.notes,held.paymentMethod,held.draft);
            if(!access())return;
            state.setValue(new State(Mode.REVIEW,officeId,held.quote,held.options,held.evidence,held.notes,held.paymentMethod,draft,null,null,false));
        } catch(Exception error) {
            if(!access())return; String failure=code(error);
            if("FORBIDDEN".equals(failure))clear(failure); else form(held.notes,held.paymentMethod,held.evidence,failure);
        }
    }
    public void edit() {
        if(cleared || !access())return; State held=state.getValue();
        if(held.mode!=Mode.REVIEW || held.uncertain)return;
        form(held.notes,held.paymentMethod,held.evidence,null);
    }
    public void submit() {
        if(cleared || !access())return; State held=state.getValue();
        if(held.mode!=Mode.REVIEW || held.draft==null)return;
        long request=generation.incrementAndGet(); RequestData.Submission draft=held.draft;
        state.setValue(new State(Mode.SUBMITTING,officeId,held.quote,held.options,held.evidence,held.notes,held.paymentMethod,draft,null,null,held.uncertain));
        worker.execute(()->{
            if(cleared || generation.get()!=request)return;
            try {
                actions.requireCurrent(); RequestData.Record result=actions.submit(draft); actions.requireCurrent();
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    state.setValue(new State(Mode.COMPLETE,officeId,null,null,List.of(),"",null,null,result,null,false));
                });
            } catch(Exception error) {
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    String failure=code(error); if("FORBIDDEN".equals(failure)) { clear(failure); return; }
                    boolean definite=List.of("QUOTE_STALE","CONFIGURATION_REQUIRED","UPLOAD_INVALID","PROVIDER_UNAVAILABLE").contains(failure);
                    state.setValue(new State(definite?Mode.EDITING:Mode.REVIEW,officeId,definite?null:held.quote,definite?null:held.options,
                        held.evidence,held.notes,held.paymentMethod,definite?null:draft,null,failure,!definite));
                });
            }
        });
    }
    public void checkCurrentAccount() { if(!cleared)access(); }
    private boolean editable() {
        if(cleared || !access())return false; State held=state.getValue();
        return !held.uncertain && (held.mode==Mode.EMPTY || held.mode==Mode.EDITING || held.mode==Mode.LOADING);
    }
    private boolean access() {
        if(blocked)return false;
        if(!actions.allowed()) { clear("FORBIDDEN"); return false; }
        try { actions.requireCurrent(); return true; }
        catch(IllegalStateException changed) { clear("STALE_ACCOUNT"); return false; }
    }
    private void clear(String code) {
        blocked=true; generation.incrementAndGet(); officeId=null; selectedService=null; selectedDelivery=null;
        state.setValue(new State(Mode.UNAVAILABLE,null,null,null,List.of(),"",null,null,null,code,false));
    }
    private static String code(Exception error) {
        return error instanceof RequestRepository.WorkflowException?((RequestRepository.WorkflowException)error).code:"NETWORK_ERROR";
    }
    private static Actions production(GoServeApplication app) {
        SessionProfile profile=app.auth().profile(); AccountGateway candidate=null;
        if(profile!=null && profile.ready() && profile.role==Role.STUDENT)try {
            AccountGateway current=app.gateway(); if(profile.id.equals(current.lease().accountId))candidate=current;
        } catch(IllegalStateException changed) { candidate=null; }
        AccountGateway gateway=candidate; RequestRepository repository=gateway==null?null:new RequestRepository(gateway);
        return new Actions() {
            public boolean allowed() { return gateway!=null; }
            public void requireCurrent() { if(gateway==null)throw new IllegalStateException("FORBIDDEN"); gateway.requireCurrent(); }
            public RequestData.Quote quote(String service,String delivery) throws Exception { return repository.quote(service,delivery); }
            public RequestData.QuoteOptions options(RequestData.Quote quote) throws Exception { return repository.quoteOptions(quote); }
            public RequestData.Submission draft(RequestData.Quote quote,RequestData.QuoteOptions options,List<RequestData.Evidence> files,String notes,String method,RequestData.Submission previous) throws Exception {
                return repository.draft(quote,options,files,notes,method,previous);
            }
            public RequestData.Record submit(RequestData.Submission draft) throws Exception { return repository.submit(draft); }
        };
    }
    @Override protected void onCleared() {
        cleared=true; clear("STALE_ACCOUNT"); if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow(); super.onCleared();
    }
}
