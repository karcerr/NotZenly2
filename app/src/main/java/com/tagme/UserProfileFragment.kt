package com.tagme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class UserProfileFragment : Fragment() {
    private lateinit var api: API
    private var userId: Int = 0
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS][.SS][.S]")
    private lateinit var pfp: ImageView
    companion object {
        private const val ARG_USER_ID = "userId"

        fun newInstance(userId: Int): UserProfileFragment {
            val fragment = UserProfileFragment()
            val args = Bundle()
            args.putInt(ARG_USER_ID, userId)
            fragment.arguments = args
            return fragment
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_user_profile, container, false)
        val backButton = view.findViewById<ImageButton>(R.id.back_button)
        val headerLayout = view.findViewById<ConstraintLayout>(R.id.user_header)
        val progressBarFrame = view.findViewById<ConstraintLayout>(R.id.progress_bar_layout)
        val nicknameTextView = view.findViewById<TextView>(R.id.user_name)
        val relationText = view.findViewById<TextView>(R.id.relation_text)
        val tagCounter = view.findViewById<TextView>(R.id.tag_counter)
        val darkOverlay = view.findViewById<View>(R.id.dark_overlay)
        val areYouSureLayout = view.findViewById<LinearLayout>(R.id.are_you_sure_layout)
        val areYouSureText = view.findViewById<TextView>(R.id.are_you_sure_text)
        val yesButton = view.findViewById<Button>(R.id.yes_button)
        val noButton = view.findViewById<Button>(R.id.no_button)
        var nickname: String = ""
        val blockButton = view.findViewById<ImageButton>(R.id.block_button)

        pfp = view.findViewById(R.id.user_picture)

        headerLayout.visibility = View.GONE
        progressBarFrame.visibility = View.VISIBLE

        val mapActivity = requireActivity() as MapActivity
        var pfpId: Int
        api = mapActivity.api
        val coroutineScope = CoroutineScope(Dispatchers.Main)
        userId = requireArguments().getInt(ARG_USER_ID)

        backButton.setOnClickListener{
            updateFriends(coroutineScope, mapActivity)
            mapActivity.onBackPressedDispatcher.onBackPressed()
        }

        darkOverlay.setOnClickListener {
            darkOverlay.visibility = View.GONE
            areYouSureLayout.visibility = View.GONE
        }
        noButton.setOnClickListener {
            darkOverlay.visibility = View.GONE
            areYouSureLayout.visibility = View.GONE
        }
        val blockListener = View.OnClickListener {
            darkOverlay.visibility = View.VISIBLE
            areYouSureText.text = mapActivity.getString(R.string.are_you_sure_block_format, nickname)
            areYouSureLayout.visibility = View.VISIBLE
            yesButton.setOnClickListener {
                coroutineScope.launch {
                    api.blockUserWS(userId)
                    reattachSelf()
                }
            }
        }
        blockButton.setOnClickListener(blockListener)
        coroutineScope.launch {
            val result = api.loadProfileFromWS(userId)
            if (result?.optString("status") == "success"){
                val message = JSONObject(result.getString("message"))
                val relation = message.optString("relation")
                pfpId = message.optInt("picture_id", 0)
                nickname = message.getString("nickname")
                nicknameTextView.text = nickname
                tagCounter.text = getString(R.string.tag_counter_format, message.optInt("user_score", 0))
                setPic(pfpId)
                when (relation) {
                    "friend" -> {
                        val friendLayout = view.findViewById<LinearLayout>(R.id.friend_layout)
                        val conversationLayout = view.findViewById<LinearLayout>(R.id.conversation_layout)
                        val findOnMapLayout = view.findViewById<LinearLayout>(R.id.find_on_map_layout)
                        val unfriendLayout = view.findViewById<LinearLayout>(R.id.unfriend_layout)
                        val blockLayout = view.findViewById<LinearLayout>(R.id.block_layout)
                        val friendsSince = view.findViewById<TextView>(R.id.friends_since)
                        val dateLinked = api.parseAndConvertTimestamp(message.getString("date_linked"))
                        val timestampDateTime = LocalDateTime.parse(dateLinked.toString(), dateFormatter)
                        val timestampText = timestampDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        friendsSince.text = getString(R.string.friends_since_format, timestampText)
                        relationText.text = getString(R.string.friends)
                        friendLayout.visibility = View.VISIBLE
                        blockButton.visibility = View.GONE
                        friendsSince.visibility = View.VISIBLE
                        blockLayout.setOnClickListener(blockListener)
                        unfriendLayout.setOnClickListener {
                            darkOverlay.visibility = View.VISIBLE
                            areYouSureText.text = mapActivity.getString(R.string.are_you_sure_unfriend_format, nickname)
                            areYouSureLayout.visibility = View.VISIBLE
                            yesButton.setOnClickListener {
                                coroutineScope.launch {
                                    api.deleteFriendWS(userId)
                                    reattachSelf()
                                }
                            }
                        }
                        conversationLayout.setOnClickListener {
                            val conversation = api.getConversationsDataList().find {it.userData.userId == userId}
                            if (conversation == null) return@setOnClickListener
                            val conversationFragment = ConversationFragment.newInstance(conversation.conversationID, conversation.userData.nickname)
                            mapActivity.fragmentManager.beginTransaction()
                                .add(R.id.profile_fragment, conversationFragment)
                                .addToBackStack(null)
                                .commit()
                        }
                        findOnMapLayout.setOnClickListener {
                            val friend = api.getFriendsData().find{it.userData.userId == userId}
                            val location = friend?.location
                            if (location != null) {
                                val friendLocation = GeoPoint(location.latitude, location.longitude)
                                mapActivity.centralizeMapAnimated(
                                    friendLocation,
                                    friend.userData.userId,
                                    isCenterTargetUser = true,
                                    withZoom = true,
                                    mutableListOf()
                                )
                                val transaction = mapActivity.fragmentManager.beginTransaction()
                                transaction.hide(mapActivity.profileFragment)
                                transaction.commit()
                                mapActivity.onBackPressedDispatcher.onBackPressed()
                            } else {
                                Toast.makeText(context, getString(R.string.no_location), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    "block_incoming" -> {
                        relationText.setTextColor(ContextCompat.getColor(mapActivity, R.color.reddish))
                        relationText.text = getString(R.string.blocked_incoming)
                    }
                    "block_outgoing", "block_mutual" -> {
                        val blockedLayout = view.findViewById<LinearLayout>(R.id.blocked_layout)
                        val unblockLayout = view.findViewById<TextView>(R.id.unblock_layout)
                        blockedLayout.visibility = View.VISIBLE
                        blockButton.visibility = View.GONE
                        relationText.setTextColor(ContextCompat.getColor(mapActivity, R.color.reddish))
                        relationText.text = getString(R.string.blocked_outgoing)
                        unblockLayout.setOnClickListener {
                            coroutineScope.launch {
                                api.unblockUserWS(userId)
                                reattachSelf()
                            }
                        }
                        relationText.text =  if (relation == "block_outgoing") getString(R.string.blocked_outgoing) else getString(R.string.blocked_mutual)
                    }
                    "request_outgoing" -> {
                        val outgoingFriendRequestLayout = view.findViewById<LinearLayout>(R.id.outgoing_friend_request_layout)
                        val cancelFriendRequestLayout = view.findViewById<TextView>(R.id.cancel_friend_request_layout)
                        relationText.setTextColor(ContextCompat.getColor(mapActivity, R.color.white))
                        relationText.text = getString(R.string.outgoing_request)
                        outgoingFriendRequestLayout.visibility = View.VISIBLE
                        cancelFriendRequestLayout.setOnClickListener {
                            coroutineScope.launch {
                                api.cancelFriendRequest(userId)
                                reattachSelf()
                            }
                        }
                    }
                    "request_incoming" -> {
                        val incomingFriendRequestLayout = view.findViewById<LinearLayout>(R.id.incoming_friend_request_layout)
                        val acceptButton = view.findViewById<ImageButton>(R.id.accept_button)
                        val denyButton = view.findViewById<ImageButton>(R.id.deny_button)
                        relationText.setTextColor(ContextCompat.getColor(mapActivity, R.color.white))
                        relationText.text = getString(R.string.incoming_request)
                        incomingFriendRequestLayout.visibility = View.VISIBLE
                        acceptButton.setOnClickListener {
                            coroutineScope.launch {
                                api.acceptFriendRequest(userId)
                                reattachSelf()
                            }
                        }
                        denyButton.setOnClickListener {
                            coroutineScope.launch {
                                api.denyFriendRequest(userId)
                                reattachSelf()
                            }
                        }
                    }
                    "default", null, "null" -> { //не связаны
                        val notRelatedLayout = view.findViewById<LinearLayout>(R.id.not_related_layout)
                        val sendFriendRequestLayout = view.findViewById<TextView>(R.id.send_friend_request_layout)
                        relationText.setTextColor(ContextCompat.getColor(mapActivity, R.color.white))
                        relationText.text = getString(R.string.no_relation)
                        notRelatedLayout.visibility = View.VISIBLE
                        sendFriendRequestLayout.setOnClickListener {
                            coroutineScope.launch {
                                val answer = api.sendFriendRequestToWS(nickname)
                                val toastMessage = if (answer?.getString("status") == "success") getString(R.string.friend_request_sent)
                                else getString(R.string.something_went_wrong)
                                Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_SHORT).show()
                                reattachSelf()
                            }
                        }
                    }
                }
            }

            headerLayout.visibility = View.VISIBLE
            progressBarFrame.visibility = View.GONE
        }
        mapActivity.onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (darkOverlay.visibility == View.VISIBLE) {
                    darkOverlay.visibility = View.GONE
                    areYouSureLayout.visibility = View.GONE
                } else {
                    updateFriends(coroutineScope, mapActivity)
                    isEnabled = false
                    mapActivity.onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        return view
    }
    private fun updateFriends(coroutineScope: CoroutineScope, mapActivity: MapActivity){
        coroutineScope.launch {
            api.getFriendsFromWS()
            val updatedFriends = api.getFriendsData()
            mapActivity.profileFragment.friendAdapter.updateData(updatedFriends)
        }
    }
    private fun reattachSelf(){
        parentFragmentManager.beginTransaction().detach(this@UserProfileFragment).commitNow()
        parentFragmentManager.beginTransaction().attach(this@UserProfileFragment).commitNow()
    }
    private suspend fun setPic(picId: Int) {
        if (picId != 0) {
            api.getPictureData(picId)?.let {
                pfp.setImageBitmap(it)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }
}
