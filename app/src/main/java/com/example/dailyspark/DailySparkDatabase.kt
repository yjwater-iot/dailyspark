package com.example.dailyspark

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Story::class], version = 3, exportSchema = false)
abstract class DailySparkDatabase : RoomDatabase() {
    abstract fun storyDao(): StoryDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stories ADD COLUMN cleanedObservation TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE stories ADD COLUMN followUpQuestion TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE stories ADD COLUMN storySeed TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE stories ADD COLUMN generatedStory TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var instance: DailySparkDatabase? = null

        fun getInstance(context: Context): DailySparkDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DailySparkDatabase::class.java,
                    "daily_spark.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { instance = it }
            }
    }
}
