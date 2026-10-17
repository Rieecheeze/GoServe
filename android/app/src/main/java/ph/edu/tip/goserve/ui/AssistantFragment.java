package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentAssistantBinding;

public final class AssistantFragment extends Fragment {
    private FragmentAssistantBinding binding;

    public static AssistantFragment create(String panel) {
        AssistantFragment fragment = new AssistantFragment();
        Bundle args = new Bundle();
        args.putString("panel", panel);
        fragment.setArguments(args);
        return fragment;
    }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
        binding = FragmentAssistantBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(View view, Bundle state) {
        MainActivity host = (MainActivity) requireActivity();
        String panel = getArguments() == null ? "chat" : getArguments().getString("panel", "chat");
        boolean consent = "consent".equals(panel);
        boolean attachment = "attachment".equals(panel);
        binding.chatPanel.setVisibility(consent || attachment ? View.GONE : View.VISIBLE);
        binding.consentPanel.setVisibility(consent ? View.VISIBLE : View.GONE);
        binding.attachmentPanel.setVisibility(attachment ? View.VISIBLE : View.GONE);
        binding.backButton.setVisibility(consent || attachment ? View.VISIBLE : View.GONE);
        binding.panelTitle.setText(consent ? R.string.seryo_privacy_title : attachment ? R.string.seryo_attachment_title : R.string.seryo_name);
        binding.backButton.setOnClickListener(v -> host.getOnBackPressedDispatcher().onBackPressed());
        binding.privacyButton.setOnClickListener(v -> host.navigate(create("consent"), true));
        binding.attachmentButton.setOnClickListener(v -> host.navigate(create("attachment"), true));
        binding.officeButton.setOnClickListener(v -> host.navigate(OfficesFragment.create("All services"), true));
        binding.returnToChatButton.setOnClickListener(v -> host.getOnBackPressedDispatcher().onBackPressed());
        // eGovAI transport stays disabled until its API contract and Seryo procedures are supplied.
        binding.sendButton.setEnabled(false);
        binding.chooseFileButton.setEnabled(false);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
