package com.tagme.presentation.viewmodels

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.tagme.R
import com.tagme.data.API
import com.tagme.data.handlers.ImageHandler
import com.tagme.domain.models.*
import com.tagme.presentation.views.CustomIconOverlay
import com.tagme.presentation.views.fragments.GeoStoryViewFragment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.sql.Timestamp
import javax.inject.Inject
import kotlin.collections.set
import kotlin.coroutines.resume

@HiltViewModel
class MapActivityViewModel @Inject constructor(
    private val application: Application,
    private val api: API,
    private val resources: Resources
) : AndroidViewModel(application) {
    private var isActive = true
    private var activeUpdatesJob: Job? = null

    lateinit var myLatitude: String
    lateinit var myLongitude: String
    private var selfLocationUpdateRunnable: Runnable? = null
    private var selfLocationUpdateHandler: Handler? = null
    private var fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(application)

    private val _tagCounter = MutableLiveData<Int>()
    val tagCounter: LiveData<Int> get() = _tagCounter

    private val _myNickname = MutableLiveData<String>()
    val myNickname: LiveData<String> get() = _myNickname

    var myUserId = -1
    private val _myPlace = MutableLiveData<Int>()
    val myPlace: LiveData<Int> get() = _myPlace


    private val _friendRequestsData = MutableLiveData<List<FriendRequestData>>()
    val friendRequestsData: LiveData<List<FriendRequestData>> get() = _friendRequestsData

    private val _friendsData = MutableLiveData<List<FriendData>>()
    val friendsData: LiveData<List<FriendData>> get() = _friendsData

    private val _conversationsData = MutableLiveData<List<ConversationData>>()
    val conversationsData: LiveData<List<ConversationData>> get() = _conversationsData

    var geoStoriesData: List<GeoStoryData> = listOf()

    var customOverlaySelf: CustomIconOverlay? = null
    val friendOverlays: MutableMap<Int, CustomIconOverlay> = mutableMapOf()
    val geoStoryOverlays: MutableMap<Int, CustomIconOverlay> = mutableMapOf()

    private val _addOverlaysEvent = MutableLiveData<List<CustomIconOverlay>>()
    val addOverlaysEvent: LiveData<List<CustomIconOverlay>> = _addOverlaysEvent

    private val _addOverlayEvent = MutableLiveData<CustomIconOverlay>()
    val addOverlayEvent: LiveData<CustomIconOverlay> = _addOverlayEvent

    private val _removeOverlayEvent = MutableLiveData<CustomIconOverlay>()
    val removeOverlayEvent: LiveData<CustomIconOverlay> get() = _removeOverlayEvent

    private val _centerMapAnimatedEvent = MutableLiveData<CustomIconOverlayEvent>()
    val centerMapAnimatedEvent: LiveData<CustomIconOverlayEvent> get() = _centerMapAnimatedEvent

    private val _conversationFragmentEvent = MutableLiveData<ConversationEvent?>()
    val conversationFragmentEvent: LiveData<ConversationEvent?> get() = _conversationFragmentEvent

    private val _friendRequestsFragmentEvent = MutableLiveData<Unit>()
    val friendRequestsFragmentEvent: LiveData<Unit> get() = _friendRequestsFragmentEvent

    private lateinit var geoStoryActionListener: GeoStoryActionListener
    private var centeredTargetId = -1
    private var isCenteredUser = false

    private val placeholderDrawable = ResourcesCompat.getDrawable(resources, R.drawable.person_placeholder, null)!!
    private val myFont = resources.getFont(R.font.my_font)

    fun startActiveUpdates() {
        activeUpdatesJob = viewModelScope.launch {
            api.getFriendsFromWS()
            _friendsData.postValue(api.getFriendsData())
            getMyDataWS()
            while (isActive) {
                try {
                    api.getLocationsFromWS()
                    val friends = api.getFriendsData()
                    updateFriendOverlays(friends)
                    _friendsData.postValue(friends)

                    api.getGeoStoriesWS()
                    val geoStories = api.getGeoStoriesDataList()
                    updateGeoStoryOverlays(geoStories)
                    geoStoriesData = geoStories

                    updateMyTagsWS()
                } catch (e: Exception) {
                    api.requestMap.clear()
                    Log.d("Tagme_exception", e.toString())
                    e.printStackTrace()
                }
                delay(3000)
            }
        }
    }
    fun updateFriendRequestsAndFriendsWS() {
        viewModelScope.launch {
            api.getFriendRequestsFromWS()
            api.getFriendsFromWS()

            val updatedRequests = api.getFriendRequestDataList()
            val updatedFriends = api.getFriendsData()

            _friendRequestsData.postValue(updatedRequests)
            _friendsData.postValue(updatedFriends)
        }
    }
    fun updateMyTagsWS() {
        viewModelScope.launch {
            api.updateMyDataWS()
            _tagCounter.postValue(api.myTags)
        }
    }
    fun getMyDataWS() {
        viewModelScope.launch {
            api.getMyDataFromWS()
            _tagCounter.postValue(api.myTags)
            _myNickname.postValue(api.myNickname.toString())
            _myPlace.postValue(api.myPlace)
        }
    }

    fun handleIntent(intent: Intent) {
        val startedFromNotification = intent.getBooleanExtra("started_from_notification", false)
        if (startedFromNotification) {
            val conversationId = intent.getIntExtra("conversationId", -1)
            if (conversationId != -1) { //Intent to open a conversation
                val conversation = api.getConversationsDataList().find { it.conversationID == conversationId }
                if (conversation != null) {
                    _conversationFragmentEvent.postValue(
                        ConversationEvent(
                            conversation.conversationID,
                            conversation.userData.nickname
                        )
                    )
                }
                api.clearNotificationsForConversation(conversationId)
            } else { //Intent to open friend requests
                updateFriendRequestsAndFriendsWS()
                val requestId = intent.getIntExtra("requestId", -1)
                if (requestId != -1) {
                    _friendRequestsFragmentEvent.postValue(Unit)
                }
            }
        }
    }
    fun resetMyToken() {
        api.myToken = null
    }
    fun getInternalStorageSize(): String {
        return api.getInternalStorageSize(application)
    }
    fun clearImageInternalStorage() {
        api.clearImageInternalStorage(application)
    }
    fun getFriendRequestsNotificationsEnabled(): Boolean {
        return api.friendRequestsNotificationsEnabled
    }
    fun setFriendRequestsNotificationsEnabled(value: Boolean) {
        api.friendRequestsNotificationsEnabled = value
    }
    fun getMessagesNotificationsEnabled(): Boolean {
        return api.messagesNotificationsEnabled
    }
    fun setMessagesNotificationsEnabled(value: Boolean) {
        api.messagesNotificationsEnabled = value
    }
    fun getPrivacyNearbyEnabled(): Boolean {
        return api.privacyNearbyEnabled
    }
    suspend fun setPrivacyNearbyEnabled(value: Boolean) {
        api.updatePrivacyNearby(value)
    }

    fun updateConversationsWS() {
        viewModelScope.launch {
            api.getConversationsFromWS()
            _conversationsData.postValue(api.getConversationsDataList())
        }
    }

    fun getLeaderBoardData(): List<LeaderBoardData>? {
        return api.getLeaderBoardData()
    }
    fun getConversationsDataList(): List<ConversationData> {
        return api.getConversationsDataList()
    }
    fun getFriendRequestDataList(): List<FriendRequestData> {
        return api.getFriendRequestDataList()
    }
    fun getFriendDataList(): List<FriendData> {
        return api.getFriendsData()
    }
    suspend fun postGeoStory(imageHandler: ImageHandler, privacy: String): JSONObject? {
        if (!imageHandler.isImageCompressed())
            return null
        api.insertPictureIntoWS(imageHandler.getOutputStream())
        if (api.lastInsertedPicId == 0)
            return null
        val latitude = myLatitude
        val longitude = myLongitude
        val result = api.createGeoStory(
            api.lastInsertedPicId,
            privacy,
            latitude,
            longitude
        )
        return result
    }
    suspend fun updatePfpPic(imageHandler: ImageHandler): Boolean {
        api.insertPictureIntoWS(imageHandler.getOutputStream())
        if (api.lastInsertedPicId != 0) {
            val message = api.setProfilePictureWS(api.lastInsertedPicId)?.getString("message")
            if (message == "success") {
                api.myPfpId = api.lastInsertedPicId
                customOverlaySelf!!.updateDrawable(BitmapDrawable(resources, api.getPictureData(api.myPfpId)))
                return true
            }
        }
        return false
    }

    suspend fun sendFriendRequestToWS(nickname: String): JSONObject? {
        return api.sendFriendRequestToWS(nickname)
    }
    suspend fun denyFriendRequest(userId: Int): JSONObject? {
        return api.denyFriendRequest(userId)
    }
    suspend fun cancelFriendRequestWS(userId: Int): JSONObject? {
        return api.cancelFriendRequest(userId)
    }
    suspend fun blockUserWS(userId: Int): JSONObject? {
        return api.blockUserWS(userId)
    }
    suspend fun unblockUserWS(userId: Int): JSONObject? {
        return api.unblockUserWS(userId)
    }
    suspend fun deleteFriendWS(userId: Int): JSONObject? {
        return api.deleteFriendWS(userId)
    }
    suspend fun cancelFriendRequest(userId: Int): JSONObject? {
        return api.cancelFriendRequest(userId)
    }
    suspend fun acceptFriendRequest(userId: Int): JSONObject? {
        return api.acceptFriendRequest(userId)
    }

    suspend fun loadProfileFromWS(userId: Int): JSONObject? {
        return api.loadProfileFromWS(userId)
    }
    suspend fun sendMessageToWS(conversationId: Int, text: String): JSONObject? {
        return api.sendMessageToWS(conversationId, text)
    }
    suspend fun getMessagesFromWS(conversationId: Int): ConversationData? {
        api.getMessagesFromWS(conversationId)
        return api.getConversationData(conversationId)
    }
    suspend fun getConversationsFromWS(): List<ConversationData> {
        api.getConversationsFromWS()
        return api.getConversationsDataList()
    }
    suspend fun readConversationWS(conversationId: Int) {
        api.readConversationWS(conversationId)

    }
    suspend fun deleteConversationWS(conversationId: Int) {
        api.deleteConversationWS(conversationId)
    }
    suspend fun changeNickname(newNickname: String): JSONObject? {
        return api.changeNickname(newNickname)
    }
    suspend fun getLeaderBoardFromWS() {
        api.getLeaderBoardFromWS()
    }
    suspend fun getNearbyPeople() {
        api.getNearbyPeople()
    }
    fun getNearbyPeopleData(): List<UserNearbyData>? {
        return api.getNearbyPeopleData()
    }
    fun togglePinnedStatus(conversationId: Int) {
        api.togglePinnedStatus(conversationId)
    }
    fun toggleMarkedUnreadStatus(conversationId: Int) {
        api.toggleMarkedUnreadStatus(conversationId)
    }
    fun disableMarkedUnreadStatus(conversationId: Int) {
        api.disableMarkedUnreadStatus(conversationId)
    }
    fun clearNotificationsForConversation(conversationId: Int) {
        api.clearNotificationsForConversation(conversationId)
    }

    fun startPassiveUpdates(){
        selfLocationUpdateHandler = Handler(Looper.getMainLooper())
        selfLocationUpdateRunnable?.let { selfLocationUpdateHandler?.postDelayed(it,
            2000L
        ) }
    }
    init {
        myUserId = api.myUserId
        selfLocationUpdateRunnable = Runnable {
            CoroutineScope(Dispatchers.Main).launch {
                val location = getCurrentLocation()

                location?.let {
                    myLatitude = it.latitude.toString()
                    myLongitude = it.longitude.toString()
                    val accuracy = it.accuracy.toString()
                    val speed = it.speed.toString()
                    try {
                        api.sendLocationToWS(myLatitude, myLongitude, accuracy, speed)
                    } catch (e: Exception) {
                        Log.d("Tagme_exception_location_update", e.toString())
                        e.printStackTrace()
                    }
                }
                selfLocationUpdateHandler?.postDelayed(selfLocationUpdateRunnable!!, 2000L)
            }
        }
    }
    fun stopActiveUpdates() {
        isActive = false
        activeUpdatesJob?.cancel()
        activeUpdatesJob = null
    }


    fun createSelfOverlay(location: GeoPoint) {
        customOverlaySelf = CustomIconOverlay(
            resources,
            location,
            0.0f,
            placeholderDrawable,
            "",
            api.myUserId,
            0,
            myFont,
            true,

            clickListener = { selfOverlay ->
                _centerMapAnimatedEvent.postValue(CustomIconOverlayEvent(selfOverlay, api.myUserId, isCenterTargetUser = true, withZoom = false))
            }
        )
        customOverlaySelf?.let {
            _addOverlayEvent.postValue(it)
        }
        viewModelScope.launch(Dispatchers.Main) {
            if (api.myPfpId != 0) {
                val bitmap = api.getPictureData(api.myPfpId)
                if (bitmap != null) {
                    customOverlaySelf!!.updateDrawable(BitmapDrawable(resources, bitmap))
                }
            }
        }
    }
    private fun updateFriendOverlays(friendsData: List<FriendData>) {
        val outdatedIds = friendOverlays.keys - friendsData.map { it.userData.userId }.toSet()
        outdatedIds.forEach { id ->
            friendOverlays.remove(id)?.let { overlay ->
                _removeOverlayEvent.postValue(overlay)
            }
        }

        val overlaysToAdd = mutableListOf<CustomIconOverlay>()
        friendsData.forEach { friend ->
            friend.location?.let { location ->
                val overlay = friendOverlays[friend.userData.userId]
                if (overlay != null) {
                    overlay.setLocation(GeoPoint(location.latitude, location.longitude))
                    overlay.setSpeed(location.speed)
                    if (centeredTargetId == overlay.getUserId() && isCenteredUser) {
                        _centerMapAnimatedEvent.postValue(CustomIconOverlayEvent(overlay, friend.userData.userId, isCenterTargetUser = true, withZoom = false))
                    }
                } else {
                    val friendLocation = GeoPoint(friend.location!!.latitude, friend.location!!.longitude)
                    val newOverlay = CustomIconOverlay(
                        resources,
                        friendLocation,
                        friend.location!!.speed,
                        placeholderDrawable,
                        friend.userData.nickname,
                        friend.userData.userId,
                        0,
                        myFont,
                        isFocusedOn = false,
                        clickListener = { friendOverlay ->
                            _centerMapAnimatedEvent.postValue(CustomIconOverlayEvent(friendOverlay, friend.userData.userId, isCenterTargetUser = true, withZoom = false))
                        }
                    )
                    friendOverlays[friend.userData.userId] = newOverlay
                    overlaysToAdd.add(newOverlay)

                    viewModelScope.launch(Dispatchers.Main) {
                        val bitmap = api.getPictureData(friend.userData.userId)
                        if (bitmap != null) {
                            newOverlay.updateDrawable(
                                BitmapDrawable(resources, bitmap)
                            )
                        }
                    }
                }
            }
        }

        _addOverlaysEvent.postValue(overlaysToAdd)
    }
    private fun updateGeoStoryOverlays(geoStoryData: List<GeoStoryData>) {
        val outdatedIds = geoStoryOverlays.keys - geoStoryData.map { it.geoStoryId }.toSet()
        outdatedIds.forEach { id ->
            geoStoryOverlays.remove(id)?.let { overlay ->
                _removeOverlayEvent.postValue(overlay)
            }
        }

        val overlaysToAdd = mutableListOf<CustomIconOverlay>()
        geoStoryData.forEach { geoStory ->
            val overlay = geoStoryOverlays[geoStory.geoStoryId]
            val geoStoryLocation = GeoPoint(geoStory.latitude, geoStory.longitude)
            if (overlay != null) {
                overlay.setLocation(geoStoryLocation)
            } else {
                val newOverlay = CustomIconOverlay(
                    resources,
                    geoStoryLocation,
                    null,
                    placeholderDrawable,
                    null,
                    0,
                    geoStory.geoStoryId,
                    myFont,
                    false,
                    clickListener = { geoStoryOverlay ->
                        _centerMapAnimatedEvent.postValue(CustomIconOverlayEvent(geoStoryOverlay, geoStory.geoStoryId, isCenterTargetUser = false, withZoom = false))
                    }
                )

                geoStoryOverlays[geoStory.geoStoryId] = newOverlay
                overlaysToAdd.add(newOverlay)

                if (geoStory.pictureId != 0) {
                    viewModelScope.launch(Dispatchers.Main) {
                        val bitmap = api.getPictureData(geoStory.pictureId)
                        if (bitmap != null) {
                            newOverlay.updateDrawable(BitmapDrawable(resources, bitmap))
                        }
                    }
                }
            }
        }
        _addOverlaysEvent.postValue(overlaysToAdd)
    }

    fun getGeoStoryData(geoStoryId: Int): GeoStoryData? {
        return api.getGeoStoriesDataList().find {it.geoStoryId == geoStoryId}
    }
    fun openGeoStory(geoStoryData: GeoStoryData, geoStoryViewFragment: GeoStoryViewFragment, context: Context) {
        viewModelScope.launch {
            api.markGeoStoryViewed(geoStoryData.geoStoryId)

            geoStoryViewFragment.nicknameText.text = geoStoryData.creatorData.nickname
            geoStoryViewFragment.timeAgo.text = getTimeAgoString(geoStoryData.timestamp)
            geoStoryViewFragment.userId = geoStoryData.creatorData.userId
            geoStoryViewFragment.viewCounter.text = geoStoryData.views.toString()
            geoStoryViewFragment.geoStoryId = geoStoryData.geoStoryId

            val bitmapPfp = api.getPictureData(geoStoryData.creatorData.userId)
            if (bitmapPfp != null) {
                geoStoryViewFragment.userPicture.setImageBitmap(bitmapPfp)
            } else {
                geoStoryViewFragment.userPicture.setImageDrawable(ContextCompat.getDrawable(context,
                    R.drawable.person_placeholder
                ))
            }

            val bitmapGeo = api.getPictureData(geoStoryData.pictureId)
            geoStoryViewFragment.geoStoryPicture.setImageBitmap(bitmapGeo)

            api.addViewGeoStory(geoStoryData.geoStoryId)

            geoStoryActionListener.onGeoStoryOpened(geoStoryViewFragment)
        }
    }
    suspend fun getPictureData(userId: Int): Bitmap? {
        return api.getPictureData(userId)
    }
    fun setGeoStoryActionListener(listener: GeoStoryActionListener) {
        geoStoryActionListener = listener
    }
    interface GeoStoryActionListener {
        fun onGeoStoryOpened(geoStoryViewFragment: GeoStoryViewFragment)
    }

    fun getTimeAgoString(timestamp: Timestamp): String {
        val duration = api.calculateDurationFromTimestamp(timestamp)

        return when {
            duration.seconds < 60 -> "${duration.seconds} seconds ago"
            duration.toMinutes() < 60 -> "${duration.toMinutes()} minutes ago"
            else -> "${duration.toHours()} hours ago"
        }
    }
    fun getMyId(): Int {
        return api.myUserId
    }
    fun getMyPfpId(): Int {
        return api.myPfpId
    }
    fun isPrivacyNearbyEnabled(): Boolean {
        return api.privacyNearbyEnabled
    }

    private suspend fun getCurrentLocation(): Location? {
        return withContext(Dispatchers.IO) {
            return@withContext suspendCancellableCoroutine { continuation ->
                if (ActivityCompat.checkSelfPermission(
                        application,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        application,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return@suspendCancellableCoroutine
                }
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
    fun parseAndConvertTimestamp(timeStampString: String): Timestamp {
        return api.parseAndConvertTimestamp(timeStampString)
    }
}