package com.rui.pvpgo

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.withTransaction
import com.rui.pvpgo.domain.DexRangeSpec
import com.rui.pvpgo.domain.MetaBuildEntry
import com.rui.pvpgo.domain.OpponentBuild
import com.rui.pvpgo.domain.PackProvenance
import com.rui.pvpgo.domain.PvPRuleset
import com.rui.pvpgo.domain.RulesetFilter
import com.rui.pvpgo.domain.RulesetFilterKind
import com.rui.pvpgo.domain.VersionedGameDataPack
import com.rui.pvpgo.domain.VersionedMetaGroup
import com.rui.pvpgo.engine.CpMultipliers
import com.rui.pvpgo.engine.GameDataPackHashing
import com.rui.pvpgo.engine.GameDataPackValidator
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import com.rui.pvpgo.engine.MetaPackAssembler
import com.rui.pvpgo.engine.MetaPackValidator
import com.rui.pvpgo.engine.PokemonSpecies
import com.rui.pvpgo.engine.PvpMove
import com.rui.pvpgo.engine.PvpokePublicMetaProvider
import com.rui.pvpgo.engine.StandardRulesets
import com.rui.pvpgo.engine.VersionedGameDataPackFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val ITEM_SEP = "\u001F"
private const val FILTER_SEP = "\u001E"
private const val FILTER_PART_SEP = "\u001D"

/** Replaceable reference-data storage. User Collection stays isolated in user.db. */
@Entity(tableName = "data_pack_snapshot")
data class DataPackSnapshotEntity(
    @PrimaryKey val contentHashSha256: String,
    val packId: String,
    val version: String,
    val generatedAtEpochMs: Long,
    val sourceName: String,
    val sourceRevision: String,
    val sourceUrl: String?,
    val pokemonCount: Int,
    val moveCount: Int,
    val cpmCount: Int,
    val typeChartVersion: String
)

@Entity(
    tableName = "reference_pack",
    indices = [Index("league"), Index("status"), Index("activatedAtEpochMs")]
)
data class ReferencePackEntity(
    @PrimaryKey val packKey: String,
    val metaPackId: String,
    val metaPackVersion: String,
    val metaContentHashSha256: String,
    val league: String,
    val status: String,
    val metaGroupId: String,
    val seasonId: String,
    val cupId: String?,
    val sourceName: String,
    val sourceVersion: String,
    val generatedAtEpochMs: Long,
    val rulesetId: String,
    val rulesetVersion: String,
    val dataPackHashSha256: String,
    val entryCount: Int,
    val activatedAtEpochMs: Long
)

@Entity(tableName = "ruleset_snapshot")
data class RulesetSnapshotEntity(
    @PrimaryKey val packKey: String,
    val id: String,
    val version: String,
    val title: String,
    val baseLeague: String,
    val cpCap: Int?,
    val minLevel: Double,
    val maxLevel: Double,
    val rankingIvFloor: Int,
    val allowShadow: Boolean,
    val allowMega: Boolean,
    val allowUnreleased: Boolean,
    val includeFilters: String,
    val excludeFilters: String,
    val explicitIncludes: String,
    val explicitExcludes: String,
    val sourceName: String,
    val sourceVersion: String,
    val generatedAtEpochMs: Long
)

@Entity(
    tableName = "meta_entry",
    primaryKeys = ["packKey", "entryKey"],
    indices = [Index("packKey"), Index("opponentSpeciesId")]
)
data class MetaEntryEntity(
    val packKey: String,
    val entryKey: String,
    val opponentSpeciesId: String,
    val fastMoveId: String,
    val chargedMoveIds: String,
    val ivAttack: Int,
    val ivDefense: Int,
    val ivStamina: Int,
    val level: Double,
    val isShadow: Boolean,
    val weight: Double,
    val labels: String
)

@Dao
interface ReferencePackDao {
    @Query("SELECT * FROM reference_pack WHERE status = 'ACTIVE' ORDER BY league ASC")
    fun observeActivePacks(): Flow<List<ReferencePackEntity>>

    @Query("SELECT * FROM reference_pack WHERE league = :league AND status = 'ACTIVE' LIMIT 1")
    suspend fun activePack(league: String): ReferencePackEntity?

    @Query("SELECT * FROM reference_pack WHERE league = :league AND status = 'SUPERSEDED' ORDER BY activatedAtEpochMs DESC LIMIT 1")
    suspend fun previousPack(league: String): ReferencePackEntity?

    @Query("SELECT * FROM ruleset_snapshot WHERE packKey = :packKey LIMIT 1")
    suspend fun ruleset(packKey: String): RulesetSnapshotEntity?

    @Query("SELECT * FROM meta_entry WHERE packKey = :packKey ORDER BY entryKey ASC")
    suspend fun entries(packKey: String): List<MetaEntryEntity>

    @Query("SELECT * FROM data_pack_snapshot WHERE contentHashSha256 = :hash LIMIT 1")
    suspend fun dataPack(hash: String): DataPackSnapshotEntity?

    @Upsert suspend fun upsertDataPack(item: DataPackSnapshotEntity)
    @Upsert suspend fun upsertReferencePack(item: ReferencePackEntity)
    @Upsert suspend fun upsertRuleset(item: RulesetSnapshotEntity)
    @Upsert suspend fun upsertEntries(items: List<MetaEntryEntity>)

    @Query("DELETE FROM meta_entry WHERE packKey = :packKey")
    suspend fun deleteEntries(packKey: String)

    @Query("UPDATE reference_pack SET status = 'SUPERSEDED' WHERE league = :league AND status = 'ACTIVE'")
    suspend fun supersedeActive(league: String)

    @Query("UPDATE reference_pack SET status = :status, activatedAtEpochMs = :activatedAt WHERE packKey = :packKey")
    suspend fun setStatus(packKey: String, status: String, activatedAt: Long)
}

@Database(
    entities = [DataPackSnapshotEntity::class, ReferencePackEntity::class, RulesetSnapshotEntity::class, MetaEntryEntity::class],
    version = 1,
    exportSchema = true
)
abstract class ReferencePackDatabase : RoomDatabase() {
    abstract fun dao(): ReferencePackDao

    companion object {
        @Volatile private var INSTANCE: ReferencePackDatabase? = null

        fun get(context: Context): ReferencePackDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                ReferencePackDatabase::class.java,
                "reference.db"
            ).build().also { INSTANCE = it }
        }
    }
}

data class ReferencePackSummary(
    val league: League,
    val sourceName: String,
    val sourceVersion: String,
    val generatedAtEpochMs: Long,
    val entryCount: Int,
    val dataPackHashSha256: String,
    val status: String
)

data class ActiveReferenceBundle(
    val dataPack: VersionedGameDataPack,
    val ruleset: PvPRuleset,
    val meta: VersionedMetaGroup,
    val summary: ReferencePackSummary
)

data class ReferenceResolution(
    val bundle: ActiveReferenceBundle?,
    val reason: String? = null
)

class ReferencePackRepository private constructor(private val db: ReferencePackDatabase) {
    private val dao = db.dao()

    val activeSummaries: Flow<List<ReferencePackSummary>> = dao.observeActivePacks().map { rows ->
        rows.map { it.toSummary() }
    }

    suspend fun syncPublicStandard(
        catalog: List<PokemonSpecies>,
        moves: List<PvpMove>,
        league: League
    ): ReferencePackSummary = withContext(Dispatchers.IO) {
        require(catalog.isNotEmpty() && moves.isNotEmpty()) { "Data Pack local ainda não está carregado." }
        val provider = PvpokePublicMetaProvider(JavaNetPvpokeSourceTransport("PvPGoPersonal/1.32"), OrgJsonPvpokePayloadDecoder)
        val snapshot = provider.fetch(league = league, cup = "all")
        val dataPack = runtimeDataPack(catalog, moves, snapshot.fetchedAtEpochMs)
        val dataReport = GameDataPackValidator.validate(dataPack)
        require(dataReport.valid) { "Data Pack local inválido: ${dataReport.errors.joinToString()}" }

        val ruleset = StandardRulesets.forLeague(league, snapshot.fetchedAtEpochMs)
        val metaPack = MetaPackAssembler.fromProviderSnapshot(
            packId = "pvpoke-${league.name.lowercase()}-all",
            packVersion = snapshot.revision.take(12),
            dataPack = dataPack,
            ruleset = ruleset,
            snapshot = snapshot,
            seasonId = "public-overall",
            generatedAtEpochMs = snapshot.fetchedAtEpochMs,
            topN = 50
        )
        val metaReport = MetaPackValidator.validate(metaPack, dataPack)
        require(metaReport.valid) { "Meta Pack rejeitado: ${metaReport.errors.joinToString()}" }
        val group = requireNotNull(metaPack.groups.singleOrNull()) { "Meta Pack deve conter exatamente um grupo nesta integração." }
        activate(dataPack, ruleset, group, metaPack.manifest.packId, metaPack.manifest.version, metaPack.manifest.contentHashSha256)
    }

    suspend fun resolve(
        catalog: List<PokemonSpecies>,
        moves: List<PvpMove>,
        league: League
    ): ReferenceResolution = withContext(Dispatchers.IO) {
        val active = dao.activePack(league.name) ?: return@withContext ReferenceResolution(null, "Ainda não existe Meta Pack ativo para ${leagueLabelLocal(league)}.")
        val rulesetRow = dao.ruleset(active.packKey) ?: return@withContext ReferenceResolution(null, "Ruleset persistente em falta para o Meta Pack ativo.")
        val entryRows = dao.entries(active.packKey)
        if (entryRows.isEmpty()) return@withContext ReferenceResolution(null, "Meta Pack ativo não contém entradas persistentes.")

        val dataPack = runtimeDataPack(catalog, moves, active.generatedAtEpochMs)
        if (dataPack.manifest.contentHashSha256 != active.dataPackHashSha256) {
            return@withContext ReferenceResolution(
                null,
                "O catálogo/moves atual não corresponde ao Data Pack usado por este Meta Pack. Atualiza o Meta Pack antes de usar Team Lab."
            )
        }
        val ruleset = rulesetRow.toDomain()
        if (ruleset.baseLeague != league || ruleset.id != active.rulesetId || ruleset.version != active.rulesetVersion) {
            return@withContext ReferenceResolution(null, "Ruleset/meta incompatíveis; análise recusada.")
        }
        val meta = VersionedMetaGroup(
            id = active.metaGroupId,
            league = league,
            seasonId = active.seasonId,
            cupId = active.cupId,
            sourceName = active.sourceName,
            sourceVersion = active.sourceVersion,
            generatedAtEpochMs = active.generatedAtEpochMs,
            entries = entryRows.map(MetaEntryEntity::toDomain),
            rulesetId = active.rulesetId,
            rulesetVersion = active.rulesetVersion,
            dataPackHashSha256 = active.dataPackHashSha256
        )
        ReferenceResolution(ActiveReferenceBundle(dataPack, ruleset, meta, active.toSummary()))
    }

    suspend fun rollback(league: League): Boolean = withContext(Dispatchers.IO) {
        db.withTransaction {
            val current = dao.activePack(league.name) ?: return@withTransaction false
            val previous = dao.previousPack(league.name) ?: return@withTransaction false
            val now = System.currentTimeMillis()
            dao.setStatus(current.packKey, "SUPERSEDED", now)
            dao.setStatus(previous.packKey, "ACTIVE", now)
            true
        }
    }

    private suspend fun activate(
        dataPack: VersionedGameDataPack,
        ruleset: PvPRuleset,
        group: VersionedMetaGroup,
        metaPackId: String,
        metaPackVersion: String,
        metaContentHashSha256: String
    ): ReferencePackSummary {
        require(group.rulesetId == ruleset.id && group.rulesetVersion == ruleset.version)
        require(group.dataPackHashSha256 == dataPack.manifest.contentHashSha256)
        val now = System.currentTimeMillis()
        val packKey = "${group.league.name}:${group.sourceVersion}:${metaContentHashSha256.take(12)}"
        val row = ReferencePackEntity(
            packKey = packKey,
            metaPackId = metaPackId,
            metaPackVersion = metaPackVersion,
            metaContentHashSha256 = metaContentHashSha256,
            league = group.league.name,
            status = "ACTIVE",
            metaGroupId = group.id,
            seasonId = group.seasonId,
            cupId = group.cupId,
            sourceName = group.sourceName,
            sourceVersion = group.sourceVersion,
            generatedAtEpochMs = group.generatedAtEpochMs,
            rulesetId = ruleset.id,
            rulesetVersion = ruleset.version,
            dataPackHashSha256 = dataPack.manifest.contentHashSha256,
            entryCount = group.entries.size,
            activatedAtEpochMs = now
        )
        db.withTransaction {
            dao.upsertDataPack(dataPack.toEntity())
            dao.supersedeActive(group.league.name)
            dao.upsertReferencePack(row)
            dao.upsertRuleset(ruleset.toEntity(packKey))
            dao.deleteEntries(packKey)
            dao.upsertEntries(group.entries.map { it.toEntity(packKey) })
        }
        return row.toSummary()
    }

    private fun runtimeDataPack(
        catalog: List<PokemonSpecies>,
        moves: List<PvpMove>,
        generatedAtEpochMs: Long
    ): VersionedGameDataPack {
        val hash = GameDataPackHashing.contentHash(
            pokemon = catalog,
            moves = moves,
            cpmByLevel = CpMultipliers.supportedLevels.associateWith(CpMultipliers::at),
            supportedTypes = VersionedGameDataPackFactory.defaultTypes,
            typeChartVersion = "pokemon-go-pvp-types-v1"
        )
        return VersionedGameDataPackFactory.create(
            packId = "runtime-game-data",
            version = hash.take(16),
            generatedAtEpochMs = generatedAtEpochMs,
            provenance = PackProvenance(
                sourceName = "PokemonRepository runtime catalog",
                sourceRevision = hash
            ),
            pokemon = catalog,
            moves = moves
        )
    }

    companion object {
        @Volatile private var INSTANCE: ReferencePackRepository? = null
        fun get(context: Context): ReferencePackRepository = INSTANCE ?: synchronized(this) {
            INSTANCE ?: ReferencePackRepository(ReferencePackDatabase.get(context)).also { INSTANCE = it }
        }
    }
}

private fun ReferencePackEntity.toSummary() = ReferencePackSummary(
    league = League.valueOf(league),
    sourceName = sourceName,
    sourceVersion = sourceVersion,
    generatedAtEpochMs = generatedAtEpochMs,
    entryCount = entryCount,
    dataPackHashSha256 = dataPackHashSha256,
    status = status
)

private fun VersionedGameDataPack.toEntity() = DataPackSnapshotEntity(
    contentHashSha256 = manifest.contentHashSha256,
    packId = manifest.packId,
    version = manifest.version,
    generatedAtEpochMs = manifest.generatedAtEpochMs,
    sourceName = manifest.provenance.sourceName,
    sourceRevision = manifest.provenance.sourceRevision,
    sourceUrl = manifest.provenance.sourceUrl,
    pokemonCount = manifest.pokemonCount,
    moveCount = manifest.moveCount,
    cpmCount = manifest.cpmCount,
    typeChartVersion = manifest.typeChartVersion
)

private fun PvPRuleset.toEntity(packKey: String) = RulesetSnapshotEntity(
    packKey = packKey,
    id = id,
    version = version,
    title = title,
    baseLeague = baseLeague.name,
    cpCap = cpCap,
    minLevel = minLevel,
    maxLevel = maxLevel,
    rankingIvFloor = rankingIvFloor,
    allowShadow = allowShadow,
    allowMega = allowMega,
    allowUnreleased = allowUnreleased,
    includeFilters = includeFilters.encodeFilters(),
    excludeFilters = excludeFilters.encodeFilters(),
    explicitIncludes = explicitlyIncludedSpeciesIds.encodeSet(),
    explicitExcludes = explicitlyExcludedSpeciesIds.encodeSet(),
    sourceName = sourceName,
    sourceVersion = sourceVersion,
    generatedAtEpochMs = generatedAtEpochMs
)

private fun RulesetSnapshotEntity.toDomain() = PvPRuleset(
    id = id,
    version = version,
    title = title,
    baseLeague = League.valueOf(baseLeague),
    cpCap = cpCap,
    minLevel = minLevel,
    maxLevel = maxLevel,
    rankingIvFloor = rankingIvFloor,
    allowShadow = allowShadow,
    allowMega = allowMega,
    allowUnreleased = allowUnreleased,
    includeFilters = includeFilters.decodeFilters(),
    excludeFilters = excludeFilters.decodeFilters(),
    explicitlyIncludedSpeciesIds = explicitIncludes.decodeSet(),
    explicitlyExcludedSpeciesIds = explicitExcludes.decodeSet(),
    sourceName = sourceName,
    sourceVersion = sourceVersion,
    generatedAtEpochMs = generatedAtEpochMs
)

private fun MetaBuildEntry.toEntity(packKey: String): MetaEntryEntity {
    val iv = requireNotNull(opponent.iv)
    return MetaEntryEntity(
        packKey = packKey,
        entryKey = key,
        opponentSpeciesId = opponent.speciesId,
        fastMoveId = requireNotNull(opponent.fastMoveId),
        chargedMoveIds = opponent.chargedMoveIds.encodeSetPreserveOrder(),
        ivAttack = iv.attack,
        ivDefense = iv.defense,
        ivStamina = iv.stamina,
        level = requireNotNull(opponent.level),
        isShadow = requireNotNull(opponent.isShadow),
        weight = weight,
        labels = labels.encodeSet()
    )
}

private fun MetaEntryEntity.toDomain() = MetaBuildEntry(
    key = entryKey,
    opponent = OpponentBuild(
        speciesId = opponentSpeciesId,
        fastMoveId = fastMoveId,
        chargedMoveIds = chargedMoveIds.decodeList(),
        iv = IvSpread(ivAttack, ivDefense, ivStamina),
        level = level,
        isShadow = isShadow
    ),
    weight = weight,
    labels = labels.decodeSet()
)

private fun Set<String>.encodeSet(): String = sorted().joinToString(ITEM_SEP)
private fun List<String>.encodeSetPreserveOrder(): String = joinToString(ITEM_SEP)
private fun String.decodeSet(): Set<String> = if (isBlank()) emptySet() else split(ITEM_SEP).filter(String::isNotBlank).toSet()
private fun String.decodeList(): List<String> = if (isBlank()) emptyList() else split(ITEM_SEP).filter(String::isNotBlank)

private fun List<RulesetFilter>.encodeFilters(): String = joinToString(FILTER_SEP) { filter ->
    val values = filter.values.sorted().joinToString(ITEM_SEP)
    val ranges = filter.dexRanges.joinToString(ITEM_SEP) { "${it.start}-${it.endInclusive}" }
    listOf(filter.kind.name, values, ranges).joinToString(FILTER_PART_SEP)
}

private fun String.decodeFilters(): List<RulesetFilter> {
    if (isBlank()) return emptyList()
    return split(FILTER_SEP).mapNotNull { raw ->
        val parts = raw.split(FILTER_PART_SEP)
        if (parts.isEmpty()) return@mapNotNull null
        val kind = runCatching { RulesetFilterKind.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
        val values = parts.getOrNull(1).orEmpty().decodeSet()
        val ranges = parts.getOrNull(2).orEmpty().decodeList().mapNotNull { token ->
            val pair = token.split('-', limit = 2)
            val start = pair.getOrNull(0)?.toIntOrNull()
            val end = pair.getOrNull(1)?.toIntOrNull()
            if (start != null && end != null) DexRangeSpec(start, end) else null
        }
        if (kind == RulesetFilterKind.DEX_RANGE && ranges.isEmpty()) null
        else if (kind != RulesetFilterKind.DEX_RANGE && values.isEmpty()) null
        else RulesetFilter(kind = kind, values = values, dexRanges = ranges)
    }
}

private fun leagueLabelLocal(league: League): String = when (league) {
    League.LITTLE -> "Little"
    League.GREAT -> "Great"
    League.ULTRA -> "Ultra"
    League.MASTER -> "Master"
}
