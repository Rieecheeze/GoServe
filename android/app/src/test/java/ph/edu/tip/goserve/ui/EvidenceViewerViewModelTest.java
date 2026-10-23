package ph.edu.tip.goserve.ui;

import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import androidx.lifecycle.ViewModelStore;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.data.PrivateDocument;
import ph.edu.tip.goserve.data.RequestRepository;
import ph.edu.tip.goserve.domain.DeliveryData;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34,application=Application.class)
public final class EvidenceViewerViewModelTest {
    @Test public void openingIsSingleFlightAndDownloadsOnlyOnTheWorker() {
        Fixture f=new Fixture(); f.vm.open(file(1)); f.vm.open(file(2));
        assertEquals(EvidenceViewerViewModel.Mode.DOWNLOADING,f.state().mode); assertEquals(0,f.actions.downloads); assertEquals(1,f.work.size());
        f.run(); assertEquals(id(1),f.actions.selected.id); assertEquals(EvidenceViewerViewModel.Mode.READY,f.state().mode);
    }
    @Test public void viewerIntentGrantsOnlyReadAccessToOneCompletedPrivateFile() {
        Fixture f=new Fixture(); f.vm.open(file(1)); f.run(); Intent intent=f.vm.takeIntent();
        assertNotNull(intent); assertEquals(Intent.ACTION_VIEW,intent.getAction()); assertEquals(f.actions.document.uri(),intent.getData());
        assertEquals("application/pdf",intent.getType()); assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,intent.getFlags());
        assertNotNull(intent.getClipData()); assertEquals(1,intent.getClipData().getItemCount()); assertEquals(intent.getData(),intent.getClipData().getItemAt(0).getUri());
    }
    @Test public void retainedModelCannotRelaunchAnAlreadyOpenedViewer() {
        Fixture f=new Fixture(); ViewModelStore store=new ViewModelStore(); store.put("viewer",f.vm);
        f.vm.open(file(1)); f.run(); assertNotNull(f.vm.takeIntent());
        EvidenceViewerViewModel retained=(EvidenceViewerViewModel)store.get("viewer");
        assertNull(retained.takeIntent()); retained.open(file(2)); assertEquals(0,f.work.size()); assertEquals(EvidenceViewerViewModel.Mode.OPEN,f.state().mode);
        store.clear(); assertTrue(f.actions.document.closed);
    }
    @Test public void returningDeletesTheCopyAndDoesNotClaimReceiptOrSavedDelivery() {
        Fixture f=new Fixture(); f.vm.open(file(1)); f.run(); assertNotNull(f.vm.takeIntent()); f.vm.returned();
        assertTrue(f.actions.document.closed); assertEquals(EvidenceViewerViewModel.Mode.IDLE,f.state().mode); assertNull(f.state().fileId); assertNull(f.vm.takeIntent());
    }
    @Test public void missingViewerReleasesTheCopyAndAllowsAnExplicitRetry() {
        Fixture f=new Fixture(); f.vm.open(file(1)); f.run(); f.vm.takeIntent(); Document first=f.actions.document; f.vm.viewerUnavailable();
        assertTrue(first.closed); assertEquals("NO_VIEWER",f.state().error); assertEquals(EvidenceViewerViewModel.Mode.ERROR,f.state().mode);
        f.actions.document=new Document(); f.vm.retry(); f.run(); assertEquals(2,f.actions.downloads); assertNotNull(f.vm.takeIntent());
    }
    @Test public void failedReadHasNoViewerIntentAndRetriesTheSameMetadata() {
        Fixture f=new Fixture(); f.actions.error=new java.io.IOException("private transport detail"); f.vm.open(file(1)); f.run();
        assertEquals("NETWORK_ERROR",f.state().error); assertNull(f.vm.takeIntent());
        f.actions.error=null; f.vm.retry(); f.run(); assertEquals(id(1),f.actions.selected.id); assertNotNull(f.vm.takeIntent());
    }
    @Test public void cancellationBeforePublicationDisposesTheUnpublishedCopy() {
        Fixture f=new Fixture(); f.vm.open(file(1)); f.work.run(); f.vm.cancel(); f.ui.run();
        assertTrue(f.actions.document.closed); assertEquals(EvidenceViewerViewModel.Mode.IDLE,f.state().mode); assertNull(f.vm.takeIntent());
    }
    @Test public void accountReplacementBeforePublicationDisposesPrivateBytes() {
        Fixture f=new Fixture(); f.vm.open(file(1)); f.work.run(); f.actions.current=false; f.ui.run();
        assertTrue(f.actions.document.closed); assertEquals(EvidenceViewerViewModel.Mode.UNAVAILABLE,f.state().mode); assertNull(f.state().fileId);
    }
    @Test public void accountReplacementDuringViewerUseRevokesTheHeldCopy() {
        Fixture f=new Fixture(); f.vm.open(file(1)); f.run(); f.vm.takeIntent(); f.actions.current=false; f.vm.checkCurrentAccount();
        assertTrue(f.actions.document.closed); assertEquals(EvidenceViewerViewModel.Mode.UNAVAILABLE,f.state().mode); assertNull(f.vm.takeIntent());
    }
    @Test public void queuedReadCannotRunAfterModelIsCleared() {
        Fixture f=new Fixture(); f.vm.open(file(1)); ViewModelStore store=new ViewModelStore(); store.put("viewer",f.vm); store.clear(); f.work.run();
        assertEquals(0,f.actions.downloads); assertEquals(EvidenceViewerViewModel.Mode.UNAVAILABLE,f.state().mode);
    }
    @Test public void clearingAfterAReadDisposesItsQueuedPublication() {
        Fixture f=new Fixture(); f.vm.open(file(1)); f.work.run(); ViewModelStore store=new ViewModelStore(); store.put("viewer",f.vm); store.clear(); f.ui.run();
        assertTrue(f.actions.document.closed); assertNull(f.vm.takeIntent());
    }
    @Test public void serverPermissionDenialLocksTheViewerWithoutARepeatedRead() {
        Fixture f=new Fixture(); f.actions.error=new RequestRepository.WorkflowException("FORBIDDEN"); f.vm.open(file(1)); f.run();
        assertEquals(EvidenceViewerViewModel.Mode.UNAVAILABLE,f.state().mode); f.vm.retry(); f.vm.open(file(2)); assertEquals(0,f.work.size());
    }
    @Test public void deliverablesCannotBypassTheSeparateSaveAndAcknowledgementFlow() {
        Fixture f=new Fixture(); DeliveryData.File evidence=file(1);
        DeliveryData.File pdf=new DeliveryData.File(evidence.id,evidence.ownerId,evidence.serviceId,evidence.requestId,DeliveryData.FileKind.DELIVERABLE,
            null,evidence.mimeType,evidence.sizeBytes,evidence.verification,evidence.version,true,evidence.createdAt,null);
        f.vm.open(pdf); assertEquals("VALIDATION_FAILED",f.state().error); assertEquals(0,f.work.size());
    }
    @Test public void synchronousAccountClearingDuringBusyPublicationDoesNotEnqueueARead() {
        Fixture f=new Fixture(); f.vm.state().observeForever(s->{if(s.mode==EvidenceViewerViewModel.Mode.DOWNLOADING) { f.actions.current=false; f.vm.checkCurrentAccount(); }});
        f.vm.open(file(1)); assertEquals(0,f.work.size()); assertEquals(EvidenceViewerViewModel.Mode.UNAVAILABLE,f.state().mode);
    }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static DeliveryData.File file(int n) {
        return new DeliveryData.File(id(n),id(90),id(60),id(70),DeliveryData.FileKind.EVIDENCE,null,"application/pdf",4,"verified",1,true,"2026-10-21T01:00:00Z",null);
    }
    private static final class Fixture {
        final Fake actions=new Fake(); final Queue work=new Queue(),ui=new Queue();
        final EvidenceViewerViewModel vm=new EvidenceViewerViewModel(RuntimeEnvironment.getApplication(),actions,work,ui);
        EvidenceViewerViewModel.State state() { return vm.state().getValue(); }
        void run() { work.run(); ui.run(); }
    }
    private static final class Fake implements EvidenceViewerViewModel.Actions {
        boolean current=true; int downloads; DeliveryData.File selected; Document document=new Document(); Exception error;
        public boolean allowed() { return true; } public void requireCurrent() { if(!current)throw new IllegalStateException("Account changed"); }
        public PrivateDocument download(DeliveryData.File file) throws Exception { selected=file; ++downloads; if(error!=null)throw error; return document; }
    }
    private static final class Document implements PrivateDocument {
        boolean closed;
        public Uri uri() { if(closed)throw new IllegalStateException("Closed"); return Uri.parse("content://ph.edu.tip.goserve.documents/review/fixture.pdf"); }
        public String mimeType() { return "application/pdf"; }
        public java.io.OutputStream openOutput() { throw new AssertionError("Transfer belongs to the repository"); }
        public void close() { closed=true; }
    }
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>(); public void execute(Runnable task) { tasks.add(task); }
        int size() { return tasks.size(); } void run() { assertFalse("Expected queued viewer task",tasks.isEmpty()); tasks.remove().run(); }
    }
}
