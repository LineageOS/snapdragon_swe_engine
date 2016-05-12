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
package org.codeaurora.swe;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.webkit.ValueCallback;
import android.util.Log;

import org.chromium.base.ThreadUtils;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.chrome.browser.favicon.FaviconHelper;
import org.chromium.chrome.browser.favicon.FaviconHelper.FaviconImageCallback;
import org.chromium.chrome.browser.preferences.LocationSettings;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.preferences.website.ContentSetting;
import org.chromium.chrome.browser.preferences.website.CookieInfo;
import org.chromium.chrome.browser.preferences.website.GeolocationInfo;
import org.chromium.chrome.browser.preferences.website.PopupExceptionInfo;
import org.chromium.chrome.browser.preferences.website.PushNotificationInfo;
import org.chromium.chrome.browser.preferences.website.SingleWebsitePreferences;
import org.chromium.chrome.browser.preferences.website.Website;
import org.chromium.chrome.browser.preferences.website.WebsiteAddress;
import org.chromium.chrome.browser.preferences.website.WebsitePermissionsFetcher;
import org.chromium.chrome.browser.preferences.website.WebsitePreferenceBridge;
import org.chromium.chrome.browser.preferences.website.VoiceAndVideoCaptureInfo;
import org.chromium.chrome.browser.preferences.website.WebRefinerInfo;
import org.chromium.chrome.browser.UrlUtilities;

import org.codeaurora.swe.WebRefiner;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Date;
import java.util.concurrent.TimeUnit;


public final class PermissionsServiceFactory {
    private static final int FAVICON_DP = 16;
    private static final int TOTAL_HOURS_IN_SECONDS = 24*60*60;

    public static void getPermissionsService(ValueCallback<PermissionsService> callback) {
        WebsitePermissionsFetcher fetcher = new WebsitePermissionsFetcher(
                                                new OriginSitesPopulator(callback));
        fetcher.fetchAllPreferences();
    }

    private static class OriginSitesPopulator implements
            WebsitePermissionsFetcher.WebsitePermissionsCallback {

        private List<Set<Website>> mAllWebsites = null;
        private Set<String> mOriginsSet = null;
        private ValueCallback<PermissionsService> mCallback = null;

        private OriginSitesPopulator(ValueCallback<PermissionsService> callback){
            mCallback = callback;
        }

        @Override
        public void onWebsitePermissionsAvailable(
            Map<String, Set<Website>> sitesByOrigin, Map<String, Set<Website>> sitesByHost) {

            mAllWebsites = new ArrayList<>();

            // maintain all the sites to be used for combining all the permission info
            mAllWebsites.addAll(sitesByOrigin.values());
            mAllWebsites.addAll(sitesByHost.values());

            final Set<String> origins = new HashSet<String>();

            for (Map.Entry<String, Set<Website>> element : sitesByOrigin.entrySet()) {
                for (Website site : element.getValue()) {
                    String currentOrigin = site.getAddress().getOrigin();
                    if (!TextUtils.isEmpty(currentOrigin)) {
                        origins.add(currentOrigin);
                    }
                }
            }

            // Next we add sites that are only accessible by host name.
            for (Map.Entry<String, Set<Website>> element : sitesByHost.entrySet()) {
                for (Website site : element.getValue()) {
                    // check if origin is available
                    String siteName = site.getAddress().getOrigin();
                    /* If origin is not available fallback to host name
                    * this would happen in cases when sie only contains WebStorage
                    * which stores by "host name" rather than origin
                    */
                    if (TextUtils.isEmpty(siteName))
                        siteName = site.getAddress().getHost();
                    if (!TextUtils.isEmpty(siteName) && !origins.contains(siteName)) {
                        origins.add(siteName);
                    }
                }
            }

            mOriginsSet = origins;
            if (mCallback != null) {
                final PermissionsService ps =
                  new PermissionsService(mOriginsSet, mAllWebsites);

                ThreadUtils.postOnUiThread(new Runnable() {
                  @Override
                  public void run() {
                    mCallback.onReceiveValue(ps);
                  }
                });
            }
        }
    } // end of Origins Populator class


    public enum PermissionType {
        COOKIE("cookie"),
        GEOLOCATION("geolocation"),
        POPUP("popup"),
        THIRDPARTYCOOKIES("thirdpartycookies"),
        VOICE("voice"),
        VIDEO("video"),
        WEBREFINER("webrefiner");

        private String mValue;

        PermissionType(String value) {
            this.mValue = value;
        }

        private String getValue() {
            return this.mValue;
        }
    } // end of PermissionType Enum

    public static class Permission {

        public static int ALLOW = ContentSetting.toInt(ContentSetting.ALLOW);
        public static int  BLOCK = ContentSetting.toInt(ContentSetting.BLOCK);
        public static int ASK = ContentSetting.toInt(ContentSetting.ASK);
        public static int CUSTOM = ContentSetting.toInt(ContentSetting.ALLOW_24H);
        public static int NOTSET = -1;

        private static int fromContentSetting(ContentSetting value) {
            if (value == null) return Permission.NOTSET;
            switch(value) {
                case ALLOW:
                    return Permission.ALLOW;
                case BLOCK:
                    return Permission.BLOCK;
                case ASK:
                    return Permission.ASK;
                default:
                    return Permission.CUSTOM;
            }
        }
    }

    // Setter for overall Permission Level
    public static void setDefaultPermissions(PermissionType type, boolean allow) {
        if (type == null) return;

        switch(type) {
            case COOKIE:
                PrefServiceBridge.getInstance().setAllowCookiesEnabled(allow);
                break;
            case GEOLOCATION:
                PrefServiceBridge.getInstance().setAllowLocationEnabled(allow);
                break;
            case POPUP:
                PrefServiceBridge.getInstance().setAllowPopupsEnabled(allow);
                break;
            case THIRDPARTYCOOKIES:
                PrefServiceBridge.getInstance().setBlockThirdPartyCookiesEnabled(!allow);
                break;
            case VOICE:
            case VIDEO:
                PrefServiceBridge.getInstance().setCameraMicEnabled(allow);
                break;
            case WEBREFINER:
                if (WebRefiner.isInitialized()) {
                    PrefServiceBridge.getInstance().setAllowWebRefinerEnabled(allow);
                    WebRefiner.getInstance().setDefaultPermission(allow);
                }
                break;
            default:
                break;
        }
    }

    // Getter for overall Permission Level
    public static boolean getDefaultPermissions(PermissionType type) {
        if (type == null) return false;

        switch(type) {
            case COOKIE:
                return PrefServiceBridge.getInstance().isAcceptCookiesEnabled();
            case GEOLOCATION:
                return PrefServiceBridge.getInstance().isAllowLocationEnabled();
            case POPUP:
                return PrefServiceBridge.getInstance().popupsEnabled();
            case THIRDPARTYCOOKIES:
                return !PrefServiceBridge.getInstance().isBlockThirdPartyCookiesEnabled();
            case VOICE:
            case VIDEO:
                return PrefServiceBridge.getInstance().isCameraMicEnabled();
            case WEBREFINER:
                return PrefServiceBridge.getInstance().webrefinerEnabled();
            default:
                return false;
        }
    }

    public static void resetDefaultPermissions() {
        PrefServiceBridge.getInstance().resetToDefaultPermissions();
        resetSiteSettings();
    }

    public static void resetSiteSettings() {
        PrefServiceBridge.getInstance().resetSiteSettings();
    }

    public static void flushPendingSettings() {
        PrefServiceBridge.getInstance().flushPendingSettings();
    }

    private static List<String> getWebRefinerOrigins() {
        List<String> webRefinerList = new ArrayList<String>();
        List<WebRefinerInfo> webRefinerInfo =  WebsitePreferenceBridge.getWebRefinerInfo();
        for (WebRefinerInfo info: webRefinerInfo)
            webRefinerList.add(info.getOrigin());
        return webRefinerList;
    }

    public static List<String> getOriginsForPermission(PermissionType type) {
        List<String> originsList = new ArrayList<String>();
        if (type == null) return originsList;

        switch(type) {
            case WEBREFINER:
                originsList = getWebRefinerOrigins();
                break;
            default:
                break;
        }

        return originsList;
    }

    public static final class PermissionsService {

        private OriginInfo mCurrentSelectedOrigin = null;
        private List<Set<Website>> mAllWebsites = null;
        private Set<String> mOriginsSet = null;

        private static final String LOGTAG = "PermissionsService";

        private PermissionsService(Set<String> originSet, List<Set<Website>> allWebsites) {
            mOriginsSet = originSet;
            mAllWebsites = allWebsites;
        }

        public Set<String> getOrigins() {
            return mOriginsSet;
        }

        public void purge() {
            if (!mOriginsSet.isEmpty())
                mOriginsSet.clear();

            if (!mAllWebsites.isEmpty())
                mAllWebsites.clear();

            mOriginsSet = null;
            mAllWebsites = null;
        }

        private boolean validPermissionValue(int value) {
            if (value == Permission.ALLOW ||
                value == Permission.BLOCK ||
                value == Permission.ASK ||
                value == Permission.CUSTOM)
                    return true;

            return false;
        }

        //  Method to get a handle on permission for a single origin
        public OriginInfo getOriginInfo(String originOrUrl) {
            mCurrentSelectedOrigin = null;
            // sanity check
            if (TextUtils.isEmpty(originOrUrl))
                return mCurrentSelectedOrigin;

            try {
                originOrUrl = UrlUtilities.fixUrl(originOrUrl);
                String origin = UrlUtilities.getOriginForDisplay(new URI(originOrUrl), true);
                WebsiteAddress currentAddress = WebsiteAddress.create(origin);

                // if neither origin or host are available in the set
                // then it needs to return
                if (!mOriginsSet.contains(currentAddress.getOrigin()) &&
                    !mOriginsSet.contains(currentAddress.getHost()))
                  return mCurrentSelectedOrigin;

                // getMergedPermissions for user selected site
                Website currentSite =
                  SingleWebsitePreferences.mergePermissionInfoForTopLevelOrigin(
                    currentAddress, mAllWebsites);

                mCurrentSelectedOrigin = new OriginInfo(currentSite);
            } catch(Exception e) {
            } finally {
                return mCurrentSelectedOrigin;
            }
        }

        public OriginInfo addOriginInfo(String originOrUrl) {
            OriginInfo newOriginInfo = null;
            if (TextUtils.isEmpty(originOrUrl))
                return newOriginInfo;

            try {
                // check if any origininfo already exists for the url
                newOriginInfo = getOriginInfo(originOrUrl);
                if (newOriginInfo == null) {
                    // create a new origin info
                    originOrUrl = UrlUtilities.fixUrl(originOrUrl);
                    String origin =
                        UrlUtilities.getOriginForDisplay(new URI(originOrUrl), true);
                    WebsiteAddress newAddress = WebsiteAddress.create(origin);
                    newOriginInfo = new OriginInfo(new Website(newAddress));
                }
            } catch (Exception e) {
            } finally {
                return newOriginInfo;
            }
        }

        public void deleteOriginInfo(OriginInfo info) {
            if (info == null)
                return;

            String origin = info.getOrigin();
            // fallback to host name if origin is not
            // available
            if (TextUtils.isEmpty(origin))
                origin = info.getHost();
            if (mOriginsSet.contains(origin)) {
                // reset all the permissions
                info.resetSitePermission();
                mOriginsSet.remove(origin);
            }
        }

        /*
         * Permission for Single Origin
         */
        public class OriginInfo {

            private Website mSite = null;
            private boolean mClearedData = false;

            private OriginInfo(Website site) {
                mSite = site;
                mClearedData = false;
            }

            public String getOrigin() {
                return mSite.getAddress().getOrigin();
            }

            public String getHost() {
                return mSite.getAddress().getHost();
            }

            public boolean didClearStoredData() {
                return mClearedData;
            }

            // get Permission per Site
            public int getPermission(PermissionType type) {
                if (type == null) return Permission.ASK;

                switch(type) {
                    case COOKIE:
                        return Permission.fromContentSetting(
                                    mSite.getCookiePermission());
                    case GEOLOCATION:
                        return Permission.fromContentSetting(
                                    mSite.getGeolocationPermission());
                    case POPUP:
                        checkOrCreateInfo(PermissionType.POPUP);
                        return Permission.fromContentSetting(
                                    mSite.getPopupPermission());
                    case VIDEO:
                        return Permission.fromContentSetting(
                                    mSite.getVideoCapturePermission());
                    case VOICE:
                        return Permission.fromContentSetting(
                                    mSite.getVoiceCapturePermission());
                    case WEBREFINER:
                        return Permission.fromContentSetting(
                                    mSite.getWebRefinerPermission());
                    default:
                        return Permission.ASK;
                }
            }

            private long calculateExpiredTime(long lastUsed) {
                Date currentDate = new Date();
                Date lastUsedDate = new Date();
                lastUsedDate.setTime((long)(lastUsed)*1000);
                long diff  = currentDate.getTime() - lastUsedDate.getTime();
                long expiresInSeconds = TOTAL_HOURS_IN_SECONDS -
                                            TimeUnit.MILLISECONDS.toSeconds(diff);
                return expiresInSeconds;
            }

            public long getPermissionCustomValue(PermissionType type) {
                if (type == null ) return -1;

                switch(type) {
                    case GEOLOCATION:
                        if (mSite.getGeolocationPermission() == ContentSetting.ALLOW_24H) {
                            return calculateExpiredTime
                                    (mSite.getGeolocationInfo().getGetLastUsage());
                        }
                        return -1;
                    default:
                        return -1;
                }
            }

            // set Permission per Site
            public void setPermission(PermissionType type, int value) {
                if (type == null || !validPermissionValue(value)) return;

                String origin = getOrigin();
                // fallback to host name if origin is not available
                if (TextUtils.isEmpty(origin)) {
                    origin = mSite.getAddress().getHost();
                }

                // Make sure that corresponding type info exists
                checkOrCreateInfo(type);

                switch(type) {
                    case COOKIE:
                        setCookie(origin, value);
                        break;
                    case GEOLOCATION:
                        setGeolocation(origin, value);
                        break;
                    case POPUP:
                        setPopup(origin, value);
                        break;
                    case VOICE:
                        setVoice(origin, value);
                        break;
                    case VIDEO:
                        setVideo(origin, value);
                        break;
                    case WEBREFINER:
                        setWebRefiner(origin, value);
                        //TODO: Set the Actual function of the WebRefiner to block PER SITE
                        break;
                    default:
                        break;
                }
            }

            private void checkOrCreateInfo(PermissionType type)  {
                String originOrHost = getOrigin();
                if (TextUtils.isEmpty(originOrHost))
                    originOrHost = getHost();

                try {

                    URI uri = new URI(originOrHost);

                    String scheme = uri.getScheme();
                    String host = uri.getHost();
                    int port = uri.getPort();

                    if (port == -1 && "http".equals(scheme))
                        port = 80;
                    else if (port == -1 && "https".equals(scheme))
                        port = 443;

                    String originUrl;
                    /**
                     * To avoid differnces between the way infobar stores the setting
                     * port number is added as a suffix
                     */
                    if (TextUtils.isEmpty(scheme) || TextUtils.isEmpty(host)) {
                        originUrl = uri.toString();
                    } else if (port == -1) {
                        originUrl = scheme + "://" + host;
                    } else {
                        originUrl = scheme + "://" + host + ":" + port;
                    }

                    switch(type) {
                        case COOKIE:
                            if (mSite.getCookiePermission() == null){
                                mSite.setCookieInfo(
                                    new CookieInfo(originUrl, null));
                            }
                            break;
                        case GEOLOCATION:
                            if (mSite.getGeolocationPermission() == null) {
                                mSite.setGeolocationInfo(
                                    new GeolocationInfo(originUrl, originUrl));
                            }
                            break;
                        case POPUP:
                            if (mSite.getPopupPermission() == null) {
                                // popupexceptioninfo doesn't take url as second parameter
                                mSite.setPopupExceptionInfo(
                                    new PopupExceptionInfo(originUrl, null));
                            }
                            break;
                        case VOICE:
                            if (mSite.getVideoCapturePermission() == null &&
                                mSite.getVoiceCapturePermission() == null) {
                                    mSite.setVoiceAndVideoCaptureInfo(
                                        new VoiceAndVideoCaptureInfo(originUrl, null));
                            }
                            break;
                        case VIDEO:
                            if (mSite.getVideoCapturePermission() == null &&
                                mSite.getVoiceCapturePermission() == null) {
                                    mSite.setVoiceAndVideoCaptureInfo(
                                    new VoiceAndVideoCaptureInfo(originUrl, null));
                            }
                            break;
                        case WEBREFINER:
                            if (mSite.getWebRefinerPermission() == null) {
                                mSite.setWebRefinerInfo(
                                    new WebRefinerInfo(originUrl, null));
                            }
                            break;
                        default:
                            break;
                    }
                } catch (Exception e) {
                    Log.e(LOGTAG, "Could not create permission info");
                }
            }

            private void setCookie(String origin, int value) {
                mSite.setCookiePermission(ContentSetting.fromInt(value));
            }

            private void setGeolocation(String origin, int value) {
                mSite.setGeolocationPermission(ContentSetting.fromInt(value));
            }

            private void setPopup(String origin, int value) {
                mSite.setPopupPermission(ContentSetting.fromInt(value));
            }

            public long getStoredData() {
                return mSite.getTotalUsage();
            }

            public void clearAllStoredData() {
                mSite.clearAllStoredData(
                    new Website.StoredDataClearedCallback() {
                        @Override
                        public void onStoredDataCleared() {
                            mClearedData = true;
                        }
                });

                // clear all data
                SingleWebsitePreferences.clearCookieForOrigin(getOrigin());
            }

            private void setVideo(String origin, int value) {
                mSite.setVideoCapturePermission(ContentSetting.fromInt(value));
            }

            private void setVoice(String origin, int value) {
                mSite.setVoiceCapturePermission(ContentSetting.fromInt(value));
            }

            private void setWebRefiner(String origin, int value) {
                mSite.setWebRefinerPermission(ContentSetting.fromInt(value));
            }

            public void resetSitePermission() {
                mSite.setCookiePermission(null);
                mSite.setGeolocationPermission(null);
                mSite.setMidiPermission(null);
                mSite.setPopupPermission(null);
                mSite.setProtectedMediaIdentifierPermission(null);
                mSite.setPushNotificationPermission(null);
                mSite.setVideoCapturePermission(null);
                mSite.setVoiceCapturePermission(null);
                mSite.setWebRefinerPermission(null);
                clearAllStoredData();
            }
        }

    }// end of PermissionsService



    public static String getPrettyUrl(String url) {
        String prettyUrl = "";
        if (TextUtils.isEmpty(url)) return prettyUrl;
        try {
            prettyUrl =
                (UrlUtilities.getOriginForDisplay(new URI(url), false)).replace("www.","");
        } catch(Exception e) {
        } finally {
            return prettyUrl;
        }
    }

    private static class FaviconCallback implements FaviconImageCallback {
        private FaviconHelper mFaviconHelper;
        private final String mUrl;
        private final ValueCallback<Bitmap> mCallback;

        public FaviconCallback(String url,
                                Context context,
                                ValueCallback<Bitmap> callback) {

            mUrl = url;
            mCallback = callback;

            Profile profile = Profile.getLastUsedProfile();
            FaviconHelper faviconHelper = Engine.getFaviconHelperInstance();
            float density =
                context.getResources().getDisplayMetrics().density;
            if (!faviconHelper.getLocalFaviconImageForURL(
                    profile, url,
                    FaviconHelper.FAVICON | FaviconHelper.TOUCH_ICON
                        | FaviconHelper.TOUCH_PRECOMPOSED_ICON,
                    Math.round(FAVICON_DP * density),
                    this)) {
                onFaviconAvailable(null, null);
            }
        }

        @Override
        public void onFaviconAvailable(final Bitmap image,final String iconUrl) {

            //Call the callback with the bitmap
            ThreadUtils.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mCallback.onReceiveValue(image);
                }
            });
        }
    }

    public static void getFavicon(String url,
                                    Context context,
                                    final ValueCallback<Bitmap> callback) {
        if (TextUtils.isEmpty(url) || callback == null
            || context == null) {
            return;
        } else if (TextUtils.isEmpty(url) || context == null) {
            ThreadUtils.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    callback.onReceiveValue(null);
                }
            });
        }

        new FaviconCallback(url, context, callback);
    }

    public static boolean isSystemLocationEnabled() {
        return LocationSettings.getInstance().isSystemLocationSettingEnabled();
    }
}
