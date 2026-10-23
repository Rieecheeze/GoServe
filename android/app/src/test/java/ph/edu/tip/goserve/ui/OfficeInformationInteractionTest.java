package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.lifecycle.*;
import java.util.List;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.AccountIntegrationTest;
import ph.edu.tip.goserve.domain.*;
import ph.edu.tip.goserve.data.OfficeManagementRepository.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,qualifiers="w390dp-h844dp-port-notnight-mdpi",shadows={AccountIntegrationTest.Store.class,AccountIntegrationTest.Server.class},instrumentedPackages={"ph.edu.tip.goserve.data"})
@LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class OfficeInformationInteractionTest {
    final ThreadInteractionTest host=new ThreadInteractionTest();final OfficeManagementViewModelTest.Q work=new OfficeManagementViewModelTest.Q(),ui=new OfficeManagementViewModelTest.Q();
    final OfficeViewModelTest.A offices=new OfficeViewModelTest.A();final OfficeAnnouncementsViewModelTest.A notices=new OfficeAnnouncementsViewModelTest.A();
    OfficeViewModel officeModel;OfficeAnnouncementsViewModel announcementModel;String officeId="OFF-REG";
    @Before public void setup()throws Exception{host.setup();}
    @After public void close(){host.close();}
    @Test public void canonicalOfficeInformationReplacesTheInitialNavigationSnapshot(){offices.rows=List.of(OfficeViewModelTest.office(OfficeStatus.ON_LUNCH_BREAK,"Current office note"));open();assertEquals("On Lunch Break",text(R.id.officeStatus));assertEquals("Current office note",text(R.id.officeNote));assertEquals("Registrar",text(R.id.officeName));}
    @Test public void aCommittedStatusAndNoteRefreshOnTheSameOpenScreen(){open();offices.rows=List.of(OfficeViewModelTest.office(OfficeStatus.CUT_OFF_REACHED,"Updated office note"));officeModel.reload(false);flush();assertEquals("Cut-Off Reached",text(R.id.officeStatus));assertEquals("Updated office note",text(R.id.officeNote));}
    @Test public void anOfficeRemovedFromTheCanonicalDirectoryCannotKeepItsOldDetailsOrStartAction(){open();offices.rows=List.of();officeModel.reload(false);flush();assertEquals("",text(R.id.officeStatus));assertEquals("",text(R.id.officeNote));assertFalse(v(R.id.startRequest).isEnabled());}
    @Test public void activeAnnouncementsUseRealBodyAndServerTime(){open();assertEquals(View.VISIBLE,v(R.id.officeAnnouncementsSection).getVisibility());assertEquals("Canonical announcement",text(R.id.officeAnnouncementBody));assertTrue(text(R.id.officeAnnouncementTime).contains("Oct"));assertFalse(text(R.id.officeAnnouncementBody).contains(OfficeManagementViewModelTest.ACTOR));}
    @Test public void announcementRefreshUpdatesTheBodyWithoutReopeningTheOffice(){open();notices.item=OfficeAnnouncementsViewModelTest.notice("OFF-REG",2,"Updated office announcement",true);announcementModel.refresh();flush();assertEquals("Updated office announcement",text(R.id.officeAnnouncementBody));}
    @Test public void visibilityLossWithoutARowEventIsReconciledWhileVisibleAndPollingStopsInBackground(){open();assertEquals(1,((android.view.ViewGroup)v(R.id.officeAnnouncementRows)).getChildCount());notices.empty=true;Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(31));flush();assertEquals(0,((android.view.ViewGroup)v(R.id.officeAnnouncementRows)).getChildCount());assertEquals(View.VISIBLE,v(R.id.officeAnnouncementsEmpty).getVisibility());int reads=notices.reads;host.controller.pause().stop();Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(61));flush();assertEquals(reads,notices.reads);}
    @Test public void failedRefreshRetainsTheConfirmedAnnouncementAndOffersRetry(){open();notices.fail=true;v(R.id.officeAnnouncementsRefresh).performClick();flush();assertEquals("Canonical announcement",text(R.id.officeAnnouncementBody));assertEquals(View.VISIBLE,v(R.id.officeAnnouncementsError).getVisibility());notices.fail=false;v(R.id.officeAnnouncementsRefresh).performClick();flush();assertEquals(View.GONE,v(R.id.officeAnnouncementsError).getVisibility());}
    @Test public void accountInvalidationClearsOfficeDetailsAndAnnouncementRows(){open();offices.current=false;notices.current=false;((MutableLiveData<Long>)((GoServeApplication)host.activity.getApplication()).accountInvalidations()).setValue(1042L);ThreadInteractionTest.idle();assertEquals("",text(R.id.officeName));assertEquals("",text(R.id.officeNote));assertEquals(0,((android.view.ViewGroup)v(R.id.officeAnnouncementRows)).getChildCount());}
    @Test public void cashierPageAlsoReceivesCurrentStatusAndAnnouncements(){officeId="OFF-CASH";offices.rows=List.of(new Office(officeId,"Cashiering","Actual campus",OfficeStatus.TEMPORARILY_CLOSED,"Current teller note","Payments",true));notices.item=OfficeAnnouncementsViewModelTest.notice(officeId,1,"Cashiering announcement",true);open();assertEquals("Temporarily Closed",text(R.id.officeStatus));assertEquals("Cashiering announcement",text(R.id.officeAnnouncementBody));assertEquals(View.GONE,v(R.id.paymentsTitle).getVisibility());}
    @Test public void captureOfficeInformationAndReachAnnouncementRefreshAtLargeText()throws Exception{for(boolean dark:new boolean[]{false,true}){RuntimeEnvironment.setQualifiers("w390dp-h844dp-port-"+(dark?"night":"notnight")+"-mdpi");open();assertEquals("Canonical announcement",text(R.id.officeAnnouncementBody));host.capture("office-information-"+(dark?"dark":"light")+".png",390,844);reset();RuntimeEnvironment.setQualifiers("w320dp-h480dp-port-"+(dark?"night":"notnight")+"-mdpi");RuntimeEnvironment.setFontScale(2f);open();host.measure(320,480);View target=v(R.id.officeAnnouncementsRefresh);androidx.core.widget.NestedScrollView scroll=(androidx.core.widget.NestedScrollView)v(R.id.officeDetailScroll);android.graphics.Rect rect=new android.graphics.Rect();target.getDrawingRect(rect);scroll.offsetDescendantRectToMyCoords(target,rect);scroll.scrollTo(0,rect.bottom-scroll.getHeight());ThreadInteractionTest.idle();host.capture("office-information-large-"+(dark?"dark":"light")+"-announcements.png",320,480);assertTrue(target.getGlobalVisibleRect(rect));assertEquals(target.getHeight(),rect.height());reset();RuntimeEnvironment.setFontScale(1f);}}
    void open(){host.open(true);officeModel=new OfficeViewModel(RuntimeEnvironment.getApplication(),offices,work,ui);announcementModel=new OfficeAnnouncementsViewModel(RuntimeEnvironment.getApplication(),notices,work,ui);ViewModelProvider.Factory factory=new ViewModelProvider.Factory(){@Override public <T extends ViewModel>T create(Class<T> type){return type.cast(announcementModel);}};OfficeDetailFragment f=new OfficeDetailFragment(officeModel,factory);Bundle args=new Bundle();args.putString("id",officeId);args.putString("name","Initial snapshot");args.putString("status","Open");args.putString("note","Initial note");args.putString("campus","Initial campus");args.putBoolean("demo",true);f.setArguments(args);host.activity.getSupportFragmentManager().beginTransaction().replace(R.id.mainContainer,f).commitNow();flush();}
    void flush(){for(int n=0;n<30;n++){ThreadInteractionTest.idle();if(work.jobs.isEmpty() && ui.jobs.isEmpty())return;while(!work.jobs.isEmpty())work.run();while(!ui.jobs.isEmpty())ui.run();}fail("Unbounded work");}
    View v(int id){return host.activity.findViewById(id);}String text(int id){return ((TextView)v(id)).getText().toString();}
    void reset(){host.reset();work.jobs.clear();ui.jobs.clear();}
}
