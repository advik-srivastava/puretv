package tv.pure.home;

import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * A glass button, pill or circle (the top bar, Control Center, Close All): glass at rest, white with dark
 * contents when focused or switched on, and a small lift on focus. Nothing animates at rest.
 */
final class Btn extends LinearLayout {
    final Glass glass;
    private final float radius, lift;
    private boolean on;
    private final List<TextView> texts = new ArrayList<>();
    private final List<IconView> icons = new ArrayList<>();
    Runnable action;

    static final class IconView extends View {
        String name;
        int color = 0xFFFFFFFF;
        IconView(MainActivity a, String name) { super(a); this.name = name; }
        @Override protected void onDraw(Canvas c) { Icons.draw(c, name, 0, 0, getWidth(), color); }
    }

    Btn(MainActivity a, float radiusDesign, float lift) {
        super(a);
        this.radius = Ui.fx(radiusDesign);
        this.lift = lift;
        glass = new Glass(this, a.scenes, radiusDesign);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setFocusable(true);
        setClickable(true);
        setClipChildren(false);
        paint(false);
        setOnFocusChangeListener((v, f) -> {
            paint(f);
            animate().scaleX(f ? lift : 1).scaleY(f ? lift : 1).setDuration(f ? 420 : 260).setInterpolator(f ? Ui.LIFT : Ui.EASE).start();
        });
        setOnClickListener(v -> { Ui.sound("ok"); if (action != null) action.run(); });
    }

    void setOn(boolean on) { this.on = on; paint(isFocused()); }
    boolean isOn() { return on; }

    private void paint(boolean focused) {
        boolean light = focused || on;
        if (light) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(focused ? 0xF2FFFFFF : 0xE6FFFFFF);
            g.setCornerRadius(radius);
            setBackground(g);
        } else setBackground(glass);
        setElevation(focused ? Ui.fx(18) : Ui.fx(4));
        int fg = light ? 0xFF111111 : 0xFFFFFFFF;
        for (TextView t : texts) t.setTextColor(t.getTag() != null ? (light ? 0xB3111111 : 0xB8FFFFFF) : fg);
        for (IconView i : icons) { i.color = fg; i.invalidate(); }
    }

    IconView icon(MainActivity a, String name, float sizeDesign, float marginRight) {
        IconView v = new IconView(a, name);
        LayoutParams lp = new LayoutParams(Ui.px(sizeDesign), Ui.px(sizeDesign));
        lp.rightMargin = Ui.px(marginRight);
        addView(v, lp);
        icons.add(v);
        paint(isFocused());
        return v;
    }

    TextView text(MainActivity a, String s, float size, boolean secondary) {
        TextView t = Ui.text(a, size, secondary ? Ui.regular : Ui.semibold, 0xFFFFFFFF);
        t.setText(s);
        if (secondary) t.setTag("2");
        texts.add(t);
        paint(isFocused());
        return t;
    }

    /** A title with a smaller line under it, e.g. "Sleep Timer / Off". */
    TextView[] twoLines(MainActivity a, String title, String sub, float size) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(VERTICAL);
        TextView t = text(a, title, size, false), s = text(a, sub, size - 3, true);
        col.addView(t);
        if (sub != null) col.addView(s);
        addView(col, new LayoutParams(0, -2, 1f));
        return new TextView[] { t, s };
    }
}
