package tv.pure.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A Settings or menu row: icon, title (and a smaller line), value, tick or chevron. Focused rows turn white. */
final class Row extends LinearLayout {
    final TextView title, sub, value;
    private final View icon;
    private final boolean check, chevron;
    final String key;
    Runnable action;

    static final class Spec {
        String key, title, sub, value, iconName, emoji, emojiBg;
        int iconColor;
        Bitmap thumb;
        boolean check, chevron, danger;
        Runnable action;
        Spec(String key, String title) { this.key = key; this.title = title; }
        Spec sub(String s) { sub = s; return this; }
        Spec value(String v) { value = v; return this; }
        Spec icon(String n, int color) { iconName = n; iconColor = color; return this; }
        Spec emoji(String e, String bg) { emoji = e; emojiBg = bg; return this; }
        Spec thumb(Bitmap b) { thumb = b; return this; }
        Spec checked(boolean on) { check = on; return this; }
        Spec go() { chevron = true; return this; }
        Spec danger() { danger = true; return this; }
        Spec on(Runnable r) { action = r; return this; }
    }

    Row(Context c, Spec s, float widthDesign) {
        super(c);
        key = s.key; action = s.action; check = s.check; chevron = s.chevron;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setFocusable(true);
        setClickable(true);
        int padH = Ui.px(28);
        setPadding(padH, Ui.px(s.sub != null ? 14 : 18), padH, Ui.px(s.sub != null ? 14 : 18));
        setMinimumHeight(Ui.px(76));
        if (s.iconName != null || s.emoji != null || s.thumb != null) {
            icon = makeIcon(c, s);
            LayoutParams lp = new LayoutParams(Ui.px(s.thumb != null ? 72 : 44), Ui.px(44));
            lp.rightMargin = Ui.px(20);
            addView(icon, lp);
        } else icon = null;
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(VERTICAL);
        title = Ui.text(c, 28, Ui.semibold, s.danger ? 0xFFFF6961 : Ui.TEXT);
        title.setText(s.title);
        title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(title);
        if (s.sub != null) {
            sub = Ui.text(c, 19, Ui.regular, Ui.TEXT2);
            sub.setText(s.sub);
            sub.setPadding(0, Ui.px(4), 0, 0);
            col.addView(sub);
        } else sub = null;
        addView(col, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        if (s.value != null) {
            value = Ui.text(c, 26, Ui.regular, Ui.TEXT2);
            value.setText(s.value);
            value.setPadding(Ui.px(16), 0, 0, 0);
            addView(value);
        } else value = null;
        if (check || chevron) {
            View mark = new View(c) {
                @Override protected void onDraw(Canvas cv) {
                    int col = Row.this.isFocused() ? 0xFF1C1C1E : check ? 0xFF0A84FF : 0x99FFFFFF;
                    Icons.draw(cv, check ? "check" : "chev", 0, 0, getWidth(), col);
                }
            };
            LayoutParams lp = new LayoutParams(Ui.px(30), Ui.px(30));
            lp.leftMargin = Ui.px(14);
            addView(mark, lp);
        }
        background(false);
        setOnFocusChangeListener((v, f) -> {
            background(f);
            animate().scaleX(f ? 1.02f : 1f).scaleY(f ? 1.02f : 1f).setDuration(220).setInterpolator(Ui.EASE).start();
            int t = f ? 0xFF1C1C1E : (s.danger ? 0xFFFF6961 : Ui.TEXT);
            title.setTextColor(t);
            if (sub != null) sub.setTextColor(f ? 0xB31C1C1E : Ui.TEXT2);
            if (value != null) value.setTextColor(f ? 0xB31C1C1E : Ui.TEXT2);
            for (int i = 0; i < getChildCount(); i++) getChildAt(i).invalidate();
        });
        setOnClickListener(v -> { Ui.sound("ok"); if (action != null) action.run(); });
    }

    private void background(boolean focused) {
        GradientDrawable g = Ui.round(focused ? 0xF2FFFFFF : 0x17FFFFFF, 22);
        setBackground(g);
        setElevation(focused ? Ui.fx(8) : 0);
    }

    void setValue(String v) { if (value != null) value.setText(v); }

    /** Title (and line under it) centred, for buttons such as Get Started. */
    void center() {
        LinearLayout col = (LinearLayout) title.getParent();
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setGravity(Gravity.CENTER);
        if (sub != null) sub.setGravity(Gravity.CENTER);
    }
    void setSub(String v) { if (sub != null) sub.setText(v); }

    private static View makeIcon(Context c, Spec s) {
        if (s.thumb != null) {
            ImageView iv = new ImageView(c);
            iv.setImageBitmap(s.thumb);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setClipToOutline(true);
            iv.setBackground(Ui.round(0xFF000000, 8));
            return iv;
        }
        if (s.emoji != null) {
            TextView t = Ui.text(c, 26, null, 0xFF000000);
            t.setText(s.emoji);
            t.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(Ui.parse(s.emojiBg, 0xFF8E8E93));
            t.setBackground(g);
            return t;
        }
        final String name = s.iconName;
        View v = new View(c) {
            @Override protected void onDraw(Canvas cv) {
                float p = getWidth() * .2f;
                Icons.draw(cv, name, p, p, getWidth() - 2 * p, 0xFFFFFFFF);
            }
        };
        v.setBackground(Ui.round(s.iconColor, 11));
        return v;
    }

    /** A small heading above a group of rows. */
    static TextView heading(Context c, String text) {
        TextView h = Ui.text(c, 20, Ui.semibold, Ui.TEXT3);
        h.setText(text);
        h.setPadding(Ui.px(14), Ui.px(26), 0, Ui.px(10));
        return h;
    }

    static FrameLayout.LayoutParams at(int w, int h, int gravity) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.gravity = gravity;
        return lp;
    }
}
