package tagme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tagme.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ConversationFragment : Fragment() {
    private lateinit var api: API
    companion object {
        private const val ARG_CONVERSATION_ID = "conversationId"

        fun newInstance(conversationId: Int): ConversationFragment {
            val fragment = ConversationFragment()
            val args = Bundle()
            args.putInt(ARG_CONVERSATION_ID, conversationId)
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
        val myId = api.myUserId
        val conversationId = arguments?.getInt(ARG_CONVERSATION_ID)
        if (conversationId != null) {

            CoroutineScope(Dispatchers.Main).launch {
                api.getMessagesFromWS(conversationId, -1)
                val conversation = conversationId.let { api.getConversationData(it) }
                if (conversation != null) {
                    val recyclerView: RecyclerView = view.findViewById(R.id.messageRecyclerView)
                    recyclerView.layoutManager = LinearLayoutManager(context)
                    val adapter = MessageAdapter(conversation.messages, myId)
                    recyclerView.adapter = adapter
                }
            }

        }
        return view
    }
}
class MessageAdapter(private val messageList: List<API.MessageData>, private val myUserId: Int) :
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

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
        holder.bind(message)
    }

    override fun getItemCount(): Int {
        return messageList.size
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
        fun bind(message: API.MessageData) {
            if (message.authorId == myUserId) {
                itemView.findViewById<TextView>(R.id.outgoing_message_text_content).text = message.text
                itemView.findViewById<TextView>(R.id.outgoing_message_timestamp).text = message.timestamp.toString()
            } else {
                itemView.findViewById<TextView>(R.id.incoming_message_text_content).text = message.text
                itemView.findViewById<TextView>(R.id.incoming_message_timestamp).text = message.timestamp.toString()
            }
        }
    }
}
