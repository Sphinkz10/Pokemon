# V1.48 offline Companion gates

Run `bash tests/v1_48_frame_quality/run_tests.sh` in the project root with a Kotlin compiler/JDK installed. No Gradle or Android SDK is required for these **pure-policy and stub-coordinator** tests. They cannot certify the Android Compose runtime or actual MediaProjection service.

Frame quality checks: session changes, invalid frames, duplicate IDs, rotation/resolution shifts, stale stream, recovery, and never inferring Pokémon/moves. Semantic admission: default DENY, consented session identity, detector approval, quality gate, score/freshness, multi-frame confirmation, de-duplication and resets. Coordinator integration uses a fake in-memory engine reducer to verify that no event reaches the reducer without admission. The V1.47 manual notes regression is also executed.
