import com.rui.pvpgo.NationalDexPolicy as P
import com.rui.pvpgo.PokemonArtworkSources as A

fun main() {
  var assertions = 0
  fun checkIt(value: Boolean, description: String) { check(value) { description }; assertions++ }
  val all = P.fromApiRecords((1..1250).map { it to "pokemon-$it" })
  checkIt(all.size == 1250, "No truncation at 150, 1025 or 1000")
  checkIt(all.first().dex == 1 && all.last().dex == 1250, "National Dex sorted")
  checkIt(P.filter(all,"#1250").single().dex == 1250, "Search numeric 1250")
  checkIt(P.filter(all,"pokemon-10").size >= 1, "Search slug")
  checkIt(P.filter(all,"zzz").isEmpty(), "No artificial search result")
  checkIt(P.filter(all,"", "G1").size == 151, "Generation one 151")
  checkIt(P.filter(all,"", "G9").size == 120, "Generation nine known range")
  checkIt(P.filter(all,"", "Novas").size == 225, "Future entries not truncated")
  checkIt(P.filter(all,"pokemon-1201", "Novas").single().dex == 1201, "Search and generation intersect")
  checkIt(P.filter(all, "").size == 1250, "Empty query returns all")
  val duplicates = P.fromApiRecords(listOf(25 to "pikachu", 25 to "pikachu-clone", 0 to "bad", -1 to "bad", 65536 to "bad"))
  checkIt(duplicates.size == 1 && duplicates[0].slug == "pikachu", "dedup and invalid dex filtering")
  checkIt(A.urls(1250).first().endsWith("official-artwork/1250.png"), "Future Dex URL not capped at 1025")
  checkIt(A.urls(65536).isEmpty() && A.urls(0).isEmpty(), "High invalid Dex guard")
  checkIt(A.urls(25,true).all { "/shiny/" in it }, "Shiny not substituted")
  checkIt(A.urls(25,form="Alolan").isEmpty(), "No incorrect regional artwork")
  checkIt(P.displayName("mr-mime") == "Mr Mime", "Display normalized")
  println("NATIONAL_DEX_SCALE_PASS $assertions assertions 1250 unique national entries")
}
