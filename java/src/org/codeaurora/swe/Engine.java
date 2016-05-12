/*
 *  Copyright (c) 2012-2014 The Linux Foundation. All rights reserved.
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

import org.chromium.base.CommandLine;
import org.chromium.base.ContentUriUtils;
import org.chromium.base.PathUtils;
import org.chromium.base.library_loader.LibraryLoader;
import org.chromium.base.library_loader.ProcessInitException;
import org.chromium.chrome.browser.profiles.Profile;

import org.chromium.content.browser.BrowserStartupController;
import org.chromium.base.library_loader.LibraryProcessType;
import org.chromium.content.browser.ChildProcessLauncher;
import org.chromium.content.browser.DeviceUtils;
import org.chromium.base.ResourceExtractor;
import org.chromium.chrome.browser.DevToolsServer;
import org.chromium.chrome.browser.FileProviderHelper;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.content.browser.TracingControllerAndroid;
import org.chromium.chrome.browser.favicon.FaviconHelper;

import org.codeaurora.swe.utils.Logger;

import android.content.Context;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;


public final class Engine {
    private static final String[] CHROME_MANDATORY_PAKS = {
        "en-US.pak",
        "resources.pak",
        "chrome_100_percent.pak",
        "icudtl.dat",
        "natives_blob.bin",
        "snapshot_blob.bin"
    };
    private static final String ENABLE_SWE = "enabled-swe";
    private static final String DISABLE_TRANSLATE = "disable-translate";
    public static final String ENABLE_DEBUG_MODE = "enable-debug-mode";

    private static boolean sInitialized = false;

    private static Engine.StartupCallback mStartupCallback;
    private static Context mContext;
    private static boolean mIsAsync = false;
    private static DevToolsServer mDevToolsServer;
    private static boolean sCommandLineInitialized = false;
    private static TracingControllerAndroid sTracingController = null;
    private static FaviconHelper sFaviconHelper = null;
    private static boolean sTracing = false;

    public static void initializeCommandLine(Context context, char[] commandLine) {
        if (sCommandLineInitialized) {
            return;
        }
        sCommandLineInitialized = true;
        CommandLine.init(commandLine == null ?
                          null : CommandLine.tokenizeQuotedAruments(commandLine));
        // append default switches
        CommandLine.getInstance().appendSwitch(DISABLE_TRANSLATE);
        CommandLine.getInstance().appendSwitch(ENABLE_SWE);
        if (CommandLine.getInstance().hasSwitch(ENABLE_DEBUG_MODE)) {
            Logger.enableVerboseLogging();
        }
    }

    /**
     * Engine initialization at the application level. Handles initialization of
     * information that needs to be shared across the main activity and the
     * sandbox services created.
     *
     * @param context
     * @param callback
     */
    public static void initialize(Context context, char[] commandLine,
                                  final Engine.StartupCallback callback) {
        if (sInitialized) {
            return;
        }
        mContext = context;
        mIsAsync = true;
        initializeCommandLine(context, commandLine);
        initializeApplicationParameters();
        mStartupCallback = callback;

        try{
            BrowserStartupController.get(context,  LibraryProcessType.PROCESS_BROWSER).startBrowserProcessesAsync(
                new BrowserStartupController.StartupCallback(){
                    @Override
                    public void onSuccess(boolean alreadyStarted) {
                        finishInitialization(alreadyStarted);
                    }
                    @Override
                    public void onFailure() {
                        initializationFailed();
                    }
            });
         } catch (Exception e) {
         }
         ContentUriUtils.setFileProviderUtil(new FileProviderHelper());
    }

    public static FaviconHelper getFaviconHelperInstance() {
        if (sFaviconHelper == null) {
            sFaviconHelper = new FaviconHelper();
        }
        return sFaviconHelper;
    }

    private static void finishInitialization(boolean alreadyStarted) {
        SharedPreferences sharedPreferences =
            mContext.getSharedPreferences("webview", Context.MODE_PRIVATE);

        sInitialized = true;

        // initialize WebStorage
        WebStorage.getInstance();
        // initialize CookieSyncManager
        CookieSyncManager.createInstance(mContext);
        // initialize WebViewDatabase
        WebViewDatabase.getInstance(mContext);
        // initialize CookieManager
        CookieManager.getInstance();
        // initialize GeolocationPermissions
        GeolocationPermissions.getInstance(sharedPreferences);
        // initialize WebRefiner
        WebRefiner.Initialize(mContext);

        if(mIsAsync && mStartupCallback!=null)
            mStartupCallback.onSuccess(alreadyStarted);
    }

    private static void initializationFailed() {
        if(mIsAsync && mStartupCallback!=null)
            mStartupCallback.onFailure();
    }

    /**
     * Used by classes that want to know whether the engine was initialized
     * (libraries loaded, engine set-up) or not.
     *
     * @return true if the web.* classes are usable.
     */
    public static boolean getIsInitialized() {
        return sInitialized;
    }

    public static void initializeApplicationParameters() {
        ResourceExtractor.setMandatoryPaksToExtract(CHROME_MANDATORY_PAKS);
        PathUtils.setPrivateDataDirectorySuffix("swe_webview");
    }

    public static String getDefaultUserAgent() {
        // SWE:TODO
        return "";
    }

    public static void pauseTracing(Context context) {
        if (sTracing) {
            getTracingController(context).unregisterReceiver(context);
            sTracing = false;
        }
    }

    public static void resumeTracing(Context context) {
        if (!sTracing) {
            getTracingController(context).registerReceiver(context);
            sTracing = true;
        }
    }

    private static TracingControllerAndroid getTracingController(Context context) {
        if (sTracingController == null) {
            sTracingController = new TracingControllerAndroid(context);
        }
        return sTracingController;
    }

    public static void startExtractingResources(Context context) {
        // SWE:TODOs : Check with Kvel is this really required???
        //Calling to ResourceExtractor is needed for libnetxt, in case of change please inform.
        ResourceExtractor resourceExtractor = ResourceExtractor.get(context);
        resourceExtractor.startExtractingResources();
    }

    public static void setWebContentsDebuggingEnabled(boolean enable) {
        if (mDevToolsServer == null) {
            if (!enable) return;
            /**
             * Keep socket name format to be of form "webview_devtools_server_pid"
             * which is required to work with Chrome devtools and Chromedriver.
             * Pass prefix string "webview" to DevToolsServer which will append
             * "_devtools_remote_%pid", where %pid is process id of running app.
             */
            mDevToolsServer = new DevToolsServer("webview");
        }
        mDevToolsServer.setRemoteDebuggingEnabled(enable);
    }

    public static void initialize(Context context, char[] commandLine ) {
        mIsAsync = false;
        mContext = context;
        if (sInitialized) {
            return;
        }
        initializeCommandLine(context, commandLine);
        try {
            BrowserStartupController.get(mContext,
                LibraryProcessType.PROCESS_BROWSER).startBrowserProcessesSync(false);
        } catch (ProcessInitException e) {
            Log.e("TAG", "SWE WebView Initialization failed", e);
            System.exit(-1);
        }
        finishInitialization(false);
        ContentUriUtils.setFileProviderUtil(new FileProviderHelper());
    }

    public static void warmUpChildProcess(Context context) {
        ChildProcessLauncher.warmUp(context);
    }

    public static void loadNativeLibraries(Context context) throws ProcessInitException {
        LibraryLoader.get(LibraryProcessType.PROCESS_BROWSER).ensureInitialized(context, true);
    }

    /**
     * This provides the interface to the callbacks for successful or failed startup
     */
    public interface StartupCallback {
        void onSuccess(boolean alreadyStarted);
        void onFailure();
    }

    public static void purgeAllSnapshots(Context context) {
        // SWE:TODO: Hold this in engine when using single ContentViewRenderView
        //           should be shared by all webviews
        TabContentManager manager = new TabContentManager(context, null, true);
        manager.purgeAllSnapshots();
        manager.destroy();
    }

    public static void destroyIncognitoProfile() {
        Profile profile = Profile.getLastUsedProfile();
        if (profile.hasOffTheRecordProfile()) {
            Profile incognito = profile.getOffTheRecordProfile();
            incognito.destroyWhenAppropriate();
        }
    }

}
