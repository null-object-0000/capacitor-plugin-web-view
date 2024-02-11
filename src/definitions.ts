export interface WebViewConfig {
  /**
   * Override width for native map.
   * @since 0.0.1
   */
  width?: number;
  /**
   * Override height for native map.
   * @since 0.0.1
   */
  height?: number;
  /**
   * Override absolute x coordinate position for native map.
   * @since 0.0.1
   */
  x?: number;
  /**
   * Override absolute y coordinate position for native map.
   * @since 0.0.1
   */
  y?: number;
  /**
   * Override pixel ratio for native map.
   * @default 1.00f
   * @since 0.0.1
   */
  devicePixelRatio?: number;

  /**
   * 指定要加载的 URL，为空时将不进行加载动作。
   * @since 0.0.1
   */
  url?: string;
}

/**
 * The callback function to be called when web-view events are emitted.
 * @since 0.0.1
 */
export type WebViewListenerCallback<T> = (data: T) => void;

export interface WebViewReadyCallbackData {
  webViewId: string;
}