package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import ph.edu.tip.goserve.databinding.FragmentAccountCreatedBinding;

public final class AccountCreatedFragment extends AccountFragment {
    private FragmentAccountCreatedBinding binding;
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved) { binding=FragmentAccountCreatedBinding.inflate(inflater,parent,false); return binding.getRoot(); }
    @Override public void onViewCreated(View view,Bundle saved) { super.onViewCreated(view,saved); binding.emailValue.setText(model.draft().email); binding.signInButton.setOnClickListener(v -> model.signInScreen()); }
    @Override public void onDestroyView() { super.onDestroyView(); binding=null; }
}
