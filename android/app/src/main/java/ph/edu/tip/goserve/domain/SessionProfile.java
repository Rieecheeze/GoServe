package ph.edu.tip.goserve.domain;

public final class SessionProfile {
    public final String id;
    public final String email;
    public final String displayName;
    public final Role role;
    public final String officeId;
    public final String capability;
    public final boolean demo;
    public final boolean consentAccepted;

    public SessionProfile(String id, String email, String displayName, Role role, String officeId,
                          String capability, boolean demo, boolean consentAccepted) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.officeId = officeId;
        this.capability = capability;
        this.demo = demo;
        this.consentAccepted = consentAccepted;
    }
}
