package com.tagme.domain.repositories

import android.graphics.Bitmap
import com.tagme.data.API
import javax.inject.Inject

class NotificationRepository @Inject constructor(
    private val api: API
) {
    suspend fun getPictureData(picId: Int): Bitmap? {
        return api.getPictureData(picId)
    }
}