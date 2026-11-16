package tv.pure.home;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

/**
 * Updates PureTV Home from its own GitHub releases.
 *
 * Asks https://api.github.com/repos/OWNER/REPO/releases/latest (the repo is R.string.update_repo),
 * compares the release tag with the installed version, downloads the release's .apk, refuses it
 * unless it is signed with the same key as the installed launcher, and hands it to Android's own
 * PackageInstaller. The first update shows Android's confirmation on the TV; PureTV never installs
 * anything silently on its own. With automatic updates on, it checks at most once a day, when Home
 * is shown.
 */
public final class Updater {
    static final String PREFS = "updates", AUTO = "auto", LAST = "last_check";
    static final long DAY = 24L * 60 * 60 * 1000;
    private static boolean busy;

    public interface Listener { void onStatus(String message); }

    private Updater() { }

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    static boolean auto(Context c) { return prefs(c).getBoolean(AUTO, true); }
    static void setAuto(Context c, boolean on) { prefs(c).edit().putBoolean(AUTO, on).apply(); }

    static String repo(Context c) {
        String r = c.getString(R.string.update_repo).trim();
        return r.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+") ? r : "";
    }

    static String installedVersion(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; }
        catch (PackageManager.NameNotFoundException e) { return "0"; }
    }

    /** 1.10 > 1.9; a leading "v" in the tag is ignored. */
    static int compare(String a, String b) {
        String[] x = a.replaceFirst("^[vV]", "").split("[.-]"), y = b.replaceFirst("^[vV]", "").split("[.-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }
    private static int num(String s) { try { return Integer.parseInt(s.replaceAll("\\D.*", "")); } catch (NumberFormatException e) { return 0; } }

    /** Called when Home is shown: a check at most once a day, and only with automatic updates on. */
    static void maybeAutoCheck(Context c) {
        if (!auto(c) || TextUtils.isEmpty(repo(c))) return;
        long last = prefs(c).getLong(LAST, 0);
        if (System.currentTimeMillis() - last < DAY) return;
        check(c, true, null);
    }

    /** Looks for a newer release; with install, downloads and installs it too. */
    static void check(final Context context, final boolean install, final Listener listener) {
        final Context c = context.getApplicationContext();
        final Handler ui = new Handler(Looper.getMainLooper());
        final Listener say = new Listener() { @Override public void onStatus(final String m) {
            if (listener != null) ui.post(new Runnable() { @Override public void run() { listener.onStatus(m); } });
        } };
        final String repo = repo(c);
        if (repo.isEmpty()) { say.onStatus(c.getString(R.string.update_no_repo)); return; }
        if (busy) { say.onStatus(c.getString(R.string.update_busy)); return; }
        busy = true;
        new Thread(new Runnable() { @Override public void run() {
            try {
                say.onStatus(c.getString(R.string.update_checking));
                JSONObject rel = new JSONObject(get("https://api.github.com/repos/" + repo + "/releases/latest"));
                prefs(c).edit().putLong(LAST, System.currentTimeMillis()).apply();
                String tag = rel.optString("tag_name", ""), mine = installedVersion(c);
                String apk = null;
                JSONArray assets = rel.optJSONArray("assets");
                for (int i = 0; assets != null && i < assets.length(); i++) {
                    JSONObject a = assets.getJSONObject(i);
                    if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break; }
                }
                if (tag.isEmpty() || compare(tag, mine) <= 0) { say.onStatus(c.getString(R.string.update_latest, mine)); return; }
                if (apk == null || !apk.startsWith("https://github.com/" + repo + "/releases/download/")) {
                    say.onStatus(c.getString(R.string.update_no_apk, tag)); return;
                }
                if (!install) { say.onStatus(c.getString(R.string.update_available, tag)); return; }
                say.onStatus(c.getString(R.string.update_downloading, tag));
                File f = new File(c.getCacheDir(), "update.apk");
                download(apk, f);
                if (!sameSigner(c, f)) { f.delete(); say.onStatus(c.getString(R.string.update_bad_signature)); return; }
                say.onStatus(c.getString(R.string.update_installing, tag));
                installApk(c, f);
            } catch (Exception e) {
                say.onStatus(c.getString(R.string.update_failed, e.getClass().getSimpleName()));
            } finally {
                busy = false;
            }
        } }, "PureTV-update").start();
    }

    private static String get(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(15000); h.setReadTimeout(20000);
        h.setRequestProperty("Accept", "application/vnd.github+json");
        h.setRequestProperty("User-Agent", "PureTV-Home-Updater");
        try {
            if (h.getResponseCode() != 200) throw new Exception("HTTP " + h.getResponseCode());
            InputStream in = h.getInputStream();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0) { out.write(b, 0, n); if (out.size() > 2_000_000) throw new Exception("reply too large"); }
            return out.toString("UTF-8");
        } finally { h.disconnect(); }
    }

    private static void download(String url, File to) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();   // GitHub redirects to its file host over https
        h.setConnectTimeout(15000); h.setReadTimeout(60000);
        h.setRequestProperty("User-Agent", "PureTV-Home-Updater");
        try {
            if (h.getResponseCode() != 200) throw new Exception("HTTP " + h.getResponseCode());
            InputStream in = h.getInputStream();
            OutputStream out = new FileOutputStream(to);
            byte[] b = new byte[16384]; int n; long total = 0;
            try {
                while ((n = in.read(b)) > 0) { out.write(b, 0, n); total += n; if (total > 64L * 1024 * 1024) throw new Exception("file too large"); }
            } finally { out.close(); }
        } finally { h.disconnect(); }
    }

    /** The download must carry the installed launcher's own signing key, or it is refused. */
    @SuppressWarnings("deprecation")
    private static boolean sameSigner(Context c, File apk) {
        PackageManager pm = c.getPackageManager();
        try {
            PackageInfo theirs = pm.getPackageArchiveInfo(apk.getPath(), PackageManager.GET_SIGNATURES);
            PackageInfo ours = pm.getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNATURES);
            if (theirs == null || theirs.signatures == null || !c.getPackageName().equals(theirs.packageName)) return false;
            Signature[] a = theirs.signatures, b = ours.signatures;
            return a.length > 0 && Arrays.equals(a, b);
        } catch (Exception e) { return false; }
    }

    private static void installApk(Context c, File apk) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName());
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        int id = pi.createSession(params);
        PackageInstaller.Session s = pi.openSession(id);
        try {
            OutputStream out = s.openWrite("purehome.apk", 0, apk.length());
            InputStream in = new FileInputStream(apk);
            byte[] b = new byte[65536]; int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            in.close(); s.fsync(out); out.close();
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            Intent done = new Intent(c, Result.class).setAction("tv.pure.home.INSTALL_RESULT");
            s.commit(PendingIntent.getBroadcast(c, id, done, flags).getIntentSender());
        } finally { s.close(); }
    }

    /** Android's answer to the install, and the relaunch after PureTV has been replaced. */
    public static class Result extends BroadcastReceiver {
        @SuppressWarnings("deprecation")
        @Override public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())) {
                new File(c.getCacheDir(), "update.apk").delete();
                c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
            int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);   // Android's own "Update this app?" screen
                if (confirm != null) c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else if (status != PackageInstaller.STATUS_SUCCESS) {
                Toast.makeText(c, c.getString(R.string.update_failed, String.valueOf(i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))), Toast.LENGTH_LONG).show();
            }
        }
    }
}
