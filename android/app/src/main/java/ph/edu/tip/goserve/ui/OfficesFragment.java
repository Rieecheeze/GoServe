package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import ph.edu.tip.goserve.databinding.FragmentOfficesBinding;
import ph.edu.tip.goserve.domain.Office;

public final class OfficesFragment extends Fragment {
    private FragmentOfficesBinding binding;
    private OfficeAdapter adapter;
    private List<Office> offices = Collections.emptyList();
    private String category = "All services";
    public static OfficesFragment create(String category) { OfficesFragment fragment = new OfficesFragment(); Bundle args = new Bundle(); args.putString("category", category); fragment.setArguments(args); return fragment; }
    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) { binding = FragmentOfficesBinding.inflate(inflater, parent, false); return binding.getRoot(); }
    @Override public void onViewCreated(View view, Bundle saved) {
        category = saved != null ? saved.getString("category", "All services") : requireArguments().getString("category", "All services");
        MainActivity host = (MainActivity) requireActivity();
        OfficeViewModel model = new ViewModelProvider(host).get(OfficeViewModel.class);
        adapter = new OfficeAdapter(false, office -> host.navigate(OfficeDetailFragment.create(office), true));
        binding.officesRecycler.setLayoutManager(new LinearLayoutManager(requireContext())); binding.officesRecycler.setAdapter(adapter);
        binding.backButton.setOnClickListener(v -> host.getOnBackPressedDispatcher().onBackPressed());
        binding.allServicesButton.setOnClickListener(v -> { category = "All services"; filter(); });
        binding.documentsButton.setOnClickListener(v -> { category = "Documents"; filter(); });
        binding.paymentsButton.setOnClickListener(v -> { category = "Payments"; filter(); });
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { filter(); }
            public void afterTextChanged(Editable s) { }
        });
        binding.errorText.setOnClickListener(v -> model.reload(host.isPreview()));
        model.state().observe(getViewLifecycleOwner(), state -> {
            offices = state.offices;
            binding.progress.setVisibility(state.loading ? View.VISIBLE : View.GONE);
            binding.errorText.setText(state.error); binding.errorText.setVisibility(state.error == null ? View.GONE : View.VISIBLE);
            filter(); if (state.loading || state.error != null) binding.emptyText.setVisibility(View.GONE);
        });
    }
    private void filter() {
        String query = binding.searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<Office> filtered = new ArrayList<>();
        for (Office office : offices) {
            boolean matchesCategory = category.equals("All services")
                    || (category.equals("Payments") ? Office.CASHIERING_ID.equals(office.id) : category.equals(office.category));
            if (matchesCategory
                    && (office.name + " " + office.category + " " + office.campus).toLowerCase(Locale.ROOT).contains(query)) filtered.add(office);
        }
        adapter.submit(filtered); binding.emptyText.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        binding.allServicesButton.setSelected(category.equals("All services")); binding.documentsButton.setSelected(category.equals("Documents")); binding.paymentsButton.setSelected(category.equals("Payments"));
    }
    @Override public void onSaveInstanceState(Bundle saved) { super.onSaveInstanceState(saved); saved.putString("category", category); }
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
