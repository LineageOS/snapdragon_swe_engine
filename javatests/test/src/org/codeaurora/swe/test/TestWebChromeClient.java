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

import android.graphics.Bitmap;

import org.chromium.content.browser.test.util.CallbackHelper;
import org.chromium.content.browser.test.util.Criteria;

import org.codeaurora.swe.WebView;
import org.codeaurora.swe.WebChromeClient;

import java.util.concurrent.TimeoutException;

public class TestWebChromeClient extends WebChromeClient {

    public static class OnReceivedIconHelper extends CallbackHelper {
        public static final int TOUCH_ICON = 0;
        public static final int FAV_ICON = 1;

        String mIconUrl;
        boolean mPrecomposed;
        Bitmap mFavIcon;
        int mIconType;

        public OnReceivedIconHelper() {
            mIconUrl = null;
            mPrecomposed = false;
            mFavIcon = null;
        }

        public void setIconType(int iconType) {
            mIconType = iconType;
        }

        public String getIconUrl() {
            assert getCallCount() > 0;
            return mIconUrl;
        }

        public boolean isPrecomposed() {
            return mPrecomposed;
        }

        public Bitmap getFavIcon() {
            assert getCallCount() > 0;
            return mFavIcon;
        }

        public void notifyCalled(String iconUrl, boolean precomposed) {
            mIconUrl = iconUrl;
            mPrecomposed = precomposed;
            if(mIconType == TOUCH_ICON)
                notifyCalled();
        }

        public void notifyCalled(WebView wv, Bitmap icon) {
            mFavIcon = icon;
            if(mIconType == FAV_ICON)
                notifyCalled();
        }
    }

    protected final OnReceivedIconHelper mOnReceivedIconHelper;

    public TestWebChromeClient() {
        mOnReceivedIconHelper = new OnReceivedIconHelper();
    }

    @Override
    public void onReceivedTouchIconUrl (WebView view, String url, boolean precomposed) {
        mOnReceivedIconHelper.notifyCalled(url, precomposed);
    }

    @Override
    public void onReceivedIcon(WebView view, Bitmap icon) {
        mOnReceivedIconHelper.notifyCalled(view, icon);
    }

}
