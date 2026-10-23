package ph.edu.tip.goserve.data;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Arrays;
import ph.edu.tip.goserve.domain.RequestData;

public final class EvidenceInput {
    public interface Source {
        String name() throws Exception;
        String mimeType() throws Exception;
        Long declaredSize() throws Exception;
        InputStream open() throws Exception;
    }
    public static final class Selection implements AutoCloseable {
        public final String name,mimeType;
        private byte[] bytes;
        Selection(String name,String mimeType,byte[] bytes) { this.name=name; this.mimeType=mimeType; this.bytes=bytes; }
        public synchronized byte[] bytes() { if(bytes==null)throw new IllegalStateException("Selection closed"); return bytes; }
        @Override public synchronized void close() { if(bytes!=null)Arrays.fill(bytes,(byte)0); bytes=null; }
    }
    public static Selection read(Source source,RequestData.FileOptions policy,long deviceLimit) throws Exception {
        if(policy==null || !policy.available)throw failure("CONFIGURATION_REQUIRED");
        if(policy.mimeTypes==null || policy.mimeTypes.isEmpty() || policy.maxBytes==null || policy.maxBytes<1)throw failure("VALIDATION_FAILED");
        for(String mime:policy.mimeTypes)if(!("application/pdf".equals(mime) || "image/png".equals(mime) || "image/jpeg".equals(mime)))throw failure("VALIDATION_FAILED");
        if(source==null)throw failure("UPLOAD_INVALID");
        String mime=source.mimeType(); if(mime==null || !policy.mimeTypes.contains(mime))throw failure("UPLOAD_INVALID");
        if(deviceLimit<1)throw failure("DEVICE_FILE_LIMIT");
        long maximum=Math.min(policy.maxBytes,Math.min(deviceLimit,Integer.MAX_VALUE-8L));
        Long declared=source.declaredSize();
        if(declared!=null && declared>policy.maxBytes)throw failure("UPLOAD_TOO_LARGE");
        if(declared!=null && declared>maximum)throw failure("DEVICE_FILE_LIMIT");
        String name=safeName(source.name()); byte[] chunk=new byte[(int)Math.min(8192,maximum+1)];
        Buffer buffer=new Buffer();
        try(InputStream input=source.open()) {
            if(input==null)throw new IOException("File unavailable");
            long total=0;
            while(true) {
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Selection cancelled");
                int count=input.read(chunk);
                if(count<0)break;
                if(count==0) { int single=input.read(); if(single<0)break; chunk[0]=(byte)single; count=1; }
                total+=count;
                if(total>policy.maxBytes)throw failure("UPLOAD_TOO_LARGE");
                if(total>maximum)throw failure("DEVICE_FILE_LIMIT");
                buffer.write(chunk,0,count);
            }
            if(total==0)throw failure("UPLOAD_INVALID");
            return new Selection(name,mime,buffer.toByteArray());
        } finally { buffer.wipe(); Arrays.fill(chunk,(byte)0); }
    }
    private static String safeName(String raw) {
        if(raw==null)return "Selected file";
        String path=raw.replace('\\','/'); String leaf=path.substring(path.lastIndexOf('/')+1);
        StringBuilder name=new StringBuilder(); leaf.codePoints().filter(c->!Character.isISOControl(c)).limit(120).forEach(name::appendCodePoint);
        return name.toString().trim().isEmpty()?"Selected file":name.toString().trim();
    }
    private static RequestRepository.WorkflowException failure(String code) { return new RequestRepository.WorkflowException(code); }
    private static final class Buffer extends ByteArrayOutputStream {
        void wipe() { Arrays.fill(buf,(byte)0); reset(); }
    }
    private EvidenceInput() {}
}
