package tv.pure.home;

import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Glass panels in the middle of the screen: app menu, profiles, inputs, parent PIN, questions, and toasts. */
final class Menus {
    private Menus() { }

    /** A centred glass card with a title, an optional line of text, and rows. Returns the full-screen holder. */
    static FrameLayout card(MainActivity a, String title, String body, List<Row.Spec> rows, float widthDesign) {
        FrameLayout holder = new FrameLayout(a);
        holder.setClickable(true);
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.px(30), Ui.px(28), Ui.px(30), Ui.px(26));
        card.setBackground(new Glass(card, a.scenes, 40));
        card.setElevation(Ui.fx(24));
        card.setClipChildren(false);
        if (title != null) {
            TextView t = Ui.text(a, 32, Ui.heavy, 0xFFFFFFFF);
            t.setText(title);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, 0, 0, Ui.px(body != null ? 10 : 18));
            card.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
        if (body != null) {
            TextView b = Ui.text(a, 22, Ui.regular, 0xCCFFFFFF);
            b.setText(body);
            b.setGravity(Gravity.CENTER);
            b.setLineSpacing(0, 1.3f);
            b.setPadding(0, 0, 0, Ui.px(20));
            card.addView(b, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        for (Row.Spec s : rows) {
            Row r = new Row(a, s, widthDesign);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.px(10);
            list.addView(r, lp);
        }
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipChildren(false);
        sv.addView(list);
        card.addView(sv, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.px(widthDesign), -2);
        lp.gravity = Gravity.CENTER;
        holder.addView(card, lp);
        holder.post(() -> { if (list.getChildCount() > 0) list.getChildAt(0).requestFocus(); });
        return holder;
    }

    static FrameLayout show(MainActivity a, String title, String body, List<Row.Spec> rows) {
        FrameLayout h = card(a, title, body, rows, 640);
        a.open(h, null);
        a.darken(.7f);
        return h;
    }

    static void toast(MainActivity a, String msg) {
        TextView t = Ui.text(a, 24, Ui.semibold, 0xFFFFFFFF);
        t.setText(msg);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.px(34), Ui.px(18), Ui.px(34), Ui.px(18));
        t.setMaxWidth(Ui.px(1100));
        t.setBackground(Ui.round(0xE62C2C30, 40));
        t.setElevation(Ui.fx(20));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = Ui.px(70);
        a.root.addView(t, lp);
        t.setAlpha(0); t.setTranslationY(Ui.fx(20));
        t.animate().alpha(1).translationY(0).setDuration(300).setInterpolator(Ui.EASE).start();
        a.ui.postDelayed(() -> t.animate().alpha(0).setDuration(300).withEndAction(() -> a.root.removeView(t)).start(), 3600);
    }

    /** Hold OK on an app: move it, hide it, or open its page in the TV's Settings. */
    static void app(MainActivity a, Tile t) {
        Store.Profile me = a.store.me();
        String pkg = t.pkg;
        Apps.App app = a.apps.get(pkg);
        if (app == null) return;
        Ui.sound("ok");
        t.held = true; t.invalidate();
        List<String> top = new ArrayList<>();
        for (Apps.App x : a.apps.top(me, a.store.dock)) top.add(x.pkg);
        boolean inTop = top.contains(pkg);
        List<Row.Spec> rows = new ArrayList<>();
        final FrameLayout[] h = new FrameLayout[1];
        Runnable done = () -> { t.held = false; a.close(h[0]); };
        if (inTop) {
            int i = top.indexOf(pkg);
            if (i > 0) rows.add(new Row.Spec("left", "Move Left").on(() -> { move(me, top, pkg, -1); a.store.save(); done.run(); a.buildHome(); }));
            if (i < top.size() - 1) rows.add(new Row.Spec("right", "Move Right").on(() -> { move(me, top, pkg, 1); a.store.save(); done.run(); a.buildHome(); }));
            rows.add(new Row.Spec("down", "Move to All Apps").on(() -> {
                top.remove(pkg); me.top.clear(); me.top.addAll(top); me.top.add("-" + pkg); a.store.save(); done.run(); a.buildHome();
                a.toast(app.name + " moved to All Apps");
            }));
        } else {
            rows.add(new Row.Spec("up", "Move to Top Row").on(() -> {
                me.top.remove("-" + pkg);
                top.add(0, pkg);
                if (top.size() > a.store.dock) top.remove(top.size() - 1);
                me.top.clear(); me.top.addAll(top); a.store.save(); done.run(); a.buildHome();
                a.toast(app.name + " is in your top row");
            }));
        }
        rows.add(new Row.Spec("hide", "Hide from Home").on(() -> {
            me.hidden.add(pkg); me.top.remove(pkg); a.store.save(); done.run(); a.buildHome();
            a.toast(app.name + " is hidden. Settings › Home Screen brings it back");
        }));
        rows.add(new Row.Spec("info", "App Info").sub("The TV's own page for this app").on(() -> { done.run(); Tv.settings(a, "appinfo:" + pkg); }));
        h[0] = card(a, app.name, null, rows, 560);
        a.open(h[0], done);
    }

    private static void move(Store.Profile me, List<String> top, String pkg, int by) {
        int i = top.indexOf(pkg), j = Math.max(0, Math.min(top.size() - 1, i + by));
        top.remove(i); top.add(j, pkg);
        List<String> minus = new ArrayList<>();
        for (String s : me.top) if (s.startsWith("-")) minus.add(s);
        me.top.clear(); me.top.addAll(top); me.top.addAll(minus);
    }

    static void profiles(MainActivity a) {
        List<Row.Spec> rows = new ArrayList<>();
        final FrameLayout[] h = new FrameLayout[1];
        for (int i = 0; i < a.store.profiles.size(); i++) {
            final int n = i;
            Store.Profile p = a.store.profiles.get(i);
            rows.add(new Row.Spec("p" + i, p.name).emoji(p.emoji, p.bg).sub(p.kids ? "Kids: approved apps only" : null)
                    .checked(i == a.store.current).on(() -> {
                        Runnable go = () -> { a.store.current = n; a.store.save(); a.closeAll(); a.profileChanged(); a.toast("Switched to " + p.name); };
                        Store.Profile was = a.store.me();
                        if (was.kids && !p.kids) pin(a, false, go); else go.run();
                    }));
        }
        h[0] = Menus.show(a, "Who's Watching?", null, rows);
    }

    static void inputs(MainActivity a) {
        List<Row.Spec> rows = new ArrayList<>();
        for (Tv.Input in : Tv.inputs(a)) rows.add(new Row.Spec(in.id, in.name).icon("inputs", 0xFF4B5563).on(() -> { a.closeAll(); Tv.input(a, in); }));
        if (rows.isEmpty()) rows.add(new Row.Spec("all", "Open the TV's Inputs").on(() -> { a.closeAll(); Tv.settings(a, ""); }));
        show(a, "Inputs", null, rows);
    }

    /** A question with buttons. */
    static FrameLayout ask(MainActivity a, String title, String body, String yes, Runnable onYes, String no, Runnable onNo) {
        List<Row.Spec> rows = new ArrayList<>();
        final FrameLayout[] h = new FrameLayout[1];
        rows.add(new Row.Spec("yes", yes).on(() -> { a.close(h[0]); if (onYes != null) onYes.run(); }));
        if (no != null) rows.add(new Row.Spec("no", no).on(() -> { a.close(h[0]); if (onNo != null) onNo.run(); }));
        h[0] = show(a, title, body, rows);
        for (int i = 0; i < 2 && i < rows.size(); i++) { /* centred buttons read better */ }
        return h[0];
    }

    /** The parent PIN pad. With setting, the four numbers typed become the new PIN. */
    static void pin(MainActivity a, boolean setting, Runnable ok) {
        FrameLayout holder = new FrameLayout(a);
        holder.setClickable(true);
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(Ui.px(32), Ui.px(28), Ui.px(32), Ui.px(24));
        card.setBackground(new Glass(card, a.scenes, 40));
        TextView t = Ui.text(a, 30, Ui.heavy, 0xFFFFFFFF);
        t.setText(setting ? "New Parent PIN" : "Parent PIN");
        card.addView(t);
        LinearLayout dots = new LinearLayout(a);
        dots.setPadding(0, Ui.px(14), 0, Ui.px(20));
        final View[] d = new View[4];
        for (int i = 0; i < 4; i++) {
            d[i] = new View(a);
            GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setStroke(Ui.px(2), 0xB3FFFFFF);
            d[i].setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(18), Ui.px(18));
            lp.leftMargin = lp.rightMargin = Ui.px(9);
            dots.addView(d[i], lp);
        }
        card.addView(dots);
        GridLayout pad = new GridLayout(a);
        pad.setColumnCount(3);
        final StringBuilder typed = new StringBuilder();
        String[] keys = { "1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫" };
        for (String k : keys) {
            TextView key = Ui.text(a, 30, Ui.semibold, 0xFFFFFFFF);
            key.setText(k);
            key.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = Ui.px(110); lp.height = Ui.px(72);
            lp.setMargins(Ui.px(6), Ui.px(6), Ui.px(6), Ui.px(6));
            if (k.isEmpty()) { pad.addView(new View(a), lp); continue; }
            key.setFocusable(true);
            key.setBackground(Ui.round(0x1FFFFFFF, 22));
            key.setOnFocusChangeListener((v, f) -> {
                key.setBackground(Ui.round(f ? 0xF2FFFFFF : 0x1FFFFFFF, 22));
                key.setTextColor(f ? 0xFF1C1C1E : 0xFFFFFFFF);
                v.animate().scaleX(f ? 1.08f : 1).scaleY(f ? 1.08f : 1).setDuration(200).start();
            });
            key.setOnClickListener(v -> {
                Ui.sound("ok");
                if (k.equals("⌫")) { if (typed.length() > 0) typed.setLength(typed.length() - 1); }
                else if (typed.length() < 4) typed.append(k);
                for (int i = 0; i < 4; i++) {
                    GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL);
                    if (i < typed.length()) g.setColor(0xFFFFFFFF); else g.setStroke(Ui.px(2), 0xB3FFFFFF);
                    d[i].setBackground(g);
                }
                if (typed.length() < 4) return;
                if (setting) {
                    a.store.pin = typed.toString(); a.store.save(); a.close(holder); a.toast("Parent PIN changed");
                    if (ok != null) ok.run();
                } else if (typed.toString().equals(a.store.pin)) {
                    a.close(holder); if (ok != null) ok.run();
                } else {
                    Ui.sound("edge");
                    dots.animate().translationX(Ui.fx(14)).setDuration(60).withEndAction(() -> dots.animate().translationX(-Ui.fx(14)).setDuration(80)
                            .withEndAction(() -> dots.animate().translationX(0).setDuration(60).start()).start()).start();
                    typed.setLength(0);
                    a.ui.postDelayed(() -> { for (View x : d) { GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setStroke(Ui.px(2), 0xB3FFFFFF); x.setBackground(g); } }, 300);
                }
            });
            pad.addView(key, lp);
        }
        card.addView(pad);
        TextView hint = Ui.text(a, 18, Ui.regular, 0x80FFFFFF);
        hint.setText(setting ? "Choose four numbers" : "1234".equals(a.store.pin) ? "The PIN is 1234 until you change it in Settings › Profiles" : "Enter the parent PIN");
        hint.setPadding(0, Ui.px(12), 0, 0);
        card.addView(hint);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
        lp.gravity = Gravity.CENTER;
        holder.addView(card, lp);
        a.open(holder, null);
        a.darken(.7f);
        holder.post(() -> pad.getChildAt(4).requestFocus());
    }

    /** Type a profile name with the TV keyboard. */
    static void editName(MainActivity a, Runnable after) {
        Store.Profile me = a.store.me();
        EditText in = new EditText(a);
        in.setText(me.name);
        in.setSelectAllOnFocus(true);
        in.setSingleLine(true);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        in.setImeOptions(EditorInfo.IME_ACTION_DONE);
        in.setTextColor(0xFF1C1C1E);
        in.setTypeface(Ui.semibold);
        in.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.fx(30));
        in.setPadding(Ui.px(24), Ui.px(16), Ui.px(24), Ui.px(16));
        in.setBackground(Ui.round(0xF2FFFFFF, 22));
        FrameLayout box = new FrameLayout(a);
        box.setClickable(true);
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.px(30), Ui.px(28), Ui.px(30), Ui.px(28));
        card.setBackground(new Glass(card, a.scenes, 40));
        TextView t = Ui.text(a, 30, Ui.heavy, 0xFFFFFFFF);
        t.setText("Your Name");
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, Ui.px(18));
        card.addView(t, new LinearLayout.LayoutParams(-1, -2));
        card.addView(in, new LinearLayout.LayoutParams(-1, -2));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.px(640), -2);
        lp.gravity = Gravity.CENTER;
        box.addView(card, lp);
        Runnable done = () -> {
            String v = in.getText().toString().trim();
            if (!v.isEmpty()) { me.name = v.length() > 24 ? v.substring(0, 24) : v; a.store.save(); a.profileChanged(); }
            ((InputMethodManager) a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(in.getWindowToken(), 0);
            a.close(box);
            if (after != null) after.run();
        };
        in.setOnEditorActionListener((v, id, e) -> { done.run(); return true; });
        a.open(box, done);
        in.post(() -> { in.requestFocus(); ((InputMethodManager) a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).showSoftInput(in, 0); });
    }


    static String ellipsize(String s, int n) { return TextUtils.isEmpty(s) || s.length() <= n ? s : s.substring(0, n - 1) + "…"; }
}
