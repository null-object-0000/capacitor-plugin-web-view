import { Capacitor } from '@capacitor/core';
import type { PluginListenerHandle } from '@capacitor/core';

import { WebViewListenerCallback, WebViewReadyCallbackData } from './definitions';
import type { CreateWebViewArgs } from "./implementation";
import { CapacitorWebView } from './implementation';

export interface WebViewInterface {
    /**
     * 获取指定 url 的 cookie。
     * @function WebView.getCookie
     * @param url  要获取 cookie 的 url。
     * @param key  要获取的 cookie 的 key。如果不指定，则返回所有 cookie。
     * @since 0.0.1
     */
    getCookie(url: string, key?: string): Promise<string>;
    /**
     * 设置指定 url 的 cookie。
     * @function WebView.setCookie
     * @param url 要设置 cookie 的 url。
     * @param key 要设置的 cookie 的 key。
     * @param value 要设置的 cookie 的值。
     * @since 0.0.1
     */
    setCookie(url: string, key: string, value: string): Promise<void>;
    /**
     * 移除所有 cookie。
     * @function WebView.removeAllCookies
     * @since 0.0.2
     */
    removeAllCookies(): Promise<void>;
    /**
     * 检查是否存在 cookie。
     * @function WebView.hasCookies
     * @since 0.0.2
     */
    hasCookies(): Promise<boolean>;

    /**
     * 创建 web 浏览器实例。
     * @function WebView.create
     * @since 0.0.1
     */
    create(options: CreateWebViewArgs, callback?: WebViewListenerCallback<WebViewReadyCallbackData>): Promise<WebView>;

    /**
     * 加载指定 url 的内容。
     * @since 0.0.1
     */
    loadUrl(url: string): Promise<void>;
    /**
     * 执行指定的 JavaScript 代码。
     * @since 0.0.1
     */
    evaluateJavascript(script: string): Promise<any>;

    /**
     * 销毁 web 浏览器实例。
     * @since 0.0.1
     */
    destroy(): Promise<void>;
    /**
     * 显示 web 浏览器。
     * @since 0.0.1
     */
    show(): Promise<void>;
    /**
     * 隐藏 web 浏览器。
     * @since 0.0.1
     */
    hide(): Promise<void>;
    /**
     * 设置 web 浏览器允许被触控。
     * @since 0.0.1
     */
    enableTouch(): Promise<void>;
    /**
     * 设置 web 浏览器禁止被触控。
     * @since 0.0.1
     */
    disableTouch(): Promise<void>;

    /**
     * 设置 web 浏览器开始加载页面时的监听器。
     * @since 0.0.1
     */
    setOnPageStartedListener(callback?: WebViewListenerCallback<void>): Promise<void>;
    /**
     * 设置 web 浏览器页面加载完成时的监听器。
     * @since 0.0.1
     */
    setOnPageFinishedListener(callback?: WebViewListenerCallback<void>): Promise<void>;
    /**
     * 设置 web 浏览器加载进度变化时的监听器。
     * @since 0.0.1
     */
    setOnProgressChangedListener(callback?: WebViewListenerCallback<{ newProgress: number }>): Promise<void>;
}

export class WebView implements WebViewInterface {
    /**
     * web 浏览器实例的唯一标识符。
     */
    private id: string;
    private element: HTMLElement | null = null;
    private resizeObserver: ResizeObserver | null = null;

    private onPageStartedListener?: PluginListenerHandle;
    private onPageFinishedListener?: PluginListenerHandle;
    private onProgressChangedListener?: PluginListenerHandle;

    private constructor(id: string) {
        this.id = id;
    }

    public static async getCookie(url: string, key?: string): Promise<string> {
        const { value } = await CapacitorWebView.getCookie({ url, key });
        return value;
    }

    public static async setCookie(url: string, key: string, value: string): Promise<void> {
        return CapacitorWebView.setCookie({ url, key, value });
    }

    public static async removeAllCookies(): Promise<void> {
        return CapacitorWebView.removeAllCookies();
    }

    public static async hasCookies(): Promise<boolean> {
        const { value } = await CapacitorWebView.hasCookies();
        return value;
    }

    public static async create(options: CreateWebViewArgs, callback?: WebViewListenerCallback<WebViewReadyCallbackData>): Promise<WebView> {
        const newWebView = new WebView(options.id);

        if (!options.element) {
            throw new Error('container element is required');
        }

        newWebView.element = options.element;
        newWebView.element.dataset.internalId = options.id;

        const elementBounds = await WebView.getElementBounds(options.element);
        options.config.width = elementBounds.width;
        options.config.height = elementBounds.height;
        options.config.x = elementBounds.x;
        options.config.y = elementBounds.y;
        options.config.devicePixelRatio = window.devicePixelRatio;

        if (Capacitor.getPlatform() == 'android') {
            newWebView.initScrolling();
        }

        if (Capacitor.isNativePlatform()) {
            (options.element as any) = {};

            const getWebViewBounds = () => newWebView.element?.getBoundingClientRect() ?? ({} as DOMRect);

            const onDisplay = () => {
                CapacitorWebView.onDisplay({
                    id: newWebView.id,
                    webViewBounds: getWebViewBounds(),
                });
            };

            const onResize = () => {
                CapacitorWebView.onResize({
                    id: newWebView.id,
                    webViewBounds: getWebViewBounds(),
                });
            };

            const ionicPage = newWebView.element.closest('.ion-page');
            if (Capacitor.getPlatform() === 'ios' && ionicPage) {
                ionicPage.addEventListener('ionViewWillEnter', () => {
                    setTimeout(() => {
                        onDisplay();
                    }, 100);
                });
                ionicPage.addEventListener('ionViewDidEnter', () => {
                    setTimeout(() => {
                        onDisplay();
                    }, 100);
                });
            }

            const lastState = {
                width: elementBounds.width,
                height: elementBounds.height,
                isHidden: false,
            };
            newWebView.resizeObserver = new ResizeObserver(() => {
                if (newWebView.element != null) {
                    const webViewRect = newWebView.element.getBoundingClientRect();

                    const isHidden = webViewRect.width === 0 && webViewRect.height === 0;
                    if (!isHidden) {
                        if (lastState.isHidden) {
                            if (Capacitor.getPlatform() === 'ios' && !ionicPage) {
                                onDisplay();
                            }
                        } else if (
                            lastState.width !== webViewRect.width ||
                            lastState.height !== webViewRect.height
                        ) {
                            onResize();
                        }
                    }

                    lastState.width = webViewRect.width;
                    lastState.height = webViewRect.height;
                    lastState.isHidden = isHidden;
                }
            });
            newWebView.resizeObserver.observe(newWebView.element);
        }

        // small delay to allow for iOS WKWebView to setup corresponding element sub-scroll views ???
        await new Promise((resolve, reject) => {
            setTimeout(async () => {
                try {
                    await CapacitorWebView.create(options);
                    resolve(undefined);
                } catch (err) {
                    reject(err);
                }
            }, 200);
        });

        if (callback) {
            const onWebViewReadyListener = await CapacitorWebView.addListener(
                'onWebViewReady',
                (data: WebViewReadyCallbackData) => {
                    if (data.webViewId == newWebView.id) {
                        callback(data);
                        onWebViewReadyListener.remove();
                    }
                },
            );
        }

        return newWebView;
    }

    private static async getElementBounds(element: HTMLElement): Promise<DOMRect> {
        return new Promise(resolve => {
            let elementBounds = element.getBoundingClientRect();
            if (elementBounds.width == 0) {
                let retries = 0;
                const boundsInterval = setInterval(function () {
                    if (elementBounds.width == 0 && retries < 30) {
                        elementBounds = element.getBoundingClientRect();
                        retries++;
                    } else {
                        if (retries == 30) {
                            console.warn('WebView size could not be determined');
                        }
                        clearInterval(boundsInterval);
                        resolve(elementBounds);
                    }
                }, 100);
            } else {
                resolve(elementBounds);
            }
        });
    }

    /**
     * @deprecated Use WebView.getCookie instead.
     */
    public getCookie(_url: string, _key?: string | undefined): Promise<string> {
        throw new Error('Method not implemented.');
    }

    /**
     * @deprecated Use WebView.setCookie instead.
     */
    public setCookie(_url: string, _key: string, _value: string): Promise<void> {
        throw new Error('Method not implemented.');
    }

    /**
     * @deprecated Use WebView.removeAllCookies instead.
     */
    public removeAllCookies(): Promise<void> {
        throw new Error('Method not implemented.');
    }

    /**
     * @deprecated Use WebView.hasCookies instead.
     */
    public hasCookies(): Promise<boolean> {
        throw new Error('Method not implemented.');
    }

    /**
     * @deprecated Use WebView.create instead.
     */
    public create(_options: CreateWebViewArgs, _callback?: WebViewListenerCallback<WebViewReadyCallbackData>): Promise<WebView> {
        throw new Error('Method not implemented.');
    }

    public loadUrl(url: string): Promise<void> {
        return CapacitorWebView.loadUrl({ id: this.id, url });
    }

    public evaluateJavascript(script: string): Promise<any> {
        return CapacitorWebView.evaluateJavascript({ id: this.id, script });
    }

    public async destroy(): Promise<void> {
        if (Capacitor.getPlatform() == 'android') {
            this.disableScrolling();
        }

        if (Capacitor.isNativePlatform()) {
            this.resizeObserver?.disconnect();
        }

        this.removeAllWebViewListeners();

        return CapacitorWebView.destroy({
            id: this.id,
        });
    }

    public show(): Promise<void> {
        return CapacitorWebView.show({ id: this.id });
    }

    public hide(): Promise<void> {
        return CapacitorWebView.hide({ id: this.id });
    }

    public enableTouch(): Promise<void> {
        return CapacitorWebView.enableTouch({ id: this.id });
    }

    public disableTouch(): Promise<void> {
        return CapacitorWebView.disableTouch({ id: this.id });
    }

    private initScrolling(): void {
        const ionContents = document.getElementsByTagName('ion-content');

        // eslint-disable-next-line @typescript-eslint/prefer-for-of
        for (let i = 0; i < ionContents.length; i++) {
            (ionContents[i] as any).scrollEvents = true;
        }

        window.addEventListener('ionScroll', this.handleScrollEvent);
        window.addEventListener('scroll', this.handleScrollEvent);
        window.addEventListener('resize', this.handleScrollEvent);
        if (screen.orientation) {
            screen.orientation.addEventListener('change', () => {
                setTimeout(this.updateWebViewBounds, 500);
            });
        } else {
            window.addEventListener('orientationchange', () => {
                setTimeout(this.updateWebViewBounds, 500);
            });
        }
    }

    private disableScrolling(): void {
        window.removeEventListener('ionScroll', this.handleScrollEvent);
        window.removeEventListener('scroll', this.handleScrollEvent);
        window.removeEventListener('resize', this.handleScrollEvent);
        if (screen.orientation) {
            screen.orientation.removeEventListener('change', () => {
                setTimeout(this.updateWebViewBounds, 1000);
            });
        } else {
            window.removeEventListener('orientationchange', () => {
                setTimeout(this.updateWebViewBounds, 1000);
            });
        }
    }

    private handleScrollEvent = (): void => this.updateWebViewBounds();

    private updateWebViewBounds(): void {
        if (this.element) {
            const webViewRect = this.element.getBoundingClientRect();

            CapacitorWebView.onScroll({
                id: this.id,
                webViewBounds: webViewRect,
            });
        }
    }

    public async setOnPageStartedListener(callback?: WebViewListenerCallback<void> | undefined): Promise<void> {
        if (this.onPageStartedListener) {
            this.onPageStartedListener.remove();
        }

        if (callback) {
            this.onPageStartedListener = await CapacitorWebView.addListener('onPageStarted', this.generateCallback(callback));
        } else {
            this.onPageStartedListener = undefined;
        }
    }

    public async setOnPageFinishedListener(callback?: WebViewListenerCallback<void> | undefined): Promise<void> {
        if (this.onPageFinishedListener) {
            this.onPageFinishedListener.remove();
        }

        if (callback) {
            this.onPageFinishedListener = await CapacitorWebView.addListener('onPageFinished', this.generateCallback(callback));
        } else {
            this.onPageFinishedListener = undefined;
        }
    }

    public async setOnProgressChangedListener(callback?: WebViewListenerCallback<{ newProgress: number; }> | undefined): Promise<void> {
        if (this.onProgressChangedListener) {
            this.onProgressChangedListener.remove();
        }

        if (callback) {
            this.onProgressChangedListener = await CapacitorWebView.addListener('onProgressChanged', this.generateCallback(callback));
        } else {
            this.onProgressChangedListener = undefined;
        }
    }

    private async removeAllWebViewListeners(): Promise<void> {

    }

    private generateCallback(callback: WebViewListenerCallback<any>): WebViewListenerCallback<any> {
        const webViewId = this.id;
        return (data: any) => {
            if (data.webViewId == webViewId) {
                callback(data);
            }
        };
    }
}