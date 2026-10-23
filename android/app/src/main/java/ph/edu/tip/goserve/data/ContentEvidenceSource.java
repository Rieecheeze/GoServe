package ph.edu.tip.goserve.data;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.InputStream;

public final class ContentEvidenceSource implements EvidenceInput.Source {
    private final ContentResolver resolver;
    private final Uri uri;
    private boolean loaded;
    private String name;
    private Long size;
    public ContentEvidenceSource(Context context,Uri uri) {
        if(uri==null || !"content".equals(uri.getScheme()))throw new IllegalArgumentException("Choose a document provider file");
        this.resolver=context.getApplicationContext().getContentResolver(); this.uri=uri;
    }
    private void metadata() {
        if(loaded)return;
        try(Cursor row=resolver.query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)) {
            if(row!=null && row.moveToFirst()) {
                int nameColumn=row.getColumnIndex(OpenableColumns.DISPLAY_NAME),sizeColumn=row.getColumnIndex(OpenableColumns.SIZE);
                if(nameColumn>=0 && !row.isNull(nameColumn))name=row.getString(nameColumn);
                if(sizeColumn>=0 && !row.isNull(sizeColumn)) { long value=row.getLong(sizeColumn); if(value>=0)size=value; }
            }
        }
        loaded=true;
    }
    @Override public String name() { metadata(); return name; }
    @Override public String mimeType() { return resolver.getType(uri); }
    @Override public Long declaredSize() { metadata(); return size; }
    @Override public InputStream open() throws Exception { return resolver.openInputStream(uri); }
}
