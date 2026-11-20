package com.duorhythm.app.util

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.duorhythm.app.R
import kotlin.math.abs

/**
 * Deterministic Contact & Member Avatar System:
 * - 4-Tier Fallback Chain: Photo URI -> 2-Letter Initials (1-Letter at <= 20dp) -> Generic Person Icon
 * - Deterministic Name Hashing: Same person always maps to the exact same dark-mode-safe background color
 * - Group Avatar Stacking with #0A0B0E cutout border and +N counter chip
 */
object DeterministicAvatarHelper {

    private val AVATAR_PALETTE = listOf(
        "#0284C7", // Sky Blue
        "#059669", // Emerald
        "#7C3AED", // Violet
        "#D97706", // Amber
        "#DB2777", // Rose
        "#0891B2", // Cyan-Teal
        "#4F46E5", // Indigo
        "#0D9488"  // Deep Teal
    )

    fun colorForName(name: String): Int {
        val clean = name.trim().lowercase()
        if (clean.isEmpty()) return Color.parseColor("#334155")
        val idx = abs(clean.hashCode()) % AVATAR_PALETTE.size
        return Color.parseColor(AVATAR_PALETTE[idx])
    }

    fun extractInitials(name: String, sizeDp: Int = 32): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return ""
        if (sizeDp <= 20 || parts.size == 1) {
            return parts.first().take(1).uppercase()
        }
        val first = parts.first().take(1).uppercase()
        val last = parts.last().take(1).uppercase()
        return "$first$last"
    }

    fun bindAvatar(
        imageView: ImageView,
        name: String,
        photoUri: String? = null,
        sizeDp: Int = 32,
        ringColorHex: String? = null
    ) {
        val ctx = imageView.context
        val density = ctx.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(16)

        // Tier 1: Valid Photo URI with 85% dark-mode glare dimming
        if (!photoUri.isNullOrBlank()) {
            try {
                val uri = Uri.parse(photoUri)
                imageView.setPadding(0, 0, 0, 0)
                imageView.imageTintList = null
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.alpha = 0.85f
                imageView.setImageURI(uri)
                if (imageView.drawable != null) {
                    applyRingBackground(imageView, Color.parseColor("#1E222D"), ringColorHex, density)
                    return
                }
            } catch (_: Exception) {}
        }

        imageView.alpha = 1.0f
        val bgColor = colorForName(name)
        val initials = extractInitials(name, sizeDp)

        // Tier 2: Deterministic Initials Bitmap
        if (initials.isNotEmpty()) {
            val bmp = renderInitialsBitmap(sizePx, bgColor, initials)
            imageView.setPadding(0, 0, 0, 0)
            imageView.imageTintList = null
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            imageView.setImageDrawable(BitmapDrawable(ctx.resources, bmp))
            applyRingBackground(imageView, bgColor, ringColorHex, density)
            return
        }

        // Tier 3 & 4: Generic Person Icon Fallback (never a broken square)
        val pad = (sizePx * 0.22f).toInt()
        imageView.setPadding(pad, pad, pad, pad)
        imageView.setImageResource(R.drawable.ic_default_profile)
        imageView.imageTintList = ColorStateList.valueOf(Color.parseColor("#F1F5F9"))
        applyRingBackground(imageView, Color.parseColor("#1E222D"), ringColorHex, density)
    }

    fun bindStackedAvatars(
        container: LinearLayout,
        members: List<Pair<String, String?>>,
        maxVisible: Int = 3,
        sizeDp: Int = 24
    ) {
        container.removeAllViews()
        if (members.isEmpty()) return
        val ctx = container.context
        val density = ctx.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()
        val overlapPx = (-6f * density).toInt()

        val visible = members.take(maxVisible)
        visible.forEachIndexed { index, (memberName, uri) ->
            val iv = ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    if (index > 0) marginStart = overlapPx
                }
            }
            bindAvatar(iv, memberName, uri, sizeDp = sizeDp, ringColorHex = "#0A0B0E")
            container.addView(iv)
        }

        val overflow = members.size - visible.size
        if (overflow > 0) {
            val chip = TextView(ctx).apply {
                text = "+$overflow"
                textSize = 9.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#F1F5F9"))
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#1E222D"))
                    setStroke((1.5f * density).toInt(), Color.parseColor("#0A0B0E"))
                }
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    marginStart = overlapPx
                }
            }
            container.addView(chip)
        }
    }

    private fun applyRingBackground(view: ImageView, fillColor: Int, ringColorHex: String?, density: Float) {
        val gd = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
            if (!ringColorHex.isNullOrBlank()) {
                try {
                    setStroke((1.5f * density).toInt(), Color.parseColor(ringColorHex))
                } catch (_: Exception) {}
            }
        }
        view.background = gd
        view.clipToOutline = true
    }

    private fun renderInitialsBitmap(sizePx: Int, bgColor: Int, initials: String): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        val r = sizePx / 2f
        canvas.drawCircle(r, r, r, bgPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            textSize = sizePx * (if (initials.length == 1) 0.44f else 0.38f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()
        textPaint.getTextBounds(initials, 0, initials.length, bounds)
        val y = r + (bounds.height() / 2f) - bounds.bottom
        canvas.drawText(initials, r, y, textPaint)
        return bmp
    }
}
