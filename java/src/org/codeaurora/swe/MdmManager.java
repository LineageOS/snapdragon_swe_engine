/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution.
 * * Neither the name of The Linux Foundation nor the names of its
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.codeaurora.swe;

import android.content.Context;

import org.chromium.chrome.browser.mdm.URLFilterRestrictionJNI;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.net.ProxyChangeListener;

/**
 * MDM (browser) to Engine Interface API
 *
 * We want to have a controlled interface between the browser code and the engine.
 * For MDM, we will use this static class to proxy needed engine calls.
 */
public final class MdmManager {

    public static void updateMdmThirdPartyCookies(boolean value) {
        PrefServiceBridge psb = PrefServiceBridge.getInstance();
        if(psb != null) {
            psb.setBlockThirdPartyCookiesEnabled(value);
        }
    }

    public static void updateMdmUrlFilters(String urlBlacklist, String urlWhitelist) {
        URLFilterRestrictionJNI.getInstance().SetMdmUrlFilters(urlBlacklist, urlWhitelist);
    }

    public static void isMdmUrlBlocked(String url) {
        URLFilterRestrictionJNI.getInstance().isBlocked(url);
    }

    public static boolean isMdmUrlBlockedResultReady() {
        return URLFilterRestrictionJNI.getInstance().isBlockedResultReady();
    }

    public static boolean getMdmUrlBlockedResult() {
        return URLFilterRestrictionJNI.getInstance().getBlockedResult();
    }

    public static String getProxyProperty(String key) {
        return ProxyChangeListener.getProperty(key);
    }

    public static String getNativeProxyProperty(Context c, String key) {
        return ProxyChangeListener.create(c).fetchProxyPropertyFromNative(key);
    }

    public static String getMdmProxyMode() {
        return ProxyChangeListener.getMdmProxyMode();
    }

    public static boolean isMdmProxyCfgValid() {
        return ProxyChangeListener.getMdmProxyConfig() != null;
    }
}
