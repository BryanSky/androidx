/*
 * Copyright (C) 2013 The Android Open Source Project
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

package androidx.appcompat.app;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.MenuInflater;
import android.view.Window;

import androidx.appcompat.R;
import androidx.appcompat.app.AppCompatDelegateImplV9.AppCompatWindowCallbackV14;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.view.SupportMenuInflater;
import androidx.appcompat.widget.TintTypedArray;

abstract class AppCompatDelegateImplBase extends AppCompatDelegate {

    static final boolean DEBUG = false;

    private static boolean sInstalledExceptionHandler;
    private static final boolean SHOULD_INSTALL_EXCEPTION_HANDLER = Build.VERSION.SDK_INT < 21;

    static final String EXCEPTION_HANDLER_MESSAGE_SUFFIX= ". If the resource you are"
            + " trying to use is a vector resource, you may be referencing it in an unsupported"
            + " way. See AppCompatDelegate.setCompatVectorFromResourcesEnabled() for more info.";

    static {
        if (SHOULD_INSTALL_EXCEPTION_HANDLER && !sInstalledExceptionHandler) {
            final Thread.UncaughtExceptionHandler defHandler
                    = Thread.getDefaultUncaughtExceptionHandler();

            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread thread, final Throwable thowable) {
                    if (shouldWrapException(thowable)) {
                        // Now wrap the throwable, but append some extra information to the message
                        final Throwable wrapped = new Resources.NotFoundException(
                                thowable.getMessage() + EXCEPTION_HANDLER_MESSAGE_SUFFIX);
                        wrapped.initCause(thowable.getCause());
                        wrapped.setStackTrace(thowable.getStackTrace());
                        defHandler.uncaughtException(thread, wrapped);
                    } else {
                        defHandler.uncaughtException(thread, thowable);
                    }
                }

                private boolean shouldWrapException(Throwable throwable) {
                    if (throwable instanceof Resources.NotFoundException) {
                        final String message = throwable.getMessage();
                        return message != null && (message.contains("drawable")
                                || message.contains("Drawable"));
                    }
                    return false;
                }
            });

            sInstalledExceptionHandler = true;
        }
    }

    private static final int[] sWindowBackgroundStyleable = {android.R.attr.windowBackground};

    final Context mContext;
    final Window mWindow;
    final Window.Callback mOriginalWindowCallback;
    final Window.Callback mAppCompatWindowCallback;
    final AppCompatCallback mAppCompatCallback;

    ActionBar mActionBar;
    MenuInflater mMenuInflater;

    // true if this activity has an action bar.
    boolean mHasActionBar;
    // true if this activity's action bar overlays other activity content.
    boolean mOverlayActionBar;
    // true if this any action modes should overlay the activity content
    boolean mOverlayActionMode;
    // true if this activity is floating (e.g. Dialog)
    boolean mIsFloating;
    // true if this activity has no title
    boolean mWindowNoTitle;

    private CharSequence mTitle;

    AppCompatDelegateImplBase(Context context, Window window, AppCompatCallback callback) {
        mContext = context;
        mWindow = window;
        mAppCompatCallback = callback;

        mOriginalWindowCallback = mWindow.getCallback();
        if (mOriginalWindowCallback instanceof AppCompatWindowCallbackV14) {
            throw new IllegalStateException(
                    "AppCompat has already installed itself into the Window");
        }
        mAppCompatWindowCallback = wrapWindowCallback(mOriginalWindowCallback);
        // Now install the new callback
        mWindow.setCallback(mAppCompatWindowCallback);

        final TintTypedArray a = TintTypedArray.obtainStyledAttributes(
                context, null, sWindowBackgroundStyleable);
        final Drawable winBg = a.getDrawableIfKnown(0);
        if (winBg != null) {
            mWindow.setBackgroundDrawable(winBg);
        }
        a.recycle();
    }

    abstract void initWindowDecorActionBar();

    abstract Window.Callback wrapWindowCallback(Window.Callback callback);

    @Override
    public ActionBar getSupportActionBar() {
        // The Action Bar should be lazily created as hasActionBar
        // could change after onCreate
        initWindowDecorActionBar();
        return mActionBar;
    }

    final ActionBar peekSupportActionBar() {
        return mActionBar;
    }

    @Override
    public MenuInflater getMenuInflater() {
        // Make sure that action views can get an appropriate theme.
        if (mMenuInflater == null) {
            initWindowDecorActionBar();
            mMenuInflater = new SupportMenuInflater(
                    mActionBar != null ? mActionBar.getThemedContext() : mContext);
        }
        return mMenuInflater;
    }

    @Override
    public final ActionBarDrawerToggle.Delegate getDrawerToggleDelegate() {
        return new ActionBarDrawableToggleImpl();
    }

    final Context getActionBarThemedContext() {
        Context context = null;

        // If we have an action bar, let it return a themed context
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            context = ab.getThemedContext();
        }

        if (context == null) {
            context = mContext;
        }
        return context;
    }

    private class ActionBarDrawableToggleImpl implements ActionBarDrawerToggle.Delegate {
        ActionBarDrawableToggleImpl() {
        }

        @Override
        public Drawable getThemeUpIndicator() {
            final TintTypedArray a = TintTypedArray.obtainStyledAttributes(
                    getActionBarThemedContext(), null, new int[]{ R.attr.homeAsUpIndicator });
            final Drawable result = a.getDrawable(0);
            a.recycle();
            return result;
        }

        @Override
        public Context getActionBarThemedContext() {
            return AppCompatDelegateImplBase.this.getActionBarThemedContext();
        }

        @Override
        public boolean isNavigationVisible() {
            final ActionBar ab = getSupportActionBar();
            return ab != null && (ab.getDisplayOptions() & ActionBar.DISPLAY_HOME_AS_UP) != 0;
        }

        @Override
        public void setActionBarUpIndicator(Drawable upDrawable, int contentDescRes) {
            ActionBar ab = getSupportActionBar();
            if (ab != null) {
                ab.setHomeAsUpIndicator(upDrawable);
                ab.setHomeActionContentDescription(contentDescRes);
            }
        }

        @Override
        public void setActionBarDescription(int contentDescRes) {
            ActionBar ab = getSupportActionBar();
            if (ab != null) {
                ab.setHomeActionContentDescription(contentDescRes);
            }
        }
    }

    abstract ActionMode startSupportActionModeFromWindow(ActionMode.Callback callback);

    final Window.Callback getWindowCallback() {
        return mWindow.getCallback();
    }

    @Override
    public final void setTitle(CharSequence title) {
        mTitle = title;
        onTitleChanged(title);
    }

    abstract void onTitleChanged(CharSequence title);

    final CharSequence getTitle() {
        // If the original window callback is an Activity, we'll use its title
        if (mOriginalWindowCallback instanceof Activity) {
            return ((Activity) mOriginalWindowCallback).getTitle();
        }
        // Else, we'll return the title we have recorded ourselves
        return mTitle;
    }
}
