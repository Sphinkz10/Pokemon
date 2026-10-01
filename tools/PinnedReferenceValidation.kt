import com.rui.pvpgo.engine.*

fun main() {
    val pin = PvPokeReferencePin.current
    check(pin.commitSha == "a93147bf1f2e829758958bfb5b37e56bbadc9678")
    check(pin.shortSha == "a93147b")
    check(pin.siteVersion == "1.40.1.3")
    check(pin.actionLogicUrl.contains(pin.commitSha))
    check(pin.battleUrl.contains(pin.commitSha))
    check(PvPokeReferencePin.HISTORICAL_RESULT_VERSION != pin.siteVersion)

    val matrix = BattleParityFixtureSuite.run()
    check(matrix.externalResults.size == 3)
    check(matrix.externalResults.all { it.status == FixtureStatus.BLOCKED })
    check(matrix.historicalExternalResults.size == 3)
    check(!matrix.externalBaselineConfirmed)

    val parity = BattleParityGate.current()
    check(parity.upstream.contains(pin.siteVersion))
    check(parity.upstream.contains(pin.shortSha))
    check(parity.capabilities.first { it.id == "shield-policy" }.status == ParityStatus.VERIFIED)
    check(parity.capabilities.first { it.id == "move-timing" }.status == ParityStatus.PARTIAL)
    check(parity.capabilities.first { it.id == "action-pre-dp" }.status == ParityStatus.VERIFIED)
    check(parity.capabilities.first { it.id == "action-dp-core" }.status == ParityStatus.PARTIAL)
    check(parity.capabilities.first { it.id == "action-post-plan" }.status == ParityStatus.VERIFIED)
    check(parity.capabilities.first { it.id == "action-policy" }.status == ParityStatus.PARTIAL)
    check(parity.capabilities.first { it.id == "independent-reference" }.status == ParityStatus.VERIFIED)
    check(parity.matchupAdviceGateOpen)
    check(!parity.exactPinMatchupAdviceGateOpen)

    println("PINNED_REFERENCE_V1_29_OK")
    println("pin=${pin.siteVersion}@${pin.commitSha}")
    println("historical=${PvPokeReferencePin.HISTORICAL_RESULT_VERSION} currentExternalBlocked=${matrix.externalResults.size}")
    println("verified=${parity.verifiedCount} partial=${parity.partialCount} pending=${parity.pendingCount} gate=${parity.matchupAdviceGateOpen}")
}
