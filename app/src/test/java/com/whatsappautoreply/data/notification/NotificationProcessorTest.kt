package com.whatsappautoreply.data.notification

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.whatsappautoreply.data.database.dao.ChatDao
import com.whatsappautoreply.data.database.dao.MessageDao
import com.whatsappautoreply.data.database.entity.ChatEntity
import com.whatsappautoreply.data.database.entity.MessageDirection
import com.whatsappautoreply.data.database.entity.MessageEntity
import com.whatsappautoreply.data.database.entity.MediaType
import com.whatsappautoreply.data.notification.NotificationStore
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

import com.whatsappautoreply.data.database.dao.SettingsDao

class NotificationProcessorTest {

    private lateinit var chatDao: ChatDao
    private lateinit var messageDao: MessageDao
    private lateinit var settingsDao: SettingsDao
    private lateinit var notificationStore: NotificationStore
    private lateinit var processor: NotificationProcessor
    private lateinit var sbn: StatusBarNotification
    private lateinit var notification: Notification
    private lateinit var extras: Bundle

    @Before
    fun setup() {
        chatDao = mockk(relaxed = true)
        messageDao = mockk(relaxed = true)
        settingsDao = mockk(relaxed = true)
        notificationStore = mockk(relaxed = true)
        processor = NotificationProcessor(chatDao, messageDao, settingsDao, notificationStore)
        
        sbn = mockk()
        notification = Notification()
        extras = mockk()
        notification.extras = extras
        
        every { sbn.notification } returns notification
        every { sbn.packageName } returns "com.whatsapp"
        every { sbn.postTime } returns 1234567890L
        every { sbn.id } returns 1
        every { sbn.key } returns "0|com.whatsapp|1|null|10001"
    }

    @Test
    fun `processNotification creates new chat if not exists`() = runTest {
        // Given
        val title = "John Doe"
        val text = "Hello there"
        
        every { extras.getCharSequence(Notification.EXTRA_TITLE ?: "android.title") } returns title
        every { extras.getCharSequence(Notification.EXTRA_TEXT ?: "android.text") } returns text
        
        // Mock static method for MessagingStyle to return null (force basic processing)
        mockkStatic(androidx.core.app.NotificationCompat.MessagingStyle::class)
        every { androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(any()) } returns null

        coEvery { chatDao.getChatById(any()) } returns null

        // When
        processor.processNotification(sbn)

        // Then
        coVerify { chatDao.insertChat(any()) }
        coVerify { messageDao.insertMessage(any()) }
        
        unmockkStatic(androidx.core.app.NotificationCompat.MessagingStyle::class)
    }

    @Test
    fun `processNotification detects incoming message correctly`() = runTest {
        // Given
        val title = "John Doe"
        val text = "Hello there" // No "You:" prefix
        
        every { extras.getCharSequence(Notification.EXTRA_TITLE ?: "android.title") } returns title
        every { extras.getCharSequence(Notification.EXTRA_TEXT ?: "android.text") } returns text
        
        mockkStatic(androidx.core.app.NotificationCompat.MessagingStyle::class)
        every { androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(any()) } returns null
        
        val messageSlot = slot<MessageEntity>()
        coEvery { messageDao.insertMessage(capture(messageSlot)) } returns 1L

        // When
        processor.processNotification(sbn)

        // Then
        assertEquals(MessageDirection.INCOMING, messageSlot.captured.direction)
        assertEquals("John Doe", messageSlot.captured.senderName)
        assertEquals("Hello there", messageSlot.captured.text)
        
        unmockkStatic(androidx.core.app.NotificationCompat.MessagingStyle::class)
    }

    @Test
    fun `processNotification detects outgoing message correctly`() = runTest {
        // Given
        val title = "John Doe"
        val text = "You: I am fine"
        
        every { extras.getCharSequence(Notification.EXTRA_TITLE ?: "android.title") } returns title
        every { extras.getCharSequence(Notification.EXTRA_TEXT ?: "android.text") } returns text
        
        mockkStatic(androidx.core.app.NotificationCompat.MessagingStyle::class)
        every { androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(any()) } returns null
        
        val messageSlot = slot<MessageEntity>()
        coEvery { messageDao.insertMessage(capture(messageSlot)) } returns 1L

        // When
        processor.processNotification(sbn)

        // Then
        assertEquals(MessageDirection.OUTGOING, messageSlot.captured.direction)
        assertEquals("I am fine", messageSlot.captured.text)
        
        unmockkStatic(androidx.core.app.NotificationCompat.MessagingStyle::class)
    }

    @Test
    fun `processNotification detects media type correctly`() = runTest {
        // Given
        val title = "John Doe"
        val text = "📷 Photo"
        
        every { extras.getCharSequence(Notification.EXTRA_TITLE ?: "android.title") } returns title
        every { extras.getCharSequence(Notification.EXTRA_TEXT ?: "android.text") } returns text
        
        mockkStatic(androidx.core.app.NotificationCompat.MessagingStyle::class)
        every { androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(any()) } returns null
        
        val messageSlot = slot<MessageEntity>()
        coEvery { messageDao.insertMessage(capture(messageSlot)) } returns 1L

        // When
        processor.processNotification(sbn)

        // Then
        assertEquals(MediaType.IMAGE, messageSlot.captured.mediaType)
        
        unmockkStatic(androidx.core.app.NotificationCompat.MessagingStyle::class)
    }
}
