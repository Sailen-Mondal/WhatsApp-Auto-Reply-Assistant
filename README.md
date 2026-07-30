# WhatsApp Auto-Reply Assistant

A native Android companion assistant that turns supported WhatsApp and WhatsApp Business notifications into context-aware reply suggestions and automatic replies using a multi-provider LLM system (Gemini → OpenRouter → NVIDIA NIM) with configurable personality and tone.

> **Disclaimer**: This project is not affiliated with, endorsed by, or supported by WhatsApp or Meta. It does not use the official WhatsApp Business API.

---

## How It Works

```text
WhatsApp Notification
  -> WhatsAppNotificationListener validates and parses incoming notification
  -> Room database stores local chat context & message history
  -> AutoReplyEngine evaluates safety rules, quiet hours, rate limits & delays
  -> Multi-Provider LLM (Gemini -> OpenRouter -> NVIDIA NIM) generates contextual reply
  -> NotificationReplySender injects reply via Android RemoteInput & PendingIntent
```

The app works only while a reply-capable WhatsApp notification is active. It does not read WhatsApp's internal database, automate the WhatsApp UI, or recover historical messages that were never received as notifications.

---

## Project Status & Phases

### ✅ Phase 1: Core Notification Logger (COMPLETED)
- ✅ Android project structure with Gradle (AGP 8.5.2, Kotlin 1.9.25, KSP), Hilt DI, and Room 2.6.1
- ✅ Room database with 5 entities (`ChatEntity`, `MessageEntity`, `MediaMetaEntity`, `SettingsEntity`, `LLMLogEntity`)
- ✅ 5 DAOs with reactive `Flow` queries and composite unique constraints
- ✅ `WhatsAppNotificationListener` with deduplication window (2000ms) and loop prevention
- ✅ `NotificationProcessor` pipeline (MessagingStyle parsing + basic text fallback)
- ✅ `KeepAliveService` foreground service with Pause/Resume/Stop notification actions
- ✅ `BootReceiver` for auto-restart on device reboot
- ✅ Compose UI with Chat List and Chat Detail screens
- ✅ Hilt dependency injection (`DatabaseModule` + `AutoReplyModule`)

### ✅ Phase 2: Manual AI Suggestions & Multi-Provider LLM (COMPLETED)
- ✅ Multi-provider LLM integration with `LlmRouter` circuit-breaker routing:
  - Primary: Google Gemini (`gemini-2.5-flash`)
  - Secondary: OpenRouter (`google/gemma-4-31b-it:free`)
  - Fallback: NVIDIA NIM (`qwen/qwen2.5-72b-instruct`)
- ✅ Manual reply suggestion with "Suggest Reply" button in Chat Detail
- ✅ Settings screen with API key management (encrypted via `AndroidKeyStore` AES/GCM)
- ✅ Copy-to-clipboard, inline edit, and one-tap send for suggestions
- ✅ Upvote/downvote feedback system persisted to `LLMLog`
- ✅ LLM Debug Panel for inspecting detailed log cards
- ✅ Reply sanitization (strips model tags, emojis, AI disclaimers, markdown)

### ✅ Phase 3: Auto-Reply Engine (COMPLETED)
- ✅ `AutoReplyEngine` with multi-step evaluation pipeline:
  - Global & per-chat enable/disable master switches
  - Quiet hours support
  - Group chat exclusion toggle
  - Question detection (regex keywords in Bengali/Hinglish/English + LLM fallback classifier)
  - User activity check (pauses auto-reply if user sent a message recently)
  - Rate limiting (max 30 per chat/10min, 200 global/hour)
  - Monologue & bot-loop prevention
- ✅ Per-chat `Mutex` locking to prevent race conditions
- ✅ Random delay system with configurable min/max seconds and message-length bonus
- ✅ `NotificationStore` with thread-safe mutex access and live active notification queries
- ✅ `NotificationReplySender` with 5-minute TTL validation and `RemoteInput` injection
- ✅ Dual execution paths: in-process coroutines for sub-second timing + WorkManager `AutoReplyWorker` for process-death resilience

### 4. Phase 4: Intelligence & Polish (PARTIALLY COMPLETED)
- ✅ **OWL Brain Personality System**: Modular markdown persona framework (9 files: IDENTITY, SOUL, AGENTS, USER, MEMORY, SKILL, TOOLS, HEARTBEAT, BOOTSTRAP) assembled dynamically by `BrainLoader` with in-app editor (`BrainEditorScreen`)
- ✅ **Per-chat tone management**: Configurable tone per chat (auto, friendly, professional, flirty, funny, chill, romantic, formal)
- ✅ **Emotional analysis**: LLM-based vibe/mood analysis banner displayed in Chat Detail
- ✅ **Analytics Dashboard**: KPI cards, auto-reply rate %, tone distribution, and direction breakdown with animated progress bars
- ✅ **Security**: Sensitive settings encryption via `CryptoManager` (AES/GCM/NoPadding)
- ✅ **Auto-cleanup**: `AutoCleanupWorker` trims messages (1,000/chat) and logs (5,000 total) daily
- ✅ **Heuristic language detection**: Bengali, Banglish, Hindi, Hinglish, English
- ⏳ App lock (PIN/biometric) — planned
- ⏳ Database encryption (SQLCipher) — planned
- ⏳ Data export/backup & restore — planned

---

## Architecture

| Layer | Responsibility |
| --- | --- |
| `presentation` | Jetpack Compose screens (Chat List, Chat Detail, Settings, Brain Editor, Analytics, Debug), navigation, and ViewModels. |
| `domain` | `AutoReplyEngine`, `DelayScheduler`, `BrainLoader`, `BrainRepository`, `ContextBuilder`, and prompt templates. |
| `data` | Room database, DAOs, repositories, `NotificationProcessor`, `NotificationStore`, `NotificationReplySender`, `LlmRouter` multi-provider client, and `CryptoManager`. |
| OS Integration | `WhatsAppNotificationListener`, `KeepAliveService`, `BootReceiver`, `AutoReplyWorker`, `AutoCleanupWorker`, and `RemoteInput`. |

### Project Structure

```text
WhatsAppAutoReply/
├── app/src/main/java/com/whatsappautoreply/
│   ├── WhatsAppAutoReplyApplication.kt
│   ├── data/
│   │   ├── database/ (WhatsAppDatabase, Converters, dao/, entity/)
│   │   ├── notification/ (WhatsAppNotificationListener, NotificationProcessor, NotificationStore, NotificationReplySender)
│   │   ├── receiver/ (BootReceiver)
│   │   ├── remote/llm/ (HuggingFaceLLMClient, HuggingFaceApi, provider/)
│   │   ├── repository/ (ChatRepository, MessageRepository, SettingsRepository, LLMLogRepository, AnalyticsRepository)
│   │   ├── service/ (KeepAliveService)
│   │   └── worker/ (AutoReplyWorker, AutoCleanupWorker)
│   ├── di/ (DatabaseModule, AutoReplyModule)
│   ├── domain/
│   │   ├── autoreply/ (AutoReplyEngine, AutoReplyConfig, AutoReplyDecision, DelayScheduler)
│   │   ├── brain/ (BrainFile, BrainLoader, BrainRepository)
│   │   └── llm/ (ContextBuilder, PromptTemplates)
│   ├── presentation/
│   │   ├── analytics/ (AnalyticsScreen, AnalyticsViewModel)
│   │   ├── brain/ (BrainEditorScreen, BrainEditorViewModel)
│   │   ├── chatdetail/ (ChatDetailScreen, ChatDetailViewModel)
│   │   ├── chatlist/ (ChatListScreen, ChatListViewModel)
│   │   ├── debug/ (LLMDebugScreen, LLMDebugViewModel)
│   │   ├── settings/ (SettingsScreen, SettingsViewModel)
│   │   └── theme/ (Theme, Type)
│   └── util/ (ChatUtils, CryptoManager, DebugLogger)
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## Setup Instructions

### Prerequisites
- Android Studio Hedgehog or later
- Android SDK 26+ (Android 8.0+), targeting SDK 34
- JDK 17
- At least one LLM API key (Gemini, OpenRouter, or NVIDIA NIM)

### API Key Setup
Add your API keys to `local.properties` in the project root:
```properties
GEMINI_API_KEY=your_gemini_api_key_here         # Primary (Gemini 2.5 Flash)
OPENROUTER_API_KEY=your_openrouter_api_key_here  # Secondary (Gemma 4 31B)
NVIDIA_NIM_API_KEY=your_nvidia_nim_api_key_here   # Fallback (Qwen 2.5 72B)
```
Keys are injected via `BuildConfig` at build time and encrypted at rest using `AndroidKeyStore`. Do not commit `local.properties`.

### Building the Project
1. Clone or navigate to the project directory:
   ```bash
   git clone https://github.com/Sailen-Mondal/WhatsApp-Auto-Reply-Assistant.git
   ```
2. Open in Android Studio.
3. Add API keys to `local.properties`.
4. Sync Gradle files and run the `app` module on a physical device or emulator.

---

## Required Permissions

| Permission / Capability | Why it is needed |
| --- | --- |
| **Notification Listener Access** | Read WhatsApp notifications and invoke their RemoteInput Reply action. |
| **Internet** | Execute LLM API completion requests. |
| **Foreground Service** | Keep `KeepAliveService` running in background. |
| **Post Notifications** | Show persistent status notification with Pause/Resume/Stop actions. |
| **Boot Completed** | Automatically restore `KeepAliveService` on device reboot. |
| **Battery Optimization Exemption** | Prevent Android OS from stopping background notification listening. |

---

## Privacy and Security

- **Local Storage**: All chat context and message history are stored locally on-device in Room SQLite.
- **Key Encryption**: API keys stored in app settings are encrypted using hardware-backed `AndroidKeyStore` AES/GCM.
- **Data Transmission**: Only the necessary conversation sliding window context (last 15 messages max) is sent to the configured LLM API provider for response generation.
- **Scope**: The assistant only sees notifications received while active; dismissed notifications or historical chats prior to installation are unavailable.

---

## Development & Testing

Run unit tests from Android Studio or via Gradle:
```powershell
.\gradlew.bat test
```
The test suite includes tests for `AutoReplyEngine` decision logic, LLM circuit-breaker router (`LlmRouterTest`), and context building.

---

## License

This project is licensed under the MIT License.
