package tv.pure.home;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * PureTV Home, drawn natively: no web view, no live blur. The wallpaper, the glass clock and the glass panels
 * are bitmaps drawn when something changes; at rest the Home screen uses no CPU and about 60 MB of memory.
 */
public class MainActivity extends Activity {
    final Handler ui = new Handler(Looper.getMainLooper());
    Store store;
    Apps apps;
    Scenes scenes;
    SceneView scene;
    FrameLayout root, overlay;
    private FrameLayout content;
    private View band;
    private LinearLayout row1, grid, bar;
    private TextView greet, date;
    private LinearLayout dateBox;
    private Btn chip, weatherPill;
    private TextView hint;
    private View hintBoxRef;
    private FrameLayout launching;
    int sleepIx;
    private boolean saver;
    private android.animation.ObjectAnimator drift;
    private final Runnable sleepNow = () -> { if (!HomeHelper.sleep()) toast("Sleep timer: time to switch the TV off"); sleepIx = 0; };
    private final Runnable saverTick = new Runnable() {
        @Override public void run() { if (saver) { scenes.show(scenes.index + 1, true); ui.postDelayed(this, 9000); } }
    };
    private final Runnable saverStart = () -> { MainActivity m = MainActivity.this; if (m.resumed && m.panels.isEmpty() && !m.deep && m.store.saverAfter > 0) startSaver(); };
    final List<View> panels = new ArrayList<>();
    private final List<Runnable> backs = new ArrayList<>();
    private boolean deep, ambient, resumed;
    private float deepY, ambientY;
    private ValueAnimator darkenAnim;
    Weather weather;
    private Updates updates;
    Updates updatesRef() { return updates; }

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateTime(); }
    };
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { ui.postDelayed(() -> { apps.load(); buildHome(); }, 800); }
    };
    private final Runnable rotate = new Runnable() {
        @Override public void run() {
            if (resumed && panels.isEmpty() && store.rotate) {
                scenes.show(scenes.index + 1, !store.reduceMotion);
                store.scene = scenes.index; store.save();
                placeDate();
            }
            ui.postDelayed(this, 20000);
        }
    };
    private final Runnable idle = () -> { if (resumed && panels.isEmpty() && !deep) ambient(true); };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        int w = Math.max(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
        Ui.init(this, w);
        store = new Store(this);
        Glass.style = store.glass;
        apps = new Apps(this);
        apps.load();
        scenes = new Scenes(this, store.scene);
        weather = new Weather(this, this::updateTime);
        updates = new Updates(this);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setClipChildren(false);
        scene = new SceneView(this, scenes);
        root.addView(scene, new FrameLayout.LayoutParams(-1, -1));

        dateBox = new LinearLayout(this);
        dateBox.setOrientation(LinearLayout.VERTICAL);
        dateBox.setGravity(Gravity.CENTER_HORIZONTAL);
        greet = Ui.text(this, 22, Ui.semibold, 0xD9FFFFFF);
        date = Ui.text(this, 34, Ui.semibold, 0xFFFFFFFF);
        for (TextView t : new TextView[] { greet, date }) { t.setShadowLayer(Ui.fx(10), 0, Ui.fx(1), 0x66000000); t.setGravity(Gravity.CENTER); }
        date.setPadding(0, Ui.px(8), 0, 0);
        dateBox.addView(greet);
        dateBox.addView(date);
        root.addView(dateBox, Row.at(-1, -2, Gravity.TOP));

        content = new FrameLayout(this);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.px(60), Ui.px(40), Ui.px(60), 0);
        bar.setVisibility(View.INVISIBLE);
        root.addView(bar, Row.at(-1, -2, Gravity.TOP));

        overlay = new FrameLayout(this);
        overlay.setClipChildren(false);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        root.getViewTreeObserver().addOnGlobalFocusChangeListener((was, now) -> {
            if (now == null) return;
            if (now != was) Ui.sound("nav");
            if (panels.isEmpty()) {
                deep(isIn(now, grid));
                if (!isIn(now, bar) && bar.getVisibility() == View.VISIBLE) bar.animate().alpha(0).translationY(-Ui.fx(36)).setDuration(250).withEndAction(() -> bar.setVisibility(View.INVISIBLE)).start();
                if (hintBoxRef != null) hintBoxRef.animate().alpha(isIn(now, row1) ? 1 : 0).translationY(isIn(now, row1) ? 0 : Ui.fx(10))
                        .setStartDelay(isIn(now, row1) ? 500 : 0).setDuration(400).start();
            }
        });

        buildBar();
        buildHome();
        updateTime();
        placeDate();
        if (!store.setupDone) ui.post(() -> new Setup(this).show());
    }

    // ------------------------------------------------------------------ Home layout

    void buildHome() {
        content.removeAllViews();
        Store.Profile me = store.me();
        boolean kids = me.kids;
        int cols = store.dock + 1;
        float gap = 36, tw = (1792 - gap * (cols - 1)) / cols, th = tw * .6f;
        float bandH = th + 52, bandTop = 1080 - 64 - bandH, gridTop = bandTop + bandH + 58;
        deepY = -Ui.fx(gridTop - 330);
        ambientY = Ui.fx(bandH + 130);

        band = new View(this);
        band.setBackground(new Glass(band, scenes, 46));
        band.setElevation(Ui.fx(6));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(Ui.px(1856), Ui.px(bandH));
        blp.leftMargin = Ui.px(32); blp.topMargin = Ui.px(bandTop);
        content.addView(band, blp);

        LinearLayout hintBox = new LinearLayout(this);   // "Press Up…", shown while the top row has focus
        hintBox.setOrientation(LinearLayout.VERTICAL);
        hintBox.setGravity(Gravity.CENTER_HORIZONTAL);
        Btn.IconView up = new Btn.IconView(this, "up");
        hintBox.addView(up, new LinearLayout.LayoutParams(Ui.px(40), Ui.px(26)));
        hint = Ui.text(this, 22, Ui.semibold, 0xD9FFFFFF);
        hint.setText("Press Up for profiles, search and settings");
        hint.setGravity(Gravity.CENTER);
        hint.setShadowLayer(Ui.fx(8), 0, Ui.fx(1), 0x80000000);
        hintBox.addView(hint);
        hintBox.setAlpha(0f);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(-1, -2);
        hlp.topMargin = Ui.px(bandTop - 92);
        content.addView(hintBox, hlp);
        hintBoxRef = hintBox;

        row1 = row();
        List<Apps.App> first = kids ? apps.kids() : apps.top(me, store.dock);
        for (Apps.App a : first) row1.addView(appTile(a, tw, th, kids), tileLp(tw, th, gap, row1.getChildCount() == 0));
        row1.addView(sysTile(kids ? "parents" : "settings", kids ? "Parents" : "Settings", tw, th), tileLp(tw, th, gap, row1.getChildCount() == 0));
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(-2, -2);
        rlp.leftMargin = Ui.px(64); rlp.topMargin = Ui.px(bandTop + 26);
        content.addView(row1, rlp);

        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        if (!kids) {
            List<View> rest = new ArrayList<>();
            for (Apps.App a : apps.all) if (!first.contains(a) && !me.hidden.contains(a.pkg)) rest.add(appTile(a, tw, th, false));
            rest.add(sysTile("search", "Search", tw, th));
            rest.add(sysTile("inputs", "Inputs", tw, th));
            LinearLayout r = null;
            for (int i = 0; i < rest.size(); i++) {
                if (i % cols == 0) { r = row(); grid.addView(r, new LinearLayout.LayoutParams(-2, Ui.px(th + 74))); }
                r.addView(rest.get(i), tileLp(tw, th, gap, i % cols == 0));
            }
        }
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(-2, -2);
        glp.leftMargin = Ui.px(64); glp.topMargin = Ui.px(gridTop);
        content.addView(grid, glp);
        for (int i = 0; i < row1.getChildCount(); i++) row1.getChildAt(i).setNextFocusUpId(R.id.puretv_chip);
        content.setTranslationY(deep ? deepY : 0);
        if (panels.isEmpty()) ui.post(() -> { View f = row1.getChildAt(0); if (f != null && (getCurrentFocus() == null || !isIn(getCurrentFocus(), root))) f.requestFocus(); });
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        return r;
    }

    private LinearLayout.LayoutParams tileLp(float tw, float th, float gap, boolean first) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(tw), Ui.px(th + 60));
        lp.leftMargin = first ? 0 : Ui.px(gap);
        return lp;
    }

    private Tile appTile(Apps.App a, float tw, float th, boolean kids) {
        String label = kids && Apps.kidsName(a.pkg) != null ? Apps.kidsName(a.pkg) : a.name;
        Tile t = new Tile(this, a.pkg, null, label, apps.art(a.pkg), Ui.fx(tw), Ui.fx(th), kids);
        t.setOnClickListener(v -> { Ui.sound("ok"); launchApp(a.pkg, t); });
        t.setOnLongClickListener(v -> { if (store.me().kids) { toast("Ask a parent to change the apps"); return true; } Menus.app(this, t); return true; });
        return t;
    }

    private Tile sysTile(String sys, String label, float tw, float th) {
        Tile t = new Tile(this, null, sys, label, null, Ui.fx(tw), Ui.fx(th), false);
        t.setOnClickListener(v -> {
            Ui.sound("ok");
            switch (sys) {
                case "settings": new Settings(this).show(null); break;
                case "parents": Menus.pin(this, false, () -> new Settings(this).show(null)); break;
                case "search": Tv.search(this); break;
                case "inputs": Menus.inputs(this); break;
            }
        });
        return t;
    }

    private void buildBar() {
        bar.removeAllViews();
        bar.setPadding(Ui.px(64), Ui.px(40), Ui.px(64), 0);
        Store.Profile me = store.me();
        chip = new Btn(this, 34, 1.12f);
        chip.setId(R.id.puretv_chip);
        chip.setPadding(Ui.px(8), 0, Ui.px(26), 0);
        TextView av = Ui.text(this, 30, null, 0xFF000000);
        av.setText(me.emoji); av.setGravity(Gravity.CENTER);
        av.setBackground(dot(Ui.parse(me.bg, 0xFF8E8E93)));
        LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(Ui.px(52), Ui.px(52)); al.rightMargin = Ui.px(14);
        chip.addView(av, al);
        chip.addView(chip.text(this, me.name, 22, false));
        chip.action = () -> Menus.profiles(this);
        bar.addView(chip, new LinearLayout.LayoutParams(-2, Ui.px(68)));
        Btn search = circle("search", () -> Tv.search(this));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Ui.px(68), Ui.px(68)); sl.leftMargin = Ui.px(16);
        bar.addView(search, sl);

        // Home / Apps / Live TV in one glass capsule
        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(Ui.px(6), Ui.px(6), Ui.px(6), Ui.px(6));
        tabs.setBackground(new Glass(tabs, scenes, 40));
        tabs.setClipChildren(false);
        String[] names = { "Home", "Apps", "Live TV" };
        for (int i = 0; i < names.length; i++) {
            final int k = i;
            TextView tab = Ui.text(this, 23, Ui.semibold, i == 0 ? 0xFFFFFFFF : 0xD1FFFFFF);
            tab.setText(names[i]);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(Ui.px(30), 0, Ui.px(30), 0);
            tab.setFocusable(true);
            tab.setBackground(i == 0 ? Ui.round(0x38FFFFFF, 28) : null);
            tab.setOnFocusChangeListener((v, f) -> {
                tab.setBackground(f ? Ui.round(0xFFFFFFFF, 28) : k == 0 ? Ui.round(0x38FFFFFF, 28) : null);
                tab.setTextColor(f ? 0xFF111111 : k == 0 ? 0xFFFFFFFF : 0xD1FFFFFF);
                v.animate().scaleX(f ? 1.08f : 1).scaleY(f ? 1.08f : 1).setDuration(300).setInterpolator(Ui.LIFT).start();
            });
            tab.setOnClickListener(v -> {
                Ui.sound("ok");
                if (k == 0) { View t = row1.getChildAt(0); if (t != null) t.requestFocus(); }
                else if (k == 1) { View g = grid.getChildCount() > 0 ? ((ViewGroup) grid.getChildAt(0)).getChildAt(0) : null; if (g != null) g.requestFocus(); else toast("All your apps are in the top row"); }
                else liveTv();
            });
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, Ui.px(56)); tl.leftMargin = i == 0 ? 0 : Ui.px(4);
            tabs.addView(tab, tl);
        }
        LinearLayout.LayoutParams tbl = new LinearLayout.LayoutParams(0, -2, 1f);
        LinearLayout tabWrap = new LinearLayout(this);
        tabWrap.setGravity(Gravity.CENTER);
        tabWrap.setClipChildren(false);
        tabWrap.addView(tabs, new LinearLayout.LayoutParams(-2, Ui.px(68)));
        bar.addView(tabWrap, tbl);

        weatherPill = new Btn(this, 34, 1.12f);
        weatherPill.setPadding(Ui.px(14), 0, Ui.px(24), 0);
        weatherPill.icon(this, weather.has() ? Weather.iconName(weather.code) : "cloud", 40, 10);
        weatherPill.addView(weatherPill.text(this, weather.has() ? weather.temp + "°" : "--°", 22, false));
        weatherPill.action = () -> Panels.weather(this);
        weatherPill.setVisibility("off".equals(store.weather) ? View.GONE : View.VISIBLE);
        bar.addView(weatherPill, new LinearLayout.LayoutParams(-2, Ui.px(68)));
        Btn cc = circle("cc", () -> Panels.controlCenter(this));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ui.px(68), Ui.px(68)); cl.leftMargin = Ui.px(16);
        bar.addView(cc, cl);
        Btn gear = circle("gearmono", () -> { if (store.me().kids) Menus.pin(this, false, () -> new Settings(this).show(null)); else new Settings(this).show(null); });
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(Ui.px(68), Ui.px(68)); gl.leftMargin = Ui.px(16);
        bar.addView(gear, gl);
    }

    private Btn circle(String icon, Runnable action) {
        Btn b = new Btn(this, 34, 1.12f);
        b.setGravity(Gravity.CENTER);
        b.icon(this, icon, 30, 0);
        b.action = action;
        return b;
    }

    private void liveTv() {
        for (Tv.Input in : Tv.inputs(this)) if (!in.pass) { Tv.input(this, in); return; }
        List<Tv.Input> all = Tv.inputs(this);
        if (!all.isEmpty()) Tv.input(this, all.get(0)); else toast("This TV has no tuner or inputs to show");
    }

    /** Opens an app; from a tile, the tile grows to fill the screen first, as in PureTV 2. */
    void launchApp(String pkg, View from) {
        Apps.App a = apps.get(pkg);
        if (a == null) return;
        store.recents.remove(pkg);
        store.recents.add(0, pkg);
        while (store.recents.size() > 8) store.recents.remove(store.recents.size() - 1);
        store.save();
        if (from == null || store.reduceMotion) { if (!apps.launch(pkg, store.me().kids)) toast(a.name + " can't be opened"); return; }
        android.graphics.Bitmap art = apps.art(pkg);
        int bg = art != null ? art.getPixel(2, art.getHeight() / 2) | 0xFF000000 : 0xFF222222;
        int[] at = new int[2], rootAt = new int[2];
        from.getLocationOnScreen(at); root.getLocationOnScreen(rootAt);
        float fw = from.getWidth() * from.getScaleX(), fh = (from.getHeight() - Ui.fx(60)) * from.getScaleY();
        float x0 = at[0] - rootAt[0] - (from.getWidth() * (from.getScaleX() - 1)) / 2 + from.getWidth() * (from.getScaleX() - 1) / 2;
        float y0 = at[1] - rootAt[1];
        launching = new FrameLayout(this);
        launching.setBackground(Ui.round(bg, 20));
        launching.setClipToOutline(true);
        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageBitmap(art);
        logo.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        launching.addView(logo, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Math.round(fw), Math.round(fh));
        root.addView(launching, lp);
        launching.setTranslationX(x0); launching.setTranslationY(y0);
        launching.setElevation(Ui.fx(40));
        final float W = root.getWidth(), H = root.getHeight();
        ValueAnimator grow = ValueAnimator.ofFloat(0, 1).setDuration(520);
        grow.setInterpolator(Ui.EASE);
        final FrameLayout L = launching;
        grow.addUpdateListener(v -> {   // the frame grows to the screen; the artwork grows less, to sit in the middle
            float t = (float) v.getAnimatedValue();
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) L.getLayoutParams();
            p.width = Math.round(fw + (W - fw) * t); p.height = Math.round(fh + (H - fh) * t);
            L.setLayoutParams(p);
            L.setTranslationX(x0 * (1 - t)); L.setTranslationY(y0 * (1 - t));
            int lw = Math.round(fw + (W * .36f / .6f * .6f - fw) * t);
            logo.setLayoutParams(new FrameLayout.LayoutParams(Math.max(lw, Math.round(fw)), Math.round(Math.max(fh, fh + (H * .36f - fh) * t)), Gravity.CENTER));
        });
        grow.start();
        ui.postDelayed(() -> { if (!apps.launch(pkg, store.me().kids)) { toast(a.name + " can't be opened"); endLaunch(); } }, 380);
    }

    private void endLaunch() {
        if (launching == null) return;
        final View L = launching;
        launching = null;
        L.animate().alpha(0).setDuration(350).withEndAction(() -> root.removeView(L)).start();
    }

    /** Recent Apps "close": Android stops the app if it is in the background. Its data and sign-in stay. */
    void closeApp(String pkg) {
        try { ((android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE)).killBackgroundProcesses(pkg); } catch (Exception ignored) { }
        store.recents.remove(pkg);
        store.save();
    }

    void setSleep(int minutes) {
        ui.removeCallbacks(sleepNow);
        if (minutes > 0) { ui.postDelayed(sleepNow, minutes * 60000L); toast("The TV switches off in " + Panels.SLEEP[sleepIx]); }
        else toast("Sleep timer off");
    }

    /** The screen saver: only the wallpaper and its clock, changing every 9 seconds with a slow drift. */
    void startSaver() {
        if (saver) return;
        saver = true;
        closeAll();
        for (View v : new View[] { content, dateBox, bar }) v.animate().alpha(0).setDuration(900).start();
        scene.setPivotX(root.getWidth() * .4f); scene.setPivotY(root.getHeight() * .4f);
        drift = android.animation.ObjectAnimator.ofPropertyValuesHolder(scene,
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.07f), android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.07f));
        drift.setDuration(24000); drift.setRepeatMode(ValueAnimator.REVERSE); drift.setRepeatCount(ValueAnimator.INFINITE);
        drift.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        drift.start();
        ui.postDelayed(saverTick, 9000);
    }

    private void stopSaver() {
        if (!saver) return;
        saver = false;
        ui.removeCallbacks(saverTick);
        if (drift != null) drift.cancel();
        scene.animate().scaleX(1).scaleY(1).setDuration(600).start();
        content.animate().alpha(1).setDuration(500).start();
        dateBox.animate().alpha(1).setDuration(500).start();
        ambient(false);
        placeDate();
    }

    void profileChanged() { buildBar(); buildHome(); updateTime(); }

    static boolean isIn(View v, View parent) {
        for (View x = v; x != null; x = x.getParent() instanceof View ? (View) x.getParent() : null) if (x == parent) return true;
        return false;
    }

    // ------------------------------------------------------------------ states

    private void deep(boolean on) {
        if (on == deep) return;
        deep = on;
        content.animate().translationY(on ? deepY : 0).setDuration(560).setInterpolator(Ui.EASE)
                .setUpdateListener(a -> band.invalidate()).start();
        dateBox.animate().alpha(on ? 0 : 1).setDuration(300).start();
        darken(on ? 1 : 0);
    }

    void darken(float to) {
        if (darkenAnim != null) darkenAnim.cancel();
        darkenAnim = ValueAnimator.ofFloat(scene.darken, to).setDuration(450);
        darkenAnim.addUpdateListener(a -> scene.setDarken((float) a.getAnimatedValue()));
        darkenAnim.start();
    }

    private void ambient(boolean on) {
        if (on == ambient) return;
        ambient = on;
        content.animate().translationY(on ? ambientY : 0).alpha(on ? 0 : 1).setDuration(on ? 900 : 500).setInterpolator(Ui.EASE)
                .setUpdateListener(a -> band.invalidate()).start();
    }

    private void wake() {
        ui.removeCallbacks(idle);
        ui.postDelayed(idle, 25000);
        ui.removeCallbacks(saverStart);
        if (store.saverAfter > 0) ui.postDelayed(saverStart, store.saverAfter * 1000L);
    }

    void placeDate() {
        Scenes.Scene s = scenes.cur();
        dateBox.measure(View.MeasureSpec.makeMeasureSpec(root.getWidth() > 0 ? root.getWidth() : 1920, View.MeasureSpec.AT_MOST), View.MeasureSpec.UNSPECIFIED);
        float digits = Ui.fx(s.clockTop + s.clockSize * .0425f);   // where the digits begin
        float top = Math.max(digits - Ui.fx(22) - dateBox.getMeasuredHeight(), Ui.fx(40));
        dateBox.animate().translationY(top).setDuration(dateBox.getTranslationY() == 0 ? 0 : 900).setInterpolator(Ui.EASE).start();
    }

    void updateTime() {
        Date now = new Date();
        boolean h24 = DateFormat.is24HourFormat(this);
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        String t = h24 ? String.format(Locale.US, "%d:%02d", h, c.get(Calendar.MINUTE))
                : String.format(Locale.US, "%d:%02d", h % 12 == 0 ? 12 : h % 12, c.get(Calendar.MINUTE));
        scene.setTime(t);
        String hello = h < 5 ? "Good night" : h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : h < 22 ? "Good evening" : "Good night";
        greet.setText(hello + ", " + store.me().name);
        String d = new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now);
        String w = weather.line();
        if (bar.getVisibility() != View.VISIBLE && weatherPill != null) buildBar();   // the weather pill follows the latest reading
        date.setText(w.isEmpty() ? d : d + "   " + w);
    }

    // ------------------------------------------------------------------ overlays (Settings, menus, setup)

    void open(View panel, Runnable onBack) {
        if (panels.isEmpty()) {
            content.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            bar.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            ambient(false);
        } else panels.get(panels.size() - 1).setVisibility(View.INVISIBLE);
        panels.add(panel);
        backs.add(onBack);
        overlay.addView(panel);
        panel.setAlpha(0f);
        panel.setScaleX(.97f); panel.setScaleY(.97f);
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(Ui.EASE).start();
    }

    void close(View panel) {
        int i = panels.indexOf(panel);
        if (i < 0) return;
        panels.remove(i);
        backs.remove(i);
        panel.animate().alpha(0f).scaleX(.98f).scaleY(.98f).setDuration(220).setInterpolator(Ui.EASE).withEndAction(() -> overlay.removeView(panel)).start();
        panel.setFocusable(false);
        if (panel instanceof ViewGroup) ((ViewGroup) panel).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if (!panels.isEmpty()) {
            View top = panels.get(panels.size() - 1);
            top.setVisibility(View.VISIBLE);
            top.requestFocus();
        } else {
            content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            bar.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            darken(deep ? 1 : 0);
            View f = deep ? grid.getChildCount() > 0 ? ((ViewGroup) grid.getChildAt(0)).getChildAt(0) : null : row1.getChildAt(0);
            if (f != null) f.requestFocus();
            wake();
        }
    }

    void closeAll() { while (!panels.isEmpty()) close(panels.get(panels.size() - 1)); }

    void toast(String msg) { Menus.toast(this, msg); }

    // ------------------------------------------------------------------ lifecycle and keys

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        endLaunch();
        registerReceiver(tick, new IntentFilter(Intent.ACTION_TIME_TICK));
        IntentFilter pf = new IntentFilter(); pf.addAction(Intent.ACTION_PACKAGE_ADDED); pf.addAction(Intent.ACTION_PACKAGE_REMOVED); pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(packages, pf);
        scenes.load(scenes.cur());
        scene.invalidate();
        updateTime();
        ui.removeCallbacks(rotate);
        ui.postDelayed(rotate, 20000);
        weather.refresh(false);
        if (store.setupDone) updates.autoCheck();
        wake();
    }

    @Override protected void onPause() {
        resumed = false;
        stopSaver();
        try { unregisterReceiver(tick); unregisterReceiver(packages); } catch (Exception ignored) { }
        ui.removeCallbacks(rotate);
        ui.removeCallbacks(idle);
        super.onPause();
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_UI_HIDDEN) scenes.trim();   // an app is on screen: keep only the wallpaper Home shows
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (!Intent.ACTION_MAIN.equals(i.getAction()) || !store.setupDone) return;
        closeAll();
        ambient(false);
        if (deep) deep(false);
        View f = row1 != null ? row1.getChildAt(0) : null;
        if (f != null) f.requestFocus();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            wake();
            if (saver) { stopSaver(); return true; }
            if (ambient) { ambient(false); return true; }   // the first press only wakes Home up
        }
        return super.dispatchKeyEvent(e);
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        View f = getCurrentFocus();
        if (code == KeyEvent.KEYCODE_DPAD_UP && f != null && isIn(f, row1) && panels.isEmpty()) {
            bar.setAlpha(0f);
            bar.setTranslationY(-Ui.fx(36));
            bar.setVisibility(View.VISIBLE);
            bar.animate().alpha(1f).translationY(0).setDuration(420).setInterpolator(Ui.EASE).start();
            chip.requestFocus();
            return true;
        }
        if (code >= KeyEvent.KEYCODE_DPAD_UP && code <= KeyEvent.KEYCODE_DPAD_RIGHT && f != null) {
            int dir = code == KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP : code == KeyEvent.KEYCODE_DPAD_DOWN ? View.FOCUS_DOWN
                    : code == KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT : View.FOCUS_RIGHT;
            if (f.focusSearch(dir) == null) Ui.sound("edge");
        }
        if (code == KeyEvent.KEYCODE_MENU) { if (panels.isEmpty() && !store.me().kids) Panels.controlCenter(this); return true; }   // as M did in PureTV 2
        return super.onKeyDown(code, e);
    }

    @Override public void onBackPressed() {
        Ui.sound("back");
        if (!panels.isEmpty()) { Runnable r = backs.get(backs.size() - 1); if (r != null) r.run(); else close(panels.get(panels.size() - 1)); return; }
        View f = getCurrentFocus();
        if (deep || (f != null && isIn(f, bar))) { View t = row1.getChildAt(0); if (t != null) t.requestFocus(); }
        // Home is never closed by Back
    }

    static GradientDrawable dot(int color) { GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(color); return g; }
}
