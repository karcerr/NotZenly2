package com.example.notzenly
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject

class API {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    suspend fun connectToServer(): Boolean {
        return withContext(Dispatchers.IO) {
            val url = "ws://141.8.193.201:8765"  // WebSocket server URL

            val request = Request.Builder()
                .url(url)
                .build()

            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    // Connection opened
                    this@API.webSocket = webSocket
                    Log.d("Tagme_custom_log", "onOpen trigger: $response")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    // Connection failed
                    // Handle failure here
                    Log.d("Tagme_custom_log", "onFailure trigger: $response")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    // Handle response from the server
                    // You may want to parse the JSON response here
                    println("Received message from server: $text")
                    Log.d("Tagme_custom_log", "onMessage trigger: $text")
                }
            }

            client.newWebSocket(request, listener)

            // This line just to indicate that the function returns a result.
            true
        }
    }

    suspend fun registerUser(username: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                put("action", "register")
                put("username", username)
                put("password", password)
            }

            webSocket?.send(requestData.toString())

            // This line just to indicate that the function returns a result.
            true
        }
    }

    suspend fun loginUser(username: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            val requestData = JSONObject().apply {
                //put("action", "login")
                put("username", username)
                put("password", password)
            }

            webSocket?.send(requestData.toString())

            // This line just to indicate that the function returns a result.
            true
        }
    }

    // Add more functions for other endpoints/actions as needed
}
