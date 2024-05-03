package tagme

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tagme.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.io.ByteArrayOutputStream

class ProfileFragment : Fragment() {
    lateinit var friendAdapter: FriendAdapter
    lateinit var friendRequestAdapter: FriendRequestAdapter
    private lateinit var outputStream: ByteArrayOutputStream
    private lateinit var myProfilePic: ImageView
    private var imageCompressed: Boolean = false
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var api: API
    companion object{
        val MAX_SIZE_BEFORE_ENCODING = 100 * 1024
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)
        val coroutineScope = CoroutineScope(Dispatchers.Main)
        val inputMethodManager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val addFriendButton = view.findViewById<ImageButton>(R.id.add_friend_button)
        val addFriendWindow = view.findViewById<View>(R.id.add_friend_window)
        val nicknameText = view.findViewById<TextView>(R.id.nickname_text)
        myProfilePic = view.findViewById(R.id.profile_picture)
        val darkOverlay = view.findViewById<View>(R.id.dark_overlay)
        val requestInput = view.findViewById<EditText>(R.id.nickname_edit_text)
        val backButton = view.findViewById<ImageButton>(R.id.back_arrow_button)
        api = (requireActivity() as MapActivity).api
        val sendRequestButton = view.findViewById<Button>(R.id.send_request_button)
        val statusText = view.findViewById<TextView>(R.id.status_text)
        nicknameText.text = api.myNickname
        if (api.myPfpId != 0) {
            coroutineScope.launch {
                val bitmap = api.getPictureData(api.myPfpId)
                if (bitmap != null) {
                    myProfilePic.setImageBitmap(bitmap)
                }
            }
        }
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                data?.data?.let { uri ->
                    compressImage(uri)
                }
            }
        }
        val friendRecyclerView: RecyclerView = view.findViewById(R.id.friends_recycler_view)
        friendAdapter = FriendAdapter(
            requireContext(),
            api.getFriendsData(),
            api,
            requireActivity().supportFragmentManager,
            requireActivity() as MapActivity
        )
        friendRecyclerView.adapter = friendAdapter
        friendRecyclerView.layoutManager = MyLinearLayoutManager(requireContext())

        val friendRequestsRecyclerView: RecyclerView = view.findViewById(R.id.friend_requests_recycler_view)
        friendRequestAdapter = FriendRequestAdapter(requireContext(), api.getFriendRequestData(), api, friendAdapter, requireActivity().supportFragmentManager)
        friendRequestsRecyclerView.adapter = friendRequestAdapter
        friendRequestsRecyclerView.layoutManager = MyLinearLayoutManager(requireContext())
        addFriendButton.setOnClickListener {
            addFriendWindow.visibility = View.VISIBLE
            darkOverlay.visibility = View.VISIBLE
        }
        darkOverlay.setOnClickListener {
            addFriendWindow.visibility = View.GONE
            darkOverlay.visibility = View.GONE
            inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
        }
        sendRequestButton.setOnClickListener{
            val nickname = requestInput.text.toString()
            coroutineScope.launch {
                val answer = api.sendFriendRequestToWS(nickname)
                if (answer != null) {
                    val message = answer.getString("message")
                    if (answer.getString("status") == "success") {
                        statusText.visibility = View.GONE
                        requestInput.setText("")
                        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
                        darkOverlay.visibility = View.GONE
                        addFriendWindow.visibility = View.GONE
                        Toast.makeText(requireContext(), "Friend request was sent!", Toast.LENGTH_SHORT).show()
                        api.getFriendRequestsFromWS()
                        val updatedRequests = api.getFriendRequestData()
                        friendRequestAdapter.updateData(updatedRequests)
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
        myProfilePic.setOnClickListener {
            imageCompressed = false
            pickImageGallery()
        }
        backButton.setOnClickListener{
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        return view
    }
    private fun pickImageGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }
    private fun compressImage(uri: Uri) {
        GlobalScope.launch(Dispatchers.IO) {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val originalBitmap = BitmapFactory.decodeStream(stream)
                val compressedBitmap = (compressBitmap(originalBitmap))
                originalBitmap.recycle()
                requireActivity().runOnUiThread {
                    //compressingStatus.visibility = View.GONE
                    val roundedImageBitmap = applyRoundedCorners(compressedBitmap, 20f)
                    imageCompressed = true
                    myProfilePic.setImageBitmap(roundedImageBitmap)
                }
            }
        }
    }
    private fun applyRoundedCorners(bitmap: Bitmap, radius: Float): Bitmap {
        val roundedBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(roundedBitmap)
        val paint = Paint()
        paint.isAntiAlias = true
        val rectF = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val path = Path()
        path.addRoundRect(rectF, radius, radius, Path.Direction.CW)
        canvas.drawPath(path, paint)
        paint.shader = shader
        canvas.drawPath(path, paint)
        return roundedBitmap
    }
    private fun compressBitmap(bitmap: Bitmap): Bitmap {
        val maxWidth = 600
        val maxHeight = 600

        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        val aspectRatio = originalWidth.toFloat() / originalHeight.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (aspectRatio > maxWidth.toFloat() / maxHeight.toFloat()) {
            targetWidth = maxWidth
            targetHeight = (maxWidth.toFloat() / aspectRatio).toInt()
        } else {
            targetWidth = (maxHeight.toFloat() * aspectRatio).toInt()
            targetHeight = maxHeight
        }

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        val initialByteArray = outputStream.toByteArray()
        requireActivity().runOnUiThread {
            myProfilePic.setImageResource(R.drawable.photo_bg)
        }
        if (initialByteArray.size <= MAX_SIZE_BEFORE_ENCODING) {
            Log.d("Tagme_PIC", "Image size is already within the desired range.")
            return BitmapFactory.decodeByteArray(initialByteArray, 0, initialByteArray.size)
        }
        var quality = 100
        var byteArray = initialByteArray
        Log.d("Tagme_PIC", "Before compressing: ${byteArray.size}")
        while (byteArray.size > MAX_SIZE_BEFORE_ENCODING && quality > 0) {
            outputStream.reset()
            quality -= if (quality <= 20) 5 else 10
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            byteArray = outputStream.toByteArray()
            Log.d("Tagme_PIC", "Compressing: $quality ${byteArray.size}")
        }

        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
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
    private val childFragmentManager: FragmentManager,
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
    fun updateData(newFriendList: MutableList<API.FriendData>) {
        friendList = newFriendList
        notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.friend_item, parent, false)
        return FriendViewHolder(view)
    }
    private fun removeItem(position: Int) {
        friendList.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, friendList.size)
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
            val userProfileDialog = UserProfileDialogFragment.newInstance(friend.userData.userId)
            userProfileDialog.show(childFragmentManager, "userProfileDialog")
        }
        holder.locateButton.setOnClickListener {
            val friendLocation = GeoPoint(friend.location!!.latitude, friend.location!!.longitude)
            mapActivity.centralizeMapAnimated(friendLocation, friend.userData.userId, true)
            mapActivity.onBackPressedDispatcher.onBackPressed()
        }
        holder.messageButton.setOnClickListener {
            val conversation = api.getConversationsData().find { it.userData.userId == friend.userData.userId }
            if (conversation != null) {
                val conversationFragment =
                    ConversationFragment.newInstance(conversation.conversationID, conversation.userData.nickname)
                mapActivity.supportFragmentManager.beginTransaction()
                    .replace(R.id.profile_fragment, conversationFragment)
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
    private val childFragmentManager: FragmentManager
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {    inner class IncomingFriendRequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
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
    fun updateData(newRequestList: MutableList<API.FriendRequestData>) {
        requestList = newRequestList
        notifyDataSetChanged()
    }
    private fun removeItem(position: Int) {
        requestList.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, requestList.size)
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
                    val userProfileDialog = UserProfileDialogFragment.newInstance(requestee.userData.userId)
                    userProfileDialog.show(childFragmentManager, "userProfileDialog")
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
                    val userProfileDialog = UserProfileDialogFragment.newInstance(requestee.userData.userId)
                    userProfileDialog.show(childFragmentManager, "userProfileDialog")
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
