package com.ascendsystem.app.feature.verification.data

import androidx.room.*
import com.ascendsystem.app.core.domain.SafetyLevel
import com.ascendsystem.app.core.domain.VerificationType
import com.ascendsystem.app.feature.verification.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Base64
import javax.inject.Inject

@Entity(
    tableName = "verification_sessions",
    indices = [Index("questId"), Index("status")]
)
data class VerificationSessionEntity(
    @PrimaryKey val id: String,
    val questId: String,
    val type: String,
    val targetType: String,
    val targetValue: Int,
    val safetyLevel: String,
    val status: String,
    val createdAtMillis: Long,
    val startedAtMillis: Long?,
    val completedAtMillis: Long?,
    val progress: Float,
    val confidence: Float?,
    val failureReasons: String,
    val metrics: String
)

@Dao
interface VerificationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(value: VerificationSessionEntity)
    @Update suspend fun update(value: VerificationSessionEntity)
    @Query("SELECT * FROM verification_sessions WHERE id = :id") suspend fun get(id: String): VerificationSessionEntity?
    @Query("SELECT * FROM verification_sessions WHERE id = :id") fun observe(id: String): Flow<VerificationSessionEntity?>
}

class RoomVerificationRepository @Inject constructor(private val dao: VerificationDao) : VerificationRepository {
    override suspend fun create(session: VerificationSession) = dao.insert(session.toEntity())
    override suspend fun get(id: String) = dao.get(id)?.toDomain()
    override fun observe(id: String) = dao.observe(id).map { it?.toDomain() }
    override suspend fun update(session: VerificationSession) = dao.update(session.toEntity())
}

private fun VerificationTarget.serialized() = when (this) {
    is VerificationTarget.Count -> "COUNT" to value
    is VerificationTarget.Duration -> "DURATION" to seconds
    is VerificationTarget.Distance -> "DISTANCE" to meters
    is VerificationTarget.BooleanTarget -> "BOOLEAN" to if (expected) 1 else 0
}
private fun target(type: String, value: Int): VerificationTarget = when (type) {
    "COUNT" -> VerificationTarget.Count(value)
    "DURATION" -> VerificationTarget.Duration(value)
    "DISTANCE" -> VerificationTarget.Distance(value)
    "BOOLEAN" -> VerificationTarget.BooleanTarget(value == 1)
    else -> error("Unknown verification target: $type")
}
private fun String.b64() = Base64.getUrlEncoder().withoutPadding().encodeToString(toByteArray())
private fun String.unb64() = String(Base64.getUrlDecoder().decode(this))
private fun Map<String, String>.pack() = entries.joinToString(",") { "${it.key.b64()}:${it.value.b64()}" }
private fun String.unpackMap() = if (isBlank()) emptyMap() else split(',').associate {
    val (key, value) = it.split(':', limit = 2); key.unb64() to value.unb64()
}
private fun List<String>.packList() = joinToString(",") { it.b64() }
private fun String.unpackList() = if (isBlank()) emptyList() else split(',').map { it.unb64() }

private fun VerificationSession.toEntity(): VerificationSessionEntity {
    val (targetType, targetValue) = target.serialized()
    return VerificationSessionEntity(
        id, questId, type.name, targetType, targetValue, safetyLevel.name, status.name,
        createdAtMillis, startedAtMillis, completedAtMillis, progress, confidence,
        failureReasons.packList(), metrics.pack()
    )
}
private fun VerificationSessionEntity.toDomain() = VerificationSession(
    id, questId, VerificationType.valueOf(type), target(targetType, targetValue),
    SafetyLevel.valueOf(safetyLevel), VerificationSessionStatus.valueOf(status),
    createdAtMillis, startedAtMillis, completedAtMillis, progress, confidence,
    failureReasons.unpackList(), metrics.unpackMap()
)
