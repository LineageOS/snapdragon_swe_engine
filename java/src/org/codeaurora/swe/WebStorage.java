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

import android.webkit.ValueCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.chromium.chrome.browser.preferences.website.LocalStorageInfo;
import org.chromium.chrome.browser.preferences.website.WebsitePreferenceBridge;
import org.chromium.chrome.browser.preferences.website.StorageInfo;

/**
 * This class is used to manage the JavaScript storage APIs provided by the
 * WebView. It manages the Application Cache API, the Web SQL Database
 * API and the HTML5 Web Storage API.
 *
 * The Application Cache API provides a mechanism to create and maintain an
 * application cache to power offline Web applications. Use of the Application
 * Cache API can be attributed to an origin, however
 * it is not possible to set per-origin quotas. Note that there can be only
 * one application cache per application.
 *
 * The Web SQL Database API provides storage which is private to a given origin.
 * Similar to the Application Cache, use of the Web SQL Database can be attributed
 * to an origin. It is also possible to set per-origin quotas.
 */

public final class WebStorage {

    /**
     * Origin class contains info regarding the amount of storage used by an origin
     */
    public static class Origin {
        private String mOrigin = null;
        private long mQuota = 0;
        private long mUsage = 0;

        protected Origin(String origin, long quota, long usage) {
            mOrigin = origin;
            mQuota = quota;
            mUsage = usage;
        }

        protected Origin(String origin, long quota) {
            mOrigin = origin;
            mQuota = quota;
        }

        protected Origin(String origin) {
            mOrigin = origin;
        }

        public String getOrigin() {
            return mOrigin;
        }

        /**
         * Gets the quota for origin of a Web SQL Database.  Returns quota in
         * bytes. For origins that do not use the Web SQL Database API, quota is set to zero.
         */
        public long getQuota() {
            return mQuota;
        }

        /**
         * Returns total amount of storage (bytes) being used by this origin for all JS storage APIs
         */
        public long getUsage() {
            return mUsage;
        }
    }

    private static WebStorage sWebStorage = null;
    private ArrayList<StorageInfo>mStorageInfoList;

    public static WebStorage getInstance() {
        if (sWebStorage == null) {
            sWebStorage = new WebStorage();
        }
        return sWebStorage;
    }

    /*
     * There are five HTML5 offline storage APIs.
     * 1) Web Storage (ie the localStorage and sessionStorage variables)
     * 2) Web SQL database
     * 3) Application cache
     * 4) Indexed Database
     * 5) Filesystem API
     */
    private WebStorage() {
        mStorageInfoList = null;
    }

    /**
     * Implements WebStorage.getOrigins. Get the per origin usage and quota of APIs 2-5 in
     * aggregate.
     */
    public void getOrigins(final ValueCallback<Map> originsCallback) {
        WebsitePreferenceBridge.fetchStorageInfo(
            new WebsitePreferenceBridge.StorageInfoReadyCallback() {
                @SuppressWarnings("unchecked")
                @Override
                public void onStorageInfoReady(ArrayList array) {
                    mStorageInfoList = array;
                    Map<String, Origin> originsMap = new HashMap<String, Origin>();
                    for (StorageInfo info : mStorageInfoList) {
                        /*
                          NOTE:
                          Setting quota value to -1 since getQuota or getQuotaForOrigin
                          API's are not used in Browser code
                        */
                        Origin origin = new Origin(info.getHost(), -1, info.getSize());
                        originsMap.put(origin.getOrigin(), origin);
                    }
                    originsCallback.onReceiveValue(originsMap);
                }
            });
    }


    /**
     * Implements WebStorage.getUsageForOrigin. Get the usage of APIs 2-5 in aggregate for given
     * origin.
     */
    public void getUsageForOrigin(final String origin,final ValueCallback<Long> usageCallback) {
        long usage = 0;
        if (mStorageInfoList != null) {
            StorageInfo selectedInfo = null;
            for (StorageInfo info : mStorageInfoList) {
                if(info.getHost().equals(origin)){
                    selectedInfo = info;
                    break;
                }
            }
            if (selectedInfo != null)
                usage = selectedInfo.getSize();
            usageCallback.onReceiveValue(usage);
        }
    }


    public void getQuotaForOrigin(String origin, ValueCallback<Long> quotaCallback) {
        // No-op, since its not being used by Browser
    }

    /**
     * Implements WebStorage.deleteOrigin(). Clear the storage of APIs 2-5 for the given origin.
     */
    public void deleteOrigin(String origin) {
        if (mStorageInfoList != null) {
            StorageInfo selectedInfo = null;
            for (StorageInfo info : mStorageInfoList) {
                if(info.getHost().equals(origin)) {
                    info.clear(new WebsitePreferenceBridge.StorageInfoClearedCallback() {
                        @Override
                        public void onStorageInfoCleared() {
                            // can invoke some other function if needed
                        }
                    });
                    break;
                }
            }
        }
    }

    /**
     * Implements WebStorage.deleteAllData(). Clear the storage of all five offline APIs.
     */
    public void deleteAllData() {
        //get all local storage and clear the info
        WebsitePreferenceBridge.fetchLocalStorageInfo(
            new WebsitePreferenceBridge.LocalStorageInfoReadyCallback() {
                @SuppressWarnings("unchecked")
                @Override
                public void onLocalStorageInfoReady(HashMap map) {
                    for (Object o : map.entrySet()) {
                        Map.Entry<String, LocalStorageInfo> entry =
                               (Map.Entry<String, LocalStorageInfo>) o;
                        // So instead of calling clear on LocalStorage
                        // which clear localSotrage as well as Cookie Data
                        // explicit call to clearLocalStorage is invoked
                        entry.getValue().clearLocalStorageData();
                    }
                }
            });

        // get all the remaining web storage API's data and clear
        if (mStorageInfoList != null) {
            for (StorageInfo info : mStorageInfoList) {
                info.clear(new WebsitePreferenceBridge.StorageInfoClearedCallback() {
                    @Override
                    public void onStorageInfoCleared() {
                        // can invoke some other function if needed
                    }
                });
            }
            mStorageInfoList.clear();
        }
    }

    @Deprecated
    public void setQuotaForOrigin(String origin, long quota) {
        // No-op, deprecated
    }
}
