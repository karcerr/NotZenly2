package tagme

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.LinkMovementMethod
import android.transition.Slide
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tagme.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.*
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
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume


class MapActivity: AppCompatActivity() {
    private lateinit var map : MapView
    private lateinit var mapController: IMapController
    private lateinit var centralizeButtonFrame: FrameLayout
    private lateinit var profileButtonFrame: FrameLayout
    private lateinit var messagesButtonFrame: FrameLayout
    lateinit var unreadMessageIcon: ImageView
    lateinit var newRequestIcon: ImageView
    private lateinit var clickedFriendProfileFrame: FrameLayout
    private lateinit var clickedFriendMessageFrame: FrameLayout
    private lateinit var clickedGeoStoryViewFrame: FrameLayout
    private lateinit var clickedIconDistanceAndSpeedLayout: LinearLayout
    private lateinit var clickedViewsAndTimeLayout: LinearLayout
    private lateinit var onCLickedOverlays: LinearLayout
    private lateinit var createGeoStoryButton: ImageButton
    private lateinit var profileFragment: ProfileFragment
    private lateinit var conversationFragment: ConversationsFragment
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
    lateinit var myLatitude: String
    lateinit var myLongitute: String
    private var scaleFactor = 15.0
    private var centeredTargetId = -1
    private var isCenteredUser = false
    private var isAnimating = false
    private var isUiHidden = false
    var customOverlaySelf: CustomIconOverlay? = null
    //private var lastY = -1f
    //these are for constant sending location:
    val coroutineScope = CoroutineScope(Dispatchers.Main)
    lateinit var api: API
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    //these are for storing friends and drawing overlays:
    private val friendOverlays: MutableMap<Int, CustomIconOverlay> = mutableMapOf()
    private val geoStoryOverlays: MutableMap<Int, CustomIconOverlay> = mutableMapOf()
    private lateinit var fragmentManager : FragmentManager
    private val handler = Handler(Looper.getMainLooper())
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS][.SS][.S]")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        api = API.getInstance(applicationContext)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        getInstance().load(this, getDefaultSharedPreferences(this))
        setContentView(R.layout.map_activity)
        map = findViewById(R.id.map)
        centralizeButtonFrame = findViewById(R.id.center_button_frame)
        profileButtonFrame = findViewById(R.id.profile_button_frame)
        messagesButtonFrame = findViewById(R.id.messages_button_frame)
        unreadMessageIcon = findViewById(R.id.unread_message_icon)
        newRequestIcon = findViewById(R.id.new_request_icon)
        createGeoStoryButton = findViewById(R.id.create_geo_story_button)
        onCLickedOverlays = findViewById(R.id.overlay_on_clicked_menus)
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
        conversationFragment = fragmentManager.findFragmentById(R.id.conversations_fragment) as ConversationsFragment
        profileFragment = fragmentManager.findFragmentById(R.id.profile_fragment) as ProfileFragment
        geoStoryCreation = fragmentManager.findFragmentById(R.id.geo_story_creation_fragment) as GeoStoryCreationFragment
        geoStoryView = fragmentManager.findFragmentById(R.id.geo_story_view_fragment) as GeoStoryViewFragment
        overlappedIconsAdapter = OverlappedIconsAdapter(this, mutableListOf(), api, fragmentManager, geoStoryView)
        recyclerView = findViewById(R.id.overlapped_icons_recyclerview)
        recyclerView.adapter = overlappedIconsAdapter
        recyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        copyrightOSV.text = HtmlCompat.fromHtml(getText(R.string.openstreetview).toString(), HtmlCompat.FROM_HTML_MODE_LEGACY)
        copyrightOSV.movementMethod = (LinkMovementMethod.getInstance())
        val transaction = fragmentManager.beginTransaction()
        transaction.hide(profileFragment)
        transaction.hide(conversationFragment)
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


        val mLocationOverlay = object:  MyLocationNewOverlay(gpsMyLocationProvider, map) {
            override fun onLocationChanged(location: Location?, source: IMyLocationProvider?) {
                super.onLocationChanged(location, source)

                if (location != null) {
                    runOnUiThread {
                        val newLocation = GeoPoint(location)
                        customOverlaySelf?.setLocation(newLocation)
                        if (location.hasSpeed()) {
                            customOverlaySelf?.setSpeed(location.speed)
                        }
                        if (centeredTargetId == api.myUserId && isCenteredUser) {
                            customOverlaySelf?.let { centralizeMapAnimated(it.getLocation(), centeredTargetId, isCenterTargetUser = true, withZoom = true, mutableListOf()) }
                        }
                    }
                }
            }
        }
        mLocationOverlay.enableMyLocation()
        coroutineScope.launch{
            api.getFriendsFromWS()
        }
        mLocationOverlay.runOnFirstFix {
            val myLocation = mLocationOverlay.myLocation
            if (myLocation != null) {
                runOnUiThread {
                    val selfDrawablePlaceHolder = ResourcesCompat.getDrawable(resources, R.drawable.person_placeholder, null)!!
                    val location = GeoPoint(mLocationOverlay.myLocation)
                    customOverlaySelf = CustomIconOverlay(
                        this,
                        location,
                        0.0f,
                        selfDrawablePlaceHolder,
                        "",
                        api.myUserId,
                        0,
                        R.font.my_font,
                        clickListener = null
                    ).apply {
                        mapView = map
                    }
                    map.overlays.add(customOverlaySelf)
                    centralizeMapInstant(location, api.myUserId)
                    coroutineScope.launch {
                        if (api.myPfpId != 0) {
                            val bitmap = api.getPictureData(api.myPfpId)
                            if (bitmap != null) {
                                customOverlaySelf!!.updateDrawable(BitmapDrawable(resources, bitmap))
                            }
                        }
                    }
                }
            }
        }

        centralizeButtonFrame.setOnClickListener{
            customOverlaySelf?.let { centralizeMapAnimated(it.getLocation(), api.myUserId, isCenterTargetUser = true, withZoom = true, mutableListOf()) }
        }
        profileButtonFrame.setOnClickListener {
            coroutineScope.launch {
                api.getFriendRequestsFromWS()
                api.getFriendsFromWS()
                val updatedRequests = api.getFriendRequestData()
                val updatedFriends = api.getFriendsData()
                profileFragment.friendRequestAdapter.updateData(updatedRequests)
                profileFragment.friendAdapter.updateData(updatedFriends)
            }

            toggleFragmentVisibility(profileFragment)
            profileFragment.nestedScrollView.scrollTo(0, 0)
        }
        messagesButtonFrame.setOnClickListener {
            coroutineScope.launch {
                api.getConversationsFromWS()
                val updatedConversations = api.getConversationsData()
                conversationFragment.conversationsAdapter.updateData(updatedConversations)
            }
            toggleFragmentVisibility(conversationFragment)
        }
        createGeoStoryButton.setOnClickListener {
            toggleFragmentVisibility(geoStoryCreation)
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
            fragment.nestedScrollView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    fragment.nestedScrollView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    fragment.nestedScrollView.scrollTo(0, 0)
                }
            })
        }
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
        location: GeoPoint,
        targetId: Int,
        isCenterTargetUser: Boolean,
        withZoom: Boolean,
        intersectedOverlays: MutableList<Pair<Int, Int>>
    ){
        Log.d("Tagme", "centralizing on $targetId")
        if (!isAnimating) {
            isAnimating = true
            setCenteredTrue(targetId, isCenterTargetUser, intersectedOverlays)
            val zoomLevel = if(withZoom) scaleFactor else map.zoomLevelDouble
            mapController.animateTo(location, zoomLevel, 500)
            handler.postDelayed({
                isAnimating = false
            }, 550)
        }
    }

    private fun centralizeMapInstant(location: GeoPoint, targetId: Int){
        centeredTargetId = targetId
        isCenteredUser = true
        mapController.setCenter(location)
        mapController.setZoom(scaleFactor)
    }

    private fun setCenteredTrue(targetId: Int, isCenteredOnUser: Boolean, intersectedOverlays: MutableList<Pair<Int, Int>>) {
        centeredTargetId = targetId
        isCenteredUser = isCenteredOnUser
        if (!isUiHidden) {
            isUiHidden = true
            slideView(profileButtonFrame, true)
            slideView(messagesButtonFrame, true)
            slideView(centralizeButtonFrame, true)
            onCLickedOverlays.visibility = View.VISIBLE
            slideView(onCLickedOverlays, false)
        }
        if (isCenteredOnUser) {
            clickedViewsAndTimeLayout.visibility = View.GONE
            clickedGeoStoryViewFrame.visibility = View.GONE
            if (targetId != api.myUserId) {
                val clickedFriend = api.getFriendsData().find{it.userData.userId == targetId}
                if (clickedFriend != null) {
                    clickedIconDistanceAndSpeedLayout.visibility = View.VISIBLE
                    clickedFriendNicknameTextView.text = clickedFriend.userData.nickname
                    if (clickedFriend.location != null) {
                        val speed =( clickedFriend.location!!.speed * 3.6).toInt()
                        clickedFriendSpeedTextView.text = getString(R.string.speed_format, speed)
                        val results = FloatArray(1)
                        Location.distanceBetween(
                            myLatitude.toDouble(),
                            myLongitute.toDouble(),
                            clickedFriend.location!!.latitude,
                            clickedFriend.location!!.longitude,
                            results
                        )
                        val distanceInKm = String.format("%.1f", results[0] / 1000)
                        clickedFriendDistanceTextView.text = getString(R.string.distance_format, distanceInKm)
                    }
                    clickedFriendProfileFrame.visibility = View.VISIBLE
                    clickedFriendProfileFrame.setOnClickListener {
                        val userProfileDialog = UserProfileDialogFragment.newInstance(clickedFriend.userData.userId)
                        userProfileDialog.show(fragmentManager, "userProfileDialog")
                    }
                    clickedFriendMessageFrame.visibility = View.VISIBLE
                    clickedFriendMessageFrame.setOnClickListener {
                        val conversation = api.getConversationsData().find {it.userData.userId == clickedFriend.userData.userId}
                        if (conversation == null) return@setOnClickListener
                        val conversationFragment = ConversationFragment.newInstance(conversation.conversationID, conversation.userData.nickname)
                        fragmentManager.beginTransaction()
                            .replace(R.id.conversations_fragment, conversationFragment)
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
            val geoStory = api.getGeoStoriesData().find {it.geoStoryId == targetId}
            if (geoStory != null) {

                clickedFriendMessageFrame.visibility = View.GONE
                clickedFriendProfileFrame.visibility = View.GONE
                clickedGeoStoryViewFrame.visibility = View.VISIBLE
                clickedGeoStoryViewFrame.setOnClickListener {
                    openGeoStory(geoStory, geoStoryView, this)
                }
                clickedIconDistanceAndSpeedLayout.visibility = View.GONE
                clickedViewsAndTimeLayout.visibility = View.VISIBLE
                clickedFriendNicknameTextView.text = getString(R.string.geo_story_by_format, geoStory.creatorData.nickname)
                clickedGeoStoryViewsTextView.text = geoStory.views.toString()
                val timestampDateTime = LocalDateTime.parse(geoStory.timestamp.toString(), dateFormatter)
                val now = LocalDateTime.now()
                val duration = Duration.between(timestampDateTime, now)
                val timestampText = when {
                    duration.seconds < 60 -> getString(R.string.seconds_ago_format, duration.seconds)
                    duration.toMinutes() < 60 -> getString(R.string.minutes_ago_format, duration.toMinutes())
                    else -> getString(R.string.hours_ago_format, duration.toHours())
                }

                clickedGeoStoryTimeTextView.text = timestampText
            }
        }
        if (intersectedOverlays != overlappedIconsAdapter.getItemList()) {
            overlappedIconsAdapter.updateData(intersectedOverlays)
        }
    }

    private fun setCenteredFalse() {
        centeredTargetId = -1
        isCenteredUser = false
        if (isUiHidden) {
            isUiHidden = false
            slideView(profileButtonFrame, false)
            slideView(messagesButtonFrame, false)
            slideView(centralizeButtonFrame, false)
            onCLickedOverlays.visibility = View.VISIBLE
            slideView(onCLickedOverlays, true)
        }
    }

    private fun slideView(view: View, hide: Boolean) {
        val parentHeight = (view.parent as View).height.toFloat()
        val animate: Animation = if (!hide) {
            TranslateAnimation(
                0f,
                0f,
                parentHeight,
                0f
            )
        } else {
            TranslateAnimation(
                0f,
                0f,
                0f,
                parentHeight
            )
        }
        animate.duration = 500
        animate.fillAfter = true
        if (!hide) {
            view.visibility = View.VISIBLE
            view.isClickable = true
        }
        view.startAnimation(animate)
        handler.postDelayed({
            if (hide) {
                view.visibility = View.INVISIBLE
                view.isClickable = false
            }
        }, animate.duration)
    }


    //Функции ниже "нужны" для my location overlays. Хотя вроде и без них работает
     override fun onResume() {
         super.onResume()
         map.onResume() //needed for compass, my location overlays, v6.0.0 and up
         startSendingLocationUpdates()
     }

     override fun onPause() {
         super.onPause()
         map.onPause()  //needed for compass, my location overlays, v6.0.0 and up
         stopSendingLocationUpdates()
     }
    private fun startSendingLocationUpdates() {
        coroutineScope.launch {
            while (isActive) {
                val location = getCurrentLocation()

                location?.let {
                    myLatitude = it.latitude.toString()
                    myLongitute = it.longitude.toString()
                    val accuracy = it.accuracy.toString()
                    val speed = it.speed.toString()
                    try {
                        api.sendLocationToWS(myLatitude, myLongitute, accuracy, speed)
                        api.getLocationsFromWS()
                        api.getGeoStories()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                delay(3000)
                val friendsData = api.getFriendsData()
                updateFriendOverlays(friendsData)
                val updatedGeoStories = api.getGeoStoriesData()
                updateGeoStoryOverlays(updatedGeoStories)
            }
        }
    }

    private fun stopSendingLocationUpdates() {
        coroutineScope.coroutineContext.cancelChildren()
    }

    private suspend fun getCurrentLocation(): Location? {
        return withContext(Dispatchers.IO) {
            if (ActivityCompat.checkSelfPermission(
                    this@MapActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this@MapActivity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return@withContext null
            }

            return@withContext suspendCancellableCoroutine { continuation ->
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location ->
                        continuation.resume(location)
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
            }
        }
    }
    private fun updateFriendOverlays(friendsData: List<API.FriendData>) {
        val outdatedIds = friendOverlays.keys - friendsData.map { it.userData.userId }.toSet()
        outdatedIds.forEach { id ->
            val overlay = friendOverlays.remove(id)
            map.overlays.remove(overlay)
        }

        friendsData.forEach { friend ->
            if (friend.location != null) {
                val overlay = friendOverlays[friend.userData.userId]
                val placeholderDrawable = ResourcesCompat.getDrawable(resources, R.drawable.person_placeholder, null)!!

                if (overlay != null) {
                    overlay.setLocation(GeoPoint(friend.location!!.latitude, friend.location!!.longitude))
                    overlay.setSpeed(friend.location!!.speed)
                    if (centeredTargetId == overlay.getUserId() && isCenteredUser) {
                        centralizeMapAnimated(overlay.getLocation(), friend.userData.userId, isCenterTargetUser = true, withZoom = false, overlay.getIntersectedIds())
                    }
                } else {
                    val friendLocation = GeoPoint(friend.location!!.latitude, friend.location!!.longitude)
                    val newOverlay = CustomIconOverlay(
                        this,
                        friendLocation,
                        friend.location!!.speed,
                        placeholderDrawable,
                        friend.userData.nickname,
                        friend.userData.userId,
                        0,
                        R.font.my_font,
                        clickListener = { customIconOverlay ->
                            centralizeMapAnimated(customIconOverlay.getLocation(), friend.userData.userId, isCenterTargetUser = true, withZoom = false, customIconOverlay.getIntersectedIds())
                        }
                    ).apply {
                        mapView = map
                    }

                    friendOverlays[friend.userData.userId] = newOverlay
                    map.overlays.add(newOverlay)
                    if (friend.userData.profilePictureId != 0) {
                        coroutineScope.launch {
                            val bitmap = api.getPictureData(friend.userData.profilePictureId)
                            if (bitmap != null) {
                                friendOverlays[friend.userData.userId]!!.updateDrawable(BitmapDrawable(resources, bitmap))
                            }
                        }
                    }
                }
            }
        }
    }
    private fun updateGeoStoryOverlays(geoStoryData: List<API.GeoStoryData>) {
        val outdatedIds = geoStoryOverlays.keys - geoStoryData.map { it.geoStoryId }.toSet()
        outdatedIds.forEach { id ->
            val overlay = geoStoryOverlays.remove(id)
            map.overlays.remove(overlay)
        }

        geoStoryData.forEach { geoStory ->
            val overlay = geoStoryOverlays[geoStory.geoStoryId]
            val placeholderDrawable = ResourcesCompat.getDrawable(resources, R.drawable.person_placeholder, null)!!
            val geoStoryLocation = GeoPoint(geoStory.latitude, geoStory.longitude)
            if (overlay != null) {
                overlay.setLocation(geoStoryLocation)
            } else {
                val newOverlay = CustomIconOverlay(
                    this,
                    geoStoryLocation,
                    null,
                    placeholderDrawable,
                    null,
                    0,
                    geoStory.geoStoryId,
                    R.font.my_font,
                    clickListener = { customIconOverlay ->
                        centralizeMapAnimated(customIconOverlay.getLocation(), geoStory.geoStoryId, isCenterTargetUser = false, withZoom = false, customIconOverlay.getIntersectedIds())
                    }
                ).apply {
                    mapView = map
                }

                geoStoryOverlays[geoStory.geoStoryId] = newOverlay
                map.overlays.add(newOverlay)
                if (geoStory.pictureId != 0) {
                    coroutineScope.launch {
                        val bitmap = api.getPictureData(geoStory.pictureId)
                        if (bitmap != null) {
                            geoStoryOverlays[geoStory.geoStoryId]!!.updateDrawable(BitmapDrawable(resources, bitmap))
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        friendOverlays.clear()
        coroutineScope.cancel()
    }
}

class OverlappedIconsAdapter(
    private val context: Context,
    private var itemList: MutableList<Pair<Int, Int>>,
    private val api: API,
    private val fragmentManager: FragmentManager,
    private val geoStoryViewFragment: GeoStoryViewFragment,
) : RecyclerView.Adapter<OverlappedIconsAdapter.OverlappedIconViewHolder>() {
    private var friendData: API.FriendData? = null
    private var geoStoryData: API.GeoStoryData? = null
    inner class OverlappedIconViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val pictureImageView: ShapeableImageView = itemView.findViewById(R.id.overlapped_picture)
        val pictureBgImageView: ImageView = itemView.findViewById(R.id.overlapped_picture_background)
        val coroutineScope = CoroutineScope(Dispatchers.Main)
    }
    fun updateData(newItemList: List<Pair<Int, Int>>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int {
                return itemList.size
            }

            override fun getNewListSize(): Int {
                return newItemList.size
            }

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return itemList[oldItemPosition] == newItemList[newItemPosition]
            }

            override fun areContentsTheSame(p0: Int, p1: Int): Boolean {
                return true
            }
        })
        diffResult.dispatchUpdatesTo(this)
        itemList = newItemList.map {it.copy()}.toMutableList()
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
            friendData = api.getFriendsData().find { it.userData.userId == userId }
        else
            geoStoryData = api.getGeoStoriesData().find {it.geoStoryId == geoStoryId}
        val picId = if(userId == 0) geoStoryData?.pictureId else
            friendData?.userData?.profilePictureId
        val shapeAppearanceModelStyle = if(userId == 0) R.style.roundImageViewGeoStoryOverlap else R.style.roundImageViewFriendOverlap
        val pictureBgStyle =
            if(userId == 0)
                if (geoStoryData?.viewed == true) {
                    Log.d("Tagme_geo", "viewed: $geoStoryData")
                    R.drawable.overlapped_geostory_viewed_bg
                }
                else {
                    Log.d("Tagme_geo", "new: $geoStoryData")
                    R.drawable.overlapped_geostory_new_bg
                }
            else
                R.drawable.overlapped_friend_bg
        holder.pictureBgImageView.setImageDrawable(ContextCompat.getDrawable(context, pictureBgStyle))
        holder.pictureImageView.shapeAppearanceModel = ShapeAppearanceModel.builder(
            context,
            0,
            shapeAppearanceModelStyle
        ).build()


        val drawablePlaceholder = ContextCompat.getDrawable(context, R.drawable.person_placeholder)
        holder.pictureImageView.setImageDrawable(drawablePlaceholder)
        if (picId != null) {
            if (userId == 0) {
                val blurEffect = RenderEffect.createBlurEffect(5f, 5f, Shader.TileMode.CLAMP)
                holder.pictureImageView.setRenderEffect(blurEffect)
            } else {
                holder.pictureImageView.setRenderEffect(null)
            }
            holder.coroutineScope.launch {
                val bitmap = api.getPictureData(picId)
                if (bitmap != null) {
                    holder.pictureImageView.setImageBitmap(bitmap)
                }
            }
        }
        holder.pictureImageView.setOnClickListener {
            if (item.first == 0) {
                //show geo story
                val geoStory = api.getGeoStoriesData().find {it.geoStoryId == item.second}
                if (geoStory != null) {
                    openGeoStory(geoStory, geoStoryViewFragment, (context as MapActivity))
                    holder.pictureBgImageView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.overlapped_geostory_viewed_bg))
                }
            } else {
                //show user profile
                if (item.first != api.myUserId) {
                    val userProfileDialog = UserProfileDialogFragment.newInstance(item.first)
                    userProfileDialog.show(fragmentManager, "userProfileDialog")
                }
            }
            Log.d("Tagme_overlapped", "$itemList, $item")
        }

    }
    fun getItemList(): MutableList<Pair<Int, Int>>{
        return itemList
    }
    override fun getItemCount(): Int {
        return itemList.size
    }
}
fun openGeoStory(geoStoryData: API.GeoStoryData, geoStoryViewFragment: GeoStoryViewFragment, context: MapActivity){
    geoStoryData.viewed = true
    geoStoryViewFragment.nicknameText.text = geoStoryData.creatorData.nickname
    geoStoryViewFragment.userId = geoStoryData.creatorData.userId
    geoStoryViewFragment.viewCounter.text = geoStoryData.views.toString()
    geoStoryViewFragment.geoStoryId = geoStoryData.geoStoryId
    context.coroutineScope.launch {
        val bitmapPfp = context.api.getPictureData(geoStoryData.creatorData.profilePictureId)
        if (bitmapPfp != null) {
            geoStoryViewFragment.userPicture.setImageBitmap(bitmapPfp)
        }
        val bitmapGeo = context.api.getPictureData(geoStoryData.pictureId)
        if (bitmapGeo != null) {
            geoStoryViewFragment.geoStoryPicture.setImageBitmap(bitmapGeo)
        }
        context.toggleFragmentVisibility(geoStoryViewFragment)
    }
}