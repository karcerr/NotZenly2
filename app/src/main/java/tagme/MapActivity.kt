package tagme

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.transition.Slide
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.example.tagme.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
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
import kotlin.coroutines.resume


class MapActivity: AppCompatActivity() {
    private lateinit var map : MapView
    private lateinit var mapController: IMapController
    private lateinit var centralizeButton: ImageButton
    private lateinit var profileButton: ImageButton
    private lateinit var messagesButton: ImageButton
    private lateinit var profileFragment: Fragment
    private lateinit var conversationFragment: Fragment
    private var scaleFactor = 15.0
    private var centeredTargetId = -1
    private var isAnimating = false
    private var customOverlaySelf: CustomIconOverlay? = null
    //private var lastY = -1f
    //these are for constant sending location:
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    lateinit var api: API
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    //these are for storing friends and drawing overlays:
    private val friendOverlays: MutableMap<Int, CustomIconOverlay> = mutableMapOf()
    lateinit var fragmentManager : FragmentManager
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        api = API.getInstance(applicationContext)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        getInstance().load(this, getDefaultSharedPreferences(this))
        setContentView(R.layout.map_activity)
        map = findViewById(R.id.map)
        centralizeButton = findViewById(R.id.center_button)
        profileButton = findViewById(R.id.profile_button)
        messagesButton = findViewById(R.id.messages_button)
        // Initializing and hiding fragments
        fragmentManager = supportFragmentManager
        conversationFragment = fragmentManager.findFragmentById(R.id.conversations_fragment)!!
        profileFragment = fragmentManager.findFragmentById(R.id.profile_fragment)!!
        val transaction = fragmentManager.beginTransaction()
        transaction.hide(profileFragment)
        transaction.hide(conversationFragment)
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
                        if (centeredTargetId == api.myUserId) {
                            customOverlaySelf?.let { centralizeMap(it.getLocation(), centeredTargetId) }
                        }
                    }
                }
            }
        }
        mLocationOverlay.enableMyLocation()
        coroutineScope.launch{
            api.getFriendsFromWS()
            api.getMyDataFromWS()
        }
        mLocationOverlay.runOnFirstFix {
            val myLocation = mLocationOverlay.myLocation
            if (myLocation != null) {
                runOnUiThread {

                    val drawable: Drawable = resources.getDrawable(R.drawable.person_placeholder, null)
                    val location = GeoPoint(mLocationOverlay.myLocation)
                    customOverlaySelf = CustomIconOverlay(
                        this,
                        location,
                        0.0f, drawable,
                        "",
                        api.myUserId,
                        R.font.my_font,
                        clickListener = null
                    )
                    map.overlays.add(customOverlaySelf)
                    centralizeMap(customOverlaySelf!!.getLocation(), api.myUserId)
                    }
            }
        }

        centralizeButton.setOnClickListener{
            customOverlaySelf?.let { centralizeMap(it.getLocation(), api.myUserId) }
        }
        profileButton.setOnClickListener {
            coroutineScope.launch {
                api.getFriendRequestsFromWS()
                api.getFriendsFromWS()
                val updatedRequests = api.getFriendRequestData()
                val updatedFriends = api.getFriendsData()
                (profileFragment as ProfileFragment).friendRequestAdapter.updateData(updatedRequests)
                (profileFragment as ProfileFragment).friendAdapter.updateData(updatedFriends)
            }
            toggleFragmentVisibility(profileFragment)
        }
        messagesButton.setOnClickListener {
            coroutineScope.launch {
                api.getConversationsFromWS()
                val updatedConversations = api.getConversationsData()
                (conversationFragment as ConversationsFragment).conversationsAdapter.updateData(updatedConversations)
            }
            toggleFragmentVisibility(conversationFragment)
        }


        map.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                if (!isAnimating) {
                    setCenteredFalse()
                }
                return true
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                if (!isAnimating) {
                    setCenteredFalse()
                }
                return true
            }
        })
    }
    private fun toggleFragmentVisibility(fragment: Fragment) {
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
    private fun centralizeMap(location: GeoPoint, targetId: Int){
        Log.d("Tagme_custom_log", "centralizing on $targetId")
        isAnimating = true
        mapController.animateTo(location, scaleFactor, 500)
        setCenteredTrue(targetId)
        handler.postDelayed({
            isAnimating = false
        }, 600)
    }

    private fun setCenteredTrue(targetId: Int){
        centeredTargetId = targetId
        profileButton.visibility = View.GONE
        messagesButton.visibility = View.GONE
        centralizeButton.visibility = View.GONE
    }
    private fun setCenteredFalse(){
        centeredTargetId = -1
        profileButton.visibility = View.VISIBLE
        messagesButton.visibility = View.VISIBLE
        centralizeButton.visibility = View.VISIBLE
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
                    val latitude = it.latitude.toString()
                    val longitude = it.longitude.toString()
                    val accuracy = it.accuracy.toString()
                    val speed = it.speed.toString()

                    try {
                        api.sendLocationToWS(latitude, longitude, accuracy, speed)
                        api.getLocationsFromWS()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                delay(3000)
                val friendsData = api.getFriendsData()
                updateFriendOverlays(friendsData)
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
                val friendDrawable: Drawable = if (friend.userData.profilePictureId != 0) {
                    val imagePath = api.getPicturesData().find { it.pictureId == friend.userData.profilePictureId }?.imagePath
                    if (imagePath != null) {
                        val bitmap = BitmapFactory.decodeFile(imagePath)
                        BitmapDrawable(resources, bitmap)
                    } else {
                        ResourcesCompat.getDrawable(resources, R.drawable.person_placeholder, null)!!
                    }
                } else {
                    ResourcesCompat.getDrawable(resources, R.drawable.person_placeholder, null)!!
                }

                if (overlay != null) {
                    overlay.setLocation(GeoPoint(friend.location!!.latitude, friend.location!!.longitude))
                    if (centeredTargetId == overlay.getUserId()) {
                        centralizeMap(overlay.getLocation(), friend.userData.userId)
                    }
                } else {
                    val friendLocation = GeoPoint(friend.location!!.latitude, friend.location!!.longitude)
                    val newOverlay = CustomIconOverlay(
                        this,
                        friendLocation,
                        friend.location!!.speed,
                        friendDrawable,
                        friend.userData.nickname,
                        friend.userData.userId,
                        R.font.my_font,
                        clickListener = { customIconOverlay ->
                            centralizeMap(customIconOverlay.getLocation(), friend.userData.userId)
                        }
                    )

                    friendOverlays[friend.userData.userId] = newOverlay
                    map.overlays.add(newOverlay)
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