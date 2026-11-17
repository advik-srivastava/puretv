package tv.pure.home;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** PureTV's own settings: profiles (each with its own top row and hidden apps), wallpaper, glass and more. */
final class Store {
    static final String[] EMOJIS = { "🦊", "🐼", "🐯", "🦁", "🐸", "🐨", "🐙", "🦄", "🐶", "🐱" };
    static final String[] EMOJI_BG = { "#ff9f0a", "#d1d1d6", "#ff9500", "#ffcc00", "#34c759", "#8e8e93", "#ff2d55", "#bf5af2", "#a2845e", "#ffd60a" };
    static final String[] CITIES = { "New Delhi", "Mumbai", "London", "New York", "Tokyo", "Sydney" };
    static final double[][] CITY_AT = { { 28.61, 77.21 }, { 19.08, 72.88 }, { 51.51, -0.13 }, { 40.71, -74.01 }, { 35.68, 139.69 }, { -33.87, 151.21 } };

    static final class Profile {
        String name = "Me", emoji = "🦊", bg = "#ff9f0a";
        boolean kids;
        List<String> top = new ArrayList<>();
        Set<String> hidden = new LinkedHashSet<>();
    }

    final List<Profile> profiles = new ArrayList<>();
    int current, scene, dock = 5, city, saverAfter = 90;   // seconds on Home before the screen saver; 0 = never
    boolean dnd;
    final List<String> recents = new ArrayList<>();          // apps opened from PureTV, newest first
    boolean rotate = true, sounds = true, setupDone, reduceMotion;
    String glass = "liquid", weather = "auto", pin = "1234";

    private final SharedPreferences p;

    Store(Context c) {
        p = c.getSharedPreferences("puretv", Context.MODE_PRIVATE);
        load();
    }

    Profile me() { return profiles.get(Math.max(0, Math.min(current, profiles.size() - 1))); }

    void load() {
        profiles.clear();
        try {
            JSONObject o = new JSONObject(p.getString("state", "{}"));
            JSONArray ps = o.optJSONArray("profiles");
            for (int i = 0; ps != null && i < ps.length(); i++) {
                JSONObject j = ps.getJSONObject(i);
                Profile pr = new Profile();
                pr.name = j.optString("name", pr.name); pr.emoji = j.optString("emoji", pr.emoji); pr.bg = j.optString("bg", pr.bg);
                pr.kids = j.optBoolean("kids");
                JSONArray t = j.optJSONArray("top"), h = j.optJSONArray("hidden");
                for (int k = 0; t != null && k < t.length(); k++) pr.top.add(t.getString(k));
                for (int k = 0; h != null && k < h.length(); k++) pr.hidden.add(h.getString(k));
                profiles.add(pr);
            }
            current = o.optInt("current"); scene = o.optInt("scene"); dock = o.optInt("dock", 5); city = o.optInt("city");
            rotate = o.optBoolean("rotate", true); sounds = o.optBoolean("sounds", true); setupDone = o.optBoolean("setupDone");
            reduceMotion = o.optBoolean("reduceMotion"); glass = o.optString("glass", "liquid"); weather = o.optString("weather", "auto");
            pin = o.optString("pin", "1234");
            saverAfter = o.optInt("saverAfter", 90); dnd = o.optBoolean("dnd");
            JSONArray r = o.optJSONArray("recents");
            recents.clear();
            for (int k = 0; r != null && k < r.length(); k++) recents.add(r.getString(k));
        } catch (Exception ignored) { }
        if (profiles.isEmpty()) {
            Profile a = new Profile(); profiles.add(a);
            Profile k = new Profile(); k.name = "Kids"; k.emoji = "🐯"; k.bg = "#ff9500"; k.kids = true; profiles.add(k);
        }
        Ui.soundsOn = sounds && !dnd;
    }

    void save() {
        try {
            JSONObject o = new JSONObject();
            JSONArray ps = new JSONArray();
            for (Profile pr : profiles) {
                JSONObject j = new JSONObject();
                j.put("name", pr.name).put("emoji", pr.emoji).put("bg", pr.bg).put("kids", pr.kids)
                        .put("top", new JSONArray(pr.top)).put("hidden", new JSONArray(pr.hidden));
                ps.put(j);
            }
            o.put("profiles", ps).put("current", current).put("scene", scene).put("dock", dock).put("city", city)
                    .put("rotate", rotate).put("sounds", sounds).put("setupDone", setupDone).put("reduceMotion", reduceMotion)
                    .put("glass", glass).put("weather", weather).put("pin", pin)
                    .put("saverAfter", saverAfter).put("dnd", dnd).put("recents", new JSONArray(recents));
            p.edit().putString("state", o.toString()).apply();
        } catch (Exception ignored) { }
        Ui.soundsOn = sounds && !dnd;
    }

    String json() { save(); return p.getString("state", "{}"); }
    void restore(String json) { p.edit().putString("state", json).apply(); load(); }
}
