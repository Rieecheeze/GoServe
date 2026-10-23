package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import java.util.List;
import java.util.Set;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.RealtimeDataSource;
import ph.edu.tip.goserve.data.OfficeManagementRepository.Announcement;
import ph.edu.tip.goserve.databinding.ItemOfficeAnnouncementBinding;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentOfficeDetailBinding;
import ph.edu.tip.goserve.domain.Office;

public final class OfficeDetailFragment extends Fragment {
    private ViewModelProvider.Factory announcementFactory;
    private OfficeViewModel officeOverride;
    public OfficeDetailFragment(){}
    OfficeDetailFragment(OfficeViewModel office,ViewModelProvider.Factory announcements){officeOverride=office;announcementFactory=announcements;}
    private FragmentOfficeDetailBinding binding;
    private Office cashier;
    private OfficeViewModel offices;
    private OfficeAnnouncementsViewModel announcements;
    private List<Announcement> shown=List.of();
    private RealtimeDataSource.Subscription subscription;
    private RealtimeDataSource.Session subscribedSession;
    private final Handler foregroundRefresh=new Handler(Looper.getMainLooper());
    // An UPDATE that hides a row under RLS may not reach its former readers.
    private final Runnable reconcileAnnouncements=new Runnable(){@Override public void run(){
        if(binding==null || !isResumed() || announcements==null || announcements.state().getValue().blocked)return;
        announcements.refresh();foregroundRefresh.postDelayed(this,30_000L);
    }};
    public static OfficeDetailFragment create(Office office) {
        OfficeDetailFragment fragment = new OfficeDetailFragment(); Bundle args = new Bundle();
        args.putString("id", office.id); args.putString("name", office.name); args.putString("status", office.status.wireValue); args.putString("note", office.note);
        args.putString("campus", office.campus); args.putBoolean("demo", office.demo); fragment.setArguments(args); return fragment;
    }
    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) { binding = FragmentOfficeDetailBinding.inflate(inflater, parent, false); return binding.getRoot(); }
    @Override public void onViewCreated(View view, Bundle saved) {
        Bundle args = requireArguments();
        binding.officeName.setText(args.getString("name")); binding.officeStatus.setText(args.getString("status"));
        binding.officeNote.setText(args.getString("note")); binding.campusLabel.setText(args.getString("campus"));
        boolean cashierPage = Office.CASHIERING_ID.equals(args.getString("id"));
        binding.servicesTitle.setText(cashierPage ? R.string.cashiering_section_title : R.string.services);
        binding.servicesText.setText(cashierPage ? R.string.cashiering_services_pending : R.string.cashiering_office_services_pending);
        binding.errorText.setVisibility(args.getBoolean("demo") ? View.VISIBLE : View.GONE); binding.errorText.setText(R.string.demo_office_logic);
        binding.backButton.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        MainActivity host = (MainActivity) requireActivity();
        GoServeApplication app=(GoServeApplication)requireActivity().getApplication();
        offices=officeOverride==null?new ViewModelProvider(host).get(OfficeViewModel.class):officeOverride;
        offices.state().observe(getViewLifecycleOwner(),state->{if(!cashierPage)renderCashierRoute(state);renderCurrentOffice(state);});
        offices.ensureLoaded(host.isPreview());
        if(!host.isPreview()){
            announcements=new ViewModelProvider(this,announcementFactory==null?getDefaultViewModelProviderFactory():announcementFactory).get(OfficeAnnouncementsViewModel.class);
            announcements.state().observe(getViewLifecycleOwner(),this::renderAnnouncements);
            binding.officeAnnouncementsRefresh.setOnClickListener(v->announcements.refresh());binding.officeAnnouncementsMore.setOnClickListener(v->announcements.more());
            app.accountInvalidations().observe(getViewLifecycleOwner(),ignored->{offices.checkCurrentAccount();announcements.checkCurrentAccount();});
            announcements.start(args.getString("id"));
        }
        SharedMotion.install(view);
        if (cashierPage) {
            binding.paymentsTitle.setVisibility(View.GONE);
            binding.cashieringCaption.setVisibility(View.GONE);
            binding.cashieringButton.setVisibility(View.GONE);
            binding.cashieringProgress.setVisibility(View.GONE);
            binding.cashieringStatus.setVisibility(View.GONE);
            binding.reloadOfficesButton.setVisibility(View.GONE);
            return;
        }
        ph.edu.tip.goserve.domain.SessionProfile profile=app.auth().profile();
        boolean canStart=!host.isPreview() && profile!=null && profile.ready() && profile.role==ph.edu.tip.goserve.domain.Role.STUDENT;
        binding.startRequest.setVisibility(canStart?View.VISIBLE:View.GONE);
        if(canStart)binding.servicesText.setText(R.string.compose_office_support);
        binding.startRequest.setOnClickListener(v->{
            ph.edu.tip.goserve.domain.SessionProfile current=app.auth().profile();
            if(current!=null && current.ready() && current.role==ph.edu.tip.goserve.domain.Role.STUDENT
                    && host.profile()!=null && current.id.equals(host.profile().id)) {
                host.navigate(RequestComposeFragment.create(args.getString("id")),true);
            } else host.returnToSignIn();
        });
        binding.cashieringButton.setOnClickListener(v -> {
            if (cashier != null && binding.cashieringButton.isEnabled()) host.navigate(create(cashier), true);
        });
        binding.reloadOfficesButton.setOnClickListener(v -> offices.reload(host.isPreview()));
    }

    private void renderCurrentOffice(OfficeViewModel.State state){
        if(binding==null)return;
        if(state.error!=null && state.offices.isEmpty()){
            binding.officeName.setText("");binding.officeStatus.setText("");binding.officeNote.setText("");binding.campusLabel.setText("");binding.startRequest.setEnabled(false);return;
        }
        for(Office office:state.offices)if(office.id.equals(requireArguments().getString("id"))){
            binding.officeName.setText(office.name);binding.officeStatus.setText(office.status.wireValue);binding.officeNote.setText(office.note);binding.campusLabel.setText(office.campus);binding.startRequest.setEnabled(true);return;
        }
        if(!state.loading && state.error==null){binding.officeName.setText("");binding.officeStatus.setText("");binding.officeNote.setText("");binding.campusLabel.setText("");binding.startRequest.setEnabled(false);}
    }
    private void renderAnnouncements(OfficeAnnouncementsViewModel.State state){
        if(binding==null)return;
        binding.officeAnnouncementsSection.setVisibility(state.blocked?View.GONE:View.VISIBLE);
        binding.officeAnnouncementsProgress.setVisibility(state.loading?View.VISIBLE:View.GONE);
        binding.officeAnnouncementsError.setVisibility(state.error==null?View.GONE:View.VISIBLE);binding.officeAnnouncementsError.setText(R.string.office_announcements_failed);
        binding.officeAnnouncementsEmpty.setVisibility(!state.loading && state.error==null && state.items.isEmpty()?View.VISIBLE:View.GONE);
        binding.officeAnnouncementsRefresh.setEnabled(!state.loading && !state.blocked);
        binding.officeAnnouncementsMore.setVisibility(state.nextId!=null && !state.blocked?View.VISIBLE:View.GONE);binding.officeAnnouncementsMore.setEnabled(!state.loading && state.error==null);
        if(state.blocked || !shown.equals(state.items)){
            shown=state.items;binding.officeAnnouncementRows.removeAllViews();
            for(Announcement announcement:state.items){ItemOfficeAnnouncementBinding row=ItemOfficeAnnouncementBinding.inflate(getLayoutInflater(),binding.officeAnnouncementRows,false);row.officeAnnouncementBody.setText(announcement.body);row.officeAnnouncementTime.setText(InboxFragment.time(announcement.createdAt));binding.officeAnnouncementRows.addView(row.getRoot());}
        }
    }

    private void renderCashierRoute(OfficeViewModel.State state) {
        cashier = null;
        for (Office office : state.offices) {
            if (Office.CASHIERING_ID.equals(office.id)) { cashier = office; break; }
        }
        boolean unavailable = state.error != null || cashier == null;
        binding.cashieringButton.setEnabled(!state.loading && !unavailable);
        binding.cashieringProgress.setVisibility(state.loading ? View.VISIBLE : View.GONE);
        binding.cashieringStatus.setVisibility(state.loading || unavailable ? View.VISIBLE : View.GONE);
        if (state.loading) binding.cashieringStatus.setText(R.string.cashiering_loading);
        else if (unavailable) binding.cashieringStatus.setText(R.string.cashiering_route_unavailable);
        binding.reloadOfficesButton.setVisibility(!state.loading && unavailable ? View.VISIBLE : View.GONE);
    }

    @Override public void onStart(){
        super.onStart();if(announcements==null)return;announcements.checkCurrentAccount();offices.checkCurrentAccount();
        if(!announcements.state().getValue().loading)announcements.refresh();
        RealtimeDataSource.Session session=((GoServeApplication)requireActivity().getApplication()).realtimeSession();if(session==null || subscription!=null)return;subscribedSession=session;
        subscription=session.subscribe(Set.of("announcements"),new RealtimeDataSource.Listener(){public void onInvalidated(RealtimeDataSource.Invalidation e){if(binding!=null && subscribedSession==session && e!=null && e.generation==session.generation())announcements.refresh();}public void onStateChanged(RealtimeDataSource.State state){if(binding!=null && subscribedSession==session && state==RealtimeDataSource.State.CONNECTED)announcements.refresh();}});
    }
    @Override public void onResume(){super.onResume();foregroundRefresh.removeCallbacks(reconcileAnnouncements);if(announcements!=null)foregroundRefresh.postDelayed(reconcileAnnouncements,30_000L);}
    @Override public void onPause(){foregroundRefresh.removeCallbacks(reconcileAnnouncements);super.onPause();}
    @Override public void onStop(){subscribedSession=null;if(subscription!=null)subscription.close();subscription=null;super.onStop();}
    @Override public void onDestroyView() { foregroundRefresh.removeCallbacks(reconcileAnnouncements);super.onDestroyView(); binding = null; cashier = null; shown=List.of(); }
}
