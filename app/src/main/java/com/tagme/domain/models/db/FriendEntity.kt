package com.tagme.domain.models.db

import androidx.room.*
import com.tagme.domain.models.FriendData
import com.tagme.domain.models.LocationData
import com.tagme.domain.models.UserData
import java.sql.Timestamp
import java.util.*

@Entity(tableName = "friend")
data class FriendEntity(
    @PrimaryKey val id: Int,
    val nickname: String,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Date = Date()
)

fun FriendEntity.toFriendData(): FriendData = FriendData(
    userData = UserData(
        userId = this.id,
        nickname = nickname,
    ),
    location = LocationData(
        latitude = this.latitude,
        longitude = this.longitude,
        accuracy = 0.0,
        speed = 0f,
        timestamp = Timestamp(this.createdAt.time)
    )
)