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
    /** The reading now and five days ahead, for the weather panel and Control Center. */
    int temp, code = -1;
    final java.util.List<String> dayName = new java.util.ArrayList<>();
    final java.util.List<int[]> days = new java.util.ArrayList<>();   // {high, low, code}
    boolean has() { return code >= 0 && !"off".equals(a.store.weather); }
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
                        "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=5&timezone=auto", p[0], p[1])));
                JSONObject cur = j.getJSONObject("current");
                int code = cur.getInt("weather_code");
                final String l = icon(code) + " " + Math.round(cur.getDouble("temperature_2m")) + "° " + word(code);
                final int t = (int) Math.round(cur.getDouble("temperature_2m"));
                final java.util.List<String> names = new java.util.ArrayList<>();
                final java.util.List<int[]> d = new java.util.ArrayList<>();
                org.json.JSONObject daily = j.optJSONObject("daily");
                if (daily != null) {
                    org.json.JSONArray dt = daily.getJSONArray("time"), hi = daily.getJSONArray("temperature_2m_max"),
                            lo = daily.getJSONArray("temperature_2m_min"), wc = daily.getJSONArray("weather_code");
                    java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US), out = new java.text.SimpleDateFormat("EEE", Locale.getDefault());
                    for (int i = 0; i < dt.length(); i++) {
                        names.add(i == 0 ? "Today" : out.format(in.parse(dt.getString(i))));
                        d.add(new int[] { (int) Math.round(hi.getDouble(i)), (int) Math.round(lo.getDouble(i)), wc.getInt(i) });
                    }
                }
                at = System.currentTimeMillis();
                a.ui.post(() -> {
                    line = l; temp = t; Weather.this.code = code;
                    dayName.clear(); dayName.addAll(names); days.clear(); days.addAll(d);
                    changed.run();
                });
            } catch (Exception ignored) {
            } finally { busy = false; }
        }, "PureTV-weather").start();
    }

    private static String icon(int c) { return c <= 1 ? "☀︎" : c <= 3 || c == 45 || c == 48 ? "☁︎" : c >= 71 && c <= 77 ? "❄︎" : "☂︎"; }
    /** Icons.java weather icon for a WMO code. */
    static String iconName(int c) { return c <= 1 ? "sun" : c == 2 ? "partly" : c == 3 || c == 45 || c == 48 ? "cloud" : c >= 71 && c <= 77 ? "snow" : "rain"; }
    String now() { return word(code); }

    static String word(int c) {
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
