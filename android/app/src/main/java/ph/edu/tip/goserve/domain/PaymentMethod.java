package ph.edu.tip.goserve.domain;

public enum PaymentMethod {
    EGOVPAY("egovpay"),
    CASH("cash");

    public final String wireValue;

    PaymentMethod(String wireValue) {
        this.wireValue = wireValue;
    }

    public static PaymentMethod fromWire(String value) {
        for (PaymentMethod method : values()) {
            if (method.wireValue.equals(value)) return method;
        }
        throw new IllegalArgumentException("Unknown payment method: " + value);
    }
}
