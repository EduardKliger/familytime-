package com.familytime.parent;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

public class AddChoreActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_chore);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("Add Chore");

        EditText etTitle   = findViewById(R.id.et_title);
        EditText etIcon    = findViewById(R.id.et_icon);
        EditText etMinutes = findViewById(R.id.et_minutes);
        Spinner  spKid     = findViewById(R.id.sp_kid);

        spKid.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item,
            new String[]{"All Kids", "Amy", "Guy", "Mia"}));

        findViewById(R.id.btn_save).setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            if (title.isEmpty()) { Toast.makeText(this, "Enter a chore name", Toast.LENGTH_SHORT).show(); return; }

            String icon = etIcon.getText().toString().trim();
            if (icon.isEmpty()) icon = "\u2705";
            int minutes;
            try { minutes = Integer.parseInt(etMinutes.getText().toString().trim()); }
            catch (NumberFormatException e) { minutes = 15; }

            String[] kidValues = {"all",
                "profile_kid_amy", "profile_kid_guy", "profile_kid_mia"};
            String assignedTo = kidValues[spKid.getSelectedItemPosition()];

            SharedPreferences prefs = getSharedPreferences(FamilyServer.PREFS, MODE_PRIVATE);
            JSONArray templates = FamilyServer.getTemplates(prefs);
            try {
                JSONObject t = new JSONObject();
                t.put("id", "t_" + UUID.randomUUID().toString().substring(0, 8));
                t.put("title", title);
                t.put("icon", icon);
                t.put("assignedTo", assignedTo);
                t.put("rewardMinutes", minutes);
                templates.put(t);
                FamilyServer.saveTemplates(prefs, templates);
                FamilyServer.regenerateDayChores(prefs);
                Toast.makeText(this, "Chore added!", Toast.LENGTH_SHORT).show();
                finish();
            } catch (Exception e) {
                Toast.makeText(this, "Error saving chore", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
