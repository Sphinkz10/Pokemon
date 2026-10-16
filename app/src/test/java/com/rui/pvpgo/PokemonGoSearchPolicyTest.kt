package com.rui.pvpgo

import com.rui.pvpgo.engine.League
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PokemonGoSearchPolicyTest {
    @Test fun greatDefaultsToWideExploration() =
        assertEquals(PokemonGoSearchPreset.BROAD, PokemonGoSearchPolicy.suggestedFor(League.GREAT))
    @Test fun ultraDefaultsToWideExploration() =
        assertEquals(PokemonGoSearchPreset.BROAD, PokemonGoSearchPolicy.suggestedFor(League.ULTRA))
    @Test fun littleDefaultsToWideExploration() =
        assertEquals(PokemonGoSearchPreset.BROAD, PokemonGoSearchPolicy.suggestedFor(League.LITTLE))
    @Test fun masterDefaultsToHighAttackButIsNotHardCoded() =
        assertEquals(PokemonGoSearchPreset.HIGH_ATTACK, PokemonGoSearchPolicy.suggestedFor(League.MASTER))

    @Test fun englishWideTemplateIsCopyable() =
        assertEquals("0-2attack&2-4defense&2-4hp",
            PokemonGoSearchPolicy.build(PokemonGoSearchPreset.BROAD, PokemonGoQueryLanguage.EN))
    @Test fun englishClassicTemplateIsCorrect() =
        assertEquals("0-1attack&3-4defense&3-4hp",
            PokemonGoSearchPolicy.build(PokemonGoSearchPreset.LOW_ATTACK, PokemonGoQueryLanguage.EN))
    @Test fun englishHighAttackTemplateIsCorrect() =
        assertEquals("3-4attack&3-4defense&3-4hp",
            PokemonGoSearchPolicy.build(PokemonGoSearchPreset.HIGH_ATTACK, PokemonGoQueryLanguage.EN))
    @Test fun ptWideTemplateUsesPortugueseStatisticNames() =
        assertEquals("0-2ataque&2-4defesa&2-4ps",
            PokemonGoSearchPolicy.build(PokemonGoSearchPreset.BROAD, PokemonGoQueryLanguage.PT))
    @Test fun ptClassicTemplateIsCorrect() =
        assertEquals("0-1ataque&3-4defesa&3-4ps",
            PokemonGoSearchPolicy.build(PokemonGoSearchPreset.LOW_ATTACK, PokemonGoQueryLanguage.PT))
    @Test fun ptHighAttackTemplateIsCorrect() =
        assertEquals("3-4ataque&3-4defesa&3-4ps",
            PokemonGoSearchPolicy.build(PokemonGoSearchPreset.HIGH_ATTACK, PokemonGoQueryLanguage.PT))
    @Test fun allDoesNotExcludeZeroOrPerfect() =
        assertEquals("0-4attack",
            PokemonGoSearchPolicy.build(PokemonGoSearchPreset.ALL, PokemonGoQueryLanguage.EN))
    @Test fun perfectIsSameInBothLanguages() {
        assertEquals("4*", PokemonGoSearchPolicy.build(PokemonGoSearchPreset.PERFECT, PokemonGoQueryLanguage.EN))
        assertEquals("4*", PokemonGoSearchPolicy.build(PokemonGoSearchPreset.PERFECT, PokemonGoQueryLanguage.PT))
    }
    @Test fun allTemplatesOmitCpAndRemovalOperators() {
        PokemonGoSearchPreset.entries.forEach { preset ->
            PokemonGoQueryLanguage.entries.forEach { language ->
                assertTrue(PokemonGoSearchPolicy.isNonDestructive(PokemonGoSearchPolicy.build(preset, language)))
            }
        }
    }
    @Test fun deletionFiltersAndCpBasedEliminationAreRejected() {
        assertFalse(PokemonGoSearchPolicy.isNonDestructive("!favorite&0attack"))
        assertFalse(PokemonGoSearchPolicy.isNonDestructive("cp-1500&0attack"))
        assertFalse(PokemonGoSearchPolicy.isNonDestructive(""))
    }
}
