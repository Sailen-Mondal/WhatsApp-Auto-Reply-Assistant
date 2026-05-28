package com.whatsappautoreply.data.database.dao

import androidx.room.*
import com.whatsappautoreply.data.database.entity.MediaMetaEntity

@Dao
interface MediaMetaDao {
    @Query("SELECT * FROM media_metadata WHERE messageId = :messageId")
    suspend fun getMediaMetaByMessageId(messageId: Long): MediaMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaMeta(mediaMeta: MediaMetaEntity)

    @Delete
    suspend fun deleteMediaMeta(mediaMeta: MediaMetaEntity)
}

