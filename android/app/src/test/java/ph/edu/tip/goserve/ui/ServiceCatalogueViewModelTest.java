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
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=Application.class)
public final class ServiceCatalogueViewModelTest {
    @Test public void firstOfficeIsPinnedAndCataloguesLoadOffMainWithoutExamples() {
        Fixture f=new Fixture(); f.vm.ensureOffice("OFF-REG"); f.vm.ensureOffice("OFF-GCC");
        assertTrue(f.state().busy()); assertEquals(1,f.work.size()); assertEquals(0,f.actions.reads); assertTrue(f.state().items.isEmpty());
        f.run(); assertEquals("OFF-REG",f.actions.office); assertEquals(1,f.state().items.size());
        assertEquals(FeeDeclaration.PENDING_REVIEW,f.state().items.get(0).fee);
    }
    @Test public void successfulEmptyIsDifferentFromUnloadedAndFailedReads() {
        Fixture f=new Fixture(); assertEquals(ServiceCatalogueViewModel.Mode.EMPTY,f.state().mode);
        f.actions.page=new RequestData.ServicePage(List.of(),null); f.load();
        assertEquals(ServiceCatalogueViewModel.Mode.CONTENT,f.state().mode); assertTrue(f.state().items.isEmpty()); assertNull(f.state().error);
    }
    @Test public void missingOfficeNeverIssuesIoOrCreatesADefaultOffice() {
        for(String office:new String[]{null,"","  "}) { Fixture f=new Fixture(); f.vm.ensureOffice(office); assertEquals("VALIDATION_FAILED",f.state().error); assertEquals(0,f.work.size()); }
    }
    @Test public void onlyCanonicalCursorCanAdvanceTheCatalogue() {
        Fixture f=new Fixture(); f.actions.page=new RequestData.ServicePage(List.of(service(1,1)),id(1)); f.load();
        f.actions.page=new RequestData.ServicePage(List.of(service(2,1)),null); f.vm.loadMore(); f.run();
        assertEquals(id(1),f.actions.cursor); assertEquals(2,f.state().items.size()); assertNull(f.state().nextId);
        f.vm.loadMore(); assertEquals(0,f.work.size());
    }
    @Test public void failedNextPageRetainsRowsAndRetriesTheExactCursor() {
        Fixture f=new Fixture(); f.actions.page=new RequestData.ServicePage(List.of(service(1,1)),id(1)); f.load();
        f.actions.error=new java.io.IOException("private host"); f.vm.loadMore(); f.run(); assertEquals("NETWORK_ERROR",f.state().error); assertEquals(1,f.state().items.size());
        f.actions.error=null; f.actions.page=new RequestData.ServicePage(List.of(service(2,1)),null); f.vm.retry(); f.run();
        assertEquals(id(1),f.actions.cursor); assertEquals(2,f.state().items.size());
    }
    @Test public void refreshSupersedesAnOlderAppendAndCannotResurrectItsRows() {
        Fixture f=new Fixture(); f.actions.page=new RequestData.ServicePage(List.of(service(1,1)),id(1)); f.load();
        f.vm.loadMore(); f.work.run(); f.vm.refresh(); f.actions.page=new RequestData.ServicePage(List.of(service(3,1)),null);
        f.ui.run(); assertTrue(f.state().busy()); f.run(); assertEquals(1,f.state().items.size()); assertEquals(id(3),f.state().items.get(0).id);
    }
    @Test public void repeatedInvalidationsCoalesceAndDuplicateRowsKeepNewerVersions() {
        Fixture f=new Fixture(); f.vm.ensureOffice("OFF-REG"); f.vm.refresh(); f.vm.refresh(); f.run(); assertEquals(1,f.work.size());
        f.actions.page=new RequestData.ServicePage(List.of(service(1,2),service(1,1)),null); f.run();
        assertEquals(2,f.actions.reads); assertEquals(1,f.state().items.size()); assertEquals(2,f.state().items.get(0).version);
    }
    @Test public void unreadyOrRevokedAccountsCannotReadOrRetainServices() {
        Fixture denied=new Fixture(false); denied.vm.ensureOffice("OFF-REG"); assertEquals(ServiceCatalogueViewModel.Mode.UNAVAILABLE,denied.state().mode); assertEquals(0,denied.work.size());
        Fixture f=new Fixture(); f.load(); f.actions.error=new RequestRepository.WorkflowException("FORBIDDEN"); f.vm.refresh(); f.run();
        assertEquals(ServiceCatalogueViewModel.Mode.UNAVAILABLE,f.state().mode); assertTrue(f.state().items.isEmpty()); f.vm.retry(); assertEquals(0,f.work.size());
    }
    @Test public void accountReplacementBeforeWorkAndBeforePublicationCannotLeakRows() {
        Fixture early=new Fixture(); early.vm.ensureOffice("OFF-REG"); early.actions.current=false; early.vm.checkCurrentAccount(); early.work.run(); assertEquals(0,early.actions.reads);
        Fixture late=new Fixture(); late.vm.ensureOffice("OFF-REG"); late.work.run(); late.actions.current=false; late.ui.run();
        assertEquals("STALE_ACCOUNT",late.state().error); assertNull(late.state().officeId); assertTrue(late.state().items.isEmpty());
    }
    @Test public void clearedCatalogueCannotPerformQueuedIo() {
        Fixture f=new Fixture(); f.vm.ensureOffice("OFF-REG"); ViewModelStore store=new ViewModelStore(); store.put("catalogue",f.vm); store.clear(); f.work.run();
        assertEquals(0,f.actions.reads);
    }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static RequestData.Service service(int n,long version) { return new RequestData.Service(id(n),"OFF-REG","Configured service",version,FeeDeclaration.PENDING_REVIEW,null,null,List.of("physical"),List.of(),true); }
    private static final class Fixture {
        final Fake actions=new Fake(); final Queue work=new Queue(),ui=new Queue(); final ServiceCatalogueViewModel vm;
        Fixture() { this(true); } Fixture(boolean allowed) { actions.allowed=allowed; vm=new ServiceCatalogueViewModel(RuntimeEnvironment.getApplication(),actions,work,ui); }
        ServiceCatalogueViewModel.State state() { return vm.state().getValue(); } void load() { vm.ensureOffice("OFF-REG"); run(); } void run() { work.run(); ui.run(); }
    }
    private static final class Fake implements ServiceCatalogueViewModel.Actions {
        boolean allowed=true,current=true; int reads; String office,cursor; Exception error;
        RequestData.ServicePage page=new RequestData.ServicePage(List.of(service(1,1)),null);
        public boolean allowed() { return allowed; } public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public RequestData.ServicePage services(String office,String cursor) throws Exception { ++reads; this.office=office; this.cursor=cursor; if(error!=null)throw error; return page; }
    }
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>(); public void execute(Runnable task) { tasks.add(task); }
        int size() { return tasks.size(); } void run() { assertFalse("Expected queued catalogue task",tasks.isEmpty()); tasks.remove().run(); }
    }
}
