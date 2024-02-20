package site.snewbie.plugins.webview;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;

import androidx.annotation.NonNull;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;

/**
 * https://developer.android.google.cn/develop/ui/views/layout/webapps?hl=zh-cn
 */
@CapacitorPlugin(name = "CapacitorWebView")
public class CapacitorWebViewPlugin extends Plugin {
    private final Map<String, CapacitorWebView> webViews = new HashMap<>();
    private final Map<String, MutableList<MotionEvent>> cachedTouchEvents = new HashMap<>();

    @Override
    public void load() {
        super.load();
        this.setOnTouchListener();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setOnTouchListener() {
        // 禁用 WebView 的点击事件拦截
        super.bridge.getWebView().setOnTouchListener((v, event) -> {
            // 不知道为啥这样写，参考的 capacitor-plugins-google-maps
            // https://github.com/ionic-team/capacitor-plugins/blob/main/google-maps/android/src/main/java/com/capacitorjs/plugins/googlemaps/CapacitorGoogleMapsPlugin.kt#L50
            if (event == null || event.getSource() == -1) {
                return v == null || v.onTouchEvent(event);
            }

            for (CapacitorWebView webView : webViews.values()) {
                if (BooleanUtil.isFalse(webView.isTouchEnabled())) {
                    continue;
                }

                float touchX = event.getX();
                float touchY = event.getY();

                Rect webViewRect = webView.getWebViewBounds();
                if (webViewRect.contains((int) touchX, (int) touchY)) {
                    MutableList<MotionEvent> events = cachedTouchEvents.get(webView.getId());
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        if (events == null) {
                            events = new MutableList<>();
                            cachedTouchEvents.put(webView.getId(), events);
                        }

                        events.clear();
                    }

                    MotionEvent motionEvent = MotionEvent.obtain(event);
                    if (events != null) {
                        events.add(motionEvent);
                    }

                    JSObject payload = new JSObject();
                    payload.put("x", touchX / webView.getConfig().getDevicePixelRatio());
                    payload.put("y", touchY / webView.getConfig().getDevicePixelRatio());

                    this.notifyListeners(webView.getId(), "isWebViewInFocus", payload);
                    return true;
                }
            }

            return v == null || v.onTouchEvent(event);
        });
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        webViews.values().removeIf(webView -> {
            webView.getWebView().destroy();
            return true;
        });
    }

    @Override
    protected void handleOnResume() {
        super.handleOnResume();
        webViews.values().stream().filter(CapacitorWebView::isHidden).forEach(map -> map.updateRender(new RectF(0, 0, 0, 0)));
        webViews.values().forEach(map -> map.getWebView().onResume());
    }

    @Override
    protected void handleOnPause() {
        super.handleOnPause();
        webViews.values().stream().filter(CapacitorWebView::isHidden).forEach(map -> map.updateRender(map.getLastBounds()));
        webViews.values().forEach(map -> map.getWebView().onPause());
    }

    @PluginMethod
    public void getCookie(PluginCall call) {
        String url = call.getString("url");
        if (StrUtil.isBlank(url)) {
            throw new IllegalArgumentException("url is required");
        }

        String cookie = CookieManager.getInstance().getCookie(url);

        String key = call.getString("key");
        if (StrUtil.isBlank(key)) {
            call.resolve(new JSObject().put("value", cookie));
        } else {
            String[] cookies = cookie.split("; ");
            for (String c : cookies) {
                if (c.startsWith(key + "=")) {
                    call.resolve(new JSObject().put("value", StrUtil.removePrefix(c, key + "=")));
                    return;
                }
            }

            call.resolve(new JSObject().put("value", null));
        }
    }

    @PluginMethod
    public void setCookie(PluginCall call) {
        String url = call.getString("url");
        if (StrUtil.isBlank(url)) {
            throw new IllegalArgumentException("url is required");
        }

        String key = call.getString("key");
        if (StrUtil.isBlank(key)) {
            throw new IllegalArgumentException("key is required");
        }

        String value = call.getString("value");
        if (StrUtil.isBlank(value)) {
            throw new IllegalArgumentException("value is required");
        }

        CookieManager.getInstance().setCookie(url, key + "=" + value);
        call.resolve();
    }

    @PluginMethod
    public void removeAllCookies(PluginCall call) {
        CookieManager.getInstance().removeAllCookies(null);
        call.resolve();
    }

    @PluginMethod
    public void hasCookies(PluginCall call) {
        boolean hasCookies = CookieManager.getInstance().hasCookies();
        call.resolve(new JSObject().put("value", hasCookies));
    }

    @PluginMethod
    public void create(PluginCall call) {
        try {
            String id = call.getString("id");
            if (null == id || id.isEmpty()) {
                throw new IllegalArgumentException("id is required");
            }

            JSObject config = call.getObject("config");
            if (null == config) {
                throw new IllegalArgumentException("config object is missing");
            }

            Boolean forceCreate = call.getBoolean("forceCreate", false);

            if (webViews.containsKey(id)) {
                if (ObjUtil.notEqual(forceCreate, true)) {
                    call.resolve();
                    return;
                }

                CapacitorWebView oldWebView = webViews.remove(id);
                if (oldWebView != null) {
                    oldWebView.getWebView().destroy();
                }
            }

            CapacitorWebView webView = new CapacitorWebView(id, new WebViewConfig(config), this, call);
            webViews.put(id, webView);
        } catch (Exception e) {
            call.reject(e.getMessage(), e);
        }
    }

    @PluginMethod
    public void loadUrl(PluginCall call) {
        try {
            CapacitorWebView webView = this.getWebView(call);

            String url = call.getString("url");
            if (StrUtil.isBlank(url)) {
                throw new IllegalArgumentException("url is required");
            }

            super.getActivity().runOnUiThread(() -> {
                webView.getWebView().loadUrl(url);
                call.resolve();
            });
        } catch (Exception e) {
            call.reject(e.getMessage(), e);
        }
    }

    @PluginMethod
    public void evaluateJavascript(PluginCall call) {
        try {
            CapacitorWebView webView = this.getWebView(call);

            String script = call.getString("script");
            if (StrUtil.isBlank(script)) {
                throw new IllegalArgumentException("script is required");
            }

            super.getActivity().runOnUiThread(() -> {
                webView.getWebView().evaluateJavascript(script, value -> {
                    if (value == null) {
                        call.resolve();
                        return;
                    }

                    JSObject result = new JSObject();
                    result.put("value", value);
                    call.resolve(result);
                });
            });
        } catch (Exception e) {
            call.reject(e.getMessage(), e);
        }
    }

    @PluginMethod
    public void destroy(PluginCall call) {
        try {
            String id = call.getString("id");
            if (null == id || id.isEmpty()) {
                throw new IllegalArgumentException("id is required");
            }

            CapacitorWebView removedWebView = webViews.remove(id);
            if (removedWebView == null) {
                throw new IllegalArgumentException("webView not found");
            }

            super.getActivity().runOnUiThread(() -> removedWebView.getWebView().destroy());
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage(), e);
        }
    }

    @PluginMethod
    public void show(PluginCall call) {
        try {
            CapacitorWebView webView = this.getWebView(call);
            webView.setHidden(false);
            webView.updateRender(webView.getLastBounds());
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage(), e);
        }
    }

    @PluginMethod
    public void hide(PluginCall call) {
        try {
            CapacitorWebView webView = this.getWebView(call);
            webView.setHidden(true);
            webView.updateRender(new RectF(0, 0, 0, 0));
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage(), e);
        }
    }

    @PluginMethod
    public void enableTouch(PluginCall call) {
        this.setOnTouchListener();
        this.setTouchEnabled(call, true);
    }

    @PluginMethod
    public void disableTouch(PluginCall call) {
        this.setTouchEnabled(call, false);
    }

    @PluginMethod
    public void onScroll(PluginCall call) {
        this.onResize(call);
    }

    @PluginMethod
    public void onResize(PluginCall call) {
        try {
            CapacitorWebView webView = this.getWebView(call);

            JSONObject boundsObj = call.getObject("webViewBounds");
            if (null == boundsObj) {
                throw new IllegalArgumentException("webViewBounds object is missing");
            }

            RectF bounds = this.boundsObjectToRect(boundsObj);

            webView.updateRender(bounds);

            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage(), e);
        }
    }

    @PluginMethod
    public void onDisplay(PluginCall call) {
        call.unavailable("this call is not available on android");
    }

    @PluginMethod
    public void dispatchWebViewEvent(PluginCall call) {
        try {
            CapacitorWebView webView = this.getWebView(call);

            String id = call.getString("id");

            boolean focus = Boolean.TRUE.equals(call.getBoolean("focus", false));

            MutableList<MotionEvent> events = cachedTouchEvents.get(id);
            if (events != null) {
                while (events.size() > 0) {
                    MotionEvent event = events.first();
                    if (focus) {
                        webView.getWebView().dispatchTouchEvent(event);
                    } else {
                        this.bridge.getWebView().onTouchEvent(event);
                    }
                    events.removeFirst();
                }
            }

            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage(), e);
        }
    }

    private void setTouchEnabled(PluginCall call, boolean enabled) {
        try {
            CapacitorWebView webView = this.getWebView(call);
            webView.setTouchEnabled(enabled);
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage(), e);
        }
    }

    private RectF boundsObjectToRect(JSONObject jsonObject) throws JSONException {
        if (!jsonObject.has("width")) {
            throw new IllegalArgumentException("WebViewConfig object is missing the required 'width' property");
        }

        if (!jsonObject.has("height")) {
            throw new IllegalArgumentException("WebViewConfig object is missing the required 'height' property");
        }

        if (!jsonObject.has("x")) {
            throw new IllegalArgumentException("WebViewConfig object is missing the required 'x' property");
        }

        if (!jsonObject.has("y")) {
            throw new IllegalArgumentException("WebViewConfig object is missing the required 'y' property");
        }

        Double width = jsonObject.getDouble("width");
        Double height = jsonObject.getDouble("height");
        Double x = jsonObject.getDouble("x");
        Double y = jsonObject.getDouble("y");

        return new RectF(x.floatValue(), y.floatValue(), (float) (x + width), (float) (y + height));
    }

    @NonNull
    private CapacitorWebView getWebView(PluginCall call) {
        String id = call.getString("id");
        if (null == id || id.isEmpty()) {
            throw new IllegalArgumentException("id is required");
        }

        CapacitorWebView webView = webViews.get(id);
        if (webView == null) {
            throw new IllegalArgumentException("webView not found");
        }

        return webView;
    }

    public void notifyListeners(String webViewId, String event, JSObject data) {
        if (data == null) {
            data = new JSObject();
        }

        data.put("webViewId", webViewId);

        super.notifyListeners(event, data);
    }
}
