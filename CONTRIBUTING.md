# Contributing to BitChord

Thank you for contributing to BitChord. Follow these guidelines to ensure an efficient development and review process.

## Proposing Contributions
- Substantial features, refactors, or UI redesigns should normally be discussed before implementation. Open an issue on GitHub to outline your proposal.
- Straightforward bug fixes, documentation improvements, or minor corrections can be submitted directly as a Pull Request.

## Branch and Pull Request Workflow
Always check the repository for the current active version or development branch before starting work.

New work should normally be based on the latest active version or development branch rather than `main`, unless maintainers explicitly instruct otherwise. For example, during the `v1.6.x` release cycle, contributions branch from `v1.6.1`. As new release cycles begin, the target branch will advance accordingly.

When contributing:
- Fork the repository and create a descriptive branch (e.g., `fix/streaming-buffer`, `docs/translation-guide`).
- Keep pull requests focused on a single change or bug fix. Avoid mixing unrelated refactors, cosmetic tweaks, or mass reformatting with functional changes.
- Keep your branch reasonably synchronized with the target base branch.
- Provide a clear PR description explaining what was changed and why.
- Include relevant testing and validation details with your PR.
- Address review feedback promptly and keep discussions focused on technical merits.

## Development Setup
This tree is the desktop client: Compose Desktop on the JVM, for Windows and Linux. The tools are:
- **JDK**: Java Development Kit 21 (Eclipse Temurin 21 recommended). `JAVA_HOME` should point at it;
  the Gradle toolchain asks for 21 and there is no machine-specific path committed to pin it.
- **CMake, Ninja and a MinGW C++ compiler** (Windows, optional): they build the native window frame
  (`desktopApp/native/window`) and the SMTC media controls (`desktopApp/native/smtc`). Without them the
  app still builds and runs — it just falls back to Compose's own frameless window and has no media keys.
- **No Android SDK, no NDK, no party server.** Those belong to the upstream phone build.

## Build and Test Commands
Run Gradle commands from the repository root.

Build and run:
```bash
./gradlew :desktopApp:run              # the app itself
./gradlew :desktopApp:compileKotlin    # compile only
./gradlew :desktopApp:packageMsi       # Windows installer
./gradlew :desktopApp:windowsPortable  # Windows portable zip
./gradlew :desktopApp:packageDeb       # Linux .deb (run this on Linux)
```

Windows:
```powershell
.\gradlew.bat :desktopApp:run
.\gradlew.bat :desktopApp:packageMsi
```

Tests:
```bash
./gradlew :desktopApp:test
./gradlew :desktopApp:test --tests "com.music.bitchord.desktop.DesktopCrossfadePositionTest"
```

Two suites need a cached dependency that is not always available offline — `:shared:jvmTest` wants `mockwebserver` and `:desktopUi:jvmTest` wants `kotlinx-datetime`. Run them without
`--offline` once so Gradle can fetch them; CI only compiles, so a red local suite there is not a
CI failure.

## Code Quality
- Follow idiomatic Kotlin and Jetpack Compose conventions.
- Maintain the project's existing architecture and separation of concerns (UI, domain logic, playback services, and data repositories).
- Write unit tests for new logic, fixes, and edge cases.
- Contributors should run relevant tests, must not introduce new failures, and should document known pre-existing failures when applicable.
- Keep diffs focused and minimal. Avoid unnecessary third-party dependencies.

## Audio Changes and Telemetry
For audio changes: application telemetry must not be presented as proof of physical hardware behavior unless that hardware behavior was actually verified.

Distinguish clearly between source metadata, decoder format, internal DSP format, what the output stream accepted (WASAPI on Windows, ALSA on Linux), advertised device capability, and external physical endpoint behavior.

## Review Process
All pull requests require review and approval by repository maintainers before merging. Maintainers review changes for correctness, architecture fit, and maintainability.

## Dependencies and Licensing
BitChord is licensed under the **GNU General Public License v3.0 (GPLv3)**.

All contributed code and dependencies must be strictly compatible with GPLv3. Avoid introducing external dependencies unless strictly necessary; any new dependency must be evaluated for necessity, binary size, and license compliance.
