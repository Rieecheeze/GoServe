package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.ImageView;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.AccountIntegrationTest;
import ph.edu.tip.goserve.data.RequestRepository;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,qualifiers="w390dp-h844dp-port-notnight-mdpi",shadows={AccountIntegrationTest.Store.class,AccountIntegrationTest.Server.class},instrumentedPackages={"ph.edu.tip.goserve.data"})
@LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class PickupInteractionTest {
    ActivityController<MainActivity> controller; MainActivity activity; PickupViewModelTest.A actions; PickupViewModel model; Scanner scanner;
    final Q work=new Q(),ui=new Q(); int width=390,height=844;
    @Before public void setup() throws Exception {
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class,"_instance",null); new AccountIntegrationTest().setup();
        ReflectionHelpers.setStaticField(AccountIntegrationTest.Server.class,"profileId",PickupViewModelTest.id(90)); ReflectionHelpers.setStaticField(AccountIntegrationTest.Server.class,"email","fixture@demo.goserve.test"); ReflectionHelpers.setStaticField(AccountIntegrationTest.Server.class,"recoveryAmr",false);
        assertTrue(((GoServeApplication)RuntimeEnvironment.getApplication()).auth().signInPassword("fixture@demo.goserve.test","fixture-password").ready());
    }
    @After public void close() { if(controller!=null)controller.close(); RuntimeEnvironment.setFontScale(1f); }
    @Test public void studentMustExplicitlyIssueTheActualRequestClaimBeforeSeeingQr() {
        open(false); assertTrue(text(R.id.pickupReference).contains("fixture-reference")); assertEquals(0,actions.claims); assertEquals(View.GONE,v(R.id.pickupQrCard).getVisibility());
        v(R.id.pickupPrimary).performClick(); flush(); assertEquals(1,actions.claims); assertEquals(PickupViewModelTest.CODE,text(R.id.pickupCode)); assertNotNull(((ImageView)v(R.id.pickupQrImage)).getDrawable()); assertEquals(0,actions.handovers);
    }
    @Test public void physicalReadyRequestDetailOpensPickupWithTheActualRequestId() {
        open(false);
        RequestDetailViewModel detail=new RequestDetailViewModel(RuntimeEnvironment.getApplication(),new RequestDetailViewModel.Actions(){
            public boolean allowed(){return true;} public void requireCurrent(){actions.requireCurrent();}
            public ph.edu.tip.goserve.domain.RequestData.Record request(String id){return actions.record(PickupViewModelTest.id(1),ph.edu.tip.goserve.domain.RequestStatus.READY_FOR_PICKUP_DOWNLOAD,2);}
            public ph.edu.tip.goserve.domain.PaymentData.Record payment(String id){return null;}
        },work,ui);
        RequestDetailFragment f=new RequestDetailFragment(new ViewModelProvider.Factory(){@Override public <T extends ViewModel>T create(Class<T> type){return type.cast(detail);}});
        android.os.Bundle args=new android.os.Bundle();args.putString("request_id",PickupViewModelTest.id(1));f.setArguments(args);
        activity.getSupportFragmentManager().beginTransaction().replace(R.id.mainContainer,f).commitNow();flush();
        assertEquals(View.VISIBLE,v(R.id.requestFileAction).getVisibility());assertEquals("View pickup code",text(R.id.requestFileAction));
        v(R.id.requestFileAction).performClick();activity.getSupportFragmentManager().executePendingTransactions();
        assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer) instanceof PickupFragment);
        assertEquals(PickupViewModelTest.id(1),activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer).requireArguments().getString("request_id"));
    }
    @Test public void missingPolicyBlocksTheIssueButtonWithoutAPlaceholderCode() {
        launch(false,false); flush(); assertFalse(v(R.id.pickupPrimary).isEnabled()); assertEquals("",text(R.id.pickupCode)); assertTrue(text(R.id.pickupMessage).contains("configuration"));
    }
    @Test public void scanResultOnlyFillsManualInputAndNeedsExplicitValidation() {
        open(true); v(R.id.pickupScan).performClick(); assertEquals(1,scanner.calls); assertFalse(v(R.id.pickupScan).isEnabled()); scanner.result.found(PickupViewModelTest.CODE); flush();
        assertEquals(PickupViewModelTest.CODE,text(R.id.pickupInput)); assertEquals(0,actions.validations); v(R.id.pickupPrimary).performClick(); flush(); assertEquals(1,actions.validations); assertEquals(0,actions.handovers); assertTrue(text(R.id.pickupBody).contains("fixture-reference"));
    }
    @Test public void cancelledOrUnavailableScannerKeepsManualEntryWorking() {
        open(true); ((EditText)v(R.id.pickupInput)).setText(PickupViewModelTest.CODE); v(R.id.pickupScan).performClick(); scanner.result.cancelled(); flush(); assertEquals(PickupViewModelTest.CODE,text(R.id.pickupInput)); assertEquals(0,actions.validations);
        v(R.id.pickupScan).performClick(); scanner.result.unavailable(); flush(); assertTrue(v(R.id.pickupInput).isEnabled()); assertTrue(text(R.id.pickupMessage).contains("manually"));
    }
    @Test public void handoverRequiresItsOwnExplicitButtonAfterReview() {
        open(true); review(); assertEquals(0,actions.handovers); v(R.id.pickupPrimary).performClick(); assertFalse(v(R.id.pickupPrimary).isEnabled()); flush(); assertEquals(1,actions.handovers); assertEquals("Handover confirmed",text(R.id.pickupHeading));
    }
    @Test public void consumedCodeDoesNotBecomeACompletedHandover() {
        open(true); review(); actions.handoverError=new RequestRepository.WorkflowException("CODE_CONSUMED"); v(R.id.pickupPrimary).performClick(); flush(); assertEquals(PickupViewModel.Mode.INPUT,model.state().getValue().mode); assertTrue(text(R.id.pickupMessage).contains("already been used"));
    }
    @Test public void unknownHandoverFreezesTheExactIntentAndAllowsOnlyItsRetry() {
        open(true); review(); actions.handoverError=new java.io.IOException("lost result"); v(R.id.pickupPrimary).performClick(); flush();
        Object intent=model.state().getValue().intent; assertTrue(text(R.id.pickupBody).contains("unconfirmed")); assertFalse(v(R.id.pickupBack).isEnabled()); assertTrue(fragment().mutationPending()); assertEquals(View.GONE,v(R.id.pickupEdit).getVisibility());
        actions.handoverError=null; v(R.id.pickupPrimary).performClick(); flush(); assertSame(intent,actions.sent); assertEquals(PickupViewModel.Mode.COMPLETE,model.state().getValue().mode);
    }
    @Test public void scannerViewRecreationCannotStartAnotherScanOrValidateAResult() {
        open(true); v(R.id.pickupScan).performClick(); PickupFragment f=fragment(); activity.getSupportFragmentManager().beginTransaction().detach(f).commitNow(); activity.getSupportFragmentManager().beginTransaction().attach(f).commitNow(); idle();
        assertEquals(1,scanner.calls); assertFalse(v(R.id.pickupScan).isEnabled()); scanner.result.found(PickupViewModelTest.CODE); flush(); assertEquals(0,actions.validations);
    }
    @Test public void accountReplacementRemovesCodeAndQrAndCannotValidateLateScan() {
        open(false); v(R.id.pickupPrimary).performClick(); flush(); actions.current=false;
        ((androidx.lifecycle.MutableLiveData<Long>)((GoServeApplication)activity.getApplication()).accountInvalidations()).setValue(502L); idle(); assertEquals("",text(R.id.pickupCode)); assertEquals(View.GONE,v(R.id.pickupQrCard).getVisibility()); assertFalse(v(R.id.pickupPrimary).isEnabled());
    }
    @Test public void captureStudentCodeAndStaffReviewInBothThemes() throws Exception {
        for(boolean dark:new boolean[]{false,true}) {
            RuntimeEnvironment.setQualifiers("w390dp-h844dp-port-"+(dark?"night":"notnight")+"-mdpi"); open(false); v(R.id.pickupPrimary).performClick(); flush(); capture("pickup-"+(dark?"dark":"light")+"-claim.png"); resetActivity();
            open(true); review(); capture("pickup-"+(dark?"dark":"light")+"-review.png"); actions.handoverError=new java.io.IOException("lost"); v(R.id.pickupPrimary).performClick(); flush(); capture("pickup-"+(dark?"dark":"light")+"-uncertain.png"); resetActivity();
        }
    }
    @Test public void captureLargeTextHandoverKeepsReviewEndAndConfirmReachable() throws Exception {
        width=320; height=480; RuntimeEnvironment.setFontScale(2f);
        for(boolean dark:new boolean[]{false,true}) {
            RuntimeEnvironment.setQualifiers("w320dp-h480dp-port-"+(dark?"night":"notnight")+"-mdpi"); open(true); review(); capture("pickup-large-"+(dark?"dark":"light")+"-top.png");
            ((androidx.core.widget.NestedScrollView)v(R.id.pickupScroll)).fullScroll(View.FOCUS_DOWN); idle(); capture("pickup-large-"+(dark?"dark":"light")+"-end.png"); assertTrue(v(R.id.pickupPrimary).isEnabled()); resetActivity();
        }
    }
    void launch(boolean staff,boolean policy) {
        GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication(); controller=Robolectric.buildActivity(MainActivity.class,new Intent(app,MainActivity.class)).setup().visible(); activity=controller.get(); idle();
        actions=new PickupViewModelTest.A(staff); actions.policy=policy; scanner=new Scanner(); model=new PickupViewModel(app,actions,work,ui);
        PickupFragment f=new PickupFragment(new ViewModelProvider.Factory(){@Override public <T extends ViewModel>T create(Class<T> type){return type.cast(model);}},scanner); android.os.Bundle args=new android.os.Bundle(); args.putString("request_id",PickupViewModelTest.id(1)); f.setArguments(args); activity.navigate(f,false); idle();
    }
    void open(boolean staff) { launch(staff,true); flush(); assertEquals(staff?PickupViewModel.Mode.INPUT:PickupViewModel.Mode.AVAILABLE,model.state().getValue().mode); }
    void review() { ((EditText)v(R.id.pickupInput)).setText(PickupViewModelTest.CODE); v(R.id.pickupPrimary).performClick(); flush(); assertEquals(PickupViewModel.Mode.REVIEW,model.state().getValue().mode); }
    PickupFragment fragment() { return (PickupFragment)activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer); }
    View v(int id) { View v=activity.findViewById(id); assertNotNull("Missing pickup control "+id,v); return v; }
    String text(int id) { return ((TextView)v(id)).getText().toString(); }
    void idle() { Shadows.shadowOf(Looper.getMainLooper()).idle(); activity.getSupportFragmentManager().executePendingTransactions(); }
    void flush() { int left=30; do { if(!work.q.isEmpty())work.q.remove().run(); if(!ui.q.isEmpty())ui.q.remove().run(); idle(); } while((!work.q.isEmpty()||!ui.q.isEmpty()) && --left>0); assertTrue(left>0); }
    void resetActivity() { controller.close(); controller=null; work.q.clear(); ui.q.clear(); }
    void capture(String name) throws Exception {
        View root=fragment().requireView(); root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY)); root.layout(0,0,width,height); idle();
        android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888); java.io.File dir=new java.io.File("build/outputs/screenshots"); assertTrue(dir.isDirectory()||dir.mkdirs());
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name))) { root.draw(new android.graphics.Canvas(image)); assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out)); } finally { image.recycle(); }
    }
    static final class Q implements Executor { final ArrayDeque<Runnable> q=new ArrayDeque<>(); public void execute(Runnable r) { q.add(r); } }
    static final class Scanner implements PickupScanner { int calls; Result result; public void scan(android.app.Activity activity,Result result) { ++calls; this.result=result; } }
}
