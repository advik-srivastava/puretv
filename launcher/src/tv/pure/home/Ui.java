package tv.pure.home;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/** Shared look: everything is laid out on a 1920 x 1080 design grid and scaled to the screen. */
final class Ui {
    private Ui() { }

    static float k = 1f;                       // screen pixels per design pixel
    static Typeface clock, regular, semibold, heavy;
    static final PathInterpolator EASE = new PathInterpolator(.2f, .8f, .2f, 1f);     // settle, no bounce
    static final PathInterpolator LIFT = new PathInterpolator(.25f, 1.35f, .4f, 1f);  // focus: a small overshoot
    static final int TEXT = 0xFFFFFFFF, TEXT2 = 0xB3FFFFFF, TEXT3 = 0x80FFFFFF;

    private static SoundPool pool;
    private static final Map<String, Integer> ids = new HashMap<>();
    static boolean soundsOn = true;
    private static long lastNav;

    static void init(Context c, int screenWidth) {
        k = screenWidth / 1920f;
        if (clock == null) {
            clock = font(c, "antonio_light"); regular = font(c, "inter_regular");
            semibold = font(c, "inter_semibold"); heavy = font(c, "inter_heavy");
        }
        if (pool == null) {
            pool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
            for (String n : new String[] { "nav", "ok", "back", "edge" }) {
                try { ids.put(n, pool.load(c.getAssets().openFd("sfx/" + n + ".wav"), 1)); } catch (Exception ignored) { }
            }
        }
    }

    private static Typeface font(Context c, String name) {
        try { return Typeface.createFromAsset(c.getAssets(), "fonts/" + name + ".ttf"); } catch (Exception e) { return Typeface.DEFAULT; }
    }

    /** Design pixels to screen pixels. */
    static int px(float design) { return Math.round(design * k); }
    static float fx(float design) { return design * k; }

    static void sound(String name) {
        if (!soundsOn || pool == null) return;
        Integer id = ids.get(name);
        if (id == null) return;
        if ("nav".equals(name)) {
            long now = System.currentTimeMillis();
            if (now - lastNav < 45) return;
            lastNav = now;
        }
        float rate = "nav".equals(name) ? 1f + (float) (Math.random() - .5) * .016f : 1f;
        pool.play(id, 1f, 1f, 1, 0, rate);
    }

    static TextView text(Context c, float size, Typeface face, int color) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, fx(size));
        t.setTypeface(face);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setSingleLine(false);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    static GradientDrawable round(int color, float radiusDesign) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(fx(radiusDesign));
        return g;
    }

    static int parse(String hex, int fallback) {
        try { return Color.parseColor(hex); } catch (Exception e) { return fallback; }
    }

    static void fadeIn(View v, long ms) {
        v.setAlpha(0f);
        v.setVisibility(View.VISIBLE);
        v.animate().alpha(1f).setDuration(ms).setInterpolator(EASE).start();
    }
}
