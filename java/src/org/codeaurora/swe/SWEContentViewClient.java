
/*
 *  Copyright (c) 2015-2016, The Linux Foundation. All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are
 *  met:
 *      * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 *  ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 *  BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 *  BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 *  WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 *  OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 *  IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.codeaurora.swe;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebResourceResponse;
import android.widget.FrameLayout;
import android.Manifest.permission;
import android.content.pm.PackageManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Random;
import org.chromium.base.CommandLine;
import org.chromium.base.ThreadUtils;
import org.chromium.content.browser.ContentViewClient;
import org.chromium.content.browser.ContentVideoView;
import org.chromium.content.browser.ContentVideoViewClient;
import org.chromium.content.common.ContentSwitches;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.Tab;
import org.chromium.chrome.browser.awc.AwContentsIoThreadClient;
import org.chromium.chrome.browser.awc.AwWebResourceResponse;
import org.chromium.chrome.browser.TabObserver;
import org.chromium.chrome.browser.EmptyTabObserver;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.components.navigation_interception.InterceptNavigationDelegate;
import org.chromium.components.navigation_interception.NavigationParams;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.favicon.FaviconHelper.FaviconImageCallback;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.PageTransition;
import org.chromium.net.NetError;
import org.chromium.ui.base.WindowAndroid;
import org.chromium.ui.base.WindowAndroid.PermissionCallback;

class SWEContentViewClient extends ContentViewClient {

    private final static String LOGTAG = "SWEContentViewClient";

    private static final int MSG_ON_LOAD_RESOURCE = 1;
    private static final int MSG_ON_PAGE_STARTED = 2;
    private static final int MSG_ON_DOWNLOAD_START = 3;
    private static final String UNREACHABLE_WEBDATAURL = "data:text/html,chromewebdata";
    private static final int PREFERRED_ICON_SIZE_IN_DP = 48;
    // SWE-TODO: FIXME Use an asset or implement video poster URI
    private static final String DEFAULT_VIDEO_POSTER = "/";

    //SWE-feature-download
    private static class DownloadInfo {
        final String mUrl;
        final String mUserAgent;
        final String mContentDisposition;
        final String mMimeType;
        final String mReferer;
        final String mAuthorization;
        final long mContentLength;
        DownloadInfo(String url,
                     String userAgent,
                     String contentDisposition,
                     String mimeType,
                     String referer,
                     String authorization,
                     long contentLength) {
            mUrl = url;
            mUserAgent = userAgent;
            mContentDisposition = contentDisposition;
            mMimeType = mimeType;
            mReferer = referer;
            mContentLength = contentLength;
            mAuthorization = authorization;
        }
    }
    //SWE-feature-download

    private class TouchIconCallback  implements FaviconImageCallback {
        private int mIconType;
        private FaviconHelper mFaviconHelper;
        private final String mPreCallbackUrl;
        private Tab mTab;

        public TouchIconCallback(Tab tab, int iconType) {
            mIconType = iconType;
            mFaviconHelper = Engine.getFaviconHelperInstance();

            DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
            final int thresholdToGetAnyLargestIcon =
                Math.round(PREFERRED_ICON_SIZE_IN_DP *
                            (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
            final Profile profile = tab.getProfile();
            mPreCallbackUrl = tab.getUrl();
            mTab = tab;

            mFaviconHelper.getLargestRawFaviconForUrl(
                profile,
                mPreCallbackUrl,
                new int[]{iconType},
                thresholdToGetAnyLargestIcon,
                this
            );
        }
        /*
         *  Fetches the TOUCH ICON and since this is callback function
         *  from FaviconImageCallback which is triggered by using getLargestRawFaviconForUrl
         */
        @Override
        public void onFaviconAvailable(Bitmap image, String iconUrl) {

            if (!TextUtils.isEmpty(iconUrl) && mPreCallbackUrl.equals(mTab.getUrl()) ) {
                if ( mIconType ==  FaviconHelper.TOUCH_PRECOMPOSED_ICON) {
                    mWebChromeClient.onReceivedTouchIconUrl(mWebView, iconUrl, true);
                } else {
                    mWebChromeClient.onReceivedTouchIconUrl(mWebView, iconUrl, false);
                }
                mWebView.setTouchIconUrl(iconUrl);
            }
        }
    }
    private class SWETabObserver extends EmptyTabObserver {

        @Override
        public void onDidAttachInterstitialPage(Tab tab) {
            mWebViewClient.onAttachInterstitialPage(mWebView);
        }

        @Override
        public void onDidDetachInterstitialPage(Tab tab) {
            mWebViewClient.onDetachInterstitialPage(mWebView);
        }

        @Override
        public void onSSLStateUpdated(Tab tab) {
            mWebViewClient.onSSLStateUpdated(mWebView);
        }

        @Override
        public void onDidFinishLoad(Tab tab, long frameId, String validatedUrl,
            boolean isMainFrame) {
            boolean isErrorUrl = UNREACHABLE_WEBDATAURL.equals(validatedUrl);

            if (isMainFrame && !isErrorUrl) {
                mWebViewClient.onPageFinished(mWebView, validatedUrl);
            }
        }

        @Override
        public void onDidFailLoad(
                Tab tab, boolean isProvisionalLoad, boolean isMainFrame, int errorCode,
                String description, String failingUrl) {
           boolean isErrorUrl = UNREACHABLE_WEBDATAURL.equals(failingUrl);
            if (isMainFrame && !isErrorUrl) {
                if (errorCode != NetError.ERR_ABORTED) {
                    mWebViewClient.onReceivedError(mWebView,
                            0,//ErrorCodeConversionHelper.convertErrorCode(errorCode),
                            description,failingUrl);
                }
                mWebViewClient.onPageFinished(mWebView, failingUrl);
            }
        }

        @Override
        public void onUrlUpdated(Tab tab) {
            WebContents wc = tab.getWebContents();
            if (wc != null && wc.hasAccessedInitialDocument()) {
                // The document contents were changed while loading from a new domain/url.
                // This is a potential url spoofing attempt.  Show the last known good url or
                // "about:blank" as this is the safest option for the user.
                String url = wc.getLastCommittedUrl();
                url = TextUtils.isEmpty(url) ? "about:blank" : url;
                mWebViewClient.onPageStarted(mWebView, url, null);
                mWebViewClient.onLoadResource(mWebView, url);
                mWebChromeClient.onProgressChanged(mWebView, 100);
                mWebViewClient.onPageFinished(mWebView, url);
            }
        }

        @Override
        public void navigationEntryCommitted(Tab tab) {
            mWebViewClient.onHistoryItemCommit(mWebView, mWebView.getLastCommittedHistoryIndex());
        }

        @Override
        public void onWebContentsSwapped(Tab tab, boolean didStartLoad, boolean didFinishLoad) {
            if (didStartLoad)
                mWebViewClient.onPageStarted(mWebView, mWebView.getUrl(), null);
        }

        @Override
        public void onLoadProgressChanged(Tab tab, int progress) {
            mWebChromeClient.onProgressChanged(mWebView, progress);
        }

        @Override
        public void didNavigateAnyFrame(Tab tab, String url, String baseUrl, boolean isReload) {
            mWebViewClient.doUpdateVisitedHistory(mWebView, url, isReload);
        }

        @Override
        public void onToggleFullscreenMode(Tab tab, boolean enable) {
            toggleFullscreenMode(tab, enable);
        }

        @Override
        public void onFaviconUpdated(Tab tab) {
            // reset the touch icon on every page update
            mWebView.setTouchIconUrl("");
            mWebView.updateFavicon(tab.getFavicon());
            mWebChromeClient.onReceivedIcon(mWebView, tab.getFavicon());
        }

        @Override
        public void onTouchIconAvailable(Tab tab) {

            // fetch the pre-composed icon
            new TouchIconCallback(tab, FaviconHelper.TOUCH_PRECOMPOSED_ICON);

            //fetch the touch icon
            new TouchIconCallback(tab, FaviconHelper.TOUCH_ICON);
        }

        @Override
        public void didFirstVisuallyNonEmptyPaint(Tab tab) {
            mWebViewClient.onFirstVisualPixel(mWebView);
        }
    }

    private class InterceptNavigationDelegateImpl implements InterceptNavigationDelegate {
        @Override
        public boolean shouldIgnoreNavigation(NavigationParams navigationParams) {
            final String url = navigationParams.url;
            boolean ignoreNavigation = false;
            WindowAndroid windowAndroid = null;
            if (mWebView != null && mWebView.getTab() != null)
                windowAndroid = ((Tab) mWebView.getTab()).getWindowAndroid();
            if (url.startsWith("file:") &&
                 (windowAndroid != null && !windowAndroid.hasPermission(
                    permission.WRITE_EXTERNAL_STORAGE))) {
                PermissionCallback permissionCallback = new PermissionCallback() {
                    @Override
                    public void onRequestPermissionsResult(String[] permissions,
                        int[] grantResults) {
                        if (grantResults.length > 0) {
                            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                                mWebView.loadUrl(url);
                            }
                        }
                    }
                };
                windowAndroid.requestPermissions(
                    new String[] {permission.WRITE_EXTERNAL_STORAGE},
                    permissionCallback);
            }
            if (navigationParams.isRedirect ||
                (navigationParams.hasUserGesture &&
                    navigationParams.pageTransitionType == PageTransition.LINK) ||
                navigationParams.isExternalProtocol) {
                    ignoreNavigation = mWebViewClient.shouldOverrideUrlLoading(mWebView, url,
                                                                               navigationParams);
            }
            // We do not want to load external protocol
            if (navigationParams.isExternalProtocol)
                ignoreNavigation = true;
            // // The shouldOverrideUrlLoading call might have resulted in posting messages to the
            // // UI thread. Using sendMessage here (instead of calling onPageStarted directly)
            // // will allow those to run in order.
            //
            if (!ignoreNavigation && !navigationParams.isRedirect) {
                // do not call this for reloads
                if (navigationParams.pageTransitionType != PageTransition.RELOAD)
                    mWebViewClient.beforeNavigation(mWebView, url);
                mWebView.beforeNavigation(navigationParams);
                mHandler.sendMessage(mHandler.obtainMessage(MSG_ON_PAGE_STARTED, url));
            }
            return ignoreNavigation;
        }
    }

    private InputStream getDefaultVideoPosterInputStream()
            throws IOException {
        final PipedInputStream inputStream = new PipedInputStream();
        final PipedOutputStream outputStream = new PipedOutputStream(inputStream);

        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final Bitmap defaultVideoPoster = mWebChromeClient.getDefaultVideoPoster();
                if (defaultVideoPoster == null) {
                    closeDefaultVideoPosterOutputStream(outputStream);
                    return;
                }
                AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            defaultVideoPoster.compress(Bitmap.CompressFormat.PNG, 100,
                                    outputStream);
                            outputStream.flush();
                        } catch (IOException e) {
                            Log.e(LOGTAG, null, e);
                        } finally {
                            closeDefaultVideoPosterOutputStream(outputStream);
                        }
                    }
                });
            }
        });
        return inputStream;
    }

    private static void closeDefaultVideoPosterOutputStream(OutputStream outputStream) {
        try {
            outputStream.close();
        } catch (IOException e) {
            Log.e(LOGTAG, null, e);
        }
    }

    private String mDefaultVideoPosterURL;

    public AwWebResourceResponse interceptRequestForDefaultVideoPoster(final String url) {
        if (!url.contains(mDefaultVideoPosterURL)) return null;
        try {
            return new AwWebResourceResponse("image/png", null, getDefaultVideoPosterInputStream());
        } catch (IOException e) {
            Log.e(LOGTAG, null, e);
            return null;
        }
    }

    public String getDefaultVideoPosterURL() {
        return mDefaultVideoPosterURL;
    }


    private static String generateDefaulVideoPosterURL() {
        Random randomGenerator = new Random();
        String path = String.valueOf(randomGenerator.nextLong());
        return DEFAULT_VIDEO_POSTER + path + ".gif";
    }

    // SWE-feature-WebViewClient.shouldInterceptRequest
    private class IoThreadClientImpl extends AwContentsIoThreadClient {
       // All methods are called on the IO thread.

        @Override
        public int getCacheMode() {
            return 0;
        }

        @Override
        public AwWebResourceResponse shouldInterceptRequest(
                AwContentsIoThreadClient.ShouldInterceptRequestParams params) {
            AwWebResourceResponse awWebResourceResponse = null;
            awWebResourceResponse = interceptRequestForDefaultVideoPoster(params.url);
            if (awWebResourceResponse == null) {
                WebResourceResponse response =
                    mWebViewClient.shouldInterceptRequest(mWebView, params.url);
                if (response != null) {
                    awWebResourceResponse = new AwWebResourceResponse(
                                                    response.getMimeType(),
                                                    response.getEncoding(),
                                                    response.getData());
                }
            }
            return awWebResourceResponse;
        }

        @Override
        public boolean shouldBlockContentUrls() {
            return false;
        }

        @Override
        public boolean shouldBlockFileUrls() {
            return true;
        }

        @Override
        public boolean shouldBlockNetworkLoads() {
            return  false;
        }

        @Override
        public boolean shouldAcceptThirdPartyCookies() {
            return false;
        }

        //SWE-feature-download
        @Override
        public void onDownloadStart(String url, String userAgent,
                String contentDisposition, String mimeType, String referer,
                String authorization, long contentLength) {
            DownloadInfo info = new DownloadInfo(url, userAgent, contentDisposition,
                                                 mimeType, referer, authorization, contentLength);
            mHandler.sendMessage(mHandler.obtainMessage(MSG_ON_DOWNLOAD_START, info));
        }
        //SWE-feature-download

        @Override
        public void newLoginRequest(String realm, String account, String args) {
        }
    }
    // SWE-feature-WebViewClient.shouldInterceptRequest

    private class SWEClientHandler extends Handler {
        private SWEClientHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MSG_ON_LOAD_RESOURCE: {
                    final String url = (String) msg.obj;
                    mWebViewClient.onLoadResource(mWebView, url);
                    break;
                }
                case MSG_ON_PAGE_STARTED: {
                    final String url = (String) msg.obj;
                    mWebViewClient.onPageStarted(mWebView, url, null);
                    break;
                }
                //SWE-feature-download
                case MSG_ON_DOWNLOAD_START: {
                    DownloadInfo info = (DownloadInfo) msg.obj;
                    if (mDownloadListener != null) {
                        if (mDownloadListener instanceof BrowserDownloadListener) {
                            ((BrowserDownloadListener) mDownloadListener).onDownloadStart(
                                                                            info.mUrl,
                                                                            info.mUserAgent,
                                                                            info.mContentDisposition,
                                                                            info.mMimeType,
                                                                            info.mReferer,
                                                                            info.mAuthorization,
                                                                            info.mContentLength);
                        } else {
                            mDownloadListener.onDownloadStart(
                                                            info.mUrl,
                                                            info.mUserAgent,
                                                            info.mContentDisposition,
                                                            info.mMimeType,
                                                            info.mContentLength);
                        }
                    }
                    break;
                }
                //SWE-feature-download
                default:
                    throw new IllegalStateException(
                            "AwContentsClientCallbackHelper: unhandled message " + msg.what);
            }
        }
    }

    public void toggleFullscreenMode(Tab tab, boolean enable) {
        if (enable) {
        } else {
            ContentVideoView videoView = ContentVideoView.getContentVideoView();
            if (videoView != null)
                videoView.exitFullscreen(false);
        }
        mWebChromeClient.toggleFullscreenModeForTab(enable);
    }

    private WebViewClient mWebViewClient;
    private WebChromeClient mWebChromeClient;
    private SWETabObserver mTabObserver;
    private final WebView mWebView;
    private DownloadListener mDownloadListener;
    protected final InterceptNavigationDelegate mInterceptNavigationDelegate;
    protected final IoThreadClientImpl mIoThreadClient;
    private final Handler mHandler;
    private Context mContext;
    private FrameLayout mCustomView = null;

    public SWEContentViewClient (WebView webview, Context context) {
        mWebView = webview;
        mContext = context;
        mWebViewClient = new WebViewClient();
        mWebChromeClient = new WebChromeClient();
        mTabObserver = new SWETabObserver();
        mHandler = new SWEClientHandler(Looper.getMainLooper());
        mDefaultVideoPosterURL = generateDefaulVideoPosterURL();
        PrefServiceBridge.getInstance().setDefaultVideoPosterURL(mDefaultVideoPosterURL);
        mInterceptNavigationDelegate = new InterceptNavigationDelegateImpl();
        mIoThreadClient = new IoThreadClientImpl();
    }

    public void setWebViewClient(WebViewClient client) {
        if (client != null)
            mWebViewClient = client;
        else
            mWebViewClient = new WebViewClient();
    }

    public WebView getWebView() {
        return mWebView;
    }

    public WebViewClient getWebViewClient() {
        return mWebViewClient;
    }

    public WebChromeClient getWebChromeClient() {
        return mWebChromeClient;
    }

    public TabObserver getTabObserver() {
        return mTabObserver;
    }

    public void setWebChromeClient(WebChromeClient client) {
        if (client != null)
            mWebChromeClient = client;
        else
            mWebChromeClient = new WebChromeClient();
    }

    //SWE-feature-download
    public void setDownloadListener(DownloadListener listener) {
        mDownloadListener = listener;
    }
    //SWE-feature-download

    @Override
    public void onUpdateTitle(String title) {
        mWebChromeClient.onReceivedTitle(mWebView, title);
    }

    //SWE-feature-onUnhandledKeyEvent
    @Override
    public void onUnhandledKeyEvent(KeyEvent event) {
        mWebViewClient.onUnhandledKeyEvent(mWebView, event);
    }
    //SWE-feature-onUnhandledKeyEvent

    @Override
    public void onOffsetsForFullscreenChanged(
            float topControlsOffsetYPix,
            float contentOffsetYPix,
            float overdrawBottomHeightPix) {
        mWebView.onOffsetsForFullscreenChanged(topControlsOffsetYPix,
            contentOffsetYPix, overdrawBottomHeightPix);
        mWebChromeClient.onOffsetsForFullscreenChanged(topControlsOffsetYPix,
            contentOffsetYPix, overdrawBottomHeightPix);
    }

    //SWE-feature-shouldOverrideKeyEvent
    @Override
    public boolean shouldOverrideKeyEvent(KeyEvent event) {
        return mWebViewClient.shouldOverrideKeyEvent(mWebView, event);
    }
    //SWE-feature-shouldOverrideKeyEvent

    private class SWEContentVideoViewClient implements ContentVideoViewClient {
        public SWEContentViewClient mClient;

        @Override
        public void setSystemUiVisibility(boolean enterFullscreen){
            // SWE:TODO
        }

        @Override
        public void exitFullscreenVideo() {
             mClient.getWebChromeClient().onHideCustomView();
             if (areHtmlControlsEnabled()) {
                 mClient.getWebView().setOverlayVideoMode(false);
                 restoreZoomControls();
             }
        }

        @Override
        public void enterFullscreenVideo(View view){
             android.webkit.WebChromeClient.CustomViewCallback cb = new android.webkit.WebChromeClient.CustomViewCallback() {
                 @Override
                 public void onCustomViewHidden() {
                     ContentVideoView contentVideoView = ContentVideoView.getContentVideoView();
                     if (contentVideoView != null)
                         contentVideoView.exitFullscreen(false);
                 }
             };
             onShowCustomView(view, cb);
        }

        private void onShowCustomView(View view, android.webkit.WebChromeClient.CustomViewCallback cb) {
            mClient.getWebChromeClient().onShowCustomView(view, cb);
            if (areHtmlControlsEnabled()) {
                mClient.getWebView().setOverlayVideoMode(true);
                mClient.getWebView().setVisibility(View.VISIBLE);
                saveAndDisableZoomControls();
            }
            view.requestFocus();
        }

        @Override
        public View getVideoLoadingProgressView() {
            return mClient.getWebChromeClient().getVideoLoadingProgressView();
        }

        private boolean mZoomControls;

        private void saveAndDisableZoomControls() {
            mZoomControls = mClient.getWebView().getSettings().getBuiltInZoomControls();
            if (mZoomControls)
                mClient.getWebView().getSettings().setBuiltInZoomControls(false);
        }

        private void restoreZoomControls() {
            if (mZoomControls != mWebView.getSettings().getBuiltInZoomControls())
                mClient.getWebView().getSettings().setBuiltInZoomControls(mZoomControls);
        }

        private boolean areHtmlControlsEnabled() {
            return true;
        }
    };

    private SWEContentVideoViewClient mVideoViewClient = null;
    @Override
    public ContentVideoViewClient getContentVideoViewClient() {
        if (mVideoViewClient == null) {
            mVideoViewClient = new SWEContentVideoViewClient();
            mVideoViewClient.mClient = this;
        }

        return mVideoViewClient;
    }

    // SWE-feature-immersive-mode
    @Override
    public void onKeyboardStateChange(boolean popup) {
        mWebViewClient.onKeyboardStateChange(popup);
    }
    // SWE-feature-immersive-mode
}
