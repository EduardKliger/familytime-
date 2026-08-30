package com.familytime.parent;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/** First-run screen: enter the home PC's IP once, then go straight to the PWA. */
public class SetupActivity extends AppCompatActivity {

    static final String PREFS      = "familytime_parent";
    static final String KEY_SERVER = "server_url";
    static final String KEY_DONE   = "setup_done";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_DONE, false)) {
            launch(prefs.getString(KEY_SERVER, ""));
            return;
        }

        setContentView(R.layout.activity_setup);

        EditText etIp   = findViewById(R.id.et_server_ip);
        Button   btnGo  = findViewById(R.id.btn_go);

        btnGo.setOnClickListener(v -> {
            String ip = etIp.getText().toString().trim();
            if (ip.isEmpty()) {
                Toast.makeText(this, "Enter the server IP", Toast.LENGTH_SHORT).show();
                return;
            }
            String url = ip.startsWith("http") ? ip : "http://" + ip + ":3000";
            prefs.edit().putString(KEY_SERVER, url).putBoolean(KEY_DONE, true).apply();
            launch(url);
        });
    }

    private void launch(String serverUrl) {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("server_url", serverUrl);
        startActivity(i);
        finish();
    }
}
