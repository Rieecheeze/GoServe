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

public final class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
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
        binding.consultButton.setOnClickListener(v -> host.feature(R.string.consult_title_logic, R.string.consult_pending_logic, true));
        binding.notificationsButton.setOnClickListener(v -> host.feature(R.string.notifications_title_logic, R.string.notifications_pending_logic, true));
        binding.activeRequestCard.setVisibility(host.isPreview() ? View.VISIBLE : View.GONE);
        if (host.isPreview()) {
            binding.requestTitle.setText(R.string.preview_request_title_logic);
            binding.requestMeta.setText(R.string.preview_request_meta_logic);
            binding.requestStatus.setText(R.string.preview_request_status_logic);
        }
        binding.previewNotice.setVisibility(View.VISIBLE);
        binding.previewNotice.setText(host.isPreview() ? R.string.preview_notice_logic : R.string.foundation_notice_logic);
        View.OnClickListener request = v -> host.navigate(RequestDetailFragment.create(RequestPreviewCatalog.REQUIRED_ID), true);
        binding.activeRequestCard.setOnClickListener(request); binding.requestDetailsButton.setOnClickListener(request);
        model.state().observe(getViewLifecycleOwner(), state -> {
            adapter.submit(state.offices);
            if (state.error != null) { binding.previewNotice.setText(state.error); binding.previewNotice.setOnClickListener(v -> model.reload(host.isPreview())); }
            else { binding.previewNotice.setText(host.isPreview() ? R.string.preview_notice_logic : R.string.foundation_notice_logic); binding.previewNotice.setOnClickListener(null); }
        });
    }
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
