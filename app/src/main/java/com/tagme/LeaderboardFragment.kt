package com.tagme

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
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
    private lateinit var progressLayout: ConstraintLayout
    var isSelfLayoutVisible = false
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
        progressLayout = view.findViewById(R.id.progress_bar_layout)
        val backButton: ImageButton = view.findViewById(R.id.back_arrow_button)
        mapActivity = requireActivity() as MapActivity
        api = mapActivity.api
        recyclerView = view.findViewById(R.id.leaderboard_recycler_view)
        nestedScrollView = view.findViewById(R.id.leaderboard_nested_scroll_view)
        val yourPlaceLayout = view.findViewById<LinearLayout>(R.id.your_place_layout)
        setupSwipeGesture(
            this,
            nestedScrollView,
            null,
            view,
            mapActivity
        )
        progressLayout.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            api.getLeaderBoardFromWS()
            val leaderboardListSorted = api.getLeaderBoardData()?.sortedBy { it.place }?.toMutableList() ?: mutableListOf()

            if (leaderboardListSorted.none { it.userData.userId == api.myUserId }) {
                isSelfLayoutVisible = true
                yourPlaceLayout.visibility = View.VISIBLE
                val myNameTextView: TextView = view.findViewById(R.id.your_nickname_text)
                val myPlaceTextView: TextView = view.findViewById(R.id.your_place_text)
                val myTagsTextView: TextView = view.findViewById(R.id.your_tags_counter_text)
                val myPictureImageView: ShapeableImageView = view.findViewById(R.id.your_picture_image_view)

                myNameTextView.text = api.myNickname
                myPlaceTextView.text = api.myPlace.toString()
                myTagsTextView.text = api.myTags.toString()
                if (api.myPfpId != 0) {
                    val bitmap = api.getPictureData(api.myPfpId)
                    if (bitmap != null) {
                        myPictureImageView.setImageBitmap(bitmap)
                    }
                }
            } else {
                isSelfLayoutVisible = false
                yourPlaceLayout.visibility = View.GONE
            }
            leaderboardAdapter = LeaderboardAdapter(
                requireContext(),
                leaderboardListSorted,
                api,
                isSelfLayoutVisible
            )
            recyclerView.adapter = leaderboardAdapter
            recyclerView.layoutManager = MyLinearLayoutManager(requireContext())


            progressLayout.visibility = View.GONE
        }

        backButton.setOnClickListener{
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        return view
    }
}

class LeaderboardAdapter(
    private val context: Context,
    private var leaderboardList: MutableList<API.LeaderBoardData>,
    private val api: API,
    private val isSelfLayoutVisible: Boolean
) : RecyclerView.Adapter<LeaderboardAdapter.LeaderboardViewHolder>() {
    inner class LeaderboardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val leaderboardLinearLayout: LinearLayout = itemView.findViewById(R.id.leaderboard_linear_layout)
        val nameTextView: TextView = itemView.findViewById(R.id.nickname_text)
        val placeTextView: TextView = itemView.findViewById(R.id.place_text)
        val starsImage: ImageView = itemView.findViewById(R.id.stars_image)
        val tagsTextView: TextView = itemView.findViewById(R.id.tags_counter_text)
        val pictureImageView: ShapeableImageView = itemView.findViewById(R.id.picture_image_view)
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
        if (position == 9 && isSelfLayoutVisible) {
            val separator = holder.itemView.findViewById<View>(R.id.separator)
            separator.setBackgroundResource(R.drawable.separator_big_dashed)
        }
        val userId = leaderboardItem.userData.userId
        if (userId == api.myUserId) {
            holder.leaderboardLinearLayout.setBackgroundResource(R.drawable.leaderboard_highlight)
        }
        holder.nameTextView.text = leaderboardItem.userData.nickname
        holder.placeTextView.text = leaderboardItem.place.toString()
        if (leaderboardItem.place <= 3) {
            holder.pictureImageView.strokeColor = ContextCompat.getColorStateList(context, R.color.yellow)
            holder.starsImage.visibility = View.VISIBLE
            holder.placeTextView.setTextColor(ContextCompat.getColor(context, R.color.yellow))
            val layoutParams = holder.pictureImageView.layoutParams
            var squareSide = 48f
            when (leaderboardItem.place){
                1 -> {
                    holder.starsImage.setImageResource(R.drawable.star_triple)
                    squareSide = 78f
                }
                2 -> {
                    holder.starsImage.setImageResource(R.drawable.star_double)
                    squareSide = 68f
                }
                3 -> {
                    holder.starsImage.setImageResource(R.drawable.star)
                    squareSide = 58f
                }
            }
            val pixels = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, squareSide, context.resources.displayMetrics).toInt()
            layoutParams.width = pixels
            layoutParams.height = pixels
            holder.pictureImageView.layoutParams = layoutParams
        }

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
            if (userId == api.myUserId)
                return@setOnClickListener
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
