package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ScrollView;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.SavedStateHandle;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.AccountIntegrationTest;
import ph.edu.tip.goserve.domain.InquiryData;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,qualifiers="w390dp-h844dp-port-notnight-mdpi",shadows={AccountIntegrationTest.Store.class,AccountIntegrationTest.Server.class},instrumentedPackages={"ph.edu.tip.goserve.data"})
@LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class ThreadInteractionTest {
    ActivityController<MainActivity> controller; MainActivity activity; ThreadFragment fragment;
    InquiryViewModelTest.Fake actions; InquiryViewModel model;
    final InquiryViewModelTest.Queue work=new InquiryViewModelTest.Queue(),ui=new InquiryViewModelTest.Queue();
    @Before public void setup() throws Exception {
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class,"_instance",null); new AccountIntegrationTest().setup();
        ReflectionHelpers.setStaticField(AccountIntegrationTest.Server.class,"profileId",InquiryViewModelTest.ACCOUNT);
        ReflectionHelpers.setStaticField(AccountIntegrationTest.Server.class,"email","fixture@demo.goserve.test");
        ReflectionHelpers.setStaticField(AccountIntegrationTest.Server.class,"recoveryAmr",false);
        assertTrue(((GoServeApplication)RuntimeEnvironment.getApplication()).auth().signInPassword("fixture@demo.goserve.test","fixture-password").ready());
        actions=new InquiryViewModelTest.Fake();
    }
    @After public void close() { if(controller!=null)controller.close(); RuntimeEnvironment.setFontScale(1f); }
    @Test public void canonicalThreadAndExactMessageAreBoundToTheOffice() {
        open(true); assertEquals("CCS Inquiry",text(R.id.inquiryThreadTitle)); assertTrue(text(R.id.inquiryThreadIdentity).contains(InquiryViewModelTest.THREAD));
        assertEquals("Canonical 1",text(R.id.inquiryMessageBody)); assertEquals("Send reply",text(R.id.inquirySend));
    }
    @Test public void unknownSendKeepsExactBodyKeyAndExplicitRetryThroughRecreation() {
        open(true); ((EditText)v(R.id.inquiryMessageInput)).setText("Keep exact punctuation?!"); actions.failSend=true;
        v(R.id.inquirySend).performClick(); flush(); assertEquals(1,actions.sent.size()); assertEquals("Keep exact punctuation?!",text(R.id.inquiryMessageInput));
        assertTrue(fragment.mutationPending()); assertFalse(v(R.id.inquiryMessageInput).isEnabled());
        String key=actions.sent.get(0).operationKey;
        activity.getSupportFragmentManager().beginTransaction().detach(fragment).commitNow(); activity.getSupportFragmentManager().beginTransaction().attach(fragment).commitNow(); idle();
        assertEquals("Keep exact punctuation?!",text(R.id.inquiryMessageInput)); v(R.id.inquirySend).performClick(); flush();
        assertEquals(key,actions.sent.get(1).operationKey);
    }
    @Test public void newInquiryNeedsCanonicalOfficeAndExplicitSend() {
        open(false); ((EditText)v(R.id.inquiryMessageInput)).setText("Actual question"); assertEquals(0,actions.sent.size());
        v(R.id.inquirySend).performClick(); flush(); assertEquals(0,actions.sent.size());
        model.selectOffice("DEP-CCS"); v(R.id.inquirySend).performClick(); flush();
        assertEquals(1,actions.sent.size()); assertEquals("DEP-CCS",actions.sent.get(0).officeId);
    }
    @Test public void unseenLinesAndJumpingToTheEndDoNotAcknowledgeALongMessage() {
        actions.messages=new InquiryData.MessagePage(InquiryViewModelTest.THREAD,List.of(new InquiryData.Message("m",InquiryViewModelTest.THREAD,InquiryViewModelTest.ACCOUNT,"Long message\n".repeat(140),InquiryViewModelTest.TIME,1)),null);
        open(true); measure(390,844); flush(); assertTrue(actions.reads.isEmpty());
        ((ScrollView)v(R.id.inquiryThreadScroll)).fullScroll(View.FOCUS_DOWN); idle(); flush(); assertTrue(actions.reads.isEmpty());
    }
    @Test public void pausingBeforeQueuedReadEvidenceNeverWritesReadState() {
        open(true); actions.reads.clear();
        actions.messages=new InquiryData.MessagePage(InquiryViewModelTest.THREAD,List.of(InquiryViewModelTest.message(1),InquiryViewModelTest.message(2)),null);
        model.refresh(); work.run(); ui.run(); measure(390,844); assertTrue(work.size()>0);
        controller.pause(); flush(); assertTrue(actions.reads.isEmpty());
    }
    @Test public void fullyVisibleShortMessagesActuallyAdvanceTheReadBoundary() {
        open(true); measure(390,844); flush(); assertFalse(actions.reads.isEmpty()); assertEquals(2L,model.state().getValue().readThrough);
    }
    @Test public void anUnknownSendBlocksBottomNavigationUntilReconciledOrLocallyDiscarded() {
        open(true); ((EditText)v(R.id.inquiryMessageInput)).setText("Question"); actions.failSend=true; v(R.id.inquirySend).performClick();flush();
        CapsuleNavigationView nav=activity.findViewById(R.id.bottomNavigation);int selected=nav.getSelectedItemId();nav.setSelectedItemId(R.id.nav_more);activity.getSupportFragmentManager().executePendingTransactions();
        assertSame(fragment,activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer));assertEquals(selected,nav.getSelectedItemId());
        model.discardLocalDraft();nav.setSelectedItemId(R.id.nav_more);activity.getSupportFragmentManager().executePendingTransactions();assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer) instanceof MoreFragment);
    }
    @Test public void liveInboxConsultationAndHomeNotificationsRoutesReachTheirRealScreens() {
        open(true); CapsuleNavigationView nav=activity.findViewById(R.id.bottomNavigation);
        nav.setSelectedItemId(R.id.nav_inbox);activity.getSupportFragmentManager().executePendingTransactions();assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer) instanceof InboxFragment);
        nav.setSelectedItemId(R.id.nav_consult);activity.getSupportFragmentManager().executePendingTransactions();assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer) instanceof ConsultationFragment);
        nav.setSelectedItemId(R.id.nav_home);activity.getSupportFragmentManager().executePendingTransactions();v(R.id.notificationsButton).performClick();activity.getSupportFragmentManager().executePendingTransactions();assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer) instanceof NotificationsFragment);
    }
    @Test public void replacingAccountErasesPrivateRowsAndDraft() {
        open(true); ((EditText)v(R.id.inquiryMessageInput)).setText("Private"); actions.current=false;
        ((androidx.lifecycle.MutableLiveData<Long>)((GoServeApplication)activity.getApplication()).accountInvalidations()).setValue(101L); idle();
        assertEquals("",text(R.id.inquiryMessageInput)); assertEquals(0,((android.view.ViewGroup)v(R.id.inquiryMessageRows)).getChildCount()); assertFalse(v(R.id.inquirySend).isEnabled());
    }
    @Test public void captureConversationBothThemesAndLargeText() throws Exception {
        for(boolean dark:new boolean[]{false,true}) {
            RuntimeEnvironment.setQualifiers("w390dp-h844dp-port-"+(dark?"night":"notnight")+"-mdpi"); open(true); assertEquals("Canonical 1",text(R.id.inquiryMessageBody)); capture("thread-"+(dark?"dark":"light")+".png",390,844); reset();
            RuntimeEnvironment.setQualifiers("w320dp-h480dp-port-"+(dark?"night":"notnight")+"-mdpi"); RuntimeEnvironment.setFontScale(2f); open(true); measure(320,480);
            ScrollView scroll=(ScrollView)v(R.id.inquiryThreadScroll);scroll.scrollTo(0,scroll.getChildAt(0).getHeight());idle();
            capture("thread-large-"+(dark?"dark":"light")+"-end.png",320,480);
            android.graphics.Rect area=new android.graphics.Rect();assertTrue(v(R.id.inquirySend).getGlobalVisibleRect(area));assertEquals(v(R.id.inquirySend).getHeight(),area.height());assertTrue(v(R.id.inquirySend).isEnabled()); reset(); RuntimeEnvironment.setFontScale(1f);
        }
    }
    void open(boolean existing) {
        model=new InquiryViewModel(RuntimeEnvironment.getApplication(),actions,work,ui,new SavedStateHandle());
        controller=Robolectric.buildActivity(MainActivity.class).setup().visible(); activity=controller.get();
        ViewModelProvider.Factory factory=new ViewModelProvider.Factory(){@Override public <T extends ViewModel>T create(Class<T> type){return type.cast(model);}};
        fragment=new ThreadFragment(factory); Bundle args=new Bundle(); if(existing)args.putString("thread_id",InquiryViewModelTest.THREAD); fragment.setArguments(args);
        activity.getSupportFragmentManager().beginTransaction().replace(R.id.mainContainer,fragment).commitNow(); flush();
    }
    void flush(){for(int n=0;n<30;n++){idle();if(work.size()==0 && ui.size()==0)break;while(work.size()>0)work.run();while(ui.size()>0)ui.run();}idle();}
    static void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
    View v(int id){return activity.findViewById(id);} String text(int id){return ((TextView)v(id)).getText().toString();}
    void measure(int width,int height){View root=activity.findViewById(android.R.id.content);root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);idle();}
    void capture(String name,int width,int height)throws Exception{measure(width,height);android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);activity.findViewById(android.R.id.content).draw(new android.graphics.Canvas(b));java.io.File dir=new java.io.File("build/outputs/screenshots");dir.mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name))){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    void reset(){controller.close();controller=null;activity=null;work.jobs.clear();ui.jobs.clear();}
}
