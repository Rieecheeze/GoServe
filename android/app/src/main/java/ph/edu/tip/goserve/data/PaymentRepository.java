package ph.edu.tip.goserve.data;

import android.net.Uri;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import ph.edu.tip.goserve.domain.PaymentData;
import ph.edu.tip.goserve.domain.PaymentMethod;
import ph.edu.tip.goserve.domain.PaymentStatus;

public final class PaymentRepository {
    interface Transport {
        String accountId();
        void requireCurrent();
        JSONArray get(String path) throws Exception;
        JSONObject rpc(String name,JSONObject args) throws Exception;
        JSONObject edge(String name,JSONObject args) throws Exception;
    }
    public static final class WorkflowException extends Exception {
        public final String code;
        public WorkflowException(String code) { super(code); this.code=code; }
    }
    private static final String COLUMNS="id,request_id,method,status,amount,currency,version,confirmed_by,confirmed_at";
    private final Transport transport;
    private final String accountId;
    private final AtomicLong cashLoads=new AtomicLong(), cashDetailLoads=new AtomicLong(), paymentLoads=new AtomicLong(), historyLoads=new AtomicLong();
    private final AtomicLong confirmations=new AtomicLong(), checkouts=new AtomicLong(), providerRefreshes=new AtomicLong();
    public PaymentRepository(AccountGateway gateway) {
        this(new Transport() {
            public String accountId() { return gateway.lease().accountId; }
            public void requireCurrent() { gateway.requireCurrent(); }
            public JSONArray get(String path) throws Exception { return gateway.get(path); }
            public JSONObject rpc(String name,JSONObject args) throws Exception { return gateway.rpc(name,args); }
            public JSONObject edge(String name,JSONObject args) throws Exception { return gateway.edge(name,args); }
        });
    }
    PaymentRepository(Transport transport) { this.transport=transport; this.accountId=transport.accountId(); }
    public void requireCurrent() {
        transport.requireCurrent();
        if(!Objects.equals(accountId,transport.accountId()))throw new IllegalStateException("STALE_ACCOUNT");
    }
    public void invalidate() { invalidateReads(); confirmations.incrementAndGet(); checkouts.incrementAndGet(); providerRefreshes.incrementAndGet(); }
    private void invalidateReads() { cashLoads.incrementAndGet(); cashDetailLoads.incrementAndGet(); paymentLoads.incrementAndGet(); historyLoads.incrementAndGet(); }

    public PaymentData.CashReview lookupCash(String reference) throws Exception {
        requireCurrent(); text(reference); long load=cashLoads.incrementAndGet();
        PaymentData.CashReview value=readCash(reference); check(cashLoads,load); return value;
    }

    public PaymentData.CashReview lookupCashById(String paymentId) throws Exception {
        requireCurrent(); id(paymentId); long load=cashDetailLoads.incrementAndGet();
        PaymentData.CashReview value=parseCash(call("cash_payment_by_id",new JSONObject().put("p_payment_id",paymentId)));
        if(!paymentId.equals(value.paymentId))throw failure("FORBIDDEN");
        check(cashDetailLoads,load); return value;
    }

    public PaymentData.CashConfirmation confirmation(PaymentData.CashReview review,PaymentData.CashConfirmation previous) throws Exception {
        requireCurrent(); validatePending(review); String key=UUID.randomUUID().toString();
        if(previous!=null) {
            validatePending(previous.review); id(previous.operationKey);
            if(sameCash(review,previous.review) && review.version==previous.review.version)key=previous.operationKey;
        }
        requireCurrent(); return new PaymentData.CashConfirmation(review,key);
    }

    public PaymentData.CashReview confirmCash(PaymentData.CashConfirmation input) throws Exception {
        requireCurrent(); if(input==null)throw failure("VALIDATION_FAILED"); validatePending(input.review); id(input.operationKey);
        long load=confirmations.incrementAndGet(); PaymentData.CashReview held=input.review;
        JSONObject args=cashArgs(held.reference,true,held.version,input.operationKey);
        PaymentData.CashReview receipt=parseCash(call(args)); check(confirmations,load);
        if(!sameCash(held,receipt) || receipt.status!=PaymentStatus.PAID || receipt.version<=held.version)throw failure("VALIDATION_FAILED");
        // Idempotent replay returns the old committed projection, not the current row.
        PaymentData.CashReview current=readCash(held.reference); check(confirmations,load);
        if(!sameCash(held,current))throw failure("VALIDATION_FAILED");
        if(current.version<receipt.version || current.status!=PaymentStatus.PAID)throw failure("STALE_RESPONSE");
        invalidateReads(); return current;
    }

    public PaymentData.Record payment(String requestId) throws Exception {
        requireCurrent(); id(requestId); long load=paymentLoads.incrementAndGet();
        JSONArray rows=read(table(2).appendQueryParameter("request_id","eq."+requestId),2); check(paymentLoads,load);
        if(rows.length()==0)return null;
        if(rows.length()!=1)throw failure("VALIDATION_FAILED");
        PaymentData.Record value=parseRecord(object(rows,0));
        if(!requestId.equals(value.requestId))throw failure("FORBIDDEN");
        check(paymentLoads,load); return value;
    }

    public PaymentData.Page cashPayments(String afterId) throws Exception {
        return cashPayments(null,afterId);
    }
    public PaymentData.Page cashPayments(PaymentStatus status,String afterId) throws Exception {
        requireCurrent(); if(afterId!=null)id(afterId); long load=historyLoads.incrementAndGet();
        Uri.Builder query=table(50).appendQueryParameter("method","eq.cash").appendQueryParameter("order","id.asc");
        if(status!=null)query.appendQueryParameter("status","eq."+status.wireValue);
        if(afterId!=null)query.appendQueryParameter("id","gt."+afterId);
        JSONArray rows=read(query,50); check(historyLoads,load);
        LinkedHashMap<String,PaymentData.Record> items=new LinkedHashMap<>(); String last=afterId;
        for(int n=0;n<rows.length();n++) {
            PaymentData.Record value=parseRecord(object(rows,n));
            if(value.method!=PaymentMethod.CASH)throw failure("FORBIDDEN");
            if(status!=null && value.status!=status)throw failure("FORBIDDEN");
            if(afterId!=null && value.id.compareToIgnoreCase(afterId)<=0 || last!=null && value.id.compareToIgnoreCase(last)<0)throw failure("STALE_RESPONSE");
            PaymentData.Record prior=items.get(value.id);
            if(prior!=null && !samePayment(prior,value))throw failure("VALIDATION_FAILED");
            if(prior==null || value.version>prior.version)items.put(value.id,value);
            last=value.id;
        }
        check(historyLoads,load); return new PaymentData.Page(new ArrayList<>(items.values()),rows.length()==50?last:null);
    }

    public PaymentData.Checkout checkout(PaymentData.Record payment,PaymentData.Checkout previous) throws Exception {
        requireCurrent(); validateCheckout(payment); String key=UUID.randomUUID().toString();
        if(previous!=null) {
            validateCheckout(previous.payment); id(previous.operationKey);
            if(samePayment(payment,previous.payment) && payment.version==previous.payment.version)key=previous.operationKey;
        }
        requireCurrent(); return new PaymentData.Checkout(payment,key);
    }

    public String startEgovPay(PaymentData.Checkout input) throws Exception {
        requireCurrent(); if(input==null)throw failure("VALIDATION_FAILED"); validateCheckout(input.payment); id(input.operationKey);
        long load=checkouts.incrementAndGet();
        JSONObject args=new JSONObject().put("payment_id",input.payment.id).put("idempotency_key",input.operationKey);
        JSONObject result;
        try { result=transport.edge("start-egovpay",args); }
        catch(SupabaseClient.ApiException error) { check(checkouts,load); throw failure(error.code); }
        catch(Exception error) { check(checkouts,load); throw error; }
        check(checkouts,load);
        exact(result,"checkout_url"); String url=validatedCheckoutUrl(string(result,"checkout_url"));
        check(checkouts,load); return url;
    }

    public String refreshEgovPay(PaymentData.Record payment) throws Exception {
        requireCurrent(); validate(payment);
        if(payment.method!=PaymentMethod.EGOVPAY || !"PHP".equals(payment.currency))throw failure("INVALID_STATE");
        long load=providerRefreshes.incrementAndGet(); JSONObject result;
        try { result=transport.edge("refresh-egovpay",new JSONObject().put("payment_id",payment.id)); }
        catch(SupabaseClient.ApiException error) { check(providerRefreshes,load); throw failure(error.code); }
        catch(Exception error) { check(providerRefreshes,load); throw error; }
        check(providerRefreshes,load); exact(result,"status"); String status=string(result,"status");
        if(!java.util.Arrays.asList("pending","paid","unknown","failed","cancelled").contains(status))throw failure("VALIDATION_FAILED");
        check(providerRefreshes,load); return status;
    }

    public static String validatedCheckoutUrl(String value) throws WorkflowException {
        if(value==null || !value.matches("https://egovpay-pgi-dev\\.oueg\\.info/[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))throw failure("VALIDATION_FAILED");
        return value;
    }

    private PaymentData.CashReview readCash(String reference) throws Exception {
        PaymentData.CashReview value=parseCash(call(cashArgs(reference,false,null,null)));
        if(!reference.equals(value.reference))throw failure("FORBIDDEN"); requireCurrent(); return value;
    }
    private static JSONObject cashArgs(String reference,boolean confirm,Long version,String key) throws JSONException {
        return new JSONObject().put("p_reference",reference).put("p_confirm",confirm)
                .put("p_expected_version",version==null?JSONObject.NULL:version).put("p_idempotency_key",key==null?JSONObject.NULL:key);
    }
    private JSONObject call(JSONObject args) throws Exception {
        return call("cash_payment",args);
    }
    private JSONObject call(String name,JSONObject args) throws Exception {
        requireCurrent();
        try { JSONObject row=transport.rpc(name,args); requireCurrent(); if(row==null)throw failure("VALIDATION_FAILED"); return row; }
        catch(SupabaseClient.ApiException error) { requireCurrent(); throw failure(error.code); }
    }
    private JSONArray read(Uri.Builder query,int limit) throws Exception {
        requireCurrent();
        try {
            JSONArray rows=transport.get(query.build().toString()); requireCurrent();
            if(rows==null || rows.length()>limit)throw failure("VALIDATION_FAILED"); return rows;
        } catch(SupabaseClient.ApiException error) { requireCurrent(); throw failure(error.code); }
    }
    private static Uri.Builder table(int limit) {
        return Uri.parse("/rest/v1/payments").buildUpon().appendQueryParameter("select",COLUMNS).appendQueryParameter("limit",Integer.toString(limit));
    }
    private void check(AtomicLong generation,long expected) throws WorkflowException {
        requireCurrent(); if(generation.get()!=expected)throw failure("STALE_RESPONSE");
    }

    private PaymentData.CashReview parseCash(JSONObject row) throws Exception {
        exact(row,"payment_id","request_id","reference","office_id","service_name","amount","currency","status","version","confirmed_at");
        PaymentData.CashReview value=new PaymentData.CashReview(accountId,string(row,"payment_id"),string(row,"request_id"),string(row,"reference"),
                string(row,"office_id"),string(row,"service_name"),money(row),string(row,"currency"),status(row),version(row),nullable(row,"confirmed_at"));
        validate(value); return value;
    }
    private PaymentData.Record parseRecord(JSONObject row) throws Exception {
        exact(row,COLUMNS.split(",")); PaymentMethod method;
        try { method=PaymentMethod.fromWire(string(row,"method")); } catch(IllegalArgumentException error) { throw failure("VALIDATION_FAILED"); }
        PaymentData.Record value=new PaymentData.Record(accountId,string(row,"id"),string(row,"request_id"),method,status(row),money(row),
                string(row,"currency"),version(row),nullable(row,"confirmed_by"),nullable(row,"confirmed_at")); validate(value); return value;
    }
    private void validate(PaymentData.CashReview value) throws Exception {
        if(value==null)throw failure("VALIDATION_FAILED"); account(value.accountId); id(value.paymentId); id(value.requestId);
        text(value.reference); text(value.serviceName); text(value.currency); positive(value.amount);
        if(!java.util.Arrays.asList("OFF-REG","OFF-ACC","OFF-CASH","OFF-GCC","DEP-CCS").contains(value.officeId))throw failure("VALIDATION_FAILED");
        metadata(value.status,value.version,value.confirmedAt);
    }
    private void validatePending(PaymentData.CashReview value) throws Exception {
        validate(value); if(value.status!=PaymentStatus.PENDING)throw failure("INVALID_STATE");
    }
    private void validate(PaymentData.Record value) throws Exception {
        if(value==null)throw failure("VALIDATION_FAILED"); account(value.accountId); id(value.id); id(value.requestId);
        if(value.method==null)throw failure("VALIDATION_FAILED"); positive(value.amount); text(value.currency); metadata(value.status,value.version,value.confirmedAt);
        if(value.confirmedBy!=null)id(value.confirmedBy);
        if(value.status!=PaymentStatus.PAID && value.confirmedBy!=null
                || value.status==PaymentStatus.PAID && value.method==PaymentMethod.CASH && value.confirmedBy==null)throw failure("VALIDATION_FAILED");
    }
    private void validateCheckout(PaymentData.Record value) throws Exception {
        validate(value); if(value.method!=PaymentMethod.EGOVPAY || value.status!=PaymentStatus.PENDING || !"PHP".equals(value.currency))throw failure("INVALID_STATE");
    }
    private static boolean sameCash(PaymentData.CashReview a,PaymentData.CashReview b) {
        return a.accountId.equals(b.accountId) && a.paymentId.equals(b.paymentId) && a.requestId.equals(b.requestId)
                && a.reference.equals(b.reference) && a.officeId.equals(b.officeId) && a.serviceName.equals(b.serviceName)
                && a.amount.compareTo(b.amount)==0 && a.currency.equals(b.currency);
    }
    private static boolean samePayment(PaymentData.Record a,PaymentData.Record b) {
        return a.accountId.equals(b.accountId) && a.id.equals(b.id) && a.requestId.equals(b.requestId)
                && a.method==b.method && a.amount.compareTo(b.amount)==0 && a.currency.equals(b.currency);
    }
    private void account(String actor) throws WorkflowException { id(actor); if(!accountId.equals(actor))throw failure("FORBIDDEN"); }
    private static void metadata(PaymentStatus status,long version,String confirmedAt) throws WorkflowException {
        if(status==null || version<1 || (status==PaymentStatus.PAID)!=(confirmedAt!=null))throw failure("VALIDATION_FAILED");
        if(confirmedAt!=null)try { Instant.parse(confirmedAt); } catch(Exception error) { throw failure("VALIDATION_FAILED"); }
    }
    private static void positive(BigDecimal value) throws WorkflowException { if(value==null || value.signum()<=0)throw failure("VALIDATION_FAILED"); }
    private static BigDecimal money(JSONObject row) throws WorkflowException {
        Object raw=row.opt("amount");
        if(!(raw instanceof String) && !(raw instanceof BigDecimal) && !(raw instanceof java.math.BigInteger)
                && !(raw instanceof Integer) && !(raw instanceof Long) && !(raw instanceof Short) && !(raw instanceof Byte))throw failure("VALIDATION_FAILED");
        try { BigDecimal value=new BigDecimal(raw.toString()); positive(value); return value; }
        catch(NumberFormatException error) { throw failure("VALIDATION_FAILED"); }
    }
    private static PaymentStatus status(JSONObject row) throws WorkflowException {
        try { return PaymentStatus.fromWire(string(row,"status")); } catch(IllegalArgumentException error) { throw failure("VALIDATION_FAILED"); }
    }
    private static long version(JSONObject row) throws WorkflowException {
        Object value=row.opt("version"); if(!(value instanceof Integer) && !(value instanceof Long))throw failure("VALIDATION_FAILED");
        long result=((Number)value).longValue(); if(result<1)throw failure("VALIDATION_FAILED"); return result;
    }
    private static JSONObject object(JSONArray rows,int n) throws WorkflowException {
        try { return rows.getJSONObject(n); } catch(JSONException error) { throw failure("VALIDATION_FAILED"); }
    }
    private static void exact(JSONObject row,String... fields) throws WorkflowException {
        if(row==null || row.length()!=fields.length)throw failure("VALIDATION_FAILED");
        for(String field:fields)if(!row.has(field))throw failure("VALIDATION_FAILED");
    }
    private static String string(JSONObject row,String key) throws WorkflowException {
        Object value=row.opt(key); if(!(value instanceof String))throw failure("VALIDATION_FAILED"); return (String)value;
    }
    private static String nullable(JSONObject row,String key) throws WorkflowException { return row.isNull(key)?null:string(row,key); }
    private static void text(String value) throws WorkflowException { if(value==null || value.isBlank())throw failure("VALIDATION_FAILED"); }
    private static void id(String value) throws WorkflowException {
        if(value==null || !value.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))throw failure("VALIDATION_FAILED");
    }
    private static WorkflowException failure(String code) { return new WorkflowException(code); }
}
