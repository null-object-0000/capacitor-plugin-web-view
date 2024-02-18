import type { Plugin } from '@capacitor/core';
import { registerPlugin } from '@capacitor/core';

import type { WebViewConfig } from './definitions';

export interface CreateWebViewArgs {
    /**
     * web 浏览器实例的唯一标识符。
     * @since 0.0.1
     */
    id: string;
    /**
     * web 浏览器的初始配置设置。
     * @since 0.0.1
     */
    config: WebViewConfig;
    /**
     * The DOM element that the Google Map View will be mounted on which determines size and positioning.
     * @since 0.0.1
     */
    element: HTMLElement;
    /**
     * 如果已经存在具有提供的`id`的 web 浏览器，则销毁并重新创建 web 浏览器实例。
     * @default false
     * @since 0.0.1
     */
    forceCreate?: boolean;
}

export interface WebViewBoundsArgs {
    id: string;
    webViewBounds: {
        x: number;
        y: number;
        width: number;
        height: number;
    };
}

export interface WebViewPlugin extends Plugin {
    getCookie(args: { url: string; key: string | undefined; }): Promise<{ value: string }>;
    setCookie(args: { url: string; key: string; value: string; }): Promise<void>;
    create(options: CreateWebViewArgs): Promise<void>;
    loadUrl(args: { id: string; url: string; }): Promise<void>;
    evaluateJavascript(args: { id: string; script: string; }): Promise<any>;
    destroy(args: { id: string }): Promise<void>;
    show(args: { id: string; }): Promise<void>;
    hide(args: { id: string; }): Promise<void>;
    enableTouch(args: { id: string }): Promise<void>;
    disableTouch(args: { id: string }): Promise<void>;
    onScroll(args: WebViewBoundsArgs): Promise<void>;
    onResize(args: WebViewBoundsArgs): Promise<void>;
    onDisplay(args: WebViewBoundsArgs): Promise<void>;
    dispatchWebViewEvent(args: { id: string; focus: boolean }): Promise<void>;
}

const CapacitorWebView = registerPlugin<WebViewPlugin>('CapacitorWebView');

CapacitorWebView.addListener('isWebViewInFocus', data => {
    const x = data.x;
    const y = data.y;

    const elem = document.elementFromPoint(x, y) as HTMLElement | null;
    const internalId = elem?.dataset?.internalId;
    const webViewInFocus = internalId === data.webViewId;

    CapacitorWebView.dispatchWebViewEvent({ id: data.webViewId, focus: webViewInFocus });
});

export { CapacitorWebView };
