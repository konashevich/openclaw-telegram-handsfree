# OpenClaw Telegram Handsfree

Native Android app for hands-free voice interaction with a specific Telegram group (or topic) using TDLib.

## What it does

Long-press a steering wheel / Bluetooth media button → beep → speak → release or wait for silence → voice message sent to your Telegram group. Incoming voice messages from the same group are auto-played through the car speaker.

## Build prerequisites

- Android SDK 35
- JDK 17
- Gradle wrapper included (`./gradlew`)

## Build

```
# Windows
set JAVA_HOME=C:\Program Files\Java\jdk-17
.\gradlew.bat :app:assembleDebug
```

APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## TDLib native dependency

The app uses TDLib via reflection — no compile-time dependency on TDLib classes. But you **must** provide the native + Java artifacts at runtime:

1. Put TDLib Java artifact (`.jar` or `.aar`) in `app/libs/`.
2. Put native `libtdjni.so` for your device ABI under `app/src/main/jniLibs/<abi>/libtdjni.so` (e.g. `arm64-v8a`).

You can get prebuilt TDLib Android artifacts from [tdlib/td releases](https://github.com/tdlib/td) or build them yourself.

## Setup & configuration

Install the APK, open the app. The settings screen lets you enter all Telegram connection parameters. No code editing needed.

### Get Telegram API ID and API Hash

Use **https://my.telegram.org** (this is the correct URL, not `my.telegram.com`).

1. Open [my.telegram.org](https://my.telegram.org) in a browser.
2. Sign in with your Telegram phone number.
3. Enter the login code Telegram sends you.
4. Open **API development tools**.
5. Create an app (title + short name).
6. Copy:
	- `App api_id` → use as **API ID** in this app
	- `App api_hash` → use as **API Hash** in this app

| Setting | What it is | Where / how to get it |
|---|---|---|
| **API ID** | Numeric ID Telegram gives your "app" | [my.telegram.org](https://my.telegram.org) → log in → API development tools → `App api_id` |
| **API Hash** | String key paired with API ID | Same page → `App api_hash` |
| **Phone Number** | Your Telegram account phone | International format: `+79161234567` |
| **Group / Chat ID** | Numeric ID of the target Telegram group | Add `@RawDataBot` to the group — it replies with the chat ID (e.g. `-1001234567890`). Remove the bot after. |
| **Topic / Thread ID** | ID of a topic inside a group with Topics enabled | `0` if no topics. Otherwise open the topic in [web.telegram.org](https://web.telegram.org), URL looks like `#-1001234567890_456` — `456` is the topic ID. |
| **Auth Code** | One-time login code | Leave blank first. Start authentication → Telegram sends a code to your phone/other clients → enter it → Submit Code. |
| **2FA Password** | Two-step verification password | Only if you have 2FA enabled in Telegram. Otherwise leave blank. |

## Onboarding flow (current UI)

1. Install APK on your Android device.
2. In **Telegram Authentication**:
	- Enter API ID, API Hash, Phone Number.
	- Tap **Start Authentication**.
	- Enter auth code when prompted, then tap **Submit Code**.
	- If your account has 2FA, enter password and submit (otherwise leave it empty).
3. After auth is connected, use **Chat / Group Connection**:
	- Enter Group / Chat ID (and Topic ID if needed).
	- Tap **Connect Chat / Group**.
4. In **Other settings**:
	- Tap **Set as Default Assistant**.
	- Configure **Use Bluetooth microphone** if needed.
	- Tap **Finish setup**.
5. Use **Record** or long-press media/assistant button to record and send voice.

Notes:
- Authentication and chat/group targeting are separate steps.
- You can reopen any collapsed group to reconfigure and reconnect.

## Project structure

- `config/ClawsfreeConfig` — SharedPreferences-backed settings (editable from the UI)
- `telegram/TdLibReflectiveBridge` — reflection-based TDLib API bridge (auth, send, receive)
- `telegram/TdLibClient` — status state machine + outbox queue
- `telegram/TelegramRepository` — thin facade for the service layer
- `voice/ClawsfreeForegroundService` — recording, playback, Telegram monitoring
- `voice/MediaButtonReceiver` — Bluetooth/steering media button handler
- `voice/ClawsfreeVoiceInteractionService` — Digital Assistant slot entry point
- `audio/AudioRecorder` — OGG/Opus recording with silence timeout
- `audio/AudioPlaybackManager` — MediaPlayer with audio focus

## Delivery rules

- Build only the working minimum described in `docs/clawbot_telegram_handsfree_spec.md`.
- Do not add scope beyond the minimum flow.
- Do not delete parked unfinished components; keep them isolated and non-blocking.
