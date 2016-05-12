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
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND mpN-INFRINGEMENT
 *  ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 *  BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *  SUBSTITUTE GOODS OR SERVICES; LOSS OFUcSE, DATA, OR PROFITS; OR
 *  BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 *  WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 *  OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 *  IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.codeaurora.swe.test;

import org.chromium.base.CommandLine;
import org.chromium.base.test.util.Feature;
import org.chromium.base.test.util.Feature;
import org.chromium.net.test.util.TestWebServer;


import org.codeaurora.swe.PageScaleManager;
import org.codeaurora.swe.WebView;
import org.codeaurora.swe.WebViewClient;

import junit.framework.Assert;

import java.util.List;

public class PageScaleTest extends SWETestBase {

  private static final String LOGTAG = "CookieTest";
  private TestWebServer mWebServer;
  private WebView mWebView;

    @Override
    protected void setUp() throws Exception {
      super.setUp();


      mWebServer = getServer();
      mWebView = createWebView(false);
      setupWebviewClient(mWebView);
      replaceWebView(mWebView, true);
      // wait for the swap because of surfaceview
      java.lang.Thread.sleep(500);
      mWebView.getSettings().setJavaScriptEnabled(true);
    }

    @Override
    protected void tearDown() throws Exception {
      super.tearDown();
      mWebView = null;
    }

    private String getHtml(String headers, String body) throws Exception {
      if (headers == null) {
        headers = "";
      }
      return "<!doctype html><html>" +
               "<head>" +
                   "<style type=\"text/css\">" +
                   "</style>" +
                   headers +
               "</head>" +
               "<body>" +
                   body +
               "</body>" +
           "</html>";
    }

    public void testPageScale() throws Exception {
      String content = getHtml(null,"Page will be scaled here");

      String url = mWebServer.setResponse(
                     "/pagescale.html", content, null);
      loadUrlSync(mWebView, mTestWebViewClient.mOnFirstVisualPixelHelper, url);

      // initally since no page scale, we expect it ot be 0
      assertEquals(PageScaleManager.getInstance().getPageScaleFactor(url),
                   0.0);
      PageScaleManager.getInstance().setPageScaleFactor("localhost.com",1.25);
      // wait for the swap because of surfaceview
      java.lang.Thread.sleep(500);

      assertEquals(PageScaleManager.getInstance().getPageScaleFactor("localhost.com"),
                   1.25);

      // test for subdomain value
      assertEquals(PageScaleManager.getInstance().getPageScaleFactor("test.localhost.com"),
                   1.25);


      List<String> pageScaleLists = PageScaleManager.getInstance().getAllPageScaleFactor();
      assertEquals(pageScaleLists.size(), 1);

      PageScaleManager.getInstance().clearAllPageScaleFactor();

      pageScaleLists = PageScaleManager.getInstance().getAllPageScaleFactor();
      assertEquals(pageScaleLists.size(), 0);

      loadUrlSync(mWebView, mTestWebViewClient.mOnFirstVisualPixelHelper, url);
      assertEquals(PageScaleManager.getInstance().getPageScaleFactor(url),
                   0.0);

    }


}
