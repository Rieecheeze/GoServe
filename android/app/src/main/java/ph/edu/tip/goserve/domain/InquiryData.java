package ph.edu.tip.goserve.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InquiryData {
    private InquiryData() {}
    private static <T> List<T> copy(List<T> items) { return Collections.unmodifiableList(new ArrayList<>(items)); }

    public static final class ThreadRecord {
        public final String id,studentId,officeId,assignedTo,createdAt;
        public final long version,lastSequence;
        public ThreadRecord(String id,String studentId,String officeId,String assignedTo,String createdAt,long version,long lastSequence) {
            this.id=id; this.studentId=studentId; this.officeId=officeId; this.assignedTo=assignedTo;
            this.createdAt=createdAt; this.version=version; this.lastSequence=lastSequence;
        }
    }
    public static final class Message {
        public final String id,threadId,senderId,body,createdAt;
        public final long sequence;
        public Message(String id,String threadId,String senderId,String body,String createdAt,long sequence) {
            this.id=id; this.threadId=threadId; this.senderId=senderId; this.body=body; this.createdAt=createdAt; this.sequence=sequence;
        }
    }
    public static final class ReadCursor {
        public final String threadId,profileId,readAt;
        public final long throughSequence;
        public ReadCursor(String threadId,String profileId,String readAt,long throughSequence) {
            this.threadId=threadId; this.profileId=profileId; this.readAt=readAt; this.throughSequence=throughSequence;
        }
    }
    public static final class ThreadPage {
        public final List<ThreadRecord> items;
        public final String nextCreatedAt,nextId;
        public ThreadPage(List<ThreadRecord> items,String nextCreatedAt,String nextId) {
            this.items=copy(items); this.nextCreatedAt=nextCreatedAt; this.nextId=nextId;
        }
    }
    public static final class MessagePage {
        public final List<Message> items;
        public final String threadId;
        public final Long nextSequence;
        public MessagePage(String threadId,List<Message> items,Long nextSequence) {
            this.threadId=threadId; this.items=copy(items); this.nextSequence=nextSequence;
        }
    }
    public static final class SendDraft {
        public final String accountId,threadId,officeId,body,operationKey;
        public SendDraft(String accountId,String threadId,String officeId,String body,String operationKey) {
            this.accountId=accountId; this.threadId=threadId; this.officeId=officeId; this.body=body; this.operationKey=operationKey;
        }
    }
    public static final class SendResult {
        public final ThreadRecord thread;
        public final Message message;
        public SendResult(ThreadRecord thread,Message message) { this.thread=thread; this.message=message; }
    }
    public static final class Notification {
        public final String id,recipientId,targetType,targetId,eventId,createdAt,readAt;
        public Notification(String id,String recipientId,String targetType,String targetId,String eventId,String createdAt,String readAt) {
            this.id=id; this.recipientId=recipientId; this.targetType=targetType; this.targetId=targetId;
            this.eventId=eventId; this.createdAt=createdAt; this.readAt=readAt;
        }
    }
    public static final class NotificationPage {
        public final List<Notification> items;
        public final String nextCreatedAt,nextId;
        public NotificationPage(List<Notification> items,String nextCreatedAt,String nextId) {
            this.items=copy(items); this.nextCreatedAt=nextCreatedAt; this.nextId=nextId;
        }
    }
    public static final class UnreadCounts {
        public final long notifications,messages;
        public final Map<String,Long> threads;
        public UnreadCounts(long notifications,long messages,Map<String,Long> threads) {
            this.notifications=notifications; this.messages=messages;
            this.threads=Collections.unmodifiableMap(new LinkedHashMap<>(threads));
        }
    }
}
