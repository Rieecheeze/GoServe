package ph.edu.tip.goserve.data;

import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.Uri;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=Application.class,shadows={AndroidFileProviderPaths.class},instrumentedPackages={"androidx.core.content"})
public final class PrivateDocumentTest {
    @Before public void reset() throws Exception {
        AndroidFileProviderPaths.reset();
        Shadows.shadowOf(android.webkit.MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("pdf","application/pdf");
        Shadows.shadowOf(android.webkit.MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("png","image/png");
        Shadows.shadowOf(android.webkit.MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("jpg","image/jpeg");
    }
    @Test public void cameraAndReviewResolveToSeparateNonexportedProviderComponents() throws Exception {
        Context app=RuntimeEnvironment.getApplication();
        android.content.pm.ProviderInfo camera=app.getPackageManager().resolveContentProvider(app.getPackageName()+".evidence",0);
        android.content.pm.ProviderInfo review=app.getPackageManager().resolveContentProvider(app.getPackageName()+".documents",0);
        assertNotNull(camera); assertNotNull(review); assertNotEquals(camera.name,review.name);
        assertFalse(camera.exported); assertFalse(review.exported); assertTrue(camera.grantUriPermissions); assertTrue(review.grantUriPermissions);
        assertTrue(androidx.core.content.FileProvider.class.isAssignableFrom(Class.forName(camera.name)));
        assertTrue(androidx.core.content.FileProvider.class.isAssignableFrom(Class.forName(review.name)));
    }
    @Test public void ownedReviewFileUsesOnlyItsNarrowViewerProviderAndIsRemovedOnClose() throws Exception {
        Context app=RuntimeEnvironment.getApplication(); PrivateDocument document=PrivateDocument.create(app,"application/pdf");
        assertNotNull(document); Uri uri=document.uri();
        try {
            assertEquals("content",uri.getScheme()); assertEquals(app.getPackageName()+".documents",uri.getAuthority()); assertTrue(uri.getPath().startsWith("/review/"));
            try(OutputStream out=document.openOutput()) { out.write(new byte[]{1,2,3,4}); }
            assertEquals("application/pdf",app.getContentResolver().getType(uri));
            try(InputStream in=app.getContentResolver().openInputStream(uri)) { assertArrayEquals(new byte[]{1,2,3,4},in.readAllBytes()); }
        } finally { document.close(); }
        try { app.getContentResolver().openInputStream(uri); fail("Closed review file must be removed"); } catch(java.io.FileNotFoundException expected) {}
    }
    @Test public void aReviewFileCannotBeRewrittenOrReopenedAfterClosure() throws Exception {
        PrivateDocument document=PrivateDocument.create(RuntimeEnvironment.getApplication(),"image/png"); assertNotNull(document);
        try {
            try(OutputStream out=document.openOutput()) { out.write(1); }
            try { document.openOutput(); fail("Only the owned download writes this file once"); } catch(IllegalStateException expected) {}
        } finally { document.close(); }
        try { document.uri(); fail("Closed URI must not be republished"); } catch(IllegalStateException expected) {}
        try { document.openOutput(); fail("Closed output must not reopen"); } catch(IllegalStateException expected) {}
    }
    @Test public void unsupportedMimeDoesNotAllocateAPrivateCacheFile() throws Exception {
        Context app=RuntimeEnvironment.getApplication(); int before=count(app);
        try { PrivateDocument.create(app,"text/html"); fail("Only the reviewed file formats may be opened"); } catch(IllegalArgumentException expected) {}
        assertEquals(before,count(app));
    }
    @Test public void closingOneFileTwiceDoesNotRemoveAnotherOwnedReview() throws Exception {
        Context app=RuntimeEnvironment.getApplication(); PrivateDocument first=PrivateDocument.create(app,"image/jpeg"),second=PrivateDocument.create(app,"application/pdf");
        assertNotNull(first); assertNotNull(second);
        try {
            try(OutputStream out=second.openOutput()) { out.write(7); }
            first.close(); first.close();
            try(InputStream in=app.getContentResolver().openInputStream(second.uri())) { assertEquals(7,in.read()); }
        } finally { first.close(); second.close(); }
    }
    @Test public void viewerProviderCannotTraverseOutsideItsReviewDirectory() throws Exception {
        Context app=RuntimeEnvironment.getApplication(); PrivateDocument document=PrivateDocument.create(app,"application/pdf"); assertNotNull(document);
        try {
            Uri escaped=new Uri.Builder().scheme("content").authority(app.getPackageName()+".documents").encodedPath("/review/..%2Foutside.pdf").build();
            try { app.getContentResolver().openInputStream(escaped); fail("Review provider must reject traversal"); } catch(SecurityException expected) {}
        } finally { document.close(); }
    }
    @Test public void failedProviderMappingRemovesOnlyTheNewlyAllocatedFile() throws Exception {
        Context app=RuntimeEnvironment.getApplication(); int before=count(app);
        Context bad=new ContextWrapper(app) {
            @Override public Context getApplicationContext() { return this; }
            @Override public String getPackageName() { return "fixture.unregistered"; }
        };
        try { PrivateDocument.create(bad,"application/pdf"); fail("Provider is deliberately absent"); } catch(IllegalArgumentException expected) {}
        assertEquals(before,count(app));
    }
    @Test public void completedEvidenceTransferReturnsTheOwnedPrivateCopy() throws Exception {
        Context app=RuntimeEnvironment.getApplication(); Transfer wire=new Transfer();
        try(PrivateDocument document=new DeliveryRepository(wire).evidenceDocument(app,file())) {
            assertNotNull(document); assertEquals(1,wire.downloads);
            try(InputStream in=app.getContentResolver().openInputStream(document.uri())) { assertArrayEquals(new byte[]{1,2,3,4},in.readAllBytes()); }
        }
    }
    @Test public void incompleteEvidenceTransferRemovesItsPartialPrivateCopy() throws Exception {
        Context app=RuntimeEnvironment.getApplication(); int before=count(app); Transfer wire=new Transfer(); wire.count=3;
        try { new DeliveryRepository(wire).evidenceDocument(app,file()); fail("Partial copy must not be published"); }
        catch(RequestRepository.WorkflowException expected) { assertEquals("UPLOAD_INVALID",expected.code); }
        assertEquals(before,count(app));
    }
    @Test public void replacedAccountDuringTransferDeletesTheUnpublishedPrivateCopy() throws Exception {
        Context app=RuntimeEnvironment.getApplication(); int before=count(app); Transfer wire=new Transfer(); wire.replace=true;
        try { new DeliveryRepository(wire).evidenceDocument(app,file()); fail("Replaced account must not publish a copy"); }
        catch(IllegalStateException expected) {}
        assertEquals(before,count(app));
    }
    private static ph.edu.tip.goserve.domain.DeliveryData.File file() {
        return new ph.edu.tip.goserve.domain.DeliveryData.File(id(1),id(90),id(60),id(70),ph.edu.tip.goserve.domain.DeliveryData.FileKind.EVIDENCE,
            null,"application/pdf",4,"verified",1,true,"2026-10-21T01:00:00Z",null);
    }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static final class Transfer implements DeliveryRepository.Transport {
        int count=4,downloads; boolean current=true,replace;
        public String accountId() { return id(90); } public void requireCurrent() { if(!current)throw new IllegalStateException("Account replaced"); }
        public org.json.JSONArray get(String path) { return new org.json.JSONArray(); }
        public long downloadTo(String edge,org.json.JSONObject args,OutputStream out,long maximum,String sha) throws Exception {
            ++downloads; out.write(new byte[]{1,2,3,4},0,count); if(replace)current=false; return count;
        }
    }
    private static int count(Context context) {
        File[] files=new File(context.getCacheDir(),"request-review").listFiles(); return files==null?0:files.length;
    }
}
