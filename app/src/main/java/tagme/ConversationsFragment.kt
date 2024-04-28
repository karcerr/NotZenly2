package tagme

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.tagme.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class ConversationsFragment : Fragment() {
    lateinit var conversationsAdapter: ConversationsAdapter
    private lateinit var api: API
    private val conversationUpdateInterval = 1000L
    private var conversationUpdateHandler: Handler? = null
    private var conversationUpdateRunnable: Runnable? = null
    private lateinit var recyclerView: RecyclerView
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d("Tagme_custom_log", "onCreateView")
        val view = inflater.inflate(R.layout.fragment_conversations, container, false)
        val backButton: ImageButton = view.findViewById(R.id.back_arrow_button)
        api = (requireActivity() as MapActivity).api
        recyclerView = view.findViewById(R.id.conversations_recycler_view)
        val conversationListSorted = api.getConversationsData().map { conversation ->
            conversation.copy()
        }.sortedByDescending { it.lastMessage?.timestamp }.toMutableList()
        conversationsAdapter = ConversationsAdapter(
            requireContext(),
            conversationListSorted, api,
            requireActivity() as AppCompatActivity
        )

        recyclerView.adapter = conversationsAdapter
        recyclerView.layoutManager = MyLinearLayoutManager(requireContext())

        backButton.setOnClickListener{
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        return view
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d("Tagme_custom_log", "onViewCreated")
        super.onViewCreated(view, savedInstanceState)
        conversationUpdateRunnable = Runnable {
            CoroutineScope(Dispatchers.Main).launch {
                api.getConversationsFromWS()
                val updatedConversations = api.getConversationsData()
                (recyclerView.adapter as? ConversationsAdapter)?.updateData(updatedConversations)
                conversationUpdateHandler?.postDelayed(conversationUpdateRunnable!!, conversationUpdateInterval)
            }
        }
        startConversationUpdates()
    }
    override fun onDestroyView() {
        Log.d("Tagme_custom_log", "onDestroyView was called")
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
}

class ConversationsAdapter(
    private val context: Context,
    private var conversationList: MutableList<API.ConversationData>,
    private val api: API,
    private val parentActivity: AppCompatActivity
) : RecyclerView.Adapter<ConversationsAdapter.ConversationViewHolder>() {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS][.SS][.S]")
    inner class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.conversation_name)
        val lastMessageText: TextView = itemView.findViewById(R.id.last_message_text)
        val lastMessageTimestamp: TextView = itemView.findViewById(R.id.last_message_timestamp)
        val readIcon: ImageView = itemView.findViewById(R.id.read_icon)
        val pictureImageView: ImageView = itemView.findViewById(R.id.conversation_picture)
        val conversationLayout: LinearLayout = itemView.findViewById(R.id.conversation_layout)
        val coroutineScope = CoroutineScope(Dispatchers.Main)
    }
    fun updateData(newConversationList: List<API.ConversationData>) {
        val newConversationListSorted = newConversationList.sortedByDescending { it.lastMessage?.timestamp }
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
                val didChange = (oldItem.lastMessage == newItem.lastMessage && oldItem.userData == newItem.userData)
                val didListChange2 = (conversationList == newConversationListSorted)
                Log.d("Tagme_custom_log", "areContentsTheSame $didChange")
                Log.d("Tagme_custom_log", "areListsTheSame2 $didListChange2")
                return didChange
            }
        })
        diffResult.dispatchUpdatesTo(this)
        conversationList = newConversationListSorted.map { conversation ->
            conversation.copy()
        }.toMutableList()
        conversationList.sortByDescending { it.lastMessage?.timestamp }

    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.conversation_item, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val conversation = conversationList[position]
        val conversationId = conversation.conversationID

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
                .replace(R.id.conversations_fragment, conversationFragment)
                .addToBackStack(null)
                .commit()
        }
    }

    override fun getItemCount(): Int {
        return conversationList.size
    }
}
