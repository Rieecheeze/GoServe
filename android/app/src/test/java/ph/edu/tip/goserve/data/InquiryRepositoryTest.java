package ph.edu.tip.goserve.data;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import ph.edu.tip.goserve.domain.InquiryData;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public final class InquiryRepositoryTest {
    private static final String ACCOUNT=id(1), THREAD=id(2), MESSAGE=id(3), OTHER=id(4), NOTICE=id(5);
    private static final String TIME="2026-10-19T01:00:00.000Z";

    @Test public void freshAccountHasEmptyCanonicalLists() throws Exception {
        Fake wire=new Fake(); wire.threads=new JSONArray();
        InquiryRepository repository=new InquiryRepository(wire);
        InquiryData.ThreadPage page=repository.threads(null,null,null);
        assertNotNull("Empty authenticated thread page is missing",page);
        assertTrue(page.items.isEmpty()); assertNull(page.nextId);
        InquiryData.NotificationPage notifications=repository.notifications(null,null);
        assertNotNull("Empty authenticated notifications are missing",notifications);
        assertTrue(notifications.items.isEmpty()); assertEquals(0,wire.calls.size());
    }

    @Test public void threadPagesUseOwnerScopeAndEqualTimestampTuple() throws Exception {
        Fake wire=new Fake(); wire.threads=new JSONArray();
        for(int n=60;n>10;n--)wire.threads.put(thread().put("id",id(n)));
        InquiryRepository repository=new InquiryRepository(wire);
        InquiryData.ThreadPage page=repository.threads(null,null,null);
        assertNotNull("Canonical thread page is missing",page);
        assertEquals(id(11),page.nextId); assertEquals(TIME,page.nextCreatedAt);
        wire.threads=new JSONArray(); repository.threads(null,page.nextCreatedAt,page.nextId);
        String query=decode(wire.paths.get(1));
        assertTrue(query.contains("student_id=eq."+ACCOUNT));
        assertTrue(query.contains("order=created_at.desc,id.desc"));
        assertTrue(query.contains("or=(created_at.lt."+TIME+",and(created_at.eq."+TIME+",id.lt."+id(11)+"))"));
        assertTrue(query.contains("limit=50")); assertFalse(query.contains("select=*"));
    }

    @Test public void staffReadRetainsOfficeFilterAndRejectsLeakedRows() throws Exception {
        Fake wire=new Fake(); wire.threads=new JSONArray().put(thread().put("student_id",OTHER));
        InquiryRepository repository=new InquiryRepository(wire);
        InquiryData.ThreadPage page=repository.threads("OFF-REG",null,null);
        assertNotNull("Office canonical page is missing",page); assertEquals(OTHER,page.items.get(0).studentId);
        assertTrue(decode(wire.paths.get(0)).contains("office_id=eq.OFF-REG"));
        expect("FORBIDDEN",()->repository.threads(null,null,null));
        wire.threads=new JSONArray().put(thread().put("office_id","OFF-GCC"));
        expect("FORBIDDEN",()->repository.threads("OFF-REG",null,null));
    }

    @Test public void unchangedDraftRetainsKeyButChangedDestinationOrTextDoesNot() throws Exception {
        InquiryRepository repository=new InquiryRepository(new Fake());
        InquiryData.SendDraft first=repository.draft(null,"OFF-REG","Question",null);
        assertNotNull("Operation draft is missing",first);
        InquiryData.SendDraft retry=repository.draft(null,"OFF-REG","Question",first);
        assertNotNull(retry); assertEquals(first.operationKey,retry.operationKey);
        assertNotEquals(first.operationKey,repository.draft(null,"OFF-REG","Question ",first).operationKey);
        assertNotEquals(first.operationKey,repository.draft(null,"OFF-GCC","Question",first).operationKey);
        assertEquals("Question",first.body);
    }

    @Test public void newSendUsesExactArgumentsAndReturnsReconciledCanonicalIdentity() throws Exception {
        Fake wire=new Fake(); InquiryRepository repository=new InquiryRepository(wire);
        InquiryData.SendDraft draft=repository.draft(null,"OFF-REG","Question",null); assertNotNull(draft);
        InquiryData.SendResult sent=repository.send(draft); assertNotNull("Confirmed canonical send is missing",sent);
        assertEquals("send_inquiry",wire.calls.get(0)); assertEquals(4,wire.args.length());
        assertTrue(wire.args.isNull("p_thread_id")); assertEquals("OFF-REG",wire.args.getString("p_office_id"));
        assertEquals("Question",wire.args.getString("p_body")); assertEquals(draft.operationKey,wire.args.getString("p_idempotency_key"));
        assertEquals(THREAD,sent.thread.id); assertEquals(MESSAGE,sent.message.id);
        assertEquals(1,sent.message.sequence); assertEquals(ACCOUNT,sent.message.senderId); assertEquals(TIME,sent.message.createdAt);
        assertTrue(wire.paths.stream().anyMatch(p->decode(p).contains("id=eq."+MESSAGE)));
    }

    @Test public void replyUsesNullOfficeAndSameOperationOnReplay() throws Exception {
        Fake wire=new Fake(); InquiryRepository repository=new InquiryRepository(wire); open(repository);
        InquiryData.SendDraft draft=repository.draft(THREAD,null,"Question",null); assertNotNull(draft);
        assertNotNull(repository.send(draft)); assertNotNull(repository.send(draft));
        assertEquals(2,Collections.frequency(wire.calls,"send_inquiry"));
        assertEquals(THREAD,wire.args.getString("p_thread_id")); assertTrue(wire.args.isNull("p_office_id"));
        assertEquals(draft.operationKey,wire.args.getString("p_idempotency_key"));
    }

    @Test public void uncertainSendKeepsDraftAndReconcilesSameKeyBeforeSuccess() throws Exception {
        Fake wire=new Fake(); InquiryRepository repository=new InquiryRepository(wire);
        InquiryData.SendDraft draft=repository.draft(null,"OFF-REG","Question",null); assertNotNull(draft);
        wire.failSend=true;
        try { repository.send(draft); fail("Uncertain send cannot succeed"); } catch(java.io.IOException expected) {}
        assertEquals("Question",draft.body); String key=draft.operationKey;
        wire.failSend=false; InquiryData.SendResult result=repository.send(draft);
        assertNotNull(result); assertEquals(MESSAGE,result.message.id); assertEquals(key,wire.args.getString("p_idempotency_key"));
    }

    @Test public void sendRejectsWrongSenderAndMissingCanonicalMessage() throws Exception {
        Fake wire=new Fake(); InquiryRepository repository=new InquiryRepository(wire);
        InquiryData.SendDraft draft=repository.draft(null,"OFF-REG","Question",null); assertNotNull(draft);
        wire.sentMessage=message(1).put("sender_id",OTHER);
        expect("FORBIDDEN",()->repository.send(draft));
        wire.sentMessage=message(1); wire.messages=new JSONArray();
        expect("NOT_FOUND",()->repository.send(draft)); assertEquals("Question",draft.body);
    }

    @Test public void messagePageUsesImmutableIdentitySequenceAndNeverMarksRead() throws Exception {
        Fake wire=new Fake(); InquiryRepository repository=new InquiryRepository(wire); open(repository);
        wire.messages=new JSONArray().put(message(1)).put(message(2).put("id",id(6)));
        InquiryData.MessagePage page=repository.messages(THREAD,0);
        assertNotNull(page); assertEquals(2,page.items.size()); assertEquals(id(6),page.items.get(1).id);
        String query=decode(wire.paths.get(wire.paths.size()-1));
        assertTrue(query.contains("thread_id=eq."+THREAD)); assertTrue(query.contains("sequence=gt.0"));
        assertTrue(query.contains("order=sequence.asc")); assertTrue(query.contains("limit=50"));
        assertEquals(0,wire.calls.size());
    }

    @Test public void duplicateConflictingMessageIdentityFailsClosed() throws Exception {
        Fake wire=new Fake(); InquiryRepository repository=new InquiryRepository(wire); open(repository);
        wire.messages=new JSONArray().put(message(1)).put(message(2));
        expect("VALIDATION_FAILED",()->repository.messages(THREAD,0));
    }

    @Test public void onlyExplicitContiguousVisibleSequenceAdvancesActorCursor() throws Exception {
        Fake wire=new Fake(); InquiryRepository repository=new InquiryRepository(wire); open(repository);
        wire.messages=new JSONArray().put(message(1)).put(message(2).put("id",id(6)));
        InquiryData.MessagePage page=repository.messages(THREAD,0); assertNotNull(page);
        InquiryData.ReadCursor read=repository.readVisible(page,1); assertNotNull(read);
        assertEquals(1,read.throughSequence); assertEquals(ACCOUNT,read.profileId);
        assertEquals("read_inquiry",wire.calls.get(0)); assertEquals(2,wire.args.length());
        assertEquals(1,wire.args.getLong("p_through_sequence"));
        expect("VALIDATION_FAILED",()->repository.readVisible(page,3));
        assertEquals(1,wire.calls.size());
    }

    @Test public void unseenGapAndDetachedPageCannotClearUnread() throws Exception {
        Fake wire=new Fake(); InquiryRepository repository=new InquiryRepository(wire); open(repository);
        wire.messages=new JSONArray().put(message(2));
        InquiryData.MessagePage page=repository.messages(THREAD,1); assertNotNull(page);
        expect("VALIDATION_FAILED",()->repository.readVisible(page,2));
        repository.openThread(THREAD);
        expect("STALE_RESPONSE",()->repository.readVisible(page,2)); assertTrue(wire.calls.isEmpty());
    }

    @Test public void persistedReadBaselineAllowsNextPageAndRejectsRegressingResponse() throws Exception {
        Fake wire=new Fake(); wire.reads=new JSONArray().put(read(1));
        InquiryRepository repository=new InquiryRepository(wire); open(repository);
        wire.messages=new JSONArray().put(message(2));
        InquiryData.MessagePage page=repository.messages(THREAD,1); assertNotNull(page);
        wire.readReply=read(0); expect("STALE_RESPONSE",()->repository.readVisible(page,2));
        wire.readReply=read(2); assertEquals(2,repository.readVisible(page,2).throughSequence);
    }

    @Test public void unreadTotalsComeFromServerOutsideLoadedPages() throws Exception {
        Fake wire=new Fake(); InquiryRepository repository=new InquiryRepository(wire);
        wire.unread=new JSONObject().put("notifications",17L).put("messages",301L).put("threads",new JSONArray().put(new JSONObject().put("thread_id",THREAD).put("unread_count",7L)));
        InquiryData.UnreadCounts counts=repository.unreadCounts(Collections.singletonList(THREAD)); assertNotNull(counts);
        assertEquals(301,counts.messages); assertEquals(17,counts.notifications); assertEquals(Long.valueOf(7),counts.threads.get(THREAD));
        assertEquals("unread_counts",wire.calls.get(0)); assertEquals(1,wire.args.length());
        assertEquals(THREAD,wire.args.getJSONArray("p_thread_ids").getString(0));
        List<String> tooMany=new ArrayList<>(); for(int n=0;n<101;n++)tooMany.add(id(n));
        expect("VALIDATION_FAILED",()->repository.unreadCounts(tooMany)); assertEquals(1,wire.calls.size());
    }

    @Test public void notificationsKeepActualTargetsAndTupleCursor() throws Exception {
        Fake wire=new Fake(); wire.notices=new JSONArray().put(notice());
        InquiryRepository repository=new InquiryRepository(wire);
        InquiryData.NotificationPage page=repository.notifications(TIME,id(8)); assertNotNull(page);
        InquiryData.Notification value=page.items.get(0); assertEquals(THREAD,value.targetId); assertEquals(MESSAGE,value.eventId);
        assertEquals("inquiry",value.targetType); assertNull(value.readAt);
        String query=decode(wire.paths.get(0)); assertTrue(query.contains("recipient_id=eq."+ACCOUNT));
        assertTrue(query.contains("or=(created_at.lt."+TIME+",and(created_at.eq."+TIME+",id.lt."+id(8)+"))"));
        assertFalse(query.contains("body")); assertTrue(wire.calls.isEmpty());
    }

    @Test public void acknowledgementUsesOnlyExplicitOwnLoadedIdsAndAcceptsScalarZero() throws Exception {
        Fake wire=new Fake(); wire.notices=new JSONArray().put(notice());
        InquiryRepository repository=new InquiryRepository(wire);
        InquiryData.NotificationPage page=repository.notifications(null,null); assertNotNull(page);
        assertEquals(0,repository.acknowledge(page,Collections.singletonList(NOTICE)));
        assertEquals("ack_notifications",wire.calls.get(0)); assertEquals(1,wire.args.length());
        assertEquals(NOTICE,wire.args.getJSONArray("p_ids").getString(0));
        expect("FORBIDDEN",()->repository.acknowledge(page,Collections.singletonList(OTHER)));
        wire.notices=new JSONArray().put(notice().put("recipient_id",OTHER));
        expect("FORBIDDEN",()->repository.notifications(null,null));
    }

    @Test public void unknownWireShapesAndOversizedPagesFailClosed() throws Exception {
        Fake wire=new Fake(); InquiryRepository repository=new InquiryRepository(wire);
        wire.threads=new JSONArray().put(thread().put("version",1.25));
        expect("VALIDATION_FAILED",()->repository.threads(null,null,null));
        wire.threads=new JSONArray(); for(int n=0;n<51;n++)wire.threads.put(thread());
        expect("VALIDATION_FAILED",()->repository.threads(null,null,null));
        wire.unread=new JSONObject().put("notifications",0).put("messages","0").put("threads",new JSONArray());
        expect("VALIDATION_FAILED",()->repository.unreadCounts(Collections.emptyList()));
    }

    @Test public void accountReplacementRejectsResponseAndFurtherTransport() throws Exception {
        Fake wire=new Fake(); InquiryRepository repository=new InquiryRepository(wire);
        wire.invalidateAfterGet=true;
        try { repository.threads(null,null,null); fail("Old-account rows were published"); } catch(IllegalStateException expected) {}
        int reads=wire.paths.size();
        try { repository.notifications(null,null); fail("Old lease remained usable"); } catch(IllegalStateException expected) {}
        assertEquals(reads,wire.paths.size());
    }

    @Test public void overlappingInboxLoadsRejectLateOlderPublication() throws Exception {
        Fake wire=new Fake(); InquiryRepository repository=new InquiryRepository(wire);
        wire.entered=new CountDownLatch(1); wire.release=new CountDownLatch(1);
        AtomicReference<Throwable> error=new AtomicReference<>();
        Thread older=new Thread(()->{try{repository.threads(null,null,null);}catch(Throwable failure){error.set(failure);}});
        older.start();
        try {
            assertTrue("First canonical read did not start",wire.entered.await(3,TimeUnit.SECONDS));
            assertNotNull(repository.threads("OFF-REG",null,null));
        } finally { wire.release.countDown(); older.join(4000); }
        assertFalse("Held worker did not finish",older.isAlive());
        assertTrue("Late older load must reject publication",error.get() instanceof InquiryRepository.WorkflowException);
        assertEquals("STALE_RESPONSE",((InquiryRepository.WorkflowException)error.get()).code);
    }

    @Test public void assignmentStaysGatedWithoutAuthorizedRecipientContract() throws Exception {
        Fake wire=new Fake(); InquiryRepository repository=new InquiryRepository(wire);
        expect("CONFIGURATION_REQUIRED",()->repository.assign(THREAD,OTHER,1)); assertTrue(wire.calls.isEmpty());
    }

    @Test public void concurrentReplyRefreshesParentTailBeforePublishingMessagePage() throws Exception {
        Fake wire=new Fake(); wire.threads=new JSONArray().put(thread().put("version",2L).put("last_sequence",1L));
        InquiryRepository repository=new InquiryRepository(wire); open(repository);
        wire.messages=new JSONArray().put(message(1)).put(message(2).put("id",id(6)).put("sender_id",OTHER));
        wire.advanceTailOnMessageRead=true;
        InquiryData.MessagePage page=repository.messages(THREAD,0);
        assertNotNull("Concurrent canonical reply page is missing",page);
        assertEquals(2,page.items.size()); assertEquals(MESSAGE,page.items.get(0).id);
        assertEquals(id(6),page.items.get(1).id); assertEquals(2,page.items.get(1).sequence);
        assertEquals(OTHER,page.items.get(1).senderId); assertTrue(wire.calls.isEmpty());
        assertTrue("The parent tail must be reconciled after the message response",
                decode(wire.paths.get(wire.paths.size()-1)).contains("/inquiry_threads?"));
        assertEquals(1,repository.readVisible(page,1).throughSequence);
        assertEquals(1,wire.calls.size()); assertEquals(1,wire.args.getLong("p_through_sequence"));
    }

    private static void open(InquiryRepository repository) throws Exception { assertNotNull("Authorized thread is missing",repository.openThread(THREAD)); }
    private interface Work { void run() throws Exception; }
    private static void expect(String code,Work work) throws Exception {
        try { work.run(); fail("Expected "+code); }
        catch(InquiryRepository.WorkflowException error) { assertEquals(code,error.code); }
    }
    private static String id(int n) { return String.format("00000000-0000-4000-8000-%012d",n); }
    private static String decode(String value) { return URLDecoder.decode(value,StandardCharsets.UTF_8); }
    private static JSONObject thread() throws Exception {
        return new JSONObject().put("id",THREAD).put("student_id",ACCOUNT).put("office_id","OFF-REG")
                .put("assigned_to",JSONObject.NULL).put("version",3L).put("last_sequence",2L).put("created_at",TIME);
    }
    private static JSONObject message(long sequence) throws Exception {
        return new JSONObject().put("id",MESSAGE).put("thread_id",THREAD).put("sequence",sequence)
                .put("sender_id",ACCOUNT).put("body","Question").put("created_at",TIME);
    }
    private static JSONObject read(long sequence) throws Exception {
        return new JSONObject().put("thread_id",THREAD).put("profile_id",ACCOUNT).put("through_sequence",sequence).put("read_at",TIME);
    }
    private static JSONObject notice() throws Exception {
        return new JSONObject().put("id",NOTICE).put("recipient_id",ACCOUNT).put("target_type","inquiry")
                .put("target_id",THREAD).put("event_id",MESSAGE).put("created_at",TIME).put("read_at",JSONObject.NULL);
    }
    private static final class Fake implements InquiryRepository.Transport {
        volatile boolean current=true, invalidateAfterGet, failSend, advanceTailOnMessageRead;
        volatile CountDownLatch entered,release;
        JSONArray threads=new JSONArray().put(thread()), messages=new JSONArray().put(message(1)), reads=new JSONArray(), notices=new JSONArray();
        JSONObject sentMessage=message(1), readReply, unread=new JSONObject().put("notifications",0).put("messages",0).put("threads",new JSONArray());
        final List<String> paths=Collections.synchronizedList(new ArrayList<>()),calls=new ArrayList<>();
        JSONObject args;
        Fake() throws Exception {}
        @Override public String accountId() { return ACCOUNT; }
        @Override public void requireCurrent() { if(!current)throw new IllegalStateException("Account changed"); }
        @Override public JSONArray get(String path) throws Exception {
            paths.add(path); JSONArray response=path.contains("/inquiry_threads?")?threads:path.contains("/inquiry_messages?")?messages:path.contains("/inquiry_reads?")?reads:notices;
            JSONArray snapshot=new JSONArray(response.toString());
            if(advanceTailOnMessageRead && path.contains("/inquiry_messages?")) {
                threads=new JSONArray().put(thread()); advanceTailOnMessageRead=false;
            }
            if(entered!=null && entered.getCount()>0) { entered.countDown(); if(!release.await(4,TimeUnit.SECONDS))throw new AssertionError("Held read timed out"); }
            if(invalidateAfterGet)current=false;
            return snapshot;
        }
        @Override public Object rpcValue(String name,JSONObject input) throws Exception {
            calls.add(name); args=new JSONObject(input.toString());
            switch(name) {
                case "send_inquiry": if(failSend)throw new java.io.IOException("Transient transport"); return new JSONObject().put("thread",thread()).put("message",sentMessage);
                case "read_inquiry": return readReply==null?read(input.getLong("p_through_sequence")):readReply;
                case "ack_notifications": return 0;
                case "unread_counts": return unread;
                default: throw new AssertionError("Unexpected operation");
            }
        }
    }
}
