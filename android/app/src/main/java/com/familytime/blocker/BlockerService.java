package com.familytime.blocker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Foreground service that auto-discovers the parent phone via mDNS and polls
 * /api/status/{kidId} every 5 s to enforce screen-time blocking.
 */
public class BlockerService extends Service {

    private static final String TAG          = "FamilyTime";
    private static final String CHANNEL      = "familytime_bg";
    private static final int    NOTIF_ID     = 1;
    private static final long   INTERVAL     = 5_000L;
    private static final String SERVICE_TYPE = "_familytime._tcp.";
    static final String KEY_SERVER = "server_url";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isLocked      = false;

    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private volatile boolean discoveryActive = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createChannel();
        startForeground(NOTIF_ID, buildNotification("Looking for parent phone…"));
        startNsdDiscovery();
        handler.post(pollTask);
        return START_STICKY;
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(pollTask);
        stopNsdDiscovery();
    }

    // ── mDNS discovery ────────────────────────────────────────────────────────

    private void startNsdDiscovery() {
        if (discoveryActive) return;
        nsdManager = (NsdManager) getSystemService(NSD_SERVICE);
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override public void onStartDiscoveryFailed(String t, int e) { discoveryActive = false; }
            @Override public void onStopDiscoveryFailed(String t, int e)  {}
            @Override public void onDiscoveryStarted(String t)   { discoveryActive = true; }
            @Override public void onDiscoveryStopped(String t)   { discoveryActive = false; }
            @Override public void onServiceLost(NsdServiceInfo i) {}
            @Override public void onServiceFound(NsdServiceInfo info) {
                nsdManager.resolveService(info, new NsdManager.ResolveListener() {
                    @Override public void onResolveFailed(NsdServiceInfo i, int e) {
                        Log.w(TAG, "NSD resolve failed: " + e);
                    }
                    @Override public void onServiceResolved(NsdServiceInfo i) {
                        String url = "http://" + i.getHost().getHostAddress() + ":" + i.getPort();
                        getSharedPreferences(SetupActivity.PREFS, MODE_PRIVATE)
                            .edit().putString(KEY_SERVER, url).apply();
                        Log.d(TAG, "Parent found at " + url);
                        updateNotification("Monitoring screen time…");
                    }
                });
            }
        };
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        } catch (Exception e) {
            Log.w(TAG, "NSD start failed", e);
            discoveryActive = false;
        }
    }

    private void stopNsdDiscovery() {
        if (nsdManager != null && discoveryListener != null && discoveryActive) {
            try { nsdManager.stopServiceDiscovery(discoveryListener); } catch (Exception ignored) {}
        }
    }

    // ── polling loop ──────────────────────────────────────────────────────────

    private final Runnable pollTask = new Runnable() {
        @Override public void run() {
            try {
                SharedPreferences prefs = getSharedPreferences(SetupActivity.PREFS, MODE_PRIVATE);
                String server = prefs.getString(KEY_SERVER, "");
                String kidId  = prefs.getString(SetupActivity.KEY_KID_ID, "");
                if (server.isEmpty() || kidId.isEmpty()) return;

                JSONObject status = httpGet(server + "/api/status/" + kidId);
                boolean blocked   = status.optBoolean("blocked", false);
                String  reason    = status.optString("reason", "");

                if (blocked && !isLocked) showLock(reason);
                else if (!blocked && isLocked) hideLock();
            } catch (Exception e) {
                Log.w(TAG, "Poll failed — will retry NSD if needed");
                if (!discoveryActive) startNsdDiscovery();
            } finally {
                handler.postDelayed(this, INTERVAL);
            }
        }
    };

    // ── lock control ──────────────────────────────────────────────────────────

    private void showLock(String reason) {
        isLocked = true;
        Intent i = new Intent(this, LockActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra("reason", reason);
        startActivity(i);
        updateNotification("🔒 Screen time locked");
    }

    private void hideLock() {
        isLocked = false;
        LockActivity.dismiss();
        updateNotification("✓ Screen time active");
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private JSONObject httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(4000);
        conn.setRequestMethod("GET");
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return new JSONObject(sb.toString());
    }

    // ── notification ──────────────────────────────────────────────────────────

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL, "FamilyTime", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Screen time monitor");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("FamilyTime")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build();
    }

    private void updateNotification(String text) {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
            .notify(NOTIF_ID, buildNotification(text));
    }
}
