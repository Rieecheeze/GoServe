package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import java.util.List;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.AccountIntegrationTest;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,qualifiers="w390dp-h844dp-port-notnight-mdpi",shadows={AccountIntegrationTest.Store.class,AccountIntegrationTest.Server.class},instrumentedPackages={"ph.edu.tip.goserve.data"})
@LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class InboxNotificationsInteractionTest {
    ActivityController<MainActivity> controller;MainActivity activity;
    InquiryViewModelTest.Fake inquiry;NotificationsViewModelTest.Fake notices;ViewModel vm;
    final InquiryViewModelTest.Queue work=new InquiryViewModelTest.Queue(),ui=new InquiryViewModelTest.Queue();
    int width=390,height=844;
    @Before public void setup()throws Exception {
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class,"_instance",null);new AccountIntegrationTest().setup();
        ReflectionHelpers.setStaticField(AccountIntegrationTest.Server.class,"profileId",InquiryViewModelTest.ACCOUNT);ReflectionHelpers.setStaticField(AccountIntegrationTest.Server.class,"email","fixture@demo.goserve.test");ReflectionHelpers.setStaticField(AccountIntegrationTest.Server.class,"recoveryAmr",false);
        assertTrue(((GoServeApplication)RuntimeEnvironment.getApplication()).auth().signInPassword("fixture@demo.goserve.test","fixture-password").ready());
    }
    @After public void close(){if(controller!=null)controller.close();RuntimeEnvironment.setFontScale(1f);}
    @Test public void inboxShowsCanonicalOfficeAndIndependentUnreadTotalsWithoutReadingMessages() {
        openInbox();assertEquals(1,((LinearLayout)v(R.id.inquiryRows)).getChildCount());assertTrue(text(R.id.inquiryCounts).contains("80 unread messages"));
        assertEquals("CCS · Secretary",text(R.id.inquiryRowTitle));assertTrue(text(R.id.inquiryRowDetail).contains("2 unread"));assertTrue(inquiry.reads.isEmpty());
    }
    @Test public void inboxRowOpensItsCanonicalThreadAndNewActionDoesNotSend() {
        openInbox();((View)v(R.id.inquiryRowTitle).getParent().getParent()).performClick();idle();
        assertEquals("ThreadFragment",fragment().getClass().getSimpleName());assertEquals(InquiryViewModelTest.THREAD,fragment().getArguments().getString("thread_id"));assertTrue(inquiry.sent.isEmpty());
    }
    @Test public void inboxFailureKeepsLastCanonicalRowsAndDoesNotInventZeroCounts() {
        openInbox();inquiry.failLoad=true;v(R.id.inquiryRefresh).performClick();flush();
        assertEquals(1,((LinearLayout)v(R.id.inquiryRows)).getChildCount());assertTrue(text(R.id.inquiryCounts).contains("80"));assertEquals(View.VISIBLE,v(R.id.inquiryRetry).getVisibility());
    }
    @Test public void replacedAccountRemovesInboxRowsAndBlocksNewInquiry() {
        openInbox();inquiry.current=false;invalidate();assertEquals(0,((LinearLayout)v(R.id.inquiryRows)).getChildCount());assertEquals("",text(R.id.inquiryCounts));assertFalse(v(R.id.inquiryStart).isEnabled());
    }
    @Test public void notificationLoadAndOpeningAreSeparateFromExplicitAcknowledgement() {
        openNotifications();assertEquals(1,((LinearLayout)v(R.id.notificationRows)).getChildCount());assertTrue(text(R.id.notificationCounts).contains("900"));assertTrue(notices.acks.isEmpty());
        assertEquals("Request update",text(R.id.notificationRowTitle));assertTrue(text(R.id.notificationRowTarget).contains("request1"));v(R.id.notificationRowOpen).performClick();idle();
        assertEquals("RequestDetailFragment",fragment().getClass().getSimpleName());assertEquals("request1",fragment().getArguments().getString("request_id"));assertTrue(notices.acks.isEmpty());
    }
    @Test public void markReadOnlyAcknowledgesTheExplicitIdAndUsesCanonicalReload() {
        openNotifications();v(R.id.notificationRowAck).performClick();assertFalse(v(R.id.notificationRowOpen).isEnabled());flush();
        assertEquals(List.of("notice1"),notices.acks);assertTrue(text(R.id.notificationCounts).contains("899"));assertTrue(text(R.id.notificationRowTime).startsWith("Read"));assertEquals(View.GONE,v(R.id.notificationRowAck).getVisibility());
    }
    @Test public void failedMarkReadKeepsUnreadAndLetsUserRetryWithoutClaimingSuccess() {
        openNotifications();notices.failAck=true;v(R.id.notificationRowAck).performClick();flush();assertTrue(text(R.id.notificationRowTime).startsWith("Unread"));assertEquals(View.VISIBLE,v(R.id.notificationError).getVisibility());assertTrue(v(R.id.notificationRowAck).isEnabled());
        notices.failAck=false;v(R.id.notificationRowAck).performClick();flush();assertEquals(List.of("notice1","notice1"),notices.acks);
    }
    @Test public void notificationPageReplacementDropsOldRowsAndAccountLossClearsCurrentPage() {
        openNotifications();v(R.id.notificationMore).performClick();flush();assertEquals(1,((LinearLayout)v(R.id.notificationRows)).getChildCount());assertTrue(text(R.id.notificationRowTarget).contains("request2"));
        notices.current=false;invalidate();assertEquals(0,((LinearLayout)v(R.id.notificationRows)).getChildCount());assertEquals("",text(R.id.notificationCounts));
    }
    @Test public void viewRecreationOnlyReloadsCanonicalStateAndNeverAcknowledges() {
        openNotifications();Fragment f=fragment();int loads=notices.loads;activity.getSupportFragmentManager().beginTransaction().detach(f).commitNow();activity.getSupportFragmentManager().beginTransaction().attach(f).commitNow();flush();
        assertEquals(loads+1,notices.loads);assertTrue(notices.acks.isEmpty());assertTrue(text(R.id.notificationCounts).contains("900"));
    }
    @Test public void captureInboxAndNotificationsInBothThemesAndLargeText()throws Exception {
        for(boolean large:new boolean[]{false,true})for(boolean dark:new boolean[]{false,true}) {
            width=large?320:390;height=large?480:844;RuntimeEnvironment.setFontScale(large?2f:1f);RuntimeEnvironment.setQualifiers("w"+width+"dp-h"+height+"dp-port-"+(dark?"night":"notnight")+"-mdpi");
            openInbox();assertEquals(1,((LinearLayout)v(R.id.inquiryRows)).getChildCount());capture("inbox-"+(large?"large-":"")+(dark?"dark":"light")+".png");reset();
            openNotifications();assertTrue(text(R.id.notificationCounts).contains("900"));capture("notifications-"+(large?"large-":"")+(dark?"dark":"light")+"-top.png");
            if(large){((android.widget.ScrollView)v(R.id.notificationScroll)).fullScroll(View.FOCUS_DOWN);idle();capture("notifications-large-"+(dark?"dark":"light")+"-end.png");assertTrue(v(R.id.notificationRowAck).isEnabled());}reset();
        }
    }
    void launch(){GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication();controller=Robolectric.buildActivity(MainActivity.class,new Intent(app,MainActivity.class)).setup().visible();activity=controller.get();idle();}
    void openInbox(){launch();inquiry=new InquiryViewModelTest.Fake();vm=new InquiryViewModel((GoServeApplication)activity.getApplication(),inquiry,work,ui,new SavedStateHandle());activity.navigate(new InboxFragment(factory()),false);idle();flush();}
    void openNotifications(){launch();notices=new NotificationsViewModelTest.Fake();vm=new NotificationsViewModel((GoServeApplication)activity.getApplication(),notices,work,ui);activity.navigate(new NotificationsFragment(factory()),false);idle();flush();}
    ViewModelProvider.Factory factory(){return new ViewModelProvider.Factory(){@Override public <T extends ViewModel>T create(Class<T> type){return type.cast(vm);}};}
    Fragment fragment(){return activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer);}
    View v(int id){View v=activity.findViewById(id);assertNotNull("Missing inbox/notification control "+id,v);return v;}
    String text(int id){return ((TextView)v(id)).getText().toString();}
    void invalidate(){((androidx.lifecycle.MutableLiveData<Long>)((GoServeApplication)activity.getApplication()).accountInvalidations()).setValue(889L);idle();}
    void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();activity.getSupportFragmentManager().executePendingTransactions();}
    void flush(){int left=25;do{if(!work.jobs.isEmpty())work.run();if(!ui.jobs.isEmpty())ui.run();idle();}while((!work.jobs.isEmpty()||!ui.jobs.isEmpty())&&--left>0);assertTrue(left>0);}
    void reset(){controller.close();controller=null;work.jobs.clear();ui.jobs.clear();}
    void capture(String name)throws Exception{View root=fragment().requireView();root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);idle();android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);java.io.File dir=new java.io.File("build/outputs/screenshots");assertTrue(dir.isDirectory()||dir.mkdirs());try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name))){root.draw(new android.graphics.Canvas(image));assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}finally{image.recycle();}}
}
