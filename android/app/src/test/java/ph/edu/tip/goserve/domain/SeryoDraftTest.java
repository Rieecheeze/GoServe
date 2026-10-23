package ph.edu.tip.goserve.domain;

import org.junit.Test;
import static org.junit.Assert.*;

public final class SeryoDraftTest {
    @Test public void replacementReleasesOldFileAndRetainsOnlyOne() {
        SeryoDraft draft=new SeryoDraft();byte[] bytes={1,2,3};
        SeryoDraft.Attachment first=new SeryoDraft.Attachment("first","image/png",bytes);
        draft.replace(first);draft.replace(new SeryoDraft.Attachment("second","image/png",new byte[]{4}));
        assertTrue(first.released());assertArrayEquals(new byte[]{0,0,0},bytes);assertEquals("second",draft.attachment.name);
    }
    @Test public void removeClearsAssociatedUnsentOperationAndPreservesTypedText() {
        SeryoDraft draft=new SeryoDraft();draft.text="My own question";
        draft.replace(new SeryoDraft.Attachment("file","application/pdf",new byte[]{1}));draft.prepare(SeryoDraft.Operation.EXPLAIN);
        assertEquals(SeryoDraft.Operation.EXPLAIN,draft.operation);assertEquals("My own question",draft.text);
        draft.remove();assertNull(draft.attachment);assertEquals(SeryoDraft.Operation.NONE,draft.operation);assertEquals("My own question",draft.text);
    }
    @Test public void accountCleanupDropsAllPrivateContext() {
        SeryoDraft draft=new SeryoDraft();draft.text="Private";draft.replace(new SeryoDraft.Attachment("file","image/png",new byte[]{1}));draft.prepare(SeryoDraft.Operation.EXTRACT);
        SeryoDraft.Attachment old=draft.attachment;draft.clear();assertTrue(old.released());assertNull(draft.attachment);assertEquals("",draft.text);assertEquals(SeryoDraft.Operation.NONE,draft.operation);
    }
}
