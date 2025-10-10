package com.tagme.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tagme.R
import com.tagme.data.db.AppDatabase
import com.tagme.domain.models.*
import com.tagme.domain.models.db.FriendEntity
import com.tagme.domain.models.db.ImageEntity
import com.tagme.domain.models.db.toFriendData
import com.tagme.domain.models.db.toPictureData
import com.tagme.presentation.services.NotificationManager
import com.tagme.presentation.views.activities.MapActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class API @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager
) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("API_PREFS", Context.MODE_PRIVATE)
    private val isFakeMode = true
    private val gson = Gson()
    private val db = AppDatabase.getDatabase(context)
    val friendDao = db.friendDao()
    val imageDao = db.friendImageDao()
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var lastInsertedPictureDataString = ""
    private val requestIdCounter = AtomicInteger(0)
    val requestMap: MutableMap<Int, Pair<CompletableFuture<JSONObject?>, String>> =
        Collections.synchronizedMap(mutableMapOf<Int, Pair<CompletableFuture<JSONObject?>, String>>())
    private var leaderBoardList: List<LeaderBoardData>? = null
    private var peopleNearbyList: List<UserNearbyData>? = null
    private val pictureMutex = Mutex()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS][.SS][.S]")

    init {
        notificationManager.createNotificationChannels()
    }

    var lastInsertedPicId = 0

    var myToken: String?
        get() = sharedPreferences.getString("TOKEN", null)
        set(value) {
            sharedPreferences.edit().putString("TOKEN", value).apply()
        }
    var friendRequestsNotificationsEnabled: Boolean
        get() = sharedPreferences.getBoolean("FRIEND_REQUEST_NOTIFICATIONS", true)
        set(value) {
            sharedPreferences.edit().putBoolean("FRIEND_REQUEST_NOTIFICATIONS", value).apply()
        }
    var messagesNotificationsEnabled: Boolean
        get() = sharedPreferences.getBoolean("MESSAGES_NOTIFICATIONS", true)
        set(value) {
            sharedPreferences.edit().putBoolean("MESSAGES_NOTIFICATIONS", value).apply()
        }
    var privacyNearbyEnabled: Boolean
        get() = sharedPreferences.getBoolean("PRIVACY_NEARBY", true)
        set(value) {
            sharedPreferences.edit().putBoolean("PRIVACY_NEARBY", value).apply()
        }

    var myUserId: Int
        get() = sharedPreferences.getInt("UserID", 0)
        set(value) {
            sharedPreferences.edit().putInt("UserID", value).apply()
        }
    var myTags: Int
        get() = sharedPreferences.getInt("TAGS", 0)
        set(value) {
            sharedPreferences.edit().putInt("TAGS", value).apply()
        }
    var myPfpId: Int
        get() = sharedPreferences.getInt("PfpId", 0)
        set(value) {
            sharedPreferences.edit().putInt("PfpId", value).apply()
        }
    var myPlace: Int
        get() = sharedPreferences.getInt("place", 999)
        set(value) {
            sharedPreferences.edit().putInt("place", value).apply()
        }
    var myNickname: String?
        get() = sharedPreferences.getString("NICKNAME", null)
        set(value) {
            sharedPreferences.edit().putString("NICKNAME", value).apply()
        }
    private var friendRequestsData: List<FriendRequestData>
        get() {
            val jsonString = sharedPreferences.getString("FRIEND_REQUESTS_DATA", null)
            return if (jsonString != null) {
                deserializeFriendRequestsData(jsonString)
            } else {
                mutableListOf()
            }
        }
        set(value) {
            val jsonString = serializeFriendRequestsData(value)
            sharedPreferences.edit().putString("FRIEND_REQUESTS_DATA", jsonString).apply()
        }
    private var conversationsData: List<ConversationData>
        get() {
            val jsonString = sharedPreferences.getString("CONVERSATIONS_DATA", null)
            return if (jsonString != null) {
                deserializeConversationsData(jsonString)
            } else {
                mutableListOf()
            }
        }
        set(value) {
            val jsonString = serializeConversationsData(value)
            sharedPreferences.edit().putString("CONVERSATIONS_DATA", jsonString).apply()
        }
    private var geoStoriesData: List<GeoStoryData>
        get() {
            val jsonString = sharedPreferences.getString("GEOSTORIES_DATA", null)
            return if (jsonString != null) {
                deserializeGeoStoriesData(jsonString)
            } else {
                mutableListOf()
            }
        }
        set(value) {
            val jsonString = serializeGeoStoriesData(value)
            sharedPreferences.edit().putString("GEOSTORIES_DATA", jsonString).apply()
        }

    suspend fun connectToServer(): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        val url = "ws://141.8.193.201:8765"

        if (isFakeMode) {
            future.complete(true)
            return future
        }

        val request = Request.Builder()
            .url(url)
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                this@API.webSocket = webSocket
                Log.d("Tagme_WS", "onOpen trigger: $response")
                future.complete(true)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.d("Tagme_WS", "onFailure trigger: $response")
                finishMapActivity()
                future.complete(false)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("Tagme_WS", "onMessage trigger: $text")
                val answer = JSONObject(text)
                val requestId = answer.getInt("request_id")
                if (answer.getString("message") == "invalid token") {
                    finishMapActivity()
                    future.complete(false)
                }
                when (answer.getString("action")) {
                    "login", "register", "auth vk" -> when (answer.getString("status")) {
                        "success" -> {
                            conversationsData = listOf()
                            myToken = answer.getString("message")
                        }
                    }

                    "get locations" -> when (answer.getString("status")) {
                        "success" -> parseFriendLocationsData(answer.getString("message"))
                    }

                    "get friend requests" -> when (answer.getString("status")) {
                        "success" -> parseFriendRequestData(answer.getString("message"))
                    }

                    "get friends" -> when (answer.getString("status")) {
                        "success" -> parseFriendsData(answer.getString("message"))
                    }

                    "get conversations" -> when (answer.getString("status")) {
                        "success" -> parseConversationsData(answer.getString("message"))
                    }

                    "get picture" -> when (answer.getString("status")) {
                        "success" -> parsePictureData(context, answer.getString("message"))
                    }

                    "get messages" -> when (answer.getString("status")) {
                        "success" -> parseMessagesData(answer.getString("message"))
                    }

                    "get my data" -> when (answer.getString("status")) {
                        "success" -> parseMyData(JSONObject(answer.getString("message")))
                    }

                    "update my data" -> when (answer.getString("status")) {
                        "success" -> parseMyUpdatedData(JSONObject(answer.getString("message")))
                    }

                    "insert picture" -> when (answer.getString("status")) {
                        "success" -> parseInsertedPictureId(context, answer.getString("message"))
                    }

                    "get geo stories" -> when (answer.getString("status")) {
                        "success" -> parseGeoStoriesNearby(answer.getString("message"))
                    }

                    "get leaderboard" -> when (answer.getString("status")) {
                        "success" -> parseLeaderBoard(answer.getString("message"))
                    }

                    "get nearby people" -> when (answer.getString("status")) {
                        "success" -> parseNearbyPeople(answer.getString("message"))
                    }

                    "update privacy nearby" -> when (answer.getString("status")) {
                        "success" -> privacyNearbyEnabled = !privacyNearbyEnabled
                    }
                }
                val futureAnswer = requestMap[requestId]?.first
                futureAnswer?.complete(answer)
            }
        }
        client.newWebSocket(request, listener)
        return future
    }

    private fun finishMapActivity() {
        MapActivity.finishCurrentInstance()
    }

    suspend fun registerUser(username: String, password: String): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "register")
            put("username", username)
            put("password", password)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun authVK(accessToken: String): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "auth vk")
            put("access_token", accessToken)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun loginUser(username: String, password: String): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "login")
            put("username", username)
            put("password", password)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun changeNickname(newNickname: String): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "change nickname")
            put("token", myToken)
            put("nickname", newNickname)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun loginToken(): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "validate token")
            put("token", myToken)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun sendLocationToWS(latitude: String, longitude: String, accuracy: String, speed: String): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "send location")
            put("token", myToken)
            put("latitude", latitude)
            put("longitude", longitude)
            put("accuracy", accuracy)
            put("speed", speed)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun getLocationsFromWS(): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "get locations")
            put("token", myToken)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun getFriendsFromWS(): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "get friends")
            put("token", myToken)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun getLeaderBoardFromWS(): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "get leaderboard")
            put("token", myToken)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun getNearbyPeople(): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "get nearby people")
            put("token", myToken)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun updatePrivacyNearby(enabled: Boolean): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "update privacy nearby")
            put("token", myToken)
            put("enable", enabled)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun getConversationsFromWS(): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "get conversations")
            put("token", myToken)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun sendFriendRequestToWS(username: String): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "add friend")
            put("token", myToken)
            put("nickname", username)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun blockUserWS(userId: Int): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "block user")
            put("token", myToken)
            put("user2_id", userId)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun deleteFriendWS(userId: Int): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "delete friend")
            put("token", myToken)
            put("user2_id", userId)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun unblockUserWS(userId: Int): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "unblock user")
            put("token", myToken)
            put("user2_id", userId)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun getFriendRequestsFromWS(): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "get friend requests")
            put("token", myToken)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun loadProfileFromWS(userId: Int): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "load profile")
            put("token", myToken)
            put("user_id", userId)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun acceptFriendRequest(id: Int): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "accept request")
            put("token", myToken)
            put("user2_id", id)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun denyFriendRequest(id: Int): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "deny request")
            put("token", myToken)
            put("user2_id", id)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun cancelFriendRequest(id: Int): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "cancel request")
            put("token", myToken)
            put("user2_id", id)
        }
        return sendRequestToWS(requestData)
    }

    private suspend fun getPictureFromWS(id: Int): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "get picture")
            put("token", myToken)
            put("picture_id", id)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun getMyDataFromWS(): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "get my data")
            put("token", myToken)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun updateMyDataWS(): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "update my data")
            put("token", myToken)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun setProfilePictureWS(picId: Int): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "set profile picture")
            put("token", myToken)
            put("picture_id", picId)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun getMessagesFromWS(conversationId: Int): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "get messages")
            put("token", myToken)
            put("conversation_id", conversationId)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun readConversationWS(conversationId: Int): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "read conversation")
            put("token", myToken)
            put("conversation_id", conversationId)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun deleteMessageWS(messageId: Int): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "delete message")
            put("token", myToken)
            put("msg_id", messageId)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun deleteConversationWS(conversationId: Int): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "delete conversation")
            put("token", myToken)
            put("conversation_id", conversationId)
        }
        return sendRequestToWS(requestData)
    }


    suspend fun sendMessageToWS(conversationId: Int, text: String): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "send message")
            put("token", myToken)
            put("conversation_id", conversationId)
            put("text", text)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun insertPictureIntoWS(picture: ByteArrayOutputStream): JSONObject? {
        lastInsertedPicId = 0
        val base64ImageData = Base64.getEncoder().encodeToString(picture.toByteArray())
        lastInsertedPictureDataString = base64ImageData
        val requestData = JSONObject().apply {
            put("action", "insert picture")
            put("token", myToken)
            put("picture", base64ImageData)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun createGeoStory(picId: Int, privacy: String, latitude: String, longitude: String): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "create geo story")
            put("token", myToken)
            put("picture_id", picId)
            put("privacy", privacy)
            put("latitude", latitude)
            put("longitude", longitude)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun getGeoStoriesWS(): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "get geo stories")
            put("token", myToken)
        }
        return sendRequestToWS(requestData)
    }

    suspend fun addViewGeoStory(geoStoryId: Int): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "add view to geo story")
            put("token", myToken)
            put("geostory_id", geoStoryId)
        }
        return sendRequestToWS(requestData)
    }

    private fun parseFriendsData(jsonString: String) {
        coroutineScope.launch {
            val result = JSONObject(jsonString).getJSONArray("result")
            val encounteredUserIds = mutableListOf<Int>()
            for (i in 0 until result.length()) {
                val friendObject = result.getJSONObject(i)
                val id = friendObject.getInt("user_id")
                encounteredUserIds.add(id)
                val nickname = friendObject.getString("nickname")
                val existingFriend = getFriendsData().find { it.userData.userId == id }
                if (existingFriend == null) {
                    addFriend(FriendData(UserData(id, nickname), null))
                } else {
                    existingFriend.userData.let {
                        it.nickname = nickname
                    }
                }
            }
            //syncFriends(encounteredUserIds)
        }
    }

    suspend fun syncFriends(encounteredUserIds: List<Int>) {
        val ids = encounteredUserIds.map { it }
        friendDao.deleteFriendsNotIn(ids)
    }

    private fun parsePictureData(context: Context, jsonString: String) {
        coroutineScope.launch {
            val result = JSONObject(jsonString).getJSONArray("result")

            for (i in 0 until result.length()) {
                val picObject = result.getJSONObject(i)
                val userId = picObject.getInt("user_id")
                val pictureId = picObject.getInt("picture_id")
                val existingPicture = getPictureData(pictureId)
                if (existingPicture == null) {
                    val pictureDataString = picObject.getString("picture")
                    val pictureData: ByteArray = Base64.getDecoder().decode(pictureDataString)
                    val imagePath =
                        saveImageToInternalStorage(context.applicationContext, pictureId.toString(), pictureData)
                    addPicture(
                        friendId = userId,
                        path = imagePath
                    )
                }
            }
        }
    }

    private fun parseFriendLocationsData(jsonString: String) {
        coroutineScope.launch {
            val result = JSONObject(jsonString).getJSONArray("result")
            for (i in 0 until result.length()) {
                val locationObject = result.getJSONObject(i)
                val id = locationObject.getInt("user_id")
                val existingFriend = getFriendsData().find { it.userData.userId == id }

                val latitude = locationObject.getDouble("latitude")
                val longitude = locationObject.getDouble("longitude")
                val accuracy = locationObject.getDouble("accuracy")
                val speed = locationObject.getDouble("speed").toFloat()
                val timestampString = locationObject.getString("timestamp")
                val timestamp = parseAndConvertTimestamp(timestampString)

                if (existingFriend != null)
                friendDao.upsertFriend(
                    friend = FriendEntity(
                        id = id,
                        nickname = existingFriend.userData.nickname,
                        latitude = latitude,
                        longitude = longitude,
                    )
                )
            }
        }
    }

    private fun parseInsertedPictureId(context: Context, picId: String) {
        coroutineScope.launch {
            lastInsertedPicId = picId.toInt()
            val existingPicture = getPictureData(lastInsertedPicId)
            if (existingPicture == null) {
                val pictureData: ByteArray = Base64.getDecoder().decode(lastInsertedPictureDataString)
                lastInsertedPictureDataString = ""
                val imagePath =
                    saveImageToInternalStorage(context.applicationContext, lastInsertedPicId.toString(), pictureData)
                addPicture(
                    friendId = 0,
                    path = imagePath
                )
            }
        }
    }

    private fun parseMyData(myData: JSONObject) {
        val userId = myData.getInt("user_id")
        val picId = myData.optInt("picture_id", 0)
        val nickname = myData.getString("nickname")
        val tags = myData.getInt("user_score")
        val privacyNearby = myData.getBoolean("show_nearby")

        myUserId = userId
        myPfpId = picId
        myTags = tags
        myNickname = nickname
        privacyNearbyEnabled = privacyNearby
    }

    private fun parseMyUpdatedData(myData: JSONObject) {
        val tags = myData.getInt("user_score")
        myTags = tags
    }

    private fun parseConversationsData(jsonString: String) {
        val result = JSONObject(jsonString).getJSONArray("result")
        val encounteredConversationIds = mutableListOf<Int>()
        val updatedConversationsData = conversationsData.map { it.copy() }.toMutableList()
        for (i in 0 until result.length()) {
            val conversationObject = result.getJSONObject(i)
            val userId = conversationObject.getInt("user_id")
            val conversationId = conversationObject.getInt("conversation_id")
            encounteredConversationIds.add(conversationId)

            val nickname = conversationObject.getString("nickname")
            val lastMessagePictureId = conversationObject.optInt("msg_picture_id", 0)
            val lastMessageText = conversationObject.optString("text", "")
            val lastMessageAuthorId = conversationObject.optInt("author_id", 0)
            val lastMessageId = conversationObject.optInt("last_message_id", 0)
            val read = conversationObject.optBoolean("read", false)
            val timestampString = conversationObject.optString("timestamp", "")
            val existingConversation = updatedConversationsData.find { it.conversationID == conversationId }
            if (existingConversation == null) {
                if (lastMessageAuthorId != 0) { //Creating a new conversation with a message
                    updatedConversationsData.add(
                        ConversationData(
                            conversationId,
                            UserData(userId, nickname), mutableListOf(),
                            MessageData(
                                lastMessageAuthorId,
                                lastMessageAuthorId,
                                lastMessageText,
                                lastMessagePictureId,
                                parseAndConvertTimestamp(timestampString),
                                read
                            ),
                            pinned = false, markedUnread = false
                        )
                    )
                } else { //Creating a new conversation without a message
                    updatedConversationsData.add(
                        ConversationData(
                            conversationId,
                            UserData(userId, nickname),
                            mutableListOf(),
                            null,
                            pinned = false,
                            markedUnread = false
                        )
                    )
                }
            } else { //Updating existing conversation
                existingConversation.userData = UserData(userId, nickname)
                if (lastMessageId != 0) {
                    val timestamp = parseAndConvertTimestamp(timestampString)
                    val lastMessageData = MessageData(
                        lastMessageId, lastMessageAuthorId, lastMessageText,
                        lastMessagePictureId, timestamp, read
                    )
                    if (existingConversation.lastMessage == null || existingConversation.lastMessage?.messageId != lastMessageId || existingConversation.lastMessage?.read != read) {
                        existingConversation.lastMessage = lastMessageData
                        if (!read && lastMessageAuthorId != myUserId && messagesNotificationsEnabled) {
                            notificationManager.showNewMessageNotification(
                                nickname,
                                lastMessageText,
                                conversationId,
                                timestamp,
                                0
                            )
                        }
                    }
                } else {
                    existingConversation.lastMessage = null
                }
            }
        }
        updatedConversationsData.removeIf { conversation -> !encounteredConversationIds.contains(conversation.conversationID) }
        conversationsData = updatedConversationsData
    }

    private fun parseMessagesData(jsonString: String) {
        val message = JSONObject(jsonString)
        val result = message.getJSONArray("result")
        val updatedConversations = conversationsData.toMutableList()
        val conversationId = message.getInt("conversation_id")
        val existingConversation = updatedConversations.find { it.conversationID == conversationId }
        val encounteredMessageIds = mutableListOf<Int>()

        for (i in 0 until result.length()) {
            val messageObject = result.getJSONObject(i)
            val messageId = messageObject.getInt("message_id")
            encounteredMessageIds.add(messageId)
            val existingMessage = existingConversation?.messages?.find { it.messageId == messageId }
            if (existingConversation != null) {
                val ifRead = messageObject.getBoolean("read")
                if (existingMessage == null) {
                    val authorId = messageObject.getInt("author_id")
                    val text = messageObject.getString("text")
                    val timestampString = messageObject.getString("timestamp")
                    val pictureId = messageObject.optInt("picture_id", 0)
                    existingConversation.messages.add(
                        MessageData(
                            messageId,
                            authorId,
                            text,
                            pictureId,
                            parseAndConvertTimestamp(timestampString),
                            ifRead
                        )
                    )
                    existingConversation.messages.sortBy { it.timestamp }
                    addSeparatorIfNeeded(existingConversation.messages)
                } else {
                    existingConversation.lastMessage?.read = ifRead
                    existingMessage.read = ifRead
                }
            }
        }
        if (existingConversation != null) {
            existingConversation.messages.removeIf {
                !encounteredMessageIds.contains(it.messageId)
            }
            addSeparatorIfNeeded(existingConversation.messages)
        }
        conversationsData = updatedConversations
    }

    private fun parseGeoStoriesNearby(jsonString: String) {
        val result = JSONObject(jsonString).getJSONArray("result")
        val encounteredGeoStoryIds = mutableListOf<Int>()
        val updatedGeoStoriesData = geoStoriesData.toMutableList()

        for (i in 0 until result.length()) {
            val geoStoryObject = result.getJSONObject(i)
            val geoStoryId = geoStoryObject.getInt("geo_story_id")
            encounteredGeoStoryIds.add(geoStoryId)
            val views = geoStoryObject.optInt("views", 0)

            val existingGeoStory = updatedGeoStoriesData.find { it.geoStoryId == geoStoryId }
            if (existingGeoStory == null) {
                val timestampString = geoStoryObject.getString("timestamp")
                val pictureId = geoStoryObject.optInt("picture_id", 0)
                val latitude = geoStoryObject.getDouble("latitude")
                val longitude = geoStoryObject.getDouble("longitude")
                val creatorId = geoStoryObject.getInt("creator_id")
                val creatorNickname = geoStoryObject.getString("nickname")
                val privacy = geoStoryObject.getString("privacy")
                updatedGeoStoriesData.add(
                    GeoStoryData(
                        geoStoryId,
                        UserData(creatorId, creatorNickname),
                        pictureId,
                        privacy,
                        views,
                        latitude,
                        longitude,
                        parseAndConvertTimestamp(timestampString),
                        false
                    )
                )
            } else {
                existingGeoStory.views = views
            }
        }
        updatedGeoStoriesData.removeIf { geoStory -> !encounteredGeoStoryIds.contains(geoStory.geoStoryId) }
        geoStoriesData = updatedGeoStoriesData
    }

    private fun parseLeaderBoard(jsonString: String) {
        val message = JSONObject(jsonString)
        val result = message.getJSONArray("result")
        val leaderBoard = mutableListOf<LeaderBoardData>()
        myPlace = message.getInt("my_place")
        for (i in 0 until result.length()) {
            val leaderBoardObject = result.getJSONObject(i)
            val userId = leaderBoardObject.getInt("id")
            val nickname = leaderBoardObject.getString("nickname")
            val place = leaderBoardObject.getInt("place")
            val tags = leaderBoardObject.getInt("tags")
            leaderBoard.add(
                LeaderBoardData(
                    UserData(userId, nickname),
                    place,
                    tags
                )
            )
        }
        leaderBoardList = leaderBoard
    }

    private fun parseNearbyPeople(jsonString: String) {
        val message = JSONObject(jsonString)
        val result = message.getJSONArray("result")
        val peopleNearby = mutableListOf<UserNearbyData>()
        for (i in 0 until result.length()) {
            val userNearbyObject = result.getJSONObject(i)
            val userId = userNearbyObject.getInt("user_id")
            val nickname = userNearbyObject.getString("nickname")
            val distance = userNearbyObject.getDouble("distance_meters")
            peopleNearby.add(
                UserNearbyData(
                    UserData(userId, nickname),
                    distance.toInt()
                )
            )
        }
        peopleNearbyList = peopleNearby
    }

    private fun addSeparatorIfNeeded(messages: MutableList<MessageData>) {
        if (messages.isEmpty()) return

        val updatedMessages = mutableListOf<MessageData>()
        var previousDate: String? = null

        for (message in messages) {
            val currentMessageDate = getDateString(message.timestamp)

            if (previousDate == null || previousDate != currentMessageDate) {
                val separator = createSeparator(message)
                updatedMessages.add(separator)
            }

            updatedMessages.add(message)
            previousDate = currentMessageDate
        }

        messages.clear()
        messages.addAll(updatedMessages)
    }

    private fun createSeparator(referenceMessage: MessageData): MessageData {
        return MessageData(
            0,
            0,
            "",
            null,
            referenceMessage.timestamp,
            true
        )
    }

    private fun getDateString(timestamp: Timestamp): String {
        val calendar = Calendar.getInstance()
        calendar.time = timestamp
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val year = calendar.get(Calendar.YEAR)
        return "$year-$dayOfYear"
    }

    private fun parseFriendRequestData(jsonString: String) {
        val result = JSONObject(jsonString).getJSONArray("result")
        val encounteredUserIds = mutableListOf<Int>()
        val updatedFriendRequestsData = friendRequestsData.toMutableList()
        for (i in 0 until result.length()) {
            val requestObject = result.getJSONObject(i)
            val id = requestObject.getInt("user_id")
            val nickname = requestObject.getString("nickname")
            val relation = requestObject.getString("relation")

            encounteredUserIds.add(id)
            val existingFriendRequest = updatedFriendRequestsData.find { it.userData.userId == id }
            if (existingFriendRequest != null) {
                existingFriendRequest.userData.nickname = nickname
                existingFriendRequest.relation = relation
            } else {
                updatedFriendRequestsData.add(FriendRequestData(UserData(id, nickname), relation))
                if (relation == "request_incoming" && friendRequestsNotificationsEnabled) {
                    notificationManager.showNewFriendRequestNotification(nickname, id)
                }
            }
        }
        updatedFriendRequestsData.removeIf { friendRequest -> !encounteredUserIds.contains(friendRequest.userData.userId) }
        friendRequestsData = updatedFriendRequestsData
    }

    fun clearNotificationsForConversation(conversationId: Int) {
        notificationManager.clearMessages(conversationId)
    }

    fun getFriendsData(): List<FriendData> {
        return runBlocking {
            val result = friendDao.getAllFriends()
                .map { it.toFriendData() }

            //Log.d("Friends", result.toString())
            result
        }
    }

    fun getGeoStoriesDataList(): List<GeoStoryData> {
        return geoStoriesData
    }

    fun getFriendRequestDataList(): List<FriendRequestData> {
        return friendRequestsData
    }

    fun getConversationsDataList(): List<ConversationData> {
        return conversationsData
    }

    fun getConversationData(id: Int): ConversationData? {
        return conversationsData.find { it.conversationID == id }
    }

    fun getLeaderBoardData(): List<LeaderBoardData>? {
        return leaderBoardList
    }

    fun getNearbyPeopleData(): List<UserNearbyData>? {
        return peopleNearbyList
    }

    suspend fun getPictureData(userId: Int): Bitmap? {
        if (userId == 0) return null

        val picEntity = imageDao.getImagesForUser(userId)
            .firstOrNull() ?: return null

        return BitmapFactory.decodeFile(picEntity.uri)
    }

    suspend fun getPicturesData(): List<PictureData> = imageDao.getAllImages().map { it.toPictureData() }

    fun togglePinnedStatus(conversationId: Int) {
        val updatedConversations = conversationsData.toMutableList()
        val conversation = updatedConversations.find { it.conversationID == conversationId }
        conversation?.pinned = conversation?.pinned?.not() ?: false
        conversationsData = updatedConversations
    }

    fun toggleMarkedUnreadStatus(conversationId: Int) {
        val updatedConversations = conversationsData.toMutableList()
        val conversation = updatedConversations.find { it.conversationID == conversationId }
        conversation?.markedUnread = conversation?.markedUnread?.not() ?: false
        conversationsData = updatedConversations
    }

    fun disableMarkedUnreadStatus(conversationId: Int) {
        val updatedConversations = conversationsData.toMutableList()
        val conversation = updatedConversations.find { it.conversationID == conversationId }
        conversation?.markedUnread = false
        conversationsData = updatedConversations
    }

    private fun serializeGeoStoriesData(geoStoriesData: List<GeoStoryData>): String {
        return gson.toJson(geoStoriesData)
    }

    private fun deserializeGeoStoriesData(jsonString: String): List<GeoStoryData> {
        val type = object : TypeToken<List<GeoStoryData>>() {}.type
        return gson.fromJson(jsonString, type)
    }

    private fun serializeConversationsData(conversationsData: List<ConversationData>): String {
        return gson.toJson(conversationsData)
    }

    private fun deserializeConversationsData(jsonString: String): List<ConversationData> {
        val type = object : TypeToken<List<ConversationData>>() {}.type
        return gson.fromJson(jsonString, type)
    }

    private fun serializeFriendRequestsData(friendRequestsData: List<FriendRequestData>): String {
        return gson.toJson(friendRequestsData)
    }

    private fun deserializeFriendRequestsData(jsonString: String): List<FriendRequestData> {
        val type = object : TypeToken<List<FriendRequestData>>() {}.type
        return gson.fromJson(jsonString, type)
    }


    fun markGeoStoryViewed(geoStoryId: Int) {
        val updatedGeoStoriesData = geoStoriesData.toMutableList()
        val geoStoryIndex = updatedGeoStoriesData.indexOfFirst { it.geoStoryId == geoStoryId }
        if (geoStoryIndex != -1) {
            val updatedGeoStory = updatedGeoStoriesData[geoStoryIndex].copy(viewed = true)
            updatedGeoStoriesData[geoStoryIndex] = updatedGeoStory
            geoStoriesData = updatedGeoStoriesData
        }
    }

    private fun serializePicturesData(picturesData: List<PictureData>): String {
        val gson = Gson()
        return gson.toJson(picturesData)
    }

    private fun deserializePicturesData(jsonString: String): List<PictureData> {
        val gson = Gson()
        val type = object : TypeToken<List<PictureData>>() {}.type
        return gson.fromJson(jsonString, type)
    }

    private fun saveImageToInternalStorage(context: Context, fileName: String, imageBytes: ByteArray): String {
        val file = File(context.filesDir, fileName)
        if (file.exists()) return file.absolutePath
        FileOutputStream(file).use { outputStream ->
            outputStream.write(imageBytes)
        }
        val absolutePath = file.absolutePath
        Log.d("Tagme_ImageStorage", "Image saved to internal storage at path: $absolutePath")
        return absolutePath
    }

    fun getInternalStorageSize(context: Context): String {
        val filesDir = context.filesDir
        var totalSize = 0L

        filesDir.listFiles()?.forEach { file ->
            totalSize += file.length()
        }

        val sizeInKB = totalSize / 1024
        val sizeInMB = sizeInKB / 1024

        return if (sizeInMB > 0) {
            context.getString(R.string.mb_format, sizeInMB)
        } else {
            context.getString(R.string.kb_format, sizeInKB)
        }
    }

    fun clearImageInternalStorage(context: Context) {
        coroutineScope.launch {
            imageDao.clearImages()
        }
    }

    private suspend fun sendRequestToWS(request: JSONObject): JSONObject? {
        if (isFakeMode) {
            return simulateFakeResponse(request)
        }

        val action = request.getString("action")
        val requestId = generateRequestId()
        val future = CompletableFuture<JSONObject?>()
        request.put("request_id", requestId)

        val isGetPictureAction = action == "get picture"

        if (!isGetPictureAction) {
            val matchingRequestIds = requestMap.filterValues { it.second == action }.keys
            if (matchingRequestIds.isNotEmpty()) {
                return JSONObject()
            }
        }

        val result = if (isGetPictureAction) {
            pictureMutex.withLock {
                val pictureId = request.getInt("picture_id")
                val picture = getPicturesData().find { it.pictureId == pictureId }
                if (picture?.imagePath == null) {
                    sendRequest(request, requestId, future, action)
                } else {
                    JSONObject() // return empty JSON if picture was already loaded
                }
            }
        } else {
            sendRequest(request, requestId, future, action)
        }

        return result
    }

    private suspend fun simulateFakeResponse(requestData: JSONObject): JSONObject {
        delay(300L)
        val action = requestData.getString("action")
        val response = JSONObject().apply {
            put("status", "success")
            put("action", action)
            put("request_id", requestData.optInt("request_id", 0))
        }


        when (action) {
            "login", "register", "auth vk" -> {
                myNickname = "ромчик:3"
                response.put("message", "fake_token_12345")
            }

            "get friends" -> {
                val friendsJson = simulateGetFriendsResponse()
                response.put("message", friendsJson.toString())
            }

            "get locations" -> {
                val locationsJson = simulateGetLocationsResponse()
                response.put("message", locationsJson.toString())
            }

            "insert picture" -> {
                val newId = (getPicturesData().maxOfOrNull { it.pictureId } ?: 0) + 1
                lastInsertedPicId = newId
                response.put("message", newId.toString())
                parseInsertedPictureId(context, newId.toString())
            }

            "get picture" -> {
                val picturesJson = simulateGetPicturesResponse()
                response.put("message", picturesJson.toString())
            }

            else -> {
                response.put("message", "{}")
            }
        }

        return response
    }

    private fun encodeResourceToBase64(resId: Int): String {
        val drawable = ResourcesCompat.getDrawable(context.resources, resId, null) ?: return ""
        val bitmap = if (drawable is BitmapDrawable) {
            drawable.bitmap
        } else {
            // Convert VectorDrawable or other drawable types to bitmap
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 100
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 100
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        }

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        return Base64.getEncoder().encodeToString(outputStream.toByteArray())
    }


    private fun simulateGetFriendsResponse(): JSONObject {
        val friendsJsonArray = JSONArray().apply {
            put(JSONObject().apply { put("user_id", 1); put("nickname", "Friend")})
            put(JSONObject().apply { put("user_id", 2); put("nickname", "Enemy")})
            put(JSONObject().apply { put("user_id", 3); put("nickname", "Enemy 2") })
            put(JSONObject().apply { put("user_id", 4); put("nickname", "Dimitriy") })
        }

        val friendsJson = JSONObject().apply { put("result", friendsJsonArray) }

        val response = JSONObject().apply {
            put("status", "success")
            put("action", "get friends")
            put("request_id", generateRequestId())
            put("message", friendsJson.toString())
        }

        simulateGetPicturesResponse()
        parseFriendsData(friendsJson.toString())

        return response
    }

    private fun simulateGetPicturesResponse() {
        val picturesJsonArray = JSONArray().apply {
            put(JSONObject().apply {
                put("picture_id", 1)
                put("user_id", 1)
                put("picture", encodeResourceToBase64(R.drawable.cat1))
            })
            put(JSONObject().apply {
                put("picture_id", 2)
                put("user_id", 2)
                put("picture", encodeResourceToBase64(R.drawable.cat2))
            })
            put(JSONObject().apply {
                put("picture_id", 3)
                put("user_id", 3)
                put("picture", encodeResourceToBase64(R.drawable.cat3))
            })
        }

        val picturesJson = JSONObject().apply {
            put("result", picturesJsonArray)
        }

        parsePictureData(context, picturesJson.toString())
    }

    private fun simulateGetLocationsResponse() {
        val baseLat = 57.994763
        val baseLon = 56.203790
        val now = Timestamp(System.currentTimeMillis())

        val locationsJsonArray = JSONArray().apply {
            put(JSONObject().apply {
                put("user_id", 1)
                put("latitude", baseLat + 0.005)
                put("longitude", baseLon + 0.005)
                put("accuracy", 5.0)
                put("speed", 0.5)
                put("timestamp", now.toString())
            })
            put(JSONObject().apply {
                put("user_id", 2)
                put("latitude", baseLat - 0.007)
                put("longitude", baseLon + 0.003)
                put("accuracy", 4.0)
                put("speed", 0.8)
                put("timestamp", now.toString())
            })
            put(JSONObject().apply {
                put("user_id", 3)
                put("latitude", baseLat + 0.01)
                put("longitude", baseLon - 0.004)
                put("accuracy", 3.5)
                put("speed", 0.4)
                put("timestamp", now.toString())
            })
            put(JSONObject().apply {
                put("user_id", 4)
                put("latitude", baseLat + 0.1)
                put("longitude", baseLon - 0.04)
                put("accuracy", 3.5)
                put("speed", 0.4)
                put("timestamp", now.toString())
            })
        }

        val locationsJson = JSONObject().apply {
            put("result", locationsJsonArray)
        }

        parseFriendLocationsData(locationsJson.toString())
    }

    suspend fun addFriend(friend: FriendData) {
        friendDao.upsertFriend(
            FriendEntity(
                id = friend.userData.userId,
                nickname = friend.userData.nickname,
                latitude = friend.location?.latitude ?: 0.0,
                longitude = friend.location?.longitude ?: 0.0,
                createdAt = friend.location?.timestamp ?: Date()
            )
        )
    }

    suspend fun deleteFriendById(friendId: Int) {
        friendDao.deleteFriendById(friendId)
    }

    suspend fun addPicture(friendId: Int, path: String) {
        imageDao.addImage(
            ImageEntity(
                userId = friendId,
                uri = path
            )
        )
    }

    suspend fun deletePictureById(imageId: Int) {
        imageDao.deleteImageById(imageId)
    }

    private suspend fun sendRequest(
        request: JSONObject,
        requestId: Int,
        future: CompletableFuture<JSONObject?>,
        action: String
    ): JSONObject? {
        requestMap[requestId] = future to action
        webSocket?.send(request.toString())
        val result = future.await()
        requestMap.remove(requestId)
        return result
    }

    fun parseAndConvertTimestamp(timestampString: String): Timestamp {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone("GMT+3")
        val parsedTimestamp = dateFormat.parse(timestampString)

        if (parsedTimestamp != null) {
            val calendar = Calendar.getInstance()
            calendar.time = parsedTimestamp
            val adjustedTimestamp = Timestamp(calendar.timeInMillis)
            return adjustedTimestamp
        } else {
            throw IllegalArgumentException("Failed to parse timestamp: $timestampString")
        }
    }

    fun calculateDurationFromTimestamp(timestamp: Timestamp): Duration {
        val timestampLocalDateTime = LocalDateTime.parse(timestamp.toString(), dateFormatter)
        val now = LocalDateTime.now()
        val duration = Duration.between(timestampLocalDateTime, now)
        return duration
    }

    private fun generateRequestId(): Int {
        return requestIdCounter.getAndIncrement()
    }

    fun closeWebSocket() {
        Log.d("Tagme_WS", "Closing WebSocket")
        webSocket?.close(1000, "Closing")
        webSocket = null
    }
}
