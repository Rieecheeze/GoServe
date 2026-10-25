package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.PopupMenu;
import androidx.lifecycle.ViewModelProvider;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowPopupMenu;
import org.robolectric.util.ReflectionHelpers;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.SeryoRepository;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,qualifiers="w390dp-h844dp-port-notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class SeryoRevisionTest {
    private ActivityController<MainActivity> controller;
    @After public void close(){if(controller!=null)controller.close();}

    @Test public void acceptedPreviewRespondsLocallyWithoutContactingTheAccountOrProvider() {
        SeryoViewModelTest.Server server=new SeryoViewModelTest.Server();
        SeryoViewModelTest.Queue queue=new SeryoViewModelTest.Queue();
        SeryoViewModel model=new SeryoViewModel(RuntimeEnvironment.getApplication(),new SeryoRepository(server),queue);
        model.start(true);model.decide("allow");model.edit("Where do I request a document?");model.checkSend();queue.finish();
        assertEquals("A local preview must show the sent question and a clearly labelled response.",2,model.snapshot().messages.size());
        assertTrue(model.snapshot().messages.get(1).text.contains("Local preview"));
        assertEquals("",model.snapshot().draft.text);
        assertEquals(0,server.reads);assertEquals(0,server.writes);assertEquals(0,server.sends);
    }

    @Test public void previewDeclinePreventsSendWithoutRecordingRealConsent() {
        SeryoViewModelTest.Server server=new SeryoViewModelTest.Server();SeryoViewModelTest.Queue queue=new SeryoViewModelTest.Queue();
        SeryoViewModel model=new SeryoViewModel(RuntimeEnvironment.getApplication(),new SeryoRepository(server),queue);
        model.start(true);model.decide("decline");model.edit("Where is the registrar?");model.checkSend();queue.finish();
        assertTrue(model.snapshot().messages.isEmpty());assertEquals("Where is the registrar?",model.snapshot().draft.text);
        assertEquals(0,server.reads);assertEquals(0,server.writes);assertEquals(0,server.sends);
    }

    @Test public void consentedLocalSelectionWorksWhenProviderFileAnalysisIsUnsupported() {
        SeryoViewModelTest.Server server=new SeryoViewModelTest.Server();server.consent="allow";
        SeryoViewModelTest.Queue queue=new SeryoViewModelTest.Queue();SeryoViewModel model=new SeryoViewModel(RuntimeEnvironment.getApplication(),new SeryoRepository(server),queue);
        model.start(false);queue.finish();boolean[] launched={false};model.requestPicker(ignored->launched[0]=true);queue.finish();
        assertTrue("A local validation picker does not require a provider upload capability.",launched[0]);
        assertEquals(0,server.sends);
    }

    @Test public void plusHasOnlyDocumentAndCameraActionsAndDoesNotNavigateToSettings() {
        MainActivity activity=launch();activity.findViewById(R.id.attachmentButton).performClick();
        PopupMenu menu=ShadowPopupMenu.getLatestPopupMenu();assertNotNull(menu);
        assertEquals(2,menu.getMenu().size());
        assertEquals("Attach File",menu.getMenu().getItem(0).getTitle());assertEquals("Open Camera",menu.getMenu().getItem(1).getTitle());
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test public void savedPrivacyRouteCannotChangeTheFirstDecision() {
        MainActivity activity=launch();activity.navigate(AssistantFragment.create("privacy"),true);settle(activity);
        assertTrue(activity.findViewById(R.id.consentPanel).isShown());
        assertEquals(View.GONE,activity.findViewById(R.id.allowAiButton).getVisibility());
        assertEquals(View.GONE,activity.findViewById(R.id.declineAiButton).getVisibility());
        assertEquals(View.GONE,activity.findViewById(R.id.revokeAiButton).getVisibility());
    }

    @Test public void noticeHasVisibleSpaceBeforeWelcomeAtNormalAndShortWidths() {
        MainActivity activity=launch();View notice=activity.findViewById(R.id.accessNotice),welcome=activity.findViewById(R.id.welcomeCard);
        for(int width:new int[]{320,390}){
            View root=activity.findViewById(R.id.activityRoot);root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(640,View.MeasureSpec.EXACTLY));root.layout(0,0,width,640);
            int[] n=new int[2],w=new int[2];notice.getLocationOnScreen(n);welcome.getLocationOnScreen(w);
            assertTrue("The setup/privacy notice must not touch or overlap the welcome bubble.",w[1]>=n[1]+notice.getHeight()+12);
        }
    }

    @Test public void reducedWindowKeepsTheComposerReachableAndRestoresItsRoomyDraft() throws Exception {
        MainActivity activity=launch();android.widget.EditText draft=activity.findViewById(R.id.questionInput);
        draft.setText("Where do I request a document?\nWhich office should I visit?");
        View root=activity.findViewById(R.id.activityRoot);
        for(int height:new int[]{460,844}){
            root.measure(View.MeasureSpec.makeMeasureSpec(390,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,390,height);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            root.measure(View.MeasureSpec.makeMeasureSpec(390,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,390,height);
            android.graphics.Rect send=new android.graphics.Rect();View button=activity.findViewById(R.id.sendButton);
            assertTrue(button.getGlobalVisibleRect(send));assertEquals(button.getHeight(),send.height());
            assertEquals(height==460?1:3,draft.getMaxLines());
            assertEquals("Where do I request a document?\nWhich office should I visit?",draft.getText().toString());
            assertTrue("A reduced window must keep a readable chat viewport; height="+height+", viewport="+activity.findViewById(R.id.assistantScroll).getHeight()+", composer="+activity.findViewById(R.id.composerHost).getHeight(),activity.findViewById(R.id.assistantScroll).getHeight()>=64);
            System.out.println("SERYO_REDUCED_WINDOW height="+height+" viewport="+activity.findViewById(R.id.assistantScroll).getHeight()+" composer="+activity.findViewById(R.id.composerHost).getHeight());
            android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(root.getWidth(),root.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
            try{root.draw(new android.graphics.Canvas(image));java.io.File directory=new java.io.File("build/outputs/screenshots");assertTrue(directory.isDirectory() || directory.mkdirs());try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(directory,"seryo-reduced-window-"+height+".png"))){assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output));}}finally{image.recycle();}
        }
    }

    private MainActivity launch(){
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class,"_instance",null);
        GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication();
        controller=Robolectric.buildActivity(MainActivity.class,new Intent(app,MainActivity.class).putExtra(MainActivity.EXTRA_PREVIEW,true)).setup().visible();
        MainActivity activity=controller.get();activity.findViewById(R.id.nav_assistant).performClick();settle(activity);return activity;
    }
    private void settle(MainActivity activity){Shadows.shadowOf(Looper.getMainLooper()).idle();activity.getSupportFragmentManager().executePendingTransactions();View root=activity.findViewById(R.id.activityRoot);root.measure(View.MeasureSpec.makeMeasureSpec(390,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(844,View.MeasureSpec.EXACTLY));root.layout(0,0,390,844);Shadows.shadowOf(Looper.getMainLooper()).idle();}
}
