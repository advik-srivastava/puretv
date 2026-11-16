package tv.pure.home;

import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * window.PureTV in the Home screen page. Everything the interface can ask of the TV is here, and
 * nothing more: list and open apps, open the TV's own Settings screens and inputs, read memory,
 * and check for PureTV updates. It never changes, clears or signs out of any app.
 */
public final class Bridge {
    private final MainActivity a;
    private final PackageManager pm;
    static final String[] GOOGLE_HOMES = { "com.google.android.apps.tv.launcherx", "com.google.android.tvlauncher", "com.google.android.leanbacklauncher" };

    Bridge(MainActivity a) { this.a = a; this.pm = a.getPackageManager(); }

    private void ui(Runnable r) { a.runOnUiThread(r); }

    private void start(final Intent i) {
        ui(new Runnable() { @Override public void run() {
            try { a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception e) { a.js("window.toast && toast('That screen is not available on this TV')"); }
        } });
    }

    @JavascriptInterface public String version() { return Updater.installedVersion(a); }

    @JavascriptInterface public String device() {
        try {
            JSONObject o = new JSONObject();
            o.put("maker", Build.MANUFACTURER); o.put("model", Build.MODEL); o.put("android", Build.VERSION.RELEASE);
            android.util.DisplayMetrics m = a.getResources().getDisplayMetrics();
            o.put("w", m.widthPixels); o.put("h", m.heightPixels);
            o.put("cores", Runtime.getRuntime().availableProcessors());
            return o.toString();
        } catch (Exception e) { return "{}"; }
    }

    /** Every app with a TV (or phone) launcher entry, except PureTV and Google TV's own home. */
    @JavascriptInterface public String apps() {
        JSONArray out = new JSONArray();
        Set<String> seen = new HashSet<>();
        seen.add(a.getPackageName());
        for (String g : GOOGLE_HOMES) seen.add(g);
        seen.add("com.android.tv.settings");
        String[] cats = { Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER };
        for (String cat : cats) {
            List<ResolveInfo> list = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0);
            for (ResolveInfo r : list) {
                String p = r.activityInfo.packageName;
                if (!seen.add(p)) continue;
                try {
                    JSONObject o = new JSONObject();
                    o.put("pkg", p);
                    o.put("name", String.valueOf(r.loadLabel(pm)));
                    o.put("tv", cat.equals(Intent.CATEGORY_LEANBACK_LAUNCHER));
                    o.put("installed", new File(r.activityInfo.applicationInfo.sourceDir).lastModified());
                    out.put(o);
                } catch (Exception ignored) { }
            }
        }
        return out.toString();
    }

    @JavascriptInterface public boolean has(String pkg) {
        try { return pm.getApplicationInfo(pkg, 0).enabled; } catch (Exception e) { return false; }
    }

    @JavascriptInterface public boolean launch(String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        start(i.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        return true;
    }

    /** The app's page in the Play Store (to add YouTube Kids, for example). */
    @JavascriptInterface public void store(String pkg) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
        start(i);
    }

    /** The TV's own Settings screens. PureTV opens them and never changes them. */
    @JavascriptInterface public void settings(String page) {
        String p = page == null ? "" : page;
        Intent i;
        if (p.startsWith("appinfo:")) i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + p.substring(8)));
        else if (p.equals("network")) i = new Intent(Settings.ACTION_WIFI_SETTINGS);
        else if (p.equals("bluetooth")) i = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        else if (p.equals("display")) i = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
        else if (p.equals("sound")) i = new Intent(Settings.ACTION_SOUND_SETTINGS);
        else if (p.equals("apps")) i = new Intent(Settings.ACTION_APPLICATION_SETTINGS);
        else if (p.equals("access")) i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        else if (p.equals("accounts")) i = new Intent(Settings.ACTION_SYNC_SETTINGS);
        else if (p.equals("date")) i = new Intent(Settings.ACTION_DATE_SETTINGS);
        else if (p.equals("unknown")) i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName()));
        else i = new Intent(Settings.ACTION_SETTINGS);
        if (i.resolveActivity(pm) == null) i = new Intent(Settings.ACTION_SETTINGS);
        start(i);
    }

    @JavascriptInterface public void search() {
        Intent i = new Intent(Intent.ACTION_ASSIST);
        if (i.resolveActivity(pm) == null) i = new Intent("android.search.action.GLOBAL_SEARCH");
        start(i);
    }

    /** HDMI, AV and the tuner, from Android's own TV input list. */
    @JavascriptInterface public String inputs() {
        JSONArray out = new JSONArray();
        try {
            TvInputManager tim = (TvInputManager) a.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) for (TvInputInfo t : tim.getTvInputList()) {
                if (t.isHidden(a)) continue;
                JSONObject o = new JSONObject();
                CharSequence custom = t.loadCustomLabel(a);
                o.put("id", t.getId());
                o.put("name", String.valueOf(TextUtils.isEmpty(custom) ? t.loadLabel(a) : custom));
                o.put("pass", t.isPassthroughInput());
                o.put("type", t.getType());
                out.put(o);
            }
        } catch (Exception ignored) { }
        return out.toString();
    }

    @JavascriptInterface public void input(String id) {
        try {
            TvInputManager tim = (TvInputManager) a.getSystemService(Context.TV_INPUT_SERVICE);
            TvInputInfo info = tim == null ? null : tim.getTvInputInfo(id);
            Uri u = info != null && info.isPassthroughInput() ? TvContract.buildChannelUriForPassthroughInput(id) : TvContract.buildChannelsUriForInput(id);
            start(new Intent(Intent.ACTION_VIEW, u));
        } catch (Exception e) { settings(""); }
    }

    /** Memory in MB: {total, free}. */
    @JavascriptInterface public String mem() {
        ActivityManager am = (ActivityManager) a.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return "{\"total\":" + mi.totalMem / 1048576 + ",\"free\":" + mi.availMem / 1048576 + ",\"low\":" + am.isLowRamDevice() + "}";
    }

    /** Recent Apps "close": asks Android to stop an app that is in the background. Its data and sign-in stay. */
    @JavascriptInterface public void close(String pkg) {
        try { ((ActivityManager) a.getSystemService(Context.ACTIVITY_SERVICE)).killBackgroundProcesses(pkg); } catch (Exception ignored) { }
    }

    /** Apps that are switched off on this TV (by the PureTV agent, or by hand in Settings). */
    @JavascriptInterface public String disabled() {
        JSONArray out = new JSONArray();
        int flags = Build.VERSION.SDK_INT >= 24 ? PackageManager.MATCH_DISABLED_COMPONENTS : PackageManager.GET_DISABLED_COMPONENTS;
        for (ApplicationInfo ai : pm.getInstalledApplications(flags)) {
            if (ai.enabled) continue;
            try {
                JSONObject o = new JSONObject();
                o.put("pkg", ai.packageName); o.put("name", String.valueOf(ai.loadLabel(pm)));
                out.put(o);
            } catch (Exception ignored) { }
        }
        return out.toString();
    }

    /** Is PureTV what the Home button opens (Google TV's home switched off, or PureTV chosen)? */
    @JavascriptInterface public boolean isHome() {
        ResolveInfo r = pm.resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        return r != null && r.activityInfo != null && a.getPackageName().equals(r.activityInfo.packageName);
    }

    @JavascriptInterface public boolean helperOn() { return HomeHelper.enabled(a); }
    @JavascriptInterface public void openHelper() { settings("access"); }
    @JavascriptInterface public boolean takeHome() { return HomeHelper.override(a); }
    @JavascriptInterface public void setTakeHome(boolean on) { HomeHelper.setOverride(a, on); }

    /** Google TV's home again: PureTV stops catching the Home button and opens Google TV. Nothing is deleted. */
    @JavascriptInterface public boolean googleHome() {
        HomeHelper.setOverride(a, false);
        for (String g : GOOGLE_HOMES) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(g);
            if (has(g) && i.resolveActivity(pm) != null) { start(i); return true; }
        }
        return false;
    }

    /** The remote's power button, through the Remote Helper (Android 9+); otherwise false. */
    @JavascriptInterface public boolean sleep() { return HomeHelper.sleep(); }

    /** PureTV's own backup of its settings (profiles, top row, wallpaper), kept in its private folder. */
    @JavascriptInterface public boolean backup(String json) {
        try {
            FileOutputStream f = new FileOutputStream(new File(a.getFilesDir(), "backup.json"));
            try { f.write(json.getBytes("UTF-8")); } finally { f.close(); }
            return true;
        } catch (Exception e) { return false; }
    }

    @JavascriptInterface public String restoreBackup() {
        File f = new File(a.getFilesDir(), "backup.json");
        if (!f.exists()) return "";
        try {
            FileInputStream in = new FileInputStream(f);
            try { byte[] b = new byte[(int) f.length()]; int n = 0; while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; } return new String(b, 0, n, "UTF-8"); }
            finally { in.close(); }
        } catch (Exception e) { return ""; }
    }

    @JavascriptInterface public long backupTime() { return new File(a.getFilesDir(), "backup.json").lastModified(); }

    /* ---------- software updates: PureTV's own GitHub releases, installed only after the owner says yes ---------- */
    @JavascriptInterface public boolean autoUpdate() { return Updater.auto(a); }
    @JavascriptInterface public void setAutoUpdate(boolean on) { Updater.setAuto(a, on); }
    @JavascriptInterface public boolean updateDue() { return Updater.due(a); }
    @JavascriptInterface public boolean canInstall() { return Build.VERSION.SDK_INT < 26 || pm.canRequestPackageInstalls(); }
    @JavascriptInterface public void checkUpdate(boolean install) {
        Updater.check(a, install, new Updater.Listener() {
            @Override public void onStatus(String json) { a.js("window.tvUpdate && tvUpdate(" + JSONObject.quote(json) + ")"); }
        });
    }

    @JavascriptInterface public void openGoogleTvAppInfo() {
        try { start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + GOOGLE_HOMES[0]))); }
        catch (ActivityNotFoundException e) { settings("apps"); }
    }

    static ComponentName home(Context c) { return new ComponentName(c, MainActivity.class); }
}
