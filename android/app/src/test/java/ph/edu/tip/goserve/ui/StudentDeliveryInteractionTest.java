package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
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
import ph.edu.tip.goserve.domain.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,qualifiers="w390dp-h844dp-port-notnight-mdpi",shadows={AccountIntegrationTest.Store.class,AccountIntegrationTest.Server.class},instrumentedPackages={"ph.edu.tip.goserve.data"})
@LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class StudentDeliveryInteractionTest {
    private ActivityController<MainActivity> controller; private MainActivity activity;
    private StudentDeliveryViewModelTest.Fake actions=new StudentDeliveryViewModelTest.Fake();
    private final Queue work=new Queue(),ui=new Queue(); private int width=390,height=844;
    @Before public void setup() throws Exception {
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class,"_instance",null);
        new AccountIntegrationTest().setup(); ReflectionHelpers.setStaticField(AccountIntegrationTest.Server.class,"profileId",id(90));
        ReflectionHelpers.setStaticField(AccountIntegrationTest.Server.class,"email","fixture@demo.goserve.test"); ReflectionHelpers.setStaticField(AccountIntegrationTest.Server.class,"recoveryAmr",false);
        assertTrue(((GoServeApplication)RuntimeEnvironment.getApplication()).auth().signInPassword("fixture@demo.goserve.test","fixture-password").ready());
    }
    @After public void close() {
        if(controller!=null)controller.close(); RuntimeEnvironment.setFontScale(1f);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
    @Test public void studentOpensRealFileDetailsWithoutPreviewCopyOrAnInventedFilename() {
        open(); assertTrue(text(R.id.deliveryReference).contains("fixture-reference")); assertEquals("Configured service",text(R.id.deliveryService));
        assertTrue(text(R.id.deliveryMetadata).contains("PDF")); assertTrue(text(R.id.deliveryMetadata).contains("Version 1")); assertFalse(text(R.id.deliveryBody).toLowerCase().contains("demo")); assertEquals(0,actions.begins);
    }
    @Test public void failedPaymentReadDoesNotClaimTheRequestChangedOrShowVerifiedMetadata() {
        actions.paymentError=new java.io.IOException("private host"); open(); assertTrue(text(R.id.deliveryBody).contains("could not check"));
        assertEquals(View.GONE,view(R.id.deliveryFileCard).getVisibility()); assertFalse(view(R.id.deliveryPrimary).isEnabled()); assertEquals(0,actions.begins);
    }
    @Test public void aSuccessfulEmptyFileReadShowsTheActualMissingDocumentState() {
        actions.file=null; open(); assertTrue(text(R.id.deliveryBody).contains("No current verified PDF")); assertEquals(View.GONE,view(R.id.deliveryFileCard).getVisibility()); assertEquals(0,actions.downloads);
    }
    @Test public void staffCannotOpenTheStudentSaveOrReceiptFlow() {
        GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication();
        ReflectionHelpers.setField(app.auth(),"profile",new SessionProfile(id(90),"fixture@demo.goserve.test","Fixture staff",Role.STAFF,"OFF-REG",null,true,1,true,"fixture-terms","fixture-terms","home",null));
        launch(); assertEquals(View.GONE,activity.findViewById(R.id.requestFileAction).getVisibility()); assertEquals(0,actions.begins);
    }
    @Test public void pickerUsesCreateDocumentPdfAndCancelNeverAcknowledgesOrConfirms() {
        open(); view(R.id.deliveryPrimary).performClick(); flush(); assertEquals(1,actions.downloads); assertNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());
        view(R.id.deliveryPrimary).performClick(); idle(); org.robolectric.shadows.ShadowActivity.IntentForResult picker=Shadows.shadowOf(activity).getNextStartedActivityForResult(); assertNotNull(picker);
        assertEquals(Intent.ACTION_CREATE_DOCUMENT,picker.intent.getAction()); assertEquals("application/pdf",picker.intent.getType()); assertTrue(picker.intent.getStringExtra(Intent.EXTRA_TITLE).contains("fixture-reference"));
        Shadows.shadowOf(activity).receiveResult(picker.intent,android.app.Activity.RESULT_CANCELED,null); idle();
        assertEquals(StudentDeliveryViewModel.Mode.DESTINATION_READY,model().state().getValue().mode); assertEquals(0,actions.saves); assertEquals(0,actions.acks); assertEquals(0,actions.confirms);
        assertTrue(text(R.id.deliveryMessage).contains("canceled"));
    }
    @Test public void returningAContentUriSavesThenAcknowledgesButDoesNotConfirmReceipt() {
        open(); prepareAndPick(); org.robolectric.shadows.ShadowActivity.IntentForResult picker=Shadows.shadowOf(activity).getNextStartedActivityForResult();
        Shadows.shadowOf(activity).receiveResult(picker.intent,android.app.Activity.RESULT_OK,new Intent().setData(android.net.Uri.parse("content://fixture.documents/new"))); flush();
        assertEquals(1,actions.saves); assertEquals(1,actions.acks); assertEquals(0,actions.confirms); assertTrue(text(R.id.deliverySavedName).contains("actual-picked.pdf")); assertTrue(text(R.id.deliveryBody).contains("download confirmed"));
    }
    @Test public void recreatingTheDialogViewWhilePickerIsOpenDoesNotLaunchAnotherPicker() {
        open(); prepareAndPick(); org.robolectric.shadows.ShadowActivity.IntentForResult picker=Shadows.shadowOf(activity).getNextStartedActivityForResult(); assertNotNull(picker);
        StudentDeliveryDialog dialog=dialog(); androidx.fragment.app.FragmentManager manager=dialog.getParentFragmentManager();
        manager.beginTransaction().detach(dialog).commitNow(); manager.beginTransaction().attach(dialog).commitNow(); idle();
        assertSame(dialog,dialog()); assertEquals(StudentDeliveryViewModel.Mode.PICKER,model().state().getValue().mode); assertNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());
        Shadows.shadowOf(activity).receiveResult(picker.intent,android.app.Activity.RESULT_CANCELED,null); idle(); assertEquals(StudentDeliveryViewModel.Mode.DESTINATION_READY,model().state().getValue().mode); assertEquals(0,actions.acks);
    }
    @Test public void lostAcknowledgementExplainsTheLocalSaveAndRetriesWithoutAnotherPicker() {
        open(); actions.ackError=new java.io.IOException("private host"); save(); assertEquals(StudentDeliveryViewModel.Mode.LOCAL_SAVED,model().state().getValue().mode);
        assertTrue(text(R.id.deliveryBody).contains("has not confirmed")); assertTrue(text(R.id.deliverySavedName).contains("actual-picked.pdf")); assertEquals(View.GONE,view(R.id.deliveryReview).getVisibility());
        actions.ackError=null; view(R.id.deliveryPrimary).performClick(); flush(); assertEquals(1,actions.saves); assertEquals(1,actions.downloads); assertEquals(2,actions.acks); assertNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());
    }
    @Test public void receiptNeedsItsOwnReviewWithTheActualReferenceAndExplicitConfirmation() {
        open(); view(R.id.deliveryReview).performClick(); idle(); assertTrue(text(R.id.deliveryBody).contains("fixture-reference")); assertEquals(0,actions.confirms);
        view(R.id.deliveryPrimary).performClick(); assertFalse(view(R.id.deliveryPrimary).isEnabled()); flush(); assertEquals(1,actions.confirms); assertEquals(StudentDeliveryViewModel.Mode.COMPLETE,model().state().getValue().mode); assertEquals("Receipt Confirmed",text(R.id.deliveryHeading));
    }
    @Test public void unknownReceiptFreezesTheSameConfirmationAndDoesNotShowCompleted() {
        open(); actions.confirmError=new RequestRepository.WorkflowException("VALIDATION_FAILED"); view(R.id.deliveryReview).performClick(); view(R.id.deliveryPrimary).performClick(); flush();
        assertTrue(text(R.id.deliveryBody).contains("unconfirmed")); assertFalse(view(R.id.deliveryBack).isEnabled()); assertEquals(View.GONE,view(R.id.deliveryEdit).getVisibility()); assertTrue(text(R.id.deliveryPrimary).contains("Retry")); assertEquals(StudentDeliveryViewModel.Mode.RECEIPT_REVIEW,model().state().getValue().mode);
    }
    @Test public void saveFailureKeepsReceiptAndDownloadUnconfirmed() {
        open(); actions.saveError=new java.io.IOException("close failed"); save(); assertEquals(0,actions.acks); assertEquals(0,actions.confirms); assertEquals(View.GONE,view(R.id.deliverySavedName).getVisibility()); assertTrue(text(R.id.deliveryMessage).contains("could not be saved"));
    }
    @Test public void accountInvalidationClearsThePrivateFileAndVisibleMetadata() {
        open(); view(R.id.deliveryPrimary).performClick(); flush(); actions.current=false;
        ((androidx.lifecycle.MutableLiveData<Long>)((GoServeApplication)activity.getApplication()).accountInvalidations()).setValue(501L); idle();
        assertEquals("",text(R.id.deliveryReference)); assertEquals(View.GONE,view(R.id.deliveryFileCard).getVisibility()); assertFalse(view(R.id.deliveryPrimary).isEnabled()); assertTrue(actions.document.closed);
    }
    @Test public void captureStudentDeliveryReviewAndUnconfirmedSaveInBothThemes() throws Exception {
        for(boolean dark:new boolean[]{false,true}) {
            RuntimeEnvironment.setQualifiers("w390dp-h844dp-port-"+(dark?"night":"notnight")+"-mdpi"); actions=new StudentDeliveryViewModelTest.Fake();
            open(); capture("delivery-"+(dark?"dark":"light")+"-available.png");
            view(R.id.deliveryReview).performClick(); idle(); capture("delivery-"+(dark?"dark":"light")+"-review.png"); view(R.id.deliveryEdit).performClick();
            actions.ackError=new java.io.IOException("lost result"); save(); capture("delivery-"+(dark?"dark":"light")+"-local-saved.png"); controller.close(); controller=null; work.tasks.clear(); ui.tasks.clear();
        }
    }
    @Test public void captureLargeTextReceiptKeepsLastCopyAndConfirmationReachable() throws Exception {
        width=320; height=480; RuntimeEnvironment.setFontScale(2f);
        for(boolean dark:new boolean[]{false,true}) {
            RuntimeEnvironment.setQualifiers("w320dp-h480dp-port-"+(dark?"night":"notnight")+"-mdpi"); actions=new StudentDeliveryViewModelTest.Fake();
            open(); view(R.id.deliveryReview).performClick(); idle(); capture("delivery-large-"+(dark?"dark":"light")+"-top.png");
            ((androidx.core.widget.NestedScrollView)view(R.id.deliveryScroll)).fullScroll(View.FOCUS_DOWN); idle(); capture("delivery-large-"+(dark?"dark":"light")+"-end.png");
            assertTrue(view(R.id.deliveryPrimary).isEnabled()); controller.close(); controller=null; work.tasks.clear(); ui.tasks.clear();
        }
    }
    private void launch() {
        GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication(); controller=Robolectric.buildActivity(MainActivity.class,new Intent(app,MainActivity.class)).setup().visible(); activity=controller.get(); idle();
        RequestDetailViewModel detail=new RequestDetailViewModel(app,new RequestDetailViewModel.Actions() {
            public boolean allowed() { return true; } public void requireCurrent() { actions.requireCurrent(); }
            public RequestData.Record request(String id) { return actions.record(RequestStatus.READY_FOR_PICKUP_DOWNLOAD,2); }
            public PaymentData.Record payment(String id) { return null; }
        },work,ui);
        StudentDeliveryViewModel delivery=new StudentDeliveryViewModel(app,actions,work,ui);
        RequestDetailFragment fragment=new RequestDetailFragment(factory(detail),null,factory(delivery)); android.os.Bundle args=new android.os.Bundle(); args.putString("request_id",id(70)); fragment.setArguments(args);
        activity.navigate(fragment,false); flush();
    }
    private void open() { launch(); View button=activity.findViewById(R.id.requestFileAction); assertEquals(View.VISIBLE,button.getVisibility()); button.performClick(); idle(); flush(); assertNotNull(dialog()); }
    private void prepareAndPick() { view(R.id.deliveryPrimary).performClick(); flush(); view(R.id.deliveryPrimary).performClick(); idle(); }
    private void save() { prepareAndPick(); org.robolectric.shadows.ShadowActivity.IntentForResult picker=Shadows.shadowOf(activity).getNextStartedActivityForResult(); assertNotNull(picker); Shadows.shadowOf(activity).receiveResult(picker.intent,android.app.Activity.RESULT_OK,new Intent().setData(android.net.Uri.parse("content://fixture.documents/new"))); flush(); }
    private StudentDeliveryDialog dialog() { RequestDetailFragment detail=(RequestDetailFragment)activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer); return (StudentDeliveryDialog)detail.getChildFragmentManager().findFragmentByTag(StudentDeliveryDialog.TAG); }
    private StudentDeliveryViewModel model() { return new ViewModelProvider(dialog()).get(StudentDeliveryViewModel.class); }
    private View view(int id) { View v=dialog().requireView().findViewById(id); assertNotNull("Missing delivery control "+id,v); return v; }
    private String text(int id) { return ((TextView)view(id)).getText().toString(); }
    private void idle() { Shadows.shadowOf(Looper.getMainLooper()).idle(); activity.getSupportFragmentManager().executePendingTransactions(); }
    private void flush() { int remaining=30; do { if(!work.tasks.isEmpty())work.tasks.remove().run(); if(!ui.tasks.isEmpty())ui.tasks.remove().run(); idle(); } while((!work.tasks.isEmpty() || !ui.tasks.isEmpty()) && --remaining>0); assertTrue(remaining>0); }
    private void capture(String name) throws Exception {
        View root=dialog().requireView(); root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY)); root.layout(0,0,width,height); idle();
        android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888); java.io.File dir=new java.io.File("build/outputs/screenshots"); assertTrue(dir.isDirectory()||dir.mkdirs());
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name))) { root.draw(new android.graphics.Canvas(image)); assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out)); } finally { image.recycle(); }
    }
    private static ViewModelProvider.Factory factory(ViewModel vm) { return new ViewModelProvider.Factory() { @Override public <T extends ViewModel>T create(Class<T> type) { return type.cast(vm); } }; }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static final class Queue implements Executor { final ArrayDeque<Runnable> tasks=new ArrayDeque<>(); public void execute(Runnable task) { tasks.add(task); } }
}
