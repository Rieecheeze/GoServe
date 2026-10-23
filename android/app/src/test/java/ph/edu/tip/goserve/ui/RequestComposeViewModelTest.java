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
import ph.edu.tip.goserve.domain.FeeDeclaration;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.RequestStatus;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=Application.class)
public final class RequestComposeViewModelTest {
    @Test public void canonicalQuoteAndOptionsLoadOffMainForAPinnedOffice() {
        Fixture f=new Fixture(); f.vm.ensureOffice("OFF-GCC"); f.vm.selectService(service(1,FeeDeclaration.FREE),"physical");
        assertEquals("OFF-REG",f.state().officeId); assertTrue(f.state().busy()); assertEquals(0,f.actions.quotes);
        f.run(); assertEquals(1,f.actions.quotes); assertEquals(1,f.actions.optionReads);
        assertEquals(id(1),f.actions.serviceId); assertEquals("physical",f.actions.delivery); assertSame(f.actions.quote,f.state().quote);
    }
    @Test public void malformedOrForeignSelectionCannotCreateAQuote() {
        Fixture f=new Fixture(); f.vm.selectService(null,"physical"); assertEquals("VALIDATION_FAILED",f.state().error); assertEquals(0,f.work.size());
        RequestData.Service service=new RequestData.Service(id(1),"OFF-GCC","Configured",1,FeeDeclaration.FREE,BigDecimal.ZERO,null,List.of("physical"),List.of(),true);
        f.vm.selectService(service,"physical"); assertEquals("VALIDATION_FAILED",f.state().error); assertEquals(0,f.work.size());
        f.vm.selectService(service(1,FeeDeclaration.FREE),"digital"); assertEquals(0,f.work.size());
    }
    @Test public void quoteSelectionSupersedesOldResponsesWithoutLosingNewNotes() {
        Fixture f=new Fixture(); f.vm.selectService(service(1,FeeDeclaration.FREE),"physical"); f.work.run();
        f.vm.selectService(service(2,FeeDeclaration.FREE),"physical"); f.vm.setNotes("Purpose supplied by student");
        f.actions.quote=quote(2,FeeDeclaration.REQUIRED); f.ui.run(); assertTrue(f.state().busy()); f.run();
        assertEquals(id(2),f.state().quote.service.id); assertEquals("Purpose supplied by student",f.state().notes);
    }
    @Test public void quoteFailureCanRetryTheSameSelectionWithoutInventingAFreeFee() {
        Fixture f=new Fixture(); f.actions.quoteError=new java.io.IOException("private host"); f.select();
        assertNull(f.state().quote); assertEquals("NETWORK_ERROR",f.state().error);
        f.actions.quoteError=null; f.vm.retryQuote(); f.run(); assertEquals(2,f.actions.quotes); assertEquals(FeeDeclaration.FREE,f.state().quote.service.fee);
    }
    @Test public void mismatchedCanonicalQuoteCannotEnterEditingOrReview() {
        Fixture f=new Fixture(); f.actions.quote=quote(2,FeeDeclaration.FREE); f.select();
        assertNull(f.state().quote); assertEquals("VALIDATION_FAILED",f.state().error); assertEquals(0,f.actions.optionReads);
    }
    @Test public void optionsFailureDoesNotPretendThatPaymentOrUploadsAreConfigured() {
        Fixture f=new Fixture(); f.actions.optionError=new RequestRepository.WorkflowException("CONFIGURATION_REQUIRED"); f.select();
        assertNull(f.state().options); assertEquals("CONFIGURATION_REQUIRED",f.state().error); f.vm.review(); assertEquals(0,f.actions.drafts);
    }
    @Test public void reviewIsExplicitAndDoesNotWriteOrAutomaticallyChooseCash() {
        Fixture f=new Fixture(); f.select(); f.vm.setNotes("Verbatim notes"); f.vm.review();
        assertEquals(RequestComposeViewModel.Mode.REVIEW,f.state().mode); assertEquals("Verbatim notes",f.state().draft.notes);
        assertNull(f.state().draft.paymentMethod); assertEquals(0,f.actions.submits); assertEquals(0,f.work.size());
    }
    @Test public void explicitPaymentAndVerifiedEvidenceArePassedToTheAuthoritativeDraftValidator() {
        Fixture f=new Fixture(); f.actions.quote=quote(1,FeeDeclaration.REQUIRED); f.select();
        f.vm.setPaymentMethod("cash"); f.vm.addEvidence(evidence(5,"student-id",true)); f.vm.review();
        assertEquals("cash",f.actions.method); assertEquals(id(5),f.actions.files.get(0).id);
        assertEquals(RequestComposeViewModel.Mode.REVIEW,f.state().mode);
    }
    @Test public void unverifiedOrForeignEvidenceNeverEntersTheReviewPayload() {
        Fixture f=new Fixture(); f.select(); f.vm.addEvidence(evidence(5,"student-id",false));
        assertTrue(f.state().evidence.isEmpty()); assertEquals("UPLOAD_INVALID",f.state().error);
        f.vm.addEvidence(new RequestData.Evidence(id(6),id(99),id(1),null,"student-id","verified",true));
        assertTrue(f.state().evidence.isEmpty());
    }
    @Test public void replacingARequirementAndRemovingAFileInvalidatesThePriorReview() {
        Fixture f=new Fixture(); f.select(); f.vm.addEvidence(evidence(5,"student-id",true)); f.vm.review();
        f.vm.edit(); f.vm.addEvidence(evidence(6,"student-id",true)); assertEquals(1,f.state().evidence.size());
        assertEquals(id(6),f.state().evidence.get(0).id); f.vm.removeEvidence(id(6)); assertTrue(f.state().evidence.isEmpty());
    }
    @Test public void serverDraftBlockersKeepTheFormEditableAndNeverSubmit() {
        Fixture f=new Fixture(); f.select(); f.actions.draftError=new RequestRepository.WorkflowException("UPLOAD_INVALID"); f.vm.review();
        assertEquals(RequestComposeViewModel.Mode.EDITING,f.state().mode); assertEquals("UPLOAD_INVALID",f.state().error); assertEquals(0,f.actions.submits);
    }
    @Test public void submitOnlyRunsAfterReviewAndPublishesCanonicalResult() {
        Fixture f=new Fixture(); f.select(); f.vm.submit(); assertEquals(0,f.work.size()); f.vm.review(); f.vm.submit(); f.vm.submit();
        assertEquals(RequestComposeViewModel.Mode.SUBMITTING,f.state().mode); assertEquals(1,f.work.size());
        f.run(); assertEquals(1,f.actions.submits); assertSame(f.actions.result,f.state().result); assertEquals(RequestComposeViewModel.Mode.COMPLETE,f.state().mode);
        f.vm.submit(); assertEquals(0,f.work.size());
    }
    @Test public void uncertainSubmissionRetainsTheExactDraftAndBlocksPayloadEditsUntilReplay() {
        Fixture f=new Fixture(); f.select(); f.vm.review(); RequestData.Submission intent=f.state().draft;
        f.actions.submitError=new java.io.IOException("lost response"); f.vm.submit(); f.run(); assertTrue(f.state().uncertain);
        f.vm.edit(); f.vm.setNotes("Changed"); f.vm.setPaymentMethod("cash"); f.vm.selectService(service(2,FeeDeclaration.FREE),"physical");
        assertSame(intent,f.state().draft); assertEquals("",f.state().notes); assertEquals(0,f.work.size());
        f.actions.submitError=null; f.vm.submit(); f.run(); assertSame(intent,f.actions.submitted); assertEquals(2,f.actions.submits);
    }
    @Test public void definitiveStaleQuoteReopensEditingAndRequiresANewQuoteBeforeReview() {
        Fixture f=new Fixture(); f.select(); f.vm.review(); f.actions.submitError=new RequestRepository.WorkflowException("QUOTE_STALE"); f.vm.submit(); f.run();
        assertFalse(f.state().uncertain); assertNull(f.state().quote); assertEquals(RequestComposeViewModel.Mode.EDITING,f.state().mode);
        f.vm.review(); assertEquals(1,f.actions.drafts); f.vm.retryQuote(); f.run(); assertNotNull(f.state().quote);
    }
    @Test public void reviewPassesTheLatestIntentToRepositoryForIdenticalOrChangedPayloads() {
        Fixture f=new Fixture(); f.select(); f.vm.review(); RequestData.Submission first=f.state().draft;
        f.vm.edit(); f.vm.review(); assertSame(first,f.actions.previous); RequestData.Submission latest=f.state().draft;
        f.vm.edit(); f.vm.setNotes("New notes"); f.vm.review();
        assertSame(latest,f.actions.previous); assertEquals("New notes",f.state().draft.notes);
    }
    @Test public void accountLossBetweenQuoteAndOptionsOrBeforeSubmissionPublicationClearsAllData() {
        Fixture early=new Fixture(); early.actions.revokeAfterQuote=true; early.select(); assertEquals(0,early.actions.optionReads); assertNull(early.state().quote);
        Fixture late=new Fixture(); late.select(); late.vm.review(); late.vm.submit(); late.work.run(); late.actions.current=false; late.ui.run();
        assertEquals(RequestComposeViewModel.Mode.UNAVAILABLE,late.state().mode); assertNull(late.state().draft); assertNull(late.state().result); assertEquals("",late.state().notes);
    }
    @Test public void unreadyRevokedOrClearedModelsCannotPerformQueuedWrites() {
        Fixture denied=new Fixture(false); denied.vm.selectService(service(1,FeeDeclaration.FREE),"physical"); assertEquals(0,denied.work.size());
        Fixture f=new Fixture(); f.select(); f.vm.review(); f.vm.submit(); ViewModelStore store=new ViewModelStore(); store.put("compose",f.vm); store.clear(); f.work.run(); assertEquals(0,f.actions.submits);
        Fixture revoked=new Fixture(); revoked.actions.quoteError=new RequestRepository.WorkflowException("FORBIDDEN"); revoked.select(); assertEquals(RequestComposeViewModel.Mode.UNAVAILABLE,revoked.state().mode);
    }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static RequestData.Service service(int n,FeeDeclaration fee) { return new RequestData.Service(id(n),"OFF-REG","Configured service",1,fee,fee==FeeDeclaration.FREE?BigDecimal.ZERO:fee==FeeDeclaration.REQUIRED?new BigDecimal("35.00"):null,fee==FeeDeclaration.REQUIRED?"PHP":null,List.of("physical"),List.of("student-id"),true); }
    private static RequestData.Quote quote(int n,FeeDeclaration fee) { return new RequestData.Quote(id(70+n),id(90),"physical",service(n,fee),fee!=FeeDeclaration.PENDING_REVIEW); }
    private static RequestData.Evidence evidence(int n,String requirement,boolean verified) { return new RequestData.Evidence(id(n),id(90),id(1),null,requirement,verified?"verified":"pending",true); }
    private static final class Fixture {
        final Fake actions=new Fake(); final Queue work=new Queue(),ui=new Queue(); final RequestComposeViewModel vm;
        Fixture() { this(true); } Fixture(boolean allowed) { actions.allowed=allowed; vm=new RequestComposeViewModel(RuntimeEnvironment.getApplication(),actions,work,ui); vm.ensureOffice("OFF-REG"); }
        RequestComposeViewModel.State state() { return vm.state().getValue(); } void select() { vm.selectService(service(1,FeeDeclaration.FREE),"physical"); run(); } void run() { work.run(); ui.run(); }
    }
    private static final class Fake implements RequestComposeViewModel.Actions {
        boolean allowed=true,current=true,revokeAfterQuote; int quotes,optionReads,drafts,submits; String serviceId,delivery,method; List<RequestData.Evidence> files;
        Exception quoteError,optionError,draftError,submitError; RequestData.Submission previous,submitted;
        RequestData.Quote quote=RequestComposeViewModelTest.quote(1,FeeDeclaration.FREE);
        RequestData.Record result=new RequestData.Record(id(80),"canonical-result",id(90),"OFF-REG","physical","2026-10-21T01:00:00Z",2,RequestStatus.SUBMITTED,service(1,FeeDeclaration.FREE));
        public boolean allowed() { return allowed; } public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public RequestData.Quote quote(String serviceId,String delivery) throws Exception { ++quotes; this.serviceId=serviceId; this.delivery=delivery; if(quoteError!=null)throw quoteError; if(revokeAfterQuote)current=false; return quote; }
        public RequestData.QuoteOptions options(RequestData.Quote quote) throws Exception { ++optionReads; if(optionError!=null)throw optionError; return new RequestData.QuoteOptions(quote.id,quote.service.version,new RequestData.ActionOptions(true,null),new RequestData.FileOptions(false,"CONFIGURATION_REQUIRED",null,null)); }
        public RequestData.Submission draft(RequestData.Quote quote,RequestData.QuoteOptions options,List<RequestData.Evidence> files,String notes,String method,RequestData.Submission previous) throws Exception {
            ++drafts; this.previous=previous; this.method=method; this.files=files; if(draftError!=null)throw draftError;
            return new RequestData.Submission(quote,options,files,notes,method,id(200+drafts));
        }
        public RequestData.Record submit(RequestData.Submission draft) throws Exception { ++submits; submitted=draft; if(submitError!=null)throw submitError; return result; }
    }
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>(); public void execute(Runnable task) { tasks.add(task); }
        int size() { return tasks.size(); } void run() { assertFalse("Expected queued composition task",tasks.isEmpty()); tasks.remove().run(); }
    }
}
