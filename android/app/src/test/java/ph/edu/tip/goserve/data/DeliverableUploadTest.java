package ph.edu.tip.goserve.data;

import android.app.Application;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
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
public final class DeliverableUploadTest {
    @Test public void explicitDraftUsesTheRequestOwnerAndMakesNoNetworkCall() throws Exception {
        Wire wire=new Wire(); DeliveryData.PdfUpload input=draft(new DeliveryRepository(wire)); assertNotNull(input);
        assertEquals(id(91),input.actorId); assertEquals(id(90),input.request.ownerId); assertEquals("OFF-REG",input.officeId);
        assertEquals(4,input.sizeBytes); assertEquals(64,input.contentSha256.length()); assertNull(input.fileId); assertTrue(wire.edges.isEmpty()); assertEquals(0,wire.uploads);
    }
    @Test public void identicalIntentRetainsItsOperationAndPreparedFileAcrossUncertainRetry() throws Exception {
        DeliveryRepository repo=new DeliveryRepository(new Wire()); DeliveryData.PdfUpload input=repo.preparePdf(draft(repo)); assertNotNull(input);
        DeliveryData.PdfUpload same=repo.pdfUpload(record(RequestStatus.PROCESSING,"digital",2),"OFF-REG",options(2,true,4L),bytes(),input);
        assertEquals(input.operationKey,same.operationKey); assertEquals(id(1),same.fileId);
        DeliveryData.PdfUpload changed=repo.pdfUpload(record(RequestStatus.PROCESSING,"digital",2),"OFF-REG",options(2,true,4L),new byte[]{4,3,2,1},same);
        assertNotEquals(same.operationKey,changed.operationKey); assertNull(changed.fileId);
    }
    @Test public void changedRequestVersionCannotReuseAnOldUploadIntent() throws Exception {
        DeliveryRepository repo=new DeliveryRepository(new Wire()); DeliveryData.PdfUpload prior=draft(repo); assertNotNull(prior);
        DeliveryData.PdfUpload changed=repo.pdfUpload(record(RequestStatus.PROCESSING,"digital",3),"OFF-REG",options(3,true,4L),bytes(),prior);
        assertNotEquals(prior.operationKey,changed.operationKey); assertNull(changed.fileId);
    }
    @Test public void preparationUsesTheExactPrivateDeliverableContract() throws Exception {
        Wire wire=new Wire(); DeliveryRepository repo=new DeliveryRepository(wire); DeliveryData.PdfUpload input=draft(repo),prepared=repo.preparePdf(input); assertNotNull(prepared);
        JSONObject args=wire.edges.get(0); assertEquals(7,args.length()); assertEquals("request-file",wire.edgeName);
        assertEquals(id(60),args.getString("service_id")); assertEquals(id(70),args.getString("request_id")); assertEquals("deliverable",args.getString("kind"));
        assertTrue(args.isNull("requirement")); assertEquals("application/pdf",args.getString("mime_type")); assertEquals(4,args.getLong("size_bytes"));
        assertEquals(input.operationKey,args.getString("idempotency_key")); assertEquals(id(1),prepared.fileId); assertEquals(input.contentSha256,prepared.contentSha256);
    }
    @Test public void aDifferentOfficeOrMissingStaffScopeIsRejectedBeforePreparation() throws Exception {
        Wire wire=new Wire(); DeliveryRepository repo=new DeliveryRepository(wire);
        failure("FORBIDDEN",()->repo.pdfUpload(record(RequestStatus.PROCESSING,"digital",2),"OFF-GCC",options(2,true,4L),bytes(),null));
        failure("FORBIDDEN",()->repo.pdfUpload(record(RequestStatus.PROCESSING,"digital",2),null,options(2,true,4L),bytes(),null)); assertTrue(wire.edges.isEmpty());
    }
    @Test public void actualStateModeAndSameVersionOptionsGateThePdf() throws Exception {
        DeliveryRepository repo=new DeliveryRepository(new Wire());
        failure("INVALID_STATE",()->repo.pdfUpload(record(RequestStatus.SUBMITTED,"digital",2),"OFF-REG",options(2,true,4L),bytes(),null));
        failure("INVALID_STATE",()->repo.pdfUpload(record(RequestStatus.PROCESSING,"physical",2),"OFF-REG",options(2,true,4L),bytes(),null));
        failure("STALE_VERSION",()->repo.pdfUpload(record(RequestStatus.PROCESSING,"digital",2),"OFF-REG",options(1,true,4L),bytes(),null));
    }
    @Test public void unavailableMissingOrExceededPolicyCannotInventAnUploadLimit() throws Exception {
        DeliveryRepository repo=new DeliveryRepository(new Wire()); RequestData.Record record=record(RequestStatus.PROCESSING,"digital",2);
        failure("CONFIGURATION_REQUIRED",()->repo.pdfUpload(record,"OFF-REG",options(2,false,null),bytes(),null));
        failure("CONFIGURATION_REQUIRED",()->repo.pdfUpload(record,"OFF-REG",new RequestData.RequestOptions(id(70),2,Map.of()),bytes(),null));
        failure("UPLOAD_INVALID",()->repo.pdfUpload(record,"OFF-REG",options(2,true,3L),bytes(),null));
        failure("VALIDATION_FAILED",()->repo.pdfUpload(record,"OFF-REG",new RequestData.RequestOptions(id(70),2,Map.of("deliverable_upload",new RequestData.FileOptions(true,null,List.of("image/jpeg"),4L))),bytes(),null));
    }
    @Test public void preparationRejectsCrossOwnerRequestOrServiceAndPrivateObjectPaths() throws Exception {
        for(String key:List.of("owner_id","request_id","service_id","object_path")) {
            Wire wire=new Wire(); wire.prepared.put(key,key.equals("object_path")?"private-path":id(99)); DeliveryRepository repo=new DeliveryRepository(wire);
            failure(key.equals("object_path")?"VALIDATION_FAILED":"FORBIDDEN",()->repo.preparePdf(draft(repo)));
        }
    }
    @Test public void uploadRequiresThePreparedIdAndIdenticalBytesBeforeSending() throws Exception {
        Wire wire=new Wire(); DeliveryRepository repo=new DeliveryRepository(wire); DeliveryData.PdfUpload input=draft(repo); assertNotNull(input);
        failure("UPLOAD_INVALID",()->repo.uploadPdf(input,bytes())); DeliveryData.PdfUpload prepared=repo.preparePdf(input); assertNotNull(prepared);
        failure("UPLOAD_INVALID",()->repo.uploadPdf(prepared,new byte[]{4,3,2,1})); assertEquals(0,wire.uploads);
    }
    @Test public void onlyServerVerifiedMatchingMetadataCanPublishTheStaffPdf() throws Exception {
        Wire wire=new Wire(); DeliveryRepository repo=new DeliveryRepository(wire); DeliveryData.PdfUpload prepared=repo.preparePdf(draft(repo)); assertNotNull(prepared);
        byte[] bytes=bytes(); DeliveryData.File result=repo.uploadPdf(prepared,bytes); assertNotNull(result);
        assertEquals("request-file",wire.uploadName); assertEquals(id(1),wire.uploadId); assertEquals("application/pdf",wire.mime); assertNotSame(bytes,wire.sent); assertArrayEquals(bytes,wire.sent);
        assertEquals(id(90),result.ownerId); assertEquals(id(70),result.requestId); assertEquals(DeliveryData.FileKind.DELIVERABLE,result.kind); assertEquals("verified",result.verification);
    }
    @Test public void pendingOrDifferentFileUploadReceiptsAreNotSuccess() throws Exception {
        Wire wire=new Wire(); DeliveryRepository repo=new DeliveryRepository(wire); DeliveryData.PdfUpload prepared=repo.preparePdf(draft(repo)); assertNotNull(prepared);
        wire.verified.put("verification","pending"); failure("UPLOAD_INVALID",()->repo.uploadPdf(prepared,bytes()));
        wire.verified=metadata("verified").put("id",id(2)); failure("VALIDATION_FAILED",()->repo.uploadPdf(prepared,bytes()));
    }
    @Test public void aLostPreparationResponseRetriesTheExactOperationBody() throws Exception {
        Wire wire=new Wire(); DeliveryRepository repo=new DeliveryRepository(wire); DeliveryData.PdfUpload input=draft(repo); assertNotNull(input); wire.edgeError=new java.io.IOException("lost");
        try { repo.preparePdf(input); fail("lost response"); } catch(java.io.IOException expected) {}
        wire.edgeError=null; assertNotNull(repo.preparePdf(input)); assertEquals(wire.edges.get(0).toString(),wire.edges.get(1).toString());
    }
    @Test public void aLostUploadResponseRetainsTheSamePreparedFileAndBytes() throws Exception {
        Wire wire=new Wire(); DeliveryRepository repo=new DeliveryRepository(wire); DeliveryData.PdfUpload input=repo.preparePdf(draft(repo)); assertNotNull(input); wire.uploadError=new java.io.IOException("lost");
        try { repo.uploadPdf(input,bytes()); fail("lost response"); } catch(java.io.IOException expected) {}
        String first=wire.uploadId; byte[] firstBytes=wire.sent; wire.uploadError=null; assertNotNull(repo.uploadPdf(input,bytes()));
        assertEquals(first,wire.uploadId); assertArrayEquals(firstBytes,wire.sent); assertEquals(2,wire.uploads);
    }
    @Test public void replacementAccountCannotPrepareOrPublishAnOldActorIntent() throws Exception {
        Wire wire=new Wire(); DeliveryRepository repo=new DeliveryRepository(wire); DeliveryData.PdfUpload input=draft(repo); assertNotNull(input);
        wire.actor=id(99); failure("FORBIDDEN",()->repo.preparePdf(input)); assertTrue(wire.edges.isEmpty());
        wire.actor=id(91); wire.replaceOnEdge=true;
        try { repo.preparePdf(input); fail("Account replacement"); } catch(IllegalStateException expected) {}
    }
    private static DeliveryData.PdfUpload draft(DeliveryRepository repo) throws Exception { return repo.pdfUpload(record(RequestStatus.PROCESSING,"digital",2),"OFF-REG",options(2,true,4L),bytes(),null); }
    private static byte[] bytes() { return new byte[]{1,2,3,4}; }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static RequestData.Record record(RequestStatus status,String mode,long version) { return new RequestData.Record(id(70),"fixture-reference",id(90),"OFF-REG",mode,"2026-10-21T01:00:00Z",version,status,
        new RequestData.Service(id(60),"OFF-REG","Configured fixture",1,FeeDeclaration.FREE,BigDecimal.ZERO,null,List.of("digital","physical"),List.of(),true)); }
    private static RequestData.RequestOptions options(long version,boolean available,Long max) { return new RequestData.RequestOptions(id(70),version,Map.of("deliverable_upload",new RequestData.FileOptions(available,available?null:"CONFIGURATION_REQUIRED",available?List.of("application/pdf"):null,max))); }
    private static JSONObject metadata(String verification) throws Exception { return new JSONObject().put("id",id(1)).put("owner_id",id(90)).put("service_id",id(60)).put("request_id",id(70)).put("kind","deliverable").put("requirement",JSONObject.NULL)
        .put("mime_type","application/pdf").put("size_bytes",4).put("verification",verification).put("version",1).put("current",true).put("created_at","2026-10-21T01:00:00Z").put("deleted_at",JSONObject.NULL); }
    private interface Operation { void run() throws Exception; }
    private static void failure(String code,Operation op) throws Exception { try { op.run(); fail("Expected "+code); } catch(RequestRepository.WorkflowException expected) { assertEquals(code,expected.code); } }
    private static final class Wire implements DeliveryRepository.Transport {
        String actor=id(91),edgeName,uploadName,uploadId,mime; boolean current=true,replaceOnEdge; int uploads; byte[] sent;
        JSONObject prepared,verified; final List<JSONObject> edges=new ArrayList<>(); Exception edgeError,uploadError;
        Wire() throws Exception { prepared=metadata("pending"); verified=metadata("verified"); }
        public String accountId() { return actor; } public void requireCurrent() { if(!current)throw new IllegalStateException("Replaced account"); }
        public JSONArray get(String path) { return new JSONArray(); }
        public long downloadTo(String name,JSONObject args,OutputStream out,long max,String sha) { throw new AssertionError("No download in upload flow"); }
        public JSONObject edge(String name,JSONObject args) throws Exception { edgeName=name; edges.add(new JSONObject(args.toString())); if(edgeError!=null)throw edgeError; if(replaceOnEdge)current=false; return new JSONObject(prepared.toString()); }
        public JSONObject upload(String name,String fileId,byte[] bytes,String type) throws Exception { ++uploads; uploadName=name; uploadId=fileId; mime=type; sent=bytes; if(uploadError!=null)throw uploadError; return new JSONObject(verified.toString()); }
    }
}
