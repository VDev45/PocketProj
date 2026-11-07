package com.vishwaravi.pocketproj.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "links",
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"]
        )
    ],
    indices = [Index("collectionId"), Index("url")]
)
data class LinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val domain: String = "",
    val collectionId: Long = 0,
    val tags: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    /** Explicit hidden state, separate from hidden-tag configuration. See REQUIREMENTS §6. */
    val isHidden: Boolean = false,
    /** Lifecycle of the background metadata fetch. */
    val metadataState: MetadataState = MetadataState.PENDING,
    /** Number of metadata fetch attempts so far (drives worker retry limits). */
    val metadataAttempts: Int = 0
)