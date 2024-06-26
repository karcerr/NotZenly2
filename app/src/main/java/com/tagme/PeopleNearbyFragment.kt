package com.tagme

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class PeopleNearbyFragment : Fragment() {
    private lateinit var view: View
    private lateinit var peopleNearbyAdapter: PeopleNearbyAdapter
    private lateinit var api: API
    private lateinit var nestedScrollView: CustomNestedScrollView
    private lateinit var mapActivity: MapActivity
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressLayout: ConstraintLayout
    companion object {
        fun newInstance(): PeopleNearbyFragment {
            val fragment = PeopleNearbyFragment()
            return fragment
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        view = inflater.inflate(R.layout.fragment_people_nearby, container, false)
        progressLayout = view.findViewById(R.id.progress_bar_layout)
        val backButton: ImageButton = view.findViewById(R.id.back_arrow_button)
        mapActivity = requireActivity() as MapActivity
        api = mapActivity.api
        recyclerView = view.findViewById(R.id.people_nearby_recycler_view)
        nestedScrollView = view.findViewById(R.id.people_nearby_nested_scroll_view)
        setupSwipeGesture(
            this,
            nestedScrollView,
            null,
            view,
            mapActivity
        )
        progressLayout.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            api.getNearbyPeople()
            val nearbyListSorted = api.getNearbyPeopleData()?.sortedBy { it.distance }?.toMutableList() ?: mutableListOf()

            peopleNearbyAdapter = PeopleNearbyAdapter(
                requireContext(),
                nearbyListSorted,
                api,
            )
            recyclerView.adapter = peopleNearbyAdapter
            recyclerView.layoutManager = MyLinearLayoutManager(requireContext())

            progressLayout.visibility = View.GONE
        }

        backButton.setOnClickListener{
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        return view
    }
}

class PeopleNearbyAdapter(
    private val context: Context,
    private var userNearbyList: MutableList<API.UserNearbyData>,
    private val api: API
) : RecyclerView.Adapter<PeopleNearbyAdapter.PeopleNearbyViewHolder>() {
    inner class PeopleNearbyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userNearbyLayout: LinearLayout = itemView.findViewById(R.id.user_nearby_linear_layout)
        val nameTextView: TextView = itemView.findViewById(R.id.nickname_text)
        val distanceTextView: TextView = itemView.findViewById(R.id.distance_text)
        val addFriendButton: ImageButton = itemView.findViewById(R.id.add_friend_button)
        val pictureImageView: ShapeableImageView = itemView.findViewById(R.id.picture_image_view)
        val coroutineScope = CoroutineScope(Dispatchers.Main)
    }
    fun updateData(newPeopleNearbyList: List<API.UserNearbyData>) {
        val newPeopleNearbySorted = newPeopleNearbyList.sortedBy { it.distance }
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int {
                return userNearbyList.size
            }

            override fun getNewListSize(): Int {
                return newPeopleNearbySorted.size
            }

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return userNearbyList[oldItemPosition].userData.userId == newPeopleNearbySorted[newItemPosition].userData.userId
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = userNearbyList[oldItemPosition]
                val newItem = newPeopleNearbySorted[newItemPosition]
                return oldItem == newItem
            }
        })
        userNearbyList.clear()
        userNearbyList.addAll(newPeopleNearbySorted)
        diffResult.dispatchUpdatesTo(this)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PeopleNearbyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.user_nearby_item, parent, false)
        return PeopleNearbyViewHolder(view)
    }

    override fun onBindViewHolder(holder: PeopleNearbyViewHolder, position: Int) {
        val userNearbyItem = userNearbyList[position]

        val userId = userNearbyItem.userData.userId
        if (api.getFriendRequestDataList().any {it.userData.userId == userId && it.relation == "request_outgoing"}) {
            holder.addFriendButton.setImageResource(R.drawable.single_check_mark)
        }
        holder.nameTextView.text = userNearbyItem.userData.nickname
        val distance = userNearbyItem.distance
        val (format, value) = if (distance >= 1000) {
            R.string.distance_format_km to String.format("%.1f", distance / 1000f)
        } else {
            val roundedDistance = (distance / 50) * 50
            if (roundedDistance < 100) {
                R.string.distance_format_m_less_than to roundedDistance
            } else {
                R.string.distance_format_m to roundedDistance
            }
        }

        holder.distanceTextView.text = context.getString(format, value)


        val drawablePlaceholder = ContextCompat.getDrawable(context, R.drawable.person_placeholder)
        holder.pictureImageView.setImageDrawable(drawablePlaceholder)
        if (userNearbyItem.userData.profilePictureId != 0) {
            holder.coroutineScope.launch {
                val bitmap = api.getPictureData(userNearbyItem.userData.profilePictureId)
                if (bitmap != null) {
                    holder.pictureImageView.setImageBitmap(bitmap)
                }
            }
        }
        holder.addFriendButton.setOnClickListener {
            holder.coroutineScope.launch {
                val answer = api.sendFriendRequestToWS(userNearbyItem.userData.nickname)
                if (answer != null) {
                    if (answer.getString("status") == "success") {
                        Toast.makeText(context, context.getString(R.string.friend_request_sent), Toast.LENGTH_SHORT)
                            .show()
                        holder.addFriendButton.setImageResource(R.drawable.single_check_mark)
                        holder.addFriendButton.isClickable = false
                        api.getFriendRequestsFromWS()
                        val updatedRequests = api.getFriendRequestDataList()
                        (context as MapActivity).profileFragment.friendRequestAdapter.updateData(updatedRequests)
                    }
                }
            }
        }
        holder.userNearbyLayout.setOnClickListener {
            val userProfileFragment = UserProfileFragment.newInstance(userId)
            (context as MapActivity).fragmentManager.beginTransaction()
                .add(R.id.profile_fragment, userProfileFragment)
                .addToBackStack(null)
                .commit()
        }
    }

    override fun getItemCount(): Int {
        return userNearbyList.size
    }
}
