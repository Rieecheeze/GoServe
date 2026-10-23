package ph.edu.tip.goserve.data;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class RealtimeDataSource implements AutoCloseable {
    private static final AtomicLong GENERATIONS = new AtomicLong();
    private static final int MAX_MESSAGE_BYTES = 64 * 1024;
    private static final int MAX_OBSERVERS = 128;
    private static final int MAX_TABLES = 64;
    private static final int MAX_DIGESTS = 256;
    private final Object lock = new Object();
    private final Request request;
    private final String publicKey;
    private final Executor callbacks;
    private final WebSocket.Factory sockets;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService tokens;
    private final OkHttpClient ownedClient;
    private SessionImpl current;
    private boolean closed;
    private long references;

    /** The callback executor should dispatch to the application's main thread. */
    public RealtimeDataSource(String url, String key, Executor callbacks) {
        this(url, key, callbacks, new OkHttpClient.Builder().followRedirects(false)
                .followSslRedirects(false).retryOnConnectionFailure(false).build(),
                newScheduler(), newTokenExecutor());
    }

    RealtimeDataSource(String url, String key, Executor callbacks, WebSocket.Factory sockets,
                       ScheduledExecutorService scheduler, ExecutorService tokens) {
        this.request = endpoint(url, key);
        this.publicKey = key;
        this.callbacks = Objects.requireNonNull(callbacks, "Callback executor required.");
        this.sockets = Objects.requireNonNull(sockets, "Socket factory required.");
        this.scheduler = Objects.requireNonNull(scheduler, "Scheduler required.");
        this.tokens = Objects.requireNonNull(tokens, "Token executor required.");
        this.ownedClient = sockets instanceof OkHttpClient ? (OkHttpClient) sockets : null;
    }

    public Session bindSession(String identity, TokenProvider provider) {
        if (identity == null || identity.trim().isEmpty() || identity.length() > 256 || provider == null) {
            throw new IllegalArgumentException("Session identity and token provider required.");
        }
        synchronized (lock) {
            if (closed) throw new IllegalStateException("Realtime source is closed.");
            if (current != null) current.dispose();
            current = new SessionImpl(GENERATIONS.incrementAndGet(), provider);
            return current;
        }
    }

    @Override public void close() {
        synchronized (lock) {
            if (closed) return;
            if (current != null) current.dispose();
            current = null;
            closed = true;
            scheduler.shutdownNow();
            tokens.shutdownNow();
            if (ownedClient != null) {
                ownedClient.dispatcher().cancelAll();
                ownedClient.dispatcher().executorService().shutdown();
                ownedClient.connectionPool().evictAll();
            }
        }
    }

    public interface TokenProvider { String currentToken() throws Exception; }
    public interface Session extends AutoCloseable {
        long generation();
        Subscription subscribe(Set<String> tables, Listener listener);
        void start();
        void stop();
        void refreshAuthorization();
        @Override void close();
    }
    public interface Subscription extends AutoCloseable { @Override void close(); }
    public interface Listener {
        void onInvalidated(Invalidation invalidation);
        void onStateChanged(State state);
    }
    public static final class Invalidation {
        public final long generation;
        public final Set<String> tables;
        public final Reason reason;
        private Invalidation(long generation, Set<String> tables, Reason reason) {
            this.generation = generation;
            this.tables = Collections.unmodifiableSet(new LinkedHashSet<>(tables));
            this.reason = reason;
        }
    }
    public enum Reason { CHANGE, SNAPSHOT_REQUIRED }
    public enum State {
        STOPPED, CONNECTING, CONNECTED, RECONNECTING, DEGRADED, AUTH_REQUIRED, FAILED, CLOSED
    }

    private final class SessionImpl implements Session {
        final long generation;
        final Map<Set<String>, Channel> channels = new LinkedHashMap<>();
        final Set<Registration> observers = new LinkedHashSet<>();
        final Set<String> digests = new LinkedHashSet<>();
        TokenProvider provider;
        State state = State.STOPPED;
        boolean started;
        boolean disposed;
        boolean paused;
        boolean opened;
        long epoch;
        long authorization;
        long tokenVersion;
        int failures;
        String token;
        String rejectedTokenDigest;
        String heartbeatRef;
        WebSocket socket;
        Future<?> tokenWork;
        ScheduledFuture<?> tokenDeadline;
        ScheduledFuture<?> poll;
        ScheduledFuture<?> heartbeat;
        ScheduledFuture<?> reconnect;
        ScheduledFuture<?> openingDeadline;
        ScheduledFuture<?> flush;

        SessionImpl(long generation, TokenProvider provider) {
            this.generation = generation;
            this.provider = provider;
        }
        @Override public long generation() { return generation; }
        boolean valid() { return !closed && !disposed && current == this; }
        boolean active() { return valid() && started && !paused && !observers.isEmpty(); }
        boolean connected(long expected, WebSocket candidate) {
            return active() && epoch == expected && socket == candidate;
        }
        void requireCurrent() {
            if (!valid()) throw new IllegalStateException("Realtime session is closed.");
        }

        @Override public Subscription subscribe(Set<String> tables, Listener listener) {
            Set<String> selected = tableSet(tables);
            if (listener == null) throw new IllegalArgumentException("Invalidation listener required.");
            synchronized (lock) {
                requireCurrent();
                if (observers.size() >= MAX_OBSERVERS) throw new IllegalStateException("Realtime observer limit reached.");
                Channel channel = channels.get(selected);
                if (channel == null) {
                    channel = new Channel(selected, "realtime:goserve:" + nextRef());
                    channels.put(selected, channel);
                }
                Registration registration = new Registration(this, channel, listener);
                observers.add(registration);
                channel.observers.add(registration);
                registration.pendingState = state;
                if (active() && !channel.joined && state == State.CONNECTED) setState(State.CONNECTING);
                else queueCallback(registration);
                if (active()) {
                    if (socket != null && opened && channel.joinRef == null) join(channel);
                    else if (socket == null && reconnect == null) requestToken();
                    if (channel.joined) invalidate(registration, selected, Reason.SNAPSHOT_REQUIRED);
                }
                return registration;
            }
        }

        @Override public void start() {
            synchronized (lock) {
                requireCurrent();
                if (paused) failures = 0;
                started = true;
                paused = false;
                if (active() && socket == null && reconnect == null) requestToken();
            }
        }
        @Override public void stop() {
            synchronized (lock) {
                if (!valid()) return;
                started = false;
                paused = false;
                tearDown();
                setState(State.STOPPED);
            }
        }
        @Override public void refreshAuthorization() {
            synchronized (lock) {
                requireCurrent();
                paused = false;
                if (active() && reconnect == null) requestToken();
            }
        }
        @Override public void close() {
            synchronized (lock) { if (!disposed) dispose(); }
        }
        void dispose() {
            disposed = true;
            tearDown();
            provider = null;
            rejectedTokenDigest = null;
            for (Registration registration : new ArrayList<>(observers)) registration.clear();
            observers.clear();
            channels.clear();
            state = State.CLOSED;
        }

        void tearDown() {
            epoch++;
            authorization++;
            cancel(tokenWork); tokenWork = null;
            if (tokens instanceof ThreadPoolExecutor) ((ThreadPoolExecutor) tokens).purge();
            cancel(tokenDeadline); tokenDeadline = null;
            cancel(poll); poll = null;
            cancel(heartbeat); heartbeat = null;
            cancel(reconnect); reconnect = null;
            cancel(openingDeadline); openingDeadline = null;
            cancel(flush); flush = null;
            WebSocket previous = socket;
            socket = null;
            opened = false;
            token = null;
            heartbeatRef = null;
            digests.clear();
            for (Channel channel : channels.values()) {
                cancel(channel.deadline); channel.deadline = null;
                channel.joinRef = null;
                channel.joined = false;
                channel.degraded = false;
                channel.ids.clear();
            }
            for (Registration registration : observers) registration.clearPending();
            if (previous != null) previous.cancel();
        }
        void pause(State failure) {
            tearDown();
            paused = true;
            setState(failure);
        }
        void retry(long minimumDelay) {
            if (!active()) return;
            tearDown();
            long delay = Math.max(minimumDelay, Math.min(10_000L, 1_000L << Math.min(failures++, 4)));
            long attempt = epoch;
            setState(State.RECONNECTING);
            if (!active() || epoch != attempt) return;
            reconnect = schedule(() -> {
                if (!active() || epoch != attempt) return;
                reconnect = null;
                requestToken();
            }, delay);
        }

        void requestToken() {
            if (!active() || tokenWork != null) return;
            long attempt = epoch;
            long requestId = ++authorization;
            FutureTask<Void> work = new FutureTask<>(() -> {
                TokenProvider selected;
                synchronized (lock) {
                    if (!active() || epoch != attempt || authorization != requestId) return null;
                    selected = provider;
                }
                String result = null;
                try { result = selected.currentToken(); }
                catch (Exception ignored) { /* Never expose credential-bearing provider exceptions. */ }
                synchronized (lock) {
                    if (!active() || epoch != attempt || authorization != requestId) return null;
                    tokenWork = null;
                    cancel(tokenDeadline); tokenDeadline = null;
                    if (!userToken(result)) { pause(State.AUTH_REQUIRED); return null; }
                    boolean changed = !result.equals(token);
                    rejectedTokenDigest = null;
                    if (changed) tokenVersion++;
                    token = result;
                    if (socket == null) openSocket();
                    else if (changed) {
                        for (Channel channel : new ArrayList<>(channels.values())) {
                            if (!active() || epoch != attempt) break;
                            if (channel.joined && send(channel.topic, "access_token",
                                    object("access_token", token), nextRef(), channel.joinRef)) {
                                channel.authorizationVersion = tokenVersion;
                            }
                        }
                    }
                    if (active() && epoch == attempt && poll == null) {
                        poll = schedule(() -> { poll = null; requestToken(); }, 20_000);
                    }
                }
                return null;
            });
            tokenWork = work;
            tokenDeadline = schedule(() -> {
                if (active() && epoch == attempt && authorization == requestId && tokenWork != null) pause(State.AUTH_REQUIRED);
            }, 15_000);
            if (socket == null && state != State.RECONNECTING) setState(State.CONNECTING);
            if (!active() || epoch != attempt || authorization != requestId) return;
            try { tokens.execute(work); }
            catch (RejectedExecutionException unavailable) { pause(State.FAILED); }
        }
        boolean userToken(String candidate) {
            if (candidate == null || candidate.length() > 16_384 || candidate.equals(publicKey)
                    || candidate.startsWith("sb_")) return false;
            return candidate.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
                    && !ByteString.encodeUtf8(candidate).sha256().hex().equals(rejectedTokenDigest);
        }
        void openSocket() {
            long attempt = epoch;
            try {
                socket = sockets.newWebSocket(request, new SocketEvents(this, attempt));
                openingDeadline = schedule(() -> { if (active() && epoch == attempt) retry(0); }, 10_000);
            } catch (RuntimeException unavailable) { retry(0); }
        }
        void opened(WebSocket candidate, long attempt) {
            if (!connected(attempt, candidate)) { candidate.cancel(); return; }
            opened = true;
            cancel(openingDeadline); openingDeadline = null;
            for (Channel channel : new ArrayList<>(channels.values())) {
                if (!connected(attempt, candidate)) return;
                join(channel);
            }
            if (connected(attempt, candidate)) heartbeat = schedule(this::beat, 20_000);
        }
        void join(Channel channel) {
            if (!active() || !opened || token == null || channel.joinRef != null) return;
            long attempt = epoch;
            channel.joinRef = nextRef();
            channel.authorizationVersion = tokenVersion;
            JSONArray changes = new JSONArray();
            for (String table : channel.tables) changes.put(object("event", "*", "schema", "public", "table", table));
            JSONObject config = object("postgres_changes", changes, "presence", object("enabled", false),
                    "broadcast", object("ack", false, "self", false), "private", false);
            if (send(channel.topic, "phx_join", object("config", config, "access_token", token), channel.joinRef, channel.joinRef)) {
                channel.deadline = schedule(() -> {
                    if (active() && epoch == attempt && !channel.joined) retry(0);
                }, 10_000);
            }
        }
        boolean send(String topic, String event, JSONObject payload, String ref, String joinRef) {
            if (!active() || socket == null) return false;
            JSONObject frame = object("topic", topic, "event", event, "payload", payload, "ref", ref, "join_ref", joinRef);
            if (socket.queueSize() > 256 * 1024 || !socket.send(frame.toString())) { retry(0); return false; }
            return true;
        }
        void beat() {
            heartbeat = null;
            if (!active() || !opened) return;
            if (heartbeatRef != null) { retry(0); return; }
            heartbeatRef = nextRef();
            if (send("phoenix", "heartbeat", new JSONObject(), heartbeatRef, null)) heartbeat = schedule(this::beat, 20_000);
        }

        void message(String text) {
            if (text.length() > MAX_MESSAGE_BYTES || text.getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES) {
                pause(State.FAILED);
                return;
            }
            try {
                JSONObject frame = new JSONObject(text);
                String topic = frame.optString("topic", "");
                String event = frame.optString("event", "");
                JSONObject payload = frame.optJSONObject("payload");
                if (payload == null) { pause(State.FAILED); return; }
                if ("phoenix".equals(topic)) {
                    if ("phx_reply".equals(event) && heartbeatRef != null
                            && heartbeatRef.equals(frame.optString("ref")) && frame.isNull("join_ref")) {
                        if ("ok".equals(payload.optString("status"))) heartbeatRef = null;
                        else retry(0);
                    }
                    return;
                }
                Channel channel = null;
                for (Channel candidate : channels.values()) if (candidate.topic.equals(topic)) channel = candidate;
                if (channel == null || channel.joinRef == null) return;
                if (!frame.isNull("join_ref") && !channel.joinRef.equals(frame.optString("join_ref"))) return;
                switch (event) {
                    case "phx_reply": reply(channel, frame, payload); break;
                    case "postgres_changes": change(channel, payload); break;
                    case "system": system(channel, payload); break;
                    case "phx_error":
                    case "phx_close": retry(0); break;
                    default: break;
                }
            } catch (JSONException | IllegalArgumentException malformed) { pause(State.FAILED); }
        }

        void reply(Channel channel, JSONObject frame, JSONObject payload) throws JSONException {
            if (channel.joined || !channel.joinRef.equals(frame.optString("ref"))
                    || !channel.joinRef.equals(frame.optString("join_ref"))) return;
            JSONObject response = payload.optJSONObject("response");
            if (!"ok".equals(payload.optString("status"))) {
                serverError(response == null ? "" : response.optString("reason", ""));
                return;
            }
            JSONArray entries = response == null ? null : response.optJSONArray("postgres_changes");
            if (entries == null || entries.length() != channel.tables.size()) { retry(0); return; }
            Map<Long, String> ids = new HashMap<>();
            Set<String> matched = new LinkedHashSet<>();
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                String table = entry.optString("table", "");
                Object id = entry.opt("id");
                if (!"public".equals(entry.optString("schema")) || !"*".equals(entry.optString("event"))
                        || !channel.tables.contains(table) || !matched.add(table) || !(id instanceof Number)
                        || ((Number) id).longValue() < 0 || ((Number) id).doubleValue() != ((Number) id).longValue()
                        || ids.put(((Number) id).longValue(), table) != null) { retry(0); return; }
            }
            channel.ids.putAll(ids);
            channel.joined = true;
            cancel(channel.deadline); channel.deadline = null;
            if (channel.authorizationVersion != tokenVersion) {
                if (!send(channel.topic, "access_token", object("access_token", token), nextRef(), channel.joinRef)) return;
                channel.authorizationVersion = tokenVersion;
            }
            updateJoinedState();
            for (Registration registration : new ArrayList<>(channel.observers)) {
                invalidate(registration, channel.tables, Reason.SNAPSHOT_REQUIRED);
            }
        }

        void change(Channel channel, JSONObject payload) throws JSONException {
            if (!channel.joined) return;
            JSONObject data = payload.optJSONObject("data");
            if (data == null || !"public".equals(data.optString("schema"))) return;
            String table = data.optString("table", "");
            if (!channel.tables.contains(table)) return;
            JSONArray ids = payload.optJSONArray("ids");
            if (ids == null || ids.length() == 0 || ids.length() > channel.tables.size()) { retry(0); return; }
            for (int i = 0; i < ids.length(); i++) {
                if (!table.equals(channel.ids.get(ids.getLong(i)))) { retry(0); return; }
            }
            String digest = ByteString.encodeUtf8(channel.topic + "\n" + data).sha256().hex();
            if (!digests.add(digest)) return;
            if (digests.size() > MAX_DIGESTS) digests.remove(digests.iterator().next());
            for (Registration registration : new ArrayList<>(channel.observers)) {
                invalidate(registration, Collections.singleton(table), Reason.CHANGE);
            }
        }

        void system(Channel channel, JSONObject payload) {
            String extension = payload.optString("extension", "");
            if ("postgres_changes".equals(extension)) {
                channel.degraded = !"ok".equals(payload.optString("status"));
                updateJoinedState();
                if (!channel.degraded && channel.joined) {
                    for (Registration registration : new ArrayList<>(channel.observers)) {
                        invalidate(registration, channel.tables, Reason.SNAPSHOT_REQUIRED);
                    }
                }
            } else if ("error".equals(payload.optString("status"))) {
                serverError(payload.optString("message", ""));
            }
        }

        void serverError(String reason) {
            if ((reason.startsWith("InvalidJWTExpiration") && reason.contains("expired"))
                    || reason.contains("Token has expired")) {
                if (token != null) rejectedTokenDigest = ByteString.encodeUtf8(token).sha256().hex();
                retry(0);
            } else if (reason.startsWith("MalformedJWT") || reason.startsWith("JwtSignatureError")
                    || reason.startsWith("InvalidJWTExpiration")
                    || reason.startsWith("Unauthorized") || reason.startsWith("Fields `role")) {
                pause(State.AUTH_REQUIRED);
            } else if (reason.startsWith("TopicNameRequired") || reason.startsWith("TenantNotFound")
                    || reason.startsWith("RealtimeDisabledFor")) {
                pause(State.FAILED);
            } else if (reason.contains("RateLimit") || reason.contains("rate limit")
                    || reason.startsWith("Too many messages") || reason.startsWith("Too many presence")) {
                retry(10_000);
            } else {
                // Reconnect obtains current authorization, including expired-token recovery.
                retry(0);
            }
        }

        void updateJoinedState() {
            if (!active() || !opened || socket == null) return;
            boolean joined = !channels.isEmpty();
            boolean degraded = false;
            for (Channel channel : channels.values()) {
                joined &= channel.joined;
                degraded |= channel.degraded;
            }
            if (joined) {
                failures = 0;
                setState(degraded ? State.DEGRADED : State.CONNECTED);
            } else if (degraded) setState(State.DEGRADED);
            else if (state != State.RECONNECTING) setState(State.CONNECTING);
        }

        void setState(State next) {
            if (!valid() || state == next) return;
            long transitionEpoch = epoch;
            state = next;
            for (Registration registration : new ArrayList<>(observers)) {
                if (!valid() || epoch != transitionEpoch || state != next) return;
                registration.pendingState = next;
                queueCallback(registration);
            }
        }
        void invalidate(Registration registration, Set<String> tables, Reason reason) {
            if (!active() || registration.listener == null) return;
            registration.pendingTables.addAll(tables);
            if (registration.pendingReason == null || reason == Reason.SNAPSHOT_REQUIRED) registration.pendingReason = reason;
            if (flush == null) {
                long attempt = epoch;
                flush = schedule(() -> {
                    if (!active() || epoch != attempt) return;
                    flush = null;
                    for (Registration candidate : new ArrayList<>(observers)) {
                        if (!candidate.pendingTables.isEmpty()) queueCallback(candidate);
                    }
                }, 100);
            }
        }
        void queueCallback(Registration registration) {
            if (registration.queued || registration.listener == null || !valid()) return;
            registration.queued = true;
            try {
                callbacks.execute(() -> {
                    synchronized (lock) {
                        registration.queued = false;
                        if (!valid() || registration.listener == null) return;
                        State pendingState = registration.pendingState;
                        Set<String> pendingTables = new LinkedHashSet<>(registration.pendingTables);
                        Reason pendingReason = registration.pendingReason;
                        registration.clearPending();
                        try {
                            // Invocation and replacement share the monitor so queued old callbacks cannot cross accounts.
                            if (pendingState != null) registration.listener.onStateChanged(pendingState);
                            if (valid() && active() && registration.listener != null && registration.channel.joined
                                    && !pendingTables.isEmpty()) {
                                registration.listener.onInvalidated(new Invalidation(generation, pendingTables, pendingReason));
                            }
                        } catch (RuntimeException listenerFailure) { /* Isolate consumer failures without logging row or auth data. */ }
                    }
                });
            } catch (RejectedExecutionException unavailable) {
                registration.queued = false;
                registration.clearPending();
            }
        }
        ScheduledFuture<?> schedule(Runnable task, long delay) {
            if (!valid()) return null;
            long scheduledEpoch = epoch;
            return scheduler.schedule(() -> {
                synchronized (lock) {
                    if (valid() && epoch == scheduledEpoch) task.run();
                }
            }, delay, TimeUnit.MILLISECONDS);
        }
    }

    private final class Registration implements Subscription {
        final SessionImpl session;
        final Channel channel;
        final Set<String> pendingTables = new LinkedHashSet<>();
        Listener listener;
        State pendingState;
        Reason pendingReason;
        boolean queued;
        Registration(SessionImpl session, Channel channel, Listener listener) {
            this.session = session;
            this.channel = channel;
            this.listener = listener;
        }
        void clearPending() { pendingTables.clear(); pendingReason = null; pendingState = null; }
        void clear() { listener = null; clearPending(); }
        @Override public void close() {
            synchronized (lock) {
                if (listener == null) return;
                clear();
                session.observers.remove(this);
                channel.observers.remove(this);
                if (!session.valid()) return;
                if (session.observers.isEmpty()) {
                    session.tearDown();
                    session.channels.clear();
                    session.setState(State.STOPPED);
                } else if (channel.observers.isEmpty()) {
                    cancel(channel.deadline);
                    session.channels.remove(channel.tables);
                    if (session.opened && channel.joinRef != null) session.send(channel.topic, "phx_leave",
                            new JSONObject(), nextRef(), channel.joinRef);
                    session.updateJoinedState();
                }
            }
        }
    }

    private final class Channel {
        final Set<String> tables;
        final String topic;
        final Set<Registration> observers = new LinkedHashSet<>();
        final Map<Long, String> ids = new HashMap<>();
        String joinRef;
        long authorizationVersion;
        boolean joined;
        boolean degraded;
        ScheduledFuture<?> deadline;
        Channel(Set<String> tables, String topic) { this.tables = tables; this.topic = topic; }
    }

    private final class SocketEvents extends WebSocketListener {
        final SessionImpl session;
        final long epoch;
        SocketEvents(SessionImpl session, long epoch) { this.session = session; this.epoch = epoch; }
        @Override public void onOpen(WebSocket socket, Response response) {
            synchronized (lock) { session.opened(socket, epoch); }
        }
        @Override public void onMessage(WebSocket socket, String text) {
            synchronized (lock) { if (session.connected(epoch, socket)) session.message(text); }
        }
        @Override public void onMessage(WebSocket socket, ByteString bytes) {
            synchronized (lock) { if (session.connected(epoch, socket)) session.pause(State.FAILED); }
        }
        @Override public void onClosing(WebSocket socket, int code, String reason) {
            synchronized (lock) { if (session.connected(epoch, socket)) session.retry(0); }
        }
        @Override public void onClosed(WebSocket socket, int code, String reason) {
            synchronized (lock) { if (session.connected(epoch, socket)) session.retry(0); }
        }
        @Override public void onFailure(WebSocket socket, Throwable failure, Response response) {
            synchronized (lock) {
                if (!session.connected(epoch, socket)) return;
                int status = response == null ? 0 : response.code();
                if (status == 401 || status == 403) session.pause(State.AUTH_REQUIRED);
                else if (status == 429) session.retry(10_000);
                else if (status >= 400 && status < 500) session.pause(State.FAILED);
                else session.retry(0);
            }
        }
    }

    private String nextRef() { return Long.toString(++references); }
    private static void cancel(Future<?> future) { if (future != null) future.cancel(false); }

    private static JSONObject object(Object... entries) {
        JSONObject result = new JSONObject();
        try {
            for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i],
                    entries[i + 1] == null ? JSONObject.NULL : entries[i + 1]);
            return result;
        } catch (JSONException invalid) { throw new IllegalStateException("Realtime message could not be prepared."); }
    }

    private static Set<String> tableSet(Set<String> tables) {
        if (tables == null || tables.isEmpty() || tables.size() > MAX_TABLES) {
            throw new IllegalArgumentException("Explicit public table names required.");
        }
        Set<String> selected = new TreeSet<>();
        for (String table : tables) {
            if (table == null || !table.matches("[a-z_][a-z0-9_]{0,62}")) {
                throw new IllegalArgumentException("Invalid public table identifier.");
            }
            selected.add(table);
        }
        return Collections.unmodifiableSet(selected);
    }

    private static Request endpoint(String url, String key) {
        try {
            URI origin = URI.create(url);
            String host = origin.getHost();
            if (!"https".equals(origin.getScheme()) || host == null
                    || !host.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.supabase\\.co")
                    || origin.getRawUserInfo() != null || origin.getRawQuery() != null || origin.getRawFragment() != null
                    || (origin.getPort() != -1 && origin.getPort() != 443)
                    || !(origin.getRawPath().isEmpty() || "/".equals(origin.getRawPath()))
                    || key == null || key.trim().isEmpty() || key.length() > 4096) throw new IllegalArgumentException();
            // OkHttp represents a secure WebSocket's handshake as HTTPS internally.
            HttpUrl endpoint = new HttpUrl.Builder().scheme("https").host(host)
                    .addPathSegments("realtime/v1/websocket").addQueryParameter("apikey", key)
                    .addQueryParameter("vsn", "1.0.0").build();
            return new Request.Builder().url(endpoint).build();
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("A Supabase HTTPS project origin and public key are required."); }
    }

    private static ScheduledExecutorService newScheduler() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "goserve-realtime-clock");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    private static ExecutorService newTokenExecutor() {
        return new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), runnable -> {
            Thread thread = new Thread(runnable, "goserve-realtime-token");
            thread.setDaemon(true);
            return thread;
        });
    }
}
