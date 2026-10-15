# Pokémon Companion V3 · Executed Android emulator gates (PR #20)

## Why this phase exists
PRs #11–#19 were validated mainly through Android compilation, JVM unit tests, CI structure checks and generated debug APKs. That is **not** evidence that Android preferences persist, Room SQL works on a real Android runtime, or the launch Activity can reach the foreground. The existing `.github/workflows/android-room-instrumentation.yml` ran only on manual dispatch or a few main-branch paths and was therefore not a PR merge gate.

## Implemented
- Actual **API 35 x86_64 Android emulator** started with hardware-assisted virtualization on GitHub Actions for PRs to main, main pushes and manual workflow dispatch.
- Gradle `:app:connectedDebugAndroidTest` runs all current instrumented suites, not just Python structural checks or Kotlin JVM tests.
- Source-contract gate asserts presence of runner, PR trigger, real Android tests, resource/SQLite coverage and that JUnit XML verification is part of the workflow.
- **JUnit execution gate** reads generated emulator XML from `app/build/outputs/androidTest-results/connected/**`. It requires every named suite with minimum passing case count and no failures/skips. No XML = no validation.
- Test results/reports are attached to each GitHub workflow run, even on failure.
- New `InvestmentJourneyAndroidTest`: seven instrumented cases for actual SQLite Room + on-device SharedPreferences + the production power-up costs/CP/investment engine, including explicit zero/unknown, family isolation, 40-level XL boundary, consent and CP inconsistency.
- New `MainActivityLaunchAndroidTest`: the actual launcher activity is started and must reach Android's resumed lifecycle.

## Minimum emulator case counts
| Suite | Minimum | Scope |
| --- | ---: | --- |
| SavedTeamRoomIntegrationTest | 6 | Real SQLite saves, updates, delete and reopen |
| UserDatabaseMigrationTest | 1 | Historic Room v1→v2 migration preserves collection/team |
| UserResourceStoreAndroidTest | 4 | Android preferences persisted; family isolation and zero |
| InvestmentJourneyAndroidTest | 7 | End-to-end persistence-to-budget logic on Android without screen tapping |
| MainActivityLaunchAndroidTest | 1 | Actual MainActivity lifecycle launch/resume |
| **Total** | **19** | **Executable Android instrumented cases** |

These 19 instrumented tests are a **new and distinct QA counter**; they do not magically complete the historic **110 full app UX/E2E scenarios** which require actual UI interactions, device/a11y checks and user journeys.

## Device acceptance and provenance
- Android CI device is a **cloud emulated API 35 x86_64** profile, not the user's own phone.
- Emulator proves runtime persistence, selected engine code paths, migration and activity launch.
- Emulator does **not** prove all screen navigation, full UI accessibility, network freshness, installation conflict behavior on older live packages, or Pokémon GO accuracy.
- Keep a separate real handset matrix: Android 13/14/15/16, low-memory devices, font-scale 200%, accessibility/TalkBack, upgrading older versions with existing collection data, camera/import, sleeping/resuming, offline/online, install/uninstall, UI response latency.

## CI gates and honest reporting
1. Main debug APK build + JVM unit tests green.
2. Instrumented emulator runner completes without error, and test report XML passes all 19 required cases.
3. The PR is merged only after both workflows are green.
4. Report the number of **real instrumented cases passing**, separate from 0/110 manual E2E, not as an invented global completion bump.

References:
- https://github.com/ReactiveCircus/android-emulator-runner
- https://developer.android.com/training/testing/instrumented-tests
