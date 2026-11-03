// SPDX-License-Identifier: Apache-2.0
package com.shymoose.livenotifications;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import me.jxl.kiosk.plugins.KsTheme;

/** One clipped, fixed-size surface; only the inner content rolls. Main thread only. */
final class NotificationChip extends FrameLayout {
    private KsTheme theme;
    private final Row first, second;
    private Row current, outgoing;
    private NotificationModel.Item item;
    private AnimatorSet transition;
    private int contentHeight;
    private final boolean adaptiveWidth;
    private boolean darkSurface;
    private ScreensaverAppearance appearance = ScreensaverAppearance.NORMAL;
    private Colors colors;
    private int textScale;
    private final View indicator;
    private ObjectAnimator pulse;
    private boolean lastMotion;
    private final ViewTreeObserver.OnGlobalLayoutListener redrawAfterLayout = this::postInvalidateOnAnimation;

    NotificationChip(Context context, KsTheme theme, boolean adaptiveWidth, int textScale, boolean darkSurface) {
        super(context);
        this.theme = theme;
        this.adaptiveWidth = adaptiveWidth;
        this.darkSurface = darkSurface;
        this.textScale = textScale;
        setClipChildren(true);
        setClipToPadding(true);
        setClipToOutline(true);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setClickable(false);
        setFocusable(false);
        first = new Row(context);
        second = new Row(context);
        addView(first, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(second, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        indicator = new LiveLight(context);
        addView(indicator, new FrameLayout.LayoutParams(0, 0));
        indicator.setVisibility(INVISIBLE);
        current = first;
        outgoing = second;
        restyle(theme);
        second.setVisibility(INVISIBLE);
        setVisibility(INVISIBLE);
    }

    void restyle(KsTheme theme) {
        this.theme = theme;
        colors = new Colors(theme, darkSurface, appearance);
        GradientDrawable surface = theme.card();
        surface.setColor(colors.surface);
        surface.setStroke(darkSurface ? theme.px(1) : Math.max(1, theme.px(1)), colors.outline);
        if (darkSurface) {
            surface.setCornerRadius(theme.px(theme.radiusRow()));
        }
        setBackground(surface);
        first.style();
        second.style();
        FrameLayout.LayoutParams lightSize = new FrameLayout.LayoutParams(theme.px(30), theme.px(30), Gravity.TOP | Gravity.END);
        lightSize.topMargin = theme.px(6);
        lightSize.setMarginEnd(theme.px(6));
        indicator.setLayoutParams(lightSize);
        ((LiveLight) indicator).setColor(colors.light);
        contentHeight = Math.max(theme.px(84),
            first.label.getLineHeight() + first.value.getLineHeight() + theme.px(34));
        requestLayout();
        if (item != null) {
            current.bind(item);
            if (transition != null) outgoing.bind(outgoing.bound);
        }
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int height = View.resolveSize(contentHeight, heightSpec);
        int width = adaptiveWidth ? View.resolveSize(theme.px(NotificationModel.defaultWidth(
            getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)), widthSpec)
            : MeasureSpec.getSize(widthSpec);
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override protected void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        requestLayout();
    }

    void show(NotificationModel.Item next, boolean motion, int scale, boolean darkSurface,
              ScreensaverAppearance appearance) {
        lastMotion = motion;
        if (textScale != scale || this.darkSurface != darkSurface || !this.appearance.equals(appearance)) {
            textScale = scale;
            this.darkSurface = darkSurface;
            this.appearance = appearance;
            restyle(theme);
        }
        if (next == null) {
            dispose();
            item = null;
            setVisibility(INVISIBLE);
            return;
        }
        boolean changed = item != null && item.id != next.id;
        boolean wasVisible = getVisibility() == VISIBLE;
        if (!changed && transition != null && motion && ValueAnimator.areAnimatorsEnabled()) {
            item = next;
            current.bind(next);
            updateIndicator();
            return;
        }
        finishTransition();
        item = next;
        setVisibility(VISIBLE);
        updateIndicator();
        if (!changed || !wasVisible || !motion || !ValueAnimator.areAnimatorsEnabled() || getHeight() == 0) {
            current.bind(next);
            current.setVisibility(VISIBLE);
            outgoing.setVisibility(INVISIBLE);
            setContentDescription(next.label + ", " + next.value);
            return;
        }
        Row old = current;
        current = outgoing;
        outgoing = old;
        current.bind(next);
        current.setVisibility(VISIBLE);
        float distance = getHeight() * 0.70f;
        current.setPivotY(getHeight() / 2f);
        outgoing.setPivotY(getHeight() / 2f);
        current.setCameraDistance(theme.px(600));
        outgoing.setCameraDistance(theme.px(600));
        transition = new AnimatorSet();
        transition.playTogether(
            ObjectAnimator.ofFloat(outgoing, "translationY", 0, -distance),
            ObjectAnimator.ofFloat(outgoing, "rotationX", 0, 60),
            ObjectAnimator.ofFloat(outgoing, "alpha", 1, 1, 0),
            ObjectAnimator.ofFloat(current, "translationY", distance, 0),
            ObjectAnimator.ofFloat(current, "rotationX", -60, 0),
            ObjectAnimator.ofFloat(current, "alpha", 0, 1, 1)
        );
        transition.setDuration(580);
        transition.setInterpolator(new PathInterpolator(0.4f, 0f, 0.2f, 1f));
        transition.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animator) {
                outgoing.setVisibility(INVISIBLE);
                transition = null;
            }
        });
        transition.start();
        setContentDescription(next.label + ", " + next.value);
    }

    private void finishTransition() {
        if (transition != null) {
            transition.removeAllListeners();
            transition.cancel();
            transition = null;
        }
        for (Row row : new Row[]{first, second}) {
            row.setTranslationY(0);
            row.setRotationX(0);
            row.setAlpha(1);
        }
        outgoing.setVisibility(INVISIBLE);
    }

    void dispose() {
        finishTransition();
        item = null;
        stopPulse();
        indicator.setVisibility(INVISIBLE);
        setVisibility(INVISIBLE);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Wrapped overlays start in a 1px texture; repaint when KS lays out the resized window.
        getViewTreeObserver().addOnGlobalLayoutListener(redrawAfterLayout);
        updateIndicator();
    }

    @Override protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnGlobalLayoutListener(redrawAfterLayout);
        stopPulse();
        super.onDetachedFromWindow();
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (indicator != null) updateIndicator();
    }

    private void updateIndicator() {
        boolean visible = item != null && item.liveIndicator && isShown() && getWindowVisibility() == VISIBLE;
        indicator.setVisibility(visible ? VISIBLE : INVISIBLE);
        if (!visible || !lastMotion || !ValueAnimator.areAnimatorsEnabled()) {
            stopPulse();
            return;
        }
        if (pulse == null) {
            pulse = ObjectAnimator.ofFloat(indicator, "alpha", 0.55f, 0.95f);
            pulse.setDuration(1600);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.start();
        }
    }

    private void stopPulse() {
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
        }
        indicator.setAlpha(0.75f);
    }

    private final class Row extends LinearLayout {
        private final ImageView icon;
        private final TextView label, value;
        private final ProgressTrack progress;
        private NotificationModel.Item bound;

        Row(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(theme.px(16), theme.px(10), theme.px(44), theme.px(10));
            icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.FIT_XY);
            LinearLayout.LayoutParams iconSize = new LinearLayout.LayoutParams(0, 0);
            addView(icon, iconSize);
            LinearLayout text = new LinearLayout(context);
            text.setOrientation(VERTICAL);
            text.setGravity(Gravity.CENTER_VERTICAL);
            label = new TextView(context);
            value = new TextView(context);
            for (TextView view : new TextView[]{label, value}) {
                view.setSingleLine(true);
                view.setEllipsize(TextUtils.TruncateAt.END);
                view.setIncludeFontPadding(false);
                text.addView(view, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }
            ((LinearLayout.LayoutParams) value.getLayoutParams()).topMargin = theme.px(4);
            progress = new ProgressTrack(context);
            LinearLayout.LayoutParams trackSize = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, theme.px(4));
            trackSize.topMargin = theme.px(6);
            text.addView(progress, trackSize);
            addView(text, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        }

        void style() {
            float scale = textScale / 100f;
            int diameter = theme.px(40 * scale);
            LinearLayout.LayoutParams iconSize = (LinearLayout.LayoutParams) icon.getLayoutParams();
            iconSize.width = iconSize.height = diameter;
            iconSize.setMarginEnd(theme.px(10 * scale));
            icon.setLayoutParams(iconSize);
            int iconPadding = theme.px(9 * scale);
            icon.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
            label.setTypeface(theme.typeface(400));
            label.setTextSize(13 * scale);
            label.setTextColor(colors.label);
            value.setTypeface(theme.typeface(600));
            value.setTextSize(17 * scale);
            value.setTextColor(colors.value);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(colors.circle);
            icon.setBackground(circle);
            progress.colors(colors.track, colors.fill);
        }

        void bind(NotificationModel.Item item) {
            bound = item;
            label.setText(item.label);
            value.setText(item.value);
            progress.setProgress(item.progress);
            Drawable drawable = theme.icon(item.icon, "onSurface");
            if (drawable == null) drawable = theme.icon("mdi:information-outline", "onSurface");
            if (drawable != null) drawable = drawable.mutate();
            if (drawable != null) drawable.setTint(colors.icon);
            icon.setImageDrawable(drawable);
        }
    }

    /** Same material geometry/opacity, with the host's resolved night palette instead of bright accents. */
    static final class Colors {
        final int surface, outline, label, value, icon, circle, track, fill, light;

        Colors(KsTheme theme, boolean glass, ScreensaverAppearance appearance) {
            if (appearance.night) {
                int background = appearance.cardColor == null ? appearance.backgroundColor : appearance.cardColor;
                surface = glass ? ScreensaverAppearance.alpha(background, 226) : background;
                label = value = icon = fill = light = appearance.textColor;
                outline = ScreensaverAppearance.alpha(appearance.textColor, 28);
                circle = ScreensaverAppearance.alpha(appearance.textColor, 24);
                track = ScreensaverAppearance.alpha(appearance.textColor, 38);
            } else {
                surface = glass ? 0xe213161b : theme.color("surfaceContainerHigh");
                outline = glass ? 0x1cffffff : theme.color("outlineVariant");
                label = glass ? 0xffcdd2da : theme.color("onSurfaceVariant");
                value = glass ? 0xfff8f9fb : theme.color("onSurface");
                icon = glass ? 0xffe1e6ee : theme.color("onSurface");
                circle = glass ? 0x18ffffff : theme.color("surfaceContainerHighest");
                track = glass ? 0x26ffffff : theme.color("outlineVariant");
                fill = glass ? 0xffd3ddea : theme.color("primary");
                light = glass ? 0xfff6fbf9 : theme.color("primary");
            }
        }
    }

    private static final class LiveLight extends View {
        private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int color;

        LiveLight(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setColor(int color) {
            this.color = color;
            core.setColor(color);
            onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight());
            invalidate();
        }

        @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            if (width > 0 && height > 0) {
                halo.setShader(new RadialGradient(width / 2f, height / 2f, Math.min(width, height) / 2f,
                    new int[]{Color.argb(176, Color.red(color), Color.green(color), Color.blue(color)),
                        Color.argb(112, Color.red(color), Color.green(color), Color.blue(color)), Color.TRANSPARENT},
                    new float[]{0, 0.4f, 1}, Shader.TileMode.CLAMP));
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            float radius = Math.min(getWidth(), getHeight()) / 2f;
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius, halo);
            canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, radius / 3f, core);
        }
    }

    private static final class ProgressTrack extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float percent;
        private int trackColor, fillColor;

        ProgressTrack(Context context) {
            super(context);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void setProgress(Double value) {
            percent = value == null ? 0 : value.floatValue();
            setVisibility(value == null ? INVISIBLE : VISIBLE);
            invalidate();
        }

        void colors(int track, int fill) {
            trackColor = track;
            fillColor = fill;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            float radius = getHeight() / 2f;
            paint.setColor(trackColor);
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, paint);
            paint.setColor(fillColor);
            canvas.drawRoundRect(0, 0, getWidth() * percent / 100f, getHeight(), radius, radius, paint);
        }
    }
}
