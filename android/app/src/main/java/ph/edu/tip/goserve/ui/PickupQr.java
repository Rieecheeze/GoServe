package ph.edu.tip.goserve.ui;

import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.Map;

public final class PickupQr {
    public static Bitmap render(String payload,int pixels) {
        if(payload==null || !payload.matches("[0-9a-f]{64}") || pixels<128 || pixels>1024)throw new IllegalArgumentException("A bounded server claim is required");
        try {
            BitMatrix matrix=new QRCodeWriter().encode(payload,BarcodeFormat.QR_CODE,pixels,pixels,Map.of(EncodeHintType.MARGIN,4,EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.M));
            int[] colors=new int[pixels*pixels];
            for(int y=0;y<pixels;y++)for(int x=0;x<pixels;x++)colors[y*pixels+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
            return Bitmap.createBitmap(colors,pixels,pixels,Bitmap.Config.RGB_565);
        } catch(com.google.zxing.WriterException invalid) { throw new IllegalArgumentException("The claim could not be encoded",invalid); }
    }
    private PickupQr() {}
}
