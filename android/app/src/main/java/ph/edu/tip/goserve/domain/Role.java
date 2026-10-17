package ph.edu.tip.goserve.domain;

public enum Role {
    STUDENT("student"),
    STAFF("staff"),
    TELLER("teller");

    public final String wireValue;

    Role(String wireValue) {
        this.wireValue = wireValue;
    }

    public static Role fromWire(String value) {
        for (Role role : values()) {
            if (role.wireValue.equals(value)) return role;
        }
        throw new IllegalArgumentException("Unknown role: " + value);
    }
}
