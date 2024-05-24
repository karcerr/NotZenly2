package com.tagme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.tagme.R

class GeoStoryViewFragment : Fragment() {
    private lateinit var mapActivity: MapActivity
    private lateinit var api: API
    var geoStoryId: Int = 0
    var userId: Int = 0
    lateinit var nicknameText: TextView
    lateinit var viewCounter: TextView
    lateinit var geoStoryPicture: ImageView
    lateinit var userPicture: ImageView
    lateinit var msgButton: ImageButton
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_geo_story_view, container, false)
        nicknameText = view.findViewById(R.id.nickname)
        viewCounter = view.findViewById(R.id.views)
        geoStoryPicture = view.findViewById(R.id.geo_story_picture)
        userPicture = view.findViewById(R.id.user_picture)
        msgButton = view.findViewById(R.id.msg_button)
        mapActivity = requireActivity() as MapActivity
        api = mapActivity.api
        val backButton = view.findViewById<ImageButton>(R.id.back_button)
        backButton.setOnClickListener{
            mapActivity.onBackPressedDispatcher.onBackPressed()
        }

        msgButton.setOnClickListener {
            if (userId != 0) {
                val conversation = api.getConversationsData().find { it.userData.userId == userId }
                if (conversation != null) {
                    val conversationFragment =
                        ConversationFragment.newInstance(conversation.conversationID, conversation.userData.nickname)
                    mapActivity.supportFragmentManager.beginTransaction()
                        .replace(R.id.profile_fragment, conversationFragment)
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
                    .replace(R.id.profile_fragment, userProfileFragment)
                    .addToBackStack(null)
                    .commit()
            }
        }
        return view
    }

}

