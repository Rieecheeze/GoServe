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
import ph.edu.tip.goserve.data.EvidenceInput;
import ph.edu.tip.goserve.data.RequestRepository;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.Role;
import ph.edu.tip.goserve.domain.SessionProfile;

public final class RequestEvidenceViewModel extends AndroidViewModel {
    interface Actions {
        boolean allowed();
        String accountId();
        void requireCurrent();
        EvidenceInput.Selection read(EvidenceInput.Source source,RequestData.FileOptions policy) throws Exception;
        RequestData.PreparedUpload input(RequestData.Quote quote,RequestData.QuoteOptions options,String requirement,EvidenceInput.Selection file,RequestData.PreparedUpload previous) throws Exception;
        RequestData.PreparedUpload prepare(RequestData.PreparedUpload input) throws Exception;
        RequestData.Evidence upload(RequestData.PreparedUpload input,EvidenceInput.Selection file) throws Exception;
    }
    public enum Mode { EMPTY, READING, READY, UPLOADING, COMPLETE, ERROR, UNAVAILABLE }
    public static final class State {
        public final Mode mode;
        public final String name,mimeType,error;
        public final long sizeBytes;
        public final RequestData.PreparedUpload prepared;
        public final RequestData.Evidence result;
        State(Mode mode,String name,String mime,long size,RequestData.PreparedUpload prepared,RequestData.Evidence result,String error) {
            this.mode=mode; this.name=name; this.mimeType=mime; this.sizeBytes=size; this.prepared=prepared; this.result=result; this.error=error;
        }
        public boolean busy() { return mode==Mode.READING || mode==Mode.UPLOADING; }
    }
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(Mode.EMPTY,null,null,0,null,null,null));
    private final Actions actions;
    private final Executor worker,publication;
    private final AtomicLong generation=new AtomicLong();
    private volatile boolean cleared;
    private boolean handled,blocked;
    private RequestData.Quote quote;
    private RequestData.QuoteOptions options;
    private String requirement;
    private EvidenceInput.Selection selection;
    public RequestEvidenceViewModel(Application app) {
        this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post);
    }
    RequestEvidenceViewModel(Application app,Actions actions,Executor worker,Executor publication) {
        super(app); this.actions=actions; this.worker=worker; this.publication=publication;
        if(!actions.allowed())clear("FORBIDDEN");
    }
    public LiveData<State> state() { return state; }
    public void ensureContext(RequestData.Quote target,RequestData.QuoteOptions policy,String required) {
        if(cleared || handled || !access())return; handled=true;
        if(target==null || target.service==null || !actions.accountId().equals(target.ownerId)) { clear("FORBIDDEN"); return; }
        if(policy==null || !target.id.equals(policy.quoteId) || target.service.version!=policy.serviceVersion
            || policy.evidence==null || !policy.evidence.available) { clear("CONFIGURATION_REQUIRED"); return; }
        if(required==null?!target.service.requirementIds.isEmpty():!target.service.requirementIds.contains(required)) { clear("VALIDATION_FAILED"); return; }
        quote=target; options=policy; requirement=required;
    }
    public void select(EvidenceInput.Source source) {
        if(cleared || !access() || quote==null || source==null || state.getValue().mode==Mode.UPLOADING || state.getValue().mode==Mode.COMPLETE)return;
        State held=state.getValue(); RequestData.PreparedUpload previous=held.prepared;
        RequestData.Quote target=quote; RequestData.QuoteOptions policy=options; String required=requirement;
        long request=generation.incrementAndGet(); releaseSelection();
        state.setValue(new State(Mode.READING,null,null,0,previous,null,null));
        worker.execute(()->{
            if(cleared || generation.get()!=request)return;
            EvidenceInput.Selection file=null;
            try {
                actions.requireCurrent(); file=actions.read(source,policy.evidence); actions.requireCurrent();
                if(cleared || generation.get()!=request) { file.close(); return; }
                RequestData.PreparedUpload input=actions.input(target,policy,required,file,previous); actions.requireCurrent();
                EvidenceInput.Selection selected=file;
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access()) { selected.close(); return; }
                    selection=selected;
                    state.setValue(new State(Mode.READY,selected.name,selected.mimeType,selected.bytes().length,input,null,null));
                });
            } catch(Exception error) {
                if(file!=null)file.close();
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    String failure=error instanceof RequestRepository.WorkflowException?code(error):"FILE_UNAVAILABLE";
                    if("FORBIDDEN".equals(failure)) { clear(failure); return; }
                    state.setValue(new State(Mode.ERROR,null,null,0,previous,null,failure));
                });
            }
        });
    }
    public void upload() {
        if(cleared || !access() || selection==null)return; State held=state.getValue();
        if(held.prepared==null || held.mode!=Mode.READY && held.mode!=Mode.ERROR)return;
        long request=generation.incrementAndGet(); EvidenceInput.Selection file=selection;
        state.setValue(new State(Mode.UPLOADING,held.name,held.mimeType,held.sizeBytes,held.prepared,null,null));
        if(held.prepared.fileId!=null)queueUpload(request,held.prepared,file); else queuePrepare(request,held.prepared,file);
    }
    private void queuePrepare(long request,RequestData.PreparedUpload input,EvidenceInput.Selection file) {
        worker.execute(()->{
            if(cleared || generation.get()!=request)return;
            try {
                actions.requireCurrent(); RequestData.PreparedUpload prepared=actions.prepare(input); actions.requireCurrent();
                if(prepared==null || prepared.fileId==null)throw new RequestRepository.WorkflowException("UPLOAD_INVALID");
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    State held=state.getValue();
                    state.setValue(new State(Mode.UPLOADING,held.name,held.mimeType,held.sizeBytes,prepared,null,null));
                    queueUpload(request,prepared,file);
                });
            } catch(Exception error) { publishFailure(request,error); }
        });
    }
    private void queueUpload(long request,RequestData.PreparedUpload input,EvidenceInput.Selection file) {
        worker.execute(()->{
            if(cleared || generation.get()!=request)return;
            try {
                actions.requireCurrent(); RequestData.Evidence result=actions.upload(input,file); actions.requireCurrent();
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    State held=state.getValue(); releaseSelection();
                    state.setValue(new State(Mode.COMPLETE,held.name,held.mimeType,held.sizeBytes,null,result,null));
                });
            } catch(Exception error) { publishFailure(request,error); }
        });
    }
    private void publishFailure(long request,Exception error) {
        publication.execute(()->{
            if(cleared || generation.get()!=request || !access())return;
            String failure=code(error); if("FORBIDDEN".equals(failure)) { clear(failure); return; }
            State held=state.getValue(); state.setValue(new State(Mode.ERROR,held.name,held.mimeType,held.sizeBytes,held.prepared,null,failure));
        });
    }
    public void retry() { if(!cleared && state.getValue().mode==Mode.ERROR)upload(); }
    public void checkCurrentAccount() { if(!cleared)access(); }
    private boolean access() {
        if(blocked)return false;
        if(!actions.allowed()) { clear("FORBIDDEN"); return false; }
        try { actions.requireCurrent(); return true; }
        catch(IllegalStateException changed) { clear("STALE_ACCOUNT"); return false; }
    }
    private void releaseSelection() { if(selection!=null)selection.close(); selection=null; }
    private void clear(String code) {
        blocked=true; generation.incrementAndGet(); releaseSelection(); quote=null; options=null; requirement=null;
        state.setValue(new State(Mode.UNAVAILABLE,null,null,0,null,null,code));
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
            public String accountId() { return gateway==null?null:gateway.lease().accountId; }
            public void requireCurrent() { if(gateway==null)throw new IllegalStateException("FORBIDDEN"); gateway.requireCurrent(); }
            public EvidenceInput.Selection read(EvidenceInput.Source source,RequestData.FileOptions policy) throws Exception {
                return EvidenceInput.read(source,policy,Math.max(1,Runtime.getRuntime().maxMemory()/16));
            }
            public RequestData.PreparedUpload input(RequestData.Quote quote,RequestData.QuoteOptions options,String required,EvidenceInput.Selection file,RequestData.PreparedUpload previous) throws Exception {
                return repository.evidenceUpload(quote,options,required,file.mimeType,file.bytes(),previous);
            }
            public RequestData.PreparedUpload prepare(RequestData.PreparedUpload input) throws Exception { return repository.prepareEvidence(input); }
            public RequestData.Evidence upload(RequestData.PreparedUpload input,EvidenceInput.Selection file) throws Exception { return repository.uploadEvidence(input,file.bytes()); }
        };
    }
    @Override protected void onCleared() {
        cleared=true; clear("STALE_ACCOUNT"); if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow(); super.onCleared();
    }
}
