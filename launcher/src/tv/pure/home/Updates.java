package tv.pure.home;

import android.os.Build;

import org.json.JSONObject;

/** Software updates on the TV: found automatically when the TV comes on, installed only after the owner says yes. */
final class Updates {
    private final MainActivity a;
    String state = "idle", version = "", notes = "";
    int pct;
    private boolean fromAuto, installAfterAllow;
    Runnable onChange;

    Updates(MainActivity a) { this.a = a; }

    void autoCheck() {
        if (installAfterAllow && canInstall()) { install(); return; }
        if (busy() || !Updater.due(a)) return;
        fromAuto = true;
        check();
    }

    boolean busy() { return state.equals("checking") || state.equals("downloading") || state.equals("installing"); }

    void check() {
        state = "checking"; changed();
        Updater.check(a, false, this::status);
    }

    void install() {
        if (!canInstall()) {
            installAfterAllow = true;
            a.toast("Allow PureTV to install updates: switch it on, then press Back");
            Tv.settings(a, "unknown");
            return;
        }
        installAfterAllow = false;
        state = "downloading"; pct = 0; changed();
        a.toast("Downloading PureTV " + version + "…");
        Updater.check(a, true, this::status);
    }

    private boolean canInstall() { return Build.VERSION.SDK_INT < 26 || a.getPackageManager().canRequestPackageInstalls(); }

    private void status(String json) {
        try {
            JSONObject o = new JSONObject(json);
            String s = o.optString("state");
            if (s.equals("downloading")) pct = Integer.parseInt(o.optString("pct", "0"));
            if (o.has("version")) version = o.optString("version");
            if (o.has("notes")) notes = o.optString("notes");
            state = s;
            if (s.equals("failed")) a.toast("signature".equals(o.optString("error")) ? "Update refused: it was not signed by PureTV" : "The update could not be downloaded. PureTV will try again later");
            if (s.equals("installing")) a.toast("Installing PureTV " + version + ". Confirm on the screen that opens");
            if (s.equals("available") && fromAuto) ask();
            fromAuto = false;
            changed();
        } catch (Exception ignored) { }
    }

    private void changed() { if (onChange != null) onChange.run(); }

    /** The owner decides: Install Now or Later. Not asked in the Kids profile or over another panel. */
    private void ask() {
        if (a.store.me().kids || !a.panels.isEmpty()) return;
        String lines = notes.replace("\r", "").trim();
        String[] l = lines.split("\n");
        StringBuilder sb = new StringBuilder("A new version of PureTV is ready. Your apps, sign-ins, profiles and settings stay exactly as they are.");
        int shown = 0;
        for (String x : l) { if (!x.trim().isEmpty() && !x.startsWith("**") && shown < 3) { sb.append(shown == 0 ? "\n\n" : "\n").append(x.trim()); shown++; } }
        Menus.ask(a, "PureTV " + version, sb.toString(), "Install Now", this::install, "Later", () -> a.toast("Later it is. Settings › Software Updates installs it any time"));
    }
}
