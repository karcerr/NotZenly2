package com.tagme

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS][.SS][.S]")
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
        val conversation = conversationsAdapter.conversationList[position]

        // Inflate the menu view which includes conversation_item layout
        val menuView = LayoutInflater.from(context).inflate(R.layout.conversation_contextual_menu, null)

        // Find views within the inflated layout
        val nameTextView = menuView.findViewById<TextView>(R.id.conversation_name)
        val lastMessageText = menuView.findViewById<TextView>(R.id.last_message_text)
        val lastMessageTimestamp = menuView.findViewById<TextView>(R.id.last_message_timestamp)
        val readIcon = menuView.findViewById<ImageView>(R.id.read_icon)
        val pinIcon = menuView.findViewById<ImageView>(R.id.pin_icon)
        val pictureImageView = menuView.findViewById<ImageView>(R.id.conversation_picture)

        // Set data from the selected conversation item to the views
        nameTextView.text = conversation.userData.nickname

        if (conversation.lastMessage != null) {
            lastMessageText.text = conversation.lastMessage?.text ?: ""
            val timestampDateTime = LocalDateTime.parse(conversation.lastMessage!!.timestamp.toString(), dateFormatter)
            val timestampText = timestampDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
            lastMessageTimestamp.visibility = View.VISIBLE
            lastMessageTimestamp.text = timestampText
            if (!conversation.lastMessage!!.read && conversation.lastMessage!!.authorId != api.myUserId){
                readIcon.visibility = View.VISIBLE
            } else {
                readIcon.visibility = View.INVISIBLE
            }
        }

        val originalPictureDrawable = (recyclerView.findViewHolderForAdapterPosition(position) as? ConversationsAdapter.ConversationViewHolder)
            ?.pictureImageView?.drawable

        pictureImageView.setImageDrawable(originalPictureDrawable)
        if (conversation.userData.profilePictureId != 0) {
            CoroutineScope(Dispatchers.Main).launch {
                val bitmap = api.getPictureData(conversation.userData.profilePictureId)
                if (bitmap != null) {
                    pictureImageView.setImageBitmap(bitmap)
                }
            }
        }

        // Set up menu item click listeners within the inflated layout
        val pinButton = menuView.findViewById<ImageView>(R.id.action_pin)
        if (conversation.pinned) {
            pinIcon.visibility = View.VISIBLE
            pinButton.setImageResource(R.drawable.unpin)
        } else {
            pinIcon.visibility = View.GONE
            pinButton.setImageResource(R.drawable.pin)
        }

        // Initialize PopupWindow with half of the screen width and WRAP_CONTENT height
        val displayMetrics = mapActivity.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val horizontalMargin = mapActivity.resources.getDimensionPixelSize(R.dimen.popup_horizontal_margin)
        val effectiveWidth = screenWidth - 2 * horizontalMargin
        val popupWindow = PopupWindow(menuView, effectiveWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popupWindow.animationStyle = R.style.NoAnimationPopupStyle
        pinButton.setOnClickListener {
            api.togglePinnedStatus(conversation.conversationID)
            updateConversationsLocal()
            popupWindow.contentView.animate().alpha(0f).setDuration(400).withEndAction {
                popupWindow.dismiss()
            }.start()
        }

        menuView.findViewById<ImageView>(R.id.action_read).setOnClickListener {
            popupWindow.contentView.animate().alpha(0f).setDuration(400).withEndAction {
                popupWindow.dismiss()
            }.start()
        }
        menuView.findViewById<ImageView>(R.id.action_delete).setOnClickListener {
            popupWindow.contentView.animate().alpha(0f).setDuration(400).withEndAction {
                popupWindow.dismiss()
            }.start()
        }
        val location = IntArray(2)
        popupWindow.contentView.alpha = 0f
        recyclerView.getChildAt(position)?.getLocationOnScreen(location)
        val x = location[0]
        val y = location[1]
        popupWindow.showAtLocation(menuView, Gravity.NO_GRAVITY, x, y)
        popupWindow.contentView.animate().alpha(1f).setDuration(400).start()

        // Set dismiss listener to handle cleanup
        popupWindow.setOnDismissListener {
            ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 400
                addUpdateListener { animation ->
                    val progress = animation.animatedValue as Float
                    darkOverlay.alpha = progress

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val blurRadius = progress * 10f
                        val blurEffect = if (blurRadius == 0f) null
                        else RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                        view.setRenderEffect(blurEffect)
                    }
                }
                start()
            }
        }

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                darkOverlay.alpha = progress

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val blurRadius = if (progress == 0f) 0.1f else progress * 10f
                    val blurEffect = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                    view.setRenderEffect(blurEffect)
                }
            }
            start()
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

    override fun getItemCount(): Int {
        return conversationList.size
    }
}
