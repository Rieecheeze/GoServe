package ph.edu.tip.goserve.data;

import android.net.Uri;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.json.JSONArray;
import org.json.JSONObject;
import ph.edu.tip.goserve.domain.InquiryData;

public final class InquiryRepository {
    interface Transport {
        String accountId();
        void requireCurrent();
        JSONArray get(String path) throws Exception;
        Object rpcValue(String name,JSONObject args) throws Exception;
    }
    public static final class WorkflowException extends Exception {
        public final String code;
        public WorkflowException(String code) { super(code); this.code=code; }
    }
    private static final String THREAD_COLUMNS="id,student_id,office_id,assigned_to,version,last_sequence,created_at";
    private static final String MESSAGE_COLUMNS="id,thread_id,sequence,sender_id,body,created_at";
    private static final String READ_COLUMNS="thread_id,profile_id,through_sequence,read_at";
    private static final String NOTICE_COLUMNS="id,recipient_id,target_type,target_id,event_id,created_at,read_at";
    private final Transport transport;
    private final AtomicLong threadLoads=new AtomicLong(),opens=new AtomicLong(),messageLoads=new AtomicLong(),
            noticeLoads=new AtomicLong(),unreadLoads=new AtomicLong(),sendLoads=new AtomicLong();
    private final Object activeLock=new Object();
    private InquiryData.ThreadRecord activeThread;
    private InquiryData.ReadCursor activeRead;
    private InquiryData.MessagePage activePage;
    private InquiryData.NotificationPage noticePage;
    public InquiryRepository(AccountGateway gateway) {
        this(new Transport() {
            @Override public String accountId() { return gateway.lease().accountId; }
            @Override public void requireCurrent() { gateway.requireCurrent(); }
            @Override public JSONArray get(String path) throws Exception { return gateway.get(path); }
            @Override public Object rpcValue(String name,JSONObject args) throws Exception { return gateway.rpcValue(name,args); }
        });
    }
    InquiryRepository(Transport transport) { this.transport=transport; }
    public InquiryData.ThreadPage threads(String officeId,String afterCreatedAt,String afterId) throws Exception {
        requireCurrent(); if(officeId!=null)office(officeId); cursor(afterCreatedAt,afterId);
        long load=threadLoads.incrementAndGet();
        Uri.Builder query=table("inquiry_threads",THREAD_COLUMNS).appendQueryParameter("order","created_at.desc,id.desc");
        if(officeId==null)query.appendQueryParameter("student_id","eq."+transport.accountId());
        else query.appendQueryParameter("office_id","eq."+officeId);
        tuple(query,afterCreatedAt,afterId);
        JSONArray rows=read(query); Map<String,InquiryData.ThreadRecord> items=new LinkedHashMap<>();
        InquiryData.ThreadRecord last=null;
        for(int n=0;n<rows.length();n++) {
            InquiryData.ThreadRecord item=thread(object(rows.opt(n)));
            if(officeId==null?!transport.accountId().equals(item.studentId):!officeId.equals(item.officeId))throw failure("FORBIDDEN");
            descending(item.createdAt,item.id,last==null?afterCreatedAt:last.createdAt,last==null?afterId:last.id);
            InquiryData.ThreadRecord previous=items.get(item.id);
            if(previous==null || item.version>previous.version)items.put(item.id,item);
            last=item;
        }
        check(threadLoads,load);
        return new InquiryData.ThreadPage(new ArrayList<>(items.values()),rows.length()==50?last.createdAt:null,rows.length()==50?last.id:null);
    }

    public InquiryData.ThreadRecord openThread(String threadId) throws Exception {
        requireCurrent(); id(threadId); long generation;
        synchronized(activeLock) {
            generation=opens.incrementAndGet(); messageLoads.incrementAndGet();
            activeThread=null; activeRead=null; activePage=null;
        }
        InquiryData.ThreadRecord current=canonicalThread(threadId);
        JSONArray rows=read(table("inquiry_reads",READ_COLUMNS).appendQueryParameter("thread_id","eq."+threadId)
                .appendQueryParameter("profile_id","eq."+transport.accountId()).appendQueryParameter("order","profile_id.asc"));
        if(rows.length()>1)throw failure("VALIDATION_FAILED");
        InquiryData.ReadCursor baseline=rows.length()==0?null:readCursor(object(rows.opt(0)),threadId);
        if(baseline!=null && baseline.throughSequence>current.lastSequence)throw failure("STALE_RESPONSE");
        synchronized(activeLock) {
            check(opens,generation); activeThread=current; activeRead=baseline;
        }
        return current;
    }

    public long confirmedReadThrough(String threadId) throws Exception {
        requireCurrent(); id(threadId);
        synchronized(activeLock) {
            active(threadId);
            return activeRead==null?0:activeRead.throughSequence;
        }
    }

    public InquiryData.MessagePage messages(String threadId,long afterSequence) throws Exception {
        requireCurrent(); id(threadId); if(afterSequence<0)throw failure("VALIDATION_FAILED");
        long generation,load;
        synchronized(activeLock) {
            active(threadId); generation=opens.get(); load=messageLoads.incrementAndGet(); activePage=null;
        }
        InquiryData.ThreadRecord current=canonicalThread(threadId);
        if(afterSequence>current.lastSequence)throw failure("VALIDATION_FAILED");
        JSONArray rows=read(table("inquiry_messages",MESSAGE_COLUMNS).appendQueryParameter("thread_id","eq."+threadId)
                .appendQueryParameter("sequence","gt."+afterSequence).appendQueryParameter("order","sequence.asc"));
        long pageTail=afterSequence;
        for(int n=0;n<rows.length();n++)pageTail=Math.max(pageTail,integer(field(object(rows.opt(n)),"sequence"),1));
        if(pageTail>current.lastSequence) {
            // A reply can commit between the parent snapshot and this message page.
            check(opens,generation); check(messageLoads,load);
            InquiryData.ThreadRecord refreshed=canonicalThread(threadId);
            if(!current.studentId.equals(refreshed.studentId) || !current.officeId.equals(refreshed.officeId))throw failure("FORBIDDEN");
            if(refreshed.version<current.version || refreshed.lastSequence<current.lastSequence)throw failure("STALE_RESPONSE");
            current=refreshed;
        }
        Map<String,InquiryData.Message> items=new LinkedHashMap<>(); Set<Long> sequences=new HashSet<>();
        long last=afterSequence;
        for(int n=0;n<rows.length();n++) {
            InquiryData.Message item=message(object(rows.opt(n)));
            if(!threadId.equals(item.threadId))throw failure("FORBIDDEN");
            InquiryData.Message previous=items.get(item.id);
            if(previous!=null) {
                if(!sameMessage(previous,item))throw failure("VALIDATION_FAILED");
                continue;
            }
            if(item.sequence<=last || item.sequence>current.lastSequence || !sequences.add(item.sequence))throw failure("VALIDATION_FAILED");
            last=item.sequence; items.put(item.id,item);
        }
        InquiryData.MessagePage page=new InquiryData.MessagePage(threadId,new ArrayList<>(items.values()),rows.length()==50?last:null);
        synchronized(activeLock) {
            check(opens,generation); check(messageLoads,load); active(threadId);
            activeThread=current; activePage=page;
        }
        return page;
    }

    public InquiryData.SendDraft draft(String threadId,String officeId,String body,InquiryData.SendDraft previous) throws Exception {
        requireCurrent(); sendInput(threadId,officeId,body);
        if(threadId!=null)synchronized(activeLock) { active(threadId); }
        String key=UUID.randomUUID().toString();
        if(previous!=null && transport.accountId().equals(previous.accountId) && Objects.equals(threadId,previous.threadId)
                && Objects.equals(officeId,previous.officeId) && body.equals(previous.body)) { id(previous.operationKey); key=previous.operationKey; }
        requireCurrent(); return new InquiryData.SendDraft(transport.accountId(),threadId,officeId,body,key);
    }

    public InquiryData.SendResult send(InquiryData.SendDraft draft) throws Exception {
        requireCurrent(); if(draft==null)throw failure("VALIDATION_FAILED");
        if(!transport.accountId().equals(draft.accountId))throw failure("FORBIDDEN");
        sendInput(draft.threadId,draft.officeId,draft.body); id(draft.operationKey);
        long generation=sendLoads.incrementAndGet();
        if(draft.threadId!=null)canonicalThread(draft.threadId);
        JSONObject args=new JSONObject().put("p_thread_id",nil(draft.threadId)).put("p_office_id",nil(draft.officeId))
                .put("p_body",draft.body).put("p_idempotency_key",draft.operationKey);
        JSONObject envelope=object(call("send_inquiry",args));
        InquiryData.ThreadRecord original=thread(object(field(envelope,"thread")));
        InquiryData.Message sent=message(object(field(envelope,"message")));
        if(!transport.accountId().equals(sent.senderId))throw failure("FORBIDDEN");
        if(!original.id.equals(sent.threadId) || !draft.body.equals(sent.body) || sent.sequence>original.lastSequence
                || draft.threadId!=null && !draft.threadId.equals(original.id)
                || draft.threadId==null && (!draft.officeId.equals(original.officeId) || !transport.accountId().equals(original.studentId)))throw failure("VALIDATION_FAILED");
        // A replay may return its original snapshot; publication uses fresh canonical rows.
        InquiryData.ThreadRecord current=canonicalThread(original.id);
        if(!original.studentId.equals(current.studentId) || !original.officeId.equals(current.officeId))throw failure("FORBIDDEN");
        if(current.version<original.version || current.lastSequence<original.lastSequence)throw failure("STALE_RESPONSE");
        JSONArray rows=read(table("inquiry_messages",MESSAGE_COLUMNS).appendQueryParameter("thread_id","eq."+current.id)
                .appendQueryParameter("id","eq."+sent.id).appendQueryParameter("order","sequence.asc"));
        if(rows.length()!=1)throw failure("NOT_FOUND");
        InquiryData.Message canonical=message(object(rows.opt(0)));
        if(!sameMessage(sent,canonical))throw failure("VALIDATION_FAILED");
        check(sendLoads,generation); return new InquiryData.SendResult(current,canonical);
    }

    /** Caller attests that every new sequence through the boundary was displayed in this active page. */
    public InquiryData.ReadCursor readVisible(InquiryData.MessagePage page,long throughSequence) throws Exception {
        requireCurrent(); long generation,load,baseline;
        synchronized(activeLock) {
            visible(page); generation=opens.get(); load=messageLoads.get();
            baseline=activeRead==null?0:activeRead.throughSequence;
            if(throughSequence<1 || throughSequence>activeThread.lastSequence)throw failure("VALIDATION_FAILED");
            long next=baseline;
            boolean boundaryExists=false;
            for(InquiryData.Message item:page.items) {
                if(item.sequence==throughSequence)boundaryExists=true;
                if(item.sequence>baseline && item.sequence<=throughSequence) {
                    if(item.sequence!=next+1)throw failure("VALIDATION_FAILED");
                    next=item.sequence;
                }
            }
            if(!boundaryExists || throughSequence>baseline && next!=throughSequence)throw failure("VALIDATION_FAILED");
            if(throughSequence<=baseline)return activeRead;
        }
        InquiryData.ReadCursor updated=readCursor(object(call("read_inquiry",new JSONObject().put("p_thread_id",page.threadId)
                .put("p_through_sequence",throughSequence))),page.threadId);
        synchronized(activeLock) {
            check(opens,generation); check(messageLoads,load); visible(page);
            long latest=activeRead==null?0:activeRead.throughSequence;
            if(updated.throughSequence<throughSequence || updated.throughSequence<latest
                    || updated.throughSequence>activeThread.lastSequence)throw failure("STALE_RESPONSE");
            activeRead=updated;
        }
        return updated;
    }

    public InquiryData.NotificationPage notifications(String afterCreatedAt,String afterId) throws Exception {
        requireCurrent(); cursor(afterCreatedAt,afterId); long load=noticeLoads.incrementAndGet();
        synchronized(activeLock) { noticePage=null; }
        Uri.Builder query=table("notifications",NOTICE_COLUMNS).appendQueryParameter("recipient_id","eq."+transport.accountId())
                .appendQueryParameter("order","created_at.desc,id.desc");
        tuple(query,afterCreatedAt,afterId); JSONArray rows=read(query);
        Map<String,InquiryData.Notification> items=new LinkedHashMap<>(); InquiryData.Notification last=null;
        for(int n=0;n<rows.length();n++) {
            JSONObject row=object(rows.opt(n)); String notificationId=text(row,"id"),recipient=text(row,"recipient_id"),type=text(row,"target_type"),
                    target=text(row,"target_id"),event=text(row,"event_id"),created=text(row,"created_at"),readAt=nullable(row,"read_at");
            id(notificationId); id(recipient); id(target); id(event); timestamp(created); if(readAt!=null)timestamp(readAt);
            if(!transport.accountId().equals(recipient))throw failure("FORBIDDEN");
            if(!java.util.Arrays.asList("inquiry","request","appointment").contains(type))throw failure("VALIDATION_FAILED");
            descending(created,notificationId,last==null?afterCreatedAt:last.createdAt,last==null?afterId:last.id);
            InquiryData.Notification item=new InquiryData.Notification(notificationId,recipient,type,target,event,created,readAt);
            if(items.put(notificationId,item)!=null)throw failure("VALIDATION_FAILED");
            last=item;
        }
        InquiryData.NotificationPage page=new InquiryData.NotificationPage(new ArrayList<>(items.values()),rows.length()==50?last.createdAt:null,rows.length()==50?last.id:null);
        synchronized(activeLock) { check(noticeLoads,load); noticePage=page; }
        return page;
    }

    public long acknowledge(InquiryData.NotificationPage page,List<String> ids) throws Exception {
        requireCurrent(); Set<String> selected=ids(ids); if(selected.isEmpty())throw failure("VALIDATION_FAILED");
        long load;
        synchronized(activeLock) {
            if(page==null || page!=noticePage)throw failure("STALE_RESPONSE"); load=noticeLoads.get();
            Set<String> allowed=new HashSet<>(); for(InquiryData.Notification item:page.items)allowed.add(item.id);
            if(!allowed.containsAll(selected))throw failure("FORBIDDEN");
        }
        Object result=call("ack_notifications",new JSONObject().put("p_ids",new JSONArray(ids)));
        long count=integer(result,0); if(count>selected.size())throw failure("VALIDATION_FAILED");
        synchronized(activeLock) { check(noticeLoads,load); if(page!=noticePage)throw failure("STALE_RESPONSE"); }
        return count;
    }

    public InquiryData.UnreadCounts unreadCounts(List<String> threadIds) throws Exception {
        requireCurrent(); Set<String> requested=ids(threadIds); long load=unreadLoads.incrementAndGet();
        JSONObject row=object(call("unread_counts",new JSONObject().put("p_thread_ids",new JSONArray(threadIds))));
        long notifications=integer(field(row,"notifications"),0),messages=integer(field(row,"messages"),0);
        Object raw=field(row,"threads"); if(!(raw instanceof JSONArray))throw failure("VALIDATION_FAILED");
        JSONArray rows=(JSONArray)raw; if(rows.length()>requested.size())throw failure("VALIDATION_FAILED");
        Map<String,Long> counts=new LinkedHashMap<>();
        for(int n=0;n<rows.length();n++) {
            JSONObject item=object(rows.opt(n)); String threadId=text(item,"thread_id"); id(threadId);
            long count=integer(field(item,"unread_count"),0);
            if(!requested.contains(threadId) || counts.containsKey(threadId) || count>messages)throw failure("VALIDATION_FAILED");
            counts.put(threadId,count);
        }
        check(unreadLoads,load); return new InquiryData.UnreadCounts(notifications,messages,counts);
    }

    public void assign(String threadId,String recipientId,long expectedVersion) throws Exception {
        requireCurrent(); throw failure("CONFIGURATION_REQUIRED");
    }
    public void requireCurrent() { transport.requireCurrent(); }

    private InquiryData.ThreadRecord canonicalThread(String threadId) throws Exception {
        JSONArray rows=read(table("inquiry_threads",THREAD_COLUMNS).appendQueryParameter("id","eq."+threadId).appendQueryParameter("order","id.asc"));
        if(rows.length()!=1)throw failure("NOT_FOUND");
        InquiryData.ThreadRecord result=thread(object(rows.opt(0)));
        if(!threadId.equals(result.id))throw failure("FORBIDDEN");
        return result;
    }
    private InquiryData.ThreadRecord thread(JSONObject row) throws Exception {
        String threadId=text(row,"id"),student=text(row,"student_id"),officeId=text(row,"office_id"),
                assigned=nullable(row,"assigned_to"),created=text(row,"created_at");
        id(threadId); id(student); office(officeId); if(assigned!=null)id(assigned); timestamp(created);
        return new InquiryData.ThreadRecord(threadId,student,officeId,assigned,created,integer(field(row,"version"),1),integer(field(row,"last_sequence"),0));
    }
    private InquiryData.Message message(JSONObject row) throws Exception {
        String messageId=text(row,"id"),threadId=text(row,"thread_id"),sender=text(row,"sender_id"),body=text(row,"body"),created=text(row,"created_at");
        id(messageId); id(threadId); id(sender); timestamp(created); if(body.isBlank())throw failure("VALIDATION_FAILED");
        return new InquiryData.Message(messageId,threadId,sender,body,created,integer(field(row,"sequence"),1));
    }
    private InquiryData.ReadCursor readCursor(JSONObject row,String expectedThread) throws Exception {
        String threadId=text(row,"thread_id"),profile=text(row,"profile_id"),readAt=text(row,"read_at");
        id(threadId); id(profile); timestamp(readAt);
        if(!expectedThread.equals(threadId) || !transport.accountId().equals(profile))throw failure("FORBIDDEN");
        return new InquiryData.ReadCursor(threadId,profile,readAt,integer(field(row,"through_sequence"),0));
    }
    private void active(String threadId) throws WorkflowException {
        if(activeThread==null || !activeThread.id.equals(threadId))throw failure("FORBIDDEN");
    }
    private void visible(InquiryData.MessagePage page) throws WorkflowException {
        if(page==null || page!=activePage || activeThread==null || !page.threadId.equals(activeThread.id))throw failure("STALE_RESPONSE");
    }
    private static boolean sameMessage(InquiryData.Message a,InquiryData.Message b) {
        return a.id.equals(b.id) && a.threadId.equals(b.threadId) && a.senderId.equals(b.senderId)
                && a.sequence==b.sequence && a.body.equals(b.body) && Instant.parse(a.createdAt).equals(Instant.parse(b.createdAt));
    }
    private JSONArray read(Uri.Builder query) throws Exception {
        requireCurrent();
        try {
            JSONArray rows;
            try { rows=transport.get(query.build().toString()); } finally { requireCurrent(); }
            if(rows==null || rows.length()>50)throw failure("VALIDATION_FAILED"); return rows;
        } catch(SupabaseClient.ApiException error) { throw failure(error.code); }
    }
    private Object call(String name,JSONObject args) throws Exception {
        requireCurrent();
        try {
            Object value;
            try { value=transport.rpcValue(name,args); } finally { requireCurrent(); }
            if(value==null || value==JSONObject.NULL)throw failure("VALIDATION_FAILED"); return value;
        } catch(SupabaseClient.ApiException error) { throw failure(error.code); }
    }
    private void check(AtomicLong counter,long expected) throws WorkflowException {
        requireCurrent(); if(counter.get()!=expected)throw failure("STALE_RESPONSE");
    }
    private static Uri.Builder table(String name,String columns) {
        return Uri.parse("/rest/v1/"+name).buildUpon().appendQueryParameter("select",columns).appendQueryParameter("limit","50");
    }
    private static void tuple(Uri.Builder query,String time,String id) {
        if(id!=null)query.appendQueryParameter("or","(created_at.lt."+time+",and(created_at.eq."+time+",id.lt."+id+"))");
    }
    private static void cursor(String time,String value) throws WorkflowException {
        if((time==null)!=(value==null))throw failure("VALIDATION_FAILED");
        if(value!=null) { id(value); timestamp(time); }
    }
    private static void descending(String time,String value,String previousTime,String previousId) throws WorkflowException {
        if(previousTime==null)return;
        int order=Instant.parse(time).compareTo(Instant.parse(previousTime));
        if(order>0 || order==0 && value.compareToIgnoreCase(previousId)>=0)throw failure("VALIDATION_FAILED");
    }
    private static void sendInput(String threadId,String officeId,String body) throws WorkflowException {
        if(threadId==null)office(officeId);
        else { id(threadId); if(officeId!=null)throw failure("VALIDATION_FAILED"); }
        if(body==null || body.isBlank())throw failure("VALIDATION_FAILED");
    }
    private static Set<String> ids(List<String> values) throws WorkflowException {
        if(values==null || values.size()>100)throw failure("VALIDATION_FAILED");
        Set<String> result=new HashSet<>();
        for(String value:values) { id(value); if(!result.add(value))throw failure("VALIDATION_FAILED"); }
        return result;
    }
    private static JSONObject object(Object value) throws WorkflowException {
        if(!(value instanceof JSONObject))throw failure("VALIDATION_FAILED"); return (JSONObject)value;
    }
    private static Object field(JSONObject row,String key) throws WorkflowException {
        if(!row.has(key))throw failure("VALIDATION_FAILED"); return row.opt(key);
    }
    private static String text(JSONObject row,String key) throws WorkflowException {
        Object value=field(row,key); if(!(value instanceof String))throw failure("VALIDATION_FAILED"); return (String)value;
    }
    private static String nullable(JSONObject row,String key) throws WorkflowException {
        return field(row,key)==JSONObject.NULL?null:text(row,key);
    }
    private static long integer(Object value,long minimum) throws WorkflowException {
        if(!(value instanceof Integer) && !(value instanceof Long))throw failure("VALIDATION_FAILED");
        long result=((Number)value).longValue(); if(result<minimum)throw failure("VALIDATION_FAILED"); return result;
    }
    private static Object nil(String value) { return value==null?JSONObject.NULL:value; }
    private static void id(String value) throws WorkflowException {
        if(value==null || !value.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))throw failure("VALIDATION_FAILED");
    }
    private static void office(String value) throws WorkflowException {
        if(!java.util.Arrays.asList("OFF-REG","OFF-ACC","OFF-CASH","OFF-GCC","DEP-CCS").contains(value))throw failure("VALIDATION_FAILED");
    }
    private static void timestamp(String value) throws WorkflowException {
        try { Instant.parse(value); } catch(Exception error) { throw failure("VALIDATION_FAILED"); }
    }
    private static WorkflowException failure(String code) { return new WorkflowException(code); }
}
