package ph.edu.tip.goserve.data;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.domain.FeeDeclaration;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.RequestStatus;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public final class RequestTransitionTest {
    @Test public void reviewHasNoIoAndPreservesOnlyAnIdenticalActorScopedIntentKey() throws Exception {
        Wire wire=new Wire(); RequestRepository repo=new RequestRepository(wire);
        RequestData.Transition first=repo.transitionDraft(record(1,RequestStatus.SUBMITTED),"OFF-REG",null,RequestStatus.PROCESSING,null,null);
        RequestData.Transition same=repo.transitionDraft(record(1,RequestStatus.SUBMITTED),"OFF-REG",null,RequestStatus.PROCESSING,null,first);
        assertEquals(first.operationKey,same.operationKey); assertEquals(wire.actor,first.actorId); assertEquals("",first.reason);
        RequestData.Transition changed=repo.transitionDraft(record(2,RequestStatus.SUBMITTED),"OFF-REG",null,RequestStatus.PROCESSING,null,first);
        assertNotEquals(first.operationKey,changed.operationKey);
        assertEquals(0,wire.calls); assertEquals(0,wire.reads);
    }
    @Test public void explicitTransitionUsesExactRpcArgumentsAndReloadsItsAssignedOffice() throws Exception {
        Wire wire=new Wire(); RequestRepository repo=new RequestRepository(wire);
        RequestData.Transition input=repo.transitionDraft(record(1,RequestStatus.SUBMITTED),"OFF-REG",null,RequestStatus.PROCESSING,"",null);
        wire.reply=row(2,RequestStatus.PROCESSING); wire.rows=new JSONArray().put(row(3,RequestStatus.READY_FOR_PICKUP_DOWNLOAD));
        RequestData.Record result=repo.transition(input);
        assertEquals("transition_request",wire.name); assertEquals(5,wire.args.length());
        assertEquals(id(1),wire.args.getString("p_request_id")); assertEquals(1,wire.args.getLong("p_expected_version"));
        assertEquals("Processing",wire.args.getString("p_target_status")); assertEquals("",wire.args.getString("p_reason"));
        assertEquals(input.operationKey,wire.args.getString("p_idempotency_key")); assertEquals(3,result.version);
        assertEquals(RequestStatus.READY_FOR_PICKUP_DOWNLOAD,result.status);
        String path=URLDecoder.decode(wire.path,StandardCharsets.UTF_8);
        assertTrue(path.contains("office_id=eq.OFF-REG")); assertTrue(path.contains("id=eq."+id(1))); assertTrue(path.contains("limit=2"));
        assertFalse(path.contains("owner_id=eq."));
    }
    @Test public void ownerCancellationUsesCurrentPolicyReasonAndOwnerReload() throws Exception {
        Wire wire=new Wire(); wire.actor=id(90); RequestRepository repo=new RequestRepository(wire);
        RequestData.Transition input=repo.transitionDraft(record(1,RequestStatus.SUBMITTED),null,policy("cancel",true,null,1),RequestStatus.CANCELLED,"  No longer needed  ",null);
        wire.reply=row(2,RequestStatus.CANCELLED); wire.rows=new JSONArray().put(wire.reply);
        assertEquals(RequestStatus.CANCELLED,repo.transition(input).status); assertEquals("No longer needed",wire.args.getString("p_reason"));
        assertTrue(URLDecoder.decode(wire.path,StandardCharsets.UTF_8).contains("owner_id=eq."+id(90)));
    }
    @Test public void cancellationAndRejectionCannotInventMissingOrBlockedPolicy() throws Exception {
        RequestRepository repo=new RequestRepository(new Wire());
        for(RequestStatus status:List.of(RequestStatus.CANCELLED,RequestStatus.REJECTED)) {
            String action=status==RequestStatus.CANCELLED?"cancel":"reject";
            expect("CONFIGURATION_REQUIRED",()->repo.transitionDraft(record(1,RequestStatus.SUBMITTED),"OFF-REG",null,status,"Reason",null));
            expect("POLICY_BLOCKED",()->repo.transitionDraft(record(1,RequestStatus.SUBMITTED),"OFF-REG",policy(action,false,"POLICY_BLOCKED",1),status,"Reason",null));
            expect("VALIDATION_FAILED",()->repo.transitionDraft(record(1,RequestStatus.SUBMITTED),"OFF-REG",policy(action,true,null,1),status," \n ",null));
            expect("STALE_VERSION",()->repo.transitionDraft(record(1,RequestStatus.SUBMITTED),"OFF-REG",policy(action,true,null,2),status,"Reason",null));
        }
    }
    @Test public void changedReasonOrActorDoesNotReuseAnEarlierOperationKey() throws Exception {
        Wire wire=new Wire(); RequestRepository repo=new RequestRepository(wire); RequestData.Record request=record(1,RequestStatus.SUBMITTED);
        RequestData.Transition first=repo.transitionDraft(request,"OFF-REG",policy("reject",true,null,1),RequestStatus.REJECTED,"First reason",null);
        RequestData.Transition next=repo.transitionDraft(request,"OFF-REG",policy("reject",true,null,1),RequestStatus.REJECTED,"Changed reason",first);
        assertNotEquals(first.operationKey,next.operationKey); wire.actor=id(92);
        RequestData.Transition actor=repo.transitionDraft(request,"OFF-REG",policy("reject",true,null,1),RequestStatus.REJECTED,"First reason",first);
        assertNotEquals(first.operationKey,actor.operationKey);
    }
    @Test public void studentCannotProcessRejectOrChangeAnotherOwnersRequest() throws Exception {
        Wire wire=new Wire(); wire.actor=id(90); RequestRepository repo=new RequestRepository(wire);
        expect("FORBIDDEN",()->repo.transitionDraft(record(1,RequestStatus.SUBMITTED),null,null,RequestStatus.PROCESSING,"",null));
        expect("FORBIDDEN",()->repo.transitionDraft(record(1,RequestStatus.SUBMITTED),null,policy("reject",true,null,1),RequestStatus.REJECTED,"Reason",null));
        wire.actor=id(91);
        expect("FORBIDDEN",()->repo.transitionDraft(record(1,RequestStatus.SUBMITTED),null,policy("cancel",true,null,1),RequestStatus.CANCELLED,"Reason",null));
        expect("FORBIDDEN",()->repo.transitionDraft(record(1,RequestStatus.SUBMITTED),"OFF-ACC",null,RequestStatus.PROCESSING,"",null));
        assertEquals(0,wire.calls);
    }
    @Test public void terminalOrUnsupportedTargetsAndWrongProcessingOrderNeverWrite() throws Exception {
        Wire wire=new Wire(); RequestRepository repo=new RequestRepository(wire);
        expect("FORBIDDEN",()->repo.transitionDraft(record(1,RequestStatus.PROCESSING),"OFF-REG",null,RequestStatus.COMPLETED,"",null));
        expect("INVALID_STATE",()->repo.transitionDraft(record(1,RequestStatus.SUBMITTED),"OFF-REG",null,RequestStatus.READY_FOR_PICKUP_DOWNLOAD,"",null));
        expect("INVALID_STATE",()->repo.transitionDraft(record(1,RequestStatus.CANCELLED),"OFF-REG",null,RequestStatus.PROCESSING,"",null));
        assertEquals(0,wire.calls);
    }
    @Test public void lostResponseRetryKeepsTheExactReviewedPayloadAndKey() throws Exception {
        Wire wire=new Wire(); RequestRepository repo=new RequestRepository(wire);
        RequestData.Transition input=repo.transitionDraft(record(1,RequestStatus.SUBMITTED),"OFF-REG",null,RequestStatus.PROCESSING,"",null);
        wire.failure=new java.io.IOException("fixture transport lost");
        try { repo.transition(input); fail("Lost response must not succeed"); } catch(java.io.IOException expected) {}
        String first=wire.args.toString(); wire.failure=null; wire.reply=row(2,RequestStatus.PROCESSING); wire.rows=new JSONArray().put(wire.reply);
        assertEquals(RequestStatus.PROCESSING,repo.transition(input).status); assertEquals(first,wire.args.toString()); assertEquals(2,wire.calls);
    }
    @Test public void mismatchedReceiptNeverTriggersCanonicalReload() throws Exception {
        Wire wire=new Wire(); RequestRepository repo=new RequestRepository(wire);
        RequestData.Transition input=repo.transitionDraft(record(1,RequestStatus.SUBMITTED),"OFF-REG",null,RequestStatus.PROCESSING,"",null);
        wire.reply=row(2,RequestStatus.PROCESSING).put("id",id(99));
        expect("FORBIDDEN",()->repo.transition(input)); assertEquals(0,wire.reads);
    }
    @Test public void staleReceiptAndStaleReloadNeverPublishSuccess() throws Exception {
        Wire wire=new Wire(); RequestRepository repo=new RequestRepository(wire);
        RequestData.Transition input=repo.transitionDraft(record(1,RequestStatus.SUBMITTED),"OFF-REG",null,RequestStatus.PROCESSING,"",null);
        wire.reply=row(1,RequestStatus.PROCESSING); expect("STALE_RESPONSE",()->repo.transition(input));
        wire.reply=row(2,RequestStatus.PROCESSING); wire.rows=new JSONArray().put(row(1,RequestStatus.SUBMITTED));
        expect("STALE_RESPONSE",()->repo.transition(input));
    }
    @Test public void successfulRpcWithFailedReloadStaysUncertainAndCanReplay() throws Exception {
        Wire wire=new Wire(); RequestRepository repo=new RequestRepository(wire);
        RequestData.Transition input=repo.transitionDraft(record(1,RequestStatus.SUBMITTED),"OFF-REG",null,RequestStatus.PROCESSING,"",null);
        wire.reply=row(2,RequestStatus.PROCESSING); wire.readFailure=new java.io.IOException("fixture reload failed");
        try { repo.transition(input); fail("No canonical reload"); } catch(java.io.IOException expected) {}
        wire.readFailure=null; wire.rows=new JSONArray().put(wire.reply);
        assertEquals(2,repo.transition(input).version); assertEquals(input.operationKey,wire.args.getString("p_idempotency_key"));
    }
    @Test public void replacedAccountCannotWriteOrPublishThePreviousActorsOperation() throws Exception {
        Wire wire=new Wire(); RequestRepository repo=new RequestRepository(wire);
        RequestData.Transition input=repo.transitionDraft(record(1,RequestStatus.SUBMITTED),"OFF-REG",null,RequestStatus.PROCESSING,"",null);
        wire.actor=id(92); expect("FORBIDDEN",()->repo.transition(input)); assertEquals(0,wire.calls);
        wire.actor=id(91); wire.reply=row(2,RequestStatus.PROCESSING); wire.replaceDuringRpc=true;
        try { repo.transition(input); fail("Replaced lease"); } catch(IllegalStateException expected) {}
        assertEquals(0,wire.reads);
    }
    private static RequestData.RequestOptions policy(String action,boolean allowed,String blocker,long version) {
        return new RequestData.RequestOptions(id(1),version,Map.of(action,new RequestData.ActionOptions(allowed,blocker)));
    }
    private static RequestData.Record record(long version,RequestStatus status) {
        return new RequestData.Record(id(1),"fixture-reference",id(90),"OFF-REG","physical","2026-10-21T01:00:00Z",version,status,
            new RequestData.Service(id(50),"OFF-REG","Configured fixture",1,FeeDeclaration.FREE,BigDecimal.ZERO,null,List.of("physical"),List.of(),true));
    }
    private static JSONObject row(long version,RequestStatus status) throws Exception {
        JSONObject service=new JSONObject().put("id",id(50)).put("office_id","OFF-REG").put("name","Configured fixture")
            .put("version",1).put("fee_state","free").put("amount",0).put("currency",JSONObject.NULL).put("delivery_modes",new JSONArray().put("physical"))
            .put("requirements",new JSONArray()).put("active",true).put("is_demo",true);
        return new JSONObject().put("id",id(1)).put("reference","fixture-reference").put("owner_id",id(90)).put("office_id","OFF-REG")
            .put("service_id",id(50)).put("snapshot",service).put("delivery_mode","physical").put("notes","").put("version",version)
            .put("status",status.wireValue).put("created_at","2026-10-21T01:00:00Z")
            .put("terminal_at",status==RequestStatus.CANCELLED?"2026-10-21T02:00:00Z":JSONObject.NULL);
    }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private interface Work { void run() throws Exception; }
    private static void expect(String code,Work work) throws Exception {
        try { work.run(); fail("Expected "+code); } catch(RequestRepository.WorkflowException failure) { assertEquals(code,failure.code); }
    }
    private static final class Wire implements RequestRepository.Transport {
        String actor=id(91),name,path; int calls,reads; JSONObject args,reply; JSONArray rows=new JSONArray(); Exception failure,readFailure;
        boolean current=true,replaceDuringRpc;
        public String accountId() { return actor; }
        public void requireCurrent() { if(!current)throw new IllegalStateException("Account replaced"); }
        public JSONArray get(String query) throws Exception { ++reads; path=query; if(readFailure!=null)throw readFailure; return rows; }
        public JSONObject rpc(String rpc,JSONObject payload) throws Exception { ++calls; name=rpc; args=payload; if(failure!=null)throw failure; if(replaceDuringRpc)current=false; return reply; }
    }
}
