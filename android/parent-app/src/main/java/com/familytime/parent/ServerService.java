package com.familytime.parent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/** Foreground service — keeps FamilyServer running when parent closes the app. */
public class ServerService extends Service {
    private static final String TAG     = "FamilyServer";
    private static final String CHANNEL = "familytime_server";
    private static final int    NOTIF   = 10;
    private FamilyServer server;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createChannel();
        startForeground(NOTIF, buildNotification());
        server = new FamilyServer(this);
        try {
            server.start();
        } catch (Exception e) {
            Log.e(TAG, "Server start failed", e);
            stopSelf();
        }
        return START_STICKY;
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (server != null) server.stop();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL, "FamilyTime Server", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("FamilyTime")
            .setContentText("Parent server running — kids can connect")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build();
    }
}
