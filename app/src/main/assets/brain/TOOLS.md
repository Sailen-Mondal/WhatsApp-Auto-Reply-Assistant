# TOOLS — Platform & System Details

This file describes the technical environment OWL operates in. It is not about personality. It is about the machine around the personality.

---

## Platform

**WhatsApp** (com.whatsapp and com.whatsapp.w4b)

OWL runs as an Android app using the **NotificationListenerService** API to intercept incoming WhatsApp notifications and send replies via the notification's **RemoteInput reply action**.

---

## How Replies Are Delivered

- Replies are sent through the Android notification's RemoteInput action
- This is the same mechanism as replying directly from a notification
- Only **text replies** are possible — no images, voice notes, files, stickers
- Maximum recommended reply length: **800 characters** per message
- Very long replies should be split into natural chunks (see SKILL.md)

---

## Message Receipt

- Messages are captured from WhatsApp push notifications
- Conversation history is built from a local Room database (SQLite)
- Context window: up to **15 recent messages** are provided per reply generation
- Time gaps between messages are annotated (e.g., `[~2h gap]`) to give the LLM pacing awareness
- Message tags: `[THEM]` for incoming, `[ME]` for user-sent, `[ME🤖]` for bot-sent

---

## Reply Constraints

| Constraint | Value |
|---|---|
| Max reply length | 800 characters |
| Max emojis per reply | 1 (zero is fine) |
| Max questions per reply | 1 |
| Reply type | Text only |
| Group chats | Auto-reply disabled by default |
| [NO_REPLY] signal | Skip sending entirely |

---

## Rate Limiting (Safety)

To prevent spam and conversation loops:
- Maximum **3 auto-replies per chat per 10 minutes**
- Maximum **100 auto-replies globally per hour**
- Loop prevention: never reply if the last message in the chat was also a bot reply
- Monologue prevention: if a newer message arrived while processing, skip the older one

---

## Session Handling

- Each reply is generated in a stateless LLM call
- Conversation context is rebuilt fresh from the database on every call
- MEMORY.md provides cross-session continuity
- Daily memory files provide same-day context continuity
- The agent does not "remember" between calls except through these files

---

## Delay Simulation

Replies are intentionally delayed to simulate human typing:
- Base delay: 1–10 seconds (configurable)
- Extra delay for longer messages: up to 5 additional seconds
- Delay is randomized with jitter to prevent robotic timing patterns

---

## Supported WhatsApp Variants

- WhatsApp (com.whatsapp)
- WhatsApp Business (com.whatsapp.w4b)

---

## API / LLM

- Provider: **OpenRouter** (https://openrouter.ai)
- Primary model: configured via settings
- Fallback model: meta-llama/llama-3.1-8b-instruct:free
- Max tokens for reply: 200
- Temperature: 0.8 (creative but controlled)
- Max tokens for emotional analysis: 40 (temperature 0.3)
- Max tokens for question detection: 5 (temperature 0.1)
