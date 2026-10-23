package ph.edu.tip.goserve.data;

import android.content.Context;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.security.MessageDigest;
import java.util.Arrays;
import ph.edu.tip.goserve.domain.DeliveryData;

public final class DocumentSaver {
    public static final class Saved {
        public final String displayName;
        public Saved(String displayName) { this.displayName=displayName; }
    }
    public static final class SaveException extends Exception {
        public final String code;
        public final boolean partialMayRemain;
        SaveException(String code,boolean partialMayRemain,Exception cause) { super(code,cause); this.code=code; this.partialMayRemain=partialMayRemain; }
    }
    private final ContentResolver resolver;
    private final Runnable requireCurrent;
    public DocumentSaver(Context context,AccountGateway gateway) { this(context,gateway::requireCurrent); }
    DocumentSaver(Context context,Runnable requireCurrent) { resolver=context.getApplicationContext().getContentResolver(); this.requireCurrent=requireCurrent; }
    public Saved save(PrivateDocument source,DeliveryData.File file,DeliveryData.DownloadTicket ticket,Uri createdDestination) throws Exception {
        checkCurrent();
        if(source==null || !"application/pdf".equals(source.mimeType()) || createdDestination==null
                || !"content".equals(createdDestination.getScheme()) || createdDestination.getAuthority()==null
                || createdDestination.getAuthority().isEmpty() || createdDestination.equals(source.uri()))throw failure("VALIDATION_FAILED");
        if(file==null || file.kind!=DeliveryData.FileKind.DELIVERABLE || !file.current || file.deletedAt!=null
                || !"verified".equals(file.verification) || !"application/pdf".equals(file.mimeType) || file.requirement!=null
                || file.sizeBytes<1 || file.sizeBytes>9007199254740991L)throw failure("VALIDATION_FAILED");
        if(ticket==null || ticket.sha256==null || !ticket.sha256.matches("[0-9a-f]{64}"))throw failure("VALIDATION_FAILED");
        if(file.id==null || !file.id.equals(ticket.fileId))throw failure("FORBIDDEN");
        byte[] chunk=new byte[16384]; boolean attempted=false;
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256"); long total=0;
            try(InputStream input=resolver.openInputStream(source.uri())) {
                if(input==null)throw new IOException("Private document unavailable");
                checkCurrent(); attempted=true;
                try(OutputStream output=resolver.openOutputStream(createdDestination,"wt")) {
                    if(output==null)throw new IOException("Document destination unavailable");
                    while(true) {
                        checkCurrent(); int count=input.read(chunk);
                        if(count<0)break;
                        if(count==0) { int single=input.read(); if(single<0)break; chunk[0]=(byte)single; count=1; }
                        checkCurrent(); total+=count;
                        if(total>file.sizeBytes)throw failure("DOWNLOAD_UNCONFIRMED");
                        output.write(chunk,0,count); digest.update(chunk,0,count); checkCurrent();
                    }
                    if(total!=file.sizeBytes || !MessageDigest.isEqual(digest.digest(),decode(ticket.sha256)))throw failure("DOWNLOAD_UNCONFIRMED");
                    checkCurrent(); output.flush(); checkCurrent();
                }
            }
            checkCurrent(); String name=displayName(createdDestination); checkCurrent(); return new Saved(name);
        } catch(Exception error) {
            boolean partial=false;
            if(attempted)try { partial=!DocumentsContract.deleteDocument(resolver,createdDestination); } catch(Exception cleanup) { partial=true; }
            if(error instanceof IllegalStateException)throw error;
            String code=error instanceof RequestRepository.WorkflowException?((RequestRepository.WorkflowException)error).code:"DOCUMENT_SAVE_FAILED";
            throw new SaveException(code,partial,error);
        } finally { Arrays.fill(chunk,(byte)0); }
    }
    private void checkCurrent() throws InterruptedIOException {
        requireCurrent.run(); if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Document save cancelled");
    }
    private String displayName(Uri uri) {
        try(Cursor cursor=resolver.query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if(cursor==null || !cursor.moveToFirst())return null;
            int column=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME); if(column<0 || cursor.isNull(column))return null;
            String raw=cursor.getString(column).replace('\\','/'); String leaf=raw.substring(raw.lastIndexOf('/')+1); StringBuilder name=new StringBuilder();
            leaf.codePoints().filter(c->!Character.isISOControl(c)).limit(120).forEach(name::appendCodePoint);
            return name.toString().trim().isEmpty()?null:name.toString().trim();
        } catch(Exception unavailable) { return null; }
    }
    private static byte[] decode(String hex) {
        byte[] bytes=new byte[32]; for(int i=0;i<bytes.length;i++)bytes[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16); return bytes;
    }
    private static RequestRepository.WorkflowException failure(String code) { return new RequestRepository.WorkflowException(code); }
}
