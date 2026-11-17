package tv.pure.home;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Liquid Glass without live blur: the panel shows the matching slice of the wallpaper's pre-blurred copy,
 * under a tint and a bright edge. It looks like frosted glass over the wallpaper, and drawing it is just a
 * bitmap copy, so it costs the TV almost nothing.
 */
final class Glass extends Drawable {
    static String style = "liquid";
    private final View host;
    private final Scenes scenes;
    private final float radius;
    private final Paint pic = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), tint = new Paint(Paint.ANTI_ALIAS_FLAG),
            sheen = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Matrix m = new Matrix();
    private final int[] at = new int[2];
    float extraAlpha = 1f;
    private final Scenes.Listener redraw = this::invalidateSelf;

    Glass(View host, Scenes scenes, float radiusDesign) {
        this.host = host; this.scenes = scenes; radius = Ui.fx(radiusDesign);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Ui.fx(1.5f));
        scenes.listen(redraw);
    }

    @Override public void draw(Canvas c) {
        r.set(getBounds());
        host.getLocationOnScreen(at);
        boolean dark = "dark".equals(style), frosted = "frosted".equals(style);
        if (scenes.prev != null && scenes.mix < 1f) slice(c, scenes.prev, 255);
        slice(c, scenes.cur(), Math.round(255 * (scenes.prev != null ? scenes.mix : 1f)));
        int a = Math.round(255 * extraAlpha);
        tint.setShader(null);
        tint.setColor(dark ? 0xCC0E0E12 : frosted ? 0x61464854 : 0x38181820);
        tint.setAlpha(Math.round(Color.alpha(tint.getColor()) * extraAlpha));
        c.drawRoundRect(r, radius, radius, tint);
        if (!dark) {   // light falling across the glass, brighter at the top
            sheen.setShader(new LinearGradient(r.left, r.top, r.left + r.width() * .4f, r.bottom,
                    frosted ? 0x57FFFFFF : 0x38FFFFFF, frosted ? 0x2EFFFFFF : 0x0FFFFFFF, Shader.TileMode.CLAMP));
            sheen.setAlpha(a);
            c.drawRoundRect(r, radius, radius, sheen);
        }
        float s = edge.getStrokeWidth() / 2;
        edge.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0x8CFFFFFF, 0x24FFFFFF, Shader.TileMode.CLAMP));
        edge.setAlpha(Math.round(a * (dark ? .5f : 1f)));
        c.drawRoundRect(r.left + s, r.top + s, r.right - s, r.bottom - s, radius, radius, edge);
    }

    private void slice(Canvas c, Scenes.Scene s, int alpha) {
        Bitmap g;
        synchronized (s) { g = s.glass; }
        if (g == null || alpha == 0) return;
        View root = host.getRootView();
        BitmapShader sh = new BitmapShader(g, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        m.setScale(root.getWidth() / (float) g.getWidth(), root.getHeight() / (float) g.getHeight());
        m.postTranslate(-at[0] - host.getScrollX(), -at[1] - host.getScrollY());
        sh.setLocalMatrix(m);
        pic.setShader(sh);
        pic.setAlpha(Math.round(alpha * extraAlpha));
        c.drawRoundRect(r, radius, radius, pic);
    }

    @Override public void setAlpha(int alpha) { extraAlpha = alpha / 255f; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    private static final class Color { static int alpha(int c) { return c >>> 24; } }
}
