package ph.edu.tip.goserve.ui;

import android.app.Application;
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
import ph.edu.tip.goserve.data.RequestRepository;
import ph.edu.tip.goserve.domain.DeliveryData;
import ph.edu.tip.goserve.domain.FeeDeclaration;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.RequestStatus;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34,application=Application.class)
public final class RequestFilesViewModelTest {
    @Test public void canonicalRequestAndKindArePinnedAndReadOffMain() {
        Fixture f=new Fixture(); f.vm.ensureRequest(id(99),DeliveryData.FileKind.DELIVERABLE);
        assertTrue(f.state().busy()); assertEquals(0,f.actions.requestReads); f.run();
        assertEquals(id(70),f.actions.requestId); assertEquals(DeliveryData.FileKind.EVIDENCE,f.actions.kinds.get(0));
        assertEquals(1,f.actions.requestReads); assertEquals(1,f.state().items.size());
    }
    @Test public void onlyASuccessfulReadCanShowAnEmptyFilePage() {
        Fixture f=new Fixture(); f.actions.page=new DeliveryData.FilePage(List.of(),null); assertTrue(f.state().busy()); f.run();
        assertFalse(f.state().busy()); assertNull(f.state().error); assertTrue(f.state().items.isEmpty()); assertNotNull(f.state().request);
    }
    @Test public void mismatchedRequestCannotPublishAnyPrivateMetadata() {
        Fixture f=new Fixture(); RequestData.Record old=f.actions.record;
        f.actions.record=new RequestData.Record(id(99),old.reference,old.ownerId,old.officeId,old.deliveryMode,old.createdAt,old.version,old.status,old.snapshot);
        f.run(); assertEquals(RequestFilesViewModel.Mode.UNAVAILABLE,f.state().mode); assertTrue(f.state().items.isEmpty()); assertEquals(0,f.actions.cursors.size());
    }
    @Test public void failedInitialReadIsRetryableWithoutInventingFiles() {
        Fixture f=new Fixture(); f.actions.requestError=new java.io.IOException("private host"); f.run();
        assertEquals("NETWORK_ERROR",f.state().error); assertNull(f.state().request); assertTrue(f.state().items.isEmpty());
        f.actions.requestError=null; f.vm.retry(); f.run(); assertNotNull(f.state().request); assertEquals(2,f.actions.requestReads);
    }
    @Test public void paginationPreservesKindAndCursorWithoutRepeatedRequestReads() {
        Fixture f=new Fixture(); f.actions.page=new DeliveryData.FilePage(List.of(file(1,1)),id(1)); f.run();
        f.actions.page=new DeliveryData.FilePage(List.of(file(2,1)),null); f.vm.loadMore(); f.vm.loadMore(); assertEquals(1,f.work.size()); f.run();
        assertEquals(id(1),f.actions.cursors.get(1)); assertEquals(2,f.state().items.size()); assertEquals(1,f.actions.requestReads);
        assertEquals(DeliveryData.FileKind.EVIDENCE,f.actions.kinds.get(1));
    }
    @Test public void failedPageRetainsVisibleFilesAndRetriesItsExactCursor() {
        Fixture f=new Fixture(); f.actions.page=new DeliveryData.FilePage(List.of(file(1,1)),id(1)); f.run();
        f.actions.fileError=new java.io.IOException("fixture lost"); f.vm.loadMore(); f.run(); assertEquals(1,f.state().items.size());
        f.actions.fileError=null; f.actions.page=new DeliveryData.FilePage(List.of(file(2,1)),null); f.vm.retry(); f.run();
        assertEquals(f.actions.cursors.get(1),f.actions.cursors.get(2)); assertEquals(2,f.state().items.size());
    }
    @Test public void refreshSupersedesOldPaginationAndReloadsCanonicalRequest() {
        Fixture f=new Fixture(); f.actions.page=new DeliveryData.FilePage(List.of(file(1,1)),id(1)); f.run();
        f.actions.page=new DeliveryData.FilePage(List.of(file(2,1)),null); f.vm.loadMore(); f.work.run(); f.vm.refresh(); f.ui.run();
        f.actions.page=new DeliveryData.FilePage(List.of(file(3,1)),null); f.run();
        assertEquals(1,f.state().items.size()); assertEquals(id(3),f.state().items.get(0).id); assertEquals(2,f.actions.requestReads);
    }
    @Test public void repeatedInvalidationsCoalesceAndKeepTheNewestFileVersion() {
        Fixture f=new Fixture(); f.vm.refresh(); f.vm.refresh(); f.run(); assertEquals(1,f.work.size());
        f.actions.page=new DeliveryData.FilePage(List.of(file(1,2)),null); f.run();
        assertEquals(2,f.actions.requestReads); assertEquals(2,f.state().items.get(0).version);
    }
    @Test public void replacingTheAccountBetweenRequestAndFilesStopsTheSecondRead() {
        Fixture f=new Fixture(); f.actions.revokeAfterRequest=true; f.run();
        assertEquals(RequestFilesViewModel.Mode.UNAVAILABLE,f.state().mode); assertTrue(f.actions.cursors.isEmpty()); assertNull(f.state().request);
    }
    @Test public void replacingTheAccountBeforePublicationClearsAllPrivateMetadata() {
        Fixture f=new Fixture(); f.work.run(); f.actions.current=false; f.ui.run();
        assertEquals(RequestFilesViewModel.Mode.UNAVAILABLE,f.state().mode); assertTrue(f.state().items.isEmpty()); assertNull(f.state().nextId); assertNull(f.state().request);
    }
    @Test public void serverRevocationClearsPermanentlyAndMissingRequestRemovesOldRows() {
        Fixture denied=new Fixture(); denied.actions.fileError=new RequestRepository.WorkflowException("FORBIDDEN"); denied.run();
        assertEquals(RequestFilesViewModel.Mode.UNAVAILABLE,denied.state().mode); denied.vm.retry(); assertEquals(0,denied.work.size());
        Fixture missing=new Fixture(); missing.run(); missing.actions.requestError=new RequestRepository.WorkflowException("NOT_FOUND"); missing.vm.refresh(); missing.run();
        assertTrue(missing.state().items.isEmpty()); assertNull(missing.state().request); assertEquals("NOT_FOUND",missing.state().error);
    }
    @Test public void deniedOrClearedModelsDoNotRunQueuedFileReads() {
        Fixture denied=new Fixture(false); assertEquals(0,denied.work.size());
        Fixture f=new Fixture(); ViewModelStore store=new ViewModelStore(); store.put("files",f.vm); store.clear(); f.work.run(); assertEquals(0,f.actions.requestReads);
    }
    @Test public void invalidRouteOrMissingKindCannotIssueARead() {
        Queue work=new Queue(),ui=new Queue(); Fake actions=new Fake();
        RequestFilesViewModel vm=new RequestFilesViewModel(RuntimeEnvironment.getApplication(),actions,work,ui);
        vm.ensureRequest("not-an-id",DeliveryData.FileKind.EVIDENCE); assertEquals(0,work.size()); assertEquals(RequestFilesViewModel.Mode.UNAVAILABLE,vm.state().getValue().mode);
        RequestFilesViewModel noKind=new RequestFilesViewModel(RuntimeEnvironment.getApplication(),actions,work,ui);
        noKind.ensureRequest(id(70),null); assertEquals(0,work.size());
    }
    private static DeliveryData.File file(int n,long version) {
        return new DeliveryData.File(id(n),id(90),id(60),id(70),DeliveryData.FileKind.EVIDENCE,"configured-requirement","application/pdf",4,"verified",version,true,"2026-10-21T01:00:00Z",null);
    }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static final class Fixture {
        final Fake actions=new Fake(); final Queue work=new Queue(),ui=new Queue(); final RequestFilesViewModel vm;
        Fixture() { this(true); } Fixture(boolean allowed) { actions.allowed=allowed; vm=new RequestFilesViewModel(RuntimeEnvironment.getApplication(),actions,work,ui); vm.ensureRequest(id(70),DeliveryData.FileKind.EVIDENCE); }
        RequestFilesViewModel.State state() { return vm.state().getValue(); } void run() { work.run(); ui.run(); }
    }
    private static final class Fake implements RequestFilesViewModel.Actions {
        boolean allowed=true,current=true,revokeAfterRequest; int requestReads; String requestId; Exception requestError,fileError;
        final List<String> cursors=new ArrayList<>(); final List<DeliveryData.FileKind> kinds=new ArrayList<>();
        DeliveryData.FilePage page=new DeliveryData.FilePage(List.of(file(1,1)),null);
        RequestData.Record record=new RequestData.Record(id(70),"fixture-reference",id(90),"OFF-REG","digital","2026-10-21T01:00:00Z",1,RequestStatus.PROCESSING,
            new RequestData.Service(id(60),"OFF-REG","Configured fixture",1,FeeDeclaration.FREE,BigDecimal.ZERO,null,List.of("digital"),List.of("configured-requirement"),true));
        public boolean allowed() { return allowed; } public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public RequestData.Record request(String id) throws Exception { ++requestReads; requestId=id; if(requestError!=null)throw requestError; if(revokeAfterRequest)current=false; return record; }
        public DeliveryData.FilePage files(RequestData.Record request,DeliveryData.FileKind kind,String afterId) throws Exception { cursors.add(afterId); kinds.add(kind); if(fileError!=null)throw fileError; return page; }
    }
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>(); public void execute(Runnable task) { tasks.add(task); }
        int size() { return tasks.size(); } void run() { assertFalse("Expected queued file read",tasks.isEmpty()); tasks.remove().run(); }
    }
}
