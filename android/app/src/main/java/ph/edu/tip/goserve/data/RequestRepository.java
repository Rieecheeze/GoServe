package ph.edu.tip.goserve.data;

import android.net.Uri;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import ph.edu.tip.goserve.domain.FeeDeclaration;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.RequestStatus;
import ph.edu.tip.goserve.domain.DeliveryData;

public final class RequestRepository {
    interface Transport {
        String accountId();
        void requireCurrent();
        JSONArray get(String path) throws Exception;
        JSONObject rpc(String name, JSONObject args) throws Exception;
        default JSONObject edge(String name, JSONObject args) throws Exception { throw new UnsupportedOperationException(); }
        default JSONObject upload(String name, String fileId, byte[] bytes, String mimeType) throws Exception { throw new UnsupportedOperationException(); }
    }

    public static final class WorkflowException extends Exception {
        public final String code;
        public WorkflowException(String code) { super(code); this.code=code; }
    }

    private static final String SERVICE_COLUMNS="id,office_id,name,delivery_modes,requirements,fee_state,amount,currency,active,version,is_demo";
    private static final String REQUEST_COLUMNS="id,reference,owner_id,office_id,service_id,snapshot,delivery_mode,notes,status,version,created_at,terminal_at";
    private static final String FILE_COLUMNS="id,owner_id,service_id,request_id,kind,requirement,mime_type,size_bytes,verification,version,current,created_at,deleted_at";
    private final Transport transport;
    private final AtomicLong receiptLoads=new AtomicLong();
    private final AtomicLong claimLoads=new AtomicLong(),validationLoads=new AtomicLong(),handoverLoads=new AtomicLong();
    private final AtomicLong catalogLoads=new AtomicLong(), requestLoads=new AtomicLong(), requestDetailLoads=new AtomicLong(),
            quoteLoads=new AtomicLong(), evidenceLoads=new AtomicLong(), submissionLoads=new AtomicLong(),
            quoteOptionLoads=new AtomicLong(), requestOptionLoads=new AtomicLong(),
            prepareLoads=new AtomicLong(), uploadLoads=new AtomicLong(), transitionLoads=new AtomicLong();

    public RequestRepository(AccountGateway gateway) {
        this(new Transport() {
            @Override public String accountId() { return gateway.lease().accountId; }
            @Override public void requireCurrent() { gateway.requireCurrent(); }
            @Override public JSONArray get(String path) throws Exception { return gateway.get(path); }
            @Override public JSONObject rpc(String name, JSONObject args) throws Exception { return gateway.rpc(name,args); }
            @Override public JSONObject edge(String name, JSONObject args) throws Exception { return gateway.edge(name,args); }
            @Override public JSONObject upload(String name, String fileId, byte[] bytes, String mimeType) throws Exception {
                return gateway.upload(name,fileId,bytes,mimeType);
            }
        });
    }
    RequestRepository(Transport transport) { this.transport=transport; }
    public DeliveryData.PickupClaim pickupClaim(RequestData.Record request,RequestData.RequestOptions options) throws Exception {
        requireCurrent(); validatePhysical(request);
        if(!transport.accountId().equals(request.ownerId))throw failure("FORBIDDEN");
        if(options==null)throw failure("CONFIGURATION_REQUIRED");
        if(!request.id.equals(options.requestId))throw failure("FORBIDDEN");
        if(request.version!=options.requestVersion)throw failure("STALE_VERSION");
        RequestData.ActionOptions action=options.actions.get("pickup_claim");
        if(action==null)throw failure("CONFIGURATION_REQUIRED"); validateAction(action); if(!action.available)throw failure(action.blocker);
        long load=claimLoads.incrementAndGet(); JSONObject row=call("pickup_claim",new JSONObject().put("p_request_id",request.id));
        try {
            exact(row,"id","request_id","display_code","qr_payload","expires_at");
            String claim=string(row,"id"),requestId=string(row,"request_id"),code=string(row,"display_code"),payload=string(row,"qr_payload"),expires=string(row,"expires_at");
            id(claim); id(requestId); timestamp(expires);
            if(!request.id.equals(requestId))throw failure("FORBIDDEN");
            if(!code.matches("[0-9a-f]{64}") || !code.equals(payload))throw failure("VALIDATION_FAILED");
            check(claimLoads,load); return new DeliveryData.PickupClaim(claim,requestId,code,payload,expires);
        } catch(JSONException malformed) { throw failure("VALIDATION_FAILED"); }
    }
    public DeliveryData.PickupReview pickupValidate(String code,String officeId) throws Exception {
        requireCurrent(); if(officeId==null)throw failure("FORBIDDEN"); office(officeId);
        if(code==null || !code.matches("[0-9a-f]{64}"))throw failure("CODE_INVALID");
        long load=validationLoads.incrementAndGet(); JSONObject row=call("pickup_validate",new JSONObject().put("p_code",code));
        try {
            exact(row,"validation_id","request_id","reference","office_id","request_version","claim_expires_at");
            String validation=string(row,"validation_id"),requestId=string(row,"request_id"),reference=string(row,"reference"),assigned=string(row,"office_id"),expires=string(row,"claim_expires_at");
            id(validation); id(requestId); timestamp(expires); long version=integer(row,"request_version");
            if(!officeId.equals(assigned))throw failure("FORBIDDEN");
            RequestData.Record current=request(requestId,officeId); validatePhysical(current);
            if(!reference.equals(current.reference))throw failure("FORBIDDEN"); if(version!=current.version)throw failure("STALE_VERSION");
            check(validationLoads,load); return new DeliveryData.PickupReview(validation,transport.accountId(),officeId,expires,current);
        } catch(JSONException malformed) { throw failure("VALIDATION_FAILED"); }
    }
    public DeliveryData.Handover handoverDraft(DeliveryData.PickupReview review,DeliveryData.Handover previous) throws Exception {
        requireCurrent(); validatePickupReview(review); String key=UUID.randomUUID().toString();
        if(previous!=null && previous.review!=null && previous.review.request!=null && previous.review.request.snapshot!=null
            && review.actorId.equals(previous.review.actorId) && review.officeId.equals(previous.review.officeId) && review.validationId.equals(previous.review.validationId)
            && review.request.version==previous.review.request.version && sameRequestIdentity(review.request,previous.review.request)) {
            id(previous.operationKey); key=previous.operationKey;
        }
        requireCurrent(); return new DeliveryData.Handover(review,key);
    }
    public RequestData.Record pickupHandover(DeliveryData.Handover input) throws Exception {
        requireCurrent(); if(input==null)throw failure("VALIDATION_FAILED"); validatePickupReview(input.review); id(input.operationKey);
        DeliveryData.PickupReview review=input.review; long load=handoverLoads.incrementAndGet();
        JSONObject args=new JSONObject().put("p_validation_id",review.validationId).put("p_expected_version",review.request.version).put("p_idempotency_key",input.operationKey);
        RequestData.Record receipt=parseRecord(call("pickup_handover",args));
        if(!sameRequestIdentity(review.request,receipt))throw failure("FORBIDDEN");
        if(receipt.status!=RequestStatus.COMPLETED || receipt.version!=review.request.version+1)throw failure("STALE_RESPONSE");
        RequestData.Record current=request(review.request.id,review.officeId);
        if(!sameRequestIdentity(review.request,current))throw failure("FORBIDDEN");
        if(current.status!=RequestStatus.COMPLETED || current.version<receipt.version)throw failure("STALE_RESPONSE");
        check(handoverLoads,load); return current;
    }
    private void validatePickupReview(DeliveryData.PickupReview review) throws Exception {
        if(review==null)throw failure("VALIDATION_FAILED"); validatePhysical(review.request); id(review.validationId); id(review.actorId); office(review.officeId); timestamp(review.expiresAt);
        if(!transport.accountId().equals(review.actorId) || !review.officeId.equals(review.request.officeId))throw failure("FORBIDDEN");
    }
    private static void validatePhysical(RequestData.Record request) throws Exception {
        if(request==null || request.snapshot==null || request.version<1)throw failure("VALIDATION_FAILED");
        id(request.id); id(request.ownerId); id(request.snapshot.id); office(request.officeId);
        if(!"physical".equals(request.deliveryMode) || request.status!=RequestStatus.READY_FOR_PICKUP_DOWNLOAD)throw failure("INVALID_STATE");
    }
    public DeliveryData.Receipt receiptDraft(RequestData.Record request,DeliveryData.Receipt previous) throws Exception {
        requireCurrent(); validateReceipt(request); String actor=transport.accountId(),key=UUID.randomUUID().toString();
        if(previous!=null && previous.request!=null && previous.request.snapshot!=null && actor.equals(previous.actorId)
            && request.version==previous.request.version && sameRequestIdentity(request,previous.request)) {
            id(previous.operationKey); key=previous.operationKey;
        }
        requireCurrent(); return new DeliveryData.Receipt(request,actor,key);
    }
    public RequestData.Record confirmReceipt(DeliveryData.Receipt input) throws Exception {
        requireCurrent(); if(input==null)throw failure("VALIDATION_FAILED");
        if(!transport.accountId().equals(input.actorId))throw failure("FORBIDDEN"); validateReceipt(input.request); id(input.operationKey);
        long load=receiptLoads.incrementAndGet();
        JSONObject args=new JSONObject().put("p_request_id",input.request.id).put("p_expected_version",input.request.version).put("p_idempotency_key",input.operationKey);
        RequestData.Record receipt=parseRecord(call("confirm_receipt",args));
        if(!sameRequestIdentity(input.request,receipt))throw failure("FORBIDDEN");
        if(receipt.status!=RequestStatus.COMPLETED || receipt.version!=input.request.version+1)throw failure("STALE_RESPONSE");
        RequestData.Record current=request(input.request.id,null);
        if(!sameRequestIdentity(input.request,current))throw failure("FORBIDDEN");
        if(current.status!=RequestStatus.COMPLETED || current.version<receipt.version)throw failure("STALE_RESPONSE");
        check(receiptLoads,load); return current;
    }
    private void validateReceipt(RequestData.Record request) throws Exception {
        if(request==null || request.snapshot==null || request.version<1)throw failure("VALIDATION_FAILED");
        id(request.id); id(request.ownerId); id(request.snapshot.id); office(request.officeId);
        if(!transport.accountId().equals(request.ownerId))throw failure("FORBIDDEN");
        if(!"digital".equals(request.deliveryMode) || request.status!=RequestStatus.READY_FOR_PICKUP_DOWNLOAD)throw failure("INVALID_STATE");
    }

    public RequestData.ServicePage services(String officeId, String afterId) throws Exception {
        requireCurrent(); office(officeId); optionalId(afterId);
        long load=catalogLoads.incrementAndGet();
        Uri.Builder query=table("services",SERVICE_COLUMNS).appendQueryParameter("office_id","eq."+officeId)
                .appendQueryParameter("active","eq.true").appendQueryParameter("order","id.asc");
        if(afterId!=null)query.appendQueryParameter("id","gt."+afterId);
        JSONArray rows=read(query); LinkedHashMap<String,RequestData.Service> values=new LinkedHashMap<>();
        String last=null;
        for(int i=0;i<rows.length();i++) {
            RequestData.Service value=parseService(rows.getJSONObject(i));
            if(!officeId.equals(value.officeId))throw failure("FORBIDDEN");
            RequestData.Service existing=values.get(value.id);
            if(existing==null || value.version>existing.version)values.put(value.id,value);
            last=value.id;
        }
        check(catalogLoads,load);
        return new RequestData.ServicePage(new ArrayList<>(values.values()),rows.length()==50?last:null);
    }

    public RequestData.RequestPage requests(String officeId, String afterCreatedAt, String afterId) throws Exception {
        return requests(officeId,null,afterCreatedAt,afterId);
    }
    public RequestData.RequestPage requests(String officeId,RequestStatus status,String afterCreatedAt,String afterId) throws Exception {
        requireCurrent(); if(officeId!=null)office(officeId);
        if(status!=null) {
            if(officeId==null)throw failure("FORBIDDEN");
            if(status!=RequestStatus.SUBMITTED && status!=RequestStatus.PROCESSING && status!=RequestStatus.READY_FOR_PICKUP_DOWNLOAD)throw failure("VALIDATION_FAILED");
        }
        if((afterCreatedAt==null)!=(afterId==null))throw failure("VALIDATION_FAILED");
        optionalId(afterId); if(afterCreatedAt!=null)timestamp(afterCreatedAt);
        long load=requestLoads.incrementAndGet();
        Uri.Builder query=table("requests",REQUEST_COLUMNS).appendQueryParameter("order","created_at.desc,id.desc");
        if(officeId==null)query.appendQueryParameter("owner_id","eq."+transport.accountId());
        else query.appendQueryParameter("office_id","eq."+officeId);
        if(status!=null)query.appendQueryParameter("status","eq."+status.wireValue);
        if(afterId!=null)query.appendQueryParameter("or","(created_at.lt."+afterCreatedAt
                +",and(created_at.eq."+afterCreatedAt+",id.lt."+afterId+"))");
        JSONArray rows=read(query); LinkedHashMap<String,RequestData.Record> values=new LinkedHashMap<>();
        RequestData.Record last=null;
        for(int i=0;i<rows.length();i++) {
            RequestData.Record value=parseRecord(rows.getJSONObject(i));
            if(officeId==null?!transport.accountId().equals(value.ownerId):!officeId.equals(value.officeId))throw failure("FORBIDDEN");
            if(status!=null && status!=value.status)throw failure("VALIDATION_FAILED");
            RequestData.Record existing=values.get(value.id);
            if(existing==null || value.version>existing.version)values.put(value.id,value);
            last=value;
        }
        check(requestLoads,load);
        return new RequestData.RequestPage(new ArrayList<>(values.values()),rows.length()==50?last.createdAt:null,rows.length()==50?last.id:null);
    }

    public RequestData.Record request(String requestId,String officeId) throws Exception {
        requireCurrent(); id(requestId); if(officeId!=null)office(officeId);
        long load=requestDetailLoads.incrementAndGet();
        Uri.Builder query=table("requests",REQUEST_COLUMNS,2).appendQueryParameter("id","eq."+requestId);
        if(officeId==null)query.appendQueryParameter("owner_id","eq."+transport.accountId());
        else query.appendQueryParameter("office_id","eq."+officeId);
        JSONArray rows=read(query); check(requestDetailLoads,load);
        if(rows.length()==0)throw failure("NOT_FOUND");
        if(rows.length()!=1)throw failure("VALIDATION_FAILED");
        RequestData.Record value;
        try { value=parseRecord(rows.getJSONObject(0)); }
        catch(JSONException malformed) { throw failure("VALIDATION_FAILED"); }
        if(!requestId.equals(value.id) || (officeId==null?!transport.accountId().equals(value.ownerId):!officeId.equals(value.officeId)))throw failure("FORBIDDEN");
        check(requestDetailLoads,load); return value;
    }

    public RequestData.Quote quote(String serviceId, String deliveryMode) throws Exception {
        requireCurrent(); id(serviceId); mode(deliveryMode);
        long load=quoteLoads.incrementAndGet();
        JSONObject row=call("quote_request",new JSONObject().put("p_service_id",serviceId).put("p_delivery_mode",deliveryMode));
        try {
            String owner=row.getString("owner_id");
            if(!transport.accountId().equals(owner))throw failure("FORBIDDEN");
            RequestData.Service service=parseService(row.getJSONObject("service"));
            if(!serviceId.equals(row.getString("service_id")) || !serviceId.equals(service.id)
                    || !deliveryMode.equals(row.getString("delivery_mode")))throw failure("VALIDATION_FAILED");
            if(integer(row,"service_version")!=service.version)throw failure("QUOTE_STALE");
            if(!service.deliveryModes.contains(deliveryMode))throw failure("QUOTE_STALE");
            String quoteId=row.getString("id"); id(quoteId);
            RequestData.Quote result=new RequestData.Quote(quoteId,owner,deliveryMode,service,row.getBoolean("submittable"));
            check(quoteLoads,load); return result;
        } catch(JSONException error) { throw failure("VALIDATION_FAILED"); }
    }

    public List<RequestData.Evidence> evidence(String serviceId, String afterId) throws Exception {
        requireCurrent(); id(serviceId); optionalId(afterId);
        long load=evidenceLoads.incrementAndGet();
        Uri.Builder query=table("request_files",FILE_COLUMNS).appendQueryParameter("owner_id","eq."+transport.accountId())
                .appendQueryParameter("service_id","eq."+serviceId).appendQueryParameter("request_id","is.null")
                .appendQueryParameter("kind","eq.evidence").appendQueryParameter("deleted_at","is.null")
                .appendQueryParameter("order","id.asc");
        if(afterId!=null)query.appendQueryParameter("id","gt."+afterId);
        JSONArray rows=read(query); List<RequestData.Evidence> result=new ArrayList<>();
        try {
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i); String fileId=row.getString("id"); id(fileId);
                if(!transport.accountId().equals(row.getString("owner_id")))throw failure("FORBIDDEN");
                if(!serviceId.equals(row.getString("service_id")) || !row.isNull("request_id")
                        || !row.isNull("deleted_at") || !"evidence".equals(row.getString("kind")))throw failure("UPLOAD_INVALID");
                result.add(new RequestData.Evidence(fileId,transport.accountId(),serviceId,null,
                        nullable(row,"requirement"),row.getString("verification"),row.getBoolean("current")));
            }
        } catch(JSONException error) { throw failure("VALIDATION_FAILED"); }
        check(evidenceLoads,load); return java.util.Collections.unmodifiableList(result);
    }

    public RequestData.Submission draft(RequestData.Quote quote, List<RequestData.Evidence> evidence,
                                        String notes, String paymentMethod, RequestData.Submission previous) throws Exception {
        return draft(quote,null,evidence,notes,paymentMethod,previous);
    }

    public RequestData.Submission draft(RequestData.Quote quote, RequestData.QuoteOptions options,
                                        List<RequestData.Evidence> evidence, String notes, String paymentMethod,
                                        RequestData.Submission previous) throws Exception {
        requireCurrent(); validateDraft(quote,options,evidence,paymentMethod);
        String text=notes==null?"":notes;
        String key=UUID.randomUUID().toString();
        if(previous!=null && samePayload(previous,quote,evidence,text,paymentMethod)) { id(previous.operationKey); key=previous.operationKey; }
        RequestData.Submission result=new RequestData.Submission(quote,options,evidence,text,paymentMethod,key);
        requireCurrent(); return result;
    }

    public RequestData.Record submit(RequestData.Submission draft) throws Exception {
        requireCurrent(); if(draft==null)throw failure("VALIDATION_FAILED");
        validateDraft(draft.quote,draft.options,draft.evidence,draft.paymentMethod); id(draft.operationKey);
        long load=submissionLoads.incrementAndGet();
        JSONArray fileIds=new JSONArray(); for(RequestData.Evidence file:draft.evidence)fileIds.put(file.id);
        JSONObject args=new JSONObject().put("p_quote_id",draft.quote.id).put("p_evidence_file_ids",fileIds)
                .put("p_notes",draft.notes==null?"":draft.notes).put("p_payment_method",draft.paymentMethod==null?JSONObject.NULL:draft.paymentMethod)
                .put("p_idempotency_key",draft.operationKey);
        RequestData.Record original=parseRecord(call("submit_request",args));
        if(!transport.accountId().equals(original.ownerId))throw failure("FORBIDDEN");
        JSONArray current=read(table("requests",REQUEST_COLUMNS).appendQueryParameter("id","eq."+original.id)
                .appendQueryParameter("owner_id","eq."+transport.accountId()));
        if(current.length()!=1)throw failure("NOT_FOUND");
        RequestData.Record result=parseRecord(current.getJSONObject(0));
        if(!original.id.equals(result.id) || !transport.accountId().equals(result.ownerId))throw failure("FORBIDDEN");
        if(result.version<original.version)throw failure("STALE_RESPONSE");
        check(submissionLoads,load); return result;
    }

    public void prepareEvidence(RequestData.Quote quote, String requirement, String mimeType,
                                long sizeBytes, String operationKey) throws Exception {
        requireCurrent();
        throw failure("CONFIGURATION_REQUIRED");
    }
    public RequestData.QuoteOptions quoteOptions(RequestData.Quote quote) throws Exception {
        requireCurrent(); validateQuote(quote);
        long load=quoteOptionLoads.incrementAndGet();
        JSONObject row=call("workflow_options_v2",new JSONObject().put("p_quote_id",quote.id).put("p_request_id",JSONObject.NULL));
        try {
            exact(row,"quote_id","service_version","cash","evidence","egovpay");
            if(!quote.id.equals(string(row,"quote_id")))throw failure("VALIDATION_FAILED");
            long version=integer(row,"service_version");
            if(version!=quote.service.version)throw failure("QUOTE_STALE");
            RequestData.QuoteOptions result=new RequestData.QuoteOptions(quote.id,version,
                    parseAction(row.getJSONObject("cash")),parseFileOptions(row.getJSONObject("evidence"),false),bool(row,"egovpay"));
            check(quoteOptionLoads,load); return result;
        } catch(JSONException error) { throw failure("VALIDATION_FAILED"); }
    }

    public RequestData.RequestOptions requestOptions(RequestData.Record request) throws Exception {
        requireCurrent(); if(request==null)throw failure("VALIDATION_FAILED"); id(request.id);
        if(request.version<1)throw failure("VALIDATION_FAILED");
        long load=requestOptionLoads.incrementAndGet();
        JSONObject row=call("workflow_options",new JSONObject().put("p_quote_id",JSONObject.NULL).put("p_request_id",request.id));
        try {
            exact(row,"request_id","request_version","actions");
            if(!request.id.equals(string(row,"request_id")))throw failure("VALIDATION_FAILED");
            long version=integer(row,"request_version"); if(version!=request.version)throw failure("STALE_VERSION");
            JSONObject actions=row.getJSONObject("actions"); LinkedHashMap<String,RequestData.ActionOptions> values=new LinkedHashMap<>();
            java.util.Iterator<String> names=actions.keys();
            while(names.hasNext()) {
                String name=names.next();
                if(!java.util.Arrays.asList("cancel","reject","pickup_claim","deliverable_upload").contains(name))throw failure("VALIDATION_FAILED");
                values.put(name,"deliverable_upload".equals(name)?parseFileOptions(actions.getJSONObject(name),true):parseAction(actions.getJSONObject(name)));
            }
            RequestData.RequestOptions result=new RequestData.RequestOptions(request.id,version,values);
            check(requestOptionLoads,load); return result;
        } catch(JSONException error) { throw failure("VALIDATION_FAILED"); }
    }

    public RequestData.PreparedUpload evidenceUpload(RequestData.Quote quote, RequestData.QuoteOptions options,
                                                     String requirement, String mimeType, byte[] bytes,
                                                     RequestData.PreparedUpload previous) throws Exception {
        requireCurrent(); if(bytes==null)throw failure("UPLOAD_INVALID");
        validateEvidenceInput(quote,options,requirement,mimeType,bytes.length);
        String digest=sha256(bytes.clone()),key=UUID.randomUUID().toString(),fileId=null;
        if(previous!=null && previous.quote!=null && previous.quote.service!=null
                && quote.id.equals(previous.quote.id) && quote.ownerId.equals(previous.quote.ownerId)
                && quote.service.id.equals(previous.quote.service.id) && quote.service.version==previous.quote.service.version
                && java.util.Objects.equals(requirement,previous.requirement) && mimeType.equals(previous.mimeType)
                && bytes.length==previous.sizeBytes && digest.equals(previous.contentSha256)) {
            id(previous.operationKey); optionalId(previous.fileId); key=previous.operationKey; fileId=previous.fileId;
        }
        requireCurrent();
        return new RequestData.PreparedUpload(quote,options,requirement,mimeType,bytes.length,digest,key,fileId);
    }

    public RequestData.PreparedUpload prepareEvidence(RequestData.PreparedUpload upload) throws Exception {
        requireCurrent(); validateUpload(upload);
        long load=prepareLoads.incrementAndGet();
        JSONObject args=new JSONObject().put("service_id",upload.quote.service.id).put("request_id",JSONObject.NULL)
                .put("kind","evidence").put("requirement",upload.requirement==null?JSONObject.NULL:upload.requirement)
                .put("mime_type",upload.mimeType).put("size_bytes",upload.sizeBytes).put("idempotency_key",upload.operationKey);
        JSONObject row=edge(args); String fileId=validateFileMetadata(row,upload,"pending");
        RequestData.PreparedUpload result=new RequestData.PreparedUpload(upload.quote,upload.options,upload.requirement,
                upload.mimeType,upload.sizeBytes,upload.contentSha256,upload.operationKey,fileId);
        check(prepareLoads,load); return result;
    }

    public RequestData.Evidence uploadEvidence(RequestData.PreparedUpload upload, byte[] identicalBytes) throws Exception {
        requireCurrent(); validateUpload(upload);
        if(upload.fileId==null || identicalBytes==null)throw failure("UPLOAD_INVALID");
        byte[] bytes=identicalBytes.clone();
        if(bytes.length!=upload.sizeBytes || !sha256(bytes).equals(upload.contentSha256))throw failure("UPLOAD_INVALID");
        long load=uploadLoads.incrementAndGet(); JSONObject row;
        try { row=transport.upload("request-file",upload.fileId,bytes,upload.mimeType); requireCurrent(); }
        catch(SupabaseClient.ApiException error) { throw failure(error.code); }
        if(row==null)throw failure("VALIDATION_FAILED");
        String fileId=validateFileMetadata(row,upload,"verified");
        RequestData.Evidence result=new RequestData.Evidence(fileId,transport.accountId(),upload.quote.service.id,null,
                upload.requirement,"verified",true);
        check(uploadLoads,load); return result;
    }
    public RequestData.Transition transitionDraft(RequestData.Record request,String officeId,RequestData.RequestOptions options,
            RequestStatus target,String reason,RequestData.Transition previous) throws Exception {
        requireCurrent(); String text=reason==null?"":reason.trim();
        validateTransition(request,officeId,options,target,text);
        String actor=transport.accountId(),key=UUID.randomUUID().toString();
        if(previous!=null && previous.request!=null && actor.equals(previous.actorId)
                && java.util.Objects.equals(officeId,previous.scopeOfficeId) && request.id.equals(previous.request.id)
                && request.version==previous.request.version && target==previous.target && text.equals(previous.reason)) {
            id(previous.operationKey); key=previous.operationKey;
        }
        requireCurrent(); return new RequestData.Transition(request,options,actor,officeId,target,text,key);
    }
    public RequestData.Record transition(RequestData.Transition input) throws Exception {
        requireCurrent(); if(input==null)throw failure("VALIDATION_FAILED");
        if(!transport.accountId().equals(input.actorId))throw failure("FORBIDDEN");
        validateTransition(input.request,input.scopeOfficeId,input.options,input.target,input.reason); id(input.operationKey);
        long load=transitionLoads.incrementAndGet();
        JSONObject args=new JSONObject().put("p_request_id",input.request.id).put("p_expected_version",input.request.version)
            .put("p_target_status",input.target.wireValue).put("p_reason",input.reason).put("p_idempotency_key",input.operationKey);
        RequestData.Record receipt=parseRecord(call("transition_request",args));
        if(!sameRequestIdentity(input.request,receipt))throw failure("FORBIDDEN");
        if(receipt.status!=input.target || receipt.version!=input.request.version+1)throw failure("STALE_RESPONSE");
        RequestData.Record current=request(input.request.id,input.scopeOfficeId);
        if(!sameRequestIdentity(input.request,current))throw failure("FORBIDDEN");
        if(current.version<receipt.version)throw failure("STALE_RESPONSE");
        check(transitionLoads,load); return current;
    }
    public void requireCurrent() { transport.requireCurrent(); }

    private void validateTransition(RequestData.Record request,String officeId,RequestData.RequestOptions options,
            RequestStatus target,String reason) throws Exception {
        if(request==null || request.snapshot==null || request.status==null || request.version<1 || reason==null)throw failure("VALIDATION_FAILED");
        id(request.id); id(request.ownerId); id(request.snapshot.id); office(request.officeId);
        if(officeId!=null) { office(officeId); if(!officeId.equals(request.officeId))throw failure("FORBIDDEN"); }
        else if(!transport.accountId().equals(request.ownerId))throw failure("FORBIDDEN");
        if(target!=RequestStatus.PROCESSING && target!=RequestStatus.READY_FOR_PICKUP_DOWNLOAD
                && target!=RequestStatus.CANCELLED && target!=RequestStatus.REJECTED)throw failure("FORBIDDEN");
        if(officeId==null && target!=RequestStatus.CANCELLED)throw failure("FORBIDDEN");
        if(request.status==RequestStatus.COMPLETED || request.status==RequestStatus.CANCELLED || request.status==RequestStatus.REJECTED)throw failure("INVALID_STATE");
        if(target==RequestStatus.CANCELLED || target==RequestStatus.REJECTED) {
            if(options==null)throw failure("CONFIGURATION_REQUIRED");
            if(!request.id.equals(options.requestId))throw failure("VALIDATION_FAILED");
            if(request.version!=options.requestVersion)throw failure("STALE_VERSION");
            RequestData.ActionOptions action=options.actions.get(target==RequestStatus.CANCELLED?"cancel":"reject");
            if(action==null)throw failure("CONFIGURATION_REQUIRED"); validateAction(action);
            if(!action.available)throw failure(action.blocker);
            if(reason.isBlank())throw failure("VALIDATION_FAILED");
        } else if(target==RequestStatus.PROCESSING && request.status!=RequestStatus.SUBMITTED && request.status!=RequestStatus.AWAITING_PAYMENT
                || target==RequestStatus.READY_FOR_PICKUP_DOWNLOAD && request.status!=RequestStatus.PROCESSING)throw failure("INVALID_STATE");
    }

    private static boolean sameRequestIdentity(RequestData.Record first,RequestData.Record next) {
        return first.id.equals(next.id) && first.ownerId.equals(next.ownerId) && first.officeId.equals(next.officeId)
            && java.util.Objects.equals(first.reference,next.reference) && java.util.Objects.equals(first.deliveryMode,next.deliveryMode)
            && first.snapshot.id.equals(next.snapshot.id) && first.snapshot.version==next.snapshot.version
            && first.snapshot.fee==next.snapshot.fee && java.util.Objects.equals(first.snapshot.currency,next.snapshot.currency)
            && (first.snapshot.amount==null?next.snapshot.amount==null:next.snapshot.amount!=null && first.snapshot.amount.compareTo(next.snapshot.amount)==0);
    }

    private void validateDraft(RequestData.Quote quote,RequestData.QuoteOptions options,List<RequestData.Evidence> evidence,String method) throws Exception {
        if(quote==null || evidence==null || quote.service==null)throw failure("VALIDATION_FAILED");
        if(!transport.accountId().equals(quote.ownerId))throw failure("FORBIDDEN");
        id(quote.id); id(quote.service.id); mode(quote.deliveryMode);
        if(!quote.service.active || !quote.service.deliveryModes.contains(quote.deliveryMode))throw failure("QUOTE_STALE");
        if(quote.service.fee==null || quote.service.fee==FeeDeclaration.PENDING_REVIEW || !quote.submittable)throw failure("CONFIGURATION_REQUIRED");
        if(quote.service.fee==FeeDeclaration.REQUIRED) {
            if("egovpay".equals(method) && (options==null || !options.egovpay))throw failure("PROVIDER_UNAVAILABLE");
            if(quote.service.amount==null || quote.service.amount.signum()<=0 || quote.service.currency==null
                    || quote.service.currency.isBlank() || !"cash".equals(method) && !"egovpay".equals(method))throw failure("VALIDATION_FAILED");
            validateQuoteOptions(quote,options);
            if("cash".equals(method)) { validateAction(options.cash); if(!options.cash.available)throw failure(options.cash.blocker); }
            else if(!"PHP".equals(quote.service.currency))throw failure("PROVIDER_UNAVAILABLE");
        } else {
            if(quote.service.amount==null || quote.service.amount.signum()!=0 || quote.service.currency!=null || method!=null)throw failure("VALIDATION_FAILED");
            if(options!=null)validateQuoteOptions(quote,options);
        }
        Set<String> ids=new HashSet<>(), requirements=new HashSet<>();
        for(RequestData.Evidence file:evidence) {
            if(file==null)throw failure("UPLOAD_INVALID");
            if(!transport.accountId().equals(file.ownerId))throw failure("FORBIDDEN");
            id(file.id);
            if(!ids.add(file.id) || !quote.service.id.equals(file.serviceId) || file.requestId!=null
                    || !"verified".equals(file.verification) || !file.current)throw failure("UPLOAD_INVALID");
            if(file.requirement!=null)requirements.add(file.requirement);
        }
        if(!requirements.containsAll(quote.service.requirementIds))throw failure("UPLOAD_INVALID");
    }

    private boolean samePayload(RequestData.Submission previous,RequestData.Quote quote,List<RequestData.Evidence> evidence,String notes,String method) {
        if(previous.quote==null || !quote.id.equals(previous.quote.id) || !notes.equals(previous.notes)
                || !java.util.Objects.equals(method,previous.paymentMethod) || evidence.size()!=previous.evidence.size())return false;
        for(int i=0;i<evidence.size();i++)if(!evidence.get(i).id.equals(previous.evidence.get(i).id))return false;
        return true;
    }

    private void validateQuote(RequestData.Quote quote) throws Exception {
        if(quote==null || quote.service==null)throw failure("VALIDATION_FAILED");
        if(!transport.accountId().equals(quote.ownerId))throw failure("FORBIDDEN");
        id(quote.id); id(quote.service.id); mode(quote.deliveryMode);
        if(quote.service.version<1)throw failure("VALIDATION_FAILED");
        if(!quote.service.active || !quote.service.deliveryModes.contains(quote.deliveryMode))throw failure("QUOTE_STALE");
    }

    private void validateQuoteOptions(RequestData.Quote quote,RequestData.QuoteOptions options) throws Exception {
        if(options==null)throw failure("CONFIGURATION_REQUIRED");
        if(!quote.id.equals(options.quoteId) || quote.service.version!=options.serviceVersion)throw failure("QUOTE_STALE");
    }

    private void validateEvidenceInput(RequestData.Quote quote,RequestData.QuoteOptions options,
                                       String requirement,String mimeType,long sizeBytes) throws Exception {
        validateQuote(quote); validateQuoteOptions(quote,options); validateFileOptions(options.evidence,false);
        if(!options.evidence.available)throw failure(options.evidence.blocker);
        if(requirement!=null && !quote.service.requirementIds.contains(requirement)
                || mimeType==null || !options.evidence.mimeTypes.contains(mimeType)
                || sizeBytes<1 || sizeBytes>options.evidence.maxBytes)throw failure("UPLOAD_INVALID");
    }

    private void validateUpload(RequestData.PreparedUpload upload) throws Exception {
        if(upload==null)throw failure("UPLOAD_INVALID");
        validateEvidenceInput(upload.quote,upload.options,upload.requirement,upload.mimeType,upload.sizeBytes);
        id(upload.operationKey); optionalId(upload.fileId);
        if(upload.contentSha256==null || !upload.contentSha256.matches("[a-f0-9]{64}"))throw failure("UPLOAD_INVALID");
    }

    private String validateFileMetadata(JSONObject row,RequestData.PreparedUpload upload,String verification) throws Exception {
        try {
            exact(row,"id","owner_id","service_id","request_id","kind","requirement","mime_type","size_bytes",
                    "verification","version","current","created_at","deleted_at");
            if(!transport.accountId().equals(string(row,"owner_id")))throw failure("FORBIDDEN");
            String fileId=string(row,"id"); id(fileId);
            if(upload.fileId!=null && !upload.fileId.equals(fileId)
                    || !upload.quote.service.id.equals(string(row,"service_id")) || !row.isNull("request_id")
                    || !row.isNull("deleted_at") || !"evidence".equals(string(row,"kind"))
                    || !java.util.Objects.equals(upload.requirement,nullableString(row,"requirement"))
                    || !upload.mimeType.equals(string(row,"mime_type")) || upload.sizeBytes!=integer(row,"size_bytes")
                    || !verification.equals(string(row,"verification")) || !Boolean.TRUE.equals(row.get("current")))throw failure("UPLOAD_INVALID");
            integer(row,"version"); timestamp(string(row,"created_at"));
            return fileId;
        } catch(JSONException error) { throw failure("VALIDATION_FAILED"); }
    }

    private static RequestData.ActionOptions parseAction(JSONObject row) throws Exception {
        exact(row,"available","blocker");
        RequestData.ActionOptions result=new RequestData.ActionOptions(bool(row,"available"),nullableString(row,"blocker"));
        validateAction(result); return result;
    }

    private static RequestData.FileOptions parseFileOptions(JSONObject row,boolean deliverable) throws Exception {
        exact(row,"available","blocker","mime_types","max_bytes");
        List<String> mimeTypes=null; Long maximum=null;
        if(!row.isNull("mime_types")) {
            mimeTypes=new ArrayList<>(); JSONArray choices=row.getJSONArray("mime_types");
            for(int i=0;i<choices.length();i++) {
                Object choice=choices.get(i); if(!(choice instanceof String))throw failure("VALIDATION_FAILED");
                mimeTypes.add((String)choice);
            }
        }
        if(!row.isNull("max_bytes"))maximum=integer(row,"max_bytes");
        RequestData.FileOptions result=new RequestData.FileOptions(bool(row,"available"),nullableString(row,"blocker"),mimeTypes,maximum);
        validateFileOptions(result,deliverable); return result;
    }

    private static void validateAction(RequestData.ActionOptions option) throws WorkflowException {
        if(option==null || option.available && option.blocker!=null
                || !option.available && !java.util.Arrays.asList("CONFIGURATION_REQUIRED","POLICY_BLOCKED","INVALID_STATE","PAYMENT_REQUIRED").contains(option.blocker))
            throw failure("VALIDATION_FAILED");
    }

    private static void validateFileOptions(RequestData.FileOptions option,boolean deliverable) throws WorkflowException {
        validateAction(option);
        if(!option.available) {
            if(option.mimeTypes!=null || option.maxBytes!=null)throw failure("VALIDATION_FAILED");
            return;
        }
        if(option.mimeTypes==null || option.mimeTypes.isEmpty() || option.maxBytes==null
                || option.maxBytes<1 || option.maxBytes>9007199254740991L)throw failure("VALIDATION_FAILED");
        Set<String> seen=new HashSet<>();
        for(String mime:option.mimeTypes) {
            if(!seen.add(mime) || !("application/pdf".equals(mime)
                    || !deliverable && ("image/png".equals(mime) || "image/jpeg".equals(mime))))throw failure("VALIDATION_FAILED");
        }
    }

    private static void exact(JSONObject row,String... fields) throws WorkflowException {
        if(row==null || row.length()!=fields.length)throw failure("VALIDATION_FAILED");
        for(String field:fields)if(!row.has(field))throw failure("VALIDATION_FAILED");
    }
    private static String string(JSONObject row,String key) throws Exception {
        Object value=row.get(key); if(!(value instanceof String))throw failure("VALIDATION_FAILED"); return (String)value;
    }
    private static String nullableString(JSONObject row,String key) throws Exception { return row.get(key)==JSONObject.NULL?null:string(row,key); }
    private static boolean bool(JSONObject row,String key) throws Exception {
        Object value=row.get(key); if(!(value instanceof Boolean))throw failure("VALIDATION_FAILED"); return (Boolean)value;
    }
    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder value=new StringBuilder(64);
        for(byte part:digest)value.append(Character.forDigit((part & 255)>>>4,16)).append(Character.forDigit(part & 15,16));
        return value.toString();
    }

    private JSONArray read(Uri.Builder query) throws Exception {
        requireCurrent();
        try {
            JSONArray rows=transport.get(query.build().toString()); requireCurrent();
            if(rows==null || rows.length()>50)throw failure("VALIDATION_FAILED");
            return rows;
        } catch(SupabaseClient.ApiException error) { throw failure(error.code); }
    }
    private JSONObject call(String name,JSONObject args) throws Exception {
        requireCurrent();
        try { JSONObject row=transport.rpc(name,args); requireCurrent(); if(row==null)throw failure("VALIDATION_FAILED"); return row; }
        catch(SupabaseClient.ApiException error) { throw failure(error.code); }
    }
    private JSONObject edge(JSONObject args) throws Exception {
        requireCurrent();
        try { JSONObject row=transport.edge("request-file",args); requireCurrent(); if(row==null)throw failure("VALIDATION_FAILED"); return row; }
        catch(SupabaseClient.ApiException error) { throw failure(error.code); }
    }
    private Uri.Builder table(String table,String columns) {
        return table(table,columns,50);
    }
    private Uri.Builder table(String table,String columns,int limit) {
        return Uri.parse("/rest/v1/"+table).buildUpon().appendQueryParameter("select",columns).appendQueryParameter("limit",Integer.toString(limit));
    }
    private void check(AtomicLong generation,long expected) throws WorkflowException {
        requireCurrent(); if(generation.get()!=expected)throw failure("STALE_RESPONSE");
    }

    static RequestData.Service parseService(JSONObject row) throws Exception {
        try {
            String serviceId=row.getString("id"), officeId=row.getString("office_id"), name=row.getString("name");
            id(serviceId); office(officeId); if(name.isBlank())throw failure("VALIDATION_FAILED");
            String wire=row.getString("fee_state"), currency=nullable(row,"currency");
            FeeDeclaration fee;
            switch(wire) {
                case "pending": fee=FeeDeclaration.PENDING_REVIEW; break;
                case "free": fee=FeeDeclaration.FREE; break;
                case "required": fee=FeeDeclaration.REQUIRED; break;
                default: throw failure("VALIDATION_FAILED");
            }
            Object raw=row.get("amount"); BigDecimal amount=null;
            if(raw!=JSONObject.NULL) {
                if(raw instanceof Float || raw instanceof Double)throw failure("VALIDATION_FAILED");
                try { amount=new BigDecimal(raw.toString()); }
                catch(NumberFormatException error) { throw failure("VALIDATION_FAILED"); }
            }
            if(fee==FeeDeclaration.PENDING_REVIEW && (amount!=null || currency!=null)
                    || fee==FeeDeclaration.FREE && (amount==null || amount.signum()!=0 || currency!=null)
                    || fee==FeeDeclaration.REQUIRED && (amount==null || amount.signum()<=0 || currency==null || currency.isBlank()))throw failure("VALIDATION_FAILED");
            List<String> modes=new ArrayList<>(), requirements=new ArrayList<>();
            JSONArray deliveries=row.getJSONArray("delivery_modes"), declared=row.getJSONArray("requirements");
            for(int i=0;i<deliveries.length();i++) { String value=deliveries.getString(i); mode(value); if(!modes.contains(value))modes.add(value); }
            if(modes.isEmpty())throw failure("VALIDATION_FAILED");
            for(int i=0;i<declared.length();i++) {
                JSONObject requirement=declared.getJSONObject(i);
                if(!requirement.has("id") || requirement.isNull("id"))throw failure("VALIDATION_FAILED");
                String value=requirement.getString("id"); if(value.isBlank())throw failure("VALIDATION_FAILED");
                if(!requirements.contains(value))requirements.add(value);
            }
            return new RequestData.Service(serviceId,officeId,name,integer(row,"version"),fee,amount,currency,modes,requirements,row.getBoolean("active"));
        } catch(JSONException error) { throw failure("VALIDATION_FAILED"); }
    }

    private RequestData.Record parseRecord(JSONObject row) throws Exception {
        try {
            String requestId=row.getString("id"),owner=row.getString("owner_id"),officeId=row.getString("office_id"),
                    deliveryMode=row.getString("delivery_mode"),createdAt=row.getString("created_at");
            id(requestId); id(owner); office(officeId); mode(deliveryMode); timestamp(createdAt);
            RequestData.Service snapshot=parseService(row.getJSONObject("snapshot"));
            if(!snapshot.id.equals(row.getString("service_id")) || !snapshot.officeId.equals(officeId)
                    || !snapshot.deliveryModes.contains(deliveryMode))throw failure("VALIDATION_FAILED");
            RequestStatus status;
            try {
                status=RequestStatus.fromWire(row.getString("status"));
                ph.edu.tip.goserve.domain.RequestWorkflow.phaseState(snapshot.fee,status,RequestStatus.SUBMITTED);
            }
            catch(IllegalArgumentException error) { throw failure("VALIDATION_FAILED"); }
            return new RequestData.Record(requestId,row.getString("reference"),owner,officeId,deliveryMode,createdAt,
                    integer(row,"version"),status,snapshot);
        } catch(JSONException error) { throw failure("VALIDATION_FAILED"); }
    }
    private static String nullable(JSONObject row,String key) throws JSONException { return row.isNull(key)?null:row.getString(key); }
    private static long integer(JSONObject row,String key) throws Exception {
        Object value=row.get(key);
        if(!(value instanceof Integer) && !(value instanceof Long))throw failure("VALIDATION_FAILED");
        long result=((Number)value).longValue(); if(result<1)throw failure("VALIDATION_FAILED"); return result;
    }
    private static void id(String value) throws WorkflowException {
        if(value==null || !value.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))throw failure("VALIDATION_FAILED");
    }
    private static void optionalId(String value) throws WorkflowException { if(value!=null)id(value); }
    private static void office(String value) throws WorkflowException {
        if(!java.util.Arrays.asList("OFF-REG","OFF-ACC","OFF-CASH","OFF-GCC","DEP-CCS").contains(value))throw failure("VALIDATION_FAILED");
    }
    private static void mode(String value) throws WorkflowException {
        if(!"physical".equals(value) && !"digital".equals(value))throw failure("VALIDATION_FAILED");
    }
    private static void timestamp(String value) throws WorkflowException {
        try { Instant.parse(value); } catch(Exception error) { throw failure("VALIDATION_FAILED"); }
    }
    private static WorkflowException failure(String code) { return new WorkflowException(code); }
}
