package ph.edu.tip.goserve.data;

import android.app.Application;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.domain.DeliveryData;
import ph.edu.tip.goserve.domain.FeeDeclaration;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.RequestStatus;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=Application.class,shadows={AndroidFileProviderPaths.class},instrumentedPackages={"androidx.core.content"})
public final class DigitalDeliveryTest {
    @Before public void reset() throws Exception { AndroidFileProviderPaths.reset(); Shadows.shadowOf(android.webkit.MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("pdf","application/pdf"); }
    @Test public void beginUsesOnlyTheExactPrivateFileIdContractAndValidatesTheTicket() throws Exception {
        Wire wire=new Wire(); DeliveryData.DownloadTicket ticket=new DeliveryRepository(wire).beginDownload(record(RequestStatus.READY_FOR_PICKUP_DOWNLOAD,"digital"),file()); assertNotNull(ticket);
        assertEquals("download-deliverable",wire.edgeName); assertEquals(2,wire.args.length()); assertEquals("begin",wire.args.getString("kind")); assertEquals(id(5),wire.args.getString("file_id"));
        assertEquals(id(70),ticket.id); assertEquals(id(5),ticket.fileId); assertEquals("a".repeat(64),ticket.sha256); assertEquals(0,wire.acks); assertEquals(0,wire.transfers);
    }
    @Test public void anotherOwnerOrRequestCannotBeginThePrivateDownload() throws Exception {
        Wire wire=new Wire(); DeliveryRepository repo=new DeliveryRepository(wire); wire.actor=id(99);
        failure("FORBIDDEN",()->repo.beginDownload(record(RequestStatus.READY_FOR_PICKUP_DOWNLOAD,"digital"),file())); assertEquals(0,wire.begins);
        wire.actor=id(90); DeliveryData.File original=file(); DeliveryData.File other=new DeliveryData.File(original.id,original.ownerId,original.serviceId,id(99),original.kind,null,original.mimeType,original.sizeBytes,"verified",1,true,original.createdAt,null);
        failure("FORBIDDEN",()->repo.beginDownload(record(RequestStatus.READY_FOR_PICKUP_DOWNLOAD,"digital"),other)); assertEquals(0,wire.begins);
    }
    @Test public void onlyReadyDigitalRequestsCanBeginAndEvidenceCannotUseTheShortcut() throws Exception {
        Wire wire=new Wire(); DeliveryRepository repo=new DeliveryRepository(wire);
        failure("INVALID_STATE",()->repo.beginDownload(record(RequestStatus.PROCESSING,"digital"),file()));
        failure("INVALID_STATE",()->repo.beginDownload(record(RequestStatus.READY_FOR_PICKUP_DOWNLOAD,"physical"),file()));
        DeliveryData.File original=file(); DeliveryData.File evidence=new DeliveryData.File(original.id,original.ownerId,original.serviceId,original.requestId,DeliveryData.FileKind.EVIDENCE,null,original.mimeType,original.sizeBytes,"verified",1,true,original.createdAt,null);
        failure("FORBIDDEN",()->repo.beginDownload(record(RequestStatus.READY_FOR_PICKUP_DOWNLOAD,"digital"),evidence)); assertEquals(0,wire.begins);
    }
    @Test public void malformedOrCrossFileTicketsNeverPublishADownload() throws Exception {
        for(String field:List.of("download_id","file_id","expires_at","sha256","object_path")) {
            Wire wire=new Wire(); wire.ticket.put(field,field.equals("file_id")?id(99):"invalid"); DeliveryRepository repo=new DeliveryRepository(wire);
            failure(field.equals("file_id")?"FORBIDDEN":"VALIDATION_FAILED",()->repo.beginDownload(record(RequestStatus.READY_FOR_PICKUP_DOWNLOAD,"digital"),file()));
        }
    }
    @Test public void completeTransferPassesExactByteAndDigestBoundsWithoutAcknowledging() throws Exception {
        Wire wire=new Wire(); DeliveryRepository repo=new DeliveryRepository(wire); ByteArrayOutputStream out=new ByteArrayOutputStream();
        assertEquals(4,repo.readDeliverable(file(),ticket(),out)); assertEquals("download-deliverable",wire.transferName);
        assertEquals(2,wire.transferArgs.length()); assertEquals("transfer",wire.transferArgs.getString("kind")); assertEquals(id(70),wire.transferArgs.getString("download_id"));
        assertEquals(4,wire.maximum); assertEquals("a".repeat(64),wire.digest); assertArrayEquals(new byte[]{1,2,3,4},out.toByteArray()); assertEquals(0,wire.acks);
    }
    @Test public void ticketForAnotherFileIsRejectedBeforeTransferring() throws Exception {
        Wire wire=new Wire(); DeliveryData.DownloadTicket wrong=new DeliveryData.DownloadTicket(id(70),id(99),ticket().expiresAt,ticket().sha256);
        failure("FORBIDDEN",()->new DeliveryRepository(wire).readDeliverable(file(),wrong,new ByteArrayOutputStream())); assertEquals(0,wire.transfers);
    }
    @Test public void truncatedTransferCannotBecomeACompletePrivateCopy() throws Exception {
        Wire wire=new Wire(); wire.count=3;
        failure("DOWNLOAD_UNCONFIRMED",()->new DeliveryRepository(wire).readDeliverable(file(),ticket(),new ByteArrayOutputStream())); assertEquals(0,wire.acks);
    }
    @Test public void verifiedTransferReturnsOnlyAnOwnedPrivateCopy() throws Exception {
        Wire wire=new Wire(); android.content.Context app=RuntimeEnvironment.getApplication(); android.net.Uri uri;
        try(PrivateDocument document=new DeliveryRepository(wire).deliverableDocument(app,file(),ticket())) {
            assertNotNull(document); uri=document.uri(); assertEquals(app.getPackageName()+".documents",uri.getAuthority());
            try(java.io.InputStream in=app.getContentResolver().openInputStream(uri)) { assertArrayEquals(new byte[]{1,2,3,4},in.readAllBytes()); }
        }
        try { app.getContentResolver().openInputStream(uri); fail("Owned transfer must be removed on close"); } catch(java.io.FileNotFoundException expected) {}
        assertEquals(0,wire.acks);
    }
    @Test public void failedTransferDeletesItsPartialCopyAndDoesNotAcknowledge() throws Exception {
        android.content.Context app=RuntimeEnvironment.getApplication(); int before=count(app); Wire wire=new Wire(); wire.count=3;
        failure("DOWNLOAD_UNCONFIRMED",()->new DeliveryRepository(wire).deliverableDocument(app,file(),ticket())); assertEquals(before,count(app)); assertEquals(0,wire.acks);
    }
    @Test public void acknowledgementUsesTheSameTicketAndDigestAndValidatesItsReceipt() throws Exception {
        Wire wire=new Wire(); DeliveryData.Acknowledgement ack=new DeliveryRepository(wire).acknowledge(file(),ticket()); assertNotNull(ack);
        assertEquals("acknowledge_download",wire.rpcName); assertEquals(2,wire.args.length()); assertEquals(id(70),wire.args.getString("p_download_id")); assertEquals("a".repeat(64),wire.args.getString("p_sha256"));
        assertEquals(id(70),ack.downloadId); assertEquals("2026-10-22T01:00:00Z",ack.confirmedAt); assertEquals(0,wire.begins); assertEquals(0,wire.transfers);
    }
    @Test public void wrongOrMalformedAcknowledgementIsNotSavedConfirmation() throws Exception {
        Wire wire=new Wire(); DeliveryRepository repo=new DeliveryRepository(wire); wire.ack.put("download_id",id(99)); failure("VALIDATION_FAILED",()->repo.acknowledge(file(),ticket()));
        wire.ack=new JSONObject().put("download_id",id(70)).put("confirmed_at","invalid"); failure("VALIDATION_FAILED",()->repo.acknowledge(file(),ticket()));
    }
    @Test public void acknowledgementDoesNotInventAnExpiryRuleMissingFromTheActualSql() throws Exception {
        Wire wire=new Wire(); DeliveryData.DownloadTicket transferred=new DeliveryData.DownloadTicket(id(70),id(5),"2020-01-01T00:00:00Z","a".repeat(64));
        assertNotNull(new DeliveryRepository(wire).acknowledge(file(),transferred)); assertEquals(1,wire.acks);
    }
    @Test public void lostAcknowledgementResponseRetriesTheSameTicketWithoutAnotherTransfer() throws Exception {
        Wire wire=new Wire(); DeliveryRepository repo=new DeliveryRepository(wire); wire.rpcError=new java.io.IOException("lost");
        try { repo.acknowledge(file(),ticket()); fail("lost response"); } catch(java.io.IOException expected) {}
        String first=wire.args.toString(); wire.rpcError=null; assertNotNull(repo.acknowledge(file(),ticket())); assertEquals(first,wire.args.toString()); assertEquals(2,wire.acks); assertEquals(0,wire.transfers);
    }
    @Test public void replacementAccountSuppressesTicketsAndUnpublishedPrivateCopies() throws Exception {
        Wire wire=new Wire(); DeliveryRepository repo=new DeliveryRepository(wire); wire.revokeOnEdge=true;
        try { repo.beginDownload(record(RequestStatus.READY_FOR_PICKUP_DOWNLOAD,"digital"),file()); fail("Replaced lease"); } catch(IllegalStateException expected) {}
        Wire transfer=new Wire(); transfer.revokeOnTransfer=true; android.content.Context app=RuntimeEnvironment.getApplication(); int before=count(app);
        try { new DeliveryRepository(transfer).deliverableDocument(app,file(),ticket()); fail("Replaced lease"); } catch(IllegalStateException expected) {}
        assertEquals(before,count(app)); assertEquals(0,transfer.acks);
    }
    private static int count(android.content.Context app) { java.io.File[] files=new java.io.File(app.getCacheDir(),"request-review").listFiles(); return files==null?0:files.length; }
    private static DeliveryData.DownloadTicket ticket() { return new DeliveryData.DownloadTicket(id(70),id(5),"2026-10-22T01:00:00Z","a".repeat(64)); }
    private static DeliveryData.File file() { return new DeliveryData.File(id(5),id(90),id(50),id(1),DeliveryData.FileKind.DELIVERABLE,null,"application/pdf",4,"verified",1,true,"2026-10-22T00:00:00Z",null); }
    private static RequestData.Record record(RequestStatus status,String mode) { return new RequestData.Record(id(1),"fixture-reference",id(90),"OFF-REG",mode,"2026-10-21T01:00:00Z",2,status,new RequestData.Service(id(50),"OFF-REG","Configured fixture",1,FeeDeclaration.FREE,BigDecimal.ZERO,null,List.of("digital","physical"),List.of(),true)); }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private interface Work { void run() throws Exception; }
    private static void failure(String code,Work work) throws Exception { try { work.run(); fail("Expected "+code); } catch(RequestRepository.WorkflowException expected) { assertEquals(code,expected.code); } }
    private static final class Wire implements DeliveryRepository.Transport {
        String actor=id(90),edgeName,rpcName,transferName,digest; JSONObject args,transferArgs,ticket,ack; int begins,transfers,acks,count=4; long maximum;
        boolean current=true,revokeOnEdge,revokeOnTransfer; Exception rpcError;
        Wire() throws Exception { ticket=new JSONObject().put("download_id",id(70)).put("file_id",id(5)).put("expires_at","2026-10-22T01:00:00Z").put("sha256","a".repeat(64)); ack=new JSONObject().put("download_id",id(70)).put("confirmed_at","2026-10-22T01:00:00Z"); }
        public String accountId() { return actor; } public void requireCurrent() { if(!current)throw new IllegalStateException("Replaced lease"); } public JSONArray get(String path) { return new JSONArray(); }
        public JSONObject edge(String name,JSONObject args) throws Exception { ++begins; edgeName=name; this.args=new JSONObject(args.toString()); if(revokeOnEdge)current=false; return new JSONObject(ticket.toString()); }
        public long downloadTo(String name,JSONObject args,OutputStream out,long maximum,String sha) throws Exception { ++transfers; transferName=name; transferArgs=new JSONObject(args.toString()); this.maximum=maximum; digest=sha; out.write(new byte[]{1,2,3,4},0,count); if(revokeOnTransfer)current=false; return count; }
        public JSONObject rpc(String name,JSONObject args) throws Exception { ++acks; rpcName=name; this.args=new JSONObject(args.toString()); if(rpcError!=null)throw rpcError; return new JSONObject(ack.toString()); }
    }
}
