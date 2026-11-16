package tv.pure.home;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Serves https://puretv.local/... from inside the APK:
 *   /ui/...                 the interface (assets/ui)
 *   /icon/PKG.png           an app's TV banner, drawn as a 5:3 tile (or its icon on a colour wash)
 *   /logo/PKG.png           an app's square icon, for menus and Recent Apps
 * Drawn artwork is cached in the app's cache folder, so it is only made once per app version.
 */
final class Assets {
    private Assets() { }

    private static final Map<String, String> TYPES = new HashMap<>();
    static {
        TYPES.put("html", "text/html"); TYPES.put("js", "text/javascript"); TYPES.put("css", "text/css");
        TYPES.put("png", "image/png"); TYPES.put("jpg", "image/jpeg"); TYPES.put("webp", "image/webp");
        TYPES.put("svg", "image/svg+xml"); TYPES.put("woff2", "font/woff2"); TYPES.put("json", "application/json");
    }

    static WebResourceResponse serve(Context c, Uri u) {
        String p = u.getPath() == null ? "" : u.getPath();
        try {
            if (p.startsWith("/ui/") && !p.contains("..")) {
                InputStream in = c.getAssets().open(p.substring(1));
                return ok(type(p), in);
            }
            if (p.startsWith("/icon/") || p.startsWith("/logo/")) {
                String pkg = p.substring(6).replaceAll("\\.png$", "");
                if (!pkg.matches("[A-Za-z0-9_.]+")) return missing();
                return ok("image/png", new ByteArrayInputStream(art(c, pkg, p.startsWith("/icon/"))));
            }
        } catch (Exception e) {
            return missing();
        }
        return missing();
    }

    private static String type(String p) {
        String ext = p.substring(p.lastIndexOf('.') + 1).toLowerCase();
        String t = TYPES.get(ext);
        return t == null ? "application/octet-stream" : t;
    }

    private static WebResourceResponse ok(String type, InputStream in) {
        WebResourceResponse r = new WebResourceResponse(type, type.startsWith("text/") ? "utf-8" : null, in);
        Map<String, String> h = new HashMap<>();
        h.put("Access-Control-Allow-Origin", "https://" + MainActivity.HOST);
        h.put("Cache-Control", "max-age=86400");
        r.setResponseHeaders(h);
        return r;
    }

    private static WebResourceResponse missing() {
        WebResourceResponse r = new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
        r.setStatusCodeAndReasonPhrase(404, "Not Found");
        r.setResponseHeaders(Collections.<String, String>emptyMap());
        return r;
    }

    /** A tile (400 x 240) from the app's TV banner, or a square logo (192 x 192) from its icon. */
    static synchronized byte[] art(Context c, String pkg, boolean tile) throws Exception {
        PackageManager pm = c.getPackageManager();
        ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
        long ver = new File(ai.sourceDir).lastModified();
        File cached = new File(c.getCacheDir(), "art/" + (tile ? "t-" : "l-") + pkg + "-" + ver + ".png");
        if (cached.exists()) return read(cached);
        Bitmap bmp;
        if (tile) {
            bmp = Bitmap.createBitmap(400, 240, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            Drawable banner = ai.banner != 0 ? pm.getApplicationBanner(ai) : null;
            if (banner == null) {
                try { banner = pm.getActivityBanner(pm.getLeanbackLaunchIntentForPackage(pkg)); } catch (Exception ignored) { }
            }
            if (banner != null) {
                // cover the 5:3 tile with the 16:9 banner, centred
                int bw = Math.max(1, banner.getIntrinsicWidth()), bh = Math.max(1, banner.getIntrinsicHeight());
                float k = Math.max(400f / bw, 240f / bh);
                int w = Math.round(bw * k), h = Math.round(bh * k);
                Paint bg = new Paint(); bg.setColor(edgeColour(banner)); cv.drawRect(0, 0, 400, 240, bg);
                banner.setBounds((400 - w) / 2, (240 - h) / 2, (400 + w) / 2, (240 + h) / 2);
                banner.draw(cv);
            } else {
                Drawable icon = pm.getApplicationIcon(ai);
                int col = edgeColour(icon);
                Paint bg = new Paint();
                bg.setShader(new LinearGradient(0, 0, 0, 240, lighten(col, .18f), darken(col, .25f), Shader.TileMode.CLAMP));
                cv.drawRect(0, 0, 400, 240, bg);
                icon.setBounds(130, 50, 270, 190);
                icon.draw(cv);
            }
        } else {
            bmp = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
            Drawable icon = pm.getApplicationIcon(ai);
            icon.setBounds(0, 0, 192, 192);
            icon.draw(new Canvas(bmp));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        bmp.recycle();
        byte[] bytes = out.toByteArray();
        cached.getParentFile().mkdirs();
        FileOutputStream f = new FileOutputStream(cached);
        try { f.write(bytes); } finally { f.close(); }
        return bytes;
    }

    private static int edgeColour(Drawable d) {
        try {
            Bitmap b = d instanceof BitmapDrawable ? ((BitmapDrawable) d).getBitmap() : null;
            if (b == null) {
                b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
                d.setBounds(0, 0, 32, 32); d.draw(new Canvas(b));
            }
            int p = b.getPixel(1, Math.max(0, b.getHeight() / 2));
            return (p >>> 24) < 128 ? 0xFF2A2C33 : p | 0xFF000000;
        } catch (Exception e) { return 0xFF2A2C33; }
    }
    private static int lighten(int c, float f) { return mix(c, 0xFFFFFFFF, f); }
    private static int darken(int c, float f) { return mix(c, 0xFF000000, f); }
    private static int mix(int a, int b, float f) {
        int r = (int) (((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = (int) (((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = (int) ((a & 255) * (1 - f) + (b & 255) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static byte[] read(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[16384]; int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return out.toByteArray();
        } finally { in.close(); }
    }
}
