package ph.edu.tip.goserve.data;

import android.net.Uri;
import java.io.InputStream;
import java.io.OutputStream;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.GoServeApplication;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=GoServeApplication.class,shadows=AndroidFileProviderPaths.class,instrumentedPackages="androidx.core.content")
public final class CameraEvidenceTest {
    @Before public void resetProviderRoots() {
        AndroidFileProviderPaths.reset();
        org.robolectric.Shadows.shadowOf(android.webkit.MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("jpg","image/jpeg");
    }
    @Test public void cameraOutputIsScopedPrivateContentAndCanBeReadOnlyWhileOwned() throws Exception {
        android.content.Context app=RuntimeEnvironment.getApplication(); CameraEvidence capture=CameraEvidence.prepare(app);
        assertNotNull(capture); Uri uri=capture.uri(); assertEquals("content",uri.getScheme()); assertEquals(app.getPackageName()+".evidence",uri.getAuthority());
        try {
            try(OutputStream output=app.getContentResolver().openOutputStream(uri)) { assertNotNull(output); output.write(new byte[]{1,2,3,4}); }
            assertEquals("image/jpeg",capture.source().mimeType()); assertEquals(Long.valueOf(4),capture.source().declaredSize());
            try(InputStream source=capture.source().open()) { assertEquals(1,source.read()); }
        } finally { capture.close(); }
        try { app.getContentResolver().openInputStream(uri); fail("Closed camera output must be removed"); } catch(java.io.FileNotFoundException expected) {}
    }
    @Test public void eachCaptureOwnsASeparateFileAndClosingItCannotDeleteTheOther() throws Exception {
        android.content.Context app=RuntimeEnvironment.getApplication(); CameraEvidence first=CameraEvidence.prepare(app),second=CameraEvidence.prepare(app);
        assertNotNull(first); assertNotNull(second); assertNotEquals(first.uri(),second.uri());
        first.close();
        try(OutputStream output=app.getContentResolver().openOutputStream(second.uri())) { assertNotNull(output); output.write(8); }
        finally { second.close(); }
    }
    @Test public void documentSourcesRejectNetworkAndRawFilesystemUris() {
        for(String uri:new String[]{"file:///private/photo.jpg","https://example.test/file.pdf"}) {
            try { new ContentEvidenceSource(RuntimeEnvironment.getApplication(),Uri.parse(uri)); fail("Only document providers are accepted"); }
            catch(IllegalArgumentException expected) {}
        }
    }
    @Test public void providerRejectsPathsThatEscapeItsNarrowCacheDirectory() throws Exception {
        android.content.Context app=RuntimeEnvironment.getApplication(); CameraEvidence capture=CameraEvidence.prepare(app);
        try {
            Uri escape=Uri.parse("content://"+app.getPackageName()+".evidence/evidence/..%2F..%2Fescape.jpg");
            try { app.getContentResolver().openInputStream(escape); fail("Traversal must remain blocked"); }
            catch(SecurityException expected) {}
        } finally { capture.close(); }
    }
}
