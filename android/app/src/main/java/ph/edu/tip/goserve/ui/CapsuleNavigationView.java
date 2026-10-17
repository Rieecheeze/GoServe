package ph.edu.tip.goserve.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import ph.edu.tip.goserve.R;

public final class CapsuleNavigationView extends LinearLayout {
    public interface OnItemSelectedListener { boolean onNavigationItemSelected(MenuItem item); }
    private final Menu menu;
    private final LinearLayout items;
    private final TextView selectedLabel;
    private OnItemSelectedListener listener;
    private int selectedId;

    public CapsuleNavigationView(Context context) { this(context, null); }
    public CapsuleNavigationView(Context context, AttributeSet attributes) {
        super(context, attributes);
        setOrientation(VERTICAL);
        setPadding(dp(4), dp(4), dp(4), dp(4));
        setBackgroundResource(R.drawable.bg_navigation_capsule);
        LayoutInflater.from(context).inflate(R.layout.view_capsule_navigation, this, true);
        items = findViewById(R.id.navigationItems);
        selectedLabel = findViewById(R.id.navigationSelectedLabel);
        menu = new PopupMenu(context, this).getMenu();
        TypedArray options = context.obtainStyledAttributes(attributes, R.styleable.CapsuleNavigationView);
        int menuResource = options.getResourceId(R.styleable.CapsuleNavigationView_capsuleMenu, 0);
        options.recycle();
        if (menuResource != 0) inflateMenu(menuResource);
    }

    public Menu getMenu() { return menu; }
    public int getSelectedItemId() { return selectedId; }
    public void setOnItemSelectedListener(OnItemSelectedListener listener) { this.listener = listener; }
    public void inflateMenu(int resource) {
        new android.view.MenuInflater(getContext()).inflate(resource, menu);
        items.removeAllViews();
        for (int index = 0; index < menu.size(); index++) {
            MenuItem item = menu.getItem(index);
            if (!item.isVisible()) continue;
            item.setCheckable(true);
            View target = LayoutInflater.from(getContext()).inflate(R.layout.item_capsule_navigation, items, false);
            target.setId(item.getItemId());
            target.setContentDescription(item.getTitle());
            target.setTooltipText(item.getTitle());
            target.setEnabled(item.isEnabled());
            ImageView icon = target.findViewById(R.id.navigationIcon);
            icon.setImageDrawable(item.getIcon() == null ? null : item.getIcon().mutate());
            ((TextView) target.findViewById(R.id.navigationItemLabel)).setText(item.getTitle());
            target.setOnClickListener(view -> setSelectedItemId(item.getItemId()));
            target.setAccessibilityDelegate(new AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View view, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(view, info);
                    info.setClassName("android.widget.Button");
                    info.setSelected(view.isSelected());
                }
            });
            items.addView(target);
        }
        if (menu.findItem(selectedId) == null && menu.size() > 0) selectedId = menu.getItem(0).getItemId();
        renderSelection();
        requestLayout();
    }

    public void setSelectedItemId(int id) {
        MenuItem item = menu.findItem(id);
        if (item == null || !item.isEnabled() || !item.isVisible()) return;
        if (listener != null && !listener.onNavigationItemSelected(item)) return;
        selectedId = id;
        renderSelection();
    }

    private void renderSelection() {
        int active = ContextCompat.getColor(getContext(), R.color.capsule_navigation_ink);
        int inactive = ContextCompat.getColor(getContext(), R.color.capsule_navigation_text);
        for (int index = 0; index < items.getChildCount(); index++) {
            View target = items.getChildAt(index);
            boolean selected = target.getId() == selectedId;
            target.setSelected(selected);
            ((ImageView) target.findViewById(R.id.navigationIcon))
                    .setImageTintList(ColorStateList.valueOf(selected ? active : inactive));
            menu.findItem(target.getId()).setChecked(selected);
        }
        MenuItem selected = menu.findItem(selectedId);
        selectedLabel.setText(selected == null ? "" : selected.getTitle());
        requestLayout();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        for (int index = 0; index < items.getChildCount(); index++) {
            items.getChildAt(index).findViewById(R.id.navigationItemLabel).setVisibility(GONE);
        }
        selectedLabel.setVisibility(GONE);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override protected Parcelable onSaveInstanceState() {
        SavedState state = new SavedState(super.onSaveInstanceState());
        state.selectedId = selectedId;
        return state;
    }
    @Override protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) { super.onRestoreInstanceState(state); return; }
        SavedState saved = (SavedState) state;
        super.onRestoreInstanceState(saved.getSuperState());
        if (menu.findItem(saved.selectedId) != null) selectedId = saved.selectedId;
        renderSelection();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    public static final class SavedState extends BaseSavedState {
        int selectedId;
        SavedState(Parcelable state) { super(state); }
        SavedState(Parcel source) { super(source); selectedId = source.readInt(); }
        @Override public void writeToParcel(Parcel destination, int flags) {
            super.writeToParcel(destination, flags);
            destination.writeInt(selectedId);
        }
        public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel source) { return new SavedState(source); }
            public SavedState[] newArray(int size) { return new SavedState[size]; }
        };
    }
}
