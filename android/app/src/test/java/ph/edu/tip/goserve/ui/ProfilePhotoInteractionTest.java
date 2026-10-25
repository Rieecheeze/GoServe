package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.util.ReflectionHelpers;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,qualifiers="w390dp-h844dp-port-notnight-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class ProfilePhotoInteractionTest {
    ActivityController<MainActivity> controller;MainActivity activity;
    final ProfilePhotoViewModelTest.Queue work=new ProfilePhotoViewModelTest.Queue(),ui=new ProfilePhotoViewModelTest.Queue();
    final ProfilePhotoViewModelTest.Fake actions=new ProfilePhotoViewModelTest.Fake();ProfilePhotoViewModel vm;
    @Before public void reset(){ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class,"_instance",null);}
    @After public void close(){if(controller!=null)controller.close();RuntimeEnvironment.setFontScale(1f);androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);}
    @Test public void settingsEntryAndDirectRouteKeepProfilePictureUnavailableInPreview(){
        host();view(R.id.accountButton).performClick();idle();
        assertTrue(current() instanceof SettingsGroupFragment);assertNull(activity.profile());assertFalse(view(R.id.profilePhotoButton).isEnabled());
        activity.navigate(new ProfilePhotoFragment(),true);idle();
        assertTrue(current() instanceof ProfilePhotoFragment);assertEquals(ProfilePhotoViewModel.Mode.UNAVAILABLE,new ViewModelProvider(current()).get(ProfilePhotoViewModel.class).state().getValue().mode);
        assertFalse(view(R.id.photoChoose).isEnabled());assertFalse(view(R.id.photoSave).isEnabled());assertFalse(view(R.id.photoReload).isEnabled());
        assertEquals(View.VISIBLE,view(R.id.photoError).getVisibility());assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }
    @Test public void loadingKeepsEditingDisabledThenRendersTheSavedPhoto(){launch();assertFalse(view(R.id.photoChoose).isEnabled());load();assertTrue(view(R.id.photoChoose).isEnabled());assertFalse(view(R.id.photoSave).isEnabled());assertEquals(activity.getString(R.string.profile_photo_current),text(R.id.photoStatus));assertFalse(view(R.id.photoImage).isSaveEnabled());assertEquals(View.GONE,view(R.id.bottomNavigation).getVisibility());}
    @Test public void removeStagesAChangeAndSaveIsExplicit(){launch();load();view(R.id.photoRemove).performClick();assertEquals(0,actions.saves);assertTrue(view(R.id.photoSave).isEnabled());assertEquals(activity.getString(R.string.profile_photo_remove_draft),text(R.id.photoStatus));view(R.id.photoSave).performClick();work.run();ui.run();idle();assertEquals(1,actions.saves);assertEquals(activity.getString(R.string.profile_photo_saved),text(R.id.photoStatus));}
    @Test public void saveLocksNavigationAndIgnoresDoubleTap(){launch();load();view(R.id.photoRemove).performClick();view(R.id.photoSave).performClick();view(R.id.photoSave).performClick();assertEquals(1,work.size());assertFalse(view(R.id.photoBack).isEnabled());assertFalse(view(R.id.photoChoose).isEnabled());assertFalse(view(R.id.photoReload).isEnabled());activity.getOnBackPressedDispatcher().onBackPressed();assertTrue(current() instanceof ProfilePhotoFragment);}
    @Test public void failureShowsSafeRecoveryAndReloadDiscardsTheUncertainDraft(){launch();load();view(R.id.photoRemove).performClick();actions.failure=new java.io.IOException("private URL and token");view(R.id.photoSave).performClick();work.run();ui.run();idle();assertEquals(activity.getString(R.string.profile_photo_uncertain),text(R.id.photoError));assertTrue(view(R.id.photoReload).isEnabled());assertFalse(view(R.id.photoChoose).isEnabled());actions.failure=null;view(R.id.photoReload).performClick();load();assertFalse(view(R.id.photoSave).isEnabled());assertTrue(view(R.id.photoChoose).isEnabled());}
    @Test public void replacingTheAccountClearsThePreviewAndDisablesEdits(){launch();load();actions.current=false;vm.checkCurrentAccount();assertFalse(view(R.id.photoChoose).isEnabled());assertFalse(view(R.id.photoSave).isEnabled());assertEquals(activity.getString(R.string.profile_photo_empty_description),view(R.id.photoImage).getContentDescription());}
    @Test public void normalBackRestoresTheSettingsNavigation(){launch();load();view(R.id.photoBack).performClick();idle();assertTrue(current() instanceof MoreFragment);assertEquals(View.VISIBLE,view(R.id.bottomNavigation).getVisibility());}
    @Test public void circularPreviewKeepsTheParentSurfaceOpaque(){launch();load();View root=current().requireView();root.measure(View.MeasureSpec.makeMeasureSpec(390,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(740,View.MeasureSpec.EXACTLY));root.layout(0,0,390,740);android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(390,740,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(b));int[] position=new int[2],origin=new int[2];view(R.id.photoImage).getLocationInWindow(position);root.getLocationInWindow(origin);int x=position[0]-origin[0],y=position[1]-origin[1];assertEquals(255,android.graphics.Color.alpha(b.getPixel(x+1,y+1)));assertEquals("Wide photos must fill the circular preview",b.getPixel(x+64,y+64),b.getPixel(x+110,y+64));b.recycle();}
    @Test public void pickerStartsOnlyAnImageSelectionWithoutSaving(){launch();load();view(R.id.photoChoose).performClick();org.robolectric.shadows.ShadowActivity.IntentForResult result=Shadows.shadowOf(activity).getNextStartedActivityForResult();assertNotNull(result);assertEquals("image/*",result.intent.getType());assertEquals(0,actions.saves);assertEquals(0,work.size());}
    @Test public void destroyingTheViewClearsItsImageButRetainsTheMemoryDraft(){launch();load();vm.select(()->new java.io.ByteArrayInputStream(ProfilePhotoViewModelTest.image()));work.run();ui.run();idle();android.widget.ImageView old=(android.widget.ImageView)view(R.id.photoImage);androidx.fragment.app.Fragment f=current();activity.getSupportFragmentManager().beginTransaction().detach(f).commitNow();assertNull(old.getDrawable());activity.getSupportFragmentManager().beginTransaction().attach(f).commitNow();idle();assertTrue(view(R.id.photoSave).isEnabled());assertEquals(0,work.size());}
    @Test @Config(qualifiers="w320dp-h480dp-port-notnight-mdpi") public void shortLargeTextScreenKeepsSaveAndScrolledFeedbackReachable()throws Exception{RuntimeEnvironment.setFontScale(2f);launch();load();actions.failure=new java.io.IOException();view(R.id.photoRemove).performClick();view(R.id.photoSave).performClick();work.run();ui.run();idle();View root=current().requireView();root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.EXACTLY));root.layout(0,0,320,480);view(R.id.photoSave).getViewTreeObserver().dispatchOnPreDraw();assertTrue(view(R.id.photoSave).getHeight()>=48);assertTrue(view(R.id.photoScroll).getHeight()>0);android.graphics.Rect visible=new android.graphics.Rect();assertTrue("The error must be visible inside the scroll viewport",view(R.id.photoError).getGlobalVisibleRect(visible));assertTrue(visible.height()>0);capture("profile-photo-small-light",320,480);}
    @Test public void lightFixtureRendersThePrivatePhotoScreen()throws Exception{launch();load();capture("profile-photo-light",390,740);}
    @Test @Config(qualifiers="w390dp-h844dp-port-night-mdpi") public void darkFixtureRendersThePrivatePhotoScreen()throws Exception{launch();load();capture("profile-photo-dark",390,740);}
    @Test @Config(qualifiers="w320dp-h480dp-port-night-mdpi") public void shortDarkLargeTextFixtureKeepsControlsReachable()throws Exception{RuntimeEnvironment.setFontScale(2f);launch();load();assertTrue(view(R.id.photoChoose).getMinimumHeight()>=48);capture("profile-photo-small-dark",320,480);}
    void host(){GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication();controller=Robolectric.buildActivity(MainActivity.class,new Intent(app,MainActivity.class).putExtra(MainActivity.EXTRA_PREVIEW,true)).setup().visible();activity=controller.get();activity.navigate(new MoreFragment(),false);idle();}
    void launch(){host();vm=new ProfilePhotoViewModel(activity.getApplication(),actions,work,ui);ViewModelProvider.Factory factory=new ViewModelProvider.Factory(){public <T extends ViewModel>T create(Class<T>type){return type.cast(vm);}};activity.navigate(new ProfilePhotoFragment(factory),true);idle();}
    void load(){work.run();ui.run();idle();}
    View view(int id){View v=activity.findViewById(id);assertNotNull(v);return v;}
    String text(int id){return ((TextView)view(id)).getText().toString();}
    androidx.fragment.app.Fragment current(){return activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer);}
    void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();activity.getSupportFragmentManager().executePendingTransactions();}
    void capture(String name,int width,int height)throws Exception{View root=current().requireView();root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);android.graphics.Bitmap b=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(b));java.io.File folder=new java.io.File("build/outputs/screenshots");assertTrue(folder.isDirectory()||folder.mkdirs());try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(folder,name+".png"))){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
}
