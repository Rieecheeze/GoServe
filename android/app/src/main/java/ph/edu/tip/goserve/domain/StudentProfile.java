package ph.edu.tip.goserve.domain;

public final class StudentProfile {
    public final String firstName;
    public final String middleName;
    public final boolean noMiddleName;
    public final String lastName;
    public final String studentId;
    public final String courseProgram;
    public final String yearLevel;
    public final String mobileNumber;
    public final String gender;

    public StudentProfile(String firstName, String middleName, boolean noMiddleName, String lastName,
                          String studentId, String courseProgram, String yearLevel, String mobileNumber,
                          String gender) {
        this.firstName = firstName;
        this.middleName = middleName;
        this.noMiddleName = noMiddleName;
        this.lastName = lastName;
        this.studentId = studentId;
        this.courseProgram = courseProgram;
        this.yearLevel = yearLevel;
        this.mobileNumber = mobileNumber;
        this.gender = gender;
    }
}
