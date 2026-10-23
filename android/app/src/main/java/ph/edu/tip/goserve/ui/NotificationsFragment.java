package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.RealtimeDataSource;
import ph.edu.tip.goserve.databinding.FragmentNotificationLiveBinding;
import ph.edu.tip.goserve.databinding.ItemNotificationLiveBinding;
import ph.edu.tip.goserve.domain.InquiryData;

public final class NotificationsFragment extends Fragment {
    private ViewModelProvider.Factory factory;
    private FragmentNotificationLiveBinding binding;
    private NotificationsViewModel model;
    private InquiryData.NotificationPage rendered;
    private final List<ItemNotificationLiveBinding> rows=new ArrayList<>();
    private RealtimeDataSource.Subscription subscription;
    private RealtimeDataSource.Session subscribedSession;
    private boolean liveUnavailable,started;
    public NotificationsFragment() {}
    NotificationsFragment(ViewModelProvider.Factory factory) { this.factory=factory; }
    public static NotificationsFragment create() { return new NotificationsFragment(); }
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved) {
        binding=FragmentNotificationLiveBinding.inflate(inflater,parent,false);return binding.getRoot();
    }
    @Override public void onViewCreated(View view,Bundle saved) {
        binding.notificationBack.setOnClickListener(v->requireActivity().getOnBackPressedDispatcher().onBackPressed());
        ViewCompat.setAccessibilityHeading(binding.notificationTitle,true);ViewCompat.setAccessibilityHeading(binding.notificationSection,true);
        SharedMotion.install(view);
        if(((MainActivity)requireActivity()).isPreview()) {
            binding.notificationCounts.setText(R.string.notification_live_preview);binding.notificationRefresh.setEnabled(false);return;
        }
        model=new ViewModelProvider(this,factory==null?getDefaultViewModelProviderFactory():factory).get(NotificationsViewModel.class);
        binding.notificationRefresh.setOnClickListener(v->model.refresh());binding.notificationRetry.setOnClickListener(v->model.retry());binding.notificationMore.setOnClickListener(v->model.loadMore());
        model.state().observe(getViewLifecycleOwner(),this::render);
        ((GoServeApplication)requireActivity().getApplication()).accountInvalidations().observe(getViewLifecycleOwner(),ignored->model.checkCurrentAccount());
        model.ensureLoaded();
    }
    private void render(NotificationsViewModel.State s) {
        if(binding==null)return;
        InboxFragment.visible(binding.notificationProgress,s.busy());
        InboxFragment.visible(binding.notificationError,s.error!=null);
        if(s.error!=null)binding.notificationError.setText(s.blocked?InboxFragment.error(s.error):R.string.notification_live_failed);
        binding.notificationCounts.setText(s.counts==null?"":getString(R.string.notification_live_total,s.counts.notifications));
        InboxFragment.visible(binding.notificationConnection,liveUnavailable && !s.blocked);
        InboxFragment.visible(binding.notificationRetry,s.error!=null && !s.blocked);binding.notificationRetry.setEnabled(!s.busy());
        binding.notificationRefresh.setEnabled(!s.blocked && !s.busy());
        InboxFragment.visible(binding.notificationEmpty,!s.blocked && !s.loading && s.error==null && s.page!=null && s.page.items.isEmpty());
        InboxFragment.visible(binding.notificationMore,!s.blocked && s.page!=null && s.page.nextId!=null);binding.notificationMore.setEnabled(!s.busy() && s.error==null);
        if(rendered!=s.page) {
            rendered=s.page;rows.clear();binding.notificationRows.removeAllViews();
            if(s.page!=null)for(InquiryData.Notification item:s.page.items) {
                InquiryData.NotificationPage page=s.page;
                ItemNotificationLiveBinding row=ItemNotificationLiveBinding.inflate(getLayoutInflater(),binding.notificationRows,false);
                row.notificationRowTitle.setText(title(item.targetType));
                row.notificationRowTarget.setText(getString(R.string.notification_live_target,item.targetId));
                row.notificationRowTime.setText(getString(item.readAt==null?R.string.notification_live_unread:R.string.notification_live_read,InboxFragment.time(item.createdAt)));
                InboxFragment.visible(row.notificationRowAck,item.readAt==null);
                row.notificationRowOpen.setOnClickListener(v->{if(model.canOpen(page,item.id))open(item);});
                row.notificationRowAck.setOnClickListener(v->model.acknowledge(page,item.id));
                SharedMotion.install(row.getRoot());rows.add(row);binding.notificationRows.addView(row.getRoot());
            }
        }
        for(ItemNotificationLiveBinding row:rows) {row.notificationRowOpen.setEnabled(!s.blocked && !s.busy());row.notificationRowAck.setEnabled(!s.blocked && !s.busy() && s.pageCurrent);}
    }
    private int title(String type) {
        switch(type){case "inquiry":return R.string.notification_live_inquiry;case "appointment":return R.string.notification_live_appointment;default:return R.string.notification_live_request;}
    }
    private void open(InquiryData.Notification item) {
        Fragment target;
        switch(item.targetType) {
            case "inquiry":target=ThreadFragment.create(item.targetId);break;
            case "appointment":target=ConsultationFragment.forAppointment(item.targetId);break;
            case "request":target=RequestDetailFragment.create(item.targetId);break;
            default:return;
        }
        ((MainActivity)requireActivity()).navigate(target,true);
    }
    @Override public void onStart() {
        super.onStart();if(model==null || binding==null)return;
        if(started)model.refresh();started=true;
        RealtimeDataSource.Session session=((GoServeApplication)requireActivity().getApplication()).realtimeSession();if(session==null || subscription!=null)return;subscribedSession=session;
        subscription=session.subscribe(Set.of("notifications"),new RealtimeDataSource.Listener(){
            public void onInvalidated(RealtimeDataSource.Invalidation event){if(binding!=null && subscribedSession==session && event!=null && event.generation==session.generation())model.refresh();}
            public void onStateChanged(RealtimeDataSource.State state){if(binding==null || subscribedSession!=session)return;liveUnavailable=state==RealtimeDataSource.State.RECONNECTING || state==RealtimeDataSource.State.DEGRADED || state==RealtimeDataSource.State.FAILED || state==RealtimeDataSource.State.AUTH_REQUIRED;render(model.state().getValue());}
        });
    }
    @Override public void onStop(){subscribedSession=null;if(subscription!=null)subscription.close();subscription=null;super.onStop();}
    @Override public void onDestroyView(){binding=null;rendered=null;rows.clear();super.onDestroyView();}
}
