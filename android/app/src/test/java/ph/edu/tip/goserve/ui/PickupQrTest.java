package ph.edu.tip.goserve.ui;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34,application=Application.class)
public final class PickupQrTest {
    @Test public void theActualServerPayloadRoundTripsThroughAnIndependentQrDecoder() throws Exception {
        String payload="2af14b876b9d449cb51be9ce8ae8fa27a8ad49ca7477424c88a82944e3b309ed";
        Bitmap image=PickupQr.render(payload,256); assertNotNull(image);
        try { assertEquals(256,image.getWidth()); assertEquals(256,image.getHeight()); assertEquals(payload,decode(image)); } finally { image.recycle(); }
    }
    @Test public void theQrQuietZoneRemainsWhiteInBothAppThemes() throws Exception {
        Bitmap image=PickupQr.render("f".repeat(64),384); assertNotNull(image);
        try { for(int x=0;x<384;x++) { assertEquals(Color.WHITE,image.getPixel(x,0)); assertEquals(Color.WHITE,image.getPixel(x,383)); } assertEquals("f".repeat(64),decode(image)); } finally { image.recycle(); }
    }
    @Test public void nonClaimTextCannotCreateAFakeOrExternalLinkQr() {
        for(String value:new String[]{null,"","demo-code","https://example.test"})try { PickupQr.render(value,256); fail("Not a server claim"); } catch(IllegalArgumentException expected) {}
    }
    @Test public void bitmapAllocationIsBounded() {
        for(int size:new int[]{-1,0,64,2048,Integer.MAX_VALUE})try { PickupQr.render("a".repeat(64),size); fail("Unbounded bitmap"); } catch(IllegalArgumentException expected) {}
    }
    private static String decode(Bitmap image) throws Exception {
        int[] colors=new int[image.getWidth()*image.getHeight()]; image.getPixels(colors,0,image.getWidth(),0,0,image.getWidth(),image.getHeight());
        return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(image.getWidth(),image.getHeight(),colors)))).getText();
    }
}
