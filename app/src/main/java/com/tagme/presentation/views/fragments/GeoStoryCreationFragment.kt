package com.tagme.presentation.views.fragments

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.tagme.R
import com.tagme.data.handlers.ImageHandler
import com.tagme.presentation.utils.SwipeGestureListener
import com.tagme.presentation.viewmodels.MapActivityViewModel
import com.tagme.presentation.views.activities.MapActivity
import com.tagme.utils.animateFragmentClose
import com.tagme.utils.animateFragmentReset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

class GeoStoryCreationFragment : Fragment() {
    private lateinit var view: View
    private lateinit var viewModel: MapActivityViewModel
    private lateinit var geoStoryPreview: ImageView
    private lateinit var geoStoryPreviewIcon: ImageView
    private lateinit var compressingStatus: LinearLayout
    private lateinit var imageHandler: ImageHandler
    private lateinit var mapActivity: MapActivity

    @SuppressLint("ClickableViewAccessibility")
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
        viewModel = mapActivity.viewModel
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
                    animateFragmentClose(view)
                } else {
                    animateFragmentReset(view)
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
                val result = viewModel.postGeoStory(imageHandler, privacy)
                if (result?.getString("message") == "success") {
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
}