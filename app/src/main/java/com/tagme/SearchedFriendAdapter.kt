package com.tagme

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SearchedFriendsAdapter(
    private val context: MapActivity,
    private var friends: MutableList<API.FriendData>
) : RecyclerView.Adapter<SearchedFriendsAdapter.FriendViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.searched_friend_item, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = friends[position]
        holder.bind(friend)
    }

    override fun getItemCount(): Int = friends.size

    fun updateData(newFriends: List<API.FriendData>) {
        val diffCallback = FriendsDiffCallback(friends, newFriends)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        friends = newFriends.map {it.copy()}.toMutableList()
        diffResult.dispatchUpdatesTo(this)
    }

    inner class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ShapeableImageView = itemView.findViewById(R.id.friend_picture)
        private val nicknameTextView: TextView = itemView.findViewById(R.id.friend_name)
        private val locateButton: ImageButton = itemView.findViewById(R.id.locate_friend_button)
        private val textFriendButton: ImageButton = itemView.findViewById(R.id.text_friend_button)
        private val friendLayout: LinearLayout = itemView.findViewById(R.id.friend_layout)
        fun bind(friend: API.FriendData) {
            nicknameTextView.text = friend.userData.nickname
            if (friend.userData.profilePictureId != 0) {
                context.coroutineScope.launch(Dispatchers.Main) {
                    val bitmap = context.api.getPictureData(friend.userData.profilePictureId)
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            }
            friendLayout.setOnClickListener {
                val userProfileFragment = UserProfileFragment.newInstance(friend.userData.userId)
                context.fragmentManager.beginTransaction()
                    .add(R.id.profile_fragment, userProfileFragment)
                    .addToBackStack(null)
                    .commit()
                context.hideSearchLayout()
            }
            locateButton.setOnClickListener {
                val friendOverlay = context.friendOverlays[friend.userData.userId]
                if (friendOverlay != null) {
                    context.hideSearchLayout()
                    context.centralizeMapAnimated(
                        friendOverlay,
                        friend.userData.userId,
                        isCenterTargetUser = true,
                        withZoom = true
                    )
                } else {
                    Toast.makeText(context, context.getString(R.string.no_location), Toast.LENGTH_LONG).show()
                }
            }
            textFriendButton.setOnClickListener {
                val conversation = context.api.getConversationsDataList().find { it.userData.userId == friend.userData.userId }
                if (conversation != null) {
                    val conversationFragment =
                        ConversationFragment.newInstance(conversation.conversationID, conversation.userData.nickname)
                    context.supportFragmentManager.beginTransaction()
                        .add(R.id.profile_fragment, conversationFragment)
                        .addToBackStack(null)
                        .commit()
                    context.hideSearchLayout()
                }
            }
        }
    }

    class FriendsDiffCallback(private val oldFriends: List<API.FriendData>, private val newFriends: List<API.FriendData>) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldFriends.size

        override fun getNewListSize(): Int = newFriends.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldFriends[oldItemPosition].userData.userId == newFriends[newItemPosition].userData.userId
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldFriends[oldItemPosition]
            val newItem = newFriends[newItemPosition]
            val didChange = oldItem == newItem
            return didChange
        }
    }
}
