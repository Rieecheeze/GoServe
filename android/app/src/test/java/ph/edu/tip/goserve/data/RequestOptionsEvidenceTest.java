package ph.edu.tip.goserve.data;

import java.math.BigDecimal;
import java.util.ArrayList;
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
import ph.edu.tip.goserve.domain.FeeDeclaration;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.RequestStatus;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public final class RequestOptionsEvidenceTest {
    private static final String OWNER="00000000-0000-4000-8000-000000000001";
    private static final String SERVICE="00000000-0000-4000-8000-000000000002";
    private static final String QUOTE="00000000-0000-4000-8000-000000000003";
    private static final String FILE="00000000-0000-4000-8000-000000000004";
    private static final String REQUEST="00000000-0000-4000-8000-000000000005";
    private static final String OTHER="00000000-0000-4000-8000-000000000009";
    private static final String TIME="2026-10-19T01:00:00.000Z";
    private static final byte[] BYTES={1,2,3};

    @Test public void quoteOptionsUseExactTargetAndApprovedPickerProjection() throws Exception {
        Fake wire=new Fake(); RequestRepository repository=new RequestRepository(wire);
        RequestData.QuoteOptions options=repository.quoteOptions(quote(FeeDeclaration.REQUIRED));
        assertNotNull("Owned quote options must be parsed",options);
        assertEquals("workflow_options_v2",wire.rpcName); assertEquals(2,wire.args.length());
        assertEquals(QUOTE,wire.args.getString("p_quote_id")); assertTrue(wire.args.has("p_request_id"));
        assertTrue(wire.args.isNull("p_request_id")); assertEquals(QUOTE,options.quoteId);
        assertEquals(7,options.serviceVersion); assertTrue(options.cash.available); assertNull(options.cash.blocker);
        assertEquals(List.of("image/png","application/pdf"),options.evidence.mimeTypes);
        assertEquals(Long.valueOf(3),options.evidence.maxBytes);
    }

    @Test public void testCheckoutAvailabilityComesFromStrictVersionedServerProjection() throws Exception {
        Fake wire=new Fake(); wire.reply=quoteOptions().put("egovpay",true);
        RequestRepository repository=new RequestRepository(wire);
        RequestData.QuoteOptions options=repository.quoteOptions(quote(FeeDeclaration.REQUIRED));
        RequestData.Submission input=repository.draft(quote(FeeDeclaration.REQUIRED),options,List.of(evidence()),"","egovpay",null);
        assertEquals("egovpay",input.paymentMethod);
        wire.reply=quoteOptions().put("egovpay",false);
        expectCode("PROVIDER_UNAVAILABLE",()->repository.draft(quote(FeeDeclaration.REQUIRED),repository.quoteOptions(quote(FeeDeclaration.REQUIRED)),List.of(evidence()),"","egovpay",null));
    }

    @Test public void versionedCheckoutCapabilityRejectsMissingWrongTypeAndUnknownFields() throws Exception {
        for(Object value:List.of("true",1,JSONObject.NULL)) {
            Fake wire=new Fake(); wire.reply=quoteOptions().put("egovpay",value);
            expectCode("VALIDATION_FAILED",()->new RequestRepository(wire).quoteOptions(quote(FeeDeclaration.REQUIRED)));
        }
        Fake wire=new Fake(); wire.reply=quoteOptions(); wire.reply.remove("egovpay");
        expectCode("VALIDATION_FAILED",()->new RequestRepository(wire).quoteOptions(quote(FeeDeclaration.REQUIRED)));
        wire.reply=quoteOptions().put("extra",true);
        expectCode("VALIDATION_FAILED",()->new RequestRepository(wire).quoteOptions(quote(FeeDeclaration.REQUIRED)));
    }

    @Test public void requestOptionsUseExactTargetAndDoNotInventAbsentActions() throws Exception {
        Fake wire=new Fake(); wire.reply=requestOptions().put("actions",new JSONObject().put("cancel",action(false,"POLICY_BLOCKED")));
        RequestData.RequestOptions result=new RequestRepository(wire).requestOptions(request());
        assertNotNull("Request policy projection must be parsed",result);
        assertEquals("workflow_options",wire.rpcName); assertEquals(2,wire.args.length());
        assertTrue(wire.args.has("p_quote_id")); assertTrue(wire.args.isNull("p_quote_id"));
        assertEquals(REQUEST,wire.args.getString("p_request_id")); assertEquals(4,result.requestVersion);
        assertFalse(result.actions.get("cancel").available); assertFalse(result.actions.containsKey("reject"));
        assertFalse(result.actions.containsKey("pickup_claim")); assertFalse(result.actions.containsKey("deliverable_upload"));
        try { result.actions.put("reject",new RequestData.ActionOptions(true,null)); fail("Options must be immutable"); }
        catch(UnsupportedOperationException expected) { }
    }

    @Test public void quoteOptionsRejectWrongTargetAndStaleVersion() throws Exception {
        Fake wire=new Fake(); RequestRepository repository=new RequestRepository(wire);
        wire.reply=quoteOptions().put("quote_id",OTHER);
        expectCode("VALIDATION_FAILED",()->repository.quoteOptions(quote(FeeDeclaration.REQUIRED)));
        wire.reply=quoteOptions().put("service_version",8);
        expectCode("QUOTE_STALE",()->repository.quoteOptions(quote(FeeDeclaration.REQUIRED)));
    }

    @Test public void requestOptionsRejectWrongTargetAndStaleVersion() throws Exception {
        Fake wire=new Fake(); RequestRepository repository=new RequestRepository(wire);
        wire.reply=requestOptions().put("request_id",OTHER);
        expectCode("VALIDATION_FAILED",()->repository.requestOptions(request()));
        wire.reply=requestOptions().put("request_version",5);
        expectCode("STALE_VERSION",()->repository.requestOptions(request()));
    }

    @Test public void availabilityRequiresBooleanAndConsistentKnownBlocker() throws Exception {
        for(JSONObject malformed:List.of(new JSONObject().put("available","true").put("blocker",JSONObject.NULL),
                action(true,"POLICY_BLOCKED"),action(false,null),action(false,"INTERNAL_POLICY"),
                new JSONObject().put("available",true))) {
            Fake wire=new Fake(); wire.reply=quoteOptions().put("cash",malformed);
            expectCode("VALIDATION_FAILED",()->new RequestRepository(wire).quoteOptions(quote(FeeDeclaration.REQUIRED)));
        }
    }

    @Test public void unavailableFileOptionsMustHideMimeAndMaximum() throws Exception {
        Fake wire=new Fake(); wire.reply=quoteOptions().put("evidence",fileOption(false,"CONFIGURATION_REQUIRED",null,null));
        RequestData.QuoteOptions result=new RequestRepository(wire).quoteOptions(quote(FeeDeclaration.REQUIRED));
        assertNotNull("Unavailable options must remain explicit",result);
        assertFalse(result.evidence.available); assertNull(result.evidence.mimeTypes); assertNull(result.evidence.maxBytes);
        wire.reply=quoteOptions().put("evidence",fileOption(false,"CONFIGURATION_REQUIRED",new JSONArray().put("image/png"),3));
        expectCode("VALIDATION_FAILED",()->new RequestRepository(wire).quoteOptions(quote(FeeDeclaration.REQUIRED)));
    }

    @Test public void fileOptionsRejectUnsupportedEmptyOrDuplicateChoicesAndUnsafeMaximum() throws Exception {
        for(JSONArray mimes:List.of(new JSONArray(),new JSONArray().put("text/plain"),new JSONArray().put("image/png").put("image/png"))) {
            Fake wire=new Fake(); wire.reply=quoteOptions().put("evidence",fileOption(true,null,mimes,3));
            expectCode("VALIDATION_FAILED",()->new RequestRepository(wire).quoteOptions(quote(FeeDeclaration.REQUIRED)));
        }
        for(Object maximum:List.of(0,-1,3.5,"3",9007199254740992L)) {
            Fake wire=new Fake(); wire.reply=quoteOptions().put("evidence",fileOption(true,null,new JSONArray().put("image/png"),maximum));
            expectCode("VALIDATION_FAILED",()->new RequestRepository(wire).quoteOptions(quote(FeeDeclaration.REQUIRED)));
        }
    }

    @Test public void requestDeliverableChoicesArePdfOnly() throws Exception {
        Fake wire=new Fake(); wire.reply=requestOptions().put("actions",new JSONObject()
                .put("deliverable_upload",fileOption(true,null,new JSONArray().put("application/pdf"),9007199254740991L)));
        RequestData.RequestOptions result=new RequestRepository(wire).requestOptions(request());
        assertNotNull("Authorized file options must remain typed",result);
        assertEquals(List.of("application/pdf"),((RequestData.FileOptions)result.actions.get("deliverable_upload")).mimeTypes);
        wire.reply.getJSONObject("actions").put("deliverable_upload",fileOption(true,null,new JSONArray().put("image/png"),3));
        expectCode("VALIDATION_FAILED",()->new RequestRepository(wire).requestOptions(request()));
    }

    @Test public void projectionsRejectMissingFieldsAndPrivateExtras() throws Exception {
        for(String missing:List.of("quote_id","service_version","cash","evidence")) {
            Fake wire=new Fake(); wire.reply=quoteOptions(); wire.reply.remove(missing);
            expectCode("VALIDATION_FAILED",()->new RequestRepository(wire).quoteOptions(quote(FeeDeclaration.REQUIRED)));
        }
        Fake wire=new Fake(); wire.reply=quoteOptions().put("policy",new JSONObject());
        expectCode("VALIDATION_FAILED",()->new RequestRepository(wire).quoteOptions(quote(FeeDeclaration.REQUIRED)));
        wire.reply=requestOptions().put("actions",new JSONObject().put("unknown_action",action(true,null)));
        expectCode("VALIDATION_FAILED",()->new RequestRepository(wire).requestOptions(request()));
    }

    @Test public void requiredCashDraftUsesMatchingAdvisoryAndPreservesMoneyAndRetryKey() throws Exception {
        RequestRepository repository=new RequestRepository(new Fake()); RequestData.Quote quote=quote(FeeDeclaration.REQUIRED);
        RequestData.Submission first=repository.draft(quote,options(),List.of(evidence()),"original","cash",null);
        assertNotNull("Approved cash quote must enter a draft",first);
        assertEquals(new BigDecimal("9007199254740993.123456789"),first.quote.service.amount);
        RequestData.Submission retry=repository.draft(quote,options(),List.of(evidence()),"original","cash",first);
        RequestData.Submission changed=repository.draft(quote,options(),List.of(evidence()),"changed","cash",first);
        assertNotNull(retry); assertNotNull(changed);
        assertEquals(first.operationKey,retry.operationKey); assertNotEquals(first.operationKey,changed.operationKey);
    }

    @Test public void requiredCashRejectsUnavailableOrMismatchedAdvisory() throws Exception {
        RequestRepository repository=new RequestRepository(new Fake()); RequestData.Quote quote=quote(FeeDeclaration.REQUIRED);
        expectCode("CONFIGURATION_REQUIRED",()->repository.draft(quote,null,List.of(evidence()),"","cash",null));
        RequestData.QuoteOptions blocked=new RequestData.QuoteOptions(QUOTE,7,new RequestData.ActionOptions(false,"CONFIGURATION_REQUIRED"),options().evidence);
        expectCode("CONFIGURATION_REQUIRED",()->repository.draft(quote,blocked,List.of(evidence()),"","cash",null));
        RequestData.QuoteOptions wrong=new RequestData.QuoteOptions(OTHER,7,options().cash,options().evidence);
        expectCode("QUOTE_STALE",()->repository.draft(quote,wrong,List.of(evidence()),"","cash",null));
        RequestData.QuoteOptions stale=new RequestData.QuoteOptions(QUOTE,8,options().cash,options().evidence);
        expectCode("QUOTE_STALE",()->repository.draft(quote,stale,List.of(evidence()),"","cash",null));
    }

    @Test public void cashEligibilityNeverReplacesVerifiedEvidenceOrValidFeeAmount() throws Exception {
        RequestRepository repository=new RequestRepository(new Fake()); RequestData.Quote quote=quote(FeeDeclaration.REQUIRED);
        expectCode("UPLOAD_INVALID",()->repository.draft(quote,options(),List.of(),"","cash",null));
        RequestData.Service invalid=new RequestData.Service(SERVICE,"OFF-REG","Test service",7,FeeDeclaration.REQUIRED,
                BigDecimal.ZERO,"PHP",List.of("physical"),List.of("student-id"),true);
        expectCode("VALIDATION_FAILED",()->repository.draft(new RequestData.Quote(QUOTE,OWNER,"physical",invalid,true),options(),List.of(evidence()),"","cash",null));
    }

    @Test public void freeStillRequiresNullMethodAndPendingAndEgovpayRemainUnavailable() throws Exception {
        RequestRepository repository=new RequestRepository(new Fake());
        RequestData.QuoteOptions freeOptions=new RequestData.QuoteOptions(QUOTE,7,new RequestData.ActionOptions(false,"POLICY_BLOCKED"),options().evidence);
        RequestData.Submission free=repository.draft(quote(FeeDeclaration.FREE),freeOptions,List.of(evidence()),"",null,null);
        assertNotNull("Free quote must preserve null payment",free); assertNull(free.paymentMethod);
        expectCode("VALIDATION_FAILED",()->repository.draft(quote(FeeDeclaration.FREE),freeOptions,List.of(evidence()),"","cash",null));
        expectCode("CONFIGURATION_REQUIRED",()->repository.draft(quote(FeeDeclaration.PENDING_REVIEW),options(),List.of(evidence()),"",null,null));
        expectCode("PROVIDER_UNAVAILABLE",()->repository.draft(quote(FeeDeclaration.REQUIRED),options(),List.of(evidence()),"","egovpay",null));
    }

    @Test public void cashSubmitUsesRetainedOptionsAndReloadsCanonicalRecord() throws Exception {
        Fake wire=new Fake(); RequestRepository repository=new RequestRepository(wire);
        RequestData.Submission draft=repository.draft(quote(FeeDeclaration.REQUIRED),options(),List.of(evidence()),"notes","cash",null);
        assertNotNull("Cash submission needs a retained draft",draft);
        wire.reply=recordRow(1); wire.rows=new JSONArray().put(recordRow(4));
        RequestData.Record result=repository.submit(draft); assertNotNull(result); assertEquals(4,result.version);
        assertEquals("submit_request",wire.rpcName); assertEquals(5,wire.args.length());
        assertEquals("cash",wire.args.getString("p_payment_method")); assertEquals(draft.operationKey,wire.args.getString("p_idempotency_key"));
        assertEquals(FILE,wire.args.getJSONArray("p_evidence_file_ids").getString(0)); assertEquals(1,wire.getCalls);
    }

    @Test public void serverRecheckFailureIsReturnedWithoutSilentRetryOrCanonicalSuccess() throws Exception {
        for(String code:List.of("QUOTE_STALE","CONFIGURATION_REQUIRED")) {
            Fake wire=new Fake(); RequestRepository repository=new RequestRepository(wire);
            RequestData.Submission draft=repository.draft(quote(FeeDeclaration.REQUIRED),options(),List.of(evidence()),"","cash",null);
            assertNotNull("Cash submission needs a retained draft",draft);
            wire.failure=new RequestRepository.WorkflowException(code);
            expectCode(code,()->repository.submit(draft)); assertEquals(1,wire.rpcCalls); assertEquals(0,wire.getCalls);
        }
    }

    @Test public void evidenceInputRetainsDigestAndKeyBeforeAnyNetworkOperation() throws Exception {
        Fake wire=new Fake(); RequestData.PreparedUpload input=input(new RequestRepository(wire),null,BYTES);
        assertNull(input.fileId); assertEquals(3,input.sizeBytes);
        assertEquals("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81",input.contentSha256);
        assertNotNull(input.operationKey); assertEquals(0,wire.edgeCalls); assertEquals(0,wire.rpcCalls);
    }

    @Test public void identicalEvidenceInputRetainsKeyAndChangedBytesOrMetadataGetsNewKey() throws Exception {
        RequestRepository repository=new RequestRepository(new Fake());
        RequestData.PreparedUpload first=input(repository,null,BYTES), same=input(repository,first,BYTES.clone());
        assertEquals(first.operationKey,same.operationKey);
        assertNotEquals(first.operationKey,input(repository,first,new byte[]{3,2,1}).operationKey);
        RequestData.PreparedUpload changed=repository.evidenceUpload(quote(FeeDeclaration.FREE),freeOptions(),"student-id","application/pdf",BYTES,first);
        assertNotNull(changed); assertNotEquals(first.operationKey,changed.operationKey);
    }

    @Test public void evidenceInputRequiresApprovedMimeSizeAndSnapshotRequirement() throws Exception {
        Fake wire=new Fake(); RequestRepository repository=new RequestRepository(wire);
        expectCode("UPLOAD_INVALID",()->repository.evidenceUpload(quote(FeeDeclaration.FREE),freeOptions(),"student-id","image/jpeg",BYTES,null));
        expectCode("UPLOAD_INVALID",()->repository.evidenceUpload(quote(FeeDeclaration.FREE),freeOptions(),"other","image/png",BYTES,null));
        expectCode("UPLOAD_INVALID",()->repository.evidenceUpload(quote(FeeDeclaration.FREE),freeOptions(),"student-id","image/png",new byte[0],null));
        expectCode("UPLOAD_INVALID",()->repository.evidenceUpload(quote(FeeDeclaration.FREE),freeOptions(),"student-id","image/png",new byte[4],null));
        RequestData.QuoteOptions unavailable=new RequestData.QuoteOptions(QUOTE,7,freeOptions().cash,new RequestData.FileOptions(false,"CONFIGURATION_REQUIRED",null,null));
        expectCode("CONFIGURATION_REQUIRED",()->repository.evidenceUpload(quote(FeeDeclaration.FREE),unavailable,"student-id","image/png",BYTES,null));
        assertEquals(0,wire.edgeCalls);
    }

    @Test public void prepareUsesExactlySevenEdgeFieldsAndReturnsOnlyPendingDescriptor() throws Exception {
        Fake wire=new Fake(); wire.edgeReply=fileRow("pending"); RequestRepository repository=new RequestRepository(wire);
        RequestData.PreparedUpload input=input(repository,null,BYTES), prepared=repository.prepareEvidence(input);
        assertNotNull("Prepare must return pending metadata identity",prepared); assertEquals(FILE,prepared.fileId);
        assertEquals(input.operationKey,prepared.operationKey); assertEquals("request-file",wire.edgeName); assertEquals(7,wire.edgeArgs.length());
        assertEquals(SERVICE,wire.edgeArgs.getString("service_id")); assertTrue(wire.edgeArgs.has("request_id"));
        assertTrue(wire.edgeArgs.isNull("request_id")); assertEquals("evidence",wire.edgeArgs.getString("kind"));
        assertEquals("student-id",wire.edgeArgs.getString("requirement")); assertEquals("image/png",wire.edgeArgs.getString("mime_type"));
        assertEquals(3,wire.edgeArgs.getLong("size_bytes")); assertEquals(input.operationKey,wire.edgeArgs.getString("idempotency_key"));
        assertEquals(0,wire.uploadCalls);
    }

    @Test public void lostPrepareResponseRetainsInputKeyForRetryWithoutAnotherOperationIdentity() throws Exception {
        Fake wire=new Fake(); RequestRepository repository=new RequestRepository(wire);
        RequestData.PreparedUpload input=input(repository,null,BYTES); wire.failure=new RequestRepository.WorkflowException("TEMPORARILY_UNAVAILABLE");
        expectCode("TEMPORARILY_UNAVAILABLE",()->repository.prepareEvidence(input));
        wire.failure=null; wire.edgeReply=fileRow("pending");
        RequestData.PreparedUpload prepared=repository.prepareEvidence(input);
        assertNotNull("Retry must recover pending metadata",prepared); assertEquals(FILE,prepared.fileId);
        assertEquals(List.of(input.operationKey,input.operationKey),wire.prepareKeys);
    }

    @Test public void prepareRejectsWrongOwnerServiceKindRequirementAndPrivateFields() throws Exception {
        Fake wire=new Fake(); RequestRepository repository=new RequestRepository(wire); RequestData.PreparedUpload input=input(repository,null,BYTES);
        wire.edgeReply=fileRow("pending").put("owner_id",OTHER);
        expectCode("FORBIDDEN",()->repository.prepareEvidence(input));
        for(String field:List.of("service_id","kind","requirement","mime_type","size_bytes","request_id","deleted_at")) {
            Object value=field.equals("size_bytes")?4:field.equals("service_id")||field.equals("request_id")?OTHER:"wrong";
            wire.edgeReply=fileRow("pending").put(field,value);
            expectCode("UPLOAD_INVALID",()->repository.prepareEvidence(input));
        }
        wire.edgeReply=fileRow("pending").put("object_path","private/object").put("policy",new JSONObject());
        expectCode("VALIDATION_FAILED",()->repository.prepareEvidence(input));
    }

    @Test public void uploadSendsBinaryUsingPreparedMetadataAndReturnsOnlyVerifiedEvidence() throws Exception {
        Fake wire=new Fake(); wire.edgeReply=fileRow("pending"); wire.uploadReply=fileRow("verified");
        RequestRepository repository=new RequestRepository(wire); RequestData.PreparedUpload prepared=prepared(repository);
        RequestData.Evidence result=repository.uploadEvidence(prepared,BYTES.clone());
        assertNotNull("Verified canonical metadata must become usable evidence",result);
        assertEquals(FILE,result.id); assertEquals(OWNER,result.ownerId); assertEquals(SERVICE,result.serviceId);
        assertEquals("verified",result.verification); assertTrue(result.current); assertNull(result.requestId);
        assertEquals("request-file",wire.uploadName); assertEquals(FILE,wire.uploadId); assertEquals("image/png",wire.uploadMime);
        assertArrayEquals(BYTES,wire.uploadBytes); assertEquals(1,wire.uploadCalls);
        assertNotNull(repository.draft(quote(FeeDeclaration.FREE),List.of(result),"",null,null));
    }

    @Test public void uploadRequiresPreparedIdAndIdenticalBytesBeforeNetwork() throws Exception {
        Fake wire=new Fake(); wire.edgeReply=fileRow("pending"); RequestRepository repository=new RequestRepository(wire);
        RequestData.PreparedUpload input=input(repository,null,BYTES);
        expectCode("UPLOAD_INVALID",()->repository.uploadEvidence(input,BYTES));
        RequestData.PreparedUpload prepared=repository.prepareEvidence(input); assertNotNull(prepared);
        expectCode("UPLOAD_INVALID",()->repository.uploadEvidence(prepared,new byte[]{3,2,1}));
        assertEquals(0,wire.uploadCalls);
    }

    @Test public void uploadRejectsUnverifiedSupersededForeignOrMismatchedMetadata() throws Exception {
        Fake wire=new Fake(); wire.edgeReply=fileRow("pending"); RequestRepository repository=new RequestRepository(wire);
        RequestData.PreparedUpload prepared=prepared(repository);
        for(String state:List.of("pending","rejected")) {
            wire.uploadReply=fileRow(state); expectCode("UPLOAD_INVALID",()->repository.uploadEvidence(prepared,BYTES));
        }
        wire.uploadReply=fileRow("verified").put("owner_id",OTHER);
        expectCode("FORBIDDEN",()->repository.uploadEvidence(prepared,BYTES));
        for(String field:List.of("id","service_id","request_id","kind","requirement","mime_type","size_bytes","current","deleted_at")) {
            Object value=field.equals("size_bytes")?4:field.equals("current")?false:field.endsWith("id")?OTHER:"wrong";
            wire.uploadReply=fileRow("verified").put(field,value);
            expectCode("UPLOAD_INVALID",()->repository.uploadEvidence(prepared,BYTES));
        }
    }

    @Test public void lostUploadResponseCanRetrySamePreparedIdAndBytes() throws Exception {
        Fake wire=new Fake(); wire.edgeReply=fileRow("pending"); RequestRepository repository=new RequestRepository(wire);
        RequestData.PreparedUpload prepared=prepared(repository); wire.failure=new RequestRepository.WorkflowException("TEMPORARILY_UNAVAILABLE");
        expectCode("TEMPORARILY_UNAVAILABLE",()->repository.uploadEvidence(prepared,BYTES));
        wire.failure=null; wire.uploadReply=fileRow("verified");
        assertNotNull(repository.uploadEvidence(prepared,BYTES)); assertEquals(List.of(FILE,FILE),wire.uploadIds);
    }

    @Test public void oldAccountOptionsAndPreparedUploadResponsesCannotPublish() throws Exception {
        Fake wire=new Fake(); wire.invalidate=true;
        expectReplaced(()->new RequestRepository(wire).quoteOptions(quote(FeeDeclaration.REQUIRED)));
        wire.current=true; wire.invalidate=false; wire.edgeReply=fileRow("pending"); RequestRepository repository=new RequestRepository(wire);
        RequestData.PreparedUpload input=input(repository,null,BYTES); wire.invalidate=true;
        expectReplaced(()->repository.prepareEvidence(input));
        wire.current=true; wire.invalidate=false; RequestData.PreparedUpload prepared=repository.prepareEvidence(input); assertNotNull(prepared);
        wire.uploadReply=fileRow("verified"); wire.invalidate=true;
        expectReplaced(()->repository.uploadEvidence(prepared,BYTES));
    }

    @Test public void olderOverlappingQuoteOptionsCannotPublish() throws Exception {
        Fake wire=new Fake(); wire.entered=new CountDownLatch(1); wire.release=new CountDownLatch(1);
        RequestRepository repository=new RequestRepository(wire); AtomicReference<Throwable> outcome=new AtomicReference<>();
        Thread old=new Thread(()->{try { repository.quoteOptions(quote(FeeDeclaration.REQUIRED)); } catch(Throwable error) { outcome.set(error); }});
        old.start();
        try {
            assertTrue("First options call must reach transport",wire.entered.await(2,TimeUnit.SECONDS));
            assertNotNull(repository.quoteOptions(quote(FeeDeclaration.REQUIRED)));
        } finally { wire.release.countDown(); old.join(2000); }
        assertTrue(outcome.get() instanceof RequestRepository.WorkflowException);
        assertEquals("STALE_RESPONSE",((RequestRepository.WorkflowException)outcome.get()).code);
    }

    @Test public void olderOverlappingRequestOptionsCannotPublish() throws Exception {
        Fake wire=new Fake(); wire.reply=requestOptions(); wire.entered=new CountDownLatch(1); wire.release=new CountDownLatch(1);
        RequestRepository repository=new RequestRepository(wire); AtomicReference<Throwable> outcome=new AtomicReference<>();
        Thread old=new Thread(()->{try { repository.requestOptions(request()); } catch(Throwable error) { outcome.set(error); }});
        old.start();
        try {
            assertTrue("First request options call must reach transport",wire.entered.await(2,TimeUnit.SECONDS));
            assertNotNull(repository.requestOptions(request()));
        } finally { wire.release.countDown(); old.join(2000); }
        assertTrue(outcome.get() instanceof RequestRepository.WorkflowException);
        assertEquals("STALE_RESPONSE",((RequestRepository.WorkflowException)outcome.get()).code);
    }

    private static RequestData.PreparedUpload input(RequestRepository repository,RequestData.PreparedUpload previous,byte[] bytes) throws Exception {
        RequestData.PreparedUpload result=repository.evidenceUpload(quote(FeeDeclaration.FREE),freeOptions(),"student-id","image/png",bytes,previous);
        assertNotNull("Retained evidence input must exist before I/O",result); return result;
    }
    private static RequestData.PreparedUpload prepared(RequestRepository repository) throws Exception {
        RequestData.PreparedUpload result=repository.prepareEvidence(input(repository,null,BYTES));
        assertNotNull("Pending upload descriptor must exist",result); return result;
    }
    private static RequestData.QuoteOptions options() {
        return new RequestData.QuoteOptions(QUOTE,7,new RequestData.ActionOptions(true,null),
                new RequestData.FileOptions(true,null,List.of("image/png","application/pdf"),3L));
    }
    private static RequestData.QuoteOptions freeOptions() {
        return new RequestData.QuoteOptions(QUOTE,7,new RequestData.ActionOptions(false,"POLICY_BLOCKED"),options().evidence);
    }
    private static RequestData.Quote quote(FeeDeclaration fee) {
        RequestData.Service service=new RequestData.Service(SERVICE,"OFF-REG","Test service",7,fee,
                fee==FeeDeclaration.FREE?BigDecimal.ZERO:fee==FeeDeclaration.REQUIRED?new BigDecimal("9007199254740993.123456789"):null,
                fee==FeeDeclaration.REQUIRED?"PHP":null,List.of("physical"),List.of("student-id"),true);
        return new RequestData.Quote(QUOTE,OWNER,"physical",service,fee!=FeeDeclaration.PENDING_REVIEW);
    }
    private static RequestData.Evidence evidence() { return new RequestData.Evidence(FILE,OWNER,SERVICE,null,"student-id","verified",true); }
    private static RequestData.Record request() {
        return new RequestData.Record(REQUEST,"test-reference",OWNER,"OFF-REG","physical",TIME,4,RequestStatus.PROCESSING,quote(FeeDeclaration.FREE).service);
    }
    private static JSONObject action(boolean available,String blocker) throws Exception {
        return new JSONObject().put("available",available).put("blocker",blocker==null?JSONObject.NULL:blocker);
    }
    private static JSONObject fileOption(boolean available,String blocker,JSONArray mime,Object maximum) throws Exception {
        return action(available,blocker).put("mime_types",mime==null?JSONObject.NULL:mime).put("max_bytes",maximum==null?JSONObject.NULL:maximum);
    }
    private static JSONObject quoteOptions() throws Exception {
        return new JSONObject().put("quote_id",QUOTE).put("service_version",7).put("egovpay",false).put("cash",action(true,null))
                .put("evidence",fileOption(true,null,new JSONArray().put("image/png").put("application/pdf"),3));
    }
    private static JSONObject requestOptions() throws Exception { return new JSONObject().put("request_id",REQUEST).put("request_version",4).put("actions",new JSONObject()); }
    private static JSONObject fileRow(String verification) throws Exception {
        return new JSONObject().put("id",FILE).put("owner_id",OWNER).put("service_id",SERVICE).put("request_id",JSONObject.NULL)
                .put("kind","evidence").put("requirement","student-id").put("mime_type","image/png").put("size_bytes",3)
                .put("verification",verification).put("version",1).put("current",true).put("created_at",TIME).put("deleted_at",JSONObject.NULL);
    }
    private static JSONObject recordRow(long version) throws Exception {
        JSONObject service=new JSONObject().put("id",SERVICE).put("office_id","OFF-REG").put("name","Test service")
                .put("delivery_modes",new JSONArray().put("physical")).put("requirements",new JSONArray().put(new JSONObject().put("id","student-id")))
                .put("fee_state","required").put("amount","9007199254740993.123456789").put("currency","PHP").put("active",true).put("version",7).put("is_demo",true);
        return new JSONObject().put("id",REQUEST).put("reference","test-reference").put("owner_id",OWNER).put("office_id","OFF-REG")
                .put("service_id",SERVICE).put("snapshot",service).put("delivery_mode","physical").put("notes","notes")
                .put("status","Awaiting Payment").put("version",version).put("created_at",TIME).put("terminal_at",JSONObject.NULL);
    }
    private interface Checked { void run() throws Exception; }
    private static void expectCode(String code,Checked action) throws Exception {
        try { action.run(); fail("Expected workflow rejection "+code); }
        catch(RequestRepository.WorkflowException error) { assertEquals(code,error.code); }
    }
    private static void expectReplaced(Checked action) throws Exception {
        try { action.run(); fail("Old account result must be rejected"); }
        catch(IllegalStateException error) { assertEquals("Account replaced",error.getMessage()); }
    }
    private static final class Fake implements RequestRepository.Transport {
        JSONObject reply,edgeReply,uploadReply,args,edgeArgs; JSONArray rows=new JSONArray();
        String rpcName,edgeName,uploadName,uploadId,uploadMime; byte[] uploadBytes;
        int rpcCalls,edgeCalls,uploadCalls,getCalls; volatile boolean current=true; boolean invalidate;
        Exception failure; CountDownLatch entered,release;
        final List<String> prepareKeys=new ArrayList<>(),uploadIds=new ArrayList<>();
        Fake() throws Exception { reply=quoteOptions(); }
        @Override public String accountId() { return OWNER; }
        @Override public void requireCurrent() { if(!current)throw new IllegalStateException("Account replaced"); }
        @Override public JSONArray get(String path) throws Exception { ++getCalls; if(failure!=null)throw failure; return rows; }
        @Override public JSONObject rpc(String name,JSONObject value) throws Exception {
            int call; synchronized(this) { call=++rpcCalls; rpcName=name; args=value; }
            if(entered!=null && call==1) { entered.countDown(); if(!release.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Test release timeout"); }
            if(failure!=null)throw failure; if(invalidate)current=false; return reply;
        }
        @Override public JSONObject edge(String name,JSONObject value) throws Exception {
            ++edgeCalls; edgeName=name; edgeArgs=value; prepareKeys.add(value.getString("idempotency_key"));
            if(failure!=null)throw failure; if(invalidate)current=false; return edgeReply;
        }
        @Override public JSONObject upload(String name,String fileId,byte[] bytes,String mimeType) throws Exception {
            ++uploadCalls; uploadName=name; uploadId=fileId; uploadBytes=bytes.clone(); uploadMime=mimeType; uploadIds.add(fileId);
            if(failure!=null)throw failure; if(invalidate)current=false; return uploadReply;
        }
    }
}
