/*
 * Copyright (c) 2013-2015 The Linux Foundation. All rights reserved.
 * Not a contribution.
 *
 * Copyright (C) 2012 The Android Open Source Project
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

import org.chromium.chrome.browser.autofill.PersonalDataManager;
import org.chromium.chrome.browser.preferences.PrefServiceBridge;
import org.chromium.chrome.browser.accessibility.FontSizePrefs;
import org.chromium.chrome.browser.ChromiumApplication;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;


public class WebSettings {

    public enum LayoutAlgorithm {
        NORMAL,
        SINGLE_COLUMN,
        NARROW_COLUMNS,
        TEXT_AUTOSIZING,
    }

    public enum TextSize {
        SMALLEST(50),
        SMALLER(75),
        NORMAL(100),
        LARGER(150),
        LARGEST(200);
        TextSize(int size) {
            value = size;
        }
        int value;
    }

    // SWE: Hard code values to be passed to setInitialPageScale.
    // These values doesn't take screen density into consideration
    public enum ZoomDensity {
        FAR(66),       // 240dpi
        MEDIUM(100),    // 160dpi
        CLOSE(133);      // 120dpi
        ZoomDensity(int size) {
            value = size;
        }
        int value;
    }

    public enum RenderPriority {
        NORMAL,
        HIGH,
        LOW
    }

    public enum PluginState {
        ON,
        ON_DEMAND,
        OFF,
    }

    private final Context mContext;

    /**
     * Default cache usage pattern. Use with setCacheMode.
     */
    public static final int LOAD_DEFAULT = -1;

    /**
     * Normal cache usage pattern. Use with setCacheMode.
     */
    public static final int LOAD_NORMAL = 0;

    /**
     * Use cache if content is there, even if expired. If it's not in the cache
     * load from network. Use with setCacheMode.
     */
    public static final int LOAD_CACHE_ELSE_NETWORK = 1;

    /**
     * Don't use the cache, load from network. Use with setCacheMode.
     */
    public static final int LOAD_NO_CACHE = 2;

    /**
     * Don't use the network, load from cache only. Use with setCacheMode.
     */
    public static final int LOAD_CACHE_ONLY = 3;

    public WebSettings(WebView webView, Context context) {
        mContext = context;
    }

    public void setAutoFillProfile(AutoFillProfile autoFillProfile) {
        // Browser uses Webview to have only a single profile.
        if (autoFillProfile != null)
            addAutoFillProfile(autoFillProfile);
        else
            removeAllAutoFillProfiles();
    }

    public String addAutoFillProfile(AutoFillProfile profile) {
        PersonalDataManager.AutofillProfile pdm_profile =
                toPDMAutofillProfile(profile);
        String guid = PersonalDataManager.getInstance().setProfile(pdm_profile);
        if (!TextUtils.isEmpty(guid)) {
            profile.mUniqueId = guid;
        }
        return guid;
    }

    public void removeAutoFillProfile(AutoFillProfile profile) {
        PersonalDataManager.getInstance().deleteProfile(profile.getUniqueId());
    }

    private PersonalDataManager.AutofillProfile toPDMAutofillProfile(AutoFillProfile profile) {
        return new PersonalDataManager.AutofillProfile(
                profile.getUniqueId(), "",
                profile.getFullName(), profile.getCompanyName(),
                profile.getAddressLine1() + " " + profile.getAddressLine2(),
                profile.getState(),
                profile.getCity(), "",
                profile.getZipCode(), "",
                profile.getCountry(), profile.getPhoneNumber(), profile.getEmailAddress(),
                "");
    }

    private AutoFillProfile fromPDMAutofillProfile(
            PersonalDataManager.AutofillProfile pdm_profile) {
        return new AutoFillProfile(
                pdm_profile.getGUID(),
                pdm_profile.getFullName(),
                pdm_profile.getEmailAddress(),
                pdm_profile.getCompanyName(),
                pdm_profile.getStreetAddress(), "",
                pdm_profile.getLocality(),
                pdm_profile.getRegion(),
                pdm_profile.getPostalCode(),
                pdm_profile.getCountryCode(),
                pdm_profile.getPhoneNumber());
    }

    public AutoFillProfile getAutoFillProfile(String guid) {
        if (TextUtils.isEmpty(guid)) {
            return  null;
        }

        PersonalDataManager.AutofillProfile pdm_profile =
                PersonalDataManager.getInstance().getProfile(guid);
        return fromPDMAutofillProfile(pdm_profile);
    }

    public AutoFillProfile[] getAllAutoFillProfiles() {
        List<PersonalDataManager.AutofillProfile> list =
                PersonalDataManager.getInstance().getProfiles();
        ListIterator<PersonalDataManager.AutofillProfile> iter = list.listIterator();

        List<AutoFillProfile> out_list = new ArrayList<AutoFillProfile>();
        while (iter.hasNext()) {
            PersonalDataManager.AutofillProfile pdm_profile = iter.next();
            AutoFillProfile profile = fromPDMAutofillProfile(pdm_profile);
            out_list.add(profile);
        }

        return (AutoFillProfile[]) out_list.toArray();
    }

    public void removeAllAutoFillProfiles() {
        List<PersonalDataManager.AutofillProfile> list =
                PersonalDataManager.getInstance().getProfiles();
        ListIterator<PersonalDataManager.AutofillProfile> iter = list.listIterator();

        while (iter.hasNext()) {
            PersonalDataManager.AutofillProfile pdm_profile = iter.next();
            PersonalDataManager.getInstance().deleteProfile(pdm_profile.getGUID());
        }
    }

    public void setAutoFillEnabled(boolean enabled) {
        PersonalDataManager.setAutofillEnabled(enabled);
    }

    /*TODO*/ public void setAllowFileAccess(boolean allow) {
    }

    /*SKIP*/ public boolean __remove__getAllowFileAccess() {
        return false;
    }

    /*TODO*/ public void setLoadWithOverviewMode(boolean overview) {
    }

    /*SKIP*/ public boolean __remove__getLoadWithOverviewMode() {
        return false;
    }

    /*DONE*/ public synchronized void setTextZoom(int textZoom) {
        FontSizePrefs.getInstance(mContext).setFontScaleFactor(textZoom / 100.0f);
    }

    /*DONE*/ public synchronized int getTextZoom() {
        return (int) (FontSizePrefs.getInstance(mContext).getFontScaleFactor() * 100);
    }

    /*TODO*/ public synchronized void setUseWideViewPort(boolean use) {
    }

    /*SKIP*/ public synchronized boolean __remove__getUseWideViewPort() {
        return false;
    }

    /*TODO*/ public synchronized void setSupportMultipleWindows(boolean support) {
    }

    /*SKIP*/ public synchronized boolean __remove__supportMultipleWindows() {
        return false;
    }

    @Deprecated
    public synchronized void setLayoutAlgorithm(LayoutAlgorithm l) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    @Deprecated
    public synchronized LayoutAlgorithm getLayoutAlgorithm() {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
        return null;
    }

    /*SKIP*/ public synchronized void __remove__setStandardFontFamily(String font) {
    }

    /*SKIP*/ public synchronized String __remove__getStandardFontFamily() {
        return null;
    }

    /*SKIP*/ public synchronized void __remove__etFixedFontFamily(String font) {
    }

    /*SKIP*/ public synchronized String __remove__getFixedFontFamily() {
        return null;
    }

    /*SKIP*/ public synchronized void __remove__setSansSerifFontFamily(String font) {
    }

    /*SKIP*/ public synchronized String __remove__getSansSerifFontFamily() {
        return null;
    }

    /*SKIP*/ public synchronized void __remove__setSerifFontFamily(String font) {
    }

    /*SKIP*/ public synchronized String __remove__getSerifFontFamily() {
        return null;
    }

    /*SKIP*/ public synchronized void __remove__setCursiveFontFamily(String font) {
    }

    /*SKIP*/ public synchronized String __remove__getCursiveFontFamily() {
        return null;
    }

    /*SKIP*/ public synchronized void __remove__setFantasyFontFamily(String font) {
    }

    /*SKIP*/ public synchronized String __remove__getFantasyFontFamily() {
        return null;
    }

    /*DONE*/ public synchronized void setMinimumFontSize(int size) {
        PrefServiceBridge.getInstance().setMinimumFontSize(size);
    }

    /*DONE*/ public synchronized int getMinimumFontSize() {
        return PrefServiceBridge.getInstance().getMinimumFontSize();
    }

    /*TODO*/ public synchronized void setMinimumLogicalFontSize(int size) {
    }

    /*SKIP*/ public synchronized int __remove__getMinimumLogicalFontSize() {
        return 1;
    }

    /*TODO*/ public synchronized void setDefaultFontSize(int size) {
    }

    /*SKIP*/ public synchronized int __remove__getDefaultFontSize() {
        return 1;
    }

    /*TODO*/ public synchronized void setDefaultFixedFontSize(int size) {
    }

    /*SKIP*/ public synchronized int __remove__getDefaultFixedFontSize() {
        return 1;
    }

    public synchronized void setLoadsImagesAutomatically(boolean flag) {
        PrefServiceBridge.getInstance().setImagesEnabled(flag);
    }

    public synchronized boolean getLoadsImagesAutomatically() {
        return PrefServiceBridge.getInstance().getImagesEnabled();
    }

    /*SKIP*/ public synchronized void __remove__setBlockNetworkImage(boolean flag) {
    }

    /*SKIP*/ public synchronized boolean __remove__getBlockNetworkImage() {
        return false;
    }

    /*SKIP*/ public synchronized void __remove__setBlockNetworkLoads(boolean flag) {
    }

    /*SKIP*/ public synchronized boolean __remove__getBlockNetworkLoads() {
        return false;
    }

    /*DONE*/ public synchronized void setJavaScriptEnabled(boolean flag) {
        PrefServiceBridge.getInstance().setJavaScriptEnabled(flag);
    }

    /*SKIP*/ public synchronized void __remove__setDisableNoScriptTag(boolean flag) {
    }

    /*TODO*/ public void setAllowUniversalAccessFromFileURLs(boolean flag) {
    }

    /*TODO*/ public void setAllowFileAccessFromFileURLs(boolean flag){
    }

    /*SKIP*/ public synchronized PluginState __remove__getPluginState() {
        return null;
    }

    /*SKIP*/ public synchronized void __remove__setPluginState(PluginState state) {
    }

    /*SKIP*/ public synchronized void setAppCacheEnabled(boolean flag) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    /*SKIP*/ public synchronized void setAppCachePath(String appCachePath) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    /*SKIP*/ public synchronized void setDatabaseEnabled(boolean flag) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    /*SKIP*/ public synchronized void setDomStorageEnabled(boolean flag) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    /*SKIP*/ public synchronized boolean __remove__getDomStorageEnabled() {
        return false;
    }

    /*SKIP*/ public synchronized boolean __remove__getDatabaseEnabled() {
        return false;
    }

    /*DONE*/ public synchronized void setGeolocationEnabled(boolean flag) {
        PrefServiceBridge.getInstance().setAllowLocationEnabled(flag);
    }

    /*SKIP*/ public synchronized boolean __remove__getGeolocationEnabled() {
        return PrefServiceBridge.getInstance().isAllowLocationEnabled();
    }

    /*DONE*/ public synchronized void setDoNotTrack(boolean flag) {
        PrefServiceBridge.getInstance().setDoNotTrackEnabled(flag);
    }

    /*SKIP*/ public synchronized boolean __remove__getDoNotTrack() {
        return PrefServiceBridge.getInstance().isDoNotTrackEnabled();
    }

    /*SKIP*/ public synchronized boolean __remove__getJavaScriptEnabled() {
        return PrefServiceBridge.getInstance().javaScriptEnabled();
    }

    /*SKIP*/ public boolean __remove__getAllowUniversalAccessFromFileURLs() {
        return false;
    }

    /*SKIP*/ public boolean __remove__getAllowFileAccessFromFileURLs() {
        return false;
    }

    public synchronized void setNightModeEnabled(boolean flag) {
        PrefServiceBridge.getInstance().setNightModeEnabled(flag);
    }

    public synchronized void setJavaScriptCanOpenWindowsAutomatically(boolean flag) {
        PrefServiceBridge.getInstance().setAllowPopupsEnabled(flag);
    }

    public synchronized boolean getJavaScriptCanOpenWindowsAutomatically() {
        return PrefServiceBridge.getInstance().popupsEnabled();
    }

    public synchronized void setDefaultTextEncodingName(String encoding) {
        PrefServiceBridge.getInstance().setDefaultTextEncodingName(encoding);
    }

    public synchronized String getDefaultTextEncodingName() {
        return PrefServiceBridge.getInstance().getDefaultTextEncodingName();
    }

    public synchronized String getUserAgentString() {
        return ChromiumApplication.getBrowserUserAgent();
    }

    /*TODO*/ public void setNeedInitialFocus(boolean flag) {
    }

    /*SKIP*/ public void __remove__setCacheMode(int mode) {
    }

    /*SKIP*/ public int __remove__getCacheMode() {
        return -1;
    }

    /*DEFER*/ public static String getDefaultUserAgent(Context context) {
        return null;
    }

    /*SKIP*/ public boolean __remove__getMediaPlaybackRequiresUserGesture() {
        return false;
    }

    /*TODO*/ public void setMediaPlaybackRequiresUserGesture(boolean require) {
    }

    /*SKIP*/ public void __remove__setSupportZoom(boolean support) {
    }

    /*SKIP*/ public boolean __remove__supportZoom() {
        return false;
    }

    /*SKIP*/ public void setBuiltInZoomControls(boolean enabled) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    /*SKIP*/ public boolean getBuiltInZoomControls() {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
        return false;
    }

    /*SKIP*/ public void setDisplayZoomControls(boolean enabled) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    /*SKIP*/ public boolean __remove__getDisplayZoomControls() {
        return false;
    }

    /*TODO*/ public void setSaveFormData(boolean save) {
    }

    /*SKIP*/ public boolean __remove__getSaveFormData() {
        return false;
    }

    /*DONE*/ public void setSavePassword(boolean save) {
        PrefServiceBridge.getInstance().setRememberPasswordsEnabled(save);
    }

    /*SKIP*/ public boolean __remove__getSavePassword() {
        return PrefServiceBridge.getInstance().isRememberPasswordsEnabled();
    }

    /*DONE*/ public void clearPasswords() {
        // SWE-feature-Clear-Browser-Data
        PrefServiceBridge.getInstance().clearBrowsingData(null,
                                                          false,
                                                          false,
                                                          false,
                                                          true,
                                                          false);
        // SWE-feature-Clear-Browser-Data
    }

    /*SKIP*/ public void setAllowMediaDownloads(boolean allow) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    /*SKIP*/ public synchronized void __remove__setRenderPriority(RenderPriority priority) {
    }

    /*SKIP*/ public synchronized void setAppCacheMaxSize(long appCacheMaxSize) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    /*SKIP*/ public synchronized void setDatabasePath(String databasePath) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    /*SKIP*/ public synchronized String getDatabasePath() {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
        return "";
    }

    /*SKIP*/ public synchronized void setGeolocationDatabasePath(String databasePath) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    /*TODO*/ public void setDefaultZoom(ZoomDensity zoom) {
    }

    /*TODO*/ public ZoomDensity getDefaultZoom() {
        return null;
    }

    /*SKIP*/ public void setLightTouchEnabled(boolean enabled) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    /*SKIP*/ public boolean __remove__getLightTouchEnabled() {
        return false;
    }


    @Deprecated
    /*SKIP*/ public void setEnableSmoothTransition(boolean enable) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    @Deprecated
    /*SKIP*/ public boolean enableSmoothTransition() {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
        return false;
    }

    @Deprecated
    /*SKIP*/ public boolean __remove__getPluginsEnabled() {
        return false;
    }

    @Deprecated
    /*SKIP*/ public void __remove__setPluginsEnabled(boolean flag) {
    }

    @Deprecated
    /*SKIP*/ public String __remove__getPluginsPath() {
        return "";
    }

    @Deprecated
    /*SKIP*/ public void __remove__setPluginsPath(String pluginPath) {
    }

    @Deprecated
    /*SKIP*/ public void setTextSize(TextSize t) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
         setTextZoom(t.value);
    }

    @Deprecated
    /*SKIP*/ public TextSize getTextSize() {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
        TextSize setSize = null;
        int smallestDifference = Integer.MAX_VALUE;
        for (TextSize textSize : TextSize.values()) {
            int difference = Math.abs(getTextZoom() - textSize.value);
            if (difference == 0) {
                return textSize;
            }
            if (difference < smallestDifference) {
                smallestDifference = difference;
                setSize = textSize;
            }
        }
        return setSize != null ? setSize : TextSize.NORMAL;
    }

    /*TODO*/ public boolean isPrivateBrowsingEnabled() {
        return false;
    }

    /*SKIP*/ public synchronized void __remove__setPrivateBrowsingEnabled(boolean flag) {
    }

    /*SKIP*/ public void setProperty(String gfxinvertedscreen, String string) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    /*DONE*/ public void setForceUserScalable(boolean forceEnableUserScalable) {
        FontSizePrefs.getInstance(mContext).setForceEnableZoom(forceEnableUserScalable);
    }

    /*DONE*/ public boolean getForceUserScalable() {
        return FontSizePrefs.getInstance(mContext).getForceEnableZoom();
    }

    /*TODO*/ public void setShowVisualIndicator(boolean enableVisualIndicator) {
    }

    /*SKIP*/ public void setDoubleTapZoom(int doubleTapZoom) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    /*TODO*/ public void setHTTPRequestHeaders(String headers) {
    }

    /*TODO*/ public void setLinkPrefetchEnabled(boolean mLinkPrefetchAllowed) {
    }

    /*TODO*/ public void setPageCacheCapacity(int pageCacheCapacity) {
    }

    /*TODO*/ public void setHardwareAccelSkiaEnabled(boolean skiaHardwareAccelerated) {
    }

    @Deprecated
    /*SKIP*/ public void setNavDump(boolean enablenavdump) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
        // Old code did NOTHING
    }

    /*SKIP*/ public void __remove__setWorkersEnabled(boolean workersenabled) {
    }

    /*TODO*/ public void setFullscreenSupported(boolean enable) {

    }

    /*TODO*/ public void setAllowContentAccess(boolean allow) {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
    }

    /*SKIP*/ public boolean __remove__getAllowContentAccess() {
        // SWE:TODO : Remove calls from browser and remove this method !! Not used
        return false;
    }
}
