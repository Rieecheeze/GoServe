package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import ph.edu.tip.goserve.data.SeryoRepository;
import ph.edu.tip.goserve.domain.SeryoDraft;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.net.Uri;
import java.util.function.IntConsumer;
import androidx.lifecycle.Observer;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.SeryoAttachmentSource;
import ph.edu.tip.goserve.data.SupabaseClient;

public final class SeryoViewModel extends AndroidViewModel {
    public static final class Message {
        public final boolean user;
        public final String text;
        Message(boolean user,String text){this.user=user;this.text=text;}
    }
    public static final class State {
        public final boolean busy;
        public final SeryoRepository.Readiness readiness;
        public final String error;
        public final SeryoDraft draft;
        public final long decisionSequence;
        public final boolean consentPending;
        public final java.util.List<Message> messages;
        public final String sendingQuestion;
        State(boolean busy,SeryoRepository.Readiness readiness,String error,SeryoDraft draft,long decisionSequence,boolean consentPending,java.util.List<Message> messages,String sendingQuestion) {
            this.busy=busy;this.readiness=readiness;this.error=error;this.draft=draft;this.decisionSequence=decisionSequence;this.consentPending=consentPending;
            this.messages=java.util.Collections.unmodifiableList(new java.util.ArrayList<>(messages));this.sendingQuestion=sendingQuestion;
        }
    }
    private static final class ConsentIntent {
        final String decision,key;
        final long expected;
        ConsentIntent(String decision,long expected) {this.decision=decision;this.expected=expected;key=java.util.UUID.randomUUID().toString();}
    }
    private final SeryoDraft draft=new SeryoDraft();
    private final java.util.List<Message> messages=new java.util.ArrayList<>();
    private String sendingQuestion;
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(false,null,null,draft,0,false,java.util.Collections.emptyList(),null));
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService executor;
    private final GoServeApplication app;
    private final Observer<Long> invalidations=ignored -> verifyAccount();
    private SeryoRepository repository;
    private boolean started,preview,pickerLaunched;
    private volatile boolean cleared;
    private volatile int generation,selectionGeneration;
    private int picker=-1;
    private long decisionSequence;
    private ConsentIntent consentIntent;
    public SeryoViewModel(Application app) {
        super(app);this.app=(GoServeApplication)app;executor=Executors.newSingleThreadExecutor();
        this.app.accountInvalidations().observeForever(invalidations);
    }
    SeryoViewModel(Application app,SeryoRepository repository,ExecutorService executor) {
        super(app);this.app=(GoServeApplication)app;this.repository=repository;this.executor=executor;
    }
    public LiveData<State> state() { return state; }
    public State snapshot() { verifyAccount();return state.getValue(); }
    public void start(boolean preview) {
        if(started) {verifyAccount();return;}started=true;this.preview=preview;
        if(preview) {publish(false,null,null);return;}
        try {if(repository==null)repository=new SeryoRepository(app.gateway());refresh();}
        catch(IllegalStateException changed) {publish(false,null,"ACCOUNT_CHANGED");}
    }
    public void refresh() {
        if(preview || repository==null || cleared || state.getValue().busy || !current())return;
        int request=++generation;publish(true,null,null);
        executor.execute(() -> {
            try {SeryoRepository.Readiness ready=repository.readiness();main.post(() -> {if(accept(request))publish(false,ready,consentIntent==null?null:"CONSENT_UNCERTAIN");});}
            catch(Exception failure) {postFailure(request,failure);}
        });
    }
    public void decide(String decision) {
        State before=state.getValue();
        if(preview || repository==null || cleared || before.busy || !current())return;
        if(!java.util.Arrays.asList("allow","decline","revoke").contains(decision))throw new IllegalArgumentException("Choose a consent decision.");
        if(consentIntent!=null) {if(consentIntent.decision.equals(decision))retryConsent();return;}
        if(before.readiness==null)return;
        consentIntent=new ConsentIntent(decision,before.readiness.version);performConsent();
    }
    public void retryConsent() {
        if(preview || repository==null || cleared || state.getValue().busy || consentIntent==null || !current())return;
        performConsent();
    }
    private void performConsent() {
        ConsentIntent command=consentIntent;
        int request=++generation;++selectionGeneration;picker=-1;pickerLaunched=false;publish(true,state.getValue().readiness,null);
        executor.execute(() -> {
            try {
                repository.consent(command.decision,command.expected,command.key);
                SeryoRepository.Readiness canonical=repository.readiness();
                main.post(() -> {if(accept(request)) {consentIntent=null;++decisionSequence;publish(false,canonical,null);}});
            } catch(Exception failure) {
                if(failure instanceof SupabaseClient.ApiException && java.util.Arrays.asList("STALE_VERSION","IDEMPOTENCY_CONFLICT").contains(((SupabaseClient.ApiException)failure).code)) {
                    try {SeryoRepository.Readiness canonical=repository.readiness();main.post(() -> {if(accept(request)) {consentIntent=null;publish(false,canonical,"CONSENT_CHANGED");}});}
                    catch(Exception refreshFailure) {main.post(() -> {if(accept(request)) {consentIntent=null;publish(false,null,errorCode(refreshFailure));}});}
                } else main.post(() -> {if(accept(request))publish(false,null,"CONSENT_UNCERTAIN");});
            }
        });
    }
    public void edit(String text) {
        if(cleared || !current() || state.getValue().busy)return;draft.text=text==null?"":text;publish(false,state.getValue().readiness,state.getValue().error);
    }
    public void verifyAccount() {if(!preview && repository!=null)current();}
    private boolean current() {
        if(preview)return true;
        if(repository==null)return false;
        try {repository.requireCurrent();return !cleared;}
        catch(IllegalStateException changed) {
            ++generation;++selectionGeneration;picker=-1;pickerLaunched=false;consentIntent=null;draft.clear();messages.clear();sendingQuestion=null;repository=null;publish(false,null,"ACCOUNT_CHANGED");return false;
        }
    }
    private boolean accept(int request) {return !cleared && request==generation && current();}
    private void publish(boolean busy,SeryoRepository.Readiness ready,String error) {state.setValue(new State(busy,ready,error,draft,decisionSequence,consentIntent!=null,messages,sendingQuestion));}
    private void postFailure(int request,Exception failure) {main.post(() -> {if(accept(request))publish(false,null,errorCode(failure));});}
    private String errorCode(Exception failure) {
        if(failure instanceof SupabaseClient.ApiException)return ((SupabaseClient.ApiException)failure).code;
        if(failure instanceof java.net.SocketTimeoutException)return "TIMEOUT";
        if(failure instanceof java.io.IOException)return "CONNECTION";
        if(failure instanceof IllegalStateException)return "ACCOUNT_CHANGED";
        return "INVALID_RESPONSE";
    }
    public void requestPicker(IntConsumer launch) {
        State before=state.getValue();
        if(before.busy || consentIntent!=null || picker!=-1 || before.readiness==null || !before.readiness.allowsAttachment() || !current())return;
        int selection=++selectionGeneration;picker=selection;pickerLaunched=false;
        int request=++generation;publish(true,null,null);
        executor.execute(() -> {
            try {
                SeryoRepository.Readiness ready=repository.readiness();
                main.post(() -> {
                    if(!accept(request) || selection!=selectionGeneration || picker!=selection)return;publish(false,ready,null);
                    if(ready.allowsAttachment()) {
                        pickerLaunched=true;
                        try {launch.accept(selection);}
                        catch(android.content.ActivityNotFoundException | IllegalStateException | SecurityException unavailable) {
                            finishPicker(null,false);publish(false,ready,"PICKER_UNAVAILABLE");
                        }
                    } else {picker=-1;pickerLaunched=false;}
                });
            } catch(Exception failure) {main.post(() -> {if(accept(request) && selection==selectionGeneration) {picker=-1;pickerLaunched=false;publish(false,null,errorCode(failure));}});}
        });
    }
    public void cancelUnlaunchedPicker() {
        if(cleared || picker<0 || pickerLaunched || !current())return;
        ++generation;++selectionGeneration;picker=-1;publish(false,null,null);
    }
    public void finishPicker(Intent result,boolean selected) {
        int selection=picker;picker=-1;pickerLaunched=false;
        if(selection<0 || selection!=selectionGeneration || !current())return;
        if(!selected || result==null)return;
        if(result.getClipData()!=null && result.getClipData().getItemCount()!=1) {publish(false,state.getValue().readiness,"ONE_FILE");return;}
        Uri uri=result.getData();
        if(uri==null && result.getClipData()!=null)uri=result.getClipData().getItemAt(0).getUri();
        if(uri==null) {publish(false,state.getValue().readiness,"FILE_UNREADABLE");return;}
        Uri candidateUri=uri;int request=++generation;publish(true,null,null);
        executor.execute(() -> {
            SeryoDraft.Attachment candidate=null;
            try {
                SeryoRepository.Readiness ready=repository.readiness();
                if(!ready.allowsAttachment()) {main.post(() -> {if(accept(request))publish(false,ready,"FILE_UNAVAILABLE");});return;}
                candidate=new SeryoAttachmentSource().read(app.getContentResolver(),candidateUri,() -> {
                    repository.requireCurrent();if(selection!=selectionGeneration || Thread.currentThread().isInterrupted())throw new IllegalStateException("Selection changed.");
                });
                SeryoRepository.Readiness canonical=repository.readiness();
                SeryoDraft.Attachment validated=candidate;candidate=null;
                main.post(() -> {
                    if(!accept(request) || selection!=selectionGeneration) {validated.close();return;}
                    if(!canonical.allowsAttachment()) {validated.close();publish(false,canonical,"FILE_UNAVAILABLE");return;}
                    draft.replace(validated);publish(false,canonical,null);
                });
            } catch(Exception failure) {
                main.post(() -> {if(accept(request) && selection==selectionGeneration)publish(false,null,failure instanceof SupabaseClient.ApiException?errorCode(failure):"FILE_UNREADABLE");});
            } finally {if(candidate!=null)candidate.close();}
        });
    }
    public void removeAttachment() {
        if(!current() || state.getValue().busy && consentIntent!=null)return;
        ++selectionGeneration;picker=-1;pickerLaunched=false;++generation;draft.remove();publish(false,state.getValue().readiness,consentIntent==null?null:"CONSENT_UNCERTAIN");
    }
    public void prepare(SeryoDraft.Operation operation) {
        if(!current() || state.getValue().busy || draft.attachment==null)return;
        draft.prepare(operation);publish(false,state.getValue().readiness,null);
    }
    public void checkSend() {
        State before=state.getValue();
        if(preview || cleared || before.busy || consentIntent!=null || !current())return;
        if(before.readiness==null || !before.readiness.canSend || !before.readiness.providerReady || !"allow".equals(before.readiness.consent) || !"clear".equals(before.readiness.cleanup))return;
        String question=draft.text.trim();if(question.isEmpty() || question.length()>2000 || draft.attachment!=null)return;
        String key=java.util.UUID.randomUUID().toString();sendingQuestion=question;
        int request=++generation;publish(true,before.readiness,null);
        executor.execute(() -> {
            try {
                String reply=repository.send(question,key);
                SeryoRepository.Readiness ready=repository.readiness();
                main.post(() -> {
                    if(!accept(request))return;sendingQuestion=null;
                    if(!"allow".equals(ready.consent) || ready.version!=before.readiness.version){publish(false,ready,"CONSENT_CHANGED");return;}
                    messages.add(new Message(true,question));messages.add(new Message(false,reply));
                    while(messages.size()>20)messages.remove(0);
                    draft.text="";publish(false,ready,null);
                });
            }catch(Exception failure){main.post(() -> {if(accept(request)){sendingQuestion=null;publish(false,null,errorCode(failure));}});}
        });
    }
    @Override protected void onCleared() {
        cleared=true;++generation;++selectionGeneration;picker=-1;pickerLaunched=false;consentIntent=null;draft.clear();messages.clear();sendingQuestion=null;executor.shutdownNow();app.accountInvalidations().removeObserver(invalidations);super.onCleared();
    }
}
