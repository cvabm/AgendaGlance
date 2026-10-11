package com.billremind.app.ui

import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.billremind.app.R

/** A single selection pill moves beneath the two accessible category buttons. */
class CategoryTabsLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.tab_selected_bg)
    }
    private val activeColor = ContextCompat.getColor(context, R.color.md_primary)
    private val inactiveColor = ContextCompat.getColor(context, R.color.md_on_surface_variant)
    private val colors = ArgbEvaluator()
    private var progress = 0f

    init { setWillNotDraw(false) }

    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        (getChildAt(0) as? TextView)?.setTextColor(colors.evaluate(progress, activeColor, inactiveColor) as Int)
        (getChildAt(1) as? TextView)?.setTextColor(colors.evaluate(progress, inactiveColor, activeColor) as Int)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (childCount < 2) return
        val first = getChildAt(0)
        val second = getChildAt(1)
        val left = first.left + (second.left - first.left) * progress
        val right = first.right + (second.right - first.right) * progress
        val inset = 2 * resources.displayMetrics.density
        val top = first.top + inset
        val bottom = first.bottom - inset
        val radius = (bottom - top) / 2
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
    }

}
