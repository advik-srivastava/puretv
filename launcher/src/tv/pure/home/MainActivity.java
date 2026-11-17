package tv.pure.home;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * PureTV Home. The Home screen is the PureTV interface (assets/ui/tv.html) in a full-screen WebView,
 * served from the APK itself through a private https://puretv.local address, so nothing is loaded
 * from the internet. Bridge.java connects it to the TV: the apps, inputs, settings and updates.
 *
 * Your apps, accounts and sign-ins are never touched: PureTV only opens apps the way any launcher does.
 */
public class MainActivity extends Activity {
    static final String HOST = "puretv.local";
    WebView web;
    private boolean loaded;
    private Bridge bridge;

    private final BroadcastReceiver screenOn = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { js("window.tvWake && tvWake()"); }
    };

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);                    // PureTV's own settings, profiles and top row
        s.setMediaPlaybackRequiresUserGesture(false);    // the soft UI sounds
        s.setUseWideViewPort(true);                      // the page is laid out at 1920 x 1080 ...
        s.setLoadWithOverviewMode(true);                 // ... and drawn at the panel's own size (1366 x 768 on HD Ready TVs)
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setTextZoom(100);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        bridge = new Bridge(this);
        web.addJavascriptInterface(bridge, "PureTV");
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return HOST.equals(r.getUrl().getHost()) ? Assets.serve(MainActivity.this, r.getUrl()) : null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return !HOST.equals(r.getUrl().getHost());   // the Home screen never navigates away
            }
            @Override public void onPageFinished(WebView v, String url) { loaded = true; }
        });
        setContentView(web);
        web.loadUrl("https://" + HOST + "/ui/tv.html");
        web.requestFocus();
        registerReceiver(screenOn, new IntentFilter(Intent.ACTION_SCREEN_ON));
    }

    void js(final String code) {
        if (web == null) return;
        web.post(new Runnable() { @Override public void run() { if (loaded) web.evaluateJavascript(code, null); } });
    }

    @Override protected void onResume() {
        super.onResume();
        web.onResume();
        web.resumeTimers();
        js("window.tvResume && tvResume()");
    }

    @Override protected void onPause() {
        js("window.tvPause && tvPause()");
        web.onPause();       // animations stop while an app is on screen ...
        web.pauseTimers();   // ... and so do the clock, wallpaper and weather timers: no CPU used in the background
        super.onPause();
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (Intent.ACTION_MAIN.equals(i.getAction())) js("window.tvKey && tvKey('home')");   // the Home button, while PureTV is already open
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(screenOn); } catch (Exception ignored) { }
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // An app is on screen (UI hidden) or memory is short: let go of the wallpapers so the app gets the memory.
        if (level >= TRIM_MEMORY_UI_HIDDEN || level == TRIM_MEMORY_RUNNING_LOW || level == TRIM_MEMORY_RUNNING_CRITICAL) {
            js("window.tvLowMemory && tvLowMemory()");
        }
    }

    /** Remote keys: OK becomes Enter (hold for the app menu), Back and Menu go to the page. */
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER || k == KeyEvent.KEYCODE_BUTTON_A) {
            return web.dispatchKeyEvent(new KeyEvent(e.getDownTime(), e.getEventTime(), e.getAction(), KeyEvent.KEYCODE_ENTER, e.getRepeatCount(), e.getMetaState()));
        }
        if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_ESCAPE || k == KeyEvent.KEYCODE_BUTTON_B) {
            if (e.getAction() == KeyEvent.ACTION_UP) js("window.tvKey && tvKey('back')");
            return true;   // the Home screen is never closed by Back
        }
        if (k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_GUIDE) {
            if (e.getAction() == KeyEvent.ACTION_UP) js("window.tvKey && tvKey('menu')");
            return true;
        }
        return super.dispatchKeyEvent(e);
    }
}
