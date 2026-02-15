# Bootstrap Notes

## What is implemented now

- Native Android project scaffold (Kotlin, AGP, app module)
- Assistant entry via `VoiceInteractionService` and `VoiceInteractionSessionService`
- Media button receiver that maps long-press to start, tap-up to stop-if-recording
- Foreground service orchestration for recording and playback lifecycle
- OGG Opus recording scaffold with 20-second silence timeout watchdog
- Telegram status model + outbox queue for temporary offline/unavailable TDLib state
- Reflective TDLib bridge handling auth state updates and send/download message functions when TDLib artifacts are present
- Runtime permission setup/status flow in `MainActivity`
- Debounced press-duration media trigger handling in `MediaButtonReceiver`
- Startup self-check UI for config + TDLib Java/native artifact validation
- App module local artifact wiring for `app/libs` JAR/AAR ingestion

## What remains for production

1. Add TDLib native libs and Java/Kotlin bindings to classpath/runtime.
2. Replace hardcoded auth placeholders with a secure provisioning strategy for production builds.
3. Add reconnect/backoff policy and explicit lifecycle shutdown for TDLib client.
4. Tune trigger thresholds and debounce windows for specific steering wheel hardware.
5. Add a richer setup/status screen if production UX requires in-app diagnostics.
6. Add instrumentation tests for authorization states, send outbox recovery, and media-button behavior.

## TDLib integration reminder

- API credentials are currently in `NovaConfig` placeholders.
- Group id is hardcoded per project spec.
- For release safety, move credentials to local-only config during development and inject in CI.
