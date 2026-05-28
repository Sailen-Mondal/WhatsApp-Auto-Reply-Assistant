package com.whatsappautoreply.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_metadata",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["messageId"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["messageId"])]
)
data class MediaMetaEntity(
    @PrimaryKey(autoGenerate = true)
    val mediaId: Long = 0L,
    val messageId: Long,
    val mediaType: MediaType,
    val thumbnailBase64: String? = null,
    val extraInfoJson: String? = null // For resolution, duration, etc.
)

