package ph.edu.tip.goserve.data;

import android.os.Looper;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
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
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.ui.AuthActivity;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,
        shadows={AccountIntegrationTest.Store.class,AccountIntegrationTest.Server.class},instrumentedPackages={"ph.edu.tip.goserve.data"})
@LooperMode(LooperMode.Mode.PAUSED)
public final class AccountBindingTest {
    @Test public void foregroundRebindAndSignoutOwnTheActualRealtimeSession() throws Exception {
        new AccountIntegrationTest().setup();
        org.robolectric.util.ReflectionHelpers.setStaticField(androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.class,"_instance",null);
        GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication();
        AuthRepository auth=new AuthRepository(new SupabaseClient("https://example.supabase.co","fixture"),new SecureSessionStore(app));
        Field authField=GoServeApplication.class.getDeclaredField("auth"); authField.setAccessible(true); authField.set(app,auth);
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
        Field sourceField=GoServeApplication.class.getDeclaredField("realtime"); sourceField.setAccessible(true);
        RealtimeDataSource previous=(RealtimeDataSource)sourceField.get(app); if (previous!=null) previous.close(); sourceField.set(app,realtime);
        AccountIntegrationTest.Store.values.put("refresh_token","fixture-refresh");
        ActivityController<AuthActivity> activity=Robolectric.buildActivity(AuthActivity.class).setup().visible();
        try {
            await(() -> auth.profile()!=null,"Canonical account must restore."); app.bindCurrentAccount();
            RealtimeDataSource.Session first=app.realtimeSession(); assertNotNull("Ready accounts must bind a shared realtime session.",first);
            AuthRepository.SessionLease firstLease=auth.sessionLease();
            first.subscribe(Set.of("profiles"),new RealtimeDataSource.Listener() {
                public void onInvalidated(RealtimeDataSource.Invalidation invalidation) {}
                public void onStateChanged(RealtimeDataSource.State state) {}
            });
            await(() -> connections.get()==1,"A foreground observer must connect through the fake socket.");
            activity.pause().stop(); await(() -> cancellations.get()>0,"Background must stop the active transport.");
            activity.start().resume().visible(); await(() -> connections.get()==2,"Foreground must resume the retained observer.");
            auth.invalidateSession(); Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNull("Sign-out must close and remove the account's shared handle.",app.realtimeSession());
            AccountIntegrationTest.Server.profileId="student-b"; auth.signInPassword("other@example.com","verbatim"); app.bindCurrentAccount();
            assertNotNull(app.realtimeSession()); assertNotSame(first,app.realtimeSession());
            try { auth.requireCurrent(firstLease); fail("Old account leases must not cross a rebind."); } catch (IllegalStateException expected) {}
        } finally { activity.close(); realtime.close(); }
    }
    private void await(BooleanSupplier condition,String message) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        do { Shadows.shadowOf(Looper.getMainLooper()).idle(); if (condition.getAsBoolean()) return; Thread.sleep(10); } while (System.nanoTime()<until);
        assertTrue(message,condition.getAsBoolean());
    }
}
