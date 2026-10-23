package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentHomeBinding;
import ph.edu.tip.goserve.domain.ServiceRequest;

public final class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private RequestListViewModel requests;
    private NotificationsViewModel notifications;
    private ph.edu.tip.goserve.data.RealtimeDataSource.Subscription subscription;
    private ph.edu.tip.goserve.data.RealtimeDataSource.Session subscribedSession;
    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) { binding = FragmentHomeBinding.inflate(inflater, parent, false); return binding.getRoot(); }
    @Override public void onViewCreated(View view, Bundle saved) {
        MainActivity host = (MainActivity) requireActivity();
        OfficeViewModel model = new ViewModelProvider(host).get(OfficeViewModel.class);
        int columns = getResources().getConfiguration().screenWidthDp < 360 || getResources().getConfiguration().fontScale > 1.3f ? 1 : 2;
        binding.officesRecycler.setLayoutManager(new GridLayoutManager(requireContext(), columns));
        int halfGap = Math.round(7 * getResources().getDisplayMetrics().density);
        binding.officesRecycler.addItemDecoration(new androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
            @Override public void getItemOffsets(android.graphics.Rect out, View item, androidx.recyclerview.widget.RecyclerView parent, androidx.recyclerview.widget.RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(item);
                out.set(columns == 2 && position % 2 == 1 ? halfGap : 0, 0,
                        columns == 2 && position % 2 == 0 ? halfGap : 0, halfGap * 2);
            }
        });
        binding.officesRecycler.setNestedScrollingEnabled(false);
        OfficeAdapter adapter = new OfficeAdapter(true, office -> host.navigate(OfficeDetailFragment.create(office), true));
        binding.officesRecycler.setAdapter(adapter);
        View.OnClickListener directory = v -> host.navigate(OfficesFragment.create("All services"), true);
        binding.searchButton.setOnClickListener(directory); binding.seeAllButton.setOnClickListener(directory); binding.allServicesButton.setOnClickListener(directory);
        binding.documentsButton.setOnClickListener(v -> host.navigate(OfficesFragment.create("Documents"), true));
        binding.paymentsButton.setOnClickListener(v -> host.navigate(OfficesFragment.create("Payments"), true));
        binding.consultButton.setOnClickListener(v -> host.navigate(new ConsultationFragment(),true));
        binding.notificationsButton.setOnClickListener(v -> host.navigate(NotificationsFragment.create(),true));
        binding.activeRequestCard.setVisibility(host.isPreview() ? View.VISIBLE : View.GONE);
        if (host.isPreview()) {
            ServiceRequest preview = RequestPreviewCatalog.find(requireContext(), RequestPreviewCatalog.REQUIRED_ID);
            binding.requestTitle.setText(preview.title);
            binding.requestMeta.setText(preview.officeName + " · " + preview.reference);
            binding.requestStatus.setText(R.string.preview_request_status_logic);
        }
        binding.previewNotice.setVisibility(host.isPreview()?View.VISIBLE:View.GONE);
        binding.previewNotice.setText(host.isPreview() ? R.string.preview_notice_logic : R.string.foundation_notice_logic);
        View.OnClickListener request = v -> host.navigate(RequestDetailFragment.create(RequestPreviewCatalog.REQUIRED_ID), true);
        binding.activeRequestCard.setOnClickListener(request); binding.requestDetailsButton.setOnClickListener(request);
        model.state().observe(getViewLifecycleOwner(), state -> {
            adapter.submit(state.offices);
            if (state.error != null) { binding.previewNotice.setVisibility(View.VISIBLE); binding.previewNotice.setText(state.error); binding.previewNotice.setOnClickListener(v -> model.reload(host.isPreview())); }
            else { binding.previewNotice.setVisibility(host.isPreview()?View.VISIBLE:View.GONE); binding.previewNotice.setText(host.isPreview() ? R.string.preview_notice_logic : R.string.foundation_notice_logic); binding.previewNotice.setOnClickListener(null); }
        });
        if(!host.isPreview()) {
            notifications=new ViewModelProvider(host).get(NotificationsViewModel.class);
            notifications.state().observe(getViewLifecycleOwner(),s->{
                if(s.counts!=null)binding.notificationsButton.setContentDescription(getString(R.string.home_unread_notifications,s.counts.notifications));
            });notifications.refresh();
            requests=new ViewModelProvider(this).get(RequestListViewModel.class);
            requests.state().observe(getViewLifecycleOwner(),s->{
                ph.edu.tip.goserve.domain.RequestData.Record selected=null;
                if(s.mode==RequestListViewModel.Mode.CONTENT && s.error==null)for(ph.edu.tip.goserve.domain.RequestData.Record r:s.items)
                    if(r.status!=ph.edu.tip.goserve.domain.RequestStatus.COMPLETED && r.status!=ph.edu.tip.goserve.domain.RequestStatus.CANCELLED && r.status!=ph.edu.tip.goserve.domain.RequestStatus.REJECTED){selected=r;break;}
                binding.activeRequestCard.setVisibility(selected==null?View.GONE:View.VISIBLE);
                if(selected!=null){
                    ph.edu.tip.goserve.domain.RequestData.Record current=selected;
                    binding.requestTitle.setText(current.snapshot.name);binding.requestMeta.setText(current.officeId+" · "+current.reference);binding.requestStatus.setText(current.status.wireValue);
                    View.OnClickListener open=v->host.navigate(RequestDetailFragment.create(current.id),true);binding.activeRequestCard.setOnClickListener(open);binding.requestDetailsButton.setOnClickListener(open);
                }
            });requests.ensureLoaded();
        }
    }
    @Override public void onStart() {
        super.onStart(); if(requests==null)return;
        if(!requests.state().getValue().busy())requests.refresh();
        if(!notifications.state().getValue().busy())notifications.refresh();
        ph.edu.tip.goserve.data.RealtimeDataSource.Session session=((ph.edu.tip.goserve.GoServeApplication)requireActivity().getApplication()).realtimeSession();
        if(session==null || subscription!=null)return;subscribedSession=session;
        subscription=session.subscribe(java.util.Set.of("requests","payments","notifications","inquiry_messages"),new ph.edu.tip.goserve.data.RealtimeDataSource.Listener(){
            public void onInvalidated(ph.edu.tip.goserve.data.RealtimeDataSource.Invalidation event){if(binding!=null && subscribedSession==session && event!=null && event.generation==session.generation()){requests.refresh();notifications.refresh();}}
            public void onStateChanged(ph.edu.tip.goserve.data.RealtimeDataSource.State state){}
        });
    }
    @Override public void onStop(){subscribedSession=null;if(subscription!=null)subscription.close();subscription=null;super.onStop();}
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
