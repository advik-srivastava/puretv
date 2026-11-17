package tv.pure.home;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * The PureTV mark: a rounded square in blue-to-indigo, with a white line-drawn mountain and sun, the
 * simplest picture of a calm Home screen. The same geometry as tools/make_brand.py (app icon and banner).
 */
final class Mark {
    private Mark() { }

    static void draw(Canvas c, float cx, float cy, float size, int alpha) {
        float l = cx - size / 2, t = cy - size / 2, r = size * .225f;
        RectF box = new RectF(l, t, l + size, t + size);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(l, t, l + size, t + size, 0xFF6C8CFF, 0xFF3A2FBF, Shader.TileMode.CLAMP));
        bg.setAlpha(alpha);
        c.drawRoundRect(box, r, r, bg);
        Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);   // light across the top, like a glass icon
        gloss.setShader(new LinearGradient(0, t, 0, t + size * .55f, 0x33FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        gloss.setAlpha(alpha);
        c.drawRoundRect(box, r, r, gloss);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(0xFFFFFFFF);
        line.setAlpha(alpha);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        // a TV: the screen and its stand
        line.setStrokeWidth(size * .055f);
        RectF screen = new RectF(l + size * .18f, t + size * .24f, l + size * .82f, t + size * .68f);
        c.drawRoundRect(screen, size * .07f, size * .07f, line);
        c.drawLine(l + size * .40f, t + size * .80f, l + size * .60f, t + size * .80f, line);
        // on it, a calm landscape: one ridge and the sun
        line.setStrokeWidth(size * .045f);
        Path m = new Path();
        m.moveTo(l + size * .28f, t + size * .59f);
        m.lineTo(l + size * .42f, t + size * .43f);
        m.lineTo(l + size * .50f, t + size * .52f);
        m.lineTo(l + size * .60f, t + size * .39f);
        m.lineTo(l + size * .72f, t + size * .59f);
        c.drawPath(m, line);
        Paint sun = new Paint(Paint.ANTI_ALIAS_FLAG);
        sun.setColor(0xFFFFFFFF);
        sun.setAlpha(Math.round(alpha * .95f));
        c.drawCircle(l + size * .33f, t + size * .345f, size * .042f, sun);
    }
}
