package com.migspod.data

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "podcasts")
data class Podcast(
    @PrimaryKey val feedUrl: String,
    val title: String,
    val author: String = "",
    val description: String = "",
    val artworkUrl: String = "",
    val lastRefreshed: Long = 0L,
    // HTTP validators from the last full fetch, so unchanged feeds come back as a 304.
    val feedEtag: String? = null,
    val feedLastModified: String? = null,
)

@Immutable
@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = Podcast::class,
            parentColumns = ["feedUrl"],
            childColumns = ["podcastFeedUrl"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("podcastFeedUrl"), Index("downloadPath"), Index("publishedDate")]
)
data class Episode(
    @PrimaryKey val id: String, // guid from RSS or generated
    val podcastFeedUrl: String,
    val title: String,
    val description: String = "",
    val audioUrl: String = "",
    val publishedDate: Long = 0L,
    val durationMs: Long = 0L,
    val playbackPositionMs: Long = 0L,
    val isCompleted: Boolean = false,
    val downloadPath: String? = null,
)
