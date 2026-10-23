package ph.edu.tip.goserve.ui;

import android.app.Application;
import androidx.lifecycle.ViewModelStore;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.data.EvidenceInput;
import ph.edu.tip.goserve.data.RequestRepository;
import ph.edu.tip.goserve.domain.FeeDeclaration;
import ph.edu.tip.goserve.domain.RequestData;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=Application.class)
public final class RequestEvidenceViewModelTest {
    @Test public void choosingAFileOnlyReadsAndAllocatesAnIntentWithoutNetwork() {
        Fixture f=new Fixture(); f.vm.select(new Source()); assertTrue(f.state().busy()); assertEquals(0,f.actions.reads);
        f.run(); assertEquals(RequestEvidenceViewModel.Mode.READY,f.state().mode); assertEquals("evidence.pdf",f.state().name);
        assertEquals(4,f.state().sizeBytes); assertNotNull(f.state().prepared.operationKey); assertNull(f.state().prepared.fileId);
        assertEquals(0,f.actions.prepares); assertEquals(0,f.actions.uploads);
    }
    @Test public void missingPolicyForeignOwnerOrUnknownRequirementCannotReadBytes() {
        for(int bad=0;bad<3;bad++) {
            Fixture f=new Fixture(false); RequestData.Quote quote=bad==1?quote(id(99)):quote(id(90));
            f.vm.ensureContext(quote,bad==0?null:options(),bad==2?"unknown":"student-id"); f.vm.select(new Source());
            assertEquals(RequestEvidenceViewModel.Mode.UNAVAILABLE,f.state().mode); assertEquals(0,f.work.size());
        }
    }
    @Test public void contextIsPinnedAndCannotRebindAnExistingFileToAnotherRequirement() {
        Fixture f=new Fixture(); f.vm.ensureContext(quote(id(90)),options(),"other"); f.select(); assertEquals("student-id",f.state().prepared.requirement);
    }
    @Test public void explicitUsePreparesThenRetainsFileIdentityBeforeUploading() {
        Fixture f=new Fixture(); f.select(); f.vm.upload(); f.vm.upload(); assertEquals(1,f.work.size()); assertEquals(0,f.actions.uploads);
        f.run(); assertEquals(RequestEvidenceViewModel.Mode.UPLOADING,f.state().mode); assertEquals(id(5),f.state().prepared.fileId); assertEquals(1,f.work.size());
        f.run(); assertEquals(RequestEvidenceViewModel.Mode.COMPLETE,f.state().mode); assertEquals(id(5),f.state().result.id);
        assertEquals(1,f.actions.prepares); assertEquals(1,f.actions.uploads); assertArrayEquals(new byte[4],f.actions.lastBytes);
    }
    @Test public void lostPrepareResponseRetriesTheSameOperationKeyAndDoesNotInventAFileId() {
        Fixture f=new Fixture(); f.select(); String key=f.state().prepared.operationKey;
        f.actions.prepareError=new java.io.IOException("lost prepare"); f.vm.upload(); f.run(); assertNull(f.state().prepared.fileId); assertNull(f.state().result);
        f.actions.prepareError=null; f.vm.retry(); f.run(); assertEquals(key,f.actions.prepareInput.operationKey); f.run(); assertEquals(2,f.actions.prepares);
    }
    @Test public void lostUploadResponseRetainsThePreparedFileAndRetriesOnlyItsIdenticalBytes() {
        Fixture f=new Fixture(); f.select(); f.actions.uploadError=new java.io.IOException("lost upload"); f.vm.upload(); f.run(); f.run();
        assertEquals(RequestEvidenceViewModel.Mode.ERROR,f.state().mode); assertEquals(id(5),f.state().prepared.fileId); assertNull(f.state().result);
        f.actions.uploadError=null; f.vm.retry(); f.run(); assertEquals(1,f.actions.prepares); assertEquals(2,f.actions.uploads); assertEquals(id(5),f.actions.uploadInput.fileId);
    }
    @Test public void failedLocalReadDoesNotPrepareOrPretendThatAFileWasSelected() {
        Fixture f=new Fixture(); f.actions.readError=new java.io.IOException("private provider"); f.select();
        assertEquals("FILE_UNAVAILABLE",f.state().error); assertNull(f.state().prepared); f.vm.retry(); assertEquals(0,f.work.size()); assertEquals(0,f.actions.prepares);
    }
    @Test public void sourceCancellationKeepsThePreviousReadyFileAndDoesNotReadAgain() {
        Fixture f=new Fixture(); f.select(); RequestData.PreparedUpload before=f.state().prepared; f.vm.select(null);
        assertSame(before,f.state().prepared); assertEquals(0,f.work.size());
    }
    @Test public void lateSelectionResponsesAreDiscardedAndTheirOwnedBytesAreWiped() {
        Fixture f=new Fixture(); f.vm.select(new Source()); f.work.run(); byte[] stale=f.actions.lastBytes;
        f.vm.select(new Source()); f.ui.run(); assertArrayEquals(new byte[4],stale); assertEquals(RequestEvidenceViewModel.Mode.READING,f.state().mode);
        f.run(); assertEquals(RequestEvidenceViewModel.Mode.READY,f.state().mode);
    }
    @Test public void selectingAgainWipesThePreviousPayloadAndPassesItsRetainedDescriptorForIdentityChecks() {
        Fixture f=new Fixture(); f.select(); byte[] original=f.actions.lastBytes; RequestData.PreparedUpload previous=f.state().prepared;
        f.vm.select(new Source()); assertArrayEquals(new byte[4],original); f.run(); assertSame(previous,f.actions.previous);
    }
    @Test public void selectingDuringUploadCannotChangeTheInFlightIntent() {
        Fixture f=new Fixture(); f.select(); f.vm.upload(); f.vm.select(new Source()); assertEquals(1,f.work.size()); assertEquals(1,f.actions.reads);
    }
    @Test public void accountLossBeforeReadOrBetweenPrepareAndUploadPreventsFurtherIo() {
        Fixture early=new Fixture(); early.vm.select(new Source()); early.actions.current=false; early.vm.checkCurrentAccount(); early.work.run(); assertEquals(0,early.actions.reads);
        Fixture f=new Fixture(); f.select(); byte[] bytes=f.actions.lastBytes; f.vm.upload(); f.work.run(); f.actions.current=false; f.ui.run();
        assertEquals(0,f.actions.uploads); assertArrayEquals(new byte[4],bytes); assertEquals(RequestEvidenceViewModel.Mode.UNAVAILABLE,f.state().mode);
    }
    @Test public void accountLossBeforeUploadPublicationClearsThePrivateResultAndBytes() {
        Fixture f=new Fixture(); f.select(); byte[] bytes=f.actions.lastBytes; f.vm.upload(); f.run(); f.work.run(); f.actions.current=false; f.ui.run();
        assertNull(f.state().result); assertNull(f.state().name); assertArrayEquals(new byte[4],bytes);
    }
    @Test public void serverDenialClearsTheFileAndStopsRetries() {
        Fixture f=new Fixture(); f.select(); f.actions.prepareError=new RequestRepository.WorkflowException("FORBIDDEN"); f.vm.upload(); f.run();
        assertEquals(RequestEvidenceViewModel.Mode.UNAVAILABLE,f.state().mode); assertNull(f.state().prepared); f.vm.retry(); assertEquals(0,f.work.size());
    }
    @Test public void clearingWhileAFileReadIsQueuedOrUnpublishedPreventsLeaks() {
        for(boolean read:new boolean[]{false,true}) { Fixture f=new Fixture(); f.vm.select(new Source()); if(read)f.work.run();
            ViewModelStore store=new ViewModelStore(); store.put("evidence",f.vm); store.clear();
            if(read) { f.ui.run(); assertArrayEquals(new byte[4],f.actions.lastBytes); } else { f.work.run(); assertEquals(0,f.actions.reads); }
        }
    }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static RequestData.Quote quote(String owner) { return new RequestData.Quote(id(70),owner,"physical",new RequestData.Service(id(1),"OFF-REG","Configured service",1,FeeDeclaration.FREE,BigDecimal.ZERO,null,List.of("physical"),List.of("student-id"),true),true); }
    private static RequestData.QuoteOptions options() { return new RequestData.QuoteOptions(id(70),1,new RequestData.ActionOptions(false,"CONFIGURATION_REQUIRED"),new RequestData.FileOptions(true,null,List.of("application/pdf"),12L)); }
    private static final class Fixture {
        final Fake actions=new Fake(); final Queue work=new Queue(),ui=new Queue(); final RequestEvidenceViewModel vm;
        Fixture() { this(true); } Fixture(boolean context) { vm=new RequestEvidenceViewModel(RuntimeEnvironment.getApplication(),actions,work,ui); if(context)vm.ensureContext(quote(id(90)),options(),"student-id"); }
        RequestEvidenceViewModel.State state() { return vm.state().getValue(); } void select() { vm.select(new Source()); run(); } void run() { work.run(); ui.run(); }
    }
    private static final class Fake implements RequestEvidenceViewModel.Actions {
        boolean current=true; int reads,prepares,uploads; byte[] lastBytes; RequestData.PreparedUpload previous,prepareInput,uploadInput;
        Exception readError,prepareError,uploadError;
        public boolean allowed() { return true; } public String accountId() { return id(90); } public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public EvidenceInput.Selection read(EvidenceInput.Source source,RequestData.FileOptions policy) throws Exception { ++reads; if(readError!=null)throw readError; EvidenceInput.Selection file=EvidenceInput.read(source,policy,20); lastBytes=file.bytes(); return file; }
        public RequestData.PreparedUpload input(RequestData.Quote quote,RequestData.QuoteOptions options,String requirement,EvidenceInput.Selection file,RequestData.PreparedUpload previous) {
            this.previous=previous; return new RequestData.PreparedUpload(quote,options,requirement,file.mimeType,file.bytes().length,"a".repeat(64),previous==null?id(200):previous.operationKey,null);
        }
        public RequestData.PreparedUpload prepare(RequestData.PreparedUpload input) throws Exception { ++prepares; prepareInput=input; if(prepareError!=null)throw prepareError;
            return new RequestData.PreparedUpload(input.quote,input.options,input.requirement,input.mimeType,input.sizeBytes,input.contentSha256,input.operationKey,id(5)); }
        public RequestData.Evidence upload(RequestData.PreparedUpload input,EvidenceInput.Selection file) throws Exception { ++uploads; uploadInput=input; if(uploadError!=null)throw uploadError;
            return new RequestData.Evidence(id(5),id(90),id(1),null,"student-id","verified",true); }
    }
    private static final class Source implements EvidenceInput.Source {
        public String name() { return "evidence.pdf"; } public String mimeType() { return "application/pdf"; } public Long declaredSize() { return 4L; }
        public InputStream open() { return new ByteArrayInputStream(new byte[]{1,2,3,4}); }
    }
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>(); public void execute(Runnable task) { tasks.add(task); }
        int size() { return tasks.size(); } void run() { assertFalse("Expected queued private evidence task",tasks.isEmpty()); tasks.remove().run(); }
    }
}
