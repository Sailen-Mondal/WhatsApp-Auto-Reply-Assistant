package com.whatsappautoreply.di

import android.content.Context
import androidx.work.WorkManager
import com.whatsappautoreply.data.notification.NotificationReplySender
import com.whatsappautoreply.data.notification.NotificationStore
import com.whatsappautoreply.domain.autoreply.AutoReplyEngine
import com.whatsappautoreply.domain.autoreply.DelayScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for  auto-reply components
 */
@Module
@InstallIn(SingletonComponent::class)
object AutoReplyModule {

    @Provides
    @Singleton
    fun provideNotificationStore(): NotificationStore {
        return NotificationStore()
    }

    @Provides
    @Singleton
    fun provideNotificationReplySender(
        @ApplicationContext context: Context,
        notificationStore: NotificationStore
    ): NotificationReplySender {
        return NotificationReplySender(context, notificationStore)
    }

    @Provides
    @Singleton
    fun provideDelayScheduler(
        @ApplicationContext context: Context
    ): DelayScheduler {
        return DelayScheduler(context)
    }

    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context
    ): WorkManager {
        return WorkManager.getInstance(context)
    }
}
