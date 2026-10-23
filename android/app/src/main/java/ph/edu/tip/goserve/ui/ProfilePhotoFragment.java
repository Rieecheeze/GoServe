package ph.edu.tip.goserve.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.databinding.FragmentProfilePhotoBinding;

public final class ProfilePhotoFragment extends Fragment {
    private FragmentProfilePhotoBinding binding;
    private ViewModelProvider.Factory factory;private ProfilePhotoViewModel model;private Bitmap preview;
    private final ActivityResultLauncher<PickVisualMediaRequest> picker=registerForActivityResult(new ActivityResultContracts.PickVisualMedia(),uri->{
        if(uri!=null && model!=null){android.content.Context context=requireContext().getApplicationContext();model.select(()->context.getContentResolver().openInputStream(uri));}
    });
    public ProfilePhotoFragment(){}
    ProfilePhotoFragment(ViewModelProvider.Factory factory){this.factory=factory;}
    @Override public View onCreateView(LayoutInflater inflater,ViewGroup parent,Bundle saved){binding=FragmentProfilePhotoBinding.inflate(inflater,parent,false);return binding.getRoot();}
    @Override public void onViewCreated(View view,Bundle saved){
        model=new ViewModelProvider(this,factory==null?getDefaultViewModelProviderFactory():factory).get(ProfilePhotoViewModel.class);
        binding.photoImage.setSaveFromParentEnabled(false);
        androidx.core.view.ViewCompat.setAccessibilityHeading(binding.photoHeading,true);
        binding.photoChoose.setOnClickListener(v->{
            if(!model.canEdit())return;
            try{picker.launch(new PickVisualMediaRequest.Builder().setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());}
            catch(android.content.ActivityNotFoundException | SecurityException unavailable){model.select(()->{throw new java.io.IOException();});}
        });
        binding.photoRemove.setOnClickListener(v->model.remove());
        binding.photoSave.setOnClickListener(v->model.save());
        binding.photoReload.setOnClickListener(v->model.reload());
        binding.photoBack.setOnClickListener(v->{if(model.state().getValue().mode!=ProfilePhotoViewModel.Mode.SAVING)requireActivity().getOnBackPressedDispatcher().onBackPressed();});
        OnBackPressedCallback back=new OnBackPressedCallback(false){@Override public void handleOnBackPressed(){}};
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),back);
        model.state().observe(getViewLifecycleOwner(),state->{render(state);back.setEnabled(state.mode==ProfilePhotoViewModel.Mode.SAVING);});
        ((GoServeApplication)requireActivity().getApplication()).accountInvalidations().observe(getViewLifecycleOwner(),ignored->model.checkCurrentAccount());
        SharedMotion.install(view);
    }
    @Override public void onStart(){super.onStart();((MainActivity)requireActivity()).setRoleNavigationVisible(false);}
    @Override public void onResume(){super.onResume();model.checkCurrentAccount();}
    private void render(ProfilePhotoViewModel.State state){
        if(binding==null)return;
        byte[] bytes=model.image();Bitmap next=bytes==null?null:BitmapFactory.decodeByteArray(bytes,0,bytes.length);
        if(next!=null && next.getWidth()!=next.getHeight()){
            int edge=Math.min(next.getWidth(),next.getHeight());Bitmap square=Bitmap.createBitmap(next,(next.getWidth()-edge)/2,(next.getHeight()-edge)/2,edge,edge);next.recycle();next=square;
        }
        if(next==null)binding.photoImage.setImageResource(R.drawable.ic_profile_person);
        else{androidx.core.graphics.drawable.RoundedBitmapDrawable drawable=androidx.core.graphics.drawable.RoundedBitmapDrawableFactory.create(getResources(),next);drawable.setCircular(true);drawable.setAntiAlias(true);binding.photoImage.setImageDrawable(drawable);}
        if(preview!=null)preview.recycle();preview=next;
        binding.photoImage.setContentDescription(getString(next==null?R.string.profile_photo_empty_description:R.string.profile_photo_description));
        binding.photoChoose.setEnabled(model.canEdit());binding.photoRemove.setEnabled(model.canEdit() && bytes!=null);
        binding.photoSave.setEnabled(model.canSave());binding.photoSave.setText(state.mode==ProfilePhotoViewModel.Mode.SAVING?R.string.profile_photo_saving:R.string.profile_photo_save);
        binding.photoReload.setEnabled(!state.busy() && state.mode!=ProfilePhotoViewModel.Mode.UNAVAILABLE);
        binding.photoBack.setEnabled(state.mode!=ProfilePhotoViewModel.Mode.SAVING);
        binding.photoProgress.setVisibility(state.busy()?View.VISIBLE:View.GONE);
        int status=state.mode==ProfilePhotoViewModel.Mode.LOADING?R.string.profile_photo_loading:
                state.mode==ProfilePhotoViewModel.Mode.SELECTING?R.string.profile_photo_selecting:
                state.mode==ProfilePhotoViewModel.Mode.SAVING?R.string.profile_photo_saving:
                state.saved?R.string.profile_photo_saved:model.canSave()?(bytes==null?R.string.profile_photo_remove_draft:R.string.profile_photo_draft):
                state.photo==null?0:state.photo.path==null?R.string.profile_photo_empty:R.string.profile_photo_current;
        binding.photoStatus.setText(status==0?"":getString(status));
        String error=state.error==null?"":getString(message(state.error));
        boolean reveal=!error.isEmpty() && !error.equals(binding.photoError.getText().toString());
        binding.photoError.setText(error);binding.photoError.setVisibility(error.isEmpty()?View.GONE:View.VISIBLE);
        if(reveal){View root=binding.getRoot();androidx.core.view.OneShotPreDrawListener.add(binding.photoError,()->{if(binding!=null && binding.getRoot()==root && binding.photoError.isShown())binding.photoError.requestRectangleOnScreen(new android.graphics.Rect(0,0,binding.photoError.getWidth(),binding.photoError.getHeight()),true);});}
    }
    private static int message(String error){
        switch(error){
            case "PHOTO_INVALID":return R.string.profile_photo_invalid;
            case "PHOTO_SAVE_UNCERTAIN":return R.string.profile_photo_uncertain;
            case "STALE_VERSION":return R.string.profile_photo_stale;
            case "PHOTO_RELOAD_REQUIRED":return R.string.profile_photo_reload_required;
            case "PHOTO_ACCESS_REQUIRED":return R.string.profile_photo_access_required;
            case "SESSION_EXPIRED":return R.string.profile_photo_session_expired;
            default:return R.string.profile_photo_load_failed;
        }
    }
    @Override public void onDestroyView(){
        if(binding!=null)binding.photoImage.setImageDrawable(null);
        if(preview!=null){preview.recycle();preview=null;}
        ((MainActivity)requireActivity()).setRoleNavigationVisible(true);binding=null;super.onDestroyView();
    }
}
