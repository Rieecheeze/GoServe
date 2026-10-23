package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.*;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.*;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.RealtimeDataSource;
import ph.edu.tip.goserve.domain.InquiryData;
import ph.edu.tip.goserve.domain.Office;
import ph.edu.tip.goserve.databinding.FragmentInquiryThreadLiveBinding;
import ph.edu.tip.goserve.databinding.ItemInquiryMessageLiveBinding;

public final class ThreadFragment extends Fragment {
    private ViewModelProvider.Factory factory;
    private FragmentInquiryThreadLiveBinding binding;
    private InquiryViewModel model;
    private InquiryData.MessagePage shown;
    private final Map<Long,TextView> bodies=new LinkedHashMap<>();
    private final Map<Long,BitSet> seenLines=new HashMap<>();
    private boolean rendering,liveUnavailable;
    private android.app.Dialog dialog;
    private RealtimeDataSource.Subscription subscription;
    private RealtimeDataSource.Session subscribedSession;
    private final ViewTreeObserver.OnScrollChangedListener scroll=this::collectVisible;
    private final ViewTreeObserver.OnGlobalLayoutListener layout=this::collectVisible;
    public ThreadFragment() {}
    ThreadFragment(ViewModelProvider.Factory factory) { this.factory=factory; }
    public static ThreadFragment create(String id) { ThreadFragment f=new ThreadFragment(); Bundle b=new Bundle(); b.putString("thread_id",id); f.setArguments(b); return f; }
    public static ThreadFragment newInquiry(String office) { ThreadFragment f=new ThreadFragment(); Bundle b=new Bundle(); b.putString("office_id",office); f.setArguments(b); return f; }
    public boolean mutationPending() { return model!=null && model.mutationPending(); }
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved) { binding=FragmentInquiryThreadLiveBinding.inflate(inflater,parent,false); return binding.getRoot(); }
    @Override public void onViewCreated(View view,Bundle saved) {
        model=new ViewModelProvider(this,factory==null?getDefaultViewModelProviderFactory():factory).get(InquiryViewModel.class);
        OnBackPressedCallback lock=new OnBackPressedCallback(false){public void handleOnBackPressed(){}};
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),lock);
        binding.inquiryThreadBack.setOnClickListener(v->requireActivity().getOnBackPressedDispatcher().onBackPressed());
        binding.inquiryThreadRefresh.setOnClickListener(v->model.refresh());
        binding.inquiryThreadRetry.setOnClickListener(v->model.refresh());
        binding.inquiryMessageMore.setOnClickListener(v->model.loadMore());
        binding.inquiryOfficeSelect.setOnClickListener(v->chooseOffice());
        binding.inquirySend.setOnClickListener(v->model.send());
        binding.inquiryDiscard.setOnClickListener(v->discard());
        binding.inquiryMessageInput.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        binding.inquiryMessageInput.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void afterTextChanged(Editable e){} public void onTextChanged(CharSequence s,int st,int before,int count){if(!rendering)model.editBody(s.toString());}});
        binding.inquiryThreadScroll.getViewTreeObserver().addOnScrollChangedListener(scroll);
        binding.inquiryThreadScroll.getViewTreeObserver().addOnGlobalLayoutListener(layout);
        model.state().observe(getViewLifecycleOwner(),s->{render(s);lock.setEnabled(model.mutationPending());});
        ((GoServeApplication)requireActivity().getApplication()).accountInvalidations().observe(getViewLifecycleOwner(),ignored->{model.checkCurrentAccount();if(model.state().getValue().blocked)dismissDialog();});
        Bundle args=getArguments(); model.conversation(args==null?null:args.getString("thread_id"),args==null?null:args.getString("office_id"));
        SharedMotion.install(view);
    }
    private void chooseOffice() {
        InquiryViewModel.State s=model.state().getValue(); if(s.busy() || s.blocked || s.uncertain || s.threadId!=null || s.offices.isEmpty())return;
        List<Office> offices=s.offices; String[] labels=new String[offices.size()];
        for(int i=0;i<labels.length;i++)labels[i]=officeLabel(offices.get(i));
        dialog=new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.inquiry_live_office).setItems(labels,(d,index)->{
            if(binding!=null && isResumed())model.selectOffice(offices.get(index).id);
        }).setNegativeButton(android.R.string.cancel,null).show();
    }
    private void discard() {
        if(model.state().getValue().sending)return;
        dialog=new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.inquiry_live_discard)
            .setMessage(R.string.inquiry_live_discard_body).setNegativeButton(android.R.string.cancel,null)
            .setPositiveButton(R.string.inquiry_live_discard,(d,w)->{if(binding!=null && isResumed())model.discardLocalDraft();}).show();
    }
    private String officeLabel(Office office) { return "DEP-CCS".equals(office.id)?getString(R.string.inquiry_live_ccs_secretary,office.name):office.name; }
    private String officeName(InquiryViewModel.State s,String id) { for(Office o:s.offices)if(o.id.equals(id))return o.name;return id==null?getString(R.string.inquiry_live_select_office):id; }
    private void render(InquiryViewModel.State s) {
        if(binding==null || s==null)return;
        boolean existing=s.thread!=null,enabled=!s.busy() && !s.blocked;
        binding.inquiryThreadTitle.setText(existing?getString(R.string.inquiry_live_thread_title,officeName(s,s.thread.officeId)):getString(R.string.inquiry_live_new));
        visible(binding.inquiryThreadProgress,s.loading); visible(binding.inquirySending,s.sending);
        visible(binding.inquiryThreadError,s.error!=null || s.uncertain);
        binding.inquiryThreadError.setText(s.uncertain?getString(R.string.inquiry_live_uncertain):s.error==null?"":getString(errorText(s.error)));
        visible(binding.inquiryThreadConnection,liveUnavailable);
        visible(binding.inquiryOfficeField,!existing && !s.blocked);
        visible(binding.inquiryThreadRetry,s.error!=null && !s.uncertain && !s.blocked); binding.inquiryThreadRetry.setEnabled(enabled);
        String destination=officeName(s,s.officeId); for(Office office:s.offices)if(office.id.equals(s.officeId))destination=officeLabel(office);
        binding.inquiryOfficeSelect.setText(s.offices.isEmpty()?getString(R.string.inquiry_live_no_offices):destination);
        binding.inquiryOfficeSelect.setEnabled(enabled && !s.uncertain && !s.offices.isEmpty());
        visible(binding.inquiryThreadNotice,!s.blocked);
        binding.inquiryThreadRecipient.setText(existing && s.thread.assignedTo!=null?getString(R.string.inquiry_live_assigned,s.thread.assignedTo):getString(R.string.inquiry_live_recipient));
        binding.inquiryThreadScope.setText(existing?R.string.inquiry_live_thread_scope:R.string.inquiry_live_recipient_body);
        visible(binding.inquiryAssignment,existing && !model.student());
        binding.inquiryThreadIdentity.setText(existing?getString(R.string.inquiry_live_thread_identity,s.thread.id):"");
        rendering=true; if(!s.body.contentEquals(binding.inquiryMessageInput.getText()))binding.inquiryMessageInput.setText(s.body); rendering=false;
        binding.inquiryMessageInput.setEnabled(enabled && !s.uncertain);
        binding.inquiryMessageInput.setHint(existing?R.string.inquiry_live_reply_hint:R.string.inquiry_live_message_hint);
        binding.inquirySend.setText(s.uncertain?R.string.inquiry_live_retry_send:existing?model.student()?R.string.inquiry_live_send_reply:R.string.inquiry_live_send_office_reply:R.string.inquiry_live_send);
        binding.inquirySend.setEnabled(enabled);
        binding.inquiryThreadBack.setEnabled(!model.mutationPending());
        binding.inquiryThreadRefresh.setEnabled(enabled);
        visible(binding.inquiryDiscard,s.uncertain); binding.inquiryDiscard.setEnabled(enabled);
        visible(binding.inquiryMessageMore,s.page!=null && s.page.nextSequence!=null);
        binding.inquiryMessageMore.setEnabled(enabled && s.page!=null && s.page.nextSequence!=null && s.readThrough>=s.page.nextSequence);
        if(shown!=s.page) {
            shown=s.page; bodies.clear(); seenLines.clear(); binding.inquiryMessageRows.removeAllViews();
            if(shown!=null)for(InquiryData.Message message:shown.items) {
                ItemInquiryMessageLiveBinding row=ItemInquiryMessageLiveBinding.inflate(getLayoutInflater(),binding.inquiryMessageRows,false);
                row.inquiryMessageSender.setText(message.senderId.equals(model.accountId())?getString(R.string.inquiry_live_you,message.createdAt):getString(R.string.inquiry_live_sender,message.senderId,message.createdAt));
                row.inquiryMessageBody.setText(message.body); row.inquiryMessageIdentity.setText(getString(R.string.inquiry_live_identity,message.id,message.sequence));
                binding.inquiryMessageRows.addView(row.getRoot()); bodies.put(message.sequence,row.inquiryMessageBody);
            }
            SharedMotion.install(binding.inquiryMessageRows);
        }
        binding.inquiryThreadScroll.post(this::collectVisible);
    }
    private void collectVisible() {
        if(binding==null || !isResumed() || shown==null || model.state().getValue().page!=shown)return;
        List<Long> complete=new ArrayList<>(); Rect rect=new Rect(); int[] location=new int[2];
        for(Map.Entry<Long,TextView> entry:bodies.entrySet()) {
            TextView text=entry.getValue(); android.text.Layout lines=text.getLayout();
            if(lines==null || !text.getGlobalVisibleRect(rect) || rect.width()<text.getWidth())continue;
            text.getLocationOnScreen(location); BitSet seen=seenLines.computeIfAbsent(entry.getKey(),ignored->new BitSet());
            for(int i=0;i<lines.getLineCount();i++) {
                int top=location[1]+text.getTotalPaddingTop()+lines.getLineTop(i)-text.getScrollY();
                int bottom=location[1]+text.getTotalPaddingTop()+lines.getLineBottom(i)-text.getScrollY();
                if(top>=rect.top && bottom<=rect.bottom)seen.set(i);
            }
            if(seen.nextClearBit(0)>=lines.getLineCount())complete.add(entry.getKey());
        }
        if(!complete.isEmpty())model.displayed(shown,complete);
    }
    static int errorText(String code) {
        switch(code) { case "FORBIDDEN":return R.string.inquiry_live_forbidden;case "NOT_FOUND":return R.string.inquiry_live_not_found;
            case "STALE_ACCOUNT":return R.string.inquiry_live_account;case "VALIDATION_FAILED":return R.string.inquiry_live_validation;
            case "CONFIGURATION_REQUIRED":return R.string.inquiry_live_configuration;default:return R.string.inquiry_live_failed; }
    }
    @Override public void onResume() { super.onResume(); model.setVisible(true); binding.inquiryThreadScroll.post(this::collectVisible); }
    @Override public void onPause() { model.setVisible(false); seenLines.clear(); dismissDialog(); super.onPause(); }
    @Override public void onStart() {
        super.onStart(); model.checkCurrentAccount(); RealtimeDataSource.Session session=((GoServeApplication)requireActivity().getApplication()).realtimeSession();
        if(session==null || subscription!=null)return; subscribedSession=session;
        subscription=session.subscribe(Set.of("inquiry_threads","inquiry_messages","inquiry_reads"),new RealtimeDataSource.Listener(){
            public void onInvalidated(RealtimeDataSource.Invalidation e){if(binding!=null && subscribedSession==session && e!=null && e.generation==session.generation())model.refresh();}
            public void onStateChanged(RealtimeDataSource.State state){if(binding!=null && subscribedSession==session){liveUnavailable=state==RealtimeDataSource.State.RECONNECTING || state==RealtimeDataSource.State.DEGRADED || state==RealtimeDataSource.State.FAILED || state==RealtimeDataSource.State.AUTH_REQUIRED;render(model.state().getValue());}}
        });
    }
    @Override public void onStop() { subscribedSession=null;if(subscription!=null)subscription.close();subscription=null;super.onStop(); }
    private void dismissDialog(){if(dialog!=null)dialog.dismiss();dialog=null;}
    @Override public void onDestroyView() {
        dismissDialog(); model.setVisible(false);
        if(binding!=null){binding.inquiryThreadScroll.getViewTreeObserver().removeOnScrollChangedListener(scroll);binding.inquiryThreadScroll.getViewTreeObserver().removeOnGlobalLayoutListener(layout);}
        bodies.clear();seenLines.clear();shown=null;binding=null;super.onDestroyView();
    }
    private static void visible(View view,boolean show){view.setVisibility(show?View.VISIBLE:View.GONE);}
}
