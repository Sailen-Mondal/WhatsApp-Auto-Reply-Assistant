# WhatsApp Auto-Reply Companion App

An Android companion app that monitors WhatsApp notifications, reconstructs chats locally, and uses LLM (Hugging Face API - Zephyr-7b) to generate context-aware auto-replies in Hinglish/Benglish style.

## Project Status

### ✅ Phase 1: Core Notification Logger (COMPLETED)
- ✅ Android project structure set up
- ✅ Room database with all entities (Chat, Message, MediaMeta, Settings, LLMLog)
- ✅ NotificationListenerService implementation
- ✅ Notification ingestion pipeline
- ✅ Basic Compose UI (Chat List & Chat Detail screens)
- ✅ Dependency injection with Hilt

### ✅ Phase 2: Manual AI Suggestions (COMPLETED)
- ✅ LLM integration with Hugging Face API (Zephyr-7b)
- ✅ Manual reply suggestion feature
- ✅ Settings screen for API key
- ✅ Copy-to-clipboard functionality for suggested replies
- ✅ Edit functionality to modify suggested replies
- ✅ Upvote/downvote feedback system
- ✅ LLM Debug Panel for viewing logs

### ✅ Phase 3: Auto-Reply Engine (IMPLEMENTED)
- ✅ Auto-reply decision logic (Global/Chat switches, Question detection)
- ✅ Random delay system with "typing" simulation
- ✅ Notification reply action integration (Send replies via notification)
- ✅ Safety controls (Group chat exclusion, User activity check)

### ⏳ Phase 4: Intelligence & Polish (TODO)
- Advanced tone management
- Analytics dashboard
- Security enhancements (Encryption)

## Features

### ✅ What It DOES
1.  **Notification Monitoring**: Listens for WhatsApp notifications to capture messages and reconstruct chat history locally.
2.  **Intelligent Auto-Reply**:
    -   **Decision Engine**: Configurable rules for when to reply (e.g., only questions, exclude groups).
    -   **Human-like Delays**: Calculates random delays (5-60s) based on message length.
    -   **User Activity Check**: Pauses auto-reply if you have sent a message recently.
3.  **LLM Integration**:
    -   Uses **Hugging Face API** (Zephyr-7b model).
    -   Maintains a specific **Persona** (e.g., Flirty, Funny, Professional).
    -   Generates responses in **Hinglish/Benglish**.
4.  **Action Execution**: Sends replies by triggering the native "Reply" action in WhatsApp notifications.

### ❌ Limitations (What It DOES NOT DO)
1.  **No Past History**: Cannot read messages sent before the app was installed or if notifications were dismissed.
2.  **No Media Content**: Does not download images/videos; only logs them as metadata (e.g., "[IMAGE]").
3.  **No Official API**: Does not use WhatsApp Business API; relies entirely on notification access.
4.  **Background Reliability**: Subject to Android's battery optimizations. Requires the notification to remain visible to send a reply.

## Architecture

The app follows Clean Architecture with 4 layers:

1.  **Presentation Layer**: Jetpack Compose UI, ViewModels
2.  **Domain Layer**: Business logic, AutoReplyEngine
3.  **Data Layer**: Room database, Repositories, Notification processing, HuggingFaceLLMClient
4.  **OS Integration**: NotificationListenerService, KeepAliveService

## Setup Instructions

### Prerequisites
- Android Studio Hedgehog or later
- Android SDK 26+ (Android 8.0+)
- Kotlin 1.9.20+
- Hugging Face API Key

### Building the Project
1.  Clone or navigate to the project directory
2.  Open in Android Studio
3.  Sync Gradle files
4.  Build and run on a device/emulator

### Required Permissions
The app requires **Notification Access** permission to monitor WhatsApp notifications:
1.  Install the app
2.  Go to Settings → Apps → Special app access → Notification access
3.  Enable access for "WhatsApp Auto Reply"

## Technology Stack

-   **Language**: Kotlin
-   **UI**: Jetpack Compose
-   **Database**: Room
-   **DI**: Hilt
-   **Architecture**: MVVM + Clean Architecture
-   **AI/LLM**: Hugging Face API (Retrofit)
-   **Background Work**: Coroutines + WorkManager

## License

Personal project - not for public distribution.

