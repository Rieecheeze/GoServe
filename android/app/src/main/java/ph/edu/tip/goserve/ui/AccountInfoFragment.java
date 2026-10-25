package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import java.util.ArrayList;
import java.util.List;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentAccountProfileBinding;
import ph.edu.tip.goserve.domain.AccountValidation;
import ph.edu.tip.goserve.domain.SessionProfile;
import ph.edu.tip.goserve.domain.StudentProfile;

public final class AccountInfoFragment extends Fragment {
    private FragmentAccountProfileBinding binding;private AccountInfoViewModel model;
    private final List<Runnable> detach=new ArrayList<>();private boolean rendering;private SessionProfile displayed;
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved){binding=FragmentAccountProfileBinding.inflate(inflater,parent,false);return binding.getRoot();}
    @Override public void onViewCreated(View view,Bundle saved){
        MainActivity host=(MainActivity)requireActivity();
        model=new ViewModelProvider(this).get(AccountInfoViewModel.class);model.start(host.isPreview());
        binding.authLogo.setVisibility(View.GONE);binding.title.setText(R.string.settings_account_info);
        binding.subtitle.setText(host.isPreview()?R.string.account_info_preview:R.string.account_info_intro);
        binding.profileEmailHelp.setText(R.string.account_info_email_help);
        for(int id:new int[]{R.id.title,R.id.personalHeading,R.id.academicHeading,R.id.contactHeading})androidx.core.view.ViewCompat.setAccessibilityHeading(view.findViewById(id),true);
        for(EditText input:inputs()){
            input.setSaveEnabled(false);input.setSaveFromParentEnabled(false);
            TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence value,int start,int count,int after){}public void onTextChanged(CharSequence value,int start,int before,int count){if(!rendering)model.setDraft(read());}public void afterTextChanged(Editable value){}};
            input.addTextChangedListener(watcher);detach.add(() -> input.removeTextChangedListener(watcher));
        }
        binding.noMiddleNameCheck.setSaveEnabled(false);binding.noMiddleNameCheck.setSaveFromParentEnabled(false);
        binding.noMiddleNameCheck.setOnCheckedChangeListener((button,checked) -> {if(!rendering){if(checked)binding.middleNameInput.setText("");model.setDraft(read());}});
        binding.saveProfileButton.setOnClickListener(v -> model.save());
        binding.backButton.setOnClickListener(v -> {if(!model.state().getValue().busy)host.getOnBackPressedDispatcher().onBackPressed();});
        OnBackPressedCallback back=new OnBackPressedCallback(false){@Override public void handleOnBackPressed(){}};
        host.getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),back);
        model.state().observe(getViewLifecycleOwner(),state -> {render(state);back.setEnabled(state.busy);});
        ((GoServeApplication)host.getApplication()).accountInvalidations().observe(getViewLifecycleOwner(),ignored -> model.checkCurrentAccount());
        SharedMotion.install(view);
    }
    @Override public void onStart(){super.onStart();((MainActivity)requireActivity()).setRoleNavigationVisible(false);}
    @Override public void onResume(){super.onResume();model.checkCurrentAccount();}
    private void render(AccountInfoViewModel.State state){
        if(binding==null)return;rendering=true;
        if(displayed!=state.profile || state.profile==null){
            displayed=state.profile;StudentProfile profile=model.draft();
            binding.firstNameInput.setText(profile.firstName);binding.middleNameInput.setText(profile.middleName);binding.lastNameInput.setText(profile.lastName);
            binding.genderInput.setText(profile.gender);binding.studentIdInput.setText(profile.studentId);binding.programInput.setText(profile.courseProgram);
            binding.yearInput.setText(profile.yearLevel);binding.mobileInput.setText(profile.mobileNumber);binding.noMiddleNameCheck.setChecked(profile.noMiddleName);
            binding.profileEmail.setText(state.profile==null?"":state.profile.email);
        }
        for(EditText input:inputs())input.setEnabled(model.canEdit());
        binding.noMiddleNameCheck.setEnabled(model.canEdit());binding.middleNameInput.setEnabled(model.canEdit()&&!binding.noMiddleNameCheck.isChecked());
        binding.saveProfileButton.setEnabled(model.canSave());binding.saveProfileButton.setText(state.busy?R.string.account_info_saving:model.retrying()?R.string.account_info_retry:R.string.account_info_save);
        binding.backButton.setEnabled(!state.busy);binding.progress.setVisibility(state.busy?View.VISIBLE:View.GONE);
        binding.errorText.setText(state.message==0?"":getString(state.message));binding.errorText.setVisibility(state.message==0?View.GONE:View.VISIBLE);
        boolean problem=state.message==R.string.account_info_invalid || state.message==R.string.account_info_uncertain || state.message==R.string.account_info_stale || state.message==R.string.account_info_unavailable;
        binding.errorText.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(),problem?R.color.gs_error:R.color.gs_secondary));
        String[] fields={"first_name","middle_name","last_name","gender","student_id","course_program","year_level","mobile_number"};
        TextView[] errors={binding.firstNameError,binding.middleNameError,binding.lastNameError,binding.genderError,binding.studentIdError,binding.programError,binding.yearError,binding.mobileError};
        for(int index=0;index<fields.length;index++){AccountValidation.ProfileError error=state.fieldErrors.get(fields[index]);errors[index].setVisibility(error==null?View.GONE:View.VISIBLE);if(error!=null)errors[index].setText(error==AccountValidation.ProfileError.MIDDLE_NAME_CONFLICT?R.string.account_info_middle_conflict:R.string.account_info_required);}
        rendering=false;
    }
    private EditText[] inputs(){return new EditText[]{binding.firstNameInput,binding.middleNameInput,binding.lastNameInput,binding.genderInput,binding.studentIdInput,binding.programInput,binding.yearInput,binding.mobileInput};}
    private StudentProfile read(){return new StudentProfile(binding.firstNameInput.getText().toString(),binding.middleNameInput.getText().toString(),binding.noMiddleNameCheck.isChecked(),binding.lastNameInput.getText().toString(),binding.studentIdInput.getText().toString(),binding.programInput.getText().toString(),binding.yearInput.getText().toString(),binding.mobileInput.getText().toString(),binding.genderInput.getText().toString());}
    @Override public void onDestroyView(){for(Runnable remove:detach)remove.run();detach.clear();displayed=null;((MainActivity)requireActivity()).setRoleNavigationVisible(true);binding=null;super.onDestroyView();}
}
