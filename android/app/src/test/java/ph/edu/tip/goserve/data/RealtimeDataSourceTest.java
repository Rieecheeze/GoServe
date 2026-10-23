package ph.edu.tip.goserve.data;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class RealtimeDataSourceTest {
    private static final String URL = "https://example-project.supabase.co";
    private static final String KEY = "sb_publishable_test_only";
    private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhIn0.signature";
    private final FakeSockets sockets = new FakeSockets();
    private final FakeScheduler clock = new FakeScheduler();
    private final QueueExecutor callbacks = new QueueExecutor();
    private final QueueExecutor tokens = new QueueExecutor();
    private final Observer observer = new Observer();
    private RealtimeDataSource source;
    private RealtimeDataSource.Session session;
    private String token = JWT;

    @Before public void create() {
        source = new RealtimeDataSource(URL, KEY, callbacks, sockets, clock, tokens);
    }

    @After public void close() { source.close(); }

    @Test public void startQueuesAuthorizationInsteadOfDoingTokenWorkOnCaller() {
        int[] calls = {0};
        session = source.bindSession("a", () -> { calls[0]++; return JWT; });
        session.subscribe(tables("requests"), observer);
        session.start();
        assertEquals(0, calls[0]);
        assertEquals("Starting with an observer must queue authorization work", 1, tokens.size());
        assertEquals(0, sockets.all.size());
    }

    @Test public void joinsOnlyExplicitPublicTablesWithUserTokenAndValidatedReply() throws Exception {
        session = source.bindSession("account-a", () -> token);
        session.subscribe(tables("requests", "payments"), observer);
        session.start();
        assertEquals("Provider work must be queued off the caller", 0, sockets.all.size());
        tokens.runAll();
        FakeSocket socket = sockets.latest();
        assertEquals("https", socket.request.url().scheme());
        assertEquals("/realtime/v1/websocket", socket.request.url().encodedPath());
        assertEquals(KEY, socket.request.url().queryParameter("apikey"));
        assertEquals("1.0.0", socket.request.url().queryParameter("vsn"));
        assertFalse(socket.request.url().toString().contains(JWT));
        socket.open();
        callbacks.runAll();
        assertFalse(observer.states.contains(RealtimeDataSource.State.CONNECTED));
        JSONObject join = socket.frames("phx_join").get(0);
        assertEquals(join.getString("ref"), join.getString("join_ref"));
        assertTrue(join.getString("topic").startsWith("realtime:goserve:"));
        assertFalse(join.getString("topic").contains("account-a"));
        assertEquals(JWT, join.getJSONObject("payload").getString("access_token"));
        JSONArray changes = join.getJSONObject("payload").getJSONObject("config")
                .getJSONArray("postgres_changes");
        assertEquals(2, changes.length());
        Set<String> joined = new HashSet<>();
        for (int i = 0; i < changes.length(); i++) {
            JSONObject filter = changes.getJSONObject(i);
            assertEquals("public", filter.getString("schema"));
            assertEquals("*", filter.getString("event"));
            assertFalse(filter.has("filter"));
            joined.add(filter.getString("table"));
        }
        assertEquals(tables("payments", "requests"), joined);
        socket.reply(join, "wrong-ref", "ok", new JSONObject());
        callbacks.runAll();
        assertFalse(observer.states.contains(RealtimeDataSource.State.CONNECTED));
        socket.joinOk();
        flush();
        assertEquals(RealtimeDataSource.State.CONNECTED, observer.lastState());
        assertEquals(RealtimeDataSource.Reason.SNAPSHOT_REQUIRED, observer.changes.get(0).reason);
        assertEquals(tables("payments", "requests"), observer.changes.get(0).tables);
        assertEquals(session.generation(), observer.changes.get(0).generation);
        assertThrows(UnsupportedOperationException.class,
                () -> observer.changes.get(0).tables.add("secret"));
    }

    @Test public void rejectsArbitraryOriginsAndInjectedTableNamesWithoutEchoingInput() {
        for (String url : Arrays.asList("http://example-project.supabase.co", "https://attacker.test",
                "https://supabase.co.attacker.test", "https://u:p@example-project.supabase.co",
                URL + "/other", URL + "?key=private", URL + "#private", URL + ":444")) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> new RealtimeDataSource(url, KEY, callbacks, sockets, clock, tokens));
            assertFalse(failure.getMessage().contains(url));
        }
        session = source.bindSession("a", () -> JWT);
        for (String table : Arrays.asList("*", "public.requests", "requests;drop", "id=eq.private", "")) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> session.subscribe(tables(table), observer));
            assertFalse(failure.getMessage().contains("private"));
        }
        assertThrows(IllegalArgumentException.class, () -> session.subscribe(Collections.emptySet(), observer));
        assertEquals(0, sockets.all.size());
    }

    @Test public void heartbeatUsesPhoenixAndUniqueRefsAndMissingReplyReconnects() throws Exception {
        ready();
        FakeSocket socket = sockets.latest();
        clock.advance(19_900);
        tokens.runAll();
        JSONObject heartbeat = socket.frames("heartbeat").get(0);
        assertEquals("phoenix", heartbeat.getString("topic"));
        assertTrue(heartbeat.isNull("join_ref"));
        assertEquals(0, heartbeat.getJSONObject("payload").length());
        assertNotEquals(socket.frames("phx_join").get(0).getString("ref"), heartbeat.getString("ref"));
        socket.reply(heartbeat, heartbeat.getString("ref"), "ok", new JSONObject());
        clock.advance(20_000);
        tokens.runAll();
        assertEquals(2, socket.frames("heartbeat").size());
        assertNotEquals(heartbeat.getString("ref"), socket.frames("heartbeat").get(1).getString("ref"));
        clock.advance(20_000);
        assertFalse(socket.live);
        clock.advance(1_000);
        tokens.runAll();
        assertEquals(2, sockets.all.size());
    }

    @Test public void refreshUpdatesExistingChannelsWithoutJoiningOrWaitingForSuccessReply() throws Exception {
        ready();
        FakeSocket socket = sockets.latest();
        session.subscribe(tables("requests"), new Observer());
        assertEquals(1, socket.frames("phx_join").size());
        token = JWT + "-new";
        session.refreshAuthorization();
        assertEquals(0, socket.frames("access_token").size());
        tokens.runAll();
        JSONObject refresh = socket.frames("access_token").get(0);
        assertEquals(token, refresh.getJSONObject("payload").getString("access_token"));
        assertEquals(socket.frames("phx_join").get(0).getString("join_ref"), refresh.getString("join_ref"));
        session.refreshAuthorization();
        tokens.runAll();
        clock.advance(15_000);
        callbacks.runAll();
        assertEquals(1, socket.frames("access_token").size());
        assertEquals(1, socket.frames("phx_join").size());
        assertEquals(1, sockets.liveCount());
        assertEquals(RealtimeDataSource.State.CONNECTED, observer.lastState());
    }

    @Test public void providerFailureAndPublicKeyResultPauseUntilExplicitRecovery() {
        AtomicReference<String> result = new AtomicReference<>(null);
        session = source.bindSession("a", () -> {
            if (result.get() == null) throw new IOException("private " + JWT + KEY);
            return result.get();
        });
        session.subscribe(tables("requests"), observer);
        session.start();
        tokens.runAll();
        callbacks.runAll();
        assertEquals(RealtimeDataSource.State.AUTH_REQUIRED, observer.lastState());
        clock.advance(3_600_000);
        tokens.runAll();
        assertEquals(0, sockets.all.size());
        assertEquals(0, clock.pending());
        result.set(KEY);
        session.refreshAuthorization();
        tokens.runAll();
        assertEquals(0, sockets.all.size());
        result.set(JWT);
        session.refreshAuthorization();
        tokens.runAll();
        assertEquals(1, sockets.all.size());
    }

    @Test public void changedRowsAreOnlyCoalescedInvalidationsIncludingOlderAndSameTimestampEvents() throws Exception {
        ready();
        observer.changes.clear();
        FakeSocket socket = sockets.latest();
        socket.change("requests", "same-time", "one");
        socket.change("requests", "same-time", "one");
        socket.change("requests", "same-time", "two");
        socket.change("requests", "older-time", "three");
        flush();
        assertEquals(1, observer.changes.size());
        assertEquals(tables("requests"), observer.changes.get(0).tables);
        assertEquals(RealtimeDataSource.Reason.CHANGE, observer.changes.get(0).reason);
        socket.change("requests", "same-time", "one");
        flush();
        assertEquals("Exact duplicate must not trigger another reload", 1, observer.changes.size());
        socket.change("requests", "even-older-time", "four");
        flush();
        assertEquals(2, observer.changes.size());
    }

    @Test public void stalledCallbacksAndDuplicateBookkeepingRemainBounded() throws Exception {
        ready();
        observer.changes.clear();
        FakeSocket socket = sockets.latest();
        socket.change("requests", "time", "first");
        flush();
        for (int i = 0; i < 350; i++) {
            socket.change("requests", "time-" + i, "row-" + i);
            clock.advance(100);
            tokens.runAll();
        }
        assertTrue("A slow executor must not accumulate a callback for every event", callbacks.size() <= 1);
        callbacks.runAll();
        assertEquals(2, observer.changes.size());
        socket.change("requests", "time", "first");
        flush();
        assertEquals("The finite duplicate cache must evict old digests", 3, observer.changes.size());
    }

    @Test public void unexpectedDisconnectBacksOffAndRejoinAlwaysInvalidatesSnapshot() throws Exception {
        ready();
        observer.changes.clear();
        FakeSocket old = sockets.latest();
        old.fail();
        callbacks.runAll();
        assertEquals(RealtimeDataSource.State.RECONNECTING, observer.lastState());
        clock.advance(999);
        tokens.runAll();
        assertEquals(1, sockets.all.size());
        clock.advance(1);
        tokens.runAll();
        FakeSocket next = sockets.latest();
        next.open();
        old.joinOk();
        assertFalse(observer.lastState() == RealtimeDataSource.State.CONNECTED);
        next.joinOk();
        flush();
        assertEquals(1, observer.changes.size());
        assertEquals(RealtimeDataSource.Reason.SNAPSHOT_REQUIRED, observer.changes.get(0).reason);
        assertEquals(1, sockets.liveCount());
    }

    @Test public void repeatedFailuresUseExponentialBackoffCappedAtTenSeconds() throws Exception {
        ready();
        long[] delays = {1_000, 2_000, 4_000, 8_000, 10_000, 10_000};
        for (long delay : delays) {
            sockets.latest().fail();
            int before = sockets.all.size();
            clock.advance(delay - 1);
            tokens.runAll();
            assertEquals(before, sockets.all.size());
            clock.advance(1);
            tokens.runAll();
            assertEquals(before + 1, sockets.all.size());
            assertEquals(1, sockets.liveCount());
        }
    }

    @Test public void joinTimeoutAndRejectedSendRecoverInsteadOfStalling() throws Exception {
        begin(observer);
        FakeSocket socket = sockets.latest();
        socket.open();
        clock.advance(10_000);
        assertFalse(socket.live);
        clock.advance(1_000);
        tokens.runAll();
        socket = sockets.latest();
        socket.acceptSends = false;
        socket.open();
        assertFalse(socket.live);
        clock.advance(2_000);
        tokens.runAll();
        assertEquals(3, sockets.all.size());
    }

    @Test public void rateLimitedJoinWaitsAndInvalidAuthorizationDoesNotRetry() throws Exception {
        begin(observer);
        FakeSocket socket = sockets.latest();
        socket.open();
        socket.joinError("ClientJoinRateLimitReached: wait");
        clock.advance(9_999);
        tokens.runAll();
        assertEquals(1, sockets.all.size());
        clock.advance(1);
        tokens.runAll();
        socket = sockets.latest();
        socket.open();
        socket.joinError("JwtSignatureError: " + JWT);
        callbacks.runAll();
        assertEquals(RealtimeDataSource.State.AUTH_REQUIRED, observer.lastState());
        clock.advance(60_000);
        tokens.runAll();
        assertEquals(2, sockets.all.size());
        assertEquals(0, sockets.liveCount());
    }

    @Test public void postgresSystemErrorsSurfaceDegradationAndRecoveryWithoutDuplicateJoins() throws Exception {
        ready();
        FakeSocket socket = sockets.latest();
        observer.changes.clear();
        socket.system("postgres_changes", "error", "database error");
        callbacks.runAll();
        assertEquals(RealtimeDataSource.State.DEGRADED, observer.lastState());
        assertEquals(1, sockets.liveCount());
        socket.system("postgres_changes", "ok", "Subscribed to PostgreSQL");
        flush();
        assertEquals(RealtimeDataSource.State.CONNECTED, observer.lastState());
        assertEquals(RealtimeDataSource.Reason.SNAPSHOT_REQUIRED, observer.changes.get(0).reason);
        assertEquals(1, socket.frames("phx_join").size());
    }

    @Test public void expiredTokenSystemErrorRefreshesBeforeRejoin() throws Exception {
        ready();
        FakeSocket old = sockets.latest();
        token = JWT + "-fresh";
        old.system("system", "error", "Token has expired");
        assertFalse(old.live);
        clock.advance(1_000);
        assertEquals(1, sockets.all.size());
        tokens.runAll();
        FakeSocket next = sockets.latest();
        next.open();
        assertEquals(token, next.frames("phx_join").get(0).getJSONObject("payload").getString("access_token"));
    }

    @Test public void mismatchedSubscriptionIdsAndPhxErrorTriggerGuardedRecovery() throws Exception {
        ready();
        FakeSocket socket = sockets.latest();
        JSONObject join = socket.frames("phx_join").get(0);
        socket.message(frame(join, "postgres_changes", new JSONObject().put("ids", new JSONArray().put(999))
                .put("data", new JSONObject().put("schema", "public").put("table", "requests"))));
        assertFalse(socket.live);
        clock.advance(1_000);
        tokens.runAll();
        socket = sockets.latest();
        socket.open();
        socket.joinOk();
        socket.message(frame(socket.frames("phx_join").get(0), "phx_error", new JSONObject()));
        assertFalse(socket.live);
        assertEquals(1, clock.pending());
    }

    @Test public void oversizeAndBinaryPayloadsFailSafelyAndCannotReconnectForever() throws Exception {
        ready();
        sockets.latest().message("x".repeat(65_537));
        callbacks.runAll();
        assertEquals(RealtimeDataSource.State.FAILED, observer.lastState());
        clock.advance(60_000);
        assertEquals(0, sockets.liveCount());
        assertEquals(1, sockets.all.size());
        session.start();
        tokens.runAll();
        sockets.latest().listener.onMessage(sockets.latest(), ByteString.encodeUtf8("private"));
        callbacks.runAll();
        assertEquals(RealtimeDataSource.State.FAILED, observer.lastState());
    }

    @Test public void lastObserverCloseCancelsAllWorkAndSourceCloseIsTerminal() throws Exception {
        session = source.bindSession("a", () -> token);
        RealtimeDataSource.Subscription subscription = session.subscribe(tables("requests"), observer);
        session.start();
        tokens.runAll();
        sockets.latest().open();
        sockets.latest().joinOk();
        subscription.close();
        assertEquals(0, sockets.liveCount());
        assertEquals(0, clock.pending());
        clock.advance(600_000);
        tokens.runAll();
        callbacks.runAll();
        assertTrue(observer.changes.isEmpty());
        assertEquals(1, sockets.all.size());
        source.close();
        assertTrue(tokens.isShutdown());
        assertTrue(clock.isShutdown());
        assertThrows(IllegalStateException.class, () -> source.bindSession("b", () -> JWT));
    }

    @Test public void accountReplacementDropsQueuedEventsReconnectsAndOldSocketCallbacks() throws Exception {
        ready();
        FakeSocket old = sockets.latest();
        int before = observer.changes.size();
        old.change("requests", "time", "private-row");
        clock.advance(100);
        old.fail();
        RealtimeDataSource.Session accountA = session;
        Observer accountB = new Observer();
        session = source.bindSession("account-b", () -> JWT + "-b");
        session.subscribe(tables("requests"), accountB);
        session.start();
        tokens.runAll();
        assertTrue(session.generation() > accountA.generation());
        old.open();
        old.joinOk();
        old.change("requests", "late", "private-row");
        clock.advance(1_000);
        callbacks.runAll();
        assertEquals(before, observer.changes.size());
        assertTrue(accountB.changes.isEmpty());
        assertEquals(2, sockets.all.size());
        assertEquals(1, sockets.liveCount());
        assertThrows(IllegalStateException.class, () -> accountA.subscribe(tables("payments"), observer));
    }

    @Test public void inFlightProviderResultCannotAuthorizeAReplacementSession() {
        Observer next = new Observer();
        session = source.bindSession("same-account", () -> {
            RealtimeDataSource.Session replacement = source.bindSession("same-account", () -> JWT + "-b");
            replacement.subscribe(tables("payments"), next);
            replacement.start();
            return JWT;
        });
        long generation = session.generation();
        session.subscribe(tables("requests"), observer);
        session.start();
        tokens.runAll();
        assertEquals(1, sockets.all.size());
        assertEquals(0, observer.changes.size());
        assertTrue(source.bindSession("same-account", () -> JWT).generation() > generation);
        assertEquals(0, sockets.liveCount());
    }

    @Test public void listenerSelfCloseAndRapidStopStartKeepOneLiveConnection() throws Exception {
        AtomicReference<RealtimeDataSource.Subscription> own = new AtomicReference<>();
        session = source.bindSession("a", () -> token);
        own.set(session.subscribe(tables("requests"), new Observer() {
            @Override public void onInvalidated(RealtimeDataSource.Invalidation value) {
                super.onInvalidated(value);
                own.get().close();
            }
        }));
        session.subscribe(tables("requests"), observer);
        session.start();
        tokens.runAll();
        FakeSocket old = sockets.latest();
        old.open();
        old.joinOk();
        flush();
        assertEquals(1, observer.changes.size());
        session.stop();
        session.start();
        session.stop();
        session.start();
        tokens.runAll();
        old.fail();
        old.joinOk();
        assertEquals(1, sockets.liveCount());
        clock.advance(1_000);
        tokens.runAll();
        assertEquals(2, sockets.all.size());
    }

    @Test public void undrainedTokenWorkerHitsAuthorizationDeadlineInsteadOfStayingSilentlyStale() throws Exception {
        ready();
        clock.advance(19_900);
        assertEquals("The periodic provider is deliberately left undrained", 1, tokens.size());
        clock.advance(15_000);
        callbacks.runAll();
        assertEquals(RealtimeDataSource.State.AUTH_REQUIRED, observer.lastState());
        assertEquals(0, sockets.liveCount());
        tokens.runAll();
        assertEquals("A late cancelled provider must not restart the socket", 1, sockets.all.size());
        assertEquals(0, clock.pending());
    }

    @Test public void synchronousConnectingCallbackCannotQueueAuthorizationTwice() {
        RealtimeDataSource direct = new RealtimeDataSource(URL, KEY, Runnable::run, sockets, clock, tokens);
        try {
            AtomicReference<RealtimeDataSource.Session> owner = new AtomicReference<>();
            owner.set(direct.bindSession("a", () -> JWT));
            owner.get().subscribe(tables("requests"), new Observer() {
                @Override public void onStateChanged(RealtimeDataSource.State state) {
                    if (state == RealtimeDataSource.State.CONNECTING) owner.get().start();
                }
            });
            owner.get().start();
            assertEquals("Reentrant start must share the pending authorization", 1, tokens.size());
            tokens.runAll();
            assertEquals(1, sockets.all.size());
        } finally { direct.close(); }
    }

    @Test public void additionalChannelDoesNotReportConnectedUntilItsOwnJoinReply() throws Exception {
        ready();
        Observer payments = new Observer();
        session.subscribe(tables("payments"), payments);
        callbacks.runAll();
        assertFalse("An existing channel cannot authorize a new channel's connected status",
                payments.states.contains(RealtimeDataSource.State.CONNECTED));
        sockets.latest().joinOk();
        flush();
        assertEquals(RealtimeDataSource.State.CONNECTED, payments.lastState());
    }

    @Test public void tokenChangedDuringJoinIsSentWhenThatJoinSucceeds() throws Exception {
        begin(observer);
        FakeSocket socket = sockets.latest();
        socket.open();
        token = JWT + "-refreshed";
        session.refreshAuthorization();
        tokens.runAll();
        socket.joinOk();
        assertEquals(1, socket.frames("phx_join").size());
        assertEquals("Pending join must not stay on superseded authorization", token,
                socket.frames("access_token").get(0).getJSONObject("payload").getString("access_token"));
    }

    @Test public void serverExpiredTokenCannotLoopWhenProviderReturnsSameRejectedToken() throws Exception {
        ready();
        sockets.latest().system("system", "error", "Token has expired");
        clock.advance(1_000);
        tokens.runAll();
        callbacks.runAll();
        assertEquals(RealtimeDataSource.State.AUTH_REQUIRED, observer.lastState());
        clock.advance(60_000);
        tokens.runAll();
        assertEquals("A rejected token must never open another socket", 1, sockets.all.size());
    }

    @Test public void removingAnotherChannelPreservesAuthorizationFailureAndStoppedState() throws Exception {
        ready();
        RealtimeDataSource.Subscription payments = session.subscribe(tables("payments"), new Observer());
        sockets.latest().joinOk();
        flush();
        token = null;
        session.refreshAuthorization();
        tokens.runAll();
        callbacks.runAll();
        assertEquals(RealtimeDataSource.State.AUTH_REQUIRED, observer.lastState());
        payments.close();
        callbacks.runAll();
        assertEquals("Removing another channel must preserve the actionable authorization failure",
                RealtimeDataSource.State.AUTH_REQUIRED, observer.lastState());
        assertEquals(0, sockets.liveCount());
        assertEquals(0, clock.pending());
        assertEquals(0, tokens.size());
        token = JWT + "-recovered";
        session.refreshAuthorization();
        tokens.runAll();
        sockets.latest().open();
        sockets.latest().joinOk();
        flush();
        assertEquals(RealtimeDataSource.State.CONNECTED, observer.lastState());
        RealtimeDataSource.Subscription whileStopped = session.subscribe(tables("payments"), new Observer());
        session.stop();
        callbacks.runAll();
        whileStopped.close();
        callbacks.runAll();
        assertEquals("Removing a stopped channel must not imply connection work",
                RealtimeDataSource.State.STOPPED, observer.lastState());
        assertEquals(0, clock.pending());
        assertEquals(0, sockets.liveCount());
    }

    @Test public void alreadyRunningOldHeartbeatAndTokenPollCannotEnterRestartedSession() throws Exception {
        ready();
        List<Runnable> alreadyStarted = clock.startDueWithoutResuming(19_900);
        assertEquals("Hold the old heartbeat and token-poll delegates at the monitor boundary", 2, alreadyStarted.size());
        session.stop();
        session.start();
        tokens.runAll();
        FakeSocket replacement = sockets.latest();
        replacement.open();
        replacement.joinOk();
        flush();
        int replacementTimers = clock.pending();
        for (Runnable oldDelegate : alreadyStarted) oldDelegate.run();
        assertEquals("Old token polling must not queue authorization for the replacement", 0, tokens.size());
        assertEquals("Old heartbeat must not write to the replacement socket", 0, replacement.frames("heartbeat").size());
        assertEquals("Old delegates must not replace or add current timer handles", replacementTimers, clock.pending());
        clock.advance(19_900);
        tokens.runAll();
        assertEquals(1, replacement.frames("heartbeat").size());
        JSONObject heartbeat = replacement.frames("heartbeat").get(0);
        replacement.reply(heartbeat, heartbeat.getString("ref"), "ok", new JSONObject());
        clock.advance(20_000);
        tokens.runAll();
        assertEquals(2, replacement.frames("heartbeat").size());
        assertEquals(1, sockets.liveCount());
    }

    @Test public void reentrantStopCannotPublishObsoleteConnectingStateToLaterObserver() {
        RealtimeDataSource direct = new RealtimeDataSource(URL, KEY, Runnable::run, sockets, clock, tokens);
        try {
            AtomicReference<RealtimeDataSource.Session> owner = new AtomicReference<>();
            owner.set(direct.bindSession("a", () -> JWT));
            owner.get().subscribe(tables("requests"), new Observer() {
                @Override public void onStateChanged(RealtimeDataSource.State state) {
                    if (state == RealtimeDataSource.State.CONNECTING) owner.get().stop();
                }
            });
            Observer later = new Observer();
            owner.get().subscribe(tables("requests"), later);
            owner.get().start();
            assertEquals("Outer fanout must stop when a reentrant lifecycle change supersedes it",
                    RealtimeDataSource.State.STOPPED, later.lastState());
            assertFalse(later.states.contains(RealtimeDataSource.State.CONNECTING));
            assertEquals(0, sockets.liveCount());
            assertEquals(0, clock.pending());
        } finally { direct.close(); }
    }

    private void begin(Observer listener) {
        session = source.bindSession("account-a", () -> token);
        session.subscribe(tables("requests"), listener);
        session.start();
        tokens.runAll();
    }

    private void ready() throws Exception {
        begin(observer);
        sockets.latest().open();
        sockets.latest().joinOk();
        flush();
    }

    private void flush() { clock.advance(100); callbacks.runAll(); }
    private static Set<String> tables(String... names) { return new HashSet<>(Arrays.asList(names)); }

    private static JSONObject frame(JSONObject join, String event, JSONObject payload) throws Exception {
        return new JSONObject().put("topic", join.getString("topic")).put("event", event)
                .put("payload", payload).put("ref", JSONObject.NULL).put("join_ref", join.get("join_ref"));
    }

    private static class Observer implements RealtimeDataSource.Listener {
        final List<RealtimeDataSource.Invalidation> changes = new ArrayList<>();
        final List<RealtimeDataSource.State> states = new ArrayList<>();
        @Override public void onInvalidated(RealtimeDataSource.Invalidation value) { changes.add(value); }
        @Override public void onStateChanged(RealtimeDataSource.State state) { states.add(state); }
        RealtimeDataSource.State lastState() { return states.get(states.size() - 1); }
    }

    private static final class FakeSockets implements WebSocket.Factory {
        final List<FakeSocket> all = new ArrayList<>();
        @Override public WebSocket newWebSocket(Request request, WebSocketListener listener) {
            FakeSocket socket = new FakeSocket(request, listener);
            all.add(socket);
            return socket;
        }
        FakeSocket latest() { return all.get(all.size() - 1); }
        int liveCount() { return (int) all.stream().filter(socket -> socket.live).count(); }
    }

    private static final class FakeSocket implements WebSocket {
        final Request request;
        final WebSocketListener listener;
        final List<String> sent = new ArrayList<>();
        boolean live = true;
        boolean acceptSends = true;
        FakeSocket(Request request, WebSocketListener listener) { this.request = request; this.listener = listener; }
        @Override public Request request() { return request; }
        @Override public long queueSize() { return 0; }
        @Override public boolean send(String text) { if (!live || !acceptSends) return false; sent.add(text); return true; }
        @Override public boolean send(ByteString bytes) { throw new AssertionError("Unexpected binary send"); }
        @Override public boolean close(int code, String reason) { live = false; return true; }
        @Override public void cancel() { live = false; }
        void open() { listener.onOpen(this, null); }
        void fail() { live = false; listener.onFailure(this, new IOException("private details"), null); }
        void message(JSONObject object) { message(object.toString()); }
        void message(String text) { listener.onMessage(this, text); }
        List<JSONObject> frames(String event) throws Exception {
            List<JSONObject> result = new ArrayList<>();
            for (String text : sent) { JSONObject frame = new JSONObject(text); if (event.equals(frame.getString("event"))) result.add(frame); }
            return result;
        }
        void reply(JSONObject request, String ref, String status, JSONObject response) throws Exception {
            message(new JSONObject().put("topic", request.get("topic")).put("ref", ref)
                    .put("join_ref", request.opt("join_ref")).put("event", "phx_reply")
                    .put("payload", new JSONObject().put("status", status).put("response", response)));
        }
        void joinOk() throws Exception {
            for (JSONObject join : frames("phx_join")) {
                JSONArray expected = join.getJSONObject("payload").getJSONObject("config").getJSONArray("postgres_changes");
                JSONArray response = new JSONArray();
                for (int i = 0; i < expected.length(); i++) response.put(new JSONObject(expected.getJSONObject(i).toString()).put("id", 100 + i));
                reply(join, join.getString("ref"), "ok", new JSONObject().put("postgres_changes", response));
            }
        }
        void joinError(String reason) throws Exception {
            JSONObject join = frames("phx_join").get(0);
            reply(join, join.getString("ref"), "error", new JSONObject().put("reason", reason));
        }
        void change(String table, String timestamp, String id) throws Exception {
            JSONObject join = frames("phx_join").get(0);
            message(frame(join, "postgres_changes", new JSONObject().put("ids", new JSONArray().put(100))
                    .put("data", new JSONObject().put("schema", "public").put("table", table)
                            .put("commit_timestamp", timestamp).put("type", "UPDATE")
                            .put("record", new JSONObject().put("id", id).put("private_code", "do-not-expose")))));
        }
        void system(String extension, String status, String message) throws Exception {
            message(frame(frames("phx_join").get(0), "system", new JSONObject()
                    .put("extension", extension).put("status", status).put("message", message)));
        }
    }

    private static class QueueExecutor extends AbstractExecutorService {
        final Deque<Runnable> queue = new ArrayDeque<>();
        boolean stopped;
        @Override public void execute(Runnable action) { if (stopped) throw new java.util.concurrent.RejectedExecutionException(); queue.add(action); }
        void runAll() { int guard = 0; while (!queue.isEmpty()) { if (++guard > 1000) throw new AssertionError("Unbounded work"); queue.remove().run(); } }
        int size() { return queue.size(); }
        @Override public void shutdown() { stopped = true; }
        @Override public List<Runnable> shutdownNow() { stopped = true; List<Runnable> pending = new ArrayList<>(queue); queue.clear(); return pending; }
        @Override public boolean isShutdown() { return stopped; }
        @Override public boolean isTerminated() { return stopped && queue.isEmpty(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
    }

    private static final class FakeScheduler extends QueueExecutor implements ScheduledExecutorService {
        final PriorityQueue<TimedTask<?>> scheduled = new PriorityQueue<>(Comparator
                .comparingLong((TimedTask<?> task) -> task.due).thenComparingLong(task -> task.sequence));
        long now;
        long sequence;
        @Override public void execute(Runnable action) { schedule(action, 0, TimeUnit.MILLISECONDS); }
        void advance(long millis) {
            long until = now + millis;
            int guard = 0;
            while (!scheduled.isEmpty() && scheduled.peek().due <= until) {
                if (++guard > 10_000) throw new AssertionError("Unbounded scheduled work");
                TimedTask<?> task = scheduled.remove();
                now = task.due;
                task.run();
            }
            now = until;
        }
        int pending() { return (int) scheduled.stream().filter(task -> !task.isCancelled()).count(); }
        List<Runnable> startDueWithoutResuming(long millis) {
            long until = now + millis;
            List<Runnable> started = new ArrayList<>();
            while (!scheduled.isEmpty() && scheduled.peek().due <= until) {
                TimedTask<?> task = scheduled.remove();
                if (!task.isCancelled()) started.add(task.startedDelegate());
            }
            now = until;
            return started;
        }
        @Override public ScheduledFuture<?> schedule(Runnable action, long delay, TimeUnit unit) {
            return schedule(java.util.concurrent.Executors.callable(action), delay, unit);
        }
        @Override public <V> ScheduledFuture<V> schedule(Callable<V> action, long delay, TimeUnit unit) {
            if (stopped) throw new java.util.concurrent.RejectedExecutionException();
            TimedTask<V> task = new TimedTask<>(action, now + unit.toMillis(delay), sequence++);
            scheduled.add(task);
            return task;
        }
        @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable action, long delay, long period, TimeUnit unit) { throw new UnsupportedOperationException(); }
        @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable action, long delay, long period, TimeUnit unit) { throw new UnsupportedOperationException(); }
        @Override public List<Runnable> shutdownNow() { for (TimedTask<?> task : new ArrayList<>(scheduled)) task.cancel(false); scheduled.clear(); return super.shutdownNow(); }
        private final class TimedTask<V> extends FutureTask<V> implements ScheduledFuture<V> {
            final long due;
            final long sequence;
            final Callable<V> action;
            TimedTask(Callable<V> action, long due, long sequence) {
                super(action);
                this.action = action;
                this.due = due;
                this.sequence = sequence;
            }
            Runnable startedDelegate() {
                // The delegate already entered run() before cancel(false), then waited for the production monitor.
                return () -> {
                    try { action.call(); }
                    catch (RuntimeException | Error failure) { throw failure; }
                    catch (Exception failure) { throw new AssertionError(failure); }
                };
            }
            @Override public long getDelay(TimeUnit unit) { return unit.convert(due - now, TimeUnit.MILLISECONDS); }
            @Override public int compareTo(Delayed other) { return Long.compare(getDelay(TimeUnit.MILLISECONDS), other.getDelay(TimeUnit.MILLISECONDS)); }
            @Override public boolean cancel(boolean interrupt) { boolean cancelled = super.cancel(interrupt); scheduled.remove(this); return cancelled; }
        }
    }
}
