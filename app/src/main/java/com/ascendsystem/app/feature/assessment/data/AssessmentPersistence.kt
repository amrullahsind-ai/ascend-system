package com.ascendsystem.app.feature.assessment.data

import androidx.room.*
import com.ascendsystem.app.core.domain.StrictnessMode
import com.ascendsystem.app.feature.assessment.domain.*
import java.io.StringReader
import java.io.StringWriter
import java.util.Base64
import java.util.Properties
import javax.inject.Inject

@Entity(tableName = "assessment_drafts")
data class AssessmentDraftEntity(
    @PrimaryKey val id: Int = 1,
    val payloadJson: String,
    val currentStep: String,
    val updatedAtEpochMs: Long
)
@Entity(tableName = "personal_protocols")
data class PersonalProtocolEntity(
    @PrimaryKey val id: String,
    val payloadJson: String,
    val status: String,
    val protocolVersion: Int,
    val contractVersion: Int?,
    val activatedAtEpochMs: Long?
)
@Entity(tableName = "app_metadata")
data class AppMetadataEntity(
    @PrimaryKey val singletonId: Int = 1,
    val assessmentCompleted: Boolean,
    val assessmentVersion: Int,
    val protocolActivated: Boolean,
    val migratedFromV1: Boolean
)

@Dao
interface AssessmentDao {
    @Query("SELECT * FROM assessment_drafts WHERE id = 1") suspend fun draft(): AssessmentDraftEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveDraft(value: AssessmentDraftEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveProtocol(value: PersonalProtocolEntity)
    @Query("SELECT * FROM app_metadata WHERE singletonId = 1") suspend fun metadata(): AppMetadataEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveMetadata(value: AppMetadataEntity)
}

/** Versioned local payload. Despite the legacy column name, no cloud JSON/API is involved. */
object AssessmentCodec {
    fun encode(value: AssessmentDraft): String = Properties().run {
        setProperty("schema", "1")
        setProperty("name", value.basicProfile.displayName)
        setProperty("age", value.basicProfile.age?.toString().orEmpty())
        setProperty("height", value.basicProfile.heightCm?.toString().orEmpty())
        setProperty("weight", value.basicProfile.weightKg?.toString().orEmpty())
        setProperty("role", value.basicProfile.dailyRole?.name.orEmpty())
        setProperty("gender", value.basicProfile.gender.orEmpty())
        setProperty("limitations", value.basicProfile.physicalLimitations)
        setProperty("goals", value.goals.joinToString(",") { it.name })
        setProperty("wake", value.dailySchedule.wakeMinute.toString())
        setProperty("sleep", value.dailySchedule.sleepMinute.toString())
        setProperty("commitments", value.dailySchedule.commitments)
        setProperty("focusPeriods", value.dailySchedule.focusPeriods)
        setProperty("exercisePeriods", value.dailySchedule.exercisePeriods)
        setProperty("distracting", value.screenTimeProfile.distractingApps.pack())
        setProperty("essential", value.screenTimeProfile.essentialApps.pack())
        setProperty("sleepAllowlist", value.screenTimeProfile.sleepAllowlist.pack())
        setProperty("focusAllowlist", value.screenTimeProfile.focusAllowlist.pack())
        setProperty("dailyScreen", value.screenTimeProfile.approximateDailyMinutes.toString())
        setProperty("lateScroll", value.screenTimeProfile.lateNightScrolling.toString())
        setProperty("shortVideo", value.screenTimeProfile.shortVideoMinutes.toString())
        setProperty("limit", value.screenTimeProfile.entertainmentLimitMinutes.toString())
        setProperty("typicalFocus", value.productivityProfile.typicalFocusMinutes.toString())
        setProperty("difficultyStarting", value.productivityProfile.difficultyStarting.toString())
        setProperty("difficultyFinishing", value.productivityProfile.difficultyFinishing.toString())
        setProperty("procrastination", value.productivityProfile.procrastinationFrequency.toString())
        setProperty("distractionCauses", value.productivityProfile.distractionCauses)
        setProperty("focus", value.productivityProfile.preferredFocusMinutes.toString())
        setProperty("reminder", value.productivityProfile.reminderIntensity.name)
        setProperty("activityLevel", value.physicalProfile.activityLevel.toString())
        setProperty("exerciseDays", value.physicalProfile.exerciseDaysPerWeek.toString())
        setProperty("squats", value.physicalProfile.comfortableSquats.toString())
        setProperty("pushups", value.physicalProfile.comfortablePushUps.toString())
        setProperty("plank", value.physicalProfile.plankSeconds.toString())
        setProperty("canWalk", value.physicalProfile.canWalkOrRun.toString())
        setProperty("injuries", value.physicalProfile.injuriesOrLimitations)
        setProperty("physicalAllowed", value.physicalProfile.physicalCorrectiveQuestsAllowed.toString())
        setProperty("sleepHours", value.sleepProfile.averageSleepHours.toString())
        setProperty("sleepConsistency", value.sleepProfile.consistency.toString())
        setProperty("difficultySleeping", value.sleepProfile.difficultySleeping.toString())
        setProperty("difficultyWaking", value.sleepProfile.difficultyWaking.toString())
        setProperty("lock", value.sleepProfile.desiredLockMinute.toString())
        setProperty("sleepWake", value.sleepProfile.desiredWakeMinute.toString())
        setProperty("nightApps", value.sleepProfile.nightEmergencyApps.pack())
        setProperty("motivation", value.motivationProfile.style.name)
        setProperty("motivators", value.motivationProfile.motivators.pack())
        setProperty("strictness", value.strictnessMode.name)
        setProperty("emergencyContacts", value.emergencySetup.contacts.pack())
        setProperty("emergencyApps", value.emergencySetup.applications.pack())
        setProperty("override", value.emergencySetup.overrideDurationMinutes.toString())
        setProperty("guardian", value.emergencySetup.guardianContact.orEmpty())
        setProperty("step", value.currentStep.name)
        StringWriter().also { store(it, null) }.toString()
    }

    fun decode(payload: String, updatedAt: Long): AssessmentDraft {
        val p = Properties().apply { load(StringReader(payload)) }
        require(p.getProperty("schema") == "1") { "Unsupported assessment schema" }
        return AssessmentDraft(
            basicProfile = BasicProfileDraft(
                displayName = p.getProperty("name", ""),
                age = p.getProperty("age").toIntOrNull(),
                heightCm = p.getProperty("height").toIntOrNull(),
                weightKg = p.getProperty("weight").toIntOrNull(),
                dailyRole = p.getProperty("role").takeIf(String::isNotBlank)?.let(DailyRole::valueOf),
                gender = p.getProperty("gender").takeIf(String::isNotBlank),
                physicalLimitations = p.getProperty("limitations", "")
            ),
            goals = p.names("goals").map(UserGoal::valueOf).toSet(),
            dailySchedule = DailyScheduleDraft(
                p.int("wake", 420), p.int("sleep", 1350),
                p.getProperty("commitments", ""), p.getProperty("focusPeriods", ""), p.getProperty("exercisePeriods", "")
            ),
            screenTimeProfile = ScreenTimeProfileDraft(
                distractingApps = p.unpack("distracting"), essentialApps = p.unpack("essential"),
                sleepAllowlist = p.unpack("sleepAllowlist"), focusAllowlist = p.unpack("focusAllowlist"),
                approximateDailyMinutes = p.int("dailyScreen", 180),
                lateNightScrolling = p.getProperty("lateScroll", "false").toBoolean(),
                shortVideoMinutes = p.int("shortVideo", 30),
                entertainmentLimitMinutes = p.int("limit", 90)
            ),
            productivityProfile = ProductivityProfileDraft(
                typicalFocusMinutes = p.int("typicalFocus", 25),
                difficultyStarting = p.int("difficultyStarting", 3),
                difficultyFinishing = p.int("difficultyFinishing", 3),
                procrastinationFrequency = p.int("procrastination", 3),
                distractionCauses = p.getProperty("distractionCauses", ""),
                preferredFocusMinutes = p.int("focus", 25),
                reminderIntensity = ReminderIntensity.valueOf(p.getProperty("reminder", ReminderIntensity.STANDARD.name))
            ),
            physicalProfile = PhysicalProfileDraft(
                activityLevel = p.int("activityLevel", 1), exerciseDaysPerWeek = p.int("exerciseDays", 0),
                comfortableSquats = p.int("squats", 0), comfortablePushUps = p.int("pushups", 0),
                plankSeconds = p.int("plank", 0), canWalkOrRun = p.getProperty("canWalk", "true").toBoolean(),
                injuriesOrLimitations = p.getProperty("injuries", ""),
                physicalCorrectiveQuestsAllowed = p.getProperty("physicalAllowed", "false").toBoolean()
            ),
            sleepProfile = SleepProfileDraft(
                averageSleepHours = p.getProperty("sleepHours", "7").toDouble(),
                consistency = p.int("sleepConsistency", 3),
                difficultySleeping = p.int("difficultySleeping", 3),
                difficultyWaking = p.int("difficultyWaking", 3),
                desiredLockMinute = p.int("lock", 1350), desiredWakeMinute = p.int("sleepWake", 420),
                nightEmergencyApps = p.unpack("nightApps")
            ),
            motivationProfile = MotivationProfileDraft(
                MotivationStyle.valueOf(p.getProperty("motivation", MotivationStyle.CALM_ANALYTICAL.name)),
                p.unpack("motivators")
            ),
            strictnessMode = StrictnessMode.valueOf(p.getProperty("strictness", StrictnessMode.GUIDED.name)),
            emergencySetup = EmergencySetupDraft(
                contacts = p.unpack("emergencyContacts"),
                applications = p.unpack("emergencyApps"),
                overrideDurationMinutes = p.int("override", 15),
                guardianContact = p.getProperty("guardian").takeIf(String::isNotBlank)
            ),
            currentStep = AssessmentStep.valueOf(p.getProperty("step", AssessmentStep.BASIC_PROFILE.name)),
            updatedAtMillis = updatedAt
        )
    }

    private fun Set<String>.pack() = joinToString(",") { Base64.getUrlEncoder().encodeToString(it.toByteArray()) }
    private fun Properties.unpack(key: String) = getProperty(key, "").split(',').filter(String::isNotBlank)
        .map { String(Base64.getUrlDecoder().decode(it)) }.toSet()
    private fun Properties.names(key: String) = getProperty(key, "").split(',').filter(String::isNotBlank)
    private fun Properties.int(key: String, fallback: Int) = getProperty(key)?.toIntOrNull() ?: fallback
}

class RoomAssessmentDraftRepository @Inject constructor(private val dao: AssessmentDao) : AssessmentDraftRepository {
    override suspend fun load(): AssessmentDraft? = dao.draft()?.let { AssessmentCodec.decode(it.payloadJson, it.updatedAtEpochMs) }
    override suspend fun save(draft: AssessmentDraft) =
        dao.saveDraft(AssessmentDraftEntity(payloadJson = AssessmentCodec.encode(draft), currentStep = draft.currentStep.name, updatedAtEpochMs = draft.updatedAtMillis))
    override suspend fun complete(protocol: PersonalProtocol) {
        dao.saveProtocol(PersonalProtocolEntity(protocol.id, protocol.toString(), protocol.status.name, protocol.protocolVersion, null, null))
        val old = dao.metadata()
        dao.saveMetadata(AppMetadataEntity(assessmentCompleted = true, assessmentVersion = 1, protocolActivated = false, migratedFromV1 = old?.migratedFromV1 ?: false))
    }
    override suspend fun activate(protocol: PersonalProtocol, contractVersion: Int, activatedAtMillis: Long) {
        dao.saveProtocol(PersonalProtocolEntity(protocol.id, protocol.toString(), ProtocolStatus.ACTIVE.name, protocol.protocolVersion, contractVersion, activatedAtMillis))
        val old = dao.metadata()
        dao.saveMetadata(AppMetadataEntity(
            assessmentCompleted = true, assessmentVersion = 1, protocolActivated = true,
            migratedFromV1 = old?.migratedFromV1 ?: false
        ))
    }
    override suspend fun metadata(): AssessmentMetadata {
        val value = dao.metadata() ?: AppMetadataEntity(assessmentCompleted = false, assessmentVersion = 0, protocolActivated = false, migratedFromV1 = false)
        return AssessmentMetadata(value.assessmentCompleted, value.assessmentVersion, value.protocolActivated, value.migratedFromV1)
    }
}
