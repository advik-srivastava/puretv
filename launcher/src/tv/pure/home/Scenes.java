package tv.pure.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The wallpapers. Each has a photo, its scenery cut out (drawn in front of the clock) and a pre-blurred copy
 * (the Liquid Glass and the clock's fill). Only the wallpaper on screen and the next one are in memory.
 */
final class Scenes {
    static final class Scene {
        final String id, name;
        final int clockTop, clockSize;   // on the 1920 x 1080 design grid, chosen so the clock sits behind the scenery
        Bitmap photo, fg, glass;
        Scene(String id, String name, int top, int size) { this.id = id; this.name = name; clockTop = top; clockSize = size; }
    }

    static final Scene[] ALL = {
        new Scene("ossau", "Ossau", 154, 400), new Scene("flatirons", "Flatirons", 150, 352),
        new Scene("luna", "Valle de la Luna", 246, 400), new Scene("kathleen", "Kathleen Lake", 174, 400),
        new Scene("titicaca", "Titicaca Sunrise", 246, 400), new Scene("louise", "Lake Louise", 114, 400),
    };

    interface Listener { void onScene(); }

    private final Context c;
    private final List<java.lang.ref.WeakReference<Listener>> listeners = new ArrayList<>();   // panels come and go
    int index;
    Scene prev;            // fading out
    float mix = 1f;        // 0 = prev, 1 = current
    private ValueAnimator fade;

    Scenes(Context c, int start) { this.c = c; index = Math.floorMod(start, ALL.length); load(cur()); }

    Scene cur() { return ALL[index]; }
    /** The caller keeps the listener in a field: it is held weakly, so closed panels can be freed. */
    void listen(Listener l) { listeners.add(new java.lang.ref.WeakReference<>(l)); }
    private void tell() {
        for (java.util.Iterator<java.lang.ref.WeakReference<Listener>> it = listeners.iterator(); it.hasNext(); ) {
            Listener l = it.next().get();
            if (l == null) it.remove(); else l.onScene();
        }
    }

    void show(int i, boolean animate) {
        int to = Math.floorMod(i, ALL.length);
        if (to == index && prev == null) return;
        if (fade != null) fade.cancel();
        prev = cur();
        index = to;
        load(cur());
        if (!animate) { done(); return; }
        mix = 0f;
        fade = ValueAnimator.ofFloat(0f, 1f).setDuration(2400);
        fade.setInterpolator(Ui.EASE);
        fade.addUpdateListener(a -> { mix = (float) a.getAnimatedValue(); tell(); });
        fade.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) { done(); }
        });
        fade.start();
    }

    private void done() {
        mix = 1f;
        Scene old = prev;
        prev = null;
        for (Scene s : ALL) if (s != cur() && s != ALL[(index + 1) % ALL.length]) free(s);
        if (old != null && old != cur()) free(old);
        tell();
        // the next one is decoded quietly in the background, so its crossfade starts instantly
        final Scene next = ALL[(index + 1) % ALL.length];
        new Thread(() -> load(next), "PureTV-wallpaper").start();
    }

    /** An app is on screen: keep only the wallpaper Home shows when it comes back. */
    void trim() {
        if (fade != null && fade.isRunning()) return;
        for (Scene s : ALL) if (s != cur()) free(s);
    }

    private static void free(Scene s) {
        synchronized (s) { s.photo = null; s.fg = null; s.glass = null; }   // recycled by the system when nothing draws them
    }

    void load(Scene s) {
        synchronized (s) {
            if (s.photo == null) s.photo = decode("wall/" + s.id + ".jpg", Bitmap.Config.RGB_565);
            if (s.fg == null) s.fg = decode("wall/" + s.id + "-fg.webp", Bitmap.Config.ARGB_8888);
            if (s.glass == null) s.glass = decode("wall/" + s.id + "-glass.jpg", Bitmap.Config.ARGB_8888);
        }
    }

    Bitmap thumb(Scene s) { return decode("wall/" + s.id + "-t.jpg", Bitmap.Config.RGB_565); }

    private Bitmap decode(String path, Bitmap.Config cfg) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = cfg;
        try (InputStream in = c.getAssets().open(path)) { return BitmapFactory.decodeStream(in, null, o); }
        catch (Exception e) { return null; }
    }
}
