package site.snewbie.plugins.webview;

import com.getcapacitor.JSObject;

import org.json.JSONException;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class WebViewConfig {
    private Integer width;
    private Integer height;
    private Integer x;
    private Integer y;
    private Float devicePixelRatio = 1.00f;

    private String url;

    public WebViewConfig(JSObject fromJSONObject) throws JSONException {
        if (!fromJSONObject.has("width")) {
            throw new IllegalArgumentException("AMapConfig object is missing the required 'width' property");
        }

        if (!fromJSONObject.has("height")) {
            throw new IllegalArgumentException("AMapConfig object is missing the required 'height' property");
        }

        if (!fromJSONObject.has("x")) {
            throw new IllegalArgumentException("AMapConfig object is missing the required 'x' property");
        }

        if (!fromJSONObject.has("y")) {
            throw new IllegalArgumentException("AMapConfig object is missing the required 'y' property");
        }

        if (fromJSONObject.has("devicePixelRatio")) {
            devicePixelRatio = Double.valueOf(fromJSONObject.getDouble("devicePixelRatio")).floatValue();
        }

        if (fromJSONObject.has("url")) {
            url = fromJSONObject.getString("url");
        }

        width = fromJSONObject.getInt("width");
        height = fromJSONObject.getInt("height");
        x = fromJSONObject.getInt("x");
        y = fromJSONObject.getInt("y");
    }
}
