# Assistant Role: RecognitionService Development Plan

Status: pending (not decided yet)

## Goal
Make the app requestable/eligible for Android `ROLE_ASSISTANT` on Samsung/Android builds where a full VoiceInteraction stack is required.

## Scope
- Add a minimal but valid `RecognitionService` implementation.
- Wire it into `voice_interaction_service.xml` (`android:recognitionService`).
- Register recognition service in `AndroidManifest.xml` with proper permission and intent filter.
- Keep existing recording UX intact.

## Pending Tasks
- [ ] Create `NovaRecognitionService` (minimal lifecycle-safe implementation).
- [ ] Implement required recognition callbacks (`startListening`, `stopListening`, `cancel`) with safe no-op/error responses.
- [ ] Add manifest service entry:
  - `android.permission.BIND_SPEECH_RECOGNITION_SERVICE`
  - `android.speech.RecognitionService` intent filter.
- [ ] Update `res/xml/voice_interaction_service.xml` with the recognition service class name.
- [ ] Verify app compiles and build debug APK.
- [ ] Device validation:
  - Tap “Set as Default Assistant”.
  - Confirm role request UI appears.
  - Confirm holder with `adb shell cmd role get-role-holders android.app.role.ASSISTANT`.
- [ ] If still rejected, collect focused logs and iterate.

## Risks / Notes
- OEM-specific role checks may require stricter behavior than AOSP.
- A stub recognizer must still respond correctly to avoid service rejection/crashes.
- Keep this change isolated from Telegram/auth flows.

## Exit Criteria
- `RequestRoleActivity` no longer logs `Role is not requestable: android.app.role.ASSISTANT`.
- `ROLE_ASSISTANT` can be granted to `io.openclaw.telegramhandsfree`.
- App button state updates to “✅ Default Assistant” after successful grant.
