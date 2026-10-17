package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentRequestDetailBinding;
import ph.edu.tip.goserve.databinding.ItemRequestPhaseBinding;
import ph.edu.tip.goserve.domain.FeeDeclaration;
import ph.edu.tip.goserve.domain.PaymentStatus;
import ph.edu.tip.goserve.domain.RequestStatus;
import ph.edu.tip.goserve.domain.RequestWorkflow;
import ph.edu.tip.goserve.domain.ServiceRequest;

public final class RequestDetailFragment extends Fragment {
    private static final String ARG_ID = "request_id";
    private FragmentRequestDetailBinding binding;

    public static RequestDetailFragment create(String previewRequestId) {
        RequestDetailFragment fragment = new RequestDetailFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_ID, previewRequestId);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) {
        binding = FragmentRequestDetailBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(View view, Bundle saved) {
        MainActivity host = (MainActivity) requireActivity();
        binding.requestDetailBack.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        String id = getArguments() == null ? null : getArguments().getString(ARG_ID);
        ServiceRequest request = host.isPreview() ? RequestPreviewCatalog.find(requireContext(), id) : null;
        if (request == null) { showUnavailable(); return; }
        binding.requestDetailName.setText(request.title);
        binding.requestDetailOffice.setText(request.officeName);
        binding.requestDetailReference.setText(getString(R.string.request_reference, request.reference));
        binding.requestDetailStatus.setText(request.status.wireValue);
        for (RequestStatus phase : RequestWorkflow.phases(request.feeDeclaration)) {
            ItemRequestPhaseBinding row = ItemRequestPhaseBinding.inflate(getLayoutInflater(), binding.requestPhases, false);
            RequestWorkflow.PhaseState state = RequestWorkflow.phaseState(request.feeDeclaration, request.status, phase);
            int stateText = state == RequestWorkflow.PhaseState.DONE ? R.string.request_phase_complete
                    : state == RequestWorkflow.PhaseState.CURRENT ? R.string.request_phase_current : R.string.request_phase_upcoming;
            int color = state == RequestWorkflow.PhaseState.DONE ? R.color.gs_request_complete
                    : state == RequestWorkflow.PhaseState.CURRENT ? R.color.gs_text : R.color.gs_secondary;
            row.phaseTitle.setText(phase.wireValue);
            row.phaseState.setText(stateText);
            row.phaseTitle.setTextColor(ContextCompat.getColor(requireContext(), color));
            row.phaseState.setTextColor(ContextCompat.getColor(requireContext(), color));
            row.phaseIcon.setImageResource(state == RequestWorkflow.PhaseState.DONE ? R.drawable.ic_request_done
                    : state == RequestWorkflow.PhaseState.CURRENT ? R.drawable.ic_request_current : R.drawable.ic_request_upcoming);
            row.getRoot().setContentDescription(getString(R.string.request_phase_description, phase.wireValue, getString(stateText)));
            row.getRoot().setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            row.phaseTitle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.phaseState.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            binding.requestPhases.addView(row.getRoot());
        }
        binding.requestFeeDeclaration.setText(RequestPreviewCatalog.feeLabel(request.feeDeclaration));
        binding.requestFeeNote.setText(request.feeDeclaration == FeeDeclaration.REQUIRED ? R.string.request_fee_required_note
                : request.feeDeclaration == FeeDeclaration.FREE ? R.string.request_fee_free_note : R.string.request_fee_pending_note);
        boolean paid = request.feeDeclaration == FeeDeclaration.REQUIRED && request.paymentStatus == PaymentStatus.PAID;
        binding.requestPaymentStatus.setVisibility(paid ? View.VISIBLE : View.GONE);
        binding.requestPaymentNote.setVisibility(paid ? View.VISIBLE : View.GONE);
        if (request.status == RequestStatus.READY_FOR_PICKUP_DOWNLOAD || request.status == RequestStatus.COMPLETED) {
            binding.requestFileAction.setVisibility(View.VISIBLE);
            binding.requestFileAction.setOnClickListener(v -> host.navigate(VerifiedFileFragment.create(request.id), true));
        }
    }

    private void showUnavailable() {
        binding.requestDetailHeading.setText(R.string.request_detail_unavailable);
        binding.requestDetailNotice.setText(R.string.request_detail_unavailable_body);
        binding.requestDetailName.setVisibility(View.GONE);
        binding.requestDetailOffice.setVisibility(View.GONE);
        binding.requestDetailReference.setVisibility(View.GONE);
        binding.requestDetailStatus.setVisibility(View.GONE);
        binding.requestProgressTitle.setVisibility(View.GONE);
        binding.requestFeeTitle.setVisibility(View.GONE);
        binding.requestFeeDeclaration.setVisibility(View.GONE);
        binding.requestFeeNote.setVisibility(View.GONE);
        binding.requestPaymentStatus.setVisibility(View.GONE);
        binding.requestPaymentNote.setVisibility(View.GONE);
    }

    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
