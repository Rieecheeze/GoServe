package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Set;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.RealtimeDataSource;
import ph.edu.tip.goserve.databinding.FragmentRequestFilesBinding;
import ph.edu.tip.goserve.databinding.ViewRequestFilesHeaderBinding;
import ph.edu.tip.goserve.databinding.ViewRequestFilesFooterBinding;
import ph.edu.tip.goserve.databinding.ItemEvidenceFileBinding;
import ph.edu.tip.goserve.domain.DeliveryData;
import ph.edu.tip.goserve.domain.Office;

public final class RequestFilesFragment extends Fragment {
    private ViewModelProvider.Factory factory;
    private FragmentRequestFilesBinding binding;
    private ViewRequestFilesHeaderBinding header;
    private ViewRequestFilesFooterBinding footer;
    private RequestFilesViewModel files;
    private EvidenceViewerViewModel viewer;
    private OfficeViewModel offices;
    private Rows rows;
    private RealtimeDataSource.Subscription subscription;
    private RealtimeDataSource.Session subscribedSession;
    private EvidenceViewerViewModel.Mode lastViewerMode;
    private final ActivityResultLauncher<android.content.Intent> document=registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),result->{
        if(viewer!=null)viewer.returned();
    });
    public RequestFilesFragment() {}
    RequestFilesFragment(ViewModelProvider.Factory factory) { this.factory=factory; }
    public static RequestFilesFragment create(String requestId) {
        RequestFilesFragment fragment=new RequestFilesFragment(); Bundle args=new Bundle(); args.putString("request_id",requestId); fragment.setArguments(args); return fragment;
    }
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved) {
        binding=FragmentRequestFilesBinding.inflate(inflater,parent,false);
        binding.requestFilesList.setLayoutManager(new LinearLayoutManager(requireContext()));
        header=ViewRequestFilesHeaderBinding.inflate(inflater,binding.requestFilesList,false);
        footer=ViewRequestFilesFooterBinding.inflate(inflater,binding.requestFilesList,false);
        return binding.getRoot();
    }
    @Override public void onViewCreated(View view,Bundle saved) {
        ViewModelProvider provider=new ViewModelProvider(this,factory==null?getDefaultViewModelProviderFactory():factory);
        files=provider.get(RequestFilesViewModel.class); viewer=provider.get(EvidenceViewerViewModel.class);
        offices=new ViewModelProvider(requireActivity()).get(OfficeViewModel.class); rows=new Rows();
        binding.requestFilesList.setAdapter(new ConcatAdapter(new Fixed(header.getRoot()),rows,new Fixed(footer.getRoot())));
        binding.requestFilesBack.setOnClickListener(v->back()); binding.requestFilesReturn.setOnClickListener(v->back());
        binding.requestFilesRefresh.setOnClickListener(v->{ viewer.cancel(); files.refresh(); });
        header.requestFilesRetry.setOnClickListener(v->{
            RequestFilesViewModel.State state=files.state().getValue();
            if(state.mode==RequestFilesViewModel.Mode.UNAVAILABLE) {
                MainActivity host=(MainActivity)requireActivity();
                if("STALE_ACCOUNT".equals(state.error))host.returnToSignIn(); else host.navigate(new MoreFragment(),false);
            } else files.retry();
        });
        header.evidenceViewerRetry.setOnClickListener(v->{
            if(reloadRequired(viewer.state().getValue().error)) { viewer.cancel(); files.refresh(); } else viewer.retry();
        });
        header.evidenceViewerCancel.setOnClickListener(v->viewer.cancel());
        footer.requestFilesMore.setOnClickListener(v->files.loadMore());
        ViewCompat.setAccessibilityHeading(header.requestFilesTitle,true);
        SharedMotion.install(view); SharedMotion.install(header.getRoot()); SharedMotion.install(footer.getRoot());
        files.state().observe(getViewLifecycleOwner(),ignored->render());
        viewer.state().observe(getViewLifecycleOwner(),ignored->render());
        offices.state().observe(getViewLifecycleOwner(),ignored->render());
        ((GoServeApplication)requireActivity().getApplication()).accountInvalidations().observe(getViewLifecycleOwner(),ignored->{ files.checkCurrentAccount(); viewer.checkCurrentAccount(); });
        files.ensureRequest(getArguments()==null?null:getArguments().getString("request_id"),DeliveryData.FileKind.EVIDENCE);
    }
    private void back() { viewer.cancel(); requireActivity().getOnBackPressedDispatcher().onBackPressed(); }
    private void render() {
        if(binding==null)return;
        RequestFilesViewModel.State state=files.state().getValue(); EvidenceViewerViewModel.State preview=viewer.state().getValue();
        boolean unavailable=state.mode==RequestFilesViewModel.Mode.UNAVAILABLE;
        if(unavailable && preview.busy()) { viewer.cancel(); preview=viewer.state().getValue(); }
        boolean canOpen=!unavailable && state.request!=null && !state.busy() && state.error==null && !preview.busy() && preview.mode!=EvidenceViewerViewModel.Mode.UNAVAILABLE;
        binding.requestFilesRefresh.setEnabled(!unavailable && !state.busy() && !preview.busy());
        header.requestFilesCaption.setText(state.request==null?"":getString(R.string.files_caption,state.request.reference,officeLabel(state.request.officeId)));
        visible(header.requestFilesLoading,state.busy()); visible(header.requestFilesError,state.error!=null);
        header.requestFilesError.setText("STALE_ACCOUNT".equals(state.error)?R.string.request_live_account:
            "FORBIDDEN".equals(state.error)?R.string.request_live_forbidden:"NOT_FOUND".equals(state.error)?R.string.request_live_not_found:R.string.files_read_failed);
        visible(header.requestFilesRetry,state.error!=null && !state.busy());
        header.requestFilesRetry.setText(unavailable?"STALE_ACCOUNT".equals(state.error)?R.string.request_sign_in:R.string.more:R.string.request_live_retry);
        visible(header.requestFilesEmpty,!state.busy() && !unavailable && state.request!=null && state.error==null && state.items.isEmpty());
        visible(footer.requestFilesMore,state.nextId!=null && state.error==null && !unavailable); footer.requestFilesMore.setEnabled(!state.busy() && !preview.busy());
        rows.update(state.items,canOpen);
        boolean downloading=preview.mode==EvidenceViewerViewModel.Mode.DOWNLOADING || preview.mode==EvidenceViewerViewModel.Mode.READY;
        visible(header.evidenceViewerLoading,downloading);
        visible(header.evidenceViewerStatus,preview.busy() || preview.mode==EvidenceViewerViewModel.Mode.ERROR);
        header.evidenceViewerStatus.setText(preview.mode==EvidenceViewerViewModel.Mode.OPEN?R.string.viewer_open:downloading?R.string.viewer_downloading:
            "NO_VIEWER".equals(preview.error)?R.string.viewer_no_app:"DEVICE_STORAGE_LIMIT".equals(preview.error)?R.string.viewer_storage:
            reloadRequired(preview.error)?R.string.viewer_invalid:R.string.viewer_failed);
        visible(header.evidenceViewerRetry,preview.mode==EvidenceViewerViewModel.Mode.ERROR && !unavailable);
        header.evidenceViewerRetry.setText(reloadRequired(preview.error)?R.string.viewer_reload:R.string.viewer_retry);
        visible(header.evidenceViewerCancel,preview.mode==EvidenceViewerViewModel.Mode.DOWNLOADING);
        if(lastViewerMode!=preview.mode && (downloading || preview.mode==EvidenceViewerViewModel.Mode.ERROR))binding.requestFilesList.scrollToPosition(0);
        lastViewerMode=preview.mode;
        if(preview.mode==EvidenceViewerViewModel.Mode.READY) {
            android.content.Intent intent=viewer.takeIntent();
            if(intent!=null)try { document.launch(intent); }
            catch(android.content.ActivityNotFoundException | SecurityException unavailableViewer) { viewer.viewerUnavailable(); }
        }
    }
    private static boolean reloadRequired(String error) { return "UPLOAD_INVALID".equals(error) || "NOT_FOUND".equals(error) || "STALE_VERSION".equals(error); }
    private String officeLabel(String id) { for(Office office:offices.state().getValue().offices)if(id.equals(office.id))return office.name; return id; }
    private void open(DeliveryData.File file) {
        files.checkCurrentAccount(); viewer.checkCurrentAccount(); RequestFilesViewModel.State state=files.state().getValue();
        if(state.mode==RequestFilesViewModel.Mode.UNAVAILABLE || state.busy() || state.error!=null)return;
        for(DeliveryData.File current:state.items)if(current.id.equals(file.id) && current.version==file.version) { viewer.open(current); return; }
    }
    @Override public void onStart() {
        super.onStart(); ((MainActivity)requireActivity()).setRoleNavigationVisible(false);
        files.checkCurrentAccount(); viewer.checkCurrentAccount(); if(!files.state().getValue().busy())files.refresh();
        RealtimeDataSource.Session session=((GoServeApplication)requireActivity().getApplication()).realtimeSession();
        if(session==null || subscription!=null)return; subscribedSession=session;
        subscription=session.subscribe(Set.of("requests","payments","request_files"),new RealtimeDataSource.Listener() {
            public void onInvalidated(RealtimeDataSource.Invalidation event) {
                if(binding!=null && subscribedSession==session && event!=null && event.generation==session.generation())files.refresh();
            }
            public void onStateChanged(RealtimeDataSource.State state) { if(binding!=null && subscribedSession==session) { files.checkCurrentAccount(); viewer.checkCurrentAccount(); } }
        });
    }
    @Override public void onStop() {
        subscribedSession=null; if(subscription!=null)subscription.close(); subscription=null;
        if(!requireActivity().isChangingConfigurations() && viewer.state().getValue().mode!=EvidenceViewerViewModel.Mode.OPEN)viewer.cancel();
        ((MainActivity)requireActivity()).setRoleNavigationVisible(true); super.onStop();
    }
    @Override public void onDestroyView() { binding=null; header=null; footer=null; rows=null; lastViewerMode=null; super.onDestroyView(); }
    private static void visible(View view,boolean show) { view.setVisibility(show?View.VISIBLE:View.GONE); }
    private final class Rows extends RecyclerView.Adapter<Row> {
        private List<DeliveryData.File> items=List.of(); private boolean enabled;
        void update(List<DeliveryData.File> values,boolean enabled) {
            if(items.equals(values) && this.enabled==enabled)return;
            items=values; this.enabled=enabled; notifyDataSetChanged();
        }
        @Override public Row onCreateViewHolder(ViewGroup parent,int type) { ItemEvidenceFileBinding card=ItemEvidenceFileBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false); SharedMotion.install(card.getRoot()); return new Row(card); }
        @Override public void onBindViewHolder(Row holder,int position) {
            DeliveryData.File file=items.get(position); String title=file.requirement==null?getString("application/pdf".equals(file.mimeType)?R.string.files_pdf:R.string.files_image):getString(R.string.files_requirement,file.requirement);
            String metadata=getString(R.string.files_metadata,file.mimeType,android.text.format.Formatter.formatShortFileSize(requireContext(),file.sizeBytes),file.version);
            holder.card.evidenceFileTitle.setText(title); holder.card.evidenceFileMeta.setText(metadata); holder.card.evidenceFileKey.setText(file.id);
            holder.card.getRoot().setEnabled(enabled); holder.card.getRoot().setContentDescription(getString(R.string.files_open_description,title,metadata,file.id));
            holder.card.getRoot().setOnClickListener(v->open(file));
        }
        @Override public int getItemCount() { return items.size(); }
    }
    private static final class Row extends RecyclerView.ViewHolder { final ItemEvidenceFileBinding card; Row(ItemEvidenceFileBinding card) { super(card.getRoot()); this.card=card; } }
    private static final class Fixed extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final View view; Fixed(View view) { this.view=view; }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,int type) { return new RecyclerView.ViewHolder(view) {}; }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder,int position) {} @Override public int getItemCount() { return 1; }
    }
}
