package tagme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.tagme.R
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
        val nickname = view.findViewById<TextView>(R.id.user_name)
        val friendsSince = view.findViewById<TextView>(R.id.friends_since)
        val pfp = view.findViewById<ImageView>(R.id.user_picture)
        userId = requireArguments().getInt(ARG_USER_ID)
        val mapActivity = requireActivity() as MapActivity
        var pfpId = 0
        api = mapActivity.api
        CoroutineScope(Dispatchers.Main).launch {
            val result = api.loadProfileFromWS(userId)
            if (result?.getString("status") == "success"){
                val message = JSONObject(result.getString("message"))
                val relation = message.optString("relation")
                when (relation) {
                    "friend" -> {
                        val friend = api.getFriendsData().find { it.userData.userId == userId }
                        if (friend != null) {
                            nickname.text = friend.userData.nickname
                            pfpId = friend.userData.profilePictureId
                        }
                        friendLayout.visibility = View.VISIBLE
                        blockedLayout.visibility = View.GONE
                        val dateLinked = api.parseAndConvertTimestamp(message.getString("date_linked"))
                        val timestampDateTime = LocalDateTime.parse(dateLinked.toString(), dateFormatter)
                        val timestampText = timestampDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        friendsSince.text = timestampText
                    }
                    "blocked_incoming" -> {
                        friendLayout.visibility = View.GONE
                    }
                    "blocked_outgoing" -> {
                        friendLayout.visibility = View.GONE
                    }
                    "blocked_mutual" -> {
                        friendLayout.visibility = View.GONE
                    }
                    null -> { //не связаны
                        friendLayout.visibility = View.GONE
                    }
                }
            }
            if (pfpId != 0) {
                api.getPictureData(pfpId)?.let {
                    pfp.setImageBitmap(it)
                }
            }
        }

        backButton.setOnClickListener{
            mapActivity.onBackPressedDispatcher.onBackPressed()
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }
}
