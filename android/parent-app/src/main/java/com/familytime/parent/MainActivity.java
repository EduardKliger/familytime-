package com.familytime.parent;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String[] KIDS      = FamilyServer.KIDS;
    private static final String[] NAMES     = {"\uD83D\uDC67 Amy", "\uD83D\uDC66 Guy", "\uD83D\uDC67 Mia"};
    private static final int[]    CARD_IDS  = {R.id.card_amy,  R.id.card_guy,  R.id.card_mia};
    private static final int[]    TIME_IDS  = {R.id.time_amy,  R.id.time_guy,  R.id.time_mia};
    private static final int[]    BADGE_IDS = {R.id.badge_amy, R.id.badge_guy, R.id.badge_mia};

    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(FamilyServer.PREFS, MODE_PRIVATE);

        Intent svc = new Intent(this, ServerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
        else startService(svc);

        for (int i = 0; i < KIDS.length; i++) {
            final String kid = KIDS[i];
            View card = findViewById(CARD_IDS[i]);
            card.setOnClickListener(v -> {
                Intent intent = new Intent(this, KidDetailActivity.class);
                intent.putExtra("kid", kid);
                startActivity(intent);
            });
        }

        findViewById(R.id.btn_add_chore).setOnClickListener(v ->
            startActivity(new Intent(this, AddChoreActivity.class)));

        refresh();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }
    @Override protected void onPause()  { super.onPause(); handler.removeCallbacksAndMessages(null); }

    private void refresh() {
        for (int i = 0; i < KIDS.length; i++) {
            String kid = KIDS[i];
            FamilyServer.tickTimer(prefs, kid);
            int remaining = FamilyServer.getRemaining(prefs, kid);
            int pending   = FamilyServer.countPendingApproval(prefs, kid);

            ((TextView) findViewById(TIME_IDS[i])).setText(fmtTime(remaining));
            View badge = findViewById(BADGE_IDS[i]);
            badge.setVisibility(pending > 0 ? View.VISIBLE : View.GONE);
            ((TextView) badge).setText("\u270B " + pending + " to approve");
        }
        handler.postDelayed(this::refresh, 5000);
    }

    private String fmtTime(int s) {
        if (s <= 0) return "\u26D4 Locked";
        int h = s / 3600, m = (s % 3600) / 60;
        if (h > 0) return h + "h " + m + "m left";
        return m + "m " + (s % 60) + "s left";
    }
}
