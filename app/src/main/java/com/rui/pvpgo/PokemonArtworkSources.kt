package com.rui.pvpgo

import java.text.Normalizer
import java.util.Locale

/** Pure Kotlin source policy. Images are third-party Pokémon media; NOT software-licensed assets. */
object PokemonArtworkSources {
    const val BASE = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/"

    private val builtInDex = mapOf(
        "carbink" to 703, "vulpix" to 37, "paras" to 46,
        "clodsire" to 980, "feraligatr" to 160, "mandibuzz" to 630,
        "lanturn" to 171, "xerneas" to 716, "lickitung" to 108,
        "gyarados" to 130, "azumarill" to 184, "mewtwo" to 150,
        "annihilape" to 979, "serperior" to 497, "axew" to 610,
        "skarmory" to 227, "dewgong" to 87, "dragonair" to 148
    )

    fun normalizedName(value: String): String = Normalizer.normalize(
        value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /** Never represent alternative forms by incorrect base art. */
    fun supportedBaseForm(form: String?): Boolean {
        val token = normalizedName(form.orEmpty())
        return token.isEmpty() || token in setOf("normal", "standard", "default", "base")
    }

    fun dexFor(name: String, catalog: Map<String, Int> = emptyMap()): Int? {
        val key = normalizedName(name)
        val candidate = catalog[key] ?: builtInDex[key]
        return candidate?.takeIf { it in 1..NationalDexPolicy.MAX_DEX }
    }

    fun urls(dex: Int, shiny: Boolean = false, form: String? = null): List<String> {
        if (dex !in 1..NationalDexPolicy.MAX_DEX || !supportedBaseForm(form)) return emptyList()
        return if (shiny) listOf(
            "${BASE}other/official-artwork/shiny/$dex.png",
            "${BASE}other/home/shiny/$dex.png",
            "${BASE}shiny/$dex.png"
        ) else listOf(
            "${BASE}other/official-artwork/$dex.png",
            "${BASE}other/home/$dex.png",
            "${BASE}$dex.png"
        )
    }
}
