package tv.pure.home;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * The PureTV Remote Helper (Settings > System > Accessibility > PureTV Remote Helper).
 *
 * On Google TV a newly installed launcher cannot simply be chosen as the Home app. With this helper
 * on, PureTV works without a computer: the Home button opens PureTV, and when Google TV's home comes
 * up by itself (after the TV starts, for example) PureTV is shown instead. It reads only which app
 * is on screen; it never reads text, passwords or anything inside other apps.
 *
 * If the PureTV agent has switched Google TV's home off, Android already sends Home to PureTV and
 * this helper is not needed.
 */
public class HomeHelper extends AccessibilityService {
    private static HomeHelper running;
    private long lastLaunch;
    private CharSequence front = "";   // the app that last came to the front
    private final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
    /** Google TV's home can pull itself back to the front once its own start-up finishes; look again. */
    private final Runnable recheck = () -> { if (override(this) && isGoogleHome(front)) showPureTV(true); };

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("home", Context.MODE_PRIVATE); }
    static boolean override(Context c) { return prefs(c).getBoolean("override", true); }
    static void setOverride(Context c, boolean on) { prefs(c).edit().putBoolean("override", on).apply(); }

    static boolean enabled(Context c) {
        String list = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(list)) return false;
        String me = new ComponentName(c, HomeHelper.class).flattenToString(), shortMe = new ComponentName(c, HomeHelper.class).flattenToShortString();
        for (String s : list.split(":")) if (s.equalsIgnoreCase(me) || s.equalsIgnoreCase(shortMe)) return true;
        return false;
    }

    static boolean sleep() {
        HomeHelper h = running;
        return h != null && Build.VERSION.SDK_INT >= 28 && h.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    @Override protected void onServiceConnected() { running = this; }
    @Override public boolean onUnbind(Intent i) { running = null; return super.onUnbind(i); }
    @Override public void onInterrupt() { }

    private boolean isGoogleHome(CharSequence pkg) {
        if (pkg == null) return false;
        for (String g : Apps.GOOGLE_HOMES) if (g.contentEquals(pkg)) return true;
        return false;
    }

    private void showPureTV(boolean force) {
        long now = SystemClock.uptimeMillis();
        if (!force && now - lastLaunch < 700) { h.removeCallbacks(recheck); h.postDelayed(recheck, 800); return; }
        lastLaunch = now;
        h.removeCallbacks(recheck);
        h.postDelayed(recheck, 1500);   // after a TV start, Google TV may come back once more
        h.postDelayed(recheck, 4000);
        Intent i = new Intent(this, MainActivity.class).setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try { startActivity(i); } catch (Exception ignored) { }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (e.getPackageName() != null) front = e.getPackageName();
        if (override(this) && isGoogleHome(e.getPackageName())) showPureTV(false);
    }

    @Override protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_HOME && override(this)) {
            if (e.getAction() == KeyEvent.ACTION_UP) showPureTV(false);
            return true;
        }
        return false;
    }
}
