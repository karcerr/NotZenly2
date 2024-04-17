package tagme

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tagme.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {
    private lateinit var friendAdapter: FriendAdapter
    private lateinit var friendRequestAdapter: FriendRequestAdapter
    private lateinit var api: API
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)

        val addFriendButton = view.findViewById<Button>(R.id.add_friend_button)
        val addFriendWindow = view.findViewById<View>(R.id.add_friend_window)
        val nicknameText = view.findViewById<TextView>(R.id.nickname_text)
        val darkOverlay = view.findViewById<View>(R.id.dark_overlay)
        api = (requireActivity() as MapActivity).api
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
        nicknameText.text = api.myNickname
        CoroutineScope(Dispatchers.Main).launch {
            api.getFriendRequests()
        }
        val myLinearLayoutManager = object : LinearLayoutManager(context) {
            override fun canScrollVertically(): Boolean {
                return false
            }
        }
        val friendRecyclerView: RecyclerView = view.findViewById(R.id.friends_recycler_view)
        friendAdapter = FriendAdapter(api.getFriendLocationsData(), api)
        friendRecyclerView.adapter = friendAdapter
        friendRecyclerView.layoutManager = MyLinearLayoutManager(requireContext())

        val friendRequestsRecyclerView: RecyclerView = view.findViewById(R.id.friend_requests_recycler_view)
        friendRequestAdapter = FriendRequestAdapter(api.getFriendRequestsData(), api)
        friendRequestsRecyclerView.adapter = friendRequestAdapter
        friendRequestsRecyclerView.layoutManager = MyLinearLayoutManager(requireContext())


        return view
    }
}
class MyLinearLayoutManager(context: Context) : LinearLayoutManager(context) {
    override fun canScrollVertically(): Boolean {
        return false
    }
}
class FriendAdapter(
    private val friendList: List<API.FriendLocationsData>,
    private val api: API
) : RecyclerView.Adapter<FriendAdapter.FriendViewHolder>() {
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

        holder.nameTextView.text = friend.userData.nickname
        // Set profile picture if available, otherwise set placeholder
        friend.userData.profilePicture?.let {
            val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
            holder.pictureImageView.setImageBitmap(bitmap)
        } ?: holder.pictureImageView.setImageResource(R.drawable.person_placeholder)
    }

    override fun getItemCount(): Int {
        return friendList.size
    }
}
class FriendRequestAdapter(
    private val requestList: List<API.FriendRequestsData>,
    private val api: API
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {    inner class IncomingFriendRequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.friend_name)
        val pictureImageView: ImageView = itemView.findViewById(R.id.friend_picture)
        val acceptButton: ImageButton = itemView.findViewById(R.id.accept_button)
        val denyButton: ImageButton = itemView.findViewById(R.id.deny_button)
    }

    inner class OutgoingFriendRequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.friend_name)
        val pictureImageView: ImageView = itemView.findViewById(R.id.friend_picture)
        val cancelButton: ImageButton = itemView.findViewById(R.id.cancel_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_INCOMING -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.incoming_friend_request_item, parent, false)
                IncomingFriendRequestViewHolder(view)
            }
            VIEW_TYPE_OUTGOING -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.outgoing_friend_request_item, parent, false)
                OutgoingFriendRequestViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val requestee = requestList[position]
        when (holder.itemViewType) {
            VIEW_TYPE_INCOMING -> {
                val incomingHolder = holder as IncomingFriendRequestViewHolder
                incomingHolder.nameTextView.text = requestee.userData.nickname
                requestee.userData.profilePicture?.let {
                    val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                    incomingHolder.pictureImageView.setImageBitmap(bitmap)
                } ?: incomingHolder.pictureImageView.setImageResource(R.drawable.person_placeholder)
                incomingHolder.acceptButton.tag = requestee.userData.userId
                incomingHolder.denyButton.tag = requestee.userData.userId

                incomingHolder.acceptButton.setOnClickListener { view ->
                    val userId = view.tag as Int
                    CoroutineScope(Dispatchers.Main).launch {
                        api.acceptFriendRequest(userId)
                    }
                }
                incomingHolder.denyButton.setOnClickListener { view ->
                    val userId = view.tag as Int
                    CoroutineScope(Dispatchers.Main).launch {
                        api.denyFriendRequest(userId)
                    }
                }
            }
            VIEW_TYPE_OUTGOING -> {
                val outgoingHolder = holder as OutgoingFriendRequestViewHolder
                outgoingHolder.nameTextView.text = requestee.userData.nickname
                requestee.userData.profilePicture?.let {
                    val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                    outgoingHolder.pictureImageView.setImageBitmap(bitmap)
                } ?: outgoingHolder.pictureImageView.setImageResource(R.drawable.person_placeholder)
                outgoingHolder.cancelButton.tag = requestee.userData.userId

                outgoingHolder.cancelButton.setOnClickListener { view ->
                    val userId = view.tag as Int
                    CoroutineScope(Dispatchers.Main).launch {
                        api.cancelFriendRequest(userId)
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return requestList.size
    }
    override fun getItemViewType(position: Int): Int {
        val friend = requestList[position]
        return if (friend.relation == "incoming") {
            VIEW_TYPE_INCOMING
        } else {
            VIEW_TYPE_OUTGOING
        }
    }
    companion object {
        private const val VIEW_TYPE_INCOMING = 0
        private const val VIEW_TYPE_OUTGOING = 1
    }
}
