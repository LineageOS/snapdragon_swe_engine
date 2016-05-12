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
 * Portions of this file derived from Chromium code, which is BSD licensed, copyright The Chromium Authors.
 */
package org.codeaurora.swe.test;

import android.app.Activity;
import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.util.Pair;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.chromium.base.test.util.Feature;
import org.chromium.net.test.util.TestWebServer;
import org.chromium.content.browser.test.util.DOMUtils;

import org.codeaurora.swe.WebRefiner;
import org.codeaurora.swe.WebView;
import org.codeaurora.swe.WebViewClient;

public class WebRefinerTest extends SWETestBase {
    private static final String LOGTAG = "WebRefinerTest";

    //private static final String CONFIG_DIR = "web_refiner";
    //private final File mDataDir;

    public WebRefinerTest() {
        super();
    }

    @Feature({"WebRefiner"})
    public void testInitialization() throws Exception {
        WebRefiner wbr = WebRefiner.getInstance();
        assertNotNull(wbr);
        assertTrue(WebRefiner.isInitialized());
        assertEquals(wbr.getInitializationStatus(), WebRefiner.STATUS_OK);
    }

    private static final String TEST_PAGE_CONTENT = "<html>\n" +
            "<head>\n" +
            "    <script src=\"http://localhost/ad_script01.js\" type=\"text/javascript\"></script>\n" +
            "    <link rel=\"stylesheet\" type=\"text/css\" href=\"http://localhost/ad_style01.css\">\n" +
            "</head>\n" +
            "<body>\n" +
            "    <p>WebRefiner test page.</p>\n" +
            "    <iframe src=\"http://localhost/ad_frame01.html\"></iframe>\n" +
            "    <iframe src=\"http://localhost/ad_frame02.html\"></iframe>\n" +
            "    <script src=\"http://localhost/ad_script02.js\" type=\"text/javascript\"></script>\n" +
            "    <link rel=\"stylesheet\" type=\"text/css\" href=\"http://localhost/ad_style02.css\">\n" +
            "    <img src=\"http://localhost/ad_img01.jpg\">\n" +
            "    <img src=\"http://localhost/ad_img02.png\">\n" +
            "</body>";

    private static final String RULE_SET_DATA = "ad_frame\n" +
            "ad_img\n" +
            "ad_style\n" +
            "ad_script\n";

    private boolean writeToFile(String data, String fileName) {

        boolean result = true;
        try {
            File file = new File(mActivity.getApplicationInfo().dataDir, fileName);
            OutputStream os = null;
            try {
                os = new FileOutputStream(file);
                os.write(data.getBytes());
                os.flush();
            } finally {
                if (os != null) {
                    os.close();
                }
            }
        } catch (Exception e) {
            Log.e(LOGTAG, e.getMessage());
            result = false;
        }

        return result;
    }

    @Feature({"WebRefiner"})
    public void testRuleSet() throws Exception {

        TestWebServer webServer = getServer();

        WebRefiner wbr = WebRefiner.getInstance();
        assertNotNull(wbr);

        String ruleSetFileName = "rule_set_01.rules";
        assertTrue(writeToFile(RULE_SET_DATA, ruleSetFileName));

        WebRefiner.RuleSet rs = new WebRefiner.RuleSet(new File(mActivity.getApplicationInfo().dataDir, ruleSetFileName).getAbsolutePath(), WebRefiner.RuleSet.CATEGORY_ADS, 1);
        wbr.addRuleSet(rs);

        WebView wv = createWebView(false);
        setupWebviewClient(wv);

        String url = webServer.setResponse("/webrefiner_test.html", TEST_PAGE_CONTENT , null);

        loadUrlSync(wv, mTestWebViewClient.mOnPageFinishedHelper, url);

        assertEquals(9, wbr.getTotalURLCount(wv));
        assertEquals(8, wbr.getBlockedURLCount(wv));

        WebRefiner.PageInfo pageInfo = wbr.getPageInfo(wv);
        assertNotNull(pageInfo);
        assertEquals(9, pageInfo.mTotalUrls);
        assertEquals(8, pageInfo.mBlockedUrls);
        assertEquals(0, pageInfo.mWhiteListedUrls);

        int ads = 0;
        int trackers = 0;
        int malwares = 0;
        int images = 0;
        int scripts = 0;
        int stylesheets = 0;
        int subframes = 0;
        int whitelisted = 0;
        int blocked = 0;

        for (WebRefiner.MatchedURLInfo urlInfo : pageInfo.mMatchedURLInfoList) {
            if (urlInfo.mActionTaken == WebRefiner.MatchedURLInfo.ACTION_BLOCKED) {
                blocked++;
                switch (urlInfo.mMatchedFilterCategory) {
                    case WebRefiner.RuleSet.CATEGORY_ADS:
                        ads++;
                        break;
                    case WebRefiner.RuleSet.CATEGORY_TRACKERS:
                        trackers++;
                        break;
                    case WebRefiner.RuleSet.CATEGORY_MALWARE_DOMAINS:
                        malwares++;
                        break;
                }
                if (0 == urlInfo.mType.compareTo("Image")) {
                    images++;
                } else if (0 == urlInfo.mType.compareTo("Script")) {
                    scripts++;
                } else if (0 == urlInfo.mType.compareTo("Stylesheet")) {
                    stylesheets++;
                } else if (0 == urlInfo.mType.compareTo("SubFrame")) {
                    subframes++;
                }
            } else if (urlInfo.mActionTaken == WebRefiner.MatchedURLInfo.ACTION_WHITELISTED) {
                whitelisted++;
            }
        }

        assertEquals(8, ads);
        assertEquals(0, trackers);
        assertEquals(0, malwares);
        assertEquals(2, images);
        assertEquals(2, scripts);
        assertEquals(2, stylesheets);
        assertEquals(2, subframes);
        assertEquals(0, whitelisted);
        assertEquals(8, blocked);

        destroyWebView(wv);
    }
}
