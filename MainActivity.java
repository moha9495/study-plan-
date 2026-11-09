package com.muhannad.studyplan;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQ_OPEN = 1;
    private static final int REQ_SAVE = 2;
    private static final String BASE = "file:///android_asset/index.html";

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingExport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);

        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try {
                    startActivityForResult(i, REQ_OPEN);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");

        String view = viewFrom(getIntent());
        web.loadUrl(view != null ? BASE + "#" + view : BASE);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String view = viewFrom(intent);
        if (view != null && web != null) {
            web.evaluateJavascript("window.__setView && window.__setView('" + view + "')", null);
        }
    }

    private static String viewFrom(Intent intent) {
        if (intent == null) return null;
        String v = intent.getStringExtra("view");
        if ("weeks".equals(v) || "subjects".equals(v) || "dates".equals(v)) return v;
        return null;
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.__back ? window.__back() : false", result -> {
            if (!"true".equals(result)) super.onBackPressed();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        PlanWidget.refreshAll(getApplicationContext());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OPEN) {
            if (fileCallback != null) {
                Uri[] result = (resultCode == RESULT_OK && data != null && data.getData() != null)
                        ? new Uri[]{data.getData()} : null;
                fileCallback.onReceiveValue(result);
                fileCallback = null;
            }
        } else if (requestCode == REQ_SAVE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingExport != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    out.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(this, "Backup saved", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Couldn't save the backup", Toast.LENGTH_LONG).show();
                }
            }
            pendingExport = null;
        }
    }

    /** Called from the web UI as window.Android.* */
    private class Bridge {
        @JavascriptInterface
        public String load() {
            return Store.load(getApplicationContext());
        }

        @JavascriptInterface
        public void save(String json) {
            Store.save(getApplicationContext(), json);
            PlanWidget.refreshAll(getApplicationContext());
        }

        @JavascriptInterface
        public void exportBackup(String json, String fileName) {
            runOnUiThread(() -> {
                pendingExport = json;
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, fileName);
                try {
                    startActivityForResult(i, REQ_SAVE);
                } catch (Exception e) {
                    pendingExport = null;
                    Toast.makeText(MainActivity.this, "No file app available", Toast.LENGTH_LONG).show();
                }
            });
        }
    }
}
