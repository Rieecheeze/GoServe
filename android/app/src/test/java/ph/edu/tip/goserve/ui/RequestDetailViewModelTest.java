package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.ViewModelStore;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.data.RequestRepository;
import ph.edu.tip.goserve.data.PaymentRepository;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.PaymentData;
import ph.edu.tip.goserve.domain.FeeDeclaration;
import ph.edu.tip.goserve.domain.RequestStatus;
import ph.edu.tip.goserve.domain.PaymentStatus;
import ph.edu.tip.goserve.domain.PaymentMethod;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=Application.class)
public final class RequestDetailViewModelTest {
    @Test public void exactDetailLoadsOffMainAndReadsPaymentForTheCanonicalRequest() {
        Fixture f=new Fixture(); f.vm.ensureRequest(id(1)); assertTrue(f.state().busy()); assertEquals(0,f.actions.requests);
        f.work.run(); assertNull(f.state().request); f.ui.run(); assertEquals("canonical-reference",f.state().request.reference);
        assertEquals(id(1),f.actions.paymentRequest); assertEquals(1,f.actions.requests); assertEquals(1,f.actions.payments);
        assertEquals(RequestDetailViewModel.Mode.CONTENT,f.state().mode);
    }
    @Test public void restoredOrDifferentRouteArgumentsNeverRepeatOrReplaceThePinnedTarget() {
        Fixture f=new Fixture(); f.vm.ensureRequest(id(1)); f.vm.ensureRequest(id(2)); assertEquals(1,f.work.size()); f.run();
        f.vm.ensureRequest(id(1)); assertEquals(0,f.work.size()); assertEquals(id(1),f.state().requestId);
    }
    @Test public void missingRouteCannotQueueIoOrPretendThatARequestExists() {
        for(String route:new String[]{null,"","  "}) { Fixture f=new Fixture(); f.vm.ensureRequest(route); assertEquals("VALIDATION_FAILED",f.state().error); assertNull(f.state().request); assertEquals(0,f.work.size()); }
    }
    @Test public void paidMessageRequiresAnActualPaymentAndNeverAdvancesTheRequestStatus() {
        Fixture f=new Fixture(); f.load(); assertFalse(f.state().confirmedPayment());
        f.actions.payment=payment(); f.vm.refresh(); f.run(); assertTrue(f.state().confirmedPayment());
        assertEquals(RequestStatus.fromWire("Awaiting Payment"),f.state().request.status); assertEquals(2,f.actions.requests);
    }
    @Test public void paymentReadFailurePreservesTheRequestButCannotClaimPaid() {
        Fixture f=new Fixture(); f.actions.paymentError=new java.io.IOException("private transport details"); f.load();
        assertEquals(RequestDetailViewModel.Mode.CONTENT,f.state().mode); assertNotNull(f.state().request);
        assertNull(f.state().payment); assertEquals("NETWORK_ERROR",f.state().paymentError); assertFalse(f.state().confirmedPayment());
        f.actions.paymentError=null; f.actions.payment=payment(); f.vm.retry(); f.run(); assertTrue(f.state().confirmedPayment());
    }
    @Test public void failedRefreshMarksCachedDetailsAndClearsUnverifiedPaymentState() {
        Fixture f=new Fixture(); f.actions.payment=payment(); f.load(); assertTrue(f.state().confirmedPayment());
        f.actions.requestError=new java.io.IOException("private"); f.vm.refresh(); f.run();
        assertEquals(RequestDetailViewModel.Mode.ERROR,f.state().mode); assertNotNull(f.state().request); assertFalse(f.state().confirmedPayment());
        assertEquals("NETWORK_ERROR",f.state().error); assertNull(f.state().payment);
    }
    @Test public void notFoundClearsCachedDetailsInsteadOfShowingAPreviousRequest() {
        Fixture f=new Fixture(); f.load(); f.actions.requestError=new RequestRepository.WorkflowException("NOT_FOUND"); f.vm.refresh(); f.run();
        assertEquals("NOT_FOUND",f.state().error); assertNull(f.state().request); assertNull(f.state().payment);
    }
    @Test public void unreadyRoleAndServerRevocationPreventOperationalReads() {
        Fixture denied=new Fixture(false); denied.vm.ensureRequest(id(1)); assertEquals(RequestDetailViewModel.Mode.UNAVAILABLE,denied.state().mode); assertEquals(0,denied.work.size());
        for(boolean paymentFailure:new boolean[]{false,true}) {
            Fixture f=new Fixture(); if(paymentFailure)f.actions.paymentError=new PaymentRepository.WorkflowException("FORBIDDEN"); else f.actions.requestError=new RequestRepository.WorkflowException("FORBIDDEN");
            f.load(); assertEquals(RequestDetailViewModel.Mode.UNAVAILABLE,f.state().mode); assertNull(f.state().request); f.vm.refresh(); assertEquals(0,f.work.size());
        }
    }
    @Test public void accountLossBeforePaymentReadAndBeforePublicationClearsAllData() {
        Fixture early=new Fixture(); early.actions.revokeAfterRequest=true; early.vm.ensureRequest(id(1)); early.work.run(); early.ui.run();
        assertEquals(0,early.actions.payments); assertEquals("STALE_ACCOUNT",early.state().error); assertNull(early.state().request);
        for(boolean failed:new boolean[]{false,true}) {
            Fixture f=new Fixture(); if(failed)f.actions.requestError=new java.io.IOException("private"); f.vm.ensureRequest(id(1)); f.work.run(); f.actions.current=false; f.ui.run();
            assertEquals(RequestDetailViewModel.Mode.UNAVAILABLE,f.state().mode); assertNull(f.state().requestId); assertNull(f.state().request); assertNull(f.state().payment);
        }
    }
    @Test public void repeatedInvalidationsCoalesceIntoOneCanonicalFollowupRead() {
        Fixture f=new Fixture(); f.vm.ensureRequest(id(1)); f.vm.refresh(); f.vm.refresh(); f.run(); assertEquals(1,f.work.size()); f.run();
        assertEquals(2,f.actions.requests); assertEquals(2,f.actions.payments); assertEquals(RequestDetailViewModel.Mode.CONTENT,f.state().mode);
    }
    @Test public void clearedOrRevokedModelsCannotIssueQueuedReads() {
        Fixture f=new Fixture(); f.vm.ensureRequest(id(1)); f.actions.current=false; f.vm.checkCurrentAccount(); f.work.run(); assertEquals(0,f.actions.requests);
        Fixture closed=new Fixture(); closed.vm.ensureRequest(id(1)); ViewModelStore store=new ViewModelStore(); store.put("detail",closed.vm); store.clear(); closed.work.run(); assertEquals(0,closed.actions.requests);
    }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static RequestData.Record request() { return new RequestData.Record(id(1),"canonical-reference",id(90),"OFF-REG","physical","2026-10-21T01:00:00Z",2,RequestStatus.fromWire("Awaiting Payment"),
        new RequestData.Service(id(50),"OFF-REG","Configured service",1,FeeDeclaration.REQUIRED,new BigDecimal("35.00"),"PHP",List.of("physical"),List.of(),true)); }
    private static PaymentData.Record payment() { return new PaymentData.Record(id(90),id(2),id(1),PaymentMethod.CASH,PaymentStatus.PAID,new BigDecimal("35.00"),"PHP",2,id(91),"2026-10-21T01:00:00Z"); }
    private static final class Fixture {
        final Fake actions=new Fake(); final Queue work=new Queue(),ui=new Queue(); final RequestDetailViewModel vm;
        Fixture() { this(true); } Fixture(boolean allowed) { actions.allowed=allowed; vm=new RequestDetailViewModel(RuntimeEnvironment.getApplication(),actions,work,ui); }
        RequestDetailViewModel.State state() { return vm.state().getValue(); } void load() { vm.ensureRequest(id(1)); run(); } void run() { work.run(); ui.run(); }
    }
    private static final class Fake implements RequestDetailViewModel.Actions {
        boolean allowed=true,current=true,revokeAfterRequest; int requests,payments; String paymentRequest; Exception requestError,paymentError;
        PaymentData.Record payment;
        public boolean allowed() { return allowed; } public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public RequestData.Record request(String id) throws Exception { ++requests; if(requestError!=null)throw requestError; if(revokeAfterRequest)current=false; return RequestDetailViewModelTest.request(); }
        public PaymentData.Record payment(String requestId) throws Exception { ++payments; paymentRequest=requestId; if(paymentError!=null)throw paymentError; return payment; }
    }
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>(); public void execute(Runnable task) { tasks.add(task); }
        int size() { return tasks.size(); } void run() { assertFalse("Expected queued detail read",tasks.isEmpty()); tasks.remove().run(); }
    }
}
