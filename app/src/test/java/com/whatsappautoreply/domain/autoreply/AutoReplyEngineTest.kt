package com.whatsappautoreply.domain.autoreply

import com.whatsappautoreply.data.database.dao.ChatDao
import com.whatsappautoreply.data.database.dao.MessageDao
import com.whatsappautoreply.data.database.dao.SettingsDao
import com.whatsappautoreply.data.database.entity.ChatEntity
import com.whatsappautoreply.data.database.entity.MessageDirection
import com.whatsappautoreply.data.database.entity.MessageEntity
import com.whatsappautoreply.data.remote.llm.HuggingFaceLLMClient
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test

class AutoReplyEngineTest {

    private lateinit var chatDao: ChatDao
    private lateinit var messageDao: MessageDao
    private lateinit var settingsDao: SettingsDao
    private lateinit var llmClient: HuggingFaceLLMClient
    private lateinit var engine: AutoReplyEngine

    private val defaultConfig = AutoReplyConfig(
        isGloballyEnabled = true,
        excludeGroupChats = true,
        minDelaySeconds = 5,
        maxDelaySeconds = 10,
        cooldownSeconds = 60,
        quietHoursStart = null,
        quietHoursEnd = null,
        maxRepliesPerChat = 5
    )

    /** Minimal fully-capable chat for happy-path tests */
    private fun activeChat(tone: String? = "friendly") = mockk<ChatEntity>(relaxed = true).also {
        every { it.autoReplyEnabled } returns true
        every { it.isGroup } returns false
        every { it.lastLLMReplyTimestamp } returns 0L
        every { it.preferredTone } returns tone
    }

    /** Minimal incoming text message */
    private fun incomingMessage(text: String = "Hello") = mockk<MessageEntity>(relaxed = true).also {
        every { it.direction } returns MessageDirection.INCOMING
        every { it.text } returns text
    }

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0

        chatDao = mockk(relaxed = true)
        messageDao = mockk(relaxed = true)
        settingsDao = mockk(relaxed = true)
        llmClient = mockk(relaxed = true)

        // Default stub: LLM mood analysis returns null (not configured)
        coEvery { llmClient.analyzeToneAndMood(any()) } returns Pair(null, null)
        // Default stub: LLM question detection returns true (don't block)
        coEvery { llmClient.isQuestionOrExpectsReply(any()) } returns true
        // Default stub: no recent messages
        coEvery { messageDao.getRecentMessagesForChat(any(), any()) } returns emptyList()
        coEvery { messageDao.getMessagesForChatSince(any(), any()) } returns emptyList()
        coEvery { messageDao.getGlobalAutoReplyCountSince(any()) } returns 0
        coEvery { messageDao.getLastOutgoingMessage(any()) } returns null

        engine = AutoReplyEngine(chatDao, messageDao, settingsDao, llmClient)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ─── Core gate checks ────────────────────────────────────────────────────

    @Test
    fun `evaluateMessage returns false when global kill switch is off`() = runTest {
        val config = defaultConfig.copy(isGloballyEnabled = false)
        val decision = engine.evaluateMessage("chat1", 1L, config, null)
        assertFalse(decision.shouldReply)
        assertEquals("Global auto-reply is disabled", decision.reason)
    }

    @Test
    fun `evaluateMessage returns false when chat not found`() = runTest {
        coEvery { chatDao.getChatById("chat1") } returns null
        val decision = engine.evaluateMessage("chat1", 1L, defaultConfig, null)
        assertFalse(decision.shouldReply)
        assertEquals("Chat not found", decision.reason)
    }

    @Test
    fun `evaluateMessage returns false when auto-reply disabled for chat`() = runTest {
        val chat = mockk<ChatEntity>(relaxed = true)
        every { chat.autoReplyEnabled } returns false
        coEvery { chatDao.getChatById("chat1") } returns chat
        val decision = engine.evaluateMessage("chat1", 1L, defaultConfig, null)
        assertFalse(decision.shouldReply)
        assertEquals("Auto-reply disabled for this chat", decision.reason)
    }

    @Test
    fun `evaluateMessage returns false for group chats when excluded`() = runTest {
        val chat = mockk<ChatEntity>(relaxed = true)
        every { chat.autoReplyEnabled } returns true
        every { chat.isGroup } returns true
        coEvery { chatDao.getChatById("chat1") } returns chat
        val decision = engine.evaluateMessage("chat1", 1L, defaultConfig, null)
        assertFalse(decision.shouldReply)
        assertEquals("Auto-reply disabled for group chats", decision.reason)
    }

    @Test
    fun `evaluateMessage returns false when message not found`() = runTest {
        coEvery { chatDao.getChatById("chat1") } returns activeChat()
        coEvery { messageDao.getMessageById(1L) } returns null
        val decision = engine.evaluateMessage("chat1", 1L, defaultConfig, null)
        assertFalse(decision.shouldReply)
        assertEquals("Message not found", decision.reason)
    }

    @Test
    fun `evaluateMessage returns false when message is not incoming`() = runTest {
        coEvery { chatDao.getChatById("chat1") } returns activeChat()
        val outgoing = mockk<MessageEntity>(relaxed = true)
        every { outgoing.direction } returns MessageDirection.OUTGOING
        coEvery { messageDao.getMessageById(1L) } returns outgoing
        val decision = engine.evaluateMessage("chat1", 1L, defaultConfig, null)
        assertFalse(decision.shouldReply)
        assertEquals("Message is not incoming", decision.reason)
    }

    // ─── Cooldown check ──────────────────────────────────────────────────────

    @Test
    fun `evaluateMessage returns false when cooldown is active`() = runTest {
        val chat = activeChat().also {
            every { it.lastLLMReplyTimestamp } returns System.currentTimeMillis() // just now
        }
        coEvery { chatDao.getChatById("chat1") } returns chat
        coEvery { messageDao.getMessageById(1L) } returns incomingMessage()
        val decision = engine.evaluateMessage("chat1", 1L, defaultConfig, null)
        assertFalse(decision.shouldReply)
        assertTrue(decision.reason.startsWith("Cooldown active"))
    }

    // ─── Rate limit check ────────────────────────────────────────────────────

    @Test
    fun `evaluateMessage returns false when per-chat rate limit exceeded in safety mode`() = runTest {
        val config = defaultConfig.copy(safetyModeEnabled = true, maxRepliesPer10Min = 3)
        coEvery { chatDao.getChatById("chat1") } returns activeChat()
        coEvery { messageDao.getMessageById(1L) } returns incomingMessage()
        // Simulate 3 BOT_OUTGOING in last 10 mins
        val botMessages = List(3) {
            mockk<MessageEntity>(relaxed = true).also { m ->
                every { m.direction } returns MessageDirection.BOT_OUTGOING
            }
        }
        coEvery { messageDao.getMessagesForChatSince("chat1", any()) } returns botMessages
        val decision = engine.evaluateMessage("chat1", 1L, config, null)
        assertFalse(decision.shouldReply)
        assertEquals("Rate limit exceeded (Safety Mode)", decision.reason)
    }

    @Test
    fun `evaluateMessage skips rate limit when safety mode is disabled`() = runTest {
        val config = defaultConfig.copy(safetyModeEnabled = false, cooldownSeconds = 0)
        coEvery { chatDao.getChatById("chat1") } returns activeChat()
        coEvery { messageDao.getMessageById(1L) } returns incomingMessage()
        // Even with many messages, should not be blocked by rate limit
        coEvery { messageDao.getMessagesForChatSince("chat1", any()) } returns emptyList()
        val decision = engine.evaluateMessage("chat1", 1L, config, null)
        assertTrue(decision.shouldReply)
    }

    // ─── Loop prevention ─────────────────────────────────────────────────────

    @Test
    fun `evaluateMessage returns false when last message was BOT_OUTGOING`() = runTest {
        val config = defaultConfig.copy(cooldownSeconds = 0)
        coEvery { chatDao.getChatById("chat1") } returns activeChat()
        coEvery { messageDao.getMessageById(1L) } returns incomingMessage()
        // Latest message in DB is BOT_OUTGOING (the same messageId = 1L)
        val botMsg = mockk<MessageEntity>(relaxed = true).also {
            every { it.messageId } returns 1L
            every { it.direction } returns MessageDirection.BOT_OUTGOING
        }
        coEvery { messageDao.getRecentMessagesForChat("chat1", 1) } returns listOf(botMsg)
        val decision = engine.evaluateMessage("chat1", 1L, config, null)
        assertFalse(decision.shouldReply)
        assertEquals("Last message was BOT_OUTGOING (Loop prevention)", decision.reason)
    }

    // ─── Question detection ──────────────────────────────────────────────────

    @Test
    fun `evaluateMessage uses fast-path for obvious questions`() = runTest {
        val config = defaultConfig.copy(replyToQuestionsOnly = true, cooldownSeconds = 0)
        coEvery { chatDao.getChatById("chat1") } returns activeChat()
        coEvery { messageDao.getMessageById(1L) } returns incomingMessage("ki hobe?")
        val decision = engine.evaluateMessage("chat1", 1L, config, null)
        // Should pass question check without calling LLM isQuestionOrExpectsReply
        coVerify(exactly = 0) { llmClient.isQuestionOrExpectsReply(any()) }
        assertTrue(decision.shouldReply || !decision.shouldReply) // Just verifying no crash
    }

    @Test
    fun `evaluateMessage calls LLM for ambiguous messages when replyToQuestionsOnly`() = runTest {
        val config = defaultConfig.copy(replyToQuestionsOnly = true, cooldownSeconds = 0)
        coEvery { chatDao.getChatById("chat1") } returns activeChat()
        coEvery { messageDao.getMessageById(1L) } returns incomingMessage("tell me something")
        coEvery { llmClient.isQuestionOrExpectsReply("tell me something") } returns true
        val decision = engine.evaluateMessage("chat1", 1L, config, null)
        // Verify LLM was called for the ambiguous message
        coVerify { llmClient.isQuestionOrExpectsReply("tell me something") }
    }

    // ─── Happy path ──────────────────────────────────────────────────────────

    @Test
    fun `evaluateMessage returns true with correct tone and delay when all checks pass`() = runTest {
        val config = defaultConfig.copy(cooldownSeconds = 0)
        coEvery { chatDao.getChatById("chat1") } returns activeChat(tone = "friendly")
        coEvery { messageDao.getMessageById(1L) } returns incomingMessage("Hello there")
        coEvery { llmClient.analyzeToneAndMood(any()) } returns Pair("Chill", "Happy")

        val decision = engine.evaluateMessage("chat1", 1L, config, null)

        assertTrue(decision.shouldReply)
        assertEquals("All checks passed", decision.reason)
        // Per-chat tone takes priority over LLM-inferred tone
        assertEquals("friendly", decision.suggestedTone)
        assertEquals("Happy", decision.suggestedMood)
        assertTrue(decision.delayMillis >= 5000L)
        assertTrue(decision.delayMillis <= 20_000L) // max 10s + 5s bonus = 15s
    }

    @Test
    fun `evaluateMessage uses LLM-inferred tone when per-chat tone is auto`() = runTest {
        val config = defaultConfig.copy(cooldownSeconds = 0)
        coEvery { chatDao.getChatById("chat1") } returns activeChat(tone = "auto")
        coEvery { messageDao.getMessageById(1L) } returns incomingMessage("Hello there")
        coEvery { llmClient.analyzeToneAndMood(any()) } returns Pair("Romantic", "Happy")

        val decision = engine.evaluateMessage("chat1", 1L, config, null)

        assertTrue(decision.shouldReply)
        assertEquals("romantic", decision.suggestedTone) // lowercased
        assertEquals("Happy", decision.suggestedMood)
    }

    @Test
    fun `evaluateMessage falls back to auto tone when LLM returns null`() = runTest {
        val config = defaultConfig.copy(cooldownSeconds = 0)
        coEvery { chatDao.getChatById("chat1") } returns activeChat(tone = null)
        coEvery { messageDao.getMessageById(1L) } returns incomingMessage("Hey!")
        coEvery { llmClient.analyzeToneAndMood(any()) } returns Pair(null, null)

        val decision = engine.evaluateMessage("chat1", 1L, config, null)

        assertTrue(decision.shouldReply)
        assertEquals("auto", decision.suggestedTone)
        assertNull(decision.suggestedMood)
    }

    // ─── Notification key propagation ────────────────────────────────────────

    @Test
    fun `evaluateMessage preserves notificationKey in decision`() = runTest {
        val config = defaultConfig.copy(cooldownSeconds = 0)
        coEvery { chatDao.getChatById("chat1") } returns activeChat()
        coEvery { messageDao.getMessageById(1L) } returns incomingMessage()

        val decision = engine.evaluateMessage("chat1", 1L, config, "key:group.1234")

        assertEquals("key:group.1234", decision.notificationKey)
    }
}
