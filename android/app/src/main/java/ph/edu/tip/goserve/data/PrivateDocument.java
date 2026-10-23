package ph.edu.tip.goserve.data;

import android.content.Context;
import android.net.Uri;
import android.content.Intent;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public interface PrivateDocument extends AutoCloseable {
    Uri uri();
    String mimeType();
    OutputStream openOutput() throws Exception;
    @Override void close();
    static PrivateDocument create(Context context,String mimeType) throws Exception {
        String extension;
        if("application/pdf".equals(mimeType))extension=".pdf";
        else if("image/png".equals(mimeType))extension=".png";
        else if("image/jpeg".equals(mimeType))extension=".jpg";
        else throw new IllegalArgumentException("Unsupported review format");
        Context app=context.getApplicationContext(); File directory=new File(app.getCacheDir(),"request-review");
        if(!directory.isDirectory() && !directory.mkdirs())throw new IOException("Private review storage is unavailable");
        File file=File.createTempFile("review-",extension,directory);
        try {
            Uri uri=FileProvider.getUriForFile(app,app.getPackageName()+".documents",file);
            return new Stored(app,file,uri,mimeType);
        } catch(Exception error) { file.delete(); throw error; }
    }
    final class Stored implements PrivateDocument {
        private final Context app;
        private final File file;
        private final Uri uri;
        private final String mime;
        private final AtomicBoolean closed=new AtomicBoolean(),outputClaimed=new AtomicBoolean();
        private Stored(Context app,File file,Uri uri,String mime) { this.app=app; this.file=file; this.uri=uri; this.mime=mime; }
        public Uri uri() { if(closed.get())throw new IllegalStateException("Private review is closed"); return uri; }
        public String mimeType() { return mime; }
        public OutputStream openOutput() throws Exception {
            if(closed.get() || !outputClaimed.compareAndSet(false,true))throw new IllegalStateException("Private review output is closed");
            return new FileOutputStream(file);
        }
        public void close() {
            if(!closed.compareAndSet(false,true))return;
            try { app.revokeUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION); }
            finally { file.delete(); }
        }
    }
}
