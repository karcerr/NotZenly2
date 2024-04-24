package tagme
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.*

class API private constructor(context: Context){
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("API_PREFS", Context.MODE_PRIVATE)
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var answerReceived = false
    private var answer = JSONObject()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
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
    var picsData: List<PictureData>
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
                    Log.d("Tagme_custom_log", "onOpen trigger: $response")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.d("Tagme_custom_log", "onFailure trigger: $response")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d("Tagme_custom_log", "onMessage trigger: $text")

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
                        "get messages" -> when (answer.getString("status")) {
                            "success" -> parseMessagesData(answer.getString("message"))
                        }
                        "get my data" -> when (answer.getString("status")) {
                            "success" -> parseMyData(answer.getString("message"))
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
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "register")
                put("username", username)
                put("password", password)
            }
            myNickname = username
            webSocket?.send(requestData.toString())

            waitForServerAnswer()
        }
    }

    suspend fun loginUser(username: String, password: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "login")
                put("username", username)
                put("password", password)
            }
            myNickname = username
            webSocket?.send(requestData.toString())

            waitForServerAnswer()
        }
    }
    suspend fun loginToken(): JSONObject? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "validate token")
                put("token", myToken)
            }
            webSocket?.send(requestData.toString())

            waitForServerAnswer()
        }
    }
    suspend fun sendLocationToWS(latitude: String, longitude: String, accuracy: String, speed: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "send location")
                put("token", myToken)
                put("latitude", latitude)
                put("longitude", longitude)
                put("accuracy", accuracy)
                put("speed", speed)
            }

            webSocket?.send(requestData.toString())

            waitForServerAnswer()
        }
    }
    suspend fun getLocationsFromWS(): JSONObject? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "get locations")
                put("token", myToken)
            }

            webSocket?.send(requestData.toString())

            waitForServerAnswer()
        }
    }
    suspend fun getFriendsFromWS(): JSONObject? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "get friends")
                put("token", myToken)
            }

            webSocket?.send(requestData.toString())

            waitForServerAnswer()
        }
    }
    suspend fun getConversationsFromWS(): JSONObject? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "get conversations")
                put("token", myToken)
            }

            webSocket?.send(requestData.toString())

            waitForServerAnswer()
        }
    }
    suspend fun sendFriendRequestToWS(username: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "add friend")
                put("token", myToken)
                put("nickname", username)
            }

            webSocket?.send(requestData.toString())

            waitForServerAnswer()
        }
    }
    suspend fun getFriendRequestsFromWS(): JSONObject? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "get friend requests")
                put("token", myToken)
            }

            webSocket?.send(requestData.toString())

            waitForServerAnswer()
        }
    }
    suspend fun acceptFriendRequest(id: Int): JSONObject? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "accept request")
                put("token", myToken)
                put("user2_id", id)
            }
            webSocket?.send(requestData.toString())

            waitForServerAnswer()
        }
    }
    suspend fun denyFriendRequest(id: Int): JSONObject? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "deny request")
                put("token", myToken)
                put("user2_id", id)
            }
            webSocket?.send(requestData.toString())

            waitForServerAnswer()
        }
    }
    suspend fun cancelFriendRequest(id: Int): JSONObject? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "cancel request")
                put("token", myToken)
                put("user2_id", id)
            }
            webSocket?.send(requestData.toString())

            waitForServerAnswer()
        }
    }
    suspend fun getPictureFromWS(id: Int): JSONObject? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "get picture")
                put("token", myToken)
                put("picture_id", id)
            }
            webSocket?.send(requestData.toString())

            waitForServerAnswer()
        }
    }
    suspend fun getMyDataFromWS(): JSONObject? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "get my data")
                put("token", myToken)
            }
            webSocket?.send(requestData.toString())

            waitForServerAnswer()
        }
    }
    suspend fun getMessagesFromWS(conversationId: Int, lastMsgId: Int): JSONObject? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "get messages")
                put("token", myToken)
                put("conversation_id", conversationId)
                put("last_message_id", lastMsgId)
            }
            webSocket?.send(requestData.toString())

            waitForServerAnswer()
        }
    }

    private suspend fun waitForServerAnswer(): JSONObject? {
        return synchronized(this) {
            val timeoutDuration = 1000
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
            if (pictureId != 0) {
                val existingPicture = picsData.find { it.pictureId == pictureId }
                if ((existingPicture == null) or (existingPicture?.imagePath == null)) {
                    coroutineScope.launch {
                        getPictureFromWS(pictureId)
                    }
                }
            }
            val existingFriend = friendsData.find { it.userData.userId == id }
            if (existingFriend == null) {
                friendsData.add(FriendData(UserData(id, nickname, pictureId), null))
            }
        }
    }
    private fun parsePictureData(context: Context, jsonString: String){
        val result = JSONObject(jsonString).getJSONArray("result")
        for (i in 0 until result.length()) {
            val friendObject = result.getJSONObject(i)
            val pictureId = friendObject.getInt("picture_id")
            val pictureDataString = friendObject.getString("picture")
            val existingPicture = picsData.find { it.pictureId == pictureId }
            if (existingPicture == null) {
                val pictureData: ByteArray = Base64.getDecoder().decode(pictureDataString)

                val imagePath = saveImageToCache(context, pictureId.toString(), pictureData)

                val newPictureData = PictureData(pictureId, imagePath)
                val updatedPicturesData = picsData.toMutableList().apply {
                    add(newPictureData)
                }
                picsData = updatedPicturesData

                Log.d("Tagme_custom_log_picID", pictureId.toString())
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
                existingFriend.location?.latitude = latitude
                existingFriend.location?.longitude = longitude
                existingFriend.location?.accuracy = accuracy
                existingFriend.location?.speed = speed.toFloat()
                existingFriend.location?.timestamp = Timestamp(dateFormat.parse(timestamp).time)
            }
        }
    }
    private fun parseMyData(jsonString: String){
        val result = JSONObject(jsonString).getJSONArray("result")

        for (i in 0 until result.length()) {
            val locationObject = result.getJSONObject(i)
            val userId = locationObject.getInt("user_id")
            val picId = locationObject.getInt("picture_id")
            myUserId = userId
            myPfpId = picId
        }
    }
    private fun parseConversationsData(jsonString: String){
        val result = JSONObject(jsonString).getJSONArray("result")
        for (i in 0 until result.length()) {
            val conversationObject = result.getJSONObject(i)
            val user_id = conversationObject.getInt("user_id")
            val conversation_id = conversationObject.getInt("conversation_id")
            val nickname = conversationObject.getString("nickname")
            val picture_id = conversationObject.optInt("picture_id", 0)

            val existingConversation = conversationsData.find { it.conversationID == conversation_id }
            if (existingConversation == null) {
                conversationsData.add(ConversationData(conversation_id,
                    UserData(user_id, nickname,
                        picture_id), mutableListOf()
                ))
            }
        }
    }
    private fun parseMessagesData(jsonString: String){
        val result = JSONObject(jsonString).getJSONArray("result")
        for (i in 0 until result.length()) {
            val messageObject = result.getJSONObject(i)
            val messageId = messageObject.getInt("id")
            val authorId = messageObject.getInt("author_id")
            val text = messageObject.getString("text")
            val pictureId = messageObject.optInt("picture_id", 0)
            val timestamp = messageObject.getString("timestamp")

            val existingConversation = conversationsData.find { it.userData.userId == authorId }
            val existingMessage = existingConversation?.messages?.find{it.messageId == messageId}
            if (existingConversation != null) {
                if (existingMessage == null) {
                    existingConversation.messages.add(
                        MessageData(
                            messageId,
                            authorId,
                            text,
                            pictureId,
                            Timestamp(dateFormat.parse(timestamp).time)
                        )
                    )
                }
            }

        }
    }
    private fun parseFriendRequestData(jsonString: String) {
        val result = JSONObject(jsonString).getJSONArray("result")
        val userIdsInResult = HashSet<Int>() // Store user ids present in the result

        // Parse result JSON and update or add FriendRequestData
        for (i in 0 until result.length()) {
            val requestObject = result.getJSONObject(i)
            val id = requestObject.getInt("user_id")
            val nickname = requestObject.getString("nickname")
            val relation = requestObject.getString("relation")
            val pictureId = requestObject.optInt("picture_id", 0)

            userIdsInResult.add(id)
            if (pictureId != 0) {
                val existingPicture = picsData.find { it.pictureId == pictureId }
                if ((existingPicture == null) or (existingPicture?.imagePath == null)) {
                    coroutineScope.launch {
                        getPictureFromWS(pictureId)
                    }
                }
            }
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
    fun getFriendRequestData(): MutableList<FriendRequestData> {
        return friendsRequestsData
    }
    fun getConversationsData(): MutableList<ConversationData> {
        return conversationsData
    }
    fun getConversationData(id: Int): ConversationData? {
        return conversationsData.find{it.conversationID == id}
    }
    fun getPicturesData(): List<PictureData> {
        return picsData
    }
    fun getPictureData(context: Context, pictureId: Int): Bitmap? {
        val picture = picsData.find { it.pictureId == pictureId }
        return if (picture != null) {
            BitmapFactory.decodeFile(picture.imagePath)
        } else {
            null
        }
    }

    data class LocationData(
        var latitude: Double,
        var longitude: Double,
        var accuracy: Double,
        var speed: Float,
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
        val location: LocationData?,
    )

    data class FriendRequestData(
        val userData: UserData,
        var relation: String
    )
    data class ConversationData(
        val conversationID: Int,
        val userData: UserData,
        var messages: MutableList<MessageData>
    )
    data class MessageData(
        val messageId: Int,
        val authorId: Int,
        var text: String?,
        val imageId: Int?,
        val timestamp: Timestamp
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
}
