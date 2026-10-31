// SPDX-License-Identifier: Apache-2.0
package com.shymoose.livenotifications;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.view.View;

/** Watches the host Activity through public Android APIs; survives overlay replacement. Main thread only. */
final class ActivityVisibility implements Application.ActivityLifecycleCallbacks {
    interface Listener {
        void changed(boolean visible);
    }

    private final Listener listener;
    private Application application;
    private Activity activity;
    private Class<?> activityClass;
    private Boolean visible;

    ActivityVisibility(Listener listener) {
        this.listener = listener;
    }

    void attach(Context context) {
        while (!(context instanceof Activity) && context instanceof ContextWrapper) {
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) break;
            context = base;
        }
        if (!(context instanceof Activity)) {
            throw new IllegalStateException("KS overlay context does not expose its Activity for visibility tracking");
        }
        activity = (Activity) context;
        activityClass = activity.getClass();
        Application next = activity.getApplication();
        if (application != next) {
            dispose();
            application = next;
            application.registerActivityLifecycleCallbacks(this);
            activity = (Activity) context;
            activityClass = activity.getClass();
        }
        View decor = activity.getWindow().getDecorView();
        publish(decor.getWindowVisibility() == View.VISIBLE && decor.isShown());
    }

    private void publish(boolean next) {
        if (visible != null && visible == next) return;
        visible = next;
        listener.changed(next);
    }

    @Override public void onActivityResumed(Activity resumed) {
        if (resumed.getClass() == activityClass) {
            activity = resumed;
            publish(true);
        }
    }

    @Override public void onActivityPaused(Activity paused) {
        if (paused == activity) publish(false);
    }

    @Override public void onActivityDestroyed(Activity destroyed) {
        if (destroyed == activity) {
            activity = null;
            publish(false);
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

    void dispose() {
        if (application != null) application.unregisterActivityLifecycleCallbacks(this);
        application = null;
        activity = null;
        activityClass = null;
        visible = null;
    }
}
