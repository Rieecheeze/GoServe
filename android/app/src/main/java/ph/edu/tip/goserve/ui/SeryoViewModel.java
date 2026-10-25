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
import ph.edu.tip.goserve.data.CameraEvidence;
import ph.edu.tip.goserve.R;

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
        public final boolean preview;
        public final String consent;
        State(boolean busy,SeryoRepository.Readiness readiness,String error,SeryoDraft draft,long decisionSequence,boolean consentPending,java.util.List<Message> messages,String sendingQuestion,boolean preview,String consent) {
            this.busy=busy;this.readiness=readiness;this.error=error;this.draft=draft;this.decisionSequence=decisionSequence;this.consentPending=consentPending;
            this.messages=java.util.Collections.unmodifiableList(new java.util.ArrayList<>(messages));this.sendingQuestion=sendingQuestion;
            this.preview=preview;this.consent=consent;
        }
        public boolean allowsLocalAttachment(){return !busy && !consentPending && "allow".equals(consent) && (preview || readiness!=null && "clear".equals(readiness.cleanup));}
        public boolean allowsSend(){return !busy && !consentPending && "allow".equals(consent) && (preview || readiness!=null && readiness.canSend && readiness.providerReady && "clear".equals(readiness.cleanup));}
    }
    private static final class ConsentIntent {
        final String decision,key;
        final long expected;
        ConsentIntent(String decision,long expected) {this.decision=decision;this.expected=expected;key=java.util.UUID.randomUUID().toString();}
    }
    private final SeryoDraft draft=new SeryoDraft();
    private final java.util.List<Message> messages=new java.util.ArrayList<>();
    private String sendingQuestion;
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(false,null,null,draft,0,false,java.util.Collections.emptyList(),null,false,null));
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService executor;
    private final GoServeApplication app;
    private final Observer<Long> invalidations=ignored -> verifyAccount();
    private SeryoRepository repository;
    private boolean started,preview,pickerLaunched;
    private String previewConsent="unset";
    private CameraEvidence capture;
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
        if(!java.util.Arrays.asList("allow","decline","revoke").contains(decision))throw new IllegalArgumentException("Choose a consent decision.");
        if(cleared || before.busy || !current())return;
        if(preview) {
            if(!"unset".equals(previewConsent) && !("allow".equals(previewConsent) && "revoke".equals(decision)))return;
            if("unset".equals(previewConsent) && "revoke".equals(decision))return;
            previewConsent=decision;++decisionSequence;
            if(!"allow".equals(decision)){draft.clear();messages.clear();clearCapture();++selectionGeneration;picker=-1;pickerLaunched=false;}
            publish(false,null,null);return;
        }
        if(repository==null)return;
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
        int request=++generation;++selectionGeneration;picker=-1;pickerLaunched=false;clearCapture();
        if(!"allow".equals(command.decision)){draft.clear();messages.clear();sendingQuestion=null;}
        publish(true,state.getValue().readiness,null);
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
            ++generation;++selectionGeneration;picker=-1;pickerLaunched=false;clearCapture();consentIntent=null;draft.clear();messages.clear();sendingQuestion=null;repository=null;publish(false,null,"ACCOUNT_CHANGED");return false;
        }
    }
    private boolean accept(int request) {return !cleared && request==generation && current();}
    private void publish(boolean busy,SeryoRepository.Readiness ready,String error) {state.setValue(new State(busy,ready,error,draft,decisionSequence,consentIntent!=null,messages,sendingQuestion,preview,preview?previewConsent:ready==null?null:ready.consent));}
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
        if(!before.allowsLocalAttachment() || picker!=-1 || !current())return;
        int selection=++selectionGeneration;picker=selection;pickerLaunched=false;
        if(preview){launchPicker(selection,launch);return;}
        int request=++generation;publish(true,null,null);
        executor.execute(() -> {
            try {
                SeryoRepository.Readiness ready=repository.readiness();
                main.post(() -> {
                    if(!accept(request) || selection!=selectionGeneration || picker!=selection)return;publish(false,ready,null);
                    if(localAllowed(ready))launchPicker(selection,launch);else {picker=-1;pickerLaunched=false;}
                });
            } catch(Exception failure) {main.post(() -> {if(accept(request) && selection==selectionGeneration) {picker=-1;pickerLaunched=false;publish(false,null,errorCode(failure));}});}
        });
    }
    private boolean localAllowed(SeryoRepository.Readiness ready){return preview?"allow".equals(previewConsent):ready!=null && "allow".equals(ready.consent) && "clear".equals(ready.cleanup);}
    private void launchPicker(int selection,IntConsumer launch){
        pickerLaunched=true;
        try{launch.accept(selection);}
        catch(android.content.ActivityNotFoundException | IllegalStateException | SecurityException unavailable){finishPicker(null,false);clearCapture();publish(false,state.getValue().readiness,"PICKER_UNAVAILABLE");}
    }
    public void requestCamera(java.util.function.Consumer<Uri> launch){
        requestPicker(ignored->{
            try{clearCapture();capture=CameraEvidence.prepare(app);launch.accept(capture.uri());}
            catch(Exception unavailable){clearCapture();finishPicker(null,false);publish(false,state.getValue().readiness,"CAMERA_UNAVAILABLE");}
        });
    }
    public void finishCamera(boolean selected){
        CameraEvidence result=capture;capture=null;
        if(result==null){finishPicker(null,false);return;}
        finishSelection(result.uri(),selected,result);
    }
    private void releaseCapture(CameraEvidence value){if(value!=null){app.revokeUriPermission(value.uri(),Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);value.close();}}
    private void clearCapture(){releaseCapture(capture);capture=null;}
    public void cancelUnlaunchedPicker() {
        if(cleared || picker<0 || pickerLaunched || !current())return;
        ++generation;++selectionGeneration;picker=-1;publish(false,null,null);
    }
    public void finishPicker(Intent result,boolean selected) {
        if(picker<0 || picker!=selectionGeneration || !current())return;
        if(selected && result!=null && result.getClipData()!=null && result.getClipData().getItemCount()!=1) {picker=-1;pickerLaunched=false;publish(false,state.getValue().readiness,"ONE_FILE");return;}
        Uri uri=result==null?null:result.getData();
        if(uri==null && result!=null && result.getClipData()!=null)uri=result.getClipData().getItemAt(0).getUri();
        finishSelection(uri,selected,null);
    }
    private void finishSelection(Uri uri,boolean selected,CameraEvidence cameraResult){
        int selection=picker;picker=-1;pickerLaunched=false;
        if(selection<0 || selection!=selectionGeneration || !current() || !selected){releaseCapture(cameraResult);return;}
        if(uri==null) {releaseCapture(cameraResult);publish(false,state.getValue().readiness,"FILE_UNREADABLE");return;}
        Uri candidateUri=uri;int request=++generation;publish(true,null,null);
        executor.execute(() -> {
            SeryoDraft.Attachment candidate=null;
            try {
                SeryoRepository.Readiness ready=preview?null:repository.readiness();
                if(!localAllowed(ready)) {main.post(() -> {if(accept(request))publish(false,ready,"FILE_UNAVAILABLE");});return;}
                candidate=new SeryoAttachmentSource().read(app.getContentResolver(),candidateUri,() -> {
                    if(!preview)repository.requireCurrent();if(selection!=selectionGeneration || Thread.currentThread().isInterrupted())throw new IllegalStateException("Selection changed.");
                });
                SeryoRepository.Readiness canonical=preview?null:repository.readiness();
                SeryoDraft.Attachment validated=candidate;candidate=null;
                main.post(() -> {
                    if(!accept(request) || selection!=selectionGeneration) {validated.close();return;}
                    if(!localAllowed(canonical)) {validated.close();publish(false,canonical,"FILE_UNAVAILABLE");return;}
                    draft.replace(validated);publish(false,canonical,null);
                });
            } catch(Exception failure) {
                main.post(() -> {if(accept(request) && selection==selectionGeneration)publish(false,null,failure instanceof SupabaseClient.ApiException?errorCode(failure):"FILE_UNREADABLE");});
            } finally {if(candidate!=null)candidate.close();releaseCapture(cameraResult);}
        });
    }
    public void removeAttachment() {
        if(!current() || state.getValue().busy && consentIntent!=null)return;
        ++selectionGeneration;picker=-1;pickerLaunched=false;clearCapture();++generation;draft.remove();publish(false,state.getValue().readiness,consentIntent==null?null:"CONSENT_UNCERTAIN");
    }
    public void prepare(SeryoDraft.Operation operation) {
        if(!current() || state.getValue().busy || draft.attachment==null)return;
        draft.prepare(operation);publish(false,state.getValue().readiness,null);
    }
    public void checkSend() {
        State before=state.getValue();
        if(cleared || before.busy || consentIntent!=null || !current())return;
        String question=draft.text.trim();if(question.isEmpty() || question.length()>2000 || draft.attachment!=null)return;
        if(preview){
            if(!"allow".equals(previewConsent))return;
            String topic=question.toLowerCase(java.util.Locale.ROOT);
            int answer=topic.contains("request") || topic.contains("document")?R.string.seryo_preview_requests:topic.contains("office") || topic.contains("registrar")?R.string.seryo_preview_offices:R.string.seryo_preview_scope;
            messages.add(new Message(true,question));messages.add(new Message(false,app.getString(answer)));
            while(messages.size()>20)messages.remove(0);draft.text="";publish(false,null,null);return;
        }
        if(before.readiness==null || !before.readiness.canSend || !before.readiness.providerReady || !"allow".equals(before.readiness.consent) || !"clear".equals(before.readiness.cleanup))return;
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
        cleared=true;++generation;++selectionGeneration;picker=-1;pickerLaunched=false;clearCapture();consentIntent=null;draft.clear();messages.clear();sendingQuestion=null;executor.shutdownNow();app.accountInvalidations().removeObserver(invalidations);super.onCleared();
    }
}
