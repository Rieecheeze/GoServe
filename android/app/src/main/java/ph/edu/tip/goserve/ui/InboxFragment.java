package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.RealtimeDataSource;
import ph.edu.tip.goserve.databinding.FragmentInquiryLiveBinding;
import ph.edu.tip.goserve.databinding.ItemInquiryLiveBinding;
import ph.edu.tip.goserve.domain.InquiryData;
import ph.edu.tip.goserve.domain.Office;

public final class InboxFragment extends Fragment {
    private ViewModelProvider.Factory factory;
    private FragmentInquiryLiveBinding binding;
    private InquiryViewModel model;
    private RealtimeDataSource.Subscription subscription;
    private RealtimeDataSource.Session subscribedSession;
    private boolean liveUnavailable,started;
    private List<InquiryData.ThreadRecord> rendered=List.of();
    private List<Office> renderedOffices=List.of();
    private Map<String,Long> renderedCounts=Map.of();
    public InboxFragment() {}
    InboxFragment(ViewModelProvider.Factory factory) { this.factory=factory; }
    public static InboxFragment create() { return new InboxFragment(); }
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved) {
        binding=FragmentInquiryLiveBinding.inflate(inflater,parent,false);return binding.getRoot();
    }
    @Override public void onViewCreated(View view,Bundle saved) {
        binding.inquiryBack.setOnClickListener(v->requireActivity().getOnBackPressedDispatcher().onBackPressed());
        ViewCompat.setAccessibilityHeading(binding.inquiryTitle,true);
        ViewCompat.setAccessibilityHeading(binding.inquirySection,true);
        SharedMotion.install(view);
        if(((MainActivity)requireActivity()).isPreview()) {
            binding.inquirySupport.setText(R.string.inbox_live_preview);binding.inquiryStart.setEnabled(false);binding.inquiryRefresh.setEnabled(false);return;
        }
        model=new ViewModelProvider(this,factory==null?getDefaultViewModelProviderFactory():factory).get(InquiryViewModel.class);
        boolean staff=!model.student();
        binding.inquiryTitle.setText(staff?R.string.inquiry_live_office_title:R.string.inquiry_live_title);
        binding.inquirySection.setText(staff?R.string.inquiry_live_staff_section:R.string.inquiry_live_section);
        binding.inquirySupport.setText(staff?R.string.inquiry_live_staff_support:R.string.inquiry_live_support);
        binding.inquiryEmptyTitle.setText(staff?R.string.inquiry_live_staff_empty:R.string.inquiry_live_empty);
        binding.inquiryEmptyBody.setText(staff?R.string.inquiry_live_staff_empty_body:R.string.inquiry_live_empty_body);
        visible(binding.inquiryStart,!staff);
        binding.inquiryStart.setOnClickListener(v->{model.checkCurrentAccount();InquiryViewModel.State s=model.state().getValue();if(!s.blocked && !s.busy())((MainActivity)requireActivity()).navigate(ThreadFragment.newInquiry(null),true);});
        binding.inquiryRefresh.setOnClickListener(v->model.refresh());binding.inquiryRetry.setOnClickListener(v->model.refresh());binding.inquiryMore.setOnClickListener(v->model.loadMore());
        model.state().observe(getViewLifecycleOwner(),this::render);
        ((GoServeApplication)requireActivity().getApplication()).accountInvalidations().observe(getViewLifecycleOwner(),ignored->model.checkCurrentAccount());
        model.inbox();
    }
    private void render(InquiryViewModel.State s) {
        if(binding==null)return;
        visible(binding.inquiryProgress,s.loading);
        visible(binding.inquiryError,s.error!=null);
        if(s.error!=null)binding.inquiryError.setText(error(s.error));
        binding.inquiryCounts.setText(s.counts==null?"":getString(R.string.inquiry_live_global_counts,s.counts.messages,s.counts.notifications));
        visible(binding.inquiryConnection,liveUnavailable && !s.blocked);
        visible(binding.inquiryRetry,s.error!=null && !s.blocked);
        binding.inquiryRetry.setEnabled(!s.busy());
        binding.inquiryStart.setEnabled(!s.blocked && !s.busy());
        binding.inquiryRefresh.setEnabled(!s.blocked && !s.busy());
        visible(binding.inquiryMore,!s.blocked && s.nextId!=null);
        binding.inquiryMore.setEnabled(!s.busy() && s.error==null);
        visible(binding.inquiryEmpty,!s.blocked && !s.loading && s.error==null && s.threads.isEmpty());
        Map<String,Long> counts=s.counts==null?Map.of():s.counts.threads;
        if(!rendered.equals(s.threads) || !renderedOffices.equals(s.offices) || !renderedCounts.equals(counts)) {
            binding.inquiryRows.removeAllViews();rendered=s.threads;renderedOffices=s.offices;renderedCounts=counts;
            for(InquiryData.ThreadRecord thread:s.threads) {
                ItemInquiryLiveBinding row=ItemInquiryLiveBinding.inflate(getLayoutInflater(),binding.inquiryRows,false);
                String office=thread.officeId;for(Office candidate:s.offices)if(candidate.id.equals(thread.officeId)){office=candidate.name;break;}
                row.inquiryRowTitle.setText("DEP-CCS".equals(thread.officeId)?getString(R.string.inquiry_live_ccs_secretary,office):office);
                row.inquiryRowDetail.setText(counts.containsKey(thread.id)?getString(R.string.inquiry_live_unread,counts.get(thread.id)):getString(R.string.inquiry_live_thread_identity,thread.id));
                row.inquiryRowTime.setText(getString(R.string.inquiry_live_created,time(thread.createdAt)));
                row.getRoot().setOnClickListener(v->{
                    model.checkCurrentAccount();InquiryViewModel.State current=model.state().getValue();
                    if(current.blocked || current.busy())return;
                    for(InquiryData.ThreadRecord item:current.threads)if(item.id.equals(thread.id)){((MainActivity)requireActivity()).navigate(ThreadFragment.create(item.id),true);return;}
                });
                SharedMotion.install(row.getRoot());binding.inquiryRows.addView(row.getRoot());
            }
        }
        for(int i=0;i<binding.inquiryRows.getChildCount();i++)binding.inquiryRows.getChildAt(i).setEnabled(!s.blocked && !s.busy());
    }
    static int error(String code) {
        switch(code){case "STALE_ACCOUNT":return R.string.inquiry_live_account;case "FORBIDDEN":return R.string.inquiry_live_forbidden;case "NOT_FOUND":return R.string.inquiry_live_not_found;case "CONFIGURATION_REQUIRED":return R.string.inquiry_live_configuration;case "VALIDATION_FAILED":return R.string.inquiry_live_validation;default:return R.string.inquiry_live_failed;}
    }
    static String time(String value) {return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM,FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(Instant.parse(value));}
    static void visible(View view,boolean show){view.setVisibility(show?View.VISIBLE:View.GONE);}
    @Override public void onStart(){
        super.onStart();if(model==null || binding==null)return;
        if(started)model.refresh();started=true;
        RealtimeDataSource.Session session=((GoServeApplication)requireActivity().getApplication()).realtimeSession();if(session==null || subscription!=null)return;subscribedSession=session;
        subscription=session.subscribe(Set.of("inquiry_threads","inquiry_messages","inquiry_reads","notifications"),new RealtimeDataSource.Listener(){
            public void onInvalidated(RealtimeDataSource.Invalidation event){if(binding!=null && subscribedSession==session && event!=null && event.generation==session.generation())model.refresh();}
            public void onStateChanged(RealtimeDataSource.State state){if(binding==null || subscribedSession!=session)return;liveUnavailable=state==RealtimeDataSource.State.RECONNECTING || state==RealtimeDataSource.State.DEGRADED || state==RealtimeDataSource.State.FAILED || state==RealtimeDataSource.State.AUTH_REQUIRED;render(model.state().getValue());}
        });
    }
    @Override public void onStop(){subscribedSession=null;if(subscription!=null)subscription.close();subscription=null;super.onStop();}
    @Override public void onDestroyView(){binding=null;rendered=List.of();renderedOffices=List.of();renderedCounts=Map.of();super.onDestroyView();}
}
