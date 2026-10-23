package ph.edu.tip.goserve.ui;

import android.app.Application;
import android.net.Uri;
import androidx.lifecycle.ViewModelStore;
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
import ph.edu.tip.goserve.data.DocumentSaver;
import ph.edu.tip.goserve.data.PrivateDocument;
import ph.edu.tip.goserve.data.RequestRepository;
import ph.edu.tip.goserve.domain.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34,application=Application.class)
public final class StudentDeliveryViewModelTest {
    private static final Uri TARGET=Uri.parse("content://fixture.documents/document/new-pdf");
    @Test public void routeIsPinnedAndAllCanonicalReadsRunOffTheCaller() {
        Fixture f=new Fixture(); f.vm.ensureRequest(id(99)); assertEquals(StudentDeliveryViewModel.Mode.LOADING,f.state().mode); assertTrue(f.actions.events.isEmpty()); f.run();
        assertEquals(id(70),f.actions.requested); assertEquals(StudentDeliveryViewModel.Mode.AVAILABLE,f.state().mode); assertEquals(List.of("request","payment","files"),f.actions.events);
    }
    @Test public void requiredFeeCannotUseRequestReadinessAsPaymentProof() {
        Fixture f=new Fixture(); f.actions.fee=FeeDeclaration.REQUIRED; f.run(); f.vm.prepareDownload(); f.vm.reviewReceipt();
        assertEquals(StudentDeliveryViewModel.Mode.BLOCKED,f.state().mode); assertEquals("PAYMENT_REQUIRED",f.state().error); assertEquals(0,f.actions.begins); assertNull(f.state().receipt);
    }
    @Test public void actualPaidRowUnlocksARequiredFeeAndIsScopedToTheRequest() {
        Fixture f=new Fixture(); f.actions.fee=FeeDeclaration.REQUIRED; f.actions.payment=payment(id(70),PaymentStatus.PAID); f.run(); assertEquals(StudentDeliveryViewModel.Mode.AVAILABLE,f.state().mode);
        Fixture other=new Fixture(); other.actions.fee=FeeDeclaration.REQUIRED; other.actions.payment=payment(id(99),PaymentStatus.PAID); other.run(); assertEquals(StudentDeliveryViewModel.Mode.UNAVAILABLE,other.state().mode);
    }
    @Test public void failedPaymentReadNeverInventsAPaidOrFreeState() {
        Fixture f=new Fixture(); f.actions.paymentError=new java.io.IOException("private host"); f.run(); assertEquals("NETWORK_ERROR",f.state().error); assertNull(f.state().file); f.vm.prepareDownload(); assertEquals(0,f.actions.begins);
    }
    @Test public void successfulEmptyMetadataIsDifferentFromFailedMetadata() {
        Fixture f=new Fixture(); f.actions.file=null; f.run(); assertEquals("NOT_FOUND",f.state().error);
        Fixture failed=new Fixture(); failed.actions.filesError=new java.io.IOException(); failed.run(); assertEquals("NETWORK_ERROR",failed.state().error);
    }
    @Test public void nonOwnerAndRetiredFileCannotStartATransfer() {
        Fixture f=new Fixture(); f.actions.owner=id(99); f.run(); assertEquals(StudentDeliveryViewModel.Mode.UNAVAILABLE,f.state().mode);
        Fixture retired=new Fixture(); retired.actions.file=file(false); retired.run(); assertEquals(StudentDeliveryViewModel.Mode.BLOCKED,retired.state().mode); assertEquals(0,retired.actions.begins);
    }
    @Test public void transferIsSingleFlightAndNeverSavesAcknowledgesOrCompletes() {
        Fixture f=ready(); f.vm.prepareDownload(); f.vm.prepareDownload(); assertEquals(1,f.work.size()); f.run();
        assertEquals(StudentDeliveryViewModel.Mode.DESTINATION_READY,f.state().mode); assertEquals(1,f.actions.begins); assertEquals(1,f.actions.downloads); assertEquals(0,f.actions.saves); assertEquals(0,f.actions.acks); assertEquals(0,f.actions.confirms);
    }
    @Test public void pickerIsClaimedOnceAndCancellationRetainsOnlyThePrivateCopy() {
        Fixture f=downloaded(); String name=f.vm.takeSaveName(); assertNotNull(name); assertTrue(name.endsWith(".pdf")); assertTrue(name.contains("fixture-reference")); assertNull(f.vm.takeSaveName());
        f.vm.destinationResult(null); assertEquals(StudentDeliveryViewModel.Mode.DESTINATION_READY,f.state().mode); assertFalse(f.actions.document.closed); assertEquals(0,f.actions.saves); assertEquals(0,f.actions.acks);
        assertNotNull(f.vm.takeSaveName()); assertEquals(1,f.actions.downloads);
    }
    @Test public void aStrayPickerResultCannotWriteOrAcknowledge() {
        Fixture f=ready(); f.vm.destinationResult(TARGET); assertEquals(0,f.work.size()); assertEquals(0,f.actions.saves); assertEquals(0,f.actions.acks);
    }
    @Test public void localCopyMustCloseBeforeAcknowledgementAndReceiptRemainsSeparate() {
        Fixture f=downloaded(); f.vm.takeSaveName(); f.vm.destinationResult(TARGET); f.vm.destinationResult(TARGET); assertEquals(1,f.work.size()); f.run();
        assertTrue(f.state().localSaved); assertEquals(StudentDeliveryViewModel.Mode.ACKNOWLEDGING,f.state().mode); assertEquals(0,f.actions.acks); assertTrue(f.actions.document.closed); f.run();
        assertEquals(StudentDeliveryViewModel.Mode.SAVED,f.state().mode); assertEquals("actual-picked.pdf",f.state().savedName); assertNotNull(f.state().acknowledgement); assertEquals(0,f.actions.confirms);
        assertTrue(f.actions.events.indexOf("save-closed")<f.actions.events.indexOf("ack"));
    }
    @Test public void failedSaveDoesNotAcknowledgeAndCanRetryTheCachedCopy() {
        Fixture f=downloaded(); f.actions.saveError=new java.io.IOException("Output close failed"); f.vm.takeSaveName(); f.vm.destinationResult(TARGET); f.run();
        assertEquals(StudentDeliveryViewModel.Mode.DESTINATION_READY,f.state().mode); assertFalse(f.state().localSaved); assertEquals(0,f.actions.acks); assertFalse(f.actions.document.closed);
        assertEquals("Only the just-created destination can be discarded",List.of(TARGET),f.actions.discarded);
        f.actions.saveError=null; assertNotNull(f.vm.takeSaveName()); assertEquals(1,f.actions.downloads);
    }
    @Test public void aChangedPrivateCacheMustBeDiscardedInsteadOfRepeatedlySavingCorruptBytes() {
        Fixture f=downloaded(); f.actions.saveError=new RequestRepository.WorkflowException("DOWNLOAD_UNCONFIRMED"); f.vm.takeSaveName(); f.vm.destinationResult(TARGET); f.run();
        assertEquals(StudentDeliveryViewModel.Mode.BLOCKED,f.state().mode); assertTrue(f.actions.document.closed); assertEquals(0,f.actions.acks); assertNull(f.vm.takeSaveName());
    }
    @Test public void acknowledgementRetryKeepsTheSameTicketAndNeverRecopies() {
        Fixture f=downloaded(); f.actions.ackError=new java.io.IOException("Receipt lost"); f.vm.takeSaveName(); f.vm.destinationResult(TARGET); f.run(); f.run();
        assertEquals(StudentDeliveryViewModel.Mode.LOCAL_SAVED,f.state().mode); assertTrue(f.state().localSaved); assertTrue(f.state().uncertain); DeliveryData.DownloadTicket ticket=f.state().ticket;
        f.vm.refresh(); f.vm.reviewReceipt(); assertEquals(StudentDeliveryViewModel.Mode.LOCAL_SAVED,f.state().mode); f.actions.ackError=null; f.vm.retryAcknowledgement(); f.run();
        assertEquals(StudentDeliveryViewModel.Mode.SAVED,f.state().mode); assertSame(ticket,f.actions.ackTicket); assertEquals(1,f.actions.saves); assertEquals(1,f.actions.downloads);
    }
    @Test public void malformedAcknowledgementNeverBecomesASavedConfirmation() {
        Fixture f=downloaded(); f.actions.wrongAck=true; f.vm.takeSaveName(); f.vm.destinationResult(TARGET); f.run(); f.run();
        assertEquals(StudentDeliveryViewModel.Mode.LOCAL_SAVED,f.state().mode); assertNull(f.state().acknowledgement); assertTrue(f.state().uncertain);
    }
    @Test public void fileRetirementAfterPickerBlocksCopyAndDiscardsOnlyTheCreatedTarget() {
        Fixture f=downloaded(); f.vm.takeSaveName(); f.actions.file=file(false); f.vm.destinationResult(TARGET); f.run();
        assertEquals(StudentDeliveryViewModel.Mode.BLOCKED,f.state().mode); assertEquals(0,f.actions.saves); assertEquals(0,f.actions.acks); assertEquals(List.of(TARGET),f.actions.discarded); assertTrue(f.actions.document.closed);
    }
    @Test public void priorSessionReceiptCanBeReviewedButNeverCommitsUntilExplicitConfirmation() {
        Fixture f=ready(); f.vm.reviewReceipt(); assertEquals(StudentDeliveryViewModel.Mode.RECEIPT_REVIEW,f.state().mode); assertNotNull(f.state().receipt); assertEquals(0,f.actions.confirms); assertEquals(0,f.actions.saves);
        f.vm.editReceipt(); assertEquals(StudentDeliveryViewModel.Mode.AVAILABLE,f.state().mode);
    }
    @Test public void uncertainReceiptRetainsTheExactIntentAndFreezesEditing() {
        Fixture f=ready(); f.vm.reviewReceipt(); DeliveryData.Receipt intent=f.state().receipt; f.actions.confirmError=new java.io.IOException("Lost result"); f.vm.confirmReceipt(); f.vm.confirmReceipt(); assertEquals(1,f.work.size()); f.run();
        assertTrue(f.state().uncertain); f.vm.editReceipt(); f.vm.refresh(); assertSame(intent,f.state().receipt); assertEquals(StudentDeliveryViewModel.Mode.RECEIPT_REVIEW,f.state().mode);
        f.actions.confirmError=null; f.vm.confirmReceipt(); f.run(); assertSame(intent,f.actions.confirmed); assertEquals(StudentDeliveryViewModel.Mode.COMPLETE,f.state().mode); assertEquals(RequestStatus.COMPLETED,f.state().request.status);
    }
    @Test public void missingSavedServerRecordRequiresActualSaveInsteadOfCompleting() {
        Fixture f=ready(); f.vm.reviewReceipt(); f.actions.confirmError=new RequestRepository.WorkflowException("DOWNLOAD_UNCONFIRMED"); f.vm.confirmReceipt(); f.run();
        assertEquals(StudentDeliveryViewModel.Mode.BLOCKED,f.state().mode); assertEquals("DOWNLOAD_UNCONFIRMED",f.state().error); assertFalse(f.state().uncertain); f.vm.refresh(); f.run(); assertEquals(StudentDeliveryViewModel.Mode.AVAILABLE,f.state().mode);
    }
    @Test public void staleReceiptRequiresCanonicalReloadButMalformedPostWriteResultStaysUncertain() {
        Fixture f=ready(); f.vm.reviewReceipt(); f.actions.confirmError=new RequestRepository.WorkflowException("STALE_VERSION"); f.vm.confirmReceipt(); f.run(); assertEquals(StudentDeliveryViewModel.Mode.BLOCKED,f.state().mode);
        Fixture malformed=ready(); malformed.vm.reviewReceipt(); malformed.actions.confirmError=new RequestRepository.WorkflowException("VALIDATION_FAILED"); malformed.vm.confirmReceipt(); malformed.run(); assertEquals(StudentDeliveryViewModel.Mode.RECEIPT_REVIEW,malformed.state().mode); assertTrue(malformed.state().uncertain);
    }
    @Test public void unconfirmedCanonicalResultCannotDisplayCompleted() {
        Fixture f=ready(); f.vm.reviewReceipt(); f.actions.confirmedStatus=RequestStatus.READY_FOR_PICKUP_DOWNLOAD; f.vm.confirmReceipt(); f.run(); assertEquals(StudentDeliveryViewModel.Mode.RECEIPT_REVIEW,f.state().mode); assertTrue(f.state().uncertain);
    }
    @Test public void changedAccountDuringTransferClosesUnpublishedPrivateBytes() {
        Fixture f=ready(); f.actions.revokeAfterDownload=true; f.vm.prepareDownload(); f.run(); assertTrue(f.actions.document.closed); assertEquals(StudentDeliveryViewModel.Mode.UNAVAILABLE,f.state().mode); assertNull(f.state().request); assertNull(f.state().ticket);
    }
    @Test public void viewModelClearDeletesOwnedCopyAndLatePublicationCannotRestoreIt() {
        Fixture f=ready(); f.vm.prepareDownload(); f.work.run(); ViewModelStore store=new ViewModelStore(); store.put("delivery",f.vm); store.clear(); f.ui.run(); assertTrue(f.actions.document.closed); assertEquals(StudentDeliveryViewModel.Mode.UNAVAILABLE,f.state().mode);
    }
    @Test public void alreadyCompletedCanonicalRequestDoesNotNeedAnotherReceiptOrTransfer() {
        Fixture f=new Fixture(); f.actions.status=RequestStatus.COMPLETED; f.run(); assertEquals(StudentDeliveryViewModel.Mode.COMPLETE,f.state().mode); assertEquals(0,f.actions.confirms); assertEquals(0,f.actions.begins);
    }
    private static Fixture ready() { Fixture f=new Fixture(); f.run(); return f; }
    private static Fixture downloaded() { Fixture f=ready(); f.vm.prepareDownload(); f.run(); return f; }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static DeliveryData.File file(boolean current) { return new DeliveryData.File(id(1),id(90),id(60),id(70),DeliveryData.FileKind.DELIVERABLE,null,"application/pdf",4,"verified",1,current,"2026-10-22T01:00:00Z",null); }
    private static PaymentData.Record payment(String request,PaymentStatus status) { return new PaymentData.Record(id(90),id(8),request,PaymentMethod.CASH,status,BigDecimal.TEN,"PHP",1,id(91),"2026-10-22T01:00:00Z"); }
    private static final class Fixture {
        final Fake actions=new Fake(); final Queue work=new Queue(),ui=new Queue(); final StudentDeliveryViewModel vm=new StudentDeliveryViewModel(RuntimeEnvironment.getApplication(),actions,work,ui);
        Fixture() { vm.ensureRequest(id(70)); } StudentDeliveryViewModel.State state() { return vm.state().getValue(); } void run() { work.run(); ui.run(); }
    }
    static final class Fake implements StudentDeliveryViewModel.Actions {
        final List<String> events=new ArrayList<>(); final List<Uri> discarded=new ArrayList<>(); final Document document=new Document();
        boolean current=true,allowed=true,revokeAfterDownload,wrongAck; String owner=id(90),requested; int begins,downloads,saves,acks,confirms;
        FeeDeclaration fee=FeeDeclaration.FREE; RequestStatus status=RequestStatus.READY_FOR_PICKUP_DOWNLOAD,confirmedStatus=RequestStatus.COMPLETED;
        PaymentData.Record payment; DeliveryData.File file=file(true); Exception paymentError,filesError,saveError,ackError,confirmError; DeliveryData.DownloadTicket ackTicket; DeliveryData.Receipt confirmed;
        public boolean allowed() { return allowed; } public String accountId() { return id(90); } public void requireCurrent() { if(!current)throw new IllegalStateException("Account replaced"); }
        public RequestData.Record request(String id) { requested=id; events.add("request"); return record(status,2); }
        RequestData.Record record(RequestStatus status,long version) { return new RequestData.Record(id(70),"fixture-reference",owner,"OFF-REG","digital","2026-10-22T01:00:00Z",version,status,new RequestData.Service(id(60),"OFF-REG","Configured service",1,fee,fee==FeeDeclaration.REQUIRED?BigDecimal.TEN:BigDecimal.ZERO,fee==FeeDeclaration.REQUIRED?"PHP":null,List.of("digital"),List.of(),true)); }
        public PaymentData.Record payment(String id) throws Exception { events.add("payment"); if(paymentError!=null)throw paymentError; return payment; }
        public DeliveryData.FilePage files(RequestData.Record request) throws Exception { events.add("files"); if(filesError!=null)throw filesError; return new DeliveryData.FilePage(file==null?List.of():List.of(file),null); }
        public DeliveryData.DownloadTicket begin(RequestData.Record request,DeliveryData.File file) { ++begins; events.add("begin"); return new DeliveryData.DownloadTicket(id(80),file.id,"2026-10-22T01:00:00Z","a".repeat(64)); }
        public PrivateDocument download(DeliveryData.File file,DeliveryData.DownloadTicket ticket) { ++downloads; events.add("download"); if(revokeAfterDownload)current=false; return document; }
        public DocumentSaver.Saved save(PrivateDocument source,DeliveryData.File file,DeliveryData.DownloadTicket ticket,Uri target) throws Exception { ++saves; events.add("save"); if(saveError!=null)throw saveError; events.add("save-closed"); return new DocumentSaver.Saved("actual-picked.pdf"); }
        public boolean discardDestination(Uri uri) { discarded.add(uri); return true; }
        public DeliveryData.Acknowledgement acknowledge(DeliveryData.File file,DeliveryData.DownloadTicket ticket) throws Exception { ++acks; events.add("ack"); ackTicket=ticket; if(ackError!=null)throw ackError; return new DeliveryData.Acknowledgement(wrongAck?id(99):ticket.id,"2026-10-22T01:00:00Z"); }
        public DeliveryData.Receipt receipt(RequestData.Record request,DeliveryData.Receipt previous) { return previous==null?new DeliveryData.Receipt(request,id(90),id(81)):previous; }
        public RequestData.Record confirm(DeliveryData.Receipt receipt) throws Exception { ++confirms; confirmed=receipt; if(confirmError!=null)throw confirmError; return record(confirmedStatus,3); }
    }
    static final class Document implements PrivateDocument {
        boolean closed; public Uri uri() { return Uri.parse("content://private-cache/current.pdf"); } public String mimeType() { return "application/pdf"; }
        public java.io.OutputStream openOutput() { throw new UnsupportedOperationException(); } public void close() { closed=true; }
    }
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>(); public void execute(Runnable task) { tasks.add(task); } int size() { return tasks.size(); }
        void run() { assertFalse("Expected queued delivery task",tasks.isEmpty()); tasks.remove().run(); }
    }
}
