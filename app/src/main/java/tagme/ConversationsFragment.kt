package tagme

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.example.tagme.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ConversationsFragment : Fragment() {
    lateinit var conversationsAdapter: ConversationsAdapter
    private lateinit var api: API
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_conversations, container, false)
        val backButton: ImageButton = view.findViewById(R.id.back_arrow_button)
        api = (requireActivity() as MapActivity).api
        CoroutineScope(Dispatchers.Main).launch {
            api.getConversationsFromWS()
        }
        val conversationsRecyclerView: RecyclerView = view.findViewById(R.id.conversations_recycler_view)
        conversationsAdapter = ConversationsAdapter(
            requireContext(),
            api.getConversationsData(), api,
            requireActivity() as AppCompatActivity
        )

        conversationsRecyclerView.adapter = conversationsAdapter
        conversationsRecyclerView.layoutManager = MyLinearLayoutManager(requireContext())

        backButton.setOnClickListener{
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        return view
    }
}

class ConversationsAdapter(
    private val context: Context,
    private var conversationList: MutableList<API.ConversationData>,
    private val api: API,
    private val parentActivity: AppCompatActivity
) : RecyclerView.Adapter<ConversationsAdapter.ConversationViewHolder>() {
    inner class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.conversation_name)
        val pictureImageView: ImageView = itemView.findViewById(R.id.conversation_picture)
        val conversationLayout: LinearLayout = itemView.findViewById(R.id.conversation_layout)
        val coroutineScope = CoroutineScope(Dispatchers.Main)
    }
    fun updateData(newConversationList: MutableList<API.ConversationData>) {
        conversationList = newConversationList
        notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.conversation_item, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val conversation = conversationList[position]
        val conversationId = conversation.conversationID

        holder.nameTextView.text = conversation.userData.nickname
        val picture = api.getPicturesData().find { it.pictureId == conversation.userData.profilePictureId }
        if (picture != null) {
            holder.coroutineScope.launch {
                val bitmap = API.getInstance(context).getPictureData(context, picture.pictureId)
                holder.pictureImageView.setImageBitmap(bitmap)
            }
        }
        holder.conversationLayout.setOnClickListener {
            val conversationFragment = ConversationFragment.newInstance(conversationId)
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
