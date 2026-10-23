package ph.edu.tip.goserve.data;

import android.content.Context;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.IOException;

public final class CameraEvidence implements AutoCloseable {
    private final Context app;
    private final File file;
    private final Uri uri;
    private boolean closed;
    private CameraEvidence(Context app,File file,Uri uri) { this.app=app; this.file=file; this.uri=uri; }
    public static CameraEvidence prepare(Context context) throws Exception {
        Context app=context.getApplicationContext(); File directory=new File(app.getCacheDir(),"request-evidence");
        if(!directory.isDirectory() && !directory.mkdirs())throw new IOException("Camera output unavailable");
        File file=File.createTempFile("capture-",".jpg",directory);
        try { return new CameraEvidence(app,file,FileProvider.getUriForFile(app,app.getPackageName()+".evidence",file)); }
        catch(RuntimeException error) { file.delete(); throw error; }
    }
    public Uri uri() { return uri; }
    public EvidenceInput.Source source() { if(closed)throw new IllegalStateException("Camera output closed"); return new ContentEvidenceSource(app,uri); }
    @Override public void close() { if(!closed) { closed=true; file.delete(); } }
}
