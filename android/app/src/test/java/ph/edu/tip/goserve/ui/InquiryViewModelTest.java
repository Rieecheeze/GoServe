package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.SavedStateHandle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.domain.InquiryData;
import ph.edu.tip.goserve.domain.Office;
import ph.edu.tip.goserve.domain.OfficeStatus;
import ph.edu.tip.goserve.domain.Role;
import ph.edu.tip.goserve.domain.SessionProfile;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=Application.class)
public final class InquiryViewModelTest {
    static final String ACCOUNT="00000000-0000-4000-8000-000000000001",THREAD="00000000-0000-4000-8000-000000000002",TIME="2026-10-22T00:00:00Z";
    @Test public void cannotSkipAnUnreadMessageWindow() {
        Fixture f=new Fixture(); f.actions.messages=new InquiryData.MessagePage(THREAD,List.of(message(1),message(2)),2L);
        f.open(); f.vm.loadMore(); assertEquals(0,f.work.size());
        f.vm.setVisible(true); f.vm.displayed(f.state().page,List.of(1L)); f.run();
        f.vm.loadMore(); assertEquals(0,f.work.size());
        f.vm.displayed(f.state().page,List.of(2L)); f.run(); f.vm.loadMore(); assertEquals(1,f.work.size());
    }
    @Test public void aPageGapIsNotEvidenceOfReadingMissingMessages() {
        Fixture f=new Fixture(); f.actions.messages=new InquiryData.MessagePage(THREAD,List.of(message(3)),null);
        f.open(); f.vm.setVisible(true); f.vm.displayed(f.state().page,List.of(3L));
        assertEquals(0,f.work.size()); assertTrue(f.actions.reads.isEmpty());
    }
    @Test public void canonicalReadBaselineAllowsHistoryPagingWithoutAnotherReadWrite() {
        Fixture f=new Fixture(); f.actions.baseline=50;
        f.actions.messages=new InquiryData.MessagePage(THREAD,List.of(message(1),message(2)),2L);
        f.open(); assertEquals(50,f.state().readThrough);
        f.vm.setVisible(true); f.vm.displayed(f.state().page,List.of(1L,2L)); assertEquals(0,f.work.size());
        f.vm.loadMore(); assertEquals(1,f.work.size()); assertTrue(f.actions.reads.isEmpty());
    }
    @Test public void explicitLocalDiscardDoesNotCancelOrRepeatAnUnconfirmedSend() {
        Fixture f=new Fixture(); f.open(); f.vm.editBody("Possibly sent"); f.actions.failSend=true;
        f.vm.send(); assertTrue(f.vm.mutationPending()); f.vm.discardLocalDraft(); assertEquals("Possibly sent",f.state().body);
        f.run(); assertTrue(f.state().uncertain); f.vm.discardLocalDraft();
        assertFalse(f.vm.mutationPending()); assertEquals("",f.state().body); assertEquals(1,f.actions.sent.size());
        assertEquals(0,f.work.size());
    }
    @Test public void canonicalLoadDoesNotMarkMessagesReadAndGlobalCountsDoNotUsePageSize() {
        Fixture f=new Fixture(); f.vm.conversation(THREAD,null); assertEquals(1,f.work.size()); f.run();
        assertEquals(2,f.state().page.items.size()); assertTrue(f.actions.reads.isEmpty()); assertEquals(80,f.state().counts.messages);
        assertSame(f.actions.messages,f.state().page);
    }
    @Test public void onlyContiguousVisibleRowsAdvanceAndDetachCancelsQueuedReadEvidence() {
        Fixture f=new Fixture(); f.open(); InquiryData.MessagePage page=f.state().page;
        f.vm.displayed(page,List.of(1L,2L)); assertEquals(0,f.work.size());
        f.vm.setVisible(true); f.vm.displayed(page,List.of(2L)); assertEquals(0,f.work.size());
        f.vm.displayed(page,List.of(1L)); f.vm.setVisible(false); f.run(); assertTrue(f.actions.reads.isEmpty());
        f.vm.setVisible(true); f.vm.displayed(page,List.of(1L)); f.run(); assertEquals(List.of(1L),f.actions.reads);
        f.vm.displayed(page,List.of(2L)); f.run(); assertEquals(List.of(1L,2L),f.actions.reads);
    }
    @Test public void successfulReadRefreshesIndependentUnreadTotals() {
        Fixture f=new Fixture(); f.open(); f.vm.setVisible(true); f.actions.total=79;
        f.vm.displayed(f.state().page,List.of(1L)); f.run(); assertEquals(79,f.state().counts.messages);
    }
    @Test public void previousPageEvidenceCannotMarkReplacementPageRead() {
        Fixture f=new Fixture(); f.open(); InquiryData.MessagePage old=f.state().page;
        f.actions.messages=new InquiryData.MessagePage(THREAD,List.of(message(3)),null); f.vm.refresh(); f.run();
        f.vm.setVisible(true); f.vm.displayed(old,List.of(1L,2L)); assertEquals(0,f.work.size()); assertTrue(f.actions.reads.isEmpty());
    }
    @Test public void failurePreservesExactBodyAndRetryKeyWhileAnEditGetsAnotherKey() {
        Fixture f=new Fixture(); f.open(); f.vm.editBody("Question exactly as entered"); f.actions.failSend=true;
        f.vm.send(); f.run(); assertEquals("Question exactly as entered",f.state().body); String first=f.actions.sent.get(0).operationKey;
        f.vm.send(); f.run(); assertEquals(first,f.actions.sent.get(1).operationKey);
        f.vm.editBody("Changed question"); f.vm.send(); f.run(); assertNotEquals(first,f.actions.sent.get(2).operationKey);
        f.actions.failSend=false; f.vm.send(); f.run(); assertEquals("",f.state().body); assertEquals(1,f.work.size()); f.run();
        assertEquals(2,f.state().page.items.size());
    }
    @Test public void accountChangeBeforeQueuedSuccessOrErrorErasesCanonicalAndDraftState() {
        for(boolean failure:new boolean[]{false,true}) {
            Fixture f=new Fixture(); f.open(); f.vm.editBody("Private draft"); f.actions.failLoad=failure; f.vm.refresh(); f.work.run();
            f.actions.current=false; f.ui.run(); assertTrue(f.state().blocked); assertEquals("",f.state().body); assertNull(f.state().page);
            assertEquals("STALE_ACCOUNT",f.state().error); assertTrue(f.saved.keys().isEmpty());
        }
    }
    @Test public void repeatedInvalidationsCoalesceWhileLoadIsRunning() {
        Fixture f=new Fixture(); f.vm.inbox(); f.vm.refresh(); f.vm.refresh(); f.run(); assertEquals(1,f.work.size()); f.run();
        assertEquals(2,f.actions.loads); assertEquals(1,f.state().threads.size());
    }
    @Test public void restoredAttemptRemainsUnsentAndIsBoundToItsOriginalAccount() {
        Fixture f=new Fixture(); f.open(); f.vm.editBody("Retained body"); f.actions.failSend=true; f.vm.send(); f.run();
        Queue work=new Queue(),ui=new Queue(); InquiryViewModel restored=new InquiryViewModel(RuntimeEnvironment.getApplication(),f.actions,work,ui,f.saved);
        assertEquals("Retained body",restored.state().getValue().body); assertEquals(0,work.size());
        restored.conversation(THREAD,null); work.run(); ui.run(); assertEquals(1,f.actions.sent.size());
        restored.send(); work.run(); ui.run(); assertEquals(f.actions.sent.get(0).operationKey,f.actions.sent.get(1).operationKey);
        f.actions.account="00000000-0000-4000-8000-000000000099";
        InquiryViewModel other=new InquiryViewModel(RuntimeEnvironment.getApplication(),f.actions,new Queue(),new Queue(),f.saved);
        assertEquals("",other.state().getValue().body); assertTrue(f.saved.keys().isEmpty());
    }
    @Test public void forgedOfficeCannotCreateANewInquiryAndNewInquiryUsesCanonicalDirectory() {
        Fixture f=new Fixture(); f.vm.conversation(null,null); f.run(); f.vm.selectOffice("FORGED"); f.vm.editBody("Question"); f.vm.send();
        assertEquals("VALIDATION_FAILED",f.state().error); assertTrue(f.actions.sent.isEmpty());
        f.vm.selectOffice("DEP-CCS"); f.vm.send(); f.run(); assertEquals("DEP-CCS",f.actions.sent.get(0).officeId); assertNull(f.actions.sent.get(0).threadId);
    }
    @Test public void nativeScopeRejectsFacultyAndTellerButAllowsStudentAndCcsSecretary() {
        assertTrue(InquiryViewModel.permitted(profile(Role.STUDENT,null,null)));
        assertTrue(InquiryViewModel.permitted(profile(Role.STAFF,"DEP-CCS","secretary")));
        assertTrue(InquiryViewModel.permitted(profile(Role.STAFF,"OFF-GCC","guidance")));
        assertFalse(InquiryViewModel.permitted(profile(Role.STAFF,"DEP-CCS","faculty")));
        assertFalse(InquiryViewModel.permitted(profile(Role.STAFF,"DEP-CCS",null)));
        assertFalse(InquiryViewModel.permitted(profile(Role.TELLER,"OFF-CASH",null)));
    }
    static SessionProfile profile(Role role,String office,String capability) { return new SessionProfile(ACCOUNT,"","",role,office,capability,false,1,true,"v","v","home",null); }
    static InquiryData.ThreadRecord thread() { return new InquiryData.ThreadRecord(THREAD,ACCOUNT,"DEP-CCS",null,TIME,1,2); }
    static InquiryData.Message message(long sequence) { return new InquiryData.Message("message"+sequence,THREAD,ACCOUNT,"Canonical "+sequence,TIME,sequence); }
    static final class Queue implements Executor { final ArrayDeque<Runnable> jobs=new ArrayDeque<>(); public void execute(Runnable r){jobs.add(r);} int size(){return jobs.size();} void run(){jobs.remove().run();} }
    static final class Fixture {
        final Fake actions=new Fake(); final Queue work=new Queue(),ui=new Queue(); final SavedStateHandle saved=new SavedStateHandle();
        final InquiryViewModel vm=new InquiryViewModel(RuntimeEnvironment.getApplication(),actions,work,ui,saved);
        InquiryViewModel.State state(){return vm.state().getValue();} void run(){work.run();ui.run();} void open(){vm.conversation(THREAD,null);run();}
    }
    static final class Fake implements InquiryViewModel.Actions {
        String account=ACCOUNT; boolean current=true,failSend,failLoad; int loads,key; long total=80,baseline;
        final List<Long> reads=new ArrayList<>(); final List<InquiryData.SendDraft> sent=new ArrayList<>();
        InquiryData.MessagePage messages=new InquiryData.MessagePage(THREAD,List.of(message(1),message(2)),null);
        public boolean allowed(){return true;} public String accountId(){return account;} public String officeId(){return null;} public boolean student(){return true;}
        public void requireCurrent(){if(!current)throw new IllegalStateException("account changed");}
        public List<Office> offices() throws Exception { if(failLoad)throw new java.io.IOException("private server"); return List.of(new Office("DEP-CCS","CCS","",OfficeStatus.OPEN,"","",false)); }
        public InquiryData.ThreadPage threads(String time,String id){loads++;return new InquiryData.ThreadPage(List.of(thread()),null,null);}
        public InquiryData.ThreadRecord open(String id){return thread();} public InquiryData.MessagePage messages(String id,long after){return messages;}
        public InquiryData.UnreadCounts counts(List<String> ids){return new InquiryData.UnreadCounts(60,total,Map.of(THREAD,2L));}
        public InquiryData.SendDraft draft(String t,String o,String b,InquiryData.SendDraft previous){
            String operation=previous!=null && java.util.Objects.equals(t,previous.threadId) && java.util.Objects.equals(o,previous.officeId) && b.equals(previous.body)?previous.operationKey:"key"+(++key);
            return new InquiryData.SendDraft(account,t,o,b,operation);
        }
        public InquiryData.SendResult send(InquiryData.SendDraft draft) throws Exception {sent.add(draft);if(failSend)throw new java.io.IOException("private server");return new InquiryData.SendResult(thread(),message(2));}
        public InquiryData.ReadCursor read(InquiryData.MessagePage page,long through){reads.add(through);return new InquiryData.ReadCursor(THREAD,account,TIME,through);}
        public long readThrough(String id){return baseline;}
    }
}
