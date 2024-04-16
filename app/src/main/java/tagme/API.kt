package tagme
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject

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
    private val friendsData = mutableListOf<FriendData>()

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
                            "success" -> token = answer.getString("message")
                        }
                        "get locations" -> when (answer.getString("status")) {
                            "success" -> parseFriendsData(answer.getString("message"))
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

    private suspend fun waitForServerAnswer(): JSONObject? {
        return synchronized(this) {
            val timeoutDuration = 1000 //
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

            val latitude = friendObject.getDouble("latitude")
            val longitude = friendObject.getDouble("longitude")
            val accuracy = friendObject.getDouble("accuracy")
            val speed = friendObject.getDouble("speed")
            //val timestamp = friendObject.get("timestamp")

            val existingFriend = friendsData.find { it.nickname == nickname }
            if (existingFriend != null) {
                existingFriend.latitude = latitude
                existingFriend.longitude = longitude
                existingFriend.accuracy = accuracy
                existingFriend.speed = speed.toFloat()
            } else {
                friendsData.add(FriendData(id, nickname, null, latitude, longitude, accuracy, speed.toFloat()))
            }
        }
    }
    fun getFriendsData(): MutableList<FriendData> {
        return friendsData
    }
    data class FriendData(
        val userId: Int,
        var nickname: String,

        var profilePicture: ByteArray?,
        var latitude: Double,
        var longitude: Double,
        var accuracy: Double,
        var speed: Float
        // var timestamp: Timestamp
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
