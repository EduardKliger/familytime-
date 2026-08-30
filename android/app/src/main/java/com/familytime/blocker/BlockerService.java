package com.familytime.blocker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
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
 * Persistent foreground service that polls /api/status/{kidId} every 5 seconds
 * and shows/hides the lock screen Activity based on the response.
 */
public class BlockerService extends Service {

    private static final String TAG      = "FamilyTime";
    private static final String CHANNEL  = "familytime_bg";
    private static final int    NOTIF_ID = 1;
    private static final long   INTERVAL = 5_000L;

    private final Handler handler  = new Handler(Looper.getMainLooper());
    private boolean       isLocked = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createChannel();
        startForeground(NOTIF_ID, buildNotification("Monitoring screen time…"));
        handler.post(pollTask);
        return START_STICKY; // restart automatically if killed
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(pollTask);
    }

    // ── polling loop ──────────────────────────────────────────────────────────

    private final Runnable pollTask = new Runnable() {
        @Override public void run() {
            try {
                SharedPreferences prefs = getSharedPreferences(SetupActivity.PREFS, MODE_PRIVATE);
                String server = prefs.getString(SetupActivity.KEY_SERVER, "");
                String kidId  = prefs.getString(SetupActivity.KEY_KID_ID,  "");
                if (server.isEmpty() || kidId.isEmpty()) return;

                JSONObject status = httpGet(server + "/api/status/" + kidId);
                boolean blocked   = status.optBoolean("blocked", false);
                String  reason    = status.optString("reason", "");

                if (blocked && !isLocked) {
                    showLock(reason);
                } else if (!blocked && isLocked) {
                    hideLock();
                }
            } catch (Exception e) {
                Log.w(TAG, "Poll failed (server unreachable?) — using cached lock state");
                // keep current isLocked state; don't unlock on network error
            } finally {
                handler.postDelayed(this, INTERVAL);
            }
        }
    };

    // ── lock screen control ───────────────────────────────────────────────────

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

    // ── helpers ───────────────────────────────────────────────────────────────

    private JSONObject httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(4000);
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return new JSONObject(sb.toString());
    }

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
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildNotification(text));
    }
}
