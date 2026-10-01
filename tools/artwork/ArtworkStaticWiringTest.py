from pathlib import Path
app=Path(__file__).resolve().parents[2]/'app'
root=app/'src/main/java/com/rui/pvpgo'
files={p.name:p.read_text() for p in root.rglob('*.kt')}
checks={
'central shared artwork entry': 'fun PokemonArtwork(speciesName:' in files['TodayScreen.kt'],
'central remote renderer': 'RemotePokemonArtwork(speciesName' in files['TodayScreen.kt'],
'authentic URL primary': 'other/official-artwork/$dex.png' in files['PokemonArtworkSources.kt'],
'home fallback': 'other/home/$dex.png' in files['PokemonArtworkSources.kt'],
'standard sprite fallback': '${BASE}$dex.png' in files['PokemonArtworkSources.kt'],
'shiny routes': 'official-artwork/shiny/$dex.png' in files['PokemonArtworkSources.kt'],
'alternate forms safe': '!supportedBaseForm(form)' in files['PokemonArtworkSources.kt'],
'catalog bound to actual engine species': 'PokemonArtworkIndex.update(loaded.pokemon)' in files['MainActivity.kt'],
'on-device cache': 'pokemon-artwork-v1' in files['PokemonArtworkRemote.kt'],
'memory cache': 'LruCache<String, Bitmap>' in files['PokemonArtworkRemote.kt'],
'HTTPS-only host allowlist': 'url.protocol != "https" || url.host != "raw.githubusercontent.com"' in files['PokemonArtworkRemote.kt'],
'URL timeout': 'connectTimeout = 4500' in files['PokemonArtworkRemote.kt'],
'max response size': '3 * 1024 * 1024' in files['PokemonArtworkRemote.kt'],
'corrupted disk cache recovered': 'destination.delete()' in files['PokemonArtworkRemote.kt'],
'failed HTTP retry cooldown': 'failedAt' in files['PokemonArtworkRemote.kt'],
'normal shiny not silently substituted': 'if (shiny) listOf(' in files['PokemonArtworkSources.kt'],
'Compose contentDescription': 'contentDescription = "$name' in files['PokemonArtworkRemote.kt'],
'Collection owned shiny': 'shiny = owned.isShiny' in files['CollectionOverviewScreen.kt'],
'Collection specimens shiny': 'shiny = item.isShiny' in files['SpeciesCollectionScreens.kt'],
'no letter avatar fallback': 'speciesName.trim().take(1)' not in files['TodayScreen.kt'],
'Today image through shared entry': 'PokemonArtwork("Carbink", Modifier.size(86.dp))' in files['TodayScreen.kt'],
'live companion shared entry': 'import com.rui.pvpgo.PokemonArtwork' in files['LiveCompanionScreen.kt'],
'Teams shared entry': 'PokemonArtwork(name, Modifier.size(53.dp))' in files['TeamsGoldenScreen.kt'],
'Battles shared entry': 'PokemonArtwork(name, Modifier.size(51.dp))' in files['BattlesGoldenScreens.kt'],
'API Internet permission exists':'android.permission.INTERNET' in (app/'src/main/AndroidManifest.xml').read_text(),
'no new dependency added': 'coil' not in (app/'build.gradle.kts').read_text().lower(),
'package version': 'versionName = "1.51.0-dev"' in (app/'build.gradle.kts').read_text(),
}
for name,passed in checks.items():
    if not passed: print('FAIL',name)
print('ARTWORK_WIRING',sum(checks.values()),'/',len(checks))
assert all(checks.values())
