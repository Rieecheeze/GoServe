package ph.edu.tip.goserve.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.domain.Office;

public final class OfficeAdapter extends RecyclerView.Adapter<OfficeAdapter.Holder> {
    public interface Selection { void selected(Office office); }
    private final List<Office> offices = new ArrayList<>();
    private final boolean cards;
    private final Selection selection;
    public OfficeAdapter(boolean cards, Selection selection) { this.cards = cards; this.selection = selection; }
    public void submit(List<Office> data) { offices.clear(); offices.addAll(data); notifyDataSetChanged(); }
    @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(cards ? R.layout.item_office_home : R.layout.item_office, parent, false));
    }
    @Override public void onBindViewHolder(Holder holder, int position) {
        Office office = offices.get(position);
        holder.name.setText(office.name); holder.category.setText(office.category); holder.status.setText(office.status.wireValue);
        holder.mark.setText(office.id.equals("OFF-REG") ? "REG" : office.id.equals("OFF-ACC") ? "ACC" : office.id.equals("OFF-CASH") ? "CASH" : office.id.equals("DEP-CCS") ? "CCS" : "GCC");
        if (cards) holder.mark.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(),
                office.id.equals("OFF-ACC") ? R.color.gs_text : R.color.gs_secondary));
        TextView campus = holder.itemView.findViewById(R.id.campusLabel);
        if (campus != null) campus.setText(office.campus);
        TextView note = holder.itemView.findViewById(R.id.officeNote);
        if (note != null) note.setText(office.demo ? holder.itemView.getContext().getString(R.string.demo_office_logic) : office.note);
        View illustration = holder.itemView.findViewById(R.id.officeIllustration);
        if (illustration instanceof android.widget.ImageView) {
            ((android.widget.ImageView) illustration).setImageResource(office.id.equals("OFF-ACC") ? R.drawable.office_art_accounting : R.drawable.office_art_registrar);
        }
        holder.itemView.setOnClickListener(v -> selection.selected(office));
        holder.itemView.setContentDescription(office.name + ", " + office.status.wireValue + ", " + office.campus);
    }
    @Override public int getItemCount() { return offices.size(); }
    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name, category, status, mark;
        Holder(View view) {
            super(view); name = view.findViewById(R.id.officeName); category = view.findViewById(R.id.officeCategory);
            status = view.findViewById(R.id.officeStatus); mark = view.findViewById(R.id.officeMark);
        }
    }
}
