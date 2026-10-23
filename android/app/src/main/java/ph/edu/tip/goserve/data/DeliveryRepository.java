package ph.edu.tip.goserve.data;

import java.io.OutputStream;
import android.net.Uri;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import ph.edu.tip.goserve.domain.DeliveryData;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.RequestStatus;

public final class DeliveryRepository {
    interface Transport {
        String accountId();
        void requireCurrent();
        JSONArray get(String path) throws Exception;
        default JSONObject edge(String name,JSONObject args) throws Exception { throw new UnsupportedOperationException(); }
        default JSONObject rpc(String name,JSONObject args) throws Exception { throw new UnsupportedOperationException(); }
        default JSONObject upload(String name,String fileId,byte[] bytes,String mimeType) throws Exception { throw new UnsupportedOperationException(); }
        long downloadTo(String edgeName,JSONObject args,OutputStream destination,long maximumBytes,String expectedSha256) throws Exception;
    }
    private static final String FILE_COLUMNS="id,owner_id,service_id,request_id,kind,requirement,mime_type,size_bytes,verification,version,current,created_at,deleted_at";
    private final Transport transport;
    private final AtomicLong fileLoads=new AtomicLong(),evidenceLoads=new AtomicLong();
    private final AtomicLong prepareLoads=new AtomicLong(),uploadLoads=new AtomicLong();
    private final AtomicLong downloadLoads=new AtomicLong(),transferLoads=new AtomicLong(),acknowledgementLoads=new AtomicLong();
    public DeliveryRepository(AccountGateway gateway) {
        this(new Transport() {
            public String accountId() { return gateway.lease().accountId; }
            public void requireCurrent() { gateway.requireCurrent(); }
            public JSONArray get(String path) throws Exception { return gateway.get(path); }
            public JSONObject edge(String name,JSONObject args) throws Exception { return gateway.edge(name,args); }
            public JSONObject rpc(String name,JSONObject args) throws Exception { return gateway.rpc(name,args); }
            public JSONObject upload(String name,String fileId,byte[] bytes,String mimeType) throws Exception { return gateway.upload(name,fileId,bytes,mimeType); }
            public long downloadTo(String edge,JSONObject args,OutputStream out,long maximum,String sha) throws Exception { return gateway.downloadTo(edge,args,out,maximum,sha); }
        });
    }
    DeliveryRepository(Transport transport) { this.transport=transport; }
    public DeliveryData.DownloadTicket beginDownload(RequestData.Record request,DeliveryData.File file) throws Exception {
        requireCurrent(); validateScope(request,null); validateDeliverable(file);
        if(!request.id.equals(file.requestId) || !request.snapshot.id.equals(file.serviceId) || !request.ownerId.equals(file.ownerId))throw failure("FORBIDDEN");
        if(!"digital".equals(request.deliveryMode) || request.status!=RequestStatus.READY_FOR_PICKUP_DOWNLOAD)throw failure("INVALID_STATE");
        long load=downloadLoads.incrementAndGet(); JSONObject row;
        try { row=transport.edge("download-deliverable",new JSONObject().put("kind","begin").put("file_id",file.id)); requireCurrent(); }
        catch(SupabaseClient.ApiException error) { requireCurrent(); throw failure(error.code); }
        DeliveryData.DownloadTicket ticket;
        try {
            exact(row,"download_id","file_id","expires_at","sha256");
            ticket=new DeliveryData.DownloadTicket(string(row,"download_id"),string(row,"file_id"),string(row,"expires_at"),string(row,"sha256"));
        } catch(JSONException error) { throw failure("VALIDATION_FAILED"); }
        validateTicket(file,ticket); check(downloadLoads,load); return ticket;
    }
    public long readDeliverable(DeliveryData.File file,DeliveryData.DownloadTicket ticket,OutputStream destination) throws Exception {
        requireCurrent(); validateDeliverable(file); validateTicket(file,ticket); if(destination==null)throw failure("VALIDATION_FAILED");
        long load=transferLoads.incrementAndGet(),count;
        JSONObject args=new JSONObject().put("kind","transfer").put("download_id",ticket.id);
        try { count=transport.downloadTo("download-deliverable",args,destination,file.sizeBytes,ticket.sha256); requireCurrent(); }
        catch(SupabaseClient.ApiException error) { requireCurrent(); throw failure(error.code); }
        if(count!=file.sizeBytes)throw failure("DOWNLOAD_UNCONFIRMED"); check(transferLoads,load); return count;
    }
    public PrivateDocument deliverableDocument(android.content.Context context,DeliveryData.File file,DeliveryData.DownloadTicket ticket) throws Exception {
        requireCurrent(); validateDeliverable(file); validateTicket(file,ticket);
        if(file.sizeBytes>context.getCacheDir().getUsableSpace())throw failure("DEVICE_STORAGE_LIMIT");
        PrivateDocument document=PrivateDocument.create(context,"application/pdf");
        try {
            try(OutputStream out=document.openOutput()) { readDeliverable(file,ticket,out); }
            requireCurrent(); return document;
        } catch(Exception error) { document.close(); throw error; }
    }
    public DeliveryData.Acknowledgement acknowledge(DeliveryData.File file,DeliveryData.DownloadTicket ticket) throws Exception {
        requireCurrent(); validateDeliverable(file); validateTicket(file,ticket);
        long load=acknowledgementLoads.incrementAndGet(); JSONObject row;
        try { row=transport.rpc("acknowledge_download",new JSONObject().put("p_download_id",ticket.id).put("p_sha256",ticket.sha256)); requireCurrent(); }
        catch(SupabaseClient.ApiException error) { requireCurrent(); throw failure(error.code); }
        DeliveryData.Acknowledgement result;
        try {
            exact(row,"download_id","confirmed_at"); result=new DeliveryData.Acknowledgement(string(row,"download_id"),string(row,"confirmed_at"));
        } catch(JSONException error) { throw failure("VALIDATION_FAILED"); }
        id(result.downloadId); if(!ticket.id.equals(result.downloadId))throw failure("VALIDATION_FAILED");
        try { Instant.parse(result.confirmedAt); } catch(Exception error) { throw failure("VALIDATION_FAILED"); }
        check(acknowledgementLoads,load); return result;
    }
    private void validateDeliverable(DeliveryData.File file) throws Exception {
        if(file==null)throw failure("VALIDATION_FAILED");
        if(file.kind!=DeliveryData.FileKind.DELIVERABLE || !transport.accountId().equals(file.ownerId))throw failure("FORBIDDEN");
        validateFile(file); if(file.requirement!=null)throw failure("UPLOAD_INVALID");
    }
    private static void validateTicket(DeliveryData.File file,DeliveryData.DownloadTicket ticket) throws Exception {
        if(ticket==null)throw failure("VALIDATION_FAILED"); id(ticket.id); id(ticket.fileId);
        if(!file.id.equals(ticket.fileId))throw failure("FORBIDDEN");
        if(ticket.sha256==null || !ticket.sha256.matches("[0-9a-f]{64}"))throw failure("VALIDATION_FAILED");
        try { Instant.parse(ticket.expiresAt); } catch(Exception error) { throw failure("VALIDATION_FAILED"); }
    }
    public DeliveryData.PdfUpload pdfUpload(RequestData.Record request,String officeId,RequestData.RequestOptions options,byte[] bytes,DeliveryData.PdfUpload previous) throws Exception {
        requireCurrent(); if(bytes==null)throw failure("UPLOAD_INVALID"); validatePdf(request,officeId,options,bytes.length);
        String actor=transport.accountId(); id(actor); String digest=sha256(bytes.clone()),key=java.util.UUID.randomUUID().toString(),fileId=null;
        if(previous!=null && previous.request!=null && previous.request.snapshot!=null && actor.equals(previous.actorId)
                && officeId.equals(previous.officeId) && request.id.equals(previous.request.id) && request.version==previous.request.version
                && request.snapshot.id.equals(previous.request.snapshot.id) && request.snapshot.version==previous.request.snapshot.version
                && request.ownerId.equals(previous.request.ownerId) && bytes.length==previous.sizeBytes && digest.equals(previous.contentSha256)) {
            id(previous.operationKey); if(previous.fileId!=null)id(previous.fileId); key=previous.operationKey; fileId=previous.fileId;
        }
        requireCurrent(); return new DeliveryData.PdfUpload(request,options,actor,officeId,bytes.length,digest,key,fileId);
    }
    public DeliveryData.PdfUpload preparePdf(DeliveryData.PdfUpload input) throws Exception {
        requireCurrent(); validatePdfInput(input); long load=prepareLoads.incrementAndGet();
        JSONObject args=new JSONObject().put("service_id",input.request.snapshot.id).put("request_id",input.request.id).put("kind","deliverable")
            .put("requirement",JSONObject.NULL).put("mime_type","application/pdf").put("size_bytes",input.sizeBytes).put("idempotency_key",input.operationKey);
        JSONObject row;
        try { row=transport.edge("request-file",args); requireCurrent(); }
        catch(SupabaseClient.ApiException error) { requireCurrent(); throw failure(error.code); }
        DeliveryData.File file=pdfReceipt(row,input,"pending"); check(prepareLoads,load);
        return new DeliveryData.PdfUpload(input.request,input.options,input.actorId,input.officeId,input.sizeBytes,input.contentSha256,input.operationKey,file.id);
    }
    public DeliveryData.File uploadPdf(DeliveryData.PdfUpload input,byte[] identicalBytes) throws Exception {
        requireCurrent(); validatePdfInput(input);
        if(input.fileId==null || identicalBytes==null)throw failure("UPLOAD_INVALID");
        byte[] bytes=identicalBytes.clone(); if(bytes.length!=input.sizeBytes || !sha256(bytes).equals(input.contentSha256))throw failure("UPLOAD_INVALID");
        long load=uploadLoads.incrementAndGet(); JSONObject row;
        try { row=transport.upload("request-file",input.fileId,bytes,"application/pdf"); requireCurrent(); }
        catch(SupabaseClient.ApiException error) { requireCurrent(); throw failure(error.code); }
        DeliveryData.File file=pdfReceipt(row,input,"verified"); check(uploadLoads,load); return file;
    }
    private void validatePdfInput(DeliveryData.PdfUpload input) throws Exception {
        if(input==null)throw failure("VALIDATION_FAILED");
        if(!transport.accountId().equals(input.actorId))throw failure("FORBIDDEN");
        validatePdf(input.request,input.officeId,input.options,input.sizeBytes); id(input.actorId); id(input.operationKey); if(input.fileId!=null)id(input.fileId);
        if(input.contentSha256==null || !input.contentSha256.matches("[0-9a-f]{64}"))throw failure("VALIDATION_FAILED");
    }
    private void validatePdf(RequestData.Record request,String officeId,RequestData.RequestOptions options,long size) throws Exception {
        if(officeId==null)throw failure("FORBIDDEN"); validateScope(request,officeId);
        if(!request.officeId.equals(request.snapshot.officeId))throw failure("FORBIDDEN");
        if(!"digital".equals(request.deliveryMode) || request.status!=RequestStatus.PROCESSING && request.status!=RequestStatus.READY_FOR_PICKUP_DOWNLOAD)throw failure("INVALID_STATE");
        if(options==null)throw failure("CONFIGURATION_REQUIRED");
        if(!request.id.equals(options.requestId))throw failure("FORBIDDEN"); if(request.version!=options.requestVersion)throw failure("STALE_VERSION");
        RequestData.ActionOptions action=options.actions.get("deliverable_upload");
        if(action==null)throw failure("CONFIGURATION_REQUIRED");
        if(!(action instanceof RequestData.FileOptions))throw failure("VALIDATION_FAILED");
        RequestData.FileOptions file=(RequestData.FileOptions)action;
        if(!file.available) {
            if(file.mimeTypes!=null || file.maxBytes!=null || !java.util.Arrays.asList("CONFIGURATION_REQUIRED","POLICY_BLOCKED","INVALID_STATE","PAYMENT_REQUIRED").contains(file.blocker))throw failure("VALIDATION_FAILED");
            throw failure(file.blocker);
        }
        if(file.blocker!=null || file.maxBytes==null || file.maxBytes<1 || file.maxBytes>9007199254740991L
            || file.mimeTypes==null || !file.mimeTypes.equals(List.of("application/pdf")))throw failure("VALIDATION_FAILED");
        if(size<1 || size>file.maxBytes)throw failure("UPLOAD_INVALID");
    }
    private static DeliveryData.File pdfReceipt(JSONObject row,DeliveryData.PdfUpload input,String verification) throws Exception {
        DeliveryData.File file;
        try { file=parseFile(row); } catch(JSONException error) { throw failure("VALIDATION_FAILED"); }
        if(!input.request.id.equals(file.requestId) || !input.request.ownerId.equals(file.ownerId) || !input.request.snapshot.id.equals(file.serviceId)
            || file.kind!=DeliveryData.FileKind.DELIVERABLE)throw failure("FORBIDDEN");
        validateFile(file,verification);
        if(file.requirement!=null || file.sizeBytes!=input.sizeBytes)throw failure("UPLOAD_INVALID");
        if(input.fileId!=null && !input.fileId.equals(file.id))throw failure("VALIDATION_FAILED"); return file;
    }
    private static String sha256(byte[] bytes) throws Exception {
        byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(bytes); StringBuilder result=new StringBuilder(64);
        for(byte value:hash)result.append(Character.forDigit((value&255)>>>4,16)).append(Character.forDigit(value&15,16)); return result.toString();
    }
    public DeliveryData.FilePage files(RequestData.Record request,String officeId,DeliveryData.FileKind kind,String afterId) throws Exception {
        requireCurrent(); validateScope(request,officeId);
        if(kind==null)throw failure("VALIDATION_FAILED"); if(afterId!=null)id(afterId);
        long load=fileLoads.incrementAndGet();
        Uri.Builder query=Uri.parse("/rest/v1/request_files").buildUpon().appendQueryParameter("select",FILE_COLUMNS)
            .appendQueryParameter("limit","50").appendQueryParameter("order","id.asc")
            .appendQueryParameter("request_id","eq."+request.id).appendQueryParameter("owner_id","eq."+request.ownerId)
            .appendQueryParameter("service_id","eq."+request.snapshot.id).appendQueryParameter("kind","eq."+kind.wireValue)
            .appendQueryParameter("current","eq.true").appendQueryParameter("verification","eq.verified").appendQueryParameter("deleted_at","is.null");
        if(afterId!=null)query.appendQueryParameter("id","gt."+afterId);
        JSONArray rows;
        try { rows=transport.get(query.build().toString()); requireCurrent(); }
        catch(SupabaseClient.ApiException error) { requireCurrent(); throw failure(error.code); }
        if(rows==null || rows.length()>50)throw failure("VALIDATION_FAILED");
        List<DeliveryData.File> result=new ArrayList<>(); String last=afterId;
        for(int i=0;i<rows.length();i++) {
            DeliveryData.File file;
            try { file=parseFile(rows.getJSONObject(i)); } catch(JSONException error) { throw failure("VALIDATION_FAILED"); }
            if(!request.id.equals(file.requestId) || !request.ownerId.equals(file.ownerId) || !request.snapshot.id.equals(file.serviceId) || file.kind!=kind)throw failure("FORBIDDEN");
            validateFile(file);
            if(kind==DeliveryData.FileKind.EVIDENCE && file.requirement!=null && !request.snapshot.requirementIds.contains(file.requirement))throw failure("UPLOAD_INVALID");
            if(last!=null && file.id.equalsIgnoreCase(last) && i>0)throw failure("VALIDATION_FAILED");
            if(last!=null && file.id.compareToIgnoreCase(last)<=0)throw failure("STALE_RESPONSE");
            result.add(file); last=file.id;
        }
        check(fileLoads,load); return new DeliveryData.FilePage(result,rows.length()==50?last:null);
    }
    public long readEvidence(DeliveryData.File file,OutputStream destination) throws Exception {
        requireCurrent(); if(file==null || destination==null)throw failure("VALIDATION_FAILED");
        if(file.kind!=DeliveryData.FileKind.EVIDENCE)throw failure("FORBIDDEN"); validateFile(file);
        long load=evidenceLoads.incrementAndGet(),count;
        JSONObject args=new JSONObject().put("kind","download").put("file_id",file.id);
        try { count=transport.downloadTo("request-file",args,destination,file.sizeBytes,null); requireCurrent(); }
        catch(SupabaseClient.ApiException error) { requireCurrent(); throw failure(error.code); }
        if(count!=file.sizeBytes)throw failure("UPLOAD_INVALID");
        check(evidenceLoads,load); return count;
    }
    public PrivateDocument evidenceDocument(android.content.Context context,DeliveryData.File file) throws Exception {
        requireCurrent(); if(file==null)throw failure("VALIDATION_FAILED");
        if(file.kind!=DeliveryData.FileKind.EVIDENCE)throw failure("FORBIDDEN"); validateFile(file);
        if(file.sizeBytes>context.getCacheDir().getUsableSpace())throw failure("DEVICE_STORAGE_LIMIT");
        PrivateDocument document=PrivateDocument.create(context,file.mimeType);
        try {
            try(OutputStream out=document.openOutput()) { readEvidence(file,out); }
            requireCurrent(); return document;
        } catch(Exception error) { document.close(); throw error; }
    }
    public void requireCurrent() { transport.requireCurrent(); }
    private void validateScope(RequestData.Record request,String officeId) throws Exception {
        if(request==null || request.snapshot==null || request.version<1)throw failure("VALIDATION_FAILED");
        id(request.id); id(request.ownerId); id(request.snapshot.id);
        if(officeId==null?!transport.accountId().equals(request.ownerId):!officeId.equals(request.officeId))throw failure("FORBIDDEN");
    }
    private static DeliveryData.File parseFile(JSONObject row) throws Exception {
        exact(row,FILE_COLUMNS.split(",")); String wire=string(row,"kind");
        DeliveryData.FileKind kind="evidence".equals(wire)?DeliveryData.FileKind.EVIDENCE:"deliverable".equals(wire)?DeliveryData.FileKind.DELIVERABLE:null;
        if(kind==null)throw failure("VALIDATION_FAILED");
        return new DeliveryData.File(string(row,"id"),string(row,"owner_id"),string(row,"service_id"),string(row,"request_id"),kind,
            nullable(row,"requirement"),string(row,"mime_type"),integer(row,"size_bytes"),string(row,"verification"),integer(row,"version"),
            bool(row,"current"),string(row,"created_at"),nullable(row,"deleted_at"));
    }
    private static void validateFile(DeliveryData.File file) throws Exception {
        validateFile(file,"verified");
    }
    private static void validateFile(DeliveryData.File file,String verification) throws Exception {
        if(file==null || file.kind==null)throw failure("VALIDATION_FAILED");
        id(file.id); id(file.ownerId); id(file.serviceId); id(file.requestId);
        if(file.sizeBytes<1 || file.sizeBytes>9007199254740991L || file.version<1 || file.version>9007199254740991L)throw failure("VALIDATION_FAILED");
        try { Instant.parse(file.createdAt); } catch(Exception error) { throw failure("VALIDATION_FAILED"); }
        if(!file.current || !verification.equals(file.verification) || file.deletedAt!=null)throw failure("UPLOAD_INVALID");
        if(!"application/pdf".equals(file.mimeType) && (file.kind==DeliveryData.FileKind.DELIVERABLE
                || !"image/png".equals(file.mimeType) && !"image/jpeg".equals(file.mimeType)))throw failure("UPLOAD_INVALID");
    }
    private void check(AtomicLong sequence,long expected) throws Exception {
        requireCurrent(); if(sequence.get()!=expected)throw failure("STALE_RESPONSE");
    }
    private static void exact(JSONObject row,String... keys) throws Exception {
        if(row==null || row.length()!=keys.length)throw failure("VALIDATION_FAILED");
        for(String key:keys)if(!row.has(key))throw failure("VALIDATION_FAILED");
    }
    private static String string(JSONObject row,String key) throws Exception {
        Object value=row.get(key); if(!(value instanceof String))throw failure("VALIDATION_FAILED"); return (String)value;
    }
    private static String nullable(JSONObject row,String key) throws Exception { return row.get(key)==JSONObject.NULL?null:string(row,key); }
    private static boolean bool(JSONObject row,String key) throws Exception {
        Object value=row.get(key); if(!(value instanceof Boolean))throw failure("VALIDATION_FAILED"); return (Boolean)value;
    }
    private static long integer(JSONObject row,String key) throws Exception {
        Object value=row.get(key); if(!(value instanceof Number))throw failure("VALIDATION_FAILED");
        try { long parsed=new BigDecimal(value.toString()).longValueExact(); if(parsed<1 || parsed>9007199254740991L)throw failure("VALIDATION_FAILED"); return parsed; }
        catch(ArithmeticException | NumberFormatException error) { throw failure("VALIDATION_FAILED"); }
    }
    private static void id(String value) throws Exception {
        if(value==null || !value.matches("[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}"))throw failure("VALIDATION_FAILED");
    }
    private static RequestRepository.WorkflowException failure(String code) { return new RequestRepository.WorkflowException(code); }
}
