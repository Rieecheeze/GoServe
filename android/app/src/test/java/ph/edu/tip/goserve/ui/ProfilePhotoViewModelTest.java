package ph.edu.tip.goserve.ui;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import android.graphics.Bitmap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import ph.edu.tip.goserve.data.ProfilePhotoRepository;
import ph.edu.tip.goserve.data.SupabaseClient;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class ProfilePhotoViewModelTest {
    @Test public void canonicalPhotoLoadsOffMainBeforeEditingIsEnabled(){Fixture f=new Fixture();assertFalse(f.vm.canEdit());assertEquals(1,f.work.size());assertEquals(0,f.a.loads);f.load();assertTrue(f.vm.canEdit());assertArrayEquals(f.a.photo.image(),f.vm.image());assertFalse(f.vm.canSave());}
    @Test public void selectionIsMemoryOnlyAndCancellationKeepsThePreviousPicture(){Fixture f=new Fixture();f.load();f.vm.select(null);assertEquals(0,f.work.size());assertArrayEquals(f.a.photo.image(),f.vm.image());f.select();assertTrue(f.vm.canSave());assertEquals(0,f.a.saves);assertNotNull(f.vm.image());}
    @Test public void invalidSelectionPreservesTheEarlierDraft(){Fixture f=new Fixture();f.load();f.select();byte[] held=f.vm.image();f.vm.select(()->new ByteArrayInputStream(new byte[]{1,2,3}));f.work.run();f.ui.run();assertArrayEquals(held,f.vm.image());assertEquals("PHOTO_INVALID",f.state().error);assertTrue(f.vm.canSave());}
    @Test public void saveIsSingleFlightAndUsesOneRetainedUpdateAcrossUncertainRetries(){Fixture f=new Fixture();f.load();f.select();f.a.failure=new IOException("private error");f.vm.save();f.vm.save();assertEquals(1,f.work.size());assertFalse(f.vm.canEdit());f.work.run();f.ui.run();assertEquals("PHOTO_SAVE_UNCERTAIN",f.state().error);assertFalse(f.vm.canEdit());assertTrue(f.vm.canSave());ProfilePhotoRepository.Update first=f.a.update;f.a.failure=null;f.vm.save();f.work.run();f.ui.run();assertSame(first,f.a.update);assertTrue(f.state().saved);assertFalse(f.vm.canSave());}
    @Test public void removalNeedsSaveAndDoesNotUploadThePriorDraft(){Fixture f=new Fixture();f.load();f.vm.remove();assertNull(f.vm.image());assertTrue(f.vm.canSave());assertEquals(0,f.a.saves);f.vm.save();f.work.run();f.ui.run();assertTrue(f.state().saved);assertNull(f.vm.image());}
    @Test public void staleVersionRequiresReloadAndCannotBlindlyRetry(){Fixture f=new Fixture();f.load();f.select();f.a.failure=new SupabaseClient.ApiException(409,"STALE_VERSION","private");f.vm.save();f.work.run();f.ui.run();assertEquals("STALE_VERSION",f.state().error);assertFalse(f.vm.canSave());assertFalse(f.vm.canEdit());f.vm.reload();f.work.run();f.ui.run();assertTrue(f.vm.canEdit());assertFalse(f.vm.canSave());assertArrayEquals(f.a.photo.image(),f.vm.image());}
    @Test public void accountLossBeforeWorkAndBeforePublicationClearPrivateBytes(){Fixture f=new Fixture();f.a.current=false;f.load();assertEquals(0,f.a.loads);assertEquals(ProfilePhotoViewModel.Mode.UNAVAILABLE,f.state().mode);assertNull(f.vm.image());Fixture g=new Fixture();g.load();g.select();g.vm.save();g.work.run();g.a.current=false;g.ui.run();assertEquals(ProfilePhotoViewModel.Mode.UNAVAILABLE,g.state().mode);assertNull(g.vm.image());}
    @Test public void loadFailureKeepsEditingDisabledAndAnExplicitRetryCanRecover(){Fixture f=new Fixture();f.a.failure=new IOException("secret");f.load();assertEquals("PHOTO_LOAD_FAILED",f.state().error);assertFalse(f.vm.canEdit());f.a.failure=null;f.vm.reload();f.work.run();f.ui.run();assertTrue(f.vm.canEdit());}
    @Test public void clearingCancelsQueuedWorkAndStalePublications(){Fixture f=new Fixture();f.vm.onCleared();f.work.run();assertEquals(0,f.a.loads);Fixture g=new Fixture();g.load();g.select();g.vm.save();g.work.run();g.vm.onCleared();g.ui.run();assertNull(g.vm.image());}
    @Test public void pendingLoadCannotBeDuplicatedByResumeOrReload(){Fixture f=new Fixture();f.vm.reload();f.vm.checkCurrentAccount();assertEquals(1,f.work.size());f.load();assertEquals(1,f.a.loads);f.vm.checkCurrentAccount();assertEquals(0,f.work.size());}
    static byte[] image(){Bitmap b=Bitmap.createBitmap(80,40,Bitmap.Config.ARGB_8888);b.eraseColor(android.graphics.Color.GRAY);ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.JPEG,88,out);b.recycle();return out.toByteArray();}
    static class Queue implements Executor{ArrayDeque<Runnable> tasks=new ArrayDeque<>();public void execute(Runnable r){tasks.add(r);}int size(){return tasks.size();}void run(){tasks.remove().run();}}
    static final class Fake implements ProfilePhotoViewModel.Actions{
        boolean current=true;int loads,saves;Exception failure;ProfilePhotoRepository.Update update;ProfilePhotoRepository.Photo photo=new ProfilePhotoRepository.Photo("own",2,image());
        public void requireCurrent(){if(!current)throw new IllegalStateException();}
        public ProfilePhotoRepository.Photo load()throws Exception{loads++;if(failure!=null)throw failure;return photo;}
        public ProfilePhotoRepository.Photo save(ProfilePhotoRepository.Update op)throws Exception{saves++;update=op;if(failure!=null)throw failure;return new ProfilePhotoRepository.Photo(null,3,null);}
    }
    static final class Fixture{final Queue work=new Queue(),ui=new Queue();final Fake a=new Fake();final ProfilePhotoViewModel vm=new ProfilePhotoViewModel(RuntimeEnvironment.getApplication(),a,work,ui);ProfilePhotoViewModel.State state(){return vm.state().getValue();}void load(){work.run();ui.run();}void select(){vm.select(()->new ByteArrayInputStream(image()));work.run();ui.run();}}
}
