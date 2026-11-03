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
        displayScopesAndVisibility();
        screensaverAppearance();
        appearanceReadRaces();
        appearanceFailuresAndLegacyHosts();
        stopAndRestart();
        System.out.println("ControllerTest: " + checkCount() + " lifecycle assertions passed");
    }

    private static void lifecycleAndReuse() throws Exception {
        Fixture f = new Fixture(Arrays.asList(row("sensor.a"), row("sensor.b"), row("sensor.a")));
        f.settings.put("displayScope", "Screensaver only");
        f.plugin.configure(f.settings);
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
        f.settings.put("displayScope", "Screensaver only");
        f.plugin.configure(f.settings);
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

    private static void displayScopesAndVisibility() throws Exception {
        Fixture f = new Fixture(Collections.singletonList(row("sensor.a")));
        f.host.complete("isScreensaverActive", false);
        f.host.complete("isScreenOn", true);
        f.scheduler.runReady();
        check(f.host.entities().size() == 1 && !f.host.spec.onTop(), "Always shows in ordinary dashboard layer");
        f.entity("sensor.a", "available", "fresh");
        f.plugin.onEvent("ks.screensaver.state", map("active", true));
        check(f.host.spec.onTop() && f.host.shows == 2 && f.host.hides == 1,
            "screensaver entry replaces only the overlay layer");
        check(selected(f.plugin).value.equals("fresh") && f.host.entities().size() == 1,
            "Always retains its live feed across the layer transition");
        f.plugin.onEvent("ks.screensaver.state", map("active", false));
        check(!f.host.spec.onTop() && f.host.shows == 3, "screensaver exit restores ordinary dashboard layer");
        f.settings.put("displayScope", "Screensaver only");
        f.plugin.configure(f.settings);
        check(f.host.entities().isEmpty() && selected(f.plugin) == null, "screensaver-only pauses on dashboard");
        f.plugin.onEvent("ks.screensaver.state", map("active", true));
        check(f.host.entities().size() == 1 && selected(f.plugin) == null, "scope entry waits for fresh values");
        f.entity("sensor.a", "available", "new");
        f.settings.put("displayScope", "Dashboard only");
        f.plugin.configure(f.settings);
        check(f.host.entities().isEmpty() && selected(f.plugin) == null, "dashboard-only pauses on screensaver");
        f.plugin.onEvent("ks.screensaver.state", map("active", false));
        check(f.host.entities().size() == 1 && !f.host.spec.onTop() && selected(f.plugin) == null,
            "dashboard-only resumes below native surfaces with no stale flash");
        f.entity("sensor.a", "available", "visible");
        f.plugin.visibilityChanged(false);
        f.scheduler.runReady();
        check(f.host.entities().isEmpty() && selected(f.plugin) == null && f.scheduler.pending() == 0,
            "backgrounding stops feeds, content and timers");
        f.plugin.visibilityChanged(true);
        check(f.host.entities().size() == 1 && selected(f.plugin) == null, "foreground resume waits for fresh values");
        f.settings.put("enabled", false);
        f.plugin.configure(f.settings);
        check(f.host.entities().isEmpty() && selected(f.plugin) == null, "global disabled blocks dashboard live mode");
        f.settings.put("displayScope", "Always");
        f.plugin.configure(f.settings);
        f.plugin.onEvent("ks.screensaver.state", map("active", true));
        check(f.host.entities().isEmpty(), "global disabled blocks Always and screensaver live mode");
        f.settings.put("preview", true);
        f.plugin.configure(f.settings);
        check(selected(f.plugin).label.startsWith("Preview - "), "explicit sample preview remains separate from live enable");
        f.plugin.visibilityChanged(false);
        check(selected(f.plugin) == null && Boolean.FALSE.equals(f.host.saved.get("preview")),
            "backgrounding stops and clears preview");
    }

    private static NotificationModel.Item selected(LiveNotificationsPlugin plugin) throws Exception {
        Field field = LiveNotificationsPlugin.class.getDeclaredField("selected");
        field.setAccessible(true);
        return (NotificationModel.Item) field.get(plugin);
    }

    private static ScreensaverAppearance appearance(LiveNotificationsPlugin plugin) throws Exception {
        java.lang.reflect.Method method = LiveNotificationsPlugin.class.getDeclaredMethod("effectiveAppearance");
        method.setAccessible(true);
        return (ScreensaverAppearance) method.invoke(plugin);
    }

    private static Map<String, Object> night(boolean hidden) {
        return map("view", "clock", "night", true, "textColor", 0xffff0000L,
            "backgroundColor", 0xff050607L, "cardColor", null, "widgetsHidden", hidden);
    }

    private static Map<String, Object> day(String view) {
        return map("view", view, "night", false, "textColor", null,
            "backgroundColor", null, "cardColor", null, "widgetsHidden", false);
    }

    private static void screensaverAppearance() throws Exception {
        Fixture f = new Fixture(Arrays.asList(row("sensor.a"), row("sensor.b")), false, true);
        check(f.host.subscriptions.contains("screensaver.appearance"), "advertised appearance event subscribed");
        check(f.host.appearanceSubscribedBeforeRead, "subscribe appearance before initial snapshot");
        f.host.complete("isScreenOn", true);
        f.host.complete("isScreensaverActive", true);
        check(f.host.shows == 0 && f.host.entities().isEmpty(), "no bright chip or HA feeds before initial night snapshot");
        f.host.complete("getScreensaverAppearance", night(false));
        check(f.host.shows == 1 && f.host.entities().size() == 2, "initial active night attaches through normal layer");
        check(appearance(f.plugin).night && appearance(f.plugin).textColor == 0xffff0000,
            "unsigned Number ARGB retains its full bits");
        f.entity("sensor.a", "available", "a");
        f.entity("sensor.b", "available", "b");
        f.scheduler.advance(5000);
        Map<String, Object> custom = night(false);
        custom.put("textColor", Integer.valueOf(0xffb84c17));
        custom.put("backgroundColor", 0xff121314L);
        custom.put("cardColor", 0xff202122L);
        f.plugin.onEvent("ks.screensaver.appearance", custom);
        check(appearance(f.plugin).textColor == 0xffb84c17 && appearance(f.plugin).cardColor == 0xff202122,
            "custom signed/unsigned night and Flip colors update");
        check(f.host.shows == 1 && selected(f.plugin).id == 0, "color-only change neither rebuilds nor interrupts current item");
        f.scheduler.advance(5000);
        check(selected(f.plugin).id == 1, "night recolor preserves dwell deadline");
        f.plugin.onEvent("ks.screensaver.appearance", day("clock"));
        check(!appearance(f.plugin).night && appearance(f.plugin).cardColor == null,
            "night exit or Now Playing takeover restores normal palette despite underlying clock view");
        check(f.host.shows == 1 && selected(f.plugin).id == 1, "Now Playing palette update keeps passive rotation and layer");
        f.plugin.onEvent("ks.screensaver.appearance", night(true));
        check(f.host.entities().isEmpty() && selected(f.plugin) == null && f.host.hides == 1,
            "clock hide-widgets preference hides overlay and releases sources");
        check(f.scheduler.pending() == 0 && !f.host.statusError && f.host.status.contains("hides widgets"),
            "hidden night is intentional paused status with no timers");
        f.settings.put("preview", true);
        f.plugin.configure(f.settings);
        check(selected(f.plugin) == null && Boolean.FALSE.equals(f.host.saved.get("preview")),
            "preview cannot override hide-widgets preference and is stopped");
        f.plugin.onEvent("ks.screensaver.appearance", night(false));
        check(f.host.shows == 2 && f.host.entities().size() == 2 && selected(f.plugin) == null,
            "unhiding resumes only with fresh subscribed data");
        f.entity("sensor.a", "available", "fresh");
        f.plugin.onEvent("ks.screensaver.state", map("active", false));
        check(!appearance(f.plugin).night && !f.host.spec.onTop(), "dashboard never inherits screensaver night tint");
        check(f.host.entities().size() == 2, "Always keeps live sources on dashboard");
        f.plugin.onEvent("ks.screensaver.appearance", night(true));
        check(!appearance(f.plugin).night && f.host.entities().size() == 2,
            "late appearance cannot hide or tint dashboard");
        f.plugin.onEvent("ks.screensaver.state", map("active", true));
        check(f.host.entities().isEmpty() && selected(f.plugin) == null, "new saver session cannot reuse prior night snapshot");
        f.host.complete("getScreensaverAppearance", day("slideshow"));
        check(!appearance(f.plugin).night && f.host.spec.onTop(), "other screensavers retain chosen material and upper layer");
        f.plugin.onEvent("ks.screensaver.view", map("view", null));
        f.host.complete("getScreensaverAppearance", day(null));
        check(f.host.entities().size() == 2 && f.host.spec.onTop(), "dim's null view remains eligible");
        f.plugin.onEvent("ks.screensaver.appearance", night(false));
        f.entity("sensor.a", "available", "night");
        f.plugin.visibilityChanged(false);
        check(f.host.entities().isEmpty() && f.scheduler.pending() == 0, "backgrounding during night releases live work");
        f.plugin.visibilityChanged(true);
        check(appearance(f.plugin).night && selected(f.plugin) == null,
            "foreground keeps current subscribed appearance but waits for fresh entity data");
        f.plugin.onEvent("ks.screen.state", map("on", false));
        check(f.host.entities().isEmpty() && f.scheduler.pending() == 0, "night screen-off leaves no live work");
        f.plugin.stop();
        check(!appearance(f.plugin).night, "stop clears the night snapshot");
    }

    private static void appearanceReadRaces() throws Exception {
        Fixture f = new Fixture(Collections.singletonList(row("sensor.a")), false, true);
        f.host.complete("isScreenOn", true);
        PluginHost.CommandCallback firstRead = f.host.callbacks.get("getScreensaverAppearance");
        f.host.complete("isScreensaverActive", true);
        PluginHost.CommandCallback newSessionRead = f.host.callbacks.get("getScreensaverAppearance");
        firstRead.onResult(true, day(null), null);
        check(f.host.shows == 0, "obsolete attach snapshot cannot override new screensaver session");
        f.plugin.onEvent("ks.screensaver.appearance", night(false));
        newSessionRead.onResult(true, day("clock"), null);
        check(appearance(f.plugin).night, "appearance event wins over older initial snapshot");
        f.plugin.onEvent("ks.screensaver.view", map("view", "slideshow"));
        check(f.host.entities().isEmpty() && !appearance(f.plugin).night,
            "view change cannot render stale clock colors while appearance is pending");
        PluginHost.CommandCallback slideRead = f.host.callbacks.get("getScreensaverAppearance");
        f.plugin.onEvent("ks.screensaver.appearance", day("slideshow"));
        f.entity("sensor.a", "available", "fresh");
        slideRead.onResult(false, null, "obsolete failed snapshot");
        check(!f.host.statusError && f.host.entities().size() == 1, "new event also supersedes stale read failure");
        f.plugin.onEvent("ks.screensaver.view", map("view", "clock"));
        PluginHost.CommandCallback stoppedRead = f.host.callbacks.get("getScreensaverAppearance");
        f.plugin.stop();
        int calls = f.host.hostCalls;
        stoppedRead.onResult(true, night(false), null);
        check(f.host.hostCalls == calls, "revoked session's appearance callback does no host work");
        f.plugin.start(f.host, f.settings);
        stoppedRead.onResult(true, night(true), null);
        check(!appearance(f.plugin).night, "previous start's callback cannot contaminate restarted plugin");
        f.host.complete("isScreenOn", true);
        f.host.complete("isScreensaverActive", true);
        f.host.complete("getScreensaverAppearance", night(false));
        check(appearance(f.plugin).night, "restart reads current appearance");

        TestScheduler scheduler = new TestScheduler();
        FakeHost host = new FakeHost();
        host.delayDiscovery = true;
        LiveNotificationsPlugin plugin = new LiveNotificationsPlugin(scheduler, () -> scheduler.now);
        plugin.start(host, settings(Collections.singletonList(row("sensor.a"))));
        host.complete("isScreenOn", true);
        host.complete("isScreensaverActive", true);
        check(host.shows == 0, "screen state reads cannot bypass pending capability discovery");
        host.complete("getHostApi", map("commands", Arrays.asList("getScreensaverAppearance"),
            "events", Arrays.asList("screensaver.appearance")));
        host.complete("getScreensaverAppearance", night(true));
        check(host.shows == 0 && host.entities().isEmpty(), "delayed discovery attaches directly into hidden night state");
        Fixture early = new Fixture(Collections.singletonList(row("sensor.a")), false, true);
        early.host.complete("getScreensaverAppearance", night(false));
        early.host.complete("isScreenOn", true);
        check(early.host.shows == 0, "early appearance snapshot cannot bypass unconfirmed screensaver state");
        early.host.complete("isScreensaverActive", true);
        check(early.host.shows == 0, "confirming a new active saver requires a current appearance snapshot");
        early.host.complete("getScreensaverAppearance", night(false));
        check(appearance(early.plugin).night && early.host.shows == 1, "early snapshot ordering never attaches in day colors");
    }

    private static void appearanceFailuresAndLegacyHosts() throws Exception {
        Fixture legacy = new Fixture(Collections.singletonList(row("sensor.a")));
        legacy.active();
        check(!legacy.host.callbacks.containsKey("getScreensaverAppearance")
            && !legacy.host.subscriptions.contains("screensaver.appearance") && legacy.host.logs.isEmpty(),
            "legacy host is not sent unsupported commands/subscriptions or warning spam");
        legacy.plugin.onEvent("ks.screensaver.appearance", night(true));
        check(legacy.host.entities().size() == 1 && !appearance(legacy.plugin).night, "unadvertised event ignored");
        Fixture failed = new Fixture(Collections.singletonList(row("sensor.a")), false, true);
        failed.host.complete("isScreenOn", true);
        failed.host.complete("isScreensaverActive", true);
        failed.host.callbacks.get("getScreensaverAppearance").onResult(false, null, "snapshot unavailable");
        check(failed.host.statusError && failed.host.status.contains("snapshot unavailable")
            && failed.host.shows == 0 && failed.host.entities().isEmpty(),
            "advertised API failure is explicit, not a success-shaped fallback");
        Fixture bad = new Fixture(Collections.singletonList(row("sensor.a")), false, true);
        bad.active();
        Map<String, Object> malformed = night(false);
        malformed.put("textColor", "red");
        bad.plugin.onEvent("ks.screensaver.appearance", malformed);
        check(bad.host.statusError && bad.host.status.contains("numeric night ARGB")
            && bad.host.entities().isEmpty(), "malformed event fails closed with actionable status");
        TestScheduler scheduler = new TestScheduler();
        FakeHost host = new FakeHost();
        host.delayDiscovery = true;
        LiveNotificationsPlugin plugin = new LiveNotificationsPlugin(scheduler, () -> scheduler.now);
        plugin.start(host, settings(Collections.singletonList(row("sensor.a"))));
        host.callbacks.get("getHostApi").onResult(false, null, "discovery failed");
        check(host.statusError && host.status.contains("discovery failed"), "discovery failure is visible");
        Fixture rejected = new Fixture(Collections.singletonList(row("sensor.a")), false, true);
        rejected.host.rejectCommand = "getScreensaverAppearance";
        rejected.host.complete("isScreensaverActive", true);
        check(rejected.host.statusError && rejected.host.status.contains("Test command refused"),
            "synchronous appearance dispatch failure in initial saver callback is caught");
        FakeHost stoppedHost = new FakeHost();
        stoppedHost.delayDiscovery = true;
        LiveNotificationsPlugin stopped = new LiveNotificationsPlugin(scheduler, () -> scheduler.now);
        stopped.start(stoppedHost, settings(Collections.singletonList(row("sensor.a"))));
        PluginHost.CommandCallback discovery = stoppedHost.callbacks.get("getHostApi");
        stopped.stop();
        int calls = stoppedHost.hostCalls;
        discovery.onResult(true, map("commands", Arrays.asList("getScreensaverAppearance"),
            "events", Arrays.asList("screensaver.appearance")), null);
        check(stoppedHost.hostCalls == calls, "late discovery cannot subscribe or read a revoked host");
        FakeHost partial = new FakeHost();
        partial.delayDiscovery = true;
        LiveNotificationsPlugin partialPlugin = new LiveNotificationsPlugin(scheduler, () -> scheduler.now);
        partialPlugin.start(partial, settings(Collections.singletonList(row("sensor.a"))));
        partial.complete("getHostApi", map("commands", Arrays.asList("getScreensaverAppearance"), "events", Collections.emptyList()));
        check(!partial.callbacks.containsKey("getScreensaverAppearance")
            && !partial.subscriptions.contains("screensaver.appearance"), "both advertised names are required");
    }

    private static final class Fixture {
        final TestScheduler scheduler = new TestScheduler();
        final FakeHost host;
        final LiveNotificationsPlugin plugin = new LiveNotificationsPlugin(scheduler, () -> scheduler.now);
        final Map<String, Object> settings;

        Fixture(List<Map<String, Object>> rows) {
            this(rows, false);
        }

        Fixture(List<Map<String, Object>> rows, boolean preview) {
            this(rows, preview, false);
        }

        Fixture(List<Map<String, Object>> rows, boolean preview, boolean supportsAppearance) {
            host = new FakeHost();
            host.supportsAppearance = supportsAppearance;
            settings = settings(rows);
            settings.put("preview", preview);
            plugin.start(host, settings);
            scheduler.runReady();
        }

        void active() {
            host.complete("isScreenOn", true);
            host.complete("isScreensaverActive", true);
            if (host.supportsAppearance) host.complete("getScreensaverAppearance", day("clock"));
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
        String status, rejectSubscription, rejectCommand;
        boolean statusError;
        boolean supportsAppearance, delayDiscovery, appearanceSubscribedBeforeRead;
        final List<String> logs = new ArrayList<>();

        public void executeCommand(String command, Map<String, Object> args, CommandCallback callback) {
            hostCalls++;
            if (command.equals(rejectCommand)) throw new IllegalStateException("Test command refused");
            callbacks.put(command, callback);
            if (command.equals("getHostApi") && !delayDiscovery) {
                callback.onResult(true, map("commands", supportsAppearance
                    ? Arrays.asList("getScreensaverAppearance") : Collections.emptyList(),
                    "events", supportsAppearance ? Arrays.asList("screensaver.appearance") : Collections.emptyList()), null);
            }
            if (command.equals("getScreensaverAppearance")) {
                appearanceSubscribedBeforeRead = subscriptions.contains("screensaver.appearance");
            }
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
        public void log(String message) { logs.add(message); }
        Set<String> entities() {
            Set<String> result = new LinkedHashSet<>();
            for (String value : subscriptions) if (value.startsWith("ha.entity.")) result.add(value);
            return result;
        }
    }
}
