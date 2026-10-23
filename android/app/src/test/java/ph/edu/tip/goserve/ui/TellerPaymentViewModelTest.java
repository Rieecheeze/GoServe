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
import ph.edu.tip.goserve.domain.PaymentStatus;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=Application.class)
public final class TellerPaymentViewModelTest {
    private static final String ACTOR="00000000-0000-4000-8000-000000000001", KEY="00000000-0000-4000-8000-000000000009";

    @Test public void searchRunsOffMainAndPublishesOnlyAfterQueuedLeaseCheck() {
        Fixture f=new Fixture(); f.vm.reference(" actual-reference "); f.vm.search();
        assertEquals(TellerPaymentViewModel.Mode.LOOKING_UP,f.state().mode); assertEquals(0,f.actions.lookups);
        f.work.run(); assertEquals(1,f.actions.lookups); assertEquals("actual-reference",f.actions.references.get(0));
        assertNull(f.state().review); f.ui.run(); assertEquals(TellerPaymentViewModel.Mode.FOUND,f.state().mode);
        assertSame(f.actions.lookup,f.state().review); assertFalse(f.state().received); assertFalse(f.state().canConfirm());
    }

    @Test public void blankReferenceShowsValidationWithoutIo() {
        Fixture f=new Fixture(); f.vm.reference("  "); f.vm.search();
        assertEquals("VALIDATION_FAILED",f.state().error); assertEquals(0,f.work.size()); assertEquals(0,f.actions.lookups);
    }

    @Test public void studentOrUnreadyTellerCannotQueueAnyPaymentWork() {
        Fixture f=new Fixture(false); f.vm.reference("reference"); f.vm.search(); f.vm.confirm();
        assertEquals(TellerPaymentViewModel.Mode.UNAVAILABLE,f.state().mode); assertEquals("FORBIDDEN",f.state().error);
        assertEquals(0,f.work.size()); assertEquals(0,f.actions.lookups); assertEquals(0,f.actions.confirms);
    }

    @Test public void explicitReceiptAcknowledgementIsRequiredAndNeverPreselected() {
        Fixture f=new Fixture(); f.lookup(); assertNotNull(f.state().review); assertFalse(f.state().received);
        f.vm.confirm(); assertEquals(0,f.actions.drafts); assertEquals(0,f.actions.confirms);
        f.vm.received(true); assertFalse("Receipt acknowledgement must still require the separate review step",f.state().canConfirm());
        f.vm.confirm(); assertEquals(0,f.actions.drafts); assertEquals(0,f.work.size());
        f.vm.received(false); assertFalse(f.state().canConfirm());
    }

    @Test public void oneExplicitConfirmationRetainsInputBeforeIoAndRejectsDoubleTap() {
        Fixture f=new Fixture(); f.lookup(); f.vm.received(true); f.vm.reviewConfirmation(); f.vm.confirm(); f.vm.confirm();
        assertEquals(1,f.actions.drafts); assertEquals(0,f.actions.confirms); assertNotNull(f.actions.held);
        assertEquals(TellerPaymentViewModel.Mode.CONFIRMING,f.state().mode); assertFalse(f.state().canConfirm());
        f.work.run(); assertEquals(1,f.actions.confirms); f.ui.run();
        assertEquals(TellerPaymentViewModel.Mode.CONFIRMED,f.state().mode); assertEquals(PaymentStatus.PAID,f.state().review.status);
        assertFalse(f.state().canConfirm()); assertFalse(f.state().received);
    }

    @Test public void uncertainFailureKeepsReviewAcknowledgementAndSameOperationForExplicitRetry() {
        Fixture f=new Fixture(); f.lookup(); f.vm.received(true); f.vm.reviewConfirmation(); f.actions.failure=new java.io.IOException("private transport detail"); f.vm.confirm();
        f.work.run(); f.ui.run(); assertEquals("NETWORK_ERROR",f.state().error); assertTrue(f.state().canConfirm());
        PaymentData.CashConfirmation first=f.actions.held; f.actions.failure=null; f.vm.confirm(); f.work.run(); f.ui.run();
        assertSame(first,f.actions.inputs.get(1)); assertEquals(KEY,f.actions.inputs.get(1).operationKey); assertEquals(1,f.actions.drafts);
        assertEquals(TellerPaymentViewModel.Mode.CONFIRMED,f.state().mode);
    }

    @Test public void conflictInvalidatesReviewAndRequiresNewLookupAndAcknowledgement() {
        for(String code:List.of("STALE_VERSION","QUOTE_STALE","ALREADY_PAID","INVALID_STATE","NOT_FOUND","CONFIGURATION_REQUIRED")) {
            Fixture f=new Fixture(); f.lookup(); f.vm.received(true); f.vm.reviewConfirmation(); f.actions.failure=new PaymentRepository.WorkflowException(code); f.vm.confirm();
            f.work.run(); f.ui.run(); assertEquals(code,f.state().error); assertNull(f.state().review); assertFalse(f.state().received);
            f.vm.confirm(); assertEquals(1,f.actions.confirms); f.actions.failure=null; f.vm.search(); f.work.run(); f.ui.run();
            assertNotNull(f.state().review); assertFalse(f.state().canConfirm()); assertEquals(1,f.actions.confirms);
        }
    }

    @Test public void terminalPaymentLookupCannotBeConfirmed() {
        for(PaymentStatus status:List.of(PaymentStatus.PAID,PaymentStatus.FAILED,PaymentStatus.EXPIRED,PaymentStatus.CANCELLED)) {
            Fixture f=new Fixture(); f.actions.lookup=review(status,2); f.lookup(); assertNotNull(f.state().review);
            f.vm.received(true); f.vm.reviewConfirmation(); f.vm.confirm(); assertFalse(f.state().canConfirm()); assertFalse(f.state().received); assertEquals(0,f.actions.confirms);
        }
    }

    @Test public void changedReferenceAndEditDiscardReviewedEligibilityWithoutWriting() {
        Fixture f=new Fixture(); f.lookup(); f.vm.received(true); f.vm.reviewConfirmation(); f.vm.editReference();
        assertEquals(TellerPaymentViewModel.Mode.SEARCH,f.state().mode); assertNull(f.state().review); assertFalse(f.state().received);
        f.vm.reference("changed"); assertEquals("changed",f.state().reference); assertEquals(0,f.actions.confirms);
    }

    @Test public void changingReferenceWhileLookupRunsRejectsOldResult() {
        Fixture f=new Fixture(); f.vm.reference("first"); f.vm.search(); f.work.run(); f.vm.reference("second"); f.ui.run();
        assertEquals("second",f.state().reference); assertEquals(TellerPaymentViewModel.Mode.SEARCH,f.state().mode); assertNull(f.state().review);
    }

    @Test public void queuedSuccessAndErrorAreClearedWhenTheAccountChanges() {
        for(boolean failure:new boolean[]{false,true}) {
            Fixture f=new Fixture(); f.vm.reference("reference");
            if(failure)f.actions.failure=new java.io.IOException("private");
            f.vm.search(); f.work.run(); f.actions.current=false; f.ui.run();
            assertEquals(TellerPaymentViewModel.Mode.UNAVAILABLE,f.state().mode); assertEquals("STALE_ACCOUNT",f.state().error);
            assertEquals("",f.state().reference); assertNull(f.state().review);
        }
    }

    @Test public void accountInvalidationPreventsQueuedIoAndClearsDraft() {
        Fixture f=new Fixture(); f.vm.reference("reference"); f.vm.search(); f.vm.invalidate(); f.work.run();
        assertEquals(0,f.actions.lookups); assertEquals(TellerPaymentViewModel.Mode.UNAVAILABLE,f.state().mode);
        assertEquals("",f.state().reference); assertNull(f.state().review);
    }

    @Test public void accountChangeAfterConfirmationCannotPublishSuccessToAnotherSession() {
        Fixture f=new Fixture(); f.lookup(); f.vm.received(true); f.vm.reviewConfirmation(); f.vm.confirm(); f.work.run(); f.actions.current=false; f.ui.run();
        assertEquals(TellerPaymentViewModel.Mode.UNAVAILABLE,f.state().mode); assertNull(f.state().review); assertEquals("",f.state().reference);
    }

    @Test public void realtimeRefreshOnlyReadsAndClearsPriorCashAcknowledgement() {
        Fixture f=new Fixture(); f.lookup(); f.vm.received(true); f.vm.reviewConfirmation(); f.actions.lookup=review(PaymentStatus.PAID,2); f.vm.refreshAfterChange();
        assertEquals(TellerPaymentViewModel.Mode.LOOKING_UP,f.state().mode); assertFalse(f.state().received); f.work.run(); f.ui.run();
        assertEquals(PaymentStatus.PAID,f.state().review.status); assertEquals(0,f.actions.confirms); assertEquals(2,f.actions.lookups);
    }

    @Test public void repeatedReadInvalidationsDuringLookupCoalesceIntoOneFollowup() {
        Fixture f=new Fixture(); f.vm.reference("reference"); f.vm.search(); f.vm.refreshAfterChange(); f.vm.refreshAfterChange();
        f.work.run(); f.ui.run(); assertEquals(TellerPaymentViewModel.Mode.LOOKING_UP,f.state().mode); assertEquals(1,f.work.size());
        f.work.run(); f.ui.run(); assertEquals(2,f.actions.lookups); assertNotNull(f.state().review); assertEquals(0,f.actions.confirms);
    }

    @Test public void detachingObserverOrResumingDoesNotResendConfirmation() {
        Fixture f=new Fixture(); f.lookup(); f.vm.received(true); f.vm.reviewConfirmation(); f.vm.confirm();
        androidx.lifecycle.Observer<TellerPaymentViewModel.State> observer=value->{};
        f.vm.state().observeForever(observer); f.vm.state().removeObserver(observer);
        f.vm.state().observeForever(observer); f.work.run(); f.ui.run(); f.vm.state().removeObserver(observer);
        assertEquals(1,f.actions.confirms); assertEquals(KEY,f.actions.inputs.get(0).operationKey);
    }

    @Test public void clearedViewModelCannotPerformQueuedWorkOrPublish() {
        Fixture f=new Fixture(); f.vm.reference("reference"); f.vm.search();
        androidx.lifecycle.ViewModelStore store=new androidx.lifecycle.ViewModelStore(); store.put("teller",f.vm); store.clear(); f.work.run();
        assertEquals(0,f.actions.lookups); assertNull(f.state().review); assertEquals("",f.state().reference);
    }

    @Test public void canonicalRefreshRetainsUncertainOperationKeyForUnchangedPayment() {
        Fixture f=new Fixture(); f.lookup(); f.vm.received(true); f.vm.reviewConfirmation(); f.actions.failure=new java.io.IOException("response lost"); f.vm.confirm();
        f.work.run(); f.ui.run(); String held=f.actions.inputs.get(0).operationKey;
        f.actions.failure=null; f.vm.refreshAfterChange(); f.work.run(); f.ui.run();
        assertFalse(f.state().received); f.vm.received(true); f.vm.reviewConfirmation(); f.vm.confirm(); f.work.run(); f.ui.run();
        assertEquals("A read/rotation refresh must not change an uncertain write's identity",held,f.actions.inputs.get(1).operationKey);
    }

    @Test public void serverPermissionDenialClearsTheFlowAndPreventsAnotherAttempt() {
        for(boolean confirming:new boolean[]{false,true}) {
            Fixture f=new Fixture();
            if(confirming) { f.lookup(); f.vm.received(true); f.vm.reviewConfirmation(); }
            f.actions.failure=new PaymentRepository.WorkflowException("FORBIDDEN");
            if(confirming)f.vm.confirm(); else { f.vm.reference("reference"); f.vm.search(); }
            f.work.run(); f.ui.run(); assertEquals(TellerPaymentViewModel.Mode.UNAVAILABLE,f.state().mode);
            assertEquals("FORBIDDEN",f.state().error); assertEquals("",f.state().reference); assertNull(f.state().review);
            f.vm.reference("another"); f.vm.search(); f.vm.confirm(); assertEquals(0,f.work.size());
        }
    }

    @Test public void paymentIdEntryLoadsCanonicalReferenceOffMainExactlyOnce() {
        Fixture f=new Fixture(); String payment=review(PaymentStatus.PENDING,1).paymentId;
        f.vm.ensurePayment(payment); f.vm.ensurePayment(payment);
        assertEquals(TellerPaymentViewModel.Mode.LOOKING_UP,f.state().mode); assertNull(f.state().review); assertEquals(0,f.actions.idLookups);
        f.work.run(); f.ui.run(); assertEquals(1,f.actions.idLookups); assertEquals(0,f.actions.lookups);
        assertEquals("actual-reference",f.state().reference); assertNotNull(f.state().review); assertFalse(f.state().received);
    }
    @Test public void failedIdEntryRetriesTheSameReadAndNotAnEmptyReference() {
        Fixture f=new Fixture(); f.actions.failure=new java.io.IOException("private"); f.vm.ensurePayment(review(PaymentStatus.PENDING,1).paymentId);
        f.work.run(); f.ui.run(); assertEquals("NETWORK_ERROR",f.state().error);
        f.actions.failure=null; f.vm.search(); f.work.run(); f.ui.run();
        assertEquals(2,f.actions.idLookups); assertEquals(0,f.actions.lookups); assertNotNull(f.state().review); assertEquals(0,f.actions.confirms);
    }
    @Test public void rotatedIdRouteDoesNotOverwriteAnExplicitlyEditedReference() {
        Fixture f=new Fixture(); String payment=review(PaymentStatus.PENDING,1).paymentId; f.vm.ensurePayment(payment); f.work.run(); f.ui.run();
        f.vm.editReference(); f.vm.reference("manual-reference"); f.vm.ensurePayment(payment);
        assertEquals("manual-reference",f.state().reference); assertNull(f.state().review); assertEquals(0,f.work.size()); assertEquals(1,f.actions.idLookups);
    }
    @Test public void idResponseCannotPublishAfterAccountReplacement() {
        Fixture f=new Fixture(); f.vm.ensurePayment(review(PaymentStatus.PENDING,1).paymentId); f.work.run(); f.actions.current=false; f.ui.run();
        assertEquals(TellerPaymentViewModel.Mode.UNAVAILABLE,f.state().mode); assertEquals("",f.state().reference); assertNull(f.state().review);
    }
    @Test public void paidIdEntryIsAReadOnlyReceiptAndCannotConfirmAgain() {
        Fixture f=new Fixture(); f.actions.lookup=review(PaymentStatus.PAID,2); f.vm.ensurePayment(f.actions.lookup.paymentId); f.work.run(); f.ui.run();
        assertEquals(PaymentStatus.PAID,f.state().review.status); f.vm.received(true); f.vm.reviewConfirmation(); f.vm.confirm(); assertFalse(f.state().canConfirm()); assertEquals(0,f.actions.confirms);
    }

    @Test public void reviewStepPerformsNoIoOrDraftAndBackRequiresAcknowledgementAgain() {
        Fixture f=new Fixture(); f.lookup(); f.vm.reviewConfirmation();
        assertEquals(TellerPaymentViewModel.Mode.FOUND,f.state().mode);
        f.vm.received(true); assertTrue(f.state().canReview()); f.vm.reviewConfirmation();
        assertTrue(f.state().canConfirm()); assertFalse(f.state().canReview());
        assertEquals(0,f.actions.drafts); assertEquals(0,f.actions.confirms); assertEquals(0,f.work.size());
        f.vm.backToPayment(); assertEquals(TellerPaymentViewModel.Mode.FOUND,f.state().mode);
        assertFalse(f.state().received); f.vm.confirm(); assertEquals(0,f.actions.drafts);
    }

    @Test public void returningFromUncertainReviewRetainsTheOriginalOperationIdentity() {
        Fixture f=new Fixture(); f.lookup(); f.vm.received(true); f.vm.reviewConfirmation();
        f.actions.failure=new java.io.IOException("response lost"); f.vm.confirm(); f.work.run(); f.ui.run();
        PaymentData.CashConfirmation first=f.actions.inputs.get(0); f.vm.backToPayment();
        assertFalse(f.state().received); f.vm.received(true); f.vm.reviewConfirmation();
        f.actions.failure=null; f.vm.confirm(); f.work.run(); f.ui.run();
        assertSame(first,f.actions.inputs.get(1)); assertEquals(1,f.actions.drafts);
    }

    private static PaymentData.CashReview review(PaymentStatus status,long version) {
        return new PaymentData.CashReview(ACTOR,"00000000-0000-4000-8000-000000000002","00000000-0000-4000-8000-000000000003",
                "actual-reference","OFF-REG","Actual service",new BigDecimal("123456789.0123456789"),"PHP",status,version,
                status==PaymentStatus.PAID?"2026-10-20T01:00:00Z":null);
    }
    private static final class Fixture {
        final Fake actions=new Fake(); final Queue work=new Queue(),ui=new Queue(); final TellerPaymentViewModel vm;
        Fixture() { this(true); }
        Fixture(boolean allowed) { actions.allowed=allowed; vm=new TellerPaymentViewModel(RuntimeEnvironment.getApplication(),actions,work,ui); }
        TellerPaymentViewModel.State state() { return vm.state().getValue(); }
        void lookup() { vm.reference("actual-reference"); vm.search(); work.run(); ui.run(); }
    }
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>(); public void execute(Runnable task) { tasks.add(task); }
        void run() { assertFalse("Expected queued work",tasks.isEmpty()); tasks.remove().run(); }
        int size() { return tasks.size(); }
    }
    private static final class Fake implements TellerPaymentViewModel.Actions {
        boolean allowed=true,current=true; int lookups,idLookups,drafts,confirms,invalidations; Exception failure;
        PaymentData.CashReview lookup=review(PaymentStatus.PENDING,1); PaymentData.CashConfirmation held;
        final List<String> references=new ArrayList<>(); final List<PaymentData.CashConfirmation> inputs=new ArrayList<>();
        public boolean allowed() { return allowed; }
        public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public void invalidate() { ++invalidations; }
        public PaymentData.CashReview lookup(String reference) throws Exception { ++lookups; references.add(reference); if(failure!=null)throw failure; return lookup; }
        public PaymentData.CashReview lookupById(String paymentId) throws Exception { ++idLookups; if(failure!=null)throw failure; return lookup; }
        public PaymentData.CashConfirmation draft(PaymentData.CashReview value,PaymentData.CashConfirmation previous) {
            ++drafts; String key=String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",8+drafts);
            held=previous==null?new PaymentData.CashConfirmation(value,key):previous; return held;
        }
        public PaymentData.CashReview confirm(PaymentData.CashConfirmation input) throws Exception {
            ++confirms; inputs.add(input); if(failure!=null)throw failure; return review(PaymentStatus.PAID,2);
        }
    }
}
