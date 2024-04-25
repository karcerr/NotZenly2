package tagme

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import java.time.LocalDateTime
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
        CoroutineScope(Dispatchers.Main).launch {
            api.getMessagesFromWS(conversationId, -1)
            val conversation = conversationId.let { api.getConversationData(it) }
            if (conversation != null) {
                recyclerView = view.findViewById(R.id.messageRecyclerView)
                recyclerView.layoutManager = LinearLayoutManager(context)
                val adapter = MessageAdapter(conversation.messages, myId)
                adapter.setLastMessageIdListener(this@ConversationFragment)
                recyclerView.adapter = adapter
                recyclerView.scrollToPosition(adapter.itemCount - 1)

                val picture = api.getPicturesData().find { it.pictureId == conversation.userData.profilePictureId }
                if (picture != null) {
                    val bitmap = requireContext().let {
                        API.getInstance(it).getPictureData(requireContext(), picture.pictureId)
                    }
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
                        (recyclerView.adapter as? MessageAdapter)?.updateData(updatedMessages)
                        editText.text.clear()

                        recyclerView.scrollToPosition(updatedMessages.size - 1)
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
        messageUpdateRunnable = object : Runnable {
            override fun run() {
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
                                    (recyclerView.adapter as? MessageAdapter)?.updateData(updatedMessages)
                                }
                            }
                        }
                    }
                    messageUpdateHandler?.postDelayed(this@ConversationFragment.messageUpdateRunnable!!, MESSAGE_UPDATE_INTERVAL_MS)
                }
            }
        }
        startMessageUpdates()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        stopMessageUpdates()
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
    }
}
class MessageAdapter(
    private var messageList:MutableList<API.MessageData>,
    private val myUserId: Int
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {
    private lateinit var lastMessageIdListener: LastMessageIdListener

    companion object {
        const val INCOMING_MESSAGE_TYPE = 0
        const val OUTGOING_MESSAGE_TYPE = 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutId = if (viewType == INCOMING_MESSAGE_TYPE) {
            R.layout.incoming_message_item
        } else {
            R.layout.outgoing_message_item
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messageList[position]
        val messageId = message.messageId
        lastMessageIdListener.onLastMessageIdChanged(messageId)
        holder.bind(message)
    }

    override fun getItemCount(): Int {
        return messageList.size
    }
    fun updateData(newMessages: List<API.MessageData>) {
        val uniqueNewMessages = newMessages.filter { newMessage ->
            !messageList.any { existingMessage ->
                existingMessage.messageId == newMessage.messageId
            }
        }
        messageList.addAll(uniqueNewMessages)
        notifyDataSetChanged()
    }
    override fun getItemViewType(position: Int): Int {
        val message = messageList[position]
        return if (message.authorId == myUserId) {
            OUTGOING_MESSAGE_TYPE
        } else {
            INCOMING_MESSAGE_TYPE
        }
    }

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS][.SS][.S]")

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
    interface LastMessageIdListener {
        fun onLastMessageIdChanged(lastMessageId: Int)
    }
    fun setLastMessageIdListener(listener: LastMessageIdListener) {
        lastMessageIdListener = listener
    }
}
