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
import ph.edu.tip.goserve.data.EvidenceInput;
import ph.edu.tip.goserve.data.RequestRepository;
import ph.edu.tip.goserve.domain.DeliveryData;
import ph.edu.tip.goserve.domain.FeeDeclaration;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.RequestStatus;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34,application=Application.class)
public final class PdfUploadViewModelTest {
    @Test public void canonicalRequestAndPolicyAreLoadedBeforeAnyLocalFileCanBeRead() {
        Fixture f=new Fixture(); f.vm.ensureRequest(id(99)); f.vm.select(new Source());
        assertEquals(PdfUploadViewModel.Mode.LOADING,f.state().mode); assertEquals(0,f.actions.requests); assertEquals(0,f.actions.reads); f.run();
        assertEquals(id(70),f.actions.requestedId); assertEquals(PdfUploadViewModel.Mode.EDITING,f.state().mode); assertNotNull(f.state().options); assertEquals(0,f.actions.prepares);
    }
    @Test public void missingConfigurationBlocksThePickerWithoutInventingAByteLimit() {
        Fixture f=new Fixture(); f.actions.policy=new RequestData.FileOptions(false,"CONFIGURATION_REQUIRED",null,null); f.run(); f.vm.select(new Source());
        assertEquals(PdfUploadViewModel.Mode.BLOCKED,f.state().mode); assertEquals("CONFIGURATION_REQUIRED",f.state().error); assertEquals(0,f.work.size()); assertEquals(0,f.actions.reads);
    }
    @Test public void failedOptionsCanReloadTheSameCanonicalRequest() {
        Fixture f=new Fixture(); f.actions.optionsError=new java.io.IOException("private host"); f.run();
        assertEquals("NETWORK_ERROR",f.state().error); assertNull(f.state().options); f.actions.optionsError=null; f.vm.reload(); f.run();
        assertEquals(id(70),f.actions.requestedId); assertEquals(2,f.actions.requests); assertNotNull(f.state().options);
    }
    @Test public void localSelectionAndReviewDoNotUploadBeforeExplicitConfirmation() {
        Fixture f=ready(); f.vm.select(new Source()); assertEquals(0,f.actions.reads); f.run();
        assertEquals("actual-selected.pdf",f.state().name); assertEquals(4,f.state().sizeBytes); assertEquals(0,f.actions.prepares);
        f.vm.review(); assertEquals(PdfUploadViewModel.Mode.REVIEW,f.state().mode); assertEquals(0,f.actions.uploads);
        f.vm.edit(); assertEquals(PdfUploadViewModel.Mode.EDITING,f.state().mode); assertEquals(0,f.actions.prepares);
    }
    @Test public void confirmationIsSingleFlightAndOnlyVerifiedResultClearsTheLocalBytes() {
        Fixture f=selected(); f.vm.review(); f.vm.confirm(); f.vm.confirm(); assertEquals(1,f.work.size()); assertEquals(0,f.actions.prepares); f.run();
        assertEquals(1,f.actions.prepares); assertEquals(1,f.actions.uploads); assertEquals(PdfUploadViewModel.Mode.COMPLETE,f.state().mode);
        assertEquals(id(1),f.state().result.id); assertArrayEquals(new byte[4],f.actions.privateBytes); f.vm.confirm(); assertEquals(0,f.work.size());
    }
    @Test public void aLostPrepareResponseRetainsTheExactOperationForRetry() {
        Fixture f=selected(); f.actions.prepareError=new java.io.IOException("lost"); f.vm.review(); String key=f.state().intent.operationKey; f.vm.confirm(); f.run();
        assertTrue(f.state().uncertain); assertEquals(key,f.state().intent.operationKey); assertNull(f.state().intent.fileId);
        f.actions.prepareError=null; f.vm.confirm(); f.run(); assertEquals(2,f.actions.prepares); assertEquals(key,f.actions.preparedKey); assertEquals(PdfUploadViewModel.Mode.COMPLETE,f.state().mode);
    }
    @Test public void uncertainUploadFreezesEditingAndReusesThePreparedFileAndBytes() {
        Fixture f=selected(); f.actions.uploadError=new java.io.IOException("lost"); f.vm.review(); f.vm.confirm(); f.run();
        assertTrue(f.state().uncertain); assertEquals(id(1),f.state().intent.fileId); f.vm.edit(); f.vm.select(new Source());
        assertEquals(PdfUploadViewModel.Mode.REVIEW,f.state().mode); assertEquals(0,f.work.size()); assertEquals(1,f.actions.reads);
        f.actions.uploadError=null; f.vm.confirm(); f.run(); assertEquals(1,f.actions.prepares); assertEquals(2,f.actions.uploads); assertEquals(id(1),f.actions.uploadedId); assertArrayEquals(new byte[]{1,2,3,4},f.actions.sent);
    }
    @Test public void definitivePolicyFailureReleasesBytesAndRequiresCanonicalReload() {
        Fixture f=selected(); f.actions.uploadError=new RequestRepository.WorkflowException("POLICY_BLOCKED"); f.vm.review(); f.vm.confirm(); f.run();
        assertEquals(PdfUploadViewModel.Mode.BLOCKED,f.state().mode); assertFalse(f.state().uncertain); assertNull(f.state().intent); assertArrayEquals(new byte[4],f.actions.privateBytes);
        f.vm.confirm(); assertEquals(0,f.work.size()); f.actions.uploadError=null; f.vm.reload(); f.run(); assertEquals(2,f.actions.requests); assertNull(f.state().name);
    }
    @Test public void malformedPostWriteMetadataRemainsUncertainForSafeReplay() {
        Fixture f=selected(); f.actions.uploadError=new RequestRepository.WorkflowException("VALIDATION_FAILED"); f.vm.review(); f.vm.confirm(); f.run();
        assertEquals(PdfUploadViewModel.Mode.REVIEW,f.state().mode); assertTrue(f.state().uncertain); assertEquals(id(1),f.state().intent.fileId); assertArrayEquals(new byte[]{1,2,3,4},f.actions.privateBytes);
    }
    @Test public void accountReplacementAfterPreparationPreventsTheUploadAndWipesSelection() {
        Fixture f=selected(); f.actions.revokeAfterPrepare=true; f.vm.review(); f.vm.confirm(); f.run();
        assertEquals(0,f.actions.uploads); assertEquals(PdfUploadViewModel.Mode.UNAVAILABLE,f.state().mode); assertNull(f.state().request); assertNull(f.state().name); assertArrayEquals(new byte[4],f.actions.privateBytes);
    }
    @Test public void replacedAccountBeforeSuccessPublicationSuppressesThePrivateReceipt() {
        Fixture f=selected(); f.vm.review(); f.vm.confirm(); f.work.run(); f.actions.current=false; f.ui.run();
        assertEquals(PdfUploadViewModel.Mode.UNAVAILABLE,f.state().mode); assertNull(f.state().result); assertArrayEquals(new byte[4],f.actions.privateBytes);
    }
    @Test public void clearingDuringLocalReadDisposesItsUnpublishedSelection() {
        Fixture f=ready(); f.vm.select(new Source()); f.work.run(); ViewModelStore store=new ViewModelStore(); store.put("pdf",f.vm); store.clear(); f.ui.run();
        assertArrayEquals(new byte[4],f.actions.privateBytes); assertNull(f.state().name); assertEquals(PdfUploadViewModel.Mode.UNAVAILABLE,f.state().mode);
    }
    @Test public void deniedOrWrongOfficeModelsCannotReadAStaffPdfContext() {
        Queue work=new Queue(),ui=new Queue(); Fake denied=new Fake(); denied.allowed=false;
        PdfUploadViewModel vm=new PdfUploadViewModel(RuntimeEnvironment.getApplication(),denied,work,ui); vm.ensureRequest(id(70)); assertEquals(0,work.size());
        Fixture f=new Fixture(); f.actions.office="OFF-GCC"; f.run(); assertEquals(PdfUploadViewModel.Mode.UNAVAILABLE,f.state().mode); assertEquals(0,f.actions.optionReads);
    }
    @Test public void synchronousClearingOfBusyStateCannotEnqueueAnUpload() {
        Fixture f=selected(); f.vm.review(); f.vm.state().observeForever(s->{if(s.mode==PdfUploadViewModel.Mode.UPLOADING) { f.actions.current=false; f.vm.checkCurrentAccount(); }});
        f.vm.confirm(); assertEquals(0,f.work.size()); assertEquals(PdfUploadViewModel.Mode.UNAVAILABLE,f.state().mode); assertArrayEquals(new byte[4],f.actions.privateBytes);
    }
    private static Fixture ready() { Fixture f=new Fixture(); f.run(); return f; }
    private static Fixture selected() { Fixture f=ready(); f.vm.select(new Source()); f.run(); return f; }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static final class Fixture {
        final Fake actions=new Fake(); final Queue work=new Queue(),ui=new Queue();
        final PdfUploadViewModel vm=new PdfUploadViewModel(RuntimeEnvironment.getApplication(),actions,work,ui);
        Fixture() { vm.ensureRequest(id(70)); } PdfUploadViewModel.State state() { return vm.state().getValue(); } void run() { work.run(); ui.run(); }
    }
    private static final class Fake implements PdfUploadViewModel.Actions {
        boolean allowed=true,current=true,revokeAfterPrepare; String office="OFF-REG",requestedId,preparedKey,uploadedId;
        int requests,optionReads,reads,prepares,uploads; byte[] privateBytes,sent; Exception optionsError,prepareError,uploadError;
        RequestData.FileOptions policy=new RequestData.FileOptions(true,null,List.of("application/pdf"),4L);
        public boolean allowed() { return allowed; } public String officeId() { return office; } public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public RequestData.Record request(String id) { ++requests; requestedId=id; return new RequestData.Record(id(70),"fixture-reference",id(90),"OFF-REG","digital","2026-10-21T01:00:00Z",2,RequestStatus.PROCESSING,
            new RequestData.Service(id(60),"OFF-REG","Configured fixture",1,FeeDeclaration.FREE,BigDecimal.ZERO,null,List.of("digital"),List.of(),true)); }
        public RequestData.RequestOptions options(RequestData.Record request) throws Exception { ++optionReads; if(optionsError!=null)throw optionsError; return new RequestData.RequestOptions(id(70),2,Map.of("deliverable_upload",policy)); }
        public EvidenceInput.Selection read(EvidenceInput.Source source,RequestData.FileOptions policy) throws Exception { ++reads; EvidenceInput.Selection file=EvidenceInput.read(source,policy,4); privateBytes=file.bytes(); return file; }
        public DeliveryData.PdfUpload input(RequestData.Record request,RequestData.RequestOptions options,EvidenceInput.Selection file,DeliveryData.PdfUpload previous) { return new DeliveryData.PdfUpload(request,options,id(91),"OFF-REG",file.bytes().length,"a".repeat(64),id(80),null); }
        public DeliveryData.PdfUpload prepare(DeliveryData.PdfUpload input) throws Exception { ++prepares; preparedKey=input.operationKey; if(prepareError!=null)throw prepareError; if(revokeAfterPrepare)current=false; return new DeliveryData.PdfUpload(input.request,input.options,input.actorId,input.officeId,input.sizeBytes,input.contentSha256,input.operationKey,id(1)); }
        public DeliveryData.File upload(DeliveryData.PdfUpload input,EvidenceInput.Selection file) throws Exception { ++uploads; uploadedId=input.fileId; sent=file.bytes().clone(); if(uploadError!=null)throw uploadError; return new DeliveryData.File(id(1),id(90),id(60),id(70),DeliveryData.FileKind.DELIVERABLE,null,"application/pdf",4,"verified",1,true,"2026-10-21T01:00:00Z",null); }
    }
    private static final class Source implements EvidenceInput.Source {
        public String name() { return "actual-selected.pdf"; } public String mimeType() { return "application/pdf"; } public Long declaredSize() { return 4L; }
        public java.io.InputStream open() { return new java.io.ByteArrayInputStream(new byte[]{1,2,3,4}); }
    }
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>(); public void execute(Runnable task) { tasks.add(task); }
        int size() { return tasks.size(); } void run() { assertFalse("Expected queued PDF task",tasks.isEmpty()); tasks.remove().run(); }
    }
}
