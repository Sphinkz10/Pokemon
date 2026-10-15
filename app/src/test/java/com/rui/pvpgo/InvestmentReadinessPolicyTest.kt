package com.rui.pvpgo

import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonMath
import com.rui.pvpgo.engine.PokemonSpecies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestmentReadinessPolicyTest {
    private val species = PokemonSpecies(
        dex = 12, speciesId = "example", name = "Exemplo", form = "Normal",
        baseAttack = 110, baseDefense = 115, baseStamina = 120
    )
    private val iv = IvSpread(0, 15, 15)
    private fun record(
        cp: Int? = null,
        level: Double? = null,
        fast: String? = null,
        charged: List<String> = emptyList()
    ) = OwnedPokemon(
        id = "candidate", speciesId = species.speciesId, iv = iv,
        cp = cp, level = level, fastMoveId = fast,
        chargedMoveIds = charged, createdAtEpochMs = 1L
    )
    private fun check(report: InvestmentReadinessReport, label: String): ReadinessCheck =
        report.checks.single { it.name == label }

    @Test fun manualIvOnlyEntryMustAskForCpLevelAndMoves() {
        val result = InvestmentReadinessPolicy.inspect(record(), species, League.GREAT)
        assertEquals(ReadinessSummary.NEEDS_DATA, result.summary)
        for (field in listOf("CP atual", "Nível atual", "Coerência de CP", "Ataque rápido", "Ataque carregado")) {
            assertEquals(field, ReadinessCheckStatus.MISSING, check(result, field).status)
        }
    }

    @Test fun unknownSpeciesDoesNotClaimRankOrEligibility() {
        val result = InvestmentReadinessPolicy.inspect(record(), null, League.GREAT)
        assertEquals(ReadinessCheckStatus.MISSING, check(result, "Espécie e forma").status)
        assertEquals(ReadinessCheckStatus.MISSING, check(result, "Limites base da liga").status)
    }

    @Test fun validCpLevelAndMovesPassOnlyGenericInputs() {
        val level = 20.0
        val cp = PokemonMath.cp(species, iv, level)
        val result = InvestmentReadinessPolicy.inspect(
            record(cp = cp, level = level, fast = "fast", charged = listOf("charged")),
            species, League.GREAT
        )
        assertEquals(ReadinessSummary.BASIC_DATA_CONSISTENT, result.summary)
        assertEquals(ReadinessCheckStatus.CHECKED, check(result, "Coerência de CP").status)
        assertEquals(ReadinessCheckStatus.CHECKED, check(result, "Limites base da liga").status)
        assertEquals(ReadinessCheckStatus.MISSING, check(result, "Custo do investimento").status)
        assertTrue(result.headline.contains("por validar"))
    }

    @Test fun noCostPackMeansNoInventedStardustOrCandy() {
        val report = InvestmentReadinessPolicy.inspect(record(), species, League.ULTRA)
        assertEquals(ReadinessCheckStatus.MISSING, check(report, "Custo do investimento").status)
        assertTrue(check(report, "Custo do investimento").explanation.contains("pack de custos validado"))
        assertTrue(InvestmentReadinessPolicy.LIMITATION.contains("Candy/Stardust"))
    }

    @Test fun savedCpNotMatchingDerivedCpBlocksReadiness() {
        val level = 20.0
        val derived = PokemonMath.cp(species, iv, level)
        val report = InvestmentReadinessPolicy.inspect(
            record(cp = derived + 1, level = level, fast = "fast", charged = listOf("charged")),
            species, League.GREAT
        )
        assertEquals(ReadinessSummary.NEEDS_CORRECTION, report.summary)
        assertEquals(ReadinessCheckStatus.RECHECK, check(report, "Coerência de CP").status)
    }

    @Test fun cpOverLeagueLimitIsNotEligibleEvenWithPerfectData() {
        val level = 50.0
        val cp = PokemonMath.cp(species, iv, level)
        assertTrue(cp > 500)
        val report = InvestmentReadinessPolicy.inspect(
            record(cp = cp, level = level, fast = "fast", charged = listOf("charged")),
            species, League.LITTLE
        )
        assertEquals(ReadinessSummary.NEEDS_CORRECTION, report.summary)
        assertEquals(ReadinessCheckStatus.BASE_INELIGIBLE, check(report, "Limites base da liga").status)
        assertTrue(check(report, "Limites base da liga").explanation.contains("CP_CAP_EXCEEDED"))
    }

    @Test fun masterLeagueDoesNotInventCpCap() {
        val level = 45.0
        val cp = PokemonMath.cp(species, iv, level)
        val report = InvestmentReadinessPolicy.inspect(
            record(cp = cp, level = level, fast = "fast", charged = listOf("charged")),
            species, League.MASTER
        )
        assertEquals(ReadinessCheckStatus.CHECKED, check(report, "Limites base da liga").status)
    }

    @Test fun missingCpIsNotReconstructedAsAnObservedCp() {
        val report = InvestmentReadinessPolicy.inspect(
            record(cp = null, level = 30.0, fast = "fast", charged = listOf("charged")),
            species, League.GREAT
        )
        assertEquals(ReadinessCheckStatus.MISSING, check(report, "CP atual").status)
        assertEquals(ReadinessCheckStatus.MISSING, check(report, "Coerência de CP").status)
        assertEquals(ReadinessSummary.NEEDS_DATA, report.summary)
    }

    @Test fun missingLevelPreventsPowerupPricing() {
        val report = InvestmentReadinessPolicy.inspect(
            record(cp = 500, level = null, fast = "fast", charged = listOf("charged")),
            species, League.GREAT
        )
        assertEquals(ReadinessCheckStatus.MISSING, check(report, "Nível atual").status)
        assertTrue(check(report, "Nível atual").explanation.contains("power-ups"))
    }

    @Test fun missingChargedMoveDoesNotInferAttackFromMovePool() {
        val report = InvestmentReadinessPolicy.inspect(
            record(fast = "fast", charged = emptyList()), species, League.GREAT
        )
        assertEquals(ReadinessCheckStatus.CHECKED, check(report, "Ataque rápido").status)
        assertEquals(ReadinessCheckStatus.MISSING, check(report, "Ataque carregado").status)
    }

    @Test fun oneChargedMoveNeverImpliesSecondMoveUnlocked() {
        val report = InvestmentReadinessPolicy.inspect(
            record(charged = listOf("move1")), species, League.GREAT
        )
        assertTrue(check(report, "Ataque carregado").explanation.contains("não se presume um segundo"))
    }

    @Test fun knownMegaCannotBeMarkedAsGenericReady() {
        val mega = species.copy(speciesId = "example_mega", tags = setOf("mega"))
        val owned = record().copy(speciesId = mega.speciesId)
        val report = InvestmentReadinessPolicy.inspect(owned, mega, League.GREAT)
        assertEquals(ReadinessSummary.NEEDS_CORRECTION, report.summary)
        assertEquals(ReadinessCheckStatus.BASE_INELIGIBLE, check(report, "Limites base da liga").status)
    }

    @Test fun knownPrimalIsIneligibleForGenericStandardFormat() {
        val primal = species.copy(speciesId = "example_primal", tags = setOf("primal"))
        val owned = record().copy(speciesId = primal.speciesId)
        val report = InvestmentReadinessPolicy.inspect(owned, primal, League.GREAT)
        assertEquals(ReadinessSummary.NEEDS_CORRECTION, report.summary)
        assertEquals(ReadinessCheckStatus.BASE_INELIGIBLE, check(report, "Limites base da liga").status)
    }

    @Test fun mismatchedFormNeverGetsCheckedAsCatalogIdentity() {
        val report = InvestmentReadinessPolicy.inspect(record(), species.copy(speciesId = "other"), League.GREAT)
        assertEquals(ReadinessCheckStatus.MISSING, check(report, "Espécie e forma").status)
    }

    @Test fun allChecksHaveExplanationsAndNextAction() {
        val report = InvestmentReadinessPolicy.inspect(record(), species, League.ULTRA)
        assertTrue(report.checks.all { it.name.isNotBlank() && it.explanation.isNotBlank() })
        assertTrue(report.nextAction.isNotBlank())
        assertEquals(report.checks.size, report.checkedCount + report.outstandingCount)
    }

    @Test fun eventCupAndMetaAreNeverDeclaredConfirmed() {
        val text = InvestmentReadinessPolicy.LIMITATION
        assertTrue(text.contains("Regras de copas e eventos não estão verificadas"))
        assertTrue(text.contains("meta"))
        assertFalse(text.contains("investimento aprovado"))
    }
}
