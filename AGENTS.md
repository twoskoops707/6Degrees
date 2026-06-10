# AGENTS.md

Guidance for cloud agents working in this repository.

## Product

**6Degrees** is a single-module Android OSINT app (`com.twoskoops707.sixdegrees`). There is no backend server, Docker stack, or `package.json`. The app talks to public OSINT APIs over the network and stores report history locally in Room.

## Cursor Cloud specific instructions

### Prerequisites (VM snapshot)

- **JDK 17** (`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`) — matches CI in `.github/workflows/release.yml`
- **Android SDK** at `$HOME/Android/Sdk` with:
  - `platforms;android-36`
  - `build-tools;36.0.0` (Gradle may also pull `build-tools;35.0.0`)
  - `platform-tools`
  - Optional for UI/instrumented tests: `emulator`, `system-images;android-34;google_apis;x86_64`, AVD `sixdegrees-test`

### `local.properties`

Gradle requires `sdk.dir`. This file is gitignored. Regenerate on each session if missing:

```bash
printf 'sdk.dir=%s\n' "$HOME/Android/Sdk" > local.properties
```

### Build and test (no device)

See `README.md` and CI workflow for the canonical build command.

| Task | Command |
|------|---------|
| Build debug APK | `./gradlew assembleDebug --no-daemon` |
| Unit tests | `./gradlew test --no-daemon` |
| Lint | `./gradlew lintDebug --no-daemon` (may fail on existing baseline issues; CI does not run lint) |

APK output: `app/build/outputs/apk/debug/app-debug.apk`

### Emulator / device (optional)

There is **no KVM** in the default cloud VM. The emulator runs with `-no-accel` and software rendering; boot and UI are slow and System UI ANRs are common. Prefer JVM unit tests and `assembleDebug` for fast verification.

When an emulator is needed:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"

# Start in tmux (long-running)
tmux -f /exec-daemon/tmux.portal.conf new-session -d -s android-emulator -- \
  emulator -avd sixdegrees-test -no-window -no-audio -no-boot-anim \
  -gpu swiftshader_indirect -no-snapshot -no-accel

# Wait for full boot (can take several minutes)
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 10; done

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.twoskoops707.sixdegrees/.MainActivity
./gradlew connectedDebugAndroidTest --no-daemon
```

### API keys and external services

No `.env` file. Optional paid API keys are entered in the in-app **Settings** UI at runtime. Live OSINT searches need outbound internet from the device/emulator.

### Gotchas

- First `./gradlew` run downloads Gradle 8.13 and dependencies (~few minutes).
- `gradle.properties` disables the daemon and limits workers (`org.gradle.workers.max=1`) — builds are slower but more stable in constrained VMs.
- Tor, Termux, and Orbot integrations require companion Android apps; not needed for core build/test.
- Guardian Project Maven repo is required for Tor deps (`settings.gradle.kts`).
