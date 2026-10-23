package ph.edu.tip.goserve.ui;

import android.os.Looper;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.SeryoRepository;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34,application=GoServeApplication.class)
@LooperMode(LooperMode.Mode.PAUSED)
public final class SeryoViewModelTest {
    static final class Queue extends AbstractExecutorService {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>();boolean stopped;
        public void execute(Runnable task) { tasks.add(task); }
        void finish() { while(!tasks.isEmpty()) { tasks.remove().run();Shadows.shadowOf(Looper.getMainLooper()).idle(); } }
        public void shutdown(){stopped=true;} public List<Runnable> shutdownNow(){stopped=true;tasks.clear();return Collections.emptyList();}
        public boolean isShutdown(){return stopped;}public boolean isTerminated(){return stopped;}public boolean awaitTermination(long n,TimeUnit t){return stopped;}
    }
    static final class Server implements SeryoRepository.Transport {
        String consent="unset",cleanup="clear";long version;boolean current=true,fail,timeoutConsent,failCanonical,futureReady,canSend;int writes,reads,sends;
        final java.util.List<JSONObject> commands=new java.util.ArrayList<>();
        final java.util.Set<String> accepted=new java.util.HashSet<>();
        public void requireCurrent(){if(!current)throw new IllegalStateException("Changed account");}
        public JSONObject control(JSONObject input)throws Exception {
            if(fail)throw new java.net.SocketTimeoutException();
            if("consent".equals(input.getString("kind"))) {
                commands.add(new JSONObject(input.toString()));
                if(accepted.add(input.getString("idempotency_key"))) {writes++;consent=input.getString("decision");version++;}
                if(timeoutConsent) {timeoutConsent=false;throw new java.net.SocketTimeoutException();}
            } else {
                reads++;
                if(failCanonical && writes>0) {failCanonical=false;throw new java.net.SocketTimeoutException();}
            }
            return new JSONObject().put("consent",consent).put("consent_version",version).put("cleanup",cleanup)
                    .put("provider_ready",futureReady).put("can_send",canSend).put("can_attach",futureReady).put("blockers",new org.json.JSONArray().put("PROVIDER_UNAVAILABLE"));
        }
        public JSONObject send(JSONObject input)throws Exception {sends++;return new JSONObject().put("reply","Open Requests and select the office.").put("refused",false);}
    }
    @Test public void allowIsOneRealDecisionThenCanonicalRefreshAndEmptyDraft() {
        Server server=new Server();Queue queue=new Queue();SeryoViewModel model=model(server,queue);model.start(false);queue.finish();
        model.decide("allow");model.decide("allow");assertTrue(model.snapshot().busy);queue.finish();
        assertEquals(1,server.writes);assertTrue(server.reads>=2);assertEquals("allow",model.snapshot().readiness.consent);
        assertEquals("",model.snapshot().draft.text);assertNull(model.snapshot().draft.attachment);assertFalse(model.snapshot().busy);assertEquals(1,model.snapshot().decisionSequence);
    }
    @Test public void declineAndRevokeStaySeparateFromTermsAndProviderRemainsUnavailable() {
        Server server=new Server();Queue queue=new Queue();SeryoViewModel model=model(server,queue);model.start(false);queue.finish();
        for(String decision:new String[]{"decline","allow","revoke"}) {model.decide(decision);queue.finish();assertEquals(decision,model.snapshot().readiness.consent);assertFalse(model.snapshot().readiness.allowsAttachment());}
        assertEquals(3,server.writes);
    }
    @Test public void refreshFailurePreservesDraftAndFailsClosed() {
        Server server=new Server();Queue queue=new Queue();SeryoViewModel model=model(server,queue);model.start(false);queue.finish();model.edit("Unsent private question");
        server.fail=true;model.refresh();queue.finish();assertNull(model.snapshot().readiness);assertEquals("Unsent private question",model.snapshot().draft.text);assertNotNull(model.snapshot().error);
    }
    @Test public void staleAccountClearsPrivateDraftBeforeDelayedReplyCanRender() {
        Server server=new Server();Queue queue=new Queue();SeryoViewModel model=model(server,queue);model.start(false);queue.finish();model.edit("Private account A");model.refresh();server.current=false;
        model.verifyAccount();assertEquals("",model.snapshot().draft.text);queue.finish();assertNull(model.snapshot().readiness);assertEquals("",model.snapshot().draft.text);
    }
    @Test public void previewNeverWritesConsentAndFreshModelHasNoPreviousDraft() {
        Server server=new Server();Queue queue=new Queue();SeryoViewModel model=model(server,queue);model.start(true);model.edit("Local only");model.decide("allow");queue.finish();
        assertEquals(0,server.writes);assertNull(model.snapshot().readiness);assertEquals("Local only",model.snapshot().draft.text);
        assertEquals("",model(server,new Queue()).snapshot().draft.text);
    }
    @Test public void acceptedConsentTimeoutRetainsExactIntentAfterReadinessRefresh() throws Exception {
        Server server=new Server();Queue queue=new Queue();SeryoViewModel model=model(server,queue);model.start(false);queue.finish();
        model.edit("Keep my unsent question");server.timeoutConsent=true;model.decide("allow");queue.finish();
        assertEquals(1,server.writes);assertNull(model.snapshot().readiness);assertEquals(0,model.snapshot().decisionSequence);
        JSONObject original=server.commands.get(0);model.refresh();queue.finish();assertEquals(1,server.commands.size());
        model.decide("allow");queue.finish();
        assertEquals("A retry must replay the original immutable command.",original.toString(),server.commands.get(1).toString());
        assertEquals(1,server.writes);assertEquals(1,model.snapshot().decisionSequence);assertEquals("Keep my unsent question",model.snapshot().draft.text);
    }
    @Test public void canonicalReadFailureDoesNotCreateASecondConsentAudit() throws Exception {
        Server server=new Server();Queue queue=new Queue();SeryoViewModel model=model(server,queue);model.start(false);queue.finish();
        server.failCanonical=true;model.decide("decline");queue.finish();assertEquals(1,server.writes);
        JSONObject original=server.commands.get(0);model.refresh();queue.finish();model.decide("decline");queue.finish();
        assertEquals(original.toString(),server.commands.get(1).toString());assertEquals(1,server.writes);
    }
    @Test public void unresolvedDecisionBlocksAConflictingNewDecisionWithoutReplayingOnRefresh() {
        Server server=new Server();Queue queue=new Queue();SeryoViewModel model=model(server,queue);model.start(false);queue.finish();
        server.timeoutConsent=true;model.decide("allow");queue.finish();model.refresh();queue.finish();
        model.decide("revoke");queue.finish();assertEquals("The uncertain command must be reconciled first.",1,server.commands.size());
        assertEquals("allow",server.consent);
    }
    @Test public void removingLocalAttachmentCannotDiscardAnInflightConsentCompletion() {
        Server server=new Server();Queue queue=new Queue();SeryoViewModel model=model(server,queue);model.start(false);queue.finish();
        model.decide("allow");model.removeAttachment();queue.finish();
        assertEquals(1,server.writes);assertEquals(1,model.snapshot().decisionSequence);assertEquals("allow",model.snapshot().readiness.consent);
    }
    @Test public void permittedSendDispatchesOnceAndClearsOnlyTheAcceptedDraft() {
        Server server=new Server();server.consent="allow";server.futureReady=true;server.canSend=true;
        Queue queue=new Queue();SeryoViewModel model=model(server,queue);model.start(false);queue.finish();
        model.edit("How do requests work?");model.checkSend();model.checkSend();assertTrue(model.snapshot().busy);queue.finish();
        assertEquals(1,server.sends);assertEquals("",model.snapshot().draft.text);assertNull(model.snapshot().error);
    }
    @Test public void unavailableOrUnconsentedSendPreservesDraftAndDoesNotDispatch() {
        Server server=new Server();Queue queue=new Queue();SeryoViewModel model=model(server,queue);model.start(false);queue.finish();
        model.edit("How do requests work?");model.checkSend();queue.finish();assertEquals(0,server.sends);assertEquals("How do requests work?",model.snapshot().draft.text);
    }
    @Test public void accountChangeDuringSendCannotReleaseAReplyOrPreserveTheOldChat() {
        Server server=new Server();server.consent="allow";server.futureReady=true;server.canSend=true;
        Queue queue=new Queue();SeryoViewModel model=model(server,queue);model.start(false);queue.finish();
        model.edit("How do requests work?");model.checkSend();server.current=false;model.verifyAccount();queue.finish();
        assertTrue(model.snapshot().messages.isEmpty());assertNull(model.snapshot().sendingQuestion);assertEquals("",model.snapshot().draft.text);
    }
    private SeryoViewModel model(Server server,Queue queue) {return new SeryoViewModel(RuntimeEnvironment.getApplication(),new SeryoRepository(server),queue);}
}
