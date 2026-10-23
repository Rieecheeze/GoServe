package ph.edu.tip.goserve.data;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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

@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public final class DeliveryRepositoryTest {
    private static final String TIME="2026-10-21T01:00:00Z";
    @Test public void successfulEmptyFilePageDoesNotInventNamesOrFiles() throws Exception {
        DeliveryData.FilePage page=new DeliveryRepository(new Wire()).files(request(),"OFF-REG",DeliveryData.FileKind.EVIDENCE,null);
        assertNotNull(page); assertTrue(page.items.isEmpty()); assertNull(page.nextId);
    }
    @Test public void boundedFileQueryKeepsRequestOwnerServiceKindAndCursorWithoutPrivatePaths() throws Exception {
        Wire wire=new Wire(); wire.rows=new JSONArray(); for(int i=1;i<=50;i++)wire.rows.put(row(i));
        DeliveryRepository repo=new DeliveryRepository(wire); DeliveryData.FilePage first=repo.files(request(),"OFF-REG",DeliveryData.FileKind.EVIDENCE,null);
        assertEquals(id(50),first.nextId); wire.rows=new JSONArray(); repo.files(request(),"OFF-REG",DeliveryData.FileKind.EVIDENCE,first.nextId);
        String path=URLDecoder.decode(wire.path,StandardCharsets.UTF_8);
        for(String part:List.of("request_id=eq."+id(70),"owner_id=eq."+id(90),"service_id=eq."+id(60),"kind=eq.evidence",
                "current=eq.true","verification=eq.verified","deleted_at=is.null","id=gt."+id(50),"order=id.asc","limit=50"))assertTrue(part,path.contains(part));
        assertFalse(path.contains("object_path")); assertFalse(path.contains("select=*"));
    }
    @Test public void wrongOwnerOrOfficeScopeCannotListAnotherRequestsFiles() throws Exception {
        Wire wire=new Wire(); DeliveryRepository repo=new DeliveryRepository(wire);
        expect("FORBIDDEN",()->repo.files(request(),null,DeliveryData.FileKind.EVIDENCE,null));
        expect("FORBIDDEN",()->repo.files(request(),"OFF-ACC",DeliveryData.FileKind.EVIDENCE,null)); assertEquals(0,wire.reads);
        wire.actor=id(90); assertTrue(repo.files(request(),null,DeliveryData.FileKind.EVIDENCE,null).items.isEmpty());
    }
    @Test public void foreignOwnerRequestServiceOrKindNeverBecomesAnAccessibleFile() throws Exception {
        for(String key:List.of("owner_id","request_id","service_id","kind")) {
            Wire wire=new Wire(); wire.rows=new JSONArray().put(row(1).put(key,key.equals("kind")?"deliverable":id(99)));
            expect("FORBIDDEN",()->new DeliveryRepository(wire).files(request(),"OFF-REG",DeliveryData.FileKind.EVIDENCE,null));
        }
    }
    @Test public void unverifiedRetiredOrDeletedFilesCannotAppearAsCurrentEvidence() throws Exception {
        for(String key:List.of("verification","current","deleted_at")) {
            Wire wire=new Wire(); JSONObject value=row(1);
            value.put(key,key.equals("verification")?"pending":key.equals("current")?false:TIME); wire.rows=new JSONArray().put(value);
            expect("UPLOAD_INVALID",()->new DeliveryRepository(wire).files(request(),"OFF-REG",DeliveryData.FileKind.EVIDENCE,null));
        }
    }
    @Test public void malformedSizesOrUnexpectedPrivateMetadataFailValidation() throws Exception {
        for(Object size:List.of(0,-1,1.5,"4")) {
            Wire wire=new Wire(); wire.rows=new JSONArray().put(row(1).put("size_bytes",size));
            expect("VALIDATION_FAILED",()->new DeliveryRepository(wire).files(request(),"OFF-REG",DeliveryData.FileKind.EVIDENCE,null));
        }
        Wire wire=new Wire(); wire.rows=new JSONArray().put(row(1).put("object_path","private fixture path"));
        expect("VALIDATION_FAILED",()->new DeliveryRepository(wire).files(request(),"OFF-REG",DeliveryData.FileKind.EVIDENCE,null));
    }
    @Test public void onlySupportedEvidenceFormatsAndPdfDeliverablesCanBeListed() throws Exception {
        Wire wire=new Wire(); wire.rows=new JSONArray().put(row(1).put("mime_type","text/html")); DeliveryRepository repo=new DeliveryRepository(wire);
        expect("UPLOAD_INVALID",()->repo.files(request(),"OFF-REG",DeliveryData.FileKind.EVIDENCE,null));
        wire.rows=new JSONArray().put(row(1).put("kind","deliverable").put("mime_type","image/png"));
        expect("UPLOAD_INVALID",()->repo.files(request(),"OFF-REG",DeliveryData.FileKind.DELIVERABLE,null));
    }
    @Test public void cursorBoundaryOrderingAndDuplicateMetadataCannotCorruptThePage() throws Exception {
        Wire wire=new Wire(); DeliveryRepository repo=new DeliveryRepository(wire); wire.rows=new JSONArray().put(row(1));
        expect("STALE_RESPONSE",()->repo.files(request(),"OFF-REG",DeliveryData.FileKind.EVIDENCE,id(1)));
        wire.rows=new JSONArray().put(row(2)).put(row(1)); expect("STALE_RESPONSE",()->repo.files(request(),"OFF-REG",DeliveryData.FileKind.EVIDENCE,null));
        wire.rows=new JSONArray().put(row(1)).put(row(1)); expect("VALIDATION_FAILED",()->repo.files(request(),"OFF-REG",DeliveryData.FileKind.EVIDENCE,null));
    }
    @Test public void newerFileReadSupersedesAnOlderPendingPublication() throws Exception {
        Wire wire=new Wire(); wire.rows=new JSONArray().put(row(1)); wire.entered=new CountDownLatch(1); wire.release=new CountDownLatch(1);
        DeliveryRepository repo=new DeliveryRepository(wire); AtomicReference<Throwable> outcome=new AtomicReference<>();
        Thread old=new Thread(()->{try { repo.files(request(),"OFF-REG",DeliveryData.FileKind.EVIDENCE,null); }catch(Throwable e) { outcome.set(e); }}); old.start();
        try { assertTrue(wire.entered.await(2,TimeUnit.SECONDS)); assertEquals(1,repo.files(request(),"OFF-REG",DeliveryData.FileKind.EVIDENCE,null).items.size()); }
        finally { wire.release.countDown(); old.join(2000); }
        assertTrue(outcome.get() instanceof RequestRepository.WorkflowException); assertEquals("STALE_RESPONSE",((RequestRepository.WorkflowException)outcome.get()).code);
    }
    @Test public void evidenceReadUsesOnlyTheAuthorizedBinaryEdgeAndExactSizeBound() throws Exception {
        Wire wire=new Wire(); DeliveryRepository repo=new DeliveryRepository(wire); ByteArrayOutputStream out=new ByteArrayOutputStream();
        assertEquals(4,repo.readEvidence(file(DeliveryData.FileKind.EVIDENCE),out)); assertArrayEquals(new byte[]{1,2,3,4},out.toByteArray());
        assertEquals("request-file",wire.edge); assertEquals(2,wire.args.length()); assertEquals("download",wire.args.getString("kind"));
        assertEquals(id(1),wire.args.getString("file_id")); assertEquals(4,wire.maximum); assertNull(wire.sha); assertEquals(1,wire.downloads);
    }
    @Test public void truncatedEvidenceOrDeliverableShortcutNeverReportsSuccessfulDownload() throws Exception {
        Wire wire=new Wire(); wire.count=3; DeliveryRepository repo=new DeliveryRepository(wire);
        expect("UPLOAD_INVALID",()->repo.readEvidence(file(DeliveryData.FileKind.EVIDENCE),new ByteArrayOutputStream()));
        int count=wire.downloads; expect("FORBIDDEN",()->repo.readEvidence(file(DeliveryData.FileKind.DELIVERABLE),new ByteArrayOutputStream()));
        assertEquals(count,wire.downloads);
    }
    @Test public void accountReplacementAfterMetadataOrBytesSuppressesTheResult() throws Exception {
        Wire metadata=new Wire(); metadata.replaceDuringRead=true;
        try { new DeliveryRepository(metadata).files(request(),"OFF-REG",DeliveryData.FileKind.EVIDENCE,null); fail("Replaced account"); } catch(IllegalStateException expected) {}
        Wire bytes=new Wire(); bytes.replaceDuringDownload=true;
        try { new DeliveryRepository(bytes).readEvidence(file(DeliveryData.FileKind.EVIDENCE),new ByteArrayOutputStream()); fail("Replaced account"); } catch(IllegalStateException expected) {}
    }
    private static RequestData.Record request() {
        return new RequestData.Record(id(70),"fixture-reference",id(90),"OFF-REG","digital",TIME,1,RequestStatus.PROCESSING,
            new RequestData.Service(id(60),"OFF-REG","Configured fixture",1,FeeDeclaration.FREE,BigDecimal.ZERO,null,List.of("digital"),List.of("configured-requirement"),true));
    }
    private static JSONObject row(int n) throws Exception {
        return new JSONObject().put("id",id(n)).put("owner_id",id(90)).put("service_id",id(60)).put("request_id",id(70))
            .put("kind","evidence").put("requirement","configured-requirement").put("mime_type","application/pdf").put("size_bytes",4)
            .put("verification","verified").put("version",1).put("current",true).put("created_at",TIME).put("deleted_at",JSONObject.NULL);
    }
    private static DeliveryData.File file(DeliveryData.FileKind kind) {
        return new DeliveryData.File(id(1),id(90),id(60),id(70),kind,"configured-requirement","application/pdf",4,"verified",1,true,TIME,null);
    }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private interface Work { void run() throws Exception; }
    private static void expect(String code,Work work) throws Exception {
        try { work.run(); fail("Expected "+code); } catch(RequestRepository.WorkflowException error) { assertEquals(code,error.code); }
    }
    private static final class Wire implements DeliveryRepository.Transport {
        String actor=id(91),path,edge,sha; long maximum,count=4; JSONObject args; JSONArray rows=new JSONArray(); int reads,downloads;
        boolean current=true,replaceDuringRead,replaceDuringDownload; CountDownLatch entered,release;
        public String accountId() { return actor; } public void requireCurrent() { if(!current)throw new IllegalStateException("Account replaced"); }
        public JSONArray get(String path) throws Exception {
            int order; synchronized(this) { order=++reads; this.path=path; }
            if(entered!=null && order==1) { entered.countDown(); if(!release.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Fixture timeout"); }
            if(replaceDuringRead)current=false; return rows;
        }
        public long downloadTo(String edge,JSONObject args,OutputStream out,long maximum,String sha) throws Exception {
            ++downloads; this.edge=edge; this.args=args; this.maximum=maximum; this.sha=sha; out.write(new byte[]{1,2,3,4},0,(int)count);
            if(replaceDuringDownload)current=false; return count;
        }
    }
}
