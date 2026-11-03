// SPDX-License-Identifier: Apache-2.0
package com.shymoose.livenotifications;

import java.util.Map;
import java.util.Objects;

/** Detached public KS appearance snapshot; no clock settings or lux heuristics. */
final class ScreensaverAppearance {
    static final ScreensaverAppearance NORMAL = new ScreensaverAppearance(null, false, 0, 0, null, false);

    final String view;
    final boolean night, widgetsHidden;
    final int textColor, backgroundColor;
    final Integer cardColor;

    private ScreensaverAppearance(String view, boolean night, int textColor, int backgroundColor,
                                 Integer cardColor, boolean widgetsHidden) {
        this.view = view;
        this.night = night;
        this.textColor = textColor;
        this.backgroundColor = backgroundColor;
        this.cardColor = cardColor;
        this.widgetsHidden = widgetsHidden;
    }

    static ScreensaverAppearance read(Object data) {
        if (!(data instanceof Map)) throw new IllegalArgumentException("Expected a screensaver appearance map");
        Map<?, ?> payload = (Map<?, ?>) data;
        Object view = payload.get("view"), night = payload.get("night"), hidden = payload.get("widgetsHidden");
        if ((view != null && !(view instanceof String)) || !(night instanceof Boolean) || !(hidden instanceof Boolean)) {
            throw new IllegalArgumentException("Invalid screensaver appearance state");
        }
        if (Boolean.TRUE.equals(night)) {
            if (!"clock".equals(view)) throw new IllegalArgumentException("Night appearance requires the clock view");
            int text = color(payload.get("textColor"));
            int background = color(payload.get("backgroundColor"));
            Integer card = payload.get("cardColor") == null ? null : color(payload.get("cardColor"));
            return new ScreensaverAppearance((String) view, true, text, background, card, (Boolean) hidden);
        }
        // Null colors on exit are normal. Never retain the previous night palette.
        return new ScreensaverAppearance((String) view, false, 0, 0, null, false);
    }

    private static int color(Object value) {
        if (!(value instanceof Number)) throw new IllegalArgumentException("Expected numeric night ARGB color");
        Number number = (Number) value;
        double numeric = number.doubleValue();
        if (!Double.isFinite(numeric) || numeric != Math.rint(numeric)
            || numeric < Integer.MIN_VALUE || numeric > 0xffffffffL) {
            throw new IllegalArgumentException("Invalid night ARGB color");
        }
        // Integral JSON numbers can be unsigned; preserve ARGB bits even for a floating Number.
        return (int) number.longValue();
    }

    static int alpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00ffffff);
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof ScreensaverAppearance)) return false;
        ScreensaverAppearance next = (ScreensaverAppearance) other;
        return Objects.equals(view, next.view) && night == next.night && widgetsHidden == next.widgetsHidden
            && textColor == next.textColor && backgroundColor == next.backgroundColor
            && Objects.equals(cardColor, next.cardColor);
    }

    @Override public int hashCode() {
        return Objects.hash(view, night, widgetsHidden, textColor, backgroundColor, cardColor);
    }
}
