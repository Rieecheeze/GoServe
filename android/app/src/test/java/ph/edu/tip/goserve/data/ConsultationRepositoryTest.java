package ph.edu.tip.goserve.data;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.domain.ConsultationData;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public final class ConsultationRepositoryTest {
    private static final String ACTOR=id(1), OWNER=id(2), SLOT=id(3), APPOINTMENT=id(4), TARGET=id(5);
    private static final String START="2026-11-01T01:00:00Z", END="2026-11-01T02:00:00Z", TIME="2026-10-19T01:00:00Z";

    @Test public void emptySlotsHaveNoInventedAvailability() throws Exception {
        ConsultationData.SlotPage page=new ConsultationRepository(new Fake()).slots("OFF-GCC",null,true,null,null);
        assertNotNull("Canonical empty schedule page is missing",page);
        assertTrue(page.items.isEmpty()); assertNull(page.nextId);
    }
    @Test public void slotPageUsesCanonicalReservedAndEqualTimeTupleCursor() throws Exception {
        Fake wire=new Fake();
        for(int n=10;n<60;n++)wire.rows.put(slotRow().put("id",id(n)));
        ConsultationRepository repo=new ConsultationRepository(wire);
        ConsultationData.SlotPage page=repo.slots("OFF-GCC",OWNER,true,null,null);
        assertNotNull("Schedule page is missing",page);
        assertEquals(3,page.items.get(0).remaining()); assertEquals(START,page.nextStartAt); assertEquals(id(59),page.nextId);
        wire.rows=new JSONArray(); repo.slots("OFF-GCC",OWNER,true,page.nextStartAt,page.nextId);
        String query=decode(wire.paths.get(1));
        assertTrue(query.contains("office_id=eq.OFF-GCC")); assertTrue(query.contains("schedule_owner_id=eq."+OWNER));
        assertTrue(query.contains("active=eq.true")); assertTrue(query.contains("order=start_at.asc,id.asc"));
        assertTrue(query.contains("or=(start_at.gt."+START+",and(start_at.eq."+START+",id.gt."+id(59)+"))"));
        assertTrue(query.contains("reserved")); assertTrue(query.contains("limit=50")); assertFalse(query.contains("select=*"));
    }
    @Test public void duplicateSlotRowsRetainNewestCanonicalVersion() throws Exception {
        Fake wire=new Fake(); wire.rows.put(slotRow().put("version",4)).put(slotRow().put("version",2));
        ConsultationData.SlotPage page=new ConsultationRepository(wire).slots("OFF-GCC",OWNER,false,null,null);
        assertNotNull(page); assertEquals(1,page.items.size()); assertEquals(4,page.items.get(0).version);
    }
    @Test public void slotParserRejectsScopeCapacityAndLossySchema() throws Exception {
        Fake wire=new Fake(); ConsultationRepository repo=new ConsultationRepository(wire);
        wire.rows=new JSONArray().put(slotRow().put("office_id","DEP-CCS"));
        expectCode("FORBIDDEN",()->repo.slots("OFF-GCC",OWNER,true,null,null));
        wire.rows=new JSONArray().put(slotRow().put("reserved",6));
        expectCode("VALIDATION_FAILED",()->repo.slots("OFF-GCC",OWNER,true,null,null));
        wire.rows=new JSONArray().put(slotRow().put("version",1.5));
        expectCode("VALIDATION_FAILED",()->repo.slots("OFF-GCC",OWNER,true,null,null));
        wire.rows=new JSONArray().put(slotRow().put("active","true"));
        expectCode("VALIDATION_FAILED",()->repo.slots("OFF-GCC",OWNER,true,null,null));
    }
    @Test public void ownAppointmentsCannotPublishAnotherStudentsRecord() throws Exception {
        Fake wire=new Fake(); wire.rows.put(appointmentRow()); ConsultationRepository repo=new ConsultationRepository(wire);
        ConsultationData.AppointmentPage page=repo.appointments(null,null,null);
        assertNotNull(page); assertEquals(APPOINTMENT,page.items.get(0).id);
        String query=decode(wire.paths.get(0)); assertTrue(query.contains("student_id=eq."+ACTOR));
        assertTrue(query.contains("order=id.asc")); assertTrue(query.contains("limit=50"));
        wire.rows=new JSONArray().put(appointmentRow().put("student_id",id(99)));
        expectCode("FORBIDDEN",()->repo.appointments(null,null,null));
    }
    @Test public void officeAppointmentsJoinTheirAuthorizedSlotParent() throws Exception {
        Fake wire=new Fake(); wire.rows.put(appointmentRow().put("consultation_slots",new JSONObject().put("office_id","OFF-GCC").put("schedule_owner_id",OWNER)));
        ConsultationRepository repo=new ConsultationRepository(wire);
        ConsultationData.AppointmentPage page=repo.appointments("OFF-GCC",OWNER,null);
        assertNotNull(page); assertEquals(1,page.items.size());
        String query=decode(wire.paths.get(0)); assertTrue(query.contains("consultation_slots!inner(office_id,schedule_owner_id)"));
        assertTrue(query.contains("consultation_slots.office_id=eq.OFF-GCC"));
        assertTrue(query.contains("consultation_slots.schedule_owner_id=eq."+OWNER));
        wire.rows.getJSONObject(0).getJSONObject("consultation_slots").put("office_id","DEP-CCS");
        expectCode("FORBIDDEN",()->repo.appointments("OFF-GCC",OWNER,null));
    }
    @Test public void eventsAreParentBoundAndUseAscendingTupleCursor() throws Exception {
        Fake wire=new Fake(); for(int n=10;n<60;n++)wire.rows.put(eventRow().put("id",id(n)));
        ConsultationRepository repo=new ConsultationRepository(wire);
        ConsultationData.EventPage page=repo.events(APPOINTMENT,null,null);
        assertNotNull(page); assertEquals(TIME,page.nextCreatedAt); assertEquals(id(59),page.nextId);
        wire.rows=new JSONArray(); repo.events(APPOINTMENT,page.nextCreatedAt,page.nextId);
        String query=decode(wire.paths.get(1)); assertTrue(query.contains("appointment_id=eq."+APPOINTMENT));
        assertTrue(query.contains("order=created_at.asc,id.asc"));
        assertTrue(query.contains("or=(created_at.gt."+TIME+",and(created_at.eq."+TIME+",id.gt."+id(59)+"))"));
        wire.rows=new JSONArray().put(eventRow().put("appointment_id",id(99)));
        expectCode("FORBIDDEN",()->repo.events(APPOINTMENT,null,null));
    }
    @Test public void malformedEventsAndOversizedPagesFailClosed() throws Exception {
        Fake wire=new Fake(); ConsultationRepository repo=new ConsultationRepository(wire);
        wire.rows.put(eventRow().put("created_at","yesterday")); expectCode("VALIDATION_FAILED",()->repo.events(APPOINTMENT,null,null));
        wire.rows=new JSONArray(); for(int n=0;n<51;n++)wire.rows.put(slotRow());
        expectCode("VALIDATION_FAILED",()->repo.slots("OFF-GCC",null,true,null,null));
        expectCode("VALIDATION_FAILED",()->repo.slots("OFF-GCC",null,true,START,null));
    }
    @Test public void identicalBookingRetryKeepsKeyChangedVersionGetsNewKey() throws Exception {
        ConsultationRepository repo=new ConsultationRepository(new Fake());
        ConsultationData.Booking first=repo.booking(slot(),null); assertNotNull("Booking draft is missing",first);
        assertEquals(first.operationKey,repo.booking(slot(),first).operationKey);
        ConsultationData.Slot changed=new ConsultationData.Slot(SLOT,"OFF-GCC",OWNER,START,END,5,2,true,8,false);
        assertNotEquals(first.operationKey,repo.booking(changed,first).operationKey);
    }
    @Test public void bookSendsExactExpectedVersionAndReloadsAfterReplay() throws Exception {
        Fake wire=new Fake(); wire.reply=appointmentRow(); wire.rows.put(appointmentRow().put("version",3).put("state","cancelled"));
        ConsultationRepository repo=new ConsultationRepository(wire);
        ConsultationData.Appointment result=repo.book(new ConsultationData.Booking(ACTOR,slot(),id(90)));
        assertNotNull("Canonical booking result is missing",result); assertEquals("cancelled",result.state); assertEquals(3,result.version);
        assertEquals("book_consultation",wire.rpcNames.get(0)); assertEquals(3,wire.args.length());
        assertEquals(SLOT,wire.args.getString("p_slot_id")); assertEquals(7,wire.args.getLong("p_expected_version"));
        assertEquals(id(90),wire.args.getString("p_idempotency_key"));
        assertTrue(decode(wire.paths.get(0)).contains("id=eq."+APPOINTMENT));
    }
    @Test public void serverFullStaleAndConfigurationDenialPreserveBookingDraft() throws Exception {
        Fake wire=new Fake(); ConsultationRepository repo=new ConsultationRepository(wire);
        ConsultationData.Booking draft=new ConsultationData.Booking(ACTOR,slot(),id(90));
        for(String code:List.of("CAPACITY_FULL","STALE_VERSION","CONFIGURATION_REQUIRED","POLICY_BLOCKED")) {
            wire.failure=code; expectCode(code,()->repo.book(draft));
            assertEquals(id(90),draft.operationKey); assertEquals(2,draft.slot.reserved); assertEquals(7,draft.slot.version);
        }
        assertTrue(wire.paths.isEmpty());
    }
    @Test public void rescheduleIsOneAtomicCallAndFailedMovePreservesOriginal() throws Exception {
        Fake wire=new Fake(); wire.failure="CAPACITY_FULL"; ConsultationRepository repo=new ConsultationRepository(wire);
        ConsultationData.Change draft=new ConsultationData.Change(ACTOR,appointment(),target(),"Class conflict",id(90));
        expectCode("CAPACITY_FULL",()->repo.changeAppointment(draft));
        assertEquals(List.of("change_appointment"),wire.rpcNames); assertEquals(6,wire.args.length());
        assertEquals(APPOINTMENT,wire.args.getString("p_appointment_id")); assertEquals(2,wire.args.getLong("p_expected_version"));
        assertEquals(TARGET,wire.args.getString("p_target_slot_id")); assertEquals(9,wire.args.getLong("p_target_version"));
        assertEquals("Class conflict",wire.args.getString("p_reason")); assertEquals(SLOT,draft.original.slotId);
        assertEquals("booked",draft.original.state); assertEquals(2,draft.original.version);
    }
    @Test public void cancelUsesExplicitNullTargetAndNullableReasonContract() throws Exception {
        Fake wire=new Fake(); wire.reply=appointmentRow().put("state","cancelled").put("version",3);
        wire.rows.put(appointmentRow().put("state","cancelled").put("version",3));
        ConsultationRepository repo=new ConsultationRepository(wire);
        ConsultationData.Appointment result=repo.changeAppointment(new ConsultationData.Change(ACTOR,appointment(),null,null,id(90)));
        assertNotNull(result); assertEquals("cancelled",result.state);
        assertTrue(wire.args.has("p_target_slot_id")); assertTrue(wire.args.isNull("p_target_slot_id"));
        assertTrue(wire.args.has("p_target_version")); assertTrue(wire.args.isNull("p_target_version"));
        assertTrue(wire.args.has("p_reason")); assertTrue(wire.args.isNull("p_reason"));
    }
    @Test public void changeDraftRetryKeyTracksReasonAndTargetVersion() throws Exception {
        ConsultationRepository repo=new ConsultationRepository(new Fake());
        ConsultationData.Change first=repo.change(appointment(),target(),"Reason",null); assertNotNull(first);
        assertEquals(first.operationKey,repo.change(appointment(),target(),"Reason",first).operationKey);
        assertNotEquals(first.operationKey,repo.change(appointment(),target(),"New reason",first).operationKey);
        assertNotEquals(first.operationKey,repo.change(appointment(),null,"Reason",first).operationKey);
    }
    @Test public void saveSlotDelegatesCapabilityPolicyToServerWithExactOwnerField() throws Exception {
        Fake wire=new Fake(); wire.reply=slotRow(); wire.rows.put(slotRow());
        ConsultationRepository repo=new ConsultationRepository(wire);
        ConsultationData.SlotDraft draft=repo.slotDraft(null,0,"OFF-GCC",OWNER,START,END,5,true,null); assertNotNull(draft);
        ConsultationData.Slot result=repo.saveSlot(draft); assertNotNull(result); assertEquals(OWNER,result.ownerId);
        assertEquals("save_slot",wire.rpcNames.get(0)); assertEquals(9,wire.args.length());
        assertTrue(wire.args.isNull("p_id")); assertEquals(0,wire.args.getLong("p_expected_version"));
        assertEquals(OWNER,wire.args.getString("p_owner_id")); assertFalse(wire.args.has("capability"));
        wire.failure="FORBIDDEN"; expectCode("FORBIDDEN",()->repo.saveSlot(draft));
    }
    @Test public void accountReplacementCannotPublishReadOrUsePreviousAccountDraft() throws Exception {
        Fake wire=new Fake(); wire.rows.put(slotRow()); wire.afterRead=()->wire.current=false;
        expectStaleAccount(()->new ConsultationRepository(wire).slots("OFF-GCC",null,true,null,null));
        wire.current=true; wire.afterRead=null;
        expectCode("FORBIDDEN",()->new ConsultationRepository(wire).book(new ConsultationData.Booking(id(99),slot(),id(90))));
        assertTrue(wire.rpcNames.isEmpty());
    }
    @Test public void invalidationDuringReadRejectsLateResponse() throws Exception {
        Fake wire=new Fake(); wire.rows.put(slotRow()); ConsultationRepository repo=new ConsultationRepository(wire);
        wire.afterRead=repo::invalidate;
        expectCode("STALE_RESPONSE",()->repo.slots("OFF-GCC",null,true,null,null));
    }
    @Test public void wrongMutationParentAndMalformedIntegerCannotPublish() throws Exception {
        Fake wire=new Fake(); wire.reply=appointmentRow().put("student_id",id(99)); wire.rows.put(appointmentRow());
        ConsultationRepository repo=new ConsultationRepository(wire);
        expectCode("FORBIDDEN",()->repo.book(new ConsultationData.Booking(ACTOR,slot(),id(90))));
        wire.reply=appointmentRow().put("version","2");
        expectCode("VALIDATION_FAILED",()->repo.book(new ConsultationData.Booking(ACTOR,slot(),id(90))));
    }

    private static ConsultationData.Slot slot() { return new ConsultationData.Slot(SLOT,"OFF-GCC",OWNER,START,END,5,2,true,7,false); }
    private static ConsultationData.Slot target() { return new ConsultationData.Slot(TARGET,"OFF-GCC",OWNER,START,END,5,0,true,9,false); }
    private static ConsultationData.Appointment appointment() { return new ConsultationData.Appointment(APPOINTMENT,ACTOR,SLOT,"booked",2,TIME); }
    private static JSONObject slotRow() throws Exception { return new JSONObject().put("id",SLOT).put("office_id","OFF-GCC").put("schedule_owner_id",OWNER).put("start_at",START).put("end_at",END).put("capacity",5).put("reserved",2).put("active",true).put("version",7).put("is_demo",false); }
    private static JSONObject appointmentRow() throws Exception { return new JSONObject().put("id",APPOINTMENT).put("student_id",ACTOR).put("slot_id",SLOT).put("state","booked").put("version",2).put("created_at",TIME); }
    private static JSONObject eventRow() throws Exception { return new JSONObject().put("id",id(6)).put("appointment_id",APPOINTMENT).put("actor_id",ACTOR).put("action","booked").put("old_slot_id",JSONObject.NULL).put("new_slot_id",SLOT).put("reason",JSONObject.NULL).put("created_at",TIME); }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static String decode(String value) { return URLDecoder.decode(value,StandardCharsets.UTF_8); }
    private interface Action { void run() throws Exception; }
    private static void expectCode(String code,Action action) throws Exception { try { action.run(); fail("Expected "+code); } catch(ConsultationRepository.WorkflowException error) { assertEquals(code,error.code); } }
    private static void expectStaleAccount(Action action) throws Exception { try { action.run(); fail("Old account response was published"); } catch(IllegalStateException expected) { assertEquals("STALE_ACCOUNT",expected.getMessage()); } }
    private static final class Fake implements ConsultationRepository.Transport {
        JSONArray rows=new JSONArray(); JSONObject reply; JSONObject args; String failure; boolean current=true; Runnable afterRead;
        final List<String> paths=new ArrayList<>(), rpcNames=new ArrayList<>();
        public String accountId() { return ACTOR; }
        public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public JSONArray get(String path) { paths.add(path); JSONArray result=rows; if(afterRead!=null)afterRead.run(); return result; }
        public JSONObject rpc(String name,JSONObject value) throws Exception { rpcNames.add(name); args=value; if(failure!=null)throw new ConsultationRepository.WorkflowException(failure); return reply; }
    }
}
