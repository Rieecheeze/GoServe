package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.After;
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
@Config(sdk=34,application=GoServeApplication.class,qualifiers="w390dp-h760dp-port-notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public final class RecoverySecurityInteractionTest {
    private ActivityController<MainActivity> controller;private MainActivity activity;private RecoverySecurityViewModelTest.Fixture fixture;
    private int width=390,height=760;
    @After public void close(){if(controller!=null)controller.close();RuntimeEnvironment.setFontScale(1f);AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);}
    @Test public void accountContainsOnlyRecoveryCodesAndPreviewGrantsNoSecurityAccess(){
        launchHost();activity.navigate(SettingsGroupFragment.account(),true);idle();View root=current();
        assertNull(text(root,"Authenticator"));View codes=text(root,"Recovery Codes");assertNotNull(codes);assertFalse(codes.isEnabled());assertFalse(activity.findViewById(R.id.changePasswordButton).isEnabled());
    }
    @Test public void forgotPasswordUsesOnlyEmailAndSavedCodeBeforeNewPassword(){
        launch(RecoverySecurityViewModel.Kind.FORGOT,390,760,1f,false);
        assertTrue(activity.findViewById(R.id.recoveryProofPanel).isShown());assertFalse(activity.findViewById(R.id.recoveryPasswordPanel).isShown());
        assertNull(text(current(),"Authenticator Code"));input(R.id.recoveryEmail).setText("student@tip.edu.ph");input(R.id.recoveryProofInput).setText("my-saved-code");click(R.id.recoveryPrimary);finish();
        assertEquals("my-saved-code",fixture.actions.proof);assertTrue(activity.findViewById(R.id.recoveryPasswordPanel).isShown());assertEquals("",input(R.id.recoveryProofInput).getText().toString());
        for(int id:new int[]{R.id.recoveryProofInput,R.id.recoveryNewPassword,R.id.recoveryConfirmPassword}){assertFalse(input(id).isSaveEnabled());assertFalse(input(id).isSaveFromParentEnabled());}
    }
    @Test public void newCodesDisplayOnceAndExplicitSavingRemovesThePlaintext(){
        launch(RecoverySecurityViewModel.Kind.CODES,390,760,1f,false);assertEquals(0,((ViewGroup)activity.findViewById(R.id.recoveryCodeRows)).getChildCount());
        click(R.id.recoveryPrimary);finish();assertEquals(10,((ViewGroup)activity.findViewById(R.id.recoveryCodeRows)).getChildCount());assertEquals(0,fixture.actions.acknowledgments);
        assertEquals(activity.getString(R.string.recovery_acknowledge),((TextView)activity.findViewById(R.id.recoveryPrimary)).getText().toString());click(R.id.recoveryPrimary);finish();
        assertEquals(1,fixture.actions.acknowledgments);assertEquals(0,((ViewGroup)activity.findViewById(R.id.recoveryCodeRows)).getChildCount());assertTrue(activity.findViewById(R.id.recoveryStatus).isShown());
    }
    @Test public void codeScreensRemainScrollableInBothThemesAndLargeText()throws Exception{
        for(boolean night:new boolean[]{false,true})for(boolean large:new boolean[]{false,true}){
            launch(RecoverySecurityViewModel.Kind.SETUP,large?320:390,large?640:760,large?2f:1f,night);
            capture("recovery-setup-"+(night?"dark":"light")+(large?"-font2":""));click(R.id.recoveryPrimary);finish();
            NestedScrollView scroll=activity.findViewById(R.id.recoveryScroll);scroll.setSmoothScrollingEnabled(false);scroll.fullScroll(View.FOCUS_DOWN);layout();
            View primary=activity.findViewById(R.id.recoveryPrimary);Rect visible=new Rect();assertTrue(primary.getGlobalVisibleRect(visible));assertEquals(primary.getHeight(),visible.height());verifyText(current());
            capture("recovery-codes-"+(night?"dark":"light")+(large?"-font2-end":"-end"));
        }
    }
    @Test public void recentSignInFailureShowsAnEnabledWorkingAction(){
        launch(RecoverySecurityViewModel.Kind.CODES,390,760,1f,false);fixture.actions.generateFailure=new ph.edu.tip.goserve.data.SupabaseClient.ApiException(403,"RECENT_AUTH_REQUIRED","private");click(R.id.recoveryPrimary);finish();
        assertTrue(activity.findViewById(R.id.recoveryReauthenticate).isShown());assertTrue(activity.findViewById(R.id.recoveryReauthenticate).isEnabled());click(R.id.recoveryReauthenticate);
        Intent destination=Shadows.shadowOf(activity).getNextStartedActivity();assertNotNull(destination);assertEquals(AuthActivity.class.getName(),destination.getComponent().getClassName());assertTrue(activity.isFinishing());
    }
    @Test public void proofPasswordAndPendingScreensReflowAcrossThemesAndReducedWindows()throws Exception{
        for(boolean night:new boolean[]{false,true})for(boolean large:new boolean[]{false,true}){
            String suffix=(night?"dark":"light")+(large?"-font2":"");
            launch(RecoverySecurityViewModel.Kind.FORGOT,large?320:390,large?640:760,large?2f:1f,night);capture("recovery-proof-"+suffix);
            input(R.id.recoveryEmail).setText("student@tip.edu.ph");input(R.id.recoveryProofInput).setText("my-saved-code");click(R.id.recoveryPrimary);finish();
            input(R.id.recoveryNewPassword).setText("New 1! password");input(R.id.recoveryConfirmPassword).setText("New 1! password");
            height=large?420:360;layout();revealPrimary();verifyText(current());capture("recovery-password-reduced-"+suffix);
            click(R.id.recoveryPrimary);finish();revealPrimary();verifyText(current());capture("recovery-pending-reduced-"+suffix);
            assertEquals(1,fixture.actions.resets);assertEquals(RecoverySecurityViewModel.Page.PENDING,fixture.state().page);
        }
    }
    @Test public void canonicalSignInReturnsToCodesOnlyForTheRequestedOwner(){
        for(String signedInOwner:new String[]{"requested-owner","different-owner"}){
            ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class,"_instance",null);
            GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication();Intent request=new Intent(app,AuthActivity.class).putExtra(AuthActivity.EXTRA_RETURN_RECOVERY_OWNER,"requested-owner");
            try(ActivityController<AuthActivity> authController=Robolectric.buildActivity(AuthActivity.class,request).setup().visible()){
                AuthActivity host=authController.get();AuthViewModel auth=new ViewModelProvider(host).get(AuthViewModel.class);
                ph.edu.tip.goserve.domain.SessionProfile profile=new ph.edu.tip.goserve.domain.SessionProfile(signedInOwner,"student@tip.edu.ph","Student",ph.edu.tip.goserve.domain.Role.STUDENT,null,null,false,1,true,"v","v","home",null);
                androidx.lifecycle.MutableLiveData<AuthViewModel.State> state=ReflectionHelpers.getField(auth,"state");state.setValue(new AuthViewModel.State(AuthViewModel.Route.HOME,false,null,profile,null,java.util.Collections.emptyMap(),null));
                Intent destination=Shadows.shadowOf(host).getNextStartedActivity();assertNotNull(destination);assertEquals(MainActivity.class.getName(),destination.getComponent().getClassName());assertEquals("requested-owner".equals(signedInOwner),destination.getBooleanExtra(MainActivity.EXTRA_OPEN_RECOVERY_CODES,false));
            }
        }
    }
    private void launch(RecoverySecurityViewModel.Kind kind,int requestedWidth,int requestedHeight,float fontScale,boolean night){
        if(controller!=null){controller.close();controller=null;}width=requestedWidth;height=requestedHeight;RuntimeEnvironment.setQualifiers("w"+width+"dp-h"+height+"dp-port-"+(night?"night":"notnight")+"-mdpi");RuntimeEnvironment.setFontScale(fontScale);AppCompatDelegate.setDefaultNightMode(night?AppCompatDelegate.MODE_NIGHT_YES:AppCompatDelegate.MODE_NIGHT_NO);launchHost();
        fixture=new RecoverySecurityViewModelTest.Fixture();fixture.vm.start(kind,false);fixture.finish();
        ViewModelProvider.Factory factory=new ViewModelProvider.Factory(){@Override public <T extends ViewModel>T create(Class<T> type){return type.cast(fixture.vm);}};
        RecoverySecurityFragment fragment=new RecoverySecurityFragment(factory);Bundle arguments=new Bundle();arguments.putString("kind",kind.name());fragment.setArguments(arguments);activity.navigate(fragment,true);idle();layout();
    }
    private void launchHost(){ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class,"_instance",null);GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication();controller=Robolectric.buildActivity(MainActivity.class,new Intent(app,MainActivity.class).putExtra(MainActivity.EXTRA_PREVIEW,true)).setup().visible();activity=controller.get();idle();}
    private void click(int id){View view=activity.findViewById(id);assertNotNull(view);assertTrue(view.isEnabled());assertTrue(view.performClick());idle();}
    private EditText input(int id){return activity.findViewById(id);}private View current(){return activity.getSupportFragmentManager().findFragmentById(R.id.mainContainer).requireView();}
    private void finish(){fixture.finish();idle();layout();}private void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();activity.getSupportFragmentManager().executePendingTransactions();}
    private void layout(){View root=activity.findViewById(R.id.activityRoot);for(int count=0;count<2;count++){root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);idle();}}
    private void revealPrimary(){View primary=activity.findViewById(R.id.recoveryPrimary);primary.requestRectangleOnScreen(new Rect(0,0,primary.getWidth(),primary.getHeight()),true);layout();Rect visible=new Rect();assertTrue(primary.getGlobalVisibleRect(visible));assertEquals(primary.getHeight(),visible.height());}
    private View text(View view,String expected){if(view instanceof TextView&&expected.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int index=0;index<group.getChildCount();index++){View found=text(group.getChildAt(index),expected);if(found!=null)return found;}}return null;}
    private void verifyText(View view){if(view.getVisibility()!=View.VISIBLE)return;if(view instanceof TextView&&!(view instanceof EditText)){TextView text=(TextView)view;if(text.getLayout()!=null)assertTrue(text.getLayout().getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom()+2);}if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int index=0;index<group.getChildCount();index++){View child=group.getChildAt(index);if(child.getVisibility()==View.VISIBLE){assertTrue(child.getRight()<=group.getWidth());verifyText(child);}}}}
    private void capture(String name)throws Exception{layout();View root=activity.findViewById(R.id.activityRoot);Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);try{root.draw(new Canvas(image));File directory=new File("build/outputs/screenshots");assertTrue(directory.isDirectory()||directory.mkdirs());try(FileOutputStream output=new FileOutputStream(new File(directory,name+".png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,output));}}finally{image.recycle();}}
}
