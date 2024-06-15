package com.tagme

import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.abs

class SwipeGestureListener(val onSwipeDown: () -> Unit) : GestureDetector.SimpleOnGestureListener() {
    @Suppress("NOTHING_TO_OVERRIDE", "ACCIDENTAL_OVERRIDE")
    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent?,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (e1 != null && e2 != null) {
            val deltaY = e2.y - e1.y
            val deltaX = e2.x - e1.x
            if (abs(deltaY) > abs(deltaX) && deltaY > 0) {
                onSwipeDown()
                return true
            }
        }
        return false
    }
}
