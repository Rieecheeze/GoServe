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
import ph.edu.tip.goserve.databinding.FragmentCashHistoryBinding;
import ph.edu.tip.goserve.databinding.ItemCashHistoryBinding;
import ph.edu.tip.goserve.databinding.ViewCashHistoryHeaderBinding;
import ph.edu.tip.goserve.databinding.ViewCashHistoryFooterBinding;
import ph.edu.tip.goserve.domain.PaymentData;

public final class CashHistoryFragment extends Fragment {
    private ViewModelProvider.Factory factory;
    private FragmentCashHistoryBinding binding;
    private ViewCashHistoryHeaderBinding header;
    private ViewCashHistoryFooterBinding footer;
    private CashHistoryViewModel model;
    private Rows rows;
    private RealtimeDataSource.Subscription subscription;
    private RealtimeDataSource.Session subscribedSession;
    private boolean liveUnavailable;
    public CashHistoryFragment() {}
    CashHistoryFragment(ViewModelProvider.Factory factory) { this.factory=factory; }
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved) {
        binding=FragmentCashHistoryBinding.inflate(inflater,parent,false);
        binding.historyList.setLayoutManager(new LinearLayoutManager(requireContext()));
        header=ViewCashHistoryHeaderBinding.inflate(inflater,binding.historyList,false);
        footer=ViewCashHistoryFooterBinding.inflate(inflater,binding.historyList,false);
        return binding.getRoot();
    }
    @Override public void onViewCreated(View view,Bundle saved) {
        model=new ViewModelProvider(requireActivity(),factory==null?requireActivity().getDefaultViewModelProviderFactory():factory).get(CashHistoryViewModel.class);
        rows=new Rows();
        binding.historyList.setAdapter(new ConcatAdapter(new FixedView(header.getRoot()),rows,new FixedView(footer.getRoot())));
        ViewCompat.setAccessibilityHeading(header.historyHeading,true);
        binding.historyRefresh.setOnClickListener(v->model.refresh());
        header.historyRetry.setOnClickListener(v->{
            CashHistoryViewModel.State state=model.state().getValue();
            if(state.mode==CashHistoryViewModel.Mode.UNAVAILABLE) {
                MainActivity host=(MainActivity)requireActivity();
                if("STALE_ACCOUNT".equals(state.error))host.returnToSignIn(); else host.navigate(new MoreFragment(),false);
            } else model.retry();
        });
        footer.historyMore.setOnClickListener(v->model.loadMore());
        SharedMotion.install(view);
        SharedMotion.install(header.getRoot());
        SharedMotion.install(footer.getRoot());
        model.state().observe(getViewLifecycleOwner(),this::render);
        ((GoServeApplication)requireActivity().getApplication()).accountInvalidations().observe(getViewLifecycleOwner(),ignored->model.checkCurrentAccount());
        model.ensureLoaded();
    }
    private void render(CashHistoryViewModel.State state) {
        if(binding==null)return;
        boolean unavailable=state.mode==CashHistoryViewModel.Mode.UNAVAILABLE;
        binding.historyRefresh.setEnabled(!unavailable && !state.busy());
        visible(header.historyProgress,state.busy());
        visible(header.historyError,state.error!=null);
        header.historyError.setText("STALE_ACCOUNT".equals(state.error)?R.string.cash_stale_account_error:
            "FORBIDDEN".equals(state.error)?R.string.cash_forbidden_error:
            "CONFIGURATION_REQUIRED".equals(state.error)?R.string.cash_configuration_error:R.string.cash_history_failed);
        visible(header.historyRetry,state.error!=null && !state.busy());
        header.historyRetry.setText(unavailable?"STALE_ACCOUNT".equals(state.error)?R.string.request_sign_in:R.string.more:R.string.cash_history_retry);
        visible(header.historyEmpty,!state.busy() && !unavailable && state.error==null && state.items.isEmpty());
        visible(header.historyConnection,liveUnavailable);
        visible(footer.historyMore,!unavailable && state.nextId!=null && state.error==null);
        footer.historyMore.setEnabled(!state.busy());
        rows.update(state.items,!unavailable && !state.busy());
    }
    private static void visible(View view,boolean show) { view.setVisibility(show?View.VISIBLE:View.GONE); }
    private void open(PaymentData.Record item) {
        model.checkCurrentAccount();
        CashHistoryViewModel.State current=model.state().getValue();
        if(current.mode==CashHistoryViewModel.Mode.UNAVAILABLE || current.busy())return;
        for(PaymentData.Record canonical:current.items)if(canonical.id.equals(item.id) && canonical.version==item.version) {
            ((MainActivity)requireActivity()).navigate(TellerPaymentFragment.forPayment(canonical.id),true);
            return;
        }
    }
    private final class Rows extends RecyclerView.Adapter<Row> {
        private List<PaymentData.Record> items=List.of();
        private boolean enabled;
        void update(List<PaymentData.Record> values,boolean canOpen) {
            if(items.equals(values) && enabled==canOpen)return;
            items=values; enabled=canOpen; notifyDataSetChanged();
        }
        @Override public Row onCreateViewHolder(ViewGroup parent,int viewType) {
            ItemCashHistoryBinding card=ItemCashHistoryBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false);
            SharedMotion.install(card.getRoot()); return new Row(card);
        }
        @Override public void onBindViewHolder(Row holder,int position) {
            PaymentData.Record value=items.get(position); ItemCashHistoryBinding card=holder.card;
            card.historyPaymentId.setText(value.id);
            card.historyPaid.setText(getString(R.string.cash_history_paid,value.currency,value.amount.toPlainString()));
            card.historyActor.setText(getString(R.string.cash_history_actor,value.confirmedBy));
            card.historyTime.setText(getString(R.string.cash_history_time,value.confirmedAt));
            card.getRoot().setEnabled(enabled);
            card.getRoot().setContentDescription(card.historyPaymentId.getText()+"\n"+card.historyPaid.getText()+"\n"+card.historyActor.getText()+"\n"+card.historyTime.getText());
            card.getRoot().setOnClickListener(v->open(value));
        }
        @Override public int getItemCount() { return items.size(); }
    }
    private static final class Row extends RecyclerView.ViewHolder {
        final ItemCashHistoryBinding card;
        Row(ItemCashHistoryBinding card) { super(card.getRoot()); this.card=card; }
    }
    private static final class FixedView extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final View view;
        FixedView(View view) { this.view=view; }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,int type) { return new RecyclerView.ViewHolder(view) {}; }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder,int position) {}
        @Override public int getItemCount() { return 1; }
    }
    @Override public void onStart() {
        super.onStart();
        if(!model.state().getValue().busy())model.refresh();
        RealtimeDataSource.Session session=((GoServeApplication)requireActivity().getApplication()).realtimeSession();
        if(session==null || subscription!=null)return; subscribedSession=session;
        subscription=session.subscribe(Set.of("payments"),new RealtimeDataSource.Listener() {
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
        if(binding!=null)binding.historyList.setAdapter(null);
        rows=null; header=null; footer=null; binding=null; super.onDestroyView();
    }
}
