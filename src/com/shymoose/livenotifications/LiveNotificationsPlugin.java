// SPDX-License-Identifier: Apache-2.0
package com.shymoose.livenotifications;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.KsTheme;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

public final class LiveNotificationsPlugin implements KioskPlugin {
    static final String OVERLAY = "notification";
    static final long PREVIEW_MS = 120_000;

    interface Scheduler {
        void post(Runnable runnable);
        void postDelayed(Runnable runnable, long delay);
        void removeCallbacks(Runnable runnable);
    }

    interface Clock {
        long now();
    }

    private final Scheduler main;
    private final Clock clock;
    private final Rotation rotation = new Rotation();
    private final Set<String> subscribed = new LinkedHashSet<>();
    private final Map<String, NotificationModel.Snapshot> snapshots = new LinkedHashMap<>();
    private PluginHost host;
    private NotificationModel.Config config;
    private NotificationModel.Item selected;
    private NotificationChip chip;
    private boolean running, shown, screenOn, screensaver, preview, previewSetting;
    private int screenRevision, saverRevision, session, overlayRevision;
    private String configError, runtimeError, lastStatus;
    private boolean lastStatusError;

    public LiveNotificationsPlugin() {
        this(new Scheduler() {
            private final Handler handler = new Handler(Looper.getMainLooper());
            public void post(Runnable runnable) { handler.post(runnable); }
            public void postDelayed(Runnable runnable, long delay) { handler.postDelayed(runnable, delay); }
            public void removeCallbacks(Runnable runnable) { handler.removeCallbacks(runnable); }
        }, SystemClock::uptimeMillis);
    }

    LiveNotificationsPlugin(Scheduler main, Clock clock) {
        this.main = main;
        this.clock = clock;
    }

    // Both the plugin worker and UI tasks take this lock; host overlay calls never block.
    @Override public synchronized void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        running = true;
        session++;
        screenOn = screensaver = preview = previewSetting = shown = false;
        screenRevision = saverRevision = 0;
        configError = runtimeError = lastStatus = null;
        chip = null;
        subscribed.clear();
        snapshots.clear();
        rotation.reset();
        Map<String, Object> initial = new LinkedHashMap<>(settings);
        initial.put("preview", false);
        try {
            if (NotificationModel.bool(settings, "preview", false)) {
                host.updateSettings(Collections.singletonMap("preview", false));
            }
            configure(initial);
            host.subscribe("screensaver.state");
            host.subscribe("screensaver.view");
            host.subscribe("screen.state");
            readInitialState();
        } catch (RuntimeException | LinkageError error) {
            fault("Cannot initialize the supported KS overlay API", error);
        }
    }

    private void readInitialState() {
        int currentSession = session;
        int initialScreen = screenRevision, initialSaver = saverRevision;
        host.executeCommand("isScreenOn", Collections.emptyMap(), (ok, data, error) -> {
            synchronized (this) {
                if (!running || currentSession != session || screenRevision != initialScreen) return;
                if (!ok || !(data instanceof Boolean)) {
                    fault("Cannot read screen state: " + error, null);
                    return;
                }
                screenOn = Boolean.TRUE.equals(data);
                refreshSafely();
            }
        });
        host.executeCommand("isScreensaverActive", Collections.emptyMap(), (ok, data, error) -> {
            synchronized (this) {
                if (!running || currentSession != session || saverRevision != initialSaver) return;
                if (!ok || !(data instanceof Boolean)) {
                    fault("Cannot read screensaver state: " + error, null);
                    return;
                }
                screensaver = Boolean.TRUE.equals(data);
                refreshSafely();
            }
        });
    }

    @Override public synchronized void configure(Map<String, Object> settings) {
        if (!running) return;
        NotificationModel.Config previous = config;
        configError = null;
        // A runtime API failure is latched until restart; configuration errors are recoverable.
        try {
            config = new NotificationModel.Config(settings);
        } catch (IllegalArgumentException error) {
            config = null;
            configError = error.getMessage();
            host.log("Configuration: " + configError);
        }
        try {
            boolean requested = NotificationModel.bool(settings, "preview", false);
            if (requested && !previewSetting && config != null && runtimeError == null) {
                preview = true;
                main.removeCallbacks(endPreview);
                main.postDelayed(endPreview, PREVIEW_MS);
            } else if (!requested) {
                preview = false;
                main.removeCallbacks(endPreview);
            }
            previewSetting = requested;
            if (!screenOn) stopPreview();
            rotation.reset();
            if (shown && (config == null || !config.samePlacement(previous))) hide();
            refresh();
        } catch (RuntimeException | LinkageError error) {
            fault("Cannot apply notification settings", error);
        }
    }

    @Override public synchronized void execute(String command, Map<String, Object> arguments) {
        host.status("Live notifications has no content actions. Use its settings to configure it.", true);
    }

    @Override public synchronized void onEvent(String event, Map<String, Object> payload) {
        if (!running || runtimeError != null) return;
        try {
            switch (event) {
                case "ks.screen.state":
                    screenRevision++;
                    screenOn = Boolean.TRUE.equals(payload.get("on"));
                    if (!screenOn) stopPreview();
                    break;
                case "ks.screensaver.state":
                    saverRevision++;
                    screensaver = Boolean.TRUE.equals(payload.get("active"));
                    break;
                case "ks.screensaver.view":
                    // A null view also describes dim mode. Only screensaver.state decides eligibility.
                    break;
                case "settings.page":
                    if (Boolean.FALSE.equals(payload.get("open"))) stopPreview();
                    break;
                default:
                    if (!event.startsWith("ks.ha.entity.")) return;
                    String entity = event.substring("ks.ha.entity.".length());
                    if (!subscribed.contains(entity)) return;
                    snapshots.put(entity, new NotificationModel.Snapshot(payload));
                    break;
            }
            refresh();
        } catch (RuntimeException | LinkageError error) {
            fault("Cannot update live notifications", error);
        }
    }

    private void stopPreview() {
        if (!preview && !previewSetting) return;
        preview = previewSetting = false;
        main.removeCallbacks(endPreview);
        rotation.reset();
        host.updateSettings(Collections.singletonMap("preview", false));
    }

    private final Runnable endPreview = () -> {
        synchronized (this) {
            if (!running) return;
            try {
                stopPreview();
                refresh();
            } catch (RuntimeException | LinkageError error) {
                fault("Cannot end notification preview", error);
            }
        }
    };

    private final Runnable rotate = () -> {
        synchronized (this) {
            if (running) refreshSafely();
        }
    };

    private final Runnable render = () -> {
        synchronized (this) {
            if (!running || chip == null || config == null || runtimeError != null) return;
            try {
                chip.show(selected, config.motion, config.textScale);
            } catch (RuntimeException | LinkageError error) {
                fault("Cannot render the notification chip", error);
            }
        }
    };

    private void refreshSafely() {
        try {
            refresh();
        } catch (RuntimeException | LinkageError error) {
            fault("Cannot refresh live notifications", error);
        }
    }

    private void refresh() {
        main.removeCallbacks(rotate);
        boolean active = runtimeError == null && config != null && screenOn
            && (preview || (config.enabled && screensaver && !config.entries.isEmpty()));
        boolean watch = active && !preview;
        syncSources(watch ? config.sources : Collections.emptySet());
        if (!active) {
            selected = null;
            rotation.reset();
            hide();
        } else {
            List<NotificationModel.Item> items = preview ? NotificationModel.samples()
                : NotificationModel.eligible(config, snapshots, Locale.getDefault());
            selected = rotation.select(items, clock.now(), config.dwellMs);
            if (!shown) show();
            main.removeCallbacks(render);
            main.post(render);
            long deadline = rotation.nextDeadline();
            if (deadline != 0) main.postDelayed(rotate, Math.max(1, deadline - clock.now()));
        }
        publishStatus();
    }

    private void syncSources(Set<String> wanted) {
        for (String id : new ArrayList<>(subscribed)) {
            if (!wanted.contains(id)) {
                host.unsubscribe("ha.entity." + id);
                subscribed.remove(id);
                snapshots.remove(id);
            }
        }
        for (String id : wanted) {
            if (!subscribed.contains(id)) {
                host.subscribe("ha.entity." + id);
                subscribed.add(id);
            }
        }
    }

    private void show() {
        final int revision = ++overlayRevision;
        final boolean adaptiveWidth = config.autoWidth;
        final int textScale = config.textScale;
        OverlaySpec spec = OverlaySpec.at(anchor(config.anchor))
            .size(config.autoWidth ? OverlaySpec.WRAP : config.width, OverlaySpec.WRAP)
            .inset(12).offset(config.offsetX, config.offsetY)
            .onTop(true).touchable(false).closeOnBack(false);
        shown = true;
        host.showOverlay(OVERLAY, spec, new OverlayFactory() {
            @Override public View create(Context context, KsTheme theme) {
                synchronized (LiveNotificationsPlugin.this) {
                    NotificationChip created = new NotificationChip(context, theme, adaptiveWidth, textScale);
                    if (running && shown && overlayRevision == revision && runtimeError == null) {
                        chip = created;
                        created.show(selected, config.motion, config.textScale);
                    }
                    return created;
                }
            }

            @Override public void onThemeChanged(View view, KsTheme theme) {
                ((NotificationChip) view).restyle(theme);
            }

            @Override public void onDestroy(View view) {
                synchronized (LiveNotificationsPlugin.this) {
                    ((NotificationChip) view).dispose();
                    if (chip == view) chip = null;
                }
            }
        });
    }

    private void hide() {
        main.removeCallbacks(render);
        if (!shown) return;
        shown = false;
        overlayRevision++;
        chip = null;
        host.hideOverlay(OVERLAY);
    }

    private void publishStatus() {
        String message;
        boolean error = false;
        if (runtimeError != null || configError != null) {
            message = runtimeError != null ? runtimeError : configError;
            error = true;
        } else if (preview) {
            message = "Preview: sample data, not live Home Assistant values. Stops after two minutes or when settings close.";
        } else if (config == null || config.entries.isEmpty()) {
            message = "Add a notification, or enable the sample-data preview to check placement.";
        } else if (!config.enabled) {
            message = "Live overlay is off. Your screensaver settings are unchanged.";
        } else if (!screenOn || !screensaver) {
            message = "Waiting for an active screensaver and screen. Home Assistant subscriptions are paused.";
        } else {
            int problems = 0;
            String first = null;
            for (NotificationModel.Entry entry : config.entries) {
                String problem = entry.problem(snapshots);
                if (problem != null) {
                    problems++;
                    if (first == null) first = problem;
                }
            }
            error = problems > 0;
            message = error ? problems + " notification(s) have source problems. " + first
                : selected == null ? "No notifications currently match their visibility conditions."
                : "Showing eligible notifications in configured order (" + subscribed.size() + "/16 unique entities).";
        }
        if (!message.equals(lastStatus) || error != lastStatusError) {
            host.status(message, error);
            lastStatus = message;
            lastStatusError = error;
        }
    }

    private void fault(String message, Throwable error) {
        runtimeError = message + (error == null ? "" : ": " + error.getClass().getSimpleName()
            + (error.getMessage() == null ? "" : " - " + error.getMessage()))
            + ". Restart the plugin after checking the KS version and configuration.";
        main.removeCallbacks(rotate);
        main.removeCallbacks(endPreview);
        main.removeCallbacks(render);
        selected = null;
        preview = false;
        // Do not turn API failures into a success-shaped empty overlay.
        host.log(runtimeError);
        try {
            syncSources(Collections.emptySet());
        } catch (RuntimeException | LinkageError cleanupError) {
            host.log("Cannot release notification subscriptions: " + cleanupError.getClass().getSimpleName());
        }
        try {
            hide();
        } catch (RuntimeException | LinkageError cleanupError) {
            host.log("Cannot remove notification overlay: " + cleanupError.getClass().getSimpleName());
        }
        subscribed.clear();
        snapshots.clear();
        shown = false;
        chip = null;
        try {
            publishStatus();
        } catch (RuntimeException | LinkageError statusError) {
            // Host revocation can race a UI runnable; never escape into Android's main loop.
            host.log("Cannot publish notification failure status: " + statusError.getClass().getSimpleName());
        }
    }

    @Override public synchronized void stop() {
        running = false;
        session++;
        overlayRevision++;
        main.removeCallbacks(rotate);
        main.removeCallbacks(endPreview);
        main.removeCallbacks(render);
        NotificationChip previous = chip;
        chip = null;
        if (previous != null) main.post(previous::dispose);
        shown = preview = previewSetting = false;
        subscribed.clear();
        snapshots.clear();
        selected = null;
        rotation.reset();
        // KS has already revoked the host and removes its overlays/subscriptions.
    }

    static String anchor(String value) {
        switch (value) {
            case "Top left": return OverlaySpec.TOP_LEFT;
            case "Top": return OverlaySpec.TOP;
            case "Top right": return OverlaySpec.TOP_RIGHT;
            case "Left": return OverlaySpec.LEFT;
            case "Center": return OverlaySpec.CENTER;
            case "Right": return OverlaySpec.RIGHT;
            case "Bottom left": return OverlaySpec.BOTTOM_LEFT;
            case "Bottom right": return OverlaySpec.BOTTOM_RIGHT;
            default: return OverlaySpec.BOTTOM;
        }
    }
}
