package com.rui.pvpgo

import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.PokemonMath
import com.rui.pvpgo.engine.PokemonSpecies

/** Transparent first-pass organisation, NOT Pokémon GO IV rank or meta quality. */
internal enum class PvpGuideStage(val title: String, val explanation: String) {
    ALL(
        "Todos",
        "Sem filtro adicional. Compara os IVs reais no módulo IV Targets por espécie; " +
            "um perfil de IV não determina automaticamente o rank ou a força em PvP."
    ),
    REVIEW(
        "1 · Confirmar",
        "Primeiro confirma CP, nível, espécie/forma e campos assinalados como incertos. " +
            "Sem dados coerentes não se deve sugerir um investimento."
    ),
    WITHIN_LIMIT(
        "2 · Cabe na liga",
        "O CP atual foi confirmado pelo motor e não ultrapassa o limite geral da liga. " +
            "Não valida copas, movimentos, força competitiva nem evolução."
    ),
    OVER_LIMIT(
        "3 · Acima do limite",
        "O CP atual está acima do teto geral. Não é possível baixar o CP por power-up. " +
            "Pode servir para outra liga."
    ),
    IV_PATTERN(
        "4 · Perfil de IV",
        "Filtro meramente exploratório: Little/Great/Ultra usam ataque 0–5, defesa 12–15, " +
            "HP 12–15; Master usa ataque 15, defesa/HP 13–15. " +
            "Não é ranking IV, nem implica superioridade PvP: há muitas exceções."
    )
}

internal enum class PvpGuideVerdict {
    NEEDS_REVIEW, WITHIN_LIMIT, OVER_LIMIT
}

internal data class PvpGuideAssessment(
    val verdict: PvpGuideVerdict,
    val reason: String,
    val cpCap: Int?
)

internal object PvpGuidePolicy {
    fun assess(
        owned: OwnedPokemon,
        species: PokemonSpecies?,
        league: League
    ): PvpGuideAssessment {
        val cap = league.cpCap
        if (species == null || species.speciesId != owned.speciesId) {
            return PvpGuideAssessment(PvpGuideVerdict.NEEDS_REVIEW,
                "A espécie/forma ainda não está disponível no catálogo PvP.", cap)
        }
        if (owned.uncertainFields.isNotEmpty()) {
            return PvpGuideAssessment(PvpGuideVerdict.NEEDS_REVIEW,
                "Há campos marcados como não confirmados.", cap)
        }
        val cp = owned.cp
        val level = owned.level
        if (cp == null || level == null) {
            return PvpGuideAssessment(PvpGuideVerdict.NEEDS_REVIEW,
                "Falta confirmar o CP ou o nível do exemplar.", cap)
        }
        if (level > 50.0 || level < 1.0) {
            return PvpGuideAssessment(PvpGuideVerdict.NEEDS_REVIEW,
                "O nível está fora do simulador padrão até 50; revê Best Buddy.", cap)
        }
        val expectedCp = runCatching { PokemonMath.cp(species, owned.iv, level) }.getOrNull()
        if (expectedCp == null || expectedCp != cp) {
            return PvpGuideAssessment(PvpGuideVerdict.NEEDS_REVIEW,
                "CP, nível, forma e IVs guardados não são coerentes; confirma antes de filtrar.", cap)
        }
        return if (cap != null && cp > cap) {
            PvpGuideAssessment(PvpGuideVerdict.OVER_LIMIT,
                "$cp CP acima do teto geral de $cap CP.", cap)
        } else {
            PvpGuideAssessment(PvpGuideVerdict.WITHIN_LIMIT,
                if (cap == null) "$cp CP · Master não tem teto geral de CP."
                else "$cp CP dentro do teto geral de $cap CP.", cap)
        }
    }

    fun hasExploratoryIvPattern(owned: OwnedPokemon, league: League): Boolean {
        val v = owned.iv
        return if (league == League.MASTER) {
            v.attack == 15 && v.defense >= 13 && v.stamina >= 13
        } else {
            v.attack <= 5 && v.defense >= 12 && v.stamina >= 12
        }
    }

    fun matches(
        owned: OwnedPokemon,
        species: PokemonSpecies?,
        league: League,
        stage: PvpGuideStage
    ): Boolean {
        val verdict = assess(owned, species, league).verdict
        return when (stage) {
            PvpGuideStage.ALL -> true
            PvpGuideStage.REVIEW -> verdict == PvpGuideVerdict.NEEDS_REVIEW
            PvpGuideStage.WITHIN_LIMIT -> verdict == PvpGuideVerdict.WITHIN_LIMIT
            PvpGuideStage.OVER_LIMIT -> verdict == PvpGuideVerdict.OVER_LIMIT
            PvpGuideStage.IV_PATTERN ->
                verdict == PvpGuideVerdict.WITHIN_LIMIT && hasExploratoryIvPattern(owned, league)
        }
    }
}
