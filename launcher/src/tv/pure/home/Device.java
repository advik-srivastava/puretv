package tv.pure.home;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What the TV can tell PureTV about itself: the Wi-Fi network, Bluetooth devices, where the sound goes,
 * and the details for Settings › About. Android hides the Wi-Fi name and Bluetooth devices unless PureTV
 * has the location and nearby-devices permissions; the PureTV agent grants them at install, and PureTV asks
 * once on the TV if they're missing. Without them, PureTV shows "Connected" and the device count instead.
 */
final class Device {
    private Device() { }

    private static BluetoothProfile a2dp;   // filled once Android hands over the audio profile

    static void init(Context c) {
        try {
            BluetoothAdapter ad = adapter(c);
            if (ad != null && canBt(c)) ad.getProfileProxy(c, new BluetoothProfile.ServiceListener() {
                @Override public void onServiceConnected(int p, BluetoothProfile proxy) { a2dp = proxy; }
                @Override public void onServiceDisconnected(int p) { a2dp = null; }
            }, BluetoothProfile.A2DP);
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ permissions

    static String[] wanted() {
        List<String> w = new ArrayList<>();
        w.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 31) w.add(Manifest.permission.BLUETOOTH_CONNECT);
        return w.toArray(new String[0]);
    }

    static boolean has(Context c, String p) { return c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }
    static boolean canBt(Context c) { return Build.VERSION.SDK_INT < 31 || has(c, Manifest.permission.BLUETOOTH_CONNECT); }

    /** Asks once (Android's own dialog) for what's missing, so names can be shown. */
    static void ask(Activity a, android.content.SharedPreferences p) {
        if (p.getBoolean("askedDevicePerms", false)) return;
        List<String> missing = new ArrayList<>();
        for (String w : wanted()) if (!has(a, w)) missing.add(w);
        if (missing.isEmpty()) return;
        p.edit().putBoolean("askedDevicePerms", true).apply();
        a.requestPermissions(missing.toArray(new String[0]), 7);
    }

    // ------------------------------------------------------------------ network

    static final class Net {
        String kind = "Offline", name = "", ip = "";
        int bars = -1;          // 0..4 for Wi-Fi
        boolean connected;
    }

    @SuppressWarnings("deprecation")
    static Net net(Context c) {
        Net n = new Net();
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network an = cm.getActiveNetwork();
            NetworkCapabilities nc = an == null ? null : cm.getNetworkCapabilities(an);
            if (nc == null) return n;
            n.connected = true;
            LinkProperties lp = cm.getLinkProperties(an);
            if (lp != null) for (LinkAddress la : lp.getLinkAddresses()) if (la.getAddress() instanceof Inet4Address) n.ip = la.getAddress().getHostAddress();
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) { n.kind = "Ethernet"; n.name = "Ethernet"; return n; }
            n.kind = "Wi-Fi";
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wi = wm.getConnectionInfo();
            if (wi != null) {
                String ssid = wi.getSSID();
                if (ssid != null && !ssid.contains("unknown ssid") && !ssid.isEmpty()) n.name = ssid.replaceAll("^\"|\"$", "");
                n.bars = WifiManager.calculateSignalLevel(wi.getRssi(), 5);
            }
            if (n.name.isEmpty()) n.name = "Connected";
        } catch (Exception ignored) { }
        return n;
    }

    static String bars(int b) { return b < 0 ? "" : b >= 4 ? "Excellent signal" : b == 3 ? "Good signal" : b == 2 ? "Fair signal" : "Weak signal"; }

    // ------------------------------------------------------------------ Bluetooth

    static final class Bt {
        String name, kind;     // kind: Remote, Headphones, Speaker, Keyboard, Game controller, Device
        boolean connected;
    }

    private static BluetoothAdapter adapter(Context c) {
        BluetoothManager bm = (BluetoothManager) c.getSystemService(Context.BLUETOOTH_SERVICE);
        return bm == null ? null : bm.getAdapter();
    }

    static boolean btOn(Context c) {
        try { BluetoothAdapter a = adapter(c); return a != null && a.isEnabled(); } catch (Exception e) { return false; }
    }

    static List<Bt> bt(Context c) {
        List<Bt> out = new ArrayList<>();
        try {
            BluetoothAdapter a = adapter(c);
            if (a == null || !canBt(c)) return out;
            Set<String> on = new HashSet<>();
            BluetoothManager bm = (BluetoothManager) c.getSystemService(Context.BLUETOOTH_SERVICE);
            for (BluetoothDevice d : bm.getConnectedDevices(BluetoothProfile.GATT)) on.add(d.getAddress());   // remotes are usually Bluetooth LE
            BluetoothProfile p = a2dp;
            if (p != null) for (BluetoothDevice d : p.getConnectedDevices()) on.add(d.getAddress());
            for (BluetoothDevice d : a.getBondedDevices()) {
                Bt b = new Bt();
                b.name = TextUtils.isEmpty(d.getName()) ? "Bluetooth device" : d.getName();
                b.kind = kind(d);
                b.connected = on.contains(d.getAddress());
                out.add(b);
            }
            java.util.Collections.sort(out, (x, y) -> x.connected != y.connected ? (x.connected ? -1 : 1) : x.name.compareToIgnoreCase(y.name));
        } catch (SecurityException ignored) {
        } catch (Exception ignored) { }
        return out;
    }

    private static String kind(BluetoothDevice d) {
        BluetoothClass bc = d.getBluetoothClass();
        String n = String.valueOf(d.getName()).toLowerCase(Locale.ROOT);
        if (n.contains("remote") || n.contains("rc") && n.length() < 12) return "Remote";
        if (bc == null) return "Device";
        switch (bc.getMajorDeviceClass()) {
            case BluetoothClass.Device.Major.AUDIO_VIDEO:
                return bc.getDeviceClass() == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER ? "Speaker" : "Headphones";
            case BluetoothClass.Device.Major.PERIPHERAL:
                int dc = bc.getDeviceClass();
                if ((dc & 0x40) != 0) return "Keyboard";
                if ((dc & 0x0C) == 0x08) return "Game controller";
                return "Remote";
            case BluetoothClass.Device.Major.PHONE: return "Phone";
            default: return "Device";
        }
    }

    /** "On · Remote" / "On · 2 devices" / "Off", for Control Center. */
    static String btLine(Context c) {
        if (!btOn(c)) return "Off";
        List<Bt> l = bt(c);
        for (Bt b : l) if (b.connected) return "On · " + b.name;
        if (!canBt(c)) return "On";
        return l.isEmpty() ? "On · No devices" : "On · " + l.size() + (l.size() == 1 ? " device" : " devices");
    }

    // ------------------------------------------------------------------ sound

    /** Where the sound goes now: Bluetooth headphones, a soundbar over HDMI, USB, or the TV speakers. */
    static String audio(Context c) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            String bt = null, hdmi = null, usb = null;
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int t = d.getType();
                String name = String.valueOf(d.getProductName());
                if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) bt = name;
                else if (t == AudioDeviceInfo.TYPE_HDMI_ARC || (Build.VERSION.SDK_INT >= 31 && t == AudioDeviceInfo.TYPE_HDMI_EARC))
                    hdmi = (t == AudioDeviceInfo.TYPE_HDMI_ARC ? "HDMI ARC" : "HDMI eARC");
                else if (t == AudioDeviceInfo.TYPE_USB_HEADSET || t == AudioDeviceInfo.TYPE_USB_DEVICE) usb = name;
            }
            if (bt != null) return bt;
            if (hdmi != null) return "Soundbar · " + hdmi;
            if (usb != null) return usb;
        } catch (Exception ignored) { }
        return "TV Speakers";
    }

    // ------------------------------------------------------------------ About

    static String name(Context c) {
        String n = Settings.Global.getString(c.getContentResolver(), "device_name");
        return TextUtils.isEmpty(n) ? Build.MODEL : n;
    }

    static String model() {
        String m = Build.MANUFACTURER, d = Build.MODEL;
        return d.toLowerCase(Locale.ROOT).startsWith(m.toLowerCase(Locale.ROOT)) ? d : cap(m) + " " + d;
    }

    static String os() {
        String patch = Build.VERSION.SDK_INT >= 23 ? Build.VERSION.SECURITY_PATCH : "";
        return "Android TV " + Build.VERSION.RELEASE + (TextUtils.isEmpty(patch) ? "" : " · security patch " + patch);
    }

    static String screen(Activity a) {
        DisplayMetrics m = new DisplayMetrics();
        a.getWindowManager().getDefaultDisplay().getRealMetrics(m);
        int w = Math.max(m.widthPixels, m.heightPixels), h = Math.min(m.widthPixels, m.heightPixels);
        String label = h >= 2160 ? "4K" : h >= 1080 ? "Full HD" : h >= 720 ? "HD" : "";
        return w + " × " + h + (label.isEmpty() ? "" : " (" + label + ")");
    }

    static String memory(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return String.format(Locale.US, "%.1f GB · %d%% free", mi.totalMem / 1073741824.0, Math.round(100.0 * mi.availMem / mi.totalMem));
    }

    static String storage() {
        StatFs s = new StatFs(Environment.getDataDirectory().getPath());
        return String.format(Locale.US, "%.1f GB free of %.1f GB", s.getAvailableBytes() / 1e9, s.getTotalBytes() / 1e9);
    }

    private static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
}
