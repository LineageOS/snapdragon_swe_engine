/*
 *  Copyright (c) 2014, The Linux Foundation. All rights reserved.
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
 * Portions of this file derived from Chromium code, which is BSD licensed, copyright The Chromium Authors.
 */
package org.codeaurora.swe.test;

import android.content.Context;
import android.os.Bundle;
import android.test.ActivityInstrumentationTestCase2;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import junit.framework.Assert;

import org.chromium.content.browser.ContentViewCore;
import org.chromium.content.browser.test.util.CallbackHelper;
import org.chromium.content.browser.test.util.TestCallbackHelperContainer.OnEvaluateJavaScriptResultHelper;
import org.chromium.content.browser.test.util.Criteria;
import org.chromium.content.browser.test.util.CriteriaHelper;
import org.chromium.net.test.util.TestWebServer;
import static org.chromium.base.test.util.ScalableTimeout.scaleTimeout;

import org.codeaurora.swe.CookieManager;
import org.codeaurora.swe.WebView;
import org.codeaurora.swe.WebChromeClient;
import org.codeaurora.swe.WebViewClient;
import org.codeaurora.swe.WebStorage;
import org.codeaurora.swe.WebBackForwardList;
import org.codeaurora.swe.Engine;

import org.codeaurora.swe.shell.SWEShellActivity;


public class SWETestBase extends ActivityInstrumentationTestCase2<SWEShellActivity> {
    private static final int INITIAL_PROGRESS = 100;
    protected static final long WAIT_TIMEOUT_MS = scaleTimeout(15000);
    protected static final long WAIT_TIMEOUT_SECONDS = scaleTimeout(2);

    protected TestWebViewClient mTestWebViewClient = new TestWebViewClient();
    protected TestWebChromeClient mTestWebChromeClient = new TestWebChromeClient();

    protected SWEShellActivity mActivity;
    private TestWebServer mWebServer;

    public SWETestBase() {
        super(SWEShellActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        mWebServer = TestWebServer.start();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mWebServer.shutdown();
    }

    protected TestWebServer getServer() {
        return mWebServer;
    }

    protected void loadUrlSync(final WebView wv,
                               CallbackHelper helper,
                               final String url) throws Exception {
        int currentCallCount = helper.getCallCount();
        loadUrlAsync(wv, url);
        helper.waitForCallback(currentCallCount, 1,
                WAIT_TIMEOUT_MS,
                TimeUnit.MILLISECONDS);
    }

    protected void loadUrlAsync(final WebView wv,
                                final String url) throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                wv.loadUrl(url);
             }
        });
    }


    protected void loadUrlSync(final WebView wv,
                                CallbackHelper helper,
                                final String url,
                                final Map<String, String> headers) throws Exception {
        int currentCallCount = helper.getCallCount();
        loadUrlAsync(wv, url, headers);
        helper.waitForCallback(currentCallCount, 1,
                WAIT_TIMEOUT_MS,
                TimeUnit.MILLISECONDS);
    }

    protected void loadUrlAsync(final WebView wv,
                                final String url,
                                final Map<String, String> headers) throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                wv.loadUrl(url, headers);
             }
        });
    }

    protected void setUseDesktopUserAgentSync(
            final WebView wv,
            final boolean usedesktop,
            final boolean shouldReload,
            CallbackHelper helper) throws Exception {
        int currentCallCount = helper.getCallCount();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                wv.setUseDesktopUserAgent(usedesktop,shouldReload);
            }
        });

        helper.waitForCallback(currentCallCount, 1, WAIT_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
    }

    protected void goBack (final WebView wv) throws Exception {
         getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                wv.goBack();
            }
        });
    }

    protected void goForward (final WebView wv) throws Exception {
         getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                wv.goForward();
            }
        });
    }

    protected WebView createWebView(final boolean incognito) throws Exception {
       final AtomicReference<WebView> testWebView =
                new AtomicReference<WebView>();
       getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                testWebView.set(getActivity().createWebView(incognito));
             }
        });
        return testWebView.get();
    }

    protected void purgeAllSnapshots() throws Exception {
        Engine.purgeAllSnapshots(getActivity());
    }

    protected void destroyIncognitoProfile() throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                getActivity().destroyIncognitoProfile();
             }
        });
    }

    protected WebView getWebView() throws Exception {
       final AtomicReference<WebView> testWebView =
                new AtomicReference<WebView>();
       getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                testWebView.set(getActivity().getWebView());
             }
        });
        return testWebView.get();
    }

    protected void reloadWebView(final WebView wv) throws Exception {
       getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                wv.reload();
             }
        });
    }

    protected void destroyWebView(final WebView wv) throws Exception {
       getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                wv.destroy();
             }
        });
    }

    protected void pauseWebView(final WebView wv) throws Exception {
       getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                wv.onPause();
             }
        });
    }

    protected void replaceWebView(final WebView wv,
                                  final boolean shouldDestroy) throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                getActivity().replaceWebView(wv, shouldDestroy);
             }
        });
    }

    protected String getCookie(final CookieManager cm, final String url) throws Exception {
        final AtomicReference<String> testCookieValue =
                new AtomicReference<String>();

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                testCookieValue.set(cm.getCookie(url));
             }
        });
        return testCookieValue.get();

    }

    protected WebBackForwardList saveState(final WebView wv, final Bundle b) throws Exception {
        final AtomicReference<WebBackForwardList> testWebBackForwardList =
                new AtomicReference<WebBackForwardList>();

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                testWebBackForwardList.set(wv.saveState(b));
             }
        });
        return testWebBackForwardList.get();
    }


    protected WebBackForwardList restoreState(final WebView wv, final Bundle b) throws Exception {
       final AtomicReference<WebBackForwardList> testWebBackForwardList =
                new AtomicReference<WebBackForwardList>();

        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                testWebBackForwardList.set(wv.restoreState(b));
             }
        });
        return testWebBackForwardList.get();
    }


    protected void setupWebviewClient(WebView wv) {
        assertNotNull(wv);
        wv.setWebViewClient(mTestWebViewClient);
    }

    protected void setupWebChromeClient(WebView wv) {
        assertNotNull(wv);
        wv.setWebChromeClient(mTestWebChromeClient);
    }

    protected void clearCache(
            final WebView wv,
            final boolean includeDiskFiles) throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                wv.clearCache(includeDiskFiles);
            }
        });
    }

    protected void clearWebStorage() throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                WebStorage.getInstance().deleteAllData();
            }
        });
    }

    protected String executeJavaScriptAndWaitForResult(final WebView wv,
            final String code) throws Exception {
        return executeJavaScriptAndWaitForResult(wv.getContentViewCore(),
                mTestWebViewClient.mOnEvaluateJavaScriptResultHelper,
                code);
    }

    private String executeJavaScriptAndWaitForResult(
            final ContentViewCore cvc,
            final OnEvaluateJavaScriptResultHelper onEvaluateJavaScriptResultHelper,
            final String code) throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                onEvaluateJavaScriptResultHelper.evaluateJavaScript(
                        cvc.getWebContents(), code);
            }
        });
        onEvaluateJavaScriptResultHelper.waitUntilHasValue();
        Assert.assertTrue("Failed to retrieve JavaScript evaluation results.",
                onEvaluateJavaScriptResultHelper.hasValue());
        return onEvaluateJavaScriptResultHelper.getJsonResultAndClear();
    }

    public boolean matchJSReturnValue(final WebView wv, final String js,
                                    final String match) throws Exception {
        return CriteriaHelper.pollForCriteria(new Criteria() {
                @Override
                public boolean isSatisfied() {
                    try {
                        String resp = executeJavaScriptAndWaitForResult(wv, js);
                        return resp.trim().equalsIgnoreCase(match);
                    } catch(Exception e) {}
                    return false;
                }
            });
    }
}
