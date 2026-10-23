package ph.edu.tip.goserve.data;

import android.net.Uri;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.domain.PaymentData;
import ph.edu.tip.goserve.domain.PaymentMethod;
import ph.edu.tip.goserve.domain.PaymentStatus;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public final class PaymentRepositoryTest {
    private static final String ACTOR=id(1), PAYMENT=id(2), REQUEST=id(3), OTHER=id(4);
    private static final String REFERENCE="canonical-reference", TIME="2026-10-20T01:00:00Z";
    private static final String COLUMNS="id,request_id,method,status,amount,currency,version,confirmed_by,confirmed_at";

    @Test public void cashLookupUsesOnlyFalseNullNullAndLosslessSafeProjection() throws Exception {
        Fake wire=new Fake(); wire.review.put("amount","12345678901234567890.123456789");
        PaymentData.CashReview result=new PaymentRepository(wire).lookupCash(REFERENCE);
        assertNotNull("Safe cash lookup is missing",result);
        assertEquals(ACTOR,result.accountId); assertEquals(PAYMENT,result.paymentId); assertEquals(REQUEST,result.requestId);
        assertEquals(REFERENCE,result.reference); assertEquals("OFF-REG",result.officeId); assertEquals("Approved service",result.serviceName);
        assertEquals(new BigDecimal("12345678901234567890.123456789"),result.amount);
        assertEquals("PHP",result.currency); assertEquals(PaymentStatus.PENDING,result.status); assertEquals(1,result.version); assertNull(result.confirmedAt);
        assertEquals(1,wire.calls.size()); Call call=wire.calls.get(0);
        assertEquals("cash_payment",call.name); assertFalse(call.edge);
        assertEquals(Set.of("p_reference","p_confirm","p_expected_version","p_idempotency_key"),keys(call.args));
        assertEquals(REFERENCE,call.args.getString("p_reference")); assertEquals(Boolean.FALSE,call.args.get("p_confirm"));
        assertTrue(call.args.isNull("p_expected_version")); assertTrue(call.args.isNull("p_idempotency_key")); assertTrue(wire.paths.isEmpty());
    }

    @Test public void invalidReferenceNeverReachesTheServer() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        for(String value:new String[]{null,"","   "})expectCode("VALIDATION_FAILED",()->repo.lookupCash(value));
        assertTrue(wire.calls.isEmpty());
    }

    @Test public void lookupRejectsWrongReferenceAndMalformedSafeFields() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        wire.review=cashRow("pending",1).put("reference","different"); expectCode("FORBIDDEN",()->repo.lookupCash(REFERENCE));
        for(String field:List.of("payment_id","request_id","reference","office_id","service_name","amount","currency","status","version","confirmed_at")) {
            wire.review=cashRow("pending",1); wire.review.remove(field);
            expectCode("VALIDATION_FAILED",()->repo.lookupCash(REFERENCE));
        }
        wire.review=cashRow("pending",1).put("private_notes","hidden"); expectCode("VALIDATION_FAILED",()->repo.lookupCash(REFERENCE));
        wire.review=cashRow("pending",1).put("office_id","unknown"); expectCode("VALIDATION_FAILED",()->repo.lookupCash(REFERENCE));
        wire.review=cashRow("pending",1).put("service_name",false); expectCode("VALIDATION_FAILED",()->repo.lookupCash(REFERENCE));
    }

    @Test public void paymentMoneyStatusVersionAndConfirmationMetadataAreStrict() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        for(Object amount:new Object[]{"0","-1","NaN","Infinity",1.1d,JSONObject.NULL}) {
            wire.review=cashRow("pending",1).put("amount",amount); expectCode("VALIDATION_FAILED",()->repo.lookupCash(REFERENCE));
        }
        for(Object version:new Object[]{0,-1,1.5,"1",JSONObject.NULL}) {
            wire.review=cashRow("pending",1).put("version",version); expectCode("VALIDATION_FAILED",()->repo.lookupCash(REFERENCE));
        }
        wire.review=cashRow("unknown",1); expectCode("VALIDATION_FAILED",()->repo.lookupCash(REFERENCE));
        wire.review=cashRow("paid",2).put("confirmed_at",JSONObject.NULL); expectCode("VALIDATION_FAILED",()->repo.lookupCash(REFERENCE));
        wire.review=cashRow("pending",1).put("confirmed_at",TIME); expectCode("VALIDATION_FAILED",()->repo.lookupCash(REFERENCE));
        wire.review=cashRow("paid",2).put("confirmed_at","tomorrow"); expectCode("VALIDATION_FAILED",()->repo.lookupCash(REFERENCE));
    }

    @Test public void allTerminalStatesRemainVisibleButCannotProduceAConfirmation() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        for(String state:List.of("paid","failed","expired","cancelled")) {
            wire.review=cashRow(state,2); PaymentData.CashReview result=repo.lookupCash(REFERENCE);
            assertNotNull(result); assertEquals(state,result.status.wireValue);
            expectCode("INVALID_STATE",()->repo.confirmation(result,null));
        }
        assertEquals(4,wire.calls.size());
    }

    @Test public void confirmationRetainsReviewedInputAndKeyBeforeAnyIo() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        PaymentData.CashConfirmation first=repo.confirmation(review("pending",1),null);
        assertNotNull("Explicit confirmation input is missing",first); assertNotNull(java.util.UUID.fromString(first.operationKey));
        PaymentData.CashConfirmation retry=repo.confirmation(review("pending",1),first);
        assertEquals(first.operationKey,retry.operationKey); assertEquals(PAYMENT,retry.review.paymentId);
        assertTrue(wire.calls.isEmpty()); assertTrue(wire.paths.isEmpty());
        PaymentData.CashConfirmation changed=repo.confirmation(review("pending",2),first);
        assertNotEquals(first.operationKey,changed.operationKey);
        PaymentData.CashReview altered=new PaymentData.CashReview(ACTOR,PAYMENT,REQUEST,REFERENCE,"OFF-REG","Approved service",
                new BigDecimal("124.00"),"PHP",PaymentStatus.PENDING,1,null);
        assertNotEquals(first.operationKey,repo.confirmation(altered,first).operationKey);
    }

    @Test public void invalidOrOldAccountConfirmationFailsBeforeIo() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        expectCode("VALIDATION_FAILED",()->repo.confirmation(null,null));
        expectCode("VALIDATION_FAILED",()->repo.confirmCash(null));
        expectCode("VALIDATION_FAILED",()->repo.confirmCash(new PaymentData.CashConfirmation(review("pending",1),"bad-key")));
        PaymentData.CashReview other=new PaymentData.CashReview(OTHER,PAYMENT,REQUEST,REFERENCE,"OFF-REG","Approved service",
                new BigDecimal("123.45"),"PHP",PaymentStatus.PENDING,1,null);
        expectCode("FORBIDDEN",()->repo.confirmation(other,null));
        expectCode("FORBIDDEN",()->repo.confirmation(review("pending",1),new PaymentData.CashConfirmation(other,id(9))));
        assertTrue(wire.calls.isEmpty());
    }

    @Test public void explicitConfirmationUsesHeldVersionKeyAndRefetchesCanonicalCash() throws Exception {
        Fake wire=new Fake(); wire.review=cashRow("paid",3); PaymentRepository repo=new PaymentRepository(wire);
        PaymentData.CashConfirmation input=repo.confirmation(review("pending",1),null);
        PaymentData.CashReview result=repo.confirmCash(input);
        assertNotNull("Cash confirmation is missing",result); assertEquals(PaymentStatus.PAID,result.status); assertEquals(3,result.version);
        assertEquals(2,wire.calls.size()); Call confirm=wire.calls.get(0), refresh=wire.calls.get(1);
        assertEquals(Set.of("p_reference","p_confirm","p_expected_version","p_idempotency_key"),keys(confirm.args));
        assertTrue(confirm.args.getBoolean("p_confirm")); assertEquals(1,confirm.args.getLong("p_expected_version"));
        assertEquals(input.operationKey,confirm.args.getString("p_idempotency_key")); assertEquals(REFERENCE,confirm.args.getString("p_reference"));
        assertFalse(refresh.args.getBoolean("p_confirm")); assertTrue(refresh.args.isNull("p_idempotency_key"));
        assertTrue(wire.paths.isEmpty()); for(Call call:wire.calls)assertEquals("cash_payment",call.name);
    }

    @Test public void lostConfirmationResponseCanRetryTheSameInputAndRefreshReplay() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        PaymentData.CashConfirmation input=repo.confirmation(review("pending",1),null); assertNotNull(input);
        wire.rpcFailure=new java.io.IOException("response lost");
        try { repo.confirmCash(input); fail("Transport failure must be retained"); } catch(java.io.IOException expected) {}
        String firstKey=wire.calls.get(0).args.getString("p_idempotency_key");
        wire.rpcFailure=null; wire.review=cashRow("paid",2);
        PaymentData.CashReview result=repo.confirmCash(input); assertNotNull(result); assertEquals(PaymentStatus.PAID,result.status);
        assertEquals(firstKey,wire.calls.get(1).args.getString("p_idempotency_key")); assertEquals(3,wire.calls.size());
    }

    @Test public void confirmationRejectsChangedIdentityOrMoneyBeforeCanonicalRead() throws Exception {
        for(String field:List.of("payment_id","request_id","reference","office_id","service_name","amount","currency")) {
            Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
            wire.confirmed.put(field,field.endsWith("_id") && !field.equals("office_id")?OTHER:
                    field.equals("office_id")?"OFF-ACC":field.equals("amount")?"999":"changed");
            PaymentData.CashConfirmation input=repo.confirmation(review("pending",1),null);
            expectCode("VALIDATION_FAILED",()->repo.confirmCash(input)); assertEquals(1,wire.calls.size());
        }
    }

    @Test public void confirmationRejectsUnpaidOrNonAdvancingReceiptAndStaleCanonicalRow() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        PaymentData.CashConfirmation input=repo.confirmation(review("pending",1),null); assertNotNull(input);
        wire.confirmed=cashRow("pending",1); expectCode("VALIDATION_FAILED",()->repo.confirmCash(input));
        wire.confirmed=cashRow("paid",1); expectCode("VALIDATION_FAILED",()->repo.confirmCash(input));
        wire.confirmed=cashRow("paid",3); wire.review=cashRow("paid",2); expectCode("STALE_RESPONSE",()->repo.confirmCash(input));
        wire.review=cashRow("pending",3); expectCode("STALE_RESPONSE",()->repo.confirmCash(input));
    }

    @Test public void serverDomainCodesRemainSafeAndNoAutomaticRetryOccurs() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        for(String code:List.of("FORBIDDEN","NOT_FOUND","ALREADY_PAID","STALE_VERSION","QUOTE_STALE","INVALID_STATE","CONFIGURATION_REQUIRED")) {
            wire.rpcFailure=new SupabaseClient.ApiException(409,code,"private SQL details");
            expectCode(code,()->repo.lookupCash(REFERENCE));
        }
        assertEquals(7,wire.calls.size()); assertTrue(wire.paths.isEmpty());
    }

    @Test public void perRequestReadUsesOnlyActualColumnsAndPreservesDecimalMoney() throws Exception {
        Fake wire=new Fake(); wire.rows.put(paymentRow().put("amount","99999999999999999999.00000001"));
        PaymentData.Record result=new PaymentRepository(wire).payment(REQUEST);
        assertNotNull("Canonical payment reader is missing",result); assertEquals(PAYMENT,result.id); assertEquals(ACTOR,result.accountId);
        assertEquals(new BigDecimal("99999999999999999999.00000001"),result.amount); assertEquals(PaymentMethod.CASH,result.method);
        Uri query=Uri.parse(wire.paths.get(0)); assertEquals("/rest/v1/payments",query.getPath());
        assertEquals(Set.of("select","request_id","limit"),query.getQueryParameterNames());
        assertEquals(COLUMNS,query.getQueryParameter("select")); assertEquals("eq."+REQUEST,query.getQueryParameter("request_id"));
        assertEquals(List.of("2"),query.getQueryParameters("limit")); assertTrue(wire.calls.isEmpty());
    }

    @Test public void canonicalAbsenceIsDistinctFromMalformedOrWrongParentRows() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        assertNull(repo.payment(REQUEST)); assertEquals(1,wire.paths.size());
        wire.rows=new JSONArray().put(paymentRow()).put(paymentRow()); expectCode("VALIDATION_FAILED",()->repo.payment(REQUEST));
        wire.rows=new JSONArray().put(paymentRow().put("request_id",OTHER)); expectCode("FORBIDDEN",()->repo.payment(REQUEST));
        wire.rows=null; expectCode("VALIDATION_FAILED",()->repo.payment(REQUEST));
        wire.rows=new JSONArray().put("bad-row"); expectCode("VALIDATION_FAILED",()->repo.payment(REQUEST));
    }

    @Test public void canonicalMetadataRejectsMalformedMethodStatusAndConfirmation() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        for(String field:List.of("id","request_id","method","status","amount","currency","version","confirmed_by","confirmed_at")) {
            JSONObject row=paymentRow(); row.remove(field); wire.rows=new JSONArray().put(row);
            expectCode("VALIDATION_FAILED",()->repo.payment(REQUEST));
        }
        for(JSONObject row:List.of(paymentRow().put("method","other"),paymentRow().put("status","other"),
                paymentRow().put("confirmed_by",OTHER),paymentRow().put("status","paid").put("confirmed_at",TIME),
                paymentRow().put("amount",1.5d),paymentRow().put("currency"," "))) {
            wire.rows=new JSONArray().put(row); expectCode("VALIDATION_FAILED",()->repo.payment(REQUEST));
        }
        wire.rows=new JSONArray().put(paymentRow().put("status","paid").put("confirmed_at",TIME).put("confirmed_by",ACTOR));
        assertEquals(PaymentStatus.PAID,repo.payment(REQUEST).status);
    }

    @Test public void cashHistoryIsBoundedRlsScopedAndDoesNotEnrichFromHiddenRequests() throws Exception {
        Fake wire=new Fake(); wire.rows.put(paymentRow()); PaymentData.Page page=new PaymentRepository(wire).cashPayments(null);
        assertNotNull("Cash history is missing",page); assertEquals(1,page.items.size()); assertNull(page.nextId);
        Uri query=Uri.parse(wire.paths.get(0)); assertEquals("/rest/v1/payments",query.getPath());
        assertEquals(Set.of("select","limit","order","method"),query.getQueryParameterNames());
        assertEquals(COLUMNS,query.getQueryParameter("select")); assertEquals("eq.cash",query.getQueryParameter("method"));
        assertEquals("id.asc",query.getQueryParameter("order")); assertEquals("50",query.getQueryParameter("limit"));
        try { page.items.clear(); fail("Published page must be immutable"); } catch(UnsupportedOperationException expected) {}
        assertTrue(wire.calls.isEmpty());
    }

    @Test public void fullHistoryPageHasStrictCursorAndCanonicalRowsOnly() throws Exception {
        Fake wire=new Fake(); for(int n=10;n<60;n++)wire.rows.put(paymentRow().put("id",id(n)).put("request_id",id(n+100)));
        PaymentRepository repo=new PaymentRepository(wire); PaymentData.Page page=repo.cashPayments(null);
        assertNotNull(page); assertEquals(50,page.items.size()); assertEquals(id(59),page.nextId);
        wire.rows=new JSONArray(); assertTrue(repo.cashPayments(page.nextId).items.isEmpty());
        assertEquals("gt."+id(59),Uri.parse(wire.paths.get(1)).getQueryParameter("id"));
        wire.rows=new JSONArray().put(paymentRow().put("id",id(59))); expectCode("STALE_RESPONSE",()->repo.cashPayments(id(59)));
        wire.rows=new JSONArray().put(paymentRow().put("method","egovpay")); expectCode("FORBIDDEN",()->repo.cashPayments(null));
        wire.rows=new JSONArray(); for(int n=0;n<51;n++)wire.rows.put(paymentRow());
        expectCode("VALIDATION_FAILED",()->repo.cashPayments(null));
    }

    @Test public void invalidRequestAndCursorCannotReachTransport() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        expectCode("VALIDATION_FAILED",()->repo.payment(null)); expectCode("VALIDATION_FAILED",()->repo.payment("request"));
        expectCode("VALIDATION_FAILED",()->repo.cashPayments("bad-cursor")); assertTrue(wire.paths.isEmpty());
    }

    @Test public void oldAccountResponseAndChangedActorAreRejected() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire); wire.afterRpc=()->wire.current=false;
        expectStaleAccount(()->repo.lookupCash(REFERENCE));
        wire.current=true; wire.afterGet=()->wire.actor=OTHER; expectStaleAccount(()->repo.payment(REQUEST));
    }

    @Test public void newerLookupAndInvalidationRejectOlderReads() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire); wire.afterRpc=()->assertNotNull(repo.lookupCash(REFERENCE));
        expectCode("STALE_RESPONSE",()->repo.lookupCash(REFERENCE));
        wire.afterGet=repo::invalidate; expectCode("STALE_RESPONSE",()->repo.payment(REQUEST));
        wire.afterGet=()->assertNotNull(repo.cashPayments(null)); expectCode("STALE_RESPONSE",()->repo.cashPayments(null));
    }

    @Test public void independentReadResourcesDoNotInvalidateEachOther() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire); wire.rows.put(paymentRow());
        wire.afterRpc=()->{ assertNotNull(repo.payment(REQUEST)); assertNotNull(repo.cashPayments(null)); };
        assertNotNull(repo.lookupCash(REFERENCE));
        wire.afterGet=()->assertNotNull(repo.lookupCash(REFERENCE)); assertNotNull(repo.payment(REQUEST));
    }

    @Test public void invalidatedConfirmationCannotIssueAFollowupReadOrPublish() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        PaymentData.CashConfirmation input=repo.confirmation(review("pending",1),null); assertNotNull(input);
        wire.afterRpc=repo::invalidate; expectCode("STALE_RESPONSE",()->repo.confirmCash(input)); assertEquals(1,wire.calls.size());
        wire.review=cashRow("paid",2); wire.afterRpc=()->wire.afterRpc=repo::invalidate;
        expectCode("STALE_RESPONSE",()->repo.confirmCash(input)); assertEquals(3,wire.calls.size());
    }

    @Test public void checkoutInputRetainsKeyButOnlyForCurrentPendingEgovPay() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        PaymentData.Checkout first=repo.checkout(egovPayment(PaymentStatus.PENDING,1),null); assertNotNull(first);
        assertEquals(first.operationKey,repo.checkout(egovPayment(PaymentStatus.PENDING,1),first).operationKey);
        assertNotEquals(first.operationKey,repo.checkout(egovPayment(PaymentStatus.PENDING,2),first).operationKey);
        expectCode("INVALID_STATE",()->repo.checkout(egovPayment(PaymentStatus.PAID,2),null));
        expectCode("INVALID_STATE",()->repo.checkout(new PaymentData.Record(ACTOR,PAYMENT,REQUEST,PaymentMethod.CASH,PaymentStatus.PENDING,
                new BigDecimal("123.45"),"PHP",1,null,null),null)); assertTrue(wire.calls.isEmpty());
    }

    @Test public void egovPayUsesOnlyApprovedArgumentsAndPreservesBlockerWithoutInventedSuccess() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        PaymentData.Checkout input=repo.checkout(egovPayment(PaymentStatus.PENDING,1),null); assertNotNull(input);
        wire.edgeFailure=new SupabaseClient.ApiException(503,"PROVIDER_UNAVAILABLE","private details");
        expectCode("PROVIDER_UNAVAILABLE",()->repo.startEgovPay(input));
        Call call=wire.calls.get(0); assertEquals("start-egovpay",call.name); assertTrue(call.edge);
        assertEquals(Set.of("payment_id","idempotency_key"),keys(call.args)); assertEquals(PAYMENT,call.args.getString("payment_id"));
        assertEquals(input.operationKey,call.args.getString("idempotency_key"));
        wire.edgeFailure=null; wire.edgeReply=new JSONObject().put("status","paid").put("checkout_url","https://unreviewed.invalid");
        expectCode("VALIDATION_FAILED",()->repo.startEgovPay(input)); assertTrue(wire.paths.isEmpty());
        assertEquals(input.operationKey,wire.calls.get(1).args.getString("idempotency_key"));
    }

    @Test public void approvedTestCheckoutSucceedsWithoutInventingAPaidPayment() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        wire.edgeReply=new JSONObject().put("checkout_url","https://egovpay-pgi-dev.oueg.info/"+PAYMENT);
        repo.startEgovPay(repo.checkout(egovPayment(PaymentStatus.PENDING,1),null));
        assertEquals(1,wire.calls.size()); assertTrue(wire.paths.isEmpty());
    }

    @Test public void checkoutRejectsEveryUnapprovedUrlShape() throws Exception {
        for(String url:List.of("http://egovpay-pgi-dev.oueg.info/"+PAYMENT,
                "https://egovpay-pgi-dev.oueg.info.evil.test/"+PAYMENT,
                "https://evil.test@egovpay-pgi-dev.oueg.info/"+PAYMENT,
                "https://egovpay-pgi-dev.oueg.info:443/"+PAYMENT,
                "https://egovpay-pgi-dev.oueg.info/"+PAYMENT+"?token=private",
                "https://egovpay-pgi-dev.oueg.info/"+PAYMENT+"#paid",
                "https://egovpay-pgi-dev.oueg.info/%2f"+PAYMENT,
                "https://egovpay-pgi-dev.oueg.info/"+PAYMENT+"/",
                "https://egovpay-pgi-dev.oueg.info/checkout")) {
            Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
            wire.edgeReply=new JSONObject().put("checkout_url",url);
            expectCode("VALIDATION_FAILED",()->repo.startEgovPay(repo.checkout(egovPayment(PaymentStatus.PENDING,1),null)));
        }
    }

    @Test public void refreshUsesExactTargetAndOnlyApprovedStatusesWithoutClaimingPaid() throws Exception {
        for(String status:List.of("pending","paid","unknown","failed","cancelled")) {
            Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
            wire.edgeReply=new JSONObject().put("status",status);
            assertEquals(status,repo.refreshEgovPay(egovPayment(PaymentStatus.PENDING,1)));
            assertEquals("refresh-egovpay",wire.calls.get(0).name); assertEquals(Set.of("payment_id"),keys(wire.calls.get(0).args));
            assertEquals(PAYMENT,wire.calls.get(0).args.getString("payment_id")); assertTrue(wire.paths.isEmpty());
        }
    }

    @Test public void refreshRejectsCorruptProjectionAndAccountSwitch() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        for(JSONObject response:List.of(new JSONObject(),new JSONObject().put("status","PAID"),new JSONObject().put("status",true),
                new JSONObject().put("status","paid").put("private","extra"))) {
            wire.edgeReply=response; expectCode("VALIDATION_FAILED",()->repo.refreshEgovPay(egovPayment(PaymentStatus.PENDING,1)));
        }
        wire.edgeReply=new JSONObject().put("status","paid"); wire.afterEdge=()->wire.current=false;
        expectStaleAccount(()->repo.refreshEgovPay(egovPayment(PaymentStatus.PENDING,1)));
    }

    @Test public void invalidatedProviderResponsesCannotPublishUrlOrStatus() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        wire.edgeReply=new JSONObject().put("checkout_url","https://egovpay-pgi-dev.oueg.info/"+PAYMENT); wire.afterEdge=repo::invalidate;
        expectCode("STALE_RESPONSE",()->repo.startEgovPay(repo.checkout(egovPayment(PaymentStatus.PENDING,1),null)));
        wire.edgeReply=new JSONObject().put("status","paid"); wire.afterEdge=repo::invalidate;
        expectCode("STALE_RESPONSE",()->repo.refreshEgovPay(egovPayment(PaymentStatus.PENDING,1)));
    }

    @Test public void unresolvedCheckoutErrorRetainsSafeDomainCodeWithoutProviderDetails() throws Exception {
        SupabaseClient.ApiException error=SupabaseClient.apiError(409,"{\"error\":{\"code\":\"CHECKOUT_UNCONFIRMED\",\"message\":\"private provider detail\"}}");
        assertEquals("CHECKOUT_UNCONFIRMED",error.code); assertFalse(error.getMessage().contains("private"));
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire); wire.edgeFailure=error;
        expectCode("CHECKOUT_UNCONFIRMED",()->repo.startEgovPay(repo.checkout(egovPayment(PaymentStatus.PENDING,1),null)));
    }

    @Test public void oldOrMalformedCheckoutNeverStartsProviderWork() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        expectCode("VALIDATION_FAILED",()->repo.startEgovPay(null));
        expectCode("VALIDATION_FAILED",()->repo.startEgovPay(new PaymentData.Checkout(egovPayment(PaymentStatus.PENDING,1),"bad")));
        wire.current=false; expectStaleAccount(()->repo.startEgovPay(new PaymentData.Checkout(egovPayment(PaymentStatus.PENDING,1),id(9))));
        assertTrue(wire.calls.isEmpty());
    }

    @Test public void providerResponseFromReplacedAccountIsRejectedBeforeAvailabilityResult() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        PaymentData.Checkout input=repo.checkout(egovPayment(PaymentStatus.PENDING,1),null); assertNotNull(input);
        wire.afterEdge=()->wire.current=false; expectStaleAccount(()->repo.startEgovPay(input));
    }

    private static PaymentData.Record egovPayment(PaymentStatus status,long version) {
        return new PaymentData.Record(ACTOR,PAYMENT,REQUEST,PaymentMethod.EGOVPAY,status,new BigDecimal("123.45"),"PHP",version,null,status==PaymentStatus.PAID?TIME:null);
    }
    private static PaymentData.CashReview review(String status,long version) {
        return new PaymentData.CashReview(ACTOR,PAYMENT,REQUEST,REFERENCE,"OFF-REG","Approved service",new BigDecimal("123.45"),"PHP",
                PaymentStatus.fromWire(status),version,"paid".equals(status)?TIME:null);
    }
    private static JSONObject cashRow(String status,long version) throws Exception {
        return new JSONObject().put("payment_id",PAYMENT).put("request_id",REQUEST).put("reference",REFERENCE).put("office_id","OFF-REG")
                .put("service_name","Approved service").put("amount","123.45").put("currency","PHP").put("status",status).put("version",version)
                .put("confirmed_at","paid".equals(status)?TIME:JSONObject.NULL);
    }
    private static JSONObject paymentRow() throws Exception {
        return new JSONObject().put("id",PAYMENT).put("request_id",REQUEST).put("method","cash").put("status","pending").put("amount","123.45")
                .put("currency","PHP").put("version",1).put("confirmed_by",JSONObject.NULL).put("confirmed_at",JSONObject.NULL);
    }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static Set<String> keys(JSONObject value) {
        Set<String> result=new java.util.HashSet<>(); value.keys().forEachRemaining(result::add); return result;
    }
    private interface Action { void run() throws Exception; }
    private static void expectCode(String code,Action action) throws Exception {
        try { action.run(); fail("Expected payment rejection "+code); }
        catch(PaymentRepository.WorkflowException error) { assertEquals(code,error.code); assertEquals(code,error.getMessage()); }
    }
    private static void expectStaleAccount(Action action) throws Exception {
        try { action.run(); fail("Old account operation must be rejected"); }
        catch(IllegalStateException expected) { assertEquals("STALE_ACCOUNT",expected.getMessage()); }
    }
    private static final class Call {
        final String name; final JSONObject args; final boolean edge;
        Call(String name,JSONObject args,boolean edge) throws Exception { this.name=name; this.args=new JSONObject(args.toString()); this.edge=edge; }
    }
    private static final class Fake implements PaymentRepository.Transport {
        String actor=ACTOR; boolean current=true; JSONArray rows=new JSONArray();
        JSONObject review=cashRow("pending",1), confirmed=cashRow("paid",2), edgeReply=new JSONObject();
        Exception rpcFailure,edgeFailure; Action afterRpc,afterGet,afterEdge;
        final List<Call> calls=new ArrayList<>(); final List<String> paths=new ArrayList<>();
        Fake() throws Exception {}
        public String accountId() { return actor; }
        public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public JSONArray get(String path) throws Exception {
            paths.add(path); JSONArray result=rows; Action action=afterGet; afterGet=null; if(action!=null)action.run(); return result;
        }
        public JSONObject rpc(String name,JSONObject args) throws Exception {
            calls.add(new Call(name,args,false)); if(rpcFailure!=null)throw rpcFailure;
            JSONObject result=new JSONObject((args.getBoolean("p_confirm")?confirmed:review).toString());
            Action action=afterRpc; afterRpc=null; if(action!=null)action.run(); return result;
        }
        public JSONObject edge(String name,JSONObject args) throws Exception {
            calls.add(new Call(name,args,true)); if(edgeFailure!=null)throw edgeFailure;
            Action action=afterEdge; afterEdge=null; if(action!=null)action.run(); return edgeReply;
        }
    }
}
