package com.tagme.domain.usecases

import android.util.Log
import com.tagme.data.API
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ConnectToWebsocketUseCase @Inject constructor(
    private val api: API
) {
    suspend fun connect(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("tagme", "connecting")
                api.connectToServer().await()
            } catch (e: Exception) {
                false
            }
        }
    }
}
