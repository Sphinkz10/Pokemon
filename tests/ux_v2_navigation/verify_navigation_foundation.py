from pathlib import Path

root = Path(__file__).resolve().parents[2]
main = (root / "app/src/main/java/com/rui/pvpgo/MainActivity.kt").read_text(encoding="utf-8")
routes = (root / "app/src/main/java/com/rui/pvpgo/navigation/AppDestination.kt").read_text(encoding="utf-8")
core = (root / "app/src/main/java/com/rui/pvpgo/CoreScreens.kt").read_text(encoding="utf-8")
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")

# P1: root navigation is no longer manual state mutation.
for forbidden in (
    "private enum class AppTab",
    "var tab by rememberSaveable",
    "var moreStartRoute by remember",
    "var selectedReturnTab by rememberSaveable",
):
    assert forbidden not in main, forbidden

# Typed Navigation Compose graph exists.
for required in (
    "rememberNavController()",
    "NavHost(",
    "composable<AppDestination.Today>",
    "composable<AppDestination.Collection>",
    "composable<AppDestination.Teams>",
    "composable<AppDestination.Battles>",
    "composable<AppDestination.More>",
    "composable<AppDestination.Pokemon>",
    "composable<AppDestination.Events>",
    "composable<AppDestination.Agenda>",
    "composable<AppDestination.Radar>",
):
    assert required in main, required

# Today shared routes preserve HOME as the journey origin.
for required in (
    "AppDestination.Events(AppSection.HOME)",
    "AppDestination.Radar(AppSection.HOME)",
    "AppDestination.Agenda(AppSection.HOME)",
    "AppDestination.Pokemon(pokemon.speciesId, AppSection.HOME)",
):
    assert required in main, required

# Shared Radar has a visible contextual Back hook.
assert "fun RadarRootScreen(catalog: List<PokemonSpecies>, onBack: (() -> Unit)? = null)" in core
assert 'TextButton(onClick = onBack) { Text("←") }' in core

# Route contract and supported Navigation line are explicit.
for required in (
    "sealed interface AppDestination",
    "data class Pokemon(",
    "data class Events(",
    "data class Agenda(",
    "data class Radar(",
):
    assert required in routes, required

assert 'androidx.navigation:navigation-compose:2.9.8' in gradle

print("UX_V2_NAVIGATION_FOUNDATION_OK")
