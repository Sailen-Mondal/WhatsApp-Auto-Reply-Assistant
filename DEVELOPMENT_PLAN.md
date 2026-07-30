# WhatsApp Auto-Reply Companion App - Development Plan

## Project Overview
An Android companion app that monitors WhatsApp notifications, reconstructs chats locally, and uses a multi-provider LLM system to generate context-aware auto-replies with configurable personality and tone.

## Technology Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3, BOM 2024.09.00)
- **Database**: Room 2.6.1 (with type converters and composite indexes)
- **Architecture**: MVVM + Clean Architecture (Presentation, Domain, Data layers)
- **Dependency Injection**: Hilt 2.51.1
- **Networking**: Retrofit 2.9.0 + OkHttp 4.12.0 for LLM APIs
- **Background Work**: WorkManager 2.9.1 + Coroutines
- **Notification Access**: NotificationListenerService
- **Security**: AndroidKeyStore (AES/GCM/NoPadding) for API key encryption
- **Build**: AGP 8.5.2, KSP, Java/Kotlin target 17

## Development Phases

### Phase 1: Core Notification Logger (Foundation) — ✅ COMPLETED
**Goal**: Build a stable notification capture and chat reconstruction system

**Tasks**:
1. ✅ Set up Android project structure
   - Gradle configuration (AGP 8.5.2, Kotlin 1.9.25, KSP)
   - Dependencies (Room, Compose, Hilt, Retrofit, WorkManager)
   - Project structure (data, domain, presentation layers)

2. ✅ Implement NotificationListenerService
   - Request notification access permission
   - Filter WhatsApp & WhatsApp Business notifications
   - Parse notification data (MessagingStyle + fallback basic text)
   - Deduplication window (2000ms) to prevent duplicate processing
   - Shadow notification filtering (skip notifications without actions)

3. ✅ Create Room Database
   - ChatEntity, MessageEntity, MediaMetaEntity, SettingsEntity, LLMLogEntity
   - DAOs for all 5 entities with reactive Flow queries
   - Type converters for enums (MessageDirection, MediaType, MessageSource, UserFeedback)
   - Composite unique index on messages (chatId, text, timestamp, direction)
   - Database class (version 2) with fallback to destructive migration

4. ✅ Build Ingestion Pipeline
   - Extract chatId (MD5 hash of title), senderName, messageText, timestamp
   - Determine message direction (INCOMING, OUTGOING, BOT_OUTGOING)
   - Persist to database with IGNORE conflict strategy for deduplication
   - Loop prevention (skip auto-reply trigger if newest message is BOT_OUTGOING)

5. ✅ Basic UI (Chat List & Chat Detail)
   - Compose Chat List screen with filter chips (All, Active, Paused)
   - Compose Chat Detail screen with message bubbles (incoming, outgoing, bot auto-reply)
   - Dynamic avatar colors with active status indicator
   - Per-chat auto-reply toggle in both list and detail views

6. ✅ Background Services
   - KeepAliveService (foreground service with Pause/Resume/Stop notification actions)
   - BootReceiver for auto-restart on device reboot
   - Battery optimization exemption request on first launch

**Deliverable**: App that successfully logs WhatsApp messages to local database and displays them in a WhatsApp-like UI.

---

### Phase 2: Manual AI Suggestions (LLM Integration) — ✅ COMPLETED
**Goal**: Integrate multi-provider LLM system for smart reply suggestions (user-triggered)

**Tasks**:
1. ✅ Multi-Provider LLM Integration
   - LlmProvider interface with pluggable implementations
   - GeminiProvider (Google Gemini REST API, `gemini-2.5-flash`)
   - OpenAiCompatibleProvider (reusable for OpenRouter + NVIDIA NIM)
   - LlmRouter circuit-breaker: tracks consecutive failures (max 2), applies 10s cooldown, performs recovery health checks
   - Fallback hierarchy: Gemini → OpenRouter (`google/gemma-4-31b-it:free`) → NVIDIA NIM (`qwen/qwen2.5-72b-instruct`)
   - API keys stored in `local.properties`, injected via BuildConfig, encrypted at rest via CryptoManager

2. ✅ Prompt Engineering
   - ContextBuilder: sliding window (15 messages, 3000 chars max), time-gap annotations, direction tags ([THEM], [ME], [ME🤖])
   - PromptTemplates: user prompt construction, emotional analysis prompt, question detection prompt
   - Heuristic language detection (Bengali, Banglish, Hindi, Hinglish, English)
   - Reply sanitization: strips model tags, emojis, markdown, AI disclaimers, robotic phrases
   - Emergency fallback responses if all providers fail

3. ✅ LLMLogEntity & Logging
   - Store all LLM interactions (prompt context, reply, tone, timestamp)
   - Track auto-sent vs manual flag (`wasAutoSent`)
   - User feedback system (UPVOTE / DOWNVOTE / NONE enum)
   - Indexed on chatId and timestamp for efficient queries

4. ✅ UI Enhancements
   - Settings screen with API key management, persona configuration, delay controls
   - "Suggest Reply" button in Chat Detail with loading state
   - Display generated replies with copy, edit, and send actions
   - Upvote/downvote feedback buttons
   - LLM Debug Panel with detailed log cards (context, reply, tone, auto-sent badge)
   - Emotional analysis vibe banner in chat detail

5. ✅ Security
   - CryptoManager with AndroidKeyStore (AES/GCM/NoPadding, 12-byte IV)
   - Transparent encryption/decryption for sensitive settings
   - Legacy unencrypted value fallback support

**Deliverable**: App that generates smart AI replies on-demand via multi-provider LLM with circuit-breaker failover, with user able to copy/edit/send.

---

### Phase 3: Auto-Reply Engine (Automation) — ✅ COMPLETED
**Goal**: Automatic reply generation and sending via notification actions

**Tasks**:
1. ✅ AutoReplyEngine Implementation
   - Multi-step evaluation pipeline with per-chat Mutex locking
   - Decision checks: global switch → quiet hours → per-chat switch → group exclusion → direction validation → question filter → user activity → rate limit → monologue/loop prevention
   - Question detection: fast-path regex/keyword detection (Bengali/Hinglish/English question words) with LLM fallback classifier
   - AutoReplyConfig data class with all configurable parameters
   - AutoReplyDecision data class capturing evaluation outcome, reason, delay, context, and metadata

2. ✅ Random Delay System
   - Configurable delay range (min/max seconds) via settings
   - DelayScheduler using WorkManager OneTimeWorkRequest with `setInitialDelay()`
   - ExistingWorkPolicy.REPLACE with chat-specific tags for targeted cancellation
   - Dual execution: in-process coroutine delay (fast path) + WorkManager (resilient path)

3. ✅ Notification Reply Action
   - NotificationStore: thread-safe (Mutex) in-memory cache of StatusBarNotification per chat
   - Live fallback: queries activeNotifications via weak reference if cache is stale (>60s)
   - NotificationReplySender: extracts RemoteInput, fills reply text, triggers PendingIntent
   - 5-minute notification TTL validation to prevent replying to stale notifications
   - PendingIntent.CanceledException handling for dismissed notifications

4. ✅ Settings & Controls
   - Per-chat auto-reply toggle (in chat list and chat detail)
   - Per-chat tone selection (auto, friendly, professional, flirty, funny, chill, romantic, formal)
   - Global auto-reply master switch (starts/stops KeepAliveService)
   - Delay range configuration (min/max seconds)
   - Cooldown duration setting
   - Exclude group chats toggle
   - Reply to questions only toggle
   - Wait-for-user activity delay

5. ✅ Safety Features
   - Rate limiting: max 30 replies per chat per 10 minutes, 200 global per hour
   - Monologue prevention: rejects reply if last message was BOT_OUTGOING
   - Loop prevention: NotificationProcessor skips trigger if newest notification message is outgoing
   - Per-chat mutex prevents concurrent evaluation races
   - Global kill switch via settings and KeepAliveService notification actions
   - AutoReplyWorker re-validates conditions before execution (process-death resilience)

**Deliverable**: Fully functional auto-reply system with multi-provider LLM, human-like delays, safety controls, and process-death resilience.

---

### Phase 4: Intelligence & Polish (Enhancement) — 🔶 PARTIALLY COMPLETED
**Goal**: Make the app smarter and more user-friendly

**Tasks**:
1. ✅ OWL Brain Personality System
   - 9 modular markdown files: IDENTITY, SOUL, AGENTS, USER, MEMORY, SKILL, TOOLS, HEARTBEAT, BOOTSTRAP
   - BrainFile enum with metadata (fileName, role, summary, isEditable, promptOrder)
   - BrainLoader: assembles system prompts from brain files + daily memory + tone guidance + emotional context
   - BrainRepository: asset sync from `assets/brain/`, user-editable persistence in `filesDir/brain/`, daily memory files (`memory/YYYY-MM-DD.md`)
   - BrainEditorScreen: in-app editor with expandable cards, save/reset, status indicators

2. ✅ Per-Chat Tone Management
   - 8 tone options: auto, friendly, professional, flirty, funny, chill, romantic, formal
   - Tone stored per-chat in ChatEntity.preferredTone
   - Tone guidance injected into system prompt via BrainLoader.buildToneGuidance()
   - Default tone configurable in Settings

3. ✅ Emotional Analysis
   - LLM-based emotion/dynamic/energy analysis via dedicated prompt
   - Vibe banner displayed in ChatDetailScreen
   - Emotional context appended to system prompt for reply generation

4. ✅ Analytics Dashboard
   - KPI cards: total messages, auto-replies count, reply rate percentage
   - Tone distribution visualization with animated progress bars
   - Message direction breakdown (received, sent by user, auto-replied by bot)
   - AnalyticsRepository aggregating data from MessageDao and LLMLogDao
   - Empty state handling

5. ✅ Auto-Cleanup System
   - AutoCleanupWorker runs daily via WorkManager (idle + battery not low)
   - Trims messages exceeding 1,000 per chat
   - Trims LLM logs exceeding 5,000 total

6. ✅ Debug & Diagnostics
   - DebugLogger: file-based logging with 2MB rotation, in-memory ring buffer (500 entries)
   - Debug log viewer dialog in ChatDetailScreen with color coding and copy/clear actions
   - LLM Debug Panel with context snippets and feedback indicators

7. ⏳ App Lock (PIN/Biometric) — not yet implemented
8. ⏳ Database Encryption (SQLCipher) — not yet implemented
9. ⏳ Data Export/Backup & Restore — not yet implemented
10. ⏳ Contact Prioritization — not yet implemented
11. ⏳ UI Filters (Favorites, Export) — not yet implemented

**Deliverable**: Intelligent auto-reply companion with modular personality, analytics, and diagnostics.

---

## Project Structure

```
WhatsAppAutoReply/
├── app/src/main/java/com/whatsappautoreply/
│   ├── WhatsAppAutoReplyApplication.kt
│   ├── data/
│   │   ├── database/
│   │   │   ├── WhatsAppDatabase.kt, Converters.kt
│   │   │   ├── dao/ (ChatDao, MessageDao, MediaMetaDao, SettingsDao, LLMLogDao)
│   │   │   └── entity/ (ChatEntity, MessageEntity, MediaMetaEntity, SettingsEntity, LLMLogEntity)
│   │   ├── notification/
│   │   │   ├── WhatsAppNotificationListener.kt
│   │   │   ├── NotificationProcessor.kt
│   │   │   ├── NotificationStore.kt
│   │   │   └── NotificationReplySender.kt
│   │   ├── receiver/BootReceiver.kt
│   │   ├── remote/llm/
│   │   │   ├── HuggingFaceApi.kt, HuggingFaceLLMClient.kt, HuggingFaceModels.kt
│   │   │   └── provider/ (LlmProvider, LlmRouter, GeminiProvider, OpenAiCompatibleProvider)
│   │   ├── repository/ (ChatRepository, MessageRepository, SettingsRepository, LLMLogRepository, AnalyticsRepository)
│   │   ├── service/KeepAliveService.kt
│   │   └── worker/ (AutoReplyWorker, AutoCleanupWorker)
│   ├── di/ (DatabaseModule, AutoReplyModule)
│   ├── domain/
│   │   ├── autoreply/ (AutoReplyEngine, AutoReplyConfig, AutoReplyDecision, DelayScheduler)
│   │   ├── brain/ (BrainFile, BrainLoader, BrainRepository)
│   │   └── llm/ (ContextBuilder, PromptTemplates)
│   ├── presentation/
│   │   ├── MainActivity.kt
│   │   ├── analytics/ (AnalyticsScreen, AnalyticsViewModel)
│   │   ├── brain/ (BrainEditorScreen, BrainEditorViewModel)
│   │   ├── chatdetail/ (ChatDetailScreen, ChatDetailViewModel)
│   │   ├── chatlist/ (ChatListScreen, ChatListViewModel)
│   │   ├── debug/ (LLMDebugScreen, LLMDebugViewModel)
│   │   ├── settings/ (SettingsScreen, SettingsViewModel)
│   │   └── theme/ (Theme.kt, Type.kt)
│   └── util/ (ChatUtils, CryptoManager, DebugLogger)
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Key Components

### Data Layer
- **WhatsAppNotificationListener**: Captures WhatsApp/WhatsApp Business notifications with deduplication and rebind logic
- **NotificationProcessor**: Parses MessagingStyle, resolves chat entities, triggers auto-reply pipeline
- **NotificationStore**: Thread-safe in-memory notification cache with live fallback queries
- **NotificationReplySender**: Injects replies via RemoteInput + PendingIntent with TTL validation
- **Room Database**: 5 entities, 5 DAOs, type converters, composite unique constraints
- **HuggingFaceLLMClient**: Central LLM orchestrator with multi-provider router, prompt construction, and reply sanitization
- **LlmRouter**: Circuit-breaker routing across Gemini, OpenRouter, and NVIDIA NIM providers
- **Repositories**: ChatRepository, MessageRepository, SettingsRepository, LLMLogRepository, AnalyticsRepository
- **CryptoManager**: AndroidKeyStore-backed AES/GCM encryption for API keys
- **KeepAliveService**: Foreground service with Pause/Resume/Stop notification actions
- **BootReceiver**: Auto-restarts services on device reboot
- **Workers**: AutoReplyWorker (process-death resilient replies), AutoCleanupWorker (daily DB maintenance)

### Domain Layer
- **AutoReplyEngine**: Multi-step evaluation pipeline with per-chat mutex, quiet hours, rate limiting, question detection
- **AutoReplyConfig**: Configuration data class with all auto-reply parameters
- **AutoReplyDecision**: Evaluation outcome with reason, delay, context, and metadata
- **DelayScheduler**: WorkManager-based delay scheduling with targeted cancellation
- **BrainFile**: Enum defining 9 modular personality files with metadata
- **BrainLoader**: Assembles system prompts from brain files, daily memory, tone guidance, and emotional context
- **BrainRepository**: Asset sync, user-editable persistence, daily memory management
- **ContextBuilder**: Sliding window context formatter with time-gap annotations and language detection
- **PromptTemplates**: Operational prompt templates for reply generation, emotional analysis, and question detection

### Presentation Layer
- **6 Screens**: Chat List, Chat Detail, Settings, Brain Editor, Analytics, LLM Debug
- **Compose NavHost**: 6 navigation routes with permission onboarding flow
- **ViewModels**: State management via StateFlow with WhileSubscribed sharing
- **Material 3 Theme**: WhatsApp-inspired dark/light color schemes with edge-to-edge layout

## Development Approach

1. ✅ **Phase 1**: Built solid notification capture and database foundation
2. ✅ **Phase 2**: Integrated multi-provider LLM with circuit-breaker failover
3. ✅ **Phase 3**: Implemented full auto-reply engine with safety controls
4. 🔶 **Phase 4**: Added personality system, analytics, and security — remaining items: app lock, SQLCipher, export

## Next Steps

1. ✅ ~~Create development plan (this document)~~
2. ✅ ~~Set up Android project structure~~
3. ✅ ~~Complete Phase 1-3 implementation~~
4. 🔶 Continue Phase 4: App lock, SQLCipher encryption, data export/backup
