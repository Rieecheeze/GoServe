package ph.edu.tip.goserve.domain;

public enum OfficeStatus {
    OPEN("Open"),
    ON_LUNCH_BREAK("On Lunch Break"),
    CUT_OFF_REACHED("Cut-Off Reached"),
    TEMPORARILY_CLOSED("Temporarily Closed");

    public final String wireValue;

    OfficeStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    public static OfficeStatus fromWire(String value) {
        for (OfficeStatus status : values()) {
            if (status.wireValue.equals(value)) return status;
        }
        throw new IllegalArgumentException("Unknown office status: " + value);
    }
}
