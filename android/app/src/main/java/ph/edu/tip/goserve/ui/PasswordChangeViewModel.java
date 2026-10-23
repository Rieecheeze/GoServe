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
import ph.edu.tip.goserve.data.AuthRepository;
import ph.edu.tip.goserve.domain.AccountValidation;

public final class PasswordChangeViewModel extends AndroidViewModel {
    interface Actions {
        void requireCurrent();
        boolean completedSignOut();
        void change(String current,String password,String confirmation,String code) throws Exception;
        void requestCode() throws Exception;
    }
    public enum Mode { EDITING, CHANGING, SENDING_CODE, DONE, UNAVAILABLE }
    public static final class State {
        public final Mode mode;
        public final boolean requiresCode,codeSent;
        public final String error;
        State(Mode mode,boolean requiresCode,boolean codeSent,String error) {
            this.mode=mode; this.requiresCode=requiresCode; this.codeSent=codeSent; this.error=error;
        }
        public boolean busy() { return mode==Mode.CHANGING || mode==Mode.SENDING_CODE; }
    }
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(Mode.EDITING,false,false,null));
    private final Actions actions;
    private final Executor worker,publication;
    private final AtomicLong generation=new AtomicLong();
    private volatile boolean cleared;
    private String current="",password="",confirmation="",code="";
    public PasswordChangeViewModel(Application app) {
        this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post);
    }
    PasswordChangeViewModel(Application app,Actions actions,Executor worker,Executor publication) {
        super(app); this.actions=actions; this.worker=worker; this.publication=publication; checkCurrentAccount();
    }
    public LiveData<State> state() { return state; }
    public void edit(String current,String password,String confirmation,String code) {
        if(!editable() || !available())return;
        this.current=text(current); this.password=text(password); this.confirmation=text(confirmation); this.code=text(code);
        editing(null,state.getValue().requiresCode,state.getValue().codeSent);
    }
    public String currentPassword() { return current; }
    public String newPassword() { return password; }
    public String confirmation() { return confirmation; }
    public String code() { return code; }
    public void submit() {
        if(!editable() || !available())return;
        String invalid=null;
        if(current.isEmpty())invalid="CURRENT_PASSWORD_REQUIRED";
        else {
            AccountValidation.PasswordError error=AccountValidation.passwordError(password,confirmation);
            if(error!=null)invalid=error==AccountValidation.PasswordError.CONFIRMATION_MISMATCH?"CONFIRMATION_MISMATCH":"PASSWORD_WEAK";
            else if(current.equals(password))invalid="PASSWORD_REUSED";
            else if(state.getValue().requiresCode && code.isEmpty())invalid="CODE_REQUIRED";
        }
        if(invalid!=null) { editing(invalid,state.getValue().requiresCode,state.getValue().codeSent); return; }
        String heldCurrent=current,heldPassword=password,heldConfirmation=confirmation,heldCode=code;
        dispatch(true,()->actions.change(heldCurrent,heldPassword,heldConfirmation,heldCode));
    }
    public void requestCode() {
        if(!editable() || !available() || !state.getValue().requiresCode)return;
        dispatch(false,actions::requestCode);
    }
    public void checkCurrentAccount() {
        if(cleared || state.getValue().mode==Mode.DONE || state.getValue().mode==Mode.UNAVAILABLE)return;
        if(state.getValue().mode==Mode.CHANGING && actions.completedSignOut())return;
        available();
    }
    private boolean editable() { return !cleared && state.getValue().mode==Mode.EDITING; }
    private boolean currentAccount() {
        try { actions.requireCurrent(); return true; } catch(IllegalStateException unavailable) { return false; }
    }
    private boolean available() {
        if(currentAccount())return true;
        unavailable("SESSION_EXPIRED"); return false;
    }
    private void unavailable(String error) {
        generation.incrementAndGet(); clearSecrets(); state.setValue(new State(Mode.UNAVAILABLE,false,false,error));
    }
    private void editing(String error,boolean requiresCode,boolean codeSent) {
        state.setValue(new State(Mode.EDITING,requiresCode,codeSent,error));
    }
    private interface Work { void run() throws Exception; }
    private void dispatch(boolean changing,Work operation) {
        State held=state.getValue(); long request=generation.incrementAndGet();
        state.setValue(new State(changing?Mode.CHANGING:Mode.SENDING_CODE,held.requiresCode,held.codeSent,null));
        worker.execute(()->{
            if(cleared || generation.get()!=request)return;
            try {
                actions.requireCurrent(); operation.run();
                publication.execute(()->{
                    if(cleared || generation.get()!=request)return;
                    if(changing) {
                        if(!actions.completedSignOut()) { unavailable("SESSION_EXPIRED"); return; }
                        clearSecrets(); state.setValue(new State(Mode.DONE,false,false,null));
                    } else if(available())editing(null,true,true);
                });
            } catch(Exception error) {
                publication.execute(()->{
                    if(cleared || generation.get()!=request)return;
                    String failure=errorCode(error);
                    if(!currentAccount()) { unavailable("OUTCOME_UNCERTAIN".equals(failure)?failure:"SESSION_EXPIRED"); return; }
                    boolean requiresCode=held.requiresCode || "REAUTHENTICATION_REQUIRED".equals(failure) || "CODE_INVALID".equals(failure);
                    if("CODE_INVALID".equals(failure))code="";
                    editing(failure,requiresCode,held.codeSent);
                });
            }
        });
    }
    private static String errorCode(Exception error) {
        if(error instanceof AuthRepository.PasswordChangeException) {
            String code=((AuthRepository.PasswordChangeException)error).code;
            switch(code) {
                case "CURRENT_PASSWORD_INVALID": case "REAUTHENTICATION_REQUIRED": case "CODE_INVALID": case "PASSWORD_REUSED":
                case "PASSWORD_WEAK": case "SESSION_EXPIRED": case "RATE_LIMITED": case "OUTCOME_UNCERTAIN": return code;
            }
        }
        return "PASSWORD_CHANGE_FAILED";
    }
    private static String text(String value) { return value==null?"":value; }
    private void clearSecrets() { current=password=confirmation=code=""; }
    private static Actions production(GoServeApplication app) {
        AuthRepository auth=app.auth(); AuthRepository.SessionLease held;
        try { held=auth.sessionLease(); } catch(IllegalStateException unavailable) { held=null; }
        AuthRepository.SessionLease lease=held;
        return new Actions() {
            public void requireCurrent() { auth.requireCurrent(lease); }
            public boolean completedSignOut() { return lease!=null && auth.profile()==null && auth.generation()==lease.generation+1; }
            public void change(String current,String password,String confirmation,String code) throws Exception { auth.changePassword(lease,current,password,confirmation,code); }
            public void requestCode() throws Exception { auth.requestPasswordReauthentication(lease); }
        };
    }
    @Override protected void onCleared() {
        cleared=true; generation.incrementAndGet(); clearSecrets();
        if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow();
        super.onCleared();
    }
}
