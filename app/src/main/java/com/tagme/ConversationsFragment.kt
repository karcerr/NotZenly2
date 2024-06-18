package com.tagme

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max


class ConversationsFragment : Fragment(), ConversationsAdapter.OnItemLongClickListener {
    private lateinit var view: View
    lateinit var conversationsAdapter: ConversationsAdapter
    private lateinit var api: API
    lateinit var nestedScrollView: CustomNestedScrollView
    private lateinit var mapActivity: MapActivity
    private val conversationUpdateInterval = 1500L
    var conversationUpdateHandler: Handler? = null
    private var conversationUpdateRunnable: Runnable? = null
    private lateinit var recyclerView: RecyclerView
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        view = inflater.inflate(R.layout.fragment_conversations, container, false)
        val backButton: ImageButton = view.findViewById(R.id.back_arrow_button)
        mapActivity = requireActivity() as MapActivity
        api = mapActivity.api
        recyclerView = view.findViewById(R.id.conversations_recycler_view)
        nestedScrollView = view.findViewById(R.id.nested_scroll_view)
        var shouldInterceptTouch = false
        val gestureListener = SwipeGestureListener(
            onSwipe = { deltaY ->
                if (nestedScrollView.scrollY == 0) {
                    val newTranslationY = view.translationY + deltaY
                    if (shouldInterceptTouch || newTranslationY > 0F){
                        shouldInterceptTouch = true
                        view.translationY = max(newTranslationY, 0F)
                        nestedScrollView.scrollTo(0, 0)
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            },
            onSwipeEnd = {
                shouldInterceptTouch = false
                if (abs(view.translationY) > 150) { //swipe threshold
                    animateFragmentClose(view)
                } else {
                    animateFragmentReset(view)
                }
            }
        )
        val gestureDetector = GestureDetector(mapActivity, gestureListener)
        nestedScrollView.gestureDetector = gestureDetector
        nestedScrollView.setOnTouchListener { v, event ->
            if (gestureDetector.onTouchEvent(event)) {
                return@setOnTouchListener true
            }
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                gestureListener.onUp(event)
                shouldInterceptTouch = false
                v.performClick()
            }
            false
        }
        val conversationListSorted = api.getConversationsDataList().map { it.copy() }
            .sortedWith(compareByDescending<API.ConversationData> { it.pinned }
                .thenByDescending { it.lastMessage?.timestamp })
            .toMutableList()

        conversationsAdapter = ConversationsAdapter(
            requireContext(),
            conversationListSorted, api,
            mapActivity,
            this
        )

        recyclerView.adapter = conversationsAdapter
        recyclerView.layoutManager = MyLinearLayoutManager(requireContext())

        backButton.setOnClickListener{
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        return view
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        conversationUpdateRunnable = Runnable {
            CoroutineScope(Dispatchers.Main).launch {
                api.getConversationsFromWS()
                val updatedConversations = api.getConversationsDataList()
                (recyclerView.adapter as? ConversationsAdapter)?.updateData(updatedConversations)
                conversationUpdateHandler?.postDelayed(conversationUpdateRunnable!!, conversationUpdateInterval)
            }
        }
        startConversationUpdates()
    }
    override fun onDestroyView() {
        Log.d("Tagme_ws_conv", "onDestroyView")
        super.onDestroyView()
        stopConversationUpdates()
    }

    private fun startConversationUpdates() {
        conversationUpdateHandler = Handler(Looper.getMainLooper())
        conversationUpdateRunnable?.let { conversationUpdateHandler?.postDelayed(it,
            conversationUpdateInterval
        ) }
    }

    private fun stopConversationUpdates() {
        conversationUpdateRunnable?.let { conversationUpdateHandler?.removeCallbacks(it) }
        conversationUpdateHandler = null
    }

    override fun onItemLongClick(position: Int) {
        showContextualMenu(position)
    }
    private fun showContextualMenu(position: Int) {
        // Inflate the contextual menu layout
        val conversation = conversationsAdapter.conversationList[position]
        val menuView = LayoutInflater.from(context).inflate(R.layout.conversation_contextual_menu, null)
        val popupWindow = PopupWindow(menuView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        popupWindow.isOutsideTouchable = true
        popupWindow.isFocusable = true
        val pinButton = menuView.findViewById<ImageView>(R.id.action_pin)
        if (conversation.pinned) {
            pinButton.setImageDrawableResource(R.drawable.unpin)
        } else {
            pinButton.setImageDrawableResource(R.drawable.pin)
        }
        // Set up menu item click listeners
        pinButton.setOnClickListener {
            api.togglePinnedStatus(conversation.conversationID)
            Log.d("Tagme_conversation", conversation.toString())
            updateConversationsLocal()
            popupWindow.dismiss()
        }
        menuView.findViewById<ImageView>(R.id.action_read).setOnClickListener {
            // Handle mute action
            popupWindow.dismiss()
        }
        menuView.findViewById<ImageView>(R.id.action_delete).setOnClickListener {
            // Handle delete action
            popupWindow.dismiss()
        }

        // Show the popup window
        val itemView = recyclerView.findViewHolderForAdapterPosition(position)?.itemView
        itemView?.let {
            popupWindow.showAsDropDown(it)
        }
    }
    private fun updateConversationsLocal() {
        val conversationList = api.getConversationsDataList()
        conversationsAdapter.updateData(conversationList)
        Log.d("Tagme_conversations", conversationList.toString())
    }
}

class ConversationsAdapter(
    private val context: Context,
    var conversationList: MutableList<API.ConversationData>,
    private val api: API,
    private val parentActivity: MapActivity,
    private val listener: OnItemLongClickListener
) : RecyclerView.Adapter<ConversationsAdapter.ConversationViewHolder>() {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS][.SS][.S]")
    interface OnItemLongClickListener {
        fun onItemLongClick(position: Int)
    }
    inner class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.conversation_name)
        val lastMessageText: TextView = itemView.findViewById(R.id.last_message_text)
        val lastMessageTimestamp: TextView = itemView.findViewById(R.id.last_message_timestamp)
        val readIcon: ImageView = itemView.findViewById(R.id.read_icon)
        val pictureImageView: ImageView = itemView.findViewById(R.id.conversation_picture)
        val conversationLayout: LinearLayout = itemView.findViewById(R.id.conversation_layout)
        val coroutineScope = CoroutineScope(Dispatchers.Main)
        init {
            itemView.setOnLongClickListener {
                listener.onItemLongClick(adapterPosition)
                true
            }
        }
    }
    fun updateData(newConversationList: List<API.ConversationData>) {
        val newConversationListSorted = newConversationList
            .sortedWith(compareByDescending<API.ConversationData> { it.pinned }
                .thenByDescending { it.lastMessage?.timestamp })
            .toList()
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int {
                return conversationList.size
            }

            override fun getNewListSize(): Int {
                return newConversationListSorted.size
            }

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return conversationList[oldItemPosition].conversationID == newConversationListSorted[newItemPosition].conversationID
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = conversationList[oldItemPosition]
                val newItem = newConversationListSorted[newItemPosition]
                return oldItem == newItem
            }
        })
        conversationList.clear()
        conversationList.addAll(newConversationListSorted)
        diffResult.dispatchUpdatesTo(this)
        val hasUnreadMessages = conversationList.any { conversation ->
            val lastMessage = conversation.lastMessage
            lastMessage != null && !lastMessage.read && lastMessage.authorId != api.myUserId
        }
        parentActivity.unreadMessageIcon.visibility = if (hasUnreadMessages) View.VISIBLE else { View.INVISIBLE }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.conversation_item, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val conversation = conversationList[position]
        val conversationId = conversation.conversationID
        if (conversation.pinned) {
            Log.d("Tagme_conv pinned", conversation.toString())
            TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(
                holder.nameTextView,
                0, 0, R.drawable.pin_blue, 0
            )
        } else {
            Log.d("Tagme_conv unpinned", conversation.toString())
            TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(
                holder.nameTextView,
                0, 0, 0, 0
            )
        }
        holder.nameTextView.text = conversation.userData.nickname
        val lastMessage = conversation.lastMessage
        if (lastMessage != null) {
            val timestampDateTime = LocalDateTime.parse(lastMessage.timestamp.toString(), dateFormatter)
            val timestampText = timestampDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
            holder.lastMessageText.text = lastMessage.text
            holder.lastMessageTimestamp.visibility = View.VISIBLE
            holder.lastMessageTimestamp.text = timestampText
            if (!lastMessage.read && lastMessage.authorId != api.myUserId) {
                holder.readIcon.visibility = View.VISIBLE
            } else {
                holder.readIcon.visibility = View.INVISIBLE
            }
        }
        val drawablePlaceholder = ContextCompat.getDrawable(context, R.drawable.person_placeholder)
        holder.pictureImageView.setImageDrawable(drawablePlaceholder)
        if (conversation.userData.profilePictureId != 0) {
            holder.coroutineScope.launch {
                val bitmap = api.getPictureData(conversation.userData.profilePictureId)
                if (bitmap != null) {
                    holder.pictureImageView.setImageBitmap(bitmap)
                }
            }
        }
        holder.conversationLayout.setOnClickListener {
            val conversationFragment = ConversationFragment.newInstance(conversationId, conversation.userData.nickname)
            parentActivity.supportFragmentManager.beginTransaction()
                .add(R.id.conversations_fragment, conversationFragment)
                .addToBackStack(null)
                .commit()
        }
    }

    override fun getItemCount(): Int {
        return conversationList.size
    }
}
