package tv.pure.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.PathInterpolator;

/**
 * PureTV's start-up: the PureTV mark and name on black, one soft highlight across the mark, then a fade
 * into Home. About 1.9 s, once per TV start; any button skips it; Reduce Motion turns it off.
 */
final class IntroView extends View {
    static boolean shown;
    static final long LENGTH = 1900;
    private static final PathInterpolator OUT = new PathInterpolator(.16f, 1f, .3f, 1f);    // a soft, Apple-like settle
    private static final PathInterpolator INOUT = new PathInterpolator(.45f, 0f, .25f, 1f);
    private final Paint word = new Paint(Paint.ANTI_ALIAS_FLAG), sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable onReveal, done;
    private boolean revealed;
    private float t;
    private ValueAnimator anim;
    private final RectF box = new RectF();
    private final Path clip = new Path();

    IntroView(Context c, Runnable onReveal, Runnable done) {
        super(c);
        this.onReveal = onReveal; this.done = done;
        setLayerType(LAYER_TYPE_HARDWARE, null);
        word.setTypeface(Ui.semibold);
        word.setTextAlign(Paint.Align.CENTER);
        word.setColor(0xFFFFFFFF);
        word.setLetterSpacing(.01f);
    }

    void start() {
        anim = ValueAnimator.ofFloat(0, 1).setDuration(LENGTH);
        anim.setInterpolator(null);
        anim.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            if (!revealed && t >= .68f) { revealed = true; if (onReveal != null) onReveal.run(); }
            invalidate();
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (getParent() != null) ((android.view.ViewGroup) getParent()).removeView(IntroView.this);
                if (done != null) done.run();
            }
        });
        anim.start();
    }

    /** Any button: straight to the fade into Home. */
    void skip() { if (anim != null && t < .68f) anim.setCurrentPlayTime(Math.round(LENGTH * .68f)); }

    private static float span(float t, float a, float b) { return Math.max(0, Math.min(1, (t - a) / (b - a))); }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float in = OUT.getInterpolation(span(t, 0f, .42f));            // the mark arrives
        float name = OUT.getInterpolation(span(t, .14f, .5f));         // the name follows
        float light = INOUT.getInterpolation(span(t, .36f, .66f));     // one soft highlight across the mark
        float out = INOUT.getInterpolation(span(t, .68f, 1f));         // and everything gives way to Home
        c.drawColor(((int) (255 * (1 - out)) << 24));
        float size = Ui.fx(200), cx = w / 2f, cy = h / 2f - Ui.fx(46);
        float s = (.86f + .14f * in) * (1 + .04f * out);
        int alpha = Math.round(255 * Math.min(1, in * 1.4f) * (1 - out));
        c.save();
        c.scale(s, s, cx, cy + Ui.fx(46));
        Mark.draw(c, cx, cy, size, alpha);
        if (light > 0 && light < 1) {   // the highlight, clipped to the mark
            box.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
            clip.reset();
            clip.addRoundRect(box, size * .225f, size * .225f, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            float x = box.left - size + (size * 3) * light;
            sheen.setShader(new LinearGradient(x - size * .35f, box.top, x + size * .35f, box.bottom,
                    new int[] { 0x00FFFFFF, 0x59FFFFFF, 0x00FFFFFF }, null, Shader.TileMode.CLAMP));
            sheen.setAlpha(Math.round(255 * (1 - out)));
            c.drawRect(box, sheen);
            c.restore();
        }
        word.setTextSize(Ui.fx(64));
        word.setAlpha(Math.round(255 * name * (1 - out)));
        c.drawText("PureTV", cx, cy + size / 2 + Ui.fx(100) + Ui.fx(14) * (1 - name), word);
        c.restore();
    }
}
