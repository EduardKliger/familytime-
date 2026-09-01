package com.familytime.blocker;

import android.app.AppOpsManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/** First-run: kid enters their profile ID; server is auto-discovered via mDNS. */
public class SetupActivity extends AppCompatActivity {

    static final String PREFS      = "familytime";
    static final String KEY_KID_ID = "kid_id";
    static final String KEY_SETUP  = "setup_done";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_SETUP, false)) {
            startBlockerService();
            startActivity(new Intent(this, HomeActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_setup);
        Button   btnSave = findViewById(R.id.btn_save);
        EditText etKidId = findViewById(R.id.et_kid_id);

        btnSave.setOnClickListener(v -> {
            String kidId = etKidId.getText().toString().trim();
            if (kidId.isEmpty()) {
                Toast.makeText(this, "Enter your name", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString(KEY_KID_ID, kidId).putBoolean(KEY_SETUP, true).apply();
            requestPermissions();
        });
    }

    private void requestPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())), 1001);
            return;
        }
        if (!hasUsageAccess()) {
            startActivityForResult(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), 1002);
            return;
        }
        startBlockerService();
        Toast.makeText(this, "FamilyTime is active ✓", Toast.LENGTH_LONG).show();        startActivity(new Intent(this, HomeActivity.class));        finish();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        requestPermissions();
    }

    private boolean hasUsageAccess() {
        AppOpsManager ops = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void startBlockerService() {
        Intent i = new Intent(this, BlockerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
    }
}
