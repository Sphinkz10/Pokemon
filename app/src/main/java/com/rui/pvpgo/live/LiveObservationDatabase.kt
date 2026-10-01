package com.rui.pvpgo.live

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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "capture_session", indices = [Index("startedAtEpochMs"), Index("status")])
data class CaptureSessionEntity(
    @PrimaryKey val id: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long?,
    val status: String,
    val frameCount: Long,
    val lastFrameAtEpochMs: Long?,
    val width: Int?,
    val height: Int?,
    val rotationDegrees: Int?,
    val stopReason: String?
)

@Entity(tableName = "observation_journal", indices = [Index("sessionId"), Index("observedAtEpochMs")])
data class ObservationJournalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val observedAtEpochMs: Long,
    val kind: String,
    val detail: String?
)

@Dao
interface LiveObservationDao {
    @Query("SELECT * FROM capture_session ORDER BY startedAtEpochMs DESC LIMIT 20")
    fun observeRecentSessions(): Flow<List<CaptureSessionEntity>>

    @Upsert
    suspend fun upsertSession(item: CaptureSessionEntity)

    @Query("SELECT * FROM capture_session WHERE id = :id LIMIT 1")
    suspend fun session(id: String): CaptureSessionEntity?

    @Query(
        "UPDATE capture_session SET frameCount = :frameCount, lastFrameAtEpochMs = :lastFrameAtEpochMs, " +
            "width = :width, height = :height, rotationDegrees = :rotationDegrees WHERE id = :sessionId"
    )
    suspend fun checkpointFrame(
        sessionId: String,
        frameCount: Long,
        lastFrameAtEpochMs: Long,
        width: Int,
        height: Int,
        rotationDegrees: Int
    )

    @Query(
        "UPDATE capture_session SET endedAtEpochMs = :endedAtEpochMs, status = :status, " +
            "frameCount = :frameCount, stopReason = :stopReason WHERE id = :sessionId"
    )
    suspend fun finishSession(
        sessionId: String,
        endedAtEpochMs: Long,
        status: String,
        frameCount: Long,
        stopReason: String?
    )

    @androidx.room.Insert
    suspend fun insertEvent(item: ObservationJournalEntity)
}

@Database(
    entities = [CaptureSessionEntity::class, ObservationJournalEntity::class],
    version = 1,
    exportSchema = true
)
abstract class LiveObservationDatabase : RoomDatabase() {
    abstract fun dao(): LiveObservationDao

    companion object {
        @Volatile private var INSTANCE: LiveObservationDatabase? = null

        fun get(context: Context): LiveObservationDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                LiveObservationDatabase::class.java,
                "staging.db"
            ).build().also { INSTANCE = it }
        }
    }
}

class LiveObservationRepository private constructor(private val db: LiveObservationDatabase) {
    private val dao = db.dao()
    val recentSessions: Flow<List<CaptureSessionEntity>> = dao.observeRecentSessions()

    suspend fun startSession(sessionId: String, startedAtEpochMs: Long) {
        dao.upsertSession(
            CaptureSessionEntity(
                id = sessionId,
                startedAtEpochMs = startedAtEpochMs,
                endedAtEpochMs = null,
                status = "CAPTURING",
                frameCount = 0,
                lastFrameAtEpochMs = null,
                width = null,
                height = null,
                rotationDegrees = null,
                stopReason = null
            )
        )
        event(sessionId, startedAtEpochMs, "SESSION_STARTED", null)
    }

    suspend fun checkpoint(sessionId: String, frameCount: Long, frame: CapturedFrame) {
        dao.checkpointFrame(sessionId, frameCount, frame.capturedAtEpochMs, frame.width, frame.height, frame.rotationDegrees)
        dao.insertEvent(
            ObservationJournalEntity(
                sessionId = sessionId,
                observedAtEpochMs = frame.capturedAtEpochMs,
                kind = "FRAME_CHECKPOINT",
                detail = "frame=$frameCount ${frame.width}x${frame.height} rot=${frame.rotationDegrees}"
            )
        )
    }

    suspend fun finish(sessionId: String, frameCount: Long, status: String, reason: String?) {
        val now = System.currentTimeMillis()
        dao.finishSession(sessionId, now, status, frameCount, reason)
        event(sessionId, now, "SESSION_FINISHED", "status=$status reason=${reason ?: "none"}")
    }

    suspend fun event(sessionId: String, at: Long, kind: String, detail: String?) {
        dao.insertEvent(ObservationJournalEntity(sessionId = sessionId, observedAtEpochMs = at, kind = kind, detail = detail))
    }

    companion object {
        @Volatile private var INSTANCE: LiveObservationRepository? = null
        fun get(context: Context): LiveObservationRepository = INSTANCE ?: synchronized(this) {
            INSTANCE ?: LiveObservationRepository(LiveObservationDatabase.get(context)).also { INSTANCE = it }
        }
    }
}
