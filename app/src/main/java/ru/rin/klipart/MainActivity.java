package ru.rin.klipart;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private static final int REQ_FILE = 11;
    private static final int REQ_PERM = 12;
    private static final String FOLDER = "Rin-klipart";

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    // состояние текущего сохранения
    private OutputStream out;
    private Uri outUri;
    private File outFile;
    private String outMime = "";
    private boolean outPdf;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERM);
        }

        web.loadUrl("file:///android_asset/www/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            if (fileCallback != null) {
                Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                fileCallback.onReceiveValue(result);
                fileCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        web.evaluateJavascript(
                "(function(){return window.onAndroidBack?window.onAndroidBack():false;})()",
                value -> {
                    if (!"true".equals(value)) {
                        finish();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        abortSave();
        super.onDestroy();
    }

    private void abortSave() {
        try {
            if (out != null) out.close();
        } catch (Exception ignored) {
        }
        out = null;
        try {
            if (outUri != null) getContentResolver().delete(outUri, null, null);
        } catch (Exception ignored) {
        }
        outUri = null;
        if (outFile != null) {
            //noinspection ResultOfMethodCallIgnored
            outFile.delete();
        }
        outFile = null;
    }

    /** Мост JS -> Android: сохранение файлов в галерею / загрузки и цвет системных панелей. */
    private class Bridge {

        @JavascriptInterface
        public synchronized String saveBegin(String name, String mime) {
            try {
                abortSave();
                outMime = mime;
                outPdf = "application/pdf".equals(mime);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentResolver r = getContentResolver();
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                    String base = outPdf ? Environment.DIRECTORY_DOWNLOADS : Environment.DIRECTORY_PICTURES;
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, base + "/" + FOLDER);
                    v.put(MediaStore.MediaColumns.IS_PENDING, 1);
                    Uri collection = outPdf
                            ? MediaStore.Downloads.EXTERNAL_CONTENT_URI
                            : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    outUri = r.insert(collection, v);
                    if (outUri == null) return "error:insert";
                    out = r.openOutputStream(outUri);
                    if (out == null) return "error:stream";
                } else {
                    if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED) {
                        return "error:permission";
                    }
                    File root = Environment.getExternalStoragePublicDirectory(
                            outPdf ? Environment.DIRECTORY_DOWNLOADS : Environment.DIRECTORY_PICTURES);
                    File dir = new File(root, FOLDER);
                    //noinspection ResultOfMethodCallIgnored
                    dir.mkdirs();
                    outFile = new File(dir, name);
                    out = new FileOutputStream(outFile);
                }
                return "ok";
            } catch (Exception e) {
                abortSave();
                return "error:" + e;
            }
        }

        @JavascriptInterface
        public synchronized String saveChunk(String base64) {
            try {
                if (out == null) return "error:closed";
                out.write(Base64.decode(base64, Base64.DEFAULT));
                return "ok";
            } catch (Exception e) {
                abortSave();
                return "error:" + e;
            }
        }

        @JavascriptInterface
        public synchronized String saveEnd() {
            try {
                if (out == null) return "error:closed";
                out.flush();
                out.close();
                out = null;
                if (outUri != null) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    getContentResolver().update(outUri, v, null, null);
                    outUri = null;
                } else if (outFile != null) {
                    MediaScannerConnection.scanFile(MainActivity.this,
                            new String[]{outFile.getAbsolutePath()}, new String[]{outMime}, null);
                    outFile = null;
                }
                return outPdf ? "ok:Загрузки/" + FOLDER : "ok:Галерея/" + FOLDER;
            } catch (Exception e) {
                abortSave();
                return "error:" + e;
            }
        }

        @JavascriptInterface
        public synchronized void saveAbort() {
            abortSave();
        }

        @JavascriptInterface
        public void setBars(final String color, final boolean darkIcons) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        int c = Color.parseColor(color);
                        getWindow().setStatusBarColor(c);
                        getWindow().setNavigationBarColor(c);
                        int flags = View.SYSTEM_UI_FLAG_VISIBLE;
                        if (darkIcons) {
                            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                        }
                        getWindow().getDecorView().setSystemUiVisibility(flags);
                    } catch (Exception ignored) {
                    }
                }
            });
        }
    }
}
