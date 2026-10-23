package ph.edu.tip.goserve.domain;

import java.util.Collections;
import java.util.List;

public final class ConsultationData {
    private ConsultationData() {}

    public static final class Slot {
        public final String id, officeId, ownerId, startAt, endAt;
        public final int capacity, reserved;
        public final long version;
        public final boolean active, isDemo;
        public Slot(String id,String officeId,String ownerId,String startAt,String endAt,int capacity,int reserved,boolean active,long version,boolean isDemo) {
            this.id=id; this.officeId=officeId; this.ownerId=ownerId; this.startAt=startAt; this.endAt=endAt;
            this.capacity=capacity; this.reserved=reserved; this.active=active; this.version=version; this.isDemo=isDemo;
        }
        public int remaining() { return capacity-reserved; }
    }
    public static final class Appointment {
        public final String id, studentId, slotId, state, createdAt;
        public final long version;
        public Appointment(String id,String studentId,String slotId,String state,long version,String createdAt) {
            this.id=id; this.studentId=studentId; this.slotId=slotId; this.state=state; this.version=version; this.createdAt=createdAt;
        }
    }
    public static final class Event {
        public final String id, appointmentId, actorId, action, oldSlotId, newSlotId, reason, createdAt;
        public Event(String id,String appointmentId,String actorId,String action,String oldSlotId,String newSlotId,String reason,String createdAt) {
            this.id=id; this.appointmentId=appointmentId; this.actorId=actorId; this.action=action;
            this.oldSlotId=oldSlotId; this.newSlotId=newSlotId; this.reason=reason; this.createdAt=createdAt;
        }
    }
    public static final class SlotPage {
        public final List<Slot> items;
        public final String nextStartAt, nextId;
        public SlotPage(List<Slot> items,String nextStartAt,String nextId) { this.items=Collections.unmodifiableList(new java.util.ArrayList<>(items)); this.nextStartAt=nextStartAt; this.nextId=nextId; }
    }
    public static final class AppointmentPage {
        public final List<Appointment> items;
        public final String nextId;
        public AppointmentPage(List<Appointment> items,String nextId) { this.items=Collections.unmodifiableList(new java.util.ArrayList<>(items)); this.nextId=nextId; }
    }
    public static final class EventPage {
        public final List<Event> items;
        public final String nextCreatedAt, nextId;
        public EventPage(List<Event> items,String nextCreatedAt,String nextId) { this.items=Collections.unmodifiableList(new java.util.ArrayList<>(items)); this.nextCreatedAt=nextCreatedAt; this.nextId=nextId; }
    }
    public static final class Booking {
        public final String accountId, operationKey;
        public final Slot slot;
        public Booking(String accountId,Slot slot,String operationKey) { this.accountId=accountId; this.slot=slot; this.operationKey=operationKey; }
    }
    public static final class Change {
        public final String accountId, reason, operationKey;
        public final Appointment original;
        public final Slot target;
        public Change(String accountId,Appointment original,Slot target,String reason,String operationKey) { this.accountId=accountId; this.original=original; this.target=target; this.reason=reason; this.operationKey=operationKey; }
    }
    public static final class SlotDraft {
        public final String accountId, id, officeId, ownerId, startAt, endAt, operationKey;
        public final long expectedVersion;
        public final int capacity;
        public final boolean active;
        public SlotDraft(String accountId,String id,long expectedVersion,String officeId,String ownerId,String startAt,String endAt,int capacity,boolean active,String operationKey) {
            this.accountId=accountId; this.id=id; this.expectedVersion=expectedVersion; this.officeId=officeId; this.ownerId=ownerId;
            this.startAt=startAt; this.endAt=endAt; this.capacity=capacity; this.active=active; this.operationKey=operationKey;
        }
    }
}
