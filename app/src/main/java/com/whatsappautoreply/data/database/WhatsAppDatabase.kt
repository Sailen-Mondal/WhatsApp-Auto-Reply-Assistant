package com.whatsappautoreply.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.whatsappautoreply.data.database.dao.*
import com.whatsappautoreply.data.database.entity.*

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        MediaMetaEntity::class,
        SettingsEntity::class,
        LLMLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class WhatsAppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun mediaMetaDao(): MediaMetaDao
    abstract fun settingsDao(): SettingsDao
    abstract fun llmLogDao(): LLMLogDao
}

