package ph.edu.tip.goserve.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public final class RequestData {
    private RequestData() {}
    private static <T> List<T> copy(List<T> values) { return Collections.unmodifiableList(new ArrayList<>(values)); }

    public static final class Service {
        public final String id, officeId, name, currency;
        public final long version;
        public final FeeDeclaration fee;
        public final BigDecimal amount;
        public final List<String> deliveryModes, requirementIds;
        public final boolean active;
        public Service(String id, String officeId, String name, long version, FeeDeclaration fee,
                       BigDecimal amount, String currency, List<String> deliveryModes,
                       List<String> requirementIds, boolean active) {
            this.id=id; this.officeId=officeId; this.name=name; this.version=version; this.fee=fee;
            this.amount=amount; this.currency=currency; this.deliveryModes=copy(deliveryModes);
            this.requirementIds=copy(requirementIds); this.active=active;
        }
    }

    public static final class Quote {
        public final String id, ownerId, deliveryMode;
        public final Service service;
        public final boolean submittable;
        public Quote(String id, String ownerId, String deliveryMode, Service service, boolean submittable) {
            this.id=id; this.ownerId=ownerId; this.deliveryMode=deliveryMode;
            this.service=service; this.submittable=submittable;
        }
    }

    public static class ActionOptions {
        public final boolean available;
        public final String blocker;
        public ActionOptions(boolean available, String blocker) {
            this.available=available; this.blocker=blocker;
        }
    }

    public static final class FileOptions extends ActionOptions {
        public final List<String> mimeTypes;
        public final Long maxBytes;
        public FileOptions(boolean available, String blocker, List<String> mimeTypes, Long maxBytes) {
            super(available,blocker);
            this.mimeTypes=mimeTypes==null?null:copy(mimeTypes); this.maxBytes=maxBytes;
        }
    }

    public static final class QuoteOptions {
        public final String quoteId;
        public final long serviceVersion;
        public final ActionOptions cash;
        public final FileOptions evidence;
        public final boolean egovpay;
        public QuoteOptions(String quoteId, long serviceVersion, ActionOptions cash, FileOptions evidence) {
            this(quoteId,serviceVersion,cash,evidence,false);
        }
        public QuoteOptions(String quoteId, long serviceVersion, ActionOptions cash, FileOptions evidence, boolean egovpay) {
            this.quoteId=quoteId; this.serviceVersion=serviceVersion; this.cash=cash; this.evidence=evidence;
            this.egovpay=egovpay;
        }
    }

    public static final class RequestOptions {
        public final String requestId;
        public final long requestVersion;
        public final Map<String,ActionOptions> actions;
        public RequestOptions(String requestId, long requestVersion, Map<String,ActionOptions> actions) {
            this.requestId=requestId; this.requestVersion=requestVersion;
            this.actions=Collections.unmodifiableMap(new LinkedHashMap<>(actions));
        }
    }

    public static final class PreparedUpload {
        public final Quote quote;
        public final QuoteOptions options;
        public final String requirement, mimeType, contentSha256, operationKey, fileId;
        public final long sizeBytes;
        public PreparedUpload(Quote quote, QuoteOptions options, String requirement, String mimeType,
                              long sizeBytes, String contentSha256, String operationKey, String fileId) {
            this.quote=quote; this.options=options; this.requirement=requirement; this.mimeType=mimeType;
            this.sizeBytes=sizeBytes; this.contentSha256=contentSha256; this.operationKey=operationKey; this.fileId=fileId;
        }
    }

    public static final class Evidence {
        public final String id, ownerId, serviceId, requestId, requirement, verification;
        public final boolean current;
        public Evidence(String id, String ownerId, String serviceId, String requestId,
                        String requirement, String verification, boolean current) {
            this.id=id; this.ownerId=ownerId; this.serviceId=serviceId; this.requestId=requestId;
            this.requirement=requirement; this.verification=verification; this.current=current;
        }
    }

    public static final class Record {
        public final String id, reference, ownerId, officeId, deliveryMode, createdAt;
        public final long version;
        public final RequestStatus status;
        public final Service snapshot;
        public Record(String id, String reference, String ownerId, String officeId, String deliveryMode,
                      String createdAt, long version, RequestStatus status, Service snapshot) {
            this.id=id; this.reference=reference; this.ownerId=ownerId; this.officeId=officeId;
            this.deliveryMode=deliveryMode; this.createdAt=createdAt; this.version=version;
            this.status=status; this.snapshot=snapshot;
        }
    }

    public static final class ServicePage {
        public final List<Service> items;
        public final String nextId;
        public ServicePage(List<Service> items, String nextId) { this.items=copy(items); this.nextId=nextId; }
    }

    public static final class RequestPage {
        public final List<Record> items;
        public final String nextCreatedAt, nextId;
        public RequestPage(List<Record> items, String nextCreatedAt, String nextId) {
            this.items=copy(items); this.nextCreatedAt=nextCreatedAt; this.nextId=nextId;
        }
    }

    public static final class Transition {
        public final Record request;
        public final RequestOptions options;
        public final String actorId,scopeOfficeId,reason,operationKey;
        public final RequestStatus target;
        public Transition(Record request,RequestOptions options,String actorId,String scopeOfficeId,RequestStatus target,String reason,String operationKey) {
            this.request=request; this.options=options; this.actorId=actorId; this.scopeOfficeId=scopeOfficeId;
            this.target=target; this.reason=reason; this.operationKey=operationKey;
        }
    }

    public static final class Submission {
        public final Quote quote;
        public final QuoteOptions options;
        public final List<Evidence> evidence;
        public final String notes, paymentMethod, operationKey;
        public Submission(Quote quote, List<Evidence> evidence, String notes, String paymentMethod, String operationKey) {
            this(quote,null,evidence,notes,paymentMethod,operationKey);
        }
        public Submission(Quote quote, QuoteOptions options, List<Evidence> evidence, String notes,
                          String paymentMethod, String operationKey) {
            this.quote=quote; this.evidence=copy(evidence); this.notes=notes;
            this.options=options; this.paymentMethod=paymentMethod; this.operationKey=operationKey;
        }
    }
}
