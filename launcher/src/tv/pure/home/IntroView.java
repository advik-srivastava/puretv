package tv.pure.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.View;
import android.view.animation.PathInterpolator;

/**
 * PureTV's start-up: a dark glass card in soft spectral light, the PureTV wordmark settling into it, then
 * the card opening into the Home screen. About 2.5 seconds, once per TV start; any button skips it.
 * The blurred light is painted once into a small bitmap, so each frame is only a few cheap draws.
 */
final class IntroView extends View {
    static boolean shown;                       // once per start of the TV (per process)
    private static final int[] SPECTRUM = { 0xFF7FD8FF, 0xFFA9A2FF, 0xFFF59BD0, 0xFFFFD08A, 0xFF9AF0C8, 0xFF7FD8FF };
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG),
            word = new Paint(Paint.ANTI_ALIAS_FLAG), sub = new Paint(Paint.ANTI_ALIAS_FLAG), img = new Paint(Paint.FILTER_BITMAP_FLAG),
            ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF card = new RectF(), dst = new RectF();
    private final Matrix spin = new Matrix();
    private Bitmap glow;
    private SweepGradient sweep;
    private float t;
    private final Runnable done;
    private ValueAnimator anim;
    private static final PathInterpolator SOFT = new PathInterpolator(.3f, 0f, .2f, 1f);

    IntroView(Context c, Runnable done) {
        super(c);
        this.done = done;
        setLayerType(LAYER_TYPE_HARDWARE, null);
        setFocusable(true);
        fill.setColor(0xFF0B0D14);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Ui.fx(1.5f));
        edge.setColor(0x40FFFFFF);
        word.setTypeface(Ui.heavy);
        word.setTextAlign(Paint.Align.CENTER);
        word.setTextSize(Ui.fx(112));
        sub.setTypeface(Ui.regular);
        sub.setTextAlign(Paint.Align.CENTER);
        sub.setTextSize(Ui.fx(22));
        sub.setColor(0x99FFFFFF);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.fx(2.5f));
    }

    void start() {
        anim = ValueAnimator.ofFloat(0, 1).setDuration(2600);
        anim.setInterpolator(null);
        anim.addUpdateListener(a -> { t = (float) a.getAnimatedValue(); invalidate(); });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) { finish(); }
        });
        anim.start();
    }

    /** Any button: straight to Home. */
    void skip() { if (anim != null && t < .78f) anim.setCurrentPlayTime(Math.round(2600 * .78f)); }

    private void finish() {
        if (getParent() == null) return;
        ((android.view.ViewGroup) getParent()).removeView(this);
        if (glow != null) { glow.recycle(); glow = null; }
        if (done != null) done.run();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        float cw = Ui.fx(620), ch = Ui.fx(360);
        card.set((w - cw) / 2, (h - ch) / 2, (w + cw) / 2, (h + ch) / 2);
        sweep = new SweepGradient(card.centerX(), card.centerY(), SPECTRUM, null);
        word.setShader(new LinearGradient(0, card.centerY() - Ui.fx(70), 0, card.centerY() + Ui.fx(30), 0xFFFFFFFF, 0xFFC9CCD8, Shader.TileMode.CLAMP));
        // the soft light around the card, painted once at half size with a real blur
        int pad = Math.round(Ui.fx(140)), gw = Math.round((cw + 2 * pad) / 2), gh = Math.round((ch + 2 * pad) / 2);
        glow = Bitmap.createBitmap(Math.max(1, gw), Math.max(1, gh), Bitmap.Config.ARGB_8888);
        Canvas g = new Canvas(glow);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Ui.fx(46) / 2);
        p.setShader(new SweepGradient(gw / 2f, gh / 2f, SPECTRUM, null));
        p.setMaskFilter(new BlurMaskFilter(Ui.fx(44) / 2, BlurMaskFilter.Blur.NORMAL));
        float r = Ui.fx(64) / 2;
        g.drawRoundRect(pad / 2f, pad / 2f, gw - pad / 2f, gh - pad / 2f, r, r, p);
        g.drawRoundRect(pad / 2f, pad / 2f, gw - pad / 2f, gh - pad / 2f, r, r, p);   // twice: a fuller, softer light
        dst.set(card.left - pad, card.top - pad, card.right + pad, card.bottom + pad);
    }

    private static float span(float t, float a, float b) { return Math.max(0, Math.min(1, (t - a) / (b - a))); }

    @Override protected void onDraw(Canvas c) {
        float in = SOFT.getInterpolation(span(t, 0f, .32f));          // the card and its light arrive
        float txt = SOFT.getInterpolation(span(t, .18f, .5f));        // the wordmark settles
        float out = SOFT.getInterpolation(span(t, .76f, 1f));         // everything opens into Home
        c.drawColor(((int) (255 * (1 - out)) << 24));
        c.save();
        float s = (.94f + .06f * in) * (1 + .18f * out);
        c.scale(s, s, card.centerX(), card.centerY());
        int alpha = Math.round(255 * in * (1 - out));
        if (glow != null) {
            float breathe = .82f + .18f * (float) Math.sin(t * Math.PI * 2.2);
            img.setAlpha(Math.round(alpha * breathe));
            c.drawBitmap(glow, null, dst, img);
        }
        fill.setAlpha(Math.round(242 * in * (1 - out)));
        float r = Ui.fx(64);
        c.drawRoundRect(card, r, r, fill);
        if (sweep != null) {   // a thin bright edge whose colours turn slowly around the card
            spin.setRotate(t * 140, card.centerX(), card.centerY());
            sweep.setLocalMatrix(spin);
            ring.setShader(sweep);
            ring.setAlpha(Math.round(alpha * .9f));
            float k = ring.getStrokeWidth() / 2;
            c.drawRoundRect(card.left + k, card.top + k, card.right - k, card.bottom - k, r, r, ring);
        }
        edge.setAlpha(Math.round(64 * in * (1 - out)));
        c.drawRoundRect(card, r, r, edge);
        word.setAlpha(Math.round(255 * txt * (1 - out)));
        word.setLetterSpacing(.12f * (1 - txt) - .01f);
        c.drawText("PureTV", card.centerX(), card.centerY() + Ui.fx(38) + Ui.fx(10) * (1 - txt), word);
        c.restore();
        sub.setAlpha(Math.round(153 * span(t, .4f, .6f) * (1 - out)));
        c.drawText("Made with ❤ by Crazified Studios", getWidth() / 2f, card.bottom + Ui.fx(110), sub);
    }
}
