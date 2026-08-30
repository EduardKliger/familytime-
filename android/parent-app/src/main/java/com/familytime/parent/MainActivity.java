package com.familytime.parent;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

/** Full-screen WebView — loads the FamilyTime parent PWA from the home server. */
public class MainActivity extends AppCompatActivity {

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String serverUrl = getIntent().getStringExtra("server_url");
        if (serverUrl == null) {
            SharedPreferences prefs = getSharedPreferences(SetupActivity.PREFS, MODE_PRIVATE);
            serverUrl = prefs.getString(SetupActivity.KEY_SERVER, "http://localhost:3000");
        }

        webView = findViewById(R.id.webview);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);        // localStorage
        s.setDatabaseEnabled(true);          // IndexedDB (PouchDB)
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setAllowFileAccess(false);         // no filesystem access needed

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                // keep all navigation inside the WebView
                return false;
            }
        });

        webView.loadUrl(serverUrl);
    }

    /** Back button navigates within the WebView instead of exiting the app. */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
