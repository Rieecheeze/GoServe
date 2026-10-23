package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import java.util.ArrayList;
import java.util.List;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentPasswordChangeBinding;

public final class PasswordChangeFragment extends Fragment {
    private FragmentPasswordChangeBinding binding;
    private ViewModelProvider.Factory factory;
    private PasswordChangeViewModel model;
    private final List<Runnable> detach=new ArrayList<>();
    private boolean rendering;
    public PasswordChangeFragment() {}
    PasswordChangeFragment(ViewModelProvider.Factory factory) { this.factory=factory; }
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved) {
        binding=FragmentPasswordChangeBinding.inflate(inflater,parent,false); return binding.getRoot();
    }
    @Override public void onViewCreated(View view,Bundle saved) {
        model=new ViewModelProvider(this,factory==null?getDefaultViewModelProviderFactory():factory).get(PasswordChangeViewModel.class);
        androidx.core.view.ViewCompat.setAccessibilityHeading(binding.passwordHeading,true);
        for(EditText input:inputs())watch(input);
        binding.passwordSaveButton.setOnClickListener(v->submit());
        binding.passwordSendCode.setOnClickListener(v->model.requestCode());
        binding.passwordBack.setOnClickListener(v->{ if(!model.state().getValue().busy())requireActivity().getOnBackPressedDispatcher().onBackPressed(); });
        binding.newPasswordConfirmation.setOnEditorActionListener((v,action,event)->{
            if(action!=android.view.inputmethod.EditorInfo.IME_ACTION_DONE)return false;
            submit(); return true;
        });
        binding.passwordCodeInput.setOnEditorActionListener((v,action,event)->{
            if(action!=android.view.inputmethod.EditorInfo.IME_ACTION_DONE)return false;
            submit(); return true;
        });
        OnBackPressedCallback back=new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() {}
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),back);
        model.state().observe(getViewLifecycleOwner(),state->{ render(state); back.setEnabled(state.busy()); });
        ((GoServeApplication)requireActivity().getApplication()).accountInvalidations().observe(getViewLifecycleOwner(),ignored->model.checkCurrentAccount());
        SharedMotion.install(view);
    }
    @Override public void onStart() {
        super.onStart(); ((MainActivity)requireActivity()).setRoleNavigationVisible(false);
    }
    @Override public void onResume() { super.onResume(); model.checkCurrentAccount(); }
    private EditText[] inputs() {
        return new EditText[]{binding.currentPasswordInput,binding.newPasswordInput,binding.newPasswordConfirmation,binding.passwordCodeInput};
    }
    private void watch(EditText input) {
        input.setSaveEnabled(false); input.setSaveFromParentEnabled(false);
        TextWatcher watcher=new TextWatcher() {
            public void beforeTextChanged(CharSequence value,int start,int count,int after) {}
            public void onTextChanged(CharSequence value,int start,int before,int count) {
                if(!rendering)model.edit(binding.currentPasswordInput.getText().toString(),binding.newPasswordInput.getText().toString(),
                        binding.newPasswordConfirmation.getText().toString(),binding.passwordCodeInput.getText().toString());
            }
            public void afterTextChanged(Editable value) {}
        };
        input.addTextChangedListener(watcher); detach.add(()->input.removeTextChangedListener(watcher));
    }
    private void submit() {
        if(model.state().getValue().mode!=PasswordChangeViewModel.Mode.EDITING)return;
        android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if(keyboard!=null)keyboard.hideSoftInputFromWindow(binding.getRoot().getWindowToken(),0);
        model.submit();
    }
    private void render(PasswordChangeViewModel.State state) {
        if(binding==null)return; rendering=true;
        try {
            boolean editable=state.mode==PasswordChangeViewModel.Mode.EDITING;
            EditText[] inputs=inputs(); String[] values={model.currentPassword(),model.newPassword(),model.confirmation(),model.code()};
            for(int i=0;i<inputs.length;++i) {
                if(!inputs[i].getText().toString().equals(values[i]))inputs[i].setText(values[i]);
                inputs[i].setEnabled(editable);
            }
            binding.currentPasswordBox.setEnabled(editable); binding.newPasswordBox.setEnabled(editable); binding.newPasswordConfirmationBox.setEnabled(editable);
            boolean codeAppeared=state.requiresCode && binding.passwordCodePanel.getVisibility()!=View.VISIBLE;
            binding.passwordCodePanel.setVisibility(state.requiresCode?View.VISIBLE:View.GONE);
            binding.passwordCodeStatus.setVisibility(state.codeSent?View.VISIBLE:View.GONE);
            binding.passwordSendCode.setEnabled(editable);
            binding.passwordSendCode.setText(state.mode==PasswordChangeViewModel.Mode.SENDING_CODE?R.string.password_sending_code:R.string.password_send_code);
            binding.passwordSaveButton.setEnabled(editable);
            binding.passwordSaveButton.setText(state.mode==PasswordChangeViewModel.Mode.CHANGING?R.string.password_change_saving:R.string.password_change_save);
            binding.passwordChangeProgress.setVisibility(state.busy()?View.VISIBLE:View.GONE);
            binding.passwordBack.setEnabled(!state.busy());
            boolean showError=state.error!=null && !"REAUTHENTICATION_REQUIRED".equals(state.error);
            String error=showError?getString(message(state.error)):"";
            boolean reveal=showError && (binding.passwordChangeError.getVisibility()!=View.VISIBLE || !binding.passwordChangeError.getText().toString().equals(error));
            binding.passwordChangeError.setVisibility(showError?View.VISIBLE:View.GONE);
            binding.passwordChangeError.setText(error);
            if(reveal)reveal(binding.passwordChangeError);
            else if(codeAppeared)reveal(binding.passwordCodeIntro);
        } finally { rendering=false; }
    }
    private void reveal(View target) {
        View root=binding.getRoot();
        androidx.core.view.OneShotPreDrawListener.add(target,()->{
            if(binding!=null && binding.getRoot()==root && target.isShown())
                target.requestRectangleOnScreen(new android.graphics.Rect(0,0,target.getWidth(),target.getHeight()),true);
        });
    }
    private static int message(String code) {
        switch(code) {
            case "CURRENT_PASSWORD_REQUIRED": return R.string.password_current_required;
            case "CURRENT_PASSWORD_INVALID": return R.string.password_current_invalid;
            case "PASSWORD_WEAK": return R.string.password_weak;
            case "CONFIRMATION_MISMATCH": return R.string.password_confirmation_mismatch;
            case "PASSWORD_REUSED": return R.string.password_reused;
            case "REAUTHENTICATION_REQUIRED": return R.string.password_code_intro;
            case "CODE_REQUIRED": return R.string.password_code_required;
            case "CODE_INVALID": return R.string.password_code_invalid;
            case "RATE_LIMITED": return R.string.password_rate_limited;
            case "SESSION_EXPIRED": return R.string.password_session_expired;
            case "OUTCOME_UNCERTAIN": return R.string.password_change_uncertain_notice;
            default: return R.string.password_change_failed;
        }
    }
    @Override public void onDestroyView() {
        for(Runnable remove:detach)remove.run(); detach.clear();
        if(binding!=null)for(EditText input:inputs())input.setText("");
        ((MainActivity)requireActivity()).setRoleNavigationVisible(true);
        binding=null; super.onDestroyView();
    }
}
