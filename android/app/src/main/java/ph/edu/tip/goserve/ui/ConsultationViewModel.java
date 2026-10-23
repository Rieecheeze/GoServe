package ph.edu.tip.goserve.ui;

import android.app.Application;
import android.os.Bundle;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.ConsultationRepository;
import ph.edu.tip.goserve.domain.Role;
import ph.edu.tip.goserve.domain.ConsultationData.*;
import ph.edu.tip.goserve.domain.SessionProfile;

public final class ConsultationViewModel extends AndroidViewModel {
    interface Actions {
        void requireCurrent(); void invalidate();
        default String accountId(){return "";}
        SlotPage slots(String office,String time,String id) throws Exception;
        AppointmentPage appointments(String after) throws Exception;
        Appointment appointment(String id) throws Exception;
        Slot slot(String office,String id) throws Exception;
        EventPage events(String id,String time,String after) throws Exception;
        Booking booking(Slot slot,Booking previous) throws Exception;
        Change change(Appointment original,Slot target,String reason,Change previous) throws Exception;
        SlotDraft draft(Slot original,String start,String end,int capacity,boolean active,SlotDraft previous) throws Exception;
        Appointment book(Booking command) throws Exception;
        Appointment change(Change command) throws Exception;
        Slot save(SlotDraft command) throws Exception;
    }
    public enum Screen { SLOTS, APPOINTMENTS, DETAIL, FORM, REVIEW }
    public static final class State {
        public Screen screen=Screen.SLOTS;
        public List<Slot> slots=List.of(); public List<Appointment> appointments=List.of(); public List<Event> events=List.of();
        public Slot selected; public Appointment appointment;
        public String office="OFF-GCC",error,notice,nextTime,nextId,review;
        public String start="",end="",capacity="",reason="";
        public boolean active=true,busy,blocked,uncertain,moving;
    }
    private final MutableLiveData<State> state=new MutableLiveData<>(new State());
    private final Actions actions;
    private final Executor worker,ui;
    private final SavedStateHandle saved;
    private long generation;
    private volatile boolean cleared;
    private boolean loaded,pendingRefresh,mutating;
    private boolean lastAppend;
    private Object command;
    private Bundle restoredIntent;
    private String restoredSlot,restoredAppointment;
    private boolean restorePending;
    public final boolean student,departmentAllowed; public final String staffOffice;
    public ConsultationViewModel(Application app) { this(app,new SavedStateHandle()); }
    public ConsultationViewModel(Application app,SavedStateHandle saved) {
        this(app,production((GoServeApplication)app),Executors.newSingleThreadExecutor(),
                new android.os.Handler(android.os.Looper.getMainLooper())::post,
                ((GoServeApplication)app).auth().profile(),saved);
    }
    private ConsultationViewModel(Application app,Actions actions,Executor worker,Executor ui,SessionProfile profile,SavedStateHandle saved) {
        this(app,actions,worker,ui,profile!=null && profile.role==Role.STUDENT,
                profile!=null && "DEP-CCS".equals(profile.departmentId),profile!=null && profile.role==Role.STAFF?profile.officeId:null,saved);
        if(!permitted(profile))block("FORBIDDEN");
    }
    ConsultationViewModel(Application app,Actions actions,Executor worker,Executor ui,boolean student,boolean departmentAllowed,String staffOffice) {
        this(app,actions,worker,ui,student,departmentAllowed,staffOffice,new SavedStateHandle());
    }
    ConsultationViewModel(Application app,Actions actions,Executor worker,Executor ui,boolean student,boolean departmentAllowed,String staffOffice,SavedStateHandle saved) {
        super(app);this.actions=actions;this.worker=worker;this.ui=ui;this.student=student;this.departmentAllowed=departmentAllowed;this.staffOffice=staffOffice;this.saved=saved;
        if(staffOffice!=null)state.getValue().office=staffOffice;
        restore();
    }
    public LiveData<State> state(){return state;}
    public static boolean permitted(SessionProfile profile){
        return profile!=null && profile.ready() && (profile.role==Role.STUDENT || profile.role==Role.STAFF &&
                ("OFF-GCC".equals(profile.officeId) && "guidance".equals(profile.capability)
                || "DEP-CCS".equals(profile.officeId) && "secretary".equals(profile.capability)));
    }
    private State s(){return state.getValue();}
    private void emit(){persist();state.setValue(s());}
    private boolean access(){
        if(cleared || s().blocked)return false;
        try{actions.requireCurrent();return true;}catch(IllegalStateException changed){block("STALE_ACCOUNT");return false;}
    }
    private boolean editable(){return access() && !mutating && !s().uncertain;}
    public void ensureLoaded(){if(!loaded && access()){loaded=true;if(restorePending)restoreCanonical();else refresh();}}
    public void refresh(){
        if(!access())return;
        if(restorePending){restoreCanonical();return;}
        if(s().uncertain)return;
        if(s().busy){pendingRefresh=true;return;}
        if(s().screen==Screen.FORM || s().screen==Screen.REVIEW && "save".equals(s().review))return;
        actions.invalidate();load(false);
    }
    public void more(){if(editable() && !s().busy && s().nextId!=null && s().error==null)load(true);}
    public void retry(){if(editable() && !s().busy && s().error!=null){if(restorePending)restoreCanonical();else load(lastAppend);}}
    public boolean mutationPending(){return mutating || s().uncertain;}
    public void appointments(){if(!editable())return;resetRead();s().moving=false;s().appointment=null;s().selected=null;s().review=null;s().events=List.of();s().screen=Screen.APPOINTMENTS;load(false);}
    public void office(String office){
        if(!editable())return;
        if(!"OFF-GCC".equals(office) && !"DEP-CCS".equals(office) || student && "DEP-CCS".equals(office) && !departmentAllowed
                || !student && !java.util.Objects.equals(staffOffice,office)){s().error="FORBIDDEN";emit();return;}
        resetRead();s().office=office;s().screen=Screen.SLOTS;s().selected=null;s().slots=List.of();load(false);
    }
    public void open(String id){if(!editable())return;resetRead();s().moving=false;s().screen=Screen.DETAIL;s().appointment=null;s().selected=null;loadDetail(id,false);}
    public void select(Slot slot){
        if(!editable() || s().busy || slot==null)return;
        Slot canonical=null;for(Slot item:s().slots)if(item.id.equals(slot.id) && item.version==slot.version)canonical=item;
        if(canonical==null)return;
        if(!student){editSlot(canonical);return;}
        if(!canonical.active || canonical.remaining()<1 || !java.time.Instant.parse(canonical.startAt).isAfter(java.time.Instant.now())){s().error="CAPACITY_FULL";emit();return;}
        if(s().moving && s().appointment!=null && canonical.id.equals(s().appointment.slotId)){s().error="INVALID_STATE";emit();return;}
        s().selected=canonical;s().review=s().moving?"move":"book";s().screen=Screen.REVIEW;s().error=null;command=null;emit();
    }
    public void beginMove(){
        if(!editable() || s().appointment==null || !"booked".equals(s().appointment.state) || s().selected==null)return;
        resetRead();s().moving=true;s().office=s().selected.officeId;s().screen=Screen.SLOTS;s().slots=List.of();load(false);
    }
    public void reviewCancel(){
        if(!editable() || s().busy || s().appointment==null || !"booked".equals(s().appointment.state))return;
        s().review="cancel";s().screen=Screen.REVIEW;s().error=null;command=null;emit();
    }
    public void newSlot(){if(!editable() || student)return;resetRead();s().selected=null;s().screen=Screen.FORM;s().start="";s().end="";s().capacity="";s().active=false;emit();}
    public void editSlot(Slot slot){
        if(!editable() || student || !java.util.Objects.equals(staffOffice,slot.officeId))return;
        resetRead();s().selected=slot;s().screen=Screen.FORM;s().start=slot.startAt;s().end=slot.endAt;s().capacity=Integer.toString(slot.capacity);s().active=slot.active;emit();
    }
    public void form(String start,String end,String capacity,boolean active){if(!editable() || s().screen!=Screen.FORM)return;s().start=start;s().end=end;s().capacity=capacity;s().active=active;persist();}
    public void reason(String reason){if(editable()){s().reason=reason;persist();}}
    public void reviewSave(){
        if(!editable() || student || s().screen!=Screen.FORM)return;
        try{
            int capacity=Integer.parseInt(s().capacity);
            command=actions.draft(s().selected,s().start,s().end,capacity,s().active,null);
            s().review="save";s().screen=Screen.REVIEW;s().error=null;emit();
        }catch(Exception invalid){s().error="VALIDATION_FAILED";emit();}
    }
    public void confirm(){
        if(!editable() || s().busy || s().screen!=Screen.REVIEW || s().error!=null)return;
        try{
            if("book".equals(s().review))command=actions.booking(s().selected,null);
            else if("cancel".equals(s().review) || "move".equals(s().review))command=actions.change(s().appointment,"move".equals(s().review)?s().selected:null,s().reason,null);
            else if(!"save".equals(s().review) || !(command instanceof SlotDraft))return;
            mutate();
        }catch(Exception error){s().error=code(error);emit();}
    }
    public void retryMutation(){if(access() && !s().busy && s().uncertain){if(restorePending)restoreCanonical();else if(command!=null)mutate();}}
    private void mutate(){
        final Object attempted=command;final long request=++generation;
        saveIntent(attempted);
        mutating=true;s().busy=true;s().error=null;s().notice=null;emit();
        worker.execute(()->{
            try{
                actions.requireCurrent();Object result=attempted instanceof Booking?actions.book((Booking)attempted):attempted instanceof Change?actions.change((Change)attempted):actions.save((SlotDraft)attempted);
                ui.execute(()->{
                    if(cleared || generation!=request || !access())return;
                    mutating=false;s().busy=false;s().uncertain=false;s().error=null;command=null;clearIntent();s().notice="confirmed";
                    pendingRefresh=false;s().moving=false;
                    if(result instanceof Appointment){s().appointment=(Appointment)result;s().screen=Screen.DETAIL;s().selected=null;s().events=List.of();loadDetail(((Appointment)result).id,false);}
                    else{s().selected=(Slot)result;s().screen=Screen.SLOTS;actions.invalidate();load(false);}
                });
            }catch(Exception error){ui.execute(()->{
                if(cleared || generation!=request || !access())return;
                mutating=false;s().busy=false;pendingRefresh=false;s().error=code(error);
                s().uncertain=!Set.of("CAPACITY_FULL","STALE_VERSION","POLICY_BLOCKED","CONFIGURATION_REQUIRED","INVALID_STATE","ALREADY_BOOKED").contains(s().error);
                if(!s().uncertain){command=null;clearIntent();}emit();
            });}
        });
    }
    public void back(){
        if(!editable())return;
        Screen previous=s().screen;resetRead();
        if(previous==Screen.REVIEW && "save".equals(s().review)){s().screen=Screen.FORM;emit();return;}
        if((previous==Screen.REVIEW || s().moving) && s().appointment!=null){s().moving=false;s().screen=Screen.DETAIL;loadDetail(s().appointment.id,false);return;}
        s().moving=false;s().screen=Screen.SLOTS;s().appointment=null;s().selected=null;load(false);
    }
    private void resetRead(){generation++;actions.invalidate();s().busy=false;pendingRefresh=false;s().error=null;s().notice=null;s().nextId=null;s().nextTime=null;command=null;clearIntent();}
    private void load(boolean append){
        lastAppend=append;
        final Screen screen=s().screen;
        if(screen==Screen.DETAIL || screen==Screen.REVIEW){
            if(s().appointment!=null)loadDetail(s().appointment.id,append);
            else if(s().selected!=null)loadSelected();
            return;
        }
        if(screen!=Screen.SLOTS && screen!=Screen.APPOINTMENTS)return;
        final String office=s().office,time=append?s().nextTime:null,id=append?s().nextId:null;
        final List<Slot> oldSlots=s().slots;final List<Appointment> oldAppointments=s().appointments;
        final long request=++generation;s().busy=true;s().error=null;pendingRefresh=false;emit();
        worker.execute(()->{
            try{
                actions.requireCurrent();Object page=screen==Screen.SLOTS?actions.slots(office,time,id):actions.appointments(id);
                ui.execute(()->{
                    if(cleared || generation!=request || !access())return;
                    if(page instanceof SlotPage){SlotPage values=(SlotPage)page;LinkedHashMap<String,Slot> rows=new LinkedHashMap<>();if(append)for(Slot value:oldSlots)rows.put(value.id,value);for(Slot value:values.items){Slot old=rows.get(value.id);if(old==null || old.version<=value.version)rows.put(value.id,value);}s().slots=List.copyOf(rows.values());s().nextTime=values.nextStartAt;s().nextId=values.nextId;}
                    else{AppointmentPage values=(AppointmentPage)page;LinkedHashMap<String,Appointment> rows=new LinkedHashMap<>();if(append)for(Appointment value:oldAppointments)rows.put(value.id,value);for(Appointment value:values.items){Appointment old=rows.get(value.id);if(old==null || old.version<=value.version)rows.put(value.id,value);}s().appointments=List.copyOf(rows.values());s().nextTime=null;s().nextId=values.nextId;}
                    finishRead();
                });
            }catch(Exception error){readFailed(request,error);}
        });
    }
    private Slot slotFor(Appointment appointment)throws Exception{
        if(!student)return actions.slot(staffOffice,appointment.slotId);
        try{return actions.slot("OFF-GCC",appointment.slotId);}
        catch(ConsultationRepository.WorkflowException error){if(!"NOT_FOUND".equals(error.code) || !departmentAllowed)throw error;return actions.slot("DEP-CCS",appointment.slotId);}
    }
    private void loadDetail(String id,boolean append){
        final long request=++generation;final List<Event> old=s().events;final String time=append?s().nextTime:null,after=append?s().nextId:null;
        final Slot target=s().screen==Screen.REVIEW && "move".equals(s().review)?s().selected:null;
        s().busy=true;s().error=null;pendingRefresh=false;emit();
        worker.execute(()->{
            try{
                actions.requireCurrent();Appointment appointment=actions.appointment(id);Slot slot=slotFor(appointment);EventPage events=actions.events(id,time,after);
                Slot refreshedTarget=target==null?null:actions.slot(target.officeId,target.id);
                ui.execute(()->{
                    if(cleared || generation!=request || !access())return;
                    s().appointment=appointment;
                    s().selected=refreshedTarget==null?slot:refreshedTarget;
                    s().office=slot.officeId;LinkedHashMap<String,Event> rows=new LinkedHashMap<>();if(append)for(Event value:old)rows.put(value.id,value);for(Event value:events.items)rows.put(value.id,value);
                    s().events=List.copyOf(rows.values());s().nextTime=events.nextCreatedAt;s().nextId=events.nextId;finishRead();
                });
            }catch(Exception error){readFailed(request,error);}
        });
    }
    private void loadSelected(){
        final Slot selected=s().selected;final long request=++generation;s().busy=true;s().error=null;emit();
        worker.execute(()->{try{Slot value=actions.slot(selected.officeId,selected.id);ui.execute(()->{if(cleared || generation!=request || !access())return;s().selected=value;finishRead();});}catch(Exception error){readFailed(request,error);}});
    }
    private void finishRead(){s().busy=false;s().error=null;command=null;emit();if(pendingRefresh){pendingRefresh=false;refresh();}}
    private void readFailed(long request,Exception error){ui.execute(()->{if(cleared || generation!=request || !access())return;if("FORBIDDEN".equals(code(error))){block("FORBIDDEN");return;}s().busy=false;pendingRefresh=false;s().error=code(error);emit();});}
    private static String code(Exception error){return error instanceof ConsultationRepository.WorkflowException?((ConsultationRepository.WorkflowException)error).code:"NETWORK_ERROR";}
    public void checkCurrentAccount(){access();}
    private void block(String code){generation++;actions.invalidate();command=null;clearIntent();eraseSaved();pendingRefresh=false;mutating=false;State empty=new State();empty.blocked=true;empty.error=code;state.setValue(empty);}
    private void restore(){
        if(!saved.contains("consult.account"))return;
        try{
            actions.requireCurrent();
            if(!actions.accountId().equals(saved.get("consult.account"))){eraseSaved();return;}
            String office=saved.get("consult.office");
            if(!"OFF-GCC".equals(office) && !"DEP-CCS".equals(office) || student && "DEP-CCS".equals(office) && !departmentAllowed
                    || !student && !java.util.Objects.equals(staffOffice,office)){eraseSaved();return;}
            s().screen=Screen.valueOf(saved.get("consult.screen"));s().office=office;s().review=saved.get("consult.review");
            s().start=value("consult.start");s().end=value("consult.end");s().capacity=value("consult.capacity");s().reason=value("consult.reason");
            s().active=Boolean.TRUE.equals(saved.get("consult.active"));s().moving=Boolean.TRUE.equals(saved.get("consult.moving"));
            restoredSlot=saved.get("consult.slot");restoredAppointment=saved.get("consult.appointment");restoredIntent=saved.get("consult.intent");
            restorePending=restoredSlot!=null || restoredAppointment!=null || restoredIntent!=null || s().screen==Screen.REVIEW && "save".equals(s().review);
            s().uncertain=restoredIntent!=null;
            if(s().uncertain)s().error="NETWORK_ERROR";
        }catch(RuntimeException invalid){eraseSaved();State empty=new State();if(staffOffice!=null)empty.office=staffOffice;state.setValue(empty);restoredIntent=null;restorePending=false;}
    }
    private String value(String key){String value=saved.get(key);return value==null?"":value;}
    private void persist(){
        if(s().blocked || actions.accountId().isEmpty())return;
        saved.set("consult.account",actions.accountId());saved.set("consult.screen",s().screen.name());saved.set("consult.office",s().office);saved.set("consult.review",s().review);
        saved.set("consult.start",s().start);saved.set("consult.end",s().end);saved.set("consult.capacity",s().capacity);saved.set("consult.reason",s().reason);saved.set("consult.active",s().active);saved.set("consult.moving",s().moving);
        saved.set("consult.slot",s().selected==null?restorePending?restoredSlot:null:s().selected.id);
        saved.set("consult.appointment",s().appointment==null?restorePending?restoredAppointment:null:s().appointment.id);
    }
    private void eraseSaved(){for(String key:List.copyOf(saved.keys()))if(key.startsWith("consult."))saved.remove(key);}
    private void clearIntent(){saved.remove("consult.intent");restoredIntent=null;restorePending=false;restoredSlot=null;restoredAppointment=null;}
    private void saveIntent(Object attempted){
        Bundle intent=new Bundle();
        if(attempted instanceof Booking){Booking booking=(Booking)attempted;intent.putString("kind","book");intent.putString("key",booking.operationKey);intent.putString("slot",booking.slot.id);intent.putLong("slotVersion",booking.slot.version);}
        else if(attempted instanceof Change){Change change=(Change)attempted;intent.putString("kind","change");intent.putString("key",change.operationKey);intent.putString("appointment",change.original.id);intent.putLong("appointmentVersion",change.original.version);intent.putString("originalSlot",change.original.slotId);intent.putString("reason",change.reason);if(change.target!=null){intent.putString("slot",change.target.id);intent.putLong("slotVersion",change.target.version);}}
        else if(attempted instanceof SlotDraft){SlotDraft draft=(SlotDraft)attempted;intent.putString("kind","save");intent.putString("key",draft.operationKey);intent.putString("slot",draft.id);intent.putLong("slotVersion",draft.expectedVersion);intent.putString("office",draft.officeId);intent.putString("owner",draft.ownerId);intent.putString("start",draft.startAt);intent.putString("end",draft.endAt);intent.putInt("capacity",draft.capacity);intent.putBoolean("active",draft.active);}
        else return;
        saved.set("consult.intent",intent);
    }
    private void restoreCanonical(){
        if(!access() || s().busy)return;
        final long request=++generation;final String office=s().office,slotId=restoredSlot,appointmentId=restoredAppointment;
        final Bundle intent=restoredIntent==null?null:new Bundle(restoredIntent);s().busy=true;emit();
        worker.execute(()->{
            try{
                actions.requireCurrent();Appointment appointment=appointmentId==null?null:actions.appointment(appointmentId);
                Slot selected=slotId==null?null:actions.slot(office,slotId);
                if(selected==null && appointment!=null)selected=slotFor(appointment);
                EventPage events=appointment==null?null:actions.events(appointment.id,null,null);
                SlotPage replacements=s().moving && s().screen==Screen.SLOTS?actions.slots(office,null,null):null;
                Object retry=null;
                if(intent!=null){
                    String kind=intent.getString("kind"),key=intent.getString("key"),account=actions.accountId();
                    if("book".equals(kind))retry=new Booking(account,versioned(selected,intent.getLong("slotVersion")),key);
                    else if("change".equals(kind)){
                        if(appointment==null || !appointment.id.equals(intent.getString("appointment")))throw new ConsultationRepository.WorkflowException("VALIDATION_FAILED");
                        Appointment original=new Appointment(appointment.id,appointment.studentId,intent.getString("originalSlot"),"booked",intent.getLong("appointmentVersion"),appointment.createdAt);
                        retry=new Change(account,original,intent.getString("slot")==null?null:versioned(selected,intent.getLong("slotVersion")),intent.getString("reason"),key);
                    }else if("save".equals(kind))retry=new SlotDraft(account,intent.getString("slot"),intent.getLong("slotVersion"),intent.getString("office"),intent.getString("owner"),intent.getString("start"),intent.getString("end"),intent.getInt("capacity"),intent.getBoolean("active"),key);
                    else throw new ConsultationRepository.WorkflowException("VALIDATION_FAILED");
                }
                if(intent==null && s().screen==Screen.REVIEW && "save".equals(s().review))retry=actions.draft(selected,s().start,s().end,Integer.parseInt(s().capacity),s().active,null);
                final Slot current=selected;final Object replay=retry;
                ui.execute(()->{
                    if(cleared || generation!=request || !access())return;
                    restorePending=false;restoredSlot=null;restoredAppointment=null;s().busy=false;s().selected=current;s().appointment=appointment;s().events=events==null?List.of():events.items;command=replay;
                    if(replacements!=null){s().slots=replacements.items;s().nextTime=replacements.nextStartAt;s().nextId=replacements.nextId;}
                    s().uncertain=intent!=null;s().error=s().uncertain?"NETWORK_ERROR":null;emit();
                });
            }catch(Exception error){ui.execute(()->{if(cleared || generation!=request || !access())return;if("FORBIDDEN".equals(code(error))){block("FORBIDDEN");return;}s().busy=false;s().error=code(error);emit();});}
        });
    }
    private static Slot versioned(Slot canonical,long version)throws ConsultationRepository.WorkflowException{
        if(canonical==null || version<1)throw new ConsultationRepository.WorkflowException("VALIDATION_FAILED");
        return new Slot(canonical.id,canonical.officeId,canonical.ownerId,canonical.startAt,canonical.endAt,canonical.capacity,canonical.reserved,canonical.active,version,canonical.isDemo);
    }
    private static Actions production(GoServeApplication app){
        final SessionProfile profile=app.auth().profile();ph.edu.tip.goserve.data.AccountGateway captured=null;
        if(permitted(profile))try{ph.edu.tip.goserve.data.AccountGateway candidate=app.gateway();if(profile.id.equals(candidate.lease().accountId))captured=candidate;}catch(IllegalStateException changed){}
        final ConsultationRepository repository=captured==null?null:new ConsultationRepository(captured);final boolean allowed=repository!=null;
        return new Actions(){
            public String accountId(){return profile==null?"":profile.id;}
            public void requireCurrent(){if(!allowed)throw new IllegalStateException("FORBIDDEN");repository.requireCurrent();}
            public void invalidate(){if(repository!=null)repository.invalidate();}
            public SlotPage slots(String office,String time,String id)throws Exception{return repository.slots(office,null,profile.role==Role.STUDENT,time,id);}
            public AppointmentPage appointments(String after)throws Exception{return repository.appointments(profile.role==Role.STUDENT?null:profile.officeId,null,after);}
            public Appointment appointment(String id)throws Exception{Appointment value=repository.appointment(id);if(profile.role==Role.STUDENT && !profile.id.equals(value.studentId))throw new ConsultationRepository.WorkflowException("FORBIDDEN");return value;}
            public Slot slot(String office,String id)throws Exception{return repository.slot(office,id);}
            public EventPage events(String id,String time,String after)throws Exception{return repository.events(id,time,after);}
            public Booking booking(Slot slot,Booking previous)throws Exception{return repository.booking(slot,previous);}
            public Change change(Appointment original,Slot target,String reason,Change previous)throws Exception{return repository.change(original,target,reason,previous);}
            public SlotDraft draft(Slot original,String start,String end,int capacity,boolean active,SlotDraft previous)throws Exception{return repository.slotDraft(original==null?null:original.id,original==null?0:original.version,profile.officeId,original==null?profile.id:original.ownerId,start,end,capacity,active,previous);}
            public Appointment book(Booking command)throws Exception{return repository.book(command);}
            public Appointment change(Change command)throws Exception{return repository.changeAppointment(command);}
            public Slot save(SlotDraft command)throws Exception{return repository.saveSlot(command);}
        };
    }
    @Override protected void onCleared(){cleared=true;block("STALE_ACCOUNT");if(worker instanceof ExecutorService)((ExecutorService)worker).shutdownNow();super.onCleared();}
}
