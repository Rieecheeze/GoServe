package ph.edu.tip.goserve.domain;

public final class SessionProfile {
    public final String id;
    public final String email;
    public final String displayName;
    public final Role role;
    public final String officeId;
    public final String departmentId;
    public final String capability;
    public final boolean demo;
    public final boolean consentAccepted;
    public final long version;
    public final boolean profileComplete;
    public final String currentTermsVersion, acceptedTermsVersion, nextAction;
    public final StudentProfile student;

    public SessionProfile(String id, String email, String displayName, Role role, String officeId,
                          String capability, boolean demo, boolean consentAccepted) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
        this.officeId = officeId;
        this.departmentId = null;
        this.capability = capability;
        this.demo = demo;
        this.consentAccepted = consentAccepted;
        this.version=0; this.profileComplete=false; this.currentTermsVersion=null; this.acceptedTermsVersion=null; this.nextAction="accept_terms"; this.student=null;
    }
    public SessionProfile(String id,String email,String displayName,Role role,String officeId,String capability,boolean demo,
                          long version,boolean complete,String currentTerms,String acceptedTerms,String nextAction,StudentProfile student) {
        this(id,email,displayName,role,officeId,capability,demo,version,complete,currentTerms,acceptedTerms,nextAction,student,null);
    }
    public SessionProfile(String id,String email,String displayName,Role role,String officeId,String capability,boolean demo,
                          long version,boolean complete,String currentTerms,String acceptedTerms,String nextAction,StudentProfile student,String departmentId) {
        this.id=id; this.email=email; this.displayName=displayName; this.role=role; this.officeId=officeId; this.capability=capability; this.demo=demo;
        this.departmentId=departmentId;
        this.version=version; this.profileComplete=complete; this.currentTermsVersion=currentTerms; this.acceptedTermsVersion=acceptedTerms;
        this.nextAction=nextAction; this.consentAccepted=acceptedTerms!=null; this.student=student;
    }
    public boolean ready() { return profileComplete && acceptedTermsVersion!=null && "home".equals(nextAction); }
}
