package com.tetan.pumpkinbox;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final String[] KEEP = {"android", "com.android.", "com.tailscale", "com.ansub.tvhome",
            "com.google.android.inputmethod", "com.google.android.gms", "com.google.android.gsf",
            "com.google.android.tvlauncher", "com.droidlogic", "com.xiaomi", "com.mitv", "mitv"};
    private TextView console, stats;
    private ScrollView scroll;
    private final Handler h = new Handler(Looper.getMainLooper());
    private String last = "";
    private final Runnable tick = new Runnable() {
        @Override public void run() { refresh(); h.postDelayed(this, 1000); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(48, 32, 48, 24);
        stats = text(18, Color.WHITE);
        console = text(15, 0xFF33FF66);
        scroll = new ScrollView(this);
        scroll.setFocusable(false);
        scroll.addView(console);
        TextView hint = text(13, 0xFF888888);
        hint.setText("OK: naredba   MENU/desno: ocisti RAM   BACK: izlaz (server radi dalje)   DUGI BACK: zaustavi server");
        root.addView(stats);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(hint);
        setContentView(root);
        if (!ServerService.alive()) {
            ServerService.log("[ram] pokusaj gasenja pozadine za " + freeRam() + " aplikacija");
            startForegroundService(new Intent(this, ServerService.class));
        }
    }

    @Override protected void onResume() { super.onResume(); h.post(tick); }

    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tick); }

    private TextView text(int sp, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    private void refresh() {
        String log = ServerService.snapshot();
        if (!log.equals(last)) {
            last = log;
            console.setText(log);
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        }
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        stats.setText(String.format(Locale.US, "Pumpkin: %s | RAM slobodno %d MB od %d MB",
                ServerService.alive() ? "RADI" : "STAO", mi.availMem >> 20, mi.totalMem >> 20));
    }

    private static boolean keep(String p) {
        for (String k : KEEP) if (p.equals(k) || p.startsWith(k.endsWith(".") ? k : k + ".")) return true;
        return false;
    }

    private int freeRam() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        int n = 0;
        for (ApplicationInfo ai : getPackageManager().getInstalledApplications(0)) {
            if (ai.packageName.equals(getPackageName()) || keep(ai.packageName)) continue;
            am.killBackgroundProcesses(ai.packageName);
            n++;
        }
        return n;
    }

    private void askCommand() {
        final EditText in = new EditText(this);
        in.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle("Naredba (npr. op Tetan)").setView(in)
                .setPositiveButton("Posalji", (d, w) -> {
                    final String s = in.getText().toString().trim();
                    if (!s.isEmpty()) new Thread(() -> ServerService.sendLine(s)).start();
                })
                .setNegativeButton("Odustani", null).show();
    }

    @Override public boolean onKeyDown(int k, KeyEvent e) {
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                askCommand();
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                ServerService.log("[ram] pokusaj gasenja pozadine za " + freeRam() + " aplikacija");
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                scroll.smoothScrollBy(0, -300);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                scroll.smoothScrollBy(0, 300);
                return true;
            case KeyEvent.KEYCODE_BACK:
                e.startTracking();
                return true;
            default:
                return super.onKeyDown(k, e);
        }
    }

    @Override public boolean onKeyLongPress(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_BACK) {
            startService(new Intent(this, ServerService.class).setAction("stop"));
            finish();
            return true;
        }
        return super.onKeyLongPress(k, e);
    }

    @Override public boolean onKeyUp(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_BACK && e.isTracking() && !e.isCanceled()) {
            finish();
            return true;
        }
        return super.onKeyUp(k, e);
    }
}
