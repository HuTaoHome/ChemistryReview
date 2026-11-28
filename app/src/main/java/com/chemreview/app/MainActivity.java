package com.chemreview.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/** Bundled UI with explicit website/local data providers sharing one progress store. */
public class MainActivity extends Activity {
    private static final String HOST = "chem-review-hh57.pages.dev";
    private static final String HOME = "https://" + HOST + "/";
    private static final int TEAL = Color.rgb(15, 118, 110);
    private static final int PAPER = Color.rgb(244, 246, 248);
    private static final int OPEN_JSON = 100, SAVE_JSON = 101;
    private WebView webView;
    private FrameLayout content;
    private ProgressBar progress;
    private LinearLayout errorPanel;
    private TextView sourceDetail;
    private Button websiteButton, localButton;
    private boolean localMode;
    private String snapshotLabel = "内置题库 · 无需联网";
    private SharedPreferences preferences;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingExport;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("chemistry", MODE_PRIVATE);
        localMode = preferences.getBoolean("local_data", true);
        if (state != null) pendingExport = state.getString("pending_export");
        try (InputStream stream = getAssets().open("web/offline/data.json")) {
            JSONObject snapshot = new JSONObject(readText(stream));
            snapshotLabel = snapshot.getJSONArray("cards").length() + " 个知识点 · "
                    + snapshot.getString("capturedAt").substring(0, 10) + " 快照 · 无需联网";
        } catch (Exception ignored) { /* An unavailable snapshot is handled by the UI. */ }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAPER);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return insets;
            });
        } else {
            getWindow().setStatusBarColor(TEAL);
            getWindow().setNavigationBarColor(TEAL);
        }
        root.addView(createSourceBar());
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        FrameLayout stage = new FrameLayout(this);
        FrameLayout page = new FrameLayout(this);
        page.addView(root, new FrameLayout.LayoutParams(-1, -1));
        stage.addView(page, new FrameLayout.LayoutParams(-1, -1));
        setContentView(stage);
        if (Build.VERSION.SDK_INT >= 30) root.post(() -> {
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        });
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::navigateBack);
        }
        String hash = state == null ? null : state.getString("route");
        openSource(hash);
        if (state == null) {
            LaunchAnimationView launch = new LaunchAnimationView(this, page);
            stage.addView(launch, new FrameLayout.LayoutParams(-1, -1));
            launch.play();
        }
    }

    private View createSourceBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(8), dp(16), dp(4));

        LinearLayout caption = new LinearLayout(this);
        caption.setOrientation(LinearLayout.VERTICAL);
        TextView label = text("数据来源", 12, Color.rgb(96, 113, 110));
        caption.addView(label);
        sourceDetail = text("", 11, Color.rgb(125, 139, 136));
        sourceDetail.setSingleLine(true);
        sourceDetail.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(3);
        caption.addView(sourceDetail, detailParams);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(0, -2, 1);
        captionParams.rightMargin = dp(12);
        bar.addView(caption, captionParams);

        LinearLayout segments = new LinearLayout(this);
        segments.setGravity(Gravity.CENTER_VERTICAL);
        segments.setPadding(dp(4), dp(4), dp(4), dp(4));
        segments.setBackground(rounded(Color.rgb(229, 237, 235), 24));
        websiteButton = sourceButton("网站", false);
        localButton = sourceButton("本地", true);
        segments.addView(websiteButton, new LinearLayout.LayoutParams(0, -1, 1));
        LinearLayout.LayoutParams localParams = new LinearLayout.LayoutParams(0, -1, 1);
        localParams.leftMargin = dp(4);
        segments.addView(localButton, localParams);
        bar.addView(segments, new LinearLayout.LayoutParams(dp(164), dp(48)));
        updateSourceBar();
        return bar;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private Button sourceButton(String title, boolean useLocal) {
        Button button = button(title, () -> switchSource(useLocal));
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setSingleLine(true);
        button.setGravity(Gravity.CENTER);
        button.setStateListAnimator(null);
        button.setElevation(0);
        return button;
    }

    private void updateSourceBar() {
        for (Button button : new Button[]{websiteButton, localButton}) {
            boolean selected = (button == localButton) == localMode;
            button.setSelected(selected);
            button.setTextColor(selected ? Color.WHITE : TEAL);
            button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            button.setBackgroundTintList(null);
            button.setBackground(new RippleDrawable(
                    ColorStateList.valueOf(selected ? 0x33FFFFFF : 0x180F766E),
                    rounded(selected ? TEAL : Color.TRANSPARENT, 20), rounded(Color.WHITE, 20)));
            button.setContentDescription((button == localButton ? "本地数据" : "网站数据")
                    + (selected ? "，当前已选" : "，点击切换"));
        }
        sourceDetail.setText(localMode ? "内置题库 · 离线可用" : "网站题库 · 联网获取");
        String detail = localMode ? snapshotLabel : "联网读取网站最新题库，断网时可切换本地数据";
        sourceDetail.setContentDescription(detail);
        if (Build.VERSION.SDK_INT >= 26) sourceDetail.setTooltipText(detail);
    }

    private String sourceUrl() { return HOME + "?dataSource=" + (localMode ? "local" : "website"); }
    private void switchSource(boolean useLocal) {
        if (localMode == useLocal) return;
        localMode = useLocal;
        preferences.edit().putBoolean("local_data", localMode).apply();
        updateSourceBar();
        openSource(null);
        Toast.makeText(this, "已切换到" + (localMode ? "本地数据" : "网站数据") + "，已记录的复习进度保留", Toast.LENGTH_SHORT).show();
    }

    private void openSource(String hash) {
        closeWebView();
        content.removeAllViews();
        final boolean useLocal = localMode;
        webView = new WebView(this);
        webView.setBackgroundColor(PAPER);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true); // User-selected JSON documents from the system picker.
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBlockNetworkLoads(useLocal);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.addJavascriptInterface(new JsonFiles(), "NativeFiles");
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equals(uri.getScheme()) && HOST.equals(uri.getHost())) {
                    String path = uri.getPath();
                    if ("/".equals(path) || "/index.html".equals(path)) return asset("index.html");
                    if (path != null && !path.contains("..") && (path.startsWith("/assets/") || path.startsWith("/offline/"))) {
                        return asset(path.substring(1));
                    }
                    if (!useLocal && path != null && path.startsWith("/api/")) return null;
                }
                return unavailable();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return routeNavigation(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return routeNavigation(Uri.parse(url));
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (view != webView) return;
                errorPanel.setVisibility(View.GONE);
                progress.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (view == webView) progress.setVisibility(View.GONE);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                if (view == webView && request.isForMainFrame()) showError();
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (view == webView && request.isForMainFrame()) showError();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                if (view != webView) return;
                progress.setProgress(value);
                progress.setVisibility(value == 100 ? View.GONE : View.VISIBLE);
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE);
                try { startActivityForResult(intent, OPEN_JSON); }
                catch (ActivityNotFoundException ignored) { fileCallback.onReceiveValue(null); fileCallback = null; }
                return true;
            }
        });
        content.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setProgressTintList(ColorStateList.valueOf(TEAL));
        content.addView(progress, new FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP));
        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setPadding(dp(24), dp(24), dp(24), dp(24));
        errorPanel.setBackgroundColor(PAPER);
        errorPanel.addView(text("页面加载失败", 22, TEAL));
        TextView detail = text("请重新加载，或在顶部切换数据来源。", 14, Color.DKGRAY);
        detail.setGravity(Gravity.CENTER);
        errorPanel.addView(detail);
        errorPanel.addView(button("重新加载", () -> openSource(null)));
        if (!localMode) errorPanel.addView(button("使用本地数据", () -> switchSource(true)));
        errorPanel.setVisibility(View.GONE);
        content.addView(errorPanel, new FrameLayout.LayoutParams(-1, -1));
        webView.loadUrl(sourceUrl() + (hash == null || hash.isEmpty() ? "" : "#" + hash));
    }

    private WebResourceResponse asset(String path) {
        String mime = path.endsWith(".html") ? "text/html" : path.endsWith(".js") ? "application/javascript"
                : path.endsWith(".css") ? "text/css" : path.endsWith(".json") ? "application/json"
                : path.endsWith(".woff2") ? "font/woff2" : path.endsWith(".woff") ? "font/woff" : "font/ttf";
        try { return new WebResourceResponse(mime, mime.startsWith("font/") ? null : "UTF-8", 200, "OK",
                Collections.singletonMap("Cache-Control", "no-store"), getAssets().open("web/" + path)); }
        catch (IOException ignored) { return unavailable(); }
    }
    private boolean routeNavigation(Uri uri) {
        if ("https".equals(uri.getScheme()) && HOST.equals(uri.getHost()) && "/".equals(uri.getPath())) return false;
        if ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
            catch (ActivityNotFoundException ignored) { Toast.makeText(this, "没有可打开此链接的浏览器", Toast.LENGTH_SHORT).show(); }
        }
        return true;
    }
    private WebResourceResponse unavailable() {
        return new WebResourceResponse("application/json", "UTF-8", 404, "Not Found", Collections.emptyMap(),
                new ByteArrayInputStream("{\"error\":\"资源不可用，请切换数据来源或重试\"}".getBytes(StandardCharsets.UTF_8)));
    }
    private void showError() { progress.setVisibility(View.GONE); errorPanel.setVisibility(View.VISIBLE); }
    private TextView text(String value, int size, int color) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(color); return text;
    }
    private Button button(String label, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setTextSize(13); button.setAllCaps(false);
        button.setOnClickListener(view -> action.run()); return button;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static String readText(InputStream stream) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192]; int length;
        while ((length = stream.read(chunk)) != -1) buffer.write(chunk, 0, length);
        return buffer.toString("UTF-8");
    }

    public class JsonFiles {
        @JavascriptInterface public void saveJson(String filename, String json) {
            if (json == null || json.length() > 2_000_000) return;
            try { new org.json.JSONTokener(json).nextValue(); } catch (Exception ignored) { return; }
            runOnUiThread(() -> {
                if (isFinishing() || pendingExport != null) return;
                pendingExport = json;
                String safeName = filename == null ? "chemistry.json" : filename.replaceAll("[^a-zA-Z0-9._-]", "_");
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
                        .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, safeName);
                try { startActivityForResult(intent, SAVE_JSON); }
                catch (ActivityNotFoundException ignored) { pendingExport = null; }
            });
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == OPEN_JSON && fileCallback != null) {
            fileCallback.onReceiveValue(result == RESULT_OK && data != null && data.getData() != null ? new Uri[]{data.getData()} : null);
            fileCallback = null;
        } else if (request == SAVE_JSON) {
            if (result == RESULT_OK && data != null && data.getData() != null && pendingExport != null) {
                try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
                    if (output == null) throw new IOException("Cannot open document");
                    output.write(pendingExport.getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(this, "已导出 JSON", Toast.LENGTH_SHORT).show();
                } catch (IOException error) {
                    new AlertDialog.Builder(this).setMessage("保存失败，请重试").setPositiveButton("确定", null).show();
                }
            }
            pendingExport = null;
        }
    }
    private void navigateBack() { if (webView != null && webView.canGoBack()) webView.goBack(); else finish(); }
    @Override public void onBackPressed() { navigateBack(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("pending_export", pendingExport);
        if (webView != null && webView.getUrl() != null) out.putString("route", Uri.parse(webView.getUrl()).getFragment());
        super.onSaveInstanceState(out);
    }
    private void closeWebView() {
        if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
        if (webView == null) return;
        WebView old = webView; webView = null;
        old.stopLoading(); old.removeJavascriptInterface("NativeFiles");
        if (old.getParent() instanceof ViewGroup) ((ViewGroup) old.getParent()).removeView(old);
        old.destroy();
    }
    @Override protected void onPause() { if (webView != null) webView.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (webView != null) webView.onResume(); }
    @Override protected void onDestroy() { closeWebView(); super.onDestroy(); }
}
