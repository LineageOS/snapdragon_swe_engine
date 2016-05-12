/*
 *  Copyright (c) 2013, The Linux Foundation. All rights reserved.
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

import android.net.ParseException;
import android.text.TextUtils;

import org.chromium.base.JNINamespace;
import org.chromium.base.ThreadUtils;

import java.util.concurrent.Callable;

// SWE-feature-Clear-Browser-Data
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
// SWE-feature-Clear-Browser-Data
import org.chromium.chrome.browser.preferences.website.ContentSetting;
import org.chromium.chrome.browser.preferences.website.CookieInfo;
import org.chromium.chrome.browser.preferences.website.WebsitePreferenceBridge;

public final class CookieManager {

    private static CookieManager sCookieManager;

    private CookieManager() {
    }

    public synchronized boolean acceptCookie() {
        return PrefServiceBridge.getInstance().isAcceptCookiesEnabled();
    }

    public String getCookie(final String url) {
        if (TextUtils.isEmpty(url))
            return null;

        String cookie = PrefServiceBridge.getInstance().getCookie(url);
        return cookie == null || cookie.trim().isEmpty() ? null : cookie;
    }

    public String getCookie(final String url, final boolean privateBrowsing) {
        if (TextUtils.isEmpty(url))
            return null;

        String cookie = privateBrowsing ?
                            PrefServiceBridge.getInstance().getIncognitoCookie(url) :
                            getCookie(url);

        return cookie;
    }

    public static CookieManager getInstance() {
        if (sCookieManager == null) {
            sCookieManager = new CookieManager();
        }
        return sCookieManager;
    }

    public void removeAllCookie() {
        // SWE-feature-Clear-Browser-Data
        PrefServiceBridge.getInstance().clearBrowsingData(null,
                                                          false,
                                                          false,
                                                          true,
                                                          false,
                                                          false);
        // SWE-feature-Clear-Browser-Data
    }

    public void setAcceptThirdPartyCookies(boolean accept) {
        PrefServiceBridge.getInstance().setBlockThirdPartyCookiesEnabled(!accept);
    }

    public boolean getAcceptThirdPartyCookies() {
        return !PrefServiceBridge.getInstance().isBlockThirdPartyCookiesEnabled();
    }

    public void removeSessionCookie() {
        PrefServiceBridge.getInstance().clearSessionCookies();
    }

    public void setAcceptCookie(boolean accept) {
        PrefServiceBridge.getInstance().setAllowCookiesEnabled(accept);
    }

    public void flushCookieStore() {
        PrefServiceBridge.getInstance().flushCookieStore();
    }

    public boolean allowFileSchemeCookies() {
        return false;
    }

    public void removeExpiredCookie() {
        //SWE:TODO: remove API deprecated since API 21 and not used
        // in browser
    }

    public void setAcceptFileSchemeCookies(boolean accept) {
        //SWE:TODO:Remove calls from browser and remove this method !! Not used
    }

    public void setCookie(final String url, final String value) {
        //SWE:TODO:Remove calls from browser and remove this method !! Not used
    }

    public boolean hasCookies() {
        // SWE:TODO: Remove calls from browser and remove this method !! Not used
        return false;
    }

    public boolean hasCookies(boolean privateBrowsing) {
        // SWE:TODO: Remove calls from browser and remove this method !! Not used
        return false;
    }
}
