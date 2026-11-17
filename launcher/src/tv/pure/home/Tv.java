package tv.pure.home;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/** The TV's own screens: Settings pages, inputs, search, and Google TV's home. PureTV opens them; it never changes them. */
final class Tv {
    private Tv() { }

    static void start(Context c, Intent i) {
        try { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) {
            try { c.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
        }
    }

    static void settings(Context c, String page) {
        String p = page == null ? "" : page;
        Intent i;
        switch (p) {
            case "network": i = new Intent(Settings.ACTION_WIFI_SETTINGS); break;
            case "bluetooth": i = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS); break;
            case "display": i = new Intent(Settings.ACTION_DISPLAY_SETTINGS); break;
            case "sound": i = new Intent(Settings.ACTION_SOUND_SETTINGS); break;
            case "apps": i = new Intent(Settings.ACTION_APPLICATION_SETTINGS); break;
            case "access": i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS); break;
            case "date": i = new Intent(Settings.ACTION_DATE_SETTINGS); break;
            case "unknown": i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.getPackageName())); break;
            default:
                if (p.startsWith("appinfo:")) i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + p.substring(8)));
                else i = new Intent(Settings.ACTION_SETTINGS);
        }
        start(c, i);
    }

    static void search(Context c) {
        Intent i = new Intent(Intent.ACTION_ASSIST);
        if (i.resolveActivity(c.getPackageManager()) == null) i = new Intent("android.search.action.GLOBAL_SEARCH");
        start(c, i);
    }

    static final class Input { String id, name; boolean pass; }

    static List<Input> inputs(Context c) {
        List<Input> out = new ArrayList<>();
        try {
            TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) for (TvInputInfo t : tim.getTvInputList()) {
                if (t.isHidden(c)) continue;
                Input in = new Input();
                CharSequence custom = t.loadCustomLabel(c);
                in.id = t.getId(); in.name = String.valueOf(TextUtils.isEmpty(custom) ? t.loadLabel(c) : custom); in.pass = t.isPassthroughInput();
                out.add(in);
            }
        } catch (Exception ignored) { }
        return out;
    }

    static void input(Context c, Input in) {
        Uri u = in.pass ? TvContract.buildChannelUriForPassthroughInput(in.id) : TvContract.buildChannelsUriForInput(in.id);
        start(c, new Intent(Intent.ACTION_VIEW, u));
    }

    /** "Wi-Fi", "Ethernet" or "Offline", for Control Center. */
    @SuppressWarnings("deprecation")
    static String wifiName(Context c) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkInfo n = cm.getActiveNetworkInfo();
            if (n == null || !n.isConnected()) return "Offline";
            return n.getType() == android.net.ConnectivityManager.TYPE_ETHERNET ? "Ethernet" : "Connected";
        } catch (Exception e) { return "Wi-Fi"; }
    }

    /** Is Google TV's own home still switched on (PureTV runs on top of it through the Remote Helper)? */
    static String googleHome(Context c) {
        PackageManager pm = c.getPackageManager();
        for (String g : Apps.GOOGLE_HOMES) {
            try { if (pm.getApplicationInfo(g, 0).enabled) return g; } catch (Exception ignored) { }
        }
        return null;
    }

    static boolean isHome(Context c) {
        ResolveInfo r = c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY);
        return r != null && r.activityInfo != null && c.getPackageName().equals(r.activityInfo.packageName);
    }

    /** Back to Google TV's home: possible from here only while it is switched on. */
    static boolean openGoogleHome(Context c) {
        String g = googleHome(c);
        if (g == null) return false;
        HomeHelper.setOverride(c, false);
        start(c, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(g));
        return true;
    }
}
