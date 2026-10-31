// SPDX-License-Identifier: Apache-2.0
package com.shymoose.livenotifications;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

import static com.shymoose.livenotifications.CoreTest.*;

/** Real controller with the official SDK, a deterministic clock and a non-rendering host. */
public final class ControllerTest {
    public static void main(String[] args) throws Exception {
        lifecycleAndReuse();
        initialReadRaces();
        previewLifetime();
        invalidConfigurationAndFailures();
        progressSubscriptions();
        stopAndRestart();
        System.out.println("ControllerTest: " + checkCount() + " lifecycle assertions passed");
    }

    private static void lifecycleAndReuse() throws Exception {
        Fixture f = new Fixture(Arrays.asList(row("sensor.a"), row("sensor.b"), row("sensor.a")));
        check(f.host.entities().isEmpty() && f.host.shows == 0, "inactive startup has no entity feed or overlay");
        check(f.host.subscriptions.containsAll(Arrays.asList("screen.state", "screensaver.state", "screensaver.view")),
            "fixed passive lifecycle subscriptions");
        f.host.complete("isScreensaverActive", true);
        check(f.host.entities().isEmpty(), "wait for confirmed screen state");
        f.host.complete("isScreenOn", true);
        check(f.host.entities().size() == 2, "deduplicated entity subscriptions after both reads");
        check(f.host.shows == 1, "one overlay created for active period");
        check(f.host.spec.onTop() && !f.host.spec.touchable() && !f.host.spec.closeOnBack(), "purely passive overlay");
        check(f.host.spec.width() == OverlaySpec.WRAP && f.host.spec.height() == OverlaySpec.WRAP && f.host.spec.offsetY() == -32,
            "placement defaults reach SDK");
        check(selected(f.plugin) == null, "wait for fresh entity snapshots");
        f.entity("sensor.a", "available", "one");
        check(selected(f.plugin).id == 0, "first row selected");
        f.entity("sensor.b", "available", "two");
        check(selected(f.plugin).id == 0, "new entry does not interrupt");
        f.scheduler.advance(9999);
        check(selected(f.plugin).id == 0, "full dwell");
        f.entity("sensor.a", "available", "changed");
        check(selected(f.plugin).value.equals("changed"), "value updates without interrupting");
        f.scheduler.advance(1);
        check(selected(f.plugin).id == 1, "update did not reset deadline");
        f.entity("sensor.b", "disconnected", null);
        check(selected(f.plugin).id == 2, "current invalidation immediately advances by row order");
        check(f.host.statusError && f.host.status.contains("disconnected"), "connection fault visible in status");
        f.entity("sensor.a", "unavailable", null);
        check(selected(f.plugin) == null && f.host.shows == 1 && f.host.hides == 0,
            "zero eligible items clears content without rebuilding");
        check(f.scheduler.pending() == 0, "zero eligible items has no rotation timer after pending render drains");
        f.entity("sensor.a", "available", "fresh");
        check(f.host.shows == 1 && selected(f.plugin) != null, "availability recovery reuses overlay");
        f.plugin.onEvent("ks.screensaver.view", map("view", null));
        check(f.host.shows == 1, "null saver view must not hide dim-mode overlay");
        f.plugin.onEvent("ks.screen.state", map("on", false));
        check(f.host.entities().isEmpty() && f.host.hides == 1, "screen off releases entity feeds and overlay");
        check(f.scheduler.pending() == 0, "screen off has no timers");
        f.entity("sensor.a", "available", "late old event");
        check(selected(f.plugin) == null, "unsubscribed late event ignored");
        f.plugin.onEvent("ks.screen.state", map("on", true));
        check(f.host.shows == 2 && selected(f.plugin) == null, "wake waits for fresh values instead of reusing stale data");
        f.plugin.onEvent("ks.screensaver.state", map("active", false));
        check(f.host.entities().isEmpty() && f.host.hides == 2, "saver exit tears down active work");
    }

    private static void initialReadRaces() throws Exception {
        Fixture f = new Fixture(Collections.singletonList(row("sensor.a")));
        f.plugin.onEvent("ks.screen.state", map("on", true));
        f.plugin.onEvent("ks.screensaver.state", map("active", true));
        f.host.complete("isScreenOn", false);
        f.host.complete("isScreensaverActive", false);
        check(f.host.entities().size() == 1 && f.host.shows == 1, "events win over older initial reads");
        f.entity("sensor.a", "available", "a");
        f.settings.put("offsetX", 100);
        f.settings.put("width", 400);
        f.settings.put("widthMode", "Custom");
        f.plugin.configure(f.settings);
        check(f.host.spec.offsetX() == 100 && f.host.spec.width() == 400 && f.host.shows == 2,
            "placement changes rebuild exactly once");
        int shows = f.host.shows;
        f.settings.put("motion", "None");
        f.plugin.configure(f.settings);
        check(f.host.shows == shows, "motion changes do not rebuild container");
        f.settings.put("entries", Collections.singletonList(row("sensor.new")));
        f.plugin.configure(f.settings);
        check(f.host.entities().equals(Collections.singleton("ha.entity.sensor.new")), "configure removes unused sources");
        check(selected(f.plugin) == null, "changed entity waits for fresh state");
    }

    private static void previewLifetime() throws Exception {
        Fixture f = new Fixture(Collections.emptyList());
        f.host.complete("isScreenOn", true);
        f.host.complete("isScreensaverActive", false);
        f.settings.put("preview", true);
        f.plugin.configure(f.settings);
        check(selected(f.plugin).label.startsWith("Preview - "), "preview independent of configured entities and conditions");
        check(f.host.entities().isEmpty() && f.host.shows == 1, "preview never subscribes to HA");
        f.scheduler.advance(10_000);
        check(selected(f.plugin).id == 1, "sample preview actually rotates");
        f.plugin.onEvent("settings.page", map("open", false));
        f.scheduler.runReady();
        check(selected(f.plugin) == null && Boolean.FALSE.equals(f.host.saved.get("preview")), "closing settings ends and persists preview off");
        check(f.scheduler.pending() == 0, "closing preview cancels all timers");
        f.plugin.configure(f.settings);
        f.scheduler.advance(LiveNotificationsPlugin.PREVIEW_MS);
        check(selected(f.plugin) == null && f.scheduler.pending() == 0, "preview expires after exactly two minutes");
        f.plugin.configure(f.settings);
        f.plugin.onEvent("ks.screen.state", map("on", false));
        check(selected(f.plugin) == null && f.scheduler.pending() == 0, "screen off stops preview immediately");
        f.plugin.configure(f.settings);
        check(selected(f.plugin) == null, "preview cannot begin with screen off");
        Fixture persisted = new Fixture(Collections.emptyList(), true);
        persisted.host.complete("isScreenOn", true);
        persisted.host.complete("isScreensaverActive", false);
        check(selected(persisted.plugin) == null && Boolean.FALSE.equals(persisted.host.saved.get("preview")),
            "persisted preview never restarts after enabling or app restart");

        Fixture live = new Fixture(Collections.singletonList(row("sensor.a")));
        live.active();
        live.entity("sensor.a", "available", "real");
        live.settings.put("preview", true);
        live.plugin.configure(live.settings);
        check(live.host.entities().isEmpty() && selected(live.plugin).label.startsWith("Preview - "), "preview suspends live values");
        live.settings.put("preview", false);
        live.plugin.configure(live.settings);
        check(live.host.entities().size() == 1 && selected(live.plugin) == null, "preview exit resubscribes and waits for fresh data");
    }

    private static void invalidConfigurationAndFailures() throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 17; i++) rows.add(row("sensor.a" + i));
        Fixture invalid = new Fixture(rows);
        invalid.active();
        check(invalid.host.entities().isEmpty() && invalid.host.shows == 0, "over-limit config subscribes to nothing");
        check(invalid.host.statusError && invalid.host.status.contains("17 unique"), "limit has actionable status");
        invalid.settings.put("entries", Collections.singletonList(row("sensor.ok")));
        invalid.plugin.configure(invalid.settings);
        check(invalid.host.entities().size() == 1 && invalid.host.shows == 1, "configuration errors recover when corrected");
        Fixture failed = new Fixture(Arrays.asList(row("sensor.a"), row("sensor.b")));
        failed.host.rejectSubscription = "ha.entity.sensor.b";
        failed.active();
        check(failed.host.entities().isEmpty(), "partial subscription failure rolls back");
        check(failed.host.statusError && failed.host.status.contains("Restart"), "runtime API failure reports recovery");
        check(failed.scheduler.pending() == 0, "runtime failure leaves no timers");
        Fixture badRead = new Fixture(Collections.singletonList(row("sensor.a")));
        badRead.host.callbacks.get("isScreenOn").onResult(false, null, "Read failed");
        check(badRead.host.statusError && badRead.host.status.contains("Read failed"), "failed initial state cannot masquerade as idle");
    }

    private static void stopAndRestart() throws Exception {
        Fixture f = new Fixture(Arrays.asList(row("sensor.a"), row("sensor.b")));
        f.active();
        f.entity("sensor.a", "available", "a");
        f.entity("sensor.b", "available", "b");
        int calls = f.host.hostCalls;
        f.plugin.stop();
        f.scheduler.advance(200_000);
        check(f.scheduler.pending() == 0, "stop cancels every owned callback");
        check(f.host.hostCalls == calls, "stop never uses revoked host");
        Fixture pending = new Fixture(Collections.singletonList(row("sensor.a")));
        FakeHost newer = new FakeHost();
        pending.plugin.stop();
        pending.plugin.start(newer, pending.settings);
        pending.host.complete("isScreenOn", true);
        pending.host.complete("isScreensaverActive", true);
        check(newer.shows == 0, "old-session callbacks ignored after restart");
    }

    private static void progressSubscriptions() throws Exception {
        Map<String, Object> row = row("sensor.activity");
        row.put("progressMode", "On");
        row.put("progressEntity", "sensor.progress");
        row.put("liveIndicator", true);
        Fixture f = new Fixture(Collections.singletonList(row));
        f.active();
        f.entity("sensor.activity", "available", "24 min remaining");
        f.entity("sensor.progress", "available", "65");
        check(f.host.entities().size() == 2 && selected(f.plugin).progress == 65, "controller subscribes to independent progress");
        check(selected(f.plugin).liveIndicator && !f.host.statusError, "valid progress enables live cue");
        f.entity("sensor.progress", "disconnected", "65");
        check(selected(f.plugin).progress == null && !selected(f.plugin).liveIndicator && f.host.statusError,
            "progress disconnect clears fill/light and reports fault without hiding readable value");
        row.put("progressMode", "Off");
        f.plugin.configure(f.settings);
        f.scheduler.runReady();
        check(f.host.entities().size() == 1 && !f.host.statusError, "Off releases extra subscription and clears source warning");
        f.plugin.onEvent("ks.screen.state", map("on", false));
        check(f.host.entities().isEmpty() && selected(f.plugin) == null, "screen off clears live cue model and sources");
    }

    private static NotificationModel.Item selected(LiveNotificationsPlugin plugin) throws Exception {
        Field field = LiveNotificationsPlugin.class.getDeclaredField("selected");
        field.setAccessible(true);
        return (NotificationModel.Item) field.get(plugin);
    }

    private static final class Fixture {
        final TestScheduler scheduler = new TestScheduler();
        final FakeHost host = new FakeHost();
        final LiveNotificationsPlugin plugin = new LiveNotificationsPlugin(scheduler, () -> scheduler.now);
        final Map<String, Object> settings;

        Fixture(List<Map<String, Object>> rows) {
            this(rows, false);
        }

        Fixture(List<Map<String, Object>> rows, boolean preview) {
            settings = settings(rows);
            settings.put("preview", preview);
            plugin.start(host, settings);
            scheduler.runReady();
        }

        void active() {
            host.complete("isScreenOn", true);
            host.complete("isScreensaverActive", true);
            scheduler.runReady();
        }

        void entity(String id, String status, String value) {
            plugin.onEvent("ks.ha.entity." + id, map("status", status, "state", value, "attributes", map()));
            scheduler.runReady();
        }
    }

    private static final class TestScheduler implements LiveNotificationsPlugin.Scheduler {
        final Map<Runnable, Long> tasks = new LinkedHashMap<>();
        long now;
        public void post(Runnable runnable) { tasks.put(runnable, now); }
        public void postDelayed(Runnable runnable, long delay) { tasks.put(runnable, now + delay); }
        public void removeCallbacks(Runnable runnable) { tasks.remove(runnable); }
        int pending() { runReady(); return tasks.size(); }
        void advance(long delta) { now += delta; runReady(); }
        void runReady() {
            for (int guard = 0; guard < 100; guard++) {
                Runnable ready = null;
                for (Map.Entry<Runnable, Long> entry : tasks.entrySet()) {
                    if (entry.getValue() <= now) { ready = entry.getKey(); break; }
                }
                if (ready == null) return;
                tasks.remove(ready);
                ready.run();
            }
            throw new AssertionError("Unbounded scheduling loop");
        }
    }

    private static final class FakeHost implements PluginHost {
        final Set<String> subscriptions = new LinkedHashSet<>();
        final Map<String, CommandCallback> callbacks = new LinkedHashMap<>();
        final Map<String, Object> saved = new LinkedHashMap<>();
        OverlaySpec spec;
        int shows, hides, hostCalls;
        String status, rejectSubscription;
        boolean statusError;

        public void executeCommand(String command, Map<String, Object> args, CommandCallback callback) {
            hostCalls++;
            callbacks.put(command, callback);
        }
        void complete(String command, Object data) { callbacks.get(command).onResult(true, data, null); }
        public void subscribe(String event) {
            hostCalls++;
            if (event.equals(rejectSubscription)) throw new IllegalStateException("Test subscription refused");
            subscriptions.add(event);
        }
        public void unsubscribe(String event) { hostCalls++; subscriptions.remove(event); }
        public void showOverlay(String key, OverlaySpec spec, OverlayFactory factory) { hostCalls++; shows++; this.spec = spec; }
        public void hideOverlay(String key) { hostCalls++; hides++; }
        public void status(String message, boolean error) { hostCalls++; status = message; statusError = error; }
        public void updateSettings(Map<String, Object> values) { hostCalls++; saved.putAll(values); }
        public void showWindow(String title, String message, String button) { throw new AssertionError("No floating windows"); }
        public void hideWindow() { throw new AssertionError("No floating windows"); }
        public void log(String message) {}
        Set<String> entities() {
            Set<String> result = new LinkedHashSet<>();
            for (String value : subscriptions) if (value.startsWith("ha.entity.")) result.add(value);
            return result;
        }
    }
}
