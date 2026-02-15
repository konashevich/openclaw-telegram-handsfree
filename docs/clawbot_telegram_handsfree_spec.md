# Nova Walkie-Talkie: Project Specification

## 1. Objective
Build a native Android application that serves as a dedicated, "eyes-free" interface for an Telegram group. The app focuses on hands-free operation while driving, using physical Bluetooth triggers and automatic audio responses.

### MVP Delivery Rule (Mandatory)
- Build and deliver only the minimum functioning application described in this specification.
- Do **not** add extra features, setup wizards, diagnostics panels, or optional UX enhancements unless they are required to satisfy this spec.
- Every implementation step must directly contribute to shipping the working minimum app behavior in sections 2-5.
- UI should remain minimal and utilitarian; no additional UI scope should be introduced during PoC delivery.

---

## 2. Technical Stack
- **Language:** Kotlin (Native Android)
- **Engine:** [TDLib](https://core.telegram.org/tdlib) (Telegram Database Library) for high-performance background messaging.
- **Service Type:** Android `VoiceInteractionService` (Digital Assistant slot) and a persistent `Foreground Service`.
- **Audio Format:** OGG Opus (Native Telegram voice format).

---

## 3. Operational Logic

### A. The "Voice Assistant" Integration
- The app registers as a **Digital Assistant** on the Android system.
- **Trigger:** A **Long-Press** of the Bluetooth Media Button (or a steering wheel clicker) invokes the app from anywhere (Home screen, Maps, etc.).
- **Initial Action:** Upon trigger, the app provides a short "beep" and begins recording audio immediately via the car's Bluetooth microphone.

### B. Message Termination & Safety Net
- **Primary Stop Method (Manual):** A **second tap** of the Play/Media button stops the recording and sends it instantly to the Nova group.
- **Secondary Stop Method (Safety Net):** A generous **20-second Auto-Silence timer**. 
  - If no speech is detected for 20 seconds, the app assumed the user forgot to tap "stop" and finishes the session automatically. 
  - This long duration ensures the user is never cut off during thinking pauses.

### C. Automatic Playback
- **Background Monitor:** The app continuously monitors the specific Telegram Group for incoming voice messages from the Nova bot.
- **Auto-Fetch & Play:** New responses are downloaded in the background and played automatically through the Bluetooth audio channel.
- **Audio Focus:** The app ducks/pauses other media (like music or audiobooks) while Nova is speaking.

---

## 4. User Experience (Driving Flow)
1. **Initiate:** Long-press steering wheel button -> *Beep heard*.
2. **Speak:** "Nova, remind me to check the tire pressure when I get home."
3. **Finish:** Tap button once -> *Recording stops*.
4. **Wait:** (Eyes remain on the road).
5. **Listen:** Nova's voice response plays automatically over the car speakers.

---

## 5. Configuration & Security
- **Hardcoded Context:** The Telegram API credentials and the specific Group ID are configured within the app to ensure zero-setup daily use.
- **Privacy:** Since it uses TDLib, it operates as a secure, independent Telegram client.
