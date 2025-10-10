package com.tagme.data.db

import androidx.room.*
import com.tagme.domain.models.db.FriendEntity

@Dao
interface FriendDao {
    @Upsert
    suspend fun upsertFriend(friend: FriendEntity)

    @Query("DELETE FROM friend WHERE id = :friendId")
    suspend fun deleteFriendById(friendId: Int)

    @Query("DELETE FROM friend WHERE id NOT IN (:ids)")
    suspend fun deleteFriendsNotIn(ids: List<Int>)

    @Query("SELECT * FROM friend")
    suspend fun getAllFriends(): List<FriendEntity>
}