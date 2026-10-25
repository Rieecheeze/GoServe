package ph.edu.tip.goserve.ui;

import android.view.View;
import androidx.fragment.app.Fragment;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.AccountIntegrationTest;
import ph.edu.tip.goserve.domain.Role;
import ph.edu.tip.goserve.domain.SessionProfile;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,qualifiers="w390dp-h844dp-port-notnight-mdpi",shadows={AccountIntegrationTest.Store.class,AccountIntegrationTest.Server.class},instrumentedPackages={"ph.edu.tip.goserve.data"})
@LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class ScreenRoutesInteractionTest {
    final ThreadInteractionTest f=new ThreadInteractionTest();
    @Before public void before() throws Exception {f.setup();}
    @After public void after(){f.close();}
    @Test public void registrarWorkspaceUsesAssignedOfficeAndDoesNotOfferScheduling() {
        openStaff("OFF-REG",null);assertTrue(current() instanceof StaffHomeFragment);
        assertTrue(f.text(R.id.workspaceOffice).contains("OFF-REG"));assertEquals(View.GONE,f.v(R.id.workspaceSchedule).getVisibility());
        assertNull(nav().getMenu().findItem(R.id.nav_consult));f.v(R.id.workspaceQueue).performClick();execute();assertTrue(current() instanceof RequestListFragment);
    }
    @Test public void secretaryWorkspaceHasTheScheduleAndInquiryRoutes() {
        openStaff("DEP-CCS","secretary");assertEquals(View.VISIBLE,f.v(R.id.workspaceSchedule).getVisibility());assertNotNull(nav().getMenu().findItem(R.id.nav_consult));
        f.v(R.id.workspaceInbox).performClick();execute();assertTrue(current() instanceof InboxFragment);
        nav().setSelectedItemId(R.id.nav_home);execute();f.v(R.id.workspaceSchedule).performClick();execute();assertTrue(current() instanceof ConsultationFragment);
    }
    @Test public void moreOpensEachActualSeryoPanelAndNotifications() {
        f.open(true);
        int[] buttons={R.id.seryoSettingsButton,R.id.seryoPrivacyButton,R.id.seryoHelpButton};String[] panels={"settings","privacy","help"};
        for(int i=0;i<buttons.length;i++){nav().setSelectedItemId(R.id.nav_more);execute();f.v(R.id.seryoButton).performClick();execute();assertTrue(current() instanceof SettingsGroupFragment);f.v(buttons[i]).performClick();execute();assertTrue(current() instanceof AssistantFragment);assertEquals(panels[i],current().requireArguments().getString("panel"));}
        nav().setSelectedItemId(R.id.nav_more);execute();f.v(R.id.accountButton).performClick();execute();f.v(R.id.notificationSettingsButton).performClick();execute();assertTrue(current() instanceof NotificationsFragment);
    }
    @Test public void assignedWorkspaceOpensEachOfficeManagementEditor(){
        openStaff("DEP-CCS","secretary");int[] ids={R.id.workspaceOfficeManage,R.id.workspaceAnnouncementsManage,R.id.workspaceServicesManage};String[] kinds={"OFFICE","ANNOUNCEMENT","SERVICE"};
        for(int i=0;i<ids.length;i++){nav().setSelectedItemId(R.id.nav_home);execute();f.v(ids[i]).performClick();execute();assertTrue(current() instanceof OfficeManagementFragment);assertEquals(kinds[i],current().requireArguments().getString("kind"));}
    }
    @Test public void captureWorkspaceInBothThemesAndLargeText() throws Exception {
        for(boolean dark:new boolean[]{false,true}){
            RuntimeEnvironment.setQualifiers("w390dp-h844dp-port-"+(dark?"night":"notnight")+"-mdpi");openStaff("DEP-CCS","secretary");assertTrue(current() instanceof StaffHomeFragment);f.capture("workspace-"+(dark?"dark":"light")+".png",390,844);f.reset();
            RuntimeEnvironment.setQualifiers("w320dp-h480dp-port-"+(dark?"night":"notnight")+"-mdpi");RuntimeEnvironment.setFontScale(2f);openStaff("DEP-CCS","secretary");f.measure(320,480);
            View target=f.v(R.id.workspaceServicesManage);android.widget.ScrollView scroll=(android.widget.ScrollView)target.getParent().getParent();scroll.scrollTo(0,scroll.getChildAt(0).getHeight());ThreadInteractionTest.idle();f.capture("workspace-large-"+(dark?"dark":"light")+"-end.png",320,480);
            android.graphics.Rect rect=new android.graphics.Rect();assertTrue(target.getGlobalVisibleRect(rect));assertEquals(target.getHeight(),rect.height());f.reset();RuntimeEnvironment.setFontScale(1f);
        }
    }
    void openStaff(String office,String capability){
        f.open(true);SessionProfile p=new SessionProfile(InquiryViewModelTest.ACCOUNT,"fixture@demo.goserve.test","Office fixture",Role.STAFF,office,capability,true,1,true,"v","v","home",null);
        ReflectionHelpers.setField(((GoServeApplication)f.activity.getApplication()).auth(),"profile",p);ReflectionHelpers.setField(f.activity,"profile",p);
        f.activity.configureRoleNavigation(Role.STAFF);nav().setSelectedItemId(R.id.nav_home);execute();
    }
    CapsuleNavigationView nav(){return f.activity.findViewById(R.id.bottomNavigation);} Fragment current(){return f.activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer);}
    void execute(){f.activity.getSupportFragmentManager().executePendingTransactions();ThreadInteractionTest.idle();}
}
