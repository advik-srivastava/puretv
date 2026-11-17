package tv.pure.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewOutlineProvider;

/**
 * One app (or Settings, Search, Inputs, Parents) as a wide 5:3 tile. On focus it lifts with a small
 * overshoot and a soft shadow, and its name appears underneath, as on tvOS. Drawing happens once;
 * the lift is a hardware transform, so moving along the row costs almost nothing.
 */
final class Tile extends View {
    final String pkg, sys;      // one of them is set
    final String label;
    private Bitmap art;
    private final boolean kidsBadge;
    private final Paint pic = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), bg = new Paint(Paint.ANTI_ALIAS_FLAG),
            name = new Paint(Paint.ANTI_ALIAS_FLAG), badge = new Paint(Paint.ANTI_ALIAS_FLAG), gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF face = new RectF();
    private final Matrix m = new Matrix();
    private final float radius, faceH;
    boolean held;

    Tile(Context c, String pkg, String sys, String label, Bitmap art, float w, float h, boolean kidsBadge) {
        super(c);
        this.pkg = pkg; this.sys = sys; this.label = label; this.art = art; this.kidsBadge = kidsBadge;
        faceH = h;
        radius = Ui.fx(20);
        setFocusable(true);
        setClipToOutline(false);
        setLayerType(LAYER_TYPE_NONE, null);
        name.setTypeface(Ui.semibold); name.setColor(0xFFFFFFFF); name.setTextSize(Ui.fx(22)); name.setTextAlign(Paint.Align.CENTER);
        name.setShadowLayer(Ui.fx(6), 0, Ui.fx(1), 0x80000000);
        badge.setTypeface(Ui.semibold); badge.setTextSize(Ui.fx(15)); badge.setTextAlign(Paint.Align.CENTER);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), Math.round(faceH), radius); }
        });
        setPivotY(h / 2f);
        setContentDescription(label);
    }

    void setArt(Bitmap b) { art = b; invalidate(); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { setPivotX(w / 2f); setPivotY(faceH / 2f); }

    @Override protected void onFocusChanged(boolean f, int dir, android.graphics.Rect prev) {
        super.onFocusChanged(f, dir, prev);
        float s = f ? 1.12f : 1f;
        animate().scaleX(s).scaleY(s).translationZ(f ? Ui.fx(30) : 0).setDuration(f ? 380 : 260).setInterpolator(f ? Ui.LIFT : Ui.EASE).start();
        setElevation(f ? Ui.fx(10) : Ui.fx(2));
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth();
        face.set(0, 0, w, faceH);
        if (art != null) {
            BitmapShader sh = new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float k = Math.max(w / art.getWidth(), faceH / art.getHeight());
            m.setScale(k, k);
            m.postTranslate((w - art.getWidth() * k) / 2, (faceH - art.getHeight() * k) / 2);
            sh.setLocalMatrix(m);
            pic.setShader(sh);
            c.drawRoundRect(face, radius, radius, pic);
        } else {
            boolean gear = "settings".equals(sys);
            bg.setShader(new LinearGradient(0, 0, 0, faceH,
                    gear ? 0xFFE4E6EA : "parents".equals(sys) ? 0xFFFFD36A : 0xFF9AA0AB,
                    gear ? 0xFFA9ADB5 : "parents".equals(sys) ? 0xFFF0A020 : 0xFF5F6571, Shader.TileMode.CLAMP));
            c.drawRoundRect(face, radius, radius, bg);
            float s = faceH * .5f;
            if (gear) Icons.gear(c, w / 2, faceH / 2, s * .62f);
            else Icons.draw(c, "parents".equals(sys) ? "lock" : sys, w / 2 - s / 2, faceH / 2 - s / 2, s, 0xFFFFFFFF);
        }
        if (isFocused()) {   // a soft highlight across the top, like light on glass
            gloss.setShader(new LinearGradient(0, 0, 0, faceH * .6f, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRoundRect(face, radius, radius, gloss);
        }
        if (held) {
            Paint ring = Icons.stroke(Ui.fx(4), 0xFFFFFFFF);
            c.drawRoundRect(face, radius, radius, ring);
        }
        if (kidsBadge) {
            float bw = Ui.fx(70), bh = Ui.fx(28), x = w - bw - Ui.fx(10), y = faceH - bh - Ui.fx(9);
            badge.setColor(0xFFFFD60A);
            c.drawRoundRect(x, y, x + bw, y + bh, bh / 2, bh / 2, badge);
            badge.setColor(0xFF1A1400);
            c.drawText("Kids", x + bw / 2, y + bh * .7f, badge);
        }
        if (isFocused()) c.drawText(label, w / 2, faceH + Ui.fx(44), name);
    }
}
