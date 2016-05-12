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
 */
package org.codeaurora.swe.test;

import android.content.Context;
import android.os.Bundle;
import android.util.Pair;
import android.util.Log;

import android.test.ActivityInstrumentationTestCase2;

import org.chromium.net.test.util.TestWebServer;

import org.chromium.base.test.util.Feature;
import org.codeaurora.swe.WebView;
import org.codeaurora.swe.WebSettings;
import org.codeaurora.swe.WebBackForwardList;

import java.lang.Thread;
import java.util.ArrayList;
import java.util.List;

public class TouchIconTest  extends SWETestBase {

    private static final String LOGTAG = "TouchIconTest";
    private static final String TOUCHICON_REL_LINK = "touch.png";
    private static final String TOUCHICON_REL_URL = "/" + TOUCHICON_REL_LINK;
    private static final String FAVICON_REL_URL="/favicon.ico";
    private TestWebServer mWebServer;
    private WebView mWebView;

    private void setupWebview(WebView wv) {
        assertNotNull(wv);
        setupWebviewClient(wv);
        setupWebChromeClient(wv);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mWebServer = getServer();
        mWebView =  getWebView();
        setupWebview(mWebView);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mWebView != null)
            destroyWebView(mWebView);
        super.tearDown();
    }


    private String getHtml(String headers, String body) {
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


    private String getTouchIconUrl() {
        String IMAGE_DATA = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAAAAAA" +
                            "6fptVAAAAAXNSR0IArs4c6QAAAA1JREFUCB0BAgD9/w" +
                            "DQANIA0U9MSZY"+"AAAAASUVORK5CYII=";

        List<Pair<String, String>> headers = new ArrayList<Pair<String, String>>();
        headers.add(Pair.create("Content-Type", "image/png"));

        final String imagePath = TOUCHICON_REL_URL;
        // load a png image and page cannot be saved
        String imageUrl =  mWebServer.setResponseBase64(imagePath, IMAGE_DATA,
               headers);

        return imageUrl;
    }


    @Feature({"FavIcon"})
    public void testBasicFavIcon() throws Exception {

        String favIconLink = getTouchIconUrl();

        String favIconHtml = getHtml(
                "<link rel=\"shortcut icon\" href=\""+favIconLink+"\" />",
                "FAV ICON Page"
            );

        String favIconUrl =  mWebServer.setResponse(
                "/favicon.html",favIconHtml,null);

        mTestWebChromeClient.mOnReceivedIconHelper.setIconType(
                    mTestWebChromeClient.mOnReceivedIconHelper.FAV_ICON);

        loadUrlSync(mWebView, mTestWebChromeClient.mOnReceivedIconHelper, favIconUrl);
        assertNotNull(mTestWebChromeClient.mOnReceivedIconHelper.getFavIcon());
    }


    @Feature({"TouchIcon"})
    public void testBasicTouchIcon() throws Exception {

        String touchIconLink = getTouchIconUrl();

        String touchIconHtml = getHtml(
                "<link rel=\"shortcut icon\" type=\"image/png\" href=\""+FAVICON_REL_URL+"\" />"+
                "<link rel=\"apple-touch-icon\" href=\""+touchIconLink+"\" />",
                "Touch Icon Page"
            );

        String nonTouchIconHtml = getHtml(
                "<title>Foo</title>",
                "Foo Bar Bax"
            );

        String touchIconUrl =  mWebServer.setResponse(
                "/touchicon.html",touchIconHtml,null);

        String normalUrl =  mWebServer.setResponse(
                "/foo.html",touchIconHtml,null);


        mTestWebChromeClient.mOnReceivedIconHelper.setIconType(
                    mTestWebChromeClient.mOnReceivedIconHelper.TOUCH_ICON);


        loadUrlSync(mWebView, mTestWebChromeClient.mOnReceivedIconHelper, touchIconUrl);
        assertNotNull(mTestWebChromeClient.mOnReceivedIconHelper.getIconUrl());
        assertFalse( mTestWebChromeClient.mOnReceivedIconHelper.getIconUrl().isEmpty());
        assertFalse( mTestWebChromeClient.mOnReceivedIconHelper.isPrecomposed());
        assertEquals(mTestWebChromeClient.mOnReceivedIconHelper.getIconUrl(),mWebServer.getBaseUrl()+TOUCHICON_REL_LINK);


        loadUrlSync(mWebView, mTestWebChromeClient.mOnReceivedIconHelper, normalUrl);
        assertEquals(1, mWebServer.getRequestCount("/foo.html"));
        goBack(mWebView);
        goForward(mWebView);
        goBack(mWebView);
        assertEquals(mWebView.getTouchIconUrl(),mWebServer.getBaseUrl()+TOUCHICON_REL_LINK);

    }


    @Feature({"TouchIcon"})
    public void testPrecomposedTouchIcon()  throws Exception {
        String touchIconLink = getTouchIconUrl();

        String precomposedTouchIconHtml = getHtml(
                "<link rel=\"shortcut icon\" type=\"image/png\" href=\""+FAVICON_REL_URL+"\" />"+
                "<link rel=\"apple-touch-icon-precomposed\" href=\""+touchIconLink+"\" />",
                "Precomposed Touch Icon Page"
        );

        mTestWebChromeClient.mOnReceivedIconHelper.setIconType(
                    mTestWebChromeClient.mOnReceivedIconHelper.TOUCH_ICON);

        String precomposedTouchIconUrl = mWebServer.setResponse(
                "/precomposedtouchicon.html",precomposedTouchIconHtml,null);

        loadUrlSync(mWebView, mTestWebChromeClient.mOnReceivedIconHelper, precomposedTouchIconUrl);
        assertNotNull(mTestWebChromeClient.mOnReceivedIconHelper.getIconUrl());
        assertFalse(mTestWebChromeClient.mOnReceivedIconHelper.getIconUrl().isEmpty());
        assertEquals(mTestWebChromeClient.mOnReceivedIconHelper.getIconUrl(),touchIconLink);
        assertTrue(mTestWebChromeClient.mOnReceivedIconHelper.isPrecomposed());
    }

    @Feature({"TouchIcon"})
    public void testTouchIconWithWebViewCrash() throws Exception {
        WebView oldWebView = createWebView(false);
        setupWebview(oldWebView);
        String touchIconImageUrl = getTouchIconUrl();

        String touchIconHtml = getHtml(
            "<link rel=\"apple-touch-icon\" href=\"" + touchIconImageUrl + "\" />",
            "Touch Icon Page"
        );

        String touchIconLink = "/touchicon.html";

        String touchIconUrl =   mWebServer.setResponse(
                                    touchIconLink,touchIconHtml,null);

        mTestWebChromeClient.mOnReceivedIconHelper.setIconType(
                    mTestWebChromeClient.mOnReceivedIconHelper.TOUCH_ICON);

        loadUrlSync(oldWebView, mTestWebChromeClient.mOnReceivedIconHelper, touchIconUrl);

        //validate the touch url
        assertEquals(mTestWebChromeClient.mOnReceivedIconHelper.getIconUrl(),touchIconImageUrl);
        assertEquals(1, mWebServer.getRequestCount(touchIconLink));

         // save the list
        Bundle bundle = new Bundle();
        WebBackForwardList saveList = saveState(oldWebView, bundle);
        assertNotNull(saveList);
        assertEquals(1, saveList.getSize());
        assertEquals(0, saveList.getCurrentIndex());

        // Create a another normal webview check if we load from cache
        WebView newWebView = createWebView(false);
        setupWebview(newWebView);

        //destroy the old webview
        destroyWebView(oldWebView);

        final WebBackForwardList restoreList = restoreState(newWebView, bundle);
        assertNotNull(restoreList);
        assertEquals(1, restoreList.getSize());
        assertEquals(0, restoreList.getCurrentIndex());

        //go back to touchIcon page and assure that touchicon gets called
        assertEquals(mTestWebChromeClient.mOnReceivedIconHelper.getIconUrl(),touchIconImageUrl);

        destroyWebView(newWebView);
    }


    @Feature({"TouchIcon"})
    public void testEmptyTouchIcon() throws Exception {

        String touchIconHtml = getHtml(
                "<link rel=\"shortcut icon\" type=\"image/png\" href=\""+FAVICON_REL_URL+"\" />",
                "NO Touch Icon  in  current Page"
            );

        String touchIconUrl =  mWebServer.setResponse(
                "/touchicon.html",touchIconHtml,null);

        mTestWebChromeClient.mOnReceivedIconHelper.setIconType(
                    mTestWebChromeClient.mOnReceivedIconHelper.TOUCH_ICON);

        loadUrlSync(mWebView, mTestWebViewClient.mOnFirstVisualPixelHelper, touchIconUrl);
        assertNull(mWebView.getTouchIconUrl());
    }
}
