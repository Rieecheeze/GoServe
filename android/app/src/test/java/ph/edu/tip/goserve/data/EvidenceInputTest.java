package ph.edu.tip.goserve.data;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import ph.edu.tip.goserve.domain.RequestData;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
public final class EvidenceInputTest {
    @Test public void selectedBytesUseTheActualMimeAndPrivateDisplayNameWithoutPersistingAUri() throws Exception {
        Source source=new Source(); EvidenceInput.Selection file=EvidenceInput.read(source,policy(12),20);
        assertNotNull(file); assertEquals("evidence.pdf",file.name); assertEquals("application/pdf",file.mimeType);
        assertArrayEquals(new byte[]{1,2,3,4},file.bytes()); assertTrue(source.closed);
    }
    @Test public void absentOrUnavailablePolicyDoesNotOpenTheSourceOrInventAcceptedTypes() throws Exception {
        for(RequestData.FileOptions policy:new RequestData.FileOptions[]{null,new RequestData.FileOptions(false,"CONFIGURATION_REQUIRED",null,null)}) {
            Source source=new Source(); expect("CONFIGURATION_REQUIRED",()->EvidenceInput.read(source,policy,20)); assertEquals(0,source.opens);
        }
    }
    @Test public void unsupportedOrMissingMimeFailsBeforeReadingRegardlessOfExtension() throws Exception {
        for(String mime:new String[]{null,"text/plain","image/jpeg"}) { Source source=new Source(); source.mime=mime;
            expect("UPLOAD_INVALID",()->EvidenceInput.read(source,policy(12),20)); assertEquals(0,source.opens); }
    }
    @Test public void declaredOversizeIsRejectedBeforeOpeningTheStream() throws Exception {
        Source source=new Source(); source.size=13L; expect("UPLOAD_TOO_LARGE",()->EvidenceInput.read(source,policy(12),20)); assertEquals(0,source.opens);
    }
    @Test public void actualBytesAreBoundedEvenWhenTheProviderLiesAboutItsSize() throws Exception {
        Source source=new Source(); source.size=1L; source.data=new byte[13]; expect("UPLOAD_TOO_LARGE",()->EvidenceInput.read(source,policy(12),20)); assertTrue(source.closed);
    }
    @Test public void unknownSizeStillStreamsWithinTheCanonicalMaximumAndAcceptsItsBoundary() throws Exception {
        Source source=new Source(); source.size=null; source.data=new byte[12]; EvidenceInput.Selection file=EvidenceInput.read(source,policy(12),20); assertEquals(12,file.bytes().length);
    }
    @Test public void deviceMemoryLimitIsSeparateFromTheServicePolicy() throws Exception {
        Source source=new Source(); source.size=9L; expect("DEVICE_FILE_LIMIT",()->EvidenceInput.read(source,policy(12),8)); assertEquals(0,source.opens);
    }
    @Test public void emptyAndBrokenSourcesCannotBecomeUsableEvidence() throws Exception {
        Source empty=new Source(); empty.data=new byte[0]; expect("UPLOAD_INVALID",()->EvidenceInput.read(empty,policy(12),20)); assertTrue(empty.closed);
        Source broken=new Source(); broken.broken=true; try { EvidenceInput.read(broken,policy(12),20); fail("Broken stream must fail"); } catch(java.io.IOException expected) { assertTrue(broken.closed); }
    }
    @Test public void filenamesCannotExposeProviderPathsOrControlCharacters() throws Exception {
        Source source=new Source(); source.name="C:\\private\\\u0000my\nfile.pdf"; EvidenceInput.Selection file=EvidenceInput.read(source,policy(12),20);
        assertEquals("myfile.pdf",file.name); assertFalse(file.name.contains("private"));
    }
    @Test public void closingASelectionWipesOwnedBytesAndPreventsReuse() throws Exception {
        EvidenceInput.Selection file=EvidenceInput.read(new Source(),policy(12),20); byte[] owned=file.bytes(); file.close();
        assertArrayEquals(new byte[4],owned); try { file.bytes(); fail("Closed data must not be available"); } catch(IllegalStateException expected) {}
    }
    private static RequestData.FileOptions policy(long bytes) { return new RequestData.FileOptions(true,null,List.of("application/pdf"),bytes); }
    private interface Work { void run() throws Exception; }
    private static void expect(String code,Work work) throws Exception { try { work.run(); fail("Expected "+code); } catch(RequestRepository.WorkflowException error) { assertEquals(code,error.code); } }
    private static final class Source implements EvidenceInput.Source {
        String name="evidence.pdf",mime="application/pdf"; Long size=4L; byte[] data={1,2,3,4}; boolean closed,broken; int opens;
        public String name() { return name; } public String mimeType() { return mime; } public Long declaredSize() { return size; }
        public InputStream open() { ++opens; return new InputStream() {
            final ByteArrayInputStream delegate=new ByteArrayInputStream(data);
            @Override public int read() throws java.io.IOException { if(broken)throw new java.io.IOException("fixture read failure"); return delegate.read(); }
            @Override public int read(byte[] buffer,int offset,int count) throws java.io.IOException { if(broken)throw new java.io.IOException("fixture read failure"); return delegate.read(buffer,offset,count); }
            @Override public void close() { closed=true; }
        }; }
    }
}
