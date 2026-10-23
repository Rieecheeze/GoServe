package ph.edu.tip.goserve.data;

import android.content.Intent;
import android.os.Looper;
import androidx.lifecycle.ViewModelProvider;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import okhttp3.Request;
import okhttp3.WebSocket;
import okio.ByteString;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.ui.AuthActivity;
import ph.edu.tip.goserve.ui.MainActivity;
import ph.edu.tip.goserve.ui.OfficeViewModel;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,
        shadows={AccountIntegrationTest.Store.class,AccountIntegrationTest.Server.class},instrumentedPackages={"ph.edu.tip.goserve.data"})
@LooperMode(LooperMode.Mode.PAUSED)
public final class AccountMainInvalidationTest {
    @Test public void terminalRefreshWhileMainStaysResumedClearsShellAndRoutesToSignin() throws Exception {
        new AccountIntegrationTest().setup();
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class,"_instance",null);
        GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication();
        SupabaseClient client=new SupabaseClient("https://example.supabase.co","fixture");
        AuthRepository auth=new AuthRepository(client,new SecureSessionStore(app));
        replace(app,"auth",auth); replace(app,"client",client); replace(app,"offices",new OfficeRepository(client,auth));
        AtomicInteger connections=new AtomicInteger(),cancellations=new AtomicInteger();
        WebSocket.Factory sockets=(request,listener) -> {
            connections.incrementAndGet();
            return new WebSocket() {
                public Request request() { return request; }
                public long queueSize() { return 0; }
                public boolean send(String text) { return true; }
                public boolean send(ByteString bytes) { return true; }
                public boolean close(int code,String reason) { cancellations.incrementAndGet(); return true; }
                public void cancel() { cancellations.incrementAndGet(); }
            };
        };
        RealtimeDataSource realtime=new RealtimeDataSource("https://example.supabase.co","fixture",Runnable::run,sockets,
                Executors.newSingleThreadScheduledExecutor(),Executors.newSingleThreadExecutor());
        Field source=GoServeApplication.class.getDeclaredField("realtime"); source.setAccessible(true);
        ((RealtimeDataSource)source.get(app)).close(); source.set(app,realtime);
        auth.signInPassword("ana@example.com","verbatim"); app.bindCurrentAccount();
        ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible();
        try {
            MainActivity activity=controller.get(); OfficeViewModel offices=new ViewModelProvider(activity).get(OfficeViewModel.class);
            await(() -> !offices.state().getValue().loading,"Initial offices must finish loading.");
            assertEquals(androidx.lifecycle.Lifecycle.State.RESUMED,activity.getLifecycle().getCurrentState());
            assertNotNull(activity.profile()); assertFalse(activity.isFinishing());
            app.realtimeSession().subscribe(Set.of("profiles"),new RealtimeDataSource.Listener() {
                public void onInvalidated(RealtimeDataSource.Invalidation invalidation) {}
                public void onStateChanged(RealtimeDataSource.State state) {}
            });
            await(() -> connections.get()>0,"The foreground account must own an active transport.");
            org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofHours(2));
            AccountIntegrationTest.Server.refreshStatus=401;
            offices.reload(false);
            await(activity::isFinishing,"Terminal refresh must route a still-resumed Main to Sign in immediately.");
            Intent next=Shadows.shadowOf(activity).getNextStartedActivity();
            assertNotNull(next); assertEquals(AuthActivity.class.getName(),next.getComponent().getClassName());
            assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK,
                    next.getFlags()&(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            assertNull("The cached signed-in profile must be cleared.",activity.profile());
            assertFalse("Protected account content must stop being visible before navigation completes.",activity.findViewById(R.id.mainContainer).isShown());
            assertNull(auth.profile()); assertNull(AccountIntegrationTest.Store.values.get("refresh_token"));
            assertNull("Routing must preserve application realtime cleanup.",app.realtimeSession());
            await(() -> cancellations.get()>0,"Invalidation must close the previous account transport.");
            assertNull("Invalidation must route once.",Shadows.shadowOf(activity).getNextStartedActivity());
        } finally { controller.close(); realtime.close(); }
    }
    private void replace(GoServeApplication app,String name,Object value) throws Exception {
        Field field=GoServeApplication.class.getDeclaredField(name); field.setAccessible(true); field.set(app,value);
    }
    private void await(BooleanSupplier condition,String message) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        do { Shadows.shadowOf(Looper.getMainLooper()).idle(); if (condition.getAsBoolean()) return; Thread.sleep(10); } while (System.nanoTime()<until);
        assertTrue(message,condition.getAsBoolean());
    }
}
