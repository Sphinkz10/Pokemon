package com.rui.pvpgo.domain

enum class League { GREAT, ULTRA, MASTER }
enum class TeamRole { LEAD, SAFE_SWITCH, CLOSER }
enum class BattleOutcome { WIN, LOSS, DRAW }
enum class MatchupAdvisorStatus { READY, REFUSED, NO_ELIGIBLE_CANDIDATES }
data class SavedMember(val ownedPokemonId:String, val role: TeamRole)
data class SavedTeam(val id:String,val name:String,val league:League,val members:List<SavedMember>)
data class OwnedPokemon(val id:String,val speciesId:String)
data class MatchupScenario(val league:League,val ownShields:Int=1,val opponentShields:Int=1)
data class Evaluation(val ownedPokemonId:String,val scenario:MatchupScenario,val battleRating:Int,val outcome:BattleOutcome)
data class Recommendation(val ownedPokemonId:String,val evaluation:Evaluation,val why:String)
data class Exclusion(val ownedPokemonId:String,val detail:String)
data class MatchupAdvisorResult(val status:MatchupAdvisorStatus,val scenario:MatchupScenario,val recommendations:List<Recommendation>,val exclusions:List<Exclusion>,val refusalReason:String?=null)
