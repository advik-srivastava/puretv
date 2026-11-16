package tv.pure.home;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.Date;
import java.util.Random;

/** Screensaver: a thin clock that moves once a minute, so nothing burns into the panel. */
public class ClockDream extends DreamService {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private TextView clock;
    private final Runnable step = new Runnable() { @Override public void run() {
        clock.setText(DateFormat.getTimeFormat(ClockDream.this).format(new Date()).replaceAll("(?i)\\s*[ap]\\.?m\\.?", ""));
        FrameLayout parent = (FrameLayout) clock.getParent();
        int w = parent.getWidth() - clock.getWidth(), h = parent.getHeight() - clock.getHeight();
        if (w > 0 && h > 0) clock.animate().translationX(random.nextInt(w)).translationY(random.nextInt(h)).setDuration(1500).start();
        ui.postDelayed(this, 60000 - System.currentTimeMillis() % 60000 + 50);
    } };

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        clock = new TextView(this);
        clock.setTextColor(0xB3ECEEF4);
        clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDisplayMetrics().widthPixels / 12f);
        root.addView(clock, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));
        setContentView(root);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        ui.post(step);
        ui.postDelayed(step, 400);   // again once laid out, so the first position is not the corner
    }

    @Override public void onDreamingStopped() {
        ui.removeCallbacks(step);
        super.onDreamingStopped();
    }
}
