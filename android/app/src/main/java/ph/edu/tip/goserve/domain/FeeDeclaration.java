package ph.edu.tip.goserve.domain;

public enum FeeDeclaration {
    REQUIRED("required"), FREE("free"), PENDING_REVIEW("pending_review");

    public final String wireValue;
    FeeDeclaration(String wireValue) { this.wireValue = wireValue; }
    public static FeeDeclaration fromWire(String value) {
        for (FeeDeclaration declaration : values()) if (declaration.wireValue.equals(value)) return declaration;
        throw new IllegalArgumentException("Unknown office fee declaration");
    }
}
