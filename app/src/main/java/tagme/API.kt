package tagme
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore

class API private constructor(context: Context){
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("API_PREFS", Context.MODE_PRIVATE)

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var answerReceived = false
    private var answer = JSONObject()
    private var lastInsertedPictureDataString = ""
    var lastInsertedPicId = 0
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    private val MAX_CONCURRENT_REQUESTS = 6
    private val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)

    var myToken: String?
        get() = sharedPreferences.getString("TOKEN", null)
        set(value) {
            sharedPreferences.edit().putString("TOKEN", value).apply()
        }
    var myUserId: Int
        get() = sharedPreferences.getInt("UserID", 0)
        set(value) {
            sharedPreferences.edit().putInt("UserID", value).apply()
        }
    var myPfpId: Int
        get() = sharedPreferences.getInt("PfpId", 0)
        set(value) {
            sharedPreferences.edit().putInt("PfpId", value).apply()
        }
    var myNickname: String?
        get() = sharedPreferences.getString("NICKNAME", null)
        set(value) {
            sharedPreferences.edit().putString("NICKNAME", value).apply()
        }
    private val friendsData = mutableListOf<FriendData>()
    private val friendsRequestsData = mutableListOf<FriendRequestData>()
    private val conversationsData = mutableListOf<ConversationData>()
    private val geoStoriesData = mutableListOf<GeoStoryData>()
    private var picsData: List<PictureData>
        get() {
            val jsonString = sharedPreferences.getString("PICTURES_DATA", null)
            return if (jsonString != null) {
                deserializePicturesData(jsonString)
            } else {
                mutableListOf()
            }
        }
        set(value) {
            val jsonString = serializePicturesData(value)
            sharedPreferences.edit().putString("PICTURES_DATA", jsonString).apply()
        }
    suspend fun connectToServer(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            val url = "ws://141.8.193.201:8765"  // WebSocket server URL

            val request = Request.Builder()
                .url(url)
                .build()

            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    this@API.webSocket = webSocket
                    Log.d("Tagme_WS", "onOpen trigger: $response")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.d("Tagme_WS", "onFailure trigger: $response")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d("Tagme_WS", "onMessage trigger: $text")
                    val jsonObject = JSONObject(text)
                    answer = jsonObject
                    when (answer.getString("action")) {
                        "login", "register" -> when (answer.getString("status")) {
                            "success" -> {
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
                        "get messages", "get new messages" -> when (answer.getString("status")) {
                            "success" -> parseMessagesData(answer.getString("message"))
                        }
                        "get my data" -> when (answer.getString("status")) {
                            "success" -> parseMyData(answer.getString("message"))
                        }
                        "insert picture" -> when (answer.getString("status")) {
                            "success" -> parseInsertedPictureId(context, answer.getString("message"))
                        }
                        "get geo stories" -> when (answer.getString("status")) {
                            "success" -> parseGeoStoriesNearby(answer.getString("message"))
                        }
                    }
                    answerReceived = true
                    synchronized(this@API) {
                        (this@API as Object).notify()
                    }
                }
            }

            client.newWebSocket(request, listener)

            true
        }
    }

    suspend fun registerUser(username: String, password: String): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "register")
            put("username", username)
            put("password", password)
        }
        myNickname = username
        return sendRequestToWS(requestData)
    }

    suspend fun loginUser(username: String, password: String): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "login")
            put("username", username)
            put("password", password)
        }
        myNickname = username
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
    suspend fun getFriendRequestsFromWS(): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "get friend requests")
            put("token", myToken)
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
    suspend fun getMessagesFromWS(conversationId: Int, lastMsgId: Int): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "get messages")
            put("token", myToken)
            put("conversation_id", conversationId)
            put("last_message_id", lastMsgId)
        }
        return sendRequestToWS(requestData)
    }
    suspend fun getNewMessagesFromWS(conversationId: Int, lastMsgId: Int): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "get new messages")
            put("token", myToken)
            put("conversation_id", conversationId)
            put("last_message_id", lastMsgId)
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
    suspend fun getGeoStories(): JSONObject? {
        val requestData = JSONObject().apply {
            put("action", "get geo stories")
            put("token", myToken)
        }
        return sendRequestToWS(requestData)
    }

    private suspend fun waitForServerAnswer(): JSONObject? {
        return synchronized(this) {
            val timeoutDuration = 15000
            val startTime = System.currentTimeMillis()

            while (!answerReceived) {
                val elapsedTime = System.currentTimeMillis() - startTime
                if (elapsedTime >= timeoutDuration) {
                    return@waitForServerAnswer null
                }
                (this as Object).wait(100)
            }
            answerReceived = false
            answer
        }
    }
    private fun parseFriendsData(jsonString: String){
        val result = JSONObject(jsonString).getJSONArray("result")

        for (i in 0 until result.length()) {
            val friendObject = result.getJSONObject(i)
            val id = friendObject.getInt("user_id")
            val nickname = friendObject.getString("nickname")
            val pictureId = friendObject.optInt("picture_id", 0)
            val existingFriend = friendsData.find { it.userData.userId == id }
            if (existingFriend == null) {
                friendsData.add(FriendData(UserData(id, nickname, pictureId), null))
            }
        }
    }
    private fun parsePictureData(context: Context, jsonString: String){
        val result = JSONObject(jsonString).getJSONArray("result")

        for (i in 0 until result.length()) {
            val picObject = result.getJSONObject(i)
            val pictureId = picObject.getInt("picture_id")
            val pictureDataString = picObject.getString("picture")
            val existingPicture = picsData.find { it.pictureId == pictureId }
            if (existingPicture == null) {
                Log.d("Tagme_WS_Pic", "Before decoding: $pictureDataString")
                val pictureData: ByteArray = Base64.getDecoder().decode(pictureDataString)
                Log.d("Tagme_WS_Pic", "After decoding: $pictureData")
                val imagePath = saveImageToCache(context, pictureId.toString(), pictureData)
                val newPictureData = PictureData(pictureId, imagePath)
                val updatedPicturesData = picsData.toMutableList().apply {
                    add(newPictureData)
                }
                Log.d("Tagme_WS_Pic", "Image $pictureId encoded: $pictureDataString")
                Log.d("Tagme_WS_Pic", "Image $pictureId decoded: ${pictureData}")
                picsData = updatedPicturesData
            }
        }
    }
    private fun parseFriendLocationsData(jsonString: String){
        val result = JSONObject(jsonString).getJSONArray("result")

        for (i in 0 until result.length()) {
            val locationObject = result.getJSONObject(i)
            val id = locationObject.getInt("user_id")
            val latitude = locationObject.getDouble("latitude")
            val longitude = locationObject.getDouble("longitude")
            val accuracy = locationObject.getDouble("accuracy")
            val speed = locationObject.getDouble("speed")
            val timestamp = locationObject.getString("timestamp")
            val existingFriend = friendsData.find { it.userData.userId == id }
            if (existingFriend != null) {
                if (existingFriend.location == null) {
                    existingFriend.location = LocationData(latitude, longitude, accuracy, speed.toFloat(), Timestamp(dateFormat.parse(timestamp)!!.time))
                } else {
                    existingFriend.location?.latitude = latitude
                    existingFriend.location?.longitude = longitude
                    existingFriend.location?.accuracy = accuracy
                    existingFriend.location?.speed = speed.toFloat()
                    existingFriend.location?.timestamp = dateFormat.parse(timestamp)?.let { Timestamp(it.time) }
                }
            }
        }
    }
    private fun parseInsertedPictureId(context: Context, picId: String){
        lastInsertedPicId = picId.toInt()
        val existingPicture = picsData.find { it.pictureId == lastInsertedPicId }
        if (existingPicture == null) {
            val pictureData: ByteArray = Base64.getDecoder().decode(lastInsertedPictureDataString)
            lastInsertedPictureDataString = ""
            val imagePath = saveImageToCache(context , lastInsertedPicId.toString(), pictureData)
            val newPictureData = PictureData(lastInsertedPicId, imagePath)
            val updatedPicturesData = picsData.toMutableList().apply {
                add(newPictureData)
            }
            picsData = updatedPicturesData
        }
    }

    private fun parseMyData(jsonString: String){
        val result = JSONObject(jsonString).getJSONArray("result")

        for (i in 0 until result.length()) {
            val locationObject = result.getJSONObject(i)
            val userId = locationObject.getInt("user_id")
            val picId = locationObject.optInt("picture_id", 0)
            myUserId = userId
            myPfpId = picId
        }
    }
    private fun parseConversationsData(jsonString: String){
        val result = JSONObject(jsonString).getJSONArray("result")
        val encounteredConversationIds = mutableListOf<Int>()

        for (i in 0 until result.length()) {
            val conversationObject = result.getJSONObject(i)
            val userId = conversationObject.getInt("user_id")
            val conversationId = conversationObject.getInt("conversation_id")
            encounteredConversationIds.add(conversationId)

            val nickname = conversationObject.getString("nickname")
            val profilePictureId = conversationObject.optInt("profile_picture_id", 0)
            val lastMessagePictureId = conversationObject.optInt("msg_picture_id", 0)
            val lastMessageText = conversationObject.optString("text", "")
            val lastMessageAuthorId = conversationObject.optInt("author_id", 0)
            val read = conversationObject.optBoolean("read", false)
            val timestamp = conversationObject.getString("timestamp")
            val existingConversation = conversationsData.find { it.conversationID == conversationId }
            if (existingConversation == null) {
                if (lastMessageAuthorId != 0) {
                    conversationsData.add(ConversationData(conversationId,
                        UserData(userId, nickname, profilePictureId), mutableListOf(),
                        LastMessageData(lastMessageAuthorId, lastMessageText, lastMessagePictureId,
                            Timestamp(dateFormat.parse(timestamp)!!.time), read)))
                } else {
                    conversationsData.add(ConversationData(conversationId,
                        UserData(userId, nickname, profilePictureId), mutableListOf(),
                        null))
                }
            } else {
                existingConversation.userData = UserData(userId, nickname, profilePictureId)
                if (lastMessageAuthorId != 0) {
                    existingConversation.lastMessage = LastMessageData(lastMessageAuthorId, lastMessageText, lastMessagePictureId,
                        Timestamp(dateFormat.parse(timestamp)!!.time), read)
                }
            }
        }
        conversationsData.removeIf { conversation -> !encounteredConversationIds.contains(conversation.conversationID) }
    }
    private fun parseMessagesData(jsonString: String){
        val result = JSONObject(jsonString).getJSONArray("result")
        for (i in 0 until result.length()) {
            val messageObject = result.getJSONObject(i)
            val conversationId = messageObject.getInt("conversation_id")
            val authorId = messageObject.getInt("author_id")
            val messageId = messageObject.getInt("message_id")
            val text = messageObject.getString("text")
            val timestamp = messageObject.getString("timestamp")
            val ifRead = messageObject.getBoolean("read")
            val pictureId = messageObject.optInt("picture_id", 0)
            val existingConversation = conversationsData.find { it.conversationID == conversationId }
            val existingMessage = existingConversation?.messages?.find{it.messageId == messageId}
            if (existingConversation != null) {
                if (existingMessage == null) {
                    existingConversation.messages.add(
                        MessageData(
                            messageId,
                            authorId,
                            text,
                            pictureId,
                            Timestamp(dateFormat.parse(timestamp)!!.time),
                            ifRead
                        )
                    )
                    existingConversation.messages.sortBy { it.timestamp }
                    addSeparatorIfNeeded(existingConversation.messages)
                } else {
                    //TBA: editing of existing images
                }
            }
        }
    }
    private fun parseGeoStoriesNearby(jsonString: String){
        val result = JSONObject(jsonString).getJSONArray("result")
        val encounteredGeoStoryIds = mutableListOf<Int>()

        for (i in 0 until result.length()) {
            val geoStoryObject = result.getJSONObject(i)
            val geoStoryId = geoStoryObject.getInt("geo_story_id")
            encounteredGeoStoryIds.add(geoStoryId)

            val pictureId = geoStoryObject.optInt("picture_id", 0)
            val latitude = geoStoryObject.getDouble("latitude")
            val longitude = geoStoryObject.getDouble("longitude")

            val existingGeoStory = geoStoriesData.find { it.geoStoryId == geoStoryId }
            if (existingGeoStory == null) {
                geoStoriesData.add(
                    GeoStoryData(
                        geoStoryId,
                        null,
                        pictureId,
                        null,
                        null,
                        latitude,
                        longitude,
                        null,
                    )
                )
            }
        }
        geoStoriesData.removeIf { geoStory -> !encounteredGeoStoryIds.contains(geoStory.geoStoryId) }
    }
    private fun addSeparatorIfNeeded(messages: MutableList<MessageData>) {
        if (messages.isEmpty()) return
        val currentMessage = messages.map{it.copy()}.last()
        if (messages.size < 2) {
            val separator = createSeparator(currentMessage)
            messages.removeLast()
            messages.add(separator)
            messages.add(currentMessage)
            return
        }

        val lastMessage = messages[messages.size - 2].copy()
        val lastMessageDate = getDateString(lastMessage.timestamp)
        val currentMessageDate = getDateString(currentMessage.timestamp)

        if (lastMessageDate != currentMessageDate) {
            val separator = createSeparator(currentMessage)
            messages.removeLast()
            messages.add(separator)
            messages.add(currentMessage)
        }
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
        val userIdsInResult = HashSet<Int>()

        for (i in 0 until result.length()) {
            val requestObject = result.getJSONObject(i)
            val id = requestObject.getInt("user_id")
            val nickname = requestObject.getString("nickname")
            val relation = requestObject.getString("relation")
            val pictureId = requestObject.optInt("picture_id", 0)

            userIdsInResult.add(id)
            val existingFriendRequest = friendsRequestsData.find { it.userData.userId == id }
            if (existingFriendRequest != null) {
                existingFriendRequest.userData.nickname = nickname
                existingFriendRequest.userData.profilePictureId = pictureId
                existingFriendRequest.relation = relation
            } else {
                friendsRequestsData.add(FriendRequestData(UserData(id, nickname, pictureId), relation))
            }
        }

        val iterator = friendsRequestsData.iterator()
        while (iterator.hasNext()) {
            val friendRequest = iterator.next()
            if (friendRequest.userData.userId !in userIdsInResult) {
                iterator.remove()
            }
        }
    }


    fun getFriendsData(): MutableList<FriendData> {
        return friendsData
    }
    fun getGeoStoriesData(): MutableList<GeoStoryData> {
        return geoStoriesData
    }
    fun getFriendRequestData(): MutableList<FriendRequestData> {
        return friendsRequestsData
    }
    fun getConversationsData(): MutableList<ConversationData> {
        return conversationsData
    }
    fun getConversationData(id: Int): ConversationData? {
        return conversationsData.find{it.conversationID == id}
    }

    suspend fun getPictureData(pictureId: Int): Bitmap? {
        var picture = picsData.find { it.pictureId == pictureId }
        if (picture == null || picture.imagePath == null) {
            getPictureFromWS(pictureId)
            picture = picsData.find { it.pictureId == pictureId }
        }
        return if (picture != null) {
            BitmapFactory.decodeFile(picture.imagePath)
        } else null
    }

    data class LocationData(
        var latitude: Double,
        var longitude: Double,
        var accuracy: Double,
        var speed: Float,
        var timestamp: Timestamp?
    )
    data class GeoStoryData(
        val geoStoryId: Int,
        var creatorData: UserData?,
        val pictureId: Int,
        var privacy: String?,
        var views: Int?,
        val latitude: Double,
        val longitude: Double,
        var timestamp: Timestamp?
    )
    data class UserData(
        val userId: Int,
        var nickname: String,
        var profilePictureId: Int
    )
    data class PictureData(
        val pictureId: Int,
        val imagePath: String? // Path to the cached image
    )


    data class FriendData(
        val userData: UserData,
        var location: LocationData?,
    )

    data class FriendRequestData(
        val userData: UserData,
        var relation: String
    )
    data class ConversationData(
        val conversationID: Int,
        var userData: UserData,
        var messages: MutableList<MessageData>,
        var lastMessage: LastMessageData?
    )
    data class MessageData(
        val messageId: Int,
        val authorId: Int,
        var text: String?,
        val imageId: Int?,
        val timestamp: Timestamp,
        val read: Boolean
    )
    data class LastMessageData(
        val authorId: Int,
        var text: String?,
        val imageId: Int?,
        val timestamp: Timestamp,
        val read: Boolean
    )


    companion object {
        // Singleton instance
        @Volatile
        private var instance: API? = null

        // Function to get the singleton instance
        fun getInstance(context: Context): API {
            return instance ?: synchronized(this) {
                instance ?: API(context.applicationContext).also { instance = it }
            }
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

    private fun saveImageToCache(context: Context, fileName: String, imageBytes: ByteArray): String {
        val cacheDir = context.cacheDir
        val file = File(cacheDir, fileName)

        FileOutputStream(file).use { outputStream ->
            outputStream.write(imageBytes)
        }

        return file.absolutePath
    }
    private suspend fun sendRequestToWS(request: JSONObject): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                semaphore.acquire()
                webSocket?.send(request.toString())
                waitForServerAnswer()
            } finally {
                semaphore.release()
            }
        }
    }
}
