package tv.pure.home;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

/** Simple line icons, drawn into a square (x, y, size), white unless the paint says otherwise. */
final class Icons {
    private Icons() { }

    static Paint stroke(float w, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(w); p.setColor(color);
        p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        return p;
    }

    /** The Settings gear: grey teeth, a light face and a three-spoke wheel, like the phone icon it recalls. */
    static void gear(Canvas c, float cx, float cy, float r) {
        Path teeth = new Path();
        int n = 36;
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = a0 + Math.PI / n;
            float ro = r, ri = r * .9f;
            if (i == 0) teeth.moveTo(cx + (float) Math.cos(a0) * ro, cy + (float) Math.sin(a0) * ro);
            teeth.lineTo(cx + (float) Math.cos(a0) * ro, cy + (float) Math.sin(a0) * ro);
            teeth.lineTo(cx + (float) Math.cos(a0 + Math.PI / n * .6) * ro, cy + (float) Math.sin(a0 + Math.PI / n * .6) * ro);
            teeth.lineTo(cx + (float) Math.cos(a1) * ri, cy + (float) Math.sin(a1) * ri);
            teeth.lineTo(cx + (float) Math.cos(a1 + Math.PI / n * .6) * ri, cy + (float) Math.sin(a1 + Math.PI / n * .6) * ri);
        }
        teeth.close();
        Paint dark = new Paint(Paint.ANTI_ALIAS_FLAG);
        dark.setShader(new LinearGradient(0, cy - r, 0, cy + r, 0xFF70747C, 0xFF33363C, Shader.TileMode.CLAMP));
        c.drawPath(teeth, dark);
        Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);
        face.setShader(new LinearGradient(0, cy - r, 0, cy + r, 0xFFE9EBEE, 0xFFB9BDC4, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r * .745f, face);
        Paint ring = stroke(r * .093f, 0xFF4A4E56);
        c.drawCircle(cx, cy, r * .58f, ring);
        Paint spoke = stroke(r * .115f, 0xFF4A4E56);
        for (int a : new int[] { -90, 30, 150 }) {
            double t = Math.toRadians(a);
            c.drawLine(cx, cy, cx + (float) Math.cos(t) * r * .56f, cy + (float) Math.sin(t) * r * .56f, spoke);
        }
        Paint hub = new Paint(Paint.ANTI_ALIAS_FLAG); hub.setColor(0xFF4A4E56);
        c.drawCircle(cx, cy, r * .18f, hub);
    }

    /** A line icon by name, in a box of side s at (x, y). */
    static void draw(Canvas c, String name, float x, float y, float s, int color) {
        Paint p = stroke(s * .085f, color);
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG); f.setColor(color);
        float u = s / 24f;
        RectF r = new RectF();
        switch (name) {
            case "wifi":
                for (int i = 0; i < 3; i++) {
                    float rad = (5 + i * 4.5f) * u;
                    r.set(x + 12 * u - rad, y + 19 * u - rad, x + 12 * u + rad, y + 19 * u + rad);
                    c.drawArc(r, 225, 90, false, p);
                }
                c.drawCircle(x + 12 * u, y + 19 * u, 1.6f * u, f);
                break;
            case "bluetooth": {
                Path b = new Path();
                b.moveTo(x + 7 * u, y + 7.5f * u); b.lineTo(x + 16.5f * u, y + 16.5f * u); b.lineTo(x + 12 * u, y + 20.5f * u);
                b.lineTo(x + 12 * u, y + 3.5f * u); b.lineTo(x + 16.5f * u, y + 7.5f * u); b.lineTo(x + 7 * u, y + 16.5f * u);
                c.drawPath(b, p);
                break;
            }
            case "display": case "inputs":
                r.set(x + 3 * u, y + 5 * u, x + 21 * u, y + 16 * u);
                c.drawRoundRect(r, 2 * u, 2 * u, p);
                c.drawLine(x + 8 * u, y + 20 * u, x + 16 * u, y + 20 * u, p);
                if (name.equals("inputs")) { c.drawLine(x + 9 * u, y + 10.5f * u, x + 15 * u, y + 10.5f * u, p); c.drawLine(x + 12.5f * u, y + 8 * u, x + 15 * u, y + 10.5f * u, p); c.drawLine(x + 12.5f * u, y + 13 * u, x + 15 * u, y + 10.5f * u, p); }
                break;
            case "home":
                for (int i = 0; i < 4; i++) {
                    float ox = x + (i % 2 == 0 ? 4 : 13) * u, oy = y + (i < 2 ? 4 : 13) * u;
                    r.set(ox, oy, ox + 7 * u, oy + 7 * u);
                    c.drawRoundRect(r, 2 * u, 2 * u, f);
                }
                break;
            case "appearance":
                c.drawCircle(x + 12 * u, y + 12 * u, 8.5f * u, p);
                r.set(x + 3.5f * u, y + 3.5f * u, x + 20.5f * u, y + 20.5f * u);
                c.drawArc(r, -90, 180, true, f);
                break;
            case "wallpaper": {
                r.set(x + 3 * u, y + 5 * u, x + 21 * u, y + 19 * u);
                c.drawRoundRect(r, 2.5f * u, 2.5f * u, p);
                Path m = new Path();
                m.moveTo(x + 5 * u, y + 17 * u); m.lineTo(x + 10 * u, y + 11 * u); m.lineTo(x + 13.5f * u, y + 15 * u);
                m.lineTo(x + 15.5f * u, y + 13 * u); m.lineTo(x + 19 * u, y + 17 * u);
                c.drawPath(m, p);
                c.drawCircle(x + 16 * u, y + 9 * u, 1.6f * u, f);
                break;
            }
            case "weather":
                c.drawCircle(x + 9 * u, y + 14 * u, 4.5f * u, f);
                c.drawCircle(x + 14.5f * u, y + 11.5f * u, 5.5f * u, f);
                r.set(x + 4.5f * u, y + 13 * u, x + 20 * u, y + 18.5f * u);
                c.drawRoundRect(r, 2.75f * u, 2.75f * u, f);
                break;
            case "profile":
                c.drawCircle(x + 12 * u, y + 8.5f * u, 4 * u, f);
                r.set(x + 4.5f * u, y + 13.5f * u, x + 19.5f * u, y + 27 * u);
                c.drawArc(r, 180, 180, true, f);
                break;
            case "updates":
                c.drawLine(x + 12 * u, y + 4 * u, x + 12 * u, y + 15 * u, p);
                c.drawLine(x + 7 * u, y + 10.5f * u, x + 12 * u, y + 15.5f * u, p);
                c.drawLine(x + 17 * u, y + 10.5f * u, x + 12 * u, y + 15.5f * u, p);
                c.drawLine(x + 5 * u, y + 20 * u, x + 19 * u, y + 20 * u, p);
                break;
            case "googletv":
                r.set(x + 3.5f * u, y + 5 * u, x + 20.5f * u, y + 16.5f * u);
                c.drawRoundRect(r, 2.2f * u, 2.2f * u, p);
                c.drawLine(x + 8.5f * u, y + 20 * u, x + 15.5f * u, y + 20 * u, p);
                c.drawLine(x + 10 * u, y + 8.5f * u, x + 8 * u, y + 11 * u, p); c.drawLine(x + 8 * u, y + 11 * u, x + 10 * u, y + 13.5f * u, p);
                c.drawLine(x + 8 * u, y + 11 * u, x + 14 * u, y + 11 * u, p);
                break;
            case "about":
                c.drawCircle(x + 12 * u, y + 12 * u, 9.5f * u, p);
                c.drawLine(x + 12 * u, y + 11 * u, x + 12 * u, y + 17 * u, p);
                c.drawCircle(x + 12 * u, y + 7.6f * u, 1.2f * u, f);
                break;
            case "lock": {
                r.set(x + 5 * u, y + 10 * u, x + 19 * u, y + 21 * u);
                c.drawRoundRect(r, 1.5f * u, 1.5f * u, f);
                r.set(x + 7.5f * u, y + 3 * u, x + 16.5f * u, y + 15 * u);
                c.drawArc(r, 180, 180, false, p);
                c.drawLine(x + 7.5f * u, y + 9 * u, x + 7.5f * u, y + 10.5f * u, p);
                c.drawLine(x + 16.5f * u, y + 9 * u, x + 16.5f * u, y + 10.5f * u, p);
                break;
            }
            case "search":
                c.drawCircle(x + 10.5f * u, y + 10.5f * u, 6.5f * u, p);
                c.drawLine(x + 15.3f * u, y + 15.3f * u, x + 20 * u, y + 20 * u, p);
                break;
            case "clean": {
                Path sh = new Path();
                sh.moveTo(x + 12 * u, y + 3 * u); sh.lineTo(x + 19.5f * u, y + 6 * u); sh.lineTo(x + 19 * u, y + 13 * u);
                sh.quadTo(x + 18 * u, y + 18.5f * u, x + 12 * u, y + 21.5f * u); sh.quadTo(x + 6 * u, y + 18.5f * u, x + 5 * u, y + 13 * u);
                sh.lineTo(x + 4.5f * u, y + 6 * u); sh.close();
                c.drawPath(sh, f);
                Paint ck = stroke(s * .085f, 0xFF27A446);
                c.drawLine(x + 8.5f * u, y + 12.5f * u, x + 11 * u, y + 15 * u, ck); c.drawLine(x + 11 * u, y + 15 * u, x + 15.5f * u, y + 9.5f * u, ck);
                break;
            }
            case "check":
                c.drawLine(x + 5 * u, y + 12.5f * u, x + 9.5f * u, y + 17 * u, p);
                c.drawLine(x + 9.5f * u, y + 17 * u, x + 19 * u, y + 7.5f * u, p);
                break;
            case "chev":
                c.drawLine(x + 9 * u, y + 5 * u, x + 16 * u, y + 12 * u, p);
                c.drawLine(x + 16 * u, y + 12 * u, x + 9 * u, y + 19 * u, p);
                break;
            case "cc":   // Control Center: two switches
                r.set(x + 3 * u, y + 4 * u, x + 21 * u, y + 11 * u); c.drawRoundRect(r, 3.5f * u, 3.5f * u, p);
                r.set(x + 3 * u, y + 13 * u, x + 21 * u, y + 20 * u); c.drawRoundRect(r, 3.5f * u, 3.5f * u, p);
                c.drawCircle(x + 16.5f * u, y + 7.5f * u, 1.7f * u, f); c.drawCircle(x + 7.5f * u, y + 16.5f * u, 1.7f * u, f);
                break;
            case "gearmono": {
                Path g = new Path();
                for (int i = 0; i < 8; i++) {
                    double a0 = Math.PI * 2 * i / 8;
                    float cx = x + 12 * u, cy = y + 12 * u;
                    r.set(cx + (float) Math.cos(a0) * 8.6f * u - 1.9f * u, cy + (float) Math.sin(a0) * 8.6f * u - 1.9f * u,
                          cx + (float) Math.cos(a0) * 8.6f * u + 1.9f * u, cy + (float) Math.sin(a0) * 8.6f * u + 1.9f * u);
                    Path tooth = new Path(); tooth.addOval(r, Path.Direction.CW);
                    g.op(tooth, Path.Op.UNION);
                }
                Path ring = new Path(); ring.addCircle(x + 12 * u, y + 12 * u, 7.6f * u, Path.Direction.CW);
                g.op(ring, Path.Op.UNION);
                Path hole = new Path(); hole.addCircle(x + 12 * u, y + 12 * u, 3.2f * u, Path.Direction.CW);
                g.op(hole, Path.Op.DIFFERENCE);
                c.drawPath(g, f);
                break;
            }
            case "power":
                c.drawLine(x + 12 * u, y + 3 * u, x + 12 * u, y + 11 * u, p);
                r.set(x + 4 * u, y + 5 * u, x + 20 * u, y + 21 * u); c.drawArc(r, -55, 290, false, p);
                break;
            case "moon": {
                Path m = new Path();
                r.set(x + 3.5f * u, y + 3.5f * u, x + 20.5f * u, y + 20.5f * u);
                m.addOval(r, Path.Direction.CW);
                Path cut = new Path(); cut.addCircle(x + 16 * u, y + 7.5f * u, 7.5f * u, Path.Direction.CW);
                m.op(cut, Path.Op.DIFFERENCE);
                c.drawPath(m, f);
                break;
            }
            case "audio":
                r.set(x + 4 * u, y + 4.5f * u, x + 20 * u, y + 20.5f * u); c.drawArc(r, 135, 270, false, p);
                r.set(x + 7.5f * u, y + 8 * u, x + 16.5f * u, y + 17 * u); c.drawArc(r, 135, 270, false, p);
                { Path t = new Path(); t.moveTo(x + 12 * u, y + 14 * u); t.lineTo(x + 16.5f * u, y + 20 * u); t.lineTo(x + 7.5f * u, y + 20 * u); t.close(); c.drawPath(t, f); }
                break;
            case "sleep":
                c.drawCircle(x + 12 * u, y + 13 * u, 8 * u, p);
                c.drawLine(x + 12 * u, y + 13 * u, x + 15.5f * u, y + 9.5f * u, p);
                c.drawLine(x + 12 * u, y + 3 * u, x + 12 * u, y + 5 * u, p);
                break;
            case "memory":
                r.set(x + 5 * u, y + 5 * u, x + 19 * u, y + 19 * u); c.drawRoundRect(r, 2 * u, 2 * u, p);
                r.set(x + 9 * u, y + 9 * u, x + 15 * u, y + 15 * u); c.drawRoundRect(r, 1 * u, 1 * u, p);
                for (int i = 0; i < 2; i++) {
                    float o = (i == 0 ? 9 : 15) * u;
                    c.drawLine(x + o, y + 2 * u, x + o, y + 5 * u, p); c.drawLine(x + o, y + 19 * u, x + o, y + 22 * u, p);
                    c.drawLine(x + 2 * u, y + o, x + 5 * u, y + o, p); c.drawLine(x + 19 * u, y + o, x + 22 * u, y + o, p);
                }
                break;
            case "saver": {
                Path m = new Path();
                m.moveTo(x + 3 * u, y + 18 * u); m.lineTo(x + 8.5f * u, y + 11 * u); m.lineTo(x + 12.5f * u, y + 16 * u);
                m.lineTo(x + 15.5f * u, y + 12.5f * u); m.lineTo(x + 21 * u, y + 18 * u); m.close();
                c.drawPath(m, f);
                c.drawCircle(x + 16.5f * u, y + 7 * u, 2 * u, p);
                break;
            }
            case "sound": {
                Path sp = new Path();
                sp.moveTo(x + 4 * u, y + 9 * u); sp.lineTo(x + 8 * u, y + 9 * u); sp.lineTo(x + 13 * u, y + 5 * u);
                sp.lineTo(x + 13 * u, y + 19 * u); sp.lineTo(x + 8 * u, y + 15 * u); sp.lineTo(x + 4 * u, y + 15 * u); sp.close();
                c.drawPath(sp, f);
                r.set(x + 11.5f * u, y + 7 * u, x + 21.5f * u, y + 17 * u); c.drawArc(r, -50, 100, false, p);
                break;
            }
            case "access":
                c.drawCircle(x + 12 * u, y + 12 * u, 9.5f * u, p);
                c.drawCircle(x + 12 * u, y + 7.3f * u, 1.4f * u, f);
                c.drawLine(x + 7.5f * u, y + 10 * u, x + 16.5f * u, y + 10 * u, p);
                c.drawLine(x + 12 * u, y + 10.5f * u, x + 12 * u, y + 14.5f * u, p);
                c.drawLine(x + 12 * u, y + 14.5f * u, x + 9.8f * u, y + 18.5f * u, p);
                c.drawLine(x + 12 * u, y + 14.5f * u, x + 14.2f * u, y + 18.5f * u, p);
                break;
            case "sun": {
                Paint y2 = new Paint(Paint.ANTI_ALIAS_FLAG); y2.setColor(0xFFFFD60A);
                Paint ys = stroke(s * .075f, 0xFFFFD60A);
                c.drawCircle(x + 12 * u, y + 12 * u, 4.2f * u, y2);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4;
                    c.drawLine(x + 12 * u + (float) Math.cos(a) * 7 * u, y + 12 * u + (float) Math.sin(a) * 7 * u,
                               x + 12 * u + (float) Math.cos(a) * 9.6f * u, y + 12 * u + (float) Math.sin(a) * 9.6f * u, ys);
                }
                break;
            }
            case "cloud": case "rain": case "partly": case "snow": {
                if (name.equals("partly")) draw(c, "sun", x - 4 * u, y - 4 * u, s * .8f, color);
                Paint w = new Paint(Paint.ANTI_ALIAS_FLAG); w.setColor(0xFFE5E7EE);
                float oy = name.equals("rain") || name.equals("snow") ? -3 * u : 0;
                c.drawCircle(x + 8.5f * u, y + 15 * u + oy, 3.6f * u, w);
                c.drawCircle(x + 13 * u, y + 12 * u + oy, 5.5f * u, w);
                r.set(x + 5 * u, y + 14 * u + oy, x + 21 * u, y + 18.6f * u + oy); c.drawRoundRect(r, 2.3f * u, 2.3f * u, w);
                if (name.equals("rain")) { Paint b = stroke(s * .075f, 0xFF64D2FF); for (int i = 0; i < 3; i++) c.drawLine(x + (9 + 4 * i) * u, y + 18 * u, x + (8 + 4 * i) * u, y + 21 * u, b); }
                if (name.equals("snow")) { Paint b = new Paint(Paint.ANTI_ALIAS_FLAG); b.setColor(0xFFFFFFFF); for (int i = 0; i < 3; i++) c.drawCircle(x + (9 + 4 * i) * u, y + 19.5f * u, 1.1f * u, b); }
                break;
            }
            case "up":
                c.drawLine(x + 4 * u, y + 16 * u, x + 12 * u, y + 8 * u, p);
                c.drawLine(x + 12 * u, y + 8 * u, x + 20 * u, y + 16 * u, p);
                break;
            default:
                c.drawCircle(x + 12 * u, y + 12 * u, 3 * u, f);
        }
    }
}
