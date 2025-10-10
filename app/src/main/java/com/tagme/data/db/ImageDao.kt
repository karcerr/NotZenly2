package com.tagme.data.db

import androidx.room.*
import com.tagme.domain.models.db.ImageEntity

@Dao
interface ImageDao {
    @Upsert
    suspend fun addImage(image: ImageEntity)

    @Query("DELETE FROM images WHERE id = :imageId")
    suspend fun deleteImageById(imageId: Int)

    @Query("SELECT * FROM images WHERE userId = :userId")
    suspend fun getImagesForUser(userId: Int): List<ImageEntity>

    @Query("DELETE FROM images")
    suspend fun clearImages()

    @Query("SELECT * FROM images")
    suspend fun getAllImages(): List<ImageEntity>
}