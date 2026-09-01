package com.familytime.parent;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;

public class KidDetailActivity extends AppCompatActivity {

    private static final String[] KID_IDS   = FamilyServer.KIDS;
    private static final String[] KID_NAMES = {"Amy", "Guy", "Mia"};

    private String kid;
    private SharedPreferences prefs;
    private LinearLayout choreList;
    private TextView tvTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kid_detail);

        kid       = getIntent().getStringExtra("kid");
        prefs     = getSharedPreferences(FamilyServer.PREFS, MODE_PRIVATE);
        tvTime    = findViewById(R.id.tv_time);
        choreList = findViewById(R.id.chore_list);

        String name = displayName(kid);
        ((TextView) findViewById(R.id.tv_kid_name)).setText(name);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(name);

        findViewById(R.id.btn_add_15).setOnClickListener(v -> { FamilyServer.addTime(prefs, kid, 15 * 60); refresh(); });
        findViewById(R.id.btn_add_30).setOnClickListener(v -> { FamilyServer.addTime(prefs, kid, 30 * 60); refresh(); });

        refresh();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        FamilyServer.tickTimer(prefs, kid);
        FamilyServer.ensureDayChores(prefs, kid);
        tvTime.setText(fmtTime(FamilyServer.getRemaining(prefs, kid)));

        choreList.removeAllViews();
        JSONArray chores = FamilyServer.getChores(prefs, kid);
        try {
            for (int i = 0; i < chores.length(); i++) addChoreRow(chores.getJSONObject(i));
        } catch (Exception ignored) {}

        if (chores.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No chores today");
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
            row.setPadding(0, 20, 0, 20);

            TextView tvTitle = new TextView(this);
            tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvTitle.setText(title + "\n+" + reward + " min");
            tvTitle.setTextSize(16);

            TextView tvAction = new TextView(this);
            tvAction.setTextSize(14);
            tvAction.setPadding(16, 0, 0, 0);
            if ("done_pending".equals(status)) {
                tvAction.setText("\u2714 Approve");
                tvAction.setTextColor(0xFFFF9800);
                tvAction.setOnClickListener(v -> { FamilyServer.approveChore(prefs, kid, id); refresh(); });
            } else if ("approved".equals(status)) {
                tvAction.setText("\u2705 Done");
                tvAction.setTextColor(0xFF4CAF50);
            } else {
                tvAction.setText("Pending");
                tvAction.setTextColor(0xFF9E9E9E);
            }

            row.addView(tvTitle);
            row.addView(tvAction);
            choreList.addView(row);

            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFFEEEEEE);
            choreList.addView(divider);
        } catch (Exception ignored) {}
    }

    private String fmtTime(int s) {
        if (s <= 0) return "\u26D4 Time's up";
        int h = s / 3600, m = (s % 3600) / 60;
        if (h > 0) return h + "h " + m + "m remaining";
        return m + "m " + (s % 60) + "s remaining";
    }

    private String displayName(String kidId) {
        for (int i = 0; i < KID_IDS.length; i++)
            if (KID_IDS[i].equals(kidId)) return KID_NAMES[i];
        return kidId;
    }
}
