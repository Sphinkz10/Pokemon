package com.rui.pvpgo

import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.OwnedPokemonField
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonMath
import com.rui.pvpgo.engine.PokemonSpecies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PvpGuidePolicyTest {
    private val species = PokemonSpecies(
        dex = 25, speciesId = "test-guide-25", name = "Guia Pikachu",
        familyId = "test-guide-family", baseAttack = 105,
        baseDefense = 115, baseStamina = 120
    )
    private val lowAtk = IvSpread(0, 15, 15)
    private fun owned(
        level: Double = 20.0,
        iv: IvSpread = lowAtk,
        cp: Int? = PokemonMath.cp(species, iv, level)
    ) = OwnedPokemon(
        id = "owned-guide", speciesId = species.speciesId, iv = iv,
        cp = cp, level = level, createdAtEpochMs = 1L
    )

    @Test fun missingCpRequiresReviewNotClaimingLeagueEligibility() {
        val p = owned(cp = null)
        assertEquals(PvpGuideVerdict.NEEDS_REVIEW,
            PvpGuidePolicy.assess(p, species, League.GREAT).verdict)
        assertFalse(PvpGuidePolicy.matches(p, species, League.GREAT, PvpGuideStage.WITHIN_LIMIT))
        assertTrue(PvpGuidePolicy.matches(p, species, League.GREAT, PvpGuideStage.REVIEW))
    }

    @Test fun missingLevelRequiresReview() {
        val p = owned().copy(level = null)
        assertEquals(PvpGuideVerdict.NEEDS_REVIEW,
            PvpGuidePolicy.assess(p, species, League.GREAT).verdict)
    }

    @Test fun uncertainFieldRequiresReviewEvenIfCpIsMathematicallyCorrect() {
        val p = owned().copy(uncertainFields = setOf(OwnedPokemonField.CP))
        assertTrue(PvpGuidePolicy.matches(p, species, League.GREAT, PvpGuideStage.REVIEW))
        assertFalse(PvpGuidePolicy.matches(p, species, League.GREAT, PvpGuideStage.IV_PATTERN))
    }

    @Test fun missingCatalogSpeciesCannotBeSilentlyConsideredValid() {
        assertEquals(PvpGuideVerdict.NEEDS_REVIEW,
            PvpGuidePolicy.assess(owned(), null, League.GREAT).verdict)
    }

    @Test fun wrongCatalogFormCannotBeReusedForCp() {
        assertEquals(PvpGuideVerdict.NEEDS_REVIEW,
            PvpGuidePolicy.assess(owned(), species.copy(speciesId = "another-form"), League.GREAT).verdict)
    }

    @Test fun storedCpMismatchDoesNotQualifyForIvPattern() {
        val p = owned().copy(cp = owned().cp!! + 1)
        assertTrue(PvpGuidePolicy.matches(p, species, League.GREAT, PvpGuideStage.REVIEW))
        assertFalse(PvpGuidePolicy.matches(p, species, League.GREAT, PvpGuideStage.IV_PATTERN))
    }

    @Test fun unsupportedLevel51DoesNotBecomeConfidentMatch() {
        val p = owned(level = 50.0).copy(level = 51.0)
        assertEquals(PvpGuideVerdict.NEEDS_REVIEW,
            PvpGuidePolicy.assess(p, species, League.MASTER).verdict)
    }

    @Test fun verifiedLowCpFitsGreatGeneralLimit() {
        val p = owned(level = 20.0)
        assertTrue(PvpGuidePolicy.matches(p, species, League.GREAT, PvpGuideStage.WITHIN_LIMIT))
        assertFalse(PvpGuidePolicy.matches(p, species, League.GREAT, PvpGuideStage.REVIEW))
    }

    @Test fun exactlyAtCapCountsAsWithinLimit() {
        val p = owned(level = 20.0)
        val ownCap = p.cp!!
        // The policy shares league.cpCap, so other limits must not be invented.
        assertEquals(1500, League.GREAT.cpCap)
        assertTrue(ownCap <= League.GREAT.cpCap!!)
    }

    @Test fun specimenOverLittleCapIsFlaggedWithoutDeletingAnyData() {
        val p = owned(level = 35.0)
        assertTrue(p.cp!! > 500)
        assertEquals(PvpGuideVerdict.OVER_LIMIT,
            PvpGuidePolicy.assess(p, species, League.LITTLE).verdict)
        assertTrue(PvpGuidePolicy.matches(p, species, League.LITTLE, PvpGuideStage.OVER_LIMIT))
        assertFalse(PvpGuidePolicy.matches(p, species, League.LITTLE, PvpGuideStage.WITHIN_LIMIT))
    }

    @Test fun switchingLeagueChangesClassificationNotOwnedData() {
        val p = owned(level = 35.0)
        assertTrue(PvpGuidePolicy.matches(p, species, League.LITTLE, PvpGuideStage.OVER_LIMIT))
        assertTrue(PvpGuidePolicy.matches(p, species, League.GREAT, PvpGuideStage.WITHIN_LIMIT))
        assertEquals(35.0, p.level!!, 0.0)
    }

    @Test fun masterNoGenericCpCapButDoesNotPromiseCompetitiveness() {
        val p = owned(level = 40.0)
        val a = PvpGuidePolicy.assess(p, species, League.MASTER)
        assertEquals(PvpGuideVerdict.WITHIN_LIMIT, a.verdict)
        assertEquals(null, a.cpCap)
        assertTrue(a.reason.contains("não tem teto"))
    }

    @Test fun greatIvPatternIsExploratoryAndNotARank() {
        val p = owned()
        assertTrue(PvpGuidePolicy.matches(p, species, League.GREAT, PvpGuideStage.IV_PATTERN))
        assertFalse(PvpGuidePolicy.hasExploratoryIvPattern(
            owned(iv = IvSpread(15, 15, 15)), League.GREAT
        ))
        assertTrue(PvpGuideStage.IV_PATTERN.explanation.contains("Não é ranking IV"))
    }

    @Test fun masterIvPatternPrefersFifteenAttackButIsNotRank() {
        val master = owned(iv = IvSpread(15, 14, 14))
        assertTrue(PvpGuidePolicy.hasExploratoryIvPattern(master, League.MASTER))
        assertFalse(PvpGuidePolicy.hasExploratoryIvPattern(owned(), League.MASTER))
        assertFalse(PvpGuidePolicy.hasExploratoryIvPattern(
            owned(iv = IvSpread(15, 12, 15)), League.MASTER))
    }

    @Test fun profileNeverBypassesCpOrDataEligibilityGate() {
        val p = owned(level = 35.0)
        assertTrue(PvpGuidePolicy.hasExploratoryIvPattern(p, League.LITTLE))
        assertFalse(PvpGuidePolicy.matches(p, species, League.LITTLE, PvpGuideStage.IV_PATTERN))
        assertTrue(PvpGuidePolicy.matches(p, species, League.LITTLE, PvpGuideStage.OVER_LIMIT))
    }

    @Test fun filteringDoesNotChangeAnyPersistedSpecimenFields() {
        val p = owned().copy(isFavorite = true, nickname = "Guardar")
        val before = p.copy()
        for (league in League.entries) for (stage in PvpGuideStage.entries) {
            PvpGuidePolicy.matches(p, species, league, stage)
        }
        assertEquals(before, p)
    }
}
