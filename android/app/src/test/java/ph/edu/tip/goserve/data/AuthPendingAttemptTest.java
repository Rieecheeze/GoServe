package ph.edu.tip.goserve.data;

import android.content.Context;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,shadows={AuthPendingAttemptTest.Store.class,AccountIntegrationTest.Server.class},
        instrumentedPackages="ph.edu.tip.goserve.data")
public final class AuthPendingAttemptTest {
    private AuthRepository auth;
    @Before public void setup() {
        new AccountIntegrationTest().setup(); Store.values.clear(); Store.afterPendingRead=null;
        auth=new AuthRepository(new SupabaseClient("https://example.supabase.co","fixture"),new SecureSessionStore(RuntimeEnvironment.getApplication()));
    }
    @Test public void expiredCleanupPreservesANewerGoogleAttempt() throws Exception {
        Store.values.put("oauth_pending",expiredAttempt());
        AtomicReference<String> replacement=new AtomicReference<>();
        Store.afterPendingRead=()->{
            auth.googleAuthorizationUrl(); replacement.set(Store.values.get("oauth_pending"));
        };
        assertNull(auth.resumeSession(null));
        assertNotNull("A replacement Google attempt must be created.",replacement.get());
        assertTrue("Expired cleanup must preserve the exact replacement Google attempt.",replacement.get().equals(Store.values.get("oauth_pending")));
        assertEquals("Only the new Google provider-settings request may be dispatched.",1,AccountIntegrationTest.Server.calls);
    }
    @Test public void expiredAttemptAloneIsClearedWithoutARequest() throws Exception {
        Store.values.put("oauth_pending",expiredAttempt());
        assertNull(auth.resumeSession(null));
        assertNull(Store.values.get("oauth_pending")); assertEquals(0,AccountIntegrationTest.Server.calls);
    }
    @Test public void freshGoogleAttemptRemainsPendingWithoutAnExchange() throws Exception {
        auth.googleAuthorizationUrl(); String pending=Store.values.get("oauth_pending"); int calls=AccountIntegrationTest.Server.calls;
        assertNotNull(pending); assertNull(auth.resumeSession(null));
        assertTrue("Resuming must preserve a fresh pending Google attempt.",pending.equals(Store.values.get("oauth_pending")));
        assertEquals(calls,AccountIntegrationTest.Server.calls); assertNull(auth.profile());
    }
    private String expiredAttempt() throws Exception {
        return new JSONObject().put("verifier","expired-verifier").put("nonce","expired-nonce").put("flow","google")
                .put("started_at",org.robolectric.shadows.ShadowSystem.currentTimeMillis()-TimeUnit.MINUTES.toMillis(11)).toString();
    }
    @Implements(value=SecureSessionStore.class,isInAndroidSdk=false)
    public static final class Store {
        interface ReadHook { void run() throws Exception; }
        static final Map<String,String> values=new ConcurrentHashMap<>();
        static ReadHook afterPendingRead;
        @Implementation protected void __constructor__(Context context) {}
        @Implementation protected void put(String name,String value) { values.put(name,value); }
        @Implementation protected String get(String name) throws Exception {
            String value=values.get(name);
            if ("oauth_pending".equals(name)) {
                ReadHook hook=afterPendingRead; afterPendingRead=null;
                if (hook!=null) hook.run();
            }
            return value;
        }
        @Implementation protected void remove(String name) { values.remove(name); }
        @Implementation protected void clear() { values.clear(); }
    }
}
