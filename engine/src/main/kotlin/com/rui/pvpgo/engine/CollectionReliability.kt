package com.rui.pvpgo.engine

import com.rui.pvpgo.domain.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

object CollectionReconciler {
    fun reconcile(candidate: OwnedPokemon, collection: List<OwnedPokemon>): ReconciliationReport {
        val matches = collection.filterNot { it.id == candidate.id }.mapNotNull { existing ->
            val exactExternal = !candidate.externalSource.isNullOrBlank() && !candidate.externalId.isNullOrBlank() &&
                candidate.externalSource == existing.externalSource && candidate.externalId == existing.externalId
            if (exactExternal) return@mapNotNull ReconciliationMatch(
                existing.id, ReconciliationMatchKind.EXACT_EXTERNAL_ID, 1.0,
                listOf("Mesma origem externa e mesmo externalId")
            )
            if (candidate.speciesId != existing.speciesId || candidate.iv != existing.iv ||
                candidate.isShadow != existing.isShadow || candidate.isPurified != existing.isPurified) return@mapNotNull null

            val reasons = mutableListOf("Mesma espécie", "Mesmos IVs", "Mesmo estado Shadow/Purified")
            var score = 0.72
            if (candidate.cp != null && candidate.cp == existing.cp) { score += 0.08; reasons += "Mesmo CP" }
            if (candidate.level != null && candidate.level == existing.level) { score += 0.08; reasons += "Mesmo nível" }
            if (candidate.caughtAtEpochMs != null && existing.caughtAtEpochMs != null &&
                kotlin.math.abs(candidate.caughtAtEpochMs - existing.caughtAtEpochMs) <= 5 * 60 * 1000L) {
                score += 0.08; reasons += "Data de captura muito próxima"
            }
            val kind = if (score >= 0.86) ReconciliationMatchKind.STRONG_SAME_POKEMON else ReconciliationMatchKind.POSSIBLE_SAME_POKEMON
            ReconciliationMatch(existing.id, kind, score.coerceAtMost(0.99), reasons)
        }.sortedByDescending { it.confidence }

        val action = when {
            matches.firstOrNull()?.kind == ReconciliationMatchKind.EXACT_EXTERNAL_ID -> ReconciliationAction.MERGE_RECOMMENDED
            matches.any { it.kind == ReconciliationMatchKind.STRONG_SAME_POKEMON } -> ReconciliationAction.REVIEW_EXISTING
            matches.isNotEmpty() -> ReconciliationAction.REVIEW_EXISTING
            else -> ReconciliationAction.CREATE_NEW
        }
        return ReconciliationReport(candidate.id, action, matches)
    }
}

object PvpBoxClassifier {
    fun build(
        collection: List<OwnedPokemon>,
        plans: List<PvpBuildPlan>,
        nowEpochMs: Long
    ): List<PvpBoxEntry> {
        val plansByOwned = plans.groupBy { it.ownedPokemonId }
        return collection.mapNotNull { owned ->
            val categories = linkedSetOf<PvpBoxCategory>()
            val reasons = mutableListOf<String>()
            val activePlans = plansByOwned[owned.id].orEmpty().filter { it.status !in setOf(BuildStatus.ARCHIVED) }
            val freshness = PokemonFreshnessEvaluator.evaluate(owned, nowEpochMs)
            if (freshness.status in setOf(PokemonFreshnessStatus.STALE, PokemonFreshnessStatus.UNKNOWN, PokemonFreshnessStatus.CHECK_SOON)) {
                categories += PvpBoxCategory.NEEDS_CONFIRMATION
                reasons += "Dados precisam de confirmação (${freshness.status.name.lowercase()})"
            }
            if (owned.fastMoveId != null && owned.chargedMoveIds.isNotEmpty() && owned.level != null && owned.cp != null) {
                categories += PvpBoxCategory.READY
                reasons += "Build tem nível, CP e moves registados"
            }
            if (activePlans.any { it.status in setOf(BuildStatus.IDEA, BuildStatus.PLANNED, BuildStatus.IN_PROGRESS) } || "candidate" in owned.tags.map(String::lowercase)) {
                categories += PvpBoxCategory.CANDIDATE
                reasons += "Existe plano/candidatura PvP"
            }
            if (owned.isFavorite || activePlans.any { it.priority >= 70 } || owned.tags.any { it.equals("pvp", true) || it.equals("important", true) }) {
                categories += PvpBoxCategory.IMPORTANT
                reasons += "Marcado como favorito/importante"
            }
            categories.takeIf { it.isNotEmpty() }?.let { PvpBoxEntry(owned.id, it, reasons.distinct()) }
        }
    }
}

class CandidateInbox {
    private val items = linkedMapOf<String, CandidateInboxItem>()

    fun submit(item: CandidateInboxItem) {
        require(item.id !in items) { "Candidate id already exists" }
        items[item.id] = item
    }

    fun pending(): List<CandidateInboxItem> = items.values.filter { it.status == CandidateInboxStatus.PENDING }

    fun confirmNew(id: String, reviewedAtEpochMs: Long): CandidateInboxItem = update(id, CandidateInboxStatus.CONFIRMED_NEW, reviewedAtEpochMs, null)
    fun merge(id: String, ownedPokemonId: String, reviewedAtEpochMs: Long): CandidateInboxItem = update(id, CandidateInboxStatus.MERGED, reviewedAtEpochMs, ownedPokemonId)
    fun discard(id: String, reviewedAtEpochMs: Long): CandidateInboxItem = update(id, CandidateInboxStatus.DISCARDED, reviewedAtEpochMs, null)

    private fun update(id: String, status: CandidateInboxStatus, at: Long, linked: String?): CandidateInboxItem {
        val current = items[id] ?: error("Unknown candidate $id")
        require(current.status == CandidateInboxStatus.PENDING) { "Candidate already reviewed" }
        val next = current.copy(status = status, reviewedAtEpochMs = at, linkedOwnedPokemonId = linked)
        items[id] = next
        return next
    }
}

object DuplicateResolver {
    fun resolve(
        collection: List<OwnedPokemon>,
        catalog: List<PokemonSpecies>,
        leagues: List<League> = listOf(League.LITTLE, League.GREAT, League.ULTRA, League.MASTER),
        settings: RankSettings = RankSettings()
    ): List<DuplicateFamilyReport> {
        val speciesById = catalog.associateBy { it.speciesId }
        val groups = collection.groupBy { owned -> speciesById[owned.speciesId]?.familyId ?: owned.speciesId }
            .filterValues { it.size > 1 }
        return groups.map { (family, ownedList) ->
            val familySpecies = catalog.filter { (it.familyId ?: it.speciesId) == family }
            val uses = mutableListOf<DuplicateUseSuggestion>()
            for (owned in ownedList) {
                val source = speciesById[owned.speciesId] ?: continue
                for (target in familySpecies) {
                    if (!canReach(source, target.speciesId, speciesById)) continue
                    for (league in leagues) {
                        val ranked = PvPRanker.rank(target, league, settings).firstOrNull { it.iv == owned.iv } ?: continue
                        uses += DuplicateUseSuggestion(owned.id, target.speciesId, league, ranked.rank, ranked.cp, ranked.level)
                    }
                }
            }
            val best = uses.groupBy { it.targetSpeciesId to it.league }.values.mapNotNull { rows -> rows.minByOrNull { it.rank } }
                .sortedWith(compareBy<DuplicateUseSuggestion> { it.league.ordinal }.thenBy { it.rank })
            DuplicateFamilyReport(
                familyKey = family,
                ownedPokemonIds = ownedList.map { it.id }.sorted(),
                bestUses = best,
                notes = listOf("Sugestões de melhor uso por rank IV; não são ordens de transferência nem força de meta.")
            )
        }.sortedBy { it.familyKey }
    }

    private fun canReach(source: PokemonSpecies, targetId: String, byId: Map<String, PokemonSpecies>): Boolean {
        if (source.speciesId == targetId) return true
        val seen = mutableSetOf<String>()
        val q = ArrayDeque<String>()
        q.addAll(source.evolutionSpeciesIds)
        while (q.isNotEmpty()) {
            val id = q.removeFirst()
            if (!seen.add(id)) continue
            if (id == targetId) return true
            byId[id]?.evolutionSpeciesIds?.let(q::addAll)
        }
        return false
    }
}

object TransferSafetyEvaluator {
    fun assess(
        owned: OwnedPokemon,
        collection: List<OwnedPokemon>,
        catalog: List<PokemonSpecies>,
        plans: List<PvpBuildPlan> = emptyList(),
        highPvpRankThreshold: Int = 100
    ): TransferSafetyAssessment {
        val species = catalog.firstOrNull { it.speciesId == owned.speciesId }
        val reasons = linkedSetOf<TransferSafetyReason>()
        if (owned.isFavorite) reasons += TransferSafetyReason.FAVORITE
        if (owned.isShiny) reasons += TransferSafetyReason.SHINY
        if (owned.isLucky) reasons += TransferSafetyReason.LUCKY
        if (owned.isBestBuddy) reasons += TransferSafetyReason.BEST_BUDDY
        if (owned.uncertainFields.isNotEmpty()) reasons += TransferSafetyReason.UNCERTAIN_DATA
        if (owned.tags.any { it.equals("pvp", true) || it.equals("candidate", true) || it.equals("important", true) }) reasons += TransferSafetyReason.PVP_TAG
        if (plans.any { it.ownedPokemonId == owned.id && it.status != BuildStatus.ARCHIVED }) reasons += TransferSafetyReason.ACTIVE_BUILD_PLAN
        if (collection.count { it.speciesId == owned.speciesId } == 1) reasons += TransferSafetyReason.UNIQUE_SPECIES
        if (collection.count { it.speciesId == owned.speciesId && it.isShadow == owned.isShadow } == 1) reasons += TransferSafetyReason.UNIQUE_SHADOW_STATE
        if (species != null && ((owned.fastMoveId != null && owned.fastMoveId in species.eliteMoveIds) || owned.chargedMoveIds.any { it in species.eliteMoveIds })) {
            reasons += TransferSafetyReason.LEGACY_OR_ELITE_MOVE
        }
        if (species != null) {
            val good = League.entries.any { league ->
                runCatching { PvPRanker.rank(species, league).firstOrNull { it.iv == owned.iv }?.rank }.getOrNull()?.let { it <= highPvpRankThreshold } == true
            }
            if (good) reasons += TransferSafetyReason.HIGH_PVP_RANK
        }
        val hardProtect = reasons.any { it in setOf(
            TransferSafetyReason.FAVORITE, TransferSafetyReason.BEST_BUDDY, TransferSafetyReason.LEGACY_OR_ELITE_MOVE,
            TransferSafetyReason.ACTIVE_BUILD_PLAN, TransferSafetyReason.HIGH_PVP_RANK, TransferSafetyReason.UNCERTAIN_DATA
        ) }
        val priority = when {
            hardProtect -> TransferPriority.PROTECTED
            reasons.isNotEmpty() -> TransferPriority.REVIEW
            else -> TransferPriority.LOW_PRIORITY
        }
        val message = when (priority) {
            TransferPriority.PROTECTED -> "Protegido: rever antes de qualquer decisão destrutiva."
            TransferPriority.REVIEW -> "Rever: existem características que podem justificar manter este exemplar."
            TransferPriority.LOW_PRIORITY -> "Baixa prioridade atual; isto não é uma ordem para transferir."
        }
        return TransferSafetyAssessment(owned.id, priority, reasons, message)
    }
}

object PokemonGoSearchStringGenerator {
    fun forOwned(ownedIds: Collection<String>, collection: List<OwnedPokemon>, catalog: List<PokemonSpecies>): PokemonGoSearchString {
        val selected = collection.filter { it.id in ownedIds }.distinctBy { it.id }
        require(selected.isNotEmpty())
        val nicknames = selected.mapNotNull { it.nickname?.trim()?.takeIf(String::isNotBlank) }
        val allHaveDistinctNicknames = nicknames.size == selected.size && nicknames.distinct().size == selected.size
        if (allHaveDistinctNicknames) {
            return PokemonGoSearchString(
                query = nicknames.joinToString(","),
                precision = SearchStringPrecision.INDIVIDUAL_HINT,
                coveredOwnedPokemonIds = selected.map { it.id },
                note = "Pesquisa por nickname; confirmar visualmente porque nicknames não são identificadores técnicos."
            )
        }
        val bySpecies = catalog.associateBy { it.speciesId }
        val dexes = selected.mapNotNull { bySpecies[it.speciesId]?.dex }.distinct().sorted()
        require(dexes.isNotEmpty())
        return PokemonGoSearchString(
            query = dexes.joinToString(","),
            precision = SearchStringPrecision.SPECIES_LEVEL,
            coveredOwnedPokemonIds = selected.map { it.id },
            note = "Pesquisa por número de Pokédex com OR; pode devolver outros exemplares da mesma espécie."
        )
    }
}

object CollectionBackupCodec {
    private const val MAGIC = "PVPGO_BACKUP"

    fun encode(snapshot: CollectionBackupSnapshot): CollectionBackupEnvelope {
        val payload = buildPayload(snapshot)
        return CollectionBackupEnvelope(snapshot.schemaVersion, snapshot.exportedAtEpochMs, snapshot.sourceAppVersion, sha256(payload), payload)
    }

    fun serialize(envelope: CollectionBackupEnvelope): String = listOf(
        MAGIC, envelope.schemaVersion.toString(), envelope.exportedAtEpochMs.toString(), enc(envelope.sourceAppVersion), envelope.payloadSha256
    ).joinToString("|") + "\n" + envelope.payload

    fun deserialize(text: String): CollectionBackupEnvelope {
        val firstBreak = text.indexOf('\n')
        require(firstBreak > 0) { "Invalid backup envelope" }
        val header = text.substring(0, firstBreak).split('|')
        require(header.size == 5 && header[0] == MAGIC) { "Invalid backup header" }
        val payload = text.substring(firstBreak + 1)
        val envelope = CollectionBackupEnvelope(header[1].toInt(), header[2].toLong(), dec(header[3]), header[4], payload)
        require(sha256(payload) == envelope.payloadSha256) { "Backup payload hash mismatch" }
        return envelope
    }

    fun decode(envelope: CollectionBackupEnvelope): CollectionBackupSnapshot {
        require(sha256(envelope.payload) == envelope.payloadSha256) { "Backup payload hash mismatch" }
        val collection = mutableListOf<OwnedPokemon>()
        val plans = mutableListOf<PvpBuildPlan>()
        val teams = mutableListOf<SavedTeam>()
        val battles = mutableListOf<BattleRecord>()
        envelope.payload.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val p = line.split('|')
            when (p.first()) {
                "O" -> collection += decodeOwned(p)
                "P" -> plans += decodePlan(p)
                "T" -> teams += decodeTeam(p)
                "B" -> battles += decodeBattle(p)
                else -> error("Unknown backup record ${p.first()}")
            }
        }
        return CollectionBackupSnapshot(collection, plans, teams, battles, envelope.exportedAtEpochMs, envelope.sourceAppVersion, envelope.schemaVersion)
    }

    private fun buildPayload(s: CollectionBackupSnapshot): String = buildString {
        s.collection.sortedBy { it.id }.forEach { o ->
            append(listOf(
                "O", enc(o.id), enc(o.speciesId), o.iv.attack, o.iv.defense, o.iv.stamina, n(o.cp), n(o.level), encN(o.nickname),
                b(o.isShadow), b(o.isPurified), b(o.isShiny), b(o.isLucky), b(o.isBestBuddy), b(o.isFavorite), encN(o.fastMoveId),
                encList(o.chargedMoveIds), n(o.secondChargedMoveUnlocked), encSet(o.tags), o.acquisitionMethod.name, n(o.caughtAtEpochMs),
                o.createdAtEpochMs, o.updatedAtEpochMs, encN(o.externalSource), encN(o.externalId), n(o.lastVerifiedAtEpochMs),
                o.verificationSource.name, encSet(o.uncertainFields.map { it.name }.toSet())
            ).joinToString("|")).append('\n')
        }
        s.buildPlans.sortedBy { it.id }.forEach { p ->
            append(listOf("P", enc(p.id), enc(p.ownedPokemonId), p.league.name, b(p.bestBuddyAllowed), encN(p.desiredFastMoveId),
                encList(p.desiredChargedMoveIds), p.status.name, p.priority, encN(p.notes), p.createdAtEpochMs, p.updatedAtEpochMs).joinToString("|")).append('\n')
        }
        s.savedTeams.sortedBy { it.id }.forEach { t ->
            val members = t.members.sortedBy { it.role.ordinal }.joinToString(",") { enc(it.ownedPokemonId) + ":" + it.role.name }
            append(listOf("T", enc(t.id), enc(t.name), t.league.name, enc(members), b(t.isPrimary), encN(t.notes), t.createdAtEpochMs, t.updatedAtEpochMs).joinToString("|")).append('\n')
        }
        s.battles.sortedBy { it.id }.forEach { battle ->
            val own = battle.ownPokemon.sortedBy { it.slot.ordinal }.joinToString(",") { enc(it.ownedPokemonId) + ":" + it.slot.name }
            val opp = battle.opponentPokemon.sortedBy { it.slot.ordinal }.joinToString(",") { o ->
                listOf(enc(o.speciesId), o.slot.name, n(o.isShadow), encN(o.fastMoveId), encList(o.chargedMoveIds)).joinToString(":")
            }
            append(listOf("B", enc(battle.id), battle.league.name, battle.outcome.name, battle.playedAtEpochMs, encN(battle.ownTeamId), enc(own), enc(opp),
                n(battle.ratingBefore), n(battle.ratingAfter), encN(battle.notes), encSet(battle.tags)).joinToString("|")).append('\n')
        }
    }

    private fun decodeOwned(p: List<String>): OwnedPokemon {
        require(p.size == 28)
        return OwnedPokemon(
            id=dec(p[1]), speciesId=dec(p[2]), iv=IvSpread(p[3].toInt(),p[4].toInt(),p[5].toInt()), cp=iN(p[6]), level=dN(p[7]), nickname=decN(p[8]),
            isShadow=bb(p[9]), isPurified=bb(p[10]), isShiny=bb(p[11]), isLucky=bb(p[12]), isBestBuddy=bb(p[13]), isFavorite=bb(p[14]),
            fastMoveId=decN(p[15]), chargedMoveIds=decList(p[16]), secondChargedMoveUnlocked=boolN(p[17]), tags=decSet(p[18]),
            acquisitionMethod=AcquisitionMethod.valueOf(p[19]), caughtAtEpochMs=lN(p[20]), createdAtEpochMs=p[21].toLong(), updatedAtEpochMs=p[22].toLong(),
            externalSource=decN(p[23]), externalId=decN(p[24]), lastVerifiedAtEpochMs=lN(p[25]),
            verificationSource=CollectionVerificationSource.valueOf(p[26]), uncertainFields=decSet(p[27]).filter(String::isNotBlank).map(OwnedPokemonField::valueOf).toSet()
        )
    }

    private fun decodePlan(p: List<String>) = PvpBuildPlan(dec(p[1]),dec(p[2]),League.valueOf(p[3]),bb(p[4]),decN(p[5]),decList(p[6]),BuildStatus.valueOf(p[7]),p[8].toInt(),decN(p[9]),p[10].toLong(),p[11].toLong())
    private fun decodeTeam(p: List<String>): SavedTeam {
        val members = dec(p[4]).split(',').filter(String::isNotBlank).map { token ->
            val idx=token.lastIndexOf(':'); TeamMember(dec(token.substring(0,idx)), TeamRole.valueOf(token.substring(idx+1)))
        }
        return SavedTeam(dec(p[1]),dec(p[2]),League.valueOf(p[3]),members,bb(p[5]),decN(p[6]),p[7].toLong(),p[8].toLong())
    }
    private fun decodeBattle(p: List<String>): BattleRecord {
        val own = dec(p[6]).split(',').filter(String::isNotBlank).map { token -> val i=token.lastIndexOf(':'); OwnBattlePokemon(dec(token.substring(0,i)), BattleSlot.valueOf(token.substring(i+1))) }
        val opp = dec(p[7]).split(',').filter(String::isNotBlank).map { token ->
            val f=token.split(':'); OpponentBattlePokemon(dec(f[0]),BattleSlot.valueOf(f[1]),boolN(f[2]),decN(f[3]),decList(f[4]))
        }
        return BattleRecord(dec(p[1]),League.valueOf(p[2]),BattleOutcome.valueOf(p[3]),p[4].toLong(),decN(p[5]),own,opp,iN(p[8]),iN(p[9]),decN(p[10]),decSet(p[11]))
    }

    private fun enc(v:String)=Base64.getUrlEncoder().withoutPadding().encodeToString(v.toByteArray(StandardCharsets.UTF_8))
    private fun dec(v:String)=String(Base64.getUrlDecoder().decode(v),StandardCharsets.UTF_8)
    private fun encN(v:String?)=if(v==null)"~" else enc(v)
    private fun decN(v:String?)=if(v==null||v=="~")null else dec(v)
    private fun encList(v:Collection<String>)=enc(v.joinToString("\u001f"))
    private fun decList(v:String)=dec(v).split('\u001f').filter(String::isNotBlank)
    private fun encSet(v:Set<String>)=enc(v.sorted().joinToString("\u001f"))
    private fun decSet(v:String)=dec(v).split('\u001f').filter(String::isNotBlank).toSet()
    private fun b(v:Boolean)=if(v)"1" else "0"
    private fun bb(v:String)=v=="1"
    private fun n(v:Any?)=v?.toString()?:"~"
    private fun iN(v:String)=v.takeUnless{it=="~"}?.toInt()
    private fun lN(v:String)=v.takeUnless{it=="~"}?.toLong()
    private fun dN(v:String)=v.takeUnless{it=="~"}?.toDouble()
    private fun boolN(v:String)=v.takeUnless{it=="~"}?.let{it=="true"||it=="1"}
    private fun sha256(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8)).joinToString(""){"%02x".format(it)}
}
