package com.tagme

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment

fun Fragment.hideKeyboard() {
    view?.let { activity?.hideKeyboard(it) }
}

fun Activity.hideKeyboard() {
    hideKeyboard(currentFocus ?: View(this))
}

fun Context.hideKeyboard(view: View) {
    val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}

fun Activity.setSoftInputMode(shouldResize: Boolean) {
    if (shouldResize) {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    } else {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
    }
}
fun Fragment.animateFragmentClose(view: View) {
    val animator = ValueAnimator.ofFloat(view.translationY, view.height.toFloat())
    animator.addUpdateListener { animation ->
        view.translationY = animation.animatedValue as Float
    }
    animator.duration = 300
    animator.start()

    animator.addListener(object : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator) {}
        override fun onAnimationEnd(animation: Animator) {
            activity?.supportFragmentManager?.popBackStackImmediate()
            view.clearAnimation()
            view.translationY = 0F
        }

        override fun onAnimationCancel(animation: Animator) {}
        override fun onAnimationRepeat(animation: Animator) {}
    })
}

fun Fragment.animateFragmentReset(view: View) {
    val animator = ValueAnimator.ofFloat(view.translationY, 0f)
    animator.addUpdateListener { animation ->
        view.translationY = animation.animatedValue as Float
    }
    animator.duration = 300
    animator.start()
}
fun ImageView.setImageDrawableResource(@DrawableRes resId: Int) {
    setImageResource(resId)
}