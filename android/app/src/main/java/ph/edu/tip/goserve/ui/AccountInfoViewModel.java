package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.AuthRepository;
import ph.edu.tip.goserve.data.SupabaseClient;
import ph.edu.tip.goserve.domain.AccountValidation;
import ph.edu.tip.goserve.domain.Role;
import ph.edu.tip.goserve.domain.SessionProfile;
import ph.edu.tip.goserve.domain.StudentProfile;

public final class AccountInfoViewModel extends AndroidViewModel {
    interface Actions {void requireCurrent();SessionProfile current();SessionProfile save(Update update)throws Exception;}
    static final class Update {
        final long expectedVersion;final StudentProfile profile;final String operationKey=UUID.randomUUID().toString();
        Update(long expectedVersion,StudentProfile profile){this.expectedVersion=expectedVersion;this.profile=profile;}
    }
    public static final class State {
        public final SessionProfile profile;public final boolean busy,saved;public final int message;
        public final Map<String,AccountValidation.ProfileError> fieldErrors;
        State(SessionProfile profile,boolean busy,boolean saved,int message,Map<String,AccountValidation.ProfileError> errors){this.profile=profile;this.busy=busy;this.saved=saved;this.message=message;this.fieldErrors=Collections.unmodifiableMap(new LinkedHashMap<>(errors));}
    }
    private final Actions actions;private final Executor worker,publication;
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(null,false,false,0,Collections.emptyMap()));
    private StudentProfile draft=empty();private Update pending;private boolean started,preview;private volatile boolean cleared;private volatile int generation;
    public AccountInfoViewModel(Application app){this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post);}
    AccountInfoViewModel(Application app,Actions actions,Executor worker,Executor publication){super(app);this.actions=actions;this.worker=worker;this.publication=publication;}
    public LiveData<State> state(){return state;}
    public StudentProfile draft(){return draft;}
    public void start(boolean preview){
        if(started)return;started=true;this.preview=preview;
        if(preview){publish(null,false,false,R.string.account_info_preview,Collections.emptyMap());return;}
        if(!available())return;SessionProfile profile=actions.current();draft=profile.student==null?empty():profile.student;
        publish(profile,false,false,profile.role==Role.STUDENT?0:R.string.account_info_read_only,Collections.emptyMap());
    }
    public boolean canEdit(){State held=state.getValue();return !cleared && !preview && held.profile!=null && held.profile.role==Role.STUDENT && held.profile.ready() && !held.busy && pending==null;}
    public boolean canSave(){State held=state.getValue();return !cleared && !preview && held.profile!=null && held.profile.role==Role.STUDENT && held.profile.ready() && !held.busy && (pending!=null || draft!=held.profile.student);}
    public boolean retrying(){return pending!=null;}
    public void setDraft(StudentProfile next){if(canEdit()){draft=next;publish(state.getValue().profile,false,false,0,Collections.emptyMap());}}
    public void checkCurrentAccount(){if(started && !preview && !cleared)available();}
    public void save(){
        if(!canSave() || !available())return;
        if(pending==null){Map<String,AccountValidation.ProfileError> errors=AccountValidation.profileErrors(draft);if(!errors.isEmpty()){publish(state.getValue().profile,false,false,R.string.account_info_invalid,errors);return;}pending=new Update(state.getValue().profile.version,draft);}
        Update held=pending;int request=++generation;SessionProfile profile=state.getValue().profile;publish(profile,true,false,0,Collections.emptyMap());
        worker.execute(() -> {
            if(cleared || request!=generation)return;
            try{actions.requireCurrent();SessionProfile saved=actions.save(held);actions.requireCurrent();publication.execute(() -> {
                if(cleared || request!=generation || !available())return;pending=null;draft=saved.student==null?empty():saved.student;publish(saved,false,true,R.string.account_info_saved,Collections.emptyMap());
            });}catch(Exception error){publication.execute(() -> {
                if(cleared || request!=generation || !available())return;
                String code=error instanceof SupabaseClient.ApiException?((SupabaseClient.ApiException)error).code:"";
                if("STALE_VERSION".equals(code)){pending=null;SessionProfile canonical=actions.current();draft=canonical.student==null?empty():canonical.student;publish(canonical,false,false,R.string.account_info_stale,Collections.emptyMap());}
                else if(java.util.Arrays.asList("FORBIDDEN","TERMS_REQUIRED","PROFILE_INCOMPLETE","AUTH_REQUIRED","AUTH_INVALID").contains(code))unavailable();
                else publish(profile,false,false,R.string.account_info_uncertain,Collections.emptyMap());
            });}
        });
    }
    private boolean available(){try{actions.requireCurrent();SessionProfile current=actions.current();if(current==null || state.getValue().profile!=null && !current.id.equals(state.getValue().profile.id))throw new IllegalStateException();return true;}catch(IllegalStateException unavailable){unavailable();return false;}}
    private void unavailable(){++generation;pending=null;draft=empty();publish(null,false,false,R.string.account_info_unavailable,Collections.emptyMap());}
    private void publish(SessionProfile profile,boolean busy,boolean saved,int message,Map<String,AccountValidation.ProfileError> errors){state.setValue(new State(profile,busy,saved,message,errors));}
    private static StudentProfile empty(){return new StudentProfile("","",false,"","","","","","");}
    private static Actions production(GoServeApplication app){
        AuthRepository auth=app.auth();
        return new Actions(){
            AuthRepository.SessionLease lease;
            public void requireCurrent(){if(lease==null)lease=auth.sessionLease();auth.requireCurrent(lease);}
            public SessionProfile current(){requireCurrent();return auth.profile();}
            public SessionProfile save(Update update)throws Exception{requireCurrent();SessionProfile result=auth.updateAccount(update.expectedVersion,null,update.profile,update.operationKey);requireCurrent();return result;}
        };
    }
    @Override protected void onCleared(){cleared=true;++generation;pending=null;draft=empty();publish(null,false,false,0,Collections.emptyMap());if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow();super.onCleared();}
}
