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
import ph.edu.tip.goserve.data.PaymentRepository;
import ph.edu.tip.goserve.domain.PaymentData;
import ph.edu.tip.goserve.domain.PaymentMethod;
import ph.edu.tip.goserve.domain.PaymentStatus;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=Application.class)
public final class CashHistoryViewModelTest {
    @Test public void initialLoadRunsOffMainAndRotationDoesNotRepeatIt() {
        Fixture f=new Fixture(); f.vm.ensureLoaded(); f.vm.ensureLoaded();
        assertTrue(f.state().busy()); assertEquals(1,f.work.size()); assertEquals(0,f.actions.cursors.size());
        f.work.run(); assertTrue(f.state().items.isEmpty()); f.ui.run();
        assertEquals(1,f.state().items.size()); assertFalse(f.state().busy()); assertNull(f.actions.cursors.get(0));
        f.vm.ensureLoaded(); assertEquals(0,f.work.size());
    }
    @Test public void paginationUsesOnlyTheCanonicalCursorAndNeverGuessesAnotherPage() {
        Fixture f=new Fixture(); f.actions.result=new PaymentData.Page(List.of(row(1,1)),id(1)); f.load();
        f.actions.result=new PaymentData.Page(List.of(row(2,1)),null); f.vm.loadMore(); f.vm.loadMore();
        assertTrue(f.state().loadingMore); assertEquals(1,f.state().items.size()); f.work.run(); f.ui.run();
        assertEquals(java.util.Arrays.asList(null,id(1)),f.actions.cursors); assertEquals(2,f.state().items.size());
        assertNull(f.state().nextId); f.vm.loadMore(); assertEquals(0,f.work.size());
    }
    @Test public void failedNextPageKeepsRowsAndRetriesTheSameCursor() {
        Fixture f=new Fixture(); f.actions.result=new PaymentData.Page(List.of(row(1,1)),id(1)); f.load();
        f.actions.error=new java.io.IOException("private server detail"); f.vm.loadMore(); f.run();
        assertEquals("NETWORK_ERROR",f.state().error); assertEquals(1,f.state().items.size()); assertEquals(id(1),f.state().nextId);
        f.actions.error=null; f.actions.result=new PaymentData.Page(List.of(row(2,1)),null); f.vm.retry(); f.run();
        assertEquals(id(1),f.actions.cursors.get(2)); assertEquals(2,f.state().items.size()); assertNull(f.state().error);
    }
    @Test public void refreshReplacesTheOldPageAndDropsAnOldPaginationResult() {
        Fixture f=new Fixture(); f.actions.result=new PaymentData.Page(List.of(row(1,1)),id(1)); f.load();
        f.actions.result=new PaymentData.Page(List.of(row(2,1)),null); f.vm.loadMore(); f.work.run();
        f.vm.refresh(); f.actions.result=new PaymentData.Page(List.of(row(3,1)),null); f.ui.run(); f.work.run(); f.ui.run();
        assertEquals(1,f.state().items.size()); assertEquals(id(3),f.state().items.get(0).id); assertNull(f.actions.cursors.get(2));
    }
    @Test public void repeatedRealtimeRefreshesCoalesceIntoOneFollowupRead() {
        Fixture f=new Fixture(); f.vm.ensureLoaded(); f.vm.refresh(); f.vm.refresh();
        f.run(); assertEquals(1,f.work.size()); f.run(); assertEquals(2,f.actions.cursors.size());
    }
    @Test public void failedRefreshKeepsPreviouslyVisibleRowsWithAnExplicitError() {
        Fixture f=new Fixture(); f.load(); f.actions.error=new java.io.IOException("private"); f.vm.refresh(); f.run();
        assertEquals(1,f.state().items.size()); assertEquals("NETWORK_ERROR",f.state().error); assertFalse(f.state().busy());
        f.actions.error=null; f.actions.result=new PaymentData.Page(List.of(),null); f.vm.retry(); f.run(); assertTrue(f.state().items.isEmpty());
    }
    @Test public void wrongRoleAndServerDenialClearAndDisableHistory() {
        Fixture denied=new Fixture(false); denied.vm.ensureLoaded(); assertEquals(CashHistoryViewModel.Mode.UNAVAILABLE,denied.state().mode); assertEquals(0,denied.work.size());
        Fixture f=new Fixture(); f.load(); f.actions.error=new PaymentRepository.WorkflowException("FORBIDDEN"); f.vm.refresh(); f.run();
        assertEquals(CashHistoryViewModel.Mode.UNAVAILABLE,f.state().mode); assertTrue(f.state().items.isEmpty()); f.vm.retry(); assertEquals(0,f.work.size());
    }
    @Test public void accountReplacementRejectsQueuedSuccessOrFailureAndClearsRows() {
        for(boolean fail:new boolean[]{false,true}) {
            Fixture f=new Fixture(); f.load(); if(fail)f.actions.error=new java.io.IOException("private");
            f.vm.refresh(); f.work.run(); f.actions.current=false; f.ui.run();
            assertEquals("STALE_ACCOUNT",f.state().error); assertTrue(f.state().items.isEmpty()); assertNull(f.state().nextId);
        }
    }
    @Test public void revokedLeasePreventsQueuedIoAndClearedModelsNeverPublish() {
        Fixture f=new Fixture(); f.vm.ensureLoaded(); f.actions.current=false; f.vm.checkCurrentAccount(); f.work.run();
        assertEquals(0,f.actions.cursors.size()); assertTrue(f.state().items.isEmpty());
        Fixture closed=new Fixture(); closed.vm.ensureLoaded(); ViewModelStore store=new ViewModelStore(); store.put("history",closed.vm); store.clear(); closed.work.run();
        assertEquals(0,closed.actions.cursors.size()); assertTrue(closed.state().items.isEmpty());
    }
    @Test public void pageFailureDoesNotTurnIntoAnEmptySuccessfulHistory() {
        Fixture f=new Fixture(); f.actions.error=new PaymentRepository.WorkflowException("CONFIGURATION_REQUIRED"); f.load();
        assertEquals("CONFIGURATION_REQUIRED",f.state().error); assertTrue(f.state().items.isEmpty()); assertFalse(f.state().busy());
    }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static PaymentData.Record row(int n,long version) {
        return new PaymentData.Record(id(90),id(n),id(50+n),PaymentMethod.CASH,PaymentStatus.PAID,new BigDecimal("35.00"),"PHP",version,id(91),"2026-10-21T01:00:00Z");
    }
    private static final class Fixture {
        final Fake actions=new Fake(); final Queue work=new Queue(),ui=new Queue(); final CashHistoryViewModel vm;
        Fixture() { this(true); }
        Fixture(boolean allowed) { actions.allowed=allowed; vm=new CashHistoryViewModel(RuntimeEnvironment.getApplication(),actions,work,ui); }
        CashHistoryViewModel.State state() { return vm.state().getValue(); }
        void load() { vm.ensureLoaded(); run(); }
        void run() { work.run(); ui.run(); }
    }
    private static final class Fake implements CashHistoryViewModel.Actions {
        boolean allowed=true,current=true; Exception error; final List<String> cursors=new ArrayList<>();
        PaymentData.Page result=new PaymentData.Page(List.of(row(1,1)),null);
        public boolean allowed() { return allowed; }
        public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public void invalidate() {}
        public PaymentData.Page page(String after) throws Exception { cursors.add(after); if(error!=null)throw error; return result; }
    }
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>(); public void execute(Runnable task) { tasks.add(task); }
        void run() { assertFalse("Expected queued history read",tasks.isEmpty()); tasks.remove().run(); }
        int size() { return tasks.size(); }
    }
}
