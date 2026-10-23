package ph.edu.tip.goserve.ui;

import android.app.Application;
import android.net.Uri;
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
import ph.edu.tip.goserve.data.PaymentRepository;
import ph.edu.tip.goserve.data.RequestRepository;
import ph.edu.tip.goserve.data.DocumentSaver;
import ph.edu.tip.goserve.data.PrivateDocument;
import ph.edu.tip.goserve.domain.DeliveryData;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.PaymentData;
import ph.edu.tip.goserve.domain.FeeDeclaration;
import ph.edu.tip.goserve.domain.PaymentStatus;
import ph.edu.tip.goserve.domain.RequestStatus;
import ph.edu.tip.goserve.domain.Role;
import ph.edu.tip.goserve.domain.SessionProfile;

public final class StudentDeliveryViewModel extends AndroidViewModel {
    interface Actions {
        boolean allowed(); String accountId(); void requireCurrent();
        RequestData.Record request(String id) throws Exception;
        PaymentData.Record payment(String id) throws Exception;
        DeliveryData.FilePage files(RequestData.Record request) throws Exception;
        DeliveryData.DownloadTicket begin(RequestData.Record request,DeliveryData.File file) throws Exception;
        PrivateDocument download(DeliveryData.File file,DeliveryData.DownloadTicket ticket) throws Exception;
        DocumentSaver.Saved save(PrivateDocument source,DeliveryData.File file,DeliveryData.DownloadTicket ticket,Uri destination) throws Exception;
        boolean discardDestination(Uri destination);
        DeliveryData.Acknowledgement acknowledge(DeliveryData.File file,DeliveryData.DownloadTicket ticket) throws Exception;
        DeliveryData.Receipt receipt(RequestData.Record request,DeliveryData.Receipt previous) throws Exception;
        RequestData.Record confirm(DeliveryData.Receipt receipt) throws Exception;
    }
    public enum Mode { EMPTY, LOADING, AVAILABLE, TRANSFERRING, DESTINATION_READY, PICKER, SAVING, ACKNOWLEDGING, LOCAL_SAVED, SAVED, RECEIPT_REVIEW, CONFIRMING, COMPLETE, BLOCKED, UNAVAILABLE }
    public static final class State {
        public final Mode mode;
        public final RequestData.Record request;
        public final PaymentData.Record payment;
        public final DeliveryData.File file;
        public final DeliveryData.DownloadTicket ticket;
        public final DeliveryData.Acknowledgement acknowledgement;
        public final DeliveryData.Receipt receipt;
        public final String savedName,error;
        public final boolean localSaved,uncertain,partialMayRemain;
        State(Mode mode,RequestData.Record request,PaymentData.Record payment,DeliveryData.File file,DeliveryData.DownloadTicket ticket,
                DeliveryData.Acknowledgement acknowledgement,DeliveryData.Receipt receipt,String savedName,String error,boolean localSaved,boolean uncertain,boolean partialMayRemain) {
            this.mode=mode; this.request=request; this.payment=payment; this.file=file; this.ticket=ticket; this.acknowledgement=acknowledgement;
            this.receipt=receipt; this.savedName=savedName; this.error=error; this.localSaved=localSaved; this.uncertain=uncertain; this.partialMayRemain=partialMayRemain;
        }
        public boolean busy() { return mode==Mode.LOADING || mode==Mode.TRANSFERRING || mode==Mode.SAVING || mode==Mode.ACKNOWLEDGING || mode==Mode.CONFIRMING; }
    }
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(Mode.EMPTY,null,null,null,null,null,null,null,null,false,false,false));
    private final Actions actions;
    private final Executor worker,publication;
    private final AtomicLong generation=new AtomicLong();
    private volatile boolean cleared;
    private boolean handled,blocked;
    private String target;
    private PrivateDocument document;
    private Mode beforeReceipt=Mode.AVAILABLE;
    public StudentDeliveryViewModel(Application app) { this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post); }
    StudentDeliveryViewModel(Application app,Actions actions,Executor worker,Executor publication) {
        super(app); this.actions=actions; this.worker=worker; this.publication=publication; if(!actions.allowed())clear("FORBIDDEN");
    }
    public LiveData<State> state() { return state; }
    public void ensureRequest(String id) {
        if(cleared || handled || !access())return; handled=true;
        if(id==null || !id.matches("[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}")) { clear("VALIDATION_FAILED"); return; }
        target=id; refresh();
    }
    public void refresh() {
        if(cleared || !access() || target==null)return; State held=state.getValue();
        if(held.busy() || held.mode==Mode.PICKER || held.mode==Mode.RECEIPT_REVIEW || held.mode==Mode.LOCAL_SAVED)return;
        release(); long operation=generation.incrementAndGet(); state.setValue(empty(Mode.LOADING,null));
        worker.execute(()->{
            if(cleared || generation.get()!=operation)return;
            try {
                State result=read(); publication.execute(()->{ if(current(operation))state.setValue(result); });
            } catch(Exception error) { publishFailure(operation,empty(Mode.BLOCKED,null),error,false); }
        });
    }
    private State read() throws Exception {
        actions.requireCurrent(); RequestData.Record request=actions.request(target); actions.requireCurrent();
        if(request==null || request.snapshot==null || request.version<1 || !target.equals(request.id))throw failure("VALIDATION_FAILED");
        if(!actions.accountId().equals(request.ownerId))throw failure("FORBIDDEN");
        if(!"digital".equals(request.deliveryMode))throw failure("INVALID_STATE");
        if(request.status==RequestStatus.COMPLETED)return new State(Mode.COMPLETE,request,null,null,null,null,null,null,null,false,false,false);
        if(request.status!=RequestStatus.READY_FOR_PICKUP_DOWNLOAD)throw failure("INVALID_STATE");
        PaymentData.Record payment=actions.payment(request.id); actions.requireCurrent();
        if(payment!=null && (!request.id.equals(payment.requestId) || !request.ownerId.equals(payment.accountId)))throw failure("FORBIDDEN");
        if(request.snapshot.fee==FeeDeclaration.REQUIRED && (payment==null || payment.status!=PaymentStatus.PAID))throw failure("PAYMENT_REQUIRED");
        if(request.snapshot.fee!=FeeDeclaration.FREE && request.snapshot.fee!=FeeDeclaration.REQUIRED)throw failure("POLICY_BLOCKED");
        DeliveryData.FilePage page=actions.files(request); actions.requireCurrent();
        if(page==null || page.nextId!=null || page.items.size()>1)throw failure("VALIDATION_FAILED");
        if(page.items.isEmpty())throw failure("NOT_FOUND"); DeliveryData.File file=page.items.get(0);
        if(file==null || file.id==null || !request.id.equals(file.requestId) || !request.ownerId.equals(file.ownerId)
            || !request.snapshot.id.equals(file.serviceId) || file.kind!=DeliveryData.FileKind.DELIVERABLE)throw failure("FORBIDDEN");
        if(!file.current || file.deletedAt!=null || !"verified".equals(file.verification) || !"application/pdf".equals(file.mimeType) || file.sizeBytes<1 || file.requirement!=null)throw failure("UPLOAD_INVALID");
        return new State(Mode.AVAILABLE,request,payment,file,null,null,null,null,null,false,false,false);
    }
    private void requireUnchanged(State held) throws Exception {
        State current=read();
        if(current.mode!=Mode.AVAILABLE)throw failure("INVALID_STATE");
        if(!sameRequest(held.request,current.request) || held.request.version!=current.request.version || !held.file.id.equals(current.file.id)
                || held.file.version!=current.file.version || held.file.sizeBytes!=current.file.sizeBytes)throw failure("STALE_VERSION");
    }
    public void prepareDownload() {
        if(cleared || !access())return; State held=state.getValue(); if(held.mode!=Mode.AVAILABLE || held.file==null)return;
        long operation=generation.incrementAndGet(); state.setValue(copy(Mode.TRANSFERRING,held,null,false,false));
        worker.execute(()->{
            if(cleared || generation.get()!=operation)return; PrivateDocument acquired=null;
            try {
                requireUnchanged(held); DeliveryData.DownloadTicket ticket=actions.begin(held.request,held.file); actions.requireCurrent();
                if(ticket==null || ticket.id==null || !held.file.id.equals(ticket.fileId) || ticket.sha256==null || !ticket.sha256.matches("[0-9a-f]{64}"))throw failure("VALIDATION_FAILED");
                acquired=actions.download(held.file,ticket); actions.requireCurrent();
                if(acquired==null || !"application/pdf".equals(acquired.mimeType()))throw failure("VALIDATION_FAILED");
                PrivateDocument completed=acquired;
                publication.execute(()->{
                    if(!current(operation)) { completed.close(); return; }
                    document=completed; state.setValue(new State(Mode.DESTINATION_READY,held.request,held.payment,held.file,ticket,null,null,null,null,false,false,false));
                });
            } catch(Exception error) {
                if(acquired!=null)acquired.close(); publishFailure(operation,held,error,false);
            }
        });
    }
    public String takeSaveName() {
        if(cleared || !access())return null; State held=state.getValue();
        if(held.mode!=Mode.DESTINATION_READY || document==null)return null;
        String reference=held.request.reference==null?"document":held.request.reference.replaceAll("[^A-Za-z0-9_-]","_");
        if(reference.isEmpty())reference="document"; if(reference.length()>80)reference=reference.substring(0,80);
        state.setValue(copy(Mode.PICKER,held,null,false,false)); return "GoServe-"+reference+".pdf";
    }
    public void destinationResult(Uri destination) {
        if(cleared || !access())return; State held=state.getValue(); if(held.mode!=Mode.PICKER || document==null)return;
        if(destination==null) { state.setValue(copy(Mode.DESTINATION_READY,held,"SAVE_CANCELLED",false,false)); return; }
        if(!"content".equals(destination.getScheme()) || destination.getAuthority()==null || destination.getAuthority().isEmpty()) {
            state.setValue(copy(Mode.DESTINATION_READY,held,"DOCUMENT_SAVE_FAILED",false,false)); return;
        }
        PrivateDocument source=document; long operation=generation.incrementAndGet(); state.setValue(copy(Mode.SAVING,held,null,false,false));
        worker.execute(()->{
            boolean started=false;
            try {
                if(cleared || generation.get()!=operation) { actions.discardDestination(destination); return; }
                requireUnchanged(held); started=true; DocumentSaver.Saved saved=actions.save(source,held.file,held.ticket,destination); actions.requireCurrent();
                if(saved==null)throw failure("VALIDATION_FAILED");
                publication.execute(()->{
                    if(!current(operation))return; release();
                    state.setValue(new State(Mode.LOCAL_SAVED,held.request,held.payment,held.file,held.ticket,null,null,saved.displayName,null,true,false,false));
                    retryAcknowledgement();
                });
            } catch(Exception error) {
                boolean partial=error instanceof DocumentSaver.SaveException && ((DocumentSaver.SaveException)error).partialMayRemain;
                if(!(error instanceof DocumentSaver.SaveException))partial=!actions.discardDestination(destination);
                Exception reported=started && !(error instanceof DocumentSaver.SaveException) && !(error instanceof RequestRepository.WorkflowException)?failure("DOCUMENT_SAVE_FAILED"):error;
                publishFailure(operation,held,reported,partial);
            }
        });
    }
    public void pickerUnavailable() {
        if(cleared || !access())return; State held=state.getValue();
        if(held.mode==Mode.PICKER)state.setValue(copy(Mode.DESTINATION_READY,held,"NO_DOCUMENT_PROVIDER",false,false));
    }
    public void retryAcknowledgement() {
        if(cleared || !access())return; State held=state.getValue(); if(held.mode!=Mode.LOCAL_SAVED || !held.localSaved || held.ticket==null)return;
        long operation=generation.incrementAndGet(); state.setValue(copy(Mode.ACKNOWLEDGING,held,null,held.uncertain,false));
        worker.execute(()->{
            if(cleared || generation.get()!=operation)return;
            try {
                actions.requireCurrent(); DeliveryData.Acknowledgement ack=actions.acknowledge(held.file,held.ticket); actions.requireCurrent();
                if(ack==null || !held.ticket.id.equals(ack.downloadId) || ack.confirmedAt==null)throw failure("VALIDATION_FAILED");
                java.time.Instant.parse(ack.confirmedAt);
                publication.execute(()->{ if(current(operation))state.setValue(new State(Mode.SAVED,held.request,held.payment,held.file,held.ticket,ack,null,held.savedName,null,true,false,false)); });
            } catch(Exception error) {
                publication.execute(()->{
                    if(!current(operation))return; String code=code(error); if("FORBIDDEN".equals(code)) { clear(code); return; }
                    state.setValue(copy(definitive(code)?Mode.BLOCKED:Mode.LOCAL_SAVED,held,code,!definitive(code) && !"DOWNLOAD_UNCONFIRMED".equals(code),false));
                });
            }
        });
    }
    public void reviewReceipt() {
        if(cleared || !access())return; State held=state.getValue();
        if(held.mode!=Mode.AVAILABLE && held.mode!=Mode.DESTINATION_READY && held.mode!=Mode.SAVED)return;
        try {
            DeliveryData.Receipt receipt=actions.receipt(held.request,held.receipt); actions.requireCurrent();
            if(receipt==null || !actions.accountId().equals(receipt.actorId) || !sameRequest(held.request,receipt.request) || held.request.version!=receipt.request.version)throw failure("VALIDATION_FAILED");
            beforeReceipt=held.mode; state.setValue(new State(Mode.RECEIPT_REVIEW,held.request,held.payment,held.file,held.ticket,held.acknowledgement,receipt,held.savedName,null,held.localSaved,false,false));
        } catch(Exception error) { if(access())state.setValue(copy(held.mode,held,code(error),false,false)); }
    }
    public void editReceipt() {
        if(cleared || !access())return; State held=state.getValue();
        if(held.mode==Mode.RECEIPT_REVIEW && !held.uncertain)state.setValue(copy(beforeReceipt,held,null,false,false));
    }
    public void confirmReceipt() {
        if(cleared || !access())return; State held=state.getValue(); if(held.mode!=Mode.RECEIPT_REVIEW || held.receipt==null)return;
        long operation=generation.incrementAndGet(); state.setValue(copy(Mode.CONFIRMING,held,null,held.uncertain,false));
        worker.execute(()->{
            if(cleared || generation.get()!=operation)return;
            try {
                actions.requireCurrent(); RequestData.Record result=actions.confirm(held.receipt); actions.requireCurrent();
                if(!sameRequest(held.request,result) || result.status!=RequestStatus.COMPLETED || result.version<=held.request.version)throw failure("STALE_RESPONSE");
                publication.execute(()->{
                    if(!current(operation))return; release(); state.setValue(new State(Mode.COMPLETE,result,held.payment,held.file,held.ticket,held.acknowledgement,null,held.savedName,null,held.localSaved,false,false));
                });
            } catch(Exception error) {
                publication.execute(()->{
                    if(!current(operation))return; String code=code(error); if("FORBIDDEN".equals(code)) { clear(code); return; }
                    boolean definite=definitive(code) || "DOWNLOAD_UNCONFIRMED".equals(code); if(definite)release();
                    state.setValue(copy(definite?Mode.BLOCKED:Mode.RECEIPT_REVIEW,held,code,!definite,false));
                });
            }
        });
    }
    private void publishFailure(long operation,State held,Exception error,boolean partial) {
        publication.execute(()->{
            if(!current(operation))return; String code=code(error); if("FORBIDDEN".equals(code)) { clear(code); return; }
            boolean definite=definitive(code) || "VALIDATION_FAILED".equals(code) || "DOWNLOAD_UNCONFIRMED".equals(code); if(definite)release();
            Mode mode=definite?Mode.BLOCKED:document==null?Mode.BLOCKED:Mode.DESTINATION_READY;
            state.setValue(copy(mode,held,code,false,partial));
        });
    }
    public void checkCurrentAccount() { if(!cleared)access(); }
    private boolean current(long operation) { return !cleared && generation.get()==operation && access(); }
    private boolean access() {
        if(blocked)return false; if(!actions.allowed()) { clear("FORBIDDEN"); return false; }
        try { actions.requireCurrent(); return true; } catch(IllegalStateException changed) { clear("STALE_ACCOUNT"); return false; }
    }
    private void release() { if(document!=null) { document.close(); document=null; } }
    private void clear(String code) { blocked=true; generation.incrementAndGet(); release(); target=null; state.setValue(empty(Mode.UNAVAILABLE,code)); }
    private static State empty(Mode mode,String code) { return new State(mode,null,null,null,null,null,null,null,code,false,false,false); }
    private static State copy(Mode mode,State held,String code,boolean uncertain,boolean partial) {
        return new State(mode,held.request,held.payment,held.file,held.ticket,held.acknowledgement,held.receipt,held.savedName,code,held.localSaved,uncertain,partial);
    }
    private static boolean sameRequest(RequestData.Record a,RequestData.Record b) {
        return a!=null && b!=null && a.snapshot!=null && b.snapshot!=null && a.id.equals(b.id) && a.ownerId.equals(b.ownerId)
            && a.officeId.equals(b.officeId) && a.deliveryMode.equals(b.deliveryMode) && a.snapshot.id.equals(b.snapshot.id);
    }
    private static boolean definitive(String code) { return java.util.Arrays.asList("STALE_VERSION","INVALID_STATE","PAYMENT_REQUIRED","POLICY_BLOCKED","CONFIGURATION_REQUIRED","UPLOAD_INVALID","NOT_FOUND").contains(code); }
    private static String code(Exception error) {
        if(error instanceof DocumentSaver.SaveException)return ((DocumentSaver.SaveException)error).code;
        if(error instanceof RequestRepository.WorkflowException)return ((RequestRepository.WorkflowException)error).code;
        if(error instanceof PaymentRepository.WorkflowException)return ((PaymentRepository.WorkflowException)error).code;
        return "NETWORK_ERROR";
    }
    private static RequestRepository.WorkflowException failure(String code) { return new RequestRepository.WorkflowException(code); }
    private static Actions production(GoServeApplication app) {
        SessionProfile profile=app.auth().profile(); AccountGateway candidate=null;
        if(profile!=null && profile.role==Role.STUDENT && RequestListViewModel.permitted(profile))try {
            AccountGateway current=app.gateway(); if(profile.id.equals(current.lease().accountId))candidate=current;
        } catch(IllegalStateException changed) { candidate=null; }
        AccountGateway gateway=candidate; RequestRepository requests=gateway==null?null:new RequestRepository(gateway);
        PaymentRepository payments=gateway==null?null:new PaymentRepository(gateway); DeliveryRepository files=gateway==null?null:new DeliveryRepository(gateway);
        DocumentSaver saver=gateway==null?null:new DocumentSaver(app,gateway);
        return new Actions() {
            public boolean allowed() { return gateway!=null; } public String accountId() { return gateway==null?null:gateway.lease().accountId; }
            public void requireCurrent() { if(gateway==null)throw new IllegalStateException("FORBIDDEN"); gateway.requireCurrent(); }
            public RequestData.Record request(String id) throws Exception { return requests.request(id,null); }
            public PaymentData.Record payment(String id) throws Exception { return payments.payment(id); }
            public DeliveryData.FilePage files(RequestData.Record request) throws Exception { return files.files(request,null,DeliveryData.FileKind.DELIVERABLE,null); }
            public DeliveryData.DownloadTicket begin(RequestData.Record request,DeliveryData.File file) throws Exception { return files.beginDownload(request,file); }
            public PrivateDocument download(DeliveryData.File file,DeliveryData.DownloadTicket ticket) throws Exception { return files.deliverableDocument(app,file,ticket); }
            public DocumentSaver.Saved save(PrivateDocument source,DeliveryData.File file,DeliveryData.DownloadTicket ticket,Uri destination) throws Exception { return saver.save(source,file,ticket,destination); }
            public boolean discardDestination(Uri uri) { try { return android.provider.DocumentsContract.deleteDocument(app.getContentResolver(),uri); } catch(Exception ignored) { return false; } }
            public DeliveryData.Acknowledgement acknowledge(DeliveryData.File file,DeliveryData.DownloadTicket ticket) throws Exception { return files.acknowledge(file,ticket); }
            public DeliveryData.Receipt receipt(RequestData.Record request,DeliveryData.Receipt previous) throws Exception { return requests.receiptDraft(request,previous); }
            public RequestData.Record confirm(DeliveryData.Receipt receipt) throws Exception { return requests.confirmReceipt(receipt); }
        };
    }
    @Override protected void onCleared() { cleared=true; clear("STALE_ACCOUNT"); if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow(); super.onCleared(); }
}
