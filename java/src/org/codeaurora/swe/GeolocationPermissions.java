/*
 * Copyright (c) 2013 The Linux Foundation. All rights reserved.
 * Not a contribution.
 *
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codeaurora.swe;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import android.content.SharedPreferences;
import android.webkit.ValueCallback;

import org.chromium.chrome.browser.UrlUtilities;
import org.chromium.base.ThreadUtils;
import org.json.JSONArray;
import org.json.JSONException;

import org.chromium.chrome.browser.preferences.website.WebsitePreferenceBridge;
import org.chromium.chrome.browser.preferences.website.GeolocationInfo;
import org.chromium.chrome.browser.preferences.website.ContentSetting;

/**
 * This class is used to manage permissions for the WebView's Geolocation
 * JavaScript API.
 *
 * Geolocation permissions are applied to an origin, which consists of the
 * host, scheme and port of a URI. In order for web content to use the
 * Geolocation API, permission must be granted for that content's origin.
 *
 * This class stores Geolocation permissions. An origin's permission state can
 * be either allowed or denied and is associated with an expiration time. This
 * class uses Strings to represent an origin.
 *
 * When an origin attempts to use the Geolocation API, but no permission state
 * is currently set for that origin,
 * WebChromeClient#onGeolocationPermissionsShowPrompt(String,GeolocationPermissions.Callback)
 * is called. This allows the permission state to be set for that origin.
 *
 * The methods of this class can be used to modify and interrogate the stored
 * Geolocation permissions at any time.
 *
 * Within WebKit, Geolocation permissions may be applied either temporarily
 * (for the duration of the page) or permanently. This class deals only with
 * permanent permissions.
*/
public final class GeolocationPermissions {

    private static GeolocationPermissions sGeolocationPermissions;
    private static GeolocationPermissions sIncognitoGeolocationPermissions;
    private static SharedPreferences sSharedPreferences;

    private static final String PREF_PREFIX = "SweGeolocationPermissions%";

    private boolean mPrivateBrowsing;

    //In Sync with ContentSetting.java
    public enum GeolocationContentSetting {
        ALLOW(ContentSetting.ALLOW),
        BLOCK(ContentSetting.BLOCK),
        ASK(ContentSetting.ASK),  // Only used for default values
        SESSION_ONLY(ContentSetting.SESSION_ONLY),
        ALLOW_24H(ContentSetting.ALLOW_24H); //

        private final int mValue;
        private GeolocationContentSetting(int value) {
            this.mValue = value;
        }

        private GeolocationContentSetting(ContentSetting value) {
            this.mValue = ContentSetting.toInt(value);
        }

        protected static GeolocationContentSetting fromInt(int i) {
            for (GeolocationContentSetting enumValue : GeolocationContentSetting.values()) {
                if (enumValue.toInt() == i) return enumValue;
            }
            return null;
        }

        protected int toInt() {
            return mValue;
        }

        protected ContentSetting toContentSetting() {
            return ContentSetting.fromInt(mValue);
        }
    }

    /**
     *  The following constant indicates that the Geolocation permission should
     *  never expire unless it is explicitly cleared.
     */
    public static final long DO_NOT_EXPIRE = -1;

    public static final long ALWAYS_ASK = 0;

    public static GeolocationPermissions getInstance() {
        if (sGeolocationPermissions == null) {
            sGeolocationPermissions = new GeolocationPermissions(false);
        }
        return sGeolocationPermissions;
    }

    public static GeolocationPermissions getInstance(SharedPreferences sharedPreferences) {
        sSharedPreferences = sharedPreferences;
        return getInstance();
    }

    public static GeolocationPermissions getIncognitoInstance() {
        if (sIncognitoGeolocationPermissions == null) {
            sIncognitoGeolocationPermissions = new GeolocationPermissions(true);
        }
        return sIncognitoGeolocationPermissions;
    }

    public static GeolocationPermissions getIncognitoInstance(SharedPreferences sharedPreferences) {
        sSharedPreferences = sharedPreferences;
        return getIncognitoInstance();
    }

    public static boolean isIncognitoCreated() {
        return sIncognitoGeolocationPermissions == null ? false : true;
    }

    /**
     * Constructor parses the specified sharedPreferences and loads the
     * geolocation policy in memory.
     */
    private GeolocationPermissions(boolean privateBrowsing) {
        mPrivateBrowsing = privateBrowsing;
    }

    public void allow(String origin, long expires) {
        GeolocationInfo info = new GeolocationInfo(origin, null);
        if(expires <= ALWAYS_ASK) {
            info.setContentSetting(ContentSetting.SESSION_ONLY);
        } else {
            info.setContentSetting(ContentSetting.ALLOW_24H);
        }
    }

    public void allow(String origin) {
        GeolocationInfo info = new GeolocationInfo(origin, null);
        info.setContentSetting(ContentSetting.ALLOW);
    }

    public void deny(String origin) {
        GeolocationInfo info = new GeolocationInfo(origin, null);
        info.setContentSetting(ContentSetting.BLOCK);
    }

    public long getGetLastUsage(String origin) {
        GeolocationInfo info = new GeolocationInfo(origin, null);
        return info.getGetLastUsage();
    }

    public GeolocationContentSetting getContentSetting(String origin) {
        GeolocationInfo info = new GeolocationInfo(origin, null);
        return GeolocationContentSetting.fromInt(info.getContentSetting().toInt());
    }

    public void setContentSetting(String origin, GeolocationContentSetting setting) {
        GeolocationInfo info = new GeolocationInfo(origin, null);
        info.setContentSetting(setting.toContentSetting());
    }

    public void clear(String origin) {
        for (Iterator<GeolocationInfo> iterator =
                WebsitePreferenceBridge.getGeolocationInfo().iterator();
                iterator.hasNext();) {
            GeolocationInfo info = iterator.next();
            if (info.getOrigin().equals(origin)) {
                info.setContentSetting(ContentSetting.ASK);
            }
        }
    }

    public void clearAll() {
        for (GeolocationInfo info : WebsitePreferenceBridge.getGeolocationInfo()) {
            //SWETODO: Change to ContentSetting.DEFAULT once new changes land
            info.setContentSetting(ContentSetting.ASK);
        }
    }

    /**
     * Method to get if an origin is set to be allowed.
     */
    public boolean isOriginAllowed(String origin) {

        GeolocationInfo selectedInfo = null;
        // get the gelocationinfo which matches the passed origin
        for (GeolocationInfo info : WebsitePreferenceBridge.getGeolocationInfo()){
            if(info.getOrigin().equals(origin)) {
                selectedInfo = info;
            }
        }
        if (selectedInfo != null) {
            if (selectedInfo.getContentSetting() == ContentSetting.ALLOW)
                return true;
        }
        return false;
    }

    /**
     * Method to get if there is a persistent policy for the specified origin
     * that has not yet expired.
     */
    public boolean hasOrigin(String origin) {
        String originUrl = getOriginFromUrl(origin);
        // Sanitize origin in case it is a URL

        GeolocationInfo selectedInfo = null;
        for (GeolocationInfo info : WebsitePreferenceBridge.getGeolocationInfo()){
            if(info.getOrigin().equals(originUrl)) {
                selectedInfo = info;
            }
        }

        if (selectedInfo != null) {
                return true;
        }
        return false;
    }

    /**
     * Asynchronous method to get if there is a persistent policy for the
     * specified origin that has not yet expired.
     */
    public void hasOrigin(String origin, final ValueCallback<Boolean> callback) {
        final boolean finalHasOrigin = hasOrigin(origin);
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                callback.onReceiveValue(finalHasOrigin);
            }
        });
    }

    /**
     * Asynchronous method to get if an origin is to be allowed.
     */
    public void getAllowed(String origin, final ValueCallback<Boolean> callback) {
        final boolean finalAllowed = isOriginAllowed(origin);
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                callback.onReceiveValue(finalAllowed);
            }
        });
    }

    /**
     * Asynchronous method to get the domains currently allowed or denied.
     */
    public void getOrigins(final ValueCallback<Set<String>> callback) {
        final Set<String> origins = new HashSet<String>();
        // fetch the approved origins from geolocation info
        for (GeolocationInfo info : WebsitePreferenceBridge.getGeolocationInfo()){
          origins.add(info.getOrigin());
        }
        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                callback.onReceiveValue(origins);
            }
        });
    }

    /**
     * Asynchronous method to get the expiration time of the policy associated
     * with the specified origin.
     */
    public void getExpirationTime(String origin, final ValueCallback<Long> callback) {
        // Sanitize origin in case it is a URL
        String key = getOriginFromUrl(origin);

        final long finalExpirationTime = getGetLastUsage(key);

        ThreadUtils.postOnUiThread(new Runnable() {
            @Override
            public void run() {
                callback.onReceiveValue(finalExpirationTime);
            }
        });
    }

    public static void onIncognitoTabsRemoved() {
        if (sIncognitoGeolocationPermissions != null) {
            sIncognitoGeolocationPermissions.clearAll();
            sIncognitoGeolocationPermissions = null;
        }
    }
    /**
     * Get the origin of a URL using the GURL library.
     */
    public static String getOriginFromUrl(String url) {
        String origin = "";
        try {
          origin = UrlUtilities.getOriginForDisplay(new URI(url), true);
          if (origin.isEmpty()) {
              return null;
          }
        } catch(Exception e){
        } finally {
          return origin;
        }
    }
}

