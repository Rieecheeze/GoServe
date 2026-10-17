package ph.edu.tip.goserve.data;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import ph.edu.tip.goserve.domain.Office;
import ph.edu.tip.goserve.domain.OfficeStatus;

public final class OfficeRepository {
    private final SupabaseClient client;
    private final AuthRepository auth;
    public OfficeRepository(SupabaseClient client, AuthRepository auth) { this.client = client; this.auth = auth; }

    public List<Office> load() throws Exception {
        JSONArray rows = client.array("/rest/v1/offices?select=id,name,campus,status,note,category,is_demo&active=eq.true&order=name", auth.token());
        List<Office> offices = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            offices.add(new Office(row.getString("id"), row.getString("name"), row.getString("campus"),
                    OfficeStatus.fromWire(row.getString("status")), row.optString("note", ""),
                    row.getString("category"), row.getBoolean("is_demo")));
        }
        return offices;
    }

    public List<Office> preview() {
        return java.util.Arrays.asList(new Office("OFF-REG", "Registrar", "Casal / Arlegui", OfficeStatus.OPEN, "Non-official demo data", "Documents", true),
                new Office("OFF-ACC", "Student Accounting", "Casal / Arlegui", OfficeStatus.ON_LUNCH_BREAK, "Non-official demo data", "Payments", true),
                new Office("OFF-CASH", "Cashiering", "Casal / Arlegui", OfficeStatus.OPEN, "Non-official demo data", "Payments", true),
                new Office("OFF-GCC", "Guidance and Counseling Center", "Casal / Arlegui", OfficeStatus.OPEN, "Non-official demo data", "Consultations", true),
                new Office("DEP-CCS", "College of Computer Studies", "Casal / Arlegui", OfficeStatus.OPEN, "Non-official demo data", "Consultations", true));
    }
}
