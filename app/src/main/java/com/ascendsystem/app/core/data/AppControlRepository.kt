package com.ascendsystem.app.core.data

import com.ascendsystem.app.core.database.AppControlDao
import com.ascendsystem.app.core.database.AppRestrictionEntity
import com.ascendsystem.app.core.database.OverrideLogEntity
import com.ascendsystem.app.core.domain.AppRestriction
import com.ascendsystem.app.core.domain.OverrideRepository
import com.ascendsystem.app.core.domain.OverrideRequest
import com.ascendsystem.app.core.domain.RestrictionRepository
import java.util.UUID
import javax.inject.Inject

class RoomRestrictionRepository @Inject constructor(
    private val dao: AppControlDao
) : RestrictionRepository {
    override suspend fun restrictions() = dao.restrictions().map { it.toDomain() }
    override suspend fun upsert(restriction: AppRestriction) =
        dao.upsertRestriction(restriction.toEntity())
    override suspend fun delete(packageName: String) = dao.deleteRestriction(packageName)
}

class RoomOverrideRepository @Inject constructor(
    private val dao: AppControlDao
) : OverrideRepository {
    override suspend fun activate(request: OverrideRequest, activeQuestId: String?, nowMillis: Long) {
        dao.insertOverride(
            OverrideLogEntity(
                id = UUID.randomUUID().toString(),
                timestampEpochMs = nowMillis,
                reason = request.reason,
                note = request.note,
                durationMinutes = request.durationMinutes.coerceIn(1, 60),
                activeQuestId = activeQuestId
            )
        )
    }

    override suspend fun activeUntilMillis(): Long {
        val latest = dao.latestOverride() ?: return 0L
        return latest.timestampEpochMs + latest.durationMinutes * 60_000L
    }
}

private fun AppRestrictionEntity.toDomain() = AppRestriction(
    packageName, displayName, dailyLimitMinutes, sessionLimitMinutes, isEssential, enabled
)

private fun AppRestriction.toEntity() = AppRestrictionEntity(
    packageName, displayName, "USER_SELECTED", dailyLimitMinutes, sessionLimitMinutes, isEssential, enabled
)
