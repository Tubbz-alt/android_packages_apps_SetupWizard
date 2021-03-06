/*
 * Copyright (C) 2013 The CyanogenMod Project
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

package com.cyanogenmod.setupwizard.setup;

import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.NumberPicker;

import com.cyanogenmod.setupwizard.R;
import com.cyanogenmod.setupwizard.SetupWizardApp;
import com.cyanogenmod.setupwizard.cmstats.SetupStats;
import com.cyanogenmod.setupwizard.ui.LocalePicker;
import com.cyanogenmod.setupwizard.ui.SetupPageFragment;
import com.cyanogenmod.setupwizard.util.SetupWizardUtils;

import java.util.Locale;

public class WelcomePage extends SetupPage {

    public static final String TAG = "WelcomePage";

    private static final String ACTION_EMERGENCY_DIAL = "com.android.phone.EmergencyDialer.DIAL";

    private WelcomeFragment mWelcomeFragment;

    public WelcomePage(Context context, SetupDataCallbacks callbacks) {
        super(context, callbacks);
    }

    @Override
    public Fragment getFragment(FragmentManager fragmentManager, int action) {
        mWelcomeFragment = (WelcomeFragment)fragmentManager.findFragmentByTag(getKey());
        if (mWelcomeFragment == null) {
            Bundle args = new Bundle();
            args.putString(Page.KEY_PAGE_ARGUMENT, getKey());
            args.putInt(Page.KEY_PAGE_ACTION, action);
            mWelcomeFragment = new WelcomeFragment();
            mWelcomeFragment.setArguments(args);
        }
        return mWelcomeFragment;
    }

    @Override
    public int getTitleResId() {
        return R.string.setup_welcome;
    }

    @Override
    public boolean doNextAction() {
        if (isLocked()) {
            confirmCyanogenCredentials(mWelcomeFragment);
            return true;
        } else {
            return super.doNextAction();
        }
    }

    @Override
    public boolean doPreviousAction() {
        Intent intent = new Intent(ACTION_EMERGENCY_DIAL);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        ActivityOptions options =
                ActivityOptions.makeCustomAnimation(mContext,
                        android.R.anim.fade_in,
                        android.R.anim.fade_out);
        SetupStats.addEvent(SetupStats.Categories.BUTTON_CLICK, SetupStats.Label.EMERGENCY_CALL);
        SetupStats.addEvent(SetupStats.Categories.EXTERNAL_PAGE_LOAD,
                SetupStats.Action.EXTERNAL_PAGE_LAUNCH,
                SetupStats.Label.PAGE,  SetupStats.Label.EMERGENCY_CALL);
        mContext.startActivity(intent, options.toBundle());
        return true;
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SetupWizardApp.REQUEST_CODE_UNLOCK) {
            if (resultCode == Activity.RESULT_OK) {
                ((SetupWizardApp) mContext.getApplicationContext()).setIsAuthorized(true);
                getCallbacks().onNextPage();
                return true;
            }
        }
        return false;
    }

    @Override
    public String getKey() {
        return TAG;
    }

    @Override
    public int getNextButtonTitleResId() {
        if (isLocked()) {
            return R.string.setup_unlock;
        } else {
            return R.string.next;
        }
    }

    @Override
    public int getPrevButtonTitleResId() {
        return R.string.emergency_call;
    }

    private void confirmCyanogenCredentials(final Fragment fragment) {
        AccountManager accountManager = AccountManager.get(mContext);
        accountManager.editProperties(SetupWizardApp.ACCOUNT_TYPE_CYANOGEN, null,
                new AccountManagerCallback<Bundle>() {
                    public void run(AccountManagerFuture<Bundle> f) {
                        try {
                            Bundle b = f.getResult();
                            Intent i = b.getParcelable(AccountManager.KEY_INTENT);
                            i.putExtra(SetupWizardApp.EXTRA_FIRST_RUN, true);
                            i.putExtra(SetupWizardApp.EXTRA_SHOW_BUTTON_BAR, true);
                            i.putExtra(SetupWizardApp.EXTRA_USE_IMMERSIVE, true);
                            i.putExtra(SetupWizardApp.EXTRA_LOGIN_FOR_KILL_SWITCH, true);
                            fragment.startActivityForResult(i,
                                    SetupWizardApp.REQUEST_CODE_UNLOCK);
                        } catch (Throwable t) {
                            Log.e(getKey(), "confirmCredentials failed", t);
                        }
                    }
                }, null);
    }

    private boolean isLocked() {
        boolean isAuthorized = ((SetupWizardApp) mContext.getApplicationContext()).isAuthorized();
        if (SetupWizardUtils.isDeviceLocked()) {
            return !isAuthorized;
        }
        return false;
    }

    public static class WelcomeFragment extends SetupPageFragment {

        private ArrayAdapter<com.android.internal.app.LocalePicker.LocaleInfo> mLocaleAdapter;
        private Locale mInitialLocale;
        private Locale mCurrentLocale;
        private int[] mAdapterIndices;

        private LocalePicker mLanguagePicker;

        private final Handler mHandler = new Handler();

        private final Runnable mUpdateLocale = new Runnable() {
            public void run() {
                if (mCurrentLocale != null) {
                    com.android.internal.app.LocalePicker.updateLocale(mCurrentLocale);
                }
            }
        };

        @Override
        protected void initializePage() {
            mLanguagePicker = (LocalePicker) mRootView.findViewById(R.id.locale_list);
            loadLanguages();
            final boolean brandedDevice = getResources().getBoolean(
                    R.bool.branded_device);
            if (brandedDevice) {
                mRootView.findViewById(R.id.powered_by_logo).setVisibility(View.VISIBLE);
            }
        }

        private void loadLanguages() {
            mLocaleAdapter = com.android.internal.app.LocalePicker.constructAdapter(getActivity(), R.layout.locale_picker_item, R.id.locale);
            mInitialLocale = Locale.getDefault();
            mCurrentLocale = mInitialLocale;
            mAdapterIndices = new int[mLocaleAdapter.getCount()];
            int currentLocaleIndex = 0;
            String [] labels = new String[mLocaleAdapter.getCount()];
            for (int i=0; i<mAdapterIndices.length; i++) {
                com.android.internal.app.LocalePicker.LocaleInfo localLocaleInfo = mLocaleAdapter.getItem(i);
                Locale localLocale = localLocaleInfo.getLocale();
                if (localLocale.equals(mCurrentLocale)) {
                    currentLocaleIndex = i;
                }
                mAdapterIndices[i] = i;
                labels[i] = localLocaleInfo.getLabel();
            }
            mLanguagePicker.setDisplayedValues(labels);
            mLanguagePicker.setMaxValue(labels.length - 1);
            mLanguagePicker.setValue(currentLocaleIndex);
            mLanguagePicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
            mLanguagePicker.setOnValueChangedListener(new LocalePicker.OnValueChangeListener() {
                public void onValueChange(LocalePicker picker, int oldVal, int newVal) {
                    setLocaleFromPicker();
                }
            });
        }

        private void setLocaleFromPicker() {
            int i = mAdapterIndices[mLanguagePicker.getValue()];
            final com.android.internal.app.LocalePicker.LocaleInfo localLocaleInfo = mLocaleAdapter.getItem(i);
            onLocaleChanged(localLocaleInfo.getLocale());
        }

        private void onLocaleChanged(Locale paramLocale) {
            Resources localResources = getActivity().getResources();
            Configuration localConfiguration1 = localResources.getConfiguration();
            Configuration localConfiguration2 = new Configuration();
            localConfiguration2.locale = paramLocale;
            localResources.updateConfiguration(localConfiguration2, null);
            localResources.updateConfiguration(localConfiguration1, null);
            mHandler.removeCallbacks(mUpdateLocale);
            mCurrentLocale = paramLocale;
            SetupStats.addEvent(SetupStats.Categories.SETTING_CHANGED,
                    SetupStats.Action.CHANGE_LOCALE, SetupStats.Label.LOCALE,
                    mCurrentLocale.getDisplayName());
            mHandler.postDelayed(mUpdateLocale, 1000);
        }

        @Override
        protected int getLayoutResource() {
            return R.layout.setup_welcome_page;
        }

    }

}
