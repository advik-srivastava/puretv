package tv.pure.home;

import android.graphics.Bitmap;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;


/** First run: welcome, your profile, the glass, a wallpaper, the Home button, done. Over the blurred wallpaper. */
final class Setup {
    private static final String[] HELLOS = { "Welcome", "नमस्ते", "Hola", "Bonjour", "こんにちは", "Ciao", "Hallo" };
    private final MainActivity a;
    private final FrameLayout holder;
    private final LinearLayout body;
    private final LinearLayout dots;
    private int step;
    private int hello;
    private TextView helloView;
    private final Runnable helloTick = new Runnable() {
        @Override public void run() {
            if (helloView == null || step != 0) return;
            hello = (hello + 1) % HELLOS.length;
            helloView.animate().alpha(0).setDuration(350).withEndAction(() -> { helloView.setText(HELLOS[hello]); helloView.animate().alpha(1).setDuration(450).start(); }).start();
            a.ui.postDelayed(this, 2600);
        }
    };

    Setup(MainActivity a) {
        this.a = a;
        holder = new FrameLayout(a);
        holder.setClickable(true);
        ImageView bg = new ImageView(a);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Scenes.Scene s = a.scenes.cur();
        Bitmap g;
        synchronized (s) { g = s.glass; }
        bg.setImageBitmap(g);
        ColorMatrix cm = new ColorMatrix(); cm.setScale(.36f, .36f, .38f, 1f);
        bg.setColorFilter(new ColorMatrixColorFilter(cm));
        holder.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setClipChildren(false);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.gravity = Gravity.CENTER;
        holder.addView(body, lp);
        dots = new LinearLayout(a);
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(-2, -2);
        dl.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        dl.bottomMargin = Ui.px(54);
        holder.addView(dots, dl);
    }

    void show() {
        a.open(holder, this::back);
        holder.animate().cancel();   // fully there from the first frame: no glimpse of Home behind it
        holder.setAlpha(1f); holder.setScaleX(1f); holder.setScaleY(1f);
        go(0);
    }

    private void back() { if (step > 0 && step < 6) go(step - 1); }

    private void go(int n) {
        step = n;
        body.animate().alpha(0).setDuration(n == 0 && body.getChildCount() == 0 ? 0 : 200).withEndAction(() -> {
            body.removeAllViews();
            build(n);
            body.setTranslationY(Ui.fx(16));
            body.animate().alpha(1).translationY(0).setDuration(420).setInterpolator(Ui.EASE).start();
        }).start();
        dots.removeAllViews();
        for (int i = 1; i <= 5 && n > 0 && n < 6; i++) {
            View d = new View(a);
            d.setBackground(MainActivity.dot(i == n ? 0xFFFFFFFF : 0x59FFFFFF));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(i == n ? 10 : 8), Ui.px(i == n ? 10 : 8));
            lp.leftMargin = lp.rightMargin = Ui.px(6);
            lp.gravity = Gravity.CENTER_VERTICAL;
            dots.addView(d, lp);
        }
    }

    private void build(int n) {
        Store s = a.store;
        Store.Profile me = s.me();
        switch (n) {
            case 0:
                helloView = Ui.text(a, 150, Ui.heavy, 0xFFFFFFFF);
                helloView.setText(HELLOS[hello]);
                helloView.setGravity(Gravity.CENTER);
                body.addView(helloView, new LinearLayout.LayoutParams(-1, Ui.px(190)));
                line("Welcome to PureTV for your TV.");
                button(new Row.Spec("start", "Get Started").sub("Set up your Home screen").on(() -> go(1)), 24);
                madeBy();
                a.ui.removeCallbacks(helloTick);
                a.ui.postDelayed(helloTick, 2600);
                break;
            case 1: {
                title("Create Your Profile");
                line("Your name and avatar appear on the Home screen and in Settings.");
                button(new Row.Spec("name", "Name").value(me.name).go().on(() -> Menus.editName(a, () -> go(1))), 24);
                LinearLayout row = new LinearLayout(a);
                row.setPadding(0, Ui.px(22), 0, Ui.px(8));
                row.setClipChildren(false);
                for (int i = 0; i < Store.EMOJIS.length; i++) {
                    final int k = i;
                    TextView e = Ui.text(a, 38, null, 0xFF000000);
                    e.setText(Store.EMOJIS[i]);
                    e.setGravity(Gravity.CENTER);
                    e.setFocusable(true);
                    GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL); g.setColor(Ui.parse(Store.EMOJI_BG[i], 0xFF888888));
                    if (Store.EMOJIS[i].equals(me.emoji)) g.setStroke(Ui.px(4), 0xFFFFFFFF);
                    e.setBackground(g);
                    e.setOnFocusChangeListener((v, f) -> v.animate().scaleX(f ? 1.22f : 1).scaleY(f ? 1.22f : 1).setDuration(260).setInterpolator(Ui.LIFT).start());
                    e.setOnClickListener(v -> { Ui.sound("ok"); me.emoji = Store.EMOJIS[k]; me.bg = Store.EMOJI_BG[k]; s.save(); a.profileChanged(); go(1); });
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(76), Ui.px(76));
                    lp.leftMargin = lp.rightMargin = Ui.px(10);
                    row.addView(e, lp);
                }
                body.addView(row);
                button(new Row.Spec("next", "Continue").on(() -> go(2)), 14);
                break;
            }
            case 2: {
                title("Look & Style");
                line("Choose the glass for menus, panels and Control Center.");
                String[][] g = { { "liquid", "Liquid Glass", "Clear glass that shows the wallpaper through" }, { "frosted", "Frosted Glass", "Softer, brighter panels" }, { "dark", "Dark", "Deep, dark panels for a dark room" } };
                for (String[] x : g) button(new Row.Spec(x[0], x[1]).sub(x[2]).checked(s.glass.equals(x[0])).on(() -> { s.glass = x[0]; Glass.style = x[0]; s.save(); a.buildHome(); go(2); }), 12);
                button(new Row.Spec("next", "Continue").on(() -> go(3)), 22);
                break;
            }
            case 3: {
                title("Weather");
                line("A small weather reading on the Home screen, with a five-day forecast.");
                button(new Row.Spec("wa", "Use My Location").sub("Found from your internet connection").checked("auto".equals(s.weather))
                        .on(() -> { s.weather = "auto"; s.save(); a.weather.refresh(true); go(3); }), 22);
                button(new Row.Spec("wc", "Choose a City…").sub("city".equals(s.weather) ? Store.CITIES[s.city] + " · press OK for the next city" : "Pick from a list")
                        .checked("city".equals(s.weather)).on(() -> {
                            if ("city".equals(s.weather)) s.city = (s.city + 1) % Store.CITIES.length;
                            s.weather = "city"; s.save(); a.weather.refresh(true); go(3);
                        }), 12);
                button(new Row.Spec("wo", "Don't Show Weather").sub("Hide the weather on the Home screen").checked("off".equals(s.weather))
                        .on(() -> { s.weather = "off"; s.save(); a.updateTime(); go(3); }), 12);
                button(new Row.Spec("next", "Continue").on(() -> go(4)), 22);
                break;
            }
            case 4: {
                title("Choose a Wallpaper");
                line("Your clock sits behind the scenery. Wallpapers change every 20 seconds.");
                LinearLayout row = new LinearLayout(a);
                row.setClipChildren(false);
                row.setPadding(0, Ui.px(26), 0, 0);
                for (int i = 0; i < Scenes.ALL.length; i++) {
                    final int k = i;
                    LinearLayout col = new LinearLayout(a);
                    col.setOrientation(LinearLayout.VERTICAL);
                    col.setGravity(Gravity.CENTER_HORIZONTAL);
                    col.setFocusable(true);
                    col.setClipChildren(false);
                    ImageView iv = new ImageView(a);
                    iv.setImageBitmap(a.scenes.thumb(Scenes.ALL[i]));
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setClipToOutline(true);
                    iv.setBackground(Ui.round(0xFF000000, 24));
                    col.addView(iv, new LinearLayout.LayoutParams(Ui.px(256), Ui.px(144)));
                    TextView name = Ui.text(a, 20, Ui.semibold, 0xCCFFFFFF);
                    name.setText(Scenes.ALL[i].name);
                    name.setPadding(0, Ui.px(16), 0, 0);
                    col.addView(name);
                    col.setOnFocusChangeListener((v, f) -> {
                        v.animate().scaleX(f ? 1.12f : 1).scaleY(f ? 1.12f : 1).setDuration(320).setInterpolator(Ui.LIFT).start();
                        iv.setElevation(f ? Ui.fx(20) : 0);
                        name.setTextColor(f ? 0xFFFFFFFF : 0xCCFFFFFF);
                    });
                    col.setOnClickListener(v -> { Ui.sound("ok"); a.scenes.show(k, false); s.scene = k; s.save(); a.placeDate(); go(5); });
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                    lp.leftMargin = lp.rightMargin = Ui.px(13);
                    row.addView(col, lp);
                }
                body.addView(row);
                row.post(() -> row.getChildAt(Math.max(0, a.scenes.index)).requestFocus());
                return;
            }
            case 5: {
                title("Home Screen & Remote");
                boolean home = Tv.isHome(a), helper = HomeHelper.enabled(a);
                if (home) line("PureTV is this TV's Home screen. Google TV's home is switched off (not deleted), so the TV runs one Home screen and stays fast.");
                else if (helper) line("The Remote Helper is on: the Home button opens PureTV.");
                else {
                    line("To make the Home button open PureTV, turn on the Remote Helper: System › Accessibility › PureTV Remote Helper. "
                            + "For the fastest TV, let the PureTV agent on your computer switch Google TV's home off instead.");
                    button(new Row.Spec("helper", "Open Accessibility").on(() -> { HomeHelper.setOverride(a, true); Tv.settings(a, "access"); }), 22);
                }
                button(new Row.Spec("next", "Continue").on(() -> go(6)), home || helper ? 26 : 12);
                break;
            }
            default: {
                TextView ok = Ui.text(a, 64, Ui.heavy, 0xFF30D158);
                ok.setText("✓");
                ok.setGravity(Gravity.CENTER);
                body.addView(ok);
                title("You're All Set");
                line("Change any of this in Settings, or run setup again from there.");
                button(new Row.Spec("done", "Go to Home Screen").on(this::finish), 24);
                madeBy();
            }
        }
        body.post(() -> {
            for (int i = 0; i < body.getChildCount(); i++) if (body.getChildAt(i).isFocusable()) { body.getChildAt(i).requestFocus(); return; }
        });
    }

    private void finish() {
        a.store.setupDone = true;
        a.store.save();
        a.ui.removeCallbacks(helloTick);
        a.close(holder);
    }

    private void title(String t) {
        TextView v = Ui.text(a, 64, Ui.heavy, 0xFFFFFFFF);
        v.setText(t);
        v.setGravity(Gravity.CENTER);
        body.addView(v, new LinearLayout.LayoutParams(-1, -2));
    }

    private void line(String t) {
        TextView v = Ui.text(a, 26, Ui.regular, 0xCCFFFFFF);
        v.setText(t);
        v.setGravity(Gravity.CENTER);
        v.setLineSpacing(0, 1.3f);
        v.setPadding(Ui.px(300), Ui.px(14), Ui.px(300), 0);
        body.addView(v, new LinearLayout.LayoutParams(-1, -2));
    }

    private void button(Row.Spec s, int topMargin) {
        Row r = new Row(a, s, 620);
        if (s.value == null && s.iconName == null && !s.check && !s.chevron) r.center();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(620), -2);
        lp.topMargin = Ui.px(topMargin);
        body.addView(r, lp);
    }

    private void madeBy() {
        TextView m = Ui.text(a, 20, Ui.regular, 0x99FFFFFF);
        m.setText("Made with ❤ by Crazified Studios");
        m.setGravity(Gravity.CENTER);
        m.setPadding(0, Ui.px(90), 0, 0);
        body.addView(m, new LinearLayout.LayoutParams(-1, -2));
    }
}
