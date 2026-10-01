# V1.49 manual Companion team linking

Pure Kotlin isolated tests (`DomainStubs.kt` contracts mirror domain models) for manually selected saved teams from Room. No battle recognition or detector enabled. The selection is transient and never written to BattleRecord, LiveBattleState, or the engine.

Run:
```
kotlinc app/src/main/java/com/rui/pvpgo/live/CompanionManualTeamPolicy.kt tests/v1_49_manual_team/DomainStubs.kt tests/v1_49_manual_team/CompanionManualTeamPolicyTest.kt -include-runtime -d /tmp/manual_team_test.jar && java -jar /tmp/manual_team_test.jar
```
