package com.example.dailyspark

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Story::class], version = 1, exportSchema = false)
abstract class DailySparkDatabase : RoomDatabase() {
    abstract fun storyDao(): StoryDao

    companion object {
        @Volatile
        private var instance: DailySparkDatabase? = null

        fun getInstance(context: Context): DailySparkDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DailySparkDatabase::class.java,
                    "daily_spark.db"
                ).build().also { instance = it }
            }
    }
}
