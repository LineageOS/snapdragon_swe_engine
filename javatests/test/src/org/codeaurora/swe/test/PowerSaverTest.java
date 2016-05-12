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
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.test.ActivityInstrumentationTestCase2;

import java.lang.reflect.Method;
import java.lang.Boolean;

import org.chromium.content.browser.ContentViewCore;

import junit.framework.Assert;

import org.chromium.base.test.util.Feature;
import org.chromium.net.test.util.TestWebServer;
import org.codeaurora.swe.WebView;

public class PowerSaverTest extends SWETestBase {
    private static final String LOGTAG = "PowerSaverTest";
    private static final String SWE_WEBVIEW_HTML = "<html><body> SWE WebView </body></html>";

    @Feature({"SWEPowerSaver"})
    public void testEnablePerflock() throws Exception {
        TestWebServer webServer = getServer();
        WebView wv = createWebView(false);
        setupWebviewClient(wv);
        ContentViewCore cvc = wv.getContentViewCore();
        Context context = wv.getContext();
        boolean isEnabled;
        Object isEnabledObject;

        // use reflection
        Class[] argTypes = new Class[] {};
        Object[] args = {};
        Method m = cvc.getClass().getDeclaredMethod("getPerfLockEnabled", argTypes);
        m.setAccessible(true);

        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mPrefs.edit().putBoolean("powersave_enabled", true).apply();
        isEnabledObject = m.invoke(cvc, args);
        isEnabled = mPrefs.getBoolean("powersave_enabled", false);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        assertTrue(Boolean.parseBoolean(isEnabledObject.toString()));
        assertTrue(isEnabled);

        mPrefs.edit().putBoolean("powersave_enabled", false).apply();
        isEnabled = mPrefs.getBoolean("powersave_enabled", false);
        isEnabledObject = m.invoke(cvc, args);
        assertFalse(Boolean.parseBoolean(isEnabledObject.toString()));
        assertFalse(isEnabled);

    }
}
