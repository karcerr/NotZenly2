package tagme

import android.content.res.Resources
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.tagme.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UserProfileDialogFragment : DialogFragment() {
    private lateinit var api: API

    companion object {
        fun newInstance(userId: Int): UserProfileDialogFragment {
            val fragment = UserProfileDialogFragment()
            val args = Bundle()
            args.putInt("userId", userId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        api = (requireActivity() as MapActivity).api
        return inflater.inflate(R.layout.fragment_user_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("Tagme_custom_log_DiaFr", "onViewCreated called")
        super.onViewCreated(view, savedInstanceState)
        val userId = arguments?.getInt("userId")
        val nameText = view.findViewById<TextView>(R.id.user_name)
        val pfp = view.findViewById<ImageView>(R.id.user_picture)
        var pfpId = 0
        val friend = api.getFriendsData().find { it.userData.userId == userId}

        if (friend != null) {
            if (friend.userData.profilePictureId != 0) {
                pfpId = friend.userData.profilePictureId
            }
            nameText.text = friend.userData.nickname
        } else {
            val user = api.getFriendRequestData().find { it.userData.userId == userId }
            if (user != null) {
                if (user.userData.profilePictureId != 0) {
                    pfpId = user.userData.profilePictureId
                }
                nameText.text = user.userData.nickname
            }
        }
        if (pfpId != 0) {
            CoroutineScope(Dispatchers.Main).launch {
                val pictureData = api.getPicturesData().find { it.pictureId == pfpId }
                if (pictureData?.imagePath == null) {
                    api.getPictureFromWS(pfpId)
                }
                // Load the image from cache
                val bitmap = api.getPictureData(requireContext(), pfpId)
                bitmap?.let {
                    pfp.setImageBitmap(it)
                }
            }
        }

        setSizePercent(100, 100)
    }
}
fun DialogFragment.setSizePercent(widthPercentage: Int, heightPercentage: Int) {
    val widthPercent = widthPercentage.toFloat() / 100
    val heightPercent = heightPercentage.toFloat() / 100

    val dm = Resources.getSystem().displayMetrics
    val rect = dm.run { Rect(0, 0, widthPixels, heightPixels) }

    val percentWidth = rect.width() * widthPercent
    val percentHeight = rect.height() * heightPercent

    dialog?.window?.setLayout(percentWidth.toInt(), percentHeight.toInt())
}