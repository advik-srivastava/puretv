package tv.pure.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * PureTV Home: a clock, your apps, and nothing else. One activity, no network, no services.
 * Everything is laid out in code against the screen width, so it looks the same on 720p and 4K.
 */
public class MainActivity extends Activity {
    static final int ACCENT = 0xFFA8B8FF;
    static final int TEXT = 0xFFECEEF4;
    static final int MUTED = 0xFF8D95A8;
    static final int CARD = 0xFF161922;
    static final int COLS = 5;

    static class App {
        String label;
        ComponentName component;
        Drawable banner;   // 16:9 TV artwork, when the app ships one
        Drawable icon;
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private int unit;                       // 1% of the screen width
    private LinearLayout grid;
    private ScrollView scroll;
    private TextView clock, date, network, inputs;
    private List<App> apps = new ArrayList<>();
    private String focusAfterLoad;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock(); updateNetwork(); }
    };
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { loadApps(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        unit = Math.max(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels) / 100;
        setContentView(buildLayout());
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        loadApps();
    }

    @Override protected void onDestroy() {
        unregisterReceiver(packages);
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(tick, f);
        updateClock();
        updateNetwork();
        updateInputs();
        Updater.maybeAutoCheck(this);   // at most once a day, and only with automatic updates on
    }

    @Override protected void onPause() {
        unregisterReceiver(tick);
        super.onPause();
    }

    /** Home pressed while Home is showing: back to the top, first app focused. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        scroll.smoothScrollTo(0, 0);
        View first = firstTile();
        if (first != null) first.requestFocus();
    }

    @Override public void onBackPressed() { /* Home is the bottom of the stack: Back does nothing. */ }

    // ------------------------------------------------------------------ layout

    private View buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(unit * 5, unit * 3, unit * 5, 0);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.TOP);
        LinearLayout time = new LinearLayout(this);
        time.setOrientation(LinearLayout.VERTICAL);
        clock = text(unit * 5.2f, TEXT);
        clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        clock.setIncludeFontPadding(false);
        date = text(unit * 1.25f, MUTED);
        date.setPadding(unit / 4, unit / 2, 0, 0);
        time.addView(clock);
        time.addView(date);
        top.addView(time, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        inputs = pill(getString(R.string.inputs));
        inputs.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showInputs(); } });
        network = pill(getString(R.string.wifi));
        network.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            open(new Intent(Settings.ACTION_WIFI_SETTINGS), new Intent(Settings.ACTION_SETTINGS)); } });
        TextView settings = pill(getString(R.string.settings));
        settings.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            open(new Intent(Settings.ACTION_SETTINGS)); } });
        settings.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) {
            showHomeMenu(); return true; } });
        top.addView(inputs);
        top.addView(network);
        top.addView(settings);
        root.addView(top);

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, unit * 2, 0, unit * 4);
        scroll.setFadingEdgeLength(unit * 3);
        scroll.setVerticalFadingEdgeEnabled(true);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        scroll.setClipChildren(false);
        scroll.addView(grid);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        lp.topMargin = unit * 3;
        root.addView(scroll, lp);
        root.setClipChildren(false);
        return root;
    }

    private TextView text(float px, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        return t;
    }

    private TextView pill(String label) {
        final TextView t = text(unit * 1.15f, TEXT);
        t.setText(label);
        t.setFocusable(true);
        t.setPadding((int) (unit * 1.6f), (int) (unit * 0.7f), (int) (unit * 1.6f), (int) (unit * 0.7f));
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(unit * 3);
        bg.setColor(0x1FFFFFFF);
        t.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = unit;
        lp.topMargin = unit;
        t.setLayoutParams(lp);
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() { @Override public void onFocusChange(View v, boolean has) {
            bg.setColor(has ? ACCENT : 0x1FFFFFFF);
            t.setTextColor(has ? 0xFF0D1024 : TEXT);
        } });
        return t;
    }

    // ------------------------------------------------------------------ clock and pills

    private void updateClock() {
        Date now = new Date();
        clock.setText(DateFormat.getTimeFormat(this).format(now).replaceAll("(?i)\\s*[ap]\\.?m\\.?", ""));
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int greet = h < 5 ? R.string.night : h < 12 ? R.string.morning : h < 17 ? R.string.afternoon : h < 22 ? R.string.evening : R.string.night;
        String day = new SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM"), Locale.getDefault()).format(now);
        date.setText(getString(greet) + "  ·  " + day);
    }

    @SuppressWarnings("deprecation")
    private void updateNetwork() {
        int label = R.string.offline;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo n = cm == null ? null : cm.getActiveNetworkInfo();
            if (n != null && n.isConnected())
                label = n.getType() == ConnectivityManager.TYPE_ETHERNET ? R.string.ethernet : R.string.wifi;
        } catch (RuntimeException ignored) { }
        network.setText(label);
    }

    private List<TvInputInfo> passthroughInputs() {
        List<TvInputInfo> out = new ArrayList<>();
        try {
            TvInputManager m = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (m != null) for (TvInputInfo i : m.getTvInputList()) if (i.isPassthroughInput()) out.add(i);
        } catch (RuntimeException ignored) { }
        return out;
    }

    /** The Inputs pill is only shown on sets that have HDMI/AV ports (not on streaming sticks). */
    private void updateInputs() {
        inputs.setVisibility(passthroughInputs().isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showInputs() {
        final List<TvInputInfo> list = passthroughInputs();
        if (list.isEmpty()) return;
        CharSequence[] names = new CharSequence[list.size()];
        for (int i = 0; i < names.length; i++) {
            CharSequence custom = list.get(i).loadCustomLabel(this);
            names[i] = TextUtils.isEmpty(custom) ? list.get(i).loadLabel(this) : custom;
        }
        new AlertDialog.Builder(this).setTitle(R.string.inputs).setItems(names, new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface d, int which) {
                open(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(list.get(which).getId())));
            }
        }).show();
    }

    /** Starts the first intent the TV can handle. */
    private void open(Intent... choices) {
        for (Intent i : choices) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (RuntimeException ignored) { }
        }
    }

    // ------------------------------------------------------------------ apps

    private void loadApps() {
        new Thread(new Runnable() { @Override public void run() {
            final List<App> found = queryApps();
            ui.post(new Runnable() { @Override public void run() { apps = found; render(); } });
        } }).start();
    }

    private List<App> queryApps() {
        PackageManager pm = getPackageManager();
        List<App> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        seen.add(getPackageName());
        Set<String> hidden = prefs.getStringSet("hidden", Collections.<String>emptySet());
        // TV apps first; then phone-style apps that have no TV entry (sideloaded ones).
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo r : pm.queryIntentActivities(q, 0)) {
                ActivityInfo a = r.activityInfo;
                if (a == null || !seen.add(a.packageName)) continue;
                App app = new App();
                app.component = new ComponentName(a.packageName, a.name);
                if (hidden.contains(app.component.flattenToString())) continue;
                app.label = String.valueOf(r.loadLabel(pm));
                try {
                    app.banner = a.loadBanner(pm);
                    if (app.banner == null) app.banner = pm.getApplicationBanner(a.applicationInfo);
                } catch (RuntimeException ignored) { }
                if (app.banner == null) app.icon = r.loadIcon(pm);
                out.add(app);
            }
        }
        final List<String> order = Arrays.asList(TextUtils.split(prefs.getString("order", ""), "\n"));
        Collections.sort(out, new java.util.Comparator<App>() { @Override public int compare(App a, App b) {
            int ia = order.indexOf(a.component.flattenToString()), ib = order.indexOf(b.component.flattenToString());
            if (ia >= 0 && ib >= 0) return ia - ib;
            if (ia >= 0) return -1;
            if (ib >= 0) return 1;
            return a.label.compareToIgnoreCase(b.label);
        } });
        return out;
    }

    private View firstTile() {
        if (grid.getChildCount() == 0 || !(grid.getChildAt(0) instanceof ViewGroup)) return null;
        ViewGroup row = (ViewGroup) grid.getChildAt(0);
        return row.getChildCount() > 0 ? row.getChildAt(0) : null;
    }

    private void render() {
        View had = getCurrentFocus();
        String keep = focusAfterLoad != null ? focusAfterLoad : had != null && had.getTag() instanceof String ? (String) had.getTag() : null;
        focusAfterLoad = null;
        grid.removeAllViews();
        if (apps.isEmpty()) {
            TextView empty = text(unit * 1.4f, MUTED);
            empty.setText(R.string.no_apps);
            grid.addView(empty);
            return;
        }
        int gap = (int) (unit * 1.6f);
        int width = (unit * 90 - gap * (COLS - 1)) / COLS;
        View target = null;
        LinearLayout row = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % COLS == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.bottomMargin = (int) (unit * 0.6f);
                grid.addView(row, rp);
            }
            View tile = tile(apps.get(i), i, width);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i % COLS != 0) lp.leftMargin = gap;
            row.addView(tile, lp);
            if (tile.getTag().equals(keep)) target = tile;
        }
        TextView hint = text(unit * 1.0f, 0x668D95A8);
        hint.setText(R.string.hint);
        hint.setPadding(0, unit, 0, 0);
        grid.addView(hint);
        TextView made = text(unit * 0.95f, 0x598D95A8);
        made.setText(R.string.made_by);
        made.setPadding(0, unit / 2, 0, unit * 2);
        grid.addView(made);
        if (target == null) target = firstTile();
        if (target != null) target.requestFocus();
    }

    private View tile(final App app, final int index, int width) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setFocusable(true);
        box.setTag(app.component.flattenToString());
        box.setClipChildren(false);

        final FrameLayout card = new FrameLayout(this);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(unit * 1.1f);
        bg.setColor(CARD);
        card.setBackground(bg);
        card.setClipToOutline(true);
        ImageView art = new ImageView(this);
        if (app.banner != null) {
            art.setImageDrawable(app.banner);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            art.setImageDrawable(app.icon);
            int s = width * 9 / 16 / 2;
            card.addView(art, new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        }
        final GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(unit * 1.1f);
        ring.setColor(Color.TRANSPARENT);
        card.setForeground(ring);
        box.addView(card, new LinearLayout.LayoutParams(width, width * 9 / 16));

        final TextView name = text(unit * 1.05f, MUTED);
        name.setText(app.label);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        name.setPadding(0, (int) (unit * 0.9f), 0, (int) (unit * 0.9f));
        name.setAlpha(0.55f);
        box.addView(name, new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));

        box.setOnFocusChangeListener(new View.OnFocusChangeListener() { @Override public void onFocusChange(View v, boolean has) {
            ring.setStroke(has ? Math.max(unit / 5, 3) : 0, ACCENT);
            card.animate().scaleX(has ? 1.09f : 1f).scaleY(has ? 1.09f : 1f).setDuration(140).start();
            card.setElevation(has ? unit * 2 : 0);
            name.setTextColor(has ? TEXT : MUTED);
            name.animate().alpha(has ? 1f : 0.55f).translationY(has ? unit * 0.35f : 0).setDuration(140).start();
        } });
        box.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { launch(app); } });
        box.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) {
            showTileMenu(app, index); return true; } });
        return box;
    }

    private void launch(App app) {
        Intent i = new Intent(Intent.ACTION_MAIN).setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (RuntimeException e) {
            loadApps();   // the app went away: refresh the grid
        }
    }

    // ------------------------------------------------------------------ menus

    private void saveOrder() {
        List<String> order = new ArrayList<>();
        for (App a : apps) order.add(a.component.flattenToString());
        prefs.edit().putString("order", TextUtils.join("\n", order)).apply();
    }

    private void move(int from, int to) {
        if (to < 0 || to >= apps.size()) return;
        App a = apps.remove(from);
        apps.add(to, a);
        focusAfterLoad = a.component.flattenToString();
        saveOrder();
        render();
    }

    private void showTileMenu(final App app, final int index) {
        CharSequence[] items = {getString(R.string.move_front), getString(R.string.move_left), getString(R.string.move_right),
                getString(R.string.hide), getString(R.string.app_info)};
        new AlertDialog.Builder(this).setTitle(app.label).setItems(items, new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface d, int which) {
                if (which == 0) move(index, 0);
                else if (which == 1) move(index, index - 1);
                else if (which == 2) move(index, index + 1);
                else if (which == 3) {
                    Set<String> hidden = new HashSet<>(prefs.getStringSet("hidden", Collections.<String>emptySet()));
                    hidden.add(app.component.flattenToString());
                    prefs.edit().putStringSet("hidden", hidden).apply();
                    loadApps();
                } else {
                    open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.component.getPackageName(), null)));
                }
            }
        }).show();
    }

    /** Hold OK on Settings: the launcher's own few options. */
    private void showHomeMenu() {
        final int hidden = prefs.getStringSet("hidden", Collections.<String>emptySet()).size();
        final boolean auto = Updater.auto(this);
        CharSequence[] items = {getString(R.string.tv_settings), getString(R.string.show_hidden, hidden), getString(R.string.home_app),
                getString(R.string.update_check, Updater.installedVersion(this)), getString(auto ? R.string.update_auto_on : R.string.update_auto_off),
                getString(R.string.about)};
        new AlertDialog.Builder(this).setTitle(R.string.app_name).setItems(items, new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface d, int which) {
                if (which == 0) open(new Intent(Settings.ACTION_SETTINGS));
                else if (which == 1) { prefs.edit().remove("hidden").apply(); loadApps(); }
                else if (which == 2) open(new Intent(Settings.ACTION_HOME_SETTINGS), new Intent(Settings.ACTION_SETTINGS));
                else if (which == 3) Updater.check(MainActivity.this, true, new Updater.Listener() {
                    @Override public void onStatus(String m) { android.widget.Toast.makeText(MainActivity.this, m, android.widget.Toast.LENGTH_LONG).show(); }
                });
                else if (which == 5) new AlertDialog.Builder(MainActivity.this).setTitle(R.string.app_name)
                        .setMessage(getString(R.string.about_text, Updater.installedVersion(MainActivity.this)))
                        .setPositiveButton(android.R.string.ok, null).show();
                else { Updater.setAuto(MainActivity.this, !auto);
                    android.widget.Toast.makeText(MainActivity.this, !auto ? R.string.update_auto_on : R.string.update_auto_off, android.widget.Toast.LENGTH_SHORT).show(); }
            }
        }).show();
    }

    /** The remote's Menu key opens the same menu as holding OK. */
    @Override public boolean onKeyUp(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_MENU) {
            View f = getCurrentFocus();
            if (f != null && f.getTag() instanceof String) { f.performLongClick(); return true; }
        }
        return super.onKeyUp(code, e);
    }
}
