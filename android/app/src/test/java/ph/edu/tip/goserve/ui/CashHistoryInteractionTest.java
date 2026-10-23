package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.List;
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
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.RealtimeDataSource;
import ph.edu.tip.goserve.domain.PaymentData;
import ph.edu.tip.goserve.domain.PaymentMethod;
import ph.edu.tip.goserve.domain.PaymentStatus;
import ph.edu.tip.goserve.domain.Role;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,qualifiers="w390dp-h844dp-port-notnight-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class CashHistoryInteractionTest {
    private ActivityController<MainActivity> controller; private MainActivity activity;
    private final Queue work=new Queue(),ui=new Queue(); private final Fake actions=new Fake(); private final Session session=new Session();
    @Before public void reset() { ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class,"_instance",null); }
    @After public void close() {
        if(controller!=null)controller.close(); RuntimeEnvironment.setFontScale(1f);
        ReflectionHelpers.setField(RuntimeEnvironment.getApplication(),"realtimeSession",null);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
    @Test public void loadingDoesNotPretendThatAnEmptyHistorySucceeded() {
        launch(); assertEquals(1,work.size()); assertEquals(View.VISIBLE,view(R.id.historyProgress).getVisibility());
        assertEquals(View.GONE,view(R.id.historyEmpty).getVisibility()); assertEquals(0,actions.reads);
        complete(); assertEquals(id(1),text(R.id.historyPaymentId)); assertEquals(View.GONE,view(R.id.historyProgress).getVisibility());
        assertTrue(text(R.id.historyPaid).contains("35.00")); assertTrue(text(R.id.historyActor).contains(id(91)));
        assertTrue(text(R.id.historyTime).contains("2026-10-21T01:00:00Z"));
    }
    @Test public void emptyHistoryAppearsOnlyAfterASuccessfulRead() {
        actions.result=new PaymentData.Page(List.of(),null); launch(); complete();
        assertEquals(View.VISIBLE,view(R.id.historyEmpty).getVisibility()); assertEquals(View.GONE,view(R.id.historyError).getVisibility());
        assertEquals(View.GONE,view(R.id.historyMore).getVisibility());
    }
    @Test public void safeRetryRetainsTheErrorUntilAnExplicitRead() {
        actions.error=new java.io.IOException("private SQL hostname"); launch(); complete();
        assertEquals(View.VISIBLE,view(R.id.historyError).getVisibility()); assertFalse(text(R.id.historyError).contains("private"));
        assertEquals(View.GONE,view(R.id.historyEmpty).getVisibility()); actions.error=null;
        view(R.id.historyRetry).performClick(); complete(); assertEquals(2,actions.reads); assertEquals(id(1),text(R.id.historyPaymentId));
    }
    @Test public void loadMoreIsShownOnlyForTheCanonicalFullPageCursor() {
        actions.result=new PaymentData.Page(List.of(row(1)),id(1)); launch(); complete();
        assertEquals(View.VISIBLE,view(R.id.historyMore).getVisibility()); actions.result=new PaymentData.Page(List.of(row(2)),null);
        view(R.id.historyMore).performClick(); complete(); assertEquals(id(1),actions.lastCursor);
        assertEquals(View.GONE,view(R.id.historyMore).getVisibility()); assertEquals(4,list().getAdapter().getItemCount());
    }
    @Test public void historyRowOpensOnlyTheCanonicalPaymentIdAndBackReturnsToHistory() {
        launch(); complete(); View row=view(R.id.historyPaymentId); ((View)row.getParent().getParent()).performClick(); idle();
        androidx.fragment.app.Fragment detail=activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer);
        assertTrue(detail instanceof TellerPaymentFragment); assertEquals(id(1),detail.requireArguments().getString(TellerPaymentFragment.ARG_PAYMENT_ID));
        activity.getOnBackPressedDispatcher().onBackPressed(); idle();
        assertTrue(activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer) instanceof CashHistoryFragment);
    }
    @Test public void accountReplacementClearsRowsAndDisablesEveryReadAction() {
        launch(); complete(); actions.current=false;
        GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication();
        ((androidx.lifecycle.MutableLiveData<Long>)app.accountInvalidations()).setValue(500L); idle(); layout(390,844);
        assertEquals(2,list().getAdapter().getItemCount()); assertFalse(view(R.id.historyRefresh).isEnabled());
        assertEquals(View.VISIBLE,view(R.id.historyError).getVisibility()); assertEquals(View.GONE,view(R.id.historyMore).getVisibility());
    }
    @Test public void sharedPaymentSubscriptionRefreshesReadsAndClosesOnDetach() {
        launch(); complete(); assertEquals(Set.of("payments"),session.tables);
        RealtimeDataSource.Invalidation event=ReflectionHelpers.callConstructor(RealtimeDataSource.Invalidation.class,
            ReflectionHelpers.ClassParameter.from(long.class,1L),ReflectionHelpers.ClassParameter.from(Set.class,Set.of("payments")),
            ReflectionHelpers.ClassParameter.from(RealtimeDataSource.Reason.class,RealtimeDataSource.Reason.SNAPSHOT_REQUIRED));
        session.listener.onInvalidated(event); complete(); assertEquals(2,actions.reads);
        activity.navigate(new MoreFragment(),false); idle(); assertEquals(1,session.closed);
    }
    @Test @Config(shadows=SeryoInteractionTest.Api26FontScaling.class)
    public void smallLargeTextHistoryScrollsWithoutClippingIdentifiers() throws Exception {
        RuntimeEnvironment.setQualifiers("w320dp-h640dp-port-night-mdpi"); RuntimeEnvironment.setFontScale(2f); launch(); complete(); layout(320,480);
        assertTrue(list().getHeight()>0); assertTrue(list().canScrollVertically(1));
        TextView toolbar=(TextView)view(R.id.historyToolbar); assertTrue("Large-text toolbar needs a readable title width",toolbar.getWidth()>=200); assertTrue(toolbar.getLineCount()<=2);
        assertTrue(view(R.id.historyPaymentId).getWidth()<=280); assertTrue(view(R.id.historyRefresh).getHeight()>=48);
        capture("cash-history-small-dark.png",320,480);
    }
    @Test public void captureHistoryWithCanonicalSyntheticRowsInBothThemes() throws Exception {
        for(boolean dark:new boolean[]{false,true}) {
            RuntimeEnvironment.setQualifiers("w390dp-h844dp-port-"+(dark?"night":"notnight")+"-mdpi");
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(dark?androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES:androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
            launch(); complete(); layout(390,844); capture("cash-history-"+(dark?"dark":"light")+".png");
            controller.close(); controller=null; reset();
        }
    }
    private void launch() {
        GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication(); ReflectionHelpers.setField(app,"realtimeSession",session);
        CashHistoryViewModel vm=new CashHistoryViewModel(app,actions,work,ui);
        controller=Robolectric.buildActivity(MainActivity.class,new Intent(app,MainActivity.class).putExtra(MainActivity.EXTRA_PREVIEW,true)).setup().visible(); activity=controller.get(); idle();
        ViewModelProvider.Factory factory=new ViewModelProvider.Factory() { @Override public <T extends ViewModel> T create(Class<T> type) { return type.cast(vm); } };
        new ViewModelProvider(activity,factory).get(CashHistoryViewModel.class); activity.configureRoleNavigation(Role.TELLER);
        ((CapsuleNavigationView)activity.findViewById(R.id.bottomNavigation)).setSelectedItemId(R.id.nav_history); idle(); layout(390,844);
    }
    private void complete() { work.run(); ui.run(); idle(); layout(390,844); }
    private View view(int id) { View v=activity.findViewById(id); assertNotNull("Missing history control: "+id,v); return v; }
    private String text(int id) { return ((TextView)view(id)).getText().toString(); }
    private RecyclerView list() { return (RecyclerView)view(R.id.historyList); }
    private void idle() { Shadows.shadowOf(Looper.getMainLooper()).idle(); activity.getSupportFragmentManager().executePendingTransactions(); }
    private void layout(int w,int h) { View root=activity.findViewById(R.id.activityRoot); root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY)); root.layout(0,0,w,h); idle(); }
    private void capture(String name) throws Exception {
        capture(name,390,844);
    }
    private void capture(String name,int width,int height) throws Exception {
        android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);
        java.io.File dir=new java.io.File("build/outputs/screenshots"); assertTrue(dir.isDirectory()||dir.mkdirs());
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name))) {
            activity.findViewById(R.id.activityRoot).draw(new android.graphics.Canvas(image)); assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));
        } finally { image.recycle(); }
    }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static PaymentData.Record row(int n) { return new PaymentData.Record(id(90),id(n),id(50+n),PaymentMethod.CASH,PaymentStatus.PAID,new BigDecimal("35.00"),"PHP",2,id(91),"2026-10-21T01:00:00Z"); }
    private static final class Fake implements CashHistoryViewModel.Actions {
        boolean current=true; int reads; String lastCursor; Exception error; PaymentData.Page result=new PaymentData.Page(List.of(row(1)),null);
        public boolean allowed() { return true; } public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); } public void invalidate() {}
        public PaymentData.Page page(String after) throws Exception { ++reads; lastCursor=after; if(error!=null)throw error; return result; }
    }
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>(); public void execute(Runnable task) { tasks.add(task); }
        int size() { return tasks.size(); } void run() { assertFalse("Expected queued history read",tasks.isEmpty()); tasks.remove().run(); }
    }
    private static final class Session implements RealtimeDataSource.Session {
        Set<String> tables; RealtimeDataSource.Listener listener; int closed;
        public long generation() { return 1; } public void start() {} public void stop() {} public void refreshAuthorization() {} public void close() {}
        public RealtimeDataSource.Subscription subscribe(Set<String> tables,RealtimeDataSource.Listener listener) { this.tables=tables; this.listener=listener; return ()->++closed; }
    }
}
