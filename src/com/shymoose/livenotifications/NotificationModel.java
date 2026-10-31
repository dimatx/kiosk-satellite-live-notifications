// SPDX-License-Identifier: Apache-2.0
package com.shymoose.livenotifications;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Android-independent configuration, eligibility and presentation. */
final class NotificationModel {
    static final int MAX_ENTITIES = 16;
    private static final Pattern PERCENT = Pattern.compile(
        "(?<![\\w.,])([-+]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+))\\s*%");
    private static final Pattern DISPLAY_PERCENT = Pattern.compile(
        "(?<![\\p{L}\\p{N}.,\\u066B])[-+]?(?:[\\p{Nd}]+(?:[.,\\u066B][\\p{Nd}]+)?|\\.[\\p{Nd}]+)\\s*%"
            + "(?:\\s+(?i:complete|completed|done)\\b)?");
    static final String[] RULES = {
        "Available", "Equals", "Does not equal", "Greater than", "At least", "Less than", "At most"
    };
    static final String[] ANCHORS = {
        "Top left", "Top", "Top right", "Left", "Center", "Right", "Bottom left", "Bottom", "Bottom right"
    };

    static final class Config {
        final boolean enabled, motion, autoWidth;
        final int dwellMs, offsetX, offsetY, width, textScale;
        final String anchor, displayScope, style, screensaverStyle;
        final List<Entry> entries;
        final Set<String> sources;

        Config(Map<String, Object> settings) {
            enabled = bool(settings, "enabled", true);
            displayScope = choice(settings, "displayScope", "Always",
                new String[]{"Always", "Screensaver only", "Dashboard only"});
            style = choice(settings, "style", "Native", new String[]{"Native", "Glass"});
            screensaverStyle = choice(settings, "screensaverStyle", "Default",
                new String[]{"Default", "Native", "Glass"});
            motion = choice(settings, "motion", "Roller", new String[]{"Roller", "None"}).equals("Roller");
            dwellMs = integer(settings, "dwell", 10, 3, 120) * 1000;
            offsetX = integer(settings, "offsetX", 0, -2048, 2048);
            offsetY = integer(settings, "offsetY", -32, -2048, 2048);
            width = integer(settings, "width", 320, 220, 640);
            autoWidth = choice(settings, "widthMode", "Adaptive", new String[]{"Adaptive", "Custom"}).equals("Adaptive");
            textScale = integer(settings, "textScale", 100, 80, 180);
            anchor = choice(settings, "anchor", "Bottom", ANCHORS);
            List<Entry> parsed = new ArrayList<>();
            Set<String> unique = new LinkedHashSet<>();
            Object rows = settings.get("entries");
            if (rows != null && !(rows instanceof List<?>)) throw invalid("Notifications must be a list.");
            if (rows instanceof List<?>) {
                if (((List<?>) rows).size() > 32) throw invalid("At most 32 notification rows are supported.");
                int index = 0;
                for (Object value : (List<?>) rows) {
                    if (!(value instanceof Map<?, ?>)) throw invalid("Invalid notification row.");
                    Map<?, ?> row = (Map<?, ?>) value;
                    if (bool(row, "enabled", true)) {
                        Entry entry = new Entry(index, row);
                        parsed.add(entry);
                        unique.add(entry.entity);
                        unique.add(entry.iconEntity);
                        unique.add(entry.valueEntity);
                        unique.add(entry.conditionEntity);
                        if (entry.progressMode.equals("On")) unique.add(entry.progressEntity);
                    }
                    index++;
                }
            }
            if (unique.size() > MAX_ENTITIES) {
                throw invalid("Configuration needs " + unique.size() + " unique entities; KS supports 16. "
                    + "Share sources, disable rows, or remove overrides. No notifications were subscribed.");
            }
            entries = Collections.unmodifiableList(parsed);
            sources = Collections.unmodifiableSet(unique);
        }

        boolean samePlacement(Config other) {
            return other != null && anchor.equals(other.anchor) && autoWidth == other.autoWidth
                && (autoWidth || width == other.width)
                && offsetX == other.offsetX && offsetY == other.offsetY;
        }

        boolean allows(boolean screensaver) {
            return displayScope.equals("Always")
                || displayScope.equals(screensaver ? "Screensaver only" : "Dashboard only");
        }

        boolean darkSurface(boolean screensaver) {
            String selected = screensaver && !screensaverStyle.equals("Default")
                ? screensaverStyle : style;
            return selected.equals("Glass");
        }
    }

    static final class Entry {
        final int id;
        final String entity, label, iconEntity, valueEntity, valueAttribute;
        final String conditionEntity, conditionAttribute, rule, operand;
        final String progressMode, progressEntity, progressAttribute;
        final boolean liveIndicator, showPercentage;
        final BigDecimal numericOperand;

        Entry(int id, Map<?, ?> row) {
            this.id = id;
            entity = entity(text(row, "entity", ""));
            label = text(row, "label", "");
            rule = choice(row, "rule", "Available", RULES);
            operand = rule.equals("Available") ? "" : text(row, "operand", "");
            if (!rule.equals("Available") && operand.isEmpty()) {
                throw invalid("Notification " + (id + 1) + ": a comparison value is required.");
            }
            numericOperand = numericRule(rule) && !operand.contains("e") && !operand.contains("E")
                ? decimal(operand) : null;
            if (numericRule(rule) && numericOperand == null) {
                throw invalid("Notification " + (id + 1) + ": use a finite plain number for the comparison.");
            }
            boolean advanced = bool(row, "advanced", false);
            iconEntity = advanced ? source(row, "iconEntity", entity) : entity;
            valueEntity = advanced ? source(row, "valueEntity", entity) : entity;
            conditionEntity = advanced ? source(row, "conditionEntity", entity) : entity;
            valueAttribute = advanced ? text(row, "valueAttribute", "") : "";
            conditionAttribute = advanced ? text(row, "conditionAttribute", "") : "";
            progressMode = choice(row, "progressMode", "Automatic", new String[]{"Automatic", "Off", "On"});
            String progressOverride = progressMode.equals("On") ? text(row, "progressEntity", "") : "";
            progressEntity = progressOverride.isEmpty() ? valueEntity : entity(progressOverride);
            String attribute = progressMode.equals("On") ? text(row, "progressAttribute", "") : "";
            progressAttribute = progressOverride.isEmpty() && attribute.isEmpty() ? valueAttribute : attribute;
            liveIndicator = bool(row, "liveIndicator", false);
            showPercentage = bool(row, "showPercentage", true);
        }

        String problem(Map<String, Snapshot> snapshots) {
            String problem = sourceProblem(snapshots, entity, "");
            if (problem == null) problem = sourceProblem(snapshots, valueEntity, valueAttribute);
            if (problem == null) problem = sourceProblem(snapshots, conditionEntity, conditionAttribute);
            if (problem == null) problem = sourceProblem(snapshots, iconEntity, "");
            if (problem == null && numericOperand != null
                && decimal(snapshots.get(conditionEntity).scalar(conditionAttribute)) == null) {
                problem = conditionEntity + ": condition input is not a finite number";
            }
            if (problem == null && progressMode.equals("On")) {
                problem = sourceProblem(snapshots, progressEntity, progressAttribute);
                if (problem == null && progress(snapshots.get(progressEntity).scalar(progressAttribute), "%") == null) {
                    problem = progressEntity + ": progress must be one finite percentage from 0 to 100";
                }
            }
            return problem;
        }

        Item evaluate(Map<String, Snapshot> snapshots, Locale locale) {
            Snapshot primary = snapshots.get(entity), value = snapshots.get(valueEntity);
            Snapshot condition = snapshots.get(conditionEntity), icon = snapshots.get(iconEntity);
            if (primary == null || !primary.available() || value == null || condition == null) return null;
            String displayed = value.scalar(valueAttribute), input = condition.scalar(conditionAttribute);
            if (displayed == null || !matches(rule, input, operand, numericOperand)) return null;
            String title = label.isEmpty() ? primary.attribute("friendly_name") : label;
            if (title == null || title.isEmpty()) title = entity;
            String iconName = icon == null || !icon.available() ? null : icon.attribute("icon");
            if (iconName == null || !iconName.startsWith("mdi:")) iconName = defaultIcon(iconEntity);
            Double completion = null;
            Double displayedPercent = progress(displayed,
                valueAttribute.isEmpty() ? value.attribute("unit_of_measurement") : null);
            if (progressMode.equals("Automatic")) {
                completion = displayedPercent;
            } else if (progressMode.equals("On")) {
                Snapshot progressSource = snapshots.get(progressEntity);
                if (progressSource != null) completion = progress(progressSource.scalar(progressAttribute), "%");
            }
            String formatted = format(value, valueAttribute, displayed, locale);
            if (!showPercentage && completion != null && completion.equals(displayedPercent)) {
                formatted = DISPLAY_PERCENT.matcher(formatted).replaceFirst("").trim()
                    .replaceFirst("^[-:|]\\s+", "").replaceFirst("\\s*[-:|]$", "").trim();
            }
            return new Item(id, clean(title), formatted, iconName,
                completion, liveIndicator && problem(snapshots) == null);
        }
    }

    static final class Snapshot {
        final String status, state;
        final Map<?, ?> attributes;

        Snapshot(Map<?, ?> data) {
            status = text(data, "status", "missing");
            state = scalarText(data.get("state"));
            attributes = data.get("attributes") instanceof Map<?, ?>
                ? (Map<?, ?>) data.get("attributes") : Collections.emptyMap();
        }

        boolean available() {
            return status.equals("available") && usable(state);
        }

        String scalar(String attribute) {
            if (!available()) return null;
            String value = attribute.isEmpty() ? state : scalarText(attributes.get(attribute));
            return usable(value) ? value : null;
        }

        String attribute(String key) {
            return scalarText(attributes.get(key));
        }
    }

    static final class Item {
        final int id;
        final String label, value, icon;
        final Double progress;
        final boolean liveIndicator;

        Item(int id, String label, String value, String icon) {
            this(id, label, value, icon, NotificationModel.progress(value, null), false);
        }

        Item(int id, String label, String value, String icon, Double progress, boolean liveIndicator) {
            this.id = id;
            this.label = label;
            this.value = value;
            this.icon = icon;
            this.progress = progress;
            this.liveIndicator = liveIndicator;
        }
    }

    static List<Item> eligible(Config config, Map<String, Snapshot> snapshots, Locale locale) {
        List<Item> result = new ArrayList<>();
        for (Entry entry : config.entries) {
            Item item = entry.evaluate(snapshots, locale);
            if (item != null) result.add(item);
        }
        return result;
    }

    static List<Item> samples() {
        List<Item> result = new ArrayList<>();
        result.add(new Item(0, "Preview - Living room", "22.5 \u00b0C", "mdi:thermometer"));
        result.add(new Item(1, "Preview - Front door", "Open", "mdi:door-open"));
        result.add(new Item(2, "Preview - Washing machine", "12 min remaining", "mdi:washing-machine", 65.0, false));
        return result;
    }

    static int defaultWidth(boolean landscape) {
        return landscape ? 480 : 320;
    }

    static Double progress(String raw, String unit) {
        BigDecimal value = "%".equals(unit) ? decimal(raw) : null;
        if (value == null && raw != null) {
            Matcher match = PERCENT.matcher(raw);
            if (!match.find()) return null;
            value = decimal(match.group(1));
            if (match.find()) return null;
        }
        return value != null && value.signum() >= 0 && value.compareTo(BigDecimal.valueOf(100)) <= 0
            ? value.doubleValue() : null;
    }

    static boolean matches(String rule, String input, String operand, BigDecimal target) {
        if (!usable(input)) return false;
        if (rule.equals("Available")) return true;
        if (rule.equals("Equals")) return input.equals(operand);
        if (rule.equals("Does not equal")) return !input.equals(operand);
        BigDecimal value = decimal(input);
        if (value == null || target == null) return false;
        int comparison = value.compareTo(target);
        switch (rule) {
            case "Greater than": return comparison > 0;
            case "At least": return comparison >= 0;
            case "Less than": return comparison < 0;
            case "At most": return comparison <= 0;
            default: return false;
        }
    }

    private static String sourceProblem(Map<String, Snapshot> snapshots, String id, String attribute) {
        Snapshot snapshot = snapshots.get(id);
        if (snapshot == null) return id + ": waiting for a fresh snapshot";
        if (!snapshot.available()) return id + ": "
            + (snapshot.status.equals("available") ? "unknown or invalid state" : snapshot.status);
        if (snapshot.scalar(attribute) == null) {
            return id + (attribute.isEmpty() ? ": invalid state" : ": missing or unusable attribute " + attribute);
        }
        return null;
    }

    private static String format(Snapshot snapshot, String attribute, String raw, Locale locale) {
        BigDecimal number = decimal(raw);
        if (number != null) {
            boolean scientific = number.signum() != 0 && (number.abs().compareTo(new BigDecimal("0.000000001")) < 0
                || number.abs().compareTo(new BigDecimal("1000000000000000")) >= 0);
            NumberFormat format = scientific
                ? new DecimalFormat("0.########E0", DecimalFormatSymbols.getInstance(locale))
                : NumberFormat.getNumberInstance(locale);
            if (!scientific) format.setMaximumFractionDigits(Math.max(0, number.stripTrailingZeros().scale()));
            format.setGroupingUsed(true);
            String formatted = format.format(number);
            String unit = attribute.isEmpty() ? snapshot.attribute("unit_of_measurement") : null;
            return clean(formatted + (unit == null || unit.isEmpty() ? "" : " " + unit));
        }
        if (attribute.isEmpty() && (raw.equals("on") || raw.equals("off"))) {
            boolean on = raw.equals("on");
            String deviceClass = snapshot.attribute("device_class");
            if ("door".equals(deviceClass) || "window".equals(deviceClass) || "opening".equals(deviceClass)
                || "garage_door".equals(deviceClass)) return on ? "Open" : "Closed";
            if ("motion".equals(deviceClass) || "occupancy".equals(deviceClass)
                || "presence".equals(deviceClass)) return on ? "Detected" : "Clear";
            if ("moisture".equals(deviceClass)) return on ? "Wet" : "Dry";
            if ("battery".equals(deviceClass)) return on ? "Low" : "Normal";
            return on ? "On" : "Off";
        }
        return clean(raw);
    }

    static String defaultIcon(String entity) {
        String domain = entity.substring(0, entity.indexOf('.'));
        switch (domain) {
            case "light": return "mdi:lightbulb-outline";
            case "climate": return "mdi:thermostat";
            case "weather": return "mdi:weather-partly-cloudy";
            case "lock": return "mdi:lock-outline";
            case "cover": return "mdi:window-shutter";
            case "binary_sensor": return "mdi:checkbox-blank-circle-outline";
            default: return "mdi:information-outline";
        }
    }

    static String clean(String input) {
        String value = input.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").trim();
        return value.length() > 160 ? value.substring(0, 159) + "\u2026" : value;
    }

    static boolean bool(Map<?, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Boolean)) throw invalid(key + " must be a boolean.");
        return (Boolean) value;
    }

    static String text(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        if (value == null) return fallback;
        if (!(value instanceof String)) throw invalid(key + " must be text.");
        return ((String) value).trim();
    }

    private static String choice(Map<?, ?> map, String key, String fallback, String[] choices) {
        String value = text(map, key, fallback);
        for (String choice : choices) if (choice.equals(value)) return value;
        throw invalid("Unsupported " + key + ": " + value);
    }

    private static int integer(Map<?, ?> map, String key, int fallback, int min, int max) {
        Object value = map.get(key);
        if (value == null) return fallback;
        if (!(value instanceof Number)) throw invalid(key + " must be a number.");
        double numeric = ((Number) value).doubleValue();
        if (!Double.isFinite(numeric) || numeric < min || numeric > max || numeric != Math.rint(numeric)) {
            throw invalid(key + " must be a whole number between " + min + " and " + max + ".");
        }
        return (int) numeric;
    }

    private static String source(Map<?, ?> row, String key, String primary) {
        String value = text(row, key, "");
        return value.isEmpty() ? primary : entity(value);
    }

    private static String entity(String value) {
        if (value.length() > 255 || !value.matches("[a-z0-9_]+\\.[a-z0-9_]+")) {
            throw invalid("Choose a valid primary/source entity for every enabled notification.");
        }
        return value;
    }

    private static boolean numericRule(String rule) {
        return !rule.equals("Available") && !rule.equals("Equals") && !rule.equals("Does not equal");
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.length() > 128
            || !value.matches("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][-+]?[0-9]{1,3})?")) {
            return null;
        }
        return new BigDecimal(value);
    }

    private static String scalarText(Object value) {
        if (value instanceof String || value instanceof Boolean || value instanceof Number) {
            if (value instanceof Number && !Double.isFinite(((Number) value).doubleValue())) return null;
            return String.valueOf(value);
        }
        return null;
    }

    private static boolean usable(String value) {
        return value != null && !value.trim().isEmpty()
            && !value.equalsIgnoreCase("unknown") && !value.equalsIgnoreCase("unavailable");
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
