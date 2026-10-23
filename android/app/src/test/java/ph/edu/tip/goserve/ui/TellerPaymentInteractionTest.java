package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.checkbox.MaterialCheckBox;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Set;
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
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.RealtimeDataSource;
import ph.edu.tip.goserve.domain.PaymentData;
import ph.edu.tip.goserve.domain.PaymentStatus;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,qualifiers="w390dp-h844dp-port-notnight-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class TellerPaymentInteractionTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private final Queue work=new Queue(),ui=new Queue();
    private final Fake actions=new Fake();
    private TellerPaymentViewModel vm;
    private final Session realtime=new Session();

    @Before public void resetFactory() {
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class,"_instance",null);
    }
    @After public void close() {
        if(controller!=null)controller.close(); RuntimeEnvironment.setFontScale(1f);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        ReflectionHelpers.setField(RuntimeEnvironment.getApplication(),"realtimeSession",null);
    }

    @Test public void initialSearchHasNoExampleReferenceOrPreselectedCashReceipt() {
        launch(); assertEquals("Cash payments",text(R.id.cashToolbar)); assertEquals("Find a payment",text(R.id.cashHeading));
        assertEquals("",text(R.id.cashReferenceInput)); assertFalse(check().isChecked());
        assertFalse(view(R.id.cashConfirmButton).isEnabled()); assertEquals(View.GONE,view(R.id.cashReviewCard).getVisibility());
        assertEquals(0,actions.lookups); assertEquals(0,actions.confirms); assertFalse(((EditText)view(R.id.cashReferenceInput)).isSaveEnabled());
    }

    @Test public void searchAndExplicitCheckboxDriveOnlyTheRealReviewedPayment() {
        launch(); lookup(); assertEquals("actual-reference",text(R.id.cashReviewReference));
        assertTrue(text(R.id.cashReviewAmount).contains("123456789.0123456789")); assertFalse(check().isChecked());
        assertEquals("The authoritative amount must contrast against its summary card",androidx.core.content.ContextCompat.getColor(activity,R.color.cash_summary_ink),((TextView)view(R.id.cashReviewAmount)).getCurrentTextColor());
        assertFalse(view(R.id.cashConfirmButton).isEnabled()); check().performClick(); assertTrue(view(R.id.cashReviewButton).isEnabled());
        view(R.id.cashReviewButton).performClick(); assertEquals(0,actions.confirms);
        view(R.id.cashConfirmButton).performClick(); view(R.id.cashConfirmButton).performClick();
        assertEquals(View.VISIBLE,view(R.id.cashProgress).getVisibility()); assertEquals(0,actions.confirms);
        work.run(); ui.run(); idle(); assertEquals(1,actions.confirms); assertEquals("Payment confirmed",text(R.id.cashHeading));
        assertTrue(text(R.id.cashReviewId).contains("00000000-0000-4000-8000-000000000002"));
        assertTrue(text(R.id.cashReviewTime).contains("2026-10-20")); assertFalse(view(R.id.cashConfirmButton).isEnabled());
    }

    @Test public void paidLookupShowsReceiptAndNeverOffersASecondConfirmation() {
        actions.review=review(PaymentStatus.PAID); launch(); lookup();
        assertEquals("Payment already confirmed",text(R.id.cashHeading)); assertEquals(View.GONE,check().getVisibility());
        assertFalse(view(R.id.cashConfirmButton).isEnabled()); assertEquals(0,actions.confirms);
        assertEquals(View.VISIBLE,view(R.id.cashReviewTime).getVisibility());
    }

    @Test public void failedLookupHasActionableRetryAndNeverLeaksTransportDetails() {
        actions.failure=new java.io.IOException("private server hostname and SQL"); launch();
        ((EditText)view(R.id.cashReferenceInput)).setText("actual-reference"); view(R.id.cashSearchButton).performClick(); work.run(); ui.run(); idle();
        assertEquals(View.VISIBLE,view(R.id.cashError).getVisibility()); assertFalse(text(R.id.cashError).contains("private"));
        actions.failure=null; assertTrue(view(R.id.cashCheckStatusButton).performClick()); work.run(); ui.run(); idle();
        assertEquals(2,actions.lookups); assertEquals(View.VISIBLE,view(R.id.cashReviewCard).getVisibility()); assertEquals(0,actions.confirms);
    }

    @Test public void unreadyOrWrongRoleHasNoOperationalControls() {
        actions.allowed=false; launch(); assertEquals(View.VISIBLE,view(R.id.cashError).getVisibility());
        assertFalse(view(R.id.cashSearchButton).isEnabled()); assertFalse(view(R.id.cashConfirmButton).isEnabled());
        assertEquals(View.GONE,view(R.id.cashReferenceInput).getVisibility()); assertEquals(0,actions.lookups);
    }

    @Test public void recreationRetainsInFlightOperationAndDoesNotSendAgain() {
        launch(); lookup(); check().performClick(); view(R.id.cashReviewButton).performClick(); view(R.id.cashConfirmButton).performClick();
        assertEquals(1,work.size()); controller.recreate(); activity=controller.get(); idle();
        assertEquals(TellerPaymentViewModel.Mode.CONFIRMING,vm.state().getValue().mode);
        assertEquals(View.VISIBLE,view(R.id.cashProgress).getVisibility()); work.run(); ui.run(); idle();
        assertEquals(1,actions.confirms); assertEquals("Payment confirmed",text(R.id.cashHeading));
    }

    @Test @Config(shadows=SeryoInteractionTest.Api26FontScaling.class)
    public void smallLargeTextReviewScrollsAndKeepsTheConfirmTargetSeparate() throws Exception {
        RuntimeEnvironment.setQualifiers("w320dp-h640dp-port-night-mdpi"); RuntimeEnvironment.setFontScale(2f); launch(); lookup();
        check().performClick(); view(R.id.cashReviewButton).performClick();
        View root=activity.findViewById(R.id.activityRoot); root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY)); root.layout(0,0,320,480);
        androidx.core.widget.NestedScrollView scroll=(androidx.core.widget.NestedScrollView)view(R.id.cashScroll);
        View confirm=view(R.id.cashConfirmButton); assertTrue("Confirm target must retain48dp",confirm.getHeight()>=48);
        assertEquals(32f,((TextView)view(R.id.cashReviewAmount)).getTextSize(),.01f);
        assertTrue(scroll.getHeight()>0); assertTrue(scroll.getBottom()<=view(R.id.cashConfirmHost).getTop());
        assertTrue(view(R.id.cashReviewAmount).getWidth()<=280); assertEquals("actual-reference",text(R.id.cashReviewReference));
        scroll.fullScroll(View.FOCUS_DOWN); idle(); assertTrue("Review must scroll",scroll.getScrollY()>0);
        assertTrue(view(R.id.cashDuplicateWarning).getBottom()<=scroll.getScrollY()+scroll.getHeight());
        int[] bodyLocation=new int[2],scrollLocation=new int[2]; view(R.id.cashDuplicateBody).getLocationOnScreen(bodyLocation); scroll.getLocationOnScreen(scrollLocation);
        assertTrue("The final warning line must be visible after scrolling",bodyLocation[1]+view(R.id.cashDuplicateBody).getHeight()<=scrollLocation[1]+scroll.getHeight());
        capture("teller-confirm-small-dark.png",320,480);
    }

    @Test public void sharedPaymentSubscriptionClosesOnDetachAndNeverMutatesFromEvents() {
        launch(); assertEquals(Set.of("payments"),realtime.tables); assertNotNull(realtime.listener); lookup();
        RealtimeDataSource.Invalidation event=ReflectionHelpers.callConstructor(RealtimeDataSource.Invalidation.class,
                ReflectionHelpers.ClassParameter.from(long.class,1L),ReflectionHelpers.ClassParameter.from(Set.class,Set.of("payments")),
                ReflectionHelpers.ClassParameter.from(RealtimeDataSource.Reason.class,RealtimeDataSource.Reason.CHANGE));
        realtime.listener.onInvalidated(event); idle(); work.run(); ui.run(); idle(); assertEquals(2,actions.lookups); assertEquals(0,actions.confirms);
        activity.navigate(new MoreFragment(),false); idle(); assertEquals(1,realtime.closed);
    }

    @Test public void priorAccountInvalidationDoesNotDisableANewCurrentLease() {
        GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication();
        ((androidx.lifecycle.MutableLiveData<Long>)app.accountInvalidations()).setValue(42L);
        launch(); assertTrue("A historical account event must be checked against the current lease",view(R.id.cashSearchButton).isEnabled());
        actions.current=false; ((androidx.lifecycle.MutableLiveData<Long>)app.accountInvalidations()).setValue(43L); idle();
        assertFalse(view(R.id.cashSearchButton).isEnabled()); assertEquals(View.VISIBLE,view(R.id.cashError).getVisibility());
    }

    @Test public void systemBackReturnsToSearchAndKeyboardCanStartANewLookup() {
        launch(); lookup(); activity.getOnBackPressedDispatcher().onBackPressed(); idle();
        assertFalse("Back from review must not finish the app",activity.isFinishing()); assertEquals(TellerPaymentViewModel.Mode.SEARCH,vm.state().getValue().mode);
        ((EditText)view(R.id.cashReferenceInput)).setText("actual-reference"); View search=view(R.id.cashSearchButton); assertTrue(search.requestFocusFromTouch());
        search.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_DPAD_CENTER));
        search.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_DPAD_CENTER)); idle(); work.run(); ui.run(); idle();
        assertEquals(2,actions.lookups); view(R.id.cashEditButton).performClick(); idle(); assertEquals(TellerPaymentViewModel.Mode.SEARCH,vm.state().getValue().mode);
        assertEquals(0,actions.confirms);
    }

    @Test public void uncheckedCashAcknowledgementHasAVisiblyDisabledConfirmation() {
        launch(); lookup(); com.google.android.material.button.MaterialButton confirm=(com.google.android.material.button.MaterialButton)view(R.id.cashReviewButton);
        int disabled=confirm.getBackgroundTintList().getColorForState(confirm.getDrawableState(),0);
        check().performClick(); int enabled=confirm.getBackgroundTintList().getColorForState(confirm.getDrawableState(),0);
        assertNotEquals("Disabled and actionable confirmations must look different",disabled,enabled);
    }

    @Test public void captureApprovedTellerSearchReviewAndReceiptInBothThemes() throws Exception {
        for(boolean night:new boolean[]{false,true}) {
            RuntimeEnvironment.setQualifiers("w390dp-h844dp-port-"+(night?"night":"notnight")+"-mdpi");
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(night?androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES:androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
            launch(); String theme=night?"dark":"light"; capture("teller-search-"+theme+".png");
            lookup(); capture("teller-pending-"+theme+".png"); check().performClick(); view(R.id.cashReviewButton).performClick(); capture("teller-confirm-"+theme+".png");
            view(R.id.cashConfirmButton).performClick(); work.run(); ui.run(); idle();
            capture("teller-confirmed-"+theme+".png"); assertEquals(1,actions.confirms-(night?1:0));
            controller.close(); controller=null; ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class,"_instance",null);
        }
    }

    @Test public void paymentFoundMustEnterReviewBeforeWritingAndBackRequiresANewAcknowledgement() {
        launch(); lookup(); assertEquals("Payment found",text(R.id.cashHeading));
        assertEquals(View.VISIBLE,view(R.id.bottomNavigation).getVisibility());
        assertEquals(View.GONE,view(R.id.cashConfirmHost).getVisibility());
        assertFalse(view(R.id.cashReviewButton).isEnabled()); check().performClick();
        view(R.id.cashConfirmButton).performClick(); assertEquals(0,work.size());
        view(R.id.cashReviewButton).performClick(); idle();
        assertEquals(TellerPaymentViewModel.Mode.REVIEW,vm.state().getValue().mode);
        assertEquals(View.GONE,view(R.id.bottomNavigation).getVisibility()); assertTrue(view(R.id.cashConfirmButton).isEnabled());
        assertEquals(0,actions.confirms); activity.getOnBackPressedDispatcher().onBackPressed(); idle();
        assertEquals(TellerPaymentViewModel.Mode.FOUND,vm.state().getValue().mode); assertFalse(check().isChecked());
        assertEquals(View.VISIBLE,view(R.id.bottomNavigation).getVisibility());
    }

    @Test public void pendingTabKeepsTheSelectedPaymentAndSearchExplicitlyResetsTheReview() {
        launch(); lookup(); activity.configureRoleNavigation(ph.edu.tip.goserve.domain.Role.TELLER);
        CapsuleNavigationView navigation=activity.findViewById(R.id.bottomNavigation);
        navigation.setSelectedItemId(R.id.nav_more); idle(); navigation.setSelectedItemId(R.id.nav_cash_pending); idle();
        assertEquals("Payment found",text(R.id.cashHeading)); assertEquals("actual-reference",text(R.id.cashReviewReference));
        assertEquals(1,actions.lookups); assertEquals(0,work.size()); assertFalse(check().isChecked());
        navigation.setSelectedItemId(R.id.nav_payments); idle(); assertEquals("Find a payment",text(R.id.cashHeading));
        assertNull(vm.state().getValue().review); assertEquals(0,actions.confirms);
    }

    @Test public void pendingWithoutASelectedPaymentOffersSearchWithoutMakingAQueue() {
        launch(); activity.configureRoleNavigation(ph.edu.tip.goserve.domain.Role.TELLER);
        ((CapsuleNavigationView)activity.findViewById(R.id.bottomNavigation)).setSelectedItemId(R.id.nav_cash_pending); idle();
        assertEquals("No payment selected",text(R.id.cashHeading)); assertEquals(View.GONE,view(R.id.cashReferenceInput).getVisibility());
        assertEquals(0,work.size()); view(R.id.cashEditButton).performClick(); idle();
        assertEquals("Find a payment",text(R.id.cashHeading)); assertEquals(0,actions.lookups); assertEquals(0,actions.confirms);
    }

    @Test public void paymentIdArgumentStartsOneCanonicalReadAndSurvivesRecreation() {
        actions.review=review(PaymentStatus.PAID); launch();
        TellerPaymentFragment detail=new TellerPaymentFragment(factory()); android.os.Bundle args=new android.os.Bundle();
        args.putString(TellerPaymentFragment.ARG_PAYMENT_ID,actions.review.paymentId); detail.setArguments(args);
        activity.navigate(detail,true); idle(); assertEquals(1,work.size()); work.run(); ui.run(); idle();
        assertEquals("actual-reference",vm.state().getValue().reference); assertEquals(1,actions.lookups);
        controller.recreate(); activity=controller.get(); idle(); assertEquals(0,work.size()); assertEquals(1,actions.lookups);
        assertEquals("Payment already confirmed",text(R.id.cashHeading)); assertEquals(0,actions.confirms);
    }

    private ViewModelProvider.Factory factory() {
        return new ViewModelProvider.Factory() { @Override public <T extends ViewModel> T create(Class<T> type) { return type.cast(vm); } };
    }
    private void launch() {
        GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication(); ReflectionHelpers.setField(app,"realtimeSession",realtime);
        vm=new TellerPaymentViewModel(app,actions,work,ui);
        controller=Robolectric.buildActivity(MainActivity.class,new Intent(app,MainActivity.class).putExtra(MainActivity.EXTRA_PREVIEW,true)).setup().visible(); activity=controller.get(); idle();
        CapsuleNavigationView navigation=activity.findViewById(R.id.bottomNavigation);
        navigation.getMenu().clear(); navigation.inflateMenu(R.menu.menu_teller); navigation.setSelectedItemId(R.id.nav_payments);
        activity.navigate(new TellerPaymentFragment(factory()),false); idle();
    }
    private void lookup() { ((EditText)view(R.id.cashReferenceInput)).setText("actual-reference"); view(R.id.cashSearchButton).performClick(); work.run(); ui.run(); idle(); }
    private View view(int id) { View result=activity.findViewById(id); assertNotNull("Missing native payment control",result); return result; }
    private String text(int id) { return ((TextView)view(id)).getText().toString(); }
    private MaterialCheckBox check() { return (MaterialCheckBox)view(R.id.cashReceivedCheck); }
    private androidx.fragment.app.Fragment current() { return activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer); }
    private void idle() { Shadows.shadowOf(Looper.getMainLooper()).idle(); activity.getSupportFragmentManager().executePendingTransactions(); }
    private void capture(String name) throws Exception {
        capture(name,390,844);
    }
    private void capture(String name,int width,int height) throws Exception {
        View root=activity.findViewById(R.id.activityRoot);
        root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY)); root.layout(0,0,width,height);
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);
        java.io.File folder=new java.io.File("build/outputs/screenshots"); assertTrue(folder.isDirectory() || folder.mkdirs());
        try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(folder,name))) {
            root.draw(new android.graphics.Canvas(bitmap)); assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output));
        } finally { bitmap.recycle(); }
    }
    private static PaymentData.CashReview review(PaymentStatus status) {
        return new PaymentData.CashReview("00000000-0000-4000-8000-000000000001","00000000-0000-4000-8000-000000000002","00000000-0000-4000-8000-000000000003",
                "actual-reference","OFF-REG","Actual service",new BigDecimal("123456789.0123456789"),"PHP",status,status==PaymentStatus.PAID?2:1,status==PaymentStatus.PAID?"2026-10-20T01:00:00Z":null);
    }
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>(); public void execute(Runnable value) { tasks.add(value); }
        void run() { assertFalse("Expected queued operation",tasks.isEmpty()); tasks.remove().run(); } int size() { return tasks.size(); }
    }
    private static final class Fake implements TellerPaymentViewModel.Actions {
        boolean allowed=true,current=true; int lookups,confirms; Exception failure; PaymentData.CashReview review=review(PaymentStatus.PENDING);
        public boolean allowed() { return allowed; } public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); } public void invalidate() {}
        public PaymentData.CashReview lookup(String reference) throws Exception { ++lookups; if(failure!=null)throw failure; return review; }
        public PaymentData.CashReview lookupById(String paymentId) throws Exception { return lookup(paymentId); }
        public PaymentData.CashConfirmation draft(PaymentData.CashReview value,PaymentData.CashConfirmation previous) { return new PaymentData.CashConfirmation(value,"00000000-0000-4000-8000-000000000009"); }
        public PaymentData.CashReview confirm(PaymentData.CashConfirmation input) { ++confirms; return review(PaymentStatus.PAID); }
    }
    private static final class Session implements RealtimeDataSource.Session {
        Set<String> tables; RealtimeDataSource.Listener listener; int closed;
        public long generation() { return 1; } public void start() {} public void stop() {} public void refreshAuthorization() {} public void close() {}
        public RealtimeDataSource.Subscription subscribe(Set<String> tables,RealtimeDataSource.Listener listener) {
            this.tables=tables; this.listener=listener; return ()->++closed;
        }
    }
}
