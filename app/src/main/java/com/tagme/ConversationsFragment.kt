package com.tagme

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
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
    private lateinit var darkOverlay: View
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
        darkOverlay = view.findViewById(R.id.dark_overlay)
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
        // Get the original item view
        val itemView = recyclerView.findViewHolderForAdapterPosition(position)?.itemView
        itemView?.let {
            // Create a copy of the original item view
            val copiedView = LayoutInflater.from(context).inflate(R.layout.conversation_item, null)
            conversationsAdapter.cloneItemViewHolder(itemView, copiedView)

            // Add the copied view to the Window.DecorView
            val decorView = (activity?.window?.decorView as? ViewGroup)
            decorView?.addView(copiedView)

            // Get the original layout parameters
            val layoutParams = itemView.layoutParams as ViewGroup.MarginLayoutParams

            // Set the layout parameters of the copied view with margins
            val copiedLayoutParams = FrameLayout.LayoutParams(layoutParams.width, layoutParams.height)
            copiedLayoutParams.setMargins(layoutParams.leftMargin, layoutParams.topMargin, layoutParams.rightMargin, layoutParams.bottomMargin)
            copiedView.layoutParams = copiedLayoutParams

            // Position the copied view to match the original item's position
            val location = IntArray(2)
            itemView.getLocationOnScreen(location)
            val left = location[0]
            val top = location[1]

            // Adjust for margins
            copiedView.x = left.toFloat() - layoutParams.leftMargin
            copiedView.y = top.toFloat() - layoutParams.topMargin

            // Show the dark overlay
            darkOverlay.visibility = View.VISIBLE

            // Show the popup window
            val menuView = LayoutInflater.from(context).inflate(R.layout.conversation_contextual_menu, decorView, false)
            // Calculate effective width with margins
            val displayMetrics = mapActivity.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val horizontalMargin = mapActivity.resources.getDimensionPixelSize(R.dimen.popup_horizontal_margin)
            val effectiveWidth = screenWidth - 2 * horizontalMargin
            val params = ViewGroup.LayoutParams(effectiveWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            menuView.layoutParams = params

            val popupWindow = PopupWindow(menuView, effectiveWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true)
            popupWindow.isOutsideTouchable = true
            popupWindow.isFocusable = true

            // Set up menu item click listeners
            val pinButton = menuView.findViewById<ImageView>(R.id.action_pin)
            val conversation = conversationsAdapter.conversationList[position]
            if (conversation.pinned) {
                pinButton.setImageResource(R.drawable.unpin)
            } else {
                pinButton.setImageResource(R.drawable.pin)
            }

            pinButton.setOnClickListener {
                api.togglePinnedStatus(conversation.conversationID)
                updateConversationsLocal()
                popupWindow.dismiss()
            }

            menuView.findViewById<ImageView>(R.id.action_read).setOnClickListener {
                popupWindow.dismiss()
            }
            menuView.findViewById<ImageView>(R.id.action_delete).setOnClickListener {
                popupWindow.dismiss()
            }

            // Show the popup window anchored to the copied view
            popupWindow.showAsDropDown(copiedView, 0, 0, Gravity.CENTER_HORIZONTAL)

            popupWindow.setOnDismissListener {
                darkOverlay.visibility = View.GONE
                decorView?.removeView(copiedView) // Remove the copied view when the popup is dismissed
            }
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
        val pinIcon: ImageView = itemView.findViewById(R.id.pin_icon)
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
            holder.pinIcon.visibility = View.VISIBLE
        } else {
            holder.pinIcon.visibility = View.GONE
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
    fun cloneItemViewHolder(originalView: View?, newView: View?) {
        originalView?.let { original ->
            newView?.let { copy ->
                val nameTextView = copy.findViewById<TextView>(R.id.conversation_name)
                val lastMessageText = copy.findViewById<TextView>(R.id.last_message_text)
                val lastMessageTimestamp = copy.findViewById<TextView>(R.id.last_message_timestamp)
                val readIcon = copy.findViewById<ImageView>(R.id.read_icon)
                val pinIcon = copy.findViewById<ImageView>(R.id.pin_icon)
                val pictureImageView = copy.findViewById<ImageView>(R.id.conversation_picture)

                val originalNameTextView = original.findViewById<TextView>(R.id.conversation_name)

                nameTextView.text = originalNameTextView.text
                lastMessageText.text = original.findViewById<TextView>(R.id.last_message_text).text
                lastMessageTimestamp.text = original.findViewById<TextView>(R.id.last_message_timestamp).text
                lastMessageTimestamp.visibility = original.findViewById<TextView>(R.id.last_message_timestamp).visibility
                readIcon.visibility = original.findViewById<ImageView>(R.id.read_icon).visibility
                pinIcon.visibility = original.findViewById<ImageView>(R.id.pin_icon).visibility
                pictureImageView.setImageDrawable(original.findViewById<ImageView>(R.id.conversation_picture).drawable)
            }
        }
    }
    override fun getItemCount(): Int {
        return conversationList.size
    }
}
