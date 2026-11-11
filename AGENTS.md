# AGENTS.md

This file provides guidance and context for AI coding agents working in the **PiSSH** (`com.minimal.pissh`) repository.

## Project Overview

**PiSSH** is a minimal, fast Android SSH client built with Kotlin and Jetpack Compose (Material 3 / Material You), designed for connecting to Raspberry Pi and Linux hosts over local LAN or Tailscale.

- **Package / Namespace**: `com.minimal.pissh`
- **Language & Toolchain**: Kotlin `1.9.24`, JDK `17`, Android Gradle Plugin `8.5.2`, Gradle `8.7`
- **Android SDK Levels**: `minSdk = 26` (Android 8.0), `targetSdk = 34`, `compileSdk = 34`
- **UI Framework**: Jetpack Compose (`2024.06.00` BOM, Material 3, dynamic Material You colors on Android 12+ with custom emerald-tinted fallback schemes)
- **SSH & Crypto Stack**: `com.github.mwiede:jsch:0.2.18` (modern JSch fork with OpenSSH key format, Ed25519, and RSA-SHA2 support) + `org.bouncycastle:bcprov-jdk18on:1.78.1`
- **Concurrency**: Kotlin Coroutines & Flow (`StateFlow`, `SharedFlow`, `Mutex`)

---

## Repository Structure

This is a single-module Android project (`:app`):

- [`app/build.gradle.kts`](app/build.gradle.kts) — App module configuration, dependencies, signing config, and Compose setup.
- [`app/proguard-rules.pro`](app/proguard-rules.pro) — R8/ProGuard keep rules for JSch, BouncyCastle, and app classes.
- [`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml) — Permissions (`INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `WAKE_LOCK`), `MainActivity`, and `SshForegroundService`.
- `app/src/main/java/com/minimal/pissh/`:
  - [`MainActivity.kt`](app/src/main/java/com/minimal/pissh/MainActivity.kt) — Single-activity entry point (`ComponentActivity`). Binds to `SshForegroundService`, applies dynamic Material You theming, requests `POST_NOTIFICATIONS` permission on Android 13+, toggles `FLAG_KEEP_SCREEN_ON`, writes uncaught crash stack traces to `filesDir/last_crash.txt`, and routes between `ConnectionScreen` and `TerminalScreen`.
  - [`ConnectionScreen.kt`](app/src/main/java/com/minimal/pissh/ConnectionScreen.kt) — Material You connection management screen. Includes a 4-slot saved profile carousel (`FilterChip`), pill-shaped text fields (`MaterialYouPillTextField`), Password vs. OpenSSH Private Key authentication (file picker + inline editor + optional passphrase), Keep Alive switch, terminal emulation selector, error/status banners, and an animated floating save confirmation banner (`SaveSuccessPopUp`).
  - [`TerminalScreen.kt`](app/src/main/java/com/minimal/pissh/TerminalScreen.kt) — Interactive monospace terminal screen. Renders terminal lines via `LazyColumn`, overlays a blinking block/inverted cursor (`buildLineWithCursor`), measures viewport cells via `rememberTextMeasurer` to trigger SSH PTY resize (`SIGWINCH`), supports pinch-to-zoom (`9sp..15sp`), long-press word selection and drag selection with translucent highlights, Material 3 contextual selection action bar, floating dropdown context menu (Copy, Paste, Select All), handles hardware key events (`handleHardwareKeyEvent` including `Ctrl+Shift+C`/`Ctrl+Shift+V`), captures soft keyboard input via an invisible `BasicTextField` using a `\u200B` sentinel, and provides a `TouchAccessoryBar` (`CTRL` latch, `ESC`, `TAB`, VT100 arrows, `Ctrl+C`, `Ctrl+D`, `Ctrl+Z`, `Select`/`Copy`, `Paste`).
  - [`TerminalSelection.kt`](app/src/main/java/com/minimal/pissh/TerminalSelection.kt) — Terminal text selection model (`TerminalSelection`) and word boundary resolver (`findWordBoundary`) supporting single-line and multi-line selection ranges, trailing whitespace trimming, and extraction for clipboard operations.
  - [`TerminalViewModel.kt`](app/src/main/java/com/minimal/pissh/TerminalViewModel.kt) — Manages `TerminalUiState`, persists 4 `HostProfile` slots in `SharedPreferences` (`pissh_host_profiles`), encrypts credentials at rest via `KeyStoreCrypto` (hardware-backed `AndroidKeyStore` AES-256-GCM), coordinates with `SshForegroundService`, and implements `AnsiTerminalBuffer` (streaming UTF-8 decoder and VT100/ANSI escape sequence parser).
  - [`SshClient.kt`](app/src/main/java/com/minimal/pissh/SshClient.kt) — Coroutine-safe JSch wrapper running on `Dispatchers.IO`. Exposes `incomingBytes: SharedFlow<ByteArray>`, `isConnected: StateFlow<Boolean>`, and `disconnectReason: SharedFlow<String?>`. Configures BouncyCastle EdDSA/X25519 providers for Android compatibility and manages the `xterm-256color` `ChannelShell` PTY.
  - [`SshForegroundService.kt`](app/src/main/java/com/minimal/pissh/SshForegroundService.kt) — Bound and started `specialUse` foreground service owning the `SshClient` instance and a 4-hour safety-capped `PARTIAL_WAKE_LOCK` (`PiSSH::SessionWakeLock`) to keep interactive SSH sessions alive while switching apps.
- `app/src/test/java/com/minimal/pissh/`:
  - [`AnsiTerminalBufferTest.kt`](app/src/test/java/com/minimal/pissh/AnsiTerminalBufferTest.kt) — Unit tests for ANSI/VT100 parsing, 16/256-color SGR styling, `zsh-autosuggestions` grey rendering, split multi-byte UTF-8 packet decoding, cursor navigation, and cursor rendering.
  - [`TerminalSelectionTest.kt`](app/src/test/java/com/minimal/pissh/TerminalSelectionTest.kt) — Unit tests for `TerminalSelection` ranges, multi-line text extraction, and `findWordBoundary` parsing (paths, IPs, URLs).
  - [`TerminalUiStateTest.kt`](app/src/test/java/com/minimal/pissh/TerminalUiStateTest.kt) — Unit tests for `TerminalUiState`.

---

## Build, Test, and CI Commands

> **Note**: If `gradlew` / `gradlew.bat` is not present in the working tree, generate it first with `gradle wrapper --gradle-version 8.7` or invoke `gradle` directly.

- **Run unit tests**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
- **Build Debug APK**:
  ```bash
  ./gradlew assembleDebug
  ```
- **Build Release APK**:
  ```bash
  ./gradlew assembleRelease
  ```
- **Release Signing**:
  `app/build.gradle.kts` reads `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` from environment variables for the `release` signing config, and automatically falls back to the `debug` signing config if unset.

---

## Architectural & Coding Guidelines

1. **Android Lifecycle & Compose Safety**:
   - Keep all runtime permission launchers inside Compose (`rememberLauncherForActivityResult`) or register them strictly before `onStart()`.
   - `MainActivity` declares `android:configChanges="orientation|screenSize|screenLayout|keyboard|keyboardHidden|smallestScreenSize"` and `windowSoftInputMode="adjustResize"` so terminal sessions and soft-keyboard transitions do not recreate the activity.

2. **SSH & Cryptography Guardrails**:
   - **Ed25519 / X25519 on Android API 26–33**: Do not remove `configureAndroidCryptoDefaults()` in `SshClient.kt`. Android's JCA on API 26–33 does not expose JDK 15's `java.security.spec.EdECPoint`, so JSch must be explicitly wired to `com.jcraft.jsch.bc.KeyPairGenEdDSA`, `SignatureEd25519`, `SignatureEd448`, and `XDH`.
   - **Memory Hygiene**: Always zero out private key and passphrase `ByteArray` buffers (`fill(0)`) inside a `finally` block after calling `jsch.addIdentity(...)`.
   - **Credential Storage**: Any sensitive field persisted to `SharedPreferences` (`password`, `privateKey`, `passphrase`) must be encrypted with `KeyStoreCrypto.encrypt(...)` (which prefixes ciphertexts with `enc:v1:`) and decrypted with `KeyStoreCrypto.decrypt(...)`.

3. **Terminal Buffer (`AnsiTerminalBuffer`) & IME Input**:
   - **Scrollback Preservation**: Bare `CSI J` (`\u001B[J` or `\u001B[0J`) must only erase from the current cursor position to the end of the screen—never clear the entire scrollback buffer (which is reserved for `CSI 2J`, `CSI 3J`, and `ESC c`). Shells like `zsh` emit `CSI J` on every prompt redraw.
   - **Streaming UTF-8 Decoding**: Incoming SSH bytes can split multi-byte UTF-8 code points across TCP packet boundaries. Always decode through `AnsiTerminalBuffer.appendBytes(ByteArray)` so incomplete trailing sequences are buffered in `leftoverBytes`.
   - **Soft Keyboard Capture**: `TerminalScreen.kt` uses an invisible `BasicTextField` initialized with a zero-width space sentinel (`"\u200B"`) and `neverEqualPolicy()`. If the sentinel is missing on `onValueChange`, emit Backspace (`0x7F`); otherwise forward newly typed characters and reset to the sentinel. Preserve this mechanism when touching keyboard handling.
   - **Cursor Rendering**: `buildLineWithCursor` in `TerminalScreen.kt` renders an inverted span when the cursor rests on a printable non-space character and a solid block (`█`) when on a space or at/past the end of a line so trailing whitespace trimming never hides the cursor.

4. **Foreground Service & Battery Safety**:
   - `SshForegroundService` uses `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`. On Android 14+ (`UPSIDE_DOWN_CAKE`), `startForeground` must pass `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE`.
   - Always bound the partial wake lock with a timeout (`acquire(4 * 60 * 60 * 1000L)`) and release it cleanly on disconnect, `onTaskRemoved`, and `onDestroy` to prevent runaway battery drain.

5. **Testing**:
   - Pure JVM unit tests run via JUnit 4 (`app/src/test/java/com/minimal/pissh/`).
   - When adding or modifying escape sequence handling, cursor logic, or `TerminalUiState` behavior, add corresponding test cases in `AnsiTerminalBufferTest.kt` or `TerminalUiStateTest.kt` and verify with `./gradlew testDebugUnitTest`.
