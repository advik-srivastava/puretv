package tv.pure.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.PathInterpolator;

/**
 * PureTV's start-up, "Clearing": the TV becoming clean, in one short moment.
 *
 *   wake      0.00–0.30 s  a hairline of soft light opens across the middle of a black screen
 *   frosted   0.25–0.75 s  it opens top and bottom into your wallpaper, deeply frosted, with "PureTV" in it
 *   clearing  0.75–1.70 s  the frost melts into the sharp photo; the wordmark gives way to the glass clock
 *                          and the scenery; the date and apps rise into place: it becomes Home
 *
 * It draws only a full-screen bitmap and a few shapes each frame, plays once per TV start, any button
 * skips it, and Reduce Motion turns it off. Nothing is advertised on launch.
 */
final class IntroView extends View {
    static boolean shown;
    static final long LENGTH = 1700;
    private static final PathInterpolator EASE = new PathInterpolator(.22f, 0f, 0f, 1f);       // Apple-like ease-out
    private static final PathInterpolator INOUT = new PathInterpolator(.45f, 0f, .25f, 1f);
    private final Paint img = new Paint(Paint.FILTER_BITMAP_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG),
            word = new Paint(Paint.ANTI_ALIAS_FLAG), black = new Paint();
    private final Rect dst = new Rect();
    private final RectF bar = new RectF();
    private final Bitmap frost;
    private final Runnable onClearing, done;
    private boolean clearingStarted;
    private float t;
    private ValueAnimator anim;

    IntroView(Context c, Bitmap frost, Runnable onClearing, Runnable done) {
        super(c);
        this.frost = frost; this.onClearing = onClearing; this.done = done;
        setLayerType(LAYER_TYPE_HARDWARE, null);
        black.setColor(0xFF000000);
        line.setColor(0xFFFFFFFF);
        word.setTypeface(Ui.semibold);
        word.setTextAlign(Paint.Align.CENTER);
        word.setTextSize(Ui.fx(54));
        word.setLetterSpacing(.02f);
        word.setColor(0xFFFFFFFF);
        word.setShadowLayer(Ui.fx(18), 0, Ui.fx(2), 0x40000000);
    }

    void start() {
        anim = ValueAnimator.ofFloat(0, 1).setDuration(LENGTH);
        anim.setInterpolator(null);
        anim.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            if (!clearingStarted && t >= .44f) { clearingStarted = true; if (onClearing != null) onClearing.run(); }
            invalidate();
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) { finish(); }
        });
        anim.start();
    }

    /** Any button: jump to the clearing, which settles in a moment. */
    void skip() { if (anim != null && t < .44f) anim.setCurrentPlayTime(Math.round(LENGTH * .44f)); }

    private void finish() {
        if (getParent() != null) ((android.view.ViewGroup) getParent()).removeView(this);
        if (done != null) done.run();
    }

    private static float span(float t, float a, float b) { return Math.max(0, Math.min(1, (t - a) / (b - a))); }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float wake = EASE.getInterpolation(span(t, 0f, .18f));      // the hairline grows across
        float open = INOUT.getInterpolation(span(t, .15f, .44f));   // and opens top and bottom
        float clear = INOUT.getInterpolation(span(t, .44f, 1f));    // the frost melts away
        float wordIn = EASE.getInterpolation(span(t, .26f, .44f)), wordOut = EASE.getInterpolation(span(t, .46f, .66f));

        // black, except for the opening window
        float half = (h / 2f) * open, cy = h / 2f;
        if (open < 1) {
            c.drawRect(0, 0, w, cy - half, black);
            c.drawRect(0, cy + half, w, h, black);
        }
        // the frosted wallpaper inside the window, melting away to reveal Home underneath
        if (frost != null && clear < 1) {
            c.save();
            c.clipRect(0, cy - half, w, cy + half);
            img.setAlpha(Math.round(255 * (1 - clear)));
            dst.set(0, 0, w, h);
            c.drawBitmap(frost, null, dst, img);
            c.restore();
        } else if (frost == null && open < 1) {
            c.drawRect(0, cy - half, w, cy + half, black);
        }
        // the hairline of light at the opening's edges
        if (open < 1) {
            float lw = w * (.12f + .88f * wake), a = (1 - open * .85f);
            for (int i = 0; i < 3; i++) {   // a core and two soft halos: light, not a stroke
                float th = Ui.fx(1.5f + i * 4), al = i == 0 ? 1f : i == 1 ? .28f : .1f;
                line.setAlpha(Math.round(255 * al * a * Math.min(1, wake * 1.4f)));
                bar.set((w - lw) / 2, cy - half - th / 2, (w + lw) / 2, cy - half + th / 2);
                c.drawRoundRect(bar, th, th, line);
                if (half > 1) { bar.offset(0, half * 2); c.drawRoundRect(bar, th, th, line); }
            }
        }
        // the wordmark, quiet, inside the frost
        float wa = wordIn * (1 - wordOut);
        if (wa > 0) {
            word.setAlpha(Math.round(235 * wa));
            c.drawText("PureTV", w / 2f, cy + Ui.fx(18) - Ui.fx(10) * wordOut, word);
        }
    }
}
