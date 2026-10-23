package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
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
import org.robolectric.shadows.ShadowPopupMenu;
import org.robolectric.util.ReflectionHelpers;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.SeryoRepository;
import ph.edu.tip.goserve.ui.common.SeryoMascotView;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,qualifiers="w390dp-h844dp-port-notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public final class SeryoConsentInteractionTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private int width=390,height=844;
    private SeryoViewModelTest.Queue queue;
    private SeryoViewModelTest.Server server;
    private SeryoViewModel model;
    @After public void close() {
        if(controller!=null)controller.close();
        RuntimeEnvironment.setFontScale(1f);AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
    @Test public void consentRetryReplaysTheUnknownDecisionAndSettingsHelpKeepTheDraft() throws Exception {
        for(boolean night:new boolean[]{false,true}) {
            launch(night?320:390,night?2f:1f,night,false);
            click(R.id.nav_assistant);settle();
            ((EditText)activity.findViewById(R.id.questionInput)).setText("Unsent private question");
            click(R.id.privacyButton);settle();assertTrue(activity.findViewById(R.id.allowAiButton).isEnabled());
            server.timeoutConsent=true;click(R.id.allowAiButton);settle();
            assertEquals(1,server.writes);assertFalse(activity.findViewById(R.id.allowAiButton).isEnabled());
            assertEquals("Retry consent decision",((TextView)activity.findViewById(R.id.retryReadinessButton)).getText().toString());
            assertEquals(SeryoMascotView.State.IDLE,((SeryoMascotView)activity.findViewById(R.id.seryoIcon)).getState());
            checkScreen("seryo-consent-uncertain-"+(night?"dark-font2":"light"),R.id.returnToChatButton);
            click(R.id.retryReadinessButton);settle();
            assertEquals(2,server.commands.size());assertEquals(1,server.writes);
            assertEquals(server.commands.get(0).toString(),server.commands.get(1).toString());
            assertTrue(activity.findViewById(R.id.chatPanel).isShown());
            chooseTool(2);settle();assertTrue(activity.findViewById(R.id.supportPanel).isShown());
            checkScreen("seryo-settings-"+(night?"dark-font2":"light"),R.id.refreshStateButton);
            click(R.id.helpSettingsButton);settle();assertTrue(activity.findViewById(R.id.supportPanel).isShown());
            assertEquals(activity.getString(R.string.seryo_help_title),((TextView)activity.findViewById(R.id.supportHeading)).getText().toString());
            checkScreen("seryo-help-"+(night?"dark-font2":"light"),R.id.refreshStateButton);
            if(night)capture("seryo-help-dark-font2-end");
            click(R.id.backButton);settle();click(R.id.backButton);settle();
            assertEquals("Unsent private question",((EditText)activity.findViewById(R.id.questionInput)).getText().toString());
            assertFalse(activity.findViewById(R.id.sendButton).isEnabled());assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
        }
    }
    @Test public void leavingAttachmentPanelBeforeAccessCheckCompletesCannotOpenAHiddenPicker() {
        launch(390,1f,false,true);
        click(R.id.nav_assistant);settle();chooseTool(0);settle();
        click(R.id.chooseFileButton);assertTrue(model.snapshot().busy);click(R.id.nav_home);
        settle();assertNull("A detached panel must not launch its document picker.",Shadows.shadowOf(activity).getNextStartedActivity());
    }
    private void launch(int requestedWidth,float fontScale,boolean night,boolean futureReady) {
        if(controller!=null)controller.close();width=requestedWidth;height=night?640:844;
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class,"_instance",null);
        RuntimeEnvironment.setQualifiers("w"+width+"dp-h"+height+"dp-port-"+(night?"night":"notnight")+"-mdpi");
        AppCompatDelegate.setDefaultNightMode(night?AppCompatDelegate.MODE_NIGHT_YES:AppCompatDelegate.MODE_NIGHT_NO);RuntimeEnvironment.setFontScale(fontScale);
        GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication();
        controller=Robolectric.buildActivity(MainActivity.class,new Intent(app,MainActivity.class).putExtra(MainActivity.EXTRA_PREVIEW,true)).setup().visible();activity=controller.get();layout();
        server=new SeryoViewModelTest.Server();server.futureReady=futureReady;if(futureReady)server.consent="allow";
        queue=new SeryoViewModelTest.Queue();SeryoViewModel supplied=new SeryoViewModel(app,new SeryoRepository(server),queue);
        model=new ViewModelProvider(activity,new ViewModelProvider.Factory(){@Override public <T extends androidx.lifecycle.ViewModel>T create(Class<T> type){return type.cast(supplied);}}).get(SeryoViewModel.class);
        model.start(false);settle();
    }
    private void settle(){queue.finish();layout();queue.finish();layout();}
    private void click(int id){View target=activity.findViewById(id);assertNotNull(target);assertTrue(target.isEnabled());assertTrue(target.performClick());layout();}
    private void chooseTool(int index){click(R.id.attachmentButton);android.widget.PopupMenu menu=ShadowPopupMenu.getLatestPopupMenu();assertNotNull(menu);assertTrue(menu.getMenu().performIdentifierAction(menu.getMenu().getItem(index).getItemId(),0));layout();}
    private void layout(){Shadows.shadowOf(Looper.getMainLooper()).idle();activity.getSupportFragmentManager().executePendingTransactions();View root=activity.findViewById(R.id.activityRoot);for(int i=0;i<2;i++){root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);Shadows.shadowOf(Looper.getMainLooper()).idle();}}
    private void checkScreen(String name,int finalId)throws Exception {
        NestedScrollView scroll=activity.findViewById(R.id.assistantScroll);scroll.setSmoothScrollingEnabled(false);scroll.scrollTo(0,0);layout();verifyText(activity.findViewById(R.id.activityRoot));capture(name);
        scroll.fullScroll(View.FOCUS_DOWN);layout();View last=activity.findViewById(finalId);Rect visible=new Rect();assertTrue(name,last.getGlobalVisibleRect(visible));assertEquals(name,last.getHeight(),visible.height());
    }
    private void verifyText(View view){
        if(view.getVisibility()!=View.VISIBLE)return;
        if(view instanceof TextView && !(view instanceof EditText)){TextView text=(TextView)view;if(text.length()>0 && text.getLayout()!=null){assertTrue("Text must wrap without clipping.",text.getLayout().getHeight()<=text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom()+2);for(int i=0;i<text.getLineCount();i++)assertEquals(0,text.getLayout().getEllipsisCount(i));}}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);if(child.getVisibility()==View.VISIBLE){assertTrue(child.getLeft()>=0);assertTrue(child.getRight()<=group.getWidth());verifyText(child);}}}
    }
    private void capture(String name)throws Exception {
        View root=activity.findViewById(R.id.activityRoot);root.jumpDrawablesToCurrentState();Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        try{Canvas canvas=new Canvas(bitmap);canvas.drawColor(ContextCompat.getColor(activity,R.color.gs_background));root.draw(canvas);File directory=new File("build/outputs/screenshots");assertTrue(directory.isDirectory()||directory.mkdirs());try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}}finally{bitmap.recycle();}
    }
}
