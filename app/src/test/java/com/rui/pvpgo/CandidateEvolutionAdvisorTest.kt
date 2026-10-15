package com.rui.pvpgo

import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.RankSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateEvolutionAdvisorTest {
    private fun species(
        id: String,
        dex: Int,
        children: List<String> = emptyList(),
        released: Boolean = true,
        tags: Set<String> = emptySet()
    ) = PokemonSpecies(
        dex = dex, name = id, speciesId = id, evolutionSpeciesIds = children,
        released = released, tags = tags,
        baseAttack = 120, baseDefense = 120, baseStamina = 120
    )

    private fun owned(id: String, type: String, iv: IvSpread = IvSpread(14, 15, 15)) =
        OwnedPokemon(id = id, speciesId = type, iv = iv, createdAtEpochMs = 1L)

    private val source = species("seed", 1, listOf("bud", "mega_bud", "hidden", "unknown"))
    private val bud = species("bud", 2, listOf("flower"))
    private val flower = species("flower", 3)
    private val mega = species("mega_bud", 4, tags = setOf("mega"))
    private val hidden = species("hidden", 5, released = false)
    private val catalog = listOf(source, bud, flower, mega, hidden)
    private val settings = RankSettings(ivFloor = 14)

    @Test fun targetListFollowsRealCatalogGraphNotFamilyIdGuess() {
        assertEquals(listOf("bud", "flower"), CandidateEvolutionAdvisor.targets(source, catalog).map { it.speciesId })
    }

    @Test fun missingEdgesAndUnreleasedAndMegaStayOutOfPreview() {
        assertFalse(CandidateEvolutionAdvisor.targets(source, catalog).any { it.speciesId in setOf("mega_bud", "hidden", "unknown") })
    }

    @Test fun noKnownEvolutionReturnsEmptyOverview() {
        val seed = species("solo", 10)
        val result = CandidateEvolutionAdvisor.evaluate(seed, owned("candidate", "solo"), emptyList(), listOf(seed), League.GREAT, settings)
        assertTrue(result.rows.isEmpty())
        assertEquals(0, result.totalCatalogTargets)
    }

    @Test fun candidateRankUsesEvolutionSpeciesStatsAndChosenLeague() {
        val iv = IvSpread(14, 15, 14)
        val candidate = owned("candidate", "seed", iv)
        val result = CandidateEvolutionAdvisor.evaluate(source, candidate, emptyList(), catalog, League.GREAT, settings)
        val expected = RankRepository.find(bud, League.GREAT, iv, settings)?.rank
        assertEquals(expected, result.rows.first().candidateRank)
        assertEquals(2, result.totalCatalogTargets)
    }

    @Test fun ancestorAndExactTargetOwnedCountAsComparableForSameEvolution() {
        val candidate = owned("candidate", "seed")
        val existing = listOf(owned("one", "seed"), owned("two", "bud"), owned("other", "other"))
        val result = CandidateEvolutionAdvisor.evaluate(source, candidate, existing, catalog, League.GREAT, settings)
        assertEquals(2, result.rows.first { it.targetSpeciesId == "bud" }.comparableOwnedCount)
        assertEquals(2, result.rows.first { it.targetSpeciesId == "flower" }.comparableOwnedCount)
    }

    @Test fun ownedSiblingEvolutionDoesNotCountAsReachableTarget() {
        val sibling = species("alt", 6)
        val ancestor = species("root", 0, listOf("seed", "alt"))
        val collection = listOf(owned("other", "alt"))
        val result = CandidateEvolutionAdvisor.evaluate(source, owned("candidate", "seed"), collection, catalog + sibling + ancestor, League.GREAT, settings)
        assertTrue(result.rows.all { it.comparableOwnedCount == 0 })
    }

    @Test fun ownCandidateIdNeverCountsAsAnExistingOwned() {
        val candidate = owned("candidate", "seed")
        val result = CandidateEvolutionAdvisor.evaluate(source, candidate, listOf(candidate), catalog, League.GREAT, settings)
        assertTrue(result.rows.all { it.comparableOwnedCount == 0 && it.bestOwnedRank == null })
    }

    @Test fun bestExistingRankUsesTargetFormNotTheSavedBaseForm() {
        val iv = IvSpread(15, 14, 15)
        val existing = owned("saved", "seed", iv)
        val result = CandidateEvolutionAdvisor.evaluate(source, owned("candidate", "seed"), listOf(existing), catalog, League.ULTRA, settings)
        val expected = RankRepository.find(flower, League.ULTRA, iv, settings)?.rank
        assertEquals(expected, result.rows.first { it.targetSpeciesId == "flower" }.bestOwnedRank)
    }

    @Test fun candidateSourceMismatchIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CandidateEvolutionAdvisor.evaluate(source, owned("candidate", "bud"), emptyList(), catalog, League.GREAT, settings)
        }
    }

    @Test fun previewIsBoundedAndReportsAdditionalCatalogTargets() {
        val children = (1..11).map { "evo_$it" }
        val many = species("branch", 10, children)
        val catalog = listOf(many) + children.mapIndexed { i, id -> species(id, i + 11) }
        val result = CandidateEvolutionAdvisor.evaluate(many, owned("candidate", "branch"), emptyList(), catalog, League.GREAT, settings)
        assertEquals(11, result.totalCatalogTargets)
        assertEquals(CandidateEvolutionAdvisor.MAX_TARGETS_PER_PREVIEW, result.rows.size)
        assertEquals(3, result.omittedCount)
    }

    @Test fun originalCandidateRemainsUnmodifiedAfterComparison() {
        val candidate = owned("candidate", "seed")
        val result = CandidateEvolutionAdvisor.evaluate(source, candidate, emptyList(), catalog, League.GREAT, settings)
        assertEquals("seed", candidate.speciesId)
        assertEquals(14, candidate.iv.attack)
        assertTrue(result.rows.all { it.targetSpeciesId != source.speciesId })
    }

    @Test fun hiddenCountCannotBecomeNegative() {
        assertEquals(0, CandidateEvolutionIvOverview(emptyList(), 0).omittedCount)
        assertEquals(0, CandidateEvolutionIvOverview(listOf(
            CandidateEvolutionIvRow("x", "x", null, 0, null)), 0).omittedCount)
    }
}
