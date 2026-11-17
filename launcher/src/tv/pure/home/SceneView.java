package tv.pure.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.View;

/**
 * The top shelf: the photo, the big glass clock, and the scenery cut out in front of it.
 * It draws only when the minute changes or the wallpaper crossfades; the rest of the time it costs nothing.
 */
final class SceneView extends View {
    private final Scenes scenes;
    private final Paint img = new Paint(Paint.FILTER_BITMAP_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG),
            rim = new Paint(Paint.ANTI_ALIAS_FLAG), dim = new Paint();
    private final Rect dst = new Rect();
    private final Matrix m = new Matrix();
    String time = "";
    float darken;
    private final Scenes.Listener redraw = this::invalidate;
    private final java.util.WeakHashMap<Bitmap, BitmapShader> shaders = new java.util.WeakHashMap<>();            // 0..1: the grid or Settings is open

    SceneView(Context c, Scenes s) {
        super(c);
        scenes = s;
        setLayerType(LAYER_TYPE_HARDWARE, null);   // kept as one texture between redraws
        for (Paint p : new Paint[] { fill, rim }) { p.setTypeface(Ui.clock); p.setTextAlign(Paint.Align.CENTER); }
        ColorMatrix cm = new ColorMatrix();
        cm.setScale(1.3f, 1.3f, 1.3f, 1f);           // the glass inside the digits is a little brighter than the sky
        ColorMatrix sat = new ColorMatrix(); sat.setSaturation(1.15f); cm.postConcat(sat);
        fill.setColorFilter(new ColorMatrixColorFilter(cm));
        rim.setStyle(Paint.Style.FILL_AND_STROKE);
        dim.setColor(0xFF000000);
        s.listen(redraw);
    }

    void setTime(String t) { if (!t.equals(time)) { time = t; invalidate(); } }
    void setDarken(float d) { if (d != darken) { darken = d; invalidate(); } }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        dst.set(0, 0, w, h);
        Scenes.Scene cur = scenes.cur(), prev = scenes.prev;
        float mix = scenes.mix;
        if (prev != null && mix < 1f) drawScene(c, prev, 255, w);
        drawScene(c, cur, Math.round(255 * (prev != null ? mix : 1f)), w);
        if (darken > 0) { dim.setAlpha(Math.round(darken * 150)); c.drawRect(dst, dim); }
    }

    private void drawScene(Canvas c, Scenes.Scene s, int alpha, int w) {
        Bitmap photo, fg, glass;
        synchronized (s) { photo = s.photo; fg = s.fg; glass = s.glass; }
        if (photo == null) return;
        img.setAlpha(alpha);
        c.drawBitmap(photo, null, dst, img);
        if (!time.isEmpty()) {
            float size = Ui.fx(s.clockSize), top = Ui.fx(s.clockTop), base = top + size * .9175f, cx = w / 2f;
            for (Paint p : new Paint[] { fill, rim }) { p.setTextSize(size); p.setLetterSpacing(.01f); }
            if (glass != null) {
                BitmapShader sh = shaders.get(glass);
                if (sh == null) { sh = new BitmapShader(glass, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP); shaders.put(glass, sh); }
                m.setScale(w / (float) glass.getWidth(), getHeight() / (float) glass.getHeight());
                sh.setLocalMatrix(m);
                fill.setShader(sh);
                fill.setAlpha(Math.round(alpha * .92f));
                c.drawText(time, cx, base, fill);              // the digits are made of the blurred wallpaper
            }
            rim.setColor(0xFFFFFFFF);
            rim.setAlpha(Math.round(alpha * .5f));   // bright enough to read on a pale sky
            rim.setStyle(Paint.Style.FILL);
            rim.setShadowLayer(Ui.fx(26), 0, Ui.fx(4), 0xFF001A2D);   // a soft shadow lifts the clock off bright skies
            c.drawText(time, cx, base, rim);
            rim.clearShadowLayer();
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(Ui.fx(2.5f));
            rim.setColor(0xE6FFFFFF);
            rim.setAlpha(Math.round(alpha * .9f));
            c.drawText(time, cx, base, rim);                   // a bright glass edge
        }
        if (fg != null) c.drawBitmap(fg, null, dst, img);       // the mountains stand in front of the clock
    }
}
