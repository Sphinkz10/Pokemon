import com.rui.pvpgo.PokemonArtworkSources as P

fun main() {
    var checked = 0
    fun expect(v: Boolean, note: String) { check(v) { note }; checked++ }
    val samples = mapOf(
        "Carbink" to 703, "Vulpix" to 37, "Paras" to 46,
        "Clodsire" to 980, "Feraligatr" to 160, "Mandibuzz" to 630,
        "Lanturn" to 171, "Xerneas" to 716, "Lickitung" to 108,
        "Gyarados" to 130, "Azumarill" to 184, "Mewtwo" to 150,
        "Annihilape" to 979, "Serperior" to 497, "Axew" to 610
    )
    for ((name, id) in samples) {
        expect(P.dexFor(name) == id, "ID errado $name")
        expect(P.urls(id).first().endsWith("official-artwork/$id.png"), "Artwork não oficial $name")
        expect(P.urls(id, shiny=true).first().endsWith("official-artwork/shiny/$id.png"), "Shiny errado $name")
    }
    expect(P.dexFor("carbink") == 703, "Case insensitive")
    expect(P.dexFor("  CARBINK  ") == 703, "trim")
    expect(P.dexFor("Vulpi") == null, "no guessed prefix")
    expect(P.dexFor("MISSING") == null, "unknown returns null")
    expect(P.dexFor("custom species", mapOf("custom-species" to 25)) == 25, "catalog resolution")
    expect(P.dexFor("Carbink", mapOf("carbink" to 999)) == 999, "catalog supersedes hardcode")
    expect(P.urls(0).isEmpty(), "reject invalid dex")
    expect(P.urls(65536).isEmpty(), "reject out-of-range")
    expect(P.urls(1250).first().endsWith("official-artwork/1250.png"), "future-proof numeric Dex")
    expect(P.urls(37, form="Alolan").isEmpty(), "reject wrong Alolan art")
    expect(P.urls(37, form="Galarian").isEmpty(), "reject wrong Galarian art")
    expect(P.urls(37, form="Shadow").isEmpty(), "reject wrong shadow art")
    expect(P.urls(37, form="Normal").size == 3, "normal form url")
    expect(P.urls(37, true).all { it.contains("shiny/") }, "shiny cannot silently display normal")
    expect(P.urls(37).all { it.startsWith("https://raw.githubusercontent.com/PokeAPI/sprites/") }, "pinned host")
    expect(P.normalizedName("Mr. Mime") == "mr-mime", "punctuation")
    expect(P.normalizedName("Flabébé") == "flabebe", "diacritics")
    println("POKEMON_ARTWORK_SOURCE_POLICY_PASS $checked assertions")
}
