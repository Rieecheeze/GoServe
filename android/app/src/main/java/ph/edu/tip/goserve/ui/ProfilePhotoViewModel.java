package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.ProfileImage;
import ph.edu.tip.goserve.data.SupabaseClient;
import ph.edu.tip.goserve.data.ProfilePhotoRepository;

public final class ProfilePhotoViewModel extends AndroidViewModel {
    interface Actions {void requireCurrent();ProfilePhotoRepository.Photo load()throws Exception;ProfilePhotoRepository.Photo save(ProfilePhotoRepository.Update update)throws Exception;}
    public interface ImageSource {InputStream open()throws Exception;}
    public enum Mode{LOADING,READY,SELECTING,SAVING,UNAVAILABLE}
    public static final class State {
        public final Mode mode;public final ProfilePhotoRepository.Photo photo;public final String error;public final boolean saved;
        State(Mode mode,ProfilePhotoRepository.Photo photo,String error,boolean saved){this.mode=mode;this.photo=photo;this.error=error;this.saved=saved;}
        public boolean busy(){return mode==Mode.LOADING || mode==Mode.SELECTING || mode==Mode.SAVING;}
    }
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(Mode.LOADING,null,null,false));
    private final Actions actions;private final Executor worker,publication;private final AtomicLong generation=new AtomicLong();
    private volatile boolean cleared;private boolean started,removeSelected,reloadRequired;private byte[] draft;private ProfilePhotoRepository.Update update;
    public ProfilePhotoViewModel(Application app){this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post);}
    ProfilePhotoViewModel(Application app,Actions actions,Executor worker,Executor publication){super(app);this.actions=actions;this.worker=worker;this.publication=publication;reload();}
    public LiveData<State> state(){return state;}
    public byte[] image(){if(cleared || state.getValue().mode==Mode.UNAVAILABLE || removeSelected)return null;return draft!=null?draft.clone():state.getValue().photo==null?null:state.getValue().photo.image();}
    public boolean canEdit(){return !cleared && state.getValue().mode==Mode.READY && state.getValue().photo!=null && update==null;}
    public boolean canSave(){return !cleared && !reloadRequired && state.getValue().mode==Mode.READY && state.getValue().photo!=null && (update!=null || draft!=null || removeSelected && state.getValue().photo.path!=null);}
    public void reload(){
        if(cleared || started && state.getValue().busy() || state.getValue().mode==Mode.UNAVAILABLE || !available())return;
        started=true;clearDraft();dispatch(Mode.LOADING,actions::load,p->{clearDraft();publish(Mode.READY,p,null,false);});
    }
    public void select(ImageSource source){
        if(source==null || !canEdit() || !available())return;
        dispatch(Mode.SELECTING,()->{try(InputStream input=source.open()){return ProfileImage.normalize(input);}},bytes->{draft=bytes;removeSelected=false;publish(Mode.READY,state.getValue().photo,null,false);});
    }
    public void remove(){if(!canEdit() || !available())return;draft=null;removeSelected=true;publish(Mode.READY,state.getValue().photo,null,false);}
    public void save(){
        if(!canSave() || !available())return;
        if(update==null)update=new ProfilePhotoRepository.Update(state.getValue().photo.version,removeSelected?null:draft);
        ProfilePhotoRepository.Update held=update;
        dispatch(Mode.SAVING,()->actions.save(held),photo->{clearDraft();publish(Mode.READY,photo,null,true);});
    }
    public void checkCurrentAccount(){if(!cleared && state.getValue().mode!=Mode.UNAVAILABLE)available();}
    private boolean available(){try{actions.requireCurrent();return true;}catch(IllegalStateException expired){unavailable("SESSION_EXPIRED");return false;}}
    private void unavailable(String error){generation.incrementAndGet();clearDraft();publish(Mode.UNAVAILABLE,null,error,false);}
    private void clearDraft(){draft=null;update=null;removeSelected=false;reloadRequired=false;}
    private void publish(Mode mode,ProfilePhotoRepository.Photo photo,String error,boolean saved){state.setValue(new State(mode,photo,error,saved));}
    private interface Work<T>{T run()throws Exception;}
    private interface Result<T>{void accept(T value);}
    private <T> void dispatch(Mode mode,Work<T> operation,Result<T> success){
        long request=generation.incrementAndGet();ProfilePhotoRepository.Photo photo=state.getValue().photo;publish(mode,photo,null,false);
        worker.execute(()->{
            if(cleared || request!=generation.get())return;
            try{
                actions.requireCurrent();T result=operation.run();actions.requireCurrent();
                publication.execute(()->{if(!cleared && request==generation.get() && available())success.accept(result);});
            }catch(Exception error){publication.execute(()->{
                if(cleared || request!=generation.get() || !available())return;
                String code=error instanceof SupabaseClient.ApiException?((SupabaseClient.ApiException)error).code:"";
                if(java.util.Arrays.asList("AUTH_REQUIRED","AUTH_INVALID","FORBIDDEN","TERMS_REQUIRED","PROFILE_INCOMPLETE").contains(code)){unavailable("PHOTO_ACCESS_REQUIRED");return;}
                reloadRequired=mode==Mode.SAVING && java.util.Arrays.asList("STALE_VERSION","UPLOAD_INVALID","IDEMPOTENCY_CONFLICT").contains(code);
                String message=mode==Mode.SELECTING?"PHOTO_INVALID":mode==Mode.LOADING?"PHOTO_LOAD_FAILED":
                        "STALE_VERSION".equals(code)?code:reloadRequired?"PHOTO_RELOAD_REQUIRED":"PHOTO_SAVE_UNCERTAIN";
                publish(Mode.READY,photo,message,false);
            });}
        });
    }
    private static Actions production(GoServeApplication app){
        ProfilePhotoRepository held;try{held=new ProfilePhotoRepository(app.gateway());}catch(IllegalStateException unavailable){held=null;}
        ProfilePhotoRepository repo=held;
        return new Actions(){
            public void requireCurrent(){if(repo==null)throw new IllegalStateException();repo.requireCurrent();}
            public ProfilePhotoRepository.Photo load()throws Exception{requireCurrent();return repo.load();}
            public ProfilePhotoRepository.Photo save(ProfilePhotoRepository.Update update)throws Exception{requireCurrent();return repo.save(update);}
        };
    }
    @Override protected void onCleared(){cleared=true;generation.incrementAndGet();clearDraft();publish(Mode.UNAVAILABLE,null,null,false);if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow();super.onCleared();}
}
