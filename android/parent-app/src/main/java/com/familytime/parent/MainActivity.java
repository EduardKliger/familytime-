package com.familytime.parent;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

/** Native toggle dashboard — parent blocks/unblocks each kid. No PC needed. */
public class MainActivity extends AppCompatActivity {
    private SharedPreferences prefs;

    private static final String[] KIDS = {
        "profile_kid_amy", "profile_kid_guy", "profile_kid_mia"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(FamilyServer.PREFS, MODE_PRIVATE);

        Intent svc = new Intent(this, ServerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
        else startService(svc);

        int[] switchIds = {R.id.sw_amy, R.id.sw_guy, R.id.sw_mia};
        for (int i = 0; i < KIDS.length; i++) {
            final String kid = KIDS[i];
            SwitchCompat sw = findViewById(switchIds[i]);
            sw.setChecked(prefs.getBoolean(kid + "_blocked", false));
            sw.setOnCheckedChangeListener((btn, checked) ->
                prefs.edit()
                    .putBoolean(kid + "_blocked", checked)
                    .putString(kid + "_reason",  checked ? "chores" : "")
                    .apply()
            );
        }
    }
}
