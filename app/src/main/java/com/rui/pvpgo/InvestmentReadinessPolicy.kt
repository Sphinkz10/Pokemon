package com.rui.pvpgo

import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.OwnedEligibilityGate
import com.rui.pvpgo.engine.OwnedEligibilityStatus
import com.rui.pvpgo.engine.PokemonMath
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.StandardRulesets

/** What evidence is present, not whether the player should spend resources. */
internal enum class ReadinessCheckStatus { CHECKED, MISSING, RECHECK, BASE_INELIGIBLE }

internal data class ReadinessCheck(
    val name: String,
    val status: ReadinessCheckStatus,
    val explanation: String
)

internal enum class ReadinessSummary { NEEDS_CORRECTION, NEEDS_DATA, BASIC_DATA_CONSISTENT }

internal data class InvestmentReadinessReport(
    val summary: ReadinessSummary,
    val headline: String,
    val checks: List<ReadinessCheck>,
    val nextAction: String
) {
    val checkedCount: Int get() = checks.count { it.status == ReadinessCheckStatus.CHECKED }
    val outstandingCount: Int get() = checks.size - checkedCount
}

/**
 * Non-prescriptive gate between IV ranking and genuine investment planning.
 *
 * No full cost estimate is available in the Companion UI without an authentic,
 * validated InvestmentCostPack, a build target and a resource ledger. The
 * StandardRulesets check is ONLY a generic CP/species sanity check; official
 * current event cups and matchup/moveset legality still require separate data.
 */
internal object InvestmentReadinessPolicy {
    const val LIMITATION =
        "Isto não é uma recomendação de investimento. O rank IV não mede a força no meta. " +
            "Custos reais exigem um pack de preços de recursos validado, um build alvo " +
            "e o inventário de Candy/Stardust. Regras de copas e eventos não estão verificadas."

    fun inspect(
        owned: OwnedPokemon,
        species: PokemonSpecies?,
        league: League
    ): InvestmentReadinessReport {
        val checks = buildList {
            add(if (species != null && species.speciesId == owned.speciesId)
                ReadinessCheck("Espécie e forma", ReadinessCheckStatus.CHECKED,
                    "${species.name} · forma ${species.form} no catálogo local.")
            else ReadinessCheck("Espécie e forma", ReadinessCheckStatus.MISSING,
                    "É necessário confirmar a espécie e forma no catálogo."))

            add(if (owned.cp != null)
                ReadinessCheck("CP atual", ReadinessCheckStatus.CHECKED,
                    "CP registado: ${owned.cp}. Confirmar no Pokémon GO se estiver desatualizado.")
            else ReadinessCheck("CP atual", ReadinessCheckStatus.MISSING,
                    "Introduz o CP atual do exemplar no Pokémon GO."))

            add(if (owned.level != null)
                ReadinessCheck("Nível atual", ReadinessCheckStatus.CHECKED,
                    "Nível registado: ${owned.level}. Verifica-o antes de calcular custos.")
            else ReadinessCheck("Nível atual", ReadinessCheckStatus.MISSING,
                    "Indica o nível atual do exemplar; sem nível não calculamos power-ups."))

            val consistency = if (species != null && owned.cp != null && owned.level != null)
                runCatching { PokemonMath.cp(species, owned.iv, owned.level) }.getOrNull()
            else null
            add(when {
                consistency == null ->
                    ReadinessCheck("Coerência de CP", ReadinessCheckStatus.MISSING,
                        "Para cruzar CP, IVs e nível é preciso ter os três dados e a espécie.")
                consistency != owned.cp ->
                    ReadinessCheck("Coerência de CP", ReadinessCheckStatus.RECHECK,
                        "O cálculo com os dados registados dá CP $consistency, mas guardaste ${owned.cp}. Revê forma, IVs e nível.")
                else ->
                    ReadinessCheck("Coerência de CP", ReadinessCheckStatus.CHECKED,
                        "CP calculado coincide com o CP registado; isto não certifica a origem dos dados.")
            })

            add(if (owned.fastMoveId != null)
                ReadinessCheck("Ataque rápido", ReadinessCheckStatus.CHECKED,
                    "Movimento registado. O seu valor competitivo requer dados atualizados.")
            else ReadinessCheck("Ataque rápido", ReadinessCheckStatus.MISSING,
                "Regista o ataque rápido real para avaliar o build."))

            add(if (owned.chargedMoveIds.isNotEmpty())
                ReadinessCheck("Ataque carregado", ReadinessCheckStatus.CHECKED,
                    "${owned.chargedMoveIds.size} ataque(s) carregado(s) registado(s); não se presume um segundo ataque.")
            else ReadinessCheck("Ataque carregado", ReadinessCheckStatus.MISSING,
                "Regista pelo menos o ataque carregado que tens no jogo."))

            val basic = if (species != null)
                OwnedEligibilityGate.evaluate(
                    ruleset = StandardRulesets.forLeague(league),
                    species = species,
                    cp = owned.cp,
                    level = owned.level
                )
            else null
            add(when (basic?.status) {
                OwnedEligibilityStatus.INELIGIBLE ->
                    ReadinessCheck("Limites base da liga", ReadinessCheckStatus.BASE_INELIGIBLE,
                        "O exemplar não cumpre limites base desta liga: " +
                            (basic.rulesetReasons.map { it.name } + basic.issues.map { it.name }).joinToString(", ") +
                            ". Não avalia copas especiais.")
                OwnedEligibilityStatus.CONFIRMED ->
                    ReadinessCheck("Limites base da liga", ReadinessCheckStatus.CHECKED,
                        "Passa apenas a verificação genérica de espécie, CP e nível desta liga; " +
                            "as regras do evento e a preparação para batalha não estão certificadas.")
                else ->
                    ReadinessCheck("Limites base da liga", ReadinessCheckStatus.MISSING,
                        "Ainda falta informação para verificar os limites genéricos desta liga.")
            })

            add(ReadinessCheck("Custo do investimento", ReadinessCheckStatus.MISSING,
                "Sem pack de custos validado, nível alvo, movimentos pretendidos e inventário de recursos não há orçamento exato."))
        }

        // Resource costing is independently blocked until a validated pack,
        // target and ledger are available. Do not let that fixed Unknown
        // obliterate a useful distinction between complete and incomplete
        // *basic specimen data*.
        val basicChecks = checks.filterNot { it.name == "Custo do investimento" }
        val summary = when {
            basicChecks.any { it.status in setOf(ReadinessCheckStatus.RECHECK, ReadinessCheckStatus.BASE_INELIGIBLE) } ->
                ReadinessSummary.NEEDS_CORRECTION
            basicChecks.any { it.status == ReadinessCheckStatus.MISSING } ->
                ReadinessSummary.NEEDS_DATA
            else -> ReadinessSummary.BASIC_DATA_CONSISTENT
        }
        // Cost is always missing for this read-only preview, so never advertise
        // a green-light state based only on the generic league gate.
        val title = when (summary) {
            ReadinessSummary.NEEDS_CORRECTION -> "Revê os dados ou os limites da liga"
            ReadinessSummary.NEEDS_DATA -> "Faltam dados para planear o investimento"
            ReadinessSummary.BASIC_DATA_CONSISTENT -> "Dados de preparação reunidos; investimento por validar"
        }
        val next = when (summary) {
            ReadinessSummary.NEEDS_CORRECTION ->
                "Confirma primeiro CP, nível, forma e limites da liga no Pokémon GO."
            ReadinessSummary.NEEDS_DATA ->
                "Completa CP, nível e movimentos em Pokémon 360. Depois define o build e os recursos."
            ReadinessSummary.BASIC_DATA_CONSISTENT ->
                "Antes de gastar recursos, valida o build, o pack de custos e o meta atual."
        }
        return InvestmentReadinessReport(summary, title, checks, next)
    }
}
