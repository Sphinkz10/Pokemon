import com.rui.pvpgo.CollectionMatchupAdvisor
import com.rui.pvpgo.domain.BattleOutcome
import com.rui.pvpgo.domain.DexRangeSpec
import com.rui.pvpgo.domain.MatchupBand
import com.rui.pvpgo.domain.MatchupEvaluation
import com.rui.pvpgo.domain.MatchupExclusionReason
import com.rui.pvpgo.domain.MatchupScenario
import com.rui.pvpgo.domain.IntelligencePackBundle
import com.rui.pvpgo.domain.PackStageStatus
import com.rui.pvpgo.domain.MetaBuildEntry
import com.rui.pvpgo.domain.MoveAwareMatchupEvaluator
import com.rui.pvpgo.domain.OpponentBuild
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.PackProvenance
import com.rui.pvpgo.domain.PvPRuleset
import com.rui.pvpgo.domain.RulesetFilter
import com.rui.pvpgo.domain.RulesetFilterKind
import com.rui.pvpgo.domain.VersionedMetaGroup
import com.rui.pvpgo.engine.GameDataPackValidator
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.MetaPackValidator
import com.rui.pvpgo.engine.MetaPackAssembler
import com.rui.pvpgo.engine.RecommendedMoveset
import com.rui.pvpgo.engine.PackUpdateManager
import com.rui.pvpgo.engine.PokemonMath
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.PvpMove
import com.rui.pvpgo.engine.RulesetEngine
import com.rui.pvpgo.engine.VersionedGameDataPackFactory
import com.rui.pvpgo.engine.VersionedMetaPackFactory

private fun checkThat(condition: Boolean, message: String) { if (!condition) error(message) }

private fun species(
    id: String,
    dex: Int,
    type: String,
    tags: Set<String> = emptySet(),
    form: String = "Normal",
    released: Boolean = true
) = PokemonSpecies(
    dex = dex,
    name = id,
    form = form,
    speciesId = id,
    types = listOf(type),
    tags = tags,
    fastMoveIds = listOf("FAST_$type".uppercase()),
    chargedMoveIds = listOf("CHARGED_$type".uppercase()),
    released = released,
    baseAttack = 100,
    baseDefense = 100,
    baseStamina = 100
)

private fun moves(types: Set<String>): List<PvpMove> = types.flatMap { type ->
    listOf(
        PvpMove("FAST_${type}".uppercase(), "Fast $type", type = type, power = 4, energyGain = 8, turns = 2),
        PvpMove("CHARGED_${type}".uppercase(), "Charged $type", type = type, power = 55, energyCost = 40)
    )
}

private fun owned(id: String, speciesId: String, type: String) = OwnedPokemon(
    id = id,
    speciesId = speciesId,
    iv = IvSpread(0, 0, 0),
    level = 20.0,
    fastMoveId = "FAST_${type}".uppercase(),
    chargedMoveIds = listOf("CHARGED_${type}".uppercase()),
    createdAtEpochMs = 1L
)

private class RulesetStub : MoveAwareMatchupEvaluator {
    override fun evaluate(pokemon: OwnedPokemon, opponent: OpponentBuild, scenario: MatchupScenario): MatchupEvaluation {
        val rating = when (pokemon.id) {
            "fire-owned" -> 700
            "water-explicit" -> 600
            else -> 400
        }
        return MatchupEvaluation(
            ownedPokemonId = pokemon.id,
            opponentSpeciesId = opponent.speciesId,
            scenario = scenario,
            battleRating = rating,
            opponentBattleRating = 1000 - rating,
            outcome = if (rating > 500) BattleOutcome.WIN else BattleOutcome.LOSS,
            band = if (rating >= 650) MatchupBand.VERY_FAVORABLE else if (rating >= 550) MatchupBand.FAVORABLE else MatchupBand.UNFAVORABLE,
            turns = 20
        )
    }
}

fun main() {
    val catalog = listOf(
        species("firemon", 10, "fire"),
        species("watermon", 20, "water"),
        species("legendfire", 30, "fire", tags = setOf("legendary")),
        species("shadowfire", 40, "fire", tags = setOf("shadow")),
        species("megafire", 50, "fire", tags = setOf("mega")),
        species("oldfire", 60, "fire", released = false)
    )
    val moveCatalog = moves(setOf("fire", "water"))

    val ruleset = PvPRuleset(
        id = "fire-cup-great",
        version = "2026.09.14",
        title = "Fire Test Cup",
        baseLeague = League.GREAT,
        cpCap = 1500,
        allowShadow = false,
        allowMega = false,
        includeFilters = listOf(
            RulesetFilter(RulesetFilterKind.TYPE, values = setOf("fire")),
            RulesetFilter(RulesetFilterKind.DEX_RANGE, dexRanges = listOf(DexRangeSpec(1, 100)))
        ),
        excludeFilters = listOf(RulesetFilter(RulesetFilterKind.TAG, values = setOf("legendary"))),
        explicitlyIncludedSpeciesIds = setOf("watermon"),
        sourceName = "fixture-rules",
        sourceVersion = "1",
        generatedAtEpochMs = 1L
    )

    val fire = catalog.first { it.speciesId == "firemon" }
    val water = catalog.first { it.speciesId == "watermon" }
    val legendary = catalog.first { it.speciesId == "legendfire" }
    val shadow = catalog.first { it.speciesId == "shadowfire" }
    val mega = catalog.first { it.speciesId == "megafire" }
    val unreleased = catalog.first { it.speciesId == "oldfire" }

    checkThat(RulesetEngine.evaluate(ruleset, fire, 20.0, PokemonMath.cp(fire, IvSpread(0,0,0), 20.0)).eligible, "Fire should be eligible")
    checkThat(RulesetEngine.evaluate(ruleset, water, 20.0, PokemonMath.cp(water, IvSpread(0,0,0), 20.0)).eligible, "Explicit include must bypass include filters")
    checkThat(!RulesetEngine.evaluate(ruleset, legendary, 20.0, 500).eligible, "Legendary exclusion must apply")
    checkThat(!RulesetEngine.evaluate(ruleset, shadow, 20.0, 500).eligible, "Shadow hard guard must apply")
    checkThat(!RulesetEngine.evaluate(ruleset, mega, 20.0, 500).eligible, "Mega hard guard must apply")
    checkThat(!RulesetEngine.evaluate(ruleset, unreleased, 20.0, 500).eligible, "Unreleased guard must apply")
    checkThat(!RulesetEngine.evaluate(ruleset, fire, 20.0, 1501).eligible, "CP cap must apply")
    checkThat(RulesetEngine.contentHash(ruleset) == RulesetEngine.contentHash(ruleset), "Ruleset hash must be deterministic")

    // F49 integration: cup eligibility must affect actual collection recommendations, not only a side utility.
    val opponent = OpponentBuild(
        speciesId = "firemon",
        fastMoveId = "FAST_FIRE",
        chargedMoveIds = listOf("CHARGED_FIRE"),
        iv = IvSpread(0,0,0),
        level = 20.0,
        isShadow = false
    )
    val collection = listOf(
        owned("fire-owned", "firemon", "fire"),
        owned("water-explicit", "watermon", "water"),
        owned("legend-excluded", "legendfire", "fire"),
        owned("shadow-excluded", "shadowfire", "fire")
    )
    val advice = CollectionMatchupAdvisor(catalog, RulesetStub()).recommend(
        collection = collection,
        opponent = opponent,
        scenario = MatchupScenario(League.GREAT),
        limit = 5,
        ruleset = ruleset
    )
    checkThat(advice.recommendations.map { it.ownedPokemonId } == listOf("fire-owned", "water-explicit"), "Ruleset-aware F49 ranking mismatch")
    checkThat(advice.exclusions.filter { it.reason == MatchupExclusionReason.RULESET_INELIGIBLE }.size == 2, "Ruleset exclusions must be explicit")

    // F66: factual data pack has reproducible hash, counts and CPM snapshot.
    val dataPack = VersionedGameDataPackFactory.create(
        packId = "game-data-test",
        version = "2026.09.14",
        generatedAtEpochMs = 1L,
        provenance = PackProvenance("fixture-gamemaster", "abc123", "https://example.invalid/gm"),
        pokemon = catalog,
        moves = moveCatalog
    )
    val dataValidation = GameDataPackValidator.validate(dataPack)
    checkThat(dataValidation.valid, "Data pack should validate: ${dataValidation.errors}")
    checkThat(dataPack.manifest.cpmCount == 101, "Expected levels 1..51 in 0.5 increments")

    val tamperedData = dataPack.copy(
        pokemon = dataPack.pokemon.mapIndexed { index, p -> if (index == 0) p.copy(baseAttack = p.baseAttack + 1) else p }
    )
    val tamperedValidation = GameDataPackValidator.validate(tamperedData)
    checkThat(!tamperedValidation.valid && tamperedValidation.errors.any { it.contains("contentHash") }, "Tampered factual data must fail hash")

    // F67: meta pack is versioned opinion bound to exact data pack + ruleset snapshot.
    val group = VersionedMetaGroup(
        id = "fire-cup-meta-test",
        league = League.GREAT,
        seasonId = "s-test",
        cupId = "fire-cup",
        sourceName = "fixture-ranking",
        sourceVersion = "rank-1",
        generatedAtEpochMs = 1L,
        entries = listOf(
            MetaBuildEntry(
                key = "firemon-standard",
                opponent = opponent,
                weight = 3.0,
                labels = setOf("core")
            ),
            MetaBuildEntry(
                key = "watermon-explicit",
                opponent = OpponentBuild(
                    speciesId = "watermon",
                    fastMoveId = "FAST_WATER",
                    chargedMoveIds = listOf("CHARGED_WATER"),
                    iv = IvSpread(0,0,0),
                    level = 20.0,
                    isShadow = false
                ),
                weight = 1.0
            )
        ),
        rulesetId = ruleset.id,
        rulesetVersion = ruleset.version,
        dataPackHashSha256 = dataPack.manifest.contentHashSha256
    )
    val metaPack = VersionedMetaPackFactory.create(
        packId = "meta-test",
        version = "1",
        generatedAtEpochMs = 1L,
        sourceName = "fixture-meta",
        sourceVersion = "1",
        dataPack = dataPack,
        rulesets = listOf(ruleset),
        groups = listOf(group)
    )
    val metaValidation = MetaPackValidator.validate(metaPack, dataPack)
    checkThat(metaValidation.valid, "Meta pack should validate: ${metaValidation.errors}")

    val tamperedMeta = metaPack.copy(
        groups = listOf(group.copy(entries = group.entries.mapIndexed { i, e -> if (i == 0) e.copy(weight = 99.0) else e }))
    )
    val tamperedMetaValidation = MetaPackValidator.validate(tamperedMeta, dataPack)
    checkThat(!tamperedMetaValidation.valid && tamperedMetaValidation.errors.any { it.contains("contentHash") }, "Tampered meta must fail hash")

    val wrongDataLink = metaPack.copy(
        manifest = metaPack.manifest.copy(dataPackHashSha256 = "0".repeat(64))
    )
    checkThat(!MetaPackValidator.validate(wrongDataLink, dataPack).valid, "Wrong factual pack linkage must fail")

    // F67 source adapter: ranking score orders the source list but is NOT converted into meta prevalence.
    val assemblerRuleset = ruleset.copy(
        id = "fire-cup-assembler",
        version = "1",
        rankingIvFloor = 15,
        maxLevel = 20.0,
        explicitlyIncludedSpeciesIds = ruleset.explicitlyIncludedSpeciesIds + "watermon"
    )
    val recommendations = listOf(
        RecommendedMoveset("firemon", League.GREAT, "FAST_FIRE", listOf("CHARGED_FIRE"), "fixture-ranking", metaScore = 95.0),
        RecommendedMoveset("watermon", League.GREAT, "FAST_WATER", listOf("CHARGED_WATER"), "fixture-ranking", metaScore = 90.0),
        RecommendedMoveset("legendfire", League.GREAT, "FAST_FIRE", listOf("CHARGED_FIRE"), "fixture-ranking", metaScore = 99.0)
    )
    val assembled = MetaPackAssembler.fromRecommendedMovesets(
        packId = "assembled-meta", packVersion = "1", dataPack = dataPack, ruleset = assemblerRuleset,
        recommendations = recommendations, seasonId = "fixture-season", sourceName = "fixture-ranking",
        sourceVersion = "ranking-hash-1", generatedAtEpochMs = 3L, topN = 10
    )
    val assembledGroup = assembled.groups.single()
    checkThat(assembledGroup.entries.map { it.opponent.speciesId } == listOf("firemon", "watermon"), "Assembler must order eligible ranking entries and exclude ruleset-invalid entries")
    checkThat(assembledGroup.entries.all { it.weight == 1.0 }, "Ranking score must not become prevalence weight")
    checkThat(assembledGroup.entries.first().labels.any { it.startsWith("source-score:") }, "Source score should remain metadata")
    checkThat(MetaPackValidator.validate(assembled, dataPack).valid, "Assembled Meta Pack must validate")

    // F68 foundation: invalid/tampered bundles never become active; smoke tests gate activation; rollback restores known-good state.
    val manager = PackUpdateManager()
    val initialStage = manager.stage(IntelligencePackBundle(dataPack, metaPack))
    checkThat(initialStage.status == PackStageStatus.READY, "Valid bundle must stage")
    checkThat(manager.activateStaged { bundle -> bundle.metaPack?.groups?.isNotEmpty() == true }, "Valid staged bundle must activate")
    checkThat(manager.state().activeDataVersion == "2026.09.14", "Active data version mismatch")

    val rejectedStage = manager.stage(IntelligencePackBundle(tamperedData, metaPack))
    checkThat(rejectedStage.status == PackStageStatus.REJECTED, "Tampered bundle must be rejected before activation")
    checkThat(manager.state().activeDataVersion == "2026.09.14" && !manager.state().hasStagedBundle, "Rejected stage must not disturb active bundle")

    val dataPackV2 = VersionedGameDataPackFactory.create(
        packId = "game-data-test",
        version = "2026.09.15",
        generatedAtEpochMs = 2L,
        provenance = PackProvenance("fixture-gamemaster", "def456"),
        pokemon = catalog,
        moves = moveCatalog.map { if (it.moveId == "CHARGED_FIRE") it.copy(power = it.power + 1) else it }
    )
    val groupV2 = group.copy(dataPackHashSha256 = dataPackV2.manifest.contentHashSha256)
    val metaPackV2 = VersionedMetaPackFactory.create(
        packId = "meta-test", version = "2", generatedAtEpochMs = 2L, sourceName = "fixture-meta", sourceVersion = "2",
        dataPack = dataPackV2, rulesets = listOf(ruleset), groups = listOf(groupV2)
    )
    checkThat(manager.stage(IntelligencePackBundle(dataPackV2, metaPackV2)).status == PackStageStatus.READY, "V2 bundle should stage")
    checkThat(!manager.activateStaged { false }, "Failed smoke test must block activation")
    checkThat(manager.state().activeDataVersion == "2026.09.14" && manager.state().hasStagedBundle, "Failed smoke must keep active and staged bundles")
    checkThat(manager.activateStaged { true }, "Passing smoke test must activate V2")
    checkThat(manager.state().activeDataVersion == "2026.09.15" && manager.state().previousDataVersion == "2026.09.14", "Activation history mismatch")
    checkThat(manager.rollback(), "Rollback must succeed")
    checkThat(manager.state().activeDataVersion == "2026.09.14", "Rollback must restore V1")

    println("SAFE_UPDATE_ROLLBACK_F68_FOUNDATION_OK")
    println("RULESET_DATA_META_PACK_F65_F66_F67_OK")
    println("ruleset=${ruleset.id}@${ruleset.version} hash=${RulesetEngine.contentHash(ruleset).take(12)}")
    println("data=${dataPack.manifest.packId}@${dataPack.manifest.version} pokemon=${dataPack.manifest.pokemonCount} moves=${dataPack.manifest.moveCount} cpm=${dataPack.manifest.cpmCount}")
    println("meta=${metaPack.manifest.packId}@${metaPack.manifest.version} groups=${metaPack.manifest.groupCount} dataHash=${metaPack.manifest.dataPackHashSha256.take(12)}")
}
