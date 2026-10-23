package ph.edu.tip.goserve.data;

import java.io.InputStream;
import java.io.IOException;
import android.content.ContentResolver;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import ph.edu.tip.goserve.domain.SeryoDraft;

public final class SeryoAttachmentSource {
    public static final int MAX_BYTES=10_485_760;
    public static byte[] readBounded(InputStream input,Runnable check)throws IOException {
        if(input==null)throw new IOException("This file cannot be opened.");
        byte[] buffer=new byte[MAX_BYTES+1];int total=0;
        try(InputStream stream=input) {
            int count;
            while((count=stream.read(buffer,total,buffer.length-total))!=-1) {
                check.run();total+=count;
                if(total>MAX_BYTES)throw new IOException("Choose a file up to 10 MB.");
                if(Thread.currentThread().isInterrupted())throw new IOException("File selection interrupted.");
            }
            check.run();if(total==0)throw new IOException("Choose a file that is not empty.");
            return Arrays.copyOf(buffer,total);
        } finally { Arrays.fill(buffer,(byte)0); }
    }
    public static String detect(byte[] bytes)throws IOException {
        if(bytes.length>=12 && bytes[0]==(byte)0x89 && bytes[1]=='P' && bytes[2]=='N' && bytes[3]=='G'
                && bytes[4]==13 && bytes[5]==10 && bytes[6]==26 && bytes[7]==10
                && bytes.length>=20 && new String(bytes,bytes.length-8,4,StandardCharsets.US_ASCII).equals("IEND")) return "image/png";
        if(bytes.length>=4 && bytes[0]==(byte)0xff && bytes[1]==(byte)0xd8 && bytes[bytes.length-2]==(byte)0xff && bytes[bytes.length-1]==(byte)0xd9) return "image/jpeg";
        if(bytes.length>=12 && new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-")
                && new String(bytes,Math.max(0,bytes.length-1024),Math.min(bytes.length,1024),StandardCharsets.US_ASCII).contains("%%EOF")) return "application/pdf";
        throw new IOException("Choose a readable PDF, JPG, or PNG.");
    }
    public SeryoDraft.Attachment read(ContentResolver resolver,Uri uri,Runnable check)throws Exception {
        if(uri==null || !"content".equals(uri.getScheme()))throw new IOException("Choose a file from the document picker.");
        byte[] bytes=null;
        try {
            check.run();bytes=readBounded(resolver.openInputStream(uri),check);
            String actual=detect(bytes),declared=resolver.getType(uri);
            if(!actual.equals(declared))throw new IOException("The file contents do not match its type.");
            if("application/pdf".equals(actual)) {
                try(ParcelFileDescriptor descriptor=resolver.openFileDescriptor(uri,"r")) {
                    if(descriptor==null)throw new IOException("This PDF cannot be opened.");
                    try(PdfRenderer renderer=new PdfRenderer(descriptor)) {
                        if(renderer.getPageCount()<1)throw new IOException("Choose a readable PDF.");
                        try(PdfRenderer.Page page=renderer.openPage(0)) { if(page.getWidth()<1 || page.getHeight()<1)throw new IOException("Choose a readable PDF."); }
                    }
                }
            } else {
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(options.outWidth<1 || options.outHeight<1)throw new IOException("This image cannot be read.");
                options.inJustDecodeBounds=false;options.inSampleSize=1;
                while((long)(options.outWidth/options.inSampleSize)*(options.outHeight/options.inSampleSize)>4_000_000)options.inSampleSize*=2;
                Bitmap image=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(image==null)throw new IOException("This image cannot be read.");image.recycle();
            }
            String name="Selected file";
            try(Cursor cursor=resolver.query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
                if(cursor!=null && cursor.moveToFirst() && !cursor.isNull(0)) {
                    String candidate=cursor.getString(0).replaceAll("[\\p{Cntrl}]","");if(!candidate.isEmpty())name=candidate.substring(0,Math.min(candidate.length(),160));
                }
            }
            check.run();SeryoDraft.Attachment result=new SeryoDraft.Attachment(name,actual,bytes);bytes=null;return result;
        } finally { if(bytes!=null)Arrays.fill(bytes,(byte)0); }
    }
}
