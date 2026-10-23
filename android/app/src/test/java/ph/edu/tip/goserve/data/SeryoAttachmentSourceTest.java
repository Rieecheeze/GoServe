package ph.edu.tip.goserve.data;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public final class SeryoAttachmentSourceTest {
    @Test public void exactTenMiBIsAcceptedAndActualOversizeRejectedRegardlessOfMetadata()throws Exception {
        byte[] bytes=new byte[SeryoAttachmentSource.MAX_BYTES];
        assertEquals(bytes.length,SeryoAttachmentSource.readBounded(new ByteArrayInputStream(bytes),()->{}).length);
        try {SeryoAttachmentSource.readBounded(new ByteArrayInputStream(new byte[bytes.length+1]),()->{});fail();}catch(IOException expected){}
    }
    @Test public void emptyAndUnreadableStreamsAreRejectedAndClosed()throws Exception {
        final boolean[] closed={false};
        try {SeryoAttachmentSource.readBounded(new ByteArrayInputStream(new byte[0]) {@Override public void close(){closed[0]=true;}},()->{});fail();}catch(IOException expected){}
        assertTrue(closed[0]);
        try {SeryoAttachmentSource.readBounded(new java.io.InputStream(){public int read()throws IOException {throw new IOException();}},()->{});fail();}catch(IOException expected){}
    }
    @Test public void streamIsClosedWhenAccountIsInvalidated()throws Exception {
        final boolean[] closed={false};
        try {SeryoAttachmentSource.readBounded(new ByteArrayInputStream(new byte[]{1}) {@Override public void close(){closed[0]=true;}},()->{throw new IllegalStateException();});fail();}catch(IllegalStateException expected){}
        assertTrue(closed[0]);
    }
    @Test public void unsupportedOrTruncatedSignaturesFailBeforeRendering()throws Exception {
        for(byte[] input:new byte[][]{new byte[]{1,2,3},"%PDF-1.7\ntruncated".getBytes(java.nio.charset.StandardCharsets.US_ASCII),new byte[]{(byte)0xff,(byte)0xd8}}) {
            try {SeryoAttachmentSource.detect(input);fail();}catch(IOException expected){}
        }
    }
}
