package tagme
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.sql.Timestamp

class API private constructor(context: Context){
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("API_PREFS", Context.MODE_PRIVATE)

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var answerReceived = false
    private var answer = JSONObject()
    var token: String?
        get() = sharedPreferences.getString("TOKEN", null)
        set(value) {
            sharedPreferences.edit().putString("TOKEN", value).apply()
        }
    var myNickname: String?
        get() = sharedPreferences.getString("NICKNAME", null)
        set(value) {
            sharedPreferences.edit().putString("NICKNAME", value).apply()
        }
    private val friendsData = mutableListOf<FriendData>()
    private val friendsRequestsData = mutableListOf<FriendRequestData>()
    private val conversationsData = mutableListOf<ConversationData>()

    suspend fun connectToServer(): Boolean {
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
                    answerReceived = true
                    val jsonObject = JSONObject(text)
                    answer = jsonObject
                    when (answer.getString("action")) {
                        "login", "register" -> when (answer.getString("status")) {
                            "success" -> {
                                token = answer.getString("message")
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
                    }

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
                put("token", token)
            }
            webSocket?.send(requestData.toString())

            waitForServerAnswer()
        }
    }
    suspend fun sendLocation(latitude: String, longitude: String, accuracy: String, speed: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "send location")
                put("token", token)
                put("latitude", latitude)
                put("longitude", longitude)
                put("accuracy", accuracy)
                put("speed", speed)
            }

            webSocket?.send(requestData.toString())

            waitForServerAnswer()
        }
    }
    suspend fun getLocations(): JSONObject? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "get locations")
                put("token", token)
            }

            webSocket?.send(requestData.toString())

            waitForServerAnswer()
        }
    }
    suspend fun getFriends(): JSONObject? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "get friends")
                put("token", token)
            }

            webSocket?.send(requestData.toString())

            waitForServerAnswer()
        }
    }
    suspend fun getConversations(): JSONObject? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "get conversations")
                put("token", token)
            }

            webSocket?.send(requestData.toString())

            waitForServerAnswer()
        }
    }
    suspend fun sendFriendRequest(username: String): JSONObject? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "add friend")
                put("token", token)
                put("nickname", username)
            }

            webSocket?.send(requestData.toString())

            waitForServerAnswer()
        }
    }
    suspend fun getFriendRequests(): JSONObject? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "get friend requests")
                put("token", token)
            }

            webSocket?.send(requestData.toString())

            waitForServerAnswer()
        }
    }
    suspend fun acceptFriendRequest(id: Int): JSONObject? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "accept request")
                put("token", token)
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
                put("token", token)
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
                put("token", token)
                put("user2_id", id)
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
            val existingFriend = friendsData.find { it.userData.userId == id }
            if (existingFriend == null) {
                friendsData.add(FriendData(UserData(id, nickname, PictureData(pictureId, null)), null))
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
            //val timestamp = friendObject.get("timestamp")

            val existingFriend = friendsData.find { it.userData.userId == id }
            if (existingFriend != null) {
                existingFriend.location = LocationData(latitude,longitude,accuracy,speed.toFloat(), null)
            }
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
                        PictureData(picture_id, null)), mutableListOf()
                ))
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

            val existingFriendRequest = friendsRequestsData.find { it.userData.userId == id }
            if (existingFriendRequest != null) {
                existingFriendRequest.userData = UserData(id, nickname, PictureData(pictureId, null))
                existingFriendRequest.relation = relation
            } else {
                friendsRequestsData.add(FriendRequestData(UserData(id, nickname, PictureData(pictureId, null)), relation))
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
        var profilePicture: PictureData
    )
    data class PictureData(
        val pictureId: Int,
        var pfpData: ByteArray?
    )

    data class FriendData(
        val userData: UserData,
        var location: LocationData?,
    )

    data class FriendRequestData(
        var userData: UserData,
        var relation: String
    )
    data class ConversationData(
        val conversationID: Int,
        var userData: UserData,
        var messages: MutableList<MessageData>
    )
    data class MessageData(
        val text: String?,
        val image: ByteArray?,
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
}
