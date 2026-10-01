package com.rui.pvpgo

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.Dao
import androidx.room.withTransaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rui.pvpgo.domain.AcquisitionMethod
import com.rui.pvpgo.domain.BuildStatus
import com.rui.pvpgo.domain.BattleOutcome
import com.rui.pvpgo.domain.BattleRecord
import com.rui.pvpgo.domain.BattleSlot
import com.rui.pvpgo.domain.CandidateInboxItem
import com.rui.pvpgo.domain.CandidateInboxStatus
import com.rui.pvpgo.domain.CollectionVerificationSource
import com.rui.pvpgo.domain.OwnedPokemon
import com.rui.pvpgo.domain.OwnedPokemonField
import com.rui.pvpgo.domain.OpponentBattlePokemon
import com.rui.pvpgo.domain.OwnBattlePokemon
import com.rui.pvpgo.domain.PvpBuildPlan
import com.rui.pvpgo.domain.SavedTeam
import com.rui.pvpgo.domain.TeamMember
import com.rui.pvpgo.domain.TeamRole
import com.rui.pvpgo.engine.IvSpread
import com.rui.pvpgo.engine.League
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val SEP = "\u001F"
private const val BATTLE_RECORD_SEP = "\u001E"
private const val BATTLE_FIELD_SEP = "\u001D"
private const val BATTLE_SUB_SEP = "\u001C"

@Entity(
    tableName = "owned_pokemon",
    indices = [
        Index("speciesId"),
        Index(value = ["externalSource", "externalId"])
    ]
)
data class OwnedPokemonEntity(
    @PrimaryKey val id: String,
    val speciesId: String,
    val ivAttack: Int,
    val ivDefense: Int,
    val ivStamina: Int,
    val cp: Int?,
    val level: Double?,
    val nickname: String?,
    val isShadow: Boolean,
    val isPurified: Boolean,
    val isShiny: Boolean,
    val isLucky: Boolean,
    val isBestBuddy: Boolean,
    val isFavorite: Boolean,
    val fastMoveId: String?,
    val chargedMoveIds: String,
    val secondChargedMoveUnlocked: Boolean?,
    val tags: String,
    val acquisitionMethod: String,
    val caughtAtEpochMs: Long?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val externalSource: String?,
    val externalId: String?,
    val lastVerifiedAtEpochMs: Long?,
    val verificationSource: String,
    val uncertainFields: String
)

@Entity(tableName = "candidate_inbox", indices = [Index("status"), Index("createdAtEpochMs")])
data class CandidateInboxEntity(
    @PrimaryKey val id: String,
    val candidateOwnedId: String,
    val speciesId: String,
    val ivAttack: Int,
    val ivDefense: Int,
    val ivStamina: Int,
    val cp: Int?,
    val level: Double?,
    val nickname: String?,
    val isShadow: Boolean,
    val isPurified: Boolean,
    val isShiny: Boolean,
    val isLucky: Boolean,
    val isBestBuddy: Boolean,
    val isFavorite: Boolean,
    val fastMoveId: String?,
    val chargedMoveIds: String,
    val secondChargedMoveUnlocked: Boolean?,
    val tags: String,
    val acquisitionMethod: String,
    val caughtAtEpochMs: Long?,
    val candidateCreatedAtEpochMs: Long,
    val candidateUpdatedAtEpochMs: Long,
    val externalSource: String?,
    val externalId: String?,
    val lastVerifiedAtEpochMs: Long?,
    val verificationSource: String,
    val uncertainFields: String,
    val createdAtEpochMs: Long,
    val status: String,
    val reviewedAtEpochMs: Long?,
    val linkedOwnedPokemonId: String?
)

@Entity(tableName = "pvp_build_plan", indices = [Index("ownedPokemonId"), Index("league")])
data class PvpBuildPlanEntity(
    @PrimaryKey val id: String,
    val ownedPokemonId: String,
    val league: String,
    val bestBuddyAllowed: Boolean,
    val desiredFastMoveId: String?,
    val desiredChargedMoveIds: String,
    val status: String,
    val priority: Int,
    val notes: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

@Entity(tableName = "saved_team", indices = [Index("league")])
data class SavedTeamEntity(
    @PrimaryKey val id: String,
    val name: String,
    val league: String,
    val members: String,
    val isPrimary: Boolean,
    val notes: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

@Entity(
    tableName = "battle_record",
    indices = [Index("league"), Index("playedAtEpochMs"), Index("ownTeamId")]
)
data class BattleRecordEntity(
    @PrimaryKey val id: String,
    val league: String,
    val outcome: String,
    val playedAtEpochMs: Long,
    val ownTeamId: String?,
    val ownPokemon: String,
    val opponentPokemon: String,
    val ratingBefore: Int?,
    val ratingAfter: Int?,
    val notes: String?,
    val tags: String
)

@Dao
interface CollectionDao {
    @Query("SELECT * FROM owned_pokemon ORDER BY updatedAtEpochMs DESC, id ASC")
    fun observeCollection(): Flow<List<OwnedPokemonEntity>>

    @Query("SELECT * FROM owned_pokemon ORDER BY updatedAtEpochMs DESC, id ASC")
    suspend fun collection(): List<OwnedPokemonEntity>

    @Query("SELECT * FROM owned_pokemon WHERE id = :id LIMIT 1")
    suspend fun ownedById(id: String): OwnedPokemonEntity?

    @Upsert
    suspend fun upsertOwned(item: OwnedPokemonEntity)

    @Query("DELETE FROM owned_pokemon WHERE id = :id")
    suspend fun deleteOwned(id: String)

    @Query("SELECT * FROM candidate_inbox ORDER BY createdAtEpochMs DESC, id ASC")
    fun observeInbox(): Flow<List<CandidateInboxEntity>>

    @Query("SELECT * FROM candidate_inbox WHERE status = 'PENDING' ORDER BY createdAtEpochMs DESC, id ASC")
    fun observePendingInbox(): Flow<List<CandidateInboxEntity>>

    @Query("SELECT * FROM candidate_inbox WHERE id = :id LIMIT 1")
    suspend fun inboxById(id: String): CandidateInboxEntity?

    @Upsert
    suspend fun upsertInbox(items: List<CandidateInboxEntity>)

    @Upsert
    suspend fun upsertInbox(item: CandidateInboxEntity)

    @Query("SELECT * FROM pvp_build_plan ORDER BY priority DESC, updatedAtEpochMs DESC")
    fun observeBuildPlans(): Flow<List<PvpBuildPlanEntity>>

    @Upsert
    suspend fun upsertBuildPlan(item: PvpBuildPlanEntity)

    @Query("SELECT * FROM saved_team ORDER BY isPrimary DESC, updatedAtEpochMs DESC")
    fun observeSavedTeams(): Flow<List<SavedTeamEntity>>

    @Upsert
    suspend fun upsertSavedTeam(item: SavedTeamEntity)

    @Query("SELECT * FROM battle_record ORDER BY playedAtEpochMs DESC, id ASC")
    fun observeBattleRecords(): Flow<List<BattleRecordEntity>>

    @Upsert
    suspend fun upsertBattleRecord(item: BattleRecordEntity)

    @Query("DELETE FROM battle_record WHERE id = :id")
    suspend fun deleteBattleRecord(id: String)
}

@Database(
    entities = [
        OwnedPokemonEntity::class,
        CandidateInboxEntity::class,
        PvpBuildPlanEntity::class,
        SavedTeamEntity::class,
        BattleRecordEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class PvpUserDatabase : RoomDatabase() {
    abstract fun collectionDao(): CollectionDao

    companion object {
        @Volatile private var INSTANCE: PvpUserDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `battle_record` (
                        `id` TEXT NOT NULL,
                        `league` TEXT NOT NULL,
                        `outcome` TEXT NOT NULL,
                        `playedAtEpochMs` INTEGER NOT NULL,
                        `ownTeamId` TEXT,
                        `ownPokemon` TEXT NOT NULL,
                        `opponentPokemon` TEXT NOT NULL,
                        `ratingBefore` INTEGER,
                        `ratingAfter` INTEGER,
                        `notes` TEXT,
                        `tags` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_battle_record_league` ON `battle_record` (`league`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_battle_record_playedAtEpochMs` ON `battle_record` (`playedAtEpochMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_battle_record_ownTeamId` ON `battle_record` (`ownTeamId`)")
            }
        }

        fun get(context: Context): PvpUserDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                PvpUserDatabase::class.java,
                "user.db"
            ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
        }
    }
}

class CollectionRepository private constructor(private val db: PvpUserDatabase) {
    private val dao = db.collectionDao()

    val collection: Flow<List<OwnedPokemon>> = dao.observeCollection().map { rows -> rows.map(OwnedPokemonEntity::toDomain) }
    val inbox: Flow<List<CandidateInboxItem>> = dao.observeInbox().map { rows -> rows.map(CandidateInboxEntity::toDomain) }
    val pendingInbox: Flow<List<CandidateInboxItem>> = dao.observePendingInbox().map { rows -> rows.map(CandidateInboxEntity::toDomain) }
    val buildPlans: Flow<List<PvpBuildPlan>> = dao.observeBuildPlans().map { rows -> rows.map(PvpBuildPlanEntity::toDomain) }
    val savedTeams: Flow<List<SavedTeam>> = dao.observeSavedTeams().map { rows -> rows.map(SavedTeamEntity::toDomain) }
    val battleRecords: Flow<List<BattleRecord>> = dao.observeBattleRecords().map { rows -> rows.map(BattleRecordEntity::toDomain) }

    suspend fun currentCollection(): List<OwnedPokemon> = dao.collection().map(OwnedPokemonEntity::toDomain)

    suspend fun submitCandidates(items: List<CandidateInboxItem>) {
        if (items.isEmpty()) return
        db.withTransaction {
            val stamp = System.currentTimeMillis()
            items.forEachIndexed { index, original ->
                var item = original
                if (dao.inboxById(item.id) != null || dao.ownedById(item.candidate.id) != null) {
                    val suffix = "$stamp-$index"
                    item = item.copy(
                        id = "${item.id}-$suffix",
                        candidate = item.candidate.copy(id = "${item.candidate.id}-$suffix")
                    )
                }
                dao.upsertInbox(item.toEntity())
            }
        }
    }

    suspend fun confirmNew(inboxId: String) {
        db.withTransaction {
            val row = requireNotNull(dao.inboxById(inboxId)) { "Unknown inbox item $inboxId" }
            require(row.status == CandidateInboxStatus.PENDING.name) { "Candidate already reviewed" }
            require(dao.ownedById(row.candidateOwnedId) == null) { "Candidate id already exists in Collection" }
            val now = System.currentTimeMillis()
            val candidate = row.toDomain().candidate
            val owned = candidate.copy(updatedAtEpochMs = maxOf(candidate.updatedAtEpochMs, now))
            dao.upsertOwned(owned.toEntity())
            dao.upsertInbox(row.copy(
                status = CandidateInboxStatus.CONFIRMED_NEW.name,
                reviewedAtEpochMs = now,
                linkedOwnedPokemonId = owned.id
            ))
        }
    }

    suspend fun discardCandidate(inboxId: String) {
        db.withTransaction {
            val row = requireNotNull(dao.inboxById(inboxId)) { "Unknown inbox item $inboxId" }
            require(row.status == CandidateInboxStatus.PENDING.name) { "Candidate already reviewed" }
            dao.upsertInbox(row.copy(
                status = CandidateInboxStatus.DISCARDED.name,
                reviewedAtEpochMs = System.currentTimeMillis()
            ))
        }
    }

    suspend fun upsertOwned(item: OwnedPokemon) = dao.upsertOwned(item.toEntity())
    suspend fun deleteOwned(id: String) = dao.deleteOwned(id)
    suspend fun upsertBuildPlan(item: PvpBuildPlan) = dao.upsertBuildPlan(item.toEntity())
    suspend fun upsertSavedTeam(item: SavedTeam) = dao.upsertSavedTeam(item.toEntity())
    suspend fun upsertBattleRecord(item: BattleRecord) = dao.upsertBattleRecord(item.toEntity())
    suspend fun deleteBattleRecord(id: String) = dao.deleteBattleRecord(id)

    companion object {
        @Volatile private var INSTANCE: CollectionRepository? = null
        fun get(context: Context): CollectionRepository = INSTANCE ?: synchronized(this) {
            INSTANCE ?: CollectionRepository(PvpUserDatabase.get(context)).also { INSTANCE = it }
        }
    }
}

private fun OwnedPokemon.toEntity() = OwnedPokemonEntity(
    id, speciesId, iv.attack, iv.defense, iv.stamina, cp, level, nickname,
    isShadow, isPurified, isShiny, isLucky, isBestBuddy, isFavorite,
    fastMoveId, chargedMoveIds.encode(), secondChargedMoveUnlocked, tags.encode(), acquisitionMethod.name,
    caughtAtEpochMs, createdAtEpochMs, updatedAtEpochMs, externalSource, externalId,
    lastVerifiedAtEpochMs, verificationSource.name, uncertainFields.map { it.name }.toSet().encode()
)

private fun OwnedPokemonEntity.toDomain() = OwnedPokemon(
    id = id,
    speciesId = speciesId,
    iv = IvSpread(ivAttack, ivDefense, ivStamina),
    cp = cp,
    level = level,
    nickname = nickname,
    isShadow = isShadow,
    isPurified = isPurified,
    isShiny = isShiny,
    isLucky = isLucky,
    isBestBuddy = isBestBuddy,
    isFavorite = isFavorite,
    fastMoveId = fastMoveId,
    chargedMoveIds = chargedMoveIds.decodeList(),
    secondChargedMoveUnlocked = secondChargedMoveUnlocked,
    tags = tags.decodeSet(),
    acquisitionMethod = AcquisitionMethod.entries.firstOrNull { it.name == acquisitionMethod } ?: AcquisitionMethod.UNKNOWN,
    caughtAtEpochMs = caughtAtEpochMs,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    externalSource = externalSource,
    externalId = externalId,
    lastVerifiedAtEpochMs = lastVerifiedAtEpochMs,
    verificationSource = CollectionVerificationSource.entries.firstOrNull { it.name == verificationSource } ?: CollectionVerificationSource.MANUAL,
    uncertainFields = uncertainFields.decodeSet().mapNotNullTo(linkedSetOf()) { name -> OwnedPokemonField.entries.firstOrNull { it.name == name } }
)

private fun CandidateInboxItem.toEntity(): CandidateInboxEntity {
    val c = candidate
    return CandidateInboxEntity(
        id = id,
        candidateOwnedId = c.id,
        speciesId = c.speciesId,
        ivAttack = c.iv.attack,
        ivDefense = c.iv.defense,
        ivStamina = c.iv.stamina,
        cp = c.cp,
        level = c.level,
        nickname = c.nickname,
        isShadow = c.isShadow,
        isPurified = c.isPurified,
        isShiny = c.isShiny,
        isLucky = c.isLucky,
        isBestBuddy = c.isBestBuddy,
        isFavorite = c.isFavorite,
        fastMoveId = c.fastMoveId,
        chargedMoveIds = c.chargedMoveIds.encode(),
        secondChargedMoveUnlocked = c.secondChargedMoveUnlocked,
        tags = c.tags.encode(),
        acquisitionMethod = c.acquisitionMethod.name,
        caughtAtEpochMs = c.caughtAtEpochMs,
        candidateCreatedAtEpochMs = c.createdAtEpochMs,
        candidateUpdatedAtEpochMs = c.updatedAtEpochMs,
        externalSource = c.externalSource,
        externalId = c.externalId,
        lastVerifiedAtEpochMs = c.lastVerifiedAtEpochMs,
        verificationSource = c.verificationSource.name,
        uncertainFields = c.uncertainFields.map { it.name }.toSet().encode(),
        createdAtEpochMs = createdAtEpochMs,
        status = status.name,
        reviewedAtEpochMs = reviewedAtEpochMs,
        linkedOwnedPokemonId = linkedOwnedPokemonId
    )
}

private fun CandidateInboxEntity.toDomain(): CandidateInboxItem {
    val candidate = OwnedPokemon(
        id = candidateOwnedId,
        speciesId = speciesId,
        iv = IvSpread(ivAttack, ivDefense, ivStamina),
        cp = cp,
        level = level,
        nickname = nickname,
        isShadow = isShadow,
        isPurified = isPurified,
        isShiny = isShiny,
        isLucky = isLucky,
        isBestBuddy = isBestBuddy,
        isFavorite = isFavorite,
        fastMoveId = fastMoveId,
        chargedMoveIds = chargedMoveIds.decodeList(),
        secondChargedMoveUnlocked = secondChargedMoveUnlocked,
        tags = tags.decodeSet(),
        acquisitionMethod = AcquisitionMethod.entries.firstOrNull { it.name == acquisitionMethod } ?: AcquisitionMethod.UNKNOWN,
        caughtAtEpochMs = caughtAtEpochMs,
        createdAtEpochMs = candidateCreatedAtEpochMs,
        updatedAtEpochMs = candidateUpdatedAtEpochMs,
        externalSource = externalSource,
        externalId = externalId,
        lastVerifiedAtEpochMs = lastVerifiedAtEpochMs,
        verificationSource = CollectionVerificationSource.entries.firstOrNull { it.name == verificationSource } ?: CollectionVerificationSource.MANUAL,
        uncertainFields = uncertainFields.decodeSet().mapNotNullTo(linkedSetOf()) { name -> OwnedPokemonField.entries.firstOrNull { it.name == name } }
    )
    return CandidateInboxItem(
        id = id,
        candidate = candidate,
        createdAtEpochMs = createdAtEpochMs,
        status = CandidateInboxStatus.entries.firstOrNull { it.name == status } ?: CandidateInboxStatus.PENDING,
        reviewedAtEpochMs = reviewedAtEpochMs,
        linkedOwnedPokemonId = linkedOwnedPokemonId
    )
}

private fun PvpBuildPlan.toEntity() = PvpBuildPlanEntity(
    id, ownedPokemonId, league.name, bestBuddyAllowed, desiredFastMoveId,
    desiredChargedMoveIds.encode(), status.name, priority, notes, createdAtEpochMs, updatedAtEpochMs
)

private fun PvpBuildPlanEntity.toDomain() = PvpBuildPlan(
    id = id,
    ownedPokemonId = ownedPokemonId,
    league = League.valueOf(league),
    bestBuddyAllowed = bestBuddyAllowed,
    desiredFastMoveId = desiredFastMoveId,
    desiredChargedMoveIds = desiredChargedMoveIds.decodeList(),
    status = BuildStatus.entries.firstOrNull { it.name == status } ?: BuildStatus.IDEA,
    priority = priority,
    notes = notes,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs
)

private fun SavedTeam.toEntity() = SavedTeamEntity(
    id, name, league.name,
    members.joinToString(SEP) { "${it.ownedPokemonId}:${it.role.name}" },
    isPrimary, notes, createdAtEpochMs, updatedAtEpochMs
)

private fun SavedTeamEntity.toDomain() = SavedTeam(
    id = id,
    name = name,
    league = League.valueOf(league),
    members = members.split(SEP).filter { it.isNotBlank() }.map { encoded ->
        val (ownedId, role) = encoded.split(':', limit = 2)
        TeamMember(ownedId, TeamRole.valueOf(role))
    },
    isPrimary = isPrimary,
    notes = notes,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs
)

private fun BattleRecord.toEntity() = BattleRecordEntity(
    id = id,
    league = league.name,
    outcome = outcome.name,
    playedAtEpochMs = playedAtEpochMs,
    ownTeamId = ownTeamId,
    ownPokemon = ownPokemon.joinToString(BATTLE_RECORD_SEP) { item ->
        listOf(item.ownedPokemonId, item.slot.name).joinToString(BATTLE_FIELD_SEP)
    },
    opponentPokemon = opponentPokemon.joinToString(BATTLE_RECORD_SEP) { item ->
        listOf(
            item.speciesId,
            item.slot.name,
            when (item.isShadow) { true -> "1"; false -> "0"; null -> "" },
            item.fastMoveId.orEmpty(),
            item.chargedMoveIds.joinToString(BATTLE_SUB_SEP)
        ).joinToString(BATTLE_FIELD_SEP)
    },
    ratingBefore = ratingBefore,
    ratingAfter = ratingAfter,
    notes = notes,
    tags = tags.encode()
)

private fun BattleRecordEntity.toDomain() = BattleRecord(
    id = id,
    league = League.valueOf(league),
    outcome = BattleOutcome.valueOf(outcome),
    playedAtEpochMs = playedAtEpochMs,
    ownTeamId = ownTeamId,
    ownPokemon = ownPokemon.split(BATTLE_RECORD_SEP).filter(String::isNotBlank).map { encoded ->
        val parts = encoded.split(BATTLE_FIELD_SEP)
        OwnBattlePokemon(
            ownedPokemonId = parts.getOrElse(0) { "" },
            slot = BattleSlot.valueOf(parts.getOrElse(1) { BattleSlot.LEAD.name })
        )
    },
    opponentPokemon = opponentPokemon.split(BATTLE_RECORD_SEP).filter(String::isNotBlank).mapNotNull { encoded ->
        val parts = encoded.split(BATTLE_FIELD_SEP)
        val speciesId = parts.getOrNull(0).orEmpty()
        if (speciesId.isBlank()) return@mapNotNull null
        OpponentBattlePokemon(
            speciesId = speciesId,
            slot = BattleSlot.valueOf(parts.getOrElse(1) { BattleSlot.LEAD.name }),
            isShadow = when (parts.getOrNull(2)) { "1" -> true; "0" -> false; else -> null },
            fastMoveId = parts.getOrNull(3)?.takeIf(String::isNotBlank),
            chargedMoveIds = parts.getOrNull(4).orEmpty().split(BATTLE_SUB_SEP).filter(String::isNotBlank)
        )
    },
    ratingBefore = ratingBefore,
    ratingAfter = ratingAfter,
    notes = notes,
    tags = tags.decodeSet()
)

private fun Collection<String>.encode(): String = joinToString(SEP)
private fun String.decodeList(): List<String> = split(SEP).filter { it.isNotBlank() }
private fun String.decodeSet(): Set<String> = decodeList().toSet()
