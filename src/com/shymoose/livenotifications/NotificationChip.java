// SPDX-License-Identifier: Apache-2.0
package com.shymoose.livenotifications;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
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

    NotificationChip(Context context, KsTheme theme) {
        super(context);
        this.theme = theme;
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
        current = first;
        outgoing = second;
        restyle(theme);
        second.setVisibility(INVISIBLE);
        setVisibility(INVISIBLE);
    }

    void restyle(KsTheme theme) {
        this.theme = theme;
        GradientDrawable surface = new GradientDrawable();
        // A screensaver can be a bright photo even while KS is in its light theme.
        surface.setColor(Color.argb(226, 19, 22, 27));
        surface.setCornerRadius(theme.px(theme.radiusRow()));
        surface.setStroke(theme.px(1), Color.argb(28, 255, 255, 255));
        setBackground(surface);
        first.style();
        second.style();
        contentHeight = Math.max(theme.px(76),
            first.label.getLineHeight() + first.value.getLineHeight() + theme.px(24));
        requestLayout();
        if (item != null) current.bind(item);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int height = View.resolveSize(contentHeight, heightSpec);
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    void show(NotificationModel.Item next, boolean motion) {
        if (next == null) {
            dispose();
            item = null;
            setVisibility(INVISIBLE);
            return;
        }
        boolean changed = item != null && item.id != next.id;
        boolean wasVisible = getVisibility() == VISIBLE;
        if (!changed && transition != null) {
            item = next;
            current.bind(next);
            return;
        }
        finishTransition();
        item = next;
        setVisibility(VISIBLE);
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
        float distance = theme.px(14);
        current.setPivotY(getHeight() / 2f);
        outgoing.setPivotY(getHeight() / 2f);
        current.setCameraDistance(theme.px(1800));
        outgoing.setCameraDistance(theme.px(1800));
        transition = new AnimatorSet();
        transition.playTogether(
            ObjectAnimator.ofFloat(outgoing, "translationY", 0, -distance),
            ObjectAnimator.ofFloat(outgoing, "rotationX", 0, 9),
            ObjectAnimator.ofFloat(outgoing, "alpha", 1, 0),
            ObjectAnimator.ofFloat(current, "translationY", distance, 0),
            ObjectAnimator.ofFloat(current, "rotationX", -9, 0),
            ObjectAnimator.ofFloat(current, "alpha", 0, 1)
        );
        transition.setDuration(350);
        transition.setInterpolator(new DecelerateInterpolator(1.5f));
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
    }

    private final class Row extends LinearLayout {
        private final ImageView icon;
        private final TextView label, value;

        Row(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(theme.px(16), theme.px(10), theme.px(16), theme.px(10));
            icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setPadding(theme.px(8), theme.px(8), theme.px(8), theme.px(8));
            LinearLayout.LayoutParams iconSize = new LinearLayout.LayoutParams(theme.px(38), theme.px(38));
            iconSize.setMarginEnd(theme.px(12));
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
            addView(text, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        }

        void style() {
            label.setTypeface(theme.typeface(400));
            label.setTextSize(13);
            label.setTextColor(Color.rgb(205, 210, 218));
            value.setTypeface(theme.typeface(500));
            value.setTextSize(21);
            value.setTextColor(Color.rgb(248, 249, 251));
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(Color.argb(24, 255, 255, 255));
            icon.setBackground(circle);
        }

        void bind(NotificationModel.Item item) {
            label.setText(item.label);
            value.setText(item.value);
            Drawable drawable = theme.icon(item.icon, "onSurface");
            if (drawable == null) drawable = theme.icon("mdi:information-outline", "onSurface");
            if (drawable != null) drawable = drawable.mutate();
            if (drawable != null) drawable.setTint(Color.rgb(225, 230, 238));
            icon.setImageDrawable(drawable);
        }
    }
}
