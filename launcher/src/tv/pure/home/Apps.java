package tv.pure.home;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.LruCache;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The installed apps, their 5:3 tile artwork (drawn once, kept in a small cache), and opening them. */
final class Apps {
    static final class App {
        String pkg, name;
        boolean tv;
    }

    static final String YT = "com.google.android.youtube.tv", YTK = "com.google.android.youtube.tvkids";
    static final String[] GOOGLE_HOMES = { "com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher", "com.google.android.leanbacklauncher" };
    /** Popular apps first in the top row on a new TV. */
    static final List<String> FAVS = Arrays.asList(YT, "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "in.startv.hotstar",
            "com.disney.disneyplus", "com.jio.media.stb.ondemand", "com.graymatrix.did", "com.sonyliv", "com.apple.atve.androidtv.appletv",
            "com.spotify.tv.android", "com.google.android.youtube.tvmusic");
    /** Allowed in the Kids profile, with the name shown there. */
    static final String[][] KIDS = { { YTK, "YouTube Kids" }, { "com.netflix.ninja", "Netflix" }, { "com.disney.disneyplus", "Disney+" },
            { "in.startv.hotstar", "Hotstar" }, { "com.amazon.amazonvideo.livingroom", "Prime Video" } };
    private static final Set<String> BUNDLED = new HashSet<>(Arrays.asList("com.amazon.amazonvideo.livingroom", "com.apple.atve.androidtv.appletv",
            "com.cbs.ca", "com.crunchyroll.webapp", "com.disney.disneyplus", YT, "com.netflix.ninja", "com.spotify.tv.android", "com.wbd.stream", "tv.twitch.android.app"));

    private final Context c;
    private final PackageManager pm;
    final List<App> all = new ArrayList<>();
    private final LruCache<String, Bitmap> art = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    Apps(Context c) { this.c = c; pm = c.getPackageManager(); }

    void load() {
        all.clear();
        Set<String> seen = new HashSet<>(Arrays.asList(GOOGLE_HOMES));
        seen.add(c.getPackageName());
        seen.add("com.android.tv.settings");
        for (String cat : new String[] { Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER }) {
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String p = r.activityInfo.packageName;
                if (!seen.add(p)) continue;
                App a = new App();
                a.pkg = p; a.name = String.valueOf(r.loadLabel(pm)); a.tv = cat.equals(Intent.CATEGORY_LEANBACK_LAUNCHER);
                all.add(a);
            }
        }
        Collections.sort(all, (x, y) -> {
            int a = rank(x), b = rank(y);
            return a != b ? a - b : x.name.compareToIgnoreCase(y.name);
        });
    }

    private static int rank(App a) { int f = FAVS.indexOf(a.pkg); return f >= 0 ? f : a.tv ? 100 : 200; }

    App get(String pkg) { for (App a : all) if (a.pkg.equals(pkg)) return a; return null; }
    boolean has(String pkg) { return get(pkg) != null; }

    /** The top row for a profile: what it chose, then popular apps until it is full. */
    List<App> top(Store.Profile p, int n) {
        List<App> out = new ArrayList<>();
        for (String k : p.top) { App a = get(k); if (a != null && !p.hidden.contains(k) && out.size() < n) out.add(a); }
        for (App a : all) { if (out.size() >= n) break; if (!out.contains(a) && !p.hidden.contains(a.pkg) && !p.top.contains("-" + a.pkg)) out.add(a); }
        return out;
    }

    List<App> kids() {
        List<App> out = new ArrayList<>();
        for (String[] k : KIDS) {
            App a = get(k[0]);
            if (a == null && k[0].equals(YTK) && has(YT)) a = get(YT);   // shown as YouTube Kids; opens the Play Store to add it
            if (a != null) out.add(a);
        }
        return out;
    }

    static String kidsName(String pkg) {
        if (pkg.equals(YT)) return "YouTube Kids";
        for (String[] k : KIDS) if (k[0].equals(pkg)) return k[1];
        return null;
    }

    boolean launch(String pkg, boolean kids) {
        if (kids && pkg.equals(YT)) {
            if (!has(YTK)) { store(YTK); return true; }
            pkg = YTK;
        }
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        try { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)); return true; }
        catch (Exception e) { return false; }
    }

    void store(String pkg) {
        try { c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception ignored) { }
    }

    /** 400 x 240 tile art: PureTV's own for popular apps, otherwise the app's TV banner (or its icon on a colour wash). */
    Bitmap art(String pkg) {
        Bitmap b = art.get(pkg);
        if (b != null) return b;
        if (BUNDLED.contains(pkg)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream in = c.getAssets().open("art/" + pkg + ".jpg")) { b = BitmapFactory.decodeStream(in, null, o); } catch (Exception ignored) { }
        }
        if (b == null) b = draw(pkg);
        if (b != null) art.put(pkg, b);
        return b;
    }

    private Bitmap draw(String pkg) {
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            Bitmap bmp = Bitmap.createBitmap(400, 240, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            Drawable banner = pm.getApplicationBanner(ai);
            if (banner == null) {
                Intent li = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (li != null) try { banner = pm.getActivityBanner(li); } catch (Exception ignored) { }
            }
            if (banner != null) {
                int bw = Math.max(1, banner.getIntrinsicWidth()), bh = Math.max(1, banner.getIntrinsicHeight());
                float k = Math.max(400f / bw, 240f / bh);
                int w = Math.round(bw * k), h = Math.round(bh * k);
                cv.drawColor(edge(banner));
                banner.setBounds((400 - w) / 2, (240 - h) / 2, (400 + w) / 2, (240 + h) / 2);
                banner.draw(cv);
            } else {
                Drawable icon = pm.getApplicationIcon(ai);
                int col = edge(icon);
                Paint bg = new Paint();
                bg.setShader(new LinearGradient(0, 0, 0, 240, mix(col, 0xFFFFFFFF, .18f), mix(col, 0xFF000000, .25f), Shader.TileMode.CLAMP));
                cv.drawRect(0, 0, 400, 240, bg);
                icon.setBounds(130, 50, 270, 190);
                icon.draw(cv);
            }
            return bmp;
        } catch (Exception e) { return null; }
    }

    Drawable icon(String pkg) { try { return pm.getApplicationIcon(pkg); } catch (Exception e) { return null; } }

    void clear() { art.evictAll(); }

    private static int edge(Drawable d) {
        try {
            Bitmap b = d instanceof BitmapDrawable ? ((BitmapDrawable) d).getBitmap() : null;
            if (b == null) { b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888); d.setBounds(0, 0, 32, 32); d.draw(new Canvas(b)); }
            int p = b.getPixel(1, b.getHeight() / 2);
            return (p >>> 24) < 128 ? 0xFF2A2C33 : p | 0xFF000000;
        } catch (Exception e) { return 0xFF2A2C33; }
    }

    private static int mix(int a, int b, float f) {
        int r = (int) (((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f), g = (int) (((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f),
                bl = (int) ((a & 255) * (1 - f) + (b & 255) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
