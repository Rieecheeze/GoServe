package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.os.Looper;
import android.util.SparseArray;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.AuthRepository;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,qualifiers="w390dp-h844dp-port-notnight-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class PasswordChangeInteractionTest {
    private static final String CURRENT=" Current 1! password ",NEXT=" Next 2! password ";
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private final Queue work=new Queue(),ui=new Queue();
    private final Fake actions=new Fake();
    private PasswordChangeViewModel vm;
    @Before public void resetFactory() { ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class,"_instance",null); }
    @After public void close() {
        if(controller!=null)controller.close(); RuntimeEnvironment.setFontScale(1f);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
    @Test public void settingsEntryOpensThePasswordScreenWithoutARequest() {
        launchHost(); view(R.id.changePasswordButton).performClick(); idle();
        assertTrue(current() instanceof PasswordChangeFragment);
        assertEquals(View.VISIBLE,view(R.id.passwordChangeError).getVisibility());
        assertFalse(view(R.id.passwordSaveButton).isEnabled());
    }
    @Test public void freshScreenKeepsPasswordsBlankAndCodeHidden() {
        launch(); assertEquals("",text(R.id.currentPasswordInput)); assertEquals("",text(R.id.newPasswordInput));
        assertEquals(View.GONE,view(R.id.passwordCodePanel).getVisibility()); assertEquals(0,work.size());
        assertEquals(View.GONE,view(R.id.bottomNavigation).getVisibility());
        for(int id:secretIds()) { assertFalse(view(id).isSaveEnabled()); assertFalse(view(id).isSaveFromParentEnabled()); }
    }
    @Test public void invalidFormShowsSafeVisibleFeedbackWithoutNetworkWork() {
        launch(); view(R.id.passwordSaveButton).performClick();
        assertEquals(activity.getString(R.string.password_current_required),text(R.id.passwordChangeError)); assertEquals(0,work.size());
        fill(); input(R.id.newPasswordConfirmation).setText("mismatch"); view(R.id.passwordSaveButton).performClick();
        assertEquals(activity.getString(R.string.password_confirmation_mismatch),text(R.id.passwordChangeError)); assertEquals(0,work.size());
    }
    @Test public void changingDisablesAllInputsNavigationAndDuplicateSubmission() {
        launch(); fill(); view(R.id.passwordSaveButton).performClick(); view(R.id.passwordSaveButton).performClick();
        assertEquals(1,work.size()); assertEquals(0,actions.changes);
        for(int id:secretIds())assertFalse(view(id).isEnabled());
        assertFalse(view(R.id.passwordBack).isEnabled()); assertEquals(View.VISIBLE,view(R.id.passwordChangeProgress).getVisibility());
        activity.getOnBackPressedDispatcher().onBackPressed(); idle(); assertTrue(current() instanceof PasswordChangeFragment); assertFalse(activity.isFinishing());
        work.run(); ui.run(); idle(); assertEquals(1,actions.changes); assertEquals(PasswordChangeViewModel.Mode.DONE,vm.state().getValue().mode);
        for(int id:secretIds())assertEquals("",text(id)); assertFalse(view(R.id.passwordSaveButton).isEnabled());
    }
    @Test public void reauthenticationAsksForAnExplicitEmailActionAndRetainsDraftOnRotation() {
        launch(); fill(); actions.failure=error("REAUTHENTICATION_REQUIRED"); submitAndComplete();
        assertEquals(View.VISIBLE,view(R.id.passwordCodePanel).getVisibility()); assertEquals(0,actions.emails);
        assertEquals(View.GONE,view(R.id.passwordCodeStatus).getVisibility());
        actions.failure=null; view(R.id.passwordSendCode).performClick(); assertEquals(1,work.size());
        assertFalse(view(R.id.passwordSaveButton).isEnabled()); work.run(); ui.run(); idle();
        assertEquals(1,actions.emails); assertEquals(View.VISIBLE,view(R.id.passwordCodeStatus).getVisibility());
        input(R.id.passwordCodeInput).setText("123456"); controller.recreate(); activity=controller.get(); idle();
        assertEquals(CURRENT,text(R.id.currentPasswordInput)); assertEquals(NEXT,text(R.id.newPasswordInput)); assertEquals("123456",text(R.id.passwordCodeInput));
        assertEquals(0,work.size()); view(R.id.passwordSaveButton).performClick(); work.run(); ui.run(); idle();
        assertEquals(2,actions.changes); assertEquals("123456",actions.code); assertEquals(1,actions.emails);
    }
    @Test public void emailFailureCannotClaimARequestedCodeAndOffersRetry() {
        launch(); fill(); actions.failure=error("REAUTHENTICATION_REQUIRED"); submitAndComplete();
        actions.failure=error("RATE_LIMITED"); view(R.id.passwordSendCode).performClick(); work.run(); ui.run(); idle();
        assertEquals(View.GONE,view(R.id.passwordCodeStatus).getVisibility()); assertTrue(view(R.id.passwordSendCode).isEnabled());
        assertEquals(activity.getString(R.string.password_rate_limited),text(R.id.passwordChangeError)); assertEquals(NEXT,text(R.id.newPasswordInput));
    }
    @Test public void discardedViewsClearSecretsAndHierarchyCannotRestoreThem() {
        launch(); fill(); SparseArray<android.os.Parcelable> saved=new SparseArray<>(); current().requireView().saveHierarchyState(saved);
        View old=current().requireView(); activity.getOnBackPressedDispatcher().onBackPressed(); idle();
        assertTrue(current() instanceof MoreFragment); assertEquals(View.VISIBLE,view(R.id.bottomNavigation).getVisibility());
        for(int id:secretIds())assertEquals("",((TextView)old.findViewById(id)).getText().toString());
        launchScreen(); current().requireView().restoreHierarchyState(saved);
        assertEquals("",text(R.id.currentPasswordInput)); assertEquals("",text(R.id.newPasswordInput));
    }
    @Test public void accountInvalidationClearsVisibleSecretsAndCannotSubmitForAnotherUser() {
        launch(); fill(); actions.current=false;
        GoServeApplication app=(GoServeApplication)activity.getApplication();
        ((androidx.lifecycle.MutableLiveData<Long>)app.accountInvalidations()).setValue(222L); idle();
        for(int id:secretIds())assertEquals("",text(id)); assertFalse(view(R.id.passwordSaveButton).isEnabled());
        view(R.id.passwordSaveButton).performClick(); assertEquals(0,work.size()); assertFalse(text(R.id.passwordChangeError).contains("private"));
    }
    @Test public void uncertainOutcomeNeverShowsSuccessOrOffersAnotherUpdate() {
        launch(); fill(); actions.failure=error("OUTCOME_UNCERTAIN"); actions.loseAccount=true; submitAndComplete();
        for(int id:secretIds())assertEquals("",text(id)); assertFalse(view(R.id.passwordSaveButton).isEnabled());
        assertEquals(activity.getString(R.string.password_change_uncertain_notice),text(R.id.passwordChangeError));
    }
    @Test @Config(shadows=SeryoInteractionTest.Api26FontScaling.class)
    public void narrowLargeTextKeepsTheFormScrollableAndPrimaryActionReachable() throws Exception {
        RuntimeEnvironment.setQualifiers("w320dp-h480dp-port-night-mdpi"); RuntimeEnvironment.setFontScale(2f); launch();
        View root=current().requireView(); root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY)); root.layout(0,0,320,480);
        androidx.core.widget.NestedScrollView scroll=(androidx.core.widget.NestedScrollView)view(R.id.passwordScroll);
        assertTrue(view(R.id.passwordSaveButton).getHeight()>=48); assertTrue(scroll.getHeight()>0);
        assertTrue(scroll.getBottom()<=view(R.id.passwordFooter).getTop());
        scroll.fullScroll(View.FOCUS_DOWN); idle(); assertTrue(scroll.getScrollY()>0);
        assertTrue(view(R.id.passwordHelp).getBottom()<=scroll.getScrollY()+scroll.getHeight());
        view(R.id.passwordSaveButton).performClick(); idle();
        root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY)); root.layout(0,0,320,480);
        assertTrue("Validation feedback must not consume the scrolling form",scroll.getHeight()>=100);
        assertEquals(View.VISIBLE,view(R.id.passwordChangeError).getVisibility());
        fill(); actions.failure=error("CURRENT_PASSWORD_INVALID"); submitAndComplete();
        root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY)); root.layout(0,0,320,480);
        assertTrue("Server feedback must leave space to correct the form",scroll.getHeight()>=100);
        root.getViewTreeObserver().dispatchOnPreDraw();
        android.graphics.Rect message=new android.graphics.Rect(); view(R.id.passwordChangeError).getDrawingRect(message);
        scroll.offsetDescendantRectToMyCoords(view(R.id.passwordChangeError),message);
        assertTrue("Failed submission must reveal the error",message.top>=scroll.getScrollY());
        assertTrue("Error must fit above the pinned action",message.bottom<=scroll.getScrollY()+scroll.getHeight());
        scroll.fullScroll(View.FOCUS_DOWN); idle();
        capture("password-change-320dp-2x-dark.png",320,480);
        scroll.scrollTo(0,0); actions.failure=error("REAUTHENTICATION_REQUIRED"); submitAndComplete();
        root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY)); root.layout(0,0,320,480);
        root.getViewTreeObserver().dispatchOnPreDraw();
        view(R.id.passwordCodeIntro).getDrawingRect(message); scroll.offsetDescendantRectToMyCoords(view(R.id.passwordCodeIntro),message);
        capture("password-code-320dp-2x-dark.png",320,480);
        String viewport="message="+message+", scroll="+scroll.getScrollY()+", height="+scroll.getHeight()+", shown="+view(R.id.passwordCodeIntro).isShown();
        assertTrue("New verification instructions must be brought into view: "+viewport,message.top>=scroll.getScrollY());
        assertTrue("Verification instructions must fit above the action: "+viewport,message.bottom<=scroll.getScrollY()+scroll.getHeight());
    }
    @Test public void captureSettingsPasswordAndCodeInBothThemes() throws Exception {
        for(boolean night:new boolean[]{false,true}) {
            RuntimeEnvironment.setQualifiers("w390dp-h844dp-port-"+(night?"night":"notnight")+"-mdpi");
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(night?androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES:androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
            launch(); capture("password-change-"+(night?"dark":"light")+".png",390,780);
            fill(); actions.failure=error("REAUTHENTICATION_REQUIRED"); submitAndComplete();
            capture("password-code-"+(night?"dark":"light")+".png",390,780);
            controller.close(); controller=null; actions.failure=null;
            ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class,"_instance",null);
        }
    }
    private void launchHost() {
        GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication();
        controller=Robolectric.buildActivity(MainActivity.class,new Intent(app,MainActivity.class).putExtra(MainActivity.EXTRA_PREVIEW,true)).setup().visible(); activity=controller.get(); idle();
        activity.navigate(new MoreFragment(),false); idle();
    }
    private void launch() { launchHost(); launchScreen(); }
    private void launchScreen() {
        vm=new PasswordChangeViewModel(activity.getApplication(),actions,work,ui);
        ViewModelProvider.Factory factory=new ViewModelProvider.Factory() { @Override public <T extends ViewModel> T create(Class<T> type) { return type.cast(vm); } };
        activity.navigate(new PasswordChangeFragment(factory),true); idle();
    }
    private static int[] secretIds() { return new int[]{R.id.currentPasswordInput,R.id.newPasswordInput,R.id.newPasswordConfirmation,R.id.passwordCodeInput}; }
    private void fill() { input(R.id.currentPasswordInput).setText(CURRENT); input(R.id.newPasswordInput).setText(NEXT); input(R.id.newPasswordConfirmation).setText(NEXT); }
    private void submitAndComplete() { view(R.id.passwordSaveButton).performClick(); work.run(); ui.run(); idle(); }
    private View view(int id) { View value=activity.findViewById(id); assertNotNull("Missing password control",value); return value; }
    private EditText input(int id) { return (EditText)view(id); }
    private String text(int id) { return ((TextView)view(id)).getText().toString(); }
    private androidx.fragment.app.Fragment current() { return activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer); }
    private void idle() { Shadows.shadowOf(Looper.getMainLooper()).idle(); activity.getSupportFragmentManager().executePendingTransactions(); }
    private void capture(String name,int width,int height) throws Exception {
        View root=current().requireView(); root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY)); root.layout(0,0,width,height);
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);
        java.io.File folder=new java.io.File("build/outputs/screenshots"); assertTrue(folder.isDirectory()||folder.mkdirs());
        try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(folder,name))) { root.draw(new android.graphics.Canvas(bitmap)); assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output)); }
        finally { bitmap.recycle(); }
    }
    private static AuthRepository.PasswordChangeException error(String code) { return new AuthRepository.PasswordChangeException(code,"private provider text"); }
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>(); public void execute(Runnable task) { tasks.add(task); }
        void run() { assertFalse("Expected queued password work",tasks.isEmpty()); tasks.remove().run(); } int size() { return tasks.size(); }
    }
    private static final class Fake implements PasswordChangeViewModel.Actions {
        boolean current=true,ownSignOut,loseAccount; int changes,emails; String code; Exception failure;
        public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public boolean completedSignOut() { return ownSignOut; }
        public void change(String old,String password,String confirmation,String code) throws Exception {
            ++changes; this.code=code; if(failure!=null) { if(loseAccount)current=false; throw failure; } current=false; ownSignOut=true;
        }
        public void requestCode() throws Exception { ++emails; if(failure!=null)throw failure; }
    }
}
