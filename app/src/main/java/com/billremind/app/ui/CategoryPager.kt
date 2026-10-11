package com.billremind.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager

class CategoryPager @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ViewPager(context, attrs) {
    var swipeEnabled = true

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
        swipeEnabled && super.onInterceptTouchEvent(event)

    override fun onTouchEvent(event: MotionEvent): Boolean =
        swipeEnabled && super.onTouchEvent(event)
}
