package ph.edu.tip.goserve.ui;

import android.app.Application;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.data.OfficeManagementRepository.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34,application=Application.class)
public final class OfficeAnnouncementsViewModelTest {
    @Test public void currentOfficeActiveAnnouncementsLoadOnAWorker(){F f=new F();f.vm.start("OFF-REG");assertTrue(f.s().loading);assertEquals(1,f.work.jobs.size());f.run();assertEquals("OFF-REG",f.a.office);assertEquals("Canonical announcement",f.s().items.get(0).body);}
    @Test public void invalidationsWhileReadingCoalesceToOneMoreCanonicalRead(){F f=new F();f.vm.start("OFF-REG");f.vm.refresh();f.vm.refresh();f.run();assertEquals(1,f.work.jobs.size());f.run();assertEquals(2,f.a.reads);}
    @Test public void scopeChangeClearsOldRowsAndSuppressesLatePublication(){F f=new F();f.vm.start("OFF-REG");f.work.run();f.vm.start("OFF-ACC");f.ui.run();assertTrue(f.s().items.isEmpty());assertEquals("OFF-ACC",f.s().officeId);f.a.item=notice("OFF-ACC",1,"Accounting update",true);f.run();assertEquals("Accounting update",f.s().items.get(0).body);}
    @Test public void accountReplacementSuppressesBothSuccessAndFailure(){for(boolean error:new boolean[]{false,true}){F f=new F();f.a.fail=error;f.vm.start("OFF-REG");f.work.run();f.a.current=false;f.ui.run();assertTrue(f.s().blocked);assertTrue(f.s().items.isEmpty());assertEquals("STALE_ACCOUNT",f.s().error);}}
    @Test public void failedRefreshRetainsOnlyThisActorsPreviouslyConfirmedRows(){F f=loaded();f.a.fail=true;f.vm.refresh();f.run();assertEquals("Canonical announcement",f.s().items.get(0).body);assertEquals("NETWORK_ERROR",f.s().error);f.a.current=false;f.vm.checkCurrentAccount();assertTrue(f.s().items.isEmpty());}
    @Test public void anotherOfficeOrHiddenAnnouncementCannotReachThePublicView(){for(Announcement wrong:List.of(notice("OFF-ACC",1,"Other",true),notice("OFF-REG",1,"Hidden",false))){F f=new F();f.a.item=wrong;f.vm.start("OFF-REG");f.run();assertTrue(f.s().items.isEmpty());assertTrue(f.s().blocked);}}
    @Test public void refreshReplacesTheBodyAndRestartsTheActualCursor(){F f=loaded();f.a.next="00000000-0000-4000-8000-000000000002";f.a.item=notice("OFF-REG",2,"Updated canonical announcement",true);f.vm.refresh();f.run();assertEquals("Updated canonical announcement",f.s().items.get(0).body);assertNull(f.a.afterId);assertEquals(f.a.next,f.s().nextId);}
    @Test public void moreUsesTheCanonicalCursorAndDoesNotDuplicateAnUpdatedRow(){F f=new F();f.a.next="00000000-0000-4000-8000-000000000002";f.vm.start("OFF-REG");f.run();f.a.item=notice("OFF-REG",2,"Latest version",true);String expected=f.a.next;f.a.next=null;f.vm.more();f.run();assertEquals(expected,f.a.afterId);assertEquals(1,f.s().items.size());assertEquals(2,f.s().items.get(0).version);}
    static Announcement notice(String office,long version,String body,boolean active){return new Announcement("00000000-0000-4000-8000-000000000001",office,body,"00000000-0000-4000-8000-000000000090","2026-10-22T00:00:00Z",active,version);}
    static F loaded(){F f=new F();f.vm.start("OFF-REG");f.run();return f;}
    static final class F{final A a=new A();final OfficeManagementViewModelTest.Q work=new OfficeManagementViewModelTest.Q(),ui=new OfficeManagementViewModelTest.Q();final OfficeAnnouncementsViewModel vm=new OfficeAnnouncementsViewModel(RuntimeEnvironment.getApplication(),a,work,ui);OfficeAnnouncementsViewModel.State s(){return vm.state().getValue();}void run(){work.run();ui.run();}}
    static final class A implements OfficeAnnouncementsViewModel.Actions{boolean current=true,fail,empty;String office,afterId,next;int reads;Announcement item=notice("OFF-REG",1,"Canonical announcement",true);public boolean allowed(){return true;}public void requireCurrent(){if(!current)throw new IllegalStateException("changed");}public Page<Announcement> page(String office,String time,String id)throws Exception{this.office=office;afterId=id;reads++;if(fail)throw new java.io.IOException();return new Page<>(empty?List.of():List.of(item),next==null?null:"2026-10-22T00:00:00Z",next);}}
}
