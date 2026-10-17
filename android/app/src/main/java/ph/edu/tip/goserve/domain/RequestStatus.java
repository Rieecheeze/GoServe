package ph.edu.tip.goserve.domain;

public enum RequestStatus {
    SUBMITTED("Submitted"),
    AWAITING_PAYMENT("Awaiting Payment"),
    PROCESSING("Processing"),
    READY_FOR_PICKUP_DOWNLOAD("Ready for Pickup/Download"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    REJECTED("Rejected");

    public final String wireValue;

    RequestStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public static RequestStatus fromWire(String value) {
        for (RequestStatus status : values()) {
            if (status.wireValue.equals(value)) return status;
        }
        throw new IllegalArgumentException("Unknown request status: " + value);
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == REJECTED;
    }
}
