package ph.edu.tip.goserve.data;

import android.app.Application;
import android.net.Uri;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.domain.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34,application=Application.class)
public final class PickupRepositoryTest {
    private static final String CODE="a".repeat(64);
    @Test public void claimUsesOnlyTheActualOwnerRequestAndReturnsTheExactServerCode() throws Exception {
        Wire w=new Wire(); RequestRepository r=new RequestRepository(w); DeliveryData.PickupClaim c=r.pickupClaim(record(),options(true,null)); assertNotNull(c);
        assertEquals("pickup_claim",w.name); assertEquals(1,w.args.length()); assertEquals(id(1),w.args.getString("p_request_id")); assertEquals(CODE,c.qrPayload); assertEquals(CODE,c.displayCode); assertEquals(0,w.reads);
    }
    @Test public void claimCannotCrossOwnerDeliveryModeOrState() throws Exception {
        Wire w=new Wire(); RequestRepository r=new RequestRepository(w); w.actor=id(91); failCode("FORBIDDEN",()->r.pickupClaim(record(),options(true,null))); w.actor=id(90);
        RequestData.Record base=record(); RequestData.Record digital=new RequestData.Record(base.id,base.reference,base.ownerId,base.officeId,"digital",base.createdAt,2,base.status,base.snapshot);
        failCode("INVALID_STATE",()->r.pickupClaim(digital,options(true,null))); RequestData.Record processing=new RequestData.Record(base.id,base.reference,base.ownerId,base.officeId,"physical",base.createdAt,2,RequestStatus.PROCESSING,base.snapshot);
        failCode("INVALID_STATE",()->r.pickupClaim(processing,options(true,null))); assertEquals(0,w.calls);
    }
    @Test public void claimCannotInventPolicyAvailabilityOrIgnoreOptionsVersion() throws Exception {
        Wire w=new Wire(); RequestRepository r=new RequestRepository(w); failCode("CONFIGURATION_REQUIRED",()->r.pickupClaim(record(),null)); failCode("POLICY_BLOCKED",()->r.pickupClaim(record(),options(false,"POLICY_BLOCKED")));
        failCode("STALE_VERSION",()->r.pickupClaim(record(),new RequestData.RequestOptions(id(1),1,Map.of("pickup_claim",new RequestData.ActionOptions(true,null))))); assertEquals(0,w.calls);
    }
    @Test public void malformedClaimOrMismatchedPayloadNeverBecomesADisplayableCode() throws Exception {
        for(String key:List.of("request_id","qr_payload","expires_at","private_path")) {
            Wire w=new Wire(); if(key.equals("private_path"))w.claim.put(key,"private"); else w.claim.put(key,key.equals("request_id")?id(99):"wrong");
            RequestRepository r=new RequestRepository(w); failCode(key.equals("request_id")?"FORBIDDEN":"VALIDATION_FAILED",()->r.pickupClaim(record(),options(true,null)));
        }
    }
    @Test public void claimDoesNotApplyAnInventedLocalExpiryPolicy() throws Exception {
        Wire w=new Wire(); w.claim.put("expires_at","2020-01-01T00:00:00Z"); assertEquals("2020-01-01T00:00:00Z",new RequestRepository(w).pickupClaim(record(),options(true,null)).expiresAt);
    }
    @Test public void validationUsesOnlyTheCodeThenReadsTheCanonicalAssignedOfficeRequest() throws Exception {
        Wire w=staff(); RequestRepository r=new RequestRepository(w); DeliveryData.PickupReview v=r.pickupValidate(CODE,"OFF-REG"); assertNotNull(v);
        assertEquals("pickup_validate",w.name); assertEquals(1,w.args.length()); assertEquals(CODE,w.args.getString("p_code")); assertEquals(id(91),v.actorId); assertEquals(id(80),v.validationId);
        Uri q=Uri.parse(w.path); assertEquals("eq.OFF-REG",q.getQueryParameter("office_id")); assertEquals("eq."+id(1),q.getQueryParameter("id")); assertNull(q.getQueryParameter("owner_id")); assertEquals(1,w.calls);
    }
    @Test public void invalidScanTextAndMissingOfficeCannotReachValidation() throws Exception {
        Wire w=staff(); RequestRepository r=new RequestRepository(w); failCode("CODE_INVALID",()->r.pickupValidate("https://not-a-claim.test", "OFF-REG")); failCode("CODE_INVALID",()->r.pickupValidate("", "OFF-REG"));
        failCode("FORBIDDEN",()->r.pickupValidate(CODE,null)); assertEquals(0,w.calls);
    }
    @Test public void crossOfficeValidationCannotReadOrHandoverAnotherOfficeRequest() throws Exception {
        Wire w=staff(); w.validation.put("office_id","OFF-GCC"); failCode("FORBIDDEN",()->new RequestRepository(w).pickupValidate(CODE,"OFF-REG")); assertEquals(0,w.reads);
    }
    @Test public void malformedValidationAndStaleCanonicalRowsCannotCreateAReview() throws Exception {
        Wire w=staff(); w.validation.put("request_version",1); failCode("STALE_VERSION",()->new RequestRepository(w).pickupValidate(CODE,"OFF-REG"));
        Wire ref=staff(); ref.validation.put("reference","other"); failCode("FORBIDDEN",()->new RequestRepository(ref).pickupValidate(CODE,"OFF-REG"));
        Wire malformed=staff(); malformed.validation.put("private_code",CODE); failCode("VALIDATION_FAILED",()->new RequestRepository(malformed).pickupValidate(CODE,"OFF-REG"));
    }
    @Test public void validationDoesNotConsumeTheClaimOrCompleteTheRequest() throws Exception {
        Wire w=staff(); DeliveryData.PickupReview review=new RequestRepository(w).pickupValidate(CODE,"OFF-REG"); assertNotNull(review);
        assertEquals(RequestStatus.READY_FOR_PICKUP_DOWNLOAD,review.request.status); assertEquals(1,w.calls); assertEquals("pickup_validate",w.name);
    }
    @Test public void handoverReviewMakesNoIoAndRetainsOneKeyForTheSameValidation() throws Exception {
        Wire w=staff(); RequestRepository r=new RequestRepository(w); DeliveryData.PickupReview review=review(); DeliveryData.Handover a=r.handoverDraft(review,null); assertNotNull(a);
        assertEquals(a.operationKey,r.handoverDraft(review,a).operationKey); assertEquals(0,w.calls);
        DeliveryData.PickupReview other=new DeliveryData.PickupReview(id(81),id(91),"OFF-REG",review.expiresAt,record()); assertNotEquals(a.operationKey,r.handoverDraft(other,a).operationKey);
    }
    @Test public void handoverRejectsAReplacedActorBeforeAnyWrite() throws Exception {
        Wire w=staff(); RequestRepository r=new RequestRepository(w); DeliveryData.Handover a=r.handoverDraft(review(),null); assertNotNull(a); w.actor=id(92); failCode("FORBIDDEN",()->r.pickupHandover(a)); assertEquals(0,w.calls);
    }
    @Test public void explicitHandoverUsesExactValidationVersionKeyAndCanonicalReload() throws Exception {
        Wire w=staff(); w.rows=new JSONArray().put(row(3,RequestStatus.COMPLETED)); RequestRepository r=new RequestRepository(w); DeliveryData.Handover a=r.handoverDraft(review(),null); assertNotNull(a);
        RequestData.Record result=r.pickupHandover(a); assertNotNull(result); assertEquals(RequestStatus.COMPLETED,result.status); assertEquals(3,result.version);
        assertEquals("pickup_handover",w.name); assertEquals(3,w.args.length()); assertEquals(id(80),w.args.getString("p_validation_id")); assertEquals(2,w.args.getLong("p_expected_version")); assertEquals(a.operationKey,w.args.getString("p_idempotency_key")); assertEquals("eq.OFF-REG",Uri.parse(w.path).getQueryParameter("office_id"));
    }
    @Test public void crossIdentityOrOldHandoverReceiptCannotPublishCompletion() throws Exception {
        Wire w=staff(); w.handover.put("owner_id",id(99)); RequestRepository r=new RequestRepository(w); failCode("FORBIDDEN",()->r.pickupHandover(r.handoverDraft(review(),null))); assertEquals(0,w.reads);
        Wire old=staff(); old.handover=row(2,RequestStatus.COMPLETED); RequestRepository rr=new RequestRepository(old); failCode("STALE_RESPONSE",()->rr.pickupHandover(rr.handoverDraft(review(),null)));
    }
    @Test public void oldCanonicalReadAfterSuccessfulHandoverRemainsUnconfirmed() throws Exception {
        Wire w=staff(); RequestRepository r=new RequestRepository(w); failCode("STALE_RESPONSE",()->r.pickupHandover(r.handoverDraft(review(),null)));
    }
    @Test public void lostHandoverAndReloadKeepTheExactReviewKeyOnReplay() throws Exception {
        Wire w=staff(); RequestRepository r=new RequestRepository(w); DeliveryData.Handover a=r.handoverDraft(review(),null); assertNotNull(a); w.error=new java.io.IOException("lost");
        try { r.pickupHandover(a); fail("lost response"); } catch(java.io.IOException expected) {} String body=w.args.toString(); w.error=null; w.readError=new java.io.IOException("reload lost");
        try { r.pickupHandover(a); fail("lost canonical read"); } catch(java.io.IOException expected) {} assertEquals(body,w.args.toString());
        w.readError=null; w.rows=new JSONArray().put(row(3,RequestStatus.COMPLETED)); assertEquals(RequestStatus.COMPLETED,r.pickupHandover(a).status); assertEquals(body,w.args.toString());
    }
    @Test public void serverExpiryAndAccountReplacementNeverBecomeValidationSuccess() throws Exception {
        Wire w=staff(); w.error=new SupabaseClient.ApiException(400,"CODE_EXPIRED","private"); failCode("CODE_EXPIRED",()->new RequestRepository(w).pickupValidate(CODE,"OFF-REG"));
        Wire changed=staff(); changed.revoke=true; try { new RequestRepository(changed).pickupValidate(CODE,"OFF-REG"); fail("Changed account"); } catch(IllegalStateException expected) {} assertEquals(0,changed.reads);
    }
    private static DeliveryData.PickupReview review() { return new DeliveryData.PickupReview(id(80),id(91),"OFF-REG","2026-10-22T10:00:00Z",record()); }
    private static RequestData.RequestOptions options(boolean available,String code) { return new RequestData.RequestOptions(id(1),2,Map.of("pickup_claim",new RequestData.ActionOptions(available,code))); }
    private static RequestData.Record record() { return new RequestData.Record(id(1),"fixture-reference",id(90),"OFF-REG","physical","2026-10-22T01:00:00Z",2,RequestStatus.READY_FOR_PICKUP_DOWNLOAD,new RequestData.Service(id(50),"OFF-REG","Configured service",1,FeeDeclaration.FREE,BigDecimal.ZERO,null,List.of("physical","digital"),List.of(),true)); }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static JSONObject row(long version,RequestStatus status) throws Exception {
        JSONObject s=new JSONObject().put("id",id(50)).put("office_id","OFF-REG").put("name","Configured service").put("version",1).put("fee_state","free").put("amount",0).put("currency",JSONObject.NULL).put("delivery_modes",new JSONArray().put("physical").put("digital")).put("requirements",new JSONArray()).put("active",true).put("is_demo",true);
        return new JSONObject().put("id",id(1)).put("reference","fixture-reference").put("owner_id",id(90)).put("office_id","OFF-REG").put("service_id",id(50)).put("snapshot",s).put("delivery_mode","physical").put("notes","").put("status",status.wireValue).put("version",version).put("created_at","2026-10-22T01:00:00Z").put("terminal_at",status==RequestStatus.COMPLETED?"2026-10-22T02:00:00Z":JSONObject.NULL);
    }
    private interface Work { void run() throws Exception; }
    private static void failCode(String code,Work work) throws Exception { try { work.run(); fail("Expected "+code); } catch(RequestRepository.WorkflowException e) { assertEquals(code,e.code); } }
    private static Wire staff() throws Exception { Wire w=new Wire(); w.actor=id(91); return w; }
    private static final class Wire implements RequestRepository.Transport {
        String actor=id(90),name,path; JSONObject args,claim,validation,handover; JSONArray rows; int calls,reads; boolean current=true,revoke; Exception error,readError;
        Wire() throws Exception { claim=new JSONObject().put("id",id(70)).put("request_id",id(1)).put("display_code",CODE).put("qr_payload",CODE).put("expires_at","2026-10-22T10:00:00Z"); validation=new JSONObject().put("validation_id",id(80)).put("request_id",id(1)).put("reference","fixture-reference").put("office_id","OFF-REG").put("request_version",2).put("claim_expires_at","2026-10-22T10:00:00Z"); handover=row(3,RequestStatus.COMPLETED); rows=new JSONArray().put(row(2,RequestStatus.READY_FOR_PICKUP_DOWNLOAD)); }
        public String accountId() { return actor; } public void requireCurrent() { if(!current)throw new IllegalStateException("Changed account"); }
        public JSONObject rpc(String name,JSONObject args) throws Exception { ++calls; this.name=name; this.args=new JSONObject(args.toString()); if(error!=null)throw error; if(revoke)current=false; return new JSONObject((name.equals("pickup_claim")?claim:name.equals("pickup_validate")?validation:handover).toString()); }
        public JSONArray get(String path) throws Exception { ++reads; this.path=path; if(readError!=null)throw readError; return new JSONArray(rows.toString()); }
    }
}
