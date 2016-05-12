/*
 *  Copyright (c) 2015, The Linux Foundation. All rights reserved.
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

package org.codeaurora.swe.test;

import android.test.ActivityInstrumentationTestCase2;
import android.util.Pair;

import static org.chromium.base.test.util.ScalableTimeout.scaleTimeout;
import org.chromium.net.test.util.TestWebServer;
import org.chromium.content.browser.test.util.Criteria;
import org.chromium.content.browser.test.util.CriteriaHelper;
import android.test.suitebuilder.annotation.SmallTest;

import org.chromium.base.ThreadUtils;
import org.chromium.base.test.util.Feature;
import org.codeaurora.swe.WebView;
import org.codeaurora.swe.CookieManager;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.List;
import android.util.Log;

public class CookieTest extends SWETestBase {

  private static final String LOGTAG = "CookieTest";
  private TestWebServer mWebServer;
  private WebView mWebView;
  private CookieManager mCookieManager;


   private void setupWebview(WebView wv) {
        assertNotNull(wv);
        setupWebviewClient(wv);
        mWebView.getSettings().setJavaScriptEnabled(true);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mWebServer = getServer();
        mWebView =  getWebView();
        setupWebview(mWebView);
        mCookieManager = CookieManager.getInstance();
        mCookieManager.setAcceptCookie(true);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mWebView != null)
            destroyWebView(mWebView);
        super.tearDown();
    }

    private void setJavascriptCookie(final String name, final String value) throws Exception {
      String setCookieScript = "document.cookie='" + name + "=" + value+"'";
      executeJavaScriptAndWaitForResult(mWebView,setCookieScript);
    }

    private void waitForCookie(final String url) throws Exception{
        final long WAIT_TIMEOUT_MS = scaleTimeout(15000);
        final int CHECK_INTERVAL = 100;

        CriteriaHelper.pollForCriteria(new Criteria() {
            @Override
            public boolean isSatisfied() {
                try {
                    return ThreadUtils.runOnUiThreadBlocking(new Callable<Boolean>() {
                                @Override
                                public Boolean call() throws Exception {
                                  Log.e("AXESH",">>>> Calling Cookie Manager getCookie");
                                  return mCookieManager.getCookie(url) != null;
                                }
                            });
                } catch (Throwable e) {
                    Log.e(LOGTAG, "Exception while polling.", e);
                    return false;
                }
            }
        }, WAIT_TIMEOUT_MS, CHECK_INTERVAL);
    }

    private String getHtml(String headers, String body) throws Exception {
        if (headers == null) {
          headers = "";
        }
        return "<!doctype html><html>" +
                 "<head>" +
                     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\""+
                     "<style type=\"text/css\">" +
                     "</style>" +
                     headers +
                 "</head>" +
                 "<body>" +
                     body +
                 "</body>" +
             "</html>";
    }

    private String getCookieUrl(final String path,
        final String response,
        final String key,
        final String value) {
      List<Pair<String, String>> responseHeaders = new ArrayList<Pair<String, String>>();
        responseHeaders.add(
                Pair.create("Set-Cookie", key + "=" + value + "; path=" + path));
      return mWebServer.setResponse(path, response, responseHeaders);
    }

    public void testGetCookies() throws Exception {
      String blockedCookieSiteContent = getHtml(null,
        "The cookies in this site will be BLOCKED!!!"
      );
      String blockedCookieUrl =  getCookieUrl(
                "/blockedsite.html",blockedCookieSiteContent,"foo","bar");

      String unBlockedCookieSiteContent = getHtml(null,
        "The cookies of this site will be ACCEPTED!!"
      );
      String nonBlockedCookieUrl =  getCookieUrl(
                "/nonblockedsite.html",unBlockedCookieSiteContent,"val","nonblocked");

      // check if cookies are enabled or not
      assertTrue(mCookieManager.acceptCookie());

      // set cookie for non blocked site
      loadUrlSync(mWebView, mTestWebViewClient.mOnPageFinishedHelper, nonBlockedCookieUrl);
      waitForCookie(nonBlockedCookieUrl);
      assertNotNull(getCookie(mCookieManager, nonBlockedCookieUrl));
    }

}
