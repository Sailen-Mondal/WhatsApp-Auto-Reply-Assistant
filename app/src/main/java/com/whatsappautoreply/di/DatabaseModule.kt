package com.whatsappautoreply.di

import android.content.Context
import androidx.room.Room
import com.whatsappautoreply.data.database.WhatsAppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WhatsAppDatabase {
        return Room.databaseBuilder(
            context,
            WhatsAppDatabase::class.java,
            "whatsapp_autoreply.db"
        )
            .fallbackToDestructiveMigration() // For development - remove in production
            .build()
    }

    @Provides
    fun provideChatDao(database: WhatsAppDatabase) = database.chatDao()

    @Provides
    fun provideMessageDao(database: WhatsAppDatabase) = database.messageDao()

    @Provides
    fun provideMediaMetaDao(database: WhatsAppDatabase) = database.mediaMetaDao()

    @Provides
    fun provideSettingsDao(database: WhatsAppDatabase) = database.settingsDao()

    @Provides
    fun provideLLMLogDao(database: WhatsAppDatabase) = database.llmLogDao()
}

