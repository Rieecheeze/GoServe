package ph.edu.tip.goserve.data;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public final class RequestRepositoryTest {
    private static final String OWNER="00000000-0000-4000-8000-000000000001";
    private static final String SERVICE="00000000-0000-4000-8000-000000000002";
    private static final String QUOTE="00000000-0000-4000-8000-000000000003";
    private static final String FILE="00000000-0000-4000-8000-000000000004";
    private static final String REQUEST="00000000-0000-4000-8000-000000000005";
    private static final String TIME="2026-10-19T01:00:00.000Z";

    @Test public void incompatibleFeeAndWorkflowSnapshotsAreRejectedBeforeRendering() throws Exception {
        for(String fee:new String[]{"free","pending"}) {
            Fake wire=new Fake(); JSONObject row=requestRow();
            row.put("snapshot",serviceRow(fee,fee.equals("free")?"0":null,null,false));
            row.put("status",fee.equals("free")?"Awaiting Payment":"Processing");
            wire.rows=new JSONArray().put(row);
            expectCode("VALIDATION_FAILED",()->new RequestRepository(wire).request(REQUEST,null));
        }
    }

    @Test public void tableDecimalLexemeSurvivesCatalogParsing() throws Exception {
        Fake wire=new Fake();
        wire.rows=(JSONArray)SupabaseJson.parse("["+serviceJson("required","9007199254740993.123456789","\"PHP\"",false)+"]");
        RequestData.ServicePage page=new RequestRepository(wire).services("OFF-REG",null);
        assertNotNull("Canonical catalog page is missing",page);
        assertEquals(new BigDecimal("9007199254740993.123456789"),page.items.get(0).amount);
        assertEquals(FeeDeclaration.REQUIRED,page.items.get(0).fee);
    }

    @Test public void emptyAuthenticatedCatalogDoesNotCreatePreviewServices() throws Exception {
        RequestData.ServicePage page=new RequestRepository(new Fake()).services("OFF-REG",null);
        assertNotNull("Authenticated empty page must be represented",page);
        assertTrue(page.items.isEmpty()); assertNull(page.nextId);
    }

    @Test public void serviceReadsAreBoundedOfficeScopedAndContinueFullPages() throws Exception {
        Fake wire=new Fake(); wire.rows=new JSONArray();
        for(int n=1;n<=50;n++) wire.rows.put(serviceRow("free","0",null,false).put("id",id(n)));
        RequestRepository repository=new RequestRepository(wire);
        RequestData.ServicePage first=repository.services("OFF-REG",null);
        assertNotNull("First bounded catalog page is missing",first);
        assertEquals(id(50),first.nextId);
        wire.rows=new JSONArray(); repository.services("OFF-REG",first.nextId);
        String query=decode(wire.paths.get(1));
        assertTrue(query.contains("office_id=eq.OFF-REG"));
        assertTrue(query.contains("id=gt."+id(50))); assertTrue(query.contains("order=id.asc"));
        assertTrue(query.contains("limit=50")); assertFalse(query.contains("select=*"));
        assertTrue(query.contains("requirements"));
    }

    @Test public void requestCursorRetainsEqualTimestampRowsAndOwnerScope() throws Exception {
        Fake wire=new Fake(); wire.rows=new JSONArray();
        for(int n=50;n>=1;n--) wire.rows.put(requestRow().put("id",id(n)));
        RequestRepository repository=new RequestRepository(wire);
        RequestData.RequestPage page=repository.requests(null,null,null);
        assertNotNull("Request page is missing",page);
        assertEquals(id(1),page.nextId); assertEquals(TIME,page.nextCreatedAt);
        wire.rows=new JSONArray().put(requestRow().put("id",id(0)));
        RequestData.RequestPage second=repository.requests(null,page.nextCreatedAt,page.nextId);
        assertEquals(id(0),second.items.get(0).id);
        String query=decode(wire.paths.get(1));
        assertTrue(query.contains("owner_id=eq."+OWNER));
        assertTrue(query.contains("order=created_at.desc,id.desc"));
        assertTrue(query.contains("or=(created_at.lt."+TIME+",and(created_at.eq."+TIME+",id.lt."+id(1)+"))"));
        assertTrue(query.contains("limit=50"));
    }

    @Test public void quoteUsesOwnerBoundSnapshotAndExactNamedArguments() throws Exception {
        Fake wire=new Fake(); wire.reply=quoteRow("required","123.4500","PHP",true);
        RequestData.Quote quote=new RequestRepository(wire).quote(SERVICE,"physical");
        assertNotNull("Owner-bound quote is missing",quote);
        assertEquals("quote_request",wire.rpcName); assertEquals(2,wire.args.length());
        assertEquals(SERVICE,wire.args.getString("p_service_id"));
        assertEquals("physical",wire.args.getString("p_delivery_mode"));
        wire.reply.getJSONObject("service").put("amount","999.00");
        assertEquals(new BigDecimal("123.4500"),quote.service.amount);
    }

    @Test public void pendingOrMissingFeeCannotBecomeFreeSubmission() throws Exception {
        RequestRepository repository=new RequestRepository(new Fake());
        expectCode("CONFIGURATION_REQUIRED",()->repository.draft(quote(FeeDeclaration.PENDING_REVIEW,false,List.of()),List.of(),"",null,null));
        Fake missing=new Fake(); missing.reply=quoteRow("free","0",null,false);
        missing.reply.getJSONObject("service").remove("fee_state");
        expectCode("VALIDATION_FAILED",()->new RequestRepository(missing).quote(SERVICE,"physical"));
    }

    @Test public void wrongOwnerQuoteCannotCreateDraft() throws Exception {
        RequestData.Quote wrong=new RequestData.Quote(QUOTE,id(99),"physical",service(FeeDeclaration.FREE,List.of()),true);
        expectCode("FORBIDDEN",()->new RequestRepository(new Fake()).draft(wrong,List.of(),"",null,null));
    }

    @Test public void quoteWithMissingRequirementIdFailsBeforeDraft() throws Exception {
        Fake wire=new Fake(); wire.reply=quoteRow("free","0",null,true);
        wire.reply.getJSONObject("service").put("requirements",new JSONArray().put(new JSONObject()));
        RequestRepository repository=new RequestRepository(wire);
        expectCode("VALIDATION_FAILED",()->repository.draft(repository.quote(SERVICE,"physical"),List.of(),"",null,null));
    }

    @Test public void quoteWithNullRequirementIdFailsBeforeDraft() throws Exception {
        Fake wire=new Fake(); wire.reply=quoteRow("free","0",null,true);
        wire.reply.getJSONObject("service").put("requirements",new JSONArray().put(new JSONObject().put("id",JSONObject.NULL)));
        RequestRepository repository=new RequestRepository(wire);
        expectCode("VALIDATION_FAILED",()->repository.draft(repository.quote(SERVICE,"physical"),List.of(),"",null,null));
    }

    @Test public void requiredFeeRemainsBlockedWithoutApprovedCashEligibilityProjection() throws Exception {
        expectCode("CONFIGURATION_REQUIRED",()->new RequestRepository(new Fake()).draft(
                quote(FeeDeclaration.REQUIRED,true,List.of()),List.of(),"","cash",null));
    }

    @Test public void freeDraftOmitsPaymentAndRetriesReuseOnlyIdenticalPayloadKey() throws Exception {
        RequestRepository repository=new RequestRepository(new Fake());
        RequestData.Quote quote=quote(FeeDeclaration.FREE,true,List.of());
        RequestData.Submission first=repository.draft(quote,List.of(),"Bring original",null,null);
        assertNotNull("Free draft is missing",first);
        RequestData.Submission retry=repository.draft(quote,List.of(),"Bring original",null,first);
        RequestData.Submission changed=repository.draft(quote,List.of(),"Changed notes",null,first);
        assertEquals(first.operationKey,retry.operationKey);
        assertNotEquals(first.operationKey,changed.operationKey);
        assertNull(first.paymentMethod);
        expectCode("VALIDATION_FAILED",()->repository.draft(quote,List.of(),"","cash",null));
    }

    @Test public void evidenceNeedsVerifiedCurrentOwnerServiceAndRequirementMatch() throws Exception {
        RequestRepository repository=new RequestRepository(new Fake());
        RequestData.Quote quote=quote(FeeDeclaration.FREE,true,List.of("student-id"));
        expectCode("UPLOAD_INVALID",()->repository.draft(quote,List.of(evidence(OWNER,SERVICE,"student-id","pending",true)),"",null,null));
        expectCode("FORBIDDEN",()->repository.draft(quote,List.of(evidence(id(99),SERVICE,"student-id","verified",true)),"",null,null));
        expectCode("UPLOAD_INVALID",()->repository.draft(quote,List.of(evidence(OWNER,id(99),"student-id","verified",true)),"",null,null));
        expectCode("UPLOAD_INVALID",()->repository.draft(quote,List.of(evidence(OWNER,SERVICE,"other","verified",true)),"",null,null));
        expectCode("UPLOAD_INVALID",()->repository.draft(quote,List.of(evidence(OWNER,SERVICE,"student-id","verified",false)),"",null,null));
        RequestData.Submission valid=repository.draft(quote,List.of(evidence(OWNER,SERVICE,"student-id","verified",true)),"",null,null);
        assertNotNull(valid); assertEquals(FILE,valid.evidence.get(0).id);
    }

    @Test public void evidenceReadsNeverExposeAnotherOwnersUnboundFiles() throws Exception {
        Fake wire=new Fake(); wire.rows=new JSONArray().put(new JSONObject()
                .put("id",FILE).put("owner_id",OWNER).put("service_id",SERVICE).put("request_id",JSONObject.NULL)
                .put("kind","evidence").put("requirement","student-id").put("mime_type","image/png")
                .put("size_bytes",123).put("verification","verified").put("version",1).put("current",true)
                .put("created_at",TIME).put("deleted_at",JSONObject.NULL));
        List<RequestData.Evidence> files=new RequestRepository(wire).evidence(SERVICE,null);
        assertNotNull("Canonical evidence page is missing",files); assertEquals(FILE,files.get(0).id);
        String query=decode(wire.paths.get(0));
        assertTrue(query.contains("owner_id=eq."+OWNER)); assertTrue(query.contains("service_id=eq."+SERVICE));
        assertTrue(query.contains("request_id=is.null")); assertTrue(query.contains("kind=eq.evidence"));
        assertTrue(query.contains("order=id.asc")); assertTrue(query.contains("limit=50"));
    }

    @Test public void evidencePrepareDoesNotInventMimeOrSizePolicy() throws Exception {
        Fake wire=new Fake();
        expectCode("CONFIGURATION_REQUIRED",()->new RequestRepository(wire).prepareEvidence(
                quote(FeeDeclaration.FREE,true,List.of("student-id")),"student-id","image/png",123,id(9)));
        assertEquals(0,wire.rpcCalls); assertTrue(wire.paths.isEmpty());
    }

    @Test public void submissionUsesExactFreePayloadAndReloadsCanonicalRecordAfterReplay() throws Exception {
        Fake wire=new Fake(); wire.reply=requestRow();
        wire.rows=new JSONArray().put(requestRow().put("version",4).put("status","Processing"));
        RequestRepository repository=new RequestRepository(wire);
        RequestData.Submission draft=new RequestData.Submission(quote(FeeDeclaration.FREE,true,List.of()),List.of(),"Notes",null,id(9));
        RequestData.Record result=repository.submit(draft);
        assertNotNull("Canonical submitted record is missing",result);
        assertEquals(4,result.version); assertEquals("Processing",result.status.wireValue);
        assertEquals("submit_request",wire.rpcName); assertEquals(5,wire.args.length());
        assertEquals(QUOTE,wire.args.getString("p_quote_id")); assertEquals("Notes",wire.args.getString("p_notes"));
        assertTrue(wire.args.isNull("p_payment_method")); assertEquals(0,wire.args.getJSONArray("p_evidence_file_ids").length());
        assertEquals(id(9),wire.args.getString("p_idempotency_key"));
        assertTrue(decode(wire.paths.get(0)).contains("id=eq."+REQUEST));
    }

    @Test public void staleQuoteCannotReplayAsSuccessfulSubmission() throws Exception {
        Fake wire=new Fake(); wire.rpcFailure=new RequestRepository.WorkflowException("QUOTE_STALE");
        expectCode("QUOTE_STALE",()->new RequestRepository(wire).submit(new RequestData.Submission(
                quote(FeeDeclaration.FREE,true,List.of()),List.of(),"",null,id(9))));
        assertTrue(wire.paths.isEmpty());
    }

    @Test public void directOfficeDenialIsPreservedInsteadOfReturningEmptyCatalog() throws Exception {
        Fake wire=new Fake(); wire.getFailure=new RequestRepository.WorkflowException("FORBIDDEN");
        expectCode("FORBIDDEN",()->new RequestRepository(wire).services("OFF-REG",null));
    }

    @Test public void oldAccountResultIsRejectedAfterNetworkBoundary() throws Exception {
        Fake wire=new Fake(); wire.rows=new JSONArray(); wire.invalidateDuringGet=true;
        try { new RequestRepository(wire).services("OFF-REG",null); fail("Old account result must be rejected"); }
        catch(IllegalStateException expected) { assertEquals("Account replaced",expected.getMessage()); }
    }

    @Test public void overlappingCatalogLoadCannotPublishOlderResponse() throws Exception {
        Fake wire=new Fake(); wire.entered=new CountDownLatch(1); wire.release=new CountDownLatch(1);
        RequestRepository repository=new RequestRepository(wire); AtomicReference<Throwable> outcome=new AtomicReference<>();
        Thread old=new Thread(()->{try{ repository.services("OFF-REG",null); }
            catch(Throwable error){outcome.set(error);}});
        old.start();
        try {
            assertTrue("First catalog read must reach the transport",wire.entered.await(2,TimeUnit.SECONDS));
            RequestData.ServicePage newer=repository.services("OFF-CASH",null);
            assertNotNull(newer);
        } finally { wire.release.countDown(); old.join(2000); }
        assertTrue("Older response must be rejected",outcome.get() instanceof RequestRepository.WorkflowException);
        assertEquals("STALE_RESPONSE",((RequestRepository.WorkflowException)outcome.get()).code);
    }

    @Test public void exactRequestReadIsOwnerScopedBoundedAndNeverWrites() throws Exception {
        Fake wire=new Fake(); wire.rows=new JSONArray().put(requestRow());
        RequestData.Record value=new RequestRepository(wire).request(REQUEST,null);
        assertEquals(REQUEST,value.id); assertEquals("canonical-reference",value.reference); assertEquals(FeeDeclaration.FREE,value.snapshot.fee);
        String query=decode(wire.paths.get(0)); assertTrue(query.startsWith("/rest/v1/requests?"));
        assertTrue(query.contains("id=eq."+REQUEST)); assertTrue(query.contains("owner_id=eq."+OWNER));
        assertTrue(query.contains("limit=2")); assertFalse(query.contains("limit=50")); assertFalse(query.contains("select=*")); assertEquals(0,wire.rpcCalls);
    }
    @Test public void assignedOfficeDetailUsesItsOfficeBoundaryWithoutPretendingToOwnTheRequest() throws Exception {
        Fake wire=new Fake(); wire.rows=new JSONArray().put(requestRow().put("owner_id",id(99)));
        RequestData.Record value=new RequestRepository(wire).request(REQUEST,"OFF-REG"); assertEquals(id(99),value.ownerId);
        String query=decode(wire.paths.get(0)); assertTrue(query.contains("office_id=eq.OFF-REG")); assertFalse(query.contains("owner_id=eq."));
    }
    @Test public void exactRequestReadRequiresExactlyOneVisibleRow() throws Exception {
        Fake wire=new Fake(); RequestRepository repository=new RequestRepository(wire);
        expectCode("NOT_FOUND",()->repository.request(REQUEST,null));
        wire.rows=new JSONArray().put(requestRow()).put(requestRow()); expectCode("VALIDATION_FAILED",()->repository.request(REQUEST,null));
    }
    @Test public void invalidDetailRoutesFailBeforeAnyTransport() throws Exception {
        Fake wire=new Fake(); RequestRepository repository=new RequestRepository(wire);
        for(String value:new String[]{null,"","not-a-uuid",REQUEST+"&owner_id=any"})expectCode("VALIDATION_FAILED",()->repository.request(value,null));
        expectCode("VALIDATION_FAILED",()->repository.request(REQUEST,"arbitrary-office")); assertTrue(wire.paths.isEmpty());
    }
    @Test public void exactRequestReadRejectsAnotherIdentityOwnerOrOffice() throws Exception {
        Fake wire=new Fake(); RequestRepository repository=new RequestRepository(wire);
        wire.rows=new JSONArray().put(requestRow().put("id",id(99))); expectCode("FORBIDDEN",()->repository.request(REQUEST,null));
        wire.rows=new JSONArray().put(requestRow().put("owner_id",id(99))); expectCode("FORBIDDEN",()->repository.request(REQUEST,null));
        wire.rows=new JSONArray().put(requestRow()); expectCode("FORBIDDEN",()->repository.request(REQUEST,"OFF-CASH"));
    }
    @Test public void exactRequestReadPreservesTheAuthoritativeDecimalSnapshot() throws Exception {
        Fake wire=new Fake(); wire.rows=new JSONArray().put(requestRow().put("status","Awaiting Payment")
            .put("snapshot",serviceRow("required","9007199254740993.123456789","PHP",false)));
        RequestData.Record value=new RequestRepository(wire).request(REQUEST,null);
        assertEquals(new BigDecimal("9007199254740993.123456789"),value.snapshot.amount); assertEquals("PHP",value.snapshot.currency);
        assertEquals("Awaiting Payment",value.status.wireValue);
    }
    @Test public void exactRequestReadRejectsAccountReplacementAtTheNetworkBoundary() throws Exception {
        Fake wire=new Fake(); wire.rows=new JSONArray().put(requestRow()); wire.invalidateDuringGet=true;
        try { new RequestRepository(wire).request(REQUEST,null); fail("Replaced account must not publish a request"); }
        catch(IllegalStateException expected) { assertEquals("Account replaced",expected.getMessage()); }
    }
    @Test public void aNewExactReadInvalidatesAnOlderDetailResponse() throws Exception {
        Fake wire=new Fake(); wire.rows=new JSONArray().put(requestRow()); wire.entered=new CountDownLatch(1); wire.release=new CountDownLatch(1);
        RequestRepository repository=new RequestRepository(wire); AtomicReference<Throwable> outcome=new AtomicReference<>();
        Thread old=new Thread(()->{try { repository.request(REQUEST,"OFF-REG"); } catch(Throwable error) { outcome.set(error); }}); old.start();
        try { assertTrue(wire.entered.await(2,TimeUnit.SECONDS)); assertEquals(REQUEST,repository.request(REQUEST,null).id); }
        finally { wire.release.countDown(); old.join(2000); }
        assertTrue(outcome.get() instanceof RequestRepository.WorkflowException); assertEquals("STALE_RESPONSE",((RequestRepository.WorkflowException)outcome.get()).code);
    }
    @Test public void aListRefreshDoesNotInvalidateAnIndependentExactDetailRead() throws Exception {
        Fake wire=new Fake(); wire.rows=new JSONArray().put(requestRow()); wire.entered=new CountDownLatch(1); wire.release=new CountDownLatch(1);
        RequestRepository repository=new RequestRepository(wire); AtomicReference<RequestData.Record> result=new AtomicReference<>(); AtomicReference<Throwable> error=new AtomicReference<>();
        Thread detail=new Thread(()->{try { result.set(repository.request(REQUEST,"OFF-REG")); } catch(Throwable failure) { error.set(failure); }}); detail.start();
        try { assertTrue(wire.entered.await(2,TimeUnit.SECONDS)); assertEquals(1,repository.requests(null,null,null).items.size()); }
        finally { wire.release.countDown(); detail.join(2000); }
        assertNull(error.get()); assertNotNull(result.get()); assertEquals(REQUEST,result.get().id);
    }

    @Test public void officeStatusFilterKeepsBothCursorFieldsAndTheBoundedOfficeQuery() throws Exception {
        Fake wire=new Fake(); wire.rows=new JSONArray().put(requestRow().put("id",id(0)).put("status","Processing"));
        RequestData.RequestPage page=new RequestRepository(wire).requests("OFF-REG",ph.edu.tip.goserve.domain.RequestStatus.PROCESSING,TIME,id(1));
        assertEquals(1,page.items.size()); String path=decode(wire.paths.get(0));
        assertTrue(path.contains("status=eq.Processing")); assertTrue(path.contains("office_id=eq.OFF-REG")); assertTrue(path.contains("limit=50"));
        assertTrue(path.contains("created_at.lt."+TIME)); assertTrue(path.contains("id.lt."+id(1)));
    }
    @Test public void filteredResponseCannotMixAnotherQueueCategory() throws Exception {
        Fake wire=new Fake(); wire.rows=new JSONArray().put(requestRow());
        expectCode("VALIDATION_FAILED",()->new RequestRepository(wire).requests("OFF-REG",ph.edu.tip.goserve.domain.RequestStatus.PROCESSING,null,null));
    }
    @Test public void ownerScopeAndUnsupportedQueueCategoriesNeverTriggerFilteredReads() throws Exception {
        Fake wire=new Fake(); RequestRepository repo=new RequestRepository(wire);
        expectCode("FORBIDDEN",()->repo.requests(null,ph.edu.tip.goserve.domain.RequestStatus.PROCESSING,null,null));
        expectCode("VALIDATION_FAILED",()->repo.requests("OFF-REG",ph.edu.tip.goserve.domain.RequestStatus.COMPLETED,null,null));
        assertTrue(wire.paths.isEmpty());
    }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static String decode(String value) { return URLDecoder.decode(value,StandardCharsets.UTF_8); }
    private static JSONObject serviceRow(String fee,String amount,String currency,boolean requirement) throws Exception {
        return new JSONObject(serviceJson(fee,amount==null?"null":JSONObject.quote(amount),currency==null?"null":JSONObject.quote(currency),requirement));
    }
    private static String serviceJson(String fee,String amount,String currency,boolean requirement) {
        return "{\"id\":\""+SERVICE+"\",\"office_id\":\"OFF-REG\",\"name\":\"Approved service\",\"delivery_modes\":[\"physical\"],"
                +"\"requirements\":"+(requirement?"[{\"id\":\"student-id\"}]":"[]")+",\"fee_state\":\""+fee+"\",\"amount\":"+amount
                +",\"currency\":"+currency+",\"active\":true,\"version\":7,\"is_demo\":true}";
    }
    private static JSONObject quoteRow(String fee,String amount,String currency,boolean submittable) throws Exception {
        return new JSONObject().put("id",QUOTE).put("owner_id",OWNER).put("service_id",SERVICE)
                .put("service_version",7).put("delivery_mode","physical").put("created_at",TIME)
                .put("service",serviceRow(fee,amount,currency,false)).put("submittable",submittable);
    }
    private static JSONObject requestRow() throws Exception {
        return new JSONObject().put("id",REQUEST).put("reference","canonical-reference").put("owner_id",OWNER)
                .put("office_id","OFF-REG").put("service_id",SERVICE).put("snapshot",serviceRow("free","0",null,false))
                .put("delivery_mode","physical").put("notes","").put("status","Submitted").put("version",1)
                .put("created_at",TIME).put("terminal_at",JSONObject.NULL);
    }
    private static RequestData.Service service(FeeDeclaration fee,List<String> requirements) {
        return new RequestData.Service(SERVICE,"OFF-REG","Approved service",7,fee,
                fee==FeeDeclaration.PENDING_REVIEW?null:fee==FeeDeclaration.FREE?BigDecimal.ZERO:new BigDecimal("123.45"),
                fee==FeeDeclaration.REQUIRED?"PHP":null,List.of("physical"),requirements,true);
    }
    private static RequestData.Quote quote(FeeDeclaration fee,boolean submittable,List<String> requirements) {
        return new RequestData.Quote(QUOTE,OWNER,"physical",service(fee,requirements),submittable);
    }
    private static RequestData.Evidence evidence(String owner,String service,String requirement,String verification,boolean current) {
        return new RequestData.Evidence(FILE,owner,service,null,requirement,verification,current);
    }
    private interface Checked { void run() throws Exception; }
    private static void expectCode(String code,Checked action) throws Exception {
        try { action.run(); fail("Expected workflow rejection "+code); }
        catch(RequestRepository.WorkflowException error) { assertEquals(code,error.code); }
    }
    private static final class Fake implements RequestRepository.Transport {
        JSONArray rows=new JSONArray(); JSONObject reply; String rpcName; JSONObject args;
        final List<String> paths=new ArrayList<>(); int rpcCalls; volatile boolean current=true;
        boolean invalidateDuringGet; Exception getFailure,rpcFailure;
        CountDownLatch entered,release;
        @Override public String accountId() { return OWNER; }
        @Override public void requireCurrent() { if(!current)throw new IllegalStateException("Account replaced"); }
        @Override public JSONArray get(String path) throws Exception {
            synchronized(paths) { paths.add(path); }
            if(getFailure!=null)throw getFailure;
            if(entered!=null && path.contains("OFF-REG")){ entered.countDown(); if(!release.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Test release timeout"); }
            if(invalidateDuringGet)current=false;
            return rows;
        }
        @Override public JSONObject rpc(String name,JSONObject value) throws Exception {
            ++rpcCalls; rpcName=name; args=value;
            if(rpcFailure!=null)throw rpcFailure;
            return reply;
        }
    }
}
