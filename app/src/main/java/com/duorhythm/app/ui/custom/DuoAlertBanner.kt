package com.duorhythm.app.ui.custom

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Toast

object DuoAlertBanner {

    enum class BannerType {
        SUCCESS,
        ERROR,
        WARNING,
        INFO
    }

    private var lastMessage: String = ""
    private var lastShowTime: Long = 0L

    internal fun getLastMessageForTesting(): String = lastMessage
    internal fun getLastShowTimeForTesting(): Long = lastShowTime
    internal fun resetForTesting() {
        lastMessage = ""
        lastShowTime = 0L
    }

    @JvmStatic
    @JvmOverloads
    fun show(
        context: Context?,
        message: String?,
        type: BannerType = BannerType.INFO,
        durationMs: Long = 2200L
    ) {
        if (message.isNullOrBlank()) return
        val now = System.currentTimeMillis()
        if (message == lastMessage && now - lastShowTime < 300L) {
            return
        }
        lastMessage = message
        lastShowTime = now

        val act = findActivity(context)
        val resolvedType = if (type == BannerType.INFO) {
            val lower = message.lowercase(java.util.Locale.US)
            when {
                lower.contains("fail") || lower.contains("error") || lower.contains("incorrect") ||
                    lower.contains("cannot") || lower.contains("could not") || lower.contains("not found") -> BannerType.ERROR
                lower.contains("require") || lower.contains("please") || lower.contains("select") ||
                    lower.contains("enter") || lower.contains("warn") || lower.contains("empty") -> BannerType.WARNING
                lower.contains("success") || lower.contains("saved") || lower.contains("created") ||
                    lower.contains("restored") || lower.contains("updated") || lower.contains("complete") ||
                    lower.contains("synced") || lower.contains("copied") || lower.contains("reset") ||
                    lower.contains("deleted") -> BannerType.SUCCESS
                else -> BannerType.INFO
            }
        } else {
            type
        }

        if (act != null && !act.isFinishing && !act.isDestroyed) {
            val showAction = Runnable {
                try {
                    val decor = act.window?.decorView as? ViewGroup
                    if (decor != null) {
                        for (i in decor.childCount - 1 downTo 0) {
                            val child = decor.getChildAt(i)
                            if (child is DuoMorphBannerView) {
                                decor.removeViewAt(i)
                            }
                        }
                        val banner = DuoMorphBannerView(act)
                        banner.setBanner(message, resolvedType, durationMs)
                        decor.addView(banner)
                        banner.bringToFront()
                        banner.show()
                        return@Runnable
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
                // Fallback in Activity
                try {
                    Toast.makeText(act, message, Toast.LENGTH_SHORT).show()
                } catch (_: Throwable) {}
            }

            if (Looper.myLooper() == Looper.getMainLooper()) {
                showAction.run()
            } else {
                Handler(Looper.getMainLooper()).post(showAction)
            }
        } else {
            // Background receiver, worker or non-activity fallback
            try {
                if (context != null) {
                    val fallbackAction = Runnable {
                        try {
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        } catch (_: Throwable) {}
                    }
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        fallbackAction.run()
                    } else {
                        Handler(Looper.getMainLooper()).post(fallbackAction)
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    @JvmStatic
    fun show(view: View?, message: String?, type: BannerType = BannerType.INFO) {
        show(view?.context, message, type)
    }

    @JvmStatic
    fun success(context: Context?, message: String?) = show(context, message, BannerType.SUCCESS)

    @JvmStatic
    fun success(view: View?, message: String?) = show(view?.context, message, BannerType.SUCCESS)

    @JvmStatic
    fun error(context: Context?, message: String?) = show(context, message, BannerType.ERROR)

    @JvmStatic
    fun error(view: View?, message: String?) = show(view?.context, message, BannerType.ERROR)

    @JvmStatic
    fun warning(context: Context?, message: String?) = show(context, message, BannerType.WARNING)

    @JvmStatic
    fun warning(view: View?, message: String?) = show(view?.context, message, BannerType.WARNING)

    @JvmStatic
    fun info(context: Context?, message: String?) = show(context, message, BannerType.INFO)

    @JvmStatic
    fun info(view: View?, message: String?) = show(view?.context, message, BannerType.INFO)

    private fun findActivity(context: Context?): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return ctx as? Activity
    }
}
