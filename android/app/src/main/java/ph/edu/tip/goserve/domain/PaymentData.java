package ph.edu.tip.goserve.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PaymentData {
    private PaymentData() {}

    public static final class Record {
        public final String accountId, id, requestId, currency, confirmedBy, confirmedAt;
        public final PaymentMethod method;
        public final PaymentStatus status;
        public final BigDecimal amount;
        public final long version;
        public Record(String accountId,String id,String requestId,PaymentMethod method,PaymentStatus status,
                      BigDecimal amount,String currency,long version,String confirmedBy,String confirmedAt) {
            this.accountId=accountId; this.id=id; this.requestId=requestId; this.method=method; this.status=status;
            this.amount=amount; this.currency=currency; this.version=version; this.confirmedBy=confirmedBy; this.confirmedAt=confirmedAt;
        }
    }

    public static final class CashReview {
        public final String accountId, paymentId, requestId, reference, officeId, serviceName, currency, confirmedAt;
        public final BigDecimal amount;
        public final PaymentStatus status;
        public final long version;
        public CashReview(String accountId,String paymentId,String requestId,String reference,String officeId,
                          String serviceName,BigDecimal amount,String currency,PaymentStatus status,long version,String confirmedAt) {
            this.accountId=accountId; this.paymentId=paymentId; this.requestId=requestId; this.reference=reference;
            this.officeId=officeId; this.serviceName=serviceName; this.amount=amount; this.currency=currency;
            this.status=status; this.version=version; this.confirmedAt=confirmedAt;
        }
    }

    public static final class CashConfirmation {
        public final CashReview review;
        public final String operationKey;
        public CashConfirmation(CashReview review,String operationKey) { this.review=review; this.operationKey=operationKey; }
    }

    public static final class Checkout {
        public final Record payment;
        public final String operationKey;
        public Checkout(Record payment,String operationKey) { this.payment=payment; this.operationKey=operationKey; }
    }

    public static final class Page {
        public final List<Record> items;
        public final String nextId;
        public Page(List<Record> items,String nextId) {
            this.items=Collections.unmodifiableList(new ArrayList<>(items)); this.nextId=nextId;
        }
    }
}
