package com.duorhythm.app.ui.custom

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.duorhythm.app.R
import com.google.android.material.button.MaterialButton

class DuoEmptyStateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    val ivEmptyIcon: ImageView
    val tvEmptyTitle: TextView
    val tvEmptySubtitle: TextView
    val btnEmptyAction: MaterialButton

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.layout_empty_state, this, true)
        ivEmptyIcon = findViewById(R.id.ivEmptyIcon)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        tvEmptySubtitle = findViewById(R.id.tvEmptySubtitle)
        btnEmptyAction = findViewById(R.id.btnEmptyAction)
    }

    fun setState(
        @DrawableRes iconRes: Int,
        title: String,
        subtitle: String,
        actionText: String,
        onAction: () -> Unit
    ) {
        ivEmptyIcon.setImageResource(iconRes)
        tvEmptyTitle.text = title
        tvEmptySubtitle.text = subtitle
        if (actionText.isNotBlank()) {
            btnEmptyAction.visibility = View.VISIBLE
            btnEmptyAction.text = actionText
            btnEmptyAction.setOnClickListener { onAction() }
        } else {
            btnEmptyAction.visibility = View.GONE
            btnEmptyAction.setOnClickListener(null)
        }
    }

    fun setAccentColor(@ColorInt color: Int) {
        ivEmptyIcon.imageTintList = ColorStateList.valueOf(color)
        btnEmptyAction.setTextColor(color)
        btnEmptyAction.strokeColor = ColorStateList.valueOf(color)
    }
}
