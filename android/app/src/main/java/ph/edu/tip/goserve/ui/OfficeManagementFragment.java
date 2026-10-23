package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.math.BigDecimal;
import java.util.*;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.data.RealtimeDataSource;
import ph.edu.tip.goserve.data.OfficeManagementRepository.*;
import ph.edu.tip.goserve.domain.*;
import ph.edu.tip.goserve.databinding.FragmentOfficeManagementBinding;

public final class OfficeManagementFragment extends Fragment {
    private ViewModelProvider.Factory factory;
    private FragmentOfficeManagementBinding binding;
    private OfficeManagementViewModel model;
    private boolean rendering;
    private android.app.Dialog dialog;
    private OnBackPressedCallback back;
    private RealtimeDataSource.Subscription subscription;
    private RealtimeDataSource.Session subscribedSession;
    private List<Catalog> renderedServices=List.of();private List<Announcement> renderedNotices=List.of();
    public OfficeManagementFragment(){}
    OfficeManagementFragment(ViewModelProvider.Factory factory){this.factory=factory;}
    public static OfficeManagementFragment create(Kind kind){OfficeManagementFragment f=new OfficeManagementFragment();Bundle b=new Bundle();b.putString("kind",kind.name());f.setArguments(b);return f;}
    public boolean mutationPending(){return model!=null && model.mutationPending();}
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved){binding=FragmentOfficeManagementBinding.inflate(inflater,parent,false);return binding.getRoot();}
    @Override public void onViewCreated(View view,Bundle saved){
        model=new ViewModelProvider(requireActivity(),factory==null?requireActivity().getDefaultViewModelProviderFactory():factory).get(OfficeManagementViewModel.class);
        back=new OnBackPressedCallback(true){public void handleOnBackPressed(){if(model.mutationPending())return;if(model.state().getValue().mode==OfficeManagementViewModel.Mode.REVIEW){model.cancel();return;}setEnabled(false);requireActivity().getOnBackPressedDispatcher().onBackPressed();}};
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),back);
        binding.officeMgmtBack.setOnClickListener(v->back.handleOnBackPressed());
        binding.officeMgmtStatus.setOnClickListener(v->chooseStatus());
        binding.officeMgmtCreate.setOnClickListener(v->{clearErrors();model.create();});
        binding.officeMgmtPrimary.setOnClickListener(v->{OfficeManagementViewModel.State s=model.state().getValue();if(s.uncertain)model.retry();else if(s.mode==OfficeManagementViewModel.Mode.REVIEW)model.confirm();else if(validate())model.review();});
        binding.officeMgmtEdit.setOnClickListener(v->model.cancel());binding.officeMgmtCancel.setOnClickListener(v->model.cancel());
        binding.officeMgmtRefresh.setOnClickListener(v->{if(model.state().getValue().error!=null)model.retry();else model.reloadCurrent();});
        binding.officeMgmtMore.setOnClickListener(v->model.more());binding.officeMgmtDiscard.setOnClickListener(v->discard());
        for(EditText field:List.of(binding.officeMgmtName,binding.officeMgmtText,binding.officeMgmtRequirements,binding.officeMgmtAmount,binding.officeMgmtCurrency)){
            field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);field.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int st,int before,int count){if(!rendering)fields();}});
        }
        binding.officeMgmtPhysical.setOnCheckedChangeListener((v,c)->fields());binding.officeMgmtDigital.setOnCheckedChangeListener((v,c)->fields());binding.officeMgmtActive.setOnCheckedChangeListener((v,c)->fields());
        binding.officeMgmtFee.setOnCheckedChangeListener((group,id)->{if(!rendering)model.fee(id==R.id.officeMgmtFeeFree?FeeDeclaration.FREE:id==R.id.officeMgmtFeeRequired?FeeDeclaration.REQUIRED:FeeDeclaration.PENDING_REVIEW);});
        ViewCompat.setAccessibilityHeading(binding.officeMgmtTitle,true);SharedMotion.install(view);
        model.state().observe(getViewLifecycleOwner(),this::render);
        ((GoServeApplication)requireActivity().getApplication()).accountInvalidations().observe(getViewLifecycleOwner(),ignored->{model.checkCurrentAccount();if(model.state().getValue().blocked)dismissDialog();});
        Kind requested=Kind.OFFICE;try{if(getArguments()!=null)requested=Kind.valueOf(getArguments().getString("kind",Kind.OFFICE.name()));}catch(IllegalArgumentException invalid){}
        model.start(requested);
    }
    private void fields(){if(rendering || binding==null)return;model.fields(value(binding.officeMgmtName),value(binding.officeMgmtText),value(binding.officeMgmtRequirements),value(binding.officeMgmtAmount),value(binding.officeMgmtCurrency),binding.officeMgmtPhysical.isChecked(),binding.officeMgmtDigital.isChecked(),binding.officeMgmtActive.isChecked());}
    private void chooseStatus(){
        if(model.state().getValue().busy() || model.state().getValue().blocked)return;OfficeStatus[] statuses=OfficeStatus.values();String[] labels=Arrays.stream(statuses).map(s->s.wireValue).toArray(String[]::new);
        dialog=new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.office_mgmt_status).setItems(labels,(d,which)->{if(binding!=null && isResumed())model.status(statuses[which]);}).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void discard(){
        if(model.state().getValue().saving)return;
        dialog=new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.office_mgmt_discard).setMessage(R.string.office_mgmt_discard_body).setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.office_mgmt_discard,(d,w)->{if(binding!=null && isResumed())model.discardLocalDraft();}).show();
    }
    private void render(OfficeManagementViewModel.State s){
        if(binding==null || s==null)return;boolean editing=s.mode==OfficeManagementViewModel.Mode.EDIT,review=s.mode==OfficeManagementViewModel.Mode.REVIEW,browse=s.mode==OfficeManagementViewModel.Mode.BROWSE,enabled=!s.busy() && !s.blocked;
        binding.officeMgmtTitle.setText(s.kind==Kind.OFFICE?R.string.office_mgmt_office:s.kind==Kind.ANNOUNCEMENT?R.string.office_mgmt_announcements:R.string.office_mgmt_services);
        binding.officeMgmtScope.setText(s.office==null?"":getString(R.string.office_mgmt_scope,s.office.name));
        visible(binding.officeMgmtAudit,s.office!=null && s.office.changedAt!=null);binding.officeMgmtAudit.setText(s.office==null || s.office.changedAt==null?"":getString(R.string.office_mgmt_updated,InboxFragment.time(s.office.changedAt)));
        visible(binding.officeMgmtProgress,s.busy());visible(binding.officeMgmtSaved,s.saved && !s.blocked);
        visible(binding.officeMgmtError,s.error!=null || s.uncertain);binding.officeMgmtError.setText(s.uncertain?getString(R.string.office_mgmt_uncertain):s.error==null?"":getString(errorText(s.error)));
        visible(binding.officeMgmtForm,editing && !s.blocked);visible(binding.officeMgmtStatusField,s.kind==Kind.OFFICE);visible(binding.officeMgmtNameField,s.kind==Kind.SERVICE);visible(binding.officeMgmtServiceFields,s.kind==Kind.SERVICE);visible(binding.officeMgmtTextField,s.kind!=Kind.SERVICE);
        binding.officeMgmtTextLabel.setText(s.kind==Kind.OFFICE?R.string.office_mgmt_note:R.string.office_mgmt_body);
        binding.officeMgmtStatus.setText(s.status==null?getString(R.string.office_mgmt_choose_status):s.status.wireValue);
        binding.officeMgmtActive.setText(s.kind==Kind.ANNOUNCEMENT?R.string.office_mgmt_active_notice:R.string.office_mgmt_active_service);visible(binding.officeMgmtActive,s.kind!=Kind.OFFICE);
        binding.officeMgmtDemo.setText(s.service==null || s.service.demo?R.string.office_mgmt_demo:R.string.office_mgmt_service_policy);
        rendering=true;set(binding.officeMgmtName,s.name);set(binding.officeMgmtText,s.text);set(binding.officeMgmtRequirements,s.requirements);set(binding.officeMgmtAmount,s.amount);set(binding.officeMgmtCurrency,s.currency);binding.officeMgmtPhysical.setChecked(s.physical);binding.officeMgmtDigital.setChecked(s.digital);binding.officeMgmtActive.setChecked(s.active);binding.officeMgmtFee.check(s.fee==FeeDeclaration.FREE?R.id.officeMgmtFeeFree:s.fee==FeeDeclaration.REQUIRED?R.id.officeMgmtFeeRequired:R.id.officeMgmtFeePending);rendering=false;
        visible(binding.officeMgmtMoney,s.fee==FeeDeclaration.REQUIRED);enableChildren(binding.officeMgmtForm,enabled);
        visible(binding.officeMgmtSummary,review && !s.blocked);binding.officeMgmtSummary.setText(review?summary(s):"");
        visible(binding.officeMgmtPrimary,!browse && !s.blocked);binding.officeMgmtPrimary.setText(s.uncertain?R.string.office_mgmt_retry:review?s.kind==Kind.OFFICE?R.string.office_mgmt_confirm_office:s.kind==Kind.ANNOUNCEMENT?R.string.office_mgmt_confirm_notice:R.string.office_mgmt_confirm_service:R.string.office_mgmt_review);binding.officeMgmtPrimary.setEnabled(enabled && !s.stale && s.office!=null);
        visible(binding.officeMgmtEdit,review && !s.uncertain && !s.blocked);binding.officeMgmtEdit.setEnabled(enabled);
        visible(binding.officeMgmtCancel,editing && s.kind!=Kind.OFFICE && !s.blocked);binding.officeMgmtCancel.setEnabled(enabled);
        visible(binding.officeMgmtDiscard,s.uncertain);binding.officeMgmtDiscard.setEnabled(enabled);binding.officeMgmtBack.setEnabled(!model.mutationPending());
        binding.officeMgmtRefresh.setText(s.stale?R.string.office_mgmt_reload:R.string.office_mgmt_refresh);binding.officeMgmtRefresh.setEnabled(enabled && !s.uncertain);
        visible(binding.officeMgmtCreate,browse && !s.blocked);binding.officeMgmtCreate.setText(s.kind==Kind.SERVICE?R.string.office_mgmt_new_service:R.string.office_mgmt_new_notice);binding.officeMgmtCreate.setEnabled(enabled && s.office!=null && s.error==null);
        visible(binding.officeMgmtMore,browse && s.nextId!=null && !s.blocked);binding.officeMgmtMore.setEnabled(enabled && s.error==null);
        visible(binding.officeMgmtRows,browse && !s.blocked);visible(binding.officeMgmtEmpty,browse && !s.blocked && !s.loading && s.error==null && s.services.isEmpty() && s.announcements.isEmpty());binding.officeMgmtEmpty.setText(s.kind==Kind.SERVICE?R.string.office_mgmt_empty_service:R.string.office_mgmt_empty_notice);
        if(s.blocked || !renderedServices.equals(s.services) || !renderedNotices.equals(s.announcements)){
            renderedServices=s.services;renderedNotices=s.announcements;binding.officeMgmtRows.removeAllViews();
            if(!s.blocked){for(Catalog c:s.services)addRow(c.service.id,c.service.name+"\n"+fee(c.service.fee,c.service.amount==null?"":c.service.amount.toPlainString(),c.service.currency)+" · "+getString(c.service.active?R.string.office_mgmt_active:R.string.office_mgmt_inactive));for(Announcement n:s.announcements)addRow(n.id,n.body+"\n"+getString(n.active?R.string.office_mgmt_active:R.string.office_mgmt_inactive));}
        }
        for(int i=0;i<binding.officeMgmtRows.getChildCount();i++)binding.officeMgmtRows.getChildAt(i).setEnabled(enabled);
    }
    private void addRow(String id,String text){MaterialButton row=(MaterialButton)getLayoutInflater().inflate(R.layout.item_office_management,binding.officeMgmtRows,false);row.setText(text);row.setOnClickListener(v->{clearErrors();model.edit(id);});binding.officeMgmtRows.addView(row);SharedMotion.install(row);}
    private String summary(OfficeManagementViewModel.State s){
        if(s.kind==Kind.OFFICE)return getString(R.string.office_mgmt_status)+": "+(s.status==null?"":s.status.wireValue)+"\n\n"+s.text;
        String visibility=getString(s.active?R.string.office_mgmt_active:R.string.office_mgmt_inactive);
        if(s.kind==Kind.ANNOUNCEMENT)return s.text+"\n\n"+visibility;
        List<String> modes=new ArrayList<>();if(s.physical)modes.add(getString(R.string.office_mgmt_physical));if(s.digital)modes.add(getString(R.string.office_mgmt_digital));
        return s.name+"\n\n"+fee(s.fee,s.amount,s.currency)+"\n"+String.join("\n",modes)+(s.requirements.isEmpty()?"":"\n\n"+s.requirements)+"\n\n"+visibility+"\n\n"+getString(s.service==null || s.service.demo?R.string.office_mgmt_demo:R.string.office_mgmt_service_policy);
    }
    private String fee(FeeDeclaration fee,String amount,String currency){return fee==FeeDeclaration.PENDING_REVIEW?getString(R.string.office_mgmt_pending):fee==FeeDeclaration.FREE?getString(R.string.office_mgmt_free):getString(R.string.office_mgmt_amount_value,amount,currency);}
    private boolean validate(){
        clearErrors();OfficeManagementViewModel.State s=model.state().getValue();boolean valid=true;
        if(s.kind==Kind.ANNOUNCEMENT && value(binding.officeMgmtText).isBlank()){binding.officeMgmtText.setError(getString(R.string.office_mgmt_body_error));valid=false;}
        if(s.kind==Kind.SERVICE){
            if(value(binding.officeMgmtName).isBlank()){binding.officeMgmtName.setError(getString(R.string.office_mgmt_name_error));valid=false;}
            if(!s.physical && !s.digital){visible(binding.officeMgmtModesError,true);valid=false;}
            if(s.fee==FeeDeclaration.REQUIRED){try{if(new BigDecimal(value(binding.officeMgmtAmount)).signum()<=0)throw new NumberFormatException();}catch(NumberFormatException invalid){binding.officeMgmtAmount.setError(getString(R.string.office_mgmt_amount_error));valid=false;}if(value(binding.officeMgmtCurrency).isBlank()){binding.officeMgmtCurrency.setError(getString(R.string.office_mgmt_currency_error));valid=false;}}
        }
        return valid;
    }
    private void clearErrors(){if(binding==null)return;binding.officeMgmtName.setError(null);binding.officeMgmtText.setError(null);binding.officeMgmtAmount.setError(null);binding.officeMgmtCurrency.setError(null);visible(binding.officeMgmtModesError,false);}
    private static int errorText(String code){switch(code){case "STALE_VERSION":case "IDEMPOTENCY_CONFLICT":return R.string.office_mgmt_stale;case "FORBIDDEN":return R.string.office_mgmt_forbidden;case "STALE_ACCOUNT":return R.string.office_mgmt_account;case "VALIDATION_FAILED":return R.string.office_mgmt_invalid;case "DRAFT_TOO_LARGE":return R.string.office_mgmt_too_large;default:return R.string.office_mgmt_failed;}}
    private static String value(EditText field){return field.getText().toString();}private static void set(EditText field,String value){if(!value.contentEquals(field.getText()))field.setText(value);}
    private static void visible(View view,boolean show){view.setVisibility(show?View.VISIBLE:View.GONE);}private static void enableChildren(View view,boolean enabled){view.setEnabled(enabled);if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)enableChildren(((ViewGroup)view).getChildAt(i),enabled);}
    @Override public void onStart(){
        super.onStart();model.checkCurrentAccount();RealtimeDataSource.Session session=((GoServeApplication)requireActivity().getApplication()).realtimeSession();if(session==null || subscription!=null)return;subscribedSession=session;
        subscription=session.subscribe(Set.of("offices","services","announcements"),new RealtimeDataSource.Listener(){public void onInvalidated(RealtimeDataSource.Invalidation e){if(binding!=null && subscribedSession==session && e!=null && e.generation==session.generation())model.refresh();}public void onStateChanged(RealtimeDataSource.State state){if(binding!=null && subscribedSession==session && state==RealtimeDataSource.State.CONNECTED)model.refresh();}});
    }
    private void dismissDialog(){if(dialog!=null)dialog.dismiss();dialog=null;}
    @Override public void onStop(){dismissDialog();subscribedSession=null;if(subscription!=null)subscription.close();subscription=null;super.onStop();}
    @Override public void onDestroyView(){dismissDialog();binding=null;renderedServices=List.of();renderedNotices=List.of();super.onDestroyView();}
}
