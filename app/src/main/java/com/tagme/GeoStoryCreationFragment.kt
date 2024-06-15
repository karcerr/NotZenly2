package com.tagme

import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

class GeoStoryCreationFragment : Fragment() {
    private lateinit var view: View
    private lateinit var api: API
    private lateinit var geoStoryPreview: ImageView
    private lateinit var geoStoryPreviewIcon: ImageView
    private lateinit var compressingStatus: LinearLayout
    private lateinit var imageHandler: ImageHandler
    private lateinit var mapActivity: MapActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        view = inflater.inflate(R.layout.geo_story_creation, container, false)
        val coroutineScope = CoroutineScope(Dispatchers.Main)
        val privacyGlobal = view.findViewById<ImageButton>(R.id.global_privacy)
        val privacyFriendsOnly = view.findViewById<ImageButton>(R.id.friend_only_privacy)
        var privacy = "friends only"
        val selectedColor = Color.parseColor("#5EFF77")
        val unselectedColor = Color.parseColor("#C6C6C6")
        val geoStoryFrameLayout = view.findViewById<FrameLayout>(R.id.geo_story_frame)
        geoStoryPreview = view.findViewById(R.id.geo_story_preview)
        geoStoryPreviewIcon = view.findViewById(R.id.geo_story_preview_icon)
        compressingStatus = view.findViewById(R.id.compressing_status)
        val backButton = view.findViewById<ImageButton>(R.id.back_arrow_button)
        mapActivity = requireActivity() as MapActivity
        api = mapActivity.api
        val publishButton = view.findViewById<ImageButton>(R.id.publish_geo_story_button)
        imageHandler = ImageHandler(mapActivity, geoStoryPreview, geoStoryPreviewIcon, compressingStatus, true, 450, 800, 20f)
        imageHandler.initImagePickerLauncher(this)
        val frameLayout = view.findViewById<FrameLayout>(R.id.frame_layout)
        var shouldInterceptTouch = false
        val gestureListener = SwipeGestureListener(
            onSwipe = { deltaY ->
                val newTranslationY = view.translationY + deltaY
                if (shouldInterceptTouch || newTranslationY > 0F){
                    shouldInterceptTouch = true
                    view.translationY = max(newTranslationY, 0F)
                    true
                } else {
                    false
                }
            },
            onSwipeEnd = {
                shouldInterceptTouch = false
                if (abs(view.translationY) > 150) { //swipe threshold
                    animateFragmentClose()
                } else {
                    animateFragmentReset()
                }
            }
        )
        val gestureDetector = GestureDetector(mapActivity, gestureListener)
        frameLayout.setOnTouchListener { v, event ->
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
        geoStoryFrameLayout.setOnClickListener {
            imageHandler.pickImageGallery()
        }
        backButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        publishButton.setOnClickListener{
            coroutineScope.launch {
                if (imageHandler.isImageCompressed()) {
                    api.insertPictureIntoWS(imageHandler.getOutputStream())
                    if (api.lastInsertedPicId != 0) {
                        val latitude = (requireActivity() as MapActivity).myLatitude
                        val longitude = (requireActivity() as MapActivity).myLongitute
                        val result = api.createGeoStory(
                            api.lastInsertedPicId,
                            privacy,
                            latitude,
                            longitude
                        )
                        val message = result?.getString("message")
                        if (message == "success") {
                            requireActivity().onBackPressedDispatcher.onBackPressed()
                            delay(500)
                            geoStoryPreview.setImageResource(R.drawable.photo_bg)
                            geoStoryPreviewIcon.visibility = View.VISIBLE
                            compressingStatus.visibility = View.GONE
                            Toast.makeText(requireActivity(), getString(R.string.geo_story_created), Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(requireActivity(), getString(R.string.something_went_wrong), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        privacyGlobal.setOnClickListener {
            privacyGlobal.setColorFilter(selectedColor)
            privacyFriendsOnly.setColorFilter(unselectedColor)
            privacy = "global"

        }
        privacyFriendsOnly.setOnClickListener {
            privacyGlobal.setColorFilter(unselectedColor)
            privacyFriendsOnly.setColorFilter(selectedColor)
            privacy = "friends only"
        }
        return view
    }
    private fun animateFragmentClose() {
        val animator = ValueAnimator.ofFloat(view.translationY, view.height.toFloat())
        animator.addUpdateListener { animation ->
            view.translationY = animation.animatedValue as Float
        }
        animator.duration = 300
        animator.start()

        animator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                mapActivity.onBackPressedDispatcher.onBackPressed()
                view.clearAnimation()
                view.translationY = 0F
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
    }

    private fun animateFragmentReset() {
        val animator = ValueAnimator.ofFloat(view.translationY, 0f)
        animator.addUpdateListener { animation ->
            view.translationY = animation.animatedValue as Float
        }
        animator.duration = 300
        animator.start()
    }
}