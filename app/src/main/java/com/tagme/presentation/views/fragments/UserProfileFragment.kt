package com.tagme.presentation.views.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.tagme.R
import com.tagme.presentation.utils.setupSwipeGesture
import com.tagme.presentation.viewmodels.MapActivityViewModel
import com.tagme.presentation.views.CustomNestedScrollView
import com.tagme.presentation.views.activities.MapActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class UserProfileFragment : Fragment() {
    private lateinit var view: View
    private lateinit var viewModel: MapActivityViewModel
    private var userId: Int = 0
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS][.SS][.S]")
    private lateinit var pfp: ImageView
    private lateinit var frameLayout: FrameLayout
    private lateinit var mapActivity: MapActivity
    private lateinit var coroutineScope: CoroutineScope
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
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        view = inflater.inflate(R.layout.fragment_user_profile, container, false)
        mapActivity = requireActivity() as MapActivity
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
        val blockButton = view.findViewById<ImageButton>(R.id.block_button)
        val nestedScrollView = view.findViewById<CustomNestedScrollView>(R.id.profile_nested_scroll_view)
        val linearLayout = view.findViewById<LinearLayout>(R.id.profile_linear_layout)
        frameLayout = view.findViewById(R.id.profile_frame_layout)
        setupSwipeGesture(
            this,
            nestedScrollView,
            linearLayout,
            view,
            mapActivity
        )

        var nickname = ""

        pfp = view.findViewById(R.id.user_picture)

        headerLayout.visibility = View.GONE
        progressBarFrame.visibility = View.VISIBLE

        var pfpId: Int
        viewModel = mapActivity.viewModel
        coroutineScope = CoroutineScope(Dispatchers.Main)
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
                    viewModel.blockUserWS(userId)
                    updateFriends(coroutineScope, context as MapActivity)
                    (context as MapActivity).setCenteredFalse()
                    reattachSelf()
                }
            }
        }
        blockButton.setOnClickListener(blockListener)
        coroutineScope.launch {
            val result = viewModel.loadProfileFromWS(userId)
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
                        val dateLinked = viewModel.parseAndConvertTimestamp(message.getString("date_linked"))
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
                                    viewModel.deleteFriendWS(userId)
                                    updateFriends(coroutineScope, context as MapActivity)
                                    (context as MapActivity).setCenteredFalse()
                                    reattachSelf()
                                }
                            }
                        }
                        conversationLayout.setOnClickListener {
                            val conversation = viewModel.getConversationsDataList().find {it.userData.userId == userId}
                            if (conversation == null) return@setOnClickListener
                            val conversationFragment = ConversationFragment.newInstance(
                                conversation.conversationID,
                                conversation.userData.nickname
                            )
                            mapActivity.fragmentManager.beginTransaction()
                                .add(R.id.profile_fragment, conversationFragment)
                                .addToBackStack(null)
                                .commit()
                        }
                        findOnMapLayout.setOnClickListener {
                            val friend = viewModel.getFriendDataList().find{it.userData.userId == userId}
                            if (friend != null) {
                                val friendOverlay = mapActivity.friendOverlays[friend.userData.userId]
                                if (friendOverlay != null) {
                                    mapActivity.centralizeMapAnimated(
                                        friendOverlay,
                                        friend.userData.userId,
                                        isCenterTargetUser = true,
                                        withZoom = true
                                    )
                                    val transaction = mapActivity.fragmentManager.beginTransaction()
                                    transaction.hide(mapActivity.profileFragment)
                                    transaction.commit()
                                    while (mapActivity.supportFragmentManager.backStackEntryCount > 0) {
                                        mapActivity.supportFragmentManager.popBackStackImmediate()
                                    }
                                } else {
                                    Toast.makeText(context, getString(R.string.no_location), Toast.LENGTH_LONG).show()
                                }
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
                                viewModel.unblockUserWS(userId)
                                reattachSelf()
                            }
                        }
                        relationText.text =  if (relation == "block_outgoing") getString(R.string.blocked_outgoing) else getString(
                            R.string.blocked_mutual
                        )
                    }
                    "request_outgoing" -> {
                        val outgoingFriendRequestLayout = view.findViewById<LinearLayout>(R.id.outgoing_friend_request_layout)
                        val cancelFriendRequestLayout = view.findViewById<TextView>(R.id.cancel_friend_request_layout)
                        relationText.setTextColor(ContextCompat.getColor(mapActivity, R.color.white))
                        relationText.text = getString(R.string.outgoing_request)
                        outgoingFriendRequestLayout.visibility = View.VISIBLE
                        cancelFriendRequestLayout.setOnClickListener {
                            coroutineScope.launch {
                                viewModel.cancelFriendRequest(userId)
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
                                viewModel.acceptFriendRequest(userId)
                                reattachSelf()
                            }
                        }
                        denyButton.setOnClickListener {
                            coroutineScope.launch {
                                viewModel.denyFriendRequest(userId)
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
                                val answer = viewModel.sendFriendRequestToWS(nickname)
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
            viewModel.updateFriendRequestsAndFriendsWS()
            val updatedFriends = viewModel.getFriendDataList()
            mapActivity.profileFragment.friendAdapter.updateData(updatedFriends)
        }
    }
    private fun reattachSelf(){
        parentFragmentManager.beginTransaction().detach(this@UserProfileFragment).commitNow()
        parentFragmentManager.beginTransaction().attach(this@UserProfileFragment).commitNow()
    }
    private suspend fun setPic(picId: Int) {
        if (picId != 0) {
            viewModel.getPictureData(picId)?.let {
                pfp.setImageBitmap(it)
            }
        }
    }
}
