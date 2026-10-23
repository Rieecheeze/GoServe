package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.AccountGateway;
import ph.edu.tip.goserve.data.InquiryRepository;
import ph.edu.tip.goserve.domain.InquiryData;

public final class NotificationsViewModel extends AndroidViewModel {
    interface Actions {
        boolean allowed(); void requireCurrent();
        InquiryData.NotificationPage page(String time,String id) throws Exception;
        InquiryData.UnreadCounts counts() throws Exception;
        long acknowledge(InquiryData.NotificationPage page,List<String> ids) throws Exception;
    }
    public static final class State {
        public final boolean loading,acknowledging,blocked,pageCurrent;
        public final InquiryData.NotificationPage page;
        public final InquiryData.UnreadCounts counts;
        public final String error;
        State(boolean loading,boolean acknowledging,boolean blocked,InquiryData.NotificationPage page,InquiryData.UnreadCounts counts,String error,boolean pageCurrent) {
            this.loading=loading;this.acknowledging=acknowledging;this.blocked=blocked;this.page=page;this.counts=counts;this.error=error;this.pageCurrent=pageCurrent;
        }
        public boolean busy() { return loading || acknowledging; }
    }
    private final Actions actions;
    private final Executor worker,publication;
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(false,false,false,null,null,null,false));
    private volatile long generation;
    private volatile boolean cleared;
    private boolean loaded,pending,blocked;
    private String currentTime,currentId;
    public NotificationsViewModel(Application app) {
        this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post);
    }
    NotificationsViewModel(Application app,Actions actions,Executor worker,Executor publication) {
        super(app);this.actions=actions;this.worker=worker;this.publication=publication;
        if(!actions.allowed())clear("FORBIDDEN");
    }
    public LiveData<State> state() { return state; }
    public void ensureLoaded() { if(!loaded) { loaded=true; refresh(); } }
    public void refresh() {
        if(!access())return; if(state.getValue().busy()) { pending=true; return; } load(null,null);
    }
    public void retry() { if(access() && !state.getValue().busy())load(currentTime,currentId); }
    public void loadMore() {
        if(!access())return; State held=state.getValue();
        if(!held.busy() && held.error==null && held.page!=null && held.page.nextId!=null)load(held.page.nextCreatedAt,held.page.nextId);
    }
    private void load(String time,String id) {
        State held=state.getValue(); currentTime=time;currentId=id; pending=false; long request=++generation;
        state.setValue(new State(true,false,false,held.page,held.counts,null,false));
        worker.execute(()->{
            if(cleared || request!=generation)return;
            try {
                actions.requireCurrent(); InquiryData.NotificationPage page=actions.page(time,id); InquiryData.UnreadCounts counts=actions.counts();
                publication.execute(()->{
                    if(cleared || request!=generation || !access())return;
                    state.setValue(new State(false,false,false,page,counts,null,true)); drain();
                });
            } catch(Exception error) { failure(request,error,held,false); }
        });
    }
    public void acknowledge(InquiryData.NotificationPage expected,String id) {
        if(!access())return; State held=state.getValue();
        if(held.busy() || !held.pageCurrent || expected==null || expected!=held.page)return;
        boolean exists=false;for(InquiryData.Notification row:expected.items)if(row.id.equals(id))exists=true;
        if(!exists)return;
        long request=++generation;state.setValue(new State(false,true,false,held.page,held.counts,null,true));
        worker.execute(()->{
            if(cleared || request!=generation)return;
            boolean reloading=false;
            try {
                actions.requireCurrent(); actions.acknowledge(expected,List.of(id));
                reloading=true;
                InquiryData.NotificationPage page=actions.page(currentTime,currentId); InquiryData.UnreadCounts counts=actions.counts();
                publication.execute(()->{
                    if(cleared || request!=generation || !access())return;
                    state.setValue(new State(false,false,false,page,counts,null,true));drain();
                });
            } catch(Exception error) { failure(request,error,held,!reloading); }
        });
    }
    public boolean canOpen(InquiryData.NotificationPage expected,String id) {
        if(!access())return false;State held=state.getValue();if(held.busy() || held.page!=expected || expected==null)return false;
        for(InquiryData.Notification row:expected.items)if(row.id.equals(id))return true;return false;
    }
    private void failure(long request,Exception error,State held,boolean pageCurrent) {
        publication.execute(()->{
            if(cleared || request!=generation || !access())return;
            String code=InquiryViewModel.code(error);if("FORBIDDEN".equals(code)) { clear(code); return; }
            state.setValue(new State(false,false,false,held.page,held.counts,code,pageCurrent));drain();
        });
    }
    private void drain() { if(pending) { pending=false;refresh(); } }
    public void checkCurrentAccount() { if(!cleared)access(); }
    private boolean access() {
        if(cleared || blocked)return false;if(!actions.allowed()) {clear("FORBIDDEN");return false;}
        try {actions.requireCurrent();return true;}catch(IllegalStateException changed){clear("STALE_ACCOUNT");return false;}
    }
    private void clear(String error) { blocked=true;++generation;pending=false;currentTime=null;currentId=null;state.setValue(new State(false,false,true,null,null,error,false)); }
    private static Actions production(GoServeApplication app) {
        AccountGateway candidate=null;
        ph.edu.tip.goserve.domain.SessionProfile profile=app.auth().profile();
        if(profile!=null && profile.ready())try {AccountGateway value=app.gateway();if(profile.id.equals(value.lease().accountId))candidate=value;}catch(IllegalStateException ignored){}
        AccountGateway gateway=candidate;InquiryRepository repository=gateway==null?null:new InquiryRepository(gateway);
        return new Actions() {
            public boolean allowed(){return repository!=null;}
            public void requireCurrent(){if(repository==null)throw new IllegalStateException("FORBIDDEN");repository.requireCurrent();}
            public InquiryData.NotificationPage page(String time,String id)throws Exception{return repository.notifications(time,id);}
            public InquiryData.UnreadCounts counts()throws Exception{return repository.unreadCounts(List.of());}
            public long acknowledge(InquiryData.NotificationPage page,List<String> ids)throws Exception{return repository.acknowledge(page,ids);}
        };
    }
    @Override protected void onCleared(){cleared=true;++generation;if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow();super.onCleared();}
}
