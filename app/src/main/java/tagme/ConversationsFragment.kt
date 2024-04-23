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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
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
        val view = inflater.inflate(R.layout.fragment_conversation, container, false)
        val backButton: ImageButton = view.findViewById(R.id.back_arrow_button)
        api = (requireActivity() as MapActivity).api
        CoroutineScope(Dispatchers.Main).launch {
            api.getConversations()
        }
        val conversationsRecyclerView: RecyclerView = view.findViewById(R.id.conversations_recycler_view)
        conversationsAdapter = ConversationsAdapter(requireContext(), api.getConversationsData(), api, requireActivity().supportFragmentManager)
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
    private val childFragmentManager: FragmentManager
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
        val friend = conversationList[position]

        holder.nameTextView.text = friend.userData.nickname
        val picture = api.getPicturesData().find { it.pictureId == friend.userData.profilePictureId }
        if (picture != null) {
            holder.coroutineScope.launch {
                val bitmap = API.getInstance(context).getPictureData(context, picture.pictureId)
                holder.pictureImageView.setImageBitmap(bitmap)
            }
        }
        holder.conversationLayout.setOnClickListener {
            val userProfileDialog = UserProfileDialogFragment.newInstance(friend.userData.userId)
            userProfileDialog.show(childFragmentManager, "userProfileDialog")
        }
    }

    override fun getItemCount(): Int {
        return conversationList.size
    }
}
