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
import ph.edu.tip.goserve.domain.FeeDeclaration;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.RequestStatus;
import ph.edu.tip.goserve.domain.Role;
import ph.edu.tip.goserve.domain.SessionProfile;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=Application.class)
public final class RequestListViewModelTest {
    private static final String TIME="2026-10-21T01:00:00Z";
    @Test public void loadingIsOffMainAndEnsureLoadedDoesNotDuplicateARead() {
        Fixture f=new Fixture(); f.vm.ensureLoaded(); f.vm.ensureLoaded(); assertTrue(f.state().busy()); assertEquals(1,f.work.size()); assertTrue(f.actions.cursors.isEmpty());
        f.work.run(); assertTrue(f.state().items.isEmpty()); f.ui.run(); assertFalse(f.state().busy()); assertEquals(id(1),f.state().items.get(0).id);
        f.vm.ensureLoaded(); assertEquals(0,f.work.size());
    }
    @Test public void pairedTimestampAndIdCursorIsPreservedAcrossPagination() {
        Fixture f=new Fixture(); f.actions.result=page(List.of(row(2)),TIME,id(2)); f.load();
        f.actions.result=page(List.of(row(1)),null,null); f.vm.loadMore(); f.vm.loadMore(); assertEquals(1,f.work.size()); f.run();
        assertEquals(TIME+"|"+id(2),f.actions.cursors.get(1)); assertEquals(id(2),f.state().items.get(0).id); assertEquals(id(1),f.state().items.get(1).id);
        f.vm.loadMore(); assertEquals(0,f.work.size()); assertNull(f.state().nextId); assertNull(f.state().nextCreatedAt);
    }
    @Test public void failedPageRetriesTheSameCursorAndKeepsVisibleRecords() {
        Fixture f=new Fixture(); f.actions.result=page(List.of(row(2)),TIME,id(2)); f.load();
        f.actions.error=new java.io.IOException("private hostname"); f.vm.loadMore(); f.run(); assertEquals("NETWORK_ERROR",f.state().error); assertEquals(1,f.state().items.size());
        f.actions.error=null; f.actions.result=page(List.of(row(1)),null,null); f.vm.retry(); f.run();
        assertEquals(f.actions.cursors.get(1),f.actions.cursors.get(2)); assertEquals(2,f.state().items.size()); assertNull(f.state().error);
    }
    @Test public void refreshDiscardsAnOlderPaginationPublication() {
        Fixture f=new Fixture(); f.actions.result=page(List.of(row(3)),TIME,id(3)); f.load();
        f.actions.result=page(List.of(row(2)),TIME,id(2)); f.vm.loadMore(); f.work.run(); f.vm.refresh(); f.ui.run();
        f.actions.result=page(List.of(row(4)),null,null); f.run(); assertEquals(1,f.state().items.size()); assertEquals(id(4),f.state().items.get(0).id);
        assertEquals("null|null",f.actions.cursors.get(2));
    }
    @Test public void repeatedInvalidationsCoalesceAndPerformOnlyReads() {
        Fixture f=new Fixture(); f.vm.ensureLoaded(); f.vm.refresh(); f.vm.refresh(); f.run(); assertEquals(1,f.work.size()); f.run(); assertEquals(2,f.actions.cursors.size());
    }
    @Test public void failedRefreshIsDistinctFromASuccessfulEmptyPage() {
        Fixture f=new Fixture(); f.load(); f.actions.error=new java.io.IOException("private"); f.vm.refresh(); f.run();
        assertEquals(1,f.state().items.size()); assertEquals("NETWORK_ERROR",f.state().error);
        f.actions.error=null; f.actions.result=page(List.of(),null,null); f.vm.retry(); f.run(); assertTrue(f.state().items.isEmpty()); assertNull(f.state().error);
    }
    @Test public void wrongRoleOrServerRevocationClearsAndDisablesTheList() {
        Fixture denied=new Fixture(false); denied.vm.ensureLoaded(); assertEquals(RequestListViewModel.Mode.UNAVAILABLE,denied.state().mode); assertEquals(0,denied.work.size());
        Fixture f=new Fixture(); f.load(); f.actions.error=new RequestRepository.WorkflowException("FORBIDDEN"); f.vm.refresh(); f.run();
        assertTrue(f.state().items.isEmpty()); assertEquals(RequestListViewModel.Mode.UNAVAILABLE,f.state().mode); f.vm.retry(); assertEquals(0,f.work.size());
    }
    @Test public void accountReplacementSuppressesQueuedSuccessAndError() {
        for(boolean failed:new boolean[]{false,true}) {
            Fixture f=new Fixture(); f.load(); if(failed)f.actions.error=new java.io.IOException("private"); f.vm.refresh(); f.work.run(); f.actions.current=false; f.ui.run();
            assertEquals("STALE_ACCOUNT",f.state().error); assertTrue(f.state().items.isEmpty()); assertNull(f.state().nextId); assertNull(f.state().nextCreatedAt);
        }
    }
    @Test public void revokedOrClearedModelsCannotRunQueuedReads() {
        Fixture f=new Fixture(); f.vm.ensureLoaded(); f.actions.current=false; f.vm.checkCurrentAccount(); f.work.run(); assertTrue(f.actions.cursors.isEmpty());
        Fixture closed=new Fixture(); closed.vm.ensureLoaded(); ViewModelStore store=new ViewModelStore(); store.put("requests",closed.vm); store.clear(); closed.work.run(); assertTrue(closed.actions.cursors.isEmpty());
    }
    @Test public void assignedOfficeIsTrustedModelScopeAndPendingFeesAreNotChangedToFree() {
        Fixture f=new Fixture(); f.actions.office="DEP-CCS"; f.actions.result=page(List.of(new RequestData.Record(id(1),"reference",id(90),"DEP-CCS","physical",TIME,1,RequestStatus.SUBMITTED,
            new RequestData.Service(id(50),"DEP-CCS","Configured service",1,FeeDeclaration.PENDING_REVIEW,null,null,List.of("physical"),List.of(),true))),null,null);
        f.load(); assertEquals("DEP-CCS",f.vm.officeId()); assertEquals(FeeDeclaration.PENDING_REVIEW,f.state().items.get(0).snapshot.fee); assertNull(f.state().items.get(0).snapshot.amount);
    }
    @Test public void productionScopeAllowsReadyStudentAndAssignedStaffButRejectsFacultyAndTellers() {
        assertFalse(RequestListViewModel.permitted(null)); assertFalse(RequestListViewModel.permitted(profile(Role.STUDENT,null,null,false)));
        assertTrue(RequestListViewModel.permitted(profile(Role.STUDENT,null,null,true)));
        assertTrue(RequestListViewModel.permitted(profile(Role.STAFF,"OFF-REG",null,true)));
        assertTrue(RequestListViewModel.permitted(profile(Role.STAFF,"OFF-GCC","guidance",true)));
        assertTrue(RequestListViewModel.permitted(profile(Role.STAFF,"DEP-CCS","secretary",true)));
        assertFalse(RequestListViewModel.permitted(profile(Role.STAFF,null,null,true)));
        assertFalse(RequestListViewModel.permitted(profile(Role.STAFF,"DEP-CCS","faculty",true)));
        assertFalse(RequestListViewModel.permitted(profile(Role.STAFF,"DEP-CCS",null,true)));
        assertFalse(RequestListViewModel.permitted(profile(Role.TELLER,"OFF-CASH",null,true)));
    }
    @Test public void officeFilterClearsPriorRowsAndCursorsBeforeItsFirstServerRead() {
        Fixture f=new Fixture(); f.actions.office="OFF-REG"; f.actions.result=page(List.of(row(3)),TIME,id(3)); f.load();
        f.vm.selectFilter(RequestStatus.PROCESSING); assertTrue(f.state().busy()); assertTrue(f.state().items.isEmpty());
        assertNull(f.state().nextId); assertNull(f.state().nextCreatedAt); assertEquals(RequestStatus.PROCESSING,f.vm.filter());
        f.actions.result=page(List.of(statusRow(2,RequestStatus.PROCESSING)),null,null); f.run();
        assertEquals(RequestStatus.PROCESSING,f.actions.filters.get(1)); assertEquals("null|null",f.actions.cursors.get(1));
        assertEquals(1,f.state().items.size()); assertEquals(id(2),f.state().items.get(0).id);
    }
    @Test public void anotherCategorySupersedesAnAlreadyQueuedOldPublication() {
        Fixture f=new Fixture(); f.actions.office="OFF-REG"; f.load();
        f.vm.selectFilter(RequestStatus.PROCESSING); f.actions.result=page(List.of(statusRow(2,RequestStatus.PROCESSING)),null,null); f.work.run();
        f.vm.selectFilter(RequestStatus.READY_FOR_PICKUP_DOWNLOAD); f.ui.run(); assertTrue(f.state().busy()); assertTrue(f.state().items.isEmpty());
        f.actions.result=page(List.of(statusRow(3,RequestStatus.READY_FOR_PICKUP_DOWNLOAD)),null,null); f.run();
        assertEquals(RequestStatus.READY_FOR_PICKUP_DOWNLOAD,f.vm.filter()); assertEquals(id(3),f.state().items.get(0).id);
    }
    @Test public void filteredFailedPaginationRetriesExactCategoryAndCursorAndRefreshStartsAtItsHead() {
        Fixture f=new Fixture(); f.actions.office="OFF-REG";
        f.actions.result=page(List.of(statusRow(3,RequestStatus.PROCESSING)),TIME,id(3)); f.vm.selectFilter(RequestStatus.PROCESSING); f.run();
        f.actions.error=new java.io.IOException("fixture connection lost"); f.vm.loadMore(); f.run();
        f.actions.error=null; f.actions.result=page(List.of(statusRow(2,RequestStatus.PROCESSING)),null,null); f.vm.retry(); f.run();
        assertEquals(f.actions.cursors.get(1),f.actions.cursors.get(2)); assertEquals(2,f.state().items.size());
        f.vm.refresh(); f.run(); assertEquals("null|null",f.actions.cursors.get(3));
        for(RequestStatus filter:f.actions.filters)assertEquals(RequestStatus.PROCESSING,filter);
    }
    @Test public void ownerHistoryCannotSelectAnOfficeFilterAndUnsupportedCategoriesDoNotRead() {
        Fixture f=new Fixture(); f.load(); f.vm.selectFilter(RequestStatus.PROCESSING); assertNull(f.vm.filter()); assertEquals(0,f.work.size());
        f.actions.office="OFF-REG"; f.vm.selectFilter(RequestStatus.COMPLETED); assertEquals(0,f.work.size());
    }
    @Test public void accountLossClearsTheCategoryAndSuppressesItsQueuedRead() {
        Fixture f=new Fixture(); f.actions.office="OFF-REG"; f.vm.selectFilter(RequestStatus.PROCESSING);
        assertEquals(1,f.work.size()); f.actions.current=false; f.vm.checkCurrentAccount(); f.work.run();
        assertNull(f.vm.filter()); assertTrue(f.actions.filters.isEmpty()); assertTrue(f.state().items.isEmpty());
    }
    private static RequestData.Record statusRow(int n,RequestStatus status) {
        RequestData.Record base=row(n); return new RequestData.Record(base.id,base.reference,base.ownerId,base.officeId,base.deliveryMode,base.createdAt,base.version,status,base.snapshot);
    }
    private static SessionProfile profile(Role role,String office,String capability,boolean ready) {
        return new SessionProfile(id(90),"fixture@demo.goserve.test","Fixture",role,office,capability,true,1,ready,"fixture-terms",ready?"fixture-terms":null,ready?"home":"accept_terms",null);
    }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static RequestData.Record row(int n) { return new RequestData.Record(id(n),"reference-"+n,id(90),"OFF-REG","physical",TIME,1,RequestStatus.SUBMITTED,
        new RequestData.Service(id(50),"OFF-REG","Configured service",1,FeeDeclaration.FREE,BigDecimal.ZERO,null,List.of("physical"),List.of(),true)); }
    private static RequestData.RequestPage page(List<RequestData.Record> items,String at,String id) { return new RequestData.RequestPage(items,at,id); }
    private static final class Fixture {
        final Fake actions=new Fake(); final Queue work=new Queue(),ui=new Queue(); final RequestListViewModel vm;
        Fixture() { this(true); } Fixture(boolean allowed) { actions.allowed=allowed; vm=new RequestListViewModel(RuntimeEnvironment.getApplication(),actions,work,ui); }
        RequestListViewModel.State state() { return vm.state().getValue(); } void load() { vm.ensureLoaded(); run(); } void run() { work.run(); ui.run(); }
    }
    private static final class Fake implements RequestListViewModel.Actions {
        boolean allowed=true,current=true; String office; Exception error; final List<String> cursors=new ArrayList<>(); final List<RequestStatus> filters=new ArrayList<>(); RequestData.RequestPage result=RequestListViewModelTest.page(List.of(row(1)),null,null);
        public boolean allowed() { return allowed; } public String officeId() { return office; } public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public RequestData.RequestPage page(RequestStatus filter,String at,String id) throws Exception { filters.add(filter); cursors.add(at+"|"+id); if(error!=null)throw error; return result; }
    }
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>(); public void execute(Runnable task) { tasks.add(task); }
        int size() { return tasks.size(); } void run() { assertFalse("Expected queued request read",tasks.isEmpty()); tasks.remove().run(); }
    }
}
