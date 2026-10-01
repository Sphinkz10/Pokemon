# V1.46 pure Kotlin B04 policy tests

These are lightweight, deliberately isolated Kotlin policy checks with test-only domain contracts, NOT Android tests. From project root:

```
kotlinc tests/v1_46_b04_policy/DomainStubs.kt app/src/main/java/com/rui/pvpgo/BattleTeamCoveragePolicy.kt tests/v1_46_b04_policy/BattleTeamCoveragePolicyTest.kt -include-runtime -d /tmp/b04.jar
java -jar /tmp/b04.jar
```

Expected: `B04_COVERAGE_POLICY_PASS 36 checks`.
