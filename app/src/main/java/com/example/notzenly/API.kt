import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject

class API {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var token: String? = null
    private var tokenReceived = false

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
                    val jsonObject = JSONObject(text)
                    when (jsonObject.getString("action")) {
                        "login" -> {
                            when (jsonObject.getString("status")) {
                                "success" -> {
                                    token = jsonObject.getString("message")
                                    tokenReceived = true
                                    synchronized(this@API) {
                                        (this@API as Object).notify()
                                    }
                                }
                            }
                        }
                        "register" -> {
                            when (jsonObject.getString("status")) {
                                "success" -> {
                                    token = jsonObject.getString("message")
                                    tokenReceived = true
                                    // Notify waiting coroutine about the token received
                                    synchronized(this@API) {
                                        (this@API as Object).notify()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            client.newWebSocket(request, listener)

            true
        }
    }

    suspend fun registerUser(username: String, password: String): String? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "register")
                put("username", username)
                put("password", password)
            }

            webSocket?.send(requestData.toString())

            waitForToken()
        }
    }

    suspend fun loginUser(username: String, password: String): String? {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "login")
                put("username", username)
                put("password", password)
            }

            webSocket?.send(requestData.toString())

            waitForToken()
        }
    }

    private suspend fun waitForToken(): String? {
        return synchronized(this) {
            val timeoutDuration = 5000 // 5 seconds
            val startTime = System.currentTimeMillis()

            while (!tokenReceived) {
                val elapsedTime = System.currentTimeMillis() - startTime
                if (elapsedTime >= timeoutDuration) {
                    return@waitForToken null
                }
                (this as Object).wait(100)
            }
            tokenReceived = false
            token
        }
    }
}
