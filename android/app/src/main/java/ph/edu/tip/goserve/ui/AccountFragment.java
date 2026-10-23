package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import ph.edu.tip.goserve.R;

abstract class AccountFragment extends Fragment {
    protected AuthViewModel model;
    private View root;
    private final List<Runnable> detach=new ArrayList<>();
    @Override public void onViewCreated(View view,Bundle saved) {
        super.onViewCreated(view,saved); root=view; model=new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        SharedMotion.install(view);
        for (int id:new int[]{R.id.title,R.id.personalHeading,R.id.academicHeading,R.id.contactHeading}) {
            View heading=view.findViewById(id); if (heading!=null) androidx.core.view.ViewCompat.setAccessibilityHeading(heading,true);
        }
    }
    protected void watch(EditText input,Consumer<String> update) {
        // The retained draft owns restoration; hierarchy text must not overwrite it after binding.
        input.setSaveEnabled(false); input.setSaveFromParentEnabled(false);
        TextWatcher watcher=new TextWatcher() {
            public void beforeTextChanged(CharSequence text,int start,int count,int after) {}
            public void onTextChanged(CharSequence text,int start,int before,int count) { update.accept(text.toString()); }
            public void afterTextChanged(Editable value) {}
        };
        input.addTextChangedListener(watcher); detach.add(() -> input.removeTextChangedListener(watcher));
    }
    protected void error(TextView view,String message) { view.setText(message); view.setVisibility(message==null?View.GONE:View.VISIBLE); }
    protected void common(View view,AuthViewModel.State state) {
        View progress=view.findViewById(R.id.progress); if (progress!=null) progress.setVisibility(state.loading?View.VISIBLE:View.GONE);
        TextView message=view.findViewById(R.id.errorText); if (message!=null) error(message,state.error);
        enabled(view,!state.loading);
    }
    private void enabled(View view,boolean enabled) {
        if (view instanceof EditText || view instanceof android.widget.Button || view instanceof android.widget.CheckBox) view.setEnabled(enabled);
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) enabled(((ViewGroup)view).getChildAt(i),enabled);
    }
    @Override public void onDestroyView() {
        for (Runnable remove:detach) remove.run(); detach.clear();
        if (root!=null) for (int id:new int[]{R.id.passwordInput,R.id.confirmationInput}) {
            TextView secret=root.findViewById(id); if (secret!=null) secret.setText("");
        }
        root=null; super.onDestroyView();
    }
}
