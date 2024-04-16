package tagme

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tagme.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {
    private lateinit var friendAdapter: FriendAdapter
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        val addFriendButton = view.findViewById<Button>(R.id.add_friend_button)
        val addFriendWindow = view.findViewById<View>(R.id.add_friend_window)
        val darkOverlay = view.findViewById<View>(R.id.dark_overlay)
        val api = (requireActivity() as MapActivity).api
        val sendRequestButton = view.findViewById<Button>(R.id.send_request_button)
        val statusText = view.findViewById<TextView>(R.id.status_text)

        addFriendButton.setOnClickListener {
            addFriendWindow.visibility = View.VISIBLE
            darkOverlay.visibility = View.VISIBLE
        }
        darkOverlay.setOnClickListener {
            addFriendWindow.visibility = View.GONE
            darkOverlay.visibility = View.GONE
        }
        sendRequestButton.setOnClickListener{
            val nickname = view.findViewById<EditText>(R.id.nickname_edit_text).text.toString()
            CoroutineScope(Dispatchers.Main).launch {
                val answer = api.sendFriendRequest(nickname)
                if (answer != null) {
                    val message = answer.getString("message")
                    if (answer.getString("status") == "success") {
                        statusText.setTextColor(Color.GREEN)
                        statusText.text = "Friend request was sent!"
                    } else {
                        statusText.setTextColor(Color.RED)
                        if (message == "no user") {
                            statusText.text = "User not found"
                        } else {
                            statusText.text = message
                        }
                    }
                    statusText.visibility = View.VISIBLE
                }
            }
        }
        val recyclerView: RecyclerView = view.findViewById(R.id.friends_recycler_view)
        friendAdapter = FriendAdapter(api.getFriendsData())
        recyclerView.adapter = friendAdapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        return view
    }
}
class FriendAdapter(private val friendList: List<API.FriendData>) : RecyclerView.Adapter<FriendAdapter.FriendViewHolder>() {

    inner class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.friend_name)
        val pictureImageView: ImageView = itemView.findViewById(R.id.friend_picture)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.friend_item, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = friendList[position]

        holder.nameTextView.text = friend.nickname
        // Set profile picture if available, otherwise set placeholder
        friend.profilePicture?.let {
            val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
            holder.pictureImageView.setImageBitmap(bitmap)
        } ?: holder.pictureImageView.setImageResource(R.drawable.person_placeholder)
    }

    override fun getItemCount(): Int {
        return friendList.size
    }
}
