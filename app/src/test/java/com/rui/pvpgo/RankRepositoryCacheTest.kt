package com.rui.pvpgo

import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.RankSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RankRepositoryCacheTest {
    private val species = PokemonSpecies(
        dex = 1, name = "Bulbasaur", speciesId = "bulbasaur",
        baseAttack = 110, baseDefense = 110, baseStamina = 110
    )
    private val settings = RankSettings(minLevel = 20.0, maxLevel = 20.0, ivFloor = 15)
    private val iv = IvSpread(15, 15, 15)

    @Test fun sameCatalogSnapshotUsesSameTable() {
        RankRepository.clear()
        val one = RankRepository.find(species, League.GREAT, iv, settings)
        val two = RankRepository.find(species.copy(), League.GREAT, iv, settings)
        assertEquals(one, two)
        assertEquals(1, RankRepository.cacheSizeForValidation())
    }

    @Test fun changedStatsInvalidateTableWithSameIdDexAndName() {
        RankRepository.clear()
        val first = RankRepository.find(species, League.GREAT, iv, settings)
        val revised = RankRepository.find(species.copy(baseAttack = 140), League.GREAT, iv, settings)
        requireNotNull(first)
        requireNotNull(revised)
        assertNotEquals(first.cp, revised.cp)
        assertEquals(2, RankRepository.cacheSizeForValidation())
    }

    @Test fun formChangesInvalidateTableWithoutCorruptingOriginal() {
        RankRepository.clear()
        RankRepository.find(species, League.GREAT, iv, settings)
        RankRepository.find(species.copy(form = "Alolan"), League.GREAT, iv, settings)
        assertEquals(2, RankRepository.cacheSizeForValidation())
    }

    @Test fun maximumCacheSizeRemainsBounded() {
        RankRepository.clear()
        for (n in 1..40) {
            val updated = species.copy(speciesId = "bulbasaur_$n", dex = n)
            RankRepository.find(updated, League.GREAT, iv, settings)
        }
        assertTrue(RankRepository.cacheSizeForValidation() <= 24)
    }
}
