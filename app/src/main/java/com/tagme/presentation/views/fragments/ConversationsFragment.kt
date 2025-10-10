package com.tagme.presentation.views.fragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.tagme.R
import com.tagme.domain.models.ConversationData
import com.tagme.presentation.utils.setupSwipeGesture
import com.tagme.presentation.viewmodels.MapActivityViewModel
import com.tagme.presentation.views.CustomNestedScrollView
import com.tagme.presentation.views.activities.MapActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class ConversationsFragment : Fragment(), ConversationsAdapter.OnItemLongClickListener {
    private lateinit var view: View
    lateinit var conversationsAdapter: ConversationsAdapter
    private lateinit var viewModel: MapActivityViewModel
    lateinit var nestedScrollView: CustomNestedScrollView
    private lateinit var mapActivity: MapActivity
    private val conversationUpdateInterval = 1500L
    var conversationUpdateHandler: Handler? = null
    private var conversationUpdateRunnable: Runnable? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var darkOverlay: View
    private lateinit var areYouSureLayout: LinearLayout
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
        viewModel = mapActivity.viewModel
        recyclerView = view.findViewById(R.id.conversations_recycler_view)
        nestedScrollView = view.findViewById(R.id.nested_scroll_view)
        darkOverlay = view.findViewById(R.id.dark_overlay)
        areYouSureLayout = view.findViewById(R.id.are_you_sure_layout)
        setupSwipeGesture(
            this,
            nestedScrollView,
            null,
            view,
            mapActivity
        )
        val conversationListSorted = viewModel.getConversationsDataList().map { it.copy() }
            .sortedWith(compareByDescending<ConversationData> { it.pinned }
                .thenByDescending { it.lastMessage?.timestamp })
            .toMutableList()

        conversationsAdapter = ConversationsAdapter(
            requireContext(),
            viewModel,
            conversationListSorted,
            mapActivity,
            this
        )

        recyclerView.adapter = conversationsAdapter
        recyclerView.layoutManager = MyLinearLayoutManager(requireContext())

        backButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        conversationUpdateRunnable = Runnable {
            CoroutineScope(Dispatchers.Main).launch {
                val updatedConversations = viewModel.getConversationsFromWS()
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
        conversationUpdateRunnable?.let {
            conversationUpdateHandler?.postDelayed(
                it,
                conversationUpdateInterval
            )
        }
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

        val menuView =
            LayoutInflater.from(context).inflate(R.layout.conversation_contextual_menu, view as ViewGroup, false)

        val relativeLayout = view.findViewById<RelativeLayout>(R.id.relative_layout)
        val nameTextView = menuView.findViewById<TextView>(R.id.conversation_name)
        val lastMessageText = menuView.findViewById<TextView>(R.id.last_message_text)
        val lastMessageTimestamp = menuView.findViewById<TextView>(R.id.last_message_timestamp)
        val lastMessageCheckMark = menuView.findViewById<ImageView>(R.id.check_mark)
        val readIcon = menuView.findViewById<ImageView>(R.id.read_icon)
        val pinIcon = menuView.findViewById<ImageView>(R.id.pin_icon)
        val pictureImageView = menuView.findViewById<ImageView>(R.id.conversation_picture)

        // Set data from the selected conversation item to the views
        nameTextView.text = conversation.userData.nickname
        val lastMessage = conversation.lastMessage
        if (lastMessage != null) {
            val lastMessageString = lastMessage.text
            if (lastMessageString != null && lastMessageString.length > 25) {
                lastMessageText.text =
                    mapActivity.getString(R.string.long_message_format, lastMessageString.substring(0, 25))
            } else {
                lastMessageText.text = lastMessageString
            }
            val timestampDateTime = LocalDateTime.parse(lastMessage.timestamp.toString(), dateFormatter)
            val timestampText = timestampDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
            lastMessageTimestamp.visibility = View.VISIBLE
            lastMessageTimestamp.text = timestampText
            if ((!lastMessage.read && lastMessage.authorId != viewModel.myUserId) || (conversation.markedUnread)) {
                readIcon.visibility = View.VISIBLE
            } else {
                readIcon.visibility = View.INVISIBLE
            }
            if (lastMessage.authorId == viewModel.myUserId) {
                lastMessageCheckMark.visibility = View.VISIBLE
                lastMessageCheckMark.setImageResource(if (lastMessage.read) R.drawable.double_check_mark else R.drawable.single_check_mark)
            }
        } else {
            lastMessageCheckMark.visibility = View.GONE
            readIcon.visibility = if (conversation.markedUnread) View.VISIBLE else View.GONE
        }

        val originalPictureDrawable =
            (recyclerView.findViewHolderForAdapterPosition(position) as? ConversationsAdapter.ConversationViewHolder)
                ?.pictureImageView?.drawable

        pictureImageView.setImageDrawable(originalPictureDrawable)
        CoroutineScope(Dispatchers.Main).launch {
            val bitmap = viewModel.getPictureData(conversation.userData.userId)
            if (bitmap != null) {
                pictureImageView.setImageBitmap(bitmap)
            }
        }

        // Set up menu item click listeners within the inflated layout
        val pinButton = menuView.findViewById<ImageButton>(R.id.action_pin)
        if (conversation.pinned) {
            pinIcon.visibility = View.VISIBLE
            pinButton.setImageResource(R.drawable.unpin)
        } else {
            pinIcon.visibility = View.GONE
            pinButton.setImageResource(R.drawable.pin)
        }
        val displayMetrics = mapActivity.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val horizontalMargin = mapActivity.resources.getDimensionPixelSize(R.dimen.popup_horizontal_margin)
        val effectiveWidth = screenWidth - 2 * horizontalMargin
        val popupWindow = PopupWindow(menuView, effectiveWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popupWindow.animationStyle = R.style.NoAnimationPopupStyle
        setupReadUnreadButton(menuView, conversation, popupWindow)

        pinButton.setOnClickListener {
            viewModel.togglePinnedStatus(conversation.conversationID)
            updateConversationsLocal()
            popupWindow.contentView.animate().alpha(0f).setDuration(300).withStartAction {
                popupWindow.dismiss()
            }.start()
        }

        menuView.findViewById<ImageView>(R.id.action_delete).setOnClickListener {
            view.findViewById<TextView>(R.id.are_you_sure_text_format).text =
                mapActivity.getString(R.string.are_you_sure_delete_conversation_format, conversation.userData.nickname)
            val closeListener = View.OnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    relativeLayout.setRenderEffect(null)
                }
                darkOverlay.alpha = 0f
                darkOverlay.isClickable = false
                darkOverlay.isFocusable = false
                darkOverlay.setOnClickListener(null)
                darkOverlay.visibility = View.GONE
                areYouSureLayout.visibility = View.GONE
            }
            darkOverlay.setOnClickListener(closeListener)
            view.findViewById<Button>(R.id.no_button).setOnClickListener(closeListener)
            view.findViewById<Button>(R.id.yes_button).setOnClickListener {
                viewModel.viewModelScope.launch {
                    viewModel.deleteConversationWS(conversation.conversationID)
                    closeListener.onClick(it)
                }
            }
            areYouSureLayout.visibility = View.VISIBLE
            popupWindow.setOnDismissListener(null)
            popupWindow.dismiss()
        }
        val location = IntArray(2)
        popupWindow.contentView.alpha = 0f
        recyclerView.getChildAt(position)?.getLocationOnScreen(location)
        val x = location[0]
        val y = location[1]
        popupWindow.showAtLocation(menuView, Gravity.NO_GRAVITY, x, y)
        popupWindow.contentView.animate().alpha(1f).setDuration(300).start()

        // Set dismiss listener to handle cleanup
        popupWindow.setOnDismissListener {
            ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 300
                addUpdateListener { animation ->
                    val progress = animation.animatedValue as Float
                    darkOverlay.alpha = progress

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val blurRadius = progress * 10f
                        val blurEffect = if (blurRadius == 0f) null
                        else RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                        relativeLayout.setRenderEffect(blurEffect)
                    }
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        darkOverlay.visibility = View.GONE
                    }
                })
                start()
            }
        }

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            darkOverlay.visibility = View.VISIBLE
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                darkOverlay.alpha = progress

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val blurRadius = if (progress == 0f) 0.1f else progress * 10f
                    val blurEffect = RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                    relativeLayout.setRenderEffect(blurEffect)
                }
            }
            start()
        }
    }

    private fun updateConversationsLocal() {
        val conversationList = viewModel.getConversationsDataList()
        conversationsAdapter.updateData(conversationList)
        Log.d("Tagme_conversations", conversationList.toString())
    }

    private fun setupReadUnreadButton(view: View, conversation: ConversationData, popupWindow: PopupWindow) {
        val readUnreadButton = view.findViewById<ImageButton>(R.id.action_read)

        fun animateAndDismiss() {
            updateConversationsLocal()
            popupWindow.contentView.animate().alpha(0f).setDuration(300).withStartAction {
                popupWindow.dismiss()
            }.start()
        }

        if (conversation.lastMessage != null && !conversation.lastMessage!!.read && conversation.lastMessage!!.authorId != viewModel.myUserId) {
            readUnreadButton.setImageResource(R.drawable.mark_chat_read)
            readUnreadButton.setOnClickListener {
                CoroutineScope(Dispatchers.Main).launch {
                    viewModel.readConversationWS(conversation.conversationID)
                    animateAndDismiss()
                }
            }
        } else {
            val markedUnread = conversation.markedUnread
            readUnreadButton.setImageResource(if (markedUnread) R.drawable.mark_chat_read else R.drawable.mark_chat_unread)
            readUnreadButton.setOnClickListener {
                viewModel.toggleMarkedUnreadStatus(conversation.conversationID)
                animateAndDismiss()
            }
        }
    }
}

class ConversationsAdapter(
    private val context: Context,
    private val viewModel: MapActivityViewModel,
    var conversationList: MutableList<ConversationData>,
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
        val lastMessageCheckMark: ImageView = itemView.findViewById(R.id.check_mark)
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

    fun updateData(newConversationList: List<ConversationData>) {
        val newConversationListSorted = newConversationList
            .sortedWith(compareByDescending<ConversationData> { it.pinned }
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
            lastMessage != null && !lastMessage.read && lastMessage.authorId != viewModel.myUserId
        }
        parentActivity.unreadMessageIcon.visibility = if (hasUnreadMessages) View.VISIBLE else {
            View.INVISIBLE
        }
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
            val lastMessageText = lastMessage.text
            if (lastMessageText != null && lastMessageText.length > 25) {
                holder.lastMessageText.text =
                    context.getString(R.string.long_message_format, lastMessageText.substring(0, 25))
            } else {
                holder.lastMessageText.text = lastMessageText
            }
            holder.lastMessageTimestamp.visibility = View.VISIBLE
            holder.lastMessageTimestamp.text = timestampText
            if ((!lastMessage.read && lastMessage.authorId != viewModel.myUserId) || (conversation.markedUnread)) {
                holder.readIcon.visibility = View.VISIBLE
            } else {
                holder.readIcon.visibility = View.INVISIBLE
            }
            if (lastMessage.authorId == viewModel.myUserId) {
                holder.lastMessageCheckMark.visibility = View.VISIBLE
                holder.lastMessageCheckMark.setImageResource(if (lastMessage.read) R.drawable.double_check_mark else R.drawable.single_check_mark)
            } else {
                holder.lastMessageCheckMark.visibility = View.GONE
            }
        } else {
            holder.lastMessageCheckMark.visibility = View.GONE
            holder.lastMessageText.text = context.getString(R.string.last_message_placeholder)
            holder.lastMessageTimestamp.visibility = View.INVISIBLE
            holder.readIcon.visibility = if (conversation.markedUnread) View.VISIBLE else View.GONE
        }
        val drawablePlaceholder = ContextCompat.getDrawable(context, R.drawable.person_placeholder)
        holder.pictureImageView.setImageDrawable(drawablePlaceholder)
        holder.coroutineScope.launch {
            val bitmap = viewModel.getPictureData(conversation.userData.userId)
            if (bitmap != null) {
                holder.pictureImageView.setImageBitmap(bitmap)
            }
        }
        holder.conversationLayout.setOnClickListener {
            viewModel.disableMarkedUnreadStatus(conversationId)
            viewModel.clearNotificationsForConversation(conversationId)
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
