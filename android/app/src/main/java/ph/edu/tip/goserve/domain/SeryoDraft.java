package ph.edu.tip.goserve.domain;

public final class SeryoDraft {
    public enum Operation { NONE, EXPLAIN, EXTRACT }
    public static final class Attachment implements AutoCloseable {
        public final String name,mime;
        public final int size;
        private byte[] bytes;
        public Attachment(String name,String mime,byte[] bytes) { this.name=name; this.mime=mime; this.bytes=bytes; size=bytes.length; }
        public boolean released() { return bytes==null; }
        public void close() { if(bytes!=null) java.util.Arrays.fill(bytes,(byte)0);bytes=null; }
    }
    public String text="";
    public Operation operation=Operation.NONE;
    public Attachment attachment;
    public void replace(Attachment next) { if(next==null)throw new IllegalArgumentException("Choose one file.");if(attachment!=null)attachment.close();attachment=next;operation=Operation.NONE; }
    public void prepare(Operation next) { if(attachment==null || next==null || next==Operation.NONE)throw new IllegalStateException("Choose a file first.");operation=next; }
    public void remove() { if(attachment!=null)attachment.close();attachment=null;operation=Operation.NONE; }
    public void clear() { remove();text=""; }
}
