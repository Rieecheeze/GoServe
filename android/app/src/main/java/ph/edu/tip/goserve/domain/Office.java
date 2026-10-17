package ph.edu.tip.goserve.domain;

public final class Office {
    public static final String CASHIERING_ID = "OFF-CASH";

    public final String id;
    public final String name;
    public final String campus;
    public final OfficeStatus status;
    public final String note;
    public final String category;
    public final boolean demo;

    public Office(String id, String name, String campus, OfficeStatus status, String note,
                  String category, boolean demo) {
        this.id = id;
        this.name = name;
        this.campus = campus;
        this.status = status;
        this.note = note;
        this.category = category;
        this.demo = demo;
    }
}
