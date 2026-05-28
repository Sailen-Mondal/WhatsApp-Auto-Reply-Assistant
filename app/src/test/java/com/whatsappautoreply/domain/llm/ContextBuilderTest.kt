package com.whatsappautoreply.domain.llm

import com.whatsappautoreply.data.database.entity.MediaType
import com.whatsappautoreply.data.database.entity.MessageDirection
import com.whatsappautoreply.data.database.entity.MessageEntity
import com.whatsappautoreply.domain.autoreply.ContextMessage
import org.junit.Assert.*
import org.junit.Test

class ContextBuilderTest {

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun msg(
        text: String?,
        direction: MessageDirection = MessageDirection.INCOMING,
        timestamp: Long = System.currentTimeMillis(),
        mediaType: MediaType = MediaType.TEXT,
        caption: String? = null
    ) = MessageEntity(
        messageId = 0,
        chatId = "chat1",
        text = text,
        direction = direction,
        timestamp = timestamp,
        mediaType = mediaType,
        mediaCaption = caption,
        source = com.whatsappautoreply.data.database.entity.MessageSource.NOTIFICATION
    )

    private fun contextMsg(text: String?, incoming: Boolean) =
        ContextMessage(text = text, isIncoming = incoming, timestamp = System.currentTimeMillis())

    // ─── build() tests ───────────────────────────────────────────────────────

    @Test
    fun `build returns empty result for empty list`() {
        val result = ContextBuilder.build(emptyList())
        assertEquals(0, result.messageCount)
        assertTrue(result.historyLines.isEmpty())
        assertEquals("[no text]", result.lastIncomingText)
        assertFalse(result.wasTruncated)
    }

    @Test
    fun `build tags INCOMING as THEM and OUTGOING as ME`() {
        val messages = listOf(
            msg("Hey", MessageDirection.INCOMING),
            msg("Hi!", MessageDirection.OUTGOING)
        )
        val result = ContextBuilder.build(messages)
        assertTrue(result.historyLines.any { it.startsWith("[THEM]") })
        assertTrue(result.historyLines.any { it.startsWith("[ME]") })
    }

    @Test
    fun `build tags BOT_OUTGOING as ME`() {
        val messages = listOf(
            msg("I replied automatically", MessageDirection.BOT_OUTGOING)
        )
        val result = ContextBuilder.build(messages)
        assertTrue(result.historyLines.first().startsWith("[ME]"))
    }

    @Test
    fun `build extracts last incoming message correctly`() {
        val messages = listOf(
            msg("First message", MessageDirection.INCOMING),
            msg("Me replying", MessageDirection.OUTGOING),
            msg("Latest from them", MessageDirection.INCOMING)
        )
        val result = ContextBuilder.build(messages)
        assertEquals("Latest from them", result.lastIncomingText)
    }

    @Test
    fun `build shows media type label when text is null`() {
        val messages = listOf(
            msg(null, MessageDirection.INCOMING, mediaType = MediaType.IMAGE)
        )
        val result = ContextBuilder.build(messages)
        val line = result.historyLines.first()
        assertTrue("Should contain [image], got: $line", line.contains("[image]"))
    }

    @Test
    fun `build uses media caption when text is null but caption exists`() {
        val messages = listOf(
            msg(null, MessageDirection.INCOMING, mediaType = MediaType.IMAGE, caption = "Look at this!")
        )
        val result = ContextBuilder.build(messages)
        assertTrue(result.historyLines.first().contains("Look at this!"))
        assertEquals("Look at this!", result.lastIncomingText)
    }

    @Test
    fun `build truncates when total chars exceed 2000`() {
        val longText = "A".repeat(500)
        // 6 × 500-char messages = 3000 chars, should truncate
        val messages = (1..6).map { msg(longText, MessageDirection.INCOMING, timestamp = it.toLong()) }
        val result = ContextBuilder.build(messages)
        assertTrue(result.wasTruncated)
        assertTrue(result.messageCount < 6)
    }

    @Test
    fun `build respects max 12 messages cap`() {
        val messages = (1..20).map { msg("Msg $it", MessageDirection.INCOMING, timestamp = it.toLong()) }
        val result = ContextBuilder.build(messages)
        assertTrue(result.messageCount <= 12)
    }

    @Test
    fun `build returns wasTruncated false for small messages`() {
        val messages = listOf(
            msg("Hi", MessageDirection.INCOMING),
            msg("Hello", MessageDirection.OUTGOING)
        )
        val result = ContextBuilder.build(messages)
        assertFalse(result.wasTruncated)
    }

    @Test
    fun `build contextSnippet joins lines with newline`() {
        val messages = listOf(
            msg("Line1", MessageDirection.INCOMING),
            msg("Line2", MessageDirection.OUTGOING)
        )
        val result = ContextBuilder.build(messages)
        assertTrue(result.contextSnippet.contains("\n"))
    }

    // ─── buildFromContext() tests ─────────────────────────────────────────────

    @Test
    fun `buildFromContext returns empty result for empty list`() {
        val result = ContextBuilder.buildFromContext(emptyList())
        assertEquals(0, result.messageCount)
        assertEquals("[no text]", result.lastIncomingText)
    }

    @Test
    fun `buildFromContext tags incoming as THEM`() {
        val context = listOf(contextMsg("Hello", incoming = true))
        val result = ContextBuilder.buildFromContext(context)
        assertTrue(result.historyLines.first().startsWith("[THEM]"))
    }

    @Test
    fun `buildFromContext tags outgoing as ME`() {
        val context = listOf(contextMsg("Reply", incoming = false))
        val result = ContextBuilder.buildFromContext(context)
        assertTrue(result.historyLines.first().startsWith("[ME]"))
    }

    @Test
    fun `buildFromContext handles null text as media`() {
        val context = listOf(contextMsg(null, incoming = true))
        val result = ContextBuilder.buildFromContext(context)
        assertTrue(result.historyLines.first().contains("[media]"))
        assertEquals("[no text]", result.lastIncomingText) // null caption too
    }

    @Test
    fun `buildFromContext picks last incoming for lastIncomingText`() {
        val context = listOf(
            contextMsg("First", incoming = true),
            contextMsg("Reply", incoming = false),
            contextMsg("Second", incoming = true)
        )
        val result = ContextBuilder.buildFromContext(context)
        assertEquals("Second", result.lastIncomingText)
    }
}
