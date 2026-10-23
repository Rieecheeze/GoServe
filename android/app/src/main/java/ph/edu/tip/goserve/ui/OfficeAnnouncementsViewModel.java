package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.*;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.AccountGateway;
import ph.edu.tip.goserve.data.OfficeManagementRepository;
import ph.edu.tip.goserve.domain.SessionProfile;
import ph.edu.tip.goserve.data.OfficeManagementRepository.*;

public final class OfficeAnnouncementsViewModel extends AndroidViewModel {
    interface Actions{boolean allowed();void requireCurrent();Page<Announcement> page(String office,String time,String id)throws Exception;}
    public static final class State{
        public final boolean loading,blocked;public final String officeId,error,nextTime,nextId;public final List<Announcement> items;
        State(boolean loading,boolean blocked,String office,String error,List<Announcement> items,String time,String id){this.loading=loading;this.blocked=blocked;officeId=office;this.error=error;this.items=List.copyOf(items);nextTime=time;nextId=id;}
    }
    private final MutableLiveData<State> state=new MutableLiveData<>(new State(false,false,null,null,List.of(),null,null));
    private final Actions actions;private final Executor worker,publication;
    private volatile long generation;private volatile boolean cleared;private boolean pending,blocked;
    public OfficeAnnouncementsViewModel(Application app){this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post);}
    OfficeAnnouncementsViewModel(Application app,Actions actions,Executor worker,Executor publication){super(app);this.actions=actions;this.worker=worker;this.publication=publication;}
    public LiveData<State> state(){return state;}
    public void start(String office){
        if(!access())return;if(office==null || !office.matches("(?:OFF|DEP)-[A-Z0-9-]+")){clear("NOT_FOUND");return;}
        if(office.equals(state.getValue().officeId))return;
        ++generation;pending=false;state.setValue(new State(false,false,office,null,List.of(),null,null));load(null,null,false);
    }
    public void refresh(){if(!access() || state.getValue().officeId==null)return;if(state.getValue().loading){pending=true;return;}load(null,null,false);}
    public void more(){State s=state.getValue();if(access() && !s.loading && s.error==null && s.nextId!=null)load(s.nextTime,s.nextId,true);}
    private void load(String time,String id,boolean append){
        State held=state.getValue();String office=held.officeId;final long request=++generation;pending=false;
        state.setValue(new State(true,false,office,null,held.items,held.nextTime,held.nextId));
        worker.execute(()->{
            if(cleared || request!=generation)return;
            try{
                actions.requireCurrent();Page<Announcement> page=actions.page(office,time,id);
                for(Announcement row:page.items)if(!office.equals(row.officeId) || !row.active)throw new WorkflowException("FORBIDDEN");
                publication.execute(()->{
                    if(cleared || request!=generation || !access())return;
                    Map<String,Announcement> items=new LinkedHashMap<>();if(append)for(Announcement row:held.items)items.put(row.id,row);
                    for(Announcement row:page.items){Announcement previous=items.get(row.id);if(previous==null || previous.version<=row.version)items.put(row.id,row);}
                    state.setValue(new State(false,false,office,null,List.copyOf(items.values()),page.nextTime,page.nextId));
                    if(pending){pending=false;refresh();}
                });
            }catch(Exception failure){publication.execute(()->{
                if(cleared || request!=generation || !access())return;pending=false;
                String error=failure instanceof WorkflowException?((WorkflowException)failure).code:"NETWORK_ERROR";
                if("FORBIDDEN".equals(error)){clear(error);return;}
                state.setValue(new State(false,false,office,error,held.items,held.nextTime,held.nextId));
            });}
        });
    }
    public void checkCurrentAccount(){if(!cleared)access();}
    private boolean access(){if(cleared || blocked)return false;if(!actions.allowed()){clear("FORBIDDEN");return false;}try{actions.requireCurrent();return true;}catch(IllegalStateException changed){clear("STALE_ACCOUNT");return false;}}
    private void clear(String code){blocked=true;++generation;pending=false;state.setValue(new State(false,true,null,code,List.of(),null,null));}
    private static Actions production(GoServeApplication app){
        SessionProfile profile=app.auth().profile();AccountGateway captured=null;
        if(profile!=null && profile.ready())try{AccountGateway candidate=app.gateway();if(profile.id.equals(candidate.lease().accountId))captured=candidate;}catch(IllegalStateException changed){}
        AccountGateway gateway=captured;
        return new Actions(){public boolean allowed(){return gateway!=null;}public void requireCurrent(){if(gateway==null)throw new IllegalStateException("FORBIDDEN");gateway.requireCurrent();}public Page<Announcement> page(String office,String time,String id)throws Exception{return new OfficeManagementRepository(gateway,office).announcements(time,id,true);}};
    }
    @Override protected void onCleared(){cleared=true;++generation;pending=false;if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow();super.onCleared();}
}
