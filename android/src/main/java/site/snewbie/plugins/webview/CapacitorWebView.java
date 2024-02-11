package site.snewbie.plugins.webview;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.getcapacitor.Bridge;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;
import lombok.Setter;

@Getter
public class CapacitorWebView {
    private final String id;
    private final WebViewConfig config;
    private final CapacitorWebViewPlugin delegate;

    private WebView webView;
    @Setter
    private boolean touchEnabled;
    @Setter
    private boolean hidden;

    private RectF lastBounds;

    public CapacitorWebView(String id, WebViewConfig config, CapacitorWebViewPlugin delegate, PluginCall call) {
        this.id = id;
        this.config = config;
        this.delegate = delegate;

        this.render(call);
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void render(PluginCall call) {
        this.delegate.getActivity().runOnUiThread(() -> {
            try {
                this.webView = new WebView(delegate.getContext());
                this.webView.getSettings().setJavaScriptEnabled(true);
                this.webView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                        webView.loadUrl(request.getUrl().toString());
                        return true;
                    }

                    @Override
                    public void onPageStarted(WebView view, String url, Bitmap favicon) {
                        super.onPageStarted(view, url, favicon);

                        notifyListeners("onPageStarted");
                    }

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);

                        notifyListeners("onPageFinished");
                    }
                });
                this.webView.setWebChromeClient(new WebChromeClient() {
                    @Override
                    public void onProgressChanged(WebView view, int newProgress) {
                        super.onProgressChanged(view, newProgress);

                        notifyListeners("onProgressChanged", new JSObject().put("newProgress", newProgress));
                    }
                });

                Bridge bridge = this.delegate.getBridge();
                FrameLayout webViewParent = new FrameLayout(bridge.getContext());
                webViewParent.setMinimumHeight(bridge.getWebView().getHeight());
                webViewParent.setMinimumWidth(bridge.getWebView().getWidth());

                FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                        this.getScaledPixels(bridge, this.config.getWidth()),
                        this.getScaledPixels(bridge, this.config.getHeight())
                );
                layoutParams.leftMargin = this.getScaledPixels(bridge, this.config.getX());
                layoutParams.topMargin = this.getScaledPixels(bridge, this.config.getY());

                webViewParent.setTag(this.id);

                this.lastBounds = new RectF(this.config.getX(), this.config.getY(), this.config.getX() + this.config.getWidth(), this.config.getY() + this.config.getHeight());
                this.webView.setLayoutParams(layoutParams);
                webViewParent.addView(this.webView);

                ((ViewGroup) (bridge.getWebView().getParent())).addView(webViewParent);

                bridge.getWebView().bringToFront();
                bridge.getWebView().setBackgroundColor(Color.TRANSPARENT);

                this.setWebViewEventListeners();

                if (StrUtil.isNotBlank(config.getUrl())) {
                    this.webView.loadUrl(config.getUrl());
                }

                call.resolve();
            } catch (Exception e) {
                call.reject(e.getMessage(), e);
            }
        });
    }

    public void updateRender(RectF updatedBounds) {
        // 如果 x, y, width, height 任意一个大于 0，就更新 lastBounds
        if (updatedBounds.left > 0 || updatedBounds.top > 0 || updatedBounds.width() > 0 || updatedBounds.height() > 0) {
            this.lastBounds = updatedBounds;
        }

        this.config.setX((int) updatedBounds.left);
        this.config.setY((int) updatedBounds.top);
        this.config.setWidth((int) updatedBounds.width());
        this.config.setHeight((int) updatedBounds.height());

        this.delegate.getActivity().runOnUiThread(() -> {
            Bridge bridge = this.delegate.getBridge();
            RectF webViewRect = getScaledRect(bridge, updatedBounds);
            webView.setX(webViewRect.left);
            webView.setY(webViewRect.top);
            if (webView.getLayoutParams().width != config.getWidth() || webView.getLayoutParams().height != config.getHeight()) {
                webView.getLayoutParams().width = this.getScaledPixels(bridge, config.getWidth());
                webView.getLayoutParams().height = this.getScaledPixels(bridge, config.getHeight());
                webView.requestLayout();
            }
        });
    }

    public Rect getWebViewBounds() {
        return new Rect(
                this.getScaledPixels(this.delegate.getBridge(), config.getX()),
                this.getScaledPixels(this.delegate.getBridge(), config.getY()),
                this.getScaledPixels(this.delegate.getBridge(), config.getX() + config.getWidth()),
                this.getScaledPixels(this.delegate.getBridge(), config.getY() + config.getHeight())
        );
    }

    private Integer getScaledPixels(Bridge bridge, int pixels) {
        // Get the screen's density scale
        float scale = bridge.getActivity().getResources().getDisplayMetrics().density;
        // Convert the dps to pixels, based on density scale
        return (int) (pixels * scale + 0.5f);
    }

    private Float getScaledPixelsF(Bridge bridge, Float pixels) {
        // Get the screen's density scale
        float scale = bridge.getActivity().getResources().getDisplayMetrics().density;
        // Convert the dps to pixels, based on density scale
        return (pixels * scale + 0.5f);
    }

    private RectF getScaledRect(Bridge bridge, RectF rectF) {
        return new RectF(
                this.getScaledPixelsF(bridge, rectF.left),
                this.getScaledPixelsF(bridge, rectF.top),
                this.getScaledPixelsF(bridge, rectF.right),
                this.getScaledPixelsF(bridge, rectF.bottom)
        );
    }

    private void setWebViewEventListeners() {

    }

    public void notifyListeners(String eventName) {
        this.delegate.notifyListeners(this.id, eventName, null);
    }

    public void notifyListeners(String eventName, Object data) {
        Class<?> clazz = data.getClass();
        if (JSObject.class.equals(clazz)) {
            this.delegate.notifyListeners(this.id, eventName, (JSObject) data);
        }
    }
}
