package ph.edu.tip.goserve.data;

import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Implementation;
import org.robolectric.shadows.ShadowContentResolver;
import ph.edu.tip.goserve.domain.DeliveryData;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,application=Application.class,shadows={AndroidFileProviderPaths.class,DocumentSaverTest.Outputs.class,DocumentSaverTest.Deletes.class},instrumentedPackages={"androidx.core.content"})
public final class DocumentSaverTest {
    private static final Uri DESTINATION=Uri.parse("content://fixture.documents/document/new-pdf");
    private static final String SHA="9f64a747e1b97f131fabb6b447296c9b6f0201e79fb3c5356e6c77e89b6a806a";
    @Before public void reset() throws Exception {
        AndroidFileProviderPaths.reset(); Shadows.shadowOf(android.webkit.MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("pdf","application/pdf");
        Outputs.out=new Sink(); Outputs.mode=null; Outputs.uri=null; Deletes.calls.clear(); Deletes.deleted=true; Metadata.fail=false; Metadata.name="picked.pdf";
        ShadowContentResolver.registerProviderInternal("fixture.documents",new Metadata());
    }
    @Test public void completeCopyClosesOutputAndUsesOnlyTheCreatedUriWithExplicitTruncation() throws Exception {
        Lease lease=new Lease(); try(PrivateDocument source=source(new byte[]{1,2,3,4})) {
            DocumentSaver.Saved saved=saver(lease).save(source,file(4),ticket(SHA),DESTINATION); assertNotNull(saved);
            assertEquals(DESTINATION,Outputs.uri); assertEquals("wt",Outputs.mode); assertArrayEquals(new byte[]{1,2,3,4},Outputs.out.bytes.toByteArray()); assertTrue(Outputs.out.flushed); assertTrue(Outputs.out.closed);
            assertEquals("picked.pdf",saved.displayName); assertTrue(Deletes.calls.isEmpty()); assertNotNull(source.uri());
        }
    }
    @Test public void aFilePathOrMismatchedTicketCannotOpenAnOutput() throws Exception {
        try(PrivateDocument source=source(new byte[]{1,2,3,4})) {
            try { saver(new Lease()).save(source,file(4),ticket(SHA),Uri.parse("file:///arbitrary.pdf")); fail("Only a content URI from the save picker"); } catch(RequestRepository.WorkflowException expected) { assertEquals("VALIDATION_FAILED",expected.code); }
            DeliveryData.DownloadTicket wrong=new DeliveryData.DownloadTicket(id(70),id(99),ticket(SHA).expiresAt,SHA);
            try { saver(new Lease()).save(source,file(4),wrong,DESTINATION); fail("Wrong ticket"); } catch(RequestRepository.WorkflowException expected) { assertEquals("FORBIDDEN",expected.code); }
            assertNull(Outputs.uri); assertTrue(Deletes.calls.isEmpty());
        }
    }
    @Test public void aFailedWriteDoesNotReturnSavedAndDeletesOnlyTheNewDestination() throws Exception {
        Outputs.out.failAfter=2;
        try(PrivateDocument source=source(new byte[]{1,2,3,4})) {
            try { saver(new Lease()).save(source,file(4),ticket(SHA),DESTINATION); fail("Partial write"); }
            catch(DocumentSaver.SaveException expected) { assertEquals("DOCUMENT_SAVE_FAILED",expected.code); assertFalse(expected.partialMayRemain); }
            assertTrue(Outputs.out.closed); assertEquals(List.of(DESTINATION),Deletes.calls); assertNotNull(source.uri());
        }
    }
    @Test public void outputCloseFailureNeverConfirmsASavedDocument() throws Exception {
        Outputs.out.failClose=true;
        try(PrivateDocument source=source(new byte[]{1,2,3,4})) {
            try { saver(new Lease()).save(source,file(4),ticket(SHA),DESTINATION); fail("Output close must succeed"); }
            catch(DocumentSaver.SaveException expected) { assertEquals("DOCUMENT_SAVE_FAILED",expected.code); }
            assertTrue(Outputs.out.closed); assertEquals(List.of(DESTINATION),Deletes.calls);
        }
    }
    @Test public void modifiedCacheDigestCannotBecomeASavedConfirmation() throws Exception {
        try(PrivateDocument source=source(new byte[]{1,2,3,4})) {
            try { saver(new Lease()).save(source,file(4),ticket("b".repeat(64)),DESTINATION); fail("Digest mismatch"); }
            catch(DocumentSaver.SaveException expected) { assertEquals("DOWNLOAD_UNCONFIRMED",expected.code); }
            assertEquals(List.of(DESTINATION),Deletes.calls); assertTrue(Outputs.out.closed);
        }
    }
    @Test public void oversizedOrTruncatedCacheCannotConfirmSaved() throws Exception {
        for(byte[] bytes:new byte[][]{{1,2,3},{1,2,3,4,5}}) {
            Outputs.out=new Sink(); Deletes.calls.clear();
            try(PrivateDocument source=source(bytes)) {
                try { saver(new Lease()).save(source,file(4),ticket(SHA),DESTINATION); fail("Wrong byte count"); }
                catch(DocumentSaver.SaveException expected) { assertEquals("DOWNLOAD_UNCONFIRMED",expected.code); }
                assertEquals(List.of(DESTINATION),Deletes.calls);
            }
        }
    }
    @Test public void accountReplacementDuringCopyStopsConfirmationAndDiscardsTheNewTarget() throws Exception {
        Lease lease=new Lease(); Outputs.out.afterWrite=()->lease.current=false;
        try(PrivateDocument source=source(new byte[]{1,2,3,4})) {
            try { saver(lease).save(source,file(4),ticket(SHA),DESTINATION); fail("Replaced account"); } catch(IllegalStateException expected) {}
            assertEquals(List.of(DESTINATION),Deletes.calls); assertTrue(Outputs.out.closed);
        }
    }
    @Test public void accountReplacementDuringSuccessfulOutputCloseStillDiscardsTheCreatedDocument() throws Exception {
        Lease lease=new Lease(); Outputs.out.afterClose=()->lease.current=false;
        try(PrivateDocument source=source(new byte[]{1,2,3,4})) {
            try { saver(lease).save(source,file(4),ticket(SHA),DESTINATION); fail("Output close cannot publish for a replaced account"); } catch(IllegalStateException expected) {}
            assertTrue(Outputs.out.closed); assertEquals(List.of(DESTINATION),Deletes.calls);
        }
    }
    @Test public void failedProviderCleanupIsReportedWithoutClaimingSaveSuccess() throws Exception {
        Deletes.deleted=false; Outputs.out.failAfter=2;
        try(PrivateDocument source=source(new byte[]{1,2,3,4})) {
            try { saver(new Lease()).save(source,file(4),ticket(SHA),DESTINATION); fail("Partial copy"); }
            catch(DocumentSaver.SaveException expected) { assertTrue(expected.partialMayRemain); assertEquals("DOCUMENT_SAVE_FAILED",expected.code); }
        }
    }
    @Test public void displayNameIsBoundedAndNormalizedFromTheActualProviderMetadata() throws Exception {
        Metadata.name="../../private\\picked.pdf";
        try(PrivateDocument source=source(new byte[]{1,2,3,4})) { assertEquals("picked.pdf",saver(new Lease()).save(source,file(4),ticket(SHA),DESTINATION).displayName); }
    }
    @Test public void optionalNameQueryFailureDoesNotUndoACompleteClosedOutput() throws Exception {
        Metadata.fail=true;
        try(PrivateDocument source=source(new byte[]{1,2,3,4})) {
            DocumentSaver.Saved saved=saver(new Lease()).save(source,file(4),ticket(SHA),DESTINATION); assertNotNull(saved); assertNull(saved.displayName); assertTrue(Outputs.out.closed); assertTrue(Deletes.calls.isEmpty());
        }
    }
    private static DocumentSaver saver(Lease lease) { return new DocumentSaver(RuntimeEnvironment.getApplication(),lease::check); }
    private static PrivateDocument source(byte[] bytes) throws Exception { PrivateDocument source=PrivateDocument.create(RuntimeEnvironment.getApplication(),"application/pdf"); try(OutputStream out=source.openOutput()) { out.write(bytes); } return source; }
    private static String id(int n) { return String.format(java.util.Locale.ROOT,"00000000-0000-4000-8000-%012d",n); }
    private static DeliveryData.File file(long size) { return new DeliveryData.File(id(5),id(90),id(50),id(1),DeliveryData.FileKind.DELIVERABLE,null,"application/pdf",size,"verified",1,true,"2026-10-22T01:00:00Z",null); }
    private static DeliveryData.DownloadTicket ticket(String sha) { return new DeliveryData.DownloadTicket(id(70),id(5),"2026-10-22T01:00:00Z",sha); }
    private static final class Lease { boolean current=true; void check() { if(!current)throw new IllegalStateException("Replaced account"); } }
    private static final class Sink extends OutputStream {
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream(); boolean flushed,closed,failClose; int failAfter=Integer.MAX_VALUE; Runnable afterWrite,afterClose;
        @Override public void write(int value) throws IOException { if(bytes.size()>=failAfter)throw new IOException("Write failed"); bytes.write(value); if(afterWrite!=null)afterWrite.run(); }
        @Override public void flush() { flushed=true; }
        @Override public void close() throws IOException { closed=true; if(afterClose!=null)afterClose.run(); if(failClose)throw new IOException("Close failed"); }
    }
    @Implements(ContentResolver.class)
    public static final class Outputs extends ShadowContentResolver {
        static Sink out; static String mode; static Uri uri;
        @Implementation @Override protected OutputStream openOutputStream(Uri uri,String mode) throws FileNotFoundException {
            if("fixture.documents".equals(uri.getAuthority())) { Outputs.uri=uri; Outputs.mode=mode; return out; }
            return super.openOutputStream(uri,mode);
        }
    }
    @Implements(DocumentsContract.class)
    public static final class Deletes {
        static final List<Uri> calls=new ArrayList<>(); static boolean deleted;
        @Implementation protected static boolean deleteDocument(ContentResolver resolver,Uri uri) { calls.add(uri); return deleted; }
    }
    public static final class Metadata extends ContentProvider {
        static boolean fail; static String name;
        @Override public boolean onCreate() { return true; }
        @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
            if(fail)throw new IllegalStateException("Provider metadata unavailable"); MatrixCursor cursor=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}); cursor.addRow(new Object[]{name,4}); return cursor;
        }
        @Override public String getType(Uri uri) { return "application/pdf"; }
        @Override public Uri insert(Uri uri,ContentValues values) { throw new UnsupportedOperationException(); }
        @Override public int delete(Uri uri,String selection,String[] args) { throw new UnsupportedOperationException(); }
        @Override public int update(Uri uri,ContentValues values,String selection,String[] args) { throw new UnsupportedOperationException(); }
    }
}
