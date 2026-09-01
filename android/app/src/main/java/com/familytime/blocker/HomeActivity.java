package com.familytime.blocker;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class HomeActivity extends AppCompatActivity {

    private TextView tvGreeting, tvTime, tvStatus;
    private Button   btnTimer;
    private LinearLayout choreList;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String kidId, serverUrl;
    private boolean timerRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        tvGreeting = findViewById(R.id.tv_greeting);
        tvTime     = findViewById(R.id.tv_time);
        tvStatus   = findViewById(R.id.tv_status);
        btnTimer   = findViewById(R.id.btn_timer);
        choreList  = findViewById(R.id.chore_list);

        SharedPreferences prefs = getSharedPreferences(SetupActivity.PREFS, MODE_PRIVATE);
        kidId = prefs.getString(SetupActivity.KEY_KID_ID, "");
        tvGreeting.setText(kidGreeting(kidId));

        btnTimer.setOnClickListener(v -> toggleTimer());
        loadData();
    }

    @Override protected void onResume() { super.onResume(); loadData(); }
    @Override protected void onPause()  { super.onPause(); handler.removeCallbacksAndMessages(null); }

    private void loadData() {
        serverUrl = getSharedPreferences(SetupActivity.PREFS, MODE_PRIVATE)
            .getString(BlockerService.KEY_SERVER, "");

        if (serverUrl.isEmpty()) {
            tvStatus.setText("Looking for parent phone\u2026");
            tvTime.setText("--:--");
            handler.postDelayed(this::loadData, 3000);
            return;
        }

        new Thread(() -> {
            try {
                JSONObject status = httpGet(serverUrl + "/api/status/" + kidId);
                JSONArray  chores = httpGetArray(serverUrl + "/api/chores/" + kidId);
                boolean blocked   = status.optBoolean("blocked", false);
                int remaining     = status.optInt("remainingSeconds", 0);
                int pendingCount  = status.optInt("choresPending", 0);

                runOnUiThread(() -> {
                    tvTime.setText(fmtTime(remaining));
                    if (blocked || remaining <= 0) {
                        tvStatus.setText("\u26D4 Screen time locked");
                        btnTimer.setEnabled(false);
                        btnTimer.setText("\u25B6 Start");
                        timerRunning = false;
                    } else {
                        tvStatus.setText(pendingCount > 0
                            ? pendingCount + " chore(s) waiting for parent approval"
                            : "You have " + fmtTime(remaining) + " available!");
                        btnTimer.setEnabled(true);
                        btnTimer.setText(timerRunning ? "\u23F8 Pause" : "\u25B6 Start");
                    }
                    updateChoreList(chores);
                });
            } catch (Exception e) {
                runOnUiThread(() -> tvStatus.setText("Can\u2019t reach parent phone"));
            }
            handler.postDelayed(this::loadData, 5000);
        }).start();
    }

    private void toggleTimer() {
        if (serverUrl.isEmpty()) return;
        new Thread(() -> {
            try {
                String ep = timerRunning ? "/api/time/stop/" : "/api/time/start/";
                JSONObject result = httpPost(serverUrl + ep + kidId);
                int remaining = result.optInt("remainingSeconds", 0);
                runOnUiThread(() -> {
                    timerRunning = !timerRunning && remaining > 0;
                    btnTimer.setText(timerRunning ? "\u23F8 Pause" : "\u25B6 Start");
                    tvTime.setText(fmtTime(remaining));
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private void updateChoreList(JSONArray chores) {
        choreList.removeAllViews();
        try {
            for (int i = 0; i < chores.length(); i++) addChoreRow(chores.getJSONObject(i));
        } catch (Exception ignored) {}
        if (chores.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No chores today \uD83C\uDF89");
            empty.setTextSize(16);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, 32, 0, 32);
            choreList.addView(empty);
        }
    }

    private void addChoreRow(JSONObject c) {
        try {
            String id     = c.getString("id");
            String title  = c.optString("icon", "\u2705") + "  " + c.getString("title");
            String status = c.optString("status", "pending");
            int    reward = c.optInt("rewardMinutes", 15);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, 16, 0, 16);

            TextView tvTitle = new TextView(this);
            tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvTitle.setText(title + "\n+" + reward + " min");
            tvTitle.setTextSize(16);
            row.addView(tvTitle);

            if ("pending".equals(status)) {
                Button btn = new Button(this);
                btn.setText("Done!");
                btn.setTextSize(13);
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF6C63FF));
                btn.setTextColor(0xFFFFFFFF);
                btn.setOnClickListener(v -> markChoreDone(id));
                row.addView(btn);
            } else {
                TextView tvSt = new TextView(this);
                tvSt.setPadding(16, 0, 0, 0);
                tvSt.setTextSize(14);
                if ("done_pending".equals(status)) {
                    tvSt.setText("\u23F3 Waiting");
                    tvSt.setTextColor(0xFFFF9800);
                } else {
                    tvSt.setText("\u2705 Done");
                    tvSt.setTextColor(0xFF4CAF50);
                }
                row.addView(tvSt);
            }

            choreList.addView(row);
            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFFEEEEEE);
            choreList.addView(divider);
        } catch (Exception ignored) {}
    }

    private void markChoreDone(String choreId) {
        if (serverUrl.isEmpty()) return;
        new Thread(() -> {
            try {
                httpPost(serverUrl + "/api/chores/" + kidId + "/" + choreId + "/done");
                runOnUiThread(this::loadData);
            } catch (Exception ignored) {}
        }).start();
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private JSONObject httpGet(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(4000);
        return new JSONObject(readResponse(conn));
    }

    private JSONArray httpGetArray(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(4000);
        return new JSONArray(readResponse(conn));
    }

    private JSONObject httpPost(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(4000);
        conn.setDoOutput(true);
        conn.getOutputStream().close();
        return new JSONObject(readResponse(conn));
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String fmtTime(int s) {
        if (s <= 0) return "0:00";
        int h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, sec);
        return String.format("%d:%02d", m, sec);
    }

    private String kidGreeting(String id) {
        if (id.contains("amy")) return "Hello Amy \uD83E\uDD84";
        if (id.contains("guy")) return "Hello Guy \uD83E\uDD81";
        if (id.contains("mia")) return "Hello Mia \uD83E\uDD8B";
        return "Hello!";
    }
}
