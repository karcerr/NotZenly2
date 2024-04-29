package tagme

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tagme.R
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max


class ConversationFragment : Fragment(), MessageAdapter.LastMessageIdListener {
    private lateinit var api: API
    private var lastMessageId: Int = -1
    private lateinit var recyclerView: RecyclerView
    private var messageUpdateHandler: Handler? = null
    private var messageUpdateRunnable: Runnable? = null


    companion object {
        private const val MESSAGE_UPDATE_INTERVAL_MS = 1000L
        private const val ARG_CONVERSATION_ID = "conversationId"
        private const val ARG_NICKNAME = "nickname"

        fun newInstance(conversationId: Int, nickname: String): ConversationFragment {
            val fragment = ConversationFragment()
            val args = Bundle()
            args.putInt(ARG_CONVERSATION_ID, conversationId)
            args.putString(ARG_NICKNAME, nickname)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        api = (requireActivity() as MapActivity).api
        val view = inflater.inflate(R.layout.fragment_conversation, container, false)
        val nickname: TextView = view.findViewById(R.id.conversation_name)
        val pfp: ShapeableImageView = view.findViewById(R.id.conversation_pfp)
        val backButton: ImageButton = view.findViewById(R.id.back_arrow_button)
        val myId = api.myUserId
        val conversationId = requireArguments().getInt(ARG_CONVERSATION_ID)
        val editText: EditText = view.findViewById(R.id.message_edit_text)
        val sendMessageButton: ImageButton = view.findViewById(R.id.send_msg_button)
        nickname.text = requireArguments().getString(ARG_NICKNAME)
        recyclerView = view.findViewById(R.id.messageRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val isAtBottom = !recyclerView.canScrollVertically(1)
                (recyclerView.adapter as? MessageAdapter)?.bottomSticking = isAtBottom
            }
        })
        recyclerView.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom) {
                recyclerView.post {
                    recyclerView.smoothScrollToPosition(
                        recyclerView.adapter!!.itemCount - 1
                    )
                }
            }
        }
        CoroutineScope(Dispatchers.Main).launch {
            api.getMessagesFromWS(conversationId, -1)
            val conversation =  api.getConversationData(conversationId)

            if (conversation != null) {
                val deepConversationCopy = conversation.copy()
                deepConversationCopy.messages = conversation.messages.map {it.copy()}.toMutableList()
                deepConversationCopy.messages.sortBy { it.timestamp }
                lastMessageId = conversation.messages.lastOrNull()?.messageId ?: -1
                val adapter = MessageAdapter(deepConversationCopy.messages, myId, recyclerView)
                adapter.setLastMessageIdListener(this@ConversationFragment)
                recyclerView.adapter = adapter
                recyclerView.scrollToPosition(adapter.itemCount - 1)

                val bitmap = api.getPictureData(conversation.userData.profilePictureId)
                if (bitmap != null) {
                    pfp.setImageBitmap(bitmap)
                }

            }
        }
        backButton.setOnClickListener{
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        sendMessageButton.setOnClickListener {
            val text = editText.text.toString()
            if (text.isNotBlank()) {
                CoroutineScope(Dispatchers.Main).launch {
                    val answer = api.sendMessageToWS(conversationId, text)
                    if (answer != null && answer.getString("status") == "success") {
                        api.getNewMessagesFromWS(conversationId, lastMessageId)
                        val updatedMessages = api.getConversationData(conversationId)?.messages.orEmpty()
                        val updatedMessagesDeepCopy = updatedMessages.map {it.copy()}
                        Log.d("Tagme_custom_log", "updatedMessages: $updatedMessagesDeepCopy")
                        (recyclerView.adapter as? MessageAdapter)?.updateData(updatedMessagesDeepCopy)
                        if ((recyclerView.adapter as? MessageAdapter)?.bottomSticking == false) {
                            recyclerView.smoothScrollToPosition(recyclerView.adapter!!.itemCount - 1)
                        }
                        editText.text.clear()
                    } else {
                        val errorText = answer?.getString("message")
                        Toast.makeText(requireContext(), "Error: $errorText", Toast.LENGTH_LONG)
                    }
                }
            }
        }
        return view
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        messageUpdateRunnable = Runnable {
            CoroutineScope(Dispatchers.Main).launch {
                val conversationId = requireArguments().getInt(ARG_CONVERSATION_ID)
                val answer = api.getNewMessagesFromWS(conversationId, lastMessageId)
                if (answer != null) {
                    val status = answer.optString("status")
                    if (status == "success") {
                        val messageObject = answer.optJSONObject("message")
                        if (messageObject != null) {
                            val result = messageObject.optJSONArray("result")
                            if (result != null && result.length() != 0) {
                                val updatedMessages = api.getConversationData(conversationId)?.messages.orEmpty()
                                val updatedMessagesDeepCopy = updatedMessages.map {it.copy()}
                                (recyclerView.adapter as? MessageAdapter)?.updateData(updatedMessagesDeepCopy)
                            }
                        }
                    }
                }
                messageUpdateHandler?.postDelayed(this@ConversationFragment.messageUpdateRunnable!!, MESSAGE_UPDATE_INTERVAL_MS)
            }
        }
        startMessageUpdates()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        stopMessageUpdates()
        recyclerView.viewTreeObserver.removeOnGlobalLayoutListener {}
    }
    private fun startMessageUpdates() {
        messageUpdateHandler = Handler(Looper.getMainLooper())
        messageUpdateRunnable?.let { messageUpdateHandler?.postDelayed(it, MESSAGE_UPDATE_INTERVAL_MS) }
    }

    private fun stopMessageUpdates() {
        messageUpdateRunnable?.let { messageUpdateHandler?.removeCallbacks(it) }
        messageUpdateHandler = null
    }
    override fun onLastMessageIdChanged(lastMessageId: Int) {
        this.lastMessageId = max(this.lastMessageId, lastMessageId)
        Log.d("Tagme_custom_log", this.lastMessageId.toString())
    }
}
class MessageAdapter(
    private var messageList:MutableList<API.MessageData>,
    private val myUserId: Int,
    private val recyclerView: RecyclerView
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private lateinit var lastMessageIdListener: LastMessageIdListener
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS][.SS][.S]")
    var bottomSticking = true
    companion object {
        const val INCOMING_MESSAGE_TYPE = 0
        const val OUTGOING_MESSAGE_TYPE = 1
        const val DATE_SEPARATOR_TYPE = 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            INCOMING_MESSAGE_TYPE -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.incoming_message_item, parent, false)
                MessageViewHolder(view)
            }
            OUTGOING_MESSAGE_TYPE -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.outgoing_message_item, parent, false)
                MessageViewHolder(view)
            }
            DATE_SEPARATOR_TYPE -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.date_separator_item, parent, false)
                DateSeparatorViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder.itemViewType) {
            INCOMING_MESSAGE_TYPE, OUTGOING_MESSAGE_TYPE -> {
                val messageViewHolder = holder as MessageViewHolder
                val message = messageList[position]
                messageViewHolder.bind(message)
            }
            DATE_SEPARATOR_TYPE -> {
                val dateSeparatorViewHolder = holder as DateSeparatorViewHolder
                val message = messageList[position]
                val date = message.timestamp.toLocalDateTime().toLocalDate().toString()
                dateSeparatorViewHolder.bind(date)
            }
        }
    }


    override fun getItemCount(): Int {
        return messageList.size
    }
    fun updateData(newMessages: List<API.MessageData>) {
        val messagesToAdd = newMessages.filter { newMessage ->
            !messageList.any { existingMessage ->
                existingMessage.messageId == newMessage.messageId
            }
        }
        val messagesToUpdate = newMessages.filter { newMessage ->
            messageList.any { existingMessage ->
                existingMessage.messageId == newMessage.messageId && existingMessage != newMessage
            }
        }
        val messagesToRemove = messageList.filter { existingMessage ->
            !newMessages.any { newMessage ->
                existingMessage.messageId == newMessage.messageId
            }
        }

        if (messagesToAdd.isNotEmpty()) {
            messageList.addAll(messagesToAdd)
            messageList.sortBy { it.timestamp }
            notifyItemRangeInserted(itemCount, messagesToAdd.size)
            Log.d("Tagme_custom_log", "Messages to add are: $messagesToAdd")
        } else {
            Log.d("Tagme_custom_log", "Messages to add are empty")
        }

        messagesToUpdate.forEach { updatedMessage ->
            val index = messageList.indexOfFirst { it.messageId == updatedMessage.messageId }
            if (index != -1) {
                messageList[index] = updatedMessage
                notifyItemChanged(index)
            }
        }
        if (messagesToUpdate.isNotEmpty()) {
            Log.d("Tagme_custom_log", "Messages to update are: $messagesToUpdate")
        } else {
            Log.d("Tagme_custom_log", "Messages to update are empty")
        }
        messagesToRemove.forEach { removedMessage ->
            val index = messageList.indexOfFirst { it.messageId == removedMessage.messageId }
            if (index != -1) {
                messageList.removeAt(index)
                notifyItemRemoved(index)
            }
        }
        if (messagesToRemove.isNotEmpty()) {
            Log.d("Tagme_custom_log", "Messages to remove are: $messagesToRemove")
        } else {
            Log.d("Tagme_custom_log", "Messages to remove are empty")
        }
        messageList.lastOrNull()?.let { lastMessage ->
            lastMessageIdListener.onLastMessageIdChanged(lastMessage.messageId)
            Log.d("Tagme_custom_log", lastMessage.messageId.toString())
        }

        if (bottomSticking && messagesToAdd.isNotEmpty()) {
            recyclerView.smoothScrollToPosition(itemCount - 1)
            Log.d("Tagme_custom_log", "ScrolledByUpdateData")
        }
    }

    override fun getItemViewType(position: Int): Int {
        val message = messageList[position]
        val previousMessage = if (position > 0) messageList[position - 1] else null

        if (previousMessage == null) {
            return if (message.authorId == myUserId) OUTGOING_MESSAGE_TYPE else INCOMING_MESSAGE_TYPE
        }

        val currentMessageDate = message.timestamp.toLocalDateTime().toLocalDate()
        val previousMessageDate = previousMessage.timestamp.toLocalDateTime().toLocalDate()

        return if (currentMessageDate != previousMessageDate) DATE_SEPARATOR_TYPE
        else if (message.authorId == myUserId) OUTGOING_MESSAGE_TYPE
        else INCOMING_MESSAGE_TYPE
    }


    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(message: API.MessageData) {
            val timestampDateTime = LocalDateTime.parse(message.timestamp.toString(), dateFormatter)
            val timestampText = timestampDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))

            if (itemViewType == OUTGOING_MESSAGE_TYPE) {
                itemView.findViewById<TextView>(R.id.outgoing_message_text_content).text = message.text
                itemView.findViewById<TextView>(R.id.outgoing_message_timestamp).text = timestampText
            } else {
                itemView.findViewById<TextView>(R.id.incoming_message_text_content).text = message.text
                itemView.findViewById<TextView>(R.id.incoming_message_timestamp).text = timestampText
            }
        }
    }
    inner class DateSeparatorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateTextView: TextView = itemView.findViewById(R.id.dateTextView)
        fun bind(date: String) {
            dateTextView.text = date
        }
    }

    interface LastMessageIdListener {
        fun onLastMessageIdChanged(lastMessageId: Int)
    }
    fun setLastMessageIdListener(listener: LastMessageIdListener) {
        lastMessageIdListener = listener
    }
}
fun Timestamp.toLocalDateTime(): LocalDateTime {
    return LocalDateTime.ofInstant(this.toInstant(), ZoneId.systemDefault())
}
