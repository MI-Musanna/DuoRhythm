package com.duorhythm.app.ui.custom

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.duorhythm.app.R

class DuoMorphBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val containerLayout: LinearLayout
    private val iconView: ImageView
    private val textView: TextView
    private val mainHandler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null
    private var currentAnimSet: AnimatorSet? = null
    private var isDismissing = false

    init {
        val lp = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            val statusBarOffset = (48 * density).toInt()
            topMargin = statusBarOffset
            leftMargin = (16 * density).toInt()
            rightMargin = (16 * density).toInt()
        }
        layoutParams = lp
        elevation = 28 * density
        clipToPadding = false
        clipChildren = false

        containerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (14 * density).toInt(),
                (9 * density).toInt(),
                (16 * density).toInt(),
                (9 * density).toInt()
            )
        }

        iconView = ImageView(context).apply {
            val iconLp = LinearLayout.LayoutParams(
                (18 * density).toInt(),
                (18 * density).toInt()
            ).apply {
                marginEnd = (10 * density).toInt()
            }
            layoutParams = iconLp
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        textView = TextView(context).apply {
            val textLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = textLp
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            letterSpacing = -0.015f
            includeFontPadding = false
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }

        containerLayout.addView(iconView)
        containerLayout.addView(textView)
        addView(containerLayout)

        setOnClickListener {
            dismiss()
        }
    }

    fun setBanner(
        message: String,
        type: DuoAlertBanner.BannerType,
        durationMs: Long = 2200L
    ) {
        textView.text = message

        val (accentColor, iconRes) = when (type) {
            DuoAlertBanner.BannerType.SUCCESS -> Pair(Color.parseColor("#10B981"), R.drawable.ic_check)
            DuoAlertBanner.BannerType.ERROR -> Pair(Color.parseColor("#EF4444"), R.drawable.ic_close)
            DuoAlertBanner.BannerType.WARNING -> Pair(Color.parseColor("#F59E0B"), R.drawable.ic_info)
            DuoAlertBanner.BannerType.INFO -> Pair(Color.parseColor("#38BDF8"), R.drawable.ic_info)
        }

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999 * density
            setColor(Color.parseColor("#1A202C"))
            setStroke((1.2f * density).toInt(), accentColor)
        }
        containerLayout.background = bg

        try {
            iconView.setImageResource(iconRes)
            iconView.setColorFilter(accentColor)
        } catch (_: Throwable) {
            iconView.visibility = View.GONE
        }

        dismissRunnable = Runnable {
            dismiss()
        }
    }

    fun show() {
        alpha = 0f
        translationY = -35 * density
        scaleX = 0.90f
        scaleY = 0.90f

        val alphaAnim = ObjectAnimator.ofFloat(this, View.ALPHA, 0f, 1f)
        val transYAnim = ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, -35 * density, 0f)
        val scaleXAnim = ObjectAnimator.ofFloat(this, View.SCALE_X, 0.90f, 1f)
        val scaleYAnim = ObjectAnimator.ofFloat(this, View.SCALE_Y, 0.90f, 1f)

        currentAnimSet?.cancel()
        currentAnimSet = AnimatorSet().apply {
            playTogether(alphaAnim, transYAnim, scaleXAnim, scaleYAnim)
            duration = 260L
            interpolator = OvershootInterpolator(1.25f)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    dismissRunnable?.let {
                        mainHandler.postDelayed(it, 2200L)
                    }
                }
            })
            start()
        }
    }

    fun dismiss() {
        if (isDismissing) return
        isDismissing = true
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }

        val alphaAnim = ObjectAnimator.ofFloat(this, View.ALPHA, alpha, 0f)
        val transYAnim = ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, translationY, -25 * density)
        val scaleXAnim = ObjectAnimator.ofFloat(this, View.SCALE_X, scaleX, 0.92f)
        val scaleYAnim = ObjectAnimator.ofFloat(this, View.SCALE_Y, scaleY, 0.92f)

        currentAnimSet?.cancel()
        currentAnimSet = AnimatorSet().apply {
            playTogether(alphaAnim, transYAnim, scaleXAnim, scaleYAnim)
            duration = 180L
            interpolator = AccelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    (parent as? ViewGroup)?.removeView(this@DuoMorphBannerView)
                }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        currentAnimSet?.cancel()
        super.onDetachedFromWindow()
    }
}
