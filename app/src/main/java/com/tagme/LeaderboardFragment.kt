package com.tagme

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class LeaderboardFragment : Fragment() {
    private lateinit var view: View
    private lateinit var leaderboardAdapter: LeaderboardAdapter
    private lateinit var api: API
    private lateinit var nestedScrollView: CustomNestedScrollView
    private lateinit var mapActivity: MapActivity
    private lateinit var recyclerView: RecyclerView
    companion object {
        fun newInstance(): LeaderboardFragment {
            val fragment = LeaderboardFragment()
            return fragment
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        view = inflater.inflate(R.layout.fragment_leaderboard, container, false)
        val backButton: ImageButton = view.findViewById(R.id.back_arrow_button)
        mapActivity = requireActivity() as MapActivity
        api = mapActivity.api
        recyclerView = view.findViewById(R.id.leaderboard_recycler_view)
        nestedScrollView = view.findViewById(R.id.leaderboard_nested_scroll_view)
        setupSwipeGesture(
            this,
            nestedScrollView,
            null,
            view,
            mapActivity
        )
        val conversationListSorted = api.getLeaderBoardData()?.sortedBy { it.place }?.toMutableList() ?: mutableListOf()

        leaderboardAdapter = LeaderboardAdapter(
            requireContext(),
            conversationListSorted,
            api
        )

        recyclerView.adapter = leaderboardAdapter
        recyclerView.layoutManager = MyLinearLayoutManager(requireContext())

        backButton.setOnClickListener{
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        return view
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }
    override fun onDestroyView() {
        super.onDestroyView()
    }
}

class LeaderboardAdapter(
    private val context: Context,
    private var leaderboardList: MutableList<API.LeaderBoardData>,
    private val api: API,
) : RecyclerView.Adapter<LeaderboardAdapter.LeaderboardViewHolder>() {
    inner class LeaderboardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val leaderboardLinearLayout: LinearLayout = itemView.findViewById(R.id.leaderboard_linear_layout)
        val nameTextView: TextView = itemView.findViewById(R.id.nickname_text)
        val placeTextView: TextView = itemView.findViewById(R.id.place_text)
        val tagsTextView: TextView = itemView.findViewById(R.id.tags_counter_text)
        val pictureImageView: ImageView = itemView.findViewById(R.id.picture_image_view)
        val coroutineScope = CoroutineScope(Dispatchers.Main)
    }
    fun updateData(newLeaderboardList: List<API.LeaderBoardData>) {
        val newLeaderboardListSorted = newLeaderboardList.sortedBy { it.place }
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int {
                return leaderboardList.size
            }

            override fun getNewListSize(): Int {
                return newLeaderboardListSorted.size
            }

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return leaderboardList[oldItemPosition].userData.userId == newLeaderboardListSorted[newItemPosition].userData.userId
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = leaderboardList[oldItemPosition]
                val newItem = newLeaderboardListSorted[newItemPosition]
                return oldItem == newItem
            }
        })
        leaderboardList.clear()
        leaderboardList.addAll(newLeaderboardListSorted)
        diffResult.dispatchUpdatesTo(this)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaderboardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.leaderboard_item, parent, false)
        return LeaderboardViewHolder(view)
    }

    override fun onBindViewHolder(holder: LeaderboardViewHolder, position: Int) {
        val leaderboardItem = leaderboardList[position]
        val userId = leaderboardItem.userData.userId
        holder.nameTextView.text = leaderboardItem.userData.nickname
        holder.placeTextView.text = leaderboardItem.place.toString()
        holder.tagsTextView.text = leaderboardItem.tags.toString()
        val drawablePlaceholder = ContextCompat.getDrawable(context, R.drawable.person_placeholder)
        holder.pictureImageView.setImageDrawable(drawablePlaceholder)
        if (leaderboardItem.userData.profilePictureId != 0) {
            holder.coroutineScope.launch {
                val bitmap = api.getPictureData(leaderboardItem.userData.profilePictureId)
                if (bitmap != null) {
                    holder.pictureImageView.setImageBitmap(bitmap)
                }
            }
        }
        holder.leaderboardLinearLayout.setOnClickListener {
            val userProfileFragment = UserProfileFragment.newInstance(userId)
            (context as MapActivity).fragmentManager.beginTransaction()
                .add(R.id.profile_fragment, userProfileFragment)
                .addToBackStack(null)
                .commit()
        }
    }

    override fun getItemCount(): Int {
        return leaderboardList.size
    }
}
