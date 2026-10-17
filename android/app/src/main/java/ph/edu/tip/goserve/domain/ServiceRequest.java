package ph.edu.tip.goserve.domain;

public final class ServiceRequest {
    public final String id;
    public final String title;
    public final String officeName;
    public final String reference;
    public final RequestStatus status;
    public final FeeDeclaration feeDeclaration;
    public final PaymentStatus paymentStatus;
    public final boolean demo;

    public ServiceRequest(String id, String title, String officeName, String reference,
                          RequestStatus status, FeeDeclaration feeDeclaration, PaymentStatus paymentStatus, boolean demo) {
        if (id == null || id.isBlank() || title == null || title.isBlank() || officeName == null || officeName.isBlank()) {
            throw new IllegalArgumentException("Request identity and service are required");
        }
        if (!RequestWorkflow.canAdvanceTo(feeDeclaration, paymentStatus, status)) {
            throw new IllegalArgumentException("Request progress does not meet its office fee declaration");
        }
        this.id = id;
        this.title = title;
        this.officeName = officeName;
        this.reference = reference;
        this.status = status;
        this.feeDeclaration = feeDeclaration;
        this.paymentStatus = paymentStatus;
        this.demo = demo;
    }
}
