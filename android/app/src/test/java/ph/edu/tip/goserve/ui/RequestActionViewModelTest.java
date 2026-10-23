package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.ViewModelStore;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.data.RequestRepository;
import ph.edu.tip.goserve.domain.FeeDeclaration;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.RequestStatus;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34,application=Application.class)
public final class RequestActionViewModelTest {
    @Test public void optionsLoadOffMainForOnlyThePinnedRequest() {
        Fixture f=new Fixture(); f.vm.ensureRequest(record(2,RequestStatus.PROCESSING));
        assertTrue(f.state().busy()); assertEquals(0,f.actions.reads); assertEquals(1,f.work.size()); f.run();
        assertEquals(1,f.actions.request.version); assertEquals(RequestActionViewModel.Mode.EDITING,f.state().mode);
        assertEquals(1,f.actions.reads);
    }
    @Test public void mismatchedPolicyCannotAuthorizeTheRetainedVersion() {
        Fixture f=new Fixture(); f.actions.options=new RequestData.RequestOptions(id(1),2,Map.of()); f.run();
        assertNull(f.state().options); assertEquals("STALE_VERSION",f.state().error); assertEquals(RequestActionViewModel.Mode.BLOCKED,f.state().mode);
    }
    @Test public void optionsFailureKeepsTheReasonAndRetriesWithoutInventingPolicy() {
        Fixture f=new Fixture(); f.vm.setReason("Staff explanation"); f.actions.readError=new java.io.IOException("private host"); f.run();
        assertNull(f.state().options); assertEquals("NETWORK_ERROR",f.state().error); assertEquals("Staff explanation",f.state().reason);
        f.vm.setReason("Edited while offline"); assertEquals("NETWORK_ERROR",f.state().error);
        f.actions.readError=null; f.vm.retryOptions(); f.run(); assertEquals(2,f.actions.reads); assertNotNull(f.state().options);
    }
    @Test public void reviewDoesNotWriteAndSubmissionIsSingleFlight() {
        Fixture f=new Fixture(); f.run(); f.vm.submit(); assertEquals(0,f.work.size()); f.vm.setReason("Reviewed reason"); f.vm.review(RequestStatus.PROCESSING);
        assertEquals(RequestActionViewModel.Mode.REVIEW,f.state().mode); assertEquals(0,f.actions.writes); assertEquals(0,f.work.size());
        f.vm.submit(); f.vm.submit(); assertEquals(1,f.work.size()); assertEquals(RequestActionViewModel.Mode.SUBMITTING,f.state().mode); f.run();
        assertSame(f.actions.result,f.state().result); assertEquals(RequestActionViewModel.Mode.COMPLETE,f.state().mode); assertEquals(1,f.actions.writes);
    }
    @Test public void deniedReasonOrPolicyStaysEditableWithoutAnyQueuedWrite() {
        Fixture f=new Fixture(); f.run(); f.actions.draftError=new RequestRepository.WorkflowException("POLICY_BLOCKED"); f.vm.review(RequestStatus.REJECTED);
        assertEquals(RequestActionViewModel.Mode.EDITING,f.state().mode); assertEquals("POLICY_BLOCKED",f.state().error); assertEquals(0,f.work.size());
    }
    @Test public void editPreservesThePriorIntentForTheRepositoryKeyDecision() {
        Fixture f=new Fixture(); f.run(); f.vm.review(RequestStatus.PROCESSING); RequestData.Transition previous=f.state().intent;
        f.vm.edit(); f.vm.setReason("Different notes"); f.vm.review(RequestStatus.PROCESSING);
        assertSame(previous,f.actions.previous); assertEquals("Different notes",f.state().intent.reason);
    }
    @Test public void uncertainCommitRetainsItsExactIntentAndFreezesEditsAndReloads() {
        Fixture f=new Fixture(); f.run(); f.vm.review(RequestStatus.PROCESSING); RequestData.Transition intent=f.state().intent;
        f.actions.commitError=new java.io.IOException("lost response"); f.vm.submit(); f.run(); assertTrue(f.state().uncertain);
        f.vm.edit(); f.vm.setReason("Changed"); f.vm.retryOptions(); f.vm.ensureRequest(record(2,RequestStatus.PROCESSING));
        assertSame(intent,f.state().intent); assertEquals("",f.state().reason); assertEquals(0,f.work.size());
        f.actions.commitError=null; f.vm.submit(); f.run(); assertSame(intent,f.actions.committed); assertEquals(2,f.actions.writes);
    }
    @Test public void malformedPostWriteResultCannotDiscardTheUncertainOperationKey() {
        Fixture f=new Fixture(); f.run(); f.vm.review(RequestStatus.PROCESSING);
        f.actions.commitError=new RequestRepository.WorkflowException("VALIDATION_FAILED"); f.vm.submit(); f.run();
        assertTrue(f.state().uncertain); assertNotNull(f.state().intent); assertEquals(RequestActionViewModel.Mode.REVIEW,f.state().mode);
    }
    @Test public void definitiveServerDenialRequiresCanonicalReloadBeforeAnotherReview() {
        for(String code:List.of("STALE_VERSION","INVALID_STATE","PAYMENT_REQUIRED","POLICY_BLOCKED","CONFIGURATION_REQUIRED","UPLOAD_INVALID")) {
            Fixture f=new Fixture(); f.run(); f.vm.review(RequestStatus.PROCESSING); f.actions.commitError=new RequestRepository.WorkflowException(code); f.vm.submit(); f.run();
            assertEquals(RequestActionViewModel.Mode.BLOCKED,f.state().mode); assertFalse(f.state().uncertain); assertNull(f.state().intent); assertNull(f.state().options);
            f.vm.review(RequestStatus.PROCESSING); f.vm.submit(); assertEquals(0,f.work.size());
        }
    }
    @Test public void accountReplacementBeforeReadPublicationClearsPrivateState() {
        Fixture f=new Fixture(); f.vm.setReason("Private reason"); f.work.run(); f.actions.current=false; f.ui.run();
        assertEquals(RequestActionViewModel.Mode.UNAVAILABLE,f.state().mode); assertNull(f.state().request); assertEquals("",f.state().reason);
    }
    @Test public void accountReplacementBeforeCommitPublicationNeverShowsSuccess() {
        Fixture f=new Fixture(); f.run(); f.vm.review(RequestStatus.PROCESSING); f.vm.submit(); f.work.run(); f.actions.current=false; f.ui.run();
        assertEquals(RequestActionViewModel.Mode.UNAVAILABLE,f.state().mode); assertNull(f.state().result); assertNull(f.state().intent);
    }
    @Test public void deniedOrClearedModelCannotExecuteQueuedWrites() {
        Fixture denied=new Fixture(false); assertEquals(0,denied.work.size()); assertEquals(RequestActionViewModel.Mode.UNAVAILABLE,denied.state().mode);
        Fixture f=new Fixture(); f.run(); f.vm.review(RequestStatus.PROCESSING); f.vm.submit();
        ViewModelStore store=new ViewModelStore(); store.put("actions",f.vm); store.clear(); f.work.run(); assertEquals(0,f.actions.writes);
    }
    private static RequestData.Record record(long version,RequestStatus status) {
        return new RequestData.Record(id(1),"fixture-reference",id(90),"OFF-REG","physical","2026-10-21T01:00:00Z",version,status,
            new RequestData.Service(id(50),"OFF-REG","Configured fixture",1,FeeDeclaration.FREE,BigDecimal.ZERO,null,List.of("physical"),List.of(),true));
    }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static final class Fixture {
        final Fake actions=new Fake(); final Queue work=new Queue(),ui=new Queue(); final RequestActionViewModel vm;
        Fixture() { this(true); }
        Fixture(boolean allowed) { actions.allowed=allowed; vm=new RequestActionViewModel(RuntimeEnvironment.getApplication(),actions,work,ui); vm.ensureRequest(record(1,RequestStatus.SUBMITTED)); }
        RequestActionViewModel.State state() { return vm.state().getValue(); }
        void run() { work.run(); ui.run(); }
    }
    private static final class Fake implements RequestActionViewModel.Actions {
        boolean current=true,allowed=true; int reads,writes; RequestData.Record request,result=record(2,RequestStatus.PROCESSING);
        RequestData.Transition previous,committed; Exception readError,draftError,commitError;
        RequestData.RequestOptions options=new RequestData.RequestOptions(id(1),1,Map.of("cancel",new RequestData.ActionOptions(false,"CONFIGURATION_REQUIRED")));
        public boolean allowed() { return allowed; } public String officeId() { return "OFF-REG"; }
        public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public RequestData.RequestOptions options(RequestData.Record request) throws Exception { ++reads; this.request=request; if(readError!=null)throw readError; return options; }
        public RequestData.Transition draft(RequestData.Record request,RequestData.RequestOptions options,RequestStatus target,String reason,RequestData.Transition previous) throws Exception {
            this.previous=previous; if(draftError!=null)throw draftError; return new RequestData.Transition(request,options,id(91),"OFF-REG",target,reason,id(200));
        }
        public RequestData.Record commit(RequestData.Transition input) throws Exception { ++writes; committed=input; if(commitError!=null)throw commitError; return result; }
    }
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>(); public void execute(Runnable task) { tasks.add(task); }
        int size() { return tasks.size(); } void run() { assertFalse("Expected queued action",tasks.isEmpty()); tasks.remove().run(); }
    }
}
