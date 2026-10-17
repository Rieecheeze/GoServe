package ph.edu.tip.goserve.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import ph.edu.tip.goserve.R;
import ph.edu.tip.goserve.data.OfficeRepository;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, qualifiers = "port-mdpi", application = android.app.Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class LayoutScreenshotTest {
    @Test public void signInLight() throws IOException {
        capture(R.layout.fragment_sign_in, "sign-in-390-light.png", 390, 796, 1f, false);
    }

    @Test public void signInDark() throws IOException {
        capture(R.layout.fragment_sign_in, "sign-in-390-dark.png", 390, 796, 1f, true);
    }

    @Test public void homeLight() throws IOException {
        capture(R.layout.fragment_home, "home-390-light.png", 390, 724, 1f, false);
    }

    @Test public void homeDark() throws IOException {
        capture(R.layout.fragment_home, "home-390-dark.png", 390, 724, 1f, true);
    }

    @Test public void signInWithLargeText() throws IOException {
        capture(R.layout.fragment_sign_in, "sign-in-320-font2.png", 320, 600, 2f, false);
    }

    @Test public void homeWithLargeText() throws IOException {
        capture(R.layout.fragment_home, "home-320-font2.png", 320, 700, 2f, false);
    }

    @Test public void authenticationLoadingLight() throws IOException {
        capture(R.layout.activity_auth, "auth-loading-390-light.png", 390, 796, 1f, false);
    }

    @Test public void authenticationLoadingDark() throws IOException {
        capture(R.layout.activity_auth, "auth-loading-390-dark.png", 390, 796, 1f, true);
    }

    private void capture(int layout, String filename, int width, int height, float fontScale,
                         boolean night) throws IOException {
        RuntimeEnvironment.setQualifiers("w" + width + "dp-h" + height + "dp-port-"
                + (night ? "night" : "notnight") + "-mdpi");
        Context app = RuntimeEnvironment.getApplication();
        Configuration configuration = new Configuration(app.getResources().getConfiguration());
        configuration.fontScale = fontScale;
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        Context context = new ContextThemeWrapper(app.createConfigurationContext(configuration),
                R.style.Theme_GoServe);
        View root = LayoutInflater.from(context).inflate(layout, null, false);
        if (layout == R.layout.fragment_home) populateHome(root, context, width, fontScale);
        if (layout == R.layout.activity_auth) root.findViewById(R.id.authLoading).setVisibility(View.VISIBLE);

        Shadows.shadowOf(Looper.getMainLooper()).idle();
        measureAndLayout(root, width, height);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        measureAndLayout(root, width, height);
        root.jumpDrawablesToCurrentState();

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            int background = ContextCompat.getColor(context, R.color.gs_background);
            canvas.drawColor(background);
            root.draw(canvas);
            assertTrue(filename + " contains no rendered widgets", changedPixels(bitmap, background) > 100);
            File directory = new File("build/outputs/screenshots");
            assertTrue("Cannot create screenshot output directory", directory.isDirectory() || directory.mkdirs());
            File output = new File(directory, filename);
            try (FileOutputStream stream = new FileOutputStream(output)) {
                assertTrue("PNG compression failed for " + filename,
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
            }
            assertTrue("Empty screenshot " + filename, output.isFile() && output.length() > 512);
        } finally {
            bitmap.recycle();
        }
    }

    private void populateHome(View root, Context context, int width, float fontScale) {
        TextView preview = root.findViewById(R.id.previewNotice);
        preview.setVisibility(View.VISIBLE);
        preview.setText(R.string.preview_notice_logic);
        root.findViewById(R.id.activeRequestCard).setVisibility(View.VISIBLE);
        ((TextView) root.findViewById(R.id.requestTitle)).setText("Certificate of enrollment");
        ((TextView) root.findViewById(R.id.requestMeta)).setText("Registrar · REQ-2026-0148");
        ((TextView) root.findViewById(R.id.requestStatus)).setText("Processing");

        int columns = width < 360 || fontScale > 1.3f ? 1 : 2;
        RecyclerView recycler = root.findViewById(R.id.officesRecycler);
        recycler.setLayoutManager(new GridLayoutManager(context, columns));
        recycler.setItemAnimator(null);
        recycler.setNestedScrollingEnabled(false);
        int halfGap = Math.round(7 * context.getResources().getDisplayMetrics().density);
        recycler.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override public void getItemOffsets(Rect out, View item, RecyclerView parent,
                                                RecyclerView.State state) {
                int position = parent.getChildAdapterPosition(item);
                out.set(columns == 2 && position % 2 == 1 ? halfGap : 0, 0,
                        columns == 2 && position % 2 == 0 ? halfGap : 0, halfGap * 2);
            }
        });
        OfficeAdapter adapter = new OfficeAdapter(true, office -> { });
        recycler.setAdapter(adapter);
        adapter.submit(new OfficeRepository(null, null).preview());
    }

    private void measureAndLayout(View root, int width, int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }

    private int changedPixels(Bitmap bitmap, int background) {
        int changed = 0;
        for (int y = 0; y < bitmap.getHeight(); y += 4) {
            for (int x = 0; x < bitmap.getWidth(); x += 4) {
                if (bitmap.getPixel(x, y) != background) changed++;
            }
        }
        return changed;
    }
}
