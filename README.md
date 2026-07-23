# WhatsApp Auto-Reply Assistant

A native Android assistant that turns supported WhatsApp notifications into context-aware reply suggestions and optional automatic replies. It observes notification content locally, applies configurable safety rules, generates a concise response with an LLM, and submits that response through WhatsApp's notification Reply action.

> This project is not affiliated with, endorsed by, or supported by WhatsApp or Meta. It does not use the official WhatsApp Business API.

## How It Works

```text
WhatsApp notification
  -> Notification listener validates and parses the message
  -> Room database stores local chat context
  -> Auto-reply engine applies safety and timing rules
  -> LLM generates a short contextual reply
  -> Android RemoteInput invokes WhatsApp's Reply notification action
```

The app works only while a reply-capable WhatsApp notification is available. It does not read WhatsApp's private database, automate the WhatsApp UI, or recover historical messages that were never received as notifications.

## Features

- Captures notifications from WhatsApp and WhatsApp Business.
- Reconstructs a local, notification-derived conversation history.
- Generates manual reply suggestions with language and tone awareness.
- Supports per-chat and global auto-reply controls.
- Applies guardrails: group exclusion, question-only mode, manual-user activity wait, cooldowns, per-chat rate limits, global rate limits, duplicate detection, and bot-loop prevention.
- Adds a randomized, message-length-aware delay before an auto-reply.
- Supports configurable response tones, including friendly, professional, funny, chill, romantic, and formal.
- Provides chat history, reply editing, manual sending, analytics, LLM logs, and feedback controls.
- Encrypts the stored API-key setting with Android Keystore AES-GCM when accessed through `SettingsRepository`.
- Runs a foreground keep-alive service and restores it after device boot when auto-reply is enabled.
- Periodically trims old messages and LLM logs with WorkManager.

## Architecture

The codebase follows a practical Clean Architecture and MVVM structure.

| Layer | Responsibility |
| --- | --- |
| `presentation` | Jetpack Compose screens, navigation, and ViewModels. |
| `domain` | Auto-reply decisions, configuration, context models, and delay calculation. |
| `data` | Room persistence, repositories, notification parsing, LLM networking, and Android service integrations. |
| Android platform | Notification listener, RemoteInput reply actions, foreground service, boot receiver, and permissions. |

Important entry points:

- `WhatsAppAutoReplyApplication`: configures Hilt and WorkManager.
- `MainActivity`: hosts the Compose application and permission onboarding.
- `WhatsAppNotificationListener`: receives and validates WhatsApp notifications.
- `NotificationProcessor`: extracts messages and writes them to Room.
- `AutoReplyEngine`: decides whether a message is eligible for an automatic reply.
- `HuggingFaceLLMClient`: the legacy class name for the current OpenRouter-backed, OpenAI-compatible LLM client.
- `NotificationReplySender`: sends reply text through Android `RemoteInput` and WhatsApp's notification action.

## Technology Stack

- Kotlin and Java 17
- Android SDK 26-34
- Jetpack Compose and Material 3
- Navigation Compose
- MVVM, ViewModel, Kotlin Flow, and Coroutines
- Hilt dependency injection and KAPT
- Room / SQLite
- WorkManager and Hilt WorkManager
- Android `NotificationListenerService`, `RemoteInput`, foreground services, and broadcast receivers
- Retrofit, OkHttp, Gson, and OpenRouter's OpenAI-compatible Chat Completions API
- Android Keystore with AES/GCM encryption
- JUnit, MockK, coroutine test utilities, MockWebServer, Espresso, and Compose UI testing dependencies

## Requirements

- Android Studio Hedgehog or newer
- JDK 17
- Android device or emulator running Android 8.0 (API 26) or newer
- A WhatsApp or WhatsApp Business installation for end-to-end notification testing
- An OpenRouter API key for LLM features

## Getting Started

1. Clone the repository and open it in Android Studio.
2. Create `local.properties` in the project root if it does not already exist.
3. Add your API key:

   ```properties
   OPENROUTER_API_KEY=your_key_here
   ```

4. Sync Gradle and run the `app` configuration on a physical device or emulator.
5. Grant notification access when prompted in the app.
6. For reliable background behavior, exempt the app from battery optimization when the app requests it.

The build reads `OPENROUTER_API_KEY` into `BuildConfig`. Do not commit `local.properties` or a real API key.

## Permissions

| Permission / capability | Why it is needed |
| --- | --- |
| Notification listener access | Read supported WhatsApp notifications and access their Reply action. |
| Internet | Call the configured LLM provider. |
| Post notifications | Show the foreground-service status notification. |
| Foreground service | Keep the assistant active while auto-reply is enabled. |
| Boot completed | Restore the keep-alive service after a reboot when enabled. |
| Battery-optimization exemption | Improve reliability on devices that aggressively stop background work. |

## Privacy and Limitations

- Conversation data is stored locally in the app's Room database.
- The relevant context is sent to the configured LLM provider when a suggestion or auto-reply is generated.
- The assistant only sees what is present in supported notifications; dismissed or old messages are unavailable.
- Media files are not downloaded or interpreted. The app stores only basic notification-derived media metadata.
- Replies require a current, reply-capable WhatsApp notification. Android and device-manufacturer battery policies can interrupt the service.
- Notification parsing and group detection use heuristics, so behavior may differ across WhatsApp versions, languages, and notification formats.

Use this project only with accounts and conversations for which you have appropriate authorization. Review generated replies before relying on them in sensitive, professional, legal, medical, or financial conversations.

## Development

Run unit tests from Android Studio or with:

```powershell
.\gradlew.bat test
```

The most important unit tests cover the auto-reply decision engine and LLM-context construction. Test notification behavior on a real device as notification content and reply actions vary by Android and WhatsApp version.

## Contributing

Contributions are welcome. Please keep changes focused, add or update tests for behavior changes, avoid committing secrets, and document any new permission or privacy impact.

## License

This project is licensed under the [MIT License](LICENSE).
