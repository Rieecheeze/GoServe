package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentRequestListBinding;
import ph.edu.tip.goserve.databinding.ItemRequestPreviewBinding;
import ph.edu.tip.goserve.domain.ServiceRequest;

public final class RequestListFragment extends Fragment {
    private FragmentRequestListBinding binding;
    public static RequestListFragment create() { return new RequestListFragment(); }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) {
        binding = FragmentRequestListBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(View view, Bundle saved) {
        MainActivity host = (MainActivity) requireActivity();
        if (!host.isPreview()) {
            boolean signedOut = host.profile() == null || !host.profile().consentAccepted;
            binding.requestListSection.setText(signedOut ? R.string.request_preview_signed_out_title : R.string.request_preview_unavailable_title);
            binding.requestListNotice.setText(signedOut ? R.string.request_preview_signed_out_body : R.string.request_preview_unavailable_body);
            binding.requestListAction.setVisibility(View.VISIBLE);
            binding.requestListAction.setText(signedOut ? R.string.request_sign_in : R.string.request_browse_offices);
            binding.requestListAction.setOnClickListener(v -> {
                if (signedOut) host.returnToSignIn();
                else host.navigate(OfficesFragment.create("All services"), true);
            });
            return;
        }
        for (ServiceRequest request : RequestPreviewCatalog.examples(requireContext())) {
            ItemRequestPreviewBinding card = ItemRequestPreviewBinding.inflate(getLayoutInflater(), binding.requestCards, false);
            card.requestCardTitle.setText(request.title);
            card.requestCardOffice.setText(request.officeName);
            card.requestCardStatus.setText(request.status.wireValue);
            card.requestCardFee.setText(RequestPreviewCatalog.feeLabel(request.feeDeclaration));
            card.getRoot().setOnClickListener(v -> host.navigate(RequestDetailFragment.create(request.id), true));
            binding.requestCards.addView(card.getRoot());
        }
    }

    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
