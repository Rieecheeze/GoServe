package ph.edu.tip.goserve.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DeliveryData {
    public static final class PickupClaim {
        public final String id,requestId,displayCode,qrPayload,expiresAt;
        public PickupClaim(String id,String requestId,String displayCode,String qrPayload,String expiresAt) {
            this.id=id; this.requestId=requestId; this.displayCode=displayCode; this.qrPayload=qrPayload; this.expiresAt=expiresAt;
        }
    }
    public static final class PickupReview {
        public final String validationId,actorId,officeId,expiresAt;
        public final RequestData.Record request;
        public PickupReview(String validationId,String actorId,String officeId,String expiresAt,RequestData.Record request) {
            this.validationId=validationId; this.actorId=actorId; this.officeId=officeId; this.expiresAt=expiresAt; this.request=request;
        }
    }
    public static final class Handover {
        public final PickupReview review;
        public final String operationKey;
        public Handover(PickupReview review,String operationKey) { this.review=review; this.operationKey=operationKey; }
    }
    public static final class Receipt {
        public final RequestData.Record request;
        public final String actorId,operationKey;
        public Receipt(RequestData.Record request,String actorId,String operationKey) { this.request=request; this.actorId=actorId; this.operationKey=operationKey; }
    }
    public static final class DownloadTicket {
        public final String id,fileId,expiresAt,sha256;
        public DownloadTicket(String id,String fileId,String expiresAt,String sha256) { this.id=id; this.fileId=fileId; this.expiresAt=expiresAt; this.sha256=sha256; }
    }
    public static final class Acknowledgement {
        public final String downloadId,confirmedAt;
        public Acknowledgement(String downloadId,String confirmedAt) { this.downloadId=downloadId; this.confirmedAt=confirmedAt; }
    }
    public static final class PdfUpload {
        public final RequestData.Record request;
        public final RequestData.RequestOptions options;
        public final String actorId,officeId,contentSha256,operationKey,fileId;
        public final long sizeBytes;
        public PdfUpload(RequestData.Record request,RequestData.RequestOptions options,String actorId,String officeId,long sizeBytes,String contentSha256,String operationKey,String fileId) {
            this.request=request; this.options=options; this.actorId=actorId; this.officeId=officeId; this.sizeBytes=sizeBytes;
            this.contentSha256=contentSha256; this.operationKey=operationKey; this.fileId=fileId;
        }
    }
    private DeliveryData() {}
    public enum FileKind {
        EVIDENCE("evidence"),DELIVERABLE("deliverable");
        public final String wireValue;
        FileKind(String value) { wireValue=value; }
    }
    public static final class File {
        public final String id,ownerId,serviceId,requestId,requirement,mimeType,verification,createdAt,deletedAt;
        public final FileKind kind;
        public final long sizeBytes,version;
        public final boolean current;
        public File(String id,String ownerId,String serviceId,String requestId,FileKind kind,String requirement,String mimeType,
                long sizeBytes,String verification,long version,boolean current,String createdAt,String deletedAt) {
            this.id=id; this.ownerId=ownerId; this.serviceId=serviceId; this.requestId=requestId; this.kind=kind; this.requirement=requirement;
            this.mimeType=mimeType; this.sizeBytes=sizeBytes; this.verification=verification; this.version=version;
            this.current=current; this.createdAt=createdAt; this.deletedAt=deletedAt;
        }
    }
    public static final class FilePage {
        public final List<File> items;
        public final String nextId;
        public FilePage(List<File> items,String nextId) { this.items=Collections.unmodifiableList(new ArrayList<>(items)); this.nextId=nextId; }
    }
}
