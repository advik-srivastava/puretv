package tv.pure.home;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** The weather in the date line, from open-meteo.com (no account). The place is the city picked, or found from the connection. */
final class Weather {
    private final MainActivity a;
    private final Runnable changed;
    private String line = "";
    private long at;
    private double[] here;
    private boolean busy;

    Weather(MainActivity a, Runnable changed) { this.a = a; this.changed = changed; }

    String line() { return "off".equals(a.store.weather) ? "" : line; }

    void refresh(boolean force) {
        if ("off".equals(a.store.weather) || busy) return;
        if (!force && System.currentTimeMillis() - at < 30 * 60000) return;
        busy = true;
        final boolean city = "city".equals(a.store.weather);
        final int ci = a.store.city;
        new Thread(() -> {
            try {
                double[] p = city ? Store.CITY_AT[Math.max(0, Math.min(ci, Store.CITY_AT.length - 1))] : here;
                if (p == null) {
                    JSONObject g = new JSONObject(get("https://ipapi.co/json/"));
                    p = here = new double[] { g.getDouble("latitude"), g.getDouble("longitude") };
                }
                JSONObject j = new JSONObject(get(String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code&timezone=auto", p[0], p[1])));
                JSONObject cur = j.getJSONObject("current");
                int code = cur.getInt("weather_code");
                final String l = icon(code) + " " + Math.round(cur.getDouble("temperature_2m")) + "° " + word(code);
                at = System.currentTimeMillis();
                a.ui.post(() -> { line = l; changed.run(); });
            } catch (Exception ignored) {
            } finally { busy = false; }
        }, "PureTV-weather").start();
    }

    private static String icon(int c) { return c <= 1 ? "☀︎" : c <= 3 || c == 45 || c == 48 ? "☁︎" : c >= 71 && c <= 77 ? "❄︎" : "☂︎"; }
    private static String word(int c) {
        return c == 0 ? "Clear" : c <= 2 ? "Partly Cloudy" : c == 3 ? "Cloudy" : c <= 48 ? "Fog" : c <= 57 ? "Drizzle" : c <= 67 ? "Rain"
                : c <= 77 ? "Snow" : c <= 82 ? "Showers" : "Thunderstorms";
    }

    private static String get(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(10000); h.setReadTimeout(10000);
        h.setRequestProperty("User-Agent", "PureTV");
        try (InputStream in = h.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) > 0 && out.size() < 200000) out.write(b, 0, n);
            return out.toString("UTF-8");
        } finally { h.disconnect(); }
    }
}
