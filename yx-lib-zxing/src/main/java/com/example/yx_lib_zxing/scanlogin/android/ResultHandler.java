/*
 * Copyright (C) 2008 ZXing authors
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

package com.example.yx_lib_zxing.scanlogin.android;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.zxing.Result;
import com.google.zxing.client.result.ParsedResult;
import com.google.zxing.client.result.ParsedResultType;

import static com.example.yx_lib_zxing.scanlogin.android.ScanConstants.KEY_CUSTOM_PRODUCT_SEARCH;

/**
 * A base class for the Android-specific barcode handlers. These allow the app to polymorphically
 * suggest the appropriate actions for each data type.
 * <p>
 * This class also contains a bunch of utility methods to take common actions like opening a URL.
 * They could easily be moved into a helper object, but it can't be static because the Activity
 * instance is needed to launch an intent.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public abstract class ResultHandler {

    private static final String TAG = ResultHandler.class.getSimpleName();

    private final ParsedResult result;
    private final Activity activity;
    private final Result rawResult;
    private final String customProductSearch;

    ResultHandler(Activity activity, ParsedResult result) {
        this(activity, result, null);
    }

    ResultHandler(Activity activity, ParsedResult result, Result rawResult) {
        this.result = result;
        this.activity = activity;
        this.rawResult = rawResult;
        this.customProductSearch = parseCustomSearchURL();
    }

    public ParsedResult getResult() {
        return result;
    }

    boolean hasCustomProductSearch() {
        return customProductSearch != null;
    }

    Activity getActivity() {
        return activity;
    }

    public CharSequence getDisplayContents() {
        String contents = result.getDisplayResult();
        return contents.replace("\r", "");
    }

    public abstract int getButtonCount();

    public abstract int getButtonText(int index);

    public abstract void handleButtonPress(int index);

    public abstract int getDisplayTitle();

    public final ParsedResultType getType() {
        return result.getType();
    }

    final void webSearch(String query) {
        Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
        intent.putExtra("query", query);
        launchIntent(intent);
    }

    void launchIntent(Intent intent) {
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
            Log.d(TAG, "Launching intent: " + intent + " with extras: " + intent.getExtras());
            try {
                activity.startActivity(intent);
            } catch (ActivityNotFoundException e) {
            }
        }
    }

    private static void putExtra(Intent intent, String key, String value) {
        if (value != null && value.length() > 0) {
            intent.putExtra(key, value);
        }
    }

    private String parseCustomSearchURL() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        String customProductSearch = prefs.getString(KEY_CUSTOM_PRODUCT_SEARCH,
                null);
        if (customProductSearch != null && customProductSearch.trim().length() == 0) {
            return null;
        }
        return customProductSearch;
    }
}
