"""Static source integration checks; NOT a substitute for an Android/Compose build."""
from pathlib import Path
root=Path(__file__).resolve().parents[2]
src=root/'app/src/main/java/com/rui/pvpgo'
read=lambda f:(src/f).read_text()
ui=read('CollectionOverviewScreen.kt')
repository=read('NationalDexRepository.kt')
policy=read('NationalDexPolicy.kt')
art=read('PokemonArtworkRemote.kt')
urls=read('PokemonArtworkSources.kt')
checks={
 'national repository separate from combat': 'internal object NationalDexRepository' in repository,
 'API official species endpoint': 'https://pokeapi.co/api/v2/pokemon-species?limit=' in repository,
 'pagination loop until end': 'while (next != null && pages < MAX_PAGES)' in repository,
 'reject partial paginated snapshots': 'check(next == null && records.isNotEmpty())' in repository,
 'seven day cache': 'CACHE_LIFETIME = 7L' in repository,
 'stale cache fallback': 'cache anterior' in repository,
 'explicit offline partial mode': 'catálogo PvP parcial' in repository,
 'HTTP host allowlist': 'url.host == "pokeapi.co"' in repository,
 'numeric URL IDs verified': 'entryId.matchEntire(' in repository,
 'no 150 cap': '.take(150)' not in ui,
 'LazyColumn list': 'LazyColumn(' in ui and 'items(nationalItems, key = { it.dex })' in ui,
 'reactive national state': 'var national by remember' in ui and 'NationalDexRepository.load(' in ui,
 'search by name and dex': 'entry.slug.contains(q)' in policy and 'entry.dex == exactDex' in policy,
 'generation filters present': all(f'"{n}"' in ui for n in ['G1','G2','G3','G4','G5','G6','G7','G8','G9','Novas']),
 'future entries retained': 'else -> "Novas"' in policy,
 'PvP fields not invented': 'hasBattleData = battleSpecies != null' in ui and 'onOpenIvTargets = battleSpecies?.let' in ui,
 'artwork keyed by real national name': 'PokemonArtworkIndex.updateNationalDex(it.entries)' in ui,
 'form policy refuses incorrect base': 'if (dex !in 1..NationalDexPolicy.MAX_DEX || !supportedBaseForm(form))' in urls,
 'shiny never downgraded': 'other/official-artwork/shiny/$dex.png' in urls,
 'image bytes capped': '3 * 1024 * 1024' in art,
 'ram LRU 16mb': 'LruCache<String, Bitmap>(16 * 1024 * 1024)' in art,
 'disk budget': 'MAX_DISK_BYTES = 120L * 1024 * 1024' in art,
 'disk file budget': 'MAX_DISK_FILES = 750' in art,
 'concurrent fetch semaphore': 'Semaphore(4)' in art and 'imageFetchSlots.withPermit' in art,
 'one network request per image key': 'imageLocks.computeIfAbsent(key)' in art,
 'bitmap downsample': 'inSampleSize = factor' in art,
 'version bump': int(__import__('re').search(r'versionCode\s*=\s*(\d+)', (root/'app/build.gradle.kts').read_text()).group(1)) >= 56,
 'engine untouched by new feature': 'engine' not in str(src.relative_to(root)),
}
for label,ok in checks.items():
 if not ok: print('FAIL:',label)
assert all(checks.values()),'Static source integration incomplete'
print(f'NATIONAL_SCALE_WIRING_PASS {len(checks)}/{len(checks)} static checks (not an Android build)')
