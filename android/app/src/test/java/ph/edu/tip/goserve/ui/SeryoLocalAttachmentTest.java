package ph.edu.tip.goserve.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import androidx.lifecycle.ViewModelProvider;
import java.io.OutputStream;
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
import org.robolectric.shadows.ShadowPopupMenu;
import org.robolectric.util.ReflectionHelpers;
import ph.edu.tip.goserve.GoServeApplication;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.AndroidFileProviderPaths;
import ph.edu.tip.goserve.data.SeryoRepository;
import ph.edu.tip.goserve.domain.SeryoDraft;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,shadows=AndroidFileProviderPaths.class,instrumentedPackages="androidx.core.content",qualifiers="w390dp-h844dp-port-notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public final class SeryoLocalAttachmentTest {
    private ActivityController<MainActivity> controller;
    @Before public void providers(){AndroidFileProviderPaths.reset();Shadows.shadowOf(android.webkit.MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("jpg","image/jpeg");}
    @After public void close(){if(controller!=null)controller.close();}

    @Test public void validCameraPhotoIsCheckedLocallyAndTemporaryCaptureIsDeleted() throws Exception {
        SeryoViewModelTest.Server server=new SeryoViewModelTest.Server();SeryoViewModelTest.Queue queue=new SeryoViewModelTest.Queue();SeryoViewModel model=model(server,queue);
        model.start(true);model.decide("allow");model.edit("Keep this draft");Uri[] output={null};model.requestCamera(uri->output[0]=uri);assertNotNull(output[0]);
        Bitmap photo=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);
        try(OutputStream stream=RuntimeEnvironment.getApplication().getContentResolver().openOutputStream(output[0])){assertTrue(photo.compress(Bitmap.CompressFormat.JPEG,90,stream));}finally{photo.recycle();}
        model.finishCamera(true);queue.finish();
        assertNull(model.snapshot().error);assertNotNull(model.snapshot().draft.attachment);assertEquals("image/jpeg",model.snapshot().draft.attachment.mime);
        assertEquals("Keep this draft",model.snapshot().draft.text);assertDeleted(output[0]);
        assertEquals(0,server.reads);assertEquals(0,server.writes);assertEquals(0,server.sends);
        SeryoDraft.Attachment selected=model.snapshot().draft.attachment;model.removeAttachment();assertTrue(selected.released());assertEquals("Keep this draft",model.snapshot().draft.text);
    }

    @Test public void cancellationDeletesCameraOutputWithoutChangingTheDraft() throws Exception {
        SeryoViewModelTest.Queue queue=new SeryoViewModelTest.Queue();SeryoViewModel model=model(new SeryoViewModelTest.Server(),queue);
        model.start(true);model.decide("allow");model.edit("Unsent question");Uri[] output={null};model.requestCamera(uri->output[0]=uri);model.finishCamera(false);
        assertDeleted(output[0]);assertNull(model.snapshot().draft.attachment);assertEquals("Unsent question",model.snapshot().draft.text);assertNull(model.snapshot().error);
    }

    @Test public void accountChangeDeletesPendingCaptureAndRejectsItsLateResult() throws Exception {
        SeryoViewModelTest.Server server=new SeryoViewModelTest.Server();server.consent="allow";SeryoViewModelTest.Queue queue=new SeryoViewModelTest.Queue();SeryoViewModel model=model(server,queue);
        model.start(false);queue.finish();Uri[] output={null};model.requestCamera(uri->output[0]=uri);queue.finish();assertNotNull(output[0]);
        server.current=false;model.verifyAccount();model.finishCamera(true);queue.finish();
        assertDeleted(output[0]);assertNull(model.snapshot().draft.attachment);assertTrue(model.snapshot().messages.isEmpty());assertEquals(0,server.sends);
    }

    @Test public void privateAttachmentBlocksSendUntilExplicitlyRemoved() {
        SeryoViewModelTest.Server server=new SeryoViewModelTest.Server();SeryoViewModelTest.Queue queue=new SeryoViewModelTest.Queue();SeryoViewModel model=model(server,queue);
        model.start(true);model.decide("allow");model.edit("Where do I request a document?");
        model.snapshot().draft.replace(new SeryoDraft.Attachment("service-paper.jpg","image/jpeg",new byte[]{1,2,3}));
        model.checkSend();assertTrue(model.snapshot().messages.isEmpty());assertEquals(0,server.sends);
        model.removeAttachment();model.checkSend();assertEquals(2,model.snapshot().messages.size());assertEquals(0,server.sends);
    }

    @Test public void cameraMenuUsesScopedContentOutputAndTemporaryReadWriteGrants() {
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory.class,"_instance",null);
        GoServeApplication app=(GoServeApplication)RuntimeEnvironment.getApplication();controller=Robolectric.buildActivity(MainActivity.class,new Intent(app,MainActivity.class).putExtra(MainActivity.EXTRA_PREVIEW,true)).setup().visible();
        MainActivity activity=controller.get();activity.findViewById(R.id.nav_assistant).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle();activity.getSupportFragmentManager().executePendingTransactions();
        SeryoViewModel model=new ViewModelProvider(activity).get(SeryoViewModel.class);model.decide("allow");
        activity.findViewById(R.id.attachmentButton).performClick();android.widget.PopupMenu menu=ShadowPopupMenu.getLatestPopupMenu();menu.getMenu().performIdentifierAction(menu.getMenu().getItem(1).getItemId(),0);
        Intent capture=Shadows.shadowOf(activity).getNextStartedActivity();assertNotNull(capture);assertEquals(MediaStore.ACTION_IMAGE_CAPTURE,capture.getAction());
        Uri output=capture.getParcelableExtra(MediaStore.EXTRA_OUTPUT);assertNotNull(output);assertEquals("content",output.getScheme());assertEquals(app.getPackageName()+".evidence",output.getAuthority());
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION,capture.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
        assertEquals(output,capture.getClipData().getItemAt(0).getUri());model.finishCamera(false);
    }

    private SeryoViewModel model(SeryoViewModelTest.Server server,SeryoViewModelTest.Queue queue){return new SeryoViewModel(RuntimeEnvironment.getApplication(),new SeryoRepository(server),queue);}
    private void assertDeleted(Uri uri)throws Exception {assertNotNull(uri);try{RuntimeEnvironment.getApplication().getContentResolver().openInputStream(uri);fail("The temporary camera file must be removed.");}catch(java.io.FileNotFoundException expected){}}
}
