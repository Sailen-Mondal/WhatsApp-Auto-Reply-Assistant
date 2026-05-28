# WhatsApp Auto-Reply Companion App - Development Plan

## Project Overview
An Android companion app that monitors WhatsApp notifications, reconstructs chats locally, and uses LLM (Groq) to generate context-aware auto-replies in Hinglish/Benglish style.

## Technology Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Database**: Room (with SQLCipher for encryption)
- **Architecture**: MVVM + Clean Architecture (Presentation, Domain, Data layers)
- **Dependency Injection**: Hilt
- **Networking**: Retrofit + OkHttp for Groq API
- **Background Work**: WorkManager + Coroutines
- **Notification Access**: NotificationListenerService

## Development Phases

### Phase 1: Core Notification Logger (Foundation)
**Goal**: Build a stable notification capture and chat reconstruction system

**Tasks**:
1. Set up Android project structure
   - Gradle configuration
   - Dependencies (Room, Compose, Hilt, etc.)
   - Project structure (data, domain, presentation layers)

2. Implement NotificationListenerService
   - Request notification access permission
   - Filter WhatsApp notifications (com.whatsapp)
   - Parse notification data (title, text, MessagingStyle, actions)

3. Create Room Database
   - ChatEntity, MessageEntity, MediaMetaEntity, SettingsEntity
   - DAOs for all entities
   - Database class with migrations

4. Build Ingestion Pipeline
   - Extract chatId, senderName, messageText, timestamp
   - Determine message direction (INCOMING/OUTGOING)
   - Persist to database

5. Basic UI (Chat List & Chat Detail)
   - Compose screens for chat list
   - Compose screen for chat detail with message timeline
   - Read from database (not directly from WhatsApp)

6. Testing
   - Test with single messages
   - Test with group chats
   - Test with media messages
   - Handle edge cases (aggregated notifications, deleted messages)

**Deliverable**: App that successfully logs WhatsApp messages to local database and displays them in a WhatsApp-like UI.

**Estimated Complexity**: Medium
**Dependencies**: None

---

### Phase 2: Manual AI Suggestions (LLM Integration)
**Goal**: Integrate Groq LLM for smart reply suggestions (user-triggered)

**Tasks**:
1. Groq API Integration
   - Create LLMClient with Retrofit
   - API key management (store securely)
   - Error handling, retries, rate limiting
   - Basic caching mechanism

2. Prompt Engineering
   - System prompt for Hinglish/Benglish style
   - Context building from message history
   - Tone detection/inference
   - Response formatting

3. LLMLogEntity & Logging
   - Store all LLM interactions
   - Track input context, generated reply, tone used
   - User feedback system (upvote/downvote)

4. UI Enhancements
   - Settings screen for API key input
   - "Suggest Reply" button in Chat Detail
   - Display generated replies with copy/edit options
   - LLM Debug Panel (developer mode)

5. Testing
   - Test with various conversation styles
   - Test Hinglish/Benglish preservation
   - Test different tones
   - Test error scenarios (API failures, rate limits)

**Deliverable**: App that can generate smart AI replies on-demand, with user able to copy/edit before sending manually.

**Estimated Complexity**: Medium-High
**Dependencies**: Phase 1 complete

---

### Phase 3: Auto-Reply Engine (Automation)
**Goal**: Automatic reply generation and sending via notification actions

**Tasks**:
1. AutoReplyEngine Implementation
   - Decision logic (when to reply)
   - Context window building
   - Tone determination (per-chat settings)
   - Integration with LLMClient
   - Throttling and frequency control

2. Random Delay System
   - Configurable delay range (min/max)
   - Per-chat jitter
   - Message length-based delay adjustment
   - WorkManager scheduling for reliability

3. Notification Reply Action
   - Store notification references per chat
   - Locate correct notification + Reply action
   - Fill RemoteInput with generated reply
   - Trigger PendingIntent to send
   - Handle notification disappearance

4. Settings & Controls
   - Per-chat auto-reply toggle
   - Per-chat tone selection
   - Global auto-reply master switch
   - Delay range configuration
   - Quiet hours (optional)

5. Safety Features
   - Re-check conditions before sending
   - Kill switch (global off)
   - Process death resilience
   - Error recovery

6. Testing
   - Test auto-send with various scenarios
   - Test delay scheduling
   - Test process death recovery
   - Test edge cases (notification cleared, settings changed)

**Deliverable**: Fully functional auto-reply bot that can automatically respond to WhatsApp messages with human-like delays.

**Estimated Complexity**: High
**Dependencies**: Phase 1 & 2 complete

---

### Phase 4: Intelligence & Polish (Enhancement)
**Goal**: Make the app smarter and more user-friendly

**Tasks**:
1. Advanced Tone Management
   - Auto-suggest tone based on conversation history
   - LLM-based mood inference
   - Tone tags and analytics

2. Smart Frequency Control
   - Rules: "only reply if user hasn't replied for X seconds"
   - Question detection (reply to direct questions)
   - Conversation context awareness

3. Analytics Dashboard
   - Messages per chat statistics
   - Auto-reply percentage
   - Tone distribution
   - LLM usage stats

4. UI/UX Improvements
   - Better visual indicators for auto-reply status
   - Message metadata on long-press
   - Filters (favorites, auto-reply enabled)
   - Export functionality

5. Security Enhancements
   - Database encryption (SQLCipher)
   - App lock (PIN/biometric)
   - Privacy warnings
   - Data export/clear options

6. Performance Optimization
   - Database query optimization
   - LLM response caching
   - Background task optimization
   - Battery usage optimization

**Deliverable**: Polished, intelligent auto-reply companion with analytics and security features.

**Estimated Complexity**: Medium
**Dependencies**: Phase 1, 2, 3 complete

---

## Project Structure

```
WhatsAppAutoReply/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/whatsappautoreply/
│   │   │   │   ├── data/
│   │   │   │   │   ├── database/
│   │   │   │   │   ├── local/
│   │   │   │   │   ├── remote/
│   │   │   │   │   └── repository/
│   │   │   │   ├── domain/
│   │   │   │   │   ├── model/
│   │   │   │   │   ├── repository/
│   │   │   │   │   └── usecase/
│   │   │   │   ├── presentation/
│   │   │   │   │   ├── ui/
│   │   │   │   │   ├── viewmodel/
│   │   │   │   │   └── theme/
│   │   │   │   └── di/
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   └── test/
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Key Components

### Data Layer
- **NotificationListenerService**: Captures WhatsApp notifications
- **Room Database**: Local storage for chats, messages, settings
- **LLMClient**: Groq API integration
- **Repositories**: Data access abstraction

### Domain Layer
- **Use Cases**: Business logic (auto-reply decision, context building)
- **Models**: Domain entities
- **AutoReplyEngine**: Core auto-reply logic

### Presentation Layer
- **Compose UI**: Chat list, chat detail, settings screens
- **ViewModels**: UI state management
- **Navigation**: Screen navigation

## Development Approach

1. **Start with Phase 1**: Build solid foundation
2. **Incremental Development**: Complete each phase before moving to next
3. **Testing**: Test each phase thoroughly before proceeding
4. **Iterative Refinement**: Improve based on testing and usage

## Timeline Estimate

- **Phase 1**: 2-3 days (notification capture + basic UI)
- **Phase 2**: 2-3 days (LLM integration + manual suggestions)
- **Phase 3**: 3-4 days (auto-reply engine + notification actions)
- **Phase 4**: 2-3 days (polish + intelligence)

**Total**: ~9-13 days of focused development

## Next Steps

1. ✅ Create development plan (this document)
2. ⏳ Set up Android project structure
3. ⏳ Begin Phase 1 implementation

