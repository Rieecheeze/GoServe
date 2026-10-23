package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.ViewModelStore;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.data.RequestRepository;
import ph.edu.tip.goserve.domain.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34,application=Application.class)
public final class PickupViewModelTest {
    static final String CODE="a".repeat(64);
    @Test public void studentLoadsOnlyCanonicalEligibilityBeforeTheExplicitClaimAction() {
        F f=new F(false); f.vm.ensureRequest(id(99)); assertEquals(PickupViewModel.Mode.LOADING,f.s().mode); assertEquals(0,f.a.reads); f.run();
        assertEquals(id(1),f.a.requested); assertEquals(PickupViewModel.Mode.AVAILABLE,f.s().mode); assertEquals(0,f.a.claims);
    }
    @Test public void absentPolicyAndUnpaidRequiredFeeCannotIssueAClaim() {
        F f=new F(false); f.a.policy=false; f.run(); assertEquals(PickupViewModel.Mode.BLOCKED,f.s().mode); f.vm.issue(); assertEquals(0,f.a.claims);
        F unpaid=new F(false); unpaid.a.fee=FeeDeclaration.REQUIRED; unpaid.run(); assertEquals("PAYMENT_REQUIRED",unpaid.s().error); assertEquals(0,unpaid.a.claims);
    }
    @Test public void readyStatusCannotReplaceAConfirmedPaymentRead() {
        F f=new F(false); f.a.fee=FeeDeclaration.REQUIRED; f.a.payment=paid(id(90)); f.run(); assertEquals(PickupViewModel.Mode.AVAILABLE,f.s().mode);
        F failed=new F(false); failed.a.paymentError=new java.io.IOException(); failed.run(); assertEquals(PickupViewModel.Mode.BLOCKED,failed.s().mode); assertNull(failed.s().claim);
    }
    @Test public void claimingIsSingleFlightAndDoesNotValidateOrCompleteAnything() {
        F f=loaded(false); f.vm.issue(); f.vm.issue(); assertEquals(1,f.work.q.size()); f.run();
        assertEquals(PickupViewModel.Mode.CLAIM,f.s().mode); assertEquals(CODE,f.s().claim.qrPayload); assertEquals(1,f.a.claims); assertEquals(0,f.a.validations); assertEquals(0,f.a.handovers);
    }
    @Test public void anExistingClaimCanBeRecheckedOnlyThroughAnotherExplicitServerClaimAction() {
        F f=loaded(false); f.vm.issue(); f.run(); f.vm.issue(); assertEquals(PickupViewModel.Mode.ISSUING,f.s().mode); f.run(); assertEquals(2,f.a.claims); assertEquals(PickupViewModel.Mode.CLAIM,f.s().mode);
    }
    @Test public void refreshRetainsAnUnchangedServerClaimButRetirementRemovesIt() {
        F f=loaded(false); f.vm.issue(); f.run(); DeliveryData.PickupClaim claim=f.s().claim; f.vm.refresh(); f.run(); assertSame(claim,f.s().claim);
        f.a.status=RequestStatus.COMPLETED; f.vm.refresh(); f.run(); assertNull(f.s().claim); assertEquals(PickupViewModel.Mode.BLOCKED,f.s().mode);
    }
    @Test public void staffStartsWithManualInputWithoutReadingOrIssuingAStudentClaim() {
        F f=new F(true); assertFalse(f.vm.student()); assertEquals(PickupViewModel.Mode.INPUT,f.s().mode); assertEquals(0,f.work.q.size()); assertEquals(0,f.a.reads); assertEquals(0,f.a.claims);
    }
    @Test public void aScanOnlyFillsTheInputAndUrlsNeverReachValidation() {
        F f=new F(true); f.vm.editCode(CODE); assertEquals(CODE,f.s().code); assertEquals(0,f.a.validations);
        f.vm.editCode("https://unsafe.example/"+CODE); f.vm.validateCode(); assertEquals("CODE_INVALID",f.s().error); assertEquals(0,f.a.validations);
    }
    @Test public void scannerIsClaimedOnceAndItsResultOnlyReturnsToInput() {
        F f=new F(true); assertTrue(f.vm.beginScan()); assertFalse(f.vm.beginScan()); assertEquals(PickupViewModel.Mode.SCANNING,f.s().mode);
        f.vm.scanResult(CODE); assertEquals(PickupViewModel.Mode.INPUT,f.s().mode); assertEquals(CODE,f.s().code); assertEquals(0,f.a.validations); assertEquals(0,f.a.handovers);
    }
    @Test public void cancellationAndMissingScannerPreserveManualInputAndNeverValidate() {
        F f=new F(true); f.vm.editCode(CODE); assertTrue(f.vm.beginScan()); f.vm.scanCancelled(); assertEquals(PickupViewModel.Mode.INPUT,f.s().mode); assertEquals(CODE,f.s().code);
        assertTrue(f.vm.beginScan()); f.vm.scannerUnavailable(); assertEquals("SCANNER_UNAVAILABLE",f.s().error); assertEquals(CODE,f.s().code); assertEquals(0,f.a.validations);
    }
    @Test public void scannerCannotPublishAfterAccountReplacementOrAcceptAnExternalUrl() {
        F f=new F(true); assertTrue(f.vm.beginScan()); f.vm.scanResult("https://example.test/"+CODE); assertEquals("CODE_INVALID",f.s().error); assertEquals("",f.s().code);
        assertTrue(f.vm.beginScan()); f.a.current=false; f.vm.scanResult(CODE); assertEquals(PickupViewModel.Mode.UNAVAILABLE,f.s().mode); assertEquals("",f.s().code);
    }
    @Test public void explicitValidationCreatesOnlyAReviewWithTheActualReference() {
        F f=reviewed(); assertEquals(PickupViewModel.Mode.REVIEW,f.s().mode); assertEquals("fixture-reference",f.s().request.reference); assertEquals(1,f.a.validations); assertEquals(0,f.a.handovers); assertEquals("OFF-REG",f.s().review.officeId);
    }
    @Test public void validationCannotCrossTheSelectedRequestOrOffice() {
        F f=new F(true); f.a.reviewRequest=id(99); f.vm.editCode(CODE); f.vm.validateCode(); f.run(); assertEquals(PickupViewModel.Mode.UNAVAILABLE,f.s().mode); assertNull(f.s().intent);
        F office=new F(true); office.a.reviewOffice="OFF-GCC"; office.vm.editCode(CODE); office.vm.validateCode(); office.run(); assertEquals(PickupViewModel.Mode.UNAVAILABLE,office.s().mode);
    }
    @Test public void returningToInputDoesNotHandoverAndRequiresAnotherValidation() {
        F f=reviewed(); f.vm.editReview(); assertEquals(PickupViewModel.Mode.INPUT,f.s().mode); assertNull(f.s().intent); f.vm.confirmHandover(); assertEquals(0,f.a.handovers);
    }
    @Test public void onlyExplicitHandoverCanPublishTheCanonicalCompletedResult() {
        F f=reviewed(); f.vm.confirmHandover(); f.vm.confirmHandover(); assertTrue(f.vm.mutationPending()); assertEquals(1,f.work.q.size()); f.run();
        assertEquals(1,f.a.handovers); assertEquals(PickupViewModel.Mode.COMPLETE,f.s().mode); assertEquals(RequestStatus.COMPLETED,f.s().request.status); assertFalse(f.vm.mutationPending()); assertEquals("",f.s().code);
    }
    @Test public void uncertainHandoverFreezesTheSameCommandUntilExplicitRetry() {
        F f=reviewed(); DeliveryData.Handover command=f.s().intent; f.a.handoverError=new java.io.IOException("Lost response"); f.vm.confirmHandover(); f.run();
        assertTrue(f.s().uncertain); assertTrue(f.vm.mutationPending()); f.vm.editCode("changed"); f.vm.editReview(); f.vm.refresh(); assertSame(command,f.s().intent); assertEquals(CODE,f.s().code);
        f.a.handoverError=null; f.vm.confirmHandover(); f.run(); assertSame(command,f.a.sent); assertEquals(PickupViewModel.Mode.COMPLETE,f.s().mode);
    }
    @Test public void expiredOrConsumedCodeCannotBecomeACompletedHandover() {
        for(String code:List.of("CODE_EXPIRED","CODE_CONSUMED","STALE_VERSION")) {
            F f=reviewed(); f.a.handoverError=new RequestRepository.WorkflowException(code); f.vm.confirmHandover(); f.run(); assertEquals(PickupViewModel.Mode.INPUT,f.s().mode); assertEquals(code,f.s().error); assertNull(f.s().intent); assertFalse(f.s().uncertain);
        }
    }
    @Test public void malformedPostWriteResultStaysUncertainInsteadOfResettingTheKey() {
        F f=reviewed(); f.a.handoverError=new RequestRepository.WorkflowException("VALIDATION_FAILED"); f.vm.confirmHandover(); f.run(); assertEquals(PickupViewModel.Mode.REVIEW,f.s().mode); assertTrue(f.s().uncertain); assertNotNull(f.s().intent);
    }
    @Test public void noncompletedOrCrossIdentityResultsNeverShowCompletion() {
        F f=reviewed(); f.a.completed=RequestStatus.READY_FOR_PICKUP_DOWNLOAD; f.vm.confirmHandover(); f.run(); assertTrue(f.s().uncertain); assertEquals(PickupViewModel.Mode.REVIEW,f.s().mode);
    }
    @Test public void accountReplacementBeforePublicationErasesThePrivateCodeAndReview() {
        F f=new F(true); f.vm.editCode(CODE); f.vm.validateCode(); f.work.run(); f.a.current=false; f.ui.run(); assertEquals(PickupViewModel.Mode.UNAVAILABLE,f.s().mode); assertEquals("",f.s().code); assertNull(f.s().review);
    }
    @Test public void clearingTheViewModelStopsLateClaimPublication() {
        F f=loaded(false); f.vm.issue(); f.work.run(); ViewModelStore store=new ViewModelStore(); store.put("pickup",f.vm); store.clear(); f.ui.run(); assertNull(f.s().claim); assertEquals(PickupViewModel.Mode.UNAVAILABLE,f.s().mode);
    }
    private static F loaded(boolean staff) { F f=new F(staff); if(!staff)f.run(); return f; }
    private static F reviewed() { F f=new F(true); f.vm.editCode(CODE); f.vm.validateCode(); f.run(); return f; }
    static String id(int n) { return String.format(Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    static PaymentData.Record paid(String actor) { return new PaymentData.Record(actor,id(8),id(1),PaymentMethod.CASH,PaymentStatus.PAID,BigDecimal.TEN,"PHP",1,id(91),"2026-10-22T01:00:00Z"); }
    static class F {
        final A a; final Q work=new Q(),ui=new Q(); final PickupViewModel vm;
        F(boolean staff) { a=new A(staff); vm=new PickupViewModel(RuntimeEnvironment.getApplication(),a,work,ui); vm.ensureRequest(id(1)); }
        PickupViewModel.State s() { return vm.state().getValue(); } void run() { work.run(); ui.run(); }
    }
    static class A implements PickupViewModel.Actions {
        final boolean staff; boolean current=true,policy=true; int reads,claims,validations,handovers; String requested,reviewRequest=id(1),reviewOffice="OFF-REG"; FeeDeclaration fee=FeeDeclaration.FREE;
        RequestStatus status=RequestStatus.READY_FOR_PICKUP_DOWNLOAD,completed=RequestStatus.COMPLETED; PaymentData.Record payment; Exception paymentError,handoverError; DeliveryData.Handover sent;
        A(boolean staff) { this.staff=staff; } public boolean allowed() { return true; } public String accountId() { return id(staff?91:90); } public String officeId() { return staff?"OFF-REG":null; } public void requireCurrent() { if(!current)throw new IllegalStateException("Changed account"); }
        RequestData.Record record(String id,RequestStatus status,long version) { return new RequestData.Record(id,"fixture-reference",id(90),"OFF-REG","physical","2026-10-22T01:00:00Z",version,status,new RequestData.Service(id(50),"OFF-REG","Configured service",1,fee,fee==FeeDeclaration.REQUIRED?BigDecimal.TEN:BigDecimal.ZERO,fee==FeeDeclaration.REQUIRED?"PHP":null,List.of("physical"),List.of(),true)); }
        public RequestData.Record request(String id) { ++reads; requested=id; return record(id(1),status,2); }
        public PaymentData.Record payment(String id) throws Exception { if(paymentError!=null)throw paymentError; return payment; }
        public RequestData.RequestOptions options(RequestData.Record r) { return new RequestData.RequestOptions(r.id,r.version,Map.of("pickup_claim",new RequestData.ActionOptions(policy,policy?null:"CONFIGURATION_REQUIRED"))); }
        public DeliveryData.PickupClaim claim(RequestData.Record r,RequestData.RequestOptions o) { ++claims; return new DeliveryData.PickupClaim(id(70),r.id,CODE,CODE,"2026-10-22T10:00:00Z"); }
        public DeliveryData.PickupReview validate(String code,String office) { ++validations; return new DeliveryData.PickupReview(id(80),accountId(),reviewOffice,"2026-10-22T10:00:00Z",record(reviewRequest,status,2)); }
        public DeliveryData.Handover draft(DeliveryData.PickupReview r,DeliveryData.Handover old) { return old==null?new DeliveryData.Handover(r,UUID.randomUUID().toString()):old; }
        public RequestData.Record handover(DeliveryData.Handover command) throws Exception { ++handovers; sent=command; if(handoverError!=null)throw handoverError; return record(id(1),completed,3); }
    }
    static class Q implements Executor { final ArrayDeque<Runnable> q=new ArrayDeque<>(); public void execute(Runnable r) { q.add(r); } void run() { assertFalse(q.isEmpty()); q.remove().run(); } }
}
