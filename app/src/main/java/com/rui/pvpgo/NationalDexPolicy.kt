package com.rui.pvpgo

/** National Pokédex is informational: it does NOT imply an entry can be simulated by PvPoke. */
data class NationalDexEntry(val dex: Int, val slug: String, val name: String)

object NationalDexPolicy {
    /** Future-compatible numeric ids, not capped to the current number of released species. */
    const val MAX_DEX = 65535

    fun normalized(slug: String): String = PokemonArtworkSources.normalizedName(slug)

    fun fromApiRecords(records: Iterable<Pair<Int, String>>): List<NationalDexEntry> = records
        .asSequence()
        .filter { (dex, slug) -> dex in 1..MAX_DEX && normalized(slug).isNotBlank() }
        .map { (dex, slug) -> NationalDexEntry(dex, normalized(slug), displayName(slug)) }
        .distinctBy { it.dex }
        .sortedBy { it.dex }
        .toList()

    fun fromPvPCatalog(catalog: List<com.rui.pvpgo.engine.PokemonSpecies>): List<NationalDexEntry> =
        fromApiRecords(catalog.asSequence().filter { PokemonArtworkSources.supportedBaseForm(it.form) }
            .map { it.dex to it.name }.asIterable())

    /** Known generation ranges; all future numbers remain reachable under Novas. */
    fun generation(dex: Int): String = when (dex) {
        in 1..151 -> "G1"; in 152..251 -> "G2"; in 252..386 -> "G3"
        in 387..493 -> "G4"; in 494..649 -> "G5"; in 650..721 -> "G6"
        in 722..809 -> "G7"; in 810..905 -> "G8"; in 906..1025 -> "G9"
        else -> "Novas"
    }

    fun filter(entries: List<NationalDexEntry>, search: String, generation: String = "Todas"): List<NationalDexEntry> {
        val q = normalized(search.removePrefix("#"))
        // Accept #1, #0001, 1 and 0001 without forcing name-only matching.
        val exactDex = q.toIntOrNull()?.takeIf { it in 1..MAX_DEX }
        return entries.filter { entry ->
            (generation == "Todas" || generation == generation(entry.dex)) &&
                (q.isEmpty() || entry.slug.contains(q) || entry.dex == exactDex ||
                    normalized(entry.name).contains(q))
        }
    }

    fun displayName(slug: String): String = normalized(slug).split('-')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token -> token.replaceFirstChar { it.uppercase() } }
}
