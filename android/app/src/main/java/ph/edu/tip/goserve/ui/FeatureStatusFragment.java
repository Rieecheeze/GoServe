package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentListStateBinding;

public final class FeatureStatusFragment extends Fragment {
    private FragmentListStateBinding binding;
    public static FeatureStatusFragment create(String title, String body) { FeatureStatusFragment fragment = new FeatureStatusFragment(); Bundle args = new Bundle(); args.putString("title", title); args.putString("body", body); fragment.setArguments(args); return fragment; }
    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle saved) { binding = FragmentListStateBinding.inflate(inflater, parent, false); return binding.getRoot(); }
    @Override public void onViewCreated(View view, Bundle saved) {
        binding.title.setText(requireArguments().getString("title")); binding.body.setText(requireArguments().getString("body"));
        binding.progress.setVisibility(View.GONE); binding.errorText.setVisibility(View.GONE);
        binding.retryButton.setText(R.string.back_logic); binding.retryButton.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        binding.retryButton.setVisibility(getParentFragmentManager().getBackStackEntryCount() > 0 ? View.VISIBLE : View.GONE);
    }
    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
