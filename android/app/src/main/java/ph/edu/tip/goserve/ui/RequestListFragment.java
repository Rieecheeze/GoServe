package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Set;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.RealtimeDataSource;
import ph.edu.tip.goserve.databinding.FragmentRequestListBinding;
import ph.edu.tip.goserve.databinding.FragmentRequestListLiveBinding;
import ph.edu.tip.goserve.databinding.ItemRequestPreviewBinding;
import ph.edu.tip.goserve.databinding.ItemRequestRecordBinding;
import ph.edu.tip.goserve.databinding.ViewRequestListHeaderBinding;
import ph.edu.tip.goserve.databinding.ViewRequestListFooterBinding;
import ph.edu.tip.goserve.domain.Office;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.RequestStatus;
import ph.edu.tip.goserve.domain.FeeDeclaration;
import ph.edu.tip.goserve.domain.ServiceRequest;

public final class RequestListFragment extends Fragment {
    private ViewModelProvider.Factory factory;
    private FragmentRequestListBinding preview;
    private FragmentRequestListLiveBinding binding;
    private ViewRequestListHeaderBinding header;
    private ViewRequestListFooterBinding footer;
    private RequestListViewModel model;
    private OfficeViewModel offices;
    private Rows rows;
    private RealtimeDataSource.Subscription subscription;
    private RealtimeDataSource.Session subscribedSession;
    private boolean liveUnavailable;
    public RequestListFragment() {}
    RequestListFragment(ViewModelProvider.Factory factory) { this.factory=factory; }
    public static RequestListFragment create() { return new RequestListFragment(); }

    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved) {
        if(((MainActivity)requireActivity()).isPreview()) {
            preview=FragmentRequestListBinding.inflate(inflater,parent,false);
            return preview.getRoot();
        }
        binding=FragmentRequestListLiveBinding.inflate(inflater,parent,false);
        binding.liveRequestList.setLayoutManager(new LinearLayoutManager(requireContext()));
        header=ViewRequestListHeaderBinding.inflate(inflater,binding.liveRequestList,false);
        footer=ViewRequestListFooterBinding.inflate(inflater,binding.liveRequestList,false);
        return binding.getRoot();
    }
    @Override public void onViewCreated(View view,Bundle saved) {
        MainActivity host=(MainActivity)requireActivity();
        if(preview!=null) {
            for(ServiceRequest request:RequestPreviewCatalog.examples(requireContext())) {
                ItemRequestPreviewBinding card=ItemRequestPreviewBinding.inflate(getLayoutInflater(),preview.requestCards,false);
                card.requestCardTitle.setText(request.title); card.requestCardOffice.setText(request.officeName);
                card.requestCardStatus.setText(request.status.wireValue);
                card.requestCardFee.setText(RequestPreviewCatalog.feeLabel(request.feeDeclaration));
                card.getRoot().setOnClickListener(v->host.navigate(RequestDetailFragment.create(request.id),true));
                preview.requestCards.addView(card.getRoot());
            }
            return;
        }
        model=new ViewModelProvider(requireActivity(),factory==null?requireActivity().getDefaultViewModelProviderFactory():factory).get(RequestListViewModel.class);
        offices=new ViewModelProvider(requireActivity()).get(OfficeViewModel.class);
        rows=new Rows();
        binding.liveRequestList.setAdapter(new ConcatAdapter(new FixedView(header.getRoot()),rows,new FixedView(footer.getRoot())));
        boolean office=model.officeId()!=null;
        if(office) {
            int padding=Math.round(20*getResources().getDisplayMetrics().density);
            binding.liveRequestList.setPadding(padding,padding,padding,padding);
            header.liveRequestTitle.setText(R.string.queue_title); header.liveRequestTitle.setTextAppearance(R.style.TextAppearance_GoServe_Heading_Toolbar);
            ViewGroup stack=(ViewGroup)header.getRoot(); stack.removeView(header.liveRequestSupport);
            stack.addView(header.liveRequestSupport,stack.indexOfChild(header.liveRequestSection)+1);
            ViewGroup.MarginLayoutParams section=(ViewGroup.MarginLayoutParams)header.liveRequestSection.getLayoutParams();
            section.topMargin=Math.round(16*getResources().getDisplayMetrics().density); section.bottomMargin=0;
            ViewGroup.MarginLayoutParams support=(ViewGroup.MarginLayoutParams)header.liveRequestSupport.getLayoutParams();
            support.topMargin=Math.round(8*getResources().getDisplayMetrics().density);
            ViewGroup.MarginLayoutParams filters=(ViewGroup.MarginLayoutParams)header.queueFilters.getLayoutParams();
            filters.bottomMargin=Math.round(12*getResources().getDisplayMetrics().density);
        }
        header.liveRequestSupport.setText(office?R.string.request_live_office_support:R.string.request_live_support);
        header.liveRequestSection.setText(office?R.string.request_live_office_section:R.string.request_live_section);
        header.liveRequestEmptyTitle.setText(office?R.string.request_live_empty_office:R.string.request_live_empty_title);
        header.liveRequestEmptyBody.setText(office?R.string.request_live_empty_office_body:R.string.request_live_empty_body);
        visible(header.liveRequestBrowse,!office);
        header.liveRequestBrowse.setOnClickListener(v->{
            model.checkCurrentAccount();
            if(model.state().getValue().mode!=RequestListViewModel.Mode.UNAVAILABLE)host.navigate(OfficesFragment.create("All services"),true);
        });
        header.liveRequestRefresh.setOnClickListener(v->model.refresh());
        header.queueAll.setOnClickListener(v->model.selectFilter(null));
        header.queueSubmitted.setOnClickListener(v->model.selectFilter(RequestStatus.SUBMITTED));
        header.queueProcessing.setOnClickListener(v->model.selectFilter(RequestStatus.PROCESSING));
        header.queueReady.setOnClickListener(v->model.selectFilter(RequestStatus.READY_FOR_PICKUP_DOWNLOAD));
        header.liveRequestRetry.setOnClickListener(v->{
            RequestListViewModel.State state=model.state().getValue();
            if(state.mode==RequestListViewModel.Mode.UNAVAILABLE) {
                if("STALE_ACCOUNT".equals(state.error))host.returnToSignIn(); else host.navigate(new MoreFragment(),false);
            } else model.retry();
        });
        footer.liveRequestMore.setOnClickListener(v->model.loadMore());
        ViewCompat.setAccessibilityHeading(header.liveRequestTitle,true);
        ViewCompat.setAccessibilityHeading(header.liveRequestSection,true);
        SharedMotion.install(header.getRoot()); SharedMotion.install(footer.getRoot());
        binding.liveRequestList.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->resizeEmpty());
        offices.state().observe(getViewLifecycleOwner(),ignored->{ rows.refreshLabels(); render(model.state().getValue()); });
        model.state().observe(getViewLifecycleOwner(),this::render);
        ((GoServeApplication)requireActivity().getApplication()).accountInvalidations().observe(getViewLifecycleOwner(),ignored->model.checkCurrentAccount());
        model.ensureLoaded();
    }
    private void render(RequestListViewModel.State state) {
        if(binding==null)return;
        boolean unavailable=state.mode==RequestListViewModel.Mode.UNAVAILABLE;
        boolean office=model.officeId()!=null; RequestStatus filter=model.filter();
        visible(header.queueFilters,office && !unavailable);
        header.queueAll.setSelected(filter==null); header.queueSubmitted.setSelected(filter==RequestStatus.SUBMITTED);
        header.queueProcessing.setSelected(filter==RequestStatus.PROCESSING); header.queueReady.setSelected(filter==RequestStatus.READY_FOR_PICKUP_DOWNLOAD);
        header.queueAll.setEnabled(!unavailable); header.queueSubmitted.setEnabled(!unavailable);
        header.queueProcessing.setEnabled(!unavailable); header.queueReady.setEnabled(!unavailable);
        if(office)header.liveRequestSection.setText(getString(R.string.queue_office_section,officeLabel(model.officeId())));
        header.liveRequestEmptyTitle.setText(office?filter!=null?R.string.queue_empty_filtered:R.string.request_live_empty_office:R.string.request_live_empty_title);
        header.liveRequestEmptyBody.setText(office?filter!=null?R.string.queue_empty_filtered_body:R.string.request_live_empty_office_body:R.string.request_live_empty_body);
        visible(header.liveRequestBrowse,!office && !unavailable);
        header.liveRequestRefresh.setEnabled(!unavailable && !state.busy());
        visible(header.liveRequestProgress,state.busy()); visible(header.liveRequestLoading,state.busy());
        visible(header.liveRequestError,state.error!=null);
        header.liveRequestError.setText("STALE_ACCOUNT".equals(state.error)?R.string.request_live_account:
            "FORBIDDEN".equals(state.error)?R.string.request_live_forbidden:R.string.request_live_failed);
        visible(header.liveRequestRetry,state.error!=null && !state.busy());
        header.liveRequestRetry.setText(unavailable?"STALE_ACCOUNT".equals(state.error)?R.string.request_sign_in:R.string.more:R.string.request_live_retry);
        visible(header.liveRequestEmpty,!state.busy() && !unavailable && state.error==null && state.items.isEmpty());
        visible(header.liveRequestSection,office || !state.items.isEmpty());
        visible(header.liveRequestConnection,liveUnavailable && !unavailable);
        visible(footer.liveRequestMore,!unavailable && state.nextId!=null && state.nextCreatedAt!=null && state.error==null);
        footer.liveRequestMore.setEnabled(!state.busy());
        rows.update(state.items,!unavailable && !state.busy());
        resizeEmpty();
    }
    private void resizeEmpty() {
        if(binding==null)return;
        RecyclerView list=binding.liveRequestList;
        int minimum=header.liveRequestEmpty.getVisibility()==View.VISIBLE
            ?Math.max(0,list.getHeight()-list.getPaddingTop()-list.getPaddingBottom()):0;
        if(header.getRoot().getMinimumHeight()!=minimum)header.getRoot().setMinimumHeight(minimum);
        int inset=Math.max(0,(list.getWidth()-list.getPaddingLeft()-list.getPaddingRight()-Math.round(310*getResources().getDisplayMetrics().density))/2);
        View empty=header.liveRequestEmpty;
        if(empty.getPaddingLeft()!=inset)empty.setPadding(inset,empty.getPaddingTop(),inset,empty.getPaddingBottom());
    }
    private static void visible(View view,boolean show) { view.setVisibility(show?View.VISIBLE:View.GONE); }
    private String officeLabel(String id) {
        for(Office office:offices.state().getValue().offices)if(id.equals(office.id))return office.name;
        return id;
    }
    static String feeLabel(android.content.Context context,RequestData.Service service) {
        return service.fee==FeeDeclaration.PENDING_REVIEW?context.getString(R.string.request_live_fee_pending):
            service.fee==FeeDeclaration.FREE?context.getString(R.string.request_live_fee_free):
            context.getString(R.string.request_live_fee_required,service.currency,service.amount.toPlainString());
    }
    private void open(RequestData.Record item) {
        model.checkCurrentAccount(); RequestListViewModel.State current=model.state().getValue();
        if(current.mode==RequestListViewModel.Mode.UNAVAILABLE || current.busy())return;
        for(RequestData.Record canonical:current.items)if(canonical.id.equals(item.id) && canonical.version==item.version) {
            ((MainActivity)requireActivity()).navigate(RequestDetailFragment.create(canonical.id),true); return;
        }
    }
    private final class Rows extends RecyclerView.Adapter<Row> {
        private List<RequestData.Record> items=List.of();
        private boolean enabled;
        void update(List<RequestData.Record> values,boolean canOpen) {
            if(items.equals(values) && enabled==canOpen)return;
            items=values; enabled=canOpen; notifyDataSetChanged();
        }
        void refreshLabels() { if(!items.isEmpty())notifyItemRangeChanged(0,items.size()); }
        @Override public Row onCreateViewHolder(ViewGroup parent,int type) {
            ItemRequestRecordBinding card=ItemRequestRecordBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false);
            SharedMotion.install(card.getRoot()); return new Row(card);
        }
        @Override public void onBindViewHolder(Row holder,int position) {
            RequestData.Record value=items.get(position); ItemRequestRecordBinding card=holder.card;
            card.liveRequestName.setText(value.snapshot.name); card.liveRequestOffice.setText(officeLabel(value.officeId));
            card.liveRequestStatus.setText(value.status.wireValue); card.liveRequestFee.setText(feeLabel(requireContext(),value.snapshot));
            card.liveRequestReference.setText(getString(R.string.request_reference,value.reference));
            card.getRoot().setEnabled(enabled);
            card.getRoot().setContentDescription(card.liveRequestName.getText()+"\n"+card.liveRequestOffice.getText()+"\n"+
                card.liveRequestStatus.getText()+"\n"+card.liveRequestFee.getText()+"\n"+card.liveRequestReference.getText());
            card.getRoot().setOnClickListener(v->open(value));
        }
        @Override public int getItemCount() { return items.size(); }
    }
    private static final class Row extends RecyclerView.ViewHolder {
        final ItemRequestRecordBinding card;
        Row(ItemRequestRecordBinding card) { super(card.getRoot()); this.card=card; }
    }
    private static final class FixedView extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final View view;
        FixedView(View view) { this.view=view; }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,int type) { return new RecyclerView.ViewHolder(view) {}; }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder,int position) {}
        @Override public int getItemCount() { return 1; }
    }
    @Override public void onStart() {
        super.onStart(); if(binding==null)return;
        if(!model.state().getValue().busy())model.refresh();
        RealtimeDataSource.Session session=((GoServeApplication)requireActivity().getApplication()).realtimeSession();
        if(session==null || subscription!=null)return; subscribedSession=session;
        subscription=session.subscribe(Set.of("requests"),new RealtimeDataSource.Listener() {
            public void onInvalidated(RealtimeDataSource.Invalidation event) {
                if(binding!=null && subscribedSession==session && event!=null && event.generation==session.generation())model.refresh();
            }
            public void onStateChanged(RealtimeDataSource.State value) {
                if(binding==null || subscribedSession!=session)return;
                liveUnavailable=value==RealtimeDataSource.State.RECONNECTING || value==RealtimeDataSource.State.DEGRADED
                    || value==RealtimeDataSource.State.FAILED || value==RealtimeDataSource.State.AUTH_REQUIRED;
                render(model.state().getValue());
            }
        });
    }
    @Override public void onStop() {
        subscribedSession=null; if(subscription!=null)subscription.close(); subscription=null; super.onStop();
    }
    @Override public void onDestroyView() {
        if(binding!=null)binding.liveRequestList.setAdapter(null);
        rows=null; header=null; footer=null; preview=null; binding=null; super.onDestroyView();
    }
}
