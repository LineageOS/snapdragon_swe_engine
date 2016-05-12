/*
 * Copyright (c) 2013-2016 The Linux Foundation. All rights reserved.
 * Not a contribution.
 *
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.codeaurora.swe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.Rect;
import android.net.http.SslCertificate;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.ValueCallback;
import android.widget.FrameLayout;
import android.text.TextUtils;
import android.os.AsyncTask;
import org.chromium.chrome.browser.contextmenu.ContextMenuParams;
import org.chromium.chrome.browser.TabSettings;
import android.view.inputmethod.InputMethodManager;

// SWE-feature-Clear-Browser-Data
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
// SWE-feature-Clear-Browser-Data

import org.chromium.base.VisibleForTesting;

import org.chromium.chrome.browser.UrlUtilities;

import org.chromium.base.JNINamespace;
import org.chromium.base.ThreadUtils;
import org.chromium.chrome.browser.ui.toolbar.ToolbarModelSecurityLevel;
import org.chromium.components.navigation_interception.NavigationParams;
import org.chromium.content_public.browser.JavaScriptCallback;
import org.chromium.content_public.common.TopControlsState;
import org.chromium.content.browser.ContentView;
import org.chromium.content.browser.ContentReadbackHandler;
import org.chromium.content.browser.ContentReadbackHandler.GetBitmapCallback;
import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.ContentViewClient;
import org.chromium.content.browser.ContentViewRenderView;
import org.chromium.content.browser.ContentViewStatics;
import org.chromium.content_public.browser.NavigationEntry;
import org.chromium.content_public.browser.WebContents;
import org.chromium.content_public.browser.LoadUrlParams;
import org.chromium.content_public.browser.NavigationHistory;
import org.chromium.ui.base.ActivityWindowAndroid;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.content.browser.ContentVideoViewClient;
import org.codeaurora.swe.utils.Logger;
import org.chromium.chrome.browser.Tab;
import org.chromium.chrome.browser.UrlConstants;
import org.chromium.chrome.browser.tabmodel.TabModel.TabSelectionType;
import org.chromium.content.browser.ContentViewStatics;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.compositor.layouts.content.ContentOffsetProvider;
import org.chromium.net.NetworkChangeNotifier;
import org.chromium.ui.gfx.DeviceDisplayInfo;

import org.chromium.chrome.browser.findinpage.FindInPageBridge;
import org.chromium.chrome.browser.ChromeWebContentsDelegateAndroid.FindResultListener;
import org.chromium.chrome.browser.FindNotificationDetails;


import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.net.MalformedURLException;
import java.net.URL;
import java.io.File;

@JNINamespace("content")
public class WebView extends ContentView {

    private final static String LOGTAG = "SWEWebView";

    public static final String SCHEME_TEL = "tel:";
    public static final String SCHEME_MAILTO = "mailto:";
    public static final String SCHEME_GEO = "geo:0,0?q=";
    private static final String WEBVIEW_FACTORY =
        "org.codeaurora.swe.WebViewFactoryProvider";
    private static final String TTFP_TAG = "TTFP";
    static final String WEB_ARCHIVE_EXTENSION = ".mht";

    protected void beforeNavigation(NavigationParams navigationParams) {
        if (mAccelerator != null) {
            mAccelerator.beforeNavigation(navigationParams);
        }
    }

    public final class SecurityLevel extends ToolbarModelSecurityLevel {

    }


    public static class HitTestResult {

        public static final int UNKNOWN_TYPE = 0;
        public static final int ANCHOR_TYPE = 1;
        public static final int PHONE_TYPE = 2;
        public static final int GEO_TYPE = 3;
        public static final int EMAIL_TYPE = 4;
        public static final int IMAGE_TYPE = 5;
        public static final int IMAGE_ANCHOR_TYPE = 6;
        public static final int SRC_ANCHOR_TYPE = 7;
        public static final int SRC_IMAGE_ANCHOR_TYPE = 8;
        public static final int EDIT_TEXT_TYPE = 9;

        private int mType;
        private String mExtra;

        public HitTestResult() {
            mType = UNKNOWN_TYPE;
        }

        public void setType(int type) {
            mType = type;
        }

        public void setExtra(String extra) {
            mExtra = extra;
        }

        public int getType() {
            return mType;
        }

        public String getExtra() {
            return mExtra;
        }
    }

    class SWEContentOffsetProvider implements ContentOffsetProvider {
        public int getOverlayTranslateY() {
            return (int) mContentViewCore.getRenderCoordinates().getContentOffsetYPix();
        }
    }

    protected class SWETab extends Tab implements FindResultListener {
        private ContentView mContentView;
        // SWE:TODO: Move this to engine when using single ContentViewRenderView
        //           should be shared by all webviews
        protected TabContentManager mTabContentManager;
        private SWEContentOffsetProvider mSWEContentOffsetProvider;

        public SWETab(Context context, boolean privateBrowsing, WindowAndroid window,
            SWEContentViewClient contentViewClient, ContentView cv,  boolean backgroundTab) {
            super(privateBrowsing, context, window);
            mContentView = cv;
            mSWEContentViewClient = contentViewClient;
            mSWEContentOffsetProvider = new SWEContentOffsetProvider();
            // SWE:TODO: Move this to engine when using single ContentViewRenderView
            //           should be shared by all webviews
            mTabContentManager = new TabContentManager(context, mSWEContentOffsetProvider, true);
            initialize(null, mTabContentManager, backgroundTab);
            setContentViewClient(contentViewClient);
            setInterceptNavigationDelegate(contentViewClient.mInterceptNavigationDelegate);
            setContentsIoThreadClient(contentViewClient.mIoThreadClient);
        }

        protected SWEContentViewClient mSWEContentViewClient;

        protected void setUIResourceProvider(long nativeUIResourceProvider) {
            mTabContentManager.setUIResourceProvider(nativeUIResourceProvider);
        }
        public void setWebContents(WebContents newWebContents) {
            swapWebContents(newWebContents);
            if (getContentViewCore() != null ) {
              getContentViewCore().onAttachedToWindow();
            }
            getContentViewCore().onFocusChanged(hasFocus());
            setInterceptNavigationDelegate(mSWEContentViewClient.mInterceptNavigationDelegate);
            setContentsIoThreadClient(mSWEContentViewClient.mIoThreadClient);
        }

        @Override
        protected ContentView getContentView(ContentViewCore cvc) {
            return mContentView;
        }

        @Override
        protected boolean reuseContentView() {
            return true;
        }

        @Override
        protected void updateTopControlsState(int constraints, int current, boolean animate) {
            super.updateTopControlsState(constraints, current, animate);
        }

        @Override
        public void onNewAsyncBitmap(byte[] data, int size, int width, int height) {
            mPendingCaptureBitmapAsync = false;
            if (mCaptureBitmapAsyncCallback != null) {
                //create bitmap.
                Bitmap bmp;
                final BitmapFactory.Options options = new BitmapFactory.Options();
                options.inMutable = true;
                options.inPurgeable = true;
                options.inInputShareable = true;
                //TODO take config params as input?
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                try {
                    bmp = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                    mCaptureBitmapAsyncCallback.onReceiveValue(bmp);
                } catch (OutOfMemoryError e) {
                    Logger.w(LOGTAG, "Out of memory error captureContentBitmap Failed");
                    mCaptureBitmapAsyncCallback.onReceiveValue(null);
                }
            }
        }

        public void saveWebArchive(
                final String basename, boolean autoname, final ValueCallback<String> callback) {
            if (!autoname) {
                saveWebArchiveInternal(basename, callback);
                return;
            }
            // If auto-generating the file name, handle the name generation on a background thread
            // as it will require I/O access for checking whether previous files existed.
            new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... params) {
                    return generateArchiveAutoNamePath(getOriginalUrl(), basename);
                }

                @Override
                protected void onPostExecute(String result) {
                    saveWebArchiveInternal(result, callback);
                }
            }.execute();
        }

        private class SWETabChromeWebContentsDelegateAndroid
            extends TabChromeWebContentsDelegateAndroid {

            @Override
            public boolean addNewContents(WebContents sourceWebContents, WebContents webContents,
                    int disposition, Rect initialPosition, boolean userGesture) {
                CreateWindowParams params = mSWEContentViewClient.getWebView()
                        .mWindowParamsMap.get(webContents);
                if (params == null) {
                    Log.e(LOGTAG, "addNewContents called before webContentsCreated");
                    return false;
                }
                params.mUserGesture = userGesture;
                Message resultMsg = mSWEContentViewClient.getWebView().createWebViewTransportMessage();
                WebViewTransport webViewTransport = new WebViewTransport();
                webViewTransport.mCreateWindowParams = params;
                resultMsg.obj =  webViewTransport;
                boolean ret =  mSWEContentViewClient.getWebChromeClient()
                        .onCreateWindow(mSWEContentViewClient.getWebView(),
                                        false, //dialog
                                        userGesture,
                                        resultMsg);
                if (!ret) {
                    mSWEContentViewClient.getWebView().mWindowParamsMap.remove(webContents);
                }

                return ret;
            }

            @Override
            public void webContentsCreated(WebContents sourceWebContents, long opener_render_frame_id,
                    String frameName, String targetUrl, WebContents newWebContents) {
                CreateWindowParams params = new CreateWindowParams();
                params.mURL = targetUrl;
                params.mWebContents = newWebContents;
                if (!newWebContents.hasOpener())
                    params.mOpenerSuppressed = true;
                mSWEContentViewClient.getWebView().setCreateWindowParams(newWebContents, params);
            }

            @Override
            public void closeContents() {
                mSWEContentViewClient.getWebChromeClient().onCloseWindow(mSWEContentViewClient.getWebView());
            }

            private boolean mFullScreen = false;

            @Override
            public void toggleFullscreenModeForTab(boolean enterFullscreen) {
                super.toggleFullscreenModeForTab(enterFullscreen);
                mFullScreen = enterFullscreen;
            }

            @Override
            public boolean isFullscreenForTabOrPending() {
                return mFullScreen;
            }
            @Override
            public void activateContents() {
                mSWEContentViewClient.getWebChromeClient().onRequestFocus(mSWEContentViewClient.getWebView());
            }
        }

        public void setFindListener(FindListener listener) {
            mFindlistener = listener;
            getChromeWebContentsDelegateAndroid().setFindResultListener(this);
        }

        @Override
        public void onFindResult(FindNotificationDetails result) {
            if (mFindlistener != null) {
                int activeMatchOrdinal = result.activeMatchOrdinal;
                if (activeMatchOrdinal > 0) {
                    activeMatchOrdinal = activeMatchOrdinal - 1;
                }
                mFindlistener.onFindResultReceived(activeMatchOrdinal,
                                                result.numberOfMatches,
                                                result.finalUpdate);
            }
        }

        @Override
        protected TabChromeWebContentsDelegateAndroid createWebContentsDelegate() {
            return new SWETabChromeWebContentsDelegateAndroid();
        }
    }

    protected class WebViewHandler extends Handler {
        // Message IDs
        protected static final int NOTIFY_CREATE_WINDOW = 1000;
        private final WebView mWebView;
        public WebViewHandler(WebView wv){
            mWebView = wv;
        }
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case NOTIFY_CREATE_WINDOW:
                    WebView wv = null;
                    WebView.WebViewTransport transport =
                        (WebView.WebViewTransport) msg.obj;
                    wv = transport.getWebView();
                    if (wv != null) {
                        CreateWindowParams windowParams = transport.mCreateWindowParams;
                        mWebView.mWindowParamsMap.remove(windowParams.mWebContents);
                        wv.setWebContents(windowParams.mWebContents);
                    }
                    break;
            }
        }
    }

    protected WebViewHandler mWebViewHandler = null;
    public Message createWebViewTransportMessage() {
        return mWebViewHandler.obtainMessage(WebViewHandler.NOTIFY_CREATE_WINDOW);
    }

    public interface PictureListener {
        @Deprecated
        public void onNewPicture(WebView view, Picture picture);
    }

    public interface FindListener {
        public void onFindResultReceived(int activeMatchOrdinal, int numberOfMatches,
            boolean isDoneCounting);
    }

    public interface WebViewDelegate {
        public void loadUrl(String url, Map<String, String> headers);
        public void stopLoading();
        public void destroy();
        public ContentViewRenderView getRenderTarget();
        public WebView getNewWebViewWithAcceleratorDisabled(Context context, AttributeSet attrs, int
                defStyle, boolean privateBrowsing);
        public void onPause();
        public void onResume();
    }

    public class WebViewTransport {
        private WebView mWebview;
        protected CreateWindowParams mCreateWindowParams;

        public synchronized void setWebView(WebView webview) {
            mWebview = webview;
        }

        public synchronized WebView getWebView() {
            return mWebview;
        }

        public synchronized CreateWindowParams getCreateWindowParams() {
            return mCreateWindowParams;
        }
    }

    // SWE-feature-create-window
    public class CreateWindowParams {
        public boolean mUserGesture;
        public boolean mIsGuest;
        public boolean mOpenerSuppressed;
        public String mURL;
        public WebContents mWebContents;

        public CreateWindowParams() {
            mUserGesture = false;
            mIsGuest = false;
            mOpenerSuppressed = false;
            mURL = null;
            mWebContents = null;
        }
    }

    protected HashMap<WebContents, CreateWindowParams> mWindowParamsMap;

    private void setCreateWindowParams(WebContents webContents, CreateWindowParams params) {
        mWindowParamsMap.put(webContents, params);
    }

    public void setPictureListener(WebView.PictureListener listener) {
    }

    private ContentViewRenderView mContentViewRenderView;
    private WindowAndroid mWindow;
    private SWETab mTab;
    private float mCurrentTouchOffsetX;
    private float mCurrentTouchOffsetY = Float.MAX_VALUE;
    private boolean mInScroll = false;
    private SWEContentViewClient mClient;
    private WebSettings mWebSettings;
    private boolean mFindIsUp;
    private FindActionModeCallback mFindCallback;
    private WebView.FindListener mFindlistener;
    private double mDIPScale;
    private boolean mPendingCaptureBitmapAsync = false;
    private ValueCallback<Bitmap> mCaptureBitmapAsyncCallback = null;
    private String mTouchIconUrl = null;
    private FindInPageBridge mFindInPageBridge = null;
    private boolean mEnableAccelerator = true;
    private Accelerator mAccelerator = null;

    public WebView(Context context) {
        this(context, null);
    }

    public WebView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WebView(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs, defStyle, false);
    }

    public WebView(Context context, AttributeSet attrs, int defStyle,
            boolean privateBrowsing) {
        this(context, attrs, defStyle, privateBrowsing, false, true);
    }

    public WebView(Context context, AttributeSet attrs, int defStyle,
            boolean privateBrowsing, boolean backgroundTab) {
        this(context, attrs, defStyle, privateBrowsing, backgroundTab, true);
    }

    private WebView(Context context, AttributeSet attrs, int defStyle,
            boolean privateBrowsing, boolean backgroundTab, boolean enableAccelerator) {
        super(context, null);
        Log.v(LOGTAG,"WebView.WebView");

        mWindowParamsMap = new HashMap<WebContents, CreateWindowParams>();
        mDIPScale = DeviceDisplayInfo.create(context).getDIPScale();
        mWebViewHandler = new WebViewHandler(this);
        mWindow = new ActivityWindowAndroid((Activity)context);
        mClient = new SWEContentViewClient(this, context);
        mTab = new SWETab(context, privateBrowsing, mWindow, mClient, this, backgroundTab);
        mTab.addObserver(mClient.getTabObserver());
        setContentViewCore(mTab.getContentViewCore());
        mContentViewRenderView = new ContentViewRenderView(getContext()) {
            @Override
            protected void onReadyToRender() {
                Log.v(LOGTAG,"onReadyToRender");
                mClient.getWebViewClient().ready();
            }
        };
        mContentViewRenderView.onNativeLibraryLoaded(mWindow);
        mContentViewRenderView.setCurrentContentViewCore(mTab.getContentViewCore());
        mWebSettings = new WebSettings(this, context);
        addView(mContentViewRenderView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));

        initializeFindPageBridge(mTab.getContentViewCore().getWebContents());
        NetworkChangeNotifier.init(context);

        // SWE:TODO: Move this to engine when using single ContentViewRenderView
        mTab.setUIResourceProvider(mContentViewRenderView.getUIResourceProvider());
        mEnableAccelerator = enableAccelerator
                        && !Tab.isFastWebViewDisabled()
                        && !isPowerSaveMode(context);
        if (mEnableAccelerator) {
            try {
                Constructor<?> constructor = Class.forName(
                        "com.qualcomm.qti.sweetview.SweetAccelerator")
                        .getConstructor(WebView.class,
                                WebView.WebViewDelegate.class, Context.class,
                                AttributeSet.class, Integer.TYPE, Boolean.TYPE);
                WebViewDelegate webViewDelegate = new WebViewDelegate() {
                    @Override
                    public void loadUrl(String url, Map<String, String> headers) {
                        if (url != null && !url.startsWith("javascript"))
                            Log.v(TTFP_TAG, "B:loading");
                        loadUrlDirectly(url, headers);
                    }

                    @Override
                    public void stopLoading() {
                        stopLoadingDirectly();
                    }

                    @Override
                    public void destroy() {
                        destroyDirectly();
                    }

                    @Override
                    public ContentViewRenderView getRenderTarget() {
                        return mContentViewRenderView;
                    }

                    @Override
                    public WebView getNewWebViewWithAcceleratorDisabled(
                            Context context, AttributeSet attrs, int defStyle,
                            boolean privateBrowsing) {
                        WebView webView = new WebView(context, attrs, defStyle,
// SWE-feature-backgroundtab
                                privateBrowsing, false, false);
// SWE-feature-backgroundtab
                        return webView;
                    }

                    @Override
                    public void onPause() {
                        onPauseDirectly();
                    }

                    @Override
                    public void onResume() {
                        onResumeDirectly();
                    }
                };
                mAccelerator = (Accelerator)constructor.newInstance(
                        this, webViewDelegate,context, attrs, defStyle, privateBrowsing);
                mTab.addObserver(mAccelerator.getTabObserver());
            } catch (Exception e) {
                Log.v(TTFP_TAG, "Error in initializing fast-webview");
                Logger.dumpTrace(e);
            }
        }
    }

    private void forceIMEDown() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mContentViewRenderView.getWindowToken(), 0);
    }
    private void initializeFindPageBridge(WebContents webcontents){
        if (mFindInPageBridge != null) {
            destroyFindPageBridge();
        }
        mFindInPageBridge = new FindInPageBridge(webcontents);
    }

    private void destroyFindPageBridge() {
        mFindInPageBridge.destroy();
        mFindInPageBridge = null;
    }

    public void setTopControlsHeight(int height, boolean topControlsShrinkBlinkSize) {
        mTab.getContentViewCore().setTopControlsHeight((int)(height * mDIPScale),
            topControlsShrinkBlinkSize);
    }

    public int getTopControlsHeight() {
        return (int) (mTab.getContentViewCore().getTopControlsHeightPix() / mDIPScale);
    }

    public boolean onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        return ((ActivityWindowAndroid) mWindow).onRequestPermissionsResult(
                requestCode, permissions, grantResults);
    }

    @VisibleForTesting
    public ContentViewCore getContentViewCore() {
        return mTab.getContentViewCore();
    }

    // SWE:NEW_API
    public View getView() {
        return mTab.getView();
    }

    public void onPause() {
        if (mAccelerator != null) {
            mAccelerator.onPause();
            return;
        }
        onPauseDirectly();
    }

    private void onPauseDirectly() {
        mTab.hide();
    }

    public void onResume() {
        if (mAccelerator != null) {
            mAccelerator.onResume();
            return;
        }
        onResumeDirectly();
    }

    private void onResumeDirectly() {
        // SWE:TODO: We can differentiate why we are paused/resume look at TabModel.TabSelectionType
        mTab.show(TabSelectionType.FROM_USER);
    }


    public void loadUrl(String url) {
        loadUrl(url, null);
    }

    private boolean mSwappingWebContent = false;

    public void setWebContents(WebContents newWebContents) {
        mSwappingWebContent = true;
        // reset the height of top controls on the new ContentViewCore
        int height = getContentViewCore().getTopControlsHeightPix();
        mTab.setWebContents(newWebContents);
        getContentViewCore().setTopControlsHeight(height, true);
        mSwappingWebContent = false;
        setContentViewCore(mTab.getContentViewCore());
        mContentViewRenderView.setCurrentContentViewCore(mTab.getContentViewCore());
        initializeFindPageBridge(newWebContents);
    }
    public boolean canGoBack() {
        return mTab.canGoBack();
    }

    public void goBack() {
        mTab.goBack();
    }

    public void goForward() {
        mTab.goForward();
    }

    public boolean canGoForward() {
        return mTab.canGoForward();
    }

    public String getUrl() {
        return mTab.getUrl();
    }

    public void reload() {
        mTab.reload();
    }

    public boolean isReady() {
        return mTab.isReady();
    }

    public boolean getUseDesktopUserAgent() {
        return mTab.getUseDesktopUserAgent();
    }

    public void setUseDesktopUserAgent(boolean useDesktop, boolean reloadOnChange) {
        mTab.setUseDesktopUserAgent(useDesktop, reloadOnChange);
    }

    public int getPageBackgroundColor() {
        return mTab.getBackgroundColor();
    }

    public void setWebViewClient(WebViewClient client) {
        mClient.setWebViewClient(client);
    }

    public void setWebChromeClient(WebChromeClient client) {
        mClient.setWebChromeClient(client);
    }

    public boolean isPrivateBrowsingEnabled() {
        return mTab.isIncognito();
    }

    public void setOverlayVideoMode(boolean enabled) {
        if (mContentViewRenderView == null) return;
        mContentViewRenderView.setOverlayVideoMode(enabled);
    }

    public String getTitle() {
        return mTab.getTitle();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            mInScroll = false;
            mCurrentTouchOffsetY = -mContentViewCore
                .getRenderCoordinates().getContentOffsetYPix();
        } else if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mInScroll = true;
        }
        MotionEvent offset = createOffsetMotionEvent(event);
        boolean consumed = mContentViewCore.onTouchEvent(offset);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            int actionIndex = event.getActionIndex();

            // Note this will trigger IPC back to browser even if nothing is
            // hit.
            mTab.requestNewHitTestDataAt(
                        offset.getX() / (float) mDIPScale,
                        offset.getY() / (float) mDIPScale,
                        offset.getTouchMajor() / (float) mDIPScale);
        }
        offset.recycle();
        return consumed;
    }

    public void onOffsetsForFullscreenChanged(float topControlsOffsetYPix,
                                              float contentOffsetYPix,
                                              float overdrawBottomHeightPix) {
        //Conditional on titlebar to be completely hidden or shown
        if (topControlsOffsetYPix == 0.0f || contentOffsetYPix == 0.0f) {
            //if scrolling, wait to apply the touch offsets
            if (!mInScroll)
                mCurrentTouchOffsetY = -contentOffsetYPix;
        }
    }

    private MotionEvent createOffsetMotionEvent(MotionEvent src) {
        MotionEvent dst = MotionEvent.obtain(src);
        dst.offsetLocation(mCurrentTouchOffsetX, mCurrentTouchOffsetY);
        return dst;
    }

    public void loadUrl(String url, Map<String, String> headers) {
        if (mAccelerator != null) {
            mAccelerator.loadUrl(url, headers);
            return;
        }
        loadUrlDirectly(url, headers);
    }

    private void loadUrlDirectly(String url, Map<String, String> headers) {
        if (url == null) return;

        // Sanitize the URL.
        url = UrlUtilities.fixupUrl(url);

        // Invalid URLs will just return empty.
        if (TextUtils.isEmpty(url)) return;

        LoadUrlParams params = new LoadUrlParams(url);
        if (headers != null) {
            params.setExtraHeaders(headers);
            params.setVerbatimHeaders(params.getExtraHttpRequestHeadersString());
        }
        mTab.loadUrl(params);
    }

    private void loadUrl(LoadUrlParams params) {
        if (params != null && mTab != null && mTab.getContentViewCore() != null
                && mTab.getContentViewCore().getWebContents() != null
                && mTab.getContentViewCore().getWebContents().getNavigationController() != null) {
            params.setUrl(sanitizeUrl(params.getUrl()));
            mTab.getContentViewCore().getWebContents().getNavigationController().loadUrl(params);
        }
    }

    public int getContentWidth() {
        return (int) Math.ceil(mContentViewCore.getContentWidthCss());
    }

    public int getContentHeight() {
       return (int) Math.ceil(mContentViewCore.getContentHeightCss());
    }

    public boolean isShowingCrashView() {
        return mTab.isShowingSadTab();
    }
    protected void updateFavicon(Bitmap favicon) {
        NavigationHistory history = mContentViewCore.getWebContents().
            getNavigationController().getNavigationHistory();
        if (history != null && history.getCurrentEntryIndex() >= 0) {
            NavigationEntry entry = history.getEntryAtIndex(history.getCurrentEntryIndex());
            entry.updateFavicon(favicon);
        }
    }

    public int getLastCommittedHistoryIndex() {
        return mContentViewCore.getWebContents().getNavigationController().getLastCommittedEntryIndex();
    }

    private boolean validateNavigationIndex(int historyIndex) {
        NavigationHistory history = mContentViewCore.getWebContents().
            getNavigationController().getNavigationHistory();
        if (history.getEntryCount() <= historyIndex || historyIndex < 0) {
           return false;
        }
        return true;
    }

    private boolean isPowerSaveMode(Context context) {
        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        return mPrefs.getBoolean("powersave_enabled", false)
                || !mPrefs.getBoolean("disable_perf", true);
    }

    /**
    * This method captures the current bitmap and cache's the bitmap
    * @param id for saving the bitmap
    * @param callback to call when bitmap is available
    * @return result if the bitmap will be retrieved
    */
    public void captureSnapshot(int id, final ValueCallback<Bitmap> callback) {
        NavigationHistory history = mContentViewCore.getWebContents().
            getNavigationController().getNavigationHistory();
        TabContentManager.DecompressThumbnailCallback myCallback =
            new TabContentManager.DecompressThumbnailCallback() {
                @Override
                public void onFinishGetBitmap(Bitmap bitmap) {
                    if (callback != null)
                        callback.onReceiveValue(bitmap);
                }
        };
        if (mTab.isHidden() || history.getCurrentEntryIndex() < 0) {
            myCallback.onFinishGetBitmap(null);
        }
        mTab.mTabContentManager.cacheAndGetTabThumbnail(mTab, id, myCallback);
    }

    public void getSnapshotIds(final ValueCallback <List<Integer>> callback) {
        mTab.mTabContentManager.getSnapshotIds(callback);
    }

    /**
    * This method retrieves bitmap that was previously cached
    * @param id for which bitmap is requested
    * @param callback to call when bitmap is available
    * @return result if the bitmap will be retrieved
    */
    public void getSnapshot(int id,
                            final ValueCallback<Bitmap> callback) {
        forceIMEDown();

        TabContentManager.DecompressThumbnailCallback myCallback =
            new TabContentManager.DecompressThumbnailCallback() {
                @Override
                public void onFinishGetBitmap(Bitmap bitmap) {
                    if (callback != null)
                        callback.onReceiveValue(bitmap);
                }
        };
        if (callback == null) {
            myCallback.onFinishGetBitmap(null);
        }
        mTab.mTabContentManager.getThumbnailForId(id, myCallback);
    }

    /**
    * Deletion of snapshot is asynchronous.
    * @param id of the snapshot to delete
    */
    public void deleteSnapshot(int id) {
        if (null != mTab) {
            mTab.mTabContentManager.removeTabThumbnail(id);
        }
    }

    /**
    * @param  id to check for a snapshot.
    * @return Whether or not there is a full sized cached thumbnail for the given Id
    */
    public boolean hasSnapshot(int id) {
        return mTab.mTabContentManager.hasFullCachedThumbnail(id);
    }

    //SWE-TODO : Deprecate
    public void getContentBitmapAsync(float scale, Rect srcRect,
                                      final ValueCallback<Bitmap> callback) {
        if (mTab.isShowingSadTab() || mTab.needsReload()) {
            callback.onReceiveValue(null);
        }
        GetBitmapCallback mycallback = new GetBitmapCallback() {
           @Override
           public void onFinishGetBitmap(Bitmap bitmap) {
               callback.onReceiveValue(bitmap);
           }
        };

        mContentViewRenderView.getContentReadbackHandler().getContentBitmapAsync(scale, srcRect,
                                                      mContentViewCore,
                                                      mycallback);
    }

    public void loadData(String data, String mimeType, String encoding) {
        loadDataWithBaseURL(null, data, mimeType, encoding, null);
    }

    public void loadDataWithBaseURL(String baseUrl, String data, String mimeType,
        String encoding, String historyUrl) {
        LoadUrlParams params = LoadUrlParams.createLoadDataParamsWithBaseUrl(data,
            mimeType, false, baseUrl, historyUrl, encoding);
        mTab.loadUrl(params);
    }

    public void destroy() {
        if (mAccelerator != null) {
            mAccelerator.destroy();
            return;
        }
        destroyDirectly();
    }

    private void destroyDirectly() {
        removeView(mContentViewRenderView);
        mContentViewRenderView.destroy();
        mContentViewRenderView = null;
        mTab.destroy();
        mClient.setWebViewClient(null);
        mClient.setWebChromeClient(null);
        destroyFindPageBridge();
        mTab = null;
    }

    public void removeJavascriptInterface(String name) {
        mContentViewCore.removeJavascriptInterface(name);
    }

    public void addJavascriptInterface(Object object, String name) {
        mContentViewCore.addPossiblyUnsafeJavascriptInterface(object, name, null);
    }

    public void evaluateJavascript (String script, final ValueCallback<String> resultCallback) {
        JavaScriptCallback jsCallback = null;
        if (resultCallback != null) {
            jsCallback = new JavaScriptCallback() {
                @Override
                public void handleJavaScriptResult(String jsonResult) {
                    resultCallback.onReceiveValue(jsonResult);
                }
            };
        }

        mContentViewCore.getWebContents().evaluateJavaScript(script, jsCallback);
    }

    public void exitFullscreen() {
        if (mContentViewCore != null) {
            mContentViewCore.getWebContents().exitFullscreen();
        }
    }

    public void updateTopControls(boolean hide, boolean show, boolean animate) {
        if (mContentViewCore == null || mSwappingWebContent == true) return;
        mTab.updateTopControlsState( getTopControlConstraint(hide, show),
             (show ? TopControlsState.SHOWN :
                     (hide && (show != hide)) ? TopControlsState.HIDDEN : TopControlsState.BOTH),
             animate);
    }

    private int getTopControlConstraint(boolean hide, boolean show) {
        String url = getUrl();
        boolean fixedTopControls = url != null && url.startsWith(UrlConstants.CHROME_SCHEME);
        if (fixedTopControls)
            return TopControlsState.SHOWN;
        else if (show && (show != hide))
            return TopControlsState.SHOWN;
        else if (hide && (show != hide))
            return TopControlsState.HIDDEN;
        else
            return TopControlsState.BOTH;
    }

    public void stopLoading() {
        if (mAccelerator != null) {
            mAccelerator.stopLoading();
            return;
        }
        stopLoadingDirectly();
    }

    private void stopLoadingDirectly() {
        mTab.stopLoading();
    }

    public static void disablePlatformNotifications() {
        ContentViewStatics.disablePlatformNotifications();
    }

    public static void enablePlatformNotifications() {
        ContentViewStatics.enablePlatformNotifications();
    }

    public void saveViewState(String filename, android.webkit.ValueCallback<String> callback) {
        String path = this.getContext().getFilesDir().getAbsolutePath() +
                "/" + filename + WEB_ARCHIVE_EXTENSION;
        saveWebArchive(path, false, callback);
    }

    public void saveWebArchive(
        final String basename, final boolean autoname, final ValueCallback<String> callback) {
        if (!ThreadUtils.runningOnUiThread()) {
            ThreadUtils.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    saveWebArchive(basename, autoname, callback);
                }
            });
            return;
        }
        mTab.saveWebArchive(basename, autoname, callback);
    }

    /**
     * Try to generate a pathname for saving an MHTML archive. This roughly follows WebView's
     * autoname logic.
     */
    private String generateArchiveAutoNamePath(String originalUrl, String baseName) {
        String name = null;
        if (originalUrl != null && !originalUrl.isEmpty()) {
            try {
                String path = new URL(originalUrl).getPath();
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash > 0) {
                    name = path.substring(lastSlash + 1);
                } else {
                    name = path;
                }
            } catch (MalformedURLException e) {
                // If it fails parsing the URL, we'll just rely on the default name below.
            }
        }

        if (TextUtils.isEmpty(name)) name = "index";

        String testName = baseName + name + WEB_ARCHIVE_EXTENSION;
        if (!new File(testName).exists()) return testName;

        for (int i = 1; i < 100; i++) {
            testName = baseName + name + "-" + i + WEB_ARCHIVE_EXTENSION;
            if (!new File(testName).exists()) return testName;
        }

        Log.e(LOGTAG, "Unable to auto generate archive name for path: " + baseName);
        return null;
    }

    public String getOriginalUrl() {
        NavigationHistory history = mContentViewCore.getWebContents().getNavigationController().getNavigationHistory();
        int currentIndex = history.getCurrentEntryIndex();
        if (currentIndex >= 0 && currentIndex < history.getEntryCount()) {
            return history.getEntryAtIndex(currentIndex).getOriginalUrl();
        }
        return null;
    }

    public WebBackForwardList restoreState(Bundle savedState) {
        return (mTab.restoreState(savedState) == true) ?
                copyBackForwardList() : null;
    }

    public WebBackForwardList saveState(Bundle savedState) {
        return (mTab.saveState(savedState) == true) ?
                copyBackForwardList() : null;
    }

    public WebBackForwardList copyBackForwardList() {
        return new WebBackForwardList(mContentViewCore.getWebContents().getNavigationController().getNavigationHistory());
    }

    public void saveWebArchive(String filename) {
         mTab.saveWebArchive(filename, false, null);
    }

    public static String findAddress(String addr) {
        return ContentViewStatics.findAddress(addr);
    }

    public void loadViewState(String filename) {
        String url = sanitizeUrl("file://" + this.getContext().getFilesDir().getAbsolutePath() +
                "/" + filename);
        LoadUrlParams params = new LoadUrlParams(url);
        params.setCanLoadLocalResources(true);
        mTab.loadUrl(params);
    }

    private static String sanitizeUrl(String url) {
        if (url == null) return url;
        if (url.startsWith("www.") || url.indexOf(":") == -1) url = "http://" + url;
        return url;
    }

    public void documentHasImages(Message message) {
        mTab.documentHasImages(message);
    }

    public HitTestResult getHitTestResult() {
        Tab.HitTestData currData =  mTab.getLastHitTestResult();
        HitTestResult result = new HitTestResult();
        result.setType(currData.hitTestResultType);
        result.setExtra(convertToHitTestExtra(currData));
        return result;
    }

    private String convertToHitTestExtra(Tab.HitTestData currData) {
        String extra = "";
        if (currData.hitTestResultType == HitTestResult.IMAGE_TYPE)
            extra = currData.imgSrc;
        else
            extra = currData.hitTestResultExtraData;
        return extra;
    }

    public void requestImageRef(Message msg) {
        mTab.requestImageRef(msg);
    }

    public int getProgress() {
        return 100;
    }

        public boolean showFindDialog(String text, boolean showIme) {
        FindActionModeCallback callback = new FindActionModeCallback(getContext());
        if (getParent() == null || startActionMode(callback) == null) {
            // Could not start the action mode, so end Find on page
            return false;
        }
        mFindCallback = callback;
        setFindIsUp(true);
        mFindCallback.setWebView(this);
        if (showIme) {
            mFindCallback.showSoftInput();
        } else if (text != null) {
            mFindCallback.setText(text);
            mFindCallback.findAll();
            return true;
        }
        if (text != null) {
            mFindCallback.setText(text);
            mFindCallback.findAll();
        }
        return true;
    }

    void notifyFindDialogDismissed() {
        mFindCallback = null;
        clearMatches();
        setFindIsUp(false);
    }

    private void setFindIsUp(boolean isUp) {
        mFindIsUp = isUp;
    }

    public void clearMatches() {
      mFindInPageBridge.stopFinding();
    }

     public void findAllAsync(String searchString) {
        mFindInPageBridge.stopFinding();
        mFindInPageBridge.startFinding(searchString, true, false);
    }

    public void findNext(boolean forward) {
        mFindInPageBridge.startFinding(
                            mFindInPageBridge.getPreviousFindText(),
                            forward,
                            false);
    }

    public void requestFocusNodeHref(Message msg) {
        mTab.requestFocusNodeHref(msg);
    }

    public void setFindListener(WebView.FindListener listener) {
        mTab.setFindListener(listener);
    }

    public Bitmap getFavicon() {
        return mTab.getFavicon();
    }

    public boolean isShowingInterstitialPage() {
        return mTab.isShowingInterstitialPage();
    }

    /**
     * @param callback will receive API callback with Bitmap of content.
     * @param x the x coordinate of the top left corner of content rect.
     * @param y the y coordinate of the top left corner of content rect.
     * @param width the width of the needed content specified in CSS pixels.
     * @param height the height of the needed content specified in CSS pixels.
     * @param scale bitmap returned will be scaled to specified value.
     *
     * This api is used to get portion or all of the content bitmap.
     * The returned bitmap(JPEG encoded) will be bounded to the content
     * rectangle before painting.
     *
     * 'scale' is bounded between 0.0 and 1.0
     *
     * Empty bitmap is returned in case where after applying devicescalefactor,
     * 'scale' and bounded rect if resultant width or height of bitmap exceeds
     * that of the max JPEG limit of 64K pixels.
     */

    public void captureContentBitmap(final ValueCallback<Bitmap> callback,
                                     int x,
                                     int y,
                                     int width,
                                     int height,
                                     float scale) {
        mCaptureBitmapAsyncCallback = callback;
        if (!mPendingCaptureBitmapAsync && callback != null) {
            mPendingCaptureBitmapAsync = mTab.captureBitmapAsync(
                                                             x,
                                                             y,
                                                             width,
                                                             height,
                                                             scale);
        }
    }

    public void onActivityResult(int requestCode, int resultCode,
            Intent intent) {
        mWindow.onActivityResult(requestCode, resultCode, intent);
    }

    public boolean canZoomIn() {
        return mContentViewCore.canZoomIn();
    }

    public boolean canZoomOut() {
        return mContentViewCore.canZoomOut();
    }

    public boolean zoomIn() {
        return mContentViewCore.zoomIn();
    }

    public boolean zoomOut() {
        return mContentViewCore.zoomOut();
    }

    public int getViewScrollY() {
        return mContentViewCore.computeVerticalScrollOffset();
    }

    public int getViewScrollX() {
        return mContentViewCore.computeHorizontalScrollOffset();
    }

    public void pauseTimers() {
        ContentViewStatics.setWebKitSharedTimersSuspended(true);
    }

    public void resumeTimers() {
        ContentViewStatics.setWebKitSharedTimersSuspended(false);
    }

    public void goBackOrForward(int offset) {
        mContentViewCore.getWebContents().getNavigationController().goToOffset(offset);
    }

    public boolean canGoBackOrForward(int offset) {
        return mContentViewCore.getWebContents().getNavigationController().canGoToOffset(offset);
    }

    public void goToHistoryIndex(int offset) {
        mContentViewCore.getWebContents().getNavigationController().goToNavigationIndex(offset);
    }

    public boolean canGoToHistoryIndex(int offset) {
        return validateNavigationIndex(offset);
    }
    public float getScale() {
       return mContentViewCore.getRenderCoordinates().getPageScaleFactor();
    }

    public void invokeZoomPicker() {
        mContentViewCore.invokeZoomPicker();
    }

    public WebSettings getSettings() {
        return mWebSettings;
    }

    public void setDownloadListener(DownloadListener listener) {
        mClient.setDownloadListener(listener);
    }

    public void clearCache(boolean includeDiskFiles) {
        // SWE-feature-Clear-Browser-Data
        PrefServiceBridge.getInstance().clearBrowsingData(null,
                                                          false,
                                                          true,
                                                          false,
                                                          false,
                                                          false);
        // SWE-feature-Clear-Browser-Data
    }

    public void clearHistory() {
        // SWE-feature-Clear-Browser-Data
        PrefServiceBridge.getInstance().clearBrowsingData(null,
                                                          true,
                                                          false,
                                                          false,
                                                          false,
                                                          false);
        // SWE-feature-Clear-Browser-Data
    }

    public int getSecurityLevel() {
        return mTab.getSecurityLevel();
    }

    public SslCertificate getCertificate() {
        return mTab.getCertificate();
    }

    private int clampHorizontalScroll(int scrollX) {
        scrollX = Math.max(0, scrollX);
        scrollX = Math.min(
                    mContentViewCore.getRenderCoordinates().getMaxHorizontalScrollPixInt(),
                    scrollX);
        return scrollX;
    }

    private int clampVerticalScroll(int scrollY) {
        scrollY = Math.max(0, scrollY);
        scrollY = Math.min(
                    mContentViewCore.getRenderCoordinates().getMaxVerticalScrollPixInt(),
                    scrollY);
        return scrollY;
    }

    private boolean performScroll(int x, int y) {
        int scrollX = getViewScrollX();
        int scrollY = getViewScrollY();

        x = clampHorizontalScroll(x);
        y = clampVerticalScroll(y);

        int dx = x - scrollX;
        int dy = y - scrollY;

        if (dx == 0 && dy == 0)
            return false;

        mContentViewCore.scrollTo(x, y);
        return true;
    }
    public boolean pageDown(boolean b) {
        int PAGE_SCROLL_OVERLAP = 24;
        int containerViewHeight = mContentViewCore.getViewportHeightPix();
        int scrollX = getViewScrollX();
        int scrollY = getViewScrollY();

        int dy = containerViewHeight / 2;
        if (containerViewHeight > 2 * PAGE_SCROLL_OVERLAP) {
            dy = containerViewHeight - PAGE_SCROLL_OVERLAP;
        }

        return performScroll(scrollX, scrollY+dy);
    }

    public boolean pageUp(boolean b) {
        int PAGE_SCROLL_OVERLAP = 24;
        int containerViewHeight = mContentViewCore.getViewportHeightPix();
        int scrollX = getViewScrollX();
        int scrollY = getViewScrollY();

        int dy = -containerViewHeight / 2;
        if (containerViewHeight > 2 * PAGE_SCROLL_OVERLAP) {
            dy = -containerViewHeight + PAGE_SCROLL_OVERLAP;
        }

        return performScroll(scrollX, scrollY + dy);
    }

    protected void setTouchIconUrl(String iconUrl){
        mTouchIconUrl = iconUrl;
    }

    protected SWETab getTab() {
        return mTab;
    }

    public String getTouchIconUrl() {
        return mTouchIconUrl;
    }

    public boolean isSavable() {
        return mTab.isSavable();
    }

    public void setNetworkAvailable(boolean networkUp) {
        NetworkChangeNotifier.forceConnectivityState(networkUp);
    }

    //===================Needs To be Deprecated====================

    public void setInitialScale(int scale) {
        // SWE:TODO: Remove calls from browser and remove this method
        // The UI is removed from the browser
    }

    public void flingScroll(int vx, int vy) {
        // SWE:TODO: remove this method !! Not used
    }

    public String[] getHttpAuthUsernamePassword(String host, String realm) {
        // SWE:TODO:  Remove calls from browser and remove this method
        // Chrome Shell has its own Implementation for using HttpAuthentication
        // its defined in LoginPrompt.java
        return null;
    }

    public void setHttpAuthUsernamePassword(String host, String realm, String username,
            String password) {
        // SWE:TODO:  Remove calls from browser and remove this method
        // Chrome Shell has its own Implementation for using HttpAuthentication
        // its defined in LoginPrompt.java
    }

    public void clearFormData() {
        // SWE:TODO: Remove calls from browser
        // Already implemented by WebViewDatabase which uses
        // PreferenceServiceBridge
    }

    public void clearSslPreferences() {
        // SWE:TODO: remove this method !! Not used
    }

    public void stopScroll() {
        // SWE:TODO: Was not implemented in earlier ports
    }

    public Bitmap getViewportBitmap() {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
        return null;
    }

    public interface TitleBarDelegate {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
        int getTitleHeight();
        void onSetEmbeddedTitleBar(View title);
    }

    public void clearViewState() {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    public void dumpDomTree(boolean b) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    public void dumpRenderTree(boolean b) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    public void dumpDisplayTree() {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    public boolean selectText() {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
        return false;
    }

    public void selectAll() {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    public void copySelection() {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    public void loadViewState(InputStream stream) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    public void saveViewState(OutputStream stream,
            ValueCallback<Boolean> callback) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    public void setNetworkType(String type, String subtype) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    public void setJsFlags(String jsFlags) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    public static void setShouldMonitorWebCoreThread() {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    public void setJavascriptInterfaces(Map<String, Object> jsInterfaces) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    public int getVisibleTitleHeight() {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
        return 0;
    }

    public void setMapTrackballToArrowKeys(boolean b) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    public void freeMemory() {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    public void debugDump() {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    public void setCertificate(SslCertificate certificate) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    public void savePassword(String host, String userName, String password) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    public void setScrollBarStyle(int style) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    public boolean overlayHorizontalScrollbar() {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
        return false;
    }

    public boolean overlayVerticalScrollbar() {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
        return false;
    }

    public void setHorizontalScrollbarOverlay(boolean overlay) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    public void setVerticalScrollbarOverlay(boolean overlay) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    public int findAll(String find) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
        return 0;
    }

    public Bitmap getBitmap() {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
        return null;
    }

    public Bitmap getBitmap(int width, int height) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
        return null;
    }
    public boolean isScrollInProgress() {
        ContentViewCore core = mTab.getContentViewCore();
        boolean inProgress = false;
        if (core != null) {
            inProgress = core.isScrollInProgress();
        }
        return inProgress;
    }

    public void disableInfoBar() {
        mTab.getInfoBarContainer().disableInfoBars();
    }

    public TabSettings getTabSettings() {
        return mTab.getTabSettings();
    }
}
