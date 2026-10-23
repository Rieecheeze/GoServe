package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.ViewCompat;
import android.text.Editable;
import android.text.TextWatcher;
import java.util.List;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.domain.RequestData;
import ph.edu.tip.goserve.domain.Office;
import ph.edu.tip.goserve.domain.FeeDeclaration;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import ph.edu.tip.goserve.databinding.FragmentRequestComposeBinding;

public final class RequestComposeFragment extends Fragment {
    private ViewModelProvider.Factory catalogueFactory,composeFactory;
    private ViewModelProvider.Factory evidenceFactory;
    private FragmentRequestComposeBinding binding;
    private ServiceCatalogueViewModel catalogue;
    private RequestComposeViewModel model;
    private OfficeViewModel offices;
    private ph.edu.tip.goserve.data.RealtimeDataSource.Subscription serviceSubscription;
    private ph.edu.tip.goserve.data.RealtimeDataSource.Session serviceSession;
    private boolean rendering;
    public RequestComposeFragment() {}
    RequestComposeFragment(ViewModelProvider.Factory catalogue,ViewModelProvider.Factory compose) { catalogueFactory=catalogue; composeFactory=compose; }
    RequestComposeFragment(ViewModelProvider.Factory catalogue,ViewModelProvider.Factory compose,ViewModelProvider.Factory evidence) {
        this(catalogue,compose); evidenceFactory=evidence;
    }
    ViewModelProvider.Factory evidenceFactory() { return evidenceFactory; }
    public static RequestComposeFragment create(String officeId) {
        RequestComposeFragment fragment=new RequestComposeFragment(); Bundle args=new Bundle(); args.putString("office_id",officeId); fragment.setArguments(args); return fragment;
    }
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved) {
        binding=FragmentRequestComposeBinding.inflate(inflater,parent,false); return binding.getRoot();
    }
    @Override public void onViewCreated(View view,Bundle saved) {
        MainActivity host=(MainActivity)requireActivity(); host.setRoleNavigationVisible(false);
        catalogue=new ViewModelProvider(this,catalogueFactory==null?getDefaultViewModelProviderFactory():catalogueFactory).get(ServiceCatalogueViewModel.class);
        model=new ViewModelProvider(this,composeFactory==null?getDefaultViewModelProviderFactory():composeFactory).get(RequestComposeViewModel.class);
        offices=new ViewModelProvider(requireActivity()).get(OfficeViewModel.class);
        String office=getArguments()==null?null:getArguments().getString("office_id");
        binding.composeBack.setOnClickListener(v->requireActivity().getOnBackPressedDispatcher().onBackPressed());
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),new OnBackPressedCallback(true) {
            public void handleOnBackPressed() {
                RequestComposeViewModel.State state=model.state().getValue();
                if(state.mode==RequestComposeViewModel.Mode.SUBMITTING || state.uncertain)return;
                if(state.mode==RequestComposeViewModel.Mode.REVIEW)model.edit();
                else { setEnabled(false); requireActivity().getOnBackPressedDispatcher().onBackPressed(); }
            }
        });
        binding.composeService.setOnClickListener(v->chooseService());
        binding.composeDelivery.setOnClickListener(v->chooseDelivery(model.selectedService()));
        binding.composeCatalogueRetry.setOnClickListener(v->{
            if(catalogue.state().getValue().error!=null)catalogue.retry(); else catalogue.refresh();
        });
        binding.composeCatalogueMore.setOnClickListener(v->catalogue.loadMore());
        binding.composeQuoteRetry.setOnClickListener(v->model.retryQuote());
        binding.composeEdit.setOnClickListener(v->model.edit());
        binding.composeAddEvidence.setOnClickListener(v->chooseEvidence());
        binding.composeCheckRequests.setOnClickListener(v->{
            model.checkCurrentAccount();
            if(model.state().getValue().mode!=RequestComposeViewModel.Mode.UNAVAILABLE)host.navigate(RequestListFragment.create(),true);
        });
        binding.composeNotes.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence text,int start,int count,int after) {}
            public void onTextChanged(CharSequence text,int start,int before,int count) { if(!rendering)model.setNotes(text.toString()); }
            public void afterTextChanged(Editable value) {}
        });
        binding.composeCash.setOnCheckedChangeListener((button,checked)->{ if(!rendering)model.setPaymentMethod(checked?"cash":null); });
        binding.composeEgovpay.setOnCheckedChangeListener((button,checked)->{ if(!rendering)model.setPaymentMethod(checked?"egovpay":null); });
        binding.composePrimary.setOnClickListener(v->{
            model.checkCurrentAccount(); RequestComposeViewModel.State state=model.state().getValue();
            if(state.mode==RequestComposeViewModel.Mode.COMPLETE && state.result!=null)host.navigate(RequestDetailFragment.create(state.result.id),true);
            else if(state.mode==RequestComposeViewModel.Mode.REVIEW)model.submit(); else model.review();
        });
        ViewCompat.setAccessibilityHeading(binding.composeHeading,true); SharedMotion.install(view);
        catalogue.state().observe(getViewLifecycleOwner(),ignored->render());
        model.state().observe(getViewLifecycleOwner(),ignored->render());
        offices.state().observe(getViewLifecycleOwner(),ignored->render());
        ((GoServeApplication)requireActivity().getApplication()).accountInvalidations().observe(getViewLifecycleOwner(),ignored->{ catalogue.checkCurrentAccount(); model.checkCurrentAccount(); });
        model.ensureOffice(office); catalogue.ensureOffice(office);
    }
    private void chooseService() {
        catalogue.checkCurrentAccount(); model.checkCurrentAccount();
        ServiceCatalogueViewModel.State state=catalogue.state().getValue();
        if(state.busy() || state.mode==ServiceCatalogueViewModel.Mode.UNAVAILABLE || state.items.isEmpty())return;
        List<RequestData.Service> choices=state.items;
        CharSequence[] labels=new CharSequence[choices.size()]; for(int i=0;i<choices.size();i++)labels[i]=choices.get(i).name;
        androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.compose_service_label)
            .setItems(labels,(sheet,position)->{
                catalogue.checkCurrentAccount(); ServiceCatalogueViewModel.State current=catalogue.state().getValue();
                if(current.mode==ServiceCatalogueViewModel.Mode.UNAVAILABLE || current.busy())return;
                RequestData.Service selected=choices.get(position);
                for(RequestData.Service value:current.items)if(selected.id.equals(value.id) && selected.version==value.version) {
                    if(value.deliveryModes.size()==1)model.selectService(value,value.deliveryModes.get(0));
                    else chooseDelivery(value);
                    return;
                }
            }).create();
        dialog.show(); SharedMotion.install(dialog.getWindow().getDecorView());
    }
    private void chooseDelivery(RequestData.Service service) {
        model.checkCurrentAccount(); if(service==null || model.state().getValue().mode==RequestComposeViewModel.Mode.UNAVAILABLE)return;
        CharSequence[] labels=new CharSequence[service.deliveryModes.size()];
        for(int i=0;i<labels.length;i++)labels[i]=deliveryLabel(service.deliveryModes.get(i));
        androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.compose_delivery_label)
            .setItems(labels,(sheet,position)->model.selectService(service,service.deliveryModes.get(position))).create();
        dialog.show(); SharedMotion.install(dialog.getWindow().getDecorView());
    }
    private String deliveryLabel(String mode) { return getString("physical".equals(mode)?R.string.compose_delivery_physical:R.string.compose_delivery_digital); }
    private void chooseEvidence() {
        model.checkCurrentAccount(); RequestComposeViewModel.State state=model.state().getValue();
        if(state.mode!=RequestComposeViewModel.Mode.EDITING || state.quote==null || state.options==null || !state.options.evidence.available
            || getChildFragmentManager().findFragmentByTag(RequestEvidenceDialog.TAG)!=null)return;
        List<String> required=state.quote.service.requirementIds;
        if(required.size()<2)RequestEvidenceDialog.create(required.isEmpty()?null:required.get(0)).show(getChildFragmentManager(),RequestEvidenceDialog.TAG);
        else {
            androidx.appcompat.app.AlertDialog dialog=new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.compose_evidence_title)
                .setItems(required.toArray(new CharSequence[0]),(sheet,position)->{
                    model.checkCurrentAccount(); RequestComposeViewModel.State current=model.state().getValue();
                    if(current.mode==RequestComposeViewModel.Mode.EDITING && current.quote!=null && current.quote.id.equals(state.quote.id))
                        RequestEvidenceDialog.create(required.get(position)).show(getChildFragmentManager(),RequestEvidenceDialog.TAG);
                }).create();
            dialog.show(); SharedMotion.install(dialog.getWindow().getDecorView());
        }
    }
    private void render() {
        if(binding==null || model==null || catalogue==null || offices==null)return;
        RequestComposeViewModel.State state=model.state().getValue(); ServiceCatalogueViewModel.State services=catalogue.state().getValue();
        boolean unavailable=state.mode==RequestComposeViewModel.Mode.UNAVAILABLE || services.mode==ServiceCatalogueViewModel.Mode.UNAVAILABLE;
        boolean review=state.mode==RequestComposeViewModel.Mode.REVIEW || state.mode==RequestComposeViewModel.Mode.SUBMITTING;
        boolean complete=state.mode==RequestComposeViewModel.Mode.COMPLETE;
        boolean editable=!unavailable && !review && !complete;
        rendering=true;
        binding.composeHeading.setText(complete?R.string.compose_complete_heading:review?R.string.compose_review_heading:R.string.compose_heading);
        visible(binding.composeForm,!complete && !unavailable);
        String officeName=state.officeId;
        for(Office office:offices.state().getValue().offices)if(office.id.equals(state.officeId)) { officeName=office.name; break; }
        binding.composeOffice.setText(unavailable || officeName==null?"":officeName);
        RequestData.Service selected=model.selectedService();
        binding.composeService.setText(state.quote!=null?state.quote.service.name:selected==null?getString(R.string.compose_service_choose):selected.name);
        binding.composeService.setEnabled(editable && !services.busy() && !services.items.isEmpty());
        binding.composeDelivery.setText(model.selectedDelivery()==null?getString(R.string.compose_delivery_choose):deliveryLabel(model.selectedDelivery()));
        binding.composeDelivery.setEnabled(editable && !state.busy() && selected!=null);
        if(!binding.composeNotes.getText().toString().equals(state.notes))binding.composeNotes.setText(state.notes);
        binding.composeNotes.setEnabled(editable);
        binding.composeFee.setText(state.quote==null?getString(R.string.compose_fee_unselected):RequestListFragment.feeLabel(requireContext(),state.quote.service));
        boolean required=state.quote!=null && state.quote.service.fee==FeeDeclaration.REQUIRED;
        visible(binding.composePayment,required);
        boolean cash=state.options!=null && state.options.cash.available;
        binding.composeCash.setEnabled(editable && !state.busy() && cash); binding.composeCash.setChecked("cash".equals(state.paymentMethod));
        binding.composeCash.jumpDrawablesToCurrentState(); visible(binding.composeCashNotice,required && !cash);
        binding.composeEgovpay.setEnabled(editable && !state.busy() && state.options!=null && state.options.egovpay);
        binding.composeEgovpay.setChecked("egovpay".equals(state.paymentMethod)); binding.composeEgovpay.jumpDrawablesToCurrentState();
        visible(binding.composeEgovpayNotice,required);
        binding.composeEgovpayNotice.setText(state.options!=null && state.options.egovpay?R.string.egovpay_test_notice:R.string.egovpay_unavailable);
        binding.composeEvidence.setText(state.quote==null?"":state.quote.service.requirementIds.isEmpty()?getString(R.string.compose_evidence_none):
            getString(R.string.compose_evidence_required,android.text.TextUtils.join(", ",state.quote.service.requirementIds)));
        binding.composeEvidenceFiles.removeAllViews();
        boolean uploads=state.options!=null && state.options.evidence.available;
        visible(binding.composeAddEvidence,uploads && !complete && !unavailable);
        binding.composeAddEvidence.setEnabled(editable && !state.busy());
        visible(binding.composeUploadNotice,state.quote!=null && !state.quote.service.requirementIds.isEmpty() && !uploads && !unavailable);
        for(RequestData.Evidence file:state.evidence) {
            MaterialButton remove=new MaterialButton(requireContext(),null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
            remove.setText(getString(R.string.compose_evidence_verified,file.requirement==null?getString(R.string.compose_evidence_title):file.requirement));
            remove.setContentDescription(remove.getText()+". "+getString(R.string.compose_evidence_remove));
            remove.setEnabled(editable && !state.busy()); remove.setOnClickListener(v->model.removeEvidence(file.id));
            binding.composeEvidenceFiles.addView(remove);
        }
        String notice=null;
        if(unavailable)notice=getString("STALE_ACCOUNT".equals(state.error) || "STALE_ACCOUNT".equals(services.error)?R.string.request_live_account:R.string.request_live_forbidden);
        else if(complete && state.result!=null)notice=getString(R.string.compose_complete,state.result.reference);
        else if(state.uncertain)notice=getString(R.string.compose_uncertain);
        else if(state.mode==RequestComposeViewModel.Mode.SUBMITTING)notice=getString(R.string.compose_submitting);
        else if(state.error!=null)notice=errorText(state.error);
        else if(state.busy())notice=getString(R.string.compose_quote_loading);
        else if(services.busy())notice=getString(R.string.compose_catalogue_loading);
        else if(services.error!=null)notice=getString(R.string.compose_catalogue_failed);
        else if(services.mode==ServiceCatalogueViewModel.Mode.CONTENT && services.items.isEmpty())notice=getString(R.string.compose_catalogue_empty);
        binding.composeNotice.setText(notice==null?"":notice); visible(binding.composeNotice,notice!=null);
        visible(binding.composeLoading,!unavailable && (state.busy() || services.busy()));
        boolean staleQuote="QUOTE_STALE".equals(state.error);
        visible(binding.composeCatalogueRetry,!unavailable && (services.error!=null || staleQuote) && !services.busy() && editable);
        visible(binding.composeCatalogueMore,!unavailable && services.nextId!=null && services.error==null && editable);
        binding.composeCatalogueMore.setEnabled(!services.busy());
        visible(binding.composeQuoteRetry,editable && !state.busy() && state.error!=null && !staleQuote && selected!=null);
        visible(binding.composeReviewNotice,review); visible(binding.composeEdit,review && !state.busy() && !state.uncertain);
        visible(binding.composeCheckRequests,state.uncertain && !state.busy());
        binding.composeBack.setEnabled(state.mode!=RequestComposeViewModel.Mode.SUBMITTING && !state.uncertain);
        binding.composePrimary.setText(complete?R.string.compose_view_request:state.uncertain?R.string.compose_retry_submit:
            review?R.string.compose_submit:R.string.compose_review);
        binding.composePrimary.setEnabled(!unavailable && !state.busy() && (complete && state.result!=null || review && state.draft!=null || state.quote!=null && state.options!=null));
        rendering=false;
    }
    private String errorText(String code) {
        switch(code) {
            case "QUOTE_STALE": return getString(R.string.compose_quote_stale);
            case "CONFIGURATION_REQUIRED": case "PROVIDER_UNAVAILABLE": return getString(R.string.compose_configuration);
            case "UPLOAD_INVALID": return getString(R.string.compose_missing_evidence);
            case "VALIDATION_FAILED": return getString(R.string.compose_invalid);
            default: return getString(R.string.compose_quote_failed);
        }
    }
    private static void visible(View view,boolean show) { view.setVisibility(show?View.VISIBLE:View.GONE); }
    @Override public void onStart(){
        super.onStart();if(binding==null || catalogue==null || serviceSubscription!=null)return;
        ph.edu.tip.goserve.data.RealtimeDataSource.Session session=((GoServeApplication)requireActivity().getApplication()).realtimeSession();
        if(session==null)return;serviceSession=session;
        serviceSubscription=session.subscribe(java.util.Set.of("services"),new ph.edu.tip.goserve.data.RealtimeDataSource.Listener(){
            private void refresh(){if(binding!=null && serviceSession==session){catalogue.checkCurrentAccount();catalogue.refresh();}}
            public void onInvalidated(ph.edu.tip.goserve.data.RealtimeDataSource.Invalidation event){if(event!=null && event.generation==session.generation())refresh();}
            public void onStateChanged(ph.edu.tip.goserve.data.RealtimeDataSource.State state){if(state==ph.edu.tip.goserve.data.RealtimeDataSource.State.CONNECTED)refresh();}
        });
    }
    @Override public void onStop(){serviceSession=null;if(serviceSubscription!=null)serviceSubscription.close();serviceSubscription=null;super.onStop();}
    @Override public void onDestroyView() {
        ((MainActivity)requireActivity()).setRoleNavigationVisible(true); binding=null; super.onDestroyView();
    }
}
