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
import ph.edu.tip.goserve.data.DeliveryRepository;
import ph.edu.tip.goserve.data.EvidenceInput;
import ph.edu.tip.goserve.data.RequestRepository;
import ph.edu.tip.goserve.domain.DeliveryData;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.RequestStatus;
import ph.edu.tip.goserve.domain.SessionProfile;
import ph.edu.tip.goserve.domain.Role;

public final class PdfUploadViewModel extends AndroidViewModel {
    interface Actions {
        boolean allowed();
        String officeId();
        void requireCurrent();
        RequestData.Record request(String id) throws Exception;
        RequestData.RequestOptions options(RequestData.Record request) throws Exception;
        EvidenceInput.Selection read(EvidenceInput.Source source,RequestData.FileOptions policy) throws Exception;
        DeliveryData.PdfUpload input(RequestData.Record request,RequestData.RequestOptions options,EvidenceInput.Selection file,DeliveryData.PdfUpload previous) throws Exception;
        DeliveryData.PdfUpload prepare(DeliveryData.PdfUpload input) throws Exception;
        DeliveryData.File upload(DeliveryData.PdfUpload input,EvidenceInput.Selection file) throws Exception;
    }
    public enum Mode { EMPTY, LOADING, EDITING, READING, REVIEW, UPLOADING, COMPLETE, BLOCKED, UNAVAILABLE }
    public static final class State {
        public final Mode mode;
        public final RequestData.Record request;
        public final RequestData.RequestOptions options;
        public final DeliveryData.PdfUpload intent;
        public final DeliveryData.File result;
        public final String name,error;
        public final long sizeBytes;
        public final boolean uncertain;
        State(Mode mode,RequestData.Record request,RequestData.RequestOptions options,DeliveryData.PdfUpload intent,DeliveryData.File result,String name,long sizeBytes,String error,boolean uncertain) {
            this.mode=mode; this.request=request; this.options=options; this.intent=intent; this.result=result; this.name=name; this.sizeBytes=sizeBytes; this.error=error; this.uncertain=uncertain;
        }
        public boolean busy() { return mode==Mode.LOADING || mode==Mode.READING || mode==Mode.UPLOADING; }
    }
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(Mode.EMPTY,null,null,null,null,null,0,null,false));
    private final Actions actions;
    private final Executor worker,publication;
    private final AtomicLong generation=new AtomicLong();
    private volatile boolean cleared;
    private boolean handled,blocked;
    private String target;
    private EvidenceInput.Selection selection;
    public PdfUploadViewModel(Application app) {
        this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post);
    }
    PdfUploadViewModel(Application app,Actions actions,Executor worker,Executor publication) {
        super(app); this.actions=actions; this.worker=worker; this.publication=publication;
        if(!actions.allowed() || actions.officeId()==null)clear("FORBIDDEN");
    }
    public LiveData<State> state() { return state; }
    public void ensureRequest(String id) {
        if(cleared || handled || !access())return; handled=true;
        if(id==null || !id.matches("[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}")) {
            state.setValue(new State(Mode.BLOCKED,null,null,null,null,null,0,"VALIDATION_FAILED",false)); return;
        }
        target=id; reload();
    }
    public void reload() {
        if(cleared || !access() || target==null)return; State held=state.getValue();
        if(held.busy() || held.uncertain || held.mode==Mode.COMPLETE || held.mode==Mode.REVIEW)return;
        release(); long request=generation.incrementAndGet(); String requestedId=target;
        state.setValue(new State(Mode.LOADING,null,null,null,null,null,0,null,false));
        if(cleared || generation.get()!=request)return;
        worker.execute(()->{
            if(cleared || generation.get()!=request)return;
            try {
                actions.requireCurrent(); RequestData.Record record=actions.request(requestedId); actions.requireCurrent();
                if(record==null || !requestedId.equals(record.id))throw new RequestRepository.WorkflowException("VALIDATION_FAILED");
                if(!actions.officeId().equals(record.officeId))throw new RequestRepository.WorkflowException("FORBIDDEN");
                RequestData.RequestOptions options=actions.options(record); actions.requireCurrent(); validateOptions(record,options);
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    state.setValue(new State(Mode.EDITING,record,options,null,null,null,0,null,false));
                });
            } catch(Exception error) {
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    String code=code(error); if("FORBIDDEN".equals(code)) { clear(code); return; }
                    state.setValue(new State(definitive(code) || "VALIDATION_FAILED".equals(code)?Mode.BLOCKED:Mode.EDITING,null,null,null,null,null,0,code,false));
                });
            }
        });
    }
    private static void validateOptions(RequestData.Record record,RequestData.RequestOptions options) throws Exception {
        if(!"digital".equals(record.deliveryMode) || record.status!=RequestStatus.PROCESSING && record.status!=RequestStatus.READY_FOR_PICKUP_DOWNLOAD)throw new RequestRepository.WorkflowException("INVALID_STATE");
        if(options==null || !record.id.equals(options.requestId))throw new RequestRepository.WorkflowException("VALIDATION_FAILED");
        if(record.version!=options.requestVersion)throw new RequestRepository.WorkflowException("STALE_VERSION");
        RequestData.ActionOptions action=options.actions.get("deliverable_upload");
        if(action==null)throw new RequestRepository.WorkflowException("CONFIGURATION_REQUIRED");
        if(!(action instanceof RequestData.FileOptions))throw new RequestRepository.WorkflowException("VALIDATION_FAILED");
        RequestData.FileOptions file=(RequestData.FileOptions)action;
        if(!file.available)throw new RequestRepository.WorkflowException(file.blocker==null?"CONFIGURATION_REQUIRED":file.blocker);
        if(file.blocker!=null || file.mimeTypes==null || !file.mimeTypes.equals(java.util.List.of("application/pdf"))
            || file.maxBytes==null || file.maxBytes<1 || file.maxBytes>9007199254740991L)throw new RequestRepository.WorkflowException("VALIDATION_FAILED");
    }
    public void select(EvidenceInput.Source source) {
        if(cleared || !access() || source==null)return; State held=state.getValue();
        if(held.mode!=Mode.EDITING || held.options==null || held.request==null || held.uncertain)return;
        RequestData.FileOptions policy=(RequestData.FileOptions)held.options.actions.get("deliverable_upload");
        release(); long request=generation.incrementAndGet();
        state.setValue(new State(Mode.READING,held.request,held.options,held.intent,null,null,0,null,false));
        if(cleared || generation.get()!=request)return;
        worker.execute(()->{
            if(cleared || generation.get()!=request)return;
            EvidenceInput.Selection file=null;
            try {
                actions.requireCurrent(); file=actions.read(source,policy); actions.requireCurrent();
                if(cleared || generation.get()!=request) { file.close(); return; }
                DeliveryData.PdfUpload intent=actions.input(held.request,held.options,file,held.intent); actions.requireCurrent();
                EvidenceInput.Selection selected=file;
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access()) { selected.close(); return; }
                    selection=selected; state.setValue(new State(Mode.EDITING,held.request,held.options,intent,null,selected.name,selected.bytes().length,null,false));
                });
            } catch(Exception error) {
                if(file!=null)file.close();
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    String code=code(error); if("FORBIDDEN".equals(code)) { clear(code); return; }
                    state.setValue(new State(Mode.EDITING,held.request,held.options,null,null,null,0,error instanceof RequestRepository.WorkflowException?code:"FILE_UNAVAILABLE",false));
                });
            }
        });
    }
    public void review() {
        if(cleared || !access())return; State held=state.getValue();
        if(held.mode==Mode.EDITING && held.intent!=null && selection!=null)
            state.setValue(new State(Mode.REVIEW,held.request,held.options,held.intent,null,held.name,held.sizeBytes,null,false));
    }
    public void edit() {
        if(cleared || !access())return; State held=state.getValue();
        if(held.mode==Mode.REVIEW && !held.uncertain)state.setValue(new State(Mode.EDITING,held.request,held.options,held.intent,null,held.name,held.sizeBytes,null,false));
    }
    public void confirm() {
        if(cleared || !access())return; State held=state.getValue();
        if(held.mode!=Mode.REVIEW || held.intent==null || selection==null)return;
        EvidenceInput.Selection selected=selection; long request=generation.incrementAndGet();
        state.setValue(new State(Mode.UPLOADING,held.request,held.options,held.intent,null,held.name,held.sizeBytes,null,held.uncertain));
        if(cleared || generation.get()!=request)return;
        worker.execute(()->{
            if(cleared || generation.get()!=request)return;
            DeliveryData.PdfUpload prepared=held.intent;
            try {
                actions.requireCurrent(); if(prepared.fileId==null)prepared=actions.prepare(prepared); actions.requireCurrent();
                DeliveryData.File result=actions.upload(prepared,selected); actions.requireCurrent();
                if(result==null)throw new RequestRepository.WorkflowException("VALIDATION_FAILED");
                DeliveryData.PdfUpload receipt=prepared;
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    release(); state.setValue(new State(Mode.COMPLETE,held.request,held.options,receipt,result,held.name,held.sizeBytes,null,false));
                });
            } catch(Exception error) {
                DeliveryData.PdfUpload retry=prepared;
                publication.execute(()->{
                    if(cleared || generation.get()!=request || !access())return;
                    String code=code(error); if("FORBIDDEN".equals(code)) { clear(code); return; }
                    boolean definite=definitive(code); if(definite)release();
                    state.setValue(new State(definite?Mode.BLOCKED:Mode.REVIEW,held.request,definite?null:held.options,definite?null:retry,null,
                        definite?null:held.name,definite?0:held.sizeBytes,code,!definite));
                });
            }
        });
    }
    private static boolean definitive(String code) { return java.util.Arrays.asList("STALE_VERSION","INVALID_STATE","PAYMENT_REQUIRED","POLICY_BLOCKED","CONFIGURATION_REQUIRED","UPLOAD_INVALID","NOT_FOUND").contains(code); }
    private static String code(Exception error) { return error instanceof RequestRepository.WorkflowException?((RequestRepository.WorkflowException)error).code:"NETWORK_ERROR"; }
    public void checkCurrentAccount() { if(!cleared)access(); }
    private boolean access() {
        if(blocked)return false;
        if(!actions.allowed()) { clear("FORBIDDEN"); return false; }
        try { actions.requireCurrent(); return true; } catch(IllegalStateException changed) { clear("STALE_ACCOUNT"); return false; }
    }
    private void release() { if(selection!=null) { selection.close(); selection=null; } }
    private void clear(String code) { blocked=true; generation.incrementAndGet(); release(); target=null; state.setValue(new State(Mode.UNAVAILABLE,null,null,null,null,null,0,code,false)); }
    private static Actions production(GoServeApplication app) {
        SessionProfile profile=app.auth().profile(); AccountGateway candidate=null;
        if(profile!=null && profile.role==Role.STAFF && RequestListViewModel.permitted(profile))try {
            AccountGateway current=app.gateway(); if(profile.id.equals(current.lease().accountId))candidate=current;
        } catch(IllegalStateException changed) { candidate=null; }
        AccountGateway gateway=candidate; String office=profile==null?null:profile.officeId;
        RequestRepository requests=gateway==null?null:new RequestRepository(gateway); DeliveryRepository files=gateway==null?null:new DeliveryRepository(gateway);
        return new Actions() {
            public boolean allowed() { return gateway!=null; } public String officeId() { return office; }
            public void requireCurrent() { if(gateway==null)throw new IllegalStateException("FORBIDDEN"); gateway.requireCurrent(); }
            public RequestData.Record request(String id) throws Exception { return requests.request(id,office); }
            public RequestData.RequestOptions options(RequestData.Record request) throws Exception { return requests.requestOptions(request); }
            public EvidenceInput.Selection read(EvidenceInput.Source source,RequestData.FileOptions policy) throws Exception { return EvidenceInput.read(source,policy,Runtime.getRuntime().maxMemory()/16); }
            public DeliveryData.PdfUpload input(RequestData.Record request,RequestData.RequestOptions options,EvidenceInput.Selection file,DeliveryData.PdfUpload previous) throws Exception { return files.pdfUpload(request,office,options,file.bytes(),previous); }
            public DeliveryData.PdfUpload prepare(DeliveryData.PdfUpload input) throws Exception { return files.preparePdf(input); }
            public DeliveryData.File upload(DeliveryData.PdfUpload input,EvidenceInput.Selection file) throws Exception { return files.uploadPdf(input,file.bytes()); }
        };
    }
    @Override protected void onCleared() { cleared=true; clear("STALE_ACCOUNT"); if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow(); super.onCleared(); }
}
