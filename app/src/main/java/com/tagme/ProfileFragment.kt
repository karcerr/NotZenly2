package com.tagme

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {
    private lateinit var view: View
    lateinit var friendAdapter: FriendAdapter
    lateinit var friendRequestAdapter: FriendRequestAdapter
    private lateinit var myProfilePic: ImageView
    private lateinit var addPfpPic: ImageView
    private lateinit var api: API
    private lateinit var mapActivity: MapActivity
    private var friendRequestUpdateRunnable: Runnable? = null
    var friendRequestUpdateHandler: Handler? = null
    private val friendRequestInterval = 2000L
    private lateinit var friendRequestsRecyclerView: RecyclerView
    private lateinit var imageHandler: ImageHandler
    lateinit var friendRequestsHeader: LinearLayout
    lateinit var nestedScrollView: CustomNestedScrollView
    lateinit var myTagCounter: TextView

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        view = inflater.inflate(R.layout.fragment_profile, container, false)
        mapActivity = requireActivity() as MapActivity
        api = mapActivity.api
        val coroutineScope = CoroutineScope(Dispatchers.Main)
        val addFriendButton = view.findViewById<ImageButton>(R.id.add_friend_button)
        val changeNicknameButton1 = view.findViewById<ImageButton>(R.id.change_nickname_button1)
        val addFriendWindow = view.findViewById<View>(R.id.add_friend_window)
        val changeNicknameWindow = view.findViewById<View>(R.id.change_nickname_window)
        val ratingLayout = view.findViewById<LinearLayout>(R.id.rating_layout)
        friendRequestsHeader = view.findViewById(R.id.friend_requests_header)
        val nicknameText = view.findViewById<TextView>(R.id.nickname_text)
        myTagCounter = view.findViewById(R.id.my_tag_counter)
        myProfilePic = view.findViewById(R.id.profile_picture)
        addPfpPic = view.findViewById(R.id.add_profile_picture_icon)
        val compressingStatus = view.findViewById<LinearLayout>(R.id.compressing_status)
        val darkOverlay = view.findViewById<View>(R.id.dark_overlay)
        val requestInput = view.findViewById<EditText>(R.id.add_friend_nickname_edit_text)
        val changeNicknameInput = view.findViewById<EditText>(R.id.change_nickname_edit_text)
        val backButton = view.findViewById<ImageButton>(R.id.back_arrow_button)
        val settingsButton = view.findViewById<ImageButton>(R.id.settings_button)
        val sendRequestButton = view.findViewById<Button>(R.id.send_request_button)
        val changeNicknameButton2 = view.findViewById<Button>(R.id.change_nickname_button2)
        val requestStatusText = view.findViewById<TextView>(R.id.status_text)
        val nicknameChangeStatusText = view.findViewById<TextView>(R.id.status_text_2)

        requestStatusText.setTextColor(Color.RED)
        nicknameChangeStatusText.setTextColor(Color.RED)
        nestedScrollView = view.findViewById(R.id.profile_nested_scroll_view)
        setupSwipeGesture(
            this,
            nestedScrollView,
            null,
            view,
            mapActivity
        )

        imageHandler = ImageHandler(mapActivity, myProfilePic, addPfpPic, compressingStatus, false, 600, 600, 40f)
        imageHandler.initImagePickerLauncher(this)
        nicknameText.text = api.myNickname
        myTagCounter.text = getString(R.string.tag_counter_format, api.myTags)
        if (api.myPfpId != 0) {
            coroutineScope.launch {
                val bitmap = api.getPictureData(api.myPfpId)
                if (bitmap != null) {
                    myProfilePic.setImageBitmap(bitmap)
                    addPfpPic.visibility = View.GONE
                }
            }
        }

        val friendRecyclerView: RecyclerView = view.findViewById(R.id.friends_recycler_view)
        friendAdapter = FriendAdapter(
            mapActivity,
            api.getFriendsData(),
            api,
            mapActivity
        )
        friendRecyclerView.adapter = friendAdapter
        friendRecyclerView.layoutManager = MyLinearLayoutManager(mapActivity)

        friendRequestsRecyclerView = view.findViewById(R.id.friend_requests_recycler_view)
        friendRequestAdapter = FriendRequestAdapter(
            mapActivity,
            api.getFriendRequestDataList().toMutableList(),
            api,
            friendAdapter,
            mapActivity)
        friendRequestsRecyclerView.adapter = friendRequestAdapter
        friendRequestsRecyclerView.layoutManager = MyLinearLayoutManager(mapActivity)
        addFriendButton.setOnClickListener {
            requestInput.text.clear()
            addFriendWindow.visibility = View.VISIBLE
            darkOverlay.visibility = View.VISIBLE
        }
        changeNicknameButton1.setOnClickListener {
            changeNicknameInput.text.clear()
            changeNicknameWindow.visibility = View.VISIBLE
            darkOverlay.visibility = View.VISIBLE
        }
        darkOverlay.setOnClickListener {
            addFriendWindow.visibility = View.GONE
            changeNicknameWindow.visibility = View.GONE
            darkOverlay.visibility = View.GONE
            requestStatusText.visibility = View.INVISIBLE
            nicknameChangeStatusText.visibility = View.INVISIBLE
            hideKeyboard()
        }
        sendRequestButton.setOnClickListener{
            val nickname = requestInput.text.toString()
            if (nickname.isEmpty()) {
                requestStatusText.visibility = View.INVISIBLE
                requestStatusText.text = getString(R.string.empty_field)
                return@setOnClickListener
            }
            coroutineScope.launch {
                val answer = api.sendFriendRequestToWS(nickname)
                if (answer != null) {
                    val message = answer.getString("message")
                    if (answer.getString("status") == "success") {
                        requestStatusText.visibility = View.INVISIBLE
                        requestStatusText.text = ""
                        requestInput.setText("")
                        hideKeyboard()
                        darkOverlay.visibility = View.GONE
                        addFriendWindow.visibility = View.GONE
                        Toast.makeText(mapActivity, getString(R.string.friend_request_sent), Toast.LENGTH_SHORT)
                            .show()
                        api.getFriendRequestsFromWS()
                        val updatedRequests = api.getFriendRequestDataList()
                        friendRequestAdapter.updateData(updatedRequests)
                    } else {
                        requestStatusText.visibility = View.VISIBLE
                        if (message == "no user") {
                            requestStatusText.text = getString(R.string.user_not_found)
                        } else {
                            requestStatusText.text = message
                        }
                    }

                }
            }
        }
        changeNicknameButton2.setOnClickListener{
            val nickname = changeNicknameInput.text.toString()
            if (nickname.isEmpty()) {
                requestStatusText.visibility = View.INVISIBLE
                requestStatusText.text = getString(R.string.empty_field)
                return@setOnClickListener
            }
            coroutineScope.launch {
                val answer = api.changeNickname(nickname)
                if (answer != null) {
                    val message = answer.getString("message")
                    if (answer.getString("status") == "success") {
                        api.getMyDataFromWS()
                        nicknameText.text = api.myNickname
                        nicknameChangeStatusText.visibility = View.INVISIBLE
                        nicknameChangeStatusText.text = ""
                        changeNicknameInput.setText("")
                        hideKeyboard()
                        darkOverlay.visibility = View.GONE
                        changeNicknameWindow.visibility = View.GONE
                        Toast.makeText(mapActivity, getString(R.string.username_changed), Toast.LENGTH_SHORT).show()
                    } else {
                        nicknameChangeStatusText.visibility = View.VISIBLE
                        if (message == "username already exists") {
                            nicknameChangeStatusText.text = getString(R.string.username_taken)
                        } else {
                            nicknameChangeStatusText.text = message
                        }
                    }
                }
            }
        }

        myProfilePic.setOnClickListener {
            imageHandler.pickImageGallery()
            coroutineScope.launch {
                while (!imageHandler.isImageCompressed()) {
                    kotlinx.coroutines.delay(100)
                }
                api.insertPictureIntoWS(imageHandler.getOutputStream())
                if (api.lastInsertedPicId != 0) {
                    val message = api.setProfilePictureWS(api.lastInsertedPicId)?.getString("message")
                    if (message == "success") {
                        Toast.makeText(mapActivity, getString(R.string.pfp_updated), Toast.LENGTH_LONG).show()
                        api.myPfpId = api.lastInsertedPicId
                        mapActivity.customOverlaySelf!!.updateDrawable(BitmapDrawable(resources, api.getPictureData(api.myPfpId)))
                    }
                }
            }
        }
        settingsButton.setOnClickListener {
            val settingsFragment = SettingsFragment.newInstance()
            mapActivity.fragmentManager.beginTransaction()
                .add(R.id.profile_fragment, settingsFragment)
                .addToBackStack(null)
                .commit()
        }
        ratingLayout.setOnClickListener {
            val leaderBoardFragment = LeaderboardFragment.newInstance()
            mapActivity.fragmentManager.beginTransaction()
                .add(R.id.profile_fragment, leaderBoardFragment)
                .addToBackStack(null)
                .commit()
        }
        backButton.setOnClickListener{
            mapActivity.onBackPressedDispatcher.onBackPressed()
        }
        return view
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        friendRequestUpdateRunnable = Runnable {
            CoroutineScope(Dispatchers.Main).launch {
                api.getFriendRequestsFromWS()
                val updatedFriendRequests = api.getFriendRequestDataList()
                (friendRequestsRecyclerView.adapter as? FriendRequestAdapter)?.updateData(updatedFriendRequests)
                friendRequestUpdateHandler?.postDelayed(friendRequestUpdateRunnable!!, friendRequestInterval)
            }
        }
        startFriendRequestsUpdates()
    }
    private fun startFriendRequestsUpdates() {
        friendRequestUpdateHandler = Handler(Looper.getMainLooper())
        friendRequestUpdateRunnable?.let { friendRequestUpdateHandler?.postDelayed(it,
            friendRequestInterval
        ) }
    }
}

class MyLinearLayoutManager(context: Context) : LinearLayoutManager(context) {
    override fun canScrollVertically(): Boolean {
        return false
    }
}
class FriendAdapter(
    private val context: Context,
    private var friendList: MutableList<API.FriendData>,
    private val api: API,
    private val mapActivity: MapActivity,
) : RecyclerView.Adapter<FriendAdapter.FriendViewHolder>() {
    inner class FriendViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.friend_name)
        val pictureImageView: ImageView = itemView.findViewById(R.id.friend_picture)
        val friendLayout: LinearLayout = itemView.findViewById(R.id.friend_layout)
        val locateButton: ImageButton = itemView.findViewById(R.id.locate_friend_button)
        val messageButton: ImageButton = itemView.findViewById(R.id.text_friend_button)
        val coroutineScope = CoroutineScope(Dispatchers.Main)
    }
    fun updateData(newFriendList: List<API.FriendData>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int {
                return friendList.size
            }

            override fun getNewListSize(): Int {
                return newFriendList.size
            }

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return friendList[oldItemPosition].userData.userId == newFriendList[newItemPosition].userData.userId
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = friendList[oldItemPosition]
                val newItem = newFriendList[newItemPosition]
                val didChange = oldItem == newItem
                return didChange
            }
        })
        friendList = newFriendList.map {it.copy()}.toMutableList()
        diffResult.dispatchUpdatesTo(this)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.friend_item, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = friendList[position]

        holder.nameTextView.text = friend.userData.nickname
        val drawablePlaceholder = ContextCompat.getDrawable(context, R.drawable.person_placeholder)
        holder.pictureImageView.setImageDrawable(drawablePlaceholder)

        if (friend.userData.profilePictureId != 0) {
            holder.coroutineScope.launch {
                val bitmap = api.getPictureData(friend.userData.profilePictureId)
                if (bitmap != null) {
                    holder.pictureImageView.setImageBitmap(bitmap)
                }
            }
        }

        holder.friendLayout.setOnClickListener {
            val userProfileFragment = UserProfileFragment.newInstance(friend.userData.userId)
            mapActivity.fragmentManager.beginTransaction()
                .add(R.id.profile_fragment, userProfileFragment)
                .addToBackStack(null)
                .commit()
        }
        holder.locateButton.setOnClickListener {
            val friendOverlay = mapActivity.friendOverlays[friend.userData.userId]
            if (friendOverlay != null) {
                mapActivity.hideSearchLayout()
                mapActivity.centralizeMapAnimated(
                    friendOverlay,
                    friend.userData.userId,
                    isCenterTargetUser = true,
                    withZoom = true
                )
            mapActivity.onBackPressedDispatcher.onBackPressed()
            } else {
                Toast.makeText(context, context.getString(R.string.no_location), Toast.LENGTH_LONG).show()
            }
        }
        holder.messageButton.setOnClickListener {
            val conversation = api.getConversationsDataList().find { it.userData.userId == friend.userData.userId }
            if (conversation != null) {
                val conversationFragment =
                    ConversationFragment.newInstance(conversation.conversationID, conversation.userData.nickname)
                mapActivity.supportFragmentManager.beginTransaction()
                    .add(R.id.profile_fragment, conversationFragment)
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    override fun getItemCount(): Int {
        return friendList.size
    }
}
class FriendRequestAdapter(
    private val context: Context,
    private var requestList: MutableList<API.FriendRequestData>,
    private val api: API,
    private val friendAdapter: FriendAdapter,
    private val mapActivity: MapActivity
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    inner class IncomingFriendRequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.friend_name)
        val pictureImageView: ImageView = itemView.findViewById(R.id.friend_picture)
        val acceptButton: ImageButton = itemView.findViewById(R.id.accept_button)
        val denyButton: ImageButton = itemView.findViewById(R.id.deny_button)
        val requestUserButton: LinearLayout = itemView.findViewById(R.id.incoming_friend_request)
        val coroutineScope = CoroutineScope(Dispatchers.Main)
    }

    inner class OutgoingFriendRequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.friend_name)
        val pictureImageView: ImageView = itemView.findViewById(R.id.friend_picture)
        val cancelButton: ImageButton = itemView.findViewById(R.id.cancel_button)
        val requestUserButton: LinearLayout = itemView.findViewById(R.id.outgoing_friend_request)
        val coroutineScope = CoroutineScope(Dispatchers.Main)
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
    fun updateData(newRequestList: List<API.FriendRequestData>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int {
                return requestList.size
            }

            override fun getNewListSize(): Int {
                return newRequestList.size
            }

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return requestList[oldItemPosition].userData.userId == newRequestList[newItemPosition].userData.userId
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = requestList[oldItemPosition]
                val newItem = newRequestList[newItemPosition]
                val didChange = (oldItem.userData == newItem.userData)
                return didChange
            }
        })
        requestList = newRequestList.map {it.copy()}.toMutableList()
        diffResult.dispatchUpdatesTo(this)

        val hasUIncomingRequests = requestList.any { it.relation == "request_incoming" }
        mapActivity.newRequestIcon.visibility = if (hasUIncomingRequests) View.VISIBLE else { View.INVISIBLE }
        mapActivity.profileFragment.friendRequestsHeader.visibility = if (requestList.isEmpty()) View.GONE else View.VISIBLE
    }
    private fun removeItem(position: Int) {
        requestList.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, requestList.size)
        mapActivity.profileFragment.friendRequestsHeader.visibility = if (requestList.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val requestee = requestList[position]
        val drawablePlaceholder = ContextCompat.getDrawable(context, R.drawable.person_placeholder)
        when (holder.itemViewType) {
            VIEW_TYPE_INCOMING -> {
                val incomingHolder = holder as IncomingFriendRequestViewHolder
                incomingHolder.nameTextView.text = requestee.userData.nickname
                incomingHolder.acceptButton.tag = requestee.userData.userId
                incomingHolder.denyButton.tag = requestee.userData.userId
                incomingHolder.requestUserButton.setOnClickListener {
                    val userProfileFragment = UserProfileFragment.newInstance(requestee.userData.userId)
                    mapActivity.fragmentManager.beginTransaction()
                        .add(R.id.profile_fragment, userProfileFragment)
                        .addToBackStack(null)
                        .commit()
                }
                holder.pictureImageView.setImageDrawable(drawablePlaceholder)
                if (requestee.userData.profilePictureId != 0) {
                    incomingHolder.coroutineScope.launch {
                        val bitmap = api.getPictureData(requestee.userData.profilePictureId)
                        if (bitmap != null) {
                            incomingHolder.pictureImageView.setImageBitmap(bitmap)
                        }
                    }
                }

                incomingHolder.acceptButton.setOnClickListener { view ->
                    val userId = view.tag as Int
                    incomingHolder.coroutineScope.launch {
                        val answer = api.acceptFriendRequest(userId)
                        if (answer != null) {
                            if (answer.getString("status") == "success") {
                                removeItem(holder.adapterPosition)
                                api.getFriendsFromWS()
                                val updatedFriends = api.getFriendsData()
                                friendAdapter.updateData(updatedFriends)
                            }
                        }
                    }
                }
                incomingHolder.denyButton.setOnClickListener { view ->
                    val userId = view.tag as Int
                    incomingHolder.coroutineScope.launch {
                        val answer = api.denyFriendRequest(userId)
                        if (answer != null) {
                            if (answer.getString("status") == "success") {
                                removeItem(holder.adapterPosition)
                                friendAdapter.updateData(api.getFriendsData())
                            }
                        }
                    }
                }
            }
            VIEW_TYPE_OUTGOING -> {
                val outgoingHolder = holder as OutgoingFriendRequestViewHolder
                outgoingHolder.nameTextView.text = requestee.userData.nickname
                outgoingHolder.cancelButton.tag = requestee.userData.userId
                outgoingHolder.requestUserButton.setOnClickListener {
                    val userProfileFragment = UserProfileFragment.newInstance(requestee.userData.userId)
                    mapActivity.fragmentManager.beginTransaction()
                        .add(R.id.profile_fragment, userProfileFragment)
                        .addToBackStack(null)
                        .commit()
                }
                holder.pictureImageView.setImageDrawable(drawablePlaceholder)
                if (requestee.userData.profilePictureId != 0) {
                    outgoingHolder.coroutineScope.launch {
                        val bitmap = api.getPictureData(requestee.userData.profilePictureId)
                        if (bitmap != null) {
                            outgoingHolder.pictureImageView.setImageBitmap(bitmap)
                        }
                    }
                }
                outgoingHolder.cancelButton.setOnClickListener { view ->
                    val userId = view.tag as Int
                    outgoingHolder.coroutineScope.launch {
                        val answer = api.cancelFriendRequest(userId)
                        if (answer != null) {
                            if (answer.getString("status") == "success") {
                                removeItem(holder.adapterPosition)
                                friendAdapter.updateData(api.getFriendsData())
                            }
                        }
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
        return if (friend.relation == "request_incoming") {
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

