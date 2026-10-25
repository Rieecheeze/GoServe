package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
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
import ph.edu.tip.goserve.domain.PaymentStatus;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,qualifiers="w390dp-h844dp-port-notnight-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class EgovPayInteractionTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private EgovPayViewModel model;
    private final EgovPayViewModelTest.Queue work=new EgovPayViewModelTest.Queue(),ui=new EgovPayViewModelTest.Queue();
    private final EgovPayViewModelTest.Fake actions=new EgovPayViewModelTest.Fake();
    @Before public void reset() { ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class,"_instance",null); }
    @After public void close() { if(controller!=null)controller.close(); RuntimeEnvironment.setFontScale(1f); }

    @Test public void explicitPrepareAndSeparateBrowserClickOpenOnlyPinnedTestUrl() {
        launch(); finish(); assertEquals(0,actions.starts); assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        view(R.id.egovpayPrepare).performClick(); view(R.id.egovpayPrepare).performClick(); assertEquals(1,work.size());
        assertFalse(view(R.id.egovpayBack).isEnabled()); finish(); assertEquals(1,actions.starts);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity()); assertTrue(view(R.id.egovpayBrowser).performClick());
        Intent opened=Shadows.shadowOf(activity).getNextStartedActivity(); assertNotNull(opened);
        assertEquals(Intent.ACTION_VIEW,opened.getAction()); assertEquals(EgovPayViewModelTest.URL,opened.getDataString());
        view(R.id.egovpayBrowser).performClick(); assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        controller.pause().resume(); idle(); finish(); assertEquals(2,actions.refreshes); assertEquals("Test Payment Pending",text(R.id.egovpayHeading));
    }

    @Test public void unknownOutcomeBlocksBackAndRetriesTheHeldCheckoutWithoutShowingPrivateErrors() {
        launch(); finish(); actions.startFailure=new java.io.IOException("private SQL and token");
        view(R.id.egovpayPrepare).performClick(); finish(); assertFalse(view(R.id.egovpayBack).isEnabled());
        assertFalse(text(R.id.egovpayStatus).contains("private")); assertEquals("Retry same checkout",text(R.id.egovpayPrepare));
        androidx.fragment.app.Fragment held=activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer);
        activity.getOnBackPressedDispatcher().onBackPressed(); idle(); assertSame(held,activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer));
        actions.startFailure=null; view(R.id.egovpayPrepare).performClick(); finish(); assertSame(actions.inputs.get(0),actions.inputs.get(1));
        assertTrue(view(R.id.egovpayBack).isEnabled());
    }

    @Test public void paidLabelRequiresTheCanonicalRowAndAccountChangeClearsPaymentDetails() {
        launch(); finish(); actions.providerStatus="paid"; view(R.id.egovpayRefresh).performClick(); finish();
        assertEquals("Test Payment Pending",text(R.id.egovpayHeading));
        actions.record=EgovPayViewModelTest.record(PaymentStatus.PAID); view(R.id.egovpayRefresh).performClick(); finish();
        assertEquals("Test Payment Confirmed",text(R.id.egovpayHeading)); assertEquals(View.GONE,view(R.id.egovpayPrepare).getVisibility());
        assertEquals("PHP 123.45",text(R.id.egovpayAmount));
        actions.current=false; model.checkCurrentAccount(); idle(); assertEquals("",text(R.id.egovpayAmount));
        assertEquals(View.GONE,view(R.id.egovpayBrowser).getVisibility()); assertEquals(View.GONE,view(R.id.egovpayRefresh).getVisibility());
    }

    @Test @Config(shadows=SeryoInteractionTest.Api26FontScaling.class)
    public void smallScreenWithLargeTextKeepsActionsScrollableAndTargetsLarge() throws Exception {
        RuntimeEnvironment.setQualifiers("w320dp-h640dp-port-night-mdpi"); RuntimeEnvironment.setFontScale(2f);
        launch(); finish(); View root=view(R.id.activityRoot);
        root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY)); root.layout(0,0,320,640);
        androidx.core.widget.NestedScrollView scroll=(androidx.core.widget.NestedScrollView)view(R.id.egovpayScroll);
        assertTrue(view(R.id.egovpayPrepare).getHeight()>=48); assertTrue(view(R.id.egovpayRefresh).getHeight()>=48);
        assertTrue(view(R.id.egovpayAmount).getWidth()<=280); assertTrue(((TextView)view(R.id.egovpayStatus)).getLayout().getLineCount()>1);
        capture("egovpay-small-dark.png",320,640);
        scroll.fullScroll(View.FOCUS_DOWN); idle(); assertTrue(scroll.getScrollY()>0);
        int[] button=new int[2],viewport=new int[2]; view(R.id.egovpayRefresh).getLocationOnScreen(button); scroll.getLocationOnScreen(viewport);
        assertTrue(button[1]+view(R.id.egovpayRefresh).getHeight()<=viewport[1]+scroll.getHeight()); capture("egovpay-small-dark-end.png",320,640);
    }

    @Test public void recreationDoesNotRepeatCheckoutOrOpenBrowserAutomatically() {
        launch(); finish(); view(R.id.egovpayPrepare).performClick();
        controller.recreate(); activity=controller.get(); idle(); assertEquals(1,work.size()); finish();
        assertEquals(1,actions.starts); assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test public void lightCheckoutStatesKeepTestContextAndServerConfirmationVisible() throws Exception {
        launch(); finish(); layout(390,844); capture("egovpay-pending-light.png",390,844);
        view(R.id.egovpayPrepare).performClick(); finish(); layout(390,844); capture("egovpay-ready-light.png",390,844);
        actions.record=EgovPayViewModelTest.record(PaymentStatus.PAID); view(R.id.egovpayRefresh).performClick(); finish();
        layout(390,844); capture("egovpay-paid-light.png",390,844);
        assertEquals("Test Payment Confirmed",text(R.id.egovpayHeading)); assertEquals(View.GONE,view(R.id.egovpayBrowser).getVisibility());
    }

    @Test public void unresolvedCheckoutHasExplicitSafeReturnWithoutAnotherFinancialAction() {
        launch(); finish(); actions.startFailure=new ph.edu.tip.goserve.data.PaymentRepository.WorkflowException("CHECKOUT_UNCONFIRMED");
        view(R.id.egovpayPrepare).performClick(); assertEquals(View.GONE,view(R.id.egovpayReturn).getVisibility()); finish();
        assertTrue(text(R.id.egovpayStatus).contains("contact the office")); assertTrue(view(R.id.egovpayReturn).isEnabled());
        int starts=actions.starts; view(R.id.egovpayReturn).performClick(); idle();
        assertFalse(activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer) instanceof EgovPayFragment);
        assertEquals(starts,actions.starts);
    }

    @Test @Config(qualifiers="w390dp-h844dp-port-night-mdpi")
    public void darkCheckoutStatesUseExistingThemeAndActionContrast() throws Exception {
        launch(); finish(); layout(390,844); capture("egovpay-pending-dark.png",390,844);
        view(R.id.egovpayPrepare).performClick(); finish(); layout(390,844); capture("egovpay-ready-dark.png",390,844);
        assertEquals("PHP 123.45",text(R.id.egovpayAmount)); assertTrue(view(R.id.egovpayBrowser).isEnabled());
        actions.record=EgovPayViewModelTest.record(PaymentStatus.PAID); view(R.id.egovpayRefresh).performClick(); finish();
        layout(390,844); capture("egovpay-paid-dark.png",390,844);
    }

    private void launch() {
        GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication(); model=new EgovPayViewModel(app,actions,work,ui);
        controller=Robolectric.buildActivity(MainActivity.class,new Intent(app,MainActivity.class).putExtra(MainActivity.EXTRA_PREVIEW,true)).setup().visible(); activity=controller.get(); idle();
        ViewModelProvider.Factory factory=new ViewModelProvider.Factory() { @Override public <T extends ViewModel> T create(Class<T> type) { return type.cast(model); } };
        EgovPayFragment fragment=new EgovPayFragment(factory); Bundle args=new Bundle(); args.putString("request_id",EgovPayViewModelTest.REQUEST); fragment.setArguments(args);
        activity.navigate(fragment,true); idle();
    }
    private void finish() { work.run(); ui.run(); idle(); }
    private View view(int id) { View result=activity.findViewById(id); assertNotNull(result); return result; }
    private String text(int id) { return ((TextView)view(id)).getText().toString(); }
    private void idle() { Shadows.shadowOf(Looper.getMainLooper()).idle(); activity.getSupportFragmentManager().executePendingTransactions(); }
    private void layout(int width,int height) {
        View root=view(R.id.activityRoot); root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY)); root.layout(0,0,width,height); idle();
    }
    private void capture(String name,int width,int height) throws Exception {
        View root=view(R.id.activityRoot); android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);
        java.io.File folder=new java.io.File("build/outputs/screenshots"); assertTrue(folder.isDirectory() || folder.mkdirs());
        try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(folder,name))) { root.draw(new android.graphics.Canvas(bitmap)); assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output)); }
        finally { bitmap.recycle(); }
    }
}
