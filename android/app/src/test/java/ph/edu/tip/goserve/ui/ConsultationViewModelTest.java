package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.SavedStateHandle;
import java.util.*;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.data.ConsultationRepository;
import ph.edu.tip.goserve.domain.ConsultationData.*;
import ph.edu.tip.goserve.domain.Role;
import ph.edu.tip.goserve.domain.SessionProfile;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34,application=Application.class)
public final class ConsultationViewModelTest {
    @Test public void uncertainReceiptOrCanonicalReloadFailureRetainsTheOriginalMutationIntent(){
        for(String code:List.of("VALIDATION_FAILED","NOT_FOUND","FORBIDDEN")){
            F f=new F();f.a.page=new SlotPage(List.of(slot(1,1)),null,null);f.load();f.vm.select(f.s().slots.get(0));f.a.error=new ConsultationRepository.WorkflowException(code);f.vm.confirm();f.run();
            Booking attempted=f.a.booking;assertTrue("Result may be lost after a committed write: "+code,f.s().uncertain);assertTrue(f.vm.mutationPending());f.a.error=null;f.vm.retryMutation();f.runAll();assertSame(attempted,f.a.booking);assertEquals(2,f.a.writes);
        }
    }
    @Test public void processRestoreRetainsEnteredScheduleButNeverSavesBeforeExplicitReview(){
        SavedStateHandle saved=new SavedStateHandle();A actions=new A();Q work=new Q(),ui=new Q();
        ConsultationViewModel first=new ConsultationViewModel(RuntimeEnvironment.getApplication(),actions,work,ui,false,false,"OFF-GCC",saved);
        first.newSlot();first.form("2026-11-01T03:00:00Z","2026-11-01T04:30:00Z","7",false);
        ConsultationViewModel restored=new ConsultationViewModel(RuntimeEnvironment.getApplication(),actions,new Q(),new Q(),false,false,"OFF-GCC",saved);
        assertEquals(ConsultationViewModel.Screen.FORM,restored.state().getValue().screen);assertEquals("7",restored.state().getValue().capacity);assertEquals("2026-11-01T03:00:00Z",restored.state().getValue().start);assertFalse(restored.state().getValue().active);assertEquals(0,actions.writes);
    }
    @Test public void restoredUnknownBookingReloadsCanonicalDataAndReplaysOnlyAfterExplicitRetryWithSameWireIntent(){
        SavedStateHandle saved=new SavedStateHandle();A actions=new A();Q work=new Q(),ui=new Q();
        actions.page=new SlotPage(List.of(slot(1,1)),null,null);ConsultationViewModel first=new ConsultationViewModel(RuntimeEnvironment.getApplication(),actions,work,ui,true,true,null,saved);
        first.ensureLoaded();work.run();ui.run();first.select(first.state().getValue().slots.get(0));actions.error=new java.io.IOException();first.confirm();work.run();ui.run();Booking attempted=actions.booking;
        Q restoredWork=new Q(),restoredUi=new Q();ConsultationViewModel restored=new ConsultationViewModel(RuntimeEnvironment.getApplication(),actions,restoredWork,restoredUi,true,true,null,saved);
        assertTrue(restored.mutationPending());assertEquals(1,actions.writes);restored.ensureLoaded();assertEquals(1,restoredWork.q.size());restoredWork.run();restoredUi.run();assertEquals(1,actions.writes);assertTrue(restored.state().getValue().uncertain);
        actions.error=null;restored.retryMutation();restoredWork.run();restoredUi.run();assertEquals(2,actions.writes);assertEquals(attempted.operationKey,actions.booking.operationKey);assertEquals(attempted.slot.id,actions.booking.slot.id);assertEquals(attempted.slot.version,actions.booking.slot.version);
    }
    @Test public void restoredDraftIsErasedBeforeAnotherAccountCanSeeOrRetryIt(){
        SavedStateHandle saved=new SavedStateHandle();A actions=new A();ConsultationViewModel first=new ConsultationViewModel(RuntimeEnvironment.getApplication(),actions,new Q(),new Q(),false,false,"OFF-GCC",saved);
        first.newSlot();first.form("2026-11-01T03:00:00Z","2026-11-01T04:30:00Z","7",false);assertFalse(saved.keys().isEmpty());actions.actor=id(99);
        ConsultationViewModel other=new ConsultationViewModel(RuntimeEnvironment.getApplication(),actions,new Q(),new Q(),false,false,"OFF-GCC",saved);
        assertEquals("",other.state().getValue().capacity);assertEquals(ConsultationViewModel.Screen.SLOTS,other.state().getValue().screen);assertTrue(saved.keys().isEmpty());assertEquals(0,actions.writes);
    }
    @Test public void unknownCancellationRestoreShowsFreshCanonicalRecordButPreservesOriginalWireVersionAndReason(){
        SavedStateHandle saved=new SavedStateHandle();A actions=new A();Q work=new Q(),ui=new Q();
        ConsultationViewModel first=new ConsultationViewModel(RuntimeEnvironment.getApplication(),actions,work,ui,true,true,null,saved);first.open(id(9));work.run();ui.run();first.reviewCancel();first.reason("Original reason");actions.error=new java.io.IOException();first.confirm();work.run();ui.run();Change attempted=actions.change;
        actions.canonical=new Appointment(id(9),id(50),id(1),"cancelled",8,"2026-10-22T01:00:00Z");Q rw=new Q(),ru=new Q();ConsultationViewModel restored=new ConsultationViewModel(RuntimeEnvironment.getApplication(),actions,rw,ru,true,true,null,saved);restored.ensureLoaded();rw.run();ru.run();
        assertEquals("cancelled",restored.state().getValue().appointment.state);assertEquals(8,restored.state().getValue().appointment.version);assertEquals(1,actions.writes);assertTrue(restored.state().getValue().uncertain);actions.error=null;restored.retryMutation();rw.run();ru.run();
        assertEquals(attempted.operationKey,actions.change.operationKey);assertEquals(attempted.original.version,actions.change.original.version);assertEquals(attempted.original.slotId,actions.change.original.slotId);assertEquals("Original reason",actions.change.reason);assertNull(actions.change.target);
    }
    @Test public void unknownNewAvailabilityRestoreUsesOnlySavedEnteredWireFieldsAndExplicitRetry(){
        SavedStateHandle saved=new SavedStateHandle();A actions=new A();Q work=new Q(),ui=new Q();ConsultationViewModel first=new ConsultationViewModel(RuntimeEnvironment.getApplication(),actions,work,ui,false,false,"OFF-GCC",saved);
        first.newSlot();first.form("2026-11-01T03:00:00Z","2026-11-01T04:30:00Z","7",false);first.reviewSave();actions.error=new java.io.IOException();first.confirm();work.run();ui.run();SlotDraft attempted=actions.savedDraft;
        for(String name:saved.keys()){Object value=saved.get(name);assertFalse(value instanceof Slot);assertFalse(value instanceof Appointment);assertFalse(value instanceof java.util.Collection);}
        Q rw=new Q(),ru=new Q();ConsultationViewModel restored=new ConsultationViewModel(RuntimeEnvironment.getApplication(),actions,rw,ru,false,false,"OFF-GCC",saved);restored.ensureLoaded();rw.run();ru.run();assertEquals(1,actions.writes);assertTrue(restored.state().getValue().uncertain);actions.error=null;restored.retryMutation();rw.run();ru.run();
        assertEquals(attempted.operationKey,actions.savedDraft.operationKey);assertEquals(attempted.startAt,actions.savedDraft.startAt);assertEquals(attempted.endAt,actions.savedDraft.endAt);assertEquals(7,actions.savedDraft.capacity);assertFalse(actions.savedDraft.active);assertNull(actions.savedDraft.id);
    }
    @Test public void restoredUnconfirmedAvailabilityReviewCanConfirmOnlyAfterCanonicalPreparation(){
        SavedStateHandle saved=new SavedStateHandle();A actions=new A();ConsultationViewModel first=new ConsultationViewModel(RuntimeEnvironment.getApplication(),actions,new Q(),new Q(),false,false,"OFF-GCC",saved);first.newSlot();first.form("2026-11-01T03:00:00Z","2026-11-01T04:30:00Z","7",false);first.reviewSave();
        Q rw=new Q(),ru=new Q();ConsultationViewModel restored=new ConsultationViewModel(RuntimeEnvironment.getApplication(),actions,rw,ru,false,false,"OFF-GCC",saved);restored.ensureLoaded();rw.run();ru.run();assertFalse(restored.state().getValue().uncertain);assertEquals(0,actions.writes);restored.confirm();rw.run();ru.run();assertEquals(1,actions.writes);assertEquals(7,actions.savedDraft.capacity);
    }
    @Test public void foregroundRefreshCannotSilentlyDropAnExistingAvailabilityReviewCommand(){
        F f=new F(false,false);f.a.page=new SlotPage(List.of(slot(1,1)),null,null);f.load();f.vm.select(f.s().slots.get(0));f.vm.form("2026-11-01T03:00:00Z","2026-11-01T04:30:00Z","7",false);f.vm.reviewSave();f.vm.refresh();f.runAll();
        assertEquals(ConsultationViewModel.Screen.REVIEW,f.s().screen);assertEquals("7",f.s().capacity);assertEquals(0,f.a.writes);f.vm.confirm();f.runAll();assertEquals(1,f.a.writes);assertEquals(7,f.a.savedDraft.capacity);
    }
    @Test public void restoredAppointmentsRouteReloadsItsPageAfterPreviouslyViewingADetail(){
        SavedStateHandle saved=new SavedStateHandle();A actions=new A();Q work=new Q(),ui=new Q();ConsultationViewModel first=new ConsultationViewModel(RuntimeEnvironment.getApplication(),actions,work,ui,true,true,null,saved);first.open(id(9));work.run();ui.run();first.appointments();work.run();ui.run();
        Q rw=new Q(),ru=new Q();ConsultationViewModel restored=new ConsultationViewModel(RuntimeEnvironment.getApplication(),actions,rw,ru,true,true,null,saved);restored.ensureLoaded();while(!rw.q.isEmpty() || !ru.q.isEmpty()){if(!rw.q.isEmpty())rw.run();if(!ru.q.isEmpty())ru.run();}
        assertEquals(ConsultationViewModel.Screen.APPOINTMENTS,restored.state().getValue().screen);assertEquals(1,restored.state().getValue().appointments.size());assertEquals(0,actions.writes);
    }
    @Test public void restoredRescheduleSelectionReloadsBothTheOriginalAndAvailableReplacements(){
        SavedStateHandle saved=new SavedStateHandle();A actions=new A();actions.page=new SlotPage(List.of(slot(2,1)),null,null);Q work=new Q(),ui=new Q();ConsultationViewModel first=new ConsultationViewModel(RuntimeEnvironment.getApplication(),actions,work,ui,true,true,null,saved);first.open(id(9));work.run();ui.run();first.beginMove();work.run();ui.run();first.reason("Retained move reason");
        Q rw=new Q(),ru=new Q();ConsultationViewModel restored=new ConsultationViewModel(RuntimeEnvironment.getApplication(),actions,rw,ru,true,true,null,saved);restored.ensureLoaded();while(!rw.q.isEmpty() || !ru.q.isEmpty()){if(!rw.q.isEmpty())rw.run();if(!ru.q.isEmpty())ru.run();}
        assertTrue(restored.state().getValue().moving);assertEquals(id(1),restored.state().getValue().appointment.slotId);assertEquals(1,restored.state().getValue().slots.size());assertEquals("Retained move reason",restored.state().getValue().reason);assertEquals(0,actions.writes);
    }
    @Test public void initialReadIsQueuedAndEmptyIsCanonical(){ F f=new F();f.vm.ensureLoaded();f.vm.ensureLoaded();assertTrue(f.s().busy);assertEquals(1,f.work.q.size());f.run();assertTrue(f.s().slots.isEmpty());assertNull(f.s().error); }
    @Test public void onlyStudentMatchingDepartmentOrGuidanceSecretaryCanEnter(){
        assertTrue(ConsultationViewModel.permitted(profile(Role.STUDENT,null,null,"DEP-CCS")));
        assertTrue(ConsultationViewModel.permitted(profile(Role.STAFF,"OFF-GCC","guidance",null)));
        assertTrue(ConsultationViewModel.permitted(profile(Role.STAFF,"DEP-CCS","secretary",null)));
        assertFalse(ConsultationViewModel.permitted(profile(Role.STAFF,"DEP-CCS","faculty",null)));
        assertFalse(ConsultationViewModel.permitted(profile(Role.TELLER,"OFF-CASH",null,null)));
    }
    @Test public void departmentChoiceCannotExpandUnmatchedStudentScope(){F f=new F(false);f.vm.office("DEP-CCS");assertEquals("FORBIDDEN",f.s().error);assertEquals("OFF-GCC",f.s().office);assertEquals(0,f.work.q.size());}
    @Test public void slotPageUsesCanonicalTupleAndDedupe(){F f=new F();f.a.page=new SlotPage(List.of(slot(1,1)),"2026-11-01T01:00:00Z",id(1));f.load();f.a.page=new SlotPage(List.of(slot(1,2),slot(2,1)),null,null);f.vm.more();f.run();assertEquals(id(1),f.a.after);assertEquals(2,f.s().slots.size());assertEquals(2,f.s().slots.get(0).version);}
    @Test public void bookingNeedsReviewAndUnknownRetryKeepsExactCommand(){F f=new F();f.a.page=new SlotPage(List.of(slot(1,1)),null,null);f.load();f.vm.select(f.s().slots.get(0));assertEquals(ConsultationViewModel.Screen.REVIEW,f.s().screen);assertEquals(0,f.a.writes);f.a.error=new java.io.IOException();f.vm.confirm();f.run();assertTrue(f.s().uncertain);Booking first=f.a.booking;f.vm.office("DEP-CCS");assertEquals("OFF-GCC",f.s().office);f.a.error=null;f.vm.retryMutation();f.runAll();assertSame(first,f.a.booking);assertEquals(2,f.a.writes);assertEquals(id(9),f.s().appointment.id);}
    @Test public void failedAtomicMovePreservesOriginalAndReason(){F f=new F();f.vm.open(id(9));f.run();f.vm.beginMove();f.run();f.vm.reason("My reason");f.a.page=new SlotPage(List.of(slot(2,1)),null,null);f.vm.refresh();f.run();f.vm.select(f.s().slots.get(0));f.a.error=new ConsultationRepository.WorkflowException("CAPACITY_FULL");f.vm.confirm();f.run();assertEquals("CAPACITY_FULL",f.s().error);assertEquals(id(1),f.s().appointment.slotId);assertEquals("My reason",f.s().reason);assertEquals(1,f.a.writes);assertEquals(id(2),f.a.change.target.id);}
    @Test public void cancellationSendsNullTargetOnlyAfterConfirmation(){F f=new F();f.vm.open(id(9));f.run();f.vm.reviewCancel();assertEquals(0,f.a.writes);f.vm.confirm();f.runAll();assertNull(f.a.change.target);assertEquals("cancelled",f.s().appointment.state);}
    @Test public void oldAccountCannotPublishReadOrMutation(){for(boolean mutate:new boolean[]{false,true}){F f=new F();if(mutate){f.a.page=new SlotPage(List.of(slot(1,1)),null,null);f.load();f.vm.select(f.s().slots.get(0));f.vm.confirm();}else f.vm.ensureLoaded();f.work.run();f.a.current=false;f.ui.run();assertTrue(f.s().blocked);assertTrue(f.s().slots.isEmpty());assertNull(f.s().appointment);}}
    @Test public void realtimeDuringLoadCoalescesAndRetainsReview(){F f=new F();f.vm.ensureLoaded();f.vm.refresh();f.vm.refresh();f.run();assertEquals(1,f.work.q.size());f.run();assertEquals(2,f.a.reads);}
    @Test public void slotSaveRetainsEnteredFieldsOnServerPolicyDenial(){F f=new F(true,false);f.vm.newSlot();f.vm.form("2026-11-01T01:00:00Z","2026-11-01T02:00:00Z","3",false);f.vm.reviewSave();f.a.error=new ConsultationRepository.WorkflowException("POLICY_BLOCKED");f.vm.confirm();f.run();assertEquals("POLICY_BLOCKED",f.s().error);assertEquals("3",f.s().capacity);assertFalse(f.s().active);assertEquals(1,f.a.writes);}
    private static SessionProfile profile(Role role,String office,String capability,String dept){return new SessionProfile(id(50),"test@tip.edu.ph","Test",role,office,capability,true,1,true,"v","v","home",null,dept);}
    static String id(int n){return String.format(Locale.ROOT,"00000000-0000-4000-8000-%012d",n);}
    static Slot slot(int n,long v){return new Slot(id(n),"OFF-GCC",id(50),"2026-11-01T01:00:00Z","2026-11-01T02:00:00Z",3,1,true,v,false);}
    static Appointment ap(String state,String slot){return new Appointment(id(9),id(50),slot,state,2,"2026-10-22T01:00:00Z");}
    static class Q implements Executor{final ArrayDeque<Runnable>q=new ArrayDeque<>();public void execute(Runnable r){q.add(r);}void run(){assertFalse(q.isEmpty());q.remove().run();}}
    static class F{final A a=new A();final Q work=new Q(),ui=new Q();final ConsultationViewModel vm;F(){this(true,true);}F(boolean dept){this(dept,true);}F(boolean dept,boolean student){vm=new ConsultationViewModel(RuntimeEnvironment.getApplication(),a,work,ui,student,dept,student?null:"OFF-GCC");}ConsultationViewModel.State s(){return vm.state().getValue();}void run(){work.run();ui.run();}void runAll(){while(!work.q.isEmpty()||!ui.q.isEmpty()){if(!work.q.isEmpty())work.run();if(!ui.q.isEmpty())ui.run();}}void load(){vm.ensureLoaded();run();}}
    static class A implements ConsultationViewModel.Actions{
        boolean current=true;int writes,reads;String after,actor=id(50);Exception error;Booking booking;Change change;SlotDraft savedDraft;SlotPage page=new SlotPage(List.of(),null,null);Appointment canonical=ap("booked",id(1));
        public String accountId(){return actor;}
        public void requireCurrent(){if(!current)throw new IllegalStateException();}public void invalidate(){}
        public SlotPage slots(String o,String t,String id){reads++;after=id;return page;}public AppointmentPage appointments(String id){return new AppointmentPage(List.of(canonical),null);}
        public Appointment appointment(String id){return canonical;}public Slot slot(String o,String id){return ConsultationViewModelTest.slot(1,1);}public EventPage events(String id,String t,String a){return new EventPage(List.of(),null,null);}
        public Booking booking(Slot s,Booking p){return new Booking(id(50),s,UUID.randomUUID().toString());}public Change change(Appointment a,Slot s,String r,Change p){return new Change(id(50),a,s,r,UUID.randomUUID().toString());}
        public SlotDraft draft(Slot s,String start,String end,int c,boolean active,SlotDraft p){return new SlotDraft(id(50),null,0,"OFF-GCC",id(50),start,end,c,active,UUID.randomUUID().toString());}
        public Appointment book(Booking c)throws Exception{writes++;booking=c;if(error!=null)throw error;canonical=ap("booked",c.slot.id);return canonical;}
        public Appointment change(Change c)throws Exception{writes++;change=c;if(error!=null)throw error;canonical=ap(c.target==null?"cancelled":"booked",c.target==null?c.original.slotId:c.target.id);return canonical;}
        public Slot save(SlotDraft c)throws Exception{writes++;savedDraft=c;if(error!=null)throw error;return ConsultationViewModelTest.slot(1,1);}
    }
}
