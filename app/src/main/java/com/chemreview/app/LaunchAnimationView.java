package com.chemreview.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Short, passive launch sequence. No network work, buttons, or persistent state. */
public final class LaunchAnimationView extends FrameLayout {
    private final FrameLayout page;
    private final LinearLayout identity;
    private final ImageView icon;
    private final TextView title, subtitle, credit;
    private final GlowField field;
    private ValueAnimator motion;
    private boolean exiting;
    private final Runnable finishSequence = this::exit;

    public LaunchAnimationView(Context context, FrameLayout page) {
        super(context);
        this.page = page;
        setBackgroundColor(Color.rgb(244, 246, 248));
        setClickable(true); // Prevent gestures leaking to the page under the animation.
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        field = new GlowField(context);
        addView(field, new LayoutParams(-1, -1));
        identity = new LinearLayout(context);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setGravity(Gravity.CENTER);
        icon = new ImageView(context);
        icon.setImageResource(com.chemreview.app.R.drawable.ic_launcher_artwork);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(Color.rgb(0, 39, 31));
        outline.setCornerRadius(dp(25));
        icon.setBackground(outline);
        icon.setClipToOutline(true);
        icon.setElevation(dp(7));
        identity.addView(icon, new LinearLayout.LayoutParams(dp(94), dp(94)));
        title = label("化学复习", 25, Color.rgb(27, 66, 59));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setLetterSpacing(.08f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-2, -2);
        titleParams.topMargin = dp(23);
        identity.addView(title, titleParams);
        subtitle = label("让每一次温习，都有所收获", 12, Color.rgb(110, 133, 125));
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-2, -2);
        subtitleParams.topMargin = dp(10);
        identity.addView(subtitle, subtitleParams);
        LayoutParams identityParams = new LayoutParams(-1, -2, Gravity.CENTER);
        identityParams.leftMargin = dp(24);
        identityParams.rightMargin = dp(24);
        addView(identity, identityParams);
        identity.setTranslationY(-dp(30));
        credit = label("Designed By HuTao", 11, Color.rgb(130, 147, 141));
        credit.setLetterSpacing(.07f);
        LayoutParams creditParams = new LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        creditParams.bottomMargin = dp(32);
        addView(credit, creditParams);
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    public void play() {
        // Respect the device animation setting; do not impose a launch delay in this case.
        boolean enabled = Build.VERSION.SDK_INT >= 26 ? ValueAnimator.areAnimatorsEnabled()
                : Settings.Global.getFloat(getContext().getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        if (!enabled) { remove(); return; }
        page.setAlpha(0f);
        page.setTranslationY(dp(12));
        PathInterpolator ease = new PathInterpolator(.2f, .75f, .2f, 1f);
        icon.setAlpha(0f);
        icon.setScaleX(.78f);
        icon.setScaleY(.78f);
        icon.setTranslationY(dp(14));
        icon.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setStartDelay(80).setDuration(720).setInterpolator(ease).start();
        reveal(title, 320, ease);
        reveal(subtitle, 510, ease);
        credit.setAlpha(0f);
        credit.animate().alpha(1f).setStartDelay(650).setDuration(600).start();
        motion = ValueAnimator.ofFloat(0f, 1f);
        motion.setDuration(2200);
        motion.setInterpolator(new android.view.animation.LinearInterpolator());
        motion.addUpdateListener(animation -> field.setPhase((Float) animation.getAnimatedValue()));
        motion.start();
        postDelayed(finishSequence, 1700);
    }

    private void reveal(TextView view, long delay, PathInterpolator ease) {
        view.setAlpha(0f);
        view.setTranslationY(dp(10));
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(620).setInterpolator(ease).start();
    }

    private void exit() {
        if (exiting || !isAttachedToWindow()) return;
        exiting = true;
        page.animate().alpha(1f).translationY(0f).setDuration(450)
                .setInterpolator(new PathInterpolator(.2f, .7f, .2f, 1f)).start();
        identity.animate().alpha(0f).scaleX(1.035f).scaleY(1.035f).setDuration(300).start();
        animate().alpha(0f).setDuration(450).setListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { remove(); }
        }).start();
    }

    private void remove() {
        page.setAlpha(1f);
        page.setTranslationY(0f);
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(finishSequence);
        if (motion != null) { motion.cancel(); motion.removeAllUpdateListeners(); }
        animate().setListener(null).cancel();
        for (View child : new View[]{icon, title, subtitle, credit, identity}) child.animate().cancel();
        page.animate().cancel();
        page.setAlpha(1f);
        page.setTranslationY(0f);
        super.onDetachedFromWindow();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    /** Soft moving radial fields are cheap to draw and work on Android 6 without blur APIs. */
    private static final class GlowField extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RadialGradient jade = glow(0x647BCBBB);
        private final RadialGradient gold = glow(0x52E8CE94);
        private final RadialGradient mist = glow(0x444FA3A0);
        private float phase;
        GlowField(Context context) { super(context); }
        private static RadialGradient glow(int color) {
            return new RadialGradient(0, 0, 1, new int[]{color, color & 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
        }
        void setPhase(float phase) { this.phase = phase; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            float sway = (float) Math.sin(phase * Math.PI);
            orb(canvas, jade, w * (.12f + .23f * phase), h * (.29f + .07f * sway), w * .88f);
            orb(canvas, gold, w * (.94f - .19f * phase), h * (.64f - .11f * sway), w * .85f);
            orb(canvas, mist, w * (.64f - .12f * phase), h * .98f, w * .95f);
        }
        private void orb(Canvas canvas, Shader shader, float x, float y, float radius) {
            int saved = canvas.save();
            canvas.translate(x, y);
            canvas.scale(radius, radius);
            paint.setShader(shader);
            canvas.drawCircle(0, 0, 1, paint);
            canvas.restoreToCount(saved);
        }
    }
}
