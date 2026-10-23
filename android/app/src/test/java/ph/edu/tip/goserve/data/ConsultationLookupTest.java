package ph.edu.tip.goserve.data;

import android.net.Uri;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.domain.ConsultationData;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public final class ConsultationLookupTest {
    private static final String ACTOR=id(1), STUDENT=id(2), OWNER=id(3), SLOT=id(4), APPOINTMENT=id(5), OTHER=id(6);
    private static final String START="2026-11-01T01:00:00Z", END="2026-11-01T02:00:00Z", TIME="2026-10-19T01:00:00Z";

    @Test public void slotLookupUsesExactOfficeIdAndBoundedCanonicalColumns() throws Exception {
        Fake wire=new Fake(); wire.rows.put(slotRow().put("active",false));
        ConsultationData.Slot result=new ConsultationRepository(wire).slot("OFF-GCC",SLOT);
        assertNotNull("Canonical exact-ID slot lookup is missing",result);
        assertEquals(SLOT,result.id); assertEquals("OFF-GCC",result.officeId); assertEquals(OWNER,result.ownerId);
        assertEquals(7,result.version); assertEquals(3,result.remaining()); assertFalse(result.active);
        assertEquals(1,wire.paths.size()); Uri query=Uri.parse(wire.paths.get(0));
        assertEquals("/rest/v1/consultation_slots",query.getPath());
        assertEquals(Set.of("select","id","office_id","limit"),query.getQueryParameterNames());
        assertEquals("id,office_id,schedule_owner_id,start_at,end_at,capacity,reserved,active,version,is_demo",query.getQueryParameter("select"));
        assertEquals("eq."+SLOT,query.getQueryParameter("id")); assertEquals("eq.OFF-GCC",query.getQueryParameter("office_id"));
        assertEquals(List.of("2"),query.getQueryParameters("limit")); assertEquals(0,wire.rpcCalls);
    }

    @Test public void appointmentLookupKeepsRlsAuthorizedStaffAccessWithoutInventedOfficeColumn() throws Exception {
        Fake wire=new Fake(); wire.rows.put(appointmentRow());
        ConsultationData.Appointment result=new ConsultationRepository(wire).appointment(APPOINTMENT);
        assertNotNull("Canonical exact-ID appointment lookup is missing",result);
        assertEquals(APPOINTMENT,result.id); assertEquals(STUDENT,result.studentId); assertNotEquals(ACTOR,result.studentId);
        assertEquals(SLOT,result.slotId); assertEquals(2,result.version); assertEquals("booked",result.state); assertEquals(TIME,result.createdAt);
        assertEquals(1,wire.paths.size()); Uri query=Uri.parse(wire.paths.get(0));
        assertEquals("/rest/v1/appointments",query.getPath());
        assertEquals(Set.of("select","id","limit"),query.getQueryParameterNames());
        assertEquals("id,student_id,slot_id,state,version,created_at",query.getQueryParameter("select"));
        assertEquals("eq."+APPOINTMENT,query.getQueryParameter("id"));
        assertEquals(List.of("2"),query.getQueryParameters("limit")); assertEquals(0,wire.rpcCalls);
    }

    @Test public void missingDuplicateAndWrongIdentityRowsFailClosed() throws Exception {
        Fake wire=new Fake(); ConsultationRepository repo=new ConsultationRepository(wire);
        expectCode("NOT_FOUND",()->repo.slot("OFF-GCC",SLOT));
        expectCode("NOT_FOUND",()->repo.appointment(APPOINTMENT));
        wire.rows=new JSONArray().put(slotRow()).put(slotRow());
        expectCode("VALIDATION_FAILED",()->repo.slot("OFF-GCC",SLOT));
        wire.rows=new JSONArray().put(appointmentRow()).put(appointmentRow());
        expectCode("VALIDATION_FAILED",()->repo.appointment(APPOINTMENT));
        wire.rows=new JSONArray().put(slotRow().put("id",OTHER));
        expectCode("FORBIDDEN",()->repo.slot("OFF-GCC",SLOT));
        wire.rows=new JSONArray().put(slotRow().put("office_id","DEP-CCS"));
        expectCode("FORBIDDEN",()->repo.slot("OFF-GCC",SLOT));
        wire.rows=new JSONArray().put(appointmentRow().put("id",OTHER));
        expectCode("FORBIDDEN",()->repo.appointment(APPOINTMENT));
        wire.rows=new JSONArray().put(slotRow()).put(slotRow()).put(slotRow());
        expectCode("VALIDATION_FAILED",()->repo.slot("OFF-GCC",SLOT));
        wire.rows=null; expectCode("VALIDATION_FAILED",()->repo.appointment(APPOINTMENT));
        assertEquals(0,wire.rpcCalls);
    }

    @Test public void invalidInputsAndMalformedCanonicalFieldsAreRejected() throws Exception {
        Fake wire=new Fake(); ConsultationRepository repo=new ConsultationRepository(wire);
        expectCode("VALIDATION_FAILED",()->repo.slot("OFF-CASH",SLOT));
        expectCode("VALIDATION_FAILED",()->repo.slot("OFF-GCC","not-a-uuid"));
        expectCode("VALIDATION_FAILED",()->repo.appointment(null));
        assertTrue("Invalid lookup input reached transport",wire.paths.isEmpty());
        wire.rows=new JSONArray().put(slotRow().put("version",1.5));
        expectCode("VALIDATION_FAILED",()->repo.slot("OFF-GCC",SLOT));
        wire.rows=new JSONArray().put(slotRow().put("reserved",6));
        expectCode("VALIDATION_FAILED",()->repo.slot("OFF-GCC",SLOT));
        wire.rows=new JSONArray().put(appointmentRow().put("version","2"));
        expectCode("VALIDATION_FAILED",()->repo.appointment(APPOINTMENT));
        wire.rows=new JSONArray().put(appointmentRow().put("created_at","yesterday"));
        expectCode("VALIDATION_FAILED",()->repo.appointment(APPOINTMENT));
        wire.rows=new JSONArray().put("not-an-object");
        expectCode("VALIDATION_FAILED",()->repo.appointment(APPOINTMENT));
    }

    @Test public void leaseIsCheckedBeforeTransportAfterTransportAndAfterParsing() throws Exception {
        Fake before=new Fake(); ConsultationRepository beforeRepo=new ConsultationRepository(before); before.current=false;
        expectStaleAccount(()->beforeRepo.slot("OFF-GCC",SLOT));
        expectStaleAccount(()->beforeRepo.appointment(APPOINTMENT));
        assertTrue(before.paths.isEmpty());

        Fake after=new Fake(); after.rows.put(slotRow()); ConsultationRepository afterRepo=new ConsultationRepository(after);
        after.afterRead=()->after.current=false;
        expectStaleAccount(()->afterRepo.slot("OFF-GCC",SLOT));
        after.current=true; after.rows=new JSONArray().put(appointmentRow()); after.afterRead=()->after.current=false;
        expectStaleAccount(()->afterRepo.appointment(APPOINTMENT));

        Fake parsed=new Fake(); ConsultationRepository parsedRepo=new ConsultationRepository(parsed);
        parsed.rows.put(onField(slotRow(),"is_demo",()->parsed.current=false));
        expectStaleAccount(()->parsedRepo.slot("OFF-GCC",SLOT));
        parsed.current=true; parsed.rows=new JSONArray().put(onField(appointmentRow(),"created_at",()->parsed.current=false));
        expectStaleAccount(()->parsedRepo.appointment(APPOINTMENT));
    }

    @Test public void invalidationDuringIoOrParsingSupersedesDetailResponses() throws Exception {
        Fake wire=new Fake(); ConsultationRepository repo=new ConsultationRepository(wire);
        wire.rows.put(slotRow()); wire.afterRead=repo::invalidate;
        expectCode("STALE_RESPONSE",()->repo.slot("OFF-GCC",SLOT));
        wire.rows=new JSONArray().put(appointmentRow()); wire.afterRead=repo::invalidate;
        expectCode("STALE_RESPONSE",()->repo.appointment(APPOINTMENT));
        wire.rows=new JSONArray().put(onField(slotRow(),"is_demo",repo::invalidate));
        expectCode("STALE_RESPONSE",()->repo.slot("OFF-GCC",SLOT));
        wire.rows=new JSONArray().put(onField(appointmentRow(),"created_at",repo::invalidate));
        expectCode("STALE_RESPONSE",()->repo.appointment(APPOINTMENT));
    }

    @Test public void newerSameKindLookupSupersedesOlderSelectedRecord() throws Exception {
        Fake wire=new Fake(); ConsultationRepository repo=new ConsultationRepository(wire);
        ConsultationData.Slot[] newestSlot=new ConsultationData.Slot[1];
        wire.rows.put(slotRow()); wire.afterRead=()->{
            wire.rows=new JSONArray().put(slotRow().put("id",OTHER).put("version",8));
            newestSlot[0]=repo.slot("OFF-GCC",OTHER);
        };
        expectCode("STALE_RESPONSE",()->repo.slot("OFF-GCC",SLOT));
        assertNotNull(newestSlot[0]); assertEquals(OTHER,newestSlot[0].id); assertEquals(8,newestSlot[0].version);

        ConsultationData.Appointment[] newestAppointment=new ConsultationData.Appointment[1];
        wire.rows=new JSONArray().put(appointmentRow()); wire.afterRead=()->{
            wire.rows=new JSONArray().put(appointmentRow().put("id",OTHER).put("version",3));
            newestAppointment[0]=repo.appointment(OTHER);
        };
        expectCode("STALE_RESPONSE",()->repo.appointment(APPOINTMENT));
        assertNotNull(newestAppointment[0]); assertEquals(OTHER,newestAppointment[0].id); assertEquals(3,newestAppointment[0].version);
    }

    @Test public void independentListAndDetailLoadsDoNotCancelEachOther() throws Exception {
        Fake wire=new Fake(); ConsultationRepository repo=new ConsultationRepository(wire);
        wire.rows.put(slotRow()); wire.afterRead=()->assertNotNull(repo.slot("OFF-GCC",SLOT));
        assertEquals(SLOT,repo.slots("OFF-GCC",null,false,null,null).items.get(0).id);
        wire.afterRead=()->assertEquals(1,repo.slots("OFF-GCC",null,false,null,null).items.size());
        assertNotNull(repo.slot("OFF-GCC",SLOT));

        wire.rows=new JSONArray().put(appointmentRow().put("student_id",ACTOR));
        wire.afterRead=()->assertNotNull(repo.appointment(APPOINTMENT));
        assertEquals(APPOINTMENT,repo.appointments(null,null,null).items.get(0).id);
        wire.afterRead=()->assertEquals(1,repo.appointments(null,null,null).items.size());
        assertNotNull(repo.appointment(APPOINTMENT));

        wire.rows=new JSONArray().put(slotRow()); wire.afterRead=()->{
            wire.rows=new JSONArray().put(appointmentRow());
            assertNotNull(repo.appointment(APPOINTMENT));
        };
        assertNotNull(repo.slot("OFF-GCC",SLOT));
        wire.rows=new JSONArray().put(appointmentRow()); wire.afterRead=()->{
            wire.rows=new JSONArray().put(slotRow());
            assertNotNull(repo.slot("OFF-GCC",SLOT));
        };
        assertNotNull(repo.appointment(APPOINTMENT));
    }

    private static JSONObject slotRow() throws Exception {
        return new JSONObject().put("id",SLOT).put("office_id","OFF-GCC").put("schedule_owner_id",OWNER)
                .put("start_at",START).put("end_at",END).put("capacity",5).put("reserved",2)
                .put("active",true).put("version",7).put("is_demo",false);
    }
    private static JSONObject appointmentRow() throws Exception {
        return new JSONObject().put("id",APPOINTMENT).put("student_id",STUDENT).put("slot_id",SLOT)
                .put("state","booked").put("version",2).put("created_at",TIME);
    }
    private static JSONObject onField(JSONObject row,String field,Runnable action) throws Exception {
        return new JSONObject(row.toString()) {
            @Override public Object get(String name) throws JSONException {
                Object value=super.get(name); if(field.equals(name))action.run(); return value;
            }
        };
    }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private interface Action { void run() throws Exception; }
    private static void expectCode(String code,Action action) throws Exception {
        try { action.run(); fail("Expected "+code); }
        catch(ConsultationRepository.WorkflowException error) { assertEquals(code,error.code); }
    }
    private static void expectStaleAccount(Action action) throws Exception {
        try { action.run(); fail("Old account lookup was published"); }
        catch(IllegalStateException error) { assertEquals("STALE_ACCOUNT",error.getMessage()); }
    }
    private static final class Fake implements ConsultationRepository.Transport {
        JSONArray rows=new JSONArray(); boolean current=true; Action afterRead; int rpcCalls;
        final List<String> paths=new ArrayList<>();
        public String accountId() { return ACTOR; }
        public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public JSONArray get(String path) throws Exception {
            paths.add(path); JSONArray result=rows; Action pending=afterRead; afterRead=null;
            if(pending!=null)pending.run(); return result;
        }
        public JSONObject rpc(String name,JSONObject args) {
            ++rpcCalls; throw new AssertionError("Lookup attempted mutation: "+name);
        }
    }
}
