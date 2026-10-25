package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import java.util.ArrayList;
import java.util.List;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentRecoverySecurityBinding;
import ph.edu.tip.goserve.domain.RecoveryData;

public final class RecoverySecurityFragment extends Fragment {
    private FragmentRecoverySecurityBinding binding;private RecoverySecurityViewModel model;
    private ViewModelProvider.Factory factory;private boolean rendering,completed;private RecoveryData.Codes displayedCodes;
    private final List<Runnable> detach=new ArrayList<>();
    public RecoverySecurityFragment(){}
    RecoverySecurityFragment(ViewModelProvider.Factory factory){this.factory=factory;}
    public static RecoverySecurityFragment create(RecoverySecurityViewModel.Kind kind){RecoverySecurityFragment fragment=new RecoverySecurityFragment();Bundle args=new Bundle();args.putString("kind",kind.name());fragment.setArguments(args);return fragment;}
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved){binding=FragmentRecoverySecurityBinding.inflate(inflater,parent,false);return binding.getRoot();}
    @Override public void onViewCreated(View view,Bundle saved){
        RecoverySecurityViewModel.Kind kind=RecoverySecurityViewModel.Kind.valueOf(requireArguments().getString("kind"));
        model=new ViewModelProvider(this,factory==null?getDefaultViewModelProviderFactory():factory).get(RecoverySecurityViewModel.class);
        boolean preview=requireActivity() instanceof MainActivity&&((MainActivity)requireActivity()).isPreview();
        for(EditText input:inputs()){
            input.setSaveEnabled(false);input.setSaveFromParentEnabled(false);
            TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence value,int start,int count,int after){}public void afterTextChanged(Editable value){}public void onTextChanged(CharSequence value,int start,int before,int count){if(!rendering){model.editEmail(binding.recoveryEmail.getText().toString());model.editProof(binding.recoveryProofInput.getText().toString());model.editPasswords(binding.recoveryNewPassword.getText().toString(),binding.recoveryConfirmPassword.getText().toString());}}};input.addTextChangedListener(watcher);detach.add(()->input.removeTextChangedListener(watcher));
        }
        binding.recoveryPrimary.setOnClickListener(v->{if(model.state().getValue().page==RecoverySecurityViewModel.Page.COMPLETE&&requireActivity() instanceof AuthActivity)new ViewModelProvider(requireActivity()).get(AuthViewModel.class).refreshAccountRoute();else model.primary();});
        binding.recoveryBack.setOnClickListener(v->leave());binding.recoveryReauthenticate.setOnClickListener(v->reauthenticate());
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),new OnBackPressedCallback(true){@Override public void handleOnBackPressed(){leave();}});
        model.start(kind,preview);model.state().observe(getViewLifecycleOwner(),this::render);
        ((GoServeApplication)requireActivity().getApplication()).accountInvalidations().observe(getViewLifecycleOwner(),ignored->model.checkCurrentAccount());
        SharedMotion.install(view);
    }
    @Override public void onStart(){super.onStart();if(requireActivity() instanceof MainActivity)((MainActivity)requireActivity()).setRoleNavigationVisible(false);}
    @Override public void onResume(){super.onResume();if(model!=null)model.checkCurrentAccount();}
    private void render(RecoverySecurityViewModel.State state){
        if(binding==null)return;if(state.page!=RecoverySecurityViewModel.Page.UNAVAILABLE&&!model.currentForDisplay())return;rendering=true;
        boolean codes=state.page==RecoverySecurityViewModel.Page.CODES,proof=state.page==RecoverySecurityViewModel.Page.PROOF,password=state.page==RecoverySecurityViewModel.Page.PASSWORD;
        binding.recoveryTitle.setText(state.kind==RecoverySecurityViewModel.Kind.SETUP?R.string.recovery_setup_title:state.kind==RecoverySecurityViewModel.Kind.CODES?R.string.recovery_codes_title:state.kind==RecoverySecurityViewModel.Kind.FORGOT?R.string.recovery_forgot_title:R.string.recovery_password_title);
        int intro=codes?R.string.recovery_codes_intro:password?R.string.recovery_new_password_intro:state.page==RecoverySecurityViewModel.Page.PENDING?R.string.recovery_pending:proof?R.string.recovery_proof_intro:state.kind==RecoverySecurityViewModel.Kind.SETUP?R.string.recovery_setup_intro:R.string.recovery_status_intro;
        binding.recoveryIntro.setText(intro);
        binding.recoveryCodesPanel.setVisibility(codes?View.VISIBLE:View.GONE);binding.recoveryProofPanel.setVisibility(proof?View.VISIBLE:View.GONE);binding.recoveryPasswordPanel.setVisibility(password?View.VISIBLE:View.GONE);
        binding.recoveryStatus.setVisibility(state.page==RecoverySecurityViewModel.Page.STATUS&&state.status!=null?View.VISIBLE:View.GONE);
        if(state.status!=null)binding.recoveryStatus.setText(getString(R.string.recovery_status,state.status.remainingCodes));
        binding.recoveryProofLabel.setVisibility(proof?View.VISIBLE:View.GONE);binding.recoveryProofInput.setVisibility(proof?View.VISIBLE:View.GONE);
        setText(binding.recoveryEmail,model.email());setText(binding.recoveryProofInput,model.proof());setText(binding.recoveryNewPassword,model.password());setText(binding.recoveryConfirmPassword,model.confirmation());
        for(EditText input:inputs())input.setEnabled(!state.busy);binding.recoveryEmail.setEnabled(!state.busy&&state.kind==RecoverySecurityViewModel.Kind.FORGOT);
        binding.recoveryBack.setEnabled(!state.busy);binding.recoveryProgress.setVisibility(state.busy?View.VISIBLE:View.GONE);
        int primary=codes?R.string.recovery_acknowledge:proof?R.string.recovery_verify:password?R.string.recovery_save_password:state.page==RecoverySecurityViewModel.Page.PENDING?R.string.recovery_check_result:state.page==RecoverySecurityViewModel.Page.STATUS&&state.status!=null?(state.status.codesConfigured?R.string.recovery_regenerate:R.string.recovery_generate):R.string.recovery_refresh;
        binding.recoveryPrimary.setText(state.page==RecoverySecurityViewModel.Page.COMPLETE?R.string.account_continue:primary);
        boolean publicUnavailable=state.page==RecoverySecurityViewModel.Page.UNAVAILABLE&&(state.kind==RecoverySecurityViewModel.Kind.FORGOT||state.kind==RecoverySecurityViewModel.Kind.PASSWORD_CHANGE);
        boolean needsSignIn="RECENT_AUTH_REQUIRED".equals(state.error)||"AUTH_REQUIRED".equals(state.error)||"AUTH_INVALID".equals(state.error)||"SIGN_IN_REQUIRED".equals(state.error)||"RECOVERY_GRANT_INVALID".equals(state.error)||"RESET_FAILED".equals(state.error);
        boolean hidePrimary=state.page==RecoverySecurityViewModel.Page.LOADING||"PREVIEW".equals(state.error)||publicUnavailable||needsSignIn;
        binding.recoveryPrimary.setVisibility(hidePrimary?View.GONE:View.VISIBLE);binding.recoveryPrimary.setEnabled(!state.busy);
        binding.recoveryReauthenticate.setVisibility(needsSignIn?View.VISIBLE:View.GONE);binding.recoveryReauthenticate.setEnabled(!state.busy);
        binding.recoveryNoBypass.setVisibility(state.kind==RecoverySecurityViewModel.Kind.FORGOT||state.kind==RecoverySecurityViewModel.Kind.PASSWORD_CHANGE?View.VISIBLE:View.GONE);
        binding.recoveryError.setVisibility(state.error==null?View.GONE:View.VISIBLE);if(state.error!=null)binding.recoveryError.setText(message(state.error));
        if(displayedCodes!=state.codes){displayedCodes=state.codes;binding.recoveryCodeRows.removeAllViews();if(state.codes!=null)for(String code:state.codes.codes){TextView row=new TextView(requireContext());row.setTextAppearance(R.style.TextAppearance_GoServe_Body);row.setText(code);row.setTextIsSelectable(true);row.setSaveEnabled(false);row.setSaveFromParentEnabled(false);row.setPadding(0,8,0,8);binding.recoveryCodeRows.addView(row,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));}}
        rendering=false;
        if(state.page==RecoverySecurityViewModel.Page.COMPLETE&&!completed){completed=true;if(requireActivity() instanceof AuthActivity){AuthViewModel auth=new ViewModelProvider(requireActivity()).get(AuthViewModel.class);if(state.signedIn!=null)auth.recoverySignedIn(state.signedIn);else auth.refreshAccountRoute();}else if(state.signedIn!=null)((MainActivity)requireActivity()).returnToSignIn();}
    }
    private void leave(){if(model==null||!model.canGoBack())return;if(requireActivity() instanceof AuthActivity){model.abandon();new ViewModelProvider(requireActivity()).get(AuthViewModel.class).signInScreen();}else if(model.unsavedCodes()){model.abandon();((MainActivity)requireActivity()).returnToSignIn();}else{model.abandon();getParentFragmentManager().popBackStack();}}
    private void reauthenticate(){boolean returnCodes=model.state().getValue().kind==RecoverySecurityViewModel.Kind.CODES;model.abandon();if(requireActivity() instanceof AuthActivity)new ViewModelProvider(requireActivity()).get(AuthViewModel.class).reauthenticateForRecovery();else if(returnCodes)((MainActivity)requireActivity()).reauthenticateForRecoveryCodes();else{((GoServeApplication)requireActivity().getApplication()).auth().invalidateSession();((MainActivity)requireActivity()).returnToSignIn();}}
    private void setText(EditText view,String value){if(!view.getText().toString().equals(value))view.setText(value);}
    private EditText[] inputs(){return new EditText[]{binding.recoveryEmail,binding.recoveryProofInput,binding.recoveryNewPassword,binding.recoveryConfirmPassword};}
    private int message(String code){switch(code){case "PREVIEW":return R.string.recovery_preview;case "EMAIL_REQUIRED":return R.string.recovery_email_required;case "CODE_REQUIRED":return R.string.recovery_code_required;case "RECOVERY_PROOF_INVALID":return R.string.recovery_invalid_proof;case "PROOF_UNCONFIRMED":return R.string.recovery_verify_uncertain;case "RECOVERY_GRANT_INVALID":return R.string.recovery_expired;case "RECENT_AUTH_REQUIRED":return R.string.recovery_recent_auth;case "RATE_LIMITED":return R.string.recovery_rate_limited;case "PASSWORD_TOO_LONG":return R.string.password_too_long;case "PASSWORD_WEAK":return R.string.password_weak;case "CONFIRMATION_MISMATCH":return R.string.password_confirmation_mismatch;case "CODES_UNCONFIRMED":return R.string.recovery_codes_unconfirmed;case "AUTH_REQUIRED":case "AUTH_INVALID":return R.string.recovery_unavailable;case "SIGN_IN_REQUIRED":return R.string.recovery_signin_required;case "RESET_FAILED":return R.string.recovery_reset_failed;default:return R.string.recovery_failure;}}
    @Override public void onDestroyView(){for(Runnable remove:detach)remove.run();detach.clear();if(binding!=null){for(EditText input:inputs())input.setText("");binding.recoveryCodeRows.removeAllViews();}displayedCodes=null;if(requireActivity() instanceof MainActivity)((MainActivity)requireActivity()).setRoleNavigationVisible(true);binding=null;super.onDestroyView();}
}
