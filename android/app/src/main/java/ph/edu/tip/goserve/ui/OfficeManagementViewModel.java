package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.*;
import androidx.lifecycle.Observer;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.AccountGateway;
import ph.edu.tip.goserve.data.OfficeManagementRepository;
import ph.edu.tip.goserve.data.SupabaseClient;
import ph.edu.tip.goserve.data.OfficeManagementRepository.*;
import ph.edu.tip.goserve.domain.*;

public final class OfficeManagementViewModel extends AndroidViewModel {
    public enum Mode { BROWSE,EDIT,REVIEW }
    interface Actions {
        boolean allowed();String accountId();String officeId();void requireCurrent();Info office()throws Exception;
        Page<Announcement> announcements(String time,String id)throws Exception;Page<Catalog> services(String id)throws Exception;
        Announcement announcement(String id)throws Exception;Catalog service(String id)throws Exception;
        Command draft(State form,Command previous)throws Exception;Command restore(Kind kind,String id,long version,String key,String body)throws Exception;Result save(Command command)throws Exception;
    }
    public static final class State {
        public final Mode mode;public final Kind kind;public final boolean loading,saving,uncertain,blocked,stale,active,physical,digital,saved;
        public final String name,text,requirements,amount,currency,error,nextTime,nextId;public final OfficeStatus status;public final FeeDeclaration fee;
        public final Info office;public final Announcement announcement;public final Catalog service;public final List<Announcement> announcements;public final List<Catalog> services;public final Command command;
        State(OfficeManagementViewModel v){mode=v.mode;kind=v.kind;loading=v.loading;saving=v.saving;uncertain=v.uncertain;blocked=v.blocked;stale=v.stale;active=v.active;physical=v.physical;digital=v.digital;saved=v.savedResult;name=v.name;text=v.text;requirements=v.requirements;amount=v.amount;currency=v.currency;error=v.error;nextTime=v.nextTime;nextId=v.nextId;status=v.status;fee=v.fee;office=v.office;announcement=v.announcement;service=v.service;announcements=List.copyOf(v.announcements);services=List.copyOf(v.services);command=v.command;}
        public boolean busy(){return loading || saving;}
    }
    private final Actions actions;private final Executor worker,publication;private final SavedStateHandle savedState;
    private final String actor,assignedOffice;private final GoServeApplication app;
    private final Observer<Long> accountObserver=ignored->checkCurrentAccount();
    private final MutableLiveData<State> state=new MutableLiveData<>();
    private Mode mode=Mode.BROWSE;private Kind kind=Kind.OFFICE;
    private boolean loading,saving,uncertain,blocked,stale,active,physical,digital,savedResult,started,restored,attempted,pending,dirty;
    private volatile boolean cleared;private volatile long generation;
    private String name="",text="",requirements="",amount="",currency="",error,nextTime,nextId,restoredId;
    private OfficeStatus status;private FeeDeclaration fee=FeeDeclaration.PENDING_REVIEW;
    private Info office;private Announcement announcement;private Catalog service;private Command command;
    private List<Announcement> announcements=List.of();private List<Catalog> services=List.of();
    public OfficeManagementViewModel(Application app){this(app,new SavedStateHandle());}
    public OfficeManagementViewModel(Application app,SavedStateHandle saved){this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),new android.os.Handler(android.os.Looper.getMainLooper())::post,saved);}
    OfficeManagementViewModel(Application application,Actions actions,Executor worker,Executor publication,SavedStateHandle saved){
        super(application);this.actions=actions;this.worker=worker;this.publication=publication;savedState=saved;actor=actions.accountId();assignedOffice=actions.officeId();app=application instanceof GoServeApplication?(GoServeApplication)application:null;
        if(access()){restore();emit();}if(app!=null)app.accountInvalidations().observeForever(accountObserver);
    }
    static boolean permitted(SessionProfile p){return p!=null && p.ready() && p.role==Role.STAFF && p.officeId!=null && (!p.officeId.startsWith("DEP-") || "DEP-CCS".equals(p.officeId) && "secretary".equals(p.capability));}
    public LiveData<State> state(){return state;}
    public void start(Kind requested){
        if(requested==null || !access())return;
        if(started){if(kind==requested || mutationPending())return;resetForm();office=null;services=List.of();announcements=List.of();restored=false;}
        started=true;if(!restored){kind=requested;mode=kind==Kind.OFFICE?Mode.EDIT:Mode.BROWSE;}
        load(false,restored);
    }
    public void refresh(){
        if(!access() || !started || uncertain)return;
        if(loading || saving){pending=true;return;}
        if(mode==Mode.REVIEW || mode==Mode.EDIT && dirty && !stale){pending=true;return;}
        load(false,mode==Mode.EDIT);
    }
    public void more(){if(access() && mode==Mode.BROWSE && !loading && !saving && nextId!=null && error==null)load(true,false);}
    public void reloadCurrent(){if(editable() && !loading && mode!=Mode.REVIEW)load(false,mode==Mode.EDIT);}
    private void load(boolean append,boolean keepFields){
        loading=true;error=null;pending=false;final long request=++generation;final Kind section=kind;final Mode screen=mode;
        final String time=append?nextTime:null,id=append?nextId:null;
        final String target=service!=null?service.service.id:announcement!=null?announcement.id:restoredId;
        final List<Catalog> oldServices=services;final List<Announcement> oldAnnouncements=announcements;emit();
        worker.execute(()->{
            if(cleared || request!=generation)return;
            try{
                actions.requireCurrent();Info context=actions.office();
                Page<Catalog> catalog=section==Kind.SERVICE && screen==Mode.BROWSE?actions.services(id):null;
                Page<Announcement> notices=section==Kind.ANNOUNCEMENT && screen==Mode.BROWSE?actions.announcements(time,id):null;
                Catalog selected=section==Kind.SERVICE && screen!=Mode.BROWSE && target!=null?actions.service(target):null;
                Announcement notice=section==Kind.ANNOUNCEMENT && screen!=Mode.BROWSE && target!=null?actions.announcement(target):null;
                publication.execute(()->{
                    if(cleared || request!=generation || !access())return;
                    loading=false;office=context;service=selected;announcement=notice;
                    if(catalog!=null){Map<String,Catalog> rows=new LinkedHashMap<>();if(append)for(Catalog c:oldServices)rows.put(c.service.id,c);for(Catalog c:catalog.items){Catalog old=rows.get(c.service.id);if(old==null || old.service.version<=c.service.version)rows.put(c.service.id,c);}services=List.copyOf(rows.values());nextId=catalog.nextId;nextTime=null;}
                    if(notices!=null){Map<String,Announcement> rows=new LinkedHashMap<>();if(append)for(Announcement n:oldAnnouncements)rows.put(n.id,n);for(Announcement n:notices.items){Announcement old=rows.get(n.id);if(old==null || old.version<=n.version)rows.put(n.id,n);}announcements=List.copyOf(rows.values());nextId=notices.nextId;nextTime=notices.nextTime;}
                    if(screen!=Mode.BROWSE && (!keepFields || !dirty))copyCurrentForm();
                    if(command!=null && !attempted && command.id!=null){long current=kind==Kind.OFFICE?office.version:kind==Kind.SERVICE?service.service.version:announcement.version;if(current!=command.version){command=null;mode=Mode.EDIT;stale=true;error="STALE_VERSION";}}
                    else if(stale){stale=false;}
                    restored=false;restoredId=null;if(uncertain)error="NETWORK_ERROR";persist();emit();drain();
                });
            }catch(Exception failure){publication.execute(()->{if(cleared || request!=generation || !access())return;loading=false;pending=false;error=code(failure);if("FORBIDDEN".equals(error) && !uncertain){clear("FORBIDDEN");return;}emit();});}
        });
    }
    public void create(){if(!editable() || loading || kind==Kind.OFFICE)return;resetForm();dirty=true;mode=Mode.EDIT;persist();emit();}
    public void edit(String id){
        if(!editable() || loading || mode!=Mode.BROWSE)return;
        if(kind==Kind.SERVICE)for(Catalog c:services)if(c.service.id.equals(id)){resetForm();service=c;mode=Mode.EDIT;name=c.service.name;requirements=String.join("\n",c.service.requirementIds);fee=c.service.fee;amount=c.service.amount==null?"":c.service.amount.toPlainString();currency=c.service.currency==null?"":c.service.currency;active=c.service.active;physical=c.service.deliveryModes.contains("physical");digital=c.service.deliveryModes.contains("digital");persist();emit();return;}
        if(kind==Kind.ANNOUNCEMENT)for(Announcement n:announcements)if(n.id.equals(id)){resetForm();announcement=n;mode=Mode.EDIT;text=n.body;active=n.active;persist();emit();return;}
    }
    public void fields(String name,String text,String requirements,String amount,String currency,boolean physical,boolean digital,boolean active){
        if(!editable() || loading || mode!=Mode.EDIT)return;this.name=name;this.text=text;this.requirements=requirements;this.amount=amount;this.currency=currency;this.physical=physical;this.digital=digital;this.active=active;dirty=true;error=null;savedResult=false;persist();emit();
    }
    public void status(OfficeStatus value){if(editable() && !loading && mode==Mode.EDIT){status=value;dirty=true;error=null;savedResult=false;persist();emit();}}
    public void fee(FeeDeclaration value){if(editable() && !loading && mode==Mode.EDIT){fee=value;dirty=true;if(value!=FeeDeclaration.REQUIRED){amount="";currency="";}error=null;persist();emit();}}
    public void review(){
        if(!editable() || loading || stale || mode!=Mode.EDIT || office==null)return;
        try{command=actions.draft(new State(this),null);if(command==null || command.arguments().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>128*1024)throw new OfficeManagementRepository.WorkflowException("DRAFT_TOO_LARGE");mode=Mode.REVIEW;error=null;attempted=false;persist();emit();}
        catch(Exception invalid){command=null;error=code(invalid);emit();}
    }
    public void confirm(){if(access() && !loading && !saving && !uncertain && !stale && mode==Mode.REVIEW && command!=null)save();}
    public void retry(){if(access() && !loading && !saving){if(uncertain && command!=null)save();else if(stale)load(false,true);else if(error!=null)load(false,mode!=Mode.BROWSE);}}
    private void save(){
        Command sent=command;saving=true;attempted=true;error=null;persist();final long request=++generation;emit();
        worker.execute(()->{
            if(cleared || request!=generation)return;
            try{
                actions.requireCurrent();Result result=actions.save(sent);
                if(result==null || kind==Kind.OFFICE && result.office==null || kind==Kind.ANNOUNCEMENT && result.announcement==null || kind==Kind.SERVICE && result.service==null)throw new OfficeManagementRepository.WorkflowException("VALIDATION_FAILED");
                publication.execute(()->{
                    if(cleared || request!=generation || !access())return;
                    saving=false;attempted=false;uncertain=false;stale=false;command=null;error=null;savedResult=true;pending=false;
                    if(result.office!=null){office=result.office;copyOffice();}if(result.announcement!=null)announcement=result.announcement;if(result.service!=null)service=result.service;
                    mode=kind==Kind.OFFICE?Mode.EDIT:Mode.BROWSE;persist();emit();if(mode==Mode.BROWSE)load(false,false);
                });
            }catch(Exception failure){publication.execute(()->{
                if(cleared || request!=generation || !access())return;saving=false;error=code(failure);
                boolean definitive=failure instanceof SupabaseClient.ApiException && Set.of("STALE_VERSION","IDEMPOTENCY_CONFLICT","POLICY_BLOCKED","CONFIGURATION_REQUIRED","VALIDATION_FAILED").contains(error);
                uncertain=!definitive;
                if(definitive){command=null;attempted=false;stale=true;mode=Mode.EDIT;}persist();emit();
            });}
        });
    }
    public void cancel(){if(!editable() || loading)return;if(mode==Mode.REVIEW){mode=Mode.EDIT;command=null;error=null;persist();emit();}else if(kind!=Kind.OFFICE){resetForm();mode=Mode.BROWSE;load(false,false);}}
    public void discardLocalDraft(){if(!access() || saving)return;resetForm();mode=kind==Kind.OFFICE?Mode.EDIT:Mode.BROWSE;persist();load(false,false);}
    public void checkCurrentAccount(){if(!cleared)access();}
    public boolean mutationPending(){return saving || uncertain;}
    private boolean editable(){return access() && !saving && !uncertain;}
    private boolean access(){
        if(cleared || blocked)return false;if(!actions.allowed()){clear("FORBIDDEN");return false;}
        try{actions.requireCurrent();if(!actor.equals(actions.accountId()) || !Objects.equals(assignedOffice,actions.officeId()))throw new IllegalStateException("Account changed.");return true;}catch(IllegalStateException changed){clear("STALE_ACCOUNT");return false;}
    }
    private void clear(String reason){blocked=true;++generation;loading=false;saving=false;pending=false;resetForm();office=null;services=List.of();announcements=List.of();nextTime=nextId=null;error=reason;eraseSaved();emit();}
    private void resetForm(){name=text=requirements=amount=currency="";status=null;fee=FeeDeclaration.PENDING_REVIEW;active=physical=digital=false;announcement=null;service=null;command=null;uncertain=stale=attempted=savedResult=dirty=false;error=null;nextId=nextTime=null;restoredId=null;}
    private void copyOffice(){status=office.status;text=office.note;dirty=false;}
    private void copyCurrentForm(){if(kind==Kind.OFFICE)copyOffice();else if(announcement!=null){text=announcement.body;active=announcement.active;dirty=false;}else if(service!=null){name=service.service.name;requirements=String.join("\n",service.service.requirementIds);fee=service.service.fee;amount=service.service.amount==null?"":service.service.amount.toPlainString();currency=service.service.currency==null?"":service.service.currency;active=service.service.active;physical=service.service.deliveryModes.contains("physical");digital=service.service.deliveryModes.contains("digital");dirty=false;}}
    private void drain(){if(pending && !uncertain && (mode==Mode.BROWSE || mode==Mode.EDIT && !dirty)){pending=false;refresh();}}
    private void emit(){state.setValue(new State(this));}
    private void persist(){
        if(blocked || cleared)return;
        savedState.set("officeAdmin.actor",actor);savedState.set("officeAdmin.office",assignedOffice);savedState.set("officeAdmin.kind",kind.name());savedState.set("officeAdmin.mode",mode.name());
        savedState.set("officeAdmin.name",name);savedState.set("officeAdmin.text",text);savedState.set("officeAdmin.requirements",requirements);savedState.set("officeAdmin.amount",amount);savedState.set("officeAdmin.currency",currency);savedState.set("officeAdmin.status",status==null?null:status.name());savedState.set("officeAdmin.fee",fee.name());savedState.set("officeAdmin.active",active);savedState.set("officeAdmin.physical",physical);savedState.set("officeAdmin.digital",digital);savedState.set("officeAdmin.attempted",attempted);
        savedState.set("officeAdmin.selected",service!=null?service.service.id:announcement!=null?announcement.id:restoredId);savedState.set("officeAdmin.dirty",dirty);
        for(String key:List.of("commandId","version","key","body"))savedState.remove("officeAdmin."+key);
        if(command!=null){try{savedState.set("officeAdmin.commandId",command.id);savedState.set("officeAdmin.version",command.version);savedState.set("officeAdmin.key",command.key);savedState.set("officeAdmin.body",command.arguments().toString());}catch(Exception invalid){throw new IllegalStateException("Cannot retain the reviewed edit.");}}
    }
    private void restore(){
        if(!savedState.contains("officeAdmin.actor"))return;
        if(!actor.equals(savedState.get("officeAdmin.actor")) || !Objects.equals(assignedOffice,savedState.get("officeAdmin.office"))){eraseSaved();return;}
        try{
            kind=Kind.valueOf(savedState.get("officeAdmin.kind"));mode=Mode.valueOf(savedState.get("officeAdmin.mode"));name=value("name");text=value("text");requirements=value("requirements");amount=value("amount");currency=value("currency");String wire=savedState.get("officeAdmin.status");status=wire==null?null:OfficeStatus.valueOf(wire);fee=FeeDeclaration.valueOf(value("fee"));active=Boolean.TRUE.equals(savedState.get("officeAdmin.active"));physical=Boolean.TRUE.equals(savedState.get("officeAdmin.physical"));digital=Boolean.TRUE.equals(savedState.get("officeAdmin.digital"));attempted=Boolean.TRUE.equals(savedState.get("officeAdmin.attempted"));restoredId=savedState.get("officeAdmin.selected");String body=savedState.get("officeAdmin.body");
            dirty=savedState.contains("officeAdmin.dirty")?Boolean.TRUE.equals(savedState.get("officeAdmin.dirty")):mode!=Mode.BROWSE;
            if(body!=null){Long version=savedState.get("officeAdmin.version");command=actions.restore(kind,savedState.get("officeAdmin.commandId"),version,savedState.get("officeAdmin.key"),body);uncertain=attempted;mode=Mode.REVIEW;}restored=true;
        }catch(Exception invalid){resetForm();mode=Mode.BROWSE;eraseSaved();}
    }
    private String value(String key){String value=savedState.get("officeAdmin."+key);return value==null?"":value;}
    private void eraseSaved(){for(String key:List.copyOf(savedState.keys()))if(key.startsWith("officeAdmin."))savedState.remove(key);}
    private static String code(Exception error){if(error instanceof OfficeManagementRepository.WorkflowException)return ((OfficeManagementRepository.WorkflowException)error).code;if(error instanceof SupabaseClient.ApiException)return ((SupabaseClient.ApiException)error).code;return "NETWORK_ERROR";}
    private static Actions production(GoServeApplication app){
        SessionProfile profile=app.auth().profile();AccountGateway captured=null;if(permitted(profile))try{AccountGateway candidate=app.gateway();if(profile.id.equals(candidate.lease().accountId))captured=candidate;}catch(IllegalStateException changed){}
        final AccountGateway gateway=captured;final OfficeManagementRepository repository=gateway==null?null:new OfficeManagementRepository(gateway,profile.officeId);
        return new Actions(){
            public boolean allowed(){return repository!=null;}public String accountId(){return gateway==null?"":gateway.lease().accountId;}public String officeId(){return profile==null?null:profile.officeId;}public void requireCurrent(){if(repository==null)throw new IllegalStateException("FORBIDDEN");repository.requireCurrent();}
            public Info office()throws Exception{return repository.office();}public Page<Announcement> announcements(String time,String id)throws Exception{return repository.announcements(time,id,false);}public Page<Catalog> services(String id)throws Exception{return repository.services(id);}public Announcement announcement(String id)throws Exception{return repository.announcement(id);}public Catalog service(String id)throws Exception{return repository.service(id);}
            public Command draft(State s,Command previous)throws Exception{
                if(s.kind==Kind.OFFICE)return repository.officeDraft(s.office,s.status,s.text,previous);
                if(s.kind==Kind.ANNOUNCEMENT)return repository.announcementDraft(s.announcement,s.text,s.active,previous);
                List<String> modes=new ArrayList<>();if(s.physical)modes.add("physical");if(s.digital)modes.add("digital");List<String> required=new ArrayList<>();for(String line:s.requirements.split("\\R"))if(!line.isBlank() && !required.contains(line))required.add(line);
                return repository.serviceDraft(s.service,s.name,modes,required,s.fee,s.amount,s.currency,s.active,previous);
            }
            public Command restore(Kind kind,String id,long version,String key,String body)throws Exception{return repository.restore(kind,id,version,key,body);}public Result save(Command c)throws Exception{return repository.save(c);}
        };
    }
    @Override protected void onCleared(){cleared=true;++generation;if(app!=null)app.accountInvalidations().removeObserver(accountObserver);if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow();super.onCleared();}
}
