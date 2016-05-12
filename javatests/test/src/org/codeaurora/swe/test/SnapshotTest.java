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

import android.content.Context;
import android.test.ActivityInstrumentationTestCase2;
import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.AssertionFailedError;
import junit.framework.Assert;

import org.chromium.content.browser.test.util.CallbackHelper;
import org.chromium.base.test.util.Feature;
import org.chromium.net.test.util.TestWebServer;
import org.chromium.content.browser.test.util.DOMUtils;
import org.chromium.content.browser.test.util.HistoryUtils;

import org.codeaurora.swe.WebBackForwardList;
import android.webkit.ValueCallback;
import org.codeaurora.swe.WebView;
import org.codeaurora.swe.WebViewClient;

public class SnapshotTest extends SWETestBase {
    private static final String LOGTAG = "WebViewTest";
    private TestWebServer mWebServer;
    private static final int COLOR_THRESHOLD = 8;
    private static int page_index = 0;

    private String getHTML(String color) {
        return "<html style=\"background: #"+ color
                        + ";\"><head><style>body { height: 5000px; }</style>"
                        + "</head></html>";
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mWebServer = getServer();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        purgeAllSnapshots();
    }

    private static void assertEqualColor(int actual, int expected, int threshold) {
        int deltaR = Math.abs(Color.red(actual) - Color.red(expected));
        int deltaG = Math.abs(Color.green(actual) - Color.green(expected));
        int deltaB = Math.abs(Color.blue(actual) - Color.blue(expected));
        if (deltaR > threshold || deltaG > threshold || deltaB > threshold) {
            throw new AssertionFailedError(
                    "Color does not match; expected " + expected + ", got " + actual);
        }
    }

    private class SnapshotIdsHelper extends CallbackHelper
            implements ValueCallback <List<Integer>> {
        List<Integer> list;
        public void getSnapshotIdsSync(final WebView wv) throws Exception{
            int currentCallCount = getCallCount();
            final SnapshotIdsHelper helper = this;
            getInstrumentation().runOnMainSync(new Runnable() {
                @Override
                public void run() {
                        wv.getSnapshotIds(helper);
                 }
            });
            waitForCallback(currentCallCount, 1, SWETestBase.WAIT_TIMEOUT_MS,
                TimeUnit.MILLISECONDS);
        }

        public List<Integer> getIds() {
            return list;
        }

        @Override
        public void onReceiveValue(List <Integer> value) {
            list = value;
            notifyCalled();
        }
    }

    private class SnapshotHelper extends CallbackHelper implements ValueCallback<Bitmap> {
        Bitmap bitmap;
        AtomicInteger color;
        long startTime = 0;
        String api = "";
        int id;

        public SnapshotHelper() {
            color = new AtomicInteger();
            bitmap = null;
        }

        public Bitmap getBitmap() {
            return bitmap;
        }

        public AtomicInteger getColor() {
            return color;
        }

        public int getId() {
            return id;
        }

        public void getSnapshotSync(final WebView wv,
                final int key, final boolean capture) throws Exception {
            id = key;
            int currentCallCount = getCallCount();
            final SnapshotHelper helper = this;
            getInstrumentation().runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    startTime = java.lang.System.nanoTime();
                    if (!capture) {
                        api = " getSnapshot ";
                        wv.getSnapshot(id, helper);
                    } else {
                        api = " captureSnapshot ";
                        wv.captureSnapshot(id, helper);
                    }
                 }
            });
            waitForCallback(currentCallCount, 1, SWETestBase.WAIT_TIMEOUT_MS,
                TimeUnit.MILLISECONDS);
        }

        @Override
        public void onReceiveValue(Bitmap bm) {
            long endTime = java.lang.System.nanoTime();
            android.util.Log.v("SnapshotTest", api +" Time " + (endTime-startTime)/1e6);
            bitmap = bm;
            if (bitmap != null) {
                color.set(bitmap.getPixel(bitmap.getWidth() / 2, bitmap.getHeight() / 2));
            }
            notifyCalled();
        }
    }

    private WebView singleWebviewTestSetUp(int key, String htmlcolor,
            int color, boolean shouldCapture) throws Exception {
        return singleWebviewTestSetUp(key, true, htmlcolor, color, shouldCapture);
    }

    // Method create a webview and loads the page
    private WebView singleWebviewTestSetUp(int key, boolean shouldDestroy, String htmlcolor,
            int color, boolean shouldCapture) throws Exception {
        WebView normalWV = createWebView(false);
        assertNotNull(normalWV);
        setupWebviewClient(normalWV);
        replaceWebView(normalWV, shouldDestroy);
        // wait for the swap because of surfaceview
        java.lang.Thread.sleep(500);
        createNavigationIndex(key, normalWV, htmlcolor, color, shouldCapture);
        return normalWV;
    }

    // create and load a page in given url
    private void createNavigationIndex(int key, WebView normalWV,
            String htmlcolor, int color, boolean shouldCapture)
            throws Exception {
        page_index++;
        String url = mWebServer.setResponse(
                     "/SnapshotTest_"+ page_index +"_.html", getHTML(htmlcolor), null);
        loadUrlSync(normalWV, mTestWebViewClient.mOnFirstVisualPixelHelper, url);
        // SWE:TODO issue with page finished / first paint ensure we have loaded
        // using sleep. Need to find a better alternative. Look for first frame update
        java.lang.Thread.sleep(500);
        if (shouldCapture) {
            SnapshotHelper helper = new SnapshotHelper();
            // validate for current index
            helper.getSnapshotSync(normalWV, key, true);
            assertNotNull(helper.getBitmap());
            assertEqualColor(helper.getColor().get(), color, COLOR_THRESHOLD);
        }
    }


    @Feature({"SnapshotTest"})
    public void testLiveNetworkGetSnapshot() throws Exception {
        WebView normalWV = createWebView(false);
        assertNotNull(normalWV);
        setupWebviewClient(normalWV);
        replaceWebView(normalWV, true);
        // wait for the swap because of surfaceview
        java.lang.Thread.sleep(500);
        loadUrlAsync(normalWV, "www.google.com");
        SnapshotHelper helper = new SnapshotHelper();
        helper.getSnapshotSync(normalWV, 0, true);
        assertNull(helper.getBitmap());
        // ensure the page loads and snapshot is written to fs
        java.lang.Thread.sleep(5000);
        helper.getSnapshotSync(normalWV, 0, true);
        assertNotNull(helper.getBitmap());
    }

    @Feature({"SnapshotTest"})
    public void testIncognitoGetSnapshot() throws Exception {
        WebView wv = createWebView(true);
        assertNotNull(wv);
        setupWebviewClient(wv);
        replaceWebView(wv, true);
        // wait for the swap because of surfaceview
        java.lang.Thread.sleep(500);
        createNavigationIndex(0, wv, "0f0",Color.GREEN, true);
        SnapshotHelper helper = new SnapshotHelper();
        helper.getSnapshotSync(wv, 0, true);
        /// should not store in memory
        assertNull(helper.getBitmap());
        //ensure the page loads and snapshot is written to fs
        java.lang.Thread.sleep(5000);
        helper = new SnapshotHelper();
        helper.getSnapshotSync(wv, 0, false);
        // incognito should not write to fs
        assertNull(helper.getBitmap());
        // read should be possible
        helper.getSnapshotSync(wv, 0, true);
        assertNotNull(helper.getBitmap());
        assertEqualColor(helper.getColor().get(), Color.GREEN, COLOR_THRESHOLD);
    }

    @Feature({"SnapshotTest"})
    public void testSimpleGetSnapshotAndHasSnapshot() throws Exception {
        WebView normalWV = singleWebviewTestSetUp(0, "f00", Color.RED, true);
        // Check for invalid index
        SnapshotHelper helper = new SnapshotHelper();
        assertFalse(normalWV.hasSnapshot(3));
        helper = new SnapshotHelper();
        helper.getSnapshotSync(normalWV, 3, false);
        assertNull(helper.getBitmap());
        helper = new SnapshotHelper();
        helper.getSnapshotSync(normalWV, -1, false);
        assertNull(helper.getBitmap());
        // hasSnapshot
        assertTrue(normalWV.hasSnapshot(0));
        helper = new SnapshotHelper();
        helper.getSnapshotSync(normalWV, 0, false);
        assertNotNull(helper.getBitmap());
        assertEqualColor(helper.getColor().get(), Color.RED, COLOR_THRESHOLD);
        // There was a issue where null bitmap
        // was returned when we call to force cache consecutively within 1 second
        // force to get from cache which should read the cached bitmap internally
        helper = new SnapshotHelper();
        helper.getSnapshotSync(normalWV, 0, true);
        assertNotNull(helper.getBitmap());
        assertEqualColor(helper.getColor().get(), Color.RED, COLOR_THRESHOLD);
        // wait for the file thread to complete deleting from fs
        // need to implement callback not sure if its worth it yet
        java.lang.Thread.sleep(1000);
        // rewrite on same index
        createNavigationIndex(0, normalWV,"0f0",Color.GREEN, true);
        // wait for the file thread to complete deleting from fs
        // need to implement callback not sure if its worth it yet
        java.lang.Thread.sleep(1000);
        // read back after flush to fs
        helper = new SnapshotHelper();
        helper.getSnapshotSync(normalWV, 0, false);
        assertNotNull(helper.getBitmap());
        assertEqualColor(helper.getColor().get(), Color.GREEN, COLOR_THRESHOLD);
    }

    @Feature({"SnapshotTest"})
    public void testMultipleWebViewNavigationsAndDeleteSnapshot()
            throws Exception, java.lang.Throwable {
        WebView normalWV = singleWebviewTestSetUp(0, "00f", Color.BLUE, true);
        SnapshotHelper helper = new SnapshotHelper();
        // validate for current index
        assertTrue(normalWV.hasSnapshot(0));
        // delete current index
        normalWV.deleteSnapshot(0);
        // wait for the file thread to complete deleting from fs
        // need to implement callback not sure if its worth it yet
        java.lang.Thread.sleep(1000);
        assertFalse(normalWV.hasSnapshot(0));
        // try getting the bitmap
        helper = new SnapshotHelper();
        helper.getSnapshotSync(normalWV, 0, false);
        assertNull(helper.getBitmap());
        // force to recapture
        helper = new SnapshotHelper();
        helper.getSnapshotSync(normalWV, 0, true);
        assertNotNull(helper.getBitmap());
        assertEqualColor(helper.getColor().get(), Color.BLUE, COLOR_THRESHOLD);
        // create another navigations
        createNavigationIndex(1, normalWV,"f00",Color.RED, true);
        createNavigationIndex(2, normalWV,"0f0",Color.GREEN, true);
        assertTrue(normalWV.hasSnapshot(0));
        assertTrue(normalWV.hasSnapshot(1));
        assertTrue(normalWV.hasSnapshot(2));
        // get older captures snapshot
        helper.getSnapshotSync(normalWV, 1, false);
        assertNotNull(helper.getBitmap());
        assertEqualColor(helper.getColor().get(), Color.RED, COLOR_THRESHOLD);
        // delete one of the navigations
        normalWV.deleteSnapshot(1);
        // wait for the file thread to complete deleting from fs
        // need to implement callback not sure if its worth it yet
        java.lang.Thread.sleep(1000);
        assertFalse(normalWV.hasSnapshot(1));
        helper = new SnapshotHelper();
        helper.getSnapshotSync(normalWV, 1, false);
        assertNull(helper.getBitmap());
        // go back and getBitmap
        HistoryUtils.goBackSync(getInstrumentation(),
            normalWV.getContentViewCore().getWebContents(),
            mTestWebViewClient.mOnFirstVisualPixelHelper);
        helper.getSnapshotSync(normalWV, 1, true);
        assertNotNull(helper.getBitmap());
        assertEqualColor(helper.getColor().get(), Color.RED, COLOR_THRESHOLD);

        // delete incorrect index
        assertFalse(normalWV.hasSnapshot(4));
        normalWV.deleteSnapshot(4);

        WebView wv1 = singleWebviewTestSetUp(10, false, "fff", Color.WHITE, true);
        helper = new SnapshotHelper();
        assertTrue(wv1.hasSnapshot(10));
        helper.getSnapshotSync(wv1, 10, false);
        assertNotNull(helper.getBitmap());
        assertEqualColor(helper.getColor().get(), Color.WHITE, COLOR_THRESHOLD);

        // test delete all the snapshots for a tab
        normalWV.deleteSnapshot(0);
        normalWV.deleteSnapshot(1);
        normalWV.deleteSnapshot(2);

        // wait for the file thread to complete deleting from fs
        // need to implement callback not sure if its worth it yet
        java.lang.Thread.sleep(1000);
        assertFalse(normalWV.hasSnapshot(0));
        assertFalse(normalWV.hasSnapshot(1));
        assertFalse(normalWV.hasSnapshot(2));
        helper = new SnapshotHelper();
        helper.getSnapshotSync(normalWV, 1, false);
        assertNull(helper.getBitmap());

        // check other WV has the snapshot
        assertTrue(wv1.hasSnapshot(10));
        helper.getSnapshotSync(wv1, 10, false);
        assertNotNull(helper.getBitmap());
        assertEqualColor(helper.getColor().get(), Color.WHITE, COLOR_THRESHOLD);

        // check if all are deleted for all the webview's
        purgeAllSnapshots();
        // wait for the file thread to complete deleting from fs
        // need to implement callback not sure if its worth it yet
        java.lang.Thread.sleep(1000);
        helper = new SnapshotHelper();
        assertFalse(wv1.hasSnapshot(10));
        helper.getSnapshotSync(wv1, 10, false);
        assertNull(helper.getBitmap());
    }

    private List toList(int[] array) {
        List l = new ArrayList<Integer>();
        for(int value : array) {
            l.add(value);
        }
        return l;
    }

    @Feature({"SnapshotTest"})
    public void testGetIds() throws Exception, java.lang.Throwable {
        WebView normalWV = singleWebviewTestSetUp(0, "00f", Color.BLUE, true);
        SnapshotIdsHelper helper = new SnapshotIdsHelper();
        // create other navigations
        createNavigationIndex(1, normalWV,"f00",Color.RED, true);
        // at index 2
        createNavigationIndex(2, normalWV,"0f0",Color.GREEN, true);
        // get the available id's
        helper.getSnapshotIdsSync(normalWV);
        List ids = helper.getIds();
        assertTrue(ids.size() == 3);
        assertTrue(ids.contains(0));
        assertTrue(ids.contains(1));
        assertTrue(ids.contains(2));
        assertFalse(ids.contains(10));

        // delete a snapshot
        normalWV.deleteSnapshot(1);
        // get the available id's
        helper = new SnapshotIdsHelper();
        helper.getSnapshotIdsSync(normalWV);
        ids = helper.getIds();
        assertTrue(ids.size() == 2);

        WebView wv1 = singleWebviewTestSetUp(10, false, "fff", Color.WHITE, true);
        WebView wv2 = singleWebviewTestSetUp(20, false, "fff", Color.WHITE, true);
        WebView wv3 = singleWebviewTestSetUp(30, false, "fff", Color.WHITE, true);
        // wait for all the writes
        java.lang.Thread.sleep(1000);
        helper = new SnapshotIdsHelper();
        helper.getSnapshotIdsSync(normalWV);
        ids = helper.getIds();
        assertTrue(ids.size() == 5);
    }

    @Feature({"SnapshotTest"})
    public void testGetSnapshotNullCallback() throws Exception {
        final WebView wv = singleWebviewTestSetUp(0, "f00", Color.RED, false);
        getInstrumentation().runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    wv.captureSnapshot(0, null);
                 }
        });
        // null callback so wait for 1 second
        java.lang.Thread.sleep(1000);
        assertTrue(wv.hasSnapshot(0));
        // null callback for getBitmap
        getInstrumentation().runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    wv.getSnapshot(0, null);
                }
        });
        // null callback so wait for 1 second
        java.lang.Thread.sleep(1000);
        // get the bitmap
        SnapshotHelper helper = new SnapshotHelper();
        helper.getSnapshotSync(wv, 0, false);
        assertNotNull(helper.getBitmap());
        assertEqualColor(helper.getColor().get(), Color.RED, COLOR_THRESHOLD);
    }

    @Feature({"SnapshotTest"})
    public void testSnapshotWebViewIsPaused() throws Exception {
        WebView wv1 = singleWebviewTestSetUp(0, false, "fff", Color.WHITE, true);
        SnapshotHelper helper = new SnapshotHelper();
        assertTrue(wv1.hasSnapshot(0));
        helper.getSnapshotSync(wv1, 0, false);
        assertNotNull(helper.getBitmap());
        assertEqualColor(helper.getColor().get(), Color.WHITE, COLOR_THRESHOLD);
        // delete
        wv1.deleteSnapshot(0);
        // wait for the file thread to complete deleting from fs
        // need to implement callback not sure if its worth it yet
        java.lang.Thread.sleep(1000);
        assertFalse(wv1.hasSnapshot(0));
        pauseWebView(wv1);
        WebView wv2 = singleWebviewTestSetUp(1, false, "f00", Color.RED, true);
        helper.getSnapshotSync(wv1, 0, true);
        assertNull(helper.getBitmap());
        // try reading
        helper.getSnapshotSync(wv1, 0, false);
        assertNull(helper.getBitmap());
    }

    private void writeToSD(int id, Bitmap bitmap) {
        java.io.FileOutputStream out = null;
        try {
            out = new java.io.FileOutputStream("/sdcard/temp_tabs_"+ id +".png");
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (Exception e) {
            }
        }
    }

}
