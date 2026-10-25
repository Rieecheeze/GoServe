package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentSettingsGroupBinding;
import ph.edu.tip.goserve.domain.SessionProfile;

public final class SettingsGroupFragment extends Fragment {
    private FragmentSettingsGroupBinding binding;
    public static SettingsGroupFragment account(){return create(false);}
    public static SettingsGroupFragment seryo(){return create(true);}
    private static SettingsGroupFragment create(boolean seryo){SettingsGroupFragment fragment=new SettingsGroupFragment();Bundle args=new Bundle();args.putBoolean("seryo",seryo);fragment.setArguments(args);return fragment;}
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved){binding=FragmentSettingsGroupBinding.inflate(inflater,parent,false);return binding.getRoot();}
    @Override public void onViewCreated(View view,Bundle saved){
        MainActivity host=(MainActivity)requireActivity();SharedMotion.install(view);
        boolean seryo=requireArguments().getBoolean("seryo");
        binding.title.setText(seryo?R.string.settings_seryo_group:R.string.account);
        binding.backButton.setOnClickListener(v -> host.getOnBackPressedDispatcher().onBackPressed());
        if(seryo){
            binding.accountText.setText(R.string.settings_seryo_intro);
            for(View action:new View[]{binding.seryoSettingsButton,binding.seryoPrivacyButton,binding.seryoHelpButton})action.setVisibility(View.VISIBLE);
            binding.seryoSettingsButton.setOnClickListener(v -> host.navigate(AssistantFragment.create("settings"),true));
            binding.seryoPrivacyButton.setOnClickListener(v -> host.navigate(AssistantFragment.create("privacy"),true));
            binding.seryoHelpButton.setOnClickListener(v -> host.navigate(AssistantFragment.create("help"),true));
        }else{
            SessionProfile profile=host.isPreview()?null:host.profile();
            binding.accountText.setText(profile==null?getString(R.string.settings_account_preview):profile.email+"\n"+profile.role.wireValue);
            if(profile!=null && profile.departmentId!=null){String department="DEP-CCS".equals(profile.departmentId)?getString(R.string.profile_department_ccs):profile.departmentId;binding.accountText.append("\n"+getString(R.string.profile_department_label,department));}
            for(View action:new View[]{binding.profilePhotoButton,binding.accountInfoButton,binding.changePasswordButton,binding.recoveryCodesButton})action.setVisibility(View.VISIBLE);
            binding.profilePhotoButton.setEnabled(profile!=null);binding.changePasswordButton.setEnabled(profile!=null);
            binding.profilePhotoButton.setOnClickListener(v -> host.navigate(new ProfilePhotoFragment(),true));
            binding.accountInfoButton.setOnClickListener(v -> host.navigate(new AccountInfoFragment(),true));
            binding.changePasswordButton.setOnClickListener(v -> host.openAccountPasswordRecovery());
            binding.recoveryCodesButton.setEnabled(profile!=null);
            binding.recoveryCodesButton.setOnClickListener(v -> host.navigate(RecoverySecurityFragment.create(RecoverySecurityViewModel.Kind.CODES),true));
            binding.notificationSettingsButton.setVisibility(profile!=null && profile.ready()?View.VISIBLE:View.GONE);
            binding.notificationSettingsButton.setOnClickListener(v -> host.navigate(NotificationsFragment.create(),true));
        }
    }
    @Override public void onDestroyView(){binding=null;super.onDestroyView();}
}
