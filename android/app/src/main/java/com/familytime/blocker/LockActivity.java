package com.familytime.blocker;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * Fullscreen lock screen shown over all other apps.
 * Cannot be dismissed by the kid — only unlocked by the service when status clears.
 */
public class LockActivity extends Activity {

    private static LockActivity _instance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        _instance = this;

        // show over lock screen, keep screen on
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON      |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED    |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON      |
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );
        // hide all system UI — no status bar, no nav bar
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE            |
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION   |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN        |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION          |
            View.SYSTEM_UI_FLAG_FULLSCREEN               |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        setContentView(R.layout.activity_lock);
        updateReason(getIntent().getStringExtra("reason"));
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        updateReason(intent.getStringExtra("reason"));
    }

    private void updateReason(String reason) {
        TextView msg = findViewById(R.id.tv_message);
        if (msg == null) return;
        if ("chores".equals(reason)) {
            msg.setText("Do your chores first! 📋");
        } else if ("no_time".equals(reason)) {
            msg.setText("Screen time is up for today! ⏰");
        } else {
            msg.setText("Screen time is locked 🔒");
        }
    }

    /** Called by BlockerService when the kid becomes unblocked. */
    public static void dismiss() {
        if (_instance != null) {
            _instance.runOnUiThread(() -> {
                _instance.finish();
                _instance = null;
            });
        }
    }

    // ── block all back/home/menu presses ─────────────────────────────────────

    @Override public void onBackPressed() { /* blocked */ }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_HOME    ||
            keyCode == KeyEvent.KEYCODE_BACK    ||
            keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
            return true; // consumed
        }
        return super.onKeyDown(keyCode, event);
    }
}
