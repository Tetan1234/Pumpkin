package com.tetan.pumpkinbox;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class ServerService extends Service {
    private static final StringBuilder LOG = new StringBuilder();
    static volatile Process proc;
    private PowerManager.WakeLock wake;

    static synchronized void log(String s) {
        LOG.append(s.replaceAll("\u001B\\[[;\\d]*[A-Za-z]", "")).append('\n');
        if (LOG.length() > 60000) LOG.delete(0, LOG.length() - 40000);
    }

    static synchronized String snapshot() { return LOG.toString(); }

    static boolean alive() { Process p = proc; return p != null && p.isAlive(); }

    static void sendLine(String s) {
        Process p = proc;
        if (p == null || !p.isAlive()) { log("[server ne radi]"); return; }
        try {
            OutputStream o = p.getOutputStream();
            o.write((s + "\n").getBytes(StandardCharsets.UTF_8));
            o.flush();
            log("> " + s);
        } catch (IOException e) { log("[greska] " + e); }
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int flags, int id) {
        if (i != null && "stop".equals(i.getAction())) {
            stopServer();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(1, notification());
        if (wake == null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pumpkinbox:server");
            wake.acquire();
        }
        if (!alive()) startServer();
        return START_STICKY;
    }

    @Override public void onDestroy() {
        if (wake != null && wake.isHeld()) wake.release();
        super.onDestroy();
    }

    private Notification notification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("server", "Pumpkin server", NotificationManager.IMPORTANCE_LOW));
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        return new Notification.Builder(this, "server")
                .setContentTitle("Pumpkin server radi")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentIntent(pi)
                .build();
    }

    private void ensureConfig() {
        try {
            File f = new File(getFilesDir(), "pumpkin.toml");
            if (f.exists()) return;
            try (InputStream in = getAssets().open("pumpkin.toml"); OutputStream out = new FileOutputStream(f)) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            log("[config] pumpkin.toml kopiran");
        } catch (Exception e) { log("[config] " + e); }
    }

    private void startServer() {
        try {
            ensureConfig();
            File bin = new File(getApplicationInfo().nativeLibraryDir, "libpumpkin.so");
            log("$ " + bin.getAbsolutePath());
            ProcessBuilder pb = new ProcessBuilder(bin.getAbsolutePath());
            pb.directory(getFilesDir());
            pb.redirectErrorStream(true);
            pb.environment().put("HOME", getFilesDir().getAbsolutePath());
            final Process p = pb.start();
            proc = p;
            new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) log(line);
                } catch (IOException ignored) { }
                try { p.waitFor(); } catch (InterruptedException ignored) { }
                log("[server stao, kod " + p.exitValue() + "]");
            }, "pumpkin-out").start();
        } catch (Exception e) { log("[greska] " + e); }
    }

    private static void stopServer() {
        sendLine("stop");
        new Thread(() -> {
            try { Thread.sleep(8000); } catch (InterruptedException ignored) { }
            Process p = proc;
            if (p != null && p.isAlive()) p.destroy();
        }).start();
    }
}
