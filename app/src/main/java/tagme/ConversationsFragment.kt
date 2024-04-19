package tagme

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
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
        val view = inflater.inflate(R.layout.fragment_conversation, container, false)
        api = (requireActivity() as MapActivity).api
        CoroutineScope(Dispatchers.Main).launch {
            api.getConversations()
        }
        val conversationsRecyclerView: RecyclerView = view.findViewById(R.id.converations_recycler_view)
        conversationsAdapter = ConversationsAdapter(api.getConversationsData(), api)
        conversationsRecyclerView.adapter = conversationsAdapter
        conversationsRecyclerView.layoutManager = MyLinearLayoutManager(requireContext())

        return view
    }
}

class ConversationsAdapter(
    private var conversationList: MutableList<API.ConversationData>,
    private val api: API
) : RecyclerView.Adapter<ConversationsAdapter.ConversationViewHolder>() {
    inner class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.friend_name)
        val pictureImageView: ImageView = itemView.findViewById(R.id.friend_picture)
    }
    fun updateData(newConversationList: MutableList<API.ConversationData>) {
        conversationList = newConversationList
        notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.friend_item, parent, false)
        return ConversationViewHolder(view)
    }


    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val friend = conversationList[position]

        holder.nameTextView.text = friend.userData.nickname
        // Set profile picture if available, otherwise set placeholder
        friend.userData.profilePicture.pfpData?.let {
            val bitmap = it.let { it1 -> BitmapFactory.decodeByteArray(it, 0, it1.size) }
            holder.pictureImageView.setImageBitmap(bitmap)
        } ?: holder.pictureImageView.setImageResource(R.drawable.person_placeholder)
    }

    override fun getItemCount(): Int {
        return conversationList.size
    }
}
