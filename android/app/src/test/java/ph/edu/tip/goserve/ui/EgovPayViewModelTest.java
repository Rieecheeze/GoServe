package ph.edu.tip.goserve.ui;

import android.app.Application;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.data.PaymentRepository;
import ph.edu.tip.goserve.domain.PaymentData;
import ph.edu.tip.goserve.domain.PaymentMethod;
import ph.edu.tip.goserve.domain.PaymentStatus;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=Application.class)
public final class EgovPayViewModelTest {
    static final String REQUEST="00000000-0000-4000-8000-000000000001", PAYMENT="00000000-0000-4000-8000-000000000002";
    static final String ACTOR="00000000-0000-4000-8000-000000000003",KEY="00000000-0000-4000-8000-000000000004";
    static final String URL="https://egovpay-pgi-dev.oueg.info/"+PAYMENT;

    @Test public void loadNeverCreatesCheckoutAndDuplicateTapCreatesOnlyOneOperation() {
        Fixture f=new Fixture(); f.load(); assertEquals(0,f.actions.starts); assertTrue(f.state().canStart());
        f.vm.start(); f.vm.start(); assertTrue(f.state().busy()); assertFalse(f.state().canLeave()); assertEquals(1,f.work.size());
        f.finish(); assertEquals(1,f.actions.starts); assertEquals(URL,f.state().checkoutUrl); assertFalse(f.state().paid());
        f.vm.start(); assertEquals(0,f.work.size());
    }

    @Test public void unknownMutationKeepsOperationKeyAndBlocksBackUntilExplicitRetryResolves() {
        Fixture f=new Fixture(); f.load(); f.actions.startFailure=new java.io.IOException("private transport detail");
        f.vm.start(); f.finish(); assertTrue(f.state().uncertain); assertFalse(f.state().canLeave()); assertTrue(f.state().canStart());
        f.actions.startFailure=null; f.vm.start(); f.finish();
        assertEquals(2,f.actions.inputs.size()); assertSame(f.actions.inputs.get(0),f.actions.inputs.get(1)); assertFalse(f.state().uncertain);
    }

    @Test public void refreshResultAloneCannotClaimPaidAndEveryRefreshReloadsCanonicalRow() {
        Fixture f=new Fixture(); f.load(); f.actions.providerStatus="paid"; f.vm.refresh(); f.finish();
        assertEquals(2,f.actions.refreshes); assertEquals(3,f.actions.loads); assertFalse(f.state().paid());
        f.actions.record=record(PaymentStatus.PAID); f.vm.refresh(); f.finish(); assertTrue(f.state().paid());
        assertFalse(f.state().canStart()); assertNull(f.state().checkoutUrl);
    }

    @Test public void accountChangeBeforePublicationErasesMoneyUrlAndHeldOperation() {
        Fixture f=new Fixture(); f.load(); f.vm.start(); f.work.run(); f.actions.current=false; f.ui.run();
        assertEquals(EgovPayViewModel.Mode.UNAVAILABLE,f.state().mode); assertNull(f.state().payment); assertNull(f.state().checkoutUrl);
        f.vm.start(); f.vm.refresh(); assertEquals(0,f.work.size());
    }

    @Test public void corruptCheckoutResponseRetainsSameKeyBecauseTheServerMayHaveCommitted() {
        Fixture f=new Fixture(); f.load(); f.actions.startFailure=new PaymentRepository.WorkflowException("VALIDATION_FAILED");
        f.vm.start(); f.finish(); assertTrue(f.state().uncertain); assertNull(f.state().checkoutUrl); assertFalse(f.state().paid());
        f.vm.start(); f.finish(); assertSame(f.actions.inputs.get(0),f.actions.inputs.get(1));
    }

    @Test public void failedRefreshDoesNotLeaveAPriorPaidClaimVisible() {
        Fixture f=new Fixture(); f.actions.record=record(PaymentStatus.PAID); f.load(); assertTrue(f.state().paid());
        f.actions.readFailure=new java.io.IOException("private"); f.vm.refresh(); f.finish();
        assertFalse(f.state().paid()); assertNull(f.state().payment); assertEquals("NETWORK_ERROR",f.state().error);
    }

    @Test public void providerFailureAndCancellationNeverCreateAFreshCheckoutAutomatically() {
        for(String status:List.of("unknown","failed","cancelled","pending")) {
            Fixture f=new Fixture(); f.load(); f.actions.providerStatus=status; f.vm.refresh(); f.finish();
            assertEquals(status,f.state().providerStatus); assertFalse(f.state().paid()); assertEquals(0,f.actions.starts);
        }
    }

    @Test public void refreshDuringMutationDoesNotCancelOrRepeatCheckout() {
        Fixture f=new Fixture(); f.load(); f.vm.start(); f.vm.refresh(); f.vm.refresh();
        assertEquals(1,f.work.size()); f.finish(); assertEquals(1,f.actions.starts); assertEquals(1,f.actions.refreshes);
    }

    @Test public void unavailableOrReplacedAccountCannotRunQueuedCheckoutWork() {
        Fixture denied=new Fixture(); denied.actions.allowed=false; denied.vm.ensureRequest(REQUEST);
        assertEquals(EgovPayViewModel.Mode.UNAVAILABLE,denied.state().mode); assertEquals(0,denied.work.size());
        Fixture changed=new Fixture(); changed.load(); changed.vm.start(); changed.actions.current=false;
        changed.vm.checkCurrentAccount(); changed.work.run(); assertEquals(0,changed.actions.starts); assertNull(changed.state().payment);
    }

    @Test public void executorReplayingAnAsyncTaskCannotCreateTwoCheckouts() {
        Fixture f=new Fixture(); f.load(); f.vm.start(); Runnable task=f.work.tasks.remove(); task.run(); task.run(); f.ui.run();
        assertEquals(1,f.actions.starts); assertEquals(0,f.ui.size());
    }

    @Test public void hostileCheckoutUrlNeverBecomesAvailableToBrowser() {
        Fixture f=new Fixture(); f.load(); f.actions.url="https://unreviewed.invalid/checkout"; f.vm.start(); f.finish();
        assertNull(f.vm.browserUrl()); assertEquals("VALIDATION_FAILED",f.state().error); assertTrue(f.state().uncertain);
    }

    @Test public void canonicalTerminalOutcomeResolvesUnknownMutationAndAllowsLeaving() {
        for(PaymentStatus status:List.of(PaymentStatus.FAILED,PaymentStatus.CANCELLED,PaymentStatus.EXPIRED)) {
            Fixture f=new Fixture(); f.load(); f.actions.startFailure=new java.io.IOException("response lost"); f.vm.start(); f.finish();
            assertFalse(f.state().canLeave()); f.actions.record=record(status); f.vm.refresh(); f.finish();
            assertTrue(f.state().canLeave()); assertFalse(f.state().uncertain); assertFalse(f.state().canStart()); assertFalse(f.state().paid());
        }
    }

    @Test public void initialPendingReadReconcilesProviderAndReloadsCanonicalRecord() {
        Fixture f=new Fixture(); f.load(); assertEquals(List.of("load","refresh","load"),f.actions.events);
        assertFalse(f.state().paid()); assertEquals(0,f.actions.starts);
    }

    @Test public void unresolvedCheckoutAllowsExplicitSafeReturnAndKeepsOneOperationForRetry() {
        Fixture f=new Fixture(); f.load(); f.actions.startFailure=new PaymentRepository.WorkflowException("CHECKOUT_UNCONFIRMED");
        f.vm.start(); assertFalse(f.state().canReturnToRequest()); f.finish();
        assertTrue(f.state().uncertain); assertTrue(f.state().canReturnToRequest()); assertFalse(f.state().paid());
        f.vm.start(); f.finish(); assertSame(f.actions.inputs.get(0),f.actions.inputs.get(1));
    }

    static PaymentData.Record record(PaymentStatus status) {
        return new PaymentData.Record(ACTOR,PAYMENT,REQUEST,PaymentMethod.EGOVPAY,status,new BigDecimal("123.45"),"PHP",status==PaymentStatus.PAID?2:1,null,status==PaymentStatus.PAID?"2026-10-23T00:00:00Z":null);
    }
    static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>();
        public void execute(Runnable task) { tasks.add(task); }
        void run() { tasks.remove().run(); }
        int size() { return tasks.size(); }
    }
    static final class Fake implements EgovPayViewModel.Actions {
        boolean current=true,allowed=true; int loads,starts,refreshes; String providerStatus="pending",url=URL;
        PaymentData.Record record=record(PaymentStatus.PENDING); Exception startFailure,readFailure;
        final List<PaymentData.Checkout> inputs=new ArrayList<>();
        final List<String> events=new ArrayList<>();
        public boolean allowed() { return allowed; }
        public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public void invalidate() {}
        public PaymentData.Record load(String requestId) throws Exception { loads++; events.add("load"); if(readFailure!=null)throw readFailure; return record; }
        public PaymentData.Checkout draft(PaymentData.Record payment,PaymentData.Checkout previous) { return previous==null?new PaymentData.Checkout(payment,KEY):previous; }
        public String start(PaymentData.Checkout input) throws Exception { starts++; inputs.add(input); if(startFailure!=null)throw startFailure; return url; }
        public String refresh(PaymentData.Record payment) { refreshes++; events.add("refresh"); return providerStatus; }
    }
    static final class Fixture {
        final Queue work=new Queue(),ui=new Queue(); final Fake actions=new Fake();
        final EgovPayViewModel vm=new EgovPayViewModel(RuntimeEnvironment.getApplication(),actions,work,ui);
        EgovPayViewModel.State state() { return vm.state().getValue(); }
        void load() { vm.ensureRequest(REQUEST); finish(); }
        void finish() { work.run(); ui.run(); }
    }
}
