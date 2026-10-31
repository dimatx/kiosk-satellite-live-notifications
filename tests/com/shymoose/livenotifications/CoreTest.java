// SPDX-License-Identifier: Apache-2.0
package com.shymoose.livenotifications;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CoreTest {
    private static int checks;

    public static void main(String[] args) {
        defaultsAndLimits();
        predicatesAndAvailability();
        overridesAndFormatting();
        percentageProgress();
        configurableProgressAndIndicator();
        optionalPercentageText();
        orderedRotation();
        System.out.println("CoreTest: " + checks + " assertions passed");
    }

    private static void defaultsAndLimits() {
        NotificationModel.Config empty = config(Collections.emptyList());
        check(empty.entries.isEmpty() && empty.sources.isEmpty(), "empty config");
        check(empty.dwellMs == 10_000 && empty.width == 320 && empty.motion, "design defaults");
        check(empty.autoWidth && empty.textScale == 100, "adaptive width and neutral default scale");
        check(new NotificationModel.Config(map("textScale", 120)).textScale == 120,
            "explicit saved device scale is preserved");
        check(!new NotificationModel.Config(map("motion", "None")).motion,
            "saved None still disables transition and indicator motion");
        check(NotificationModel.defaultWidth(true) == 480, "wider landscape default");
        check(NotificationModel.defaultWidth(false) == 320, "compact portrait default");
        Map<String, Object> customWidth = map("widthMode", "Custom", "width", 400, "textScale", 150);
        NotificationModel.Config custom = new NotificationModel.Config(customWidth);
        check(!custom.autoWidth && custom.width == 400 && custom.textScale == 150, "explicit width and type size preserved");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 32; i++) rows.add(row("sensor.shared"));
        check(config(rows).sources.size() == 1, "duplicate sources share one subscription");
        rows.clear();
        for (int i = 0; i < 16; i++) rows.add(row("sensor.value_" + i));
        check(config(rows).sources.size() == 16, "exact limit allowed");
        rows.add(row("sensor.seventeenth"));
        rejects(() -> config(rows), "17 unique entities must fail, not truncate");
        rows.get(16).put("enabled", false);
        check(config(rows).entries.size() == 16, "disabled rows do not consume subscriptions");
        Map<String, Object> advanced = row("sensor.primary");
        advanced.put("advanced", true);
        advanced.put("iconEntity", "sensor.icon");
        advanced.put("valueEntity", "sensor.value");
        advanced.put("conditionEntity", "sensor.condition");
        check(config(Collections.singletonList(advanced)).sources.size() == 4, "overrides count");
        advanced.put("advanced", false);
        advanced.put("valueEntity", "invalid but hidden");
        check(config(Collections.singletonList(advanced)).sources.size() == 1, "hidden overrides ignored");
        rejects(() -> config(Collections.singletonList(row("bad_entity"))), "invalid entity rejected");
        Map<String, Object> malformed = settings(Collections.singletonList(row("sensor.a")));
        malformed.put("dwell", Double.NaN);
        rejects(() -> new NotificationModel.Config(malformed), "NaN dwell rejected");
        malformed.put("dwell", 2);
        rejects(() -> new NotificationModel.Config(malformed), "dwell threshold enforced");
        malformed.put("dwell", 3);
        check(new NotificationModel.Config(malformed).dwellMs == 3000, "minimum dwell accepted");
        malformed.put("dwell", 120);
        check(new NotificationModel.Config(malformed).dwellMs == 120_000, "maximum dwell accepted");
        malformed.put("dwell", 120.5);
        rejects(() -> new NotificationModel.Config(malformed), "fractional dwell rejected");
    }

    private static void predicatesAndAvailability() {
        for (String status : new String[]{"unknown", "unavailable", "missing", "not_configured",
            "connecting", "disconnected", "too_large"}) {
            Map<String, NotificationModel.Snapshot> states = states("sensor.a", snapshot(status, "19", map()));
            Map<String, Object> row = row("sensor.a");
            row.put("rule", "Does not equal");
            row.put("operand", "on");
            NotificationModel.Config config = config(Collections.singletonList(row));
            check(NotificationModel.eligible(config, states, Locale.US).isEmpty(), status + " never matches inequality");
            check(config.entries.get(0).problem(states) != null, status + " is diagnosed");
        }
        for (String input : new String[]{"unknown", "unavailable", "", "  "}) {
            check(!NotificationModel.matches("Does not equal", input, "on", null), "invalid scalar fails closed");
        }
        Map<String, Object> row = row("sensor.a");
        Map<String, NotificationModel.Snapshot> states = states("sensor.a", snapshot("available", "20.5", map()));
        String[] rules = {"Equals", "Does not equal", "Greater than", "At least", "Less than", "At most"};
        String[] operands = {"20.5", "19", "20", "20.5", "21", "20.5"};
        for (int i = 0; i < rules.length; i++) {
            row.put("rule", rules[i]);
            row.put("operand", operands[i]);
            check(NotificationModel.eligible(config(Collections.singletonList(row)), states, Locale.US).size() == 1,
                rules[i] + " matches explicitly");
        }
        row.put("rule", "Greater than");
        row.put("operand", "20.5");
        check(NotificationModel.eligible(config(Collections.singletonList(row)), states, Locale.US).isEmpty(),
            "strict greater than excludes equality");
        row.put("operand", "NaN");
        rejects(() -> config(Collections.singletonList(row)), "non-finite operand rejected");
        row.put("operand", "25,5");
        rejects(() -> config(Collections.singletonList(row)), "ambiguous locale operand rejected");
        row.put("operand", "20");
        states.put("sensor.a", snapshot("available", "not_numeric", map()));
        NotificationModel.Config config = config(Collections.singletonList(row));
        check(NotificationModel.eligible(config, states, Locale.US).isEmpty(), "nonnumeric input fails closed");
        check(config.entries.get(0).problem(states).contains("not a finite number"), "bad numeric input diagnosed");
        states.put("sensor.a", snapshot("available", "2.5e1", map()));
        check(config.entries.get(0).evaluate(states, Locale.US) != null, "HA scientific numeric states compare correctly");
        row.put("operand", "2e1");
        rejects(() -> config(Collections.singletonList(row)), "comparison settings require a plain number");
        row.put("rule", "Equals");
        row.put("operand", "ON");
        states.put("sensor.a", snapshot("available", "on", map()));
        check(NotificationModel.eligible(config(Collections.singletonList(row)), states, Locale.US).isEmpty(),
            "state equality is case sensitive");
    }

    private static void overridesAndFormatting() {
        Map<String, Object> row = row("sensor.primary");
        row.put("advanced", true);
        row.put("valueEntity", "sensor.value");
        row.put("valueAttribute", "remaining");
        row.put("iconEntity", "sensor.icon");
        row.put("conditionEntity", "binary_sensor.ready");
        row.put("rule", "Equals");
        row.put("operand", "on");
        NotificationModel.Config config = config(Collections.singletonList(row));
        Map<String, NotificationModel.Snapshot> states = states("sensor.primary",
            snapshot("available", "idle", map("friendly_name", "Laundry")));
        states.put("sensor.value", snapshot("available", "running", map("remaining", 12)));
        states.put("sensor.icon", snapshot("available", "on", map("icon", "mdi:washing-machine")));
        states.put("binary_sensor.ready", snapshot("available", "on", map()));
        NotificationModel.Item item = NotificationModel.eligible(config, states, Locale.US).get(0);
        check(item.label.equals("Laundry") && item.value.equals("12"), "independent label and value");
        check(item.icon.equals("mdi:washing-machine"), "independent icon source");
        row.put("label", "Custom");
        check(config(Collections.singletonList(row)).entries.get(0).evaluate(states, Locale.US).label.equals("Custom"),
            "custom label");
        states.put("sensor.value", snapshot("available", "running", map("remaining", Collections.singletonMap("x", 1))));
        check(config.entries.get(0).evaluate(states, Locale.US) == null, "object attribute not rendered");
        check(config.entries.get(0).problem(states).contains("attribute"), "attribute error diagnosed");
        states.put("sensor.value", snapshot("available", "running", map()));
        check(config.entries.get(0).evaluate(states, Locale.US) == null, "missing attribute fails closed");
        states.put("sensor.value", snapshot("available", "running", map("remaining", 12)));
        states.put("binary_sensor.ready", snapshot("disconnected", null, map()));
        check(config.entries.get(0).evaluate(states, Locale.US) == null, "condition disconnect clears chip");
        row.put("rule", "Available");
        row.put("conditionAttribute", "ready");
        states.put("binary_sensor.ready", snapshot("available", "on", map("ready", false)));
        check(config(Collections.singletonList(row)).entries.get(0).evaluate(states, Locale.US) != null,
            "Available accepts false scalar, not only truthy values");
        row.put("conditionAttribute", "tiny");
        row.put("rule", "Less than");
        row.put("operand", "0.001");
        states.put("binary_sensor.ready", snapshot("available", "on", map("tiny", 0.0000001)));
        check(config(Collections.singletonList(row)).entries.get(0).evaluate(states, Locale.US) != null,
            "numeric attributes serialized in exponent notation compare correctly");
        row.put("conditionAttribute", "");
        states.put("sensor.primary", snapshot("disconnected", "stale", map("friendly_name", "Stale")));
        check(config.entries.get(0).evaluate(states, Locale.US) == null, "primary disconnect never displays stale metadata");

        row = row("sensor.temp");
        config = config(Collections.singletonList(row));
        states = states("sensor.temp", snapshot("available", "22.50",
            map("unit_of_measurement", "\u00b0C", "friendly_name", "Room")));
        check(config.entries.get(0).evaluate(states, Locale.US).value.equals("22.5 \u00b0C"), "numeric state and unit");
        check(config.entries.get(0).evaluate(states, Locale.GERMANY).value.equals("22,5 \u00b0C"), "locale number format");
        states.put("sensor.temp", snapshot("available", "0.0000000000001", map()));
        check(!config.entries.get(0).evaluate(states, Locale.US).value.equals("0"), "tiny value does not round to zero");
        states.put("sensor.temp", snapshot("available", "on", map("device_class", "door")));
        check(config.entries.get(0).evaluate(states, Locale.US).value.equals("Open"), "door state formatting");
        states.put("sensor.temp", snapshot("available", "off", map("device_class", "motion")));
        check(config.entries.get(0).evaluate(states, Locale.US).value.equals("Clear"), "motion state formatting");
        check(NotificationModel.clean("a\nb\u202ec").equals("a b c"), "control and bidi characters removed");
        check(NotificationModel.clean(String.join("", Collections.nCopies(300, "x"))).length() == 160, "bounded text");
        check(NotificationModel.samples().stream().allMatch(sample -> sample.label.startsWith("Preview - ")),
            "sample content is clearly distinguished");
    }

    private static void orderedRotation() {
        Rotation rotation = new Rotation();
        NotificationModel.Item a = new NotificationModel.Item(0, "A", "1", "");
        NotificationModel.Item b = new NotificationModel.Item(1, "B", "2", "");
        NotificationModel.Item c = new NotificationModel.Item(2, "C", "3", "");
        List<NotificationModel.Item> all = Arrays.asList(a, b, c);
        check(rotation.select(Collections.emptyList(), 0, 10_000) == null, "zero hides");
        check(rotation.nextDeadline() == 0, "zero has no timer");
        check(rotation.select(Collections.singletonList(a), 0, 10_000) == a, "one shows");
        check(rotation.select(Collections.singletonList(a), 60_000, 10_000) == a, "one remains still");
        check(rotation.nextDeadline() == 0, "one has no rotation timer");
        check(rotation.select(all, 60_001, 10_000) == a, "new eligibility does not interrupt");
        check(rotation.nextDeadline() == 70_001, "new peers get a full dwell");
        check(rotation.select(all, 70_000, 10_000) == a, "no early rotation");
        check(rotation.select(all, 70_001, 10_000) == b, "rotates at exact deadline");
        check(rotation.select(Arrays.asList(a, c), 70_002, 10_000) == c, "cleared current advances promptly in order");
        check(rotation.select(Arrays.asList(a, c), 80_002, 10_000) == a, "wraps predictably");
        long deadline = rotation.nextDeadline();
        NotificationModel.Item changedA = new NotificationModel.Item(0, "A", "new", "");
        check(rotation.select(Arrays.asList(changedA, c), 80_100, 10_000) == changedA, "value updates immediately");
        check(rotation.nextDeadline() == deadline, "value update preserves dwell");
        check(rotation.select(Collections.emptyList(), 80_101, 10_000) == null, "last clearing hides");
        check(rotation.select(all, 90_000, 10_000) == a, "fresh cycle restarts at configured first");
    }

    private static void percentageProgress() {
        check(NotificationModel.progress("68", "%") == 68, "HA percent unit");
        check(NotificationModel.progress("68.5", "%") == 68.5, "fractional progress");
        check(NotificationModel.progress("0", "%") == 0, "zero progress is valid");
        check(NotificationModel.progress("100", "%") == 100, "complete progress is valid");
        check(NotificationModel.progress("42%", null) == 42, "percent-formatted attribute");
        check(NotificationModel.progress("Cleaning - 42% complete", null) == 42, "single embedded percent token");
        for (String value : new String[]{"-1%", "101%", "unknown", "NaN%", "1e9999%", "1.2.3%", "40% battery 60%"}) {
            check(NotificationModel.progress(value, null) == null, "invalid or ambiguous percent: " + value);
        }
        check(NotificationModel.progress("101", "%") == null, "out-of-range HA percent is not clamped");
        check(NotificationModel.progress("-1", "%") == null, "negative HA percent rejected");
        check(NotificationModel.progress("12", "min") == null, "ETA minutes are not progress");
        check(NotificationModel.progress("42", null) == null, "arbitrary number is not progress");
        Map<String, Object> row = row("input_text.demo");
        row.put("advanced", true);
        row.put("valueEntity", "input_number.percent");
        NotificationModel.Config config = config(Collections.singletonList(row));
        Map<String, NotificationModel.Snapshot> states = states("input_text.demo", snapshot("available", "Drying", map()));
        states.put("input_number.percent", snapshot("available", "68", map("unit_of_measurement", "%")));
        check(config.entries.get(0).evaluate(states, Locale.US).progress == 68, "separate value entity supplies progress");
        row.put("valueAttribute", "completion");
        states.put("input_number.percent", snapshot("available", "68", map("completion", "42%")));
        check(config(Collections.singletonList(row)).entries.get(0).evaluate(states, Locale.US).progress == 42,
            "percent-formatted value attribute supplies progress");
        states.put("input_number.percent", snapshot("unavailable", "68", map("completion", "42%")));
        check(config.entries.get(0).evaluate(states, Locale.US) == null, "unavailable sources never retain progress");
    }

    private static void configurableProgressAndIndicator() {
        Map<String, Object> row = row("input_text.demo");
        row.put("liveIndicator", true);
        row.put("progressMode", "On");
        row.put("progressEntity", "sensor.progress");
        Map<String, NotificationModel.Snapshot> states = states("input_text.demo",
            snapshot("available", "24 min remaining", map()));
        states.put("sensor.progress", snapshot("available", "65", map()));
        NotificationModel.Config config = config(Collections.singletonList(row));
        NotificationModel.Item item = config.entries.get(0).evaluate(states, Locale.US);
        check(item.value.equals("24 min remaining") && item.progress == 65, "independent progress preserves remaining-time text");
        check(item.liveIndicator, "light allowed only with valid subscribed sources");
        check(config.sources.size() == 2, "independent progress consumes one shared subscription");
        states.put("sensor.progress", snapshot("available", "150", map()));
        item = config.entries.get(0).evaluate(states, Locale.US);
        check(item.progress == null && !item.liveIndicator, "invalid progress hides fill and live cue");
        check(config.entries.get(0).problem(states).contains("0 to 100"), "invalid explicit progress diagnosed");
        states.put("sensor.progress", snapshot("disconnected", "65", map()));
        item = config.entries.get(0).evaluate(states, Locale.US);
        check(item.progress == null && !item.liveIndicator, "disconnected progress never shows stale fill/light");
        check(config.entries.get(0).problem(states).contains("disconnected"), "progress connection diagnosed");
        row.put("progressAttribute", "completion");
        states.put("sensor.progress", snapshot("available", "running", map("completion", 42)));
        check(config(Collections.singletonList(row)).entries.get(0).evaluate(states, Locale.US).progress == 42,
            "independent numeric progress attribute");
        row.put("progressMode", "Off");
        row.put("progressEntity", "invalid but hidden");
        config = config(Collections.singletonList(row));
        check(config.sources.size() == 1, "Off ignores stored overrides and releases progress subscription");
        states.put("input_text.demo", snapshot("available", "68%", map()));
        item = config.entries.get(0).evaluate(states, Locale.US);
        check(item.progress == null && item.liveIndicator, "Off suppresses percent bar without suppressing valid light");
        row.put("progressMode", "Automatic");
        config = config(Collections.singletonList(row));
        check(config.sources.size() == 1 && config.entries.get(0).evaluate(states, Locale.US).progress == 68,
            "Automatic ignores independent overrides");
        row.put("progressMode", "On");
        row.put("progressEntity", "");
        row.put("progressAttribute", "");
        row.put("advanced", true);
        row.put("valueAttribute", "completion");
        states.put("input_text.demo", snapshot("available", "running", map("completion", 71)));
        config = config(Collections.singletonList(row));
        check(config.sources.size() == 1 && config.entries.get(0).evaluate(states, Locale.US).progress == 71,
            "empty On overrides follow displayed attribute and deduplicate");
        row.put("liveIndicator", false);
        check(!config(Collections.singletonList(row)).entries.get(0).evaluate(states, Locale.US).liveIndicator,
            "per-entry light can be disabled");
        check(NotificationModel.samples().stream().noneMatch(sample -> sample.liveIndicator),
            "preview never presents samples as live");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 16; i++) rows.add(row("sensor.value_" + i));
        rows.get(0).put("progressMode", "On");
        rows.get(0).put("progressEntity", "sensor.seventeenth");
        rejects(() -> config(rows), "independent progress participates in atomic subscription limit");
    }

    private static void optionalPercentageText() {
        Map<String, Object> row = row("sensor.progress");
        Map<String, NotificationModel.Snapshot> states = states("sensor.progress",
            snapshot("available", "68.5", map("unit_of_measurement", "%")));
        check(config(Collections.singletonList(row)).entries.get(0).evaluate(states, Locale.US).value.equals("68.5 %"),
            "percentage text is retained by default");
        row.put("showPercentage", false);
        NotificationModel.Config config = config(Collections.singletonList(row));
        check(config.entries.get(0).evaluate(states, Locale.GERMANY).value.isEmpty(),
            "valid bar can replace localized percentage-only text");
        states.put("sensor.progress", snapshot("available", "Cleaning - 42% complete", map()));
        check(config.entries.get(0).evaluate(states, Locale.US).value.equals("Cleaning"),
            "hiding percentage preserves useful embedded activity text");
        states.put("sensor.progress", snapshot("available", "101%", map()));
        check(config.entries.get(0).evaluate(states, Locale.US).value.equals("101%"),
            "invalid bar never discards percent text");
        row.put("progressMode", "Off");
        states.put("sensor.progress", snapshot("available", "42%", map()));
        check(config(Collections.singletonList(row)).entries.get(0).evaluate(states, Locale.US).value.equals("42%"),
            "Off retains percentage text");
        check(!config(Collections.singletonList(row)).entries.get(0).showPercentage,
            "hidden percentage preference remains saved while progress is Off");
        row.put("progressMode", "On");
        row.put("progressEntity", "sensor.other");
        states.put("sensor.other", snapshot("available", "68", map()));
        check(config(Collections.singletonList(row)).entries.get(0).evaluate(states, Locale.US).value.equals("42%"),
            "a different percentage is not redundant with independent progress");
        states.put("sensor.progress", snapshot("available", "24 min remaining", map()));
        check(config(Collections.singletonList(row)).entries.get(0).evaluate(states, Locale.US).value.equals("24 min remaining"),
            "separate progress never removes remaining-time text");
    }

    static Map<String, Object> row(String entity) {
        return map("entity", entity);
    }

    static NotificationModel.Config config(List<Map<String, Object>> rows) {
        return new NotificationModel.Config(settings(rows));
    }

    static Map<String, Object> settings(List<Map<String, Object>> rows) {
        return map("entries", rows);
    }

    static NotificationModel.Snapshot snapshot(String status, String state, Map<String, Object> attributes) {
        return new NotificationModel.Snapshot(map("status", status, "state", state, "attributes", attributes));
    }

    static Map<String, NotificationModel.Snapshot> states(String id, NotificationModel.Snapshot state) {
        Map<String, NotificationModel.Snapshot> result = new LinkedHashMap<>();
        result.put(id, state);
        return result;
    }

    static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }

    static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    static int checkCount() {
        return checks;
    }

    static void rejects(Runnable runnable, String message) {
        try {
            runnable.run();
        } catch (IllegalArgumentException expected) {
            check(true, message);
            return;
        }
        throw new AssertionError(message);
    }
}
