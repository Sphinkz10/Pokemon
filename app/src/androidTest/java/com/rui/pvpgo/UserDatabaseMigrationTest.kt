package com.rui.pvpgo

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Recreates the v1 tables from the exported v2 schema (without v2 battle_record).
 * This fixture tests the real v1-to-v2 Room migration with an existing saved team.
 */
@RunWith(AndroidJUnit4::class)
class UserDatabaseMigrationTest {
    @Test fun migrationFromV1KeepsSavedTeamAndAddsBattleRecord() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val filename = "room-migration-v1-to-v2.db"
        context.deleteDatabase(filename)
        try {
            val sqlite = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(filename), null)
            try {
                listOf(
                "CREATE TABLE IF NOT EXISTS `owned_pokemon` (`id` TEXT NOT NULL, `speciesId` TEXT NOT NULL, `ivAttack` INTEGER NOT NULL, `ivDefense` INTEGER NOT NULL, `ivStamina` INTEGER NOT NULL, `cp` INTEGER, `level` REAL, `nickname` TEXT, `isShadow` INTEGER NOT NULL, `isPurified` INTEGER NOT NULL, `isShiny` INTEGER NOT NULL, `isLucky` INTEGER NOT NULL, `isBestBuddy` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `fastMoveId` TEXT, `chargedMoveIds` TEXT NOT NULL, `secondChargedMoveUnlocked` INTEGER, `tags` TEXT NOT NULL, `acquisitionMethod` TEXT NOT NULL, `caughtAtEpochMs` INTEGER, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, `externalSource` TEXT, `externalId` TEXT, `lastVerifiedAtEpochMs` INTEGER, `verificationSource` TEXT NOT NULL, `uncertainFields` TEXT NOT NULL, PRIMARY KEY(`id`))",
                "CREATE INDEX IF NOT EXISTS `index_owned_pokemon_speciesId` ON `owned_pokemon` (`speciesId`)",
                "CREATE INDEX IF NOT EXISTS `index_owned_pokemon_externalSource_externalId` ON `owned_pokemon` (`externalSource`, `externalId`)",
                "CREATE TABLE IF NOT EXISTS `candidate_inbox` (`id` TEXT NOT NULL, `candidateOwnedId` TEXT NOT NULL, `speciesId` TEXT NOT NULL, `ivAttack` INTEGER NOT NULL, `ivDefense` INTEGER NOT NULL, `ivStamina` INTEGER NOT NULL, `cp` INTEGER, `level` REAL, `nickname` TEXT, `isShadow` INTEGER NOT NULL, `isPurified` INTEGER NOT NULL, `isShiny` INTEGER NOT NULL, `isLucky` INTEGER NOT NULL, `isBestBuddy` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, `fastMoveId` TEXT, `chargedMoveIds` TEXT NOT NULL, `secondChargedMoveUnlocked` INTEGER, `tags` TEXT NOT NULL, `acquisitionMethod` TEXT NOT NULL, `caughtAtEpochMs` INTEGER, `candidateCreatedAtEpochMs` INTEGER NOT NULL, `candidateUpdatedAtEpochMs` INTEGER NOT NULL, `externalSource` TEXT, `externalId` TEXT, `lastVerifiedAtEpochMs` INTEGER, `verificationSource` TEXT NOT NULL, `uncertainFields` TEXT NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, `status` TEXT NOT NULL, `reviewedAtEpochMs` INTEGER, `linkedOwnedPokemonId` TEXT, PRIMARY KEY(`id`))",
                "CREATE INDEX IF NOT EXISTS `index_candidate_inbox_status` ON `candidate_inbox` (`status`)",
                "CREATE INDEX IF NOT EXISTS `index_candidate_inbox_createdAtEpochMs` ON `candidate_inbox` (`createdAtEpochMs`)",
                "CREATE TABLE IF NOT EXISTS `pvp_build_plan` (`id` TEXT NOT NULL, `ownedPokemonId` TEXT NOT NULL, `league` TEXT NOT NULL, `bestBuddyAllowed` INTEGER NOT NULL, `desiredFastMoveId` TEXT, `desiredChargedMoveIds` TEXT NOT NULL, `status` TEXT NOT NULL, `priority` INTEGER NOT NULL, `notes` TEXT, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                "CREATE INDEX IF NOT EXISTS `index_pvp_build_plan_ownedPokemonId` ON `pvp_build_plan` (`ownedPokemonId`)",
                "CREATE INDEX IF NOT EXISTS `index_pvp_build_plan_league` ON `pvp_build_plan` (`league`)",
                "CREATE TABLE IF NOT EXISTS `saved_team` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `league` TEXT NOT NULL, `members` TEXT NOT NULL, `isPrimary` INTEGER NOT NULL, `notes` TEXT, `createdAtEpochMs` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                "CREATE INDEX IF NOT EXISTS `index_saved_team_league` ON `saved_team` (`league`)"
                ).forEach(sqlite::execSQL)
                sqlite.execSQL(
                    "INSERT INTO saved_team (id, name, league, members, isPrimary, notes, createdAtEpochMs, updatedAtEpochMs) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf("legacy-team", "Equipa anterior", "GREAT", "legacy-member", 0, "Preservar", 100L, 100L)
                )
                sqlite.version = 1
            } finally {
                sqlite.close()
            }
            val upgraded = Room.databaseBuilder(context, PvpUserDatabase::class.java, filename)
                .addMigrations(PvpUserDatabase.MIGRATION_1_2)
                .build()
            try {
                val rows = upgraded.collectionDao().observeSavedTeams().first()
                assertEquals(1, rows.size)
                assertEquals("legacy-team", rows.single().id)
                assertEquals("Equipa anterior", rows.single().name)
                assertEquals("Preservar", rows.single().notes)
                assertTrue(upgraded.collectionDao().observeBattleRecords().first().isEmpty())
            } finally {
                upgraded.close()
            }
        } finally {
            context.deleteDatabase(filename)
        }
    }
}
