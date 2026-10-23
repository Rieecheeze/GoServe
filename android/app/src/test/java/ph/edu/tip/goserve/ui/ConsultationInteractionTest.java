package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
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
import ph.edu.tip.goserve.data.ConsultationRepository;
import ph.edu.tip.goserve.domain.ConsultationData.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,qualifiers="w390dp-h844dp-port-notnight-mdpi",shadows={AccountIntegrationTest.Store.class,AccountIntegrationTest.Server.class},instrumentedPackages={"ph.edu.tip.goserve.data"})
@LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class ConsultationInteractionTest {
    ActivityController<MainActivity> controller; MainActivity activity; Actions actions; ConsultationViewModel model;
    final ConsultationViewModelTest.Q work=new ConsultationViewModelTest.Q(),ui=new ConsultationViewModelTest.Q();
    int width=390,height=844;
    @Before public void setup() throws Exception {
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class,"_instance",null);new AccountIntegrationTest().setup();
        ReflectionHelpers.setStaticField(AccountIntegrationTest.Server.class,"profileId",ConsultationViewModelTest.id(50));ReflectionHelpers.setStaticField(AccountIntegrationTest.Server.class,"email","fixture@demo.goserve.test");ReflectionHelpers.setStaticField(AccountIntegrationTest.Server.class,"recoveryAmr",false);
        assertTrue(((GoServeApplication)RuntimeEnvironment.getApplication()).auth().signInPassword("fixture@demo.goserve.test","fixture-password").ready());
    }
    @After public void close(){if(controller!=null)controller.close();RuntimeEnvironment.setFontScale(1f);}
    @Test public void studentSlotTapOpensReviewAndOnlyExplicitConfirmationBooks(){
        open(true,true);tapFirstRow();assertEquals("Review booking",text(R.id.consultHeading));assertEquals(0,actions.writes);assertTrue(text(R.id.consultSummary).contains("Guidance"));
        v(R.id.consultPrimary).performClick();assertFalse(v(R.id.consultPrimary).isEnabled());flush();assertEquals(1,actions.writes);assertEquals(ConsultationViewModel.Screen.DETAIL,model.state().getValue().screen);assertTrue(text(R.id.consultSummary).contains(ConsultationViewModelTest.id(9)));
    }
    @Test public void unmatchedStudentCannotChooseDepartmentAndDoesNotSeeFaculty(){
        open(true,false);assertEquals(View.GONE,v(R.id.consultDepartment).getVisibility());assertEquals("Guidance consultation",text(R.id.consultGuidance));assertFalse(text(R.id.consultIntro).contains("Faculty"));
    }
    @Test public void unknownBookingDisablesChangingTheIntentAndRetryKeepsExactKey(){
        open(true,true);tapFirstRow();actions.error=new java.io.IOException("lost response");v(R.id.consultPrimary).performClick();flush();Booking attempted=actions.booking;
        assertTrue(model.mutationPending());assertFalse(v(R.id.consultBack).isEnabled());assertFalse(v(R.id.consultPrimary).isEnabled());assertTrue(text(R.id.consultError).contains("could not be verified"));
        actions.error=null;v(R.id.consultRetry).performClick();flush();assertSame(attempted,actions.booking);assertEquals(2,actions.writes);
    }
    @Test public void cancelledAppointmentRequiresItsOwnReviewAndExplicitConfirmation(){
        open(true,true);model.open(ConsultationViewModelTest.id(9));flush();v(R.id.consultCancel).performClick();assertEquals("Review cancellation",text(R.id.consultHeading));assertEquals(0,actions.writes);
        ((EditText)v(R.id.consultReason)).setText("Schedule conflict");v(R.id.consultPrimary).performClick();flush();assertNull(actions.change.target);assertEquals("Schedule conflict",actions.change.reason);assertTrue(text(R.id.consultSummary).contains("Cancelled"));
    }
    @Test public void fullReplacementPreservesTheOriginalAppointmentAndTypedReason(){
        open(true,true);model.open(ConsultationViewModelTest.id(9));flush();v(R.id.consultMove).performClick();flush();((EditText)v(R.id.consultReason)).setText("My reason");
        model.select(model.state().getValue().slots.get(1));actions.error=new ConsultationRepository.WorkflowException("CAPACITY_FULL");v(R.id.consultPrimary).performClick();flush();
        assertEquals(ConsultationViewModelTest.id(1),model.state().getValue().appointment.slotId);assertEquals("My reason",text(R.id.consultReason));assertTrue(text(R.id.consultError).contains("full"));assertEquals(1,actions.writes);
    }
    @Test public void staffScheduleCollectsActualFieldsAndRequiresReviewBeforeSaving(){
        open(false,false);assertEquals("Guidance availability",text(R.id.consultHeading));v(R.id.consultPrimary).performClick();assertEquals("",text(R.id.consultCapacity));assertEquals("Choose date and time",text(R.id.consultStart));
        model.form("2026-11-01T03:00:00Z","2026-11-01T04:30:00Z","",false);((EditText)v(R.id.consultCapacity)).setText("7");v(R.id.consultPrimary).performClick();assertEquals("Review availability",text(R.id.consultHeading));assertEquals(0,actions.writes);assertTrue(text(R.id.consultSummary).contains("Capacity: 7"));
        v(R.id.consultPrimary).performClick();flush();assertEquals(1,actions.writes);
    }
    @Test public void viewRecreationPreservesUnsentReasonWithoutAnAutomaticChange(){
        open(true,true);model.open(ConsultationViewModelTest.id(9));flush();v(R.id.consultCancel).performClick();((EditText)v(R.id.consultReason)).setText("Unsent private reason");
        ConsultationFragment fragment=fragment();activity.getSupportFragmentManager().beginTransaction().detach(fragment).commitNow();activity.getSupportFragmentManager().beginTransaction().attach(fragment).commitNow();flush();assertEquals("Unsent private reason",text(R.id.consultReason));assertEquals(0,actions.writes);
    }
    @Test public void accountReplacementBeforeQueuedReplyClearsPrivateRecordsAndForm(){
        open(true,true);model.open(ConsultationViewModelTest.id(9));work.run();actions.current=false;ui.run();idle();assertTrue(model.state().getValue().blocked);assertEquals(View.GONE,v(R.id.consultSummary).getVisibility());assertEquals(View.GONE,v(R.id.consultPrimary).getVisibility());assertTrue(model.state().getValue().events.isEmpty());
    }
    @Test public void captureStudentReviewAndStaffFormInBothThemes() throws Exception {
        for(boolean dark:new boolean[]{false,true}){
            RuntimeEnvironment.setQualifiers("w390dp-h844dp-port-"+(dark?"night":"notnight")+"-mdpi");open(true,true);tapFirstRow();capture("consult-"+(dark?"dark":"light")+"-review.png");reset();
            open(false,false);v(R.id.consultPrimary).performClick();assertEquals(ConsultationViewModel.Screen.FORM,model.state().getValue().screen);capture("consult-"+(dark?"dark":"light")+"-form.png");reset();
        }
    }
    @Test public void captureLargeTextReviewKeepsConfirmationReachable() throws Exception {
        width=320;height=480;RuntimeEnvironment.setFontScale(2f);
        for(boolean dark:new boolean[]{false,true}){
            RuntimeEnvironment.setQualifiers("w320dp-h480dp-port-"+(dark?"night":"notnight")+"-mdpi");open(true,true);tapFirstRow();capture("consult-large-"+(dark?"dark":"light")+"-review-top.png");
            RecyclerView list=(RecyclerView)v(R.id.consultList);list.scrollBy(0,10000);layout();capture("consult-large-"+(dark?"dark":"light")+"-review-end.png");assertTrue(v(R.id.consultPrimary).isEnabled());assertEquals(0,actions.writes);reset();
        }
    }
    void open(boolean student,boolean department){
        GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication();controller=Robolectric.buildActivity(MainActivity.class,new Intent(app,MainActivity.class)).setup().visible();activity=controller.get();idle();
        actions=new Actions();actions.page=new SlotPage(List.of(ConsultationViewModelTest.slot(1,1),ConsultationViewModelTest.slot(2,1)),null,null);model=new ConsultationViewModel(app,actions,work,ui,student,department,student?null:"OFF-GCC");
        activity.navigate(new ConsultationFragment(new ViewModelProvider.Factory(){@Override public <T extends ViewModel>T create(Class<T> type){return type.cast(model);}}),false);flush();assertEquals(ConsultationViewModel.Screen.SLOTS,model.state().getValue().screen);layout();
    }
    void tapFirstRow(){RecyclerView list=(RecyclerView)v(R.id.consultList);list.scrollToPosition(1);layout();RecyclerView.ViewHolder row=list.findViewHolderForAdapterPosition(1);assertNotNull(row);assertTrue(row.itemView.performClick());idle();assertEquals(ConsultationViewModel.Screen.REVIEW,model.state().getValue().screen);layout();}
    ConsultationFragment fragment(){return (ConsultationFragment)activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer);}
    View v(int id){View view=activity.findViewById(id);assertNotNull("Missing consultation control "+id,view);return view;}
    String text(int id){return ((TextView)v(id)).getText().toString();}
    void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();activity.getSupportFragmentManager().executePendingTransactions();}
    void flush(){int left=40;do{if(!work.q.isEmpty())work.run();if(!ui.q.isEmpty())ui.run();idle();}while((!work.q.isEmpty()||!ui.q.isEmpty())&&--left>0);assertTrue(left>0);layout();}
    void layout(){View root=fragment().requireView();root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);idle();}
    void reset(){controller.close();controller=null;work.q.clear();ui.q.clear();}
    void capture(String name)throws Exception{layout();android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);java.io.File dir=new java.io.File("build/outputs/screenshots");assertTrue(dir.isDirectory()||dir.mkdirs());try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name))){fragment().requireView().draw(new android.graphics.Canvas(image));assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}finally{image.recycle();}}
    static final class Actions extends ConsultationViewModelTest.A{
        @Override public Slot slot(String office,String id){return ConsultationViewModelTest.slot(id.equals(ConsultationViewModelTest.id(2))?2:1,1);}
    }
}
