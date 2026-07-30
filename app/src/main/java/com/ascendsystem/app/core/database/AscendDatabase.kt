package com.ascendsystem.app.core.database

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ascendsystem.app.feature.assessment.data.AppMetadataEntity
import com.ascendsystem.app.feature.assessment.data.AssessmentDao
import com.ascendsystem.app.feature.assessment.data.AssessmentDraftEntity
import com.ascendsystem.app.feature.assessment.data.PersonalProtocolEntity
import com.ascendsystem.app.feature.verification.data.VerificationDao
import com.ascendsystem.app.feature.verification.data.VerificationSessionEntity
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "quests")
data class QuestEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val type: String,
    val verificationType: String,
    val targetValue: Int,
    val rewardXp: Int,
    val scheduledAtEpochMs: Long?,
    val deadlineAtEpochMs: Long?,
    val status: String,
    val safetyLevel: String,
    val createdBy: String,
    val updatedAtEpochMs: Long
)

@Entity(tableName = "app_restrictions")
data class AppRestrictionEntity(
    @PrimaryKey val packageName: String,
    val displayName: String,
    val category: String,
    val dailyLimitMinutes: Int?,
    val sessionLimitMinutes: Int?,
    val isEssential: Boolean,
    val enabled: Boolean
)

@Entity(tableName = "override_logs")
data class OverrideLogEntity(
    @PrimaryKey val id: String,
    val timestampEpochMs: Long,
    val reason: String,
    val note: String?,
    val durationMinutes: Int,
    val activeQuestId: String?
)

@Entity(tableName = "system_state")
data class SystemStateEntity(
    @PrimaryKey val singletonId: Int = 1,
    val state: String,
    val activeQuestId: String?,
    val lockReason: String?,
    val updatedAtEpochMs: Long
)

@Dao
interface QuestDao {
    @Query("SELECT * FROM quests ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<QuestEntity>>
    @Query("SELECT * FROM quests ORDER BY updatedAtEpochMs DESC")
    suspend fun all(): List<QuestEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: QuestEntity)
    @Query("DELETE FROM quests WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(
    entities = [
        QuestEntity::class, AppRestrictionEntity::class, OverrideLogEntity::class, SystemStateEntity::class,
        AssessmentDraftEntity::class, PersonalProtocolEntity::class, AppMetadataEntity::class,
        VerificationSessionEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AscendDatabase : RoomDatabase() {
    abstract fun questDao(): QuestDao
    abstract fun assessmentDao(): AssessmentDao
    abstract fun verificationDao(): VerificationDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS assessment_drafts (id INTEGER NOT NULL PRIMARY KEY, payloadJson TEXT NOT NULL, currentStep TEXT NOT NULL, updatedAtEpochMs INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS personal_protocols (id TEXT NOT NULL PRIMARY KEY, payloadJson TEXT NOT NULL, status TEXT NOT NULL, protocolVersion INTEGER NOT NULL, contractVersion INTEGER, activatedAtEpochMs INTEGER)")
                db.execSQL("CREATE TABLE IF NOT EXISTS app_metadata (singletonId INTEGER NOT NULL PRIMARY KEY, assessmentCompleted INTEGER NOT NULL, assessmentVersion INTEGER NOT NULL, protocolActivated INTEGER NOT NULL, migratedFromV1 INTEGER NOT NULL)")
                db.execSQL("INSERT OR IGNORE INTO app_metadata VALUES (1, 0, 0, 0, 1)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS verification_sessions (id TEXT NOT NULL PRIMARY KEY, questId TEXT NOT NULL, type TEXT NOT NULL, targetType TEXT NOT NULL, targetValue INTEGER NOT NULL, safetyLevel TEXT NOT NULL, status TEXT NOT NULL, createdAtMillis INTEGER NOT NULL, startedAtMillis INTEGER, completedAtMillis INTEGER, progress REAL NOT NULL, confidence REAL, failureReasons TEXT NOT NULL, metrics TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_verification_sessions_questId ON verification_sessions (questId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_verification_sessions_status ON verification_sessions (status)")
            }
        }
    }
}
