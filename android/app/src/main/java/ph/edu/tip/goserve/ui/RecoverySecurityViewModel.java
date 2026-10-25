package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.RecoveryRepository;
import ph.edu.tip.goserve.data.SupabaseClient;
import ph.edu.tip.goserve.domain.AccountValidation;
import ph.edu.tip.goserve.domain.RecoveryData;
import ph.edu.tip.goserve.domain.SessionProfile;

public final class RecoverySecurityViewModel extends AndroidViewModel {
    public enum Kind { SETUP, CODES, FORGOT, PASSWORD_CHANGE }
    public enum Page { LOADING, STATUS, CODES, PROOF, PASSWORD, PENDING, COMPLETE, UNAVAILABLE }
    interface Actions extends AutoCloseable {
        void requireCurrent();String email();RecoveryData.Status status()throws Exception;
        RecoveryData.Codes generate()throws Exception;RecoveryData.Status acknowledge(String generation)throws Exception;
        void verify(String email,String code)throws Exception;RecoveryData.ResetResult reset(String password)throws Exception;
        RecoveryData.ResetResult result()throws Exception;SessionProfile signIn(String password)throws Exception;void close();
    }
    public static final class State {
        public final Kind kind;public final Page page;public final boolean busy;public final String error;
        public final RecoveryData.Status status;public final RecoveryData.Codes codes;public final SessionProfile signedIn;
        State(Kind kind,Page page,boolean busy,String error,RecoveryData.Status status,RecoveryData.Codes codes,SessionProfile signedIn){this.kind=kind;this.page=page;this.busy=busy;this.error=error;this.status=status;this.codes=codes;this.signedIn=signedIn;}
    }
    private final Actions actions;private final Executor worker,publication;
    private final MutableLiveData<State> state=new MutableLiveData<>();
    private Kind kind;private Page page=Page.LOADING;private RecoveryData.Status status;private RecoveryData.Codes codes;
    private String email="",proof="",password="",confirmation="";private boolean started,preview,busy,signingIn;
    private volatile boolean cleared;private volatile int generation;
    public RecoverySecurityViewModel(Application app){this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post);}
    RecoverySecurityViewModel(Application app,Actions actions,Executor worker,Executor publication){super(app);this.actions=actions;this.worker=worker;this.publication=publication;}
    public LiveData<State> state(){return state;}
    public String email(){return email;}public String proof(){return proof;}public String password(){return password;}public String confirmation(){return confirmation;}
    public void start(Kind kind,boolean preview){
        if(started)return;started=true;this.kind=kind;this.preview=preview;
        if(preview){page=Page.UNAVAILABLE;emit("PREVIEW");return;}
        if(kind==Kind.FORGOT){page=Page.PROOF;emit(null);return;}
        try{actions.requireCurrent();email=actions.email();}catch(IllegalStateException ended){unavailable();return;}
        if(kind==Kind.PASSWORD_CHANGE){page=Page.PROOF;emit(null);}else refresh();
    }
    public void editEmail(String value){if(editable()&&kind==Kind.FORGOT)email=value;}
    public void editProof(String value){if(editable())proof=value;}
    public void editPasswords(String value,String confirmed){if(editable()){password=value;confirmation=confirmed;}}
    private boolean editable(){return !busy&&!preview&&!cleared;}
    public void refresh(){if(!editable())return;codes=null;proof="";run(Page.LOADING,actions::status,value->{status=value;page=kind==Kind.SETUP&&!value.requiresSetup?Page.COMPLETE:Page.STATUS;emit(null);},false);}
    public void primary(){
        if(!editable())return;
        switch(page){
            case STATUS:run(Page.STATUS,actions::generate,value->{codes=value;proof="";page=Page.CODES;emit(null);},true);break;
            case CODES:run(Page.CODES,()->actions.acknowledge(codes.generation),value->{status=value;codes=null;proof="";page=kind==Kind.SETUP?Page.COMPLETE:Page.STATUS;emit(null);},false);break;
            case PROOF:
                if(!AccountValidation.isValidEmail(email)){emit("EMAIL_REQUIRED");return;}
                if(proof.trim().isEmpty()){emit("CODE_REQUIRED");return;}
                run(Page.PROOF,()->{actions.verify(email,proof);return Boolean.TRUE;},value->{proof="";page=Page.PASSWORD;emit(null);},false);break;
            case PASSWORD:
                AccountValidation.PasswordError error=AccountValidation.passwordError(password,confirmation);
                if(error!=null){emit(error==AccountValidation.PasswordError.CONFIRMATION_MISMATCH?"CONFIRMATION_MISMATCH":"PASSWORD_WEAK");return;}
                if(password.getBytes(StandardCharsets.UTF_8).length>72){emit("PASSWORD_TOO_LONG");return;}
                run(Page.PASSWORD,()->actions.reset(password),this::resetResult,false);break;
            case PENDING:run(Page.PENDING,actions::result,this::resetResult,false);break;
            case UNAVAILABLE:if(!preview&&kind!=Kind.FORGOT&&kind!=Kind.PASSWORD_CHANGE)refresh();break;
            default:break;
        }
    }
    private void resetResult(RecoveryData.ResetResult result){
        if("pending".equals(result.state)){page=Page.PENDING;emit(null);return;}
        if("failed".equals(result.state)){clearSecrets();page=Page.UNAVAILABLE;emit("RESET_FAILED");return;}
        signingIn=true;
        run(Page.PENDING,()->actions.signIn(password),profile->{signingIn=false;clearSecrets();page=Page.COMPLETE;state.setValue(new State(kind,page,false,null,null,null,profile));},false);
    }
    public boolean unsavedCodes(){return codes!=null;}public boolean canGoBack(){return !busy;}
    public void checkCurrentAccount(){if(!started||preview||cleared||signingIn)return;try{actions.requireCurrent();}catch(IllegalStateException expired){unavailable();}}
    public boolean currentForDisplay(){if(preview)return true;if(cleared)return false;if(signingIn&&busy&&page==Page.PENDING)return true;try{actions.requireCurrent();return true;}catch(IllegalStateException expired){if(state.getValue().page!=Page.UNAVAILABLE)unavailable();return false;}}
    public void abandon(){++generation;busy=false;status=null;email="";clearSecrets();actions.close();state.setValue(new State(kind,Page.UNAVAILABLE,false,null,null,null,null));}
    private interface Work<T>{T run()throws Exception;}
    private <T> void run(Page whileWorking,Work<T> operation,Consumer<T> success,boolean generationMayBeLost){
        int request=++generation;page=whileWorking;busy=true;emit(null);
        worker.execute(()->{
            if(cleared||request!=generation)return;
            try{actions.requireCurrent();T value=operation.run();actions.requireCurrent();publication.execute(()->{
                if(cleared||request!=generation)return;
                try{actions.requireCurrent();}catch(IllegalStateException expired){unavailable();return;}
                busy=false;success.accept(value);
            });}catch(Exception failure){publication.execute(()->{
                if(cleared||request!=generation)return;busy=false;
                if(signingIn){signingIn=false;clearSecrets();page=Page.UNAVAILABLE;emit("SIGN_IN_REQUIRED");return;}
                try{actions.requireCurrent();}catch(IllegalStateException expired){unavailable();return;}
                String code=failure instanceof SupabaseClient.ApiException?((SupabaseClient.ApiException)failure).code:"TEMPORARILY_UNAVAILABLE";
                if(page==Page.PROOF&&"TEMPORARILY_UNAVAILABLE".equals(code))code="PROOF_UNCONFIRMED";
                if(generationMayBeLost&&!"RECENT_AUTH_REQUIRED".equals(code)&&!"RATE_LIMITED".equals(code)){codes=null;proof="";page=Page.UNAVAILABLE;emit("CODES_UNCONFIRMED");}
                else{if("RECOVERY_GRANT_INVALID".equals(code)){clearSecrets();page=Page.UNAVAILABLE;}emit(code);}
            });}
        });
    }
    private void unavailable(){++generation;busy=false;status=null;email="";clearSecrets();page=Page.UNAVAILABLE;emit("AUTH_REQUIRED");}
    private void clearSecrets(){codes=null;proof="";password="";confirmation="";}
    private void emit(String error){state.setValue(new State(kind,page,busy,error,status,codes,null));}
    private static Actions production(GoServeApplication app){return new Actions(){
        RecoveryRepository repository;private RecoveryRepository repo(){if(repository==null)repository=new RecoveryRepository(app.auth());return repository;}
        public void requireCurrent(){repo().requireCurrent();}public String email(){SessionProfile profile=app.auth().profile();return profile==null?"":profile.email;}
        public RecoveryData.Status status()throws Exception{return repo().status();}public RecoveryData.Codes generate()throws Exception{return repo().generateCodes();}
        public RecoveryData.Status acknowledge(String generation)throws Exception{return repo().acknowledgeCodes(generation);}
        public void verify(String email,String code)throws Exception{repo().verifyProof(email,code);}
        public RecoveryData.ResetResult reset(String password)throws Exception{return repo().resetPassword(password);}public RecoveryData.ResetResult result()throws Exception{return repo().queryResetResult();}
        public SessionProfile signIn(String password)throws Exception{return repo().completePasswordSignIn(password);}public void close(){if(repository!=null)repository.close();}
    };}
    @Override protected void onCleared(){cleared=true;++generation;status=null;email="";clearSecrets();state.setValue(new State(kind,Page.UNAVAILABLE,false,null,null,null,null));actions.close();if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow();super.onCleared();}
}
