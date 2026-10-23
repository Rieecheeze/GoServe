package ph.edu.tip.goserve.data;

import android.app.Application;
import android.net.Uri;
import java.math.BigDecimal;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.domain.DeliveryData;
import ph.edu.tip.goserve.domain.FeeDeclaration;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.RequestStatus;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34,application=Application.class)
public final class ReceiptConfirmationTest {
    @Test public void reviewedReceiptMakesNoWriteAndRetainsActorVersionAndKey() throws Exception {
        Wire wire=new Wire(); RequestRepository repo=new RequestRepository(wire); DeliveryData.Receipt input=repo.receiptDraft(record(2,RequestStatus.READY_FOR_PICKUP_DOWNLOAD,"digital"),null); assertNotNull(input);
        DeliveryData.Receipt same=repo.receiptDraft(record(2,RequestStatus.READY_FOR_PICKUP_DOWNLOAD,"digital"),input); assertEquals(input.operationKey,same.operationKey); assertEquals(id(90),input.actorId); assertEquals(0,wire.calls);
        DeliveryData.Receipt next=repo.receiptDraft(record(3,RequestStatus.READY_FOR_PICKUP_DOWNLOAD,"digital"),input); assertNotEquals(input.operationKey,next.operationKey);
    }
    @Test public void onlyTheOwnerOfAReadyDigitalRequestCanReviewReceipt() throws Exception {
        Wire wire=new Wire(); RequestRepository repo=new RequestRepository(wire); wire.actor=id(91);
        failure("FORBIDDEN",()->repo.receiptDraft(record(2,RequestStatus.READY_FOR_PICKUP_DOWNLOAD,"digital"),null)); wire.actor=id(90);
        failure("INVALID_STATE",()->repo.receiptDraft(record(2,RequestStatus.PROCESSING,"digital"),null));
        failure("INVALID_STATE",()->repo.receiptDraft(record(2,RequestStatus.READY_FOR_PICKUP_DOWNLOAD,"physical"),null)); assertEquals(0,wire.calls);
    }
    @Test public void explicitReceiptCallsTheExactRpcThenReloadsCanonicalOwnerScope() throws Exception {
        Wire wire=new Wire(); RequestRepository repo=new RequestRepository(wire); DeliveryData.Receipt input=draft(repo); assertNotNull(input);
        RequestData.Record result=repo.confirmReceipt(input); assertNotNull(result); assertEquals(RequestStatus.COMPLETED,result.status); assertEquals(3,result.version);
        assertEquals("confirm_receipt",wire.name); assertEquals(3,wire.args.length()); assertEquals(id(1),wire.args.getString("p_request_id")); assertEquals(2,wire.args.getLong("p_expected_version")); assertEquals(input.operationKey,wire.args.getString("p_idempotency_key"));
        Uri query=Uri.parse(wire.path); assertEquals("eq."+id(90),query.getQueryParameter("owner_id")); assertEquals("eq."+id(1),query.getQueryParameter("id")); assertEquals("2",query.getQueryParameter("limit")); assertEquals(1,wire.reads);
    }
    @Test public void genericRequestTransitionStillCannotCompleteARequest() throws Exception {
        Wire wire=new Wire(); RequestRepository repo=new RequestRepository(wire);
        failure("FORBIDDEN",()->repo.transitionDraft(record(2,RequestStatus.READY_FOR_PICKUP_DOWNLOAD,"digital"),"OFF-REG",null,RequestStatus.COMPLETED,"",null)); assertEquals(0,wire.calls);
    }
    @Test public void crossOwnerOrRequestReceiptCannotTriggerAReloadOrCompletion() throws Exception {
        for(String field:List.of("id","owner_id","office_id","service_id")) {
            Wire wire=new Wire(); wire.reply.put(field,field.equals("office_id")?"OFF-GCC":id(99)); RequestRepository repo=new RequestRepository(wire);
            if(field.equals("office_id"))wire.reply.getJSONObject("snapshot").put("office_id","OFF-GCC");
            if(field.equals("service_id"))wire.reply.getJSONObject("snapshot").put("id",id(99));
            failure("FORBIDDEN",()->repo.confirmReceipt(draft(repo))); assertEquals(0,wire.reads);
        }
    }
    @Test public void staleOrNoncompletedReceiptsAreNotCompletion() throws Exception {
        Wire wire=new Wire(); RequestRepository repo=new RequestRepository(wire); DeliveryData.Receipt input=draft(repo); assertNotNull(input);
        wire.reply=row(2,RequestStatus.COMPLETED); failure("STALE_RESPONSE",()->repo.confirmReceipt(input));
        wire.reply=row(3,RequestStatus.READY_FOR_PICKUP_DOWNLOAD); failure("STALE_RESPONSE",()->repo.confirmReceipt(input)); assertEquals(0,wire.reads);
    }
    @Test public void successfulRpcWithOldCanonicalReadRemainsUnconfirmed() throws Exception {
        Wire wire=new Wire(); RequestRepository repo=new RequestRepository(wire); wire.rows=new JSONArray().put(row(2,RequestStatus.READY_FOR_PICKUP_DOWNLOAD));
        failure("STALE_RESPONSE",()->repo.confirmReceipt(draft(repo)));
    }
    @Test public void lostReceiptResponseRetriesTheSameReviewedOperation() throws Exception {
        Wire wire=new Wire(); RequestRepository repo=new RequestRepository(wire); DeliveryData.Receipt input=draft(repo); assertNotNull(input); wire.error=new java.io.IOException("lost");
        try { repo.confirmReceipt(input); fail("Lost response"); } catch(java.io.IOException expected) {}
        String first=wire.args.toString(); wire.error=null; assertEquals(RequestStatus.COMPLETED,repo.confirmReceipt(input).status); assertEquals(first,wire.args.toString()); assertEquals(2,wire.calls);
    }
    @Test public void failedCanonicalReloadCanRetryTheReceiptWithoutANewKey() throws Exception {
        Wire wire=new Wire(); RequestRepository repo=new RequestRepository(wire); DeliveryData.Receipt input=draft(repo); assertNotNull(input); wire.readError=new java.io.IOException("lost read");
        try { repo.confirmReceipt(input); fail("No canonical read"); } catch(java.io.IOException expected) {}
        wire.readError=null; assertEquals(RequestStatus.COMPLETED,repo.confirmReceipt(input).status); assertEquals(input.operationKey,wire.args.getString("p_idempotency_key"));
    }
    @Test public void replacedActorCannotConfirmOrPublishThePreviousReceipt() throws Exception {
        Wire wire=new Wire(); RequestRepository repo=new RequestRepository(wire); DeliveryData.Receipt input=draft(repo); assertNotNull(input); wire.actor=id(91);
        failure("FORBIDDEN",()->repo.confirmReceipt(input)); assertEquals(0,wire.calls); wire.actor=id(90); wire.revokeDuringRpc=true;
        try { repo.confirmReceipt(input); fail("Replaced account"); } catch(IllegalStateException expected) {} assertEquals(0,wire.reads);
    }
    private static DeliveryData.Receipt draft(RequestRepository repo) throws Exception { return repo.receiptDraft(record(2,RequestStatus.READY_FOR_PICKUP_DOWNLOAD,"digital"),null); }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static RequestData.Record record(long version,RequestStatus status,String mode) { return new RequestData.Record(id(1),"fixture-reference",id(90),"OFF-REG",mode,"2026-10-21T01:00:00Z",version,status,
        new RequestData.Service(id(50),"OFF-REG","Configured fixture",1,FeeDeclaration.FREE,BigDecimal.ZERO,null,List.of("digital","physical"),List.of(),true)); }
    private static JSONObject row(long version,RequestStatus status) throws Exception {
        JSONObject service=new JSONObject().put("id",id(50)).put("office_id","OFF-REG").put("name","Configured fixture").put("version",1).put("fee_state","free").put("amount",0).put("currency",JSONObject.NULL)
            .put("delivery_modes",new JSONArray().put("digital").put("physical")).put("requirements",new JSONArray()).put("active",true).put("is_demo",true);
        return new JSONObject().put("id",id(1)).put("reference","fixture-reference").put("owner_id",id(90)).put("office_id","OFF-REG").put("service_id",id(50)).put("snapshot",service)
            .put("delivery_mode","digital").put("notes","").put("version",version).put("status",status.wireValue).put("created_at","2026-10-21T01:00:00Z").put("terminal_at",status==RequestStatus.COMPLETED?"2026-10-22T01:00:00Z":JSONObject.NULL);
    }
    private interface Work { void run() throws Exception; }
    private static void failure(String code,Work work) throws Exception { try { work.run(); fail("Expected "+code); } catch(RequestRepository.WorkflowException expected) { assertEquals(code,expected.code); } }
    private static final class Wire implements RequestRepository.Transport {
        String actor=id(90),name,path; int calls,reads; boolean current=true,revokeDuringRpc; JSONObject args,reply; JSONArray rows; Exception error,readError;
        Wire() throws Exception { reply=row(3,RequestStatus.COMPLETED); rows=new JSONArray().put(row(3,RequestStatus.COMPLETED)); }
        public String accountId() { return actor; } public void requireCurrent() { if(!current)throw new IllegalStateException("Replaced account"); }
        public JSONObject rpc(String name,JSONObject args) throws Exception { ++calls; this.name=name; this.args=new JSONObject(args.toString()); if(error!=null)throw error; if(revokeDuringRpc)current=false; return new JSONObject(reply.toString()); }
        public JSONArray get(String path) throws Exception { ++reads; this.path=path; if(readError!=null)throw readError; return new JSONArray(rows.toString()); }
    }
}
