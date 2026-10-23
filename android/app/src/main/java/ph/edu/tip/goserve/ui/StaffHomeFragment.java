package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentStaffHomeLiveBinding;
import ph.edu.tip.goserve.domain.Office;
import ph.edu.tip.goserve.domain.SessionProfile;

public final class StaffHomeFragment extends Fragment {
    private FragmentStaffHomeLiveBinding binding;
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved){binding=FragmentStaffHomeLiveBinding.inflate(inflater,parent,false);return binding.getRoot();}
    @Override public void onViewCreated(View view,Bundle saved){
        MainActivity host=(MainActivity)requireActivity();SessionProfile profile=host.profile();
        String id=profile==null?"":profile.officeId;
        binding.workspaceOffice.setText(getString(R.string.workspace_scope,id));
        binding.workspaceBack.setOnClickListener(v->host.getOnBackPressedDispatcher().onBackPressed());
        binding.workspaceQueue.setEnabled(RequestListViewModel.permitted(profile));binding.workspaceQueue.setOnClickListener(v->host.navigate(RequestListFragment.create(),true));
        binding.workspaceScan.setVisibility(RequestListViewModel.permitted(profile)?View.VISIBLE:View.GONE);binding.workspaceScan.setOnClickListener(v->host.navigate(PickupFragment.create(null),true));
        binding.workspaceSchedule.setVisibility(ConsultationViewModel.permitted(profile)?View.VISIBLE:View.GONE);binding.workspaceSchedule.setOnClickListener(v->host.navigate(new ConsultationFragment(),true));
        binding.workspaceInbox.setVisibility(InquiryViewModel.permitted(profile)?View.VISIBLE:View.GONE);binding.workspaceInbox.setOnClickListener(v->host.navigate(InboxFragment.create(),true));
        binding.workspaceNotifications.setOnClickListener(v->host.navigate(NotificationsFragment.create(),true));
        boolean manage=OfficeManagementViewModel.permitted(profile);
        binding.workspaceOfficeManage.setVisibility(manage?View.VISIBLE:View.GONE);binding.workspaceAnnouncementsManage.setVisibility(manage?View.VISIBLE:View.GONE);binding.workspaceServicesManage.setVisibility(manage?View.VISIBLE:View.GONE);
        binding.workspaceOfficeManage.setOnClickListener(v->host.navigate(OfficeManagementFragment.create(ph.edu.tip.goserve.data.OfficeManagementRepository.Kind.OFFICE),true));
        binding.workspaceAnnouncementsManage.setOnClickListener(v->host.navigate(OfficeManagementFragment.create(ph.edu.tip.goserve.data.OfficeManagementRepository.Kind.ANNOUNCEMENT),true));
        binding.workspaceServicesManage.setOnClickListener(v->host.navigate(OfficeManagementFragment.create(ph.edu.tip.goserve.data.OfficeManagementRepository.Kind.SERVICE),true));
        OfficeViewModel offices=new ViewModelProvider(host).get(OfficeViewModel.class);
        offices.state().observe(getViewLifecycleOwner(),s->{for(Office office:s.offices)if(office.id.equals(id))binding.workspaceOffice.setText(getString(R.string.workspace_scope,office.name));});
        SharedMotion.install(view);
    }
    @Override public void onDestroyView(){binding=null;super.onDestroyView();}
}
