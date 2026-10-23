package ph.edu.tip.goserve.data;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class ProfileImage {
    public static final int MAX_BYTES=1048576,MAX_SOURCE_BYTES=12582912,MAX_EDGE=512;
    private static final int MAX_SOURCE_EDGE=20000;
    private ProfileImage() {}
    public static byte[] normalize(InputStream input) throws IOException {
        if(input==null)throw invalid();
        ByteArrayOutputStream source=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
        while((count=input.read(buffer))!=-1){if(count>MAX_SOURCE_BYTES-source.size())throw invalid();source.write(buffer,0,count);}
        byte[] bytes=source.toByteArray();BitmapFactory.Options bounds=bounds(bytes);
        if(bounds.outWidth<=0 || bounds.outHeight<=0 || bounds.outWidth>MAX_SOURCE_EDGE || bounds.outHeight>MAX_SOURCE_EDGE || (long)bounds.outWidth*bounds.outHeight>80000000
                || !java.util.Arrays.asList("image/jpeg","image/png","image/webp").contains(bounds.outMimeType))throw invalid();
        BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1024)options.inSampleSize*=2;
        Bitmap decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(decoded==null)throw invalid();
        Bitmap oriented=decoded,scaled=null,opaque=null;
        try{
            int orientation=ExifInterface.ORIENTATION_NORMAL;
            try{orientation=new ExifInterface(new ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);}catch(IOException ignored){}
            Matrix matrix=new Matrix();
            switch(orientation){
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:matrix.setScale(-1,1);break;
                case ExifInterface.ORIENTATION_ROTATE_180:matrix.setRotate(180);break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:matrix.setScale(1,-1);break;
                case ExifInterface.ORIENTATION_TRANSPOSE:matrix.setRotate(90);matrix.postScale(-1,1);break;
                case ExifInterface.ORIENTATION_ROTATE_90:matrix.setRotate(90);break;
                case ExifInterface.ORIENTATION_TRANSVERSE:matrix.setRotate(-90);matrix.postScale(-1,1);break;
                case ExifInterface.ORIENTATION_ROTATE_270:matrix.setRotate(270);break;
            }
            if(!matrix.isIdentity())oriented=Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),decoded.getHeight(),matrix,true);
            float ratio=Math.min(1f,(float)MAX_EDGE/Math.max(oriented.getWidth(),oriented.getHeight()));
            scaled=Bitmap.createScaledBitmap(oriented,Math.max(1,Math.round(oriented.getWidth()*ratio)),Math.max(1,Math.round(oriented.getHeight()*ratio)),true);
            opaque=Bitmap.createBitmap(scaled.getWidth(),scaled.getHeight(),Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(opaque);canvas.drawColor(Color.WHITE);canvas.drawBitmap(scaled,0,0,null);
            ByteArrayOutputStream output=new ByteArrayOutputStream();if(!opaque.compress(Bitmap.CompressFormat.JPEG,88,output))throw invalid();
            byte[] jpeg=output.toByteArray();validate(jpeg);return jpeg;
        }finally{
            if(opaque!=null)opaque.recycle();if(scaled!=null && scaled!=oriented)scaled.recycle();if(oriented!=decoded)oriented.recycle();decoded.recycle();
        }
    }
    static void validate(byte[] jpeg) throws IOException {
        if(jpeg==null || jpeg.length==0 || jpeg.length>MAX_BYTES)throw invalid();
        BitmapFactory.Options b=bounds(jpeg);
        if(!"image/jpeg".equals(b.outMimeType) || b.outWidth<=0 || b.outHeight<=0 || b.outWidth>MAX_EDGE || b.outHeight>MAX_EDGE)throw invalid();
    }
    private static BitmapFactory.Options bounds(byte[] bytes){BitmapFactory.Options b=new BitmapFactory.Options();b.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,b);return b;}
    private static IOException invalid(){return new IOException("Choose a readable JPG, PNG or WebP image under 12 MB.");}
}
