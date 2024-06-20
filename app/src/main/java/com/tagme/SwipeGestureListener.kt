package com.tagme

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import kotlin.math.abs
import kotlin.math.max

@Suppress("NOTHING_TO_OVERRIDE", "ACCIDENTAL_OVERRIDE")
class SwipeGestureListener(
    val onSwipe: (Float) -> Boolean,
    val onSwipeEnd: () -> Unit
) : GestureDetector.SimpleOnGestureListener() {
    private var startY = 0f
    private var isScrolling = false
    override fun onDown(e: MotionEvent?): Boolean {
        startY = e?.y ?: 0f
        Log.d("Tagme_gest", "onDown $startY")
        return false
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean {
        if (e1 != null && e2 != null) {
            if (!isScrolling) {
                startY = e2.y
                isScrolling = true
            }
            val deltaY = e2.y - startY
            if (onSwipe(deltaY)) {
                return true
            } else {
                startY = e2.y
            }
        }
        return false
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        Log.d("Tagme_gest", "onSingleTapUp")
        startY = 0f
        onSwipeEnd()
        return super.onSingleTapUp(e)
    }
    override fun onLongPress(e: MotionEvent) {
        Log.d("Tagme_gest", "onLongPress")
        startY = 0f
        onSwipeEnd()
        super.onLongPress(e)
    }
    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent?,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        Log.d("Tagme_gest", "onFling")
        isScrolling = false
        onSwipeEnd()
        return false
    }
    fun onUp(e: MotionEvent?) {
        Log.d("Tagme_gest", "onUp")
        if (isScrolling) {
            isScrolling = false
            onSwipeEnd()
        }
    }
}
@SuppressLint("ClickableViewAccessibility")
fun setupSwipeGesture(
    fragment: Fragment,
    nestedScrollView: CustomNestedScrollView,
    linearLayout: LinearLayout?,
    view: View,
    mapActivity: Activity
) {
    var shouldInterceptTouch = false
    val gestureListener = SwipeGestureListener(
        onSwipe = { deltaY ->
            if (nestedScrollView.scrollY == 0) {
                val newTranslationY = view.translationY + deltaY
                if (shouldInterceptTouch || newTranslationY > 0F) {
                    shouldInterceptTouch = true
                    view.translationY = max(newTranslationY, 0F)
                    nestedScrollView.scrollTo(0, 0)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        },
        onSwipeEnd = {
            shouldInterceptTouch = false
            if (abs(view.translationY) > 150) { // swipe threshold
                fragment.animateFragmentClose(view)
            } else {
                fragment.animateFragmentReset(view)
            }
        }
    )
    val gestureDetector = GestureDetector(mapActivity, gestureListener)
    nestedScrollView.gestureDetector = gestureDetector
    nestedScrollView.setOnTouchListener { v, event ->
        if (gestureDetector.onTouchEvent(event)) {
            return@setOnTouchListener true
        }
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            gestureListener.onUp(event)
            shouldInterceptTouch = false
            v.performClick()
        }
        false
    }
    linearLayout?.setOnTouchListener { v, event ->
        if (gestureDetector.onTouchEvent(event)) {
            return@setOnTouchListener true
        }
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            gestureListener.onUp(event)
            shouldInterceptTouch = false
            v.performClick()
        }
        false
    }
}
