package ph.edu.tip.goserve.domain;

public enum PaymentStatus {
    PENDING("pending"),
    PAID("paid"),
    FAILED("failed"),
    EXPIRED("expired"),
    CANCELLED("cancelled");

    public final String wireValue;

    PaymentStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public static PaymentStatus fromWire(String value) {
        for (PaymentStatus status : values()) {
            if (status.wireValue.equals(value)) return status;
        }
        throw new IllegalArgumentException("Unknown payment status: " + value);
    }
}
