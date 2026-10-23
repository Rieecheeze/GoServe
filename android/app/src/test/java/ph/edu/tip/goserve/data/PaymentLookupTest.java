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
import ph.edu.tip.goserve.domain.PaymentStatus;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public final class PaymentLookupTest {
    private static final String ACTOR=id(1),PAYMENT=id(2),REQUEST=id(3),OTHER=id(4),REFERENCE="canonical-reference",TIME="2026-10-20T01:00:00Z";

    @Test public void byIdLookupUsesExactReadOnlyRpcAndSafeLosslessProjection() throws Exception {
        Fake wire=new Fake(); PaymentData.CashReview value=new PaymentRepository(wire).lookupCashById(PAYMENT);
        assertNotNull("Canonical cash ID reader is missing",value); assertEquals(PAYMENT,value.paymentId); assertEquals(REQUEST,value.requestId);
        assertEquals(ACTOR,value.accountId); assertEquals(REFERENCE,value.reference); assertEquals("OFF-REG",value.officeId);
        assertEquals(new BigDecimal("12345678901234567890.123456789"),value.amount); assertEquals(PaymentStatus.PENDING,value.status);
        assertEquals(List.of("cash_payment_by_id"),wire.names); assertEquals(1,wire.args.get(0).length());
        assertEquals(PAYMENT,wire.args.get(0).getString("p_payment_id")); assertTrue(wire.paths.isEmpty());
    }
    @Test public void invalidIdAndWrongProjectionIdentityFailClosed() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        expect("VALIDATION_FAILED",()->repo.lookupCashById(null)); expect("VALIDATION_FAILED",()->repo.lookupCashById("not-an-id")); assertTrue(wire.names.isEmpty());
        wire.reply=cash().put("payment_id",OTHER); expect("FORBIDDEN",()->repo.lookupCashById(PAYMENT));
    }
    @Test public void missingPrivateOrMalformedProjectionAndServerBlockersAreNotAccepted() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        wire.reply=cash(); wire.reply.remove("confirmed_at"); expect("VALIDATION_FAILED",()->repo.lookupCashById(PAYMENT));
        wire.reply=cash().put("notes","private"); expect("VALIDATION_FAILED",()->repo.lookupCashById(PAYMENT));
        wire.reply=cash().put("amount",1.1d); expect("VALIDATION_FAILED",()->repo.lookupCashById(PAYMENT));
        for(String code:List.of("NOT_FOUND","FORBIDDEN","TERMS_REQUIRED","PROFILE_INCOMPLETE")) {
            wire.error=new SupabaseClient.ApiException(403,code,"private error details"); expect(code,()->repo.lookupCashById(PAYMENT));
        }
    }
    @Test public void replacedAccountAndNewerDetailCannotPublishOldResponses() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        wire.afterRpc=()->wire.current=false;
        try { repo.lookupCashById(PAYMENT); fail("Old account cash result must be discarded"); } catch(IllegalStateException expected) { assertEquals("STALE_ACCOUNT",expected.getMessage()); }
        wire.current=true; wire.afterRpc=repo::invalidate; expect("STALE_RESPONSE",()->repo.lookupCashById(PAYMENT));
        wire.afterRpc=()->assertNotNull(repo.lookupCashById(PAYMENT)); expect("STALE_RESPONSE",()->repo.lookupCashById(PAYMENT));
    }
    @Test public void cashReferenceAndHistoryReadsDoNotSupersedeExactIdDetail() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        wire.afterRpc=()->{ assertNotNull(repo.lookupCash(REFERENCE)); assertNotNull(repo.cashPayments(null)); };
        assertNotNull(repo.lookupCashById(PAYMENT));
        wire.afterRpc=()->assertNotNull(repo.lookupCashById(PAYMENT)); assertNotNull(repo.lookupCash(REFERENCE));
    }
    @Test public void explicitPendingAndPaidPagesUseCanonicalStatusAndStableIdCursor() throws Exception {
        for(PaymentStatus status:List.of(PaymentStatus.PENDING,PaymentStatus.PAID)) {
            Fake wire=new Fake(); wire.rows.put(row(status)); PaymentData.Page page=new PaymentRepository(wire).cashPayments(status,ACTOR);
            assertNotNull("Status-filtered cash page is missing",page); assertEquals(1,page.items.size()); assertEquals(status,page.items.get(0).status); assertNull(page.nextId);
            Uri uri=Uri.parse(wire.paths.get(0)); assertEquals("/rest/v1/payments",uri.getPath());
            assertEquals(Set.of("select","method","status","order","limit","id"),uri.getQueryParameterNames());
            assertEquals("id,request_id,method,status,amount,currency,version,confirmed_by,confirmed_at",uri.getQueryParameter("select"));
            assertEquals("eq.cash",uri.getQueryParameter("method")); assertEquals("eq."+status.wireValue,uri.getQueryParameter("status"));
            assertEquals("id.asc",uri.getQueryParameter("order")); assertEquals(List.of("50"),uri.getQueryParameters("limit")); assertEquals("gt."+ACTOR,uri.getQueryParameter("id"));
            assertTrue(wire.names.isEmpty());
        }
    }
    @Test public void statusMismatchOrInvalidCursorCannotBecomeAFilteredPage() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire);
        expect("VALIDATION_FAILED",()->repo.cashPayments(PaymentStatus.PAID,"bad-cursor")); assertTrue(wire.paths.isEmpty());
        wire.rows.put(row(PaymentStatus.PENDING)); expect("FORBIDDEN",()->repo.cashPayments(PaymentStatus.PAID,null));
        wire.rows=new JSONArray().put(row(PaymentStatus.PAID)); expect("FORBIDDEN",()->repo.cashPayments(PaymentStatus.PENDING,null));
        wire.rows=new JSONArray().put(row(PaymentStatus.EXPIRED)); assertEquals(PaymentStatus.EXPIRED,repo.cashPayments(PaymentStatus.EXPIRED,null).items.get(0).status);
    }
    @Test public void statusPageKeepsExistingLeaseGenerationAndEmptyPageSemantics() throws Exception {
        Fake wire=new Fake(); PaymentRepository repo=new PaymentRepository(wire); PaymentData.Page empty=repo.cashPayments(PaymentStatus.PAID,null);
        assertNotNull(empty); assertTrue(empty.items.isEmpty()); assertNull(empty.nextId);
        wire.afterGet=repo::invalidate; expect("STALE_RESPONSE",()->repo.cashPayments(PaymentStatus.PAID,null));
        wire.afterGet=()->assertNotNull(repo.cashPayments(PaymentStatus.PENDING,null)); expect("STALE_RESPONSE",()->repo.cashPayments(PaymentStatus.PAID,null));
        wire.afterGet=()->wire.current=false;
        try { repo.cashPayments(PaymentStatus.PAID,null); fail("Old account page must be discarded"); } catch(IllegalStateException expected) { assertEquals("STALE_ACCOUNT",expected.getMessage()); }
    }
    private static JSONObject cash() throws Exception {
        return new JSONObject().put("payment_id",PAYMENT).put("request_id",REQUEST).put("reference",REFERENCE).put("office_id","OFF-REG").put("service_name","Actual service")
                .put("amount","12345678901234567890.123456789").put("currency","PHP").put("status","pending").put("version",1).put("confirmed_at",JSONObject.NULL);
    }
    private static JSONObject row(PaymentStatus status) throws Exception {
        boolean paid=status==PaymentStatus.PAID;
        return new JSONObject().put("id",PAYMENT).put("request_id",REQUEST).put("method","cash").put("status",status.wireValue).put("amount","123.45")
                .put("currency","PHP").put("version",paid?2:1).put("confirmed_at",paid?TIME:JSONObject.NULL).put("confirmed_by",paid?ACTOR:JSONObject.NULL);
    }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private interface Action { void run() throws Exception; }
    private static void expect(String code,Action action) throws Exception {
        try { action.run(); fail("Expected payment rejection "+code); } catch(PaymentRepository.WorkflowException expected) { assertEquals(code,expected.code); }
    }
    private static final class Fake implements PaymentRepository.Transport {
        boolean current=true; JSONObject reply=cash(); JSONArray rows=new JSONArray(); Exception error; Action afterRpc,afterGet;
        final List<String> names=new ArrayList<>(),paths=new ArrayList<>(); final List<JSONObject> args=new ArrayList<>();
        Fake() throws Exception {}
        public String accountId() { return ACTOR; } public void requireCurrent() { if(!current)throw new IllegalStateException("STALE_ACCOUNT"); }
        public JSONArray get(String path) throws Exception { paths.add(path); JSONArray result=rows; Action action=afterGet; afterGet=null; if(action!=null)action.run(); return result; }
        public JSONObject rpc(String name,JSONObject value) throws Exception {
            names.add(name); args.add(new JSONObject(value.toString())); if(error!=null)throw error;
            JSONObject result=new JSONObject(reply.toString()); Action action=afterRpc; afterRpc=null; if(action!=null)action.run(); return result;
        }
        public JSONObject edge(String name,JSONObject value) { throw new AssertionError("Cash lookup must not call an Edge/provider endpoint"); }
    }
}
