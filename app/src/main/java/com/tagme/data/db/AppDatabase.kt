package com.tagme.data.db

import androidx.room.*
import android.content.Context
import androidx.room.TypeConverters
import com.tagme.domain.models.db.FriendEntity
import com.tagme.domain.models.db.ImageEntity
import java.util.*

@Database(entities = [FriendEntity::class, ImageEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun friendDao(): FriendDao
    abstract fun friendImageDao(): ImageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "friends_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromDate(value: Date): Long = value.time

    @TypeConverter
    fun toDate(value: Long): Date = Date(value)
}