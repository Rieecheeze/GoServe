package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import ph.edu.tip.goserve.databinding.FragmentGoogleRegistrationBinding;

public final class GoogleRegistrationFragment extends AccountFragment {
    private FragmentGoogleRegistrationBinding binding;
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved){binding=FragmentGoogleRegistrationBinding.inflate(inflater,parent,false);return binding.getRoot();}
    @Override public void onViewCreated(View view,Bundle saved){super.onViewCreated(view,saved);binding.registrationGoogleContinue.setOnClickListener(v->model.google());binding.registrationViewTerms.setOnClickListener(v->model.showTerms(true));binding.registrationBack.setOnClickListener(v->model.signInScreen());model.state().observe(getViewLifecycleOwner(),state->common(view,state));}
    @Override public void onDestroyView(){binding=null;super.onDestroyView();}
}
