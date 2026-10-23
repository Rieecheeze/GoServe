package ph.edu.tip.goserve.data;

import android.net.Uri;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import ph.edu.tip.goserve.domain.ConsultationData;

public final class ConsultationRepository {
    interface Transport {
        String accountId();
        void requireCurrent();
        JSONArray get(String path) throws Exception;
        JSONObject rpc(String name,JSONObject args) throws Exception;
    }
    public static final class WorkflowException extends Exception {
        public final String code;
        public WorkflowException(String code) { super(code); this.code=code; }
    }
    private static final String SLOT_COLUMNS="id,office_id,schedule_owner_id,start_at,end_at,capacity,reserved,active,version,is_demo";
    private static final String APPOINTMENT_COLUMNS="id,student_id,slot_id,state,version,created_at";
    private static final String EVENT_COLUMNS="id,appointment_id,actor_id,action,old_slot_id,new_slot_id,reason,created_at";
    private final Transport transport;
    private final String accountId;
    private final AtomicLong slotLoads=new AtomicLong(), appointmentLoads=new AtomicLong(), eventLoads=new AtomicLong(), mutationLoads=new AtomicLong();
    private final AtomicLong slotDetailLoads=new AtomicLong(), appointmentDetailLoads=new AtomicLong();
    public ConsultationRepository(AccountGateway gateway) {
        this(new Transport() {
            public String accountId() { return gateway.lease().accountId; }
            public void requireCurrent() { gateway.requireCurrent(); }
            public JSONArray get(String path) throws Exception { return gateway.get(path); }
            public JSONObject rpc(String name,JSONObject args) throws Exception { return gateway.rpc(name,args); }
        });
    }
    ConsultationRepository(Transport transport) { this.transport=transport; this.accountId=transport.accountId(); }
    public void requireCurrent() {
        transport.requireCurrent();
        if(!Objects.equals(accountId,transport.accountId()))throw new IllegalStateException("STALE_ACCOUNT");
    }
    public void invalidate() { invalidateReads(); mutationLoads.incrementAndGet(); }
    private void invalidateReads() { slotLoads.incrementAndGet(); appointmentLoads.incrementAndGet(); eventLoads.incrementAndGet(); slotDetailLoads.incrementAndGet(); appointmentDetailLoads.incrementAndGet(); }

    public ConsultationData.Slot slot(String officeId,String slotId) throws Exception {
        requireCurrent(); office(officeId); id(slotId);
        long load=slotDetailLoads.incrementAndGet();
        JSONArray rows=read(table("consultation_slots",SLOT_COLUMNS,2)
                .appendQueryParameter("office_id","eq."+officeId).appendQueryParameter("id","eq."+slotId));
        check(slotDetailLoads,load);
        if(rows.length()==0)throw failure("NOT_FOUND");
        if(rows.length()!=1)throw failure("VALIDATION_FAILED");
        ConsultationData.Slot value=parseSlot(object(rows,0));
        if(!slotId.equals(value.id) || !officeId.equals(value.officeId))throw failure("FORBIDDEN");
        check(slotDetailLoads,load);
        return value;
    }
    public ConsultationData.Appointment appointment(String appointmentId) throws Exception {
        requireCurrent(); id(appointmentId);
        long load=appointmentDetailLoads.incrementAndGet();
        JSONArray rows=read(table("appointments",APPOINTMENT_COLUMNS,2).appendQueryParameter("id","eq."+appointmentId));
        check(appointmentDetailLoads,load);
        if(rows.length()==0)throw failure("NOT_FOUND");
        if(rows.length()!=1)throw failure("VALIDATION_FAILED");
        ConsultationData.Appointment value=parseAppointment(object(rows,0));
        if(!appointmentId.equals(value.id))throw failure("FORBIDDEN");
        check(appointmentDetailLoads,load);
        return value;
    }

    public ConsultationData.SlotPage slots(String officeId,String ownerId,boolean activeOnly,String afterStartAt,String afterId) throws Exception {
        requireCurrent(); office(officeId); optionalId(ownerId); cursor(afterStartAt,afterId);
        long load=slotLoads.incrementAndGet();
        Uri.Builder query=table("consultation_slots",SLOT_COLUMNS).appendQueryParameter("office_id","eq."+officeId)
                .appendQueryParameter("order","start_at.asc,id.asc");
        if(ownerId!=null)query.appendQueryParameter("schedule_owner_id","eq."+ownerId);
        if(activeOnly)query.appendQueryParameter("active","eq.true");
        afterTuple(query,"start_at",afterStartAt,afterId);
        JSONArray rows=read(query); LinkedHashMap<String,ConsultationData.Slot> items=new LinkedHashMap<>();
        ConsultationData.Slot last=null;
        for(int n=0;n<rows.length();n++) {
            ConsultationData.Slot value=parseSlot(object(rows,n));
            if(!officeId.equals(value.officeId) || ownerId!=null && !ownerId.equals(value.ownerId))throw failure("FORBIDDEN");
            if(activeOnly && !value.active)throw failure("VALIDATION_FAILED");
            if(afterId!=null && compareTuple(value.startAt,value.id,afterStartAt,afterId)<=0)throw failure("STALE_RESPONSE");
            ConsultationData.Slot existing=items.get(value.id);
            if(existing==null || value.version>existing.version)items.put(value.id,value);
            last=value;
        }
        check(slotLoads,load);
        return new ConsultationData.SlotPage(new ArrayList<>(items.values()),rows.length()==50?last.startAt:null,rows.length()==50?last.id:null);
    }

    public ConsultationData.AppointmentPage appointments(String officeId,String ownerId,String afterId) throws Exception {
        requireCurrent(); optionalId(afterId); optionalId(ownerId);
        if(officeId==null && ownerId!=null)throw failure("VALIDATION_FAILED");
        if(officeId!=null)office(officeId);
        long load=appointmentLoads.incrementAndGet();
        String columns=APPOINTMENT_COLUMNS+(officeId==null?"":",consultation_slots!inner(office_id,schedule_owner_id)");
        Uri.Builder query=table("appointments",columns).appendQueryParameter("order","id.asc");
        if(officeId==null)query.appendQueryParameter("student_id","eq."+accountId);
        else {
            query.appendQueryParameter("consultation_slots.office_id","eq."+officeId);
            if(ownerId!=null)query.appendQueryParameter("consultation_slots.schedule_owner_id","eq."+ownerId);
        }
        if(afterId!=null)query.appendQueryParameter("id","gt."+afterId);
        JSONArray rows=read(query); LinkedHashMap<String,ConsultationData.Appointment> items=new LinkedHashMap<>();
        String last=null;
        for(int n=0;n<rows.length();n++) {
            JSONObject row=object(rows,n); ConsultationData.Appointment value=parseAppointment(row);
            if(officeId==null) { if(!accountId.equals(value.studentId))throw failure("FORBIDDEN"); }
            else {
                JSONObject parent;
                try { parent=row.getJSONObject("consultation_slots"); }
                catch(JSONException error) { throw failure("VALIDATION_FAILED"); }
                String parentOffice=string(parent,"office_id"), parentOwner=string(parent,"schedule_owner_id"); office(parentOffice); id(parentOwner);
                if(!officeId.equals(parentOffice) || ownerId!=null && !ownerId.equals(parentOwner))throw failure("FORBIDDEN");
            }
            if(afterId!=null && value.id.compareToIgnoreCase(afterId)<=0)throw failure("STALE_RESPONSE");
            ConsultationData.Appointment existing=items.get(value.id);
            if(existing==null || value.version>existing.version)items.put(value.id,value);
            last=value.id;
        }
        check(appointmentLoads,load);
        return new ConsultationData.AppointmentPage(new ArrayList<>(items.values()),rows.length()==50?last:null);
    }

    public ConsultationData.EventPage events(String appointmentId,String afterCreatedAt,String afterId) throws Exception {
        requireCurrent(); id(appointmentId); cursor(afterCreatedAt,afterId); long load=eventLoads.incrementAndGet();
        Uri.Builder query=table("appointment_events",EVENT_COLUMNS).appendQueryParameter("appointment_id","eq."+appointmentId)
                .appendQueryParameter("order","created_at.asc,id.asc");
        afterTuple(query,"created_at",afterCreatedAt,afterId);
        JSONArray rows=read(query); LinkedHashMap<String,ConsultationData.Event> items=new LinkedHashMap<>(); ConsultationData.Event last=null;
        for(int n=0;n<rows.length();n++) {
            ConsultationData.Event value=parseEvent(object(rows,n));
            if(!appointmentId.equals(value.appointmentId))throw failure("FORBIDDEN");
            if(afterId!=null && compareTuple(value.createdAt,value.id,afterCreatedAt,afterId)<=0)throw failure("STALE_RESPONSE");
            ConsultationData.Event prior=items.get(value.id);
            if(prior!=null && (!prior.actorId.equals(value.actorId) || !prior.action.equals(value.action)
                    || !Objects.equals(prior.oldSlotId,value.oldSlotId) || !Objects.equals(prior.newSlotId,value.newSlotId)
                    || !Objects.equals(prior.reason,value.reason) || !prior.createdAt.equals(value.createdAt)))throw failure("VALIDATION_FAILED");
            items.put(value.id,value); last=value;
        }
        check(eventLoads,load);
        return new ConsultationData.EventPage(new ArrayList<>(items.values()),rows.length()==50?last.createdAt:null,rows.length()==50?last.id:null);
    }

    public ConsultationData.Booking booking(ConsultationData.Slot slot,ConsultationData.Booking previous) throws Exception {
        requireCurrent(); validate(slot); String key=UUID.randomUUID().toString();
        if(previous!=null) {
            account(previous.accountId);
            if(previous.slot!=null && slot.id.equals(previous.slot.id) && slot.version==previous.slot.version) { id(previous.operationKey); key=previous.operationKey; }
        }
        requireCurrent(); return new ConsultationData.Booking(accountId,slot,key);
    }

    public ConsultationData.Appointment book(ConsultationData.Booking draft) throws Exception {
        requireCurrent(); if(draft==null)throw failure("VALIDATION_FAILED"); account(draft.accountId); validate(draft.slot); id(draft.operationKey);
        long load=mutationLoads.incrementAndGet();
        JSONObject args=new JSONObject().put("p_slot_id",draft.slot.id).put("p_expected_version",draft.slot.version).put("p_idempotency_key",draft.operationKey);
        ConsultationData.Appointment confirmed=parseAppointment(call("book_consultation",args));
        if(!accountId.equals(confirmed.studentId))throw failure("FORBIDDEN");
        if(!draft.slot.id.equals(confirmed.slotId))throw failure("VALIDATION_FAILED");
        ConsultationData.Appointment current=reloadAppointment(confirmed);
        check(mutationLoads,load); invalidateReads(); return current;
    }

    public ConsultationData.Change change(ConsultationData.Appointment original,ConsultationData.Slot target,String reason,ConsultationData.Change previous) throws Exception {
        requireCurrent(); validateChange(original,target); String key=UUID.randomUUID().toString();
        if(previous!=null) {
            account(previous.accountId);
            if(previous.original!=null && original.id.equals(previous.original.id) && original.version==previous.original.version
                    && Objects.equals(reason,previous.reason) && sameTarget(target,previous.target)) { id(previous.operationKey); key=previous.operationKey; }
        }
        requireCurrent(); return new ConsultationData.Change(accountId,original,target,reason,key);
    }

    public ConsultationData.Appointment changeAppointment(ConsultationData.Change draft) throws Exception {
        requireCurrent(); if(draft==null)throw failure("VALIDATION_FAILED"); account(draft.accountId); validateChange(draft.original,draft.target); id(draft.operationKey);
        long load=mutationLoads.incrementAndGet();
        JSONObject args=new JSONObject().put("p_appointment_id",draft.original.id).put("p_expected_version",draft.original.version)
                .put("p_target_slot_id",draft.target==null?JSONObject.NULL:draft.target.id)
                .put("p_target_version",draft.target==null?JSONObject.NULL:draft.target.version)
                .put("p_reason",draft.reason==null?JSONObject.NULL:draft.reason).put("p_idempotency_key",draft.operationKey);
        ConsultationData.Appointment confirmed=parseAppointment(call("change_appointment",args));
        if(!draft.original.id.equals(confirmed.id) || !draft.original.studentId.equals(confirmed.studentId))throw failure("FORBIDDEN");
        if(confirmed.version<=draft.original.version || (draft.target==null
                ? !"cancelled".equals(confirmed.state) || !draft.original.slotId.equals(confirmed.slotId)
                : !"booked".equals(confirmed.state) || !draft.target.id.equals(confirmed.slotId)))throw failure("VALIDATION_FAILED");
        ConsultationData.Appointment current=reloadAppointment(confirmed);
        check(mutationLoads,load); invalidateReads(); return current;
    }

    public ConsultationData.SlotDraft slotDraft(String id,long expectedVersion,String officeId,String ownerId,String startAt,String endAt,int capacity,boolean active,ConsultationData.SlotDraft previous) throws Exception {
        requireCurrent(); String key=UUID.randomUUID().toString();
        ConsultationData.SlotDraft candidate=new ConsultationData.SlotDraft(accountId,id,expectedVersion,officeId,ownerId,startAt,endAt,capacity,active,key); validate(candidate);
        if(previous!=null) {
            account(previous.accountId);
            if(Objects.equals(id,previous.id) && expectedVersion==previous.expectedVersion && officeId.equals(previous.officeId)
                    && ownerId.equals(previous.ownerId) && startAt.equals(previous.startAt) && endAt.equals(previous.endAt)
                    && capacity==previous.capacity && active==previous.active) { id(previous.operationKey); key=previous.operationKey; }
        }
        requireCurrent(); return new ConsultationData.SlotDraft(accountId,id,expectedVersion,officeId,ownerId,startAt,endAt,capacity,active,key);
    }

    public ConsultationData.Slot saveSlot(ConsultationData.SlotDraft draft) throws Exception {
        requireCurrent(); validate(draft); account(draft.accountId); id(draft.operationKey); long load=mutationLoads.incrementAndGet();
        JSONObject args=new JSONObject().put("p_id",draft.id==null?JSONObject.NULL:draft.id).put("p_expected_version",draft.expectedVersion)
                .put("p_office_id",draft.officeId).put("p_owner_id",draft.ownerId).put("p_start_at",draft.startAt).put("p_end_at",draft.endAt)
                .put("p_capacity",draft.capacity).put("p_active",draft.active).put("p_idempotency_key",draft.operationKey);
        ConsultationData.Slot confirmed=parseSlot(call("save_slot",args));
        if(draft.id!=null && !draft.id.equals(confirmed.id) || !draft.officeId.equals(confirmed.officeId) || !draft.ownerId.equals(confirmed.ownerId))throw failure("FORBIDDEN");
        JSONArray rows=read(table("consultation_slots",SLOT_COLUMNS).appendQueryParameter("id","eq."+confirmed.id)
                .appendQueryParameter("office_id","eq."+draft.officeId).appendQueryParameter("schedule_owner_id","eq."+draft.ownerId));
        if(rows.length()!=1)throw failure("NOT_FOUND");
        ConsultationData.Slot current=parseSlot(object(rows,0));
        if(!confirmed.id.equals(current.id) || !draft.officeId.equals(current.officeId) || !draft.ownerId.equals(current.ownerId))throw failure("FORBIDDEN");
        if(current.version<confirmed.version)throw failure("STALE_RESPONSE");
        check(mutationLoads,load); invalidateReads(); return current;
    }

    private ConsultationData.Appointment reloadAppointment(ConsultationData.Appointment confirmed) throws Exception {
        JSONArray rows=read(table("appointments",APPOINTMENT_COLUMNS).appendQueryParameter("id","eq."+confirmed.id)
                .appendQueryParameter("student_id","eq."+confirmed.studentId));
        if(rows.length()!=1)throw failure("NOT_FOUND");
        ConsultationData.Appointment current=parseAppointment(object(rows,0));
        if(!confirmed.id.equals(current.id) || !confirmed.studentId.equals(current.studentId))throw failure("FORBIDDEN");
        if(current.version<confirmed.version)throw failure("STALE_RESPONSE");
        return current;
    }

    private JSONArray read(Uri.Builder query) throws Exception {
        requireCurrent();
        try {
            JSONArray rows=transport.get(query.build().toString()); requireCurrent();
            if(rows==null || rows.length()>50)throw failure("VALIDATION_FAILED"); return rows;
        } catch(SupabaseClient.ApiException error) { throw failure(error.code); }
    }
    private JSONObject call(String name,JSONObject args) throws Exception {
        requireCurrent();
        try { JSONObject value=transport.rpc(name,args); requireCurrent(); if(value==null)throw failure("VALIDATION_FAILED"); return value; }
        catch(SupabaseClient.ApiException error) { throw failure(error.code); }
    }
    private void check(AtomicLong generation,long expected) throws WorkflowException {
        requireCurrent(); if(generation.get()!=expected)throw failure("STALE_RESPONSE");
    }
    private static Uri.Builder table(String name,String columns) {
        return table(name,columns,50);
    }
    private static Uri.Builder table(String name,String columns,int limit) {
        return Uri.parse("/rest/v1/"+name).buildUpon().appendQueryParameter("select",columns).appendQueryParameter("limit",Integer.toString(limit));
    }
    private static void afterTuple(Uri.Builder query,String column,String time,String id) {
        if(id!=null)query.appendQueryParameter("or","("+column+".gt."+time+",and("+column+".eq."+time+",id.gt."+id+"))");
    }
    private static int compareTuple(String time,String id,String previousTime,String previousId) {
        int order=Instant.parse(time).compareTo(Instant.parse(previousTime)); return order==0?id.compareToIgnoreCase(previousId):order;
    }

    private static ConsultationData.Slot parseSlot(JSONObject row) throws Exception {
        ConsultationData.Slot value=new ConsultationData.Slot(string(row,"id"),string(row,"office_id"),string(row,"schedule_owner_id"),
                string(row,"start_at"),string(row,"end_at"),count(row,"capacity"),count(row,"reserved"),bool(row,"active"),integer(row,"version"),bool(row,"is_demo"));
        validate(value); return value;
    }
    private static ConsultationData.Appointment parseAppointment(JSONObject row) throws Exception {
        ConsultationData.Appointment value=new ConsultationData.Appointment(string(row,"id"),string(row,"student_id"),string(row,"slot_id"),
                string(row,"state"),integer(row,"version"),string(row,"created_at")); validate(value); return value;
    }
    private static ConsultationData.Event parseEvent(JSONObject row) throws Exception {
        String eventId=string(row,"id"), appointmentId=string(row,"appointment_id"), actorId=string(row,"actor_id"), action=string(row,"action"),
                oldSlot=nullable(row,"old_slot_id"), newSlot=nullable(row,"new_slot_id"), reason=nullable(row,"reason"), time=string(row,"created_at");
        id(eventId); id(appointmentId); id(actorId); optionalId(oldSlot); optionalId(newSlot); timestamp(time);
        if("booked".equals(action)) { if(oldSlot!=null || newSlot==null)throw failure("VALIDATION_FAILED"); }
        else if("cancel".equals(action)) { if(oldSlot==null || newSlot!=null)throw failure("VALIDATION_FAILED"); }
        else if("reschedule".equals(action)) { if(oldSlot==null || newSlot==null || oldSlot.equals(newSlot))throw failure("VALIDATION_FAILED"); }
        else throw failure("VALIDATION_FAILED");
        return new ConsultationData.Event(eventId,appointmentId,actorId,action,oldSlot,newSlot,reason,time);
    }
    private static void validate(ConsultationData.Slot value) throws Exception {
        if(value==null)throw failure("VALIDATION_FAILED"); id(value.id); office(value.officeId); id(value.ownerId);
        times(value.startAt,value.endAt); if(value.capacity<1 || value.reserved<0 || value.reserved>value.capacity || value.version<1)throw failure("VALIDATION_FAILED");
    }
    private static void validate(ConsultationData.Appointment value) throws Exception {
        if(value==null)throw failure("VALIDATION_FAILED"); id(value.id); id(value.studentId); id(value.slotId); timestamp(value.createdAt);
        if(value.version<1 || !("booked".equals(value.state) || "cancelled".equals(value.state)))throw failure("VALIDATION_FAILED");
    }
    private static void validate(ConsultationData.SlotDraft value) throws Exception {
        if(value==null)throw failure("VALIDATION_FAILED"); optionalId(value.id); office(value.officeId); id(value.ownerId); times(value.startAt,value.endAt);
        if(value.capacity<1 || (value.id==null?value.expectedVersion!=0:value.expectedVersion<1))throw failure("VALIDATION_FAILED");
    }
    private static void validateChange(ConsultationData.Appointment original,ConsultationData.Slot target) throws Exception {
        validate(original); if(!"booked".equals(original.state))throw failure("INVALID_STATE");
        if(target!=null) { validate(target); if(original.slotId.equals(target.id))throw failure("INVALID_STATE"); }
    }
    private static boolean sameTarget(ConsultationData.Slot a,ConsultationData.Slot b) { return a==null?b==null:b!=null && a.id.equals(b.id) && a.version==b.version; }
    private void account(String actor) throws WorkflowException { id(actor); if(!accountId.equals(actor))throw failure("FORBIDDEN"); }
    private static JSONObject object(JSONArray rows,int n) throws WorkflowException {
        try { return rows.getJSONObject(n); } catch(JSONException error) { throw failure("VALIDATION_FAILED"); }
    }
    private static String string(JSONObject row,String key) throws WorkflowException {
        try { Object raw=row.get(key); if(!(raw instanceof String))throw failure("VALIDATION_FAILED"); return (String)raw; }
        catch(JSONException error) { throw failure("VALIDATION_FAILED"); }
    }
    private static String nullable(JSONObject row,String key) throws WorkflowException {
        if(!row.has(key))throw failure("VALIDATION_FAILED"); return row.isNull(key)?null:string(row,key);
    }
    private static boolean bool(JSONObject row,String key) throws WorkflowException {
        try { Object raw=row.get(key); if(!(raw instanceof Boolean))throw failure("VALIDATION_FAILED"); return (Boolean)raw; }
        catch(JSONException error) { throw failure("VALIDATION_FAILED"); }
    }
    private static long integer(JSONObject row,String key) throws WorkflowException {
        try { Object raw=row.get(key); if(!(raw instanceof Integer) && !(raw instanceof Long))throw failure("VALIDATION_FAILED"); return ((Number)raw).longValue(); }
        catch(JSONException error) { throw failure("VALIDATION_FAILED"); }
    }
    private static int count(JSONObject row,String key) throws WorkflowException {
        long value=integer(row,key); if(value<0 || value>Integer.MAX_VALUE)throw failure("VALIDATION_FAILED"); return (int)value;
    }
    private static void id(String value) throws WorkflowException {
        if(value==null || !value.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))throw failure("VALIDATION_FAILED");
    }
    private static void optionalId(String value) throws WorkflowException { if(value!=null)id(value); }
    private static void office(String value) throws WorkflowException {
        if(!"OFF-GCC".equals(value) && !"DEP-CCS".equals(value))throw failure("VALIDATION_FAILED");
    }
    private static void timestamp(String value) throws WorkflowException {
        try { Instant.parse(value); } catch(Exception error) { throw failure("VALIDATION_FAILED"); }
    }
    private static void times(String start,String end) throws WorkflowException {
        timestamp(start); timestamp(end); if(!Instant.parse(start).isBefore(Instant.parse(end)))throw failure("VALIDATION_FAILED");
    }
    private static void cursor(String time,String id) throws WorkflowException {
        if((time==null)!=(id==null))throw failure("VALIDATION_FAILED"); if(id!=null) { id(id); timestamp(time); }
    }
    private static WorkflowException failure(String code) { return new WorkflowException(code); }
}
