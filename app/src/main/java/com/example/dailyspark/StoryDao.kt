package com.example.dailyspark

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryDao {
    @Insert
    suspend fun insert(story: Story)

    @Query("SELECT * FROM stories ORDER BY createdAtMillis DESC")
    fun observeStories(): Flow<List<Story>>
}
