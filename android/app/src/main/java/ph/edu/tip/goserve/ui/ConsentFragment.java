package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentAccountProfileBinding;
import ph.edu.tip.goserve.domain.StudentProfile;

public final class ConsentFragment extends AccountFragment {
    private FragmentAccountProfileBinding binding;
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved) { binding=FragmentAccountProfileBinding.inflate(inflater,parent,false); return binding.getRoot(); }
    @Override public void onViewCreated(View view,Bundle saved) {
        super.onViewCreated(view,saved); StudentProfile profile=model.draft().profile;
        binding.firstNameInput.setText(profile.firstName); binding.middleNameInput.setText(profile.middleName); binding.lastNameInput.setText(profile.lastName);
        binding.genderInput.setText(profile.gender); binding.studentIdInput.setText(profile.studentId); binding.programInput.setText(profile.courseProgram);
        binding.yearInput.setText(profile.yearLevel); binding.mobileInput.setText(profile.mobileNumber); binding.profileEmail.setText(model.draft().email);
        binding.noMiddleNameCheck.setChecked(profile.noMiddleName); binding.middleNameInput.setEnabled(!profile.noMiddleName);
        binding.noMiddleNameCheck.setSaveEnabled(false); binding.noMiddleNameCheck.setSaveFromParentEnabled(false);
        for (EditText input:new EditText[]{binding.firstNameInput,binding.middleNameInput,binding.lastNameInput,binding.genderInput,binding.studentIdInput,binding.programInput,binding.yearInput,binding.mobileInput}) watch(input,value -> model.saveProfileDraft(read()));
        binding.noMiddleNameCheck.setOnCheckedChangeListener((button,checked) -> {
            if (checked) binding.middleNameInput.setText(""); binding.middleNameInput.setEnabled(!checked); model.saveProfileDraft(read());
        });
        binding.saveProfileButton.setText(model.registering()?R.string.account_create:R.string.account_finish_setup);
        binding.subtitle.setText(model.registering()?R.string.account_profile_email_intro:R.string.account_profile_google_intro);
        binding.profileEmailHelp.setText(model.registering()?R.string.account_profile_email_help:R.string.account_profile_google_email_help);
        binding.saveProfileButton.setOnClickListener(v -> model.submitProfile(read())); binding.backButton.setOnClickListener(v -> model.back());
        model.state().observe(getViewLifecycleOwner(),state -> {
            common(view,state); binding.middleNameInput.setEnabled(!state.loading&&!binding.noMiddleNameCheck.isChecked());
            error(binding.firstNameError,state.fieldErrors.get("first_name")); error(binding.middleNameError,state.fieldErrors.get("middle_name"));
            error(binding.lastNameError,state.fieldErrors.get("last_name")); error(binding.genderError,state.fieldErrors.get("gender"));
            error(binding.studentIdError,state.fieldErrors.get("student_id")); error(binding.programError,state.fieldErrors.get("course_program"));
            error(binding.yearError,state.fieldErrors.get("year_level")); error(binding.mobileError,state.fieldErrors.get("mobile_number"));
        });
    }
    private StudentProfile read() { return new StudentProfile(binding.firstNameInput.getText().toString(),binding.middleNameInput.getText().toString(),binding.noMiddleNameCheck.isChecked(),
            binding.lastNameInput.getText().toString(),binding.studentIdInput.getText().toString(),binding.programInput.getText().toString(),binding.yearInput.getText().toString(),binding.mobileInput.getText().toString(),binding.genderInput.getText().toString()); }
    @Override public void onDestroyView() { super.onDestroyView(); binding=null; }
}
