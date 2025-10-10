package com.tagme.domain.models.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tagme.domain.models.PictureData
import java.util.Date

@Entity(
    tableName = "images",
    indices = [Index("userId")]
)
data class ImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val uri: String,
    val createdAt: Date = Date()
)

fun ImageEntity.toPictureData(): PictureData = PictureData(
    pictureId = this.id,
    imagePath = this.uri,
    userId = this.userId,
)