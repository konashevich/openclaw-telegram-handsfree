# Assistant Default + Chooser Recovery Plan (Android 16+)

Status: pending (not decided yet)

## Objective
Fix assistant integration end-to-end for Android 16+ so that:
1) Clawsfree is requestable/grantable for `ROLE_ASSISTANT`, and
2) Clawsfree is visible again in the system "Complete action using" assistant chooser.

## Current Known State
- Role request currently fails with: `Role is not requestable: android.app.role.ASSISTANT`.
- Clawsfree disappeared from chooser after removing `AssistActivity` assist intent filters during troubleshooting.
- Assistant holder remains Google in dumpsys (`android.app.role.ASSISTANT`).

## Target Behavior (Android 16+)
- In-app button requests `ROLE_ASSISTANT` via `RoleManager`.
- If role is granted, app UI reflects active assistant state.
- Long-press assistant invocation can resolve Clawsfree (chooser path) when role is not yet granted.
- Once role is granted, invocation routes consistently through Clawsfree assistant stack.

## Development Plan

### Phase 1 — Restore Chooser Visibility (Non-destructive)
- [ ] Re-introduce `AssistActivity` intent filters for:
  - `android.intent.action.ASSIST`
  - `android.intent.action.VOICE_COMMAND`
  - `android.intent.action.SEARCH_LONG_PRESS`
- [ ] Confirm Clawsfree appears in "Complete action using" list.
- [ ] Confirm selecting Clawsfree still starts recording path.

### Phase 2 — Make Role Requestable on Android 16+
- [ ] Add `ClawsfreeRecognitionService` (minimal valid implementation).
- [ ] Implement required callbacks safely:
  - `onStartListening`
  - `onStopListening`
  - `onCancel`
- [ ] Register recognition service in manifest with:
  - permission `android.permission.BIND_SPEECH_RECOGNITION_SERVICE`
  - intent action `android.speech.RecognitionService`
- [ ] Wire recognition service into `res/xml/voice_interaction_service.xml` using `android:recognitionService`.
- [ ] Keep `VoiceInteractionService` and `VoiceInteractionSessionService` declarations valid.

### Phase 3 — Role Flow Hardening
- [ ] Keep `RoleManager.ROLE_ASSISTANT` as primary in-app setup path.
- [ ] Improve result handling messages:
  - granted
  - denied/rejected
  - not requestable
- [ ] Keep fallback settings navigation only as secondary path.

### Phase 4 — Invocation Consistency Check
- [ ] Validate long-press start and short-press stop/send behavior after assistant changes.
- [ ] Confirm no regression in software `Record / Stop & Send` button behavior.
- [ ] Capture focused logs for media events if intermittent stop issue persists.

## Verification Matrix
- [ ] `adb shell cmd role get-role-holders android.app.role.ASSISTANT` shows `io.openclaw.telegramhandsfree` after grant.
- [ ] `dumpsys role` no longer reports only Google as holder after successful grant.
- [ ] No `Role is not requestable` error in logcat during request.
- [ ] Clawsfree appears in chooser list when no default assistant is fixed.
- [ ] End-user flow works without requiring repeated manual chooser selection.

## Risks / Notes
- Samsung/OEM role requirements can be stricter than AOSP baseline.
- Recognition service must be valid enough for role candidacy even if not used for rich ASR.
- Supporting both chooser path and role path is intentional for setup reliability.

## Exit Criteria
- `ROLE_ASSISTANT` can be granted to `io.openclaw.telegramhandsfree` on target Android 16+ device.
- In-app button transitions to active assistant state after grant.
- Clawsfree is visible in chooser when role is not yet fixed.
- Assistant invocation path is predictable and testable.
