// SPDX-License-Identifier: Apache-2.0
package com.shymoose.livenotifications;

import java.util.Map;
import me.jxl.kiosk.plugins.KsTheme;
import static com.shymoose.livenotifications.CoreTest.*;

/** Exercises the same palette resolver the native chip uses, without Android rendering. */
public final class AppearanceTest {
    public static void main(String[] args) {
        Map<String, Integer> roles = new java.util.LinkedHashMap<>();
        roles.put("surfaceContainerHigh", 0xfff1f2f3);
        roles.put("outlineVariant", 0xff444546);
        roles.put("onSurfaceVariant", 0xff202122);
        roles.put("onSurface", 0xff101112);
        roles.put("surfaceContainerHighest", 0xffd1d2d3);
        roles.put("primary", 0xff13579b);
        KsTheme theme = new KsTheme(false, 1, roles, null);
        NotificationChip.Colors nativeDay = new NotificationChip.Colors(theme, false, ScreensaverAppearance.NORMAL);
        check(nativeDay.surface == roles.get("surfaceContainerHigh") && nativeDay.outline == roles.get("outlineVariant"),
            "normal Native material keeps exact KS surface/stroke");
        check(nativeDay.label == roles.get("onSurfaceVariant") && nativeDay.value == roles.get("onSurface")
            && nativeDay.icon == roles.get("onSurface"), "normal Native keeps semantic text/icon roles");
        check(nativeDay.circle == roles.get("surfaceContainerHighest") && nativeDay.track == roles.get("outlineVariant")
            && nativeDay.fill == roles.get("primary") && nativeDay.light == roles.get("primary"),
            "normal Native progress/circle/light remain unchanged");
        NotificationChip.Colors glassDay = new NotificationChip.Colors(theme, true, ScreensaverAppearance.NORMAL);
        check(glassDay.surface == 0xe213161b && glassDay.outline == 0x1cffffff, "normal Glass opacity/surface unchanged");
        check(glassDay.label == 0xffcdd2da && glassDay.value == 0xfff8f9fb && glassDay.icon == 0xffe1e6ee,
            "normal Glass text/icon unchanged");
        check(glassDay.fill == 0xffd3ddea && glassDay.light == 0xfff6fbf9
            && glassDay.circle == 0x18ffffff && glassDay.track == 0x26ffffff, "normal Glass accents unchanged");

        Map<String, Object> payload = map("view", "clock", "night", true, "textColor", 0xffbd4319L,
            "backgroundColor", 0xff08090aL, "cardColor", null, "widgetsHidden", false);
        ScreensaverAppearance night = ScreensaverAppearance.read(payload);
        check(night.night && night.textColor == 0xffbd4319 && night.backgroundColor == 0xff08090a,
            "opaque unsigned JSON ARGB safely converts to Java signed colors");
        check(night.equals(ScreensaverAppearance.read(payload)) && night.hashCode() == ScreensaverAppearance.read(payload).hashCode(),
            "identical snapshots do not require recoloring");
        payload.put("textColor", Double.valueOf(0xffbd4319L));
        check(ScreensaverAppearance.read(payload).textColor == night.textColor,
            "integral floating JSON Number preserves unsigned ARGB instead of saturating to Integer.MAX_VALUE");
        for (boolean glass : new boolean[]{false, true}) {
            NotificationChip.Colors colors = new NotificationChip.Colors(theme, glass, night);
            check(colors.surface == (glass ? 0xe208090a : 0xff08090a), "night material keeps Native/Glass opacity");
            check(colors.label == night.textColor && colors.value == night.textColor && colors.icon == night.textColor,
                "both styles match exact clock foreground, not bright theme accents");
            check(colors.fill == night.textColor && colors.light == night.textColor, "progress and optional glow follow night tint");
            check(colors.circle == 0x18bd4319 && colors.track == 0x26bd4319 && colors.outline == 0x1cbd4319,
                "quiet circle/track/hairline use night tint, never white");
        }
        payload.put("cardColor", Integer.valueOf(0xff242526));
        ScreensaverAppearance flip = ScreensaverAppearance.read(payload);
        check(new NotificationChip.Colors(theme, false, flip).surface == 0xff242526, "Native honors resolved Flip card color");
        check(new NotificationChip.Colors(theme, true, flip).surface == 0xe2242526, "Glass honors Flip card color with retained translucency");
        payload.put("night", false);
        payload.put("widgetsHidden", true);
        ScreensaverAppearance exit = ScreensaverAppearance.read(payload);
        check(!exit.night && !exit.widgetsHidden && exit.cardColor == null && exit.textColor == 0,
            "night exit clears colors/hidden flag even if old fields remain in payload");
        check(new NotificationChip.Colors(theme, false, exit).surface == nativeDay.surface
            && new NotificationChip.Colors(theme, true, exit).light == glassDay.light, "exit fully restores both normal materials");
        roles.put("onSurface", 0xffeff0f1);
        KsTheme changed = new KsTheme(true, 2, roles, null);
        check(new NotificationChip.Colors(changed, false, exit).value == 0xffeff0f1, "restoration resolves newest host theme");
        check(new NotificationChip.Colors(changed, false, night).value == night.textColor, "theme update cannot override active night");
        expectInvalid(map());
        expectInvalid(map("view", "clock", "night", true, "widgetsHidden", false, "textColor", "red"));
        for (Object color : new Object[]{Double.NaN, Double.POSITIVE_INFINITY, 3.5, 0x100000000L, -2147483649L}) {
            Map<String, Object> bad = map("view", "clock", "night", true, "widgetsHidden", false,
                "textColor", color, "backgroundColor", 0xff000000L);
            expectInvalid(bad);
        }
        expectInvalid(map("view", "slideshow", "night", true, "widgetsHidden", false,
            "textColor", 0xffff0000L, "backgroundColor", 0xff000000L));
        System.out.println("AppearanceTest: " + checkCount() + " snapshot and material assertions passed");
    }

    private static void expectInvalid(Object payload) {
        boolean rejected = false;
        try {
            ScreensaverAppearance.read(payload);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "malformed host appearance is rejected explicitly");
    }
}
