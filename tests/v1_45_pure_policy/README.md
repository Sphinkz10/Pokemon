Run without Android using Kotlin JVM (`kotlinc` must be installed):

`kotlinc tests/v1_45_pure_policy/{ContractStubs,DomainStubs,BattlesGoldenPolicyTest}.kt app/src/main/java/com/rui/pvpgo/BattlesGoldenPolicy.kt -include-runtime -d /tmp/v145-policy.jar && java -jar /tmp/v145-policy.jar`

**Scope:** Isolated presentation-policy tests compiled against narrow stubs. These do not demonstrate a full Android/Compose build and do not replace emulator/device tests.
