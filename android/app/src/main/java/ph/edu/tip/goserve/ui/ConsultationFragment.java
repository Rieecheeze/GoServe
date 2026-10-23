package ph.edu.tip.goserve.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.RealtimeDataSource;
import ph.edu.tip.goserve.databinding.FragmentConsultationLiveBinding;
import ph.edu.tip.goserve.databinding.ViewConsultationLiveHeaderBinding;
import ph.edu.tip.goserve.databinding.ItemConsultationLiveBinding;
import ph.edu.tip.goserve.domain.ConsultationData.*;

public final class ConsultationFragment extends Fragment {
    private FragmentConsultationLiveBinding binding;
    private ViewConsultationLiveHeaderBinding header;
    private ConsultationViewModel model;
    private ViewModelProvider.Factory factory;
    private Rows rows;
    private MaterialButton more;
    private boolean rendering,liveUnavailable;
    private RealtimeDataSource.Subscription subscription;
    private RealtimeDataSource.Session subscribedSession;
    private DatePickerDialog datePicker;
    private TimePickerDialog timePicker;
    private OnBackPressedCallback back;
    public ConsultationFragment(){}
    ConsultationFragment(ViewModelProvider.Factory factory){this.factory=factory;}
    public boolean mutationPending(){return model!=null && model.mutationPending();}
    public static ConsultationFragment forAppointment(String id){ConsultationFragment fragment=new ConsultationFragment();Bundle args=new Bundle();args.putString("appointment_id",id);fragment.setArguments(args);return fragment;}
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved){
        binding=FragmentConsultationLiveBinding.inflate(inflater,parent,false);
        binding.consultList.setLayoutManager(new LinearLayoutManager(requireContext()));
        header=ViewConsultationLiveHeaderBinding.inflate(inflater,binding.consultList,false);
        more=new MaterialButton(requireContext(),null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
        more.setText(R.string.consultation_live_more);more.setMinHeight(dp(48));
        more.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        rows=new Rows();binding.consultList.setAdapter(new ConcatAdapter(new Fixed(header.getRoot()),rows,new Fixed(more)));
        return binding.getRoot();
    }
    @Override public void onViewCreated(View view,Bundle saved){
        model=new ViewModelProvider(requireActivity(),factory==null?requireActivity().getDefaultViewModelProviderFactory():factory).get(ConsultationViewModel.class);
        back=new OnBackPressedCallback(true){@Override public void handleOnBackPressed(){if(model.mutationPending())return;if(model.state().getValue().screen==ConsultationViewModel.Screen.SLOTS && !model.state().getValue().moving){setEnabled(false);requireActivity().getOnBackPressedDispatcher().onBackPressed();}else model.back();}};
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),back);
        binding.consultBack.setOnClickListener(v->back.handleOnBackPressed());
        binding.consultRefresh.setOnClickListener(v->model.refresh());
        header.consultAppointments.setOnClickListener(v->model.appointments());
        header.consultOffice.setOnCheckedChangeListener((group,id)->{if(!rendering)model.office(id==R.id.consultDepartment?"DEP-CCS":"OFF-GCC");});
        header.consultStart.setOnClickListener(v->pick(true));header.consultEnd.setOnClickListener(v->pick(false));
        header.consultCapacity.addTextChangedListener(watch(()->saveForm()));
        header.consultActive.setOnCheckedChangeListener((button,checked)->saveForm());
        header.consultReason.addTextChangedListener(watch(()->model.reason(header.consultReason.getText().toString())));
        header.consultRetry.setOnClickListener(v->{if(model.state().getValue().uncertain)model.retryMutation();else model.retry();});
        header.consultPrimary.setOnClickListener(v->{switch(model.state().getValue().screen){case SLOTS:model.newSlot();break;case FORM:model.reviewSave();break;case REVIEW:model.confirm();break;default:break;}});
        header.consultMove.setOnClickListener(v->model.beginMove());header.consultCancel.setOnClickListener(v->model.reviewCancel());more.setOnClickListener(v->model.more());
        ViewCompat.setAccessibilityHeading(header.consultHeading,true);ViewCompat.setAccessibilityHeading(header.consultHistoryHeading,true);
        SharedMotion.install(view);SharedMotion.install(header.getRoot());SharedMotion.install(more);
        model.state().observe(getViewLifecycleOwner(),this::render);
        ((GoServeApplication)requireActivity().getApplication()).accountInvalidations().observe(getViewLifecycleOwner(),ignored->{model.checkCurrentAccount();if(model.state().getValue().blocked)dismissPickers();});
        String target=getArguments()==null?null:getArguments().getString("appointment_id");
        if(target!=null && !getArguments().getBoolean("target_opened")){getArguments().putBoolean("target_opened",true);model.open(target);}else model.ensureLoaded();
    }
    private TextWatcher watch(Runnable change){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(!rendering && header!=null)change.run();}public void afterTextChanged(Editable e){}};}
    private void saveForm(){if(rendering || header==null)return;ConsultationViewModel.State s=model.state().getValue();model.form(s.start,s.end,header.consultCapacity.getText().toString(),header.consultActive.isChecked());}
    private void pick(boolean start){
        if(model.state().getValue().busy || model.state().getValue().blocked)return;
        String prior=start?model.state().getValue().start:model.state().getValue().end;
        ZonedDateTime initial=prior.isEmpty()?ZonedDateTime.now():Instant.parse(prior).atZone(ZoneId.systemDefault());
        datePicker=new DatePickerDialog(requireContext(),(picker,year,month,day)->{
            if(binding==null || !isResumed() || model.state().getValue().blocked)return;
            timePicker=new TimePickerDialog(requireContext(),(clock,hour,minute)->{
                if(binding==null || !isResumed())return;model.checkCurrentAccount();if(model.state().getValue().blocked)return;
                String chosen=java.time.LocalDateTime.of(year,month+1,day,hour,minute).atZone(ZoneId.systemDefault()).toInstant().toString();
                ConsultationViewModel.State s=model.state().getValue();model.form(start?chosen:s.start,start?s.end:chosen,s.capacity,s.active);render(s);
            },initial.getHour(),initial.getMinute(),android.text.format.DateFormat.is24HourFormat(requireContext()));timePicker.show();
        },initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth());datePicker.show();
    }
    private void render(ConsultationViewModel.State s){
        if(binding==null)return;rendering=true;
        boolean list=s.screen==ConsultationViewModel.Screen.SLOTS,detail=s.screen==ConsultationViewModel.Screen.DETAIL,review=s.screen==ConsultationViewModel.Screen.REVIEW,form=s.screen==ConsultationViewModel.Screen.FORM,appointments=s.screen==ConsultationViewModel.Screen.APPOINTMENTS;
        boolean enabled=!s.busy && !s.blocked && !s.uncertain;
        binding.consultToolbar.setText(review?reviewTitle(s.review):form?R.string.consultation_live_edit:detail?R.string.consultation_live_detail:appointments?model.student?R.string.consultation_live_appointments:R.string.consultation_live_office_appointments:R.string.consultation_live_title);
        binding.consultBack.setEnabled(!model.mutationPending());binding.consultRefresh.setEnabled(enabled && !form && !(review && "save".equals(s.review)));
        visible(header.consultAppointments,list && !s.moving && !s.blocked);header.consultAppointments.setText(model.student?R.string.consultation_live_appointments:R.string.consultation_live_office_appointments);header.consultAppointments.setEnabled(enabled);
        header.consultHeading.setText(review?reviewTitle(s.review):detail?R.string.consultation_live_detail:form?R.string.consultation_live_edit:appointments?model.student?R.string.consultation_live_appointments:R.string.consultation_live_office_appointments:model.student?R.string.consultation_live_find:"DEP-CCS".equals(s.office)?R.string.consultation_live_secretary_schedule:R.string.consultation_live_guidance_schedule);
        header.consultIntro.setText(s.moving?R.string.consultation_live_move_intro:model.student?R.string.consultation_live_intro:R.string.consultation_live_staff_intro);visible(header.consultIntro,list || form);
        visible(header.consultOffice,list && model.student && !s.moving && !s.blocked);visible(header.consultDepartment,model.departmentAllowed);
        header.consultOffice.check("DEP-CCS".equals(s.office)?R.id.consultDepartment:R.id.consultGuidance);header.consultGuidance.setEnabled(enabled);header.consultDepartment.setEnabled(enabled);
        visible(header.consultConnection,liveUnavailable && !s.blocked);visible(header.consultProgress,s.busy);visible(header.consultError,s.error!=null);header.consultError.setText(error(s));
        visible(header.consultNotice,s.notice!=null && !s.blocked);
        visible(header.consultRetry,s.error!=null && !s.blocked && !s.busy && !form);header.consultRetry.setText(s.uncertain?R.string.consultation_live_retry_mutation:R.string.consultation_live_retry);
        boolean empty=list?s.slots.isEmpty():appointments && s.appointments.isEmpty();visible(header.consultEmpty,empty && !s.busy && !s.blocked && s.error==null);header.consultEmpty.setText(appointments?R.string.consultation_live_empty_appointments:model.student?R.string.consultation_live_empty_slots:R.string.consultation_live_empty_staff);
        visible(header.consultSummary,(detail && s.appointment!=null || review) && !s.blocked);header.consultSummary.setText(summary(s));
        visible(header.consultForm,form && !s.blocked);header.consultStart.setText(s.start.isEmpty()?getString(R.string.consultation_live_choose_datetime):when(s.start));header.consultEnd.setText(s.end.isEmpty()?getString(R.string.consultation_live_choose_datetime):when(s.end));
        text(header.consultCapacity,s.capacity);header.consultActive.setChecked(s.active);header.consultStart.setEnabled(enabled);header.consultEnd.setEnabled(enabled);header.consultCapacity.setEnabled(enabled);header.consultActive.setEnabled(enabled);
        visible(header.consultReasonGroup,!s.blocked && (s.moving || review && "cancel".equals(s.review)));text(header.consultReason,s.reason);header.consultReason.setEnabled(enabled);
        visible(header.consultPolicy,(review || form) && !s.blocked);
        visible(header.consultPrimary,!s.blocked && (form || review || list && !model.student));header.consultPrimary.setEnabled(enabled && (!review || s.error==null));header.consultPrimary.setText(list?R.string.consultation_live_add:form?R.string.consultation_live_review:confirmTitle(s.review));
        boolean canChange=detail && s.appointment!=null && s.selected!=null && "booked".equals(s.appointment.state) && !s.blocked;
        visible(header.consultMove,canChange);visible(header.consultCancel,canChange);header.consultMove.setEnabled(enabled);header.consultCancel.setEnabled(enabled);
        visible(header.consultHistoryHeading,detail && s.appointment!=null && !s.blocked);visible(more,s.nextId!=null && s.error==null && !s.blocked && (list || detail || appointments));more.setEnabled(enabled);
        List<Object> values=new ArrayList<>();if(!s.blocked){if(list)values.addAll(s.slots);else if(appointments)values.addAll(s.appointments);else if(detail)values.addAll(s.events);}rows.update(values,enabled);
        back.setEnabled(true);rendering=false;
    }
    private String summary(ConsultationViewModel.State s){
        if(s.screen==ConsultationViewModel.Screen.REVIEW && "save".equals(s.review))return getString(R.string.consultation_live_save_summary,when(s.start),when(s.end),s.capacity,getString(s.active?R.string.consultation_live_active_state:R.string.consultation_live_inactive_state));
        String value=s.appointment==null?"":appointment(s.appointment);
        if(s.selected!=null)value+=(value.isEmpty()?"":"\n\n")+slot(s.selected);
        return value;
    }
    private int error(ConsultationViewModel.State s){if(s.uncertain)return R.string.consultation_live_error_unknown;if(s.error==null)return R.string.consultation_live_error_network;switch(s.error){case "CAPACITY_FULL":return R.string.consultation_live_error_capacity;case "STALE_VERSION":case "STALE_RESPONSE":return R.string.consultation_live_error_stale;case "POLICY_BLOCKED":return R.string.consultation_live_error_policy;case "CONFIGURATION_REQUIRED":return R.string.consultation_live_error_configuration;case "FORBIDDEN":return R.string.consultation_live_error_forbidden;case "STALE_ACCOUNT":return R.string.consultation_live_error_account;case "NOT_FOUND":return R.string.consultation_live_error_missing;case "VALIDATION_FAILED":return R.string.consultation_live_error_validation;case "INVALID_STATE":return R.string.consultation_live_error_state;case "ALREADY_BOOKED":return R.string.consultation_live_error_duplicate;default:return R.string.consultation_live_error_network;}}
    private int reviewTitle(String review){return "save".equals(review)?R.string.consultation_live_review_save:"cancel".equals(review)?R.string.consultation_live_review_cancel:"move".equals(review)?R.string.consultation_live_review_move:R.string.consultation_live_review_book;}
    private int confirmTitle(String review){return "save".equals(review)?R.string.consultation_live_confirm_save:"cancel".equals(review)?R.string.consultation_live_confirm_cancel:"move".equals(review)?R.string.consultation_live_confirm_move:R.string.consultation_live_confirm_book;}
    private String slot(Slot s){return getString(R.string.consultation_live_slot,getString("DEP-CCS".equals(s.officeId)?R.string.consultation_live_department:R.string.consultation_live_guidance),when(s.startAt),when(s.endAt),s.remaining(),s.capacity,getString(s.active?R.string.consultation_live_active_state:R.string.consultation_live_inactive_state),s.ownerId);}
    private String appointment(Appointment a){return getString(R.string.consultation_live_appointment,a.id,getString("booked".equals(a.state)?R.string.consultation_live_booked:R.string.consultation_live_cancelled),when(a.createdAt));}
    private String when(String instant){if(instant==null || instant.isEmpty())return "";return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM,FormatStyle.SHORT).format(Instant.parse(instant).atZone(ZoneId.systemDefault()));}
    private static void visible(View view,boolean show){view.setVisibility(show?View.VISIBLE:View.GONE);}
    private static void text(TextView view,String value){if(!view.getText().toString().equals(value))view.setText(value);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private final class Rows extends RecyclerView.Adapter<Row>{
        private List<Object> values=List.of();private boolean enabled;
        void update(List<Object> items,boolean canOpen){values=items;enabled=canOpen;notifyDataSetChanged();}
        @Override public Row onCreateViewHolder(ViewGroup parent,int type){return new Row(ItemConsultationLiveBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));}
        @Override public int getItemCount(){return values.size();}
        @Override public void onBindViewHolder(Row holder,int position){Object item=values.get(position);ItemConsultationLiveBinding row=holder.binding;
            if(item instanceof Slot){Slot value=(Slot)item;row.consultRowTitle.setText(when(value.startAt));row.consultRowBody.setText(slot(value));}
            else if(item instanceof Appointment){Appointment value=(Appointment)item;row.consultRowTitle.setText(appointment(value));row.consultRowBody.setText(getString(R.string.consultation_live_slot_id,value.slotId));}
            else{Event value=(Event)item;row.consultRowTitle.setText("booked".equals(value.action)?R.string.consultation_live_event_book:"cancel".equals(value.action)?R.string.consultation_live_event_cancel:R.string.consultation_live_event_move);row.consultRowBody.setText(getString(R.string.consultation_live_event,when(value.createdAt),value.actorId,value.appointmentId,value.oldSlotId==null?getString(R.string.consultation_live_none):value.oldSlotId,value.newSlotId==null?getString(R.string.consultation_live_none):value.newSlotId,value.reason==null?"":value.reason));}
            boolean clickable=enabled && !(item instanceof Event);row.getRoot().setEnabled(clickable);row.getRoot().setFocusable(clickable);row.getRoot().setClickable(clickable);row.getRoot().setContentDescription(row.consultRowTitle.getText()+"\n"+row.consultRowBody.getText());row.getRoot().setOnClickListener(clickable?v->{if(item instanceof Slot)model.select((Slot)item);else model.open(((Appointment)item).id);}:null);
        }
    }
    private static class Row extends RecyclerView.ViewHolder{final ItemConsultationLiveBinding binding;Row(ItemConsultationLiveBinding binding){super(binding.getRoot());this.binding=binding;}}
    private static class Fixed extends RecyclerView.Adapter<RecyclerView.ViewHolder>{final View view;Fixed(View view){this.view=view;}@Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,int type){return new RecyclerView.ViewHolder(view){};}@Override public void onBindViewHolder(RecyclerView.ViewHolder holder,int position){}@Override public int getItemCount(){return 1;}}
    @Override public void onStart(){super.onStart();model.checkCurrentAccount();if(!model.state().getValue().busy)model.refresh();
        RealtimeDataSource.Session session=((GoServeApplication)requireActivity().getApplication()).realtimeSession();if(session==null || subscription!=null)return;subscribedSession=session;
        subscription=session.subscribe(Set.of("consultation_slots","appointments","appointment_events","notifications"),new RealtimeDataSource.Listener(){public void onInvalidated(RealtimeDataSource.Invalidation event){if(binding!=null && subscribedSession==session && event!=null && event.generation==session.generation())model.refresh();}public void onStateChanged(RealtimeDataSource.State state){if(binding==null || subscribedSession!=session)return;liveUnavailable=state==RealtimeDataSource.State.RECONNECTING || state==RealtimeDataSource.State.DEGRADED || state==RealtimeDataSource.State.FAILED || state==RealtimeDataSource.State.AUTH_REQUIRED;render(model.state().getValue());}});
    }
    private void dismissPickers(){if(datePicker!=null)datePicker.dismiss();if(timePicker!=null)timePicker.dismiss();datePicker=null;timePicker=null;}
    @Override public void onStop(){dismissPickers();subscribedSession=null;if(subscription!=null)subscription.close();subscription=null;super.onStop();}
    @Override public void onDestroyView(){dismissPickers();if(binding!=null)binding.consultList.setAdapter(null);binding=null;header=null;rows=null;more=null;super.onDestroyView();}
}
