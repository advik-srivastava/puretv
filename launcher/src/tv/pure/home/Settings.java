package tv.pure.home;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Settings, laid out like tvOS: a sidebar on the left, the chosen page on the right. */
final class Settings {
    private final MainActivity a;
    private final FrameLayout holder;
    private final LinearLayout side, page;
    private final ScrollView pageScroll;
    private String current = "";
    private final List<View> sideItems = new ArrayList<>();

    // the PureTV 2 sidebar
    private static final String[][] SIDE = {
        { "network", "Wi-Fi", "wifi", "#5AA8FF", "#1C64D6" }, { "bluetooth", "Bluetooth", "bluetooth", "#57A8FF", "#1C64D6" },
        { "display", "Display & Sound", "display", "#5AA8FF", "#2A6FE6" },
        { "", "", "", "", "" },
        { "homescreen", "Home Screen", "home", "#5AA8FF", "#1C64D6" }, { "appearance", "Appearance", "appearance", "#7FD8FF", "#2AA4E6" },
        { "wallpaper", "Wallpaper", "wallpaper", "#5FE0C8", "#1AA58E" }, { "weather", "Weather", "weather", "#6FC3FF", "#2A86E6" },
        { "saverpg", "Screen Saver", "moon", "#7D86FF", "#4B4FE0" },
        { "", "", "", "", "" },
        { "switched", "Clean Up", "clean", "#6AE087", "#27A446" }, { "googletv", "Google TV", "googletv", "#8E98AB", "#4F5869" },
        { "updates", "Software Updates", "updates", "#9AA0AB", "#5F6571" }, { "about", "About", "about", "#9AA0AB", "#5F6571" },
    };

    Settings(MainActivity a) {
        this.a = a;
        holder = new FrameLayout(a);
        holder.setClickable(true);
        holder.setBackgroundColor(0x4D000000);

        side = new LinearLayout(a);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(Ui.px(24), Ui.px(30), Ui.px(24), Ui.px(24));
        side.setBackground(new Glass(side, a.scenes, 44));
        side.setClipChildren(false);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(Ui.px(560), -1);
        slp.leftMargin = Ui.px(48); slp.topMargin = Ui.px(48); slp.bottomMargin = Ui.px(48);
        holder.addView(side, slp);

        page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Ui.px(20), Ui.px(20), Ui.px(40), Ui.px(60));
        page.setClipChildren(false);
        pageScroll = new ScrollView(a);
        pageScroll.setVerticalScrollBarEnabled(false);
        pageScroll.setClipChildren(false);
        pageScroll.addView(page);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.px(1180), -1);
        plp.leftMargin = Ui.px(660); plp.topMargin = Ui.px(40);
        holder.addView(pageScroll, plp);
        a.updatesRef().onChange = () -> { if ("updates".equals(current)) render(); };
    }

    void show(String at) {
        buildSide();
        a.open(holder, this::back);
        a.darken(.55f);
        String start = at != null ? at : "homescreen";
        render(start);
        holder.post(() -> { for (View v : sideItems) if (start.equals(v.getTag())) { v.requestFocus(); return; } });
    }

    private void back() {
        View f = a.getCurrentFocus();
        if (f != null && MainActivity.isIn(f, page)) { for (View v : sideItems) if (current.equals(v.getTag())) { v.requestFocus(); return; } }
        a.close(holder);
    }

    private void buildSide() {
        side.removeAllViews();
        sideItems.clear();
        Store.Profile me = a.store.me();
        Btn search = new Btn(a, 30, 1.03f);   // the search field at the top of the sidebar
        search.setPadding(Ui.px(22), 0, Ui.px(22), 0);
        search.icon(a, "search", 26, 14);
        search.addView(search.text(a, "Search", 24, true));
        search.action = () -> a.toast("Type or say what you are looking for, like \"wallpaper\" or \"Wi-Fi\"");
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, Ui.px(62)); sl.bottomMargin = Ui.px(14);
        side.addView(search, sl);
        Row card = new Row(a, new Row.Spec("profile", possessive(me.name) + " PureTV").sub("Profile & Backup").emoji(me.emoji, me.bg), 500);
        card.setTag("profile");
        addSide(card);
        View gap = new View(a);
        side.addView(gap, new LinearLayout.LayoutParams(1, Ui.px(10)));
        for (String[] s : SIDE) {
            if (s[0].isEmpty()) { side.addView(new View(a), new LinearLayout.LayoutParams(1, Ui.px(14))); continue; }
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] { Ui.parse(s[3], 0), Ui.parse(s[4], 0) });
            g.setCornerRadius(Ui.fx(11));
            Row r = new Row(a, new Row.Spec(s[0], s[1]).icon(s[2], 0), 500);
            ((View) r.getChildAt(0)).setBackground(g);
            r.setTag(s[0]);
            addSide(r);
        }
    }

    private void addSide(Row r) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.px(4);
        r.setMinimumHeight(Ui.px(62));
        r.setPadding(r.getPaddingLeft(), Ui.px(10), r.getPaddingRight(), Ui.px(10));
        side.addView(r, lp);
        sideItems.add(r);
        final String key = (String) r.getTag();
        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            final View.OnFocusChangeListener base = r.getOnFocusChangeListener();
            @Override public void onFocusChange(View v, boolean f) { if (base != null) base.onFocusChange(v, f); if (f) render(key); }
        });
        r.action = () -> { if (page.getChildCount() > 1) { for (int i = 1; i < page.getChildCount(); i++) if (page.getChildAt(i) instanceof Row) { page.getChildAt(i).requestFocus(); return; } } };
        r.setNextFocusRightId(View.NO_ID);
    }

    private void render(String key) { if (!key.equals(current)) { current = key; render(); pageScroll.scrollTo(0, 0); } }

    /** Rebuild the page, keeping focus on the same row. */
    private void render() {
        View f = a.getCurrentFocus();
        String keep = f instanceof Row && MainActivity.isIn(f, page) ? ((Row) f).key : null;
        page.removeAllViews();
        TextView title = Ui.text(a, 52, Ui.heavy, 0xFFFFFFFF);
        title.setPadding(Ui.px(14), Ui.px(10), 0, Ui.px(8));
        page.addView(title);
        Store s = a.store;
        Store.Profile me = s.me();
        switch (current) {
            case "profile": {
                title.setText("Profiles");
                head("Who's Watching");
                for (int i = 0; i < s.profiles.size(); i++) {
                    final int n = i;
                    Store.Profile p = s.profiles.get(i);
                    row(new Row.Spec("p" + i, p.name).emoji(p.emoji, p.bg).sub(p.kids ? "Kids: approved apps only, Settings need the parent PIN" : null)
                            .checked(i == s.current).on(() -> { s.current = n; s.save(); a.profileChanged(); buildSide(); render(); }));
                }
                head("This Profile");
                row(new Row.Spec("name", "Name").value(me.name).go().on(() -> Menus.editName(a, () -> { buildSide(); render(); })));
                row(new Row.Spec("emoji", "Avatar").emoji(me.emoji, me.bg).value("Change").on(() -> {
                    int i = 0;
                    for (int k = 0; k < Store.EMOJIS.length; k++) if (Store.EMOJIS[k].equals(me.emoji)) i = (k + 1) % Store.EMOJIS.length;
                    me.emoji = Store.EMOJIS[i]; me.bg = Store.EMOJI_BG[i]; s.save(); a.profileChanged(); buildSide(); render();
                }));
                if (s.profiles.size() < 5) row(new Row.Spec("add", "Add Profile").on(() -> {
                    Store.Profile p = new Store.Profile();
                    int i = (s.profiles.size() * 3) % Store.EMOJIS.length;
                    p.name = "Profile " + (s.profiles.size() + 1); p.emoji = Store.EMOJIS[i]; p.bg = Store.EMOJI_BG[i];
                    s.profiles.add(p); s.save(); render(); a.toast(p.name + " added");
                }));
                head("Backup");
                long when = new java.io.File(a.getFilesDir(), "backup.json").lastModified();
                row(new Row.Spec("bnow", "Back Up Now").value(when == 0 ? "Never" : new java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.getDefault()).format(new java.util.Date(when)))
                        .on(() -> { if (backup(a)) { render(); a.toast("Backed up: profiles, top row, hidden apps and settings"); } else a.toast("The backup could not be saved"); }));
                row(new Row.Spec("brestore", "Restore from Backup").on(() -> {
                    String j = readBackup(a);
                    if (j == null) { a.toast("There is no backup yet"); return; }
                    a.store.restore(j); Glass.style = a.store.glass; a.scenes.show(a.store.scene, false); a.profileChanged(); buildSide(); render();
                    a.toast("Restored from your backup");
                }));
                head("Kids");
                row(new Row.Spec("pin", "Parent PIN").sub("Needed to leave the Kids profile or open Settings from it").value("Change")
                        .on(() -> Menus.pin(a, true, null)));
                foot("Each profile has its own name, avatar, top row and hidden apps. Backups hold your profiles, top row, hidden apps and settings, and stay on this TV.");
                break;
            }
            case "homescreen": {
                title.setText("Home Screen");
                head("Apps");
                row(new Row.Spec("dock", "Apps in the Top Row").value(String.valueOf(s.dock)).on(() -> { s.dock = s.dock >= 7 ? 4 : s.dock + 1; s.save(); a.buildHome(); render(); }));
                row(new Row.Spec("arrange", "Move or Hide Apps").value("Hold OK on an app").on(() -> a.toast("On the Home screen, hold OK on any app to move or hide it")));
                row(new Row.Spec("hidden", "Hidden Apps").value(me.hidden.isEmpty() ? "None" : String.valueOf(me.hidden.size())).on(() -> {
                    if (me.hidden.isEmpty()) { a.toast("Hold OK on an app to hide it from Home"); return; }
                    int n = me.hidden.size(); me.hidden.clear(); s.save(); a.buildHome(); render();
                    a.toast(n + " hidden app" + (n > 1 ? "s are" : " is") + " back on Home");
                }));
                head("Home Button");
                boolean home = Tv.isHome(a), helper = HomeHelper.enabled(a);
                row(new Row.Spec("hb", "Home Button Opens PureTV").value(home ? "Yes" : helper ? "Through the Remote Helper" : "No")
                        .sub(home ? "PureTV is this TV's only Home screen" : "Turn on the Remote Helper, or let the PureTV agent set it up")
                        .on(() -> { if (!home && !helper) Tv.settings(a, "access"); }));
                head("Setup");
                row(new Row.Spec("setup", "Run Setup Again").on(() -> { a.closeAll(); new Setup(a).show(); }));
                break;
            }
            case "appearance": {
                title.setText("Appearance");
                head("Glass");
                String[][] g = { { "liquid", "Liquid Glass", "Clear glass that shows the wallpaper through" }, { "frosted", "Frosted Glass", "Softer, brighter panels" }, { "dark", "Dark", "Deep, dark panels for a dark room" } };
                for (String[] x : g) row(new Row.Spec("g" + x[0], x[1]).sub(x[2]).checked(s.glass.equals(x[0])).on(() -> {
                    s.glass = x[0]; Glass.style = x[0]; s.save(); a.root.invalidate(); a.buildHome(); render(); side.invalidate();
                }));
                head("Motion and Sound");
                row(new Row.Spec("snd", "UI Sounds").sub("Soft clicks as you move and select").value(s.sounds ? "On" : "Off").on(() -> { s.sounds = !s.sounds; s.save(); render(); }));
                row(new Row.Spec("motion", "Reduce Motion").sub("Wallpapers change without a crossfade").value(s.reduceMotion ? "On" : "Off").on(() -> { s.reduceMotion = !s.reduceMotion; s.save(); render(); }));
                break;
            }
            case "wallpaper": {
                title.setText("Wallpaper");
                for (int i = 0; i < Scenes.ALL.length; i++) {
                    final int n = i;
                    Scenes.Scene sc = Scenes.ALL[i];
                    row(new Row.Spec("w" + i, sc.name).thumb(a.scenes.thumb(sc)).checked(a.scenes.index == i).on(() -> {
                        a.scenes.show(n, !s.reduceMotion); s.scene = n; s.save(); a.placeDate(); render();
                    }));
                }
                head("Options");
                row(new Row.Spec("rot", "Change Every 20 Seconds").value(s.rotate ? "On" : "Off").on(() -> { s.rotate = !s.rotate; s.save(); render(); }));
                foot("Photographs from Wikimedia Commons (CC BY-SA): Myrabella, Tyler Cipriani, Diego Delso, Jakub Fryš and Chensiyuan.");
                break;
            }
            case "weather": {
                title.setText("Weather");
                row(new Row.Spec("wa", "Use My Location").sub("Found from your internet connection").checked("auto".equals(s.weather))
                        .on(() -> { s.weather = "auto"; s.save(); a.weather.refresh(true); render(); a.updateTime(); }));
                row(new Row.Spec("wc", "Choose a City").sub("city".equals(s.weather) ? Store.CITIES[s.city] + " · press OK for the next city" : "Press OK to pick")
                        .checked("city".equals(s.weather)).on(() -> {
                            if ("city".equals(s.weather)) s.city = (s.city + 1) % Store.CITIES.length;
                            s.weather = "city"; s.save(); a.weather.refresh(true); render(); a.updateTime();
                        }));
                row(new Row.Spec("wo", "Don't Show Weather").checked("off".equals(s.weather)).on(() -> { s.weather = "off"; s.save(); render(); a.updateTime(); }));
                foot("Weather comes from open-meteo.com. No account, and nothing about you is stored.");
                break;
            }
            case "network": {
                Device.Net n = Device.net(a);
                title.setText(n.kind.equals("Ethernet") ? "Network" : "Wi-Fi");
                if (n.connected) {
                    row(new Row.Spec("net", n.name).icon(n.kind.equals("Ethernet") ? "inputs" : "wifi", 0xFF1C64D6)
                            .sub(n.kind.equals("Ethernet") ? "Wired connection" : Device.bars(n.bars)).checked(true).on(() -> Tv.settings(a, "network")));
                    if (!n.ip.isEmpty()) row(new Row.Spec("ip", "IP Address").value(n.ip));
                } else row(new Row.Spec("off", "Not connected").on(() -> Tv.settings(a, "network")));
                row(new Row.Spec("other", "Other Networks…").go().on(() -> Tv.settings(a, "network")));
                foot(n.kind.equals("Wi-Fi") && "Connected".equals(n.name)
                        ? "To show the network's name, allow PureTV location access (Settings › Apps › PureTV Home › Permissions) and turn Location on. Nothing about your location is used or stored."
                        : "Changes open the TV's own network settings.");
                break;
            }
            case "bluetooth": {
                title.setText("Bluetooth");
                row(new Row.Spec("bton", "Bluetooth").value(Device.btOn(a) ? "On" : "Off").on(() -> Tv.settings(a, "bluetooth")));
                if (Device.btOn(a)) {
                    if (!Device.canBt(a)) row(new Row.Spec("perm", "Show Your Devices").sub("Allow PureTV to see nearby devices")
                            .on(() -> a.requestPermissions(new String[] { android.Manifest.permission.BLUETOOTH_CONNECT }, 9)));
                    else {
                        java.util.List<Device.Bt> list = Device.bt(a);
                        head(list.isEmpty() ? "No Paired Devices" : "My Devices");
                        int i = 0;
                        for (Device.Bt b : list) row(new Row.Spec("d" + i++, b.name).icon("bluetooth", b.connected ? 0xFF1C64D6 : 0xFF5F6571)
                                .sub(b.kind).value(b.connected ? "Connected" : "Not Connected").on(() -> Tv.settings(a, "bluetooth")));
                    }
                }
                head("");
                row(new Row.Spec("pair", "Pair a Remote or Accessory").go().on(() -> Tv.settings(a, "bluetooth")));
                break;
            }
            case "display": {
                title.setText("Display & Sound");
                row(new Row.Spec("pic", "Picture").go().on(() -> Tv.settings(a, "display")));
                row(new Row.Spec("snd", "Sound").value("TV Speakers").go().on(() -> Tv.settings(a, "sound")));
                row(new Row.Spec("tvapps", "Apps").go().on(() -> Tv.settings(a, "apps")));
                row(new Row.Spec("all", "All TV Settings").go().on(() -> Tv.settings(a, "")));
                break;
            }
            case "saverpg": {
                title.setText("Screen Saver");
                row(new Row.Spec("start", "Start Screen Saver").on(() -> { a.closeAll(); a.startSaver(); }));
                int[] opts = { 60, 90, 180, 300, 0 };
                String[] names = { "1 minute", "90 seconds", "3 minutes", "5 minutes", "Never" };
                int cur = 1;
                for (int i = 0; i < opts.length; i++) if (opts[i] == s.saverAfter) cur = i;
                final int ci = cur;
                row(new Row.Spec("after", "Start After").value(names[cur]).on(() -> { s.saverAfter = opts[(ci + 1) % opts.length]; s.save(); render(); }));
                foot("On the Home screen, the wallpapers and the clock fill the screen after this long. Any button brings Home back.");
                break;
            }
            case "switched": {
                title.setText("Clean Up");
                java.util.List<String[]> off = disabledApps(a);
                head("This TV");
                row(new Row.Spec("hoff", "Apps switched off").value(String.valueOf(off.size())));
                row(new Row.Spec("hmem", "Memory in use").value(Panels.memLine(a).replace("RAM ", "")));
                row(new Row.Spec("recheck", "Check After an Update").on(() -> a.toast("After a TV update, ask Claude to check: paste the PureTV link and say \"check my TV\"")));
                if (!off.isEmpty()) {
                    head("Switched Off");
                    for (String[] x : off) row(new Row.Spec("x" + x[0], x[1]).sub(x[0]).value("Off")
                            .on(() -> a.toast("Switched off, not uninstalled. Restore turns it back on")));
                }
                foot("Apps are switched off by the PureTV agent on your computer. Nothing is uninstalled, and their data stays on the TV.");
                break;
            }
            case "tv": {
                title.setText("TV Settings");
                String[][] t = { { "network", "Wi-Fi and Network", "wifi" }, { "bluetooth", "Remotes and Accessories", "bluetooth" }, { "display", "Picture and Display", "display" },
                        { "sound", "Sound", "display" }, { "apps", "Apps", "home" }, { "access", "Accessibility", "about" }, { "date", "Date and Time", "about" }, { "", "All TV Settings", "display" } };
                for (String[] x : t) row(new Row.Spec("t" + x[0], x[1]).icon(x[2], 0xFF5F6571).go().on(() -> Tv.settings(a, x[0])));
                foot("These open the TV's own Settings. PureTV doesn't change them.");
                break;
            }
            case "googletv": {
                title.setText("Google TV");
                String g = Tv.googleHome(a);
                if (g != null) row(new Row.Spec("gh", "Use Google TV's Home Again").sub("PureTV stays installed. Switch back here any time").on(() -> {
                    if (!Tv.openGoogleHome(a)) a.toast("Google TV's home is switched off on this TV");
                }));
                row(new Row.Spec("restore", "Restore Original TV").sub(g == null ? "Google TV's home is switched off. The PureTV agent turns it back on" : "Everything switched off comes back on")
                        .on(() -> Menus.ask(a, "Restore Original TV", "On your computer, open Claude Code and paste:\n\nRestore my TV with https://github.com/advik-srivastava/puretv\n\nEverything comes back on exactly as the backup recorded it.", "OK", null, null, null)));
                head("Always Kept");
                row(new Row.Spec("k1", "Apps, accounts and sign-ins").value("Kept"));
                row(new Row.Spec("k2", "Watch history").value("Kept"));
                foot("Nothing is ever uninstalled. Apps are only switched off, so restoring is one step.");
                break;
            }
            case "updates": {
                title.setText("Software Updates");
                Updates u = a.updatesRef();
                String st = u.state;
                String t = st.equals("available") ? "Install PureTV " + u.version : st.equals("checking") ? "Checking…" : st.equals("downloading") ? "Downloading " + u.pct + "%"
                        : st.equals("installing") ? "Installing…" : st.equals("latest") ? "PureTV " + Updater.installedVersion(a) + " is up to date" : "Check for Updates";
                row(new Row.Spec("upd", t).go().on(() -> { if (u.state.equals("available")) u.install(); else if (!u.busy()) u.check(); }));
                row(new Row.Spec("auto", "Look for Updates Automatically").value(Updater.auto(a) ? "On" : "Off").on(() -> { Updater.setAuto(a, !Updater.auto(a)); render(); }));
                row(new Row.Spec("src", "Update Source").value("GitHub Releases"));
                foot("When the TV is switched on, PureTV looks for a new version and asks you before installing it. Android then shows its own confirmation. Your profiles and settings stay as they are.");
                break;
            }
            case "about": {
                title.setText("About");
                TextView big = Ui.text(a, 64, Ui.heavy, 0xFFFFFFFF);
                big.setText("PureTV");
                big.setPadding(Ui.px(14), Ui.px(12), 0, 0);
                page.addView(big);
                TextView by = Ui.text(a, 24, Ui.regular, 0xCCFFFFFF);
                by.setText("Made with ❤ by Crazified Studios");
                by.setPadding(Ui.px(14), Ui.px(6), 0, Ui.px(10));
                page.addView(by);
                head("This TV");
                row(new Row.Spec("name", "Name").value(Device.name(a)));
                row(new Row.Spec("model", "Model").value(Device.model()));
                row(new Row.Spec("os", "Software").value(Device.os()));
                row(new Row.Spec("screen", "Screen").value(Device.screen(a)));
                row(new Row.Spec("mem", "Memory").value(Device.memory(a)));
                row(new Row.Spec("disk", "Storage").value(Device.storage()));
                Device.Net n = Device.net(a);
                row(new Row.Spec("net", n.kind.equals("Offline") ? "Network" : n.kind).value(n.connected ? n.name : "Not connected"));
                if (!n.ip.isEmpty()) row(new Row.Spec("ip", "IP Address").value(n.ip));
                row(new Row.Spec("off", "Apps Switched Off").value(String.valueOf(disabledApps(a).size())));
                head("PureTV");
                row(new Row.Spec("ver", "Version").value("PureTV " + Updater.installedVersion(a)).on(() -> { a.closeAll(); new Settings(a).show("updates"); }));
                row(new Row.Spec("web", "Website").value("crazifiedstudios.com").on(() -> Tv.start(a, new Intent(Intent.ACTION_VIEW, Uri.parse("https://crazifiedstudios.com")))));
                row(new Row.Spec("help", "Help").value("crazifiedstudios@gmail.com"));
                row(new Row.Spec("design", "Design").value("Inspired by Apple tvOS"));
                foot("Apple and tvOS are trademarks of Apple Inc. PureTV is an independent project, not affiliated with or endorsed by Apple.");
                break;
            }
        }
        if (keep != null) for (int i = 0; i < page.getChildCount(); i++) {
            View v = page.getChildAt(i);
            if (v instanceof Row && keep.equals(((Row) v).key)) { v.requestFocus(); break; }
        }
        for (int i = 0; i < page.getChildCount(); i++) {   // Left from any row goes back to the sidebar
            View v = page.getChildAt(i);
            v.setOnKeyListener((x, code, e) -> {
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && e.getAction() == KeyEvent.ACTION_DOWN) {
                    for (View si : sideItems) if (current.equals(si.getTag())) { si.requestFocus(); return true; }
                }
                return false;
            });
        }
    }

    private void head(String t) { page.addView(Row.heading(a, t)); }

    private void row(Row.Spec s) {
        Row r = new Row(a, s, 1100);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.px(10);
        page.addView(r, lp);
    }

    private void foot(String t) {
        TextView f = Ui.text(a, 19, Ui.regular, 0x8CFFFFFF);
        f.setText(t);
        f.setLineSpacing(0, 1.3f);
        f.setPadding(Ui.px(14), Ui.px(22), Ui.px(14), 0);
        page.addView(f);
    }

    static java.util.List<String[]> disabledApps(MainActivity a) {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        android.content.pm.PackageManager pm = a.getPackageManager();
        int flags = android.os.Build.VERSION.SDK_INT >= 24 ? android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS : 0;
        for (android.content.pm.ApplicationInfo ai : pm.getInstalledApplications(flags)) {
            if (!ai.enabled) out.add(new String[] { ai.packageName, String.valueOf(ai.loadLabel(pm)) });
        }
        java.util.Collections.sort(out, (x, y) -> x[1].compareToIgnoreCase(y[1]));
        return out;
    }

    static boolean backup(MainActivity a) {
        try (java.io.FileOutputStream f = new java.io.FileOutputStream(new java.io.File(a.getFilesDir(), "backup.json"))) {
            f.write(a.store.json().getBytes("UTF-8"));
            return true;
        } catch (Exception e) { return false; }
    }

    static String readBackup(MainActivity a) {
        java.io.File f = new java.io.File(a.getFilesDir(), "backup.json");
        if (!f.exists()) return null;
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int n = 0;
            while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; }
            return new String(b, 0, n, "UTF-8");
        } catch (Exception e) { return null; }
    }

    static String possessive(String n) { return n.endsWith("s") || n.endsWith("S") ? n + "'" : n + "'s"; }
}
