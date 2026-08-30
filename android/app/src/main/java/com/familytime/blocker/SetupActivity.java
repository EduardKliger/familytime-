package com.familytime.blocker;

import android.app.AppOpsManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/**
 * First-run setup: kid picks their profile ID and enters the server IP.
 * After setup is complete, the BlockerService starts and runs forever.
 */
public class SetupActivity extends AppCompatActivity {

    static final String PREFS       = "familytime";
    static final String KEY_KID_ID  = "kid_id";
    static final String KEY_SERVER  = "server_url";
    static final String KEY_SETUP   = "setup_done";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_SETUP, false)) {
            // already configured — just ensure the service is running
            startBlockerService();
            finish();
            return;
        }

        setContentView(R.layout.activity_setup);

        Button btnSave   = findViewById(R.id.btn_save);
        EditText etIp    = findViewById(R.id.et_server_ip);
        EditText etKidId = findViewById(R.id.et_kid_id);

        btnSave.setOnClickListener(v -> {
            String ip    = etIp.getText().toString().trim();
            String kidId = etKidId.getText().toString().trim();
            if (ip.isEmpty() || kidId.isEmpty()) {
                Toast.makeText(this, "Fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            String serverUrl = ip.startsWith("http") ? ip : "http://" + ip + ":3000";
            prefs.edit()
                 .putString(KEY_SERVER, serverUrl)
                 .putString(KEY_KID_ID, kidId)
                 .putBoolean(KEY_SETUP, true)
                 .apply();

            requestPermissions();
        });
    }

    private void requestPermissions() {
        // SYSTEM_ALERT_WINDOW: draw lock screen over other apps
        if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                  Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, 1001);
            return;
        }
        // PACKAGE_USAGE_STATS: detect foreground app
        if (!hasUsageAccess()) {
            Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivityForResult(i, 1002);
            return;
        }
        startBlockerService();
        Toast.makeText(this, "FamilyTime is active ✓", Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        requestPermissions(); // re-check after user returns from settings
    }

    private boolean hasUsageAccess() {
        AppOpsManager ops = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                                      android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void startBlockerService() {
        Intent i = new Intent(this, BlockerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }
}
