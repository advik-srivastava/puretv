package tv.pure.home;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Control Center, the five-day weather and Recent Apps, as in PureTV 2, drawn natively. */
final class Panels {
    private Panels() { }

    static final String[] SLEEP = { "Off", "15 minutes", "30 minutes", "1 hour", "2 hours" };
    static final int[] SLEEP_MIN = { 0, 15, 30, 60, 120 };

    static int memUsedPct(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.totalMem > 0 ? (int) Math.round(100.0 * (mi.totalMem - mi.availMem) / mi.totalMem) : 0;
    }

    static String memLine(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return String.format(Locale.US, "RAM %.1f GB used of %.1f GB", (mi.totalMem - mi.availMem) / 1073741824.0, mi.totalMem / 1073741824.0);
    }

    private static View bar(MainActivity a, int pct, float w) {
        FrameLayout b = new FrameLayout(a);
        b.setBackground(Ui.round(0x38FFFFFF, 9));
        View fill = new View(a);
        fill.setBackground(Ui.round(0xFFFFFFFF, 9));
        b.addView(fill, new FrameLayout.LayoutParams(Ui.px(w * pct / 100f), -1));
        return b;
    }

    // ------------------------------------------------------------------ Control Center

    static void controlCenter(MainActivity a) {
        Store s = a.store;
        FrameLayout holder = new FrameLayout(a);
        holder.setClickable(true);
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipChildren(false);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.px(640), -2);
        plp.gravity = Gravity.TOP | Gravity.RIGHT;
        plp.rightMargin = Ui.px(80); plp.topMargin = Ui.px(60);
        holder.addView(panel, plp);

        // header: time, the Control Center mark, your avatar
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setClipChildren(false);
        TextView time = Ui.text(a, 32, Ui.semibold, 0xFFFFFFFF);
        time.setText(DateFormat.getTimeFormat(a).format(new Date()));
        time.setShadowLayer(Ui.fx(10), 0, Ui.fx(2), 0x4D000000);
        head.addView(time, new LinearLayout.LayoutParams(0, -2, 1f));
        View sw = new View(a) { @Override protected void onDraw(android.graphics.Canvas c) { Icons.draw(c, "cc", getWidth() * .26f, getHeight() * .26f, getWidth() * .48f, 0xFF111111); } };
        GradientDrawable white = new GradientDrawable(); white.setShape(GradientDrawable.OVAL); white.setColor(0xFFFFFFFF);
        sw.setBackground(white); sw.setElevation(Ui.fx(10));
        LinearLayout.LayoutParams swl = new LinearLayout.LayoutParams(Ui.px(74), Ui.px(74)); swl.rightMargin = Ui.px(22);
        head.addView(sw, swl);
        Store.Profile me = s.me();
        TextView av = Ui.text(a, 42, null, 0xFF000000);
        av.setText(me.emoji); av.setGravity(Gravity.CENTER); av.setFocusable(true);
        GradientDrawable ab = new GradientDrawable(); ab.setShape(GradientDrawable.OVAL); ab.setColor(Ui.parse(me.bg, 0xFF8E8E93));
        av.setBackground(ab);
        av.setOnFocusChangeListener((v, f) -> { v.animate().scaleX(f ? 1.18f : 1).scaleY(f ? 1.18f : 1).setDuration(300).setInterpolator(Ui.LIFT).start();
            GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(Ui.parse(me.bg, 0xFF8E8E93)); if (f) g.setStroke(Ui.px(4), 0xFFFFFFFF); v.setBackground(g); });
        av.setOnClickListener(v -> { Ui.sound("ok"); a.closeAll(); Menus.profiles(a); });
        head.addView(av, new LinearLayout.LayoutParams(Ui.px(74), Ui.px(74)));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2); hl.bottomMargin = Ui.px(30);
        panel.addView(head, hl);

        // the grid: Power (two rows tall) beside Wi-Fi and Do Not Disturb, then two rows of pills
        Btn power = new Btn(a, 64, 1.06f);
        power.setOrientation(LinearLayout.VERTICAL);
        power.setGravity(Gravity.CENTER);
        power.icon(a, "power", 120, 0);
        TextView pt = power.text(a, "Power Off", 27, false);
        pt.setPadding(0, Ui.px(20), 0, 0);
        power.addView(pt);
        power.action = () -> { a.closeAll(); if (!HomeHelper.sleep()) a.toast("Press the power button on the remote"); };

        Btn wifi = pill(a, "wifi", "Wi-Fi", Tv.wifiName(a), () -> { a.closeAll(); Tv.settings(a, "network"); });
        Btn dnd = pill(a, "moon", "Do Not Disturb", null, null);
        dnd.setOn(s.dnd);
        dnd.action = () -> { s.dnd = !s.dnd; s.save(); dnd.setOn(s.dnd); a.toast(s.dnd ? "Do Not Disturb: no update prompts or sounds" : "Do Not Disturb is off"); };
        Btn audio = pill(a, "audio", "Audio", "TV Speakers", () -> { a.closeAll(); Tv.settings(a, "sound"); });
        Btn sleep = pill(a, "sleep", "Sleep Timer", SLEEP[a.sleepIx], null);
        sleep.setOn(a.sleepIx > 0);
        sleep.action = () -> {
            a.sleepIx = (a.sleepIx + 1) % SLEEP.length;
            a.setSleep(SLEEP_MIN[a.sleepIx]);
            sleep.setOn(a.sleepIx > 0);
            ((TextView) ((LinearLayout) sleep.getChildAt(1)).getChildAt(1)).setText(SLEEP[a.sleepIx]);
        };
        Btn bt = pill(a, "bluetooth", "Bluetooth", "Remotes", () -> { a.closeAll(); Tv.settings(a, "bluetooth"); });
        int pct = memUsedPct(a);
        Btn mem = pill(a, "memory", "Memory", pct + "% used", () -> { a.closeAll(); recents(a); });
        ((LinearLayout) mem.getChildAt(1)).addView(bar(a, pct, 150), new LinearLayout.LayoutParams(Ui.px(150), Ui.px(8)));

        LinearLayout grid = new LinearLayout(a);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setClipChildren(false);
        LinearLayout colL = col(a), colR = col(a);
        colL.addView(power, cell(300, 302, 0));
        colR.addView(wifi, cell(300, 140, 0));
        colR.addView(dnd, cell(300, 140, 22));
        grid.addView(colL);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2); rl.leftMargin = Ui.px(40);
        grid.addView(colR, rl);
        panel.addView(grid);
        LinearLayout row2 = new LinearLayout(a), row3 = new LinearLayout(a);
        for (LinearLayout r : new LinearLayout[] { row2, row3 }) r.setClipChildren(false);
        row2.addView(audio, cell(300, 128, 0)); row2.addView(sleep, cellL(300, 128, 40));
        row3.addView(bt, cell(300, 128, 0)); row3.addView(mem, cellL(300, 128, 40));
        LinearLayout.LayoutParams r2 = new LinearLayout.LayoutParams(-2, -2); r2.topMargin = Ui.px(22);
        panel.addView(row2, r2);
        LinearLayout.LayoutParams r3 = new LinearLayout.LayoutParams(-2, -2); r3.topMargin = Ui.px(22);
        panel.addView(row3, r3);

        // round buttons: inputs, screen saver, UI sounds, accessibility, search
        LinearLayout round = new LinearLayout(a);
        round.setClipChildren(false);
        String[][] rb = { { "inputs", "Inputs" }, { "saver", "Screen Saver" }, { "sound", "UI Sounds" }, { "access", "Accessibility" }, { "search", "Search" } };
        for (int i = 0; i < rb.length; i++) {
            final String k = rb[i][0];
            Btn b = new Btn(a, 64, 1.1f);
            b.setGravity(Gravity.CENTER);
            b.icon(a, k, 54, 0);
            b.setContentDescription(rb[i][1]);
            if (k.equals("sound")) b.setOn(s.sounds);
            b.action = () -> {
                switch (k) {
                    case "inputs": a.closeAll(); Menus.inputs(a); break;
                    case "saver": a.closeAll(); a.startSaver(); break;
                    case "sound": s.sounds = !s.sounds; s.save(); b.setOn(s.sounds); a.toast("UI Sounds: " + (s.sounds ? "On" : "Off")); break;
                    case "access": a.closeAll(); Tv.settings(a, "access"); break;
                    case "search": a.closeAll(); Tv.search(a); break;
                }
            };
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(110), Ui.px(110));
            lp.leftMargin = i == 0 ? 0 : Ui.px(22.5f);
            round.addView(b, lp);
        }
        LinearLayout.LayoutParams rr = new LinearLayout.LayoutParams(-2, -2); rr.topMargin = Ui.px(22);
        panel.addView(round, rr);

        // weather: now, today's high and low, tomorrow
        Weather w = a.weather;
        if (w.has()) {
            Btn wx = new Btn(a, 64, 1.04f);
            wx.setPadding(Ui.px(30), 0, Ui.px(34), 0);
            wx.icon(a, Weather.iconName(w.code), 64, 22);
            int[] d0 = w.days.isEmpty() ? null : w.days.get(0);
            wx.twoLines(a, w.temp + "°", w.now() + (d0 != null ? " · H " + d0[0] + "° L " + d0[1] + "°" : ""), 30);
            if (w.days.size() > 1) {
                LinearLayout right = new LinearLayout(a);
                right.setOrientation(LinearLayout.VERTICAL);
                right.setGravity(Gravity.RIGHT);
                right.addView(wx.text(a, w.days.get(1)[0] + "°", 26, false));
                right.addView(wx.text(a, "Tomorrow", 21, true));
                wx.addView(right);
            }
            wx.action = () -> { a.closeAll(); weather(a); };
            LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(Ui.px(640), Ui.px(128)); wl.topMargin = Ui.px(22);
            panel.addView(wx, wl);
        }

        a.open(holder, null);
        a.darken(.25f);
        holder.post(power::requestFocus);
    }

    private static LinearLayout col(MainActivity a) { LinearLayout c = new LinearLayout(a); c.setOrientation(LinearLayout.VERTICAL); c.setClipChildren(false); return c; }
    private static LinearLayout.LayoutParams cell(float w, float h, float top) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(w), Ui.px(h)); lp.topMargin = Ui.px(top); return lp; }
    private static LinearLayout.LayoutParams cellL(float w, float h, float left) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(w), Ui.px(h)); lp.leftMargin = Ui.px(left); return lp; }

    private static Btn pill(MainActivity a, String icon, String title, String sub, Runnable action) {
        Btn b = new Btn(a, 64, 1.06f);
        b.setPadding(Ui.px(22), 0, Ui.px(24), 0);
        b.icon(a, icon, 40, 18);
        b.twoLines(a, title, sub, 25);
        b.action = action;
        return b;
    }

    // ------------------------------------------------------------------ the weather, five days ahead

    static void weather(MainActivity a) {
        Weather w = a.weather;
        if (!w.has()) { a.toast("Weather is off. Settings › Weather turns it on"); return; }
        FrameLayout holder = new FrameLayout(a);
        holder.setClickable(true);
        holder.setBackgroundColor(0x59080A10);
        LinearLayout now = new LinearLayout(a);
        now.setOrientation(LinearLayout.VERTICAL);
        Btn.IconView ic = new Btn.IconView(a, Weather.iconName(w.code));
        now.addView(ic, new LinearLayout.LayoutParams(Ui.px(150), Ui.px(150)));
        TextView big = Ui.text(a, 220, Ui.regular, 0xFFFFFFFF);
        big.setText(w.temp + "°");
        big.setLetterSpacing(-.04f);
        now.addView(big);
        TextView word = Ui.text(a, 36, Ui.semibold, 0xFFFFFFFF);
        word.setText(w.now());
        now.addView(word);
        if (!w.days.isEmpty()) {
            TextView hl = Ui.text(a, 26, Ui.regular, 0xB3FFFFFF);
            hl.setText("High " + w.days.get(0)[0] + "° · Low " + w.days.get(0)[1] + "°");
            hl.setPadding(0, Ui.px(8), 0, 0);
            now.addView(hl);
        }
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2);
        nl.leftMargin = Ui.px(120); nl.topMargin = Ui.px(150);
        holder.addView(now, nl);
        LinearLayout row = new LinearLayout(a);
        row.setClipChildren(false);
        for (int i = 0; i < w.days.size(); i++) {
            int[] d = w.days.get(i);
            Btn card = new Btn(a, 44, 1.08f);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            TextView day = card.text(a, w.dayName.get(i), 26, false);
            card.addView(day);
            Btn.IconView di = card.icon(a, Weather.iconName(d[2]), 72, 0);
            ((LinearLayout.LayoutParams) di.getLayoutParams()).topMargin = Ui.px(20);
            card.removeView(di); card.addView(di);
            TextView hi = card.text(a, d[0] + "°", 30, false); hi.setPadding(0, Ui.px(20), 0, 0); card.addView(hi);
            TextView lo = card.text(a, d[1] + "°", 24, true); lo.setPadding(0, Ui.px(6), 0, 0); card.addView(lo);
            card.action = () -> a.close(holder);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(200), Ui.px(340));
            lp.leftMargin = i == 0 ? 0 : Ui.px(26);
            row.addView(card, lp);
        }
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(-2, -2);
        rl.gravity = Gravity.TOP | Gravity.RIGHT; rl.rightMargin = Ui.px(110); rl.topMargin = Ui.px(210);
        holder.addView(row, rl);
        a.open(holder, null);
        a.darken(.7f);
        holder.post(() -> { if (row.getChildCount() > 0) row.getChildAt(0).requestFocus(); });
    }

    // ------------------------------------------------------------------ Recent Apps

    static void recents(MainActivity a) {
        FrameLayout holder = new FrameLayout(a);
        holder.setClickable(true);
        holder.setBackgroundColor(0x73080810);
        TextView h = Ui.text(a, 40, Ui.heavy, 0xFFFFFFFF);
        h.setText("Recent Apps");
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2); hl.leftMargin = Ui.px(90); hl.topMargin = Ui.px(64);
        holder.addView(h, hl);
        Btn ram = new Btn(a, 40, 1f);
        ram.setFocusable(false);
        ram.setPadding(Ui.px(30), Ui.px(16), Ui.px(30), Ui.px(16));
        ram.addView(ram.text(a, memLine(a), 22, false));
        View bar = bar(a, memUsedPct(a), 170);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.px(170), Ui.px(8)); bl.leftMargin = Ui.px(22);
        ram.addView(bar, bl);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(-2, -2); rl.gravity = Gravity.TOP | Gravity.RIGHT; rl.rightMargin = Ui.px(90); rl.topMargin = Ui.px(58);
        holder.addView(ram, rl);

        LinearLayout track = new LinearLayout(a);
        track.setClipChildren(false);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, Ui.px(470)); tl.topMargin = Ui.px(290);
        holder.addView(track, tl);
        List<String> list = new ArrayList<>();
        for (String p : a.store.recents) if (a.apps.has(p)) list.add(p);
        Btn closeAll = new Btn(a, 50, 1.08f);
        closeAll.setPadding(Ui.px(50), Ui.px(18), Ui.px(50), Ui.px(18));
        closeAll.addView(closeAll.text(a, "Close All", 24, false));
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-2, -2); cl.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL; cl.bottomMargin = Ui.px(60);
        holder.addView(closeAll, cl);
        TextView tip = Ui.text(a, 22, Ui.regular, 0xB3FFFFFF);
        tip.setText(list.isEmpty() ? "No recent apps" : "Press Up on an app to close it");
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-2, -2); pl.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL; pl.bottomMargin = Ui.px(list.isEmpty() ? 470 : 150);
        holder.addView(tip, pl);

        Runnable[] center = new Runnable[1];
        for (String pkg : list) {
            LinearLayout card = new LinearLayout(a);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setFocusable(true);
            card.setClipChildren(false);
            Apps.App ap = a.apps.get(pkg);
            TextView lab = Ui.text(a, 24, Ui.semibold, 0xFFFFFFFF);
            lab.setText(ap.name);
            lab.setAlpha(0f);
            lab.setPadding(Ui.px(6), 0, 0, Ui.px(18));
            card.addView(lab);
            ImageView shot = new ImageView(a);
            Bitmap art = a.apps.art(pkg);
            shot.setImageBitmap(art);
            shot.setScaleType(ImageView.ScaleType.CENTER_CROP);
            shot.setClipToOutline(true);
            shot.setBackground(Ui.round(0xFF222222, 44));
            shot.setElevation(Ui.fx(24));
            card.addView(shot, new LinearLayout.LayoutParams(Ui.px(640), Ui.px(380)));
            card.setScaleX(.82f); card.setScaleY(.82f); card.setAlpha(.6f);
            card.setOnFocusChangeListener((v, f) -> {
                v.animate().scaleX(f ? 1 : .82f).scaleY(f ? 1 : .82f).alpha(f ? 1 : .6f).setDuration(450).setInterpolator(Ui.EASE).start();
                lab.animate().alpha(f ? 1 : 0).setDuration(250).start();
                if (f) center[0].run();
            });
            card.setOnClickListener(v -> { Ui.sound("ok"); a.closeAll(); a.launchApp(pkg, null); });
            card.setOnKeyListener((v, code, e) -> {
                if (code != KeyEvent.KEYCODE_DPAD_UP || e.getAction() != KeyEvent.ACTION_DOWN) return false;
                Ui.sound("back");
                a.closeApp(pkg);
                int i = track.indexOfChild(card);
                View next = track.getChildAt(i + 1) != null ? track.getChildAt(i + 1) : track.getChildAt(i - 1);
                card.animate().translationY(-Ui.fx(520)).alpha(0).setDuration(420).setInterpolator(Ui.EASE).withEndAction(() -> {
                    track.removeView(card);
                    if (track.getChildCount() == 0) { tip.setText("No recent apps"); closeAll.requestFocus(); }
                }).start();
                if (next != null) next.requestFocus(); else closeAll.requestFocus();
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(640), -2);
            lp.rightMargin = Ui.px(60);
            track.addView(card, lp);
        }
        center[0] = () -> {   // the focused card slides to the middle
            View f = a.getCurrentFocus();
            if (f == null || f.getParent() != track) return;
            track.animate().translationX(Ui.fx(960) - (f.getLeft() + Ui.fx(320))).setDuration(600).setInterpolator(Ui.EASE).start();
        };
        closeAll.action = () -> {
            int n = track.getChildCount();
            for (int i = 0; i < n; i++) {
                View c = track.getChildAt(i);
                c.animate().translationY(-Ui.fx(520)).alpha(0).setStartDelay(i * 70L).setDuration(420).start();
            }
            for (String p : list) a.closeApp(p);
            a.ui.postDelayed(() -> { a.close(holder); if (n > 0) a.toast("Closed " + n + " app" + (n > 1 ? "s" : "")); }, 420 + n * 70L);
        };
        a.open(holder, null);
        a.darken(.8f);
        holder.post(() -> { if (track.getChildCount() > 0) track.getChildAt(0).requestFocus(); else closeAll.requestFocus(); });
    }

    static String clock(Context c) { return new SimpleDateFormat(DateFormat.is24HourFormat(c) ? "H:mm" : "h:mm a", Locale.getDefault()).format(new Date()); }
}
