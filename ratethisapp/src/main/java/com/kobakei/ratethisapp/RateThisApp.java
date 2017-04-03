/*
 * Copyright 2013-2015 Keisuke Kobayashi
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kobakei.ratethisapp;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;

import com.kobakei.ratethisapp.enums.RateConditionsEnum;

/**
 * RateThisApp<br>
 * A library to show the app rate dialog
 * @author Keisuke Kobayashi (k.kobayashi.122@gmail.com)
 *
 */
public class RateThisApp {

    private static final String TAG = RateThisApp.class.getSimpleName();

    private static final String PREF_NAME = "RateThisApp";
    private static final String KEY_INSTALL_DATE = "rta_install_date";
    private static final String KEY_LAUNCH_TIMES = "rta_launch_times";
    private static final String KEY_OPT_OUT = "rta_opt_out";
    private static final String KEY_ASK_LATER_DATE = "rta_ask_later_date";
    private static final String KEY_ALREADY_REVIEWED = "rta_user_reviewed";
    private static final String KEY_APP_COUNTER_VERSION = "rta_counter_version";
    private static final String KEY_APP_VERSION = "rta_reviewed_version";

    private static Date mInstallDate = new Date();
    private static int mLaunchTimes = 0;
    private static boolean mOptOut = false;
    private static Date mAskLaterDate = new Date();
    private static boolean mAlreadyReviewed = false;
    private static int mCounterVersion = -1;
    private static int mReviewedVersion = -1;

    private static Config sConfig = new Config();
    private static Callback sCallback = null;
    // Weak ref to avoid leaking the context
    private static WeakReference<AlertDialog> sDialogRef = null;

    /**
     * If true, print LogCat
     */
    public static final boolean DEBUG = false;

    /**
     * Initialize RateThisApp configuration.
     * @param config Configuration object.
     */
    public static void init(Config config) {
        sConfig = config;
    }

    /**
     * Set callback instance.
     * The callback will receive yes/no/later events.
     * @param callback
     */
    public static void setCallback(Callback callback) {
        sCallback = callback;
    }

    /**
     * Call this API when the launcher activity is launched.<br>
     * It is better to call this API in onStart() of the launcher activity.
     * @param context Context
     */
    public static void onStart(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Editor editor = pref.edit();
        // If it is the first launch, save the date in shared preference.
        if (pref.getLong(KEY_INSTALL_DATE, 0) == 0L) {
            storeInstallDate(context, editor);
        }
        // Increment launch times
        int launchTimes = pref.getInt(KEY_LAUNCH_TIMES, 0);
        launchTimes++;
        editor.putInt(KEY_LAUNCH_TIMES, launchTimes);
        log("Launch times; " + launchTimes);

        editor.apply();

        mInstallDate = new Date(pref.getLong(KEY_INSTALL_DATE, 0));
        mLaunchTimes = pref.getInt(KEY_LAUNCH_TIMES, 0);
        mOptOut = pref.getBoolean(KEY_OPT_OUT, false);
        mAskLaterDate = new Date(pref.getLong(KEY_ASK_LATER_DATE, 0));
        mAlreadyReviewed = pref.getBoolean(KEY_ALREADY_REVIEWED, false);
        mCounterVersion = pref.getInt(KEY_APP_COUNTER_VERSION, -1);
        mReviewedVersion = pref.getInt(KEY_APP_VERSION, -1);

        if (!mOptOut && sConfig.mPromptForNewVersion && mReviewedVersion < sConfig.mCurrentAppVersion && mCounterVersion < sConfig.mCurrentAppVersion){
            clearSharedPreferences(context);
        } else if (mCounterVersion != sConfig.mCurrentAppVersion){ //let's just update the counter version just in case we need it later
            pref.edit().putInt(KEY_APP_COUNTER_VERSION, sConfig.mCurrentAppVersion).commit();
        }

        printStatus(context);
    }

    /**
     * Show the rate dialog if the criteria is satisfied.
     * @param context Context
     * @return true if shown, false otherwise.
     */
    public static boolean showRateDialogIfNeeded(final Context context) {
        if (shouldShowRateDialog()) {
            showRateDialog(context);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Show the rate dialog if the criteria is satisfied.
     * @param context Context
     * @param themeId Theme ID
     * @return true if shown, false otherwise.
     */
    public static boolean showRateDialogIfNeeded(final Context context, int themeId) {
        if (shouldShowRateDialog()) {
            showRateDialog(context, themeId);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Check whether the rate dialog should be shown or not.
     * Developers may call this method directly if they want to show their own view instead of
     * dialog provided by this library.
     * @return
     */
    public static boolean shouldShowRateDialog() {
        if (mOptOut || mAlreadyReviewed) {
            return false;
        } else {
            boolean launchTimesFulfilled = (mLaunchTimes >= sConfig.mCriteriaLaunchTimes);

            long threshold = TimeUnit.DAYS.toMillis(sConfig.mCriteriaInstallDays);   // msec
            boolean installThresholdFulfilled = ((new Date().getTime() - mInstallDate.getTime() >= threshold)
                                                 && (new Date().getTime() - mAskLaterDate.getTime() >= threshold)) ;

            if (sConfig.mAndCondition){
                return launchTimesFulfilled && installThresholdFulfilled;
            } else {
                return launchTimesFulfilled || installThresholdFulfilled;
            }
        }
    }

    /**
     * Show the rate dialog
     * @param context
     */
    public static void showRateDialog(final Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        showRateDialog(context, builder);
    }

    /**
     * Show the rate dialog
     * @param context
     * @param themeId
     */
    public static void showRateDialog(final Context context, int themeId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, themeId);
        showRateDialog(context, builder);
    }

    /**
     * Stop showing the rate dialog
     * @param context
     */
    public static void stopRateDialog(final Context context){
        setOptOut(context, true);
    }

    /**
     * Get count number of the rate dialog launches
     * @return
     */
    public static int getLaunchCount(final Context context){
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return pref.getInt(KEY_LAUNCH_TIMES, 0);
    }
    
    private static void openPlayStore(Context context, String packageName, String url) {
        Uri uri = Uri.parse("market://details?id=" + packageName);
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            uri = Uri.parse(url + packageName);
            context.startActivity(new Intent(Intent.ACTION_VIEW, uri));
        }
    }

    private static void showRateDialog(final Context context, AlertDialog.Builder builder) {
        if (sDialogRef != null && sDialogRef.get() != null) {
            // Dialog is already present
            return;
        }

        int titleId = sConfig.mTitleId != 0 ? sConfig.mTitleId : R.string.rta_dialog_title;
        int messageId = sConfig.mMessageId != 0 ? sConfig.mMessageId : R.string.rta_dialog_message;
        int cancelButtonID = sConfig.mCancelButton != 0 ? sConfig.mCancelButton : R.string.rta_dialog_cancel;
        int thanksButtonID = sConfig.mNoButtonId != 0 ? sConfig.mNoButtonId : R.string.rta_dialog_no;
        int rateButtonID = sConfig.mYesButtonId != 0 ? sConfig.mYesButtonId : R.string.rta_dialog_ok;
        builder.setTitle(titleId);
        builder.setMessage(messageId);
        builder.setCancelable(sConfig.mCancelable);
        builder.setPositiveButton(rateButtonID, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (sCallback != null) {
                    sCallback.onYesClicked();
                }
                String appPackage = context.getPackageName();
                String url = "https://play.google.com/store/apps/details?id=" + appPackage;
                if (!TextUtils.isEmpty(sConfig.mUrl)) {
                    url = sConfig.mUrl;
                }
<<<<<<< HEAD
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                context.startActivity(intent);
                setAlreadyReviewed(context, true);
=======
                openPlayStore(context, appPackage, url);
                setOptOut(context, true);
>>>>>>> 1bda54a572d070d940cceabba4aa82ea0f347aa0
            }
        });
        builder.setNeutralButton(cancelButtonID, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (sCallback != null) {
                    sCallback.onCancelClicked();
                }
                clearSharedPreferences(context);
                storeAskLaterDate(context);
            }
        });
        builder.setNegativeButton(thanksButtonID, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (sCallback != null) {
                    sCallback.onNoClicked();
                }
                setOptOut(context, true);
            }
        });
        builder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (sCallback != null) {
                    sCallback.onCancelClicked();
                }
                clearSharedPreferences(context);
                storeAskLaterDate(context);
            }
        });
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                sDialogRef.clear();
            }
        });
        sDialogRef = new WeakReference<>(builder.show());
    }

    /**
     * Clear data in shared preferences.<br>
     * This API is called when the rate dialog is approved, canceled. or with new app version if the
     * prompt for newer version flag is set in the configuration
     * @param context
     */
    private static void clearSharedPreferences(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Editor editor = pref.edit();
        editor.remove(KEY_INSTALL_DATE);
        mInstallDate = new Date();
        editor.remove(KEY_LAUNCH_TIMES);
        editor.apply();
        mLaunchTimes = 0;
        editor.putInt(KEY_APP_COUNTER_VERSION, sConfig.mCurrentAppVersion);
        mCounterVersion = sConfig.mCurrentAppVersion;
        editor.putBoolean(KEY_ALREADY_REVIEWED, false);
        mAlreadyReviewed = false;
        editor.commit();
    }

    /**
     * Set opt out flag. If it is true, the rate dialog will never shown unless app data is cleared.
     * @param context
     * @param optOut
     */
    private static void setOptOut(final Context context, boolean optOut) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Editor editor = pref.edit();
        editor.putBoolean(KEY_OPT_OUT, optOut);
        editor.apply();
        mOptOut = optOut;
    }

    /**
     * Store install date.
     * Install date is retrieved from package manager if possible.
     * @param context
     * @param editor
     */
    private static void storeInstallDate(final Context context, SharedPreferences.Editor editor) {
        Date installDate = new Date();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            PackageManager packMan = context.getPackageManager();
            try {
                PackageInfo pkgInfo = packMan.getPackageInfo(context.getPackageName(), 0);
                installDate = new Date(pkgInfo.firstInstallTime);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        editor.putLong(KEY_INSTALL_DATE, installDate.getTime());
        log("First install: " + installDate.toString());
    }

    /**
     * Store the date the user asked for being asked again later.
     * @param context
     */
    private static void storeAskLaterDate(final Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Editor editor = pref.edit();
        editor.putLong(KEY_ASK_LATER_DATE, System.currentTimeMillis());
        editor.apply();
    }

    /**
     * Set already reviewed flag. If it is true, the rate dialog will not be shown unless the config
     * is set to ask again for newer versions
     * @param context
     * @param alreadyReviewed
     */
    private static void setAlreadyReviewed(final Context context, boolean alreadyReviewed){
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Editor editor = pref.edit();
        editor.putBoolean(KEY_ALREADY_REVIEWED, alreadyReviewed);
        editor.putInt(KEY_APP_VERSION, sConfig.mCurrentAppVersion);
        editor.commit();
        mAlreadyReviewed = alreadyReviewed;
    }

    /**
     * Print values in SharedPreferences (used for debug)
     * @param context
     */
    private static void printStatus(final Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        log("*** RateThisApp Status ***");
        log("Counter App version" + pref.getInt(KEY_APP_COUNTER_VERSION, -1));
        log("Install Date: " + new Date(pref.getLong(KEY_INSTALL_DATE, 0)));
        log("Launch Times: " + pref.getInt(KEY_LAUNCH_TIMES, 0));
        log("Opt out: " + pref.getBoolean(KEY_OPT_OUT, false));
        log("Already reviewed: " + pref.getBoolean(KEY_ALREADY_REVIEWED, false));
        log("Reviewed version: " + pref.getInt(KEY_APP_VERSION, -1));
    }

    /**
     * Print log if enabled
     * @param message
     */
    private static void log(String message) {
        if (DEBUG) {
            Log.v(TAG, message);
        }
    }

    /**
     * RateThisApp configuration.
     */
    public static class Config {
        private String mUrl = null;
        private int mCriteriaInstallDays;
        private int mCriteriaLaunchTimes;
        private int mTitleId = 0;
        private int mMessageId = 0;
        private int mYesButtonId = 0;
        private int mNoButtonId = 0;
        private int mCancelButton = 0;
        private boolean mCancelable = true;
        private boolean mAndCondition = false;

        private boolean mPromptForNewVersion = false;
        private int mCurrentAppVersion = -1;
        /**
         * Constructor with default criteria.
         */
        public Config() {
            this(7, 10);
        }

        public Config(RateConditionsEnum rateConditionsJoin) {
            this();
            this.mAndCondition = rateConditionsJoin == RateConditionsEnum.AND;
        }

        /**
         * Constructor.
         * @param criteriaInstallDays
         * @param criteriaLaunchTimes
         */
        public Config(int criteriaInstallDays, int criteriaLaunchTimes) {
            this.mCriteriaInstallDays = criteriaInstallDays;
            this.mCriteriaLaunchTimes = criteriaLaunchTimes;
        }

        /**
         * Constructor.
         * @param criteriaInstallDays
         * @param criteriaLaunchTimes
         * @param rateConditionsJoin
         */
        public Config(int criteriaInstallDays, int criteriaLaunchTimes, RateConditionsEnum rateConditionsJoin) {
            this.mCriteriaInstallDays = criteriaInstallDays;
            this.mCriteriaLaunchTimes = criteriaLaunchTimes;
            this.mAndCondition = rateConditionsJoin == RateConditionsEnum.AND;
        }

        /**
         * Set title string ID.
         * @param stringId
         */
        public void setTitle(@StringRes int stringId) {
            this.mTitleId = stringId;
        }

        /**
         * Set message string ID.
         * @param stringId
         */
        public void setMessage(@StringRes int stringId) {
            this.mMessageId = stringId;
        }

        /**
         * Set rate now string ID.
         * @param stringId
         */
        public void setYesButtonText(@StringRes int stringId) {
            this.mYesButtonId = stringId;
        }

        /**
         * Set no thanks string ID.
         * @param stringId
         */
        public void setNoButtonText(@StringRes int stringId) {
            this.mNoButtonId = stringId;
        }

        /**
         * Set cancel string ID.
         * @param stringId
         */
        public void setCancelButtonText(@StringRes int stringId) {
            this.mCancelButton = stringId;
        }

        /**
         * Set navigation url when user clicks rate button.
         * Typically, url will be https://play.google.com/store/apps/details?id=PACKAGE_NAME for Google Play.
         * @param url
         */
        public void setUrl(String url) {
            this.mUrl = url;
        }

        public void setCancelable(boolean cancelable) {
            this.mCancelable = cancelable;
        }
        
        /**
         * Set promptForNewVersion if user rated
         * @param promptForNewVersion
         * @param currentAppVersion
         */
        public void setPromptForNewVersion(boolean promptForNewVersion, int currentAppVersion){
            this.mPromptForNewVersion = promptForNewVersion;
            this.mCurrentAppVersion = currentAppVersion;
        }
        
        /**
         * Set condition to And
         */
        public void setOrCondition() { this.mAndCondition = false; }

        /**
         * Set condition to Or
         */
        public void setAndCondition(){ this.mAndCondition = true; }
    }

    /**
     * Callback of dialog click event
     */
    public interface Callback {
        /**
         * "Rate now" event
         */
        void onYesClicked();

        /**
         * "No, thanks" event
         */
        void onNoClicked();

        /**
         * "Later" event
         */
        void onCancelClicked();
    }
}
