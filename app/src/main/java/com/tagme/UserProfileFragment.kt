package com.tagme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
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
        val friendLayout = view.findViewById<LinearLayout>(R.id.friend_layout)
        val blockedLayout = view.findViewById<LinearLayout>(R.id.blocked_layout)
        val headerLayout = view.findViewById<ConstraintLayout>(R.id.user_header)
        val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar)
        val nickname = view.findViewById<TextView>(R.id.user_name)
        val friendsSince = view.findViewById<TextView>(R.id.friends_since)
        val relationText = view.findViewById<TextView>(R.id.relation_text)
        val darkOverlay = view.findViewById<View>(R.id.dark_overlay)
        val areYouSureLayout = view.findViewById<LinearLayout>(R.id.are_you_sure_layout)
        val yesButton = view.findViewById<Button>(R.id.yes_button)
        val noButton = view.findViewById<Button>(R.id.no_button)
        val conversationLayout = view.findViewById<LinearLayout>(R.id.conversation_layout)
        val findOnMapLayout = view.findViewById<LinearLayout>(R.id.find_on_map_layout)
        val unfriendLayout = view.findViewById<LinearLayout>(R.id.unfriend_layout)
        val blockLayout = view.findViewById<LinearLayout>(R.id.block_layout)
        pfp = view.findViewById(R.id.user_picture)

        friendLayout.visibility = View.GONE
        blockedLayout.visibility = View.GONE
        headerLayout.visibility = View.GONE
        progressBar.visibility = View.VISIBLE

        val mapActivity = requireActivity() as MapActivity
        var pfpId = 0
        api = mapActivity.api
        userId = requireArguments().getInt(ARG_USER_ID)

        backButton.setOnClickListener{
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
        CoroutineScope(Dispatchers.Main).launch {
            val result = api.loadProfileFromWS(userId)
            if (result?.getString("status") == "success"){
                val message = JSONObject(result.getString("message"))
                val relation = message.optString("relation")
                pfpId = message.optInt("picture_id", 0)
                nickname.text = message.getString("nickname")
                setPic(pfpId)
                when (relation) {
                    "friend" -> {
                        val dateLinked = api.parseAndConvertTimestamp(message.getString("date_linked"))
                        val timestampDateTime = LocalDateTime.parse(dateLinked.toString(), dateFormatter)
                        val timestampText = timestampDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        friendsSince.text = getString(R.string.friends_since_format, timestampText)
                        relationText.text = getString(R.string.friends)
                        friendLayout.visibility = View.VISIBLE
                        blockLayout.setOnClickListener {
                            darkOverlay.visibility = View.VISIBLE
                            areYouSureLayout.visibility = View.VISIBLE
                            yesButton.setOnClickListener {
                                CoroutineScope(Dispatchers.Main).launch {
                                    api.blockUserWS(userId)
                                    parentFragmentManager.beginTransaction().detach(this@UserProfileFragment).commitNow()
                                    parentFragmentManager.beginTransaction().attach(this@UserProfileFragment).commitNow()
                                }
                            }
                        }
                        unfriendLayout.setOnClickListener {
                            darkOverlay.visibility = View.VISIBLE
                            areYouSureLayout.visibility = View.VISIBLE
                            yesButton.setOnClickListener {
                                CoroutineScope(Dispatchers.Main).launch {
                                    api.deleteFriendWS(userId)
                                    parentFragmentManager.beginTransaction().detach(this@UserProfileFragment).commitNow()
                                    parentFragmentManager.beginTransaction().attach(this@UserProfileFragment).commitNow()
                                }
                            }
                        }
                    }
                    "block_incoming" -> {
                        relationText.setTextColor(ContextCompat.getColor(mapActivity, R.color.reddish))
                        relationText.text = getString(R.string.blocked_incoming)
                        setPic(pfpId)
                    }
                    "block_outgoing" -> {
                        relationText.setTextColor(ContextCompat.getColor(mapActivity, R.color.reddish))
                        relationText.text = getString(R.string.blocked_outgoing)
                        setPic(pfpId)
                    }
                    "block_mutual" -> {
                        relationText.setTextColor(ContextCompat.getColor(mapActivity, R.color.reddish))
                        relationText.text = getString(R.string.blocked_mutual)
                        setPic(pfpId)
                    }
                    "request_outgoing" -> {
                        relationText.setTextColor(ContextCompat.getColor(mapActivity, R.color.white))
                        relationText.text = getString(R.string.outgoing_request)
                        val requestee = api.getFriendRequestDataList().find {it.userData.userId == userId}
                        if (requestee != null) {
                            nickname.text = requestee.userData.nickname
                            pfpId = requestee.userData.profilePictureId
                        }

                        setPic(pfpId)
                    }
                    "request_incoming" -> {
                        relationText.setTextColor(ContextCompat.getColor(mapActivity, R.color.white))
                        relationText.text = getString(R.string.incoming_request)
                        val requestee = api.getFriendRequestDataList().find {it.userData.userId == userId}
                        if (requestee != null) {
                            nickname.text = requestee.userData.nickname
                            pfpId = requestee.userData.profilePictureId
                        }

                        setPic(pfpId)
                    }
                    "default", null -> { //не связаны
                        relationText.setTextColor(ContextCompat.getColor(mapActivity, R.color.white))
                        relationText.text = getString(R.string.no_relation)
                    }
                }
            }

            headerLayout.visibility = View.VISIBLE
            progressBar.visibility = View.GONE
        }

        return view
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
