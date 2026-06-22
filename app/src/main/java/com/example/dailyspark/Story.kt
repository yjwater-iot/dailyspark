package com.example.dailyspark

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stories")
data class Story(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transcript: String,
    val cleanedObservation: String,
    val followUpQuestion: String,
    val storySeed: String,
    val createdAtMillis: Long
)
