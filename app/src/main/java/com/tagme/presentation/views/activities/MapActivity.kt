package com.tagme.presentation.views.activities

import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.transition.Slide
import android.util.Log
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.*
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel
import com.tagme.R
import com.tagme.databinding.MapActivityBinding
import com.tagme.domain.models.FriendData
import com.tagme.domain.models.GeoStoryData
import com.tagme.presentation.adapters.SearchedFriendsAdapter
import com.tagme.presentation.viewmodels.MapActivityViewModel
import com.tagme.presentation.views.CustomIconOverlay
import com.tagme.presentation.views.fragments.*
import com.tagme.utils.hideKeyboard
import com.tagme.utils.setSoftInputMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration.getInstance
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.lang.ref.WeakReference
import javax.inject.Inject

@AndroidEntryPoint
class MapActivity @Inject constructor() : AppCompatActivity(), MapActivityViewModel.GeoStoryActionListener {
    val viewModel: MapActivityViewModel by viewModels()

    private lateinit var map: MapView
    private lateinit var mapController: IMapController
    private lateinit var binding: MapActivityBinding
    private lateinit var centralizeButtonFrame: FrameLayout
    private lateinit var profileButtonFrame: FrameLayout
    private lateinit var messagesButtonFrame: FrameLayout
    private lateinit var createGeoStoryFrame: FrameLayout
    private lateinit var searchFrame: FrameLayout
    lateinit var unreadMessageIcon: ImageView
    lateinit var newRequestIcon: ImageView
    private lateinit var clickedFriendProfileFrame: FrameLayout
    private lateinit var clickedFriendMessageFrame: FrameLayout
    private lateinit var clickedGeoStoryViewFrame: FrameLayout
    private lateinit var clickedIconDistanceAndSpeedLayout: LinearLayout
    private lateinit var clickedViewsAndTimeLayout: LinearLayout
    private lateinit var onCLickedOverlays: LinearLayout
    private lateinit var topButtonsOverlay: LinearLayout
    private lateinit var bottomButtonsOverlay: LinearLayout
    private lateinit var searchWindow: FrameLayout
    private lateinit var searchAdapter: SearchedFriendsAdapter
    lateinit var profileFragment: ProfileFragment
    private lateinit var conversationsFragment: ConversationsFragment
    private lateinit var geoStoryCreation: GeoStoryCreationFragment
    private lateinit var geoStoryView: GeoStoryViewFragment
    private lateinit var clickedFriendNicknameTextView: TextView
    private lateinit var clickedFriendDistanceTextView: TextView
    private lateinit var clickedFriendSpeedTextView: TextView
    private lateinit var clickedGeoStoryViewsTextView: TextView
    private lateinit var clickedGeoStoryTimeTextView: TextView
    private lateinit var copyrightOSV: TextView
    private lateinit var overlappedIconsAdapter: OverlappedIconsAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var searchedFriendsListView: RecyclerView
    private lateinit var searchDarkOverlay: View
    private lateinit var findPeopleNearbyButton: Button
    private var _centeredOverlay: CustomIconOverlay? = null
    var centeredOverlay: CustomIconOverlay?
        get() = _centeredOverlay
        set(value) {
            if (_centeredOverlay != value) {
                _centeredOverlay?.setSize(100)
                _centeredOverlay = value
                centeredOverlay?.setSize(150)
            }
        }
    lateinit var coroutineScope: CoroutineScope

    private var scaleFactor = 15.0
    private var centeredTargetId = -1
    private var isCenteredUser = false
    private var isAnimating = false
    private var isUiHidden = false
    private var isSearchLayoutVisible = false
    //private var lastY = -1f

    //these are for storing friends and drawing overlays:
    lateinit var friendOverlays: MutableMap<Int, CustomIconOverlay>
    lateinit var geoStoryOverlays: MutableMap<Int, CustomIconOverlay>
    lateinit var fragmentManager: FragmentManager
    private val handler = Handler(Looper.getMainLooper())
    private var lastBackPressedTime: Long = 0
    private val exitHandler = Handler(Looper.getMainLooper())

    companion object {
        private var currentInstance: WeakReference<MapActivity?> = WeakReference(null)

        fun registerInstance(instance: MapActivity) {
            currentInstance = WeakReference(instance)
        }

        fun unregisterInstance(instance: MapActivity) {
            currentInstance.get()?.let {
                if (it == instance) {
                    currentInstance.clear()
                }
            }
        }

        fun finishCurrentInstance() {
            currentInstance.get()?.let {
                val intent = Intent(it, LogInActivity::class.java)
                it.startActivity(intent)
                it.finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coroutineScope = CoroutineScope(Dispatchers.Main)
        registerInstance(this)

        getInstance().load(this, getDefaultSharedPreferences(this))
        binding = MapActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.setOnApplyWindowInsetsListener { _, windowInsets -> //this is for keyboard
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val imeHeight = windowInsets.getInsets(WindowInsets.Type.ime()).bottom
                binding.root.setPadding(0, 0, 0, imeHeight)
            }
            windowInsets
        }
        map = findViewById(R.id.map)

        initObservers()
        viewModel.setGeoStoryActionListener(this)
        friendOverlays = mutableMapOf()
        geoStoryOverlays = mutableMapOf()
        centralizeButtonFrame = findViewById(R.id.center_button_frame)
        profileButtonFrame = findViewById(R.id.profile_button_frame)
        messagesButtonFrame = findViewById(R.id.messages_button_frame)
        unreadMessageIcon = findViewById(R.id.unread_message_icon)
        newRequestIcon = findViewById(R.id.new_request_icon)
        createGeoStoryFrame = findViewById(R.id.create_geo_story_frame)
        searchFrame = findViewById(R.id.search_frame)
        onCLickedOverlays = findViewById(R.id.overlay_on_clicked_menus)
        topButtonsOverlay = findViewById(R.id.top_buttons_overlay)
        bottomButtonsOverlay = findViewById(R.id.bottom_buttons_overlay)
        searchWindow = findViewById(R.id.search_window)
        searchEditText = findViewById(R.id.search_edit_text)
        searchedFriendsListView = findViewById(R.id.friends_list_view)
        searchDarkOverlay = findViewById(R.id.dark_overlay)
        findPeopleNearbyButton = findViewById(R.id.find_people_nearby_button)
        clickedFriendNicknameTextView = findViewById(R.id.nickname_text)
        clickedFriendDistanceTextView = findViewById(R.id.distance_text)
        clickedFriendSpeedTextView = findViewById(R.id.speed_text)
        clickedGeoStoryViewsTextView = findViewById(R.id.views_text)
        clickedGeoStoryTimeTextView = findViewById(R.id.time_text)
        copyrightOSV = findViewById(R.id.copyright_OSV)
        clickedFriendProfileFrame = findViewById(R.id.friend_profile_frame)
        clickedFriendMessageFrame = findViewById(R.id.personal_message_frame)
        clickedGeoStoryViewFrame = findViewById(R.id.geo_story_view_frame)
        clickedIconDistanceAndSpeedLayout = findViewById(R.id.distance_and_speed_layout)
        clickedViewsAndTimeLayout = findViewById(R.id.views_and_time_layout)
        // Initializing and hiding fragments
        fragmentManager = supportFragmentManager
        conversationsFragment = fragmentManager.findFragmentById(R.id.conversations_fragment) as ConversationsFragment
        profileFragment = fragmentManager.findFragmentById(R.id.profile_fragment) as ProfileFragment
        geoStoryCreation =
            fragmentManager.findFragmentById(R.id.geo_story_creation_fragment) as GeoStoryCreationFragment
        geoStoryView = fragmentManager.findFragmentById(R.id.geo_story_view_fragment) as GeoStoryViewFragment
        overlappedIconsAdapter = OverlappedIconsAdapter(this, mutableListOf(), viewModel)
        recyclerView = findViewById(R.id.overlapped_icons_recyclerview)
        recyclerView.adapter = overlappedIconsAdapter
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        copyrightOSV.text =
            HtmlCompat.fromHtml(getText(R.string.openstreetview).toString(), HtmlCompat.FROM_HTML_MODE_LEGACY)
        copyrightOSV.movementMethod = (LinkMovementMethod.getInstance())
        val transaction = fragmentManager.beginTransaction()
        transaction.hide(profileFragment)
        transaction.hide(conversationsFragment)
        transaction.hide(geoStoryView)
        transaction.hide(geoStoryCreation)
        transaction.commit()


        map.setTileSource(TileSourceFactory.MAPNIK)
        mapController = map.controller

        mapController.setZoom(scaleFactor)
        map.setMultiTouchControls(true) // needed for pinch zooms/rotates
        map.isTilesScaledToDpi = true // apparently helps with readability of labels
        map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER) //removes ugly "+" and "-" buttons

        /* Rotation gestures */
        val mRotationGestureOverlay = RotationGestureOverlay(map)
        mRotationGestureOverlay.isEnabled = true
        map.overlays.add(mRotationGestureOverlay)


        val gpsMyLocationProvider = GpsMyLocationProvider(this)
        gpsMyLocationProvider.locationUpdateMinTime = 1000 // 1 sec


        val mLocationOverlay = object : MyLocationNewOverlay(gpsMyLocationProvider, map) {
            override fun onLocationChanged(location: Location?, source: IMyLocationProvider?) {
                super.onLocationChanged(location, source)

                if (location != null) {
                    runOnUiThread {
                        val newLocation = GeoPoint(location)
                        viewModel.customOverlaySelf?.setLocation(newLocation)
                        if (location.hasSpeed()) {
                            viewModel.customOverlaySelf?.setSpeed(location.speed)
                        }
                        if (centeredTargetId == viewModel.getMyId() && isCenteredUser) {
                            viewModel.customOverlaySelf?.let {
                                centralizeMapAnimated(
                                    viewModel.customOverlaySelf!!,
                                    centeredTargetId,
                                    isCenterTargetUser = true,
                                    withZoom = true
                                )
                            }
                        }
                    }
                }
            }
        }
        mLocationOverlay.enableMyLocation()
        mLocationOverlay.runOnFirstFix {
            val myLocation = mLocationOverlay.myLocation
            if (myLocation != null) {
                runOnUiThread {
                    val location = GeoPoint(mLocationOverlay.myLocation)
                    viewModel.createSelfOverlay(location)
                    centralizeMapInstant(location, viewModel.getMyId())
                }
            }
        }

        viewModel.startPassiveUpdates()
        onBackPressedDispatcher.addCallback(this) {
            if (isSearchLayoutVisible) {
                hideSearchLayout()
            } else {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastBackPressedTime < 3000) {
                        moveTaskToBack(true)
                    } else {
                        Toast.makeText(this@MapActivity, getString(R.string.press_back_again), Toast.LENGTH_SHORT)
                            .show()
                        lastBackPressedTime = currentTime
                        exitHandler.postDelayed({ lastBackPressedTime = 0 }, 3000)
                    }
                }
            }
        }
        viewModel.handleIntent(intent)
        centralizeButtonFrame.setOnClickListener {
            viewModel.customOverlaySelf?.let {
                centralizeMapAnimated(
                    viewModel.customOverlaySelf!!,
                    viewModel.getMyId(),
                    isCenterTargetUser = true,
                    withZoom = true
                )
            }
        }
        profileButtonFrame.setOnClickListener {
            viewModel.updateFriendRequestsAndFriendsWS()

            toggleFragmentVisibility(profileFragment)
            profileFragment.nestedScrollView.scrollTo(0, 0)
        }
        messagesButtonFrame.setOnClickListener {
            viewModel.updateConversationsWS()
            toggleFragmentVisibility(conversationsFragment)
            conversationsFragment.nestedScrollView.scrollTo(0, 0)
        }
        createGeoStoryFrame.setOnClickListener {
            toggleFragmentVisibility(geoStoryCreation)
        }
        initializeFriendsList()
        searchFrame.setOnClickListener {
            showSearchLayout()
        }
        searchDarkOverlay.setOnClickListener {
            hideSearchLayout()
        }
        findPeopleNearbyButton.setOnClickListener {
            hideSearchLayout()
            val peopleNearbyFragment = PeopleNearbyFragment.newInstance()
            fragmentManager.beginTransaction()
                .add(R.id.profile_fragment, peopleNearbyFragment)
                .addToBackStack(null)
                .commit()
        }

        map.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                if (!isAnimating and (centeredTargetId != -1)) {
                    setCenteredFalse()
                }
                return true
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                if (!isAnimating and (centeredTargetId != -1)) {
                    setCenteredFalse()
                }
                return true
            }
        })
    }

    fun toggleFragmentVisibility(fragment: Fragment) {
        val transaction = fragmentManager.beginTransaction()
        transaction.setCustomAnimations(R.anim.slide_up, 0, 0, R.anim.slide_down)
        if (fragment.isVisible) {
            fragment.exitTransition = Slide(Gravity.BOTTOM)
            transaction.hide(fragment)
        } else {
            transaction.addToBackStack(null)
            transaction.show(fragment)
        }

        transaction.commit()
        if (fragment is ProfileFragment) {
            fragment.nestedScrollView.viewTreeObserver.addOnGlobalLayoutListener(object :
                ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    fragment.nestedScrollView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    fragment.nestedScrollView.scrollTo(0, 0)
                }
            })
        }
    }

    private fun showSearchLayout() {
        isSearchLayoutVisible = true
        setSoftInputMode(false)
        topButtonsOverlay.visibility = View.GONE
        bottomButtonsOverlay.visibility = View.GONE
        copyrightOSV.visibility = View.GONE
        searchEditText.setText("")
        findPeopleNearbyButton.visibility = if (viewModel.isPrivacyNearbyEnabled()) View.VISIBLE else View.GONE
        searchWindow.visibility = View.VISIBLE
        searchWindow.alpha = 0f
        searchWindow.animate()
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    fun hideSearchLayout() {
        isSearchLayoutVisible = false
        setSoftInputMode(true)
        hideKeyboard()
        searchWindow.visibility = View.GONE
        topButtonsOverlay.visibility = View.VISIBLE
        bottomButtonsOverlay.visibility = View.VISIBLE
        copyrightOSV.visibility = View.VISIBLE
    }

    private fun initializeFriendsList() {
        initializeSearchAdapter()
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()
                searchFriends(query)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun searchFriends(query: String) {
        val filteredFriends =
            viewModel.friendsData.value?.filter { it.userData.nickname.contains(query, ignoreCase = true) }?.take(5)
                ?: listOf()
        updateFriendsListView(filteredFriends)
    }

    private fun initializeSearchAdapter() {
        searchAdapter = SearchedFriendsAdapter(this, mutableListOf(), viewModel)
        searchedFriendsListView.adapter = searchAdapter
        searchedFriendsListView.layoutManager = MyLinearLayoutManager(this)
    }

    private fun updateFriendsListView(friends: List<FriendData>) {
        searchAdapter.updateData(friends)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleIntent(intent)
    }


    /* Пытался сделать зум по свайпу, не вышло
    override fun onTouchEvent(event: MotionEvent): Boolean {
        Log.d("scale_change_", event.toString())
        when (event.actionMasked) {
            MotionEvent.ACTION_UP -> {
                lastY = -1f
                Log.d("scale_change_2", lastY.toString())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.x > map.width * 0.8) {
                    Log.d("scale_change_Factor", scaleFactor.toString())
                    Log.d("scale_change_lastY", lastY.toString())
                    Log.d("scale_change_newY", event.y.toString())

                    if (lastY != -1f) {
                        // Adjust scaleFactor based on the deltaY
                        scaleFactor -= (event.y - lastY) * 0.001
                        scaleFactor = scaleFactor.coerceIn(9.0, 13.0)
                        mapController.setZoom(scaleFactor)
                    }

                    // Update the last Y position
                    lastY = event.y
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }
    */
    fun centralizeMapAnimated(
        overlay: CustomIconOverlay,
        targetId: Int,
        isCenterTargetUser: Boolean,
        withZoom: Boolean,
    ) {
        if (!isAnimating) {
            isAnimating = true
            centeredOverlay = overlay
            val intersectedIds = overlay.getIntersectedIds()
            setCenteredTrue(targetId, isCenterTargetUser, intersectedIds)
            val zoomLevel = if (withZoom) scaleFactor else map.zoomLevelDouble
            mapController.animateTo(overlay.getLocation(), zoomLevel, 500)
            handler.postDelayed({
                if (targetId == centeredTargetId && isCenteredUser == isCenterTargetUser) { //check if still centered on the same target after 0.5 sec
                    setCenteredTrue(targetId, isCenterTargetUser, intersectedIds)
                }
                isAnimating = false
            }, 550)
        }
    }

    private fun centralizeMapInstant(location: GeoPoint, targetId: Int) {
        centeredTargetId = targetId
        isCenteredUser = true
        mapController.setCenter(location)
        mapController.setZoom(scaleFactor)
    }

    private fun setCenteredTrue(
        targetId: Int,
        isCenteredOnUser: Boolean,
        intersectedOverlays: MutableSet<Pair<Int, Int>>
    ) {
        centeredTargetId = targetId
        isCenteredUser = isCenteredOnUser
        if (!isUiHidden) {
            isUiHidden = true
            slideView(profileButtonFrame, hide = true, animateDown = true)
            slideView(messagesButtonFrame, hide = true, animateDown = true)
            slideView(centralizeButtonFrame, hide = true, animateDown = true)
            slideView(createGeoStoryFrame, hide = true, animateDown = false)
            slideView(searchFrame, hide = true, animateDown = false)
            slideView(onCLickedOverlays, hide = false, animateDown = true)
        }
        if (isCenteredOnUser) {
            clickedViewsAndTimeLayout.visibility = View.GONE
            clickedGeoStoryViewFrame.visibility = View.GONE
            if (targetId != viewModel.getMyId()) {
                val clickedFriend = viewModel.friendsData.value?.find { it.userData.userId == targetId }
                if (clickedFriend != null) {
                    clickedIconDistanceAndSpeedLayout.visibility = View.VISIBLE
                    clickedFriendNicknameTextView.text = clickedFriend.userData.nickname
                    if (clickedFriend.location != null) {
                        val speed = (clickedFriend.location!!.speed * 3.6).toInt()
                        clickedFriendSpeedTextView.text = getString(R.string.speed_format, speed)
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            viewModel.myLatitude.toDouble(),
                            viewModel.myLongitude.toDouble(),
                            clickedFriend.location!!.latitude,
                            clickedFriend.location!!.longitude,
                            results
                        )
                        val distanceInKm = String.format("%.1f", results[0] / 1000)
                        clickedFriendDistanceTextView.text = getString(R.string.distance_format_km, distanceInKm)
                    }
                    clickedFriendProfileFrame.visibility = View.VISIBLE
                    clickedFriendProfileFrame.setOnClickListener {
                        val userProfileFragment = UserProfileFragment.newInstance(clickedFriend.userData.userId)
                        fragmentManager.beginTransaction()
                            .add(R.id.profile_fragment, userProfileFragment)
                            .addToBackStack(null)
                            .commit()
                    }
                    clickedFriendMessageFrame.visibility = View.VISIBLE
                    clickedFriendMessageFrame.setOnClickListener {
                        val conversation =
                            viewModel.conversationsData.value?.find { it.userData.userId == clickedFriend.userData.userId }
                        if (conversation == null) return@setOnClickListener
                        val conversationFragment = ConversationFragment.newInstance(
                            conversation.conversationID,
                            conversation.userData.nickname
                        )
                        fragmentManager.beginTransaction()
                            .add(R.id.conversations_fragment, conversationFragment)
                            .addToBackStack(null)
                            .commit()
                    }
                }
            } else {
                clickedFriendMessageFrame.visibility = View.GONE
                clickedFriendProfileFrame.visibility = View.GONE
                clickedIconDistanceAndSpeedLayout.visibility = View.GONE
                clickedFriendNicknameTextView.text = getString(R.string.You)
            }
        } else { //focusing on Geo Story
            val geoStory = viewModel.getGeoStoryData(targetId)
            if (geoStory != null) {
                clickedFriendMessageFrame.visibility = View.GONE
                clickedFriendProfileFrame.visibility = View.GONE
                clickedGeoStoryViewFrame.visibility = View.VISIBLE
                clickedGeoStoryViewFrame.setOnClickListener {
                    viewModel.openGeoStory(geoStory, geoStoryView, this)
                }
                clickedIconDistanceAndSpeedLayout.visibility = View.GONE
                clickedViewsAndTimeLayout.visibility = View.VISIBLE
                clickedFriendNicknameTextView.text =
                    getString(R.string.geo_story_by_format, geoStory.creatorData.nickname)
                clickedGeoStoryViewsTextView.text = geoStory.views.toString()

                clickedGeoStoryTimeTextView.text = viewModel.getTimeAgoString(geoStory.timestamp)
            }
        }
        if (intersectedOverlays != overlappedIconsAdapter.getItemList()) {
            overlappedIconsAdapter.updateData(intersectedOverlays.toList())
        }
    }

    fun setCenteredFalse() {
        centeredOverlay = null
        centeredTargetId = -1
        isCenteredUser = false
        if (isUiHidden) {
            isUiHidden = false
            slideView(profileButtonFrame, hide = false, animateDown = true)
            slideView(messagesButtonFrame, hide = false, animateDown = true)
            slideView(centralizeButtonFrame, hide = false, animateDown = true)
            slideView(searchFrame, hide = false, animateDown = false)
            slideView(createGeoStoryFrame, hide = false, animateDown = false)
            slideView(onCLickedOverlays, hide = true, animateDown = true)
        }
    }

    private fun slideView(view: View, hide: Boolean, animateDown: Boolean) {
        view.visibility = View.VISIBLE
        val parentHeight = 1000f
        val animate = if (!hide) {
            if (animateDown) {
                TranslateAnimation(0f, 0f, parentHeight, 0f)  // Slide in from bottom
            } else {
                TranslateAnimation(0f, 0f, -parentHeight, 0f)  // Slide in from top
            }
        } else {
            if (animateDown) {
                TranslateAnimation(0f, 0f, 0f, parentHeight)  // Slide out to bottom
            } else {
                TranslateAnimation(0f, 0f, 0f, -parentHeight)  // Slide out to top
            }
        }

        animate.duration = 500
        animate.fillAfter = true

        animate.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}

            override fun onAnimationEnd(animation: Animation) {
                view.clearAnimation()
                if (hide) {
                    view.visibility = View.GONE
                }
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })

        view.startAnimation(animate)
    }

    //Функции ниже "нужны" для my location overlays. Хотя вроде и без них работает
    override fun onResume() {
        super.onResume()
        map.onResume() //needed for compass, my location overlays, v6.0.0 and up
        viewModel.startActiveUpdates()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()  //needed for compass, my location overlays, v6.0.0 and up
        viewModel.stopActiveUpdates()
    }

    private fun initObservers() {
        viewModel.tagCounter.observe(this) { tagCounter ->
            profileFragment.myTagCounter.text = tagCounter.toString()
        }
        viewModel.addOverlayEvent.observe(this) { newOverlay ->
            map.overlays.add(newOverlay)
            newOverlay.apply {
                mapView = map
            }
            viewModel.addOverlaysEvent.observe(this) { overlays ->
                overlays.forEach { overlay ->
                    map.overlays.add(overlay)
                    overlay.mapView = map
                }
                Log.d("overlays", "Current overlays: ${map.overlays}")
            }
            viewModel.centerMapAnimatedEvent.observe(this) { quadEvent ->
                centeredOverlay = quadEvent.overlay
                centralizeMapAnimated(
                    quadEvent.overlay,
                    quadEvent.id,
                    quadEvent.isCenterTargetUser,
                    quadEvent.withZoom
                )
            }
            viewModel.removeOverlayEvent.observe(this) { overlay ->
                Log.d("overlays", "Remove overlay: ${overlay.getUserId()}")
                map.overlays.remove(overlay)
            }

            viewModel.friendRequestsData.observe(this) { updatedRequests ->
                profileFragment.friendRequestAdapter.updateData(updatedRequests)
            }

            viewModel.friendsData.observe(this) { updatedFriends ->
                profileFragment.friendAdapter.updateData(updatedFriends)
            }

            viewModel.conversationsData.observe(this) { conversations ->
                conversationsFragment.conversationsAdapter.updateData(conversations)
            }

            viewModel.conversationFragmentEvent.observe(this) { event ->
                event?.let {
                    val conversationFragment = ConversationFragment.newInstance(it.conversationId, it.nickname)
                    fragmentManager.beginTransaction()
                        .add(R.id.conversations_fragment, conversationFragment)
                        .addToBackStack(null)
                        .commit()
                }
            }
            viewModel.friendRequestsFragmentEvent.observe(this) {
                toggleFragmentVisibility(profileFragment)
                profileFragment.nestedScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterInstance(this)
        friendOverlays.clear()
        profileFragment.friendRequestUpdateHandler?.removeCallbacksAndMessages(null)
        profileFragment.friendRequestUpdateHandler = null
        conversationsFragment.conversationUpdateHandler?.removeCallbacksAndMessages(null)
        conversationsFragment.conversationUpdateHandler = null
        coroutineScope.cancel()
    }

    override fun onGeoStoryOpened(geoStoryViewFragment: GeoStoryViewFragment) {
        toggleFragmentVisibility(geoStoryViewFragment)
    }
}

class OverlappedIconsAdapter(
    private val mapActivity: MapActivity,
    private var itemList: MutableList<Pair<Int, Int>>,
    private val viewModel: MapActivityViewModel
) : RecyclerView.Adapter<OverlappedIconsAdapter.OverlappedIconViewHolder>() {
    private var friendData: FriendData? = null
    private var geoStoryData: GeoStoryData? = null

    inner class OverlappedIconViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val pictureImageView: ShapeableImageView = itemView.findViewById(R.id.overlapped_picture)
        val pictureBgImageView: ImageView = itemView.findViewById(R.id.overlapped_picture_background)
        val coroutineScope = CoroutineScope(Dispatchers.Main)
    }

    fun updateData(newItemList: List<Pair<Int, Int>>) {
        val orderedNewItemList = itemList.mapNotNull { oldItem ->
            newItemList.find { newItem -> newItem == oldItem }
        } + newItemList.filter { newItem -> itemList.none { oldItem -> oldItem == newItem } }
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int {
                return itemList.size
            }

            override fun getNewListSize(): Int {
                return orderedNewItemList.size
            }

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return itemList[oldItemPosition] == orderedNewItemList[newItemPosition]
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = itemList[oldItemPosition]
                val newItem = orderedNewItemList[newItemPosition]

                if (oldItem.first != newItem.first || oldItem.second != newItem.second) {
                    return false
                }

                val oldFriendData =
                    if (oldItem.first != 0) viewModel.friendsData.value?.find { it.userData.userId == oldItem.first } else null
                val newFriendData =
                    if (newItem.first != 0) viewModel.friendsData.value?.find { it.userData.userId == newItem.first } else null

                val oldGeoStoryData =
                    if (oldItem.first == 0) viewModel.geoStoriesData.find { it.geoStoryId == oldItem.second } else null
                val newGeoStoryData =
                    if (newItem.first == 0) viewModel.geoStoriesData.find { it.geoStoryId == newItem.second } else null

                return (oldFriendData == newFriendData) && (oldGeoStoryData == newGeoStoryData)
            }
        })

        diffResult.dispatchUpdatesTo(this)
        itemList = orderedNewItemList.map { it.copy() }.toMutableList()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OverlappedIconViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.overlapped_icon_item, parent, false)
        return OverlappedIconViewHolder(view)
    }

    override fun onBindViewHolder(holder: OverlappedIconViewHolder, position: Int) {
        val item = itemList[position]
        val userId = item.first
        val geoStoryId = item.second
        if (userId != 0)
            friendData = viewModel.friendsData.value?.find { it.userData.userId == userId }
        else
            geoStoryData = viewModel.geoStoriesData.find { it.geoStoryId == geoStoryId }
        val shapeAppearanceModelStyle =
            if (userId == 0) R.style.roundImageViewGeoStoryOverlap else R.style.roundImageViewFriendOverlap
        val pictureBgStyle =
            if (userId == 0)
                if (geoStoryData?.viewed == true) {
                    R.drawable.overlapped_geostory_viewed_bg
                } else {
                    R.drawable.overlapped_geostory_new_bg
                }
            else
                R.drawable.overlapped_friend_bg
        holder.pictureBgImageView.setImageDrawable(ContextCompat.getDrawable(mapActivity, pictureBgStyle))
        holder.pictureImageView.shapeAppearanceModel = ShapeAppearanceModel.builder(
            mapActivity,
            0,
            shapeAppearanceModelStyle
        ).build()


        val drawablePlaceholder = ContextCompat.getDrawable(mapActivity, R.drawable.person_placeholder)
        holder.pictureImageView.setImageDrawable(drawablePlaceholder)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (userId == 0) {
                val blurEffect = RenderEffect.createBlurEffect(5f, 5f, Shader.TileMode.CLAMP)
                holder.pictureImageView.setRenderEffect(blurEffect)
            } else {
                holder.pictureImageView.setRenderEffect(null)
            }
        }
        viewModel.viewModelScope.launch {
            val bitmap = viewModel.getPictureData(userId)
            if (bitmap != null) {
                holder.pictureImageView.setImageBitmap(bitmap)
            }
        }

        holder.pictureImageView.setOnClickListener {
            if (item.first == 0) {
                val geoStoryOverlay = mapActivity.geoStoryOverlays[item.second]
                if (geoStoryOverlay != null) {
                    mapActivity.centralizeMapAnimated(
                        geoStoryOverlay,
                        item.second,
                        isCenterTargetUser = false,
                        withZoom = true
                    )
                }
            } else {
                if (item.first != viewModel.getMyId()) {
                    val friendOverlay = mapActivity.friendOverlays[item.first]
                    if (friendOverlay != null) {
                        mapActivity.centralizeMapAnimated(
                            friendOverlay,
                            item.first,
                            isCenterTargetUser = true,
                            withZoom = true
                        )
                    }
                } else {
                    viewModel.customOverlaySelf?.let { it1 ->
                        mapActivity.centralizeMapAnimated(
                            it1,
                            viewModel.getMyId(),
                            isCenterTargetUser = true,
                            withZoom = true
                        )
                    }
                }
            }
        }

    }

    fun getItemList(): MutableList<Pair<Int, Int>> {
        return itemList
    }

    override fun getItemCount(): Int {
        return itemList.size
    }
}