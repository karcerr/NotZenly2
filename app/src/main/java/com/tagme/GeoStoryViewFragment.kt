package com.tagme

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import kotlin.math.abs
import kotlin.math.max

class GeoStoryViewFragment : Fragment() {
    private lateinit var mapActivity: MapActivity
    private lateinit var api: API
    private lateinit var frameLayout: FrameLayout
    private lateinit var view: View
    var geoStoryId: Int = 0
    var userId: Int = 0
    lateinit var nicknameText: TextView
    lateinit var timeAgo: TextView
    lateinit var viewCounter: TextView
    lateinit var geoStoryPicture: ImageView
    lateinit var userPicture: ImageView
    lateinit var msgButton: ImageButton
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        view = inflater.inflate(R.layout.fragment_geo_story_view, container, false)
        nicknameText = view.findViewById(R.id.nickname)
        timeAgo = view.findViewById(R.id.time_ago)
        viewCounter = view.findViewById(R.id.views)
        geoStoryPicture = view.findViewById(R.id.geo_story_picture)
        userPicture = view.findViewById(R.id.user_picture)
        msgButton = view.findViewById(R.id.msg_button)
        mapActivity = requireActivity() as MapActivity
        api = mapActivity.api
        frameLayout = view.findViewById(R.id.frame_layout)
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
        val backButton = view.findViewById<ImageButton>(R.id.back_button)
        backButton.setOnClickListener{
            mapActivity.onBackPressedDispatcher.onBackPressed()
        }

        msgButton.setOnClickListener {
            if (userId != 0) {
                val conversation = api.getConversationsDataList().find { it.userData.userId == userId }
                if (conversation != null) {
                    val conversationFragment =
                        ConversationFragment.newInstance(conversation.conversationID, conversation.userData.nickname)
                    mapActivity.supportFragmentManager.beginTransaction()
                        .add(R.id.geo_story_view_fragment, conversationFragment)
                        .addToBackStack(null)
                        .commit()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.cant_message), Toast.LENGTH_LONG).show()
                }
            }
        }
        userPicture.setOnClickListener {
            if (userId != 0 && userId != api.myUserId) {
                val userProfileFragment = UserProfileFragment.newInstance(userId)
                mapActivity.fragmentManager.beginTransaction()
                    .add(R.id.geo_story_view_fragment, userProfileFragment)
                    .addToBackStack(null)
                    .commit()
            }
        }
        return view
    }
}

