package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentOfficeDetailBinding;
import ph.edu.tip.goserve.domain.Office;

public final class OfficeDetailFragment extends Fragment {
    private FragmentOfficeDetailBinding binding;
    private Office cashier;
    public static OfficeDetailFragment create(Office office) {
        OfficeDetailFragment fragment = new OfficeDetailFragment(); Bundle args = new Bundle();
        args.putString("id", office.id); args.putString("name", office.name); args.putString("status", office.status.wireValue); args.putString("note", office.note);
        args.putString("campus", office.campus); args.putBoolean("demo", office.demo); fragment.setArguments(args); return fragment;
    }
    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) { binding = FragmentOfficeDetailBinding.inflate(inflater, parent, false); return binding.getRoot(); }
    @Override public void onViewCreated(View view, Bundle saved) {
        Bundle args = requireArguments();
        binding.officeName.setText(args.getString("name")); binding.officeStatus.setText(args.getString("status"));
        binding.officeNote.setText(args.getString("note")); binding.campusLabel.setText(args.getString("campus"));
        boolean cashierPage = Office.CASHIERING_ID.equals(args.getString("id"));
        binding.servicesTitle.setText(cashierPage ? R.string.cashiering_section_title : R.string.services);
        binding.servicesText.setText(cashierPage ? R.string.cashiering_services_pending : R.string.cashiering_office_services_pending);
        binding.errorText.setVisibility(args.getBoolean("demo") ? View.VISIBLE : View.GONE); binding.errorText.setText(R.string.demo_office_logic);
        binding.backButton.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        if (cashierPage) {
            binding.paymentsTitle.setVisibility(View.GONE);
            binding.cashieringCaption.setVisibility(View.GONE);
            binding.cashieringButton.setVisibility(View.GONE);
            binding.cashieringProgress.setVisibility(View.GONE);
            binding.cashieringStatus.setVisibility(View.GONE);
            binding.reloadOfficesButton.setVisibility(View.GONE);
            return;
        }
        MainActivity host = (MainActivity) requireActivity();
        OfficeViewModel model = new ViewModelProvider(host).get(OfficeViewModel.class);
        binding.cashieringButton.setOnClickListener(v -> {
            if (cashier != null && binding.cashieringButton.isEnabled()) host.navigate(create(cashier), true);
        });
        binding.reloadOfficesButton.setOnClickListener(v -> model.reload(host.isPreview()));
        model.state().observe(getViewLifecycleOwner(), this::renderCashierRoute);
        model.ensureLoaded(host.isPreview());
    }

    private void renderCashierRoute(OfficeViewModel.State state) {
        cashier = null;
        for (Office office : state.offices) {
            if (Office.CASHIERING_ID.equals(office.id)) { cashier = office; break; }
        }
        boolean unavailable = state.error != null || cashier == null;
        binding.cashieringButton.setEnabled(!state.loading && !unavailable);
        binding.cashieringProgress.setVisibility(state.loading ? View.VISIBLE : View.GONE);
        binding.cashieringStatus.setVisibility(state.loading || unavailable ? View.VISIBLE : View.GONE);
        if (state.loading) binding.cashieringStatus.setText(R.string.cashiering_loading);
        else if (unavailable) binding.cashieringStatus.setText(R.string.cashiering_route_unavailable);
        binding.reloadOfficesButton.setVisibility(!state.loading && unavailable ? View.VISIBLE : View.GONE);
    }

    @Override public void onDestroyView() { super.onDestroyView(); binding = null; cashier = null; }
}
