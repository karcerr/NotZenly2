package tagme

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Bundle
import android.transition.Slide
import android.view.Gravity
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
    private lateinit var messagesFragment: Fragment
    private var scaleFactor = 15.0
    private var isCentered = false
    private var customOverlaySelf: CustomIconOverlay? = null
    //private var lastY = -1f
    //these are for constant sending location:
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    lateinit var api: API
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    //these are for storing friends and drawing overlays:
    private val friendOverlays: MutableMap<String, CustomIconOverlay> = mutableMapOf()
    private lateinit var fragmentManager : FragmentManager

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
        // Initialize and hide fragments
        fragmentManager = supportFragmentManager
        messagesFragment = fragmentManager.findFragmentById(R.id.messages_fragment)!!
        profileFragment = fragmentManager.findFragmentById(R.id.profile_fragment)!!
        val transaction = fragmentManager.beginTransaction()
        transaction.hide(profileFragment)
        transaction.hide(messagesFragment)
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


        // Creating a GpsMyLocationProvider instance
        val gpsMyLocationProvider = GpsMyLocationProvider(this)
        // Setting the minimum time interval for location updates (in milliseconds)
        gpsMyLocationProvider.locationUpdateMinTime = 1000 // 1 sec


        /* MyLocation overlay */
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
                        if (isCentered) {
                            centralizeMap(this)
                        }
                    }
                }
            }
        }
        mLocationOverlay.enableMyLocation()

        // Center the map on current location, draw an image
        mLocationOverlay.runOnFirstFix {
            val myLocation = mLocationOverlay.myLocation
            if (myLocation != null) {
                runOnUiThread {
                    centralizeMap(mLocationOverlay)
                    val drawable: Drawable = resources.getDrawable(R.drawable.person_placeholder, null)
                    val location = GeoPoint(mLocationOverlay.myLocation)
                    customOverlaySelf = CustomIconOverlay(this, location, 0.0f, drawable, "Я", R.font.my_font)
                    map.overlays.add(customOverlaySelf)
                    }
            }
        }

        centralizeButton.setOnClickListener{
            centralizeMap(mLocationOverlay)
        }
        profileButton.setOnClickListener {

            coroutineScope.launch {
                api.getFriendRequests()
                val updatedRequests = api.getFriendRequestsData()
                val updatedFriends = api.getFriendLocationsData()
                (profileFragment as ProfileFragment).friendRequestAdapter.updateData(updatedRequests)
                (profileFragment as ProfileFragment).friendAdapter.updateData(updatedFriends)
            }
            toggleFragmentVisibility(profileFragment)
        }
        messagesButton.setOnClickListener {
            toggleFragmentVisibility(messagesFragment)
            //Тут будет загрузка сообщений
        }


        map.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                // Map is being scrolled, set isCentered to false
                isCentered = false
                return true
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                // Map is being zoomed, set isCentered to false
                isCentered = false
                return true
            }
        })
    }
    private fun toggleFragmentVisibility(fragment: Fragment) {
        val transaction = fragmentManager.beginTransaction()
        transaction.setCustomAnimations(R.anim.slide_up, 0, 0, R.anim.slide_down)
        if (fragment.isAdded) {
            if (fragment.isVisible) {
                fragment.exitTransition = Slide(Gravity.BOTTOM)
                transaction.hide(fragment)
            } else {
                transaction.addToBackStack(null)
                transaction.show(fragment)
            }
        } else {
            transaction.add(R.id.map, fragment, "profile_fragment")
            transaction.addToBackStack(null)
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
    private fun centralizeMap(mLocOverlay: MyLocationNewOverlay){
        val myLocation = mLocOverlay.myLocation
        if (myLocation != null) {
            mapController.setCenter(GeoPoint(myLocation))
            mapController.setZoom(scaleFactor)
            map.mapOrientation = 0f
            isCentered = true
        }
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
                val location = getCurrentLocation() // Implement this to get the current location

                location?.let {
                    val latitude = it.latitude.toString()
                    val longitude = it.longitude.toString()
                    val accuracy = it.accuracy.toString()
                    val speed = it.speed.toString()

                    // Send location data to the server
                    try {
                        api.sendLocation(latitude, longitude, accuracy, speed)
                        api.getLocations()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                delay(3000)
                val friendsData = api.getFriendLocationsData()

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
    private fun updateFriendOverlays(friendsData: List<API.FriendLocationsData>) {
        // Remove outdated friend overlays
        val outdatedNicknames = friendOverlays.keys - friendsData.map { it.userData.nickname }
        outdatedNicknames.forEach { nickname ->
            val overlay = friendOverlays.remove(nickname)
            map.overlays.remove(overlay)
        }

        // Update or add friend overlays
        friendsData.forEach { friend ->
            val overlay = friendOverlays[friend.userData.nickname]
            if (overlay != null) {
                // Update existing overlay
                overlay.setLocation(GeoPoint(friend.latitude, friend.longitude))
            } else {
                // Add new overlay
                val friendDrawable: Drawable = resources.getDrawable(R.drawable.person_placeholder, null)
                val friendLocation = GeoPoint(friend.latitude, friend.longitude)
                val newOverlay = CustomIconOverlay(this, friendLocation, friend.speed, friendDrawable, friend.userData.nickname, R.font.my_font)
                friendOverlays[friend.userData.nickname] = newOverlay
                map.overlays.add(newOverlay)
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        // Clear friend overlays and cancel coroutine
        friendOverlays.clear()
        coroutineScope.cancel()
    }
}