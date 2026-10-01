package com.rui.pvpgo.engine

import kotlin.system.measureTimeMillis

fun main() {
    val charizard = PokemonSpecies(6, "Charizard", baseAttack = 223, baseDefense = 173, baseStamina = 186)
    val swampert = PokemonSpecies(260, "Swampert", baseAttack = 208, baseDefense = 175, baseStamina = 225)
    val azumarill = PokemonSpecies(184, "Azumarill", baseAttack = 112, baseDefense = 152, baseStamina = 225)
    val medicham = PokemonSpecies(308, "Medicham", baseAttack = 121, baseDefense = 152, baseStamina = 155)
    val gStunfisk = PokemonSpecies(618, "Galarian Stunfisk", baseAttack = 144, baseDefense = 171, baseStamina = 240)

    lateinit var czGreat: PvPRankEntry
    lateinit var czUltra: PvPRankEntry
    lateinit var czMaster: PvPRankEntry
    lateinit var swUltra: PvPRankEntry

    val elapsed = measureTimeMillis {
        czGreat = PvPRanker.rank(charizard, League.GREAT).first()
        czUltra = PvPRanker.rank(charizard, League.ULTRA).first()
        czMaster = PvPRanker.rank(charizard, League.MASTER).first()
        swUltra = PvPRanker.rank(swampert, League.ULTRA).first()
    }

    fun expect(
        species: PokemonSpecies,
        league: League,
        iv: IvSpread,
        level: Double,
        cp: Int,
        settings: RankSettings = RankSettings()
    ) {
        val rank1 = PvPRanker.rank(species, league, settings).first()
        println("${species.name} $league: ${rank1.iv} L${rank1.level} CP${rank1.cp}")
        check(rank1.iv == iv && rank1.level == level && rank1.cp == cp) {
            "Expected $iv L$level CP$cp, got ${rank1.iv} L${rank1.level} CP${rank1.cp}"
        }
    }

    expect(charizard, League.GREAT, IvSpread(0, 15, 13), 19.5, 1500)
    expect(charizard, League.ULTRA, IvSpread(0, 13, 15), 35.0, 2500)
    expect(charizard, League.MASTER, IvSpread(15, 15, 15), 50.0, 3266)
    expect(swampert, League.ULTRA, IvSpread(0, 14, 13), 33.5, 2499)
    expect(azumarill, League.GREAT, IvSpread(0, 15, 15), 45.5, 1499)
    expect(medicham, League.GREAT, IvSpread(5, 15, 15), 50.0, 1499)
    expect(gStunfisk, League.GREAT, IvSpread(0, 12, 15), 27.0, 1498)
    expect(
        medicham,
        League.GREAT,
        IvSpread(4, 15, 15),
        50.5,
        1496,
        RankSettings(maxLevel = 51.0)
    )

    val floor4 = RankSettings(ivFloor = 4)
    check(PvPRanker.rank(charizard, League.GREAT, floor4).size == 12 * 12 * 12)
    check(PvPRanker.find(charizard, League.GREAT, IvSpread(0, 15, 13))?.rank == 1)
    check(PokemonMath.highestLevelAtOrBelowCap(charizard, IvSpread(0, 15, 13), 1500) == 19.5)
    check(czGreat.iv == IvSpread(0, 15, 13) && czUltra.iv == IvSpread(0, 13, 15))
    check(czMaster.iv == IvSpread(15, 15, 15) && swUltra.iv == IvSpread(0, 14, 13))

    // Structural audit: the rank table must be complete, legal and deterministic.
    listOf(charizard, swampert, azumarill, medicham, gStunfisk).forEach { species ->
        listOf(League.GREAT, League.ULTRA, League.MASTER).forEach { league ->
            val rows = PvPRanker.rank(species, league)
            check(rows.size == 4096) { "${species.name} $league did not produce 4096 spreads" }
            check(rows.map { it.iv }.toSet().size == 4096) { "Duplicate IV spread in ${species.name} $league" }
            check(rows.map { it.rank } == (1..4096).toList()) { "Broken rank sequence in ${species.name} $league" }
            check(rows.map { it.mirrorRank }.toSet().size == 4096) { "Broken mirror rank sequence in ${species.name} $league" }
            check(rows.zipWithNext().all { (a, b) -> a.stats.statProduct + 1e-9 >= b.stats.statProduct }) {
                "Stat Product is not monotonic in ${species.name} $league"
            }
            check(rows.zipWithNext().all { (a, b) -> a.percentOfRank1 + 1e-9 >= b.percentOfRank1 }) {
                "Rank percentage is not monotonic in ${species.name} $league"
            }
            league.cpCap?.let { cap ->
                check(rows.all { it.cp <= cap }) { "CP cap exceeded in ${species.name} $league" }
            }
            check(rows.all { it.level in 1.0..50.0 && it.level * 2 == (it.level * 2).toInt().toDouble() }) {
                "Invalid level in ${species.name} $league"
            }
            if (league == League.MASTER) {
                check(rows.first().iv == IvSpread(15, 15, 15)) { "Master Rank #1 must be hundo for ${species.name}" }
            }
        }
    }

    // Lv51 audit: only Best Buddy mode may reach 50.5/51.
    val medicham51 = PvPRanker.rank(medicham, League.GREAT, RankSettings(maxLevel = 51.0))
    check(medicham51.first().level == 50.5)
    check(medicham51.all { it.level <= 51.0 })

    validateCompanionDomain()
    println("VALIDATION_OK · reference cases + structural audit · core benchmark $elapsed ms")
}

// Domain-model audit for Collection / Battle / Meta architecture.
private fun validateCompanionDomain() {
    val now = 1_800_000_000_000L
    val a = com.rui.pvpgo.domain.OwnedPokemon(
        id = "owned:a",
        speciesId = "swampert",
        iv = IvSpread(0, 14, 13),
        cp = 2499,
        level = 33.5,
        fastMoveId = "MUD_SHOT",
        chargedMoveIds = listOf("HYDRO_CANNON", "EARTHQUAKE"),
        createdAtEpochMs = now
    )
    val b = com.rui.pvpgo.domain.OwnedPokemon(
        id = "owned:b",
        speciesId = "clodsire",
        iv = IvSpread(0, 14, 15),
        cp = 1498,
        level = 29.0,
        createdAtEpochMs = now
    )
    val c = com.rui.pvpgo.domain.OwnedPokemon(
        id = "owned:c",
        speciesId = "feraligatr",
        iv = IvSpread(2, 15, 14),
        cp = 1499,
        level = 20.5,
        createdAtEpochMs = now
    )

    val team = com.rui.pvpgo.domain.SavedTeam(
        id = "team:great:1",
        name = "Great main",
        league = League.GREAT,
        members = listOf(
            com.rui.pvpgo.domain.TeamMember(b.id, com.rui.pvpgo.domain.TeamRole.LEAD),
            com.rui.pvpgo.domain.TeamMember(c.id, com.rui.pvpgo.domain.TeamRole.SAFE_SWITCH),
            com.rui.pvpgo.domain.TeamMember(a.id, com.rui.pvpgo.domain.TeamRole.CLOSER)
        ),
        isPrimary = true,
        createdAtEpochMs = now
    )
    check(team.members.size == 3)

    fun opponent(speciesId: String, slot: com.rui.pvpgo.domain.BattleSlot) =
        com.rui.pvpgo.domain.OpponentBattlePokemon(speciesId, slot)
    fun own(id: String, slot: com.rui.pvpgo.domain.BattleSlot) =
        com.rui.pvpgo.domain.OwnBattlePokemon(id, slot)

    val ownLine = listOf(
        own(b.id, com.rui.pvpgo.domain.BattleSlot.LEAD),
        own(c.id, com.rui.pvpgo.domain.BattleSlot.SWITCH),
        own(a.id, com.rui.pvpgo.domain.BattleSlot.CLOSER)
    )
    val battles = listOf(
        com.rui.pvpgo.domain.BattleRecord(
            id = "battle:1", league = League.GREAT, outcome = com.rui.pvpgo.domain.BattleOutcome.WIN,
            playedAtEpochMs = now, ownTeamId = team.id, ownPokemon = ownLine,
            opponentPokemon = listOf(opponent("azumarill", com.rui.pvpgo.domain.BattleSlot.LEAD), opponent("gastrodon", com.rui.pvpgo.domain.BattleSlot.SWITCH))
        ),
        com.rui.pvpgo.domain.BattleRecord(
            id = "battle:2", league = League.GREAT, outcome = com.rui.pvpgo.domain.BattleOutcome.LOSS,
            playedAtEpochMs = now + 1, ownTeamId = team.id, ownPokemon = ownLine,
            opponentPokemon = listOf(opponent("azumarill", com.rui.pvpgo.domain.BattleSlot.LEAD), opponent("malamar", com.rui.pvpgo.domain.BattleSlot.SWITCH))
        ),
        com.rui.pvpgo.domain.BattleRecord(
            id = "battle:3", league = League.GREAT, outcome = com.rui.pvpgo.domain.BattleOutcome.LOSS,
            playedAtEpochMs = now + 2, ownTeamId = team.id, ownPokemon = ownLine,
            opponentPokemon = listOf(opponent("gastrodon", com.rui.pvpgo.domain.BattleSlot.LEAD), opponent("azumarill", com.rui.pvpgo.domain.BattleSlot.SWITCH))
        )
    )

    val summary = com.rui.pvpgo.domain.BattleAnalytics.summarize(battles, League.GREAT)
    check(summary.total == 3 && summary.wins == 1 && summary.losses == 2 && summary.draws == 0)
    check(kotlin.math.abs(summary.winRatePercent - (100.0 / 3.0)) < 1e-9)
    val azu = summary.opponents.first { it.speciesId == "azumarill" }
    check(azu.encounters == 3 && azu.wins == 1 && azu.losses == 2 && azu.leadEncounters == 2)
    val gastro = summary.opponents.first { it.speciesId == "gastrodon" }
    check(gastro.encounters == 2 && gastro.losses == 1 && gastro.leadEncounters == 1)

    val scenario = com.rui.pvpgo.domain.MatchupScenario(League.GREAT, ownShields = 1, opponentShields = 1)
    check(scenario.ownShields == 1 && scenario.opponentShields == 1)

    println("COMPANION_DOMAIN_OK · collection/team invariants + battle analytics")
}
