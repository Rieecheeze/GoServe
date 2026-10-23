package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.AccountGateway;
import ph.edu.tip.goserve.data.InquiryRepository;
import ph.edu.tip.goserve.domain.InquiryData;
import ph.edu.tip.goserve.domain.Office;
import ph.edu.tip.goserve.domain.Role;
import ph.edu.tip.goserve.domain.SessionProfile;

public final class InquiryViewModel extends AndroidViewModel {
    interface Actions {
        boolean allowed(); String accountId(); String officeId(); boolean student(); void requireCurrent();
        List<Office> offices() throws Exception;
        InquiryData.ThreadPage threads(String time,String id) throws Exception;
        InquiryData.ThreadRecord open(String id) throws Exception;
        InquiryData.MessagePage messages(String id,long after) throws Exception;
        InquiryData.UnreadCounts counts(List<String> ids) throws Exception;
        InquiryData.SendDraft draft(String thread,String office,String body,InquiryData.SendDraft previous) throws Exception;
        InquiryData.SendResult send(InquiryData.SendDraft draft) throws Exception;
        InquiryData.ReadCursor read(InquiryData.MessagePage page,long sequence) throws Exception;
        default long readThrough(String id) throws Exception { return 0; }
    }
    public static final class State {
        public final boolean loading,sending,blocked,uncertain;
        public final long readThrough;
        public final String error,threadId,officeId,body,nextCreatedAt,nextId;
        public final List<InquiryData.ThreadRecord> threads;
        public final List<Office> offices;
        public final InquiryData.ThreadRecord thread;
        public final InquiryData.MessagePage page;
        public final InquiryData.UnreadCounts counts;
        State(InquiryViewModel vm) {
            loading=vm.loading; sending=vm.sending; blocked=vm.blocked; error=vm.error;
            uncertain=vm.uncertain; readThrough=vm.readThrough;
            threadId=vm.threadId; officeId=vm.selectedOffice; body=vm.body;
            nextCreatedAt=vm.nextCreatedAt; nextId=vm.nextId;
            threads=Collections.unmodifiableList(new ArrayList<>(vm.threads));
            offices=Collections.unmodifiableList(new ArrayList<>(vm.offices)); thread=vm.thread; page=vm.page; counts=vm.counts;
        }
        public boolean busy() { return loading || sending; }
    }
    private final Actions actions;
    private final Executor worker,publication;
    private final SavedStateHandle saved;
    private final MutableLiveData<State> state=new MutableLiveData<>();
    private boolean loading,sending,blocked,started,inbox,pending,reading,uncertain;
    private volatile boolean cleared,visible;
    private volatile long generation,visibility;
    private String error,threadId,selectedOffice,body="",nextCreatedAt,nextId;
    private List<InquiryData.ThreadRecord> threads=List.of();
    private List<Office> offices=List.of();
    private InquiryData.ThreadRecord thread;
    private InquiryData.MessagePage page;
    private InquiryData.UnreadCounts counts;
    private InquiryData.SendDraft attempted;
    private final java.util.Set<Long> displayed=new java.util.HashSet<>();
    private long readThrough;

    public InquiryViewModel(Application app) { this(app,new SavedStateHandle()); }
    public InquiryViewModel(Application app,SavedStateHandle saved) {
        this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post,saved);
    }
    InquiryViewModel(Application app,Actions actions,Executor worker,Executor publication,SavedStateHandle saved) {
        super(app); this.actions=actions; this.worker=worker; this.publication=publication; this.saved=saved;
        boolean current=actions.allowed();
        if(current)try { actions.requireCurrent(); } catch(IllegalStateException changed) { current=false; }
        if(current && actions.accountId().equals(saved.get("inquiry.account"))) {
            threadId=saved.get("inquiry.thread"); selectedOffice=saved.get("inquiry.office");
            String restored=saved.get("inquiry.body"); body=restored==null?"":restored;
            String key=saved.get("inquiry.key"),attemptBody=saved.get("inquiry.attemptBody");
            if(key!=null && attemptBody!=null)attempted=new InquiryData.SendDraft(actions.accountId(),saved.get("inquiry.attemptThread"),saved.get("inquiry.attemptOffice"),attemptBody,key);
            uncertain=attempted!=null;
        } else eraseSaved();
        if(!current)clear(actions.allowed()?"STALE_ACCOUNT":"FORBIDDEN"); else emit();
    }
    static boolean permitted(SessionProfile p) {
        return p!=null && p.ready() && (p.role==Role.STUDENT || p.role==Role.STAFF && p.officeId!=null
                && (!p.officeId.startsWith("DEP-") || "DEP-CCS".equals(p.officeId) && "secretary".equals(p.capability)));
    }
    public LiveData<State> state() { return state; }
    public boolean student() { return actions.student(); }
    public String accountId() { return blocked?null:actions.accountId(); }
    public String assignedOffice() { return blocked?null:actions.officeId(); }
    public boolean mutationPending() { return sending || uncertain; }
    public void discardLocalDraft() {
        if(!access() || sending)return;
        body=""; attempted=null; uncertain=false; error=null; persist(); emit();
    }
    public void inbox() { if(started || !access())return; inbox=true; started=true; refresh(); }
    public void conversation(String id,String office) {
        if(started || !access())return;
        if(!actions.student() && id==null) { clear("FORBIDDEN"); return; }
        if(!java.util.Objects.equals(threadId,id) && !(id==null && threadId!=null && saved.contains("inquiry.account"))) {
            threadId=id; selectedOffice=office; body=""; attempted=null;
        } else if(selectedOffice==null)selectedOffice=office;
        started=true; persist(); refresh();
    }
    public void editBody(String value) { if(!access() || sending)return; body=value; error=null; persist(); emit(); }
    public void selectOffice(String id) {
        if(!access() || threadId!=null || sending)return;
        for(Office office:offices)if(office.id.equals(id)) { selectedOffice=id; error=null; persist(); emit(); return; }
    }
    public void refresh() {
        if(cleared || !access() || !started)return;
        if(loading || sending || reading) { pending=true; return; }
        load(null,null,0,false);
    }
    public void loadMore() {
        if(!access() || loading || sending || reading)return;
        if(inbox && nextId!=null)load(nextCreatedAt,nextId,0,true);
        else if(!inbox && page!=null && page.nextSequence!=null && readThrough>=page.nextSequence)load(null,null,page.nextSequence,false);
    }
    private void load(String time,String id,long after,boolean append) {
        loading=true; pending=false; error=null; long request=++generation; String selected=threadId;
        displayed.clear(); ++visibility; emit();
        List<InquiryData.ThreadRecord> held=threads;
        worker.execute(()->{
            if(cleared || request!=generation)return;
            try {
                actions.requireCurrent();
                List<Office> directory=actions.offices();
                InquiryData.ThreadPage result=inbox?actions.threads(time,id):null;
                InquiryData.ThreadRecord opened=!inbox && selected!=null?actions.open(selected):null;
                InquiryData.MessagePage messages=opened==null?null:actions.messages(selected,after);
                long confirmed=opened==null?0:actions.readThrough(selected);
                List<String> ids=new ArrayList<>(); if(result!=null)for(InquiryData.ThreadRecord row:result.items)ids.add(row.id);
                if(opened!=null)ids.add(opened.id);
                InquiryData.UnreadCounts unread=actions.counts(ids);
                publication.execute(()->{
                    if(cleared || request!=generation || !access())return;
                    loading=false; offices=directory; counts=unread;
                    if(result!=null) {
                        LinkedHashMap<String,InquiryData.ThreadRecord> merged=new LinkedHashMap<>();
                        if(append)for(InquiryData.ThreadRecord row:held)merged.put(row.id,row);
                        for(InquiryData.ThreadRecord row:result.items) {
                            InquiryData.ThreadRecord previous=merged.get(row.id);
                            if(previous==null || row.version>=previous.version)merged.put(row.id,row);
                        }
                        threads=new ArrayList<>(merged.values()); nextCreatedAt=result.nextCreatedAt; nextId=result.nextId;
                    } else { thread=opened; page=messages; readThrough=confirmed; }
                    emit(); drain();
                });
            } catch(Exception failure) { publishFailure(request,failure); }
        });
    }
    public void send() {
        if(!access() || inbox || loading || sending || reading)return;
        try {
            if(threadId==null) {
                boolean exists=false; for(Office item:offices)if(item.id.equals(selectedOffice))exists=true;
                if(!actions.student() || !exists)throw new InquiryRepository.WorkflowException("VALIDATION_FAILED");
            }
            attempted=actions.draft(threadId,threadId==null?selectedOffice:null,body,attempted);
        } catch(Exception failure) { error=code(failure); emit(); return; }
        InquiryData.SendDraft command=attempted; persist(); sending=true; error=null; long request=++generation; emit();
        worker.execute(()->{
            if(cleared || request!=generation)return;
            try {
                actions.requireCurrent(); InquiryData.SendResult result=actions.send(command);
                publication.execute(()->{
                    if(cleared || request!=generation || !access())return;
                    sending=false; threadId=result.thread.id; thread=result.thread; selectedOffice=result.thread.officeId;
                    body=""; attempted=null; uncertain=false; page=null; displayed.clear(); persist(); emit(); refresh();
                });
            } catch(Exception failure) { publishFailure(request,failure); }
        });
    }
    public void setVisible(boolean value) { visible=value; ++visibility; displayed.clear(); }
    public void displayed(InquiryData.MessagePage expected,List<Long> sequences) {
        if(!access() || !visible || loading || sending || reading || expected==null || expected!=page)return;
        java.util.Set<Long> allowed=new java.util.HashSet<>(); for(InquiryData.Message item:page.items)allowed.add(item.sequence);
        if(!allowed.containsAll(sequences))return; displayed.addAll(sequences);
        long boundary=readThrough;
        for(InquiryData.Message item:page.items) {
            if(item.sequence<=boundary)continue;
            if(item.sequence!=boundary+1 || !displayed.contains(item.sequence))break;
            boundary=item.sequence;
        }
        if(boundary<=readThrough || boundary==0)return;
        long through=boundary,epoch=visibility,request=generation; reading=true;
        worker.execute(()->{
            if(cleared || request!=generation || epoch!=visibility || !visible) { publication.execute(()->{reading=false; drain();}); return; }
            try {
                actions.requireCurrent(); InquiryData.ReadCursor cursor=actions.read(expected,through);
                InquiryData.UnreadCounts unread=actions.counts(List.of(expected.threadId));
                publication.execute(()->{
                    reading=false; if(cleared || request!=generation || !access())return;
                    readThrough=Math.max(readThrough,cursor.throughSequence); counts=unread; emit(); drain();
                });
            } catch(Exception failure) {
                publication.execute(()->{
                    reading=false; if(cleared || request!=generation || !access())return;
                    error=code(failure); emit(); drain();
                });
            }
        });
    }
    private void publishFailure(long request,Exception failure) {
        publication.execute(()->{
            if(cleared || request!=generation || !access())return;
            boolean writing=sending; loading=false; sending=false; String value=code(failure);
            if(writing)uncertain=!java.util.Set.of("FORBIDDEN","NOT_FOUND","VALIDATION_FAILED","CONFIGURATION_REQUIRED","STALE_VERSION").contains(value);
            if("FORBIDDEN".equals(value) || "NOT_FOUND".equals(value)) { clear(value); return; }
            error=value; emit(); drain();
        });
    }
    private void drain() { if(pending && !cleared && !blocked) { pending=false; refresh(); } }
    public void checkCurrentAccount() { if(!cleared)access(); }
    private boolean access() {
        if(blocked || cleared)return false;
        if(!actions.allowed()) { clear("FORBIDDEN"); return false; }
        try { actions.requireCurrent(); return true; }
        catch(IllegalStateException changed) { clear("STALE_ACCOUNT"); return false; }
    }
    private void clear(String value) {
        blocked=true; ++generation; ++visibility; pending=false; visible=false; loading=false; sending=false; reading=false; uncertain=false;
        threads=List.of(); offices=List.of(); page=null; thread=null; counts=null; body=""; attempted=null;
        threadId=null; selectedOffice=null; nextId=null; nextCreatedAt=null; displayed.clear(); error=value; eraseSaved(); emit();
    }
    private void persist() {
        saved.set("inquiry.account",actions.accountId()); saved.set("inquiry.thread",threadId); saved.set("inquiry.office",selectedOffice); saved.set("inquiry.body",body);
        saved.set("inquiry.key",attempted==null?null:attempted.operationKey); saved.set("inquiry.attemptBody",attempted==null?null:attempted.body);
        saved.set("inquiry.attemptThread",attempted==null?null:attempted.threadId); saved.set("inquiry.attemptOffice",attempted==null?null:attempted.officeId);
    }
    private void eraseSaved() { for(String key:List.copyOf(saved.keys()))if(key.startsWith("inquiry."))saved.remove(key); }
    private void emit() { state.setValue(new State(this)); }
    static String code(Exception e) {
        if(e instanceof InquiryRepository.WorkflowException)return ((InquiryRepository.WorkflowException)e).code;
        if(e instanceof ph.edu.tip.goserve.data.SupabaseClient.ApiException) {
            String code=((ph.edu.tip.goserve.data.SupabaseClient.ApiException)e).code;
            if(java.util.Set.of("FORBIDDEN","NOT_FOUND","VALIDATION_FAILED","CONFIGURATION_REQUIRED","STALE_VERSION").contains(code))return code;
        }
        return "NETWORK_ERROR";
    }
    private static Actions production(GoServeApplication app) {
        SessionProfile profile=app.auth().profile(); AccountGateway candidate=null;
        if(permitted(profile))try { AccountGateway value=app.gateway(); if(profile.id.equals(value.lease().accountId))candidate=value; } catch(IllegalStateException ignored) {}
        AccountGateway gateway=candidate; InquiryRepository repo=gateway==null?null:new InquiryRepository(gateway);
        return new Actions() {
            public boolean allowed() { return repo!=null; }
            public String accountId() { return gateway==null?"":gateway.lease().accountId; }
            public String officeId() { return profile!=null && profile.role==Role.STAFF?profile.officeId:null; }
            public boolean student() { return profile!=null && profile.role==Role.STUDENT; }
            public void requireCurrent() { if(repo==null)throw new IllegalStateException("FORBIDDEN"); repo.requireCurrent(); }
            public List<Office> offices() throws Exception {
                List<Office> result=new ArrayList<>();
                for(Office office:app.offices().load(gateway.lease()))
                    if(!office.id.startsWith("DEP-") || ("DEP-CCS".equals(office.id) && (!student() || "DEP-CCS".equals(profile.departmentId))))result.add(office);
                return result;
            }
            public InquiryData.ThreadPage threads(String time,String id) throws Exception { return repo.threads(officeId(),time,id); }
            public InquiryData.ThreadRecord open(String id) throws Exception {
                InquiryData.ThreadRecord value=repo.openThread(id);
                if(student()?!accountId().equals(value.studentId):!java.util.Objects.equals(officeId(),value.officeId))throw new InquiryRepository.WorkflowException("FORBIDDEN");
                return value;
            }
            public InquiryData.MessagePage messages(String id,long after) throws Exception { return repo.messages(id,after); }
            public InquiryData.UnreadCounts counts(List<String> ids) throws Exception { return repo.unreadCounts(ids); }
            public InquiryData.SendDraft draft(String t,String o,String b,InquiryData.SendDraft previous) throws Exception { return repo.draft(t,o,b,previous); }
            public InquiryData.SendResult send(InquiryData.SendDraft value) throws Exception { return repo.send(value); }
            public InquiryData.ReadCursor read(InquiryData.MessagePage p,long sequence) throws Exception { return repo.readVisible(p,sequence); }
            public long readThrough(String id) throws Exception { return repo.confirmedReadThrough(id); }
        };
    }
    @Override protected void onCleared() { cleared=true; ++generation; ++visibility; visible=false; if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow(); super.onCleared(); }
}
