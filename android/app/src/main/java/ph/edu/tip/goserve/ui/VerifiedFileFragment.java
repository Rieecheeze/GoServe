package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentVerifiedFileBinding;
import ph.edu.tip.goserve.domain.RequestStatus;
import ph.edu.tip.goserve.domain.ServiceRequest;

public final class VerifiedFileFragment extends Fragment {
    private static final String ARG_ID = "request_id";
    private static final String STATE_EXPLANATION = "download_explanation_visible";
    private FragmentVerifiedFileBinding binding;

    public static VerifiedFileFragment create(String previewRequestId) {
        VerifiedFileFragment fragment = new VerifiedFileFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_ID, previewRequestId);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) {
        binding = FragmentVerifiedFileBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(View view, Bundle saved) {
        MainActivity host = (MainActivity) requireActivity();
        binding.verifiedFileBack.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        String id = getArguments() == null ? null : getArguments().getString(ARG_ID);
        ServiceRequest request = host.isPreview() ? RequestPreviewCatalog.find(requireContext(), id) : null;
        if (request == null || request.status != RequestStatus.READY_FOR_PICKUP_DOWNLOAD && request.status != RequestStatus.COMPLETED) {
            binding.verifiedFileHeading.setText(R.string.request_detail_unavailable);
            binding.verifiedFileNotice.setText(R.string.request_detail_unavailable_body);
            binding.verifiedFileCheck.setVisibility(View.GONE);
            binding.verifiedFileStatus.setVisibility(View.GONE);
            binding.verifiedFileName.setVisibility(View.GONE);
            binding.verifiedFileBody.setVisibility(View.GONE);
            binding.verifiedFileDownloadInfo.setVisibility(View.GONE);
            return;
        }
        binding.verifiedFileDownloadInfo.setOnClickListener(v -> {
            binding.verifiedFileDownloadExplanation.setVisibility(View.VISIBLE);
            binding.verifiedFileDownloadExplanation.announceForAccessibility(getString(R.string.request_verified_download_explanation));
        });
        if (saved != null && saved.getBoolean(STATE_EXPLANATION)) binding.verifiedFileDownloadExplanation.setVisibility(View.VISIBLE);
    }

    @Override public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (binding != null) state.putBoolean(STATE_EXPLANATION, binding.verifiedFileDownloadExplanation.getVisibility() == View.VISIBLE);
    }

    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
