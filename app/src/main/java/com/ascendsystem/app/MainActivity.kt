package com.ascendsystem.app

import android.os.Bundle
import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.ascendsystem.app.core.designsystem.*
import com.ascendsystem.app.core.domain.StrictnessMode
import com.ascendsystem.app.feature.assessment.AssessmentViewModel
import com.ascendsystem.app.feature.assessment.StartFlow
import com.ascendsystem.app.feature.assessment.StartupViewModel
import com.ascendsystem.app.feature.assessment.domain.*
import com.ascendsystem.app.feature.dashboard.DashboardViewModel
import com.ascendsystem.app.feature.dashboard.QuestViewModel
import com.ascendsystem.app.feature.dashboard.AppControlViewModel
import com.ascendsystem.app.feature.verification.camera.CameraVerificationScreen
import com.ascendsystem.app.service.restriction.RestrictionMonitorService
import com.ascendsystem.app.service.scheduling.SleepProtocolScheduler
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AscendTheme { AscendApp() } }
    }
}

private enum class Route(val value: String) {
    ONBOARDING("onboarding"), PERMISSIONS("permissions"), DASHBOARD("dashboard"),
    QUESTS("quests"), ALLOWLIST("allowlist"), SLEEP("sleep"), BLOCKING("blocking"),
    OVERRIDE("override"), SETTINGS("settings"), CALIBRATION("calibration"),
    ASSESSMENT("assessment"), PROTOCOL_REVIEW("protocolReview"), CONTRACT("contract"),
    CAMERA_VERIFICATION("cameraVerification"), VERIFICATION_DEBUG("verificationDebug")
}

@Composable
private fun AscendApp(startup: StartupViewModel = hiltViewModel()) {
    val flow by startup.flow.collectAsStateWithLifecycle()
    if (flow == null) {
        AscendBackground {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) { CircularProgressIndicator() }
        }
        return
    }
    val nav = rememberNavController()
    val startRoute = when (flow) {
        StartFlow.INITIALIZATION -> Route.ONBOARDING
        StartFlow.CALIBRATION -> Route.CALIBRATION
        StartFlow.ASSESSMENT -> Route.ASSESSMENT
        StartFlow.PROTOCOL_REVIEW -> Route.PROTOCOL_REVIEW
        StartFlow.DASHBOARD -> Route.DASHBOARD
        null -> Route.ONBOARDING
    }
    NavHost(navController = nav, startDestination = startRoute.value) {
        composable(Route.ONBOARDING.value) { Onboarding { nav.navigate(Route.PERMISSIONS.value) } }
        composable(Route.PERMISSIONS.value) { PermissionEducation { nav.navigate(Route.ASSESSMENT.value) } }
        composable(Route.CALIBRATION.value) { CalibrationRequired { nav.navigate(Route.ASSESSMENT.value) } }
        composable(Route.ASSESSMENT.value) { AssessmentScreen(nav) }
        composable(Route.PROTOCOL_REVIEW.value) { ProtocolReviewScreen(nav) }
        composable(Route.CONTRACT.value) { SystemContractScreen(nav) }
        composable(Route.DASHBOARD.value) { Dashboard(nav) }
        composable(Route.QUESTS.value) { QuestCrudScreen(nav) }
        composable(Route.ALLOWLIST.value) { AppRestrictionScreen(nav) }
        composable(Route.SLEEP.value) { SleepProtocolScreen(nav) }
        composable(Route.BLOCKING.value) { BlockingScreen(nav) }
        composable(Route.OVERRIDE.value) { OverrideScreen(nav) }
        composable(Route.SETTINGS.value) { InfoScreen("DEPLOYMENT MODES", "Consumer: best-effort. Guardian: consent + guardian approval. Dedicated: provisioning and Device Owner required.", nav) }
        composable("${Route.CAMERA_VERIFICATION.value}/{questId}/{targetReps}") {
            CameraVerificationScreen(onClose = { nav.popBackStack() })
        }
        if (BuildConfig.DEBUG) {
            composable(Route.VERIFICATION_DEBUG.value) { CameraVerificationScreen(onClose = { nav.popBackStack() }, debugMode = true) }
        }
    }
}

@Composable
private fun Screen(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("ASCEND SYSTEM // $title", color = Cyan, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun Onboarding(next: () -> Unit) = Screen("INITIALIZATION") {
    var name by remember { mutableStateOf("") }
    HoloPanel(Modifier.fillMaxWidth()) {
        Text("[SYSTEM INITIALIZATION]", color = Cyan)
        Spacer(Modifier.height(10.dp))
        Text("Build discipline through rules you choose. Safety override remains available.")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Call sign") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Button(onClick = next, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("CONTINUE") }
    }
}

@Composable
private fun PermissionEducation(next: () -> Unit) = Screen("PERMISSION MATRIX") {
    listOf(
        "Notifications — quest reminders; fallback: in-app cards",
        "Usage access — optional limit detection; fallback: focus timer",
        "Display over apps — optional warning; fallback: notification"
    ).forEach { HoloPanel(Modifier.fillMaxWidth()) { Text(it) } }
    Text("No permission is requested from this prototype screen. Requests must be contextual.")
    Button(onClick = next, modifier = Modifier.fillMaxWidth()) { Text("I UNDERSTAND") }
}

@Composable
private fun DashboardPrototype(nav: NavHostController) = Screen("DASHBOARD") {
    HoloPanel(Modifier.fillMaxWidth()) {
        Text("LEVEL 04", color = Cyan, style = MaterialTheme.typography.headlineMedium)
        Text("1,240 XP · 7 day streak")
        LinearProgressIndicator(.62f, Modifier.fillMaxWidth().padding(top = 12.dp))
    }
    HoloPanel(Modifier.fillMaxWidth()) {
        Text("[NEW QUEST]", color = Violet)
        Text("25 minute deep-focus protocol")
        Text("Reward: Focus +1 · 30 XP")
    }
    NavButton("QUEST DATABASE") { nav.navigate(Route.QUESTS.value) }
    NavButton("APP ALLOWLIST") { nav.navigate(Route.ALLOWLIST.value) }
    NavButton("SLEEP PROTOCOL") { nav.navigate(Route.SLEEP.value) }
    NavButton("BLOCKING SCREEN DEMO") { nav.navigate(Route.BLOCKING.value) }
    NavButton("MODES & SETTINGS") { nav.navigate(Route.SETTINGS.value) }
    OutlinedButton(onClick = { nav.navigate(Route.OVERRIDE.value) }, modifier = Modifier.fillMaxWidth()) {
        Text("EMERGENCY OVERRIDE", color = Danger)
    }
}

@Composable
private fun Dashboard(nav: NavHostController, viewModel: DashboardViewModel = hiltViewModel()) = SystemPage {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SystemStatusIndicator("System online", true)
    SystemHeader("Command interface", "Dashboard", "Local repository status; no preview metrics are shown at runtime.")
    when {
        state.loading -> CircularProgressIndicator()
        state.error != null -> {
            SystemAlertCard("Data unavailable", state.error.orEmpty(), true)
            SecondarySystemButton("Retry", viewModel::refresh)
        }
        state.quests.isEmpty() -> SystemPanel(Modifier.fillMaxWidth()) {
            Text("NO ACTIVE QUEST", color = AscendColors.Muted)
            Text("Create a quest to begin progression tracking.")
        }
        else -> SystemPanel(Modifier.fillMaxWidth()) {
            Text("QUEST STATUS", color = AscendColors.Cyan)
            Text("${state.quests.size} local quests")
            Text(state.quests.first().title, color = AscendColors.Text)
        }
    }
    ProtocolRuleCard("Progression", "Awaiting persisted XP profile")
    ProtocolRuleCard("Restrictions", "Consumer Strict tersedia melalui Usage Access")
    ProtocolRuleCard("Sleep protocol", "Jadwal otomatis dapat diaktifkan")
    PrimarySystemButton("Quest database") { nav.navigate(Route.QUESTS.value) }
    SecondarySystemButton("App allowlist") { nav.navigate(Route.ALLOWLIST.value) }
    SecondarySystemButton("Sleep protocol") { nav.navigate(Route.SLEEP.value) }
    SecondarySystemButton("Modes and settings") { nav.navigate(Route.SETTINGS.value) }
    if (BuildConfig.DEBUG) SecondarySystemButton("Verification debug") { nav.navigate(Route.VERIFICATION_DEBUG.value) }
    EmergencyOverrideButton { nav.navigate(Route.OVERRIDE.value) }
}

@Composable
private fun NavButton(label: String, action: () -> Unit) =
    Button(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(label) }

@Composable
private fun QuestCrudScreen(nav: NavHostController, viewModel: QuestViewModel = hiltViewModel()) = Screen("QUEST DATABASE") {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var repetitions by remember { mutableIntStateOf(10) }
    var delayMinutes by remember { mutableIntStateOf(5) }
    HoloPanel(Modifier.fillMaxWidth()) {
        if (state.loading) CircularProgressIndicator()
        if (state.quests.isEmpty() && !state.loading) Text("Belum ada quest tersimpan.")
        state.quests.forEach { quest ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(quest.title)
                    Text("${quest.targetValue} repetisi · ${quest.status}", color = AscendColors.Muted)
                }
                TextButton(onClick = {
                    nav.navigate("${Route.CAMERA_VERIFICATION.value}/${quest.id}/${quest.targetValue}")
                }) { Text("MULAI") }
                TextButton(onClick = { viewModel.delete(quest) }) { Text("DELETE") }
            }
        }
    }
    state.error?.let { SystemAlertCard("Quest error", it, true) }
    OutlinedTextField(draft, { draft = it }, label = { Text("Nama quest squat") }, modifier = Modifier.fillMaxWidth())
    Text("Target repetisi: $repetitions")
    Slider(repetitions.toFloat(), { repetitions = it.toInt() }, valueRange = 1f..50f, steps = 48)
    Text("Aktif dalam: $delayMinutes menit")
    Slider(delayMinutes.toFloat(), { delayMinutes = it.toInt() }, valueRange = 1f..60f, steps = 58)
    Button(
        onClick = { viewModel.createSquatQuest(draft, repetitions, delayMinutes); draft = "" },
        enabled = draft.isNotBlank(), modifier = Modifier.fillMaxWidth()
    ) { Text("SIMPAN & JADWALKAN") }
    Text("Quest disimpan di Room dan alarm Android dijadwalkan walaupun aplikasi ditutup.")
    OutlinedButton(onClick = { nav.popBackStack() }) { Text("BACK") }
}

@Composable
private fun BlockingScreen(nav: NavHostController) = Screen("ACCESS RESTRICTED") {
    HoloPanel(Modifier.fillMaxWidth()) {
        Text("[SYSTEM ALERT]", color = Danger)
        Text("Configured entertainment limit reached.")
        Text("Complete the active focus quest to restore access.")
    }
    Button(onClick = { nav.navigate(Route.QUESTS.value) }, modifier = Modifier.fillMaxWidth()) { Text("VIEW QUEST") }
    OutlinedButton(onClick = { nav.navigate(Route.OVERRIDE.value) }, modifier = Modifier.fillMaxWidth()) {
        Text("EMERGENCY OVERRIDE", color = Danger)
    }
    Text("Consumer mode is best-effort and cannot fully lock the device.")
}

@Composable
private fun OverrideScreen(
    nav: NavHostController,
    viewModel: AppControlViewModel = hiltViewModel()
) = Screen("EMERGENCY OVERRIDE") {
    var note by remember { mutableStateOf("") }
    var duration by remember { mutableIntStateOf(5) }
    Text("Override is always available and will be recorded locally.")
    OutlinedTextField(note, { note = it }, label = { Text("Reason / note") }, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(5, 15, 30).forEach { value ->
            FilterChip(selected = duration == value, onClick = { duration = value }, label = { Text("$value min") })
        }
    }
    Button(onClick = {
        viewModel.activateOverride(note, duration) {
            nav.navigate(Route.DASHBOARD.value) { popUpTo(Route.DASHBOARD.value) { inclusive = true } }
        }
    },
        enabled = note.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("ACTIVATE $duration MIN") }
}

@Composable
private fun AppRestrictionScreen(
    nav: NavHostController,
    viewModel: AppControlViewModel = hiltViewModel()
) = Screen("STRICT APP CONTROL") {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var packageName by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var usageGranted by remember { mutableStateOf(context.hasUsageAccess()) }
    var notificationGranted by remember {
        mutableStateOf(
            android.os.Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationGranted = it
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        val listener = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                usageGranted = context.hasUsageAccess()
            }
        }
        (context as? androidx.activity.ComponentActivity)?.lifecycle?.addObserver(listener)
        onDispose { (context as? androidx.activity.ComponentActivity)?.lifecycle?.removeObserver(listener) }
    }

    SystemStatusIndicator(if (usageGranted) "Usage Access aktif" else "Usage Access belum aktif", usageGranted)
    SystemStatusIndicator(if (notificationGranted) "Notifikasi aktif" else "Notifikasi belum aktif", notificationGranted)
    SafetyNoticeCard("Consumer Strict hanya memblokir aplikasi yang kamu pilih. Phone dan aplikasi darurat jangan dimasukkan.")
    if (!usageGranted) {
        PrimarySystemButton("Buka Usage Access") {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }
    if (!notificationGranted && android.os.Build.VERSION.SDK_INT >= 33) {
        SecondarySystemButton("Izinkan notifikasi quest") {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    state.restrictions.forEach { app ->
        SystemPanel(Modifier.fillMaxWidth()) {
            Text(app.displayName, color = AscendColors.Text)
            Text(app.packageName, color = AscendColors.Muted)
            Text(if (app.isEssential) "ESSENTIAL ALLOWLIST" else "RESTRICTED", color = if (app.isEssential) AscendColors.Success else AscendColors.Amber)
            TextButton(onClick = { viewModel.delete(app.packageName) }) { Text("HAPUS") }
        }
    }
    OutlinedTextField(packageName, { packageName = it }, label = { Text("Package aplikasi") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(displayName, { displayName = it }, label = { Text("Nama tampilan") }, modifier = Modifier.fillMaxWidth())
    SecondarySystemButton("Tambah ke daftar pembatasan") {
        viewModel.add(packageName, displayName, false)
        packageName = ""; displayName = ""
    }
    SecondarySystemButton("Tambah ke essential allowlist") {
        viewModel.add(packageName, displayName, true)
        packageName = ""; displayName = ""
    }
    if (state.installedApps.isNotEmpty()) {
        Text("APLIKASI TERPASANG", color = AscendColors.Cyan)
        state.installedApps.forEach { app ->
            SystemPanel(Modifier.fillMaxWidth()) {
                Text(app.displayName, color = AscendColors.Text)
                Text(app.packageName, color = AscendColors.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(AscendSpacing.sm)) {
                    TextButton(onClick = { viewModel.add(app.packageName, app.displayName, false) }) { Text("BATASI") }
                    TextButton(onClick = { viewModel.add(app.packageName, app.displayName, true) }) { Text("ESSENTIAL") }
                }
            }
        }
    }
    state.message?.let { SystemAlertCard("App control", it, true) }
    PrimarySystemButton("Aktifkan Consumer Strict") {
        if (context.hasUsageAccess()) RestrictionMonitorService.start(context)
        else context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }
    SecondarySystemButton("Matikan pemantauan") { RestrictionMonitorService.stop(context) }
    OutlinedButton(onClick = { nav.popBackStack() }) { Text("BACK") }
}

@Composable
private fun InfoScreen(title: String, body: String, nav: NavHostController) = Screen(title) {
    HoloPanel(Modifier.fillMaxWidth()) { Text(body) }
    OutlinedButton(onClick = { nav.popBackStack() }) { Text("BACK") }
}

@Composable
private fun SleepProtocolScreen(nav: NavHostController) = Screen("SLEEP PROTOCOL") {
    val context = androidx.compose.ui.platform.LocalContext.current
    val initial = remember { SleepProtocolScheduler.settings(context) }
    var lockMinute by rememberSaveable { mutableIntStateOf(initial.first) }
    var wakeMinute by rememberSaveable { mutableIntStateOf(initial.second) }
    var enabled by rememberSaveable { mutableStateOf(initial.third) }
    var saved by remember { mutableStateOf(false) }

    SystemStatusIndicator(if (enabled) "Sleep protocol aktif" else "Sleep protocol nonaktif", enabled)
    Text("Waktu pembatasan: ${lockMinute.asTime()}")
    Slider(lockMinute.toFloat(), { lockMinute = it.toInt(); saved = false }, valueRange = 18 * 60f..23 * 60f + 59f)
    Text("Waktu bangun: ${wakeMinute.asTime()}")
    Slider(wakeMinute.toFloat(), { wakeMinute = it.toInt(); saved = false }, valueRange = 4 * 60f..10 * 60f)
    Row(horizontalArrangement = Arrangement.spacedBy(AscendSpacing.sm)) {
        FilterChip(enabled, { enabled = true; saved = false }, label = { Text("AKTIF") })
        FilterChip(!enabled, { enabled = false; saved = false }, label = { Text("NONAKTIF") })
    }
    SafetyNoticeCard("Peringatan muncul 60 menit sebelum waktu tidur. Saat lock aktif, daftar aplikasi pilihan dibatasi; Emergency Override tetap tersedia.")
    PrimarySystemButton(if (saved) "Tersimpan" else "Simpan jadwal") {
        SleepProtocolScheduler.configure(context, lockMinute, wakeMinute, enabled)
        saved = true
    }
    OutlinedButton(onClick = { nav.popBackStack() }) { Text("BACK") }
}

@Composable
private fun CalibrationRequired(next: () -> Unit) = SystemPage {
    SystemHeader("Application update", "System calibration required", "Existing quests, XP, restrictions, and schedules will be preserved.")
    SafetyNoticeCard("Recommendations will be compared with existing settings. Nothing is overwritten automatically.")
    PrimarySystemButton("Begin calibration", action = next)
}

@Composable
private fun AssessmentScreen(nav: NavHostController, viewModel: AssessmentViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SystemPage {
        SystemHeader("System calibration", "Initial assessment", "Progress is saved locally after every change.")
        AssessmentProgressIndicator(state.draft.currentStep.ordinal, AssessmentStep.entries.size)
        when {
            state.loading -> CircularProgressIndicator()
            state.error != null -> {
                SystemAlertCard("Persistence error", state.error.orEmpty(), true)
                SecondarySystemButton("Retry") { viewModel.dispatch(AssessmentAction.Retry) }
            }
            else -> AssessmentStepContent(state.draft, viewModel::dispatch)
        }
        state.stepErrors.forEach { Text(it, color = AscendColors.Critical) }
        if (state.saving) Text("SAVING CALIBRATION…", color = AscendColors.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(AscendSpacing.sm)) {
            OutlinedButton({ viewModel.dispatch(AssessmentAction.Back) }, Modifier.weight(1f), shape = AscendShapes.panel) { Text("BACK") }
            Button({
                if (state.draft.currentStep == AssessmentStep.REVIEW) {
                    viewModel.refreshProtocol(); nav.navigate(Route.PROTOCOL_REVIEW.value)
                } else viewModel.dispatch(AssessmentAction.Next)
            }, Modifier.weight(1f), shape = AscendShapes.panel) { Text(if (state.draft.currentStep == AssessmentStep.REVIEW) "REVIEW PROTOCOL" else "NEXT") }
        }
        EmergencyOverrideButton { nav.navigate(Route.OVERRIDE.value) }
    }
}

@Composable
private fun AssessmentStepContent(draft: AssessmentDraft, dispatch: (AssessmentAction) -> Unit) {
    when (draft.currentStep) {
        AssessmentStep.BASIC_PROFILE -> {
            SystemHeader("A", "Basic profile", "Not a medical diagnosis.")
            OutlinedTextField(draft.basicProfile.displayName, { dispatch(AssessmentAction.SetDisplayName(it)) }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
            SafetyNoticeCard("Physical limitations are used only to reduce or replace unsafe quests.")
        }
        AssessmentStep.GOALS -> {
            SystemHeader("B", "Main goals", "Select multiple objectives.")
            UserGoal.entries.forEach { goal -> AssessmentOptionCard(goal.name.replace('_', ' '), goal in draft.goals) { dispatch(AssessmentAction.ToggleGoal(goal)) } }
        }
        AssessmentStep.DAILY_SCHEDULE -> AssessmentSummaryPanel("Daily schedule", "Wake ${draft.dailySchedule.wakeMinute.asTime()} · Sleep ${draft.dailySchedule.sleepMinute.asTime()}", "Commitments, focus, exercise, meals, commute, and personal routines remain editable in protocol review.")
        AssessmentStep.SCREEN_TIME -> AssessmentSummaryPanel("Screen-time behavior", "${draft.screenTimeProfile.approximateDailyMinutes} min/day · ${draft.screenTimeProfile.entertainmentLimitMinutes} min entertainment limit", "Distracting, essential, sleep, and focus allowlists are kept separate.")
        AssessmentStep.PRODUCTIVITY -> AssessmentSummaryPanel("Productivity profile", "${draft.productivityProfile.preferredFocusMinutes}-minute focus sessions", "Starting, finishing, procrastination, distraction causes, and reminder intensity inform recommendations.")
        AssessmentStep.PHYSICAL -> AssessmentSummaryPanel("Physical profile", if (draft.physicalProfile.physicalCorrectiveQuestsAllowed) "Physical corrective quests allowed" else "Physical corrective quests disabled", "Capabilities and limitations are self-reported and are not a diagnosis.")
        AssessmentStep.SLEEP_RECOVERY -> AssessmentSummaryPanel("Sleep and recovery", "${draft.sleepProfile.averageSleepHours} average hours · lock ${draft.sleepProfile.desiredLockMinute.asTime()}", "Night emergency applications remain available.")
        AssessmentStep.MOTIVATION -> AssessmentSummaryPanel("Motivation style", draft.motivationProfile.style.name.replace('_', ' '), draft.motivationProfile.motivators.joinToString())
        AssessmentStep.STRICTNESS -> {
            SystemHeader("I", "Strictness configuration", "Consumer modes are best-effort. Dedicated mode requires Device Owner provisioning.")
            StrictnessMode.entries.forEach { mode -> AssessmentOptionCard(mode.name, draft.strictnessMode == mode) { dispatch(AssessmentAction.SelectStrictness(mode)) } }
            if (draft.strictnessMode == StrictnessMode.GUARDIAN) {
                OutlinedTextField(
                    draft.emergencySetup.guardianContact.orEmpty(),
                    { dispatch(AssessmentAction.SetGuardianContact(it)) },
                    label = { Text("Guardian contact") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        AssessmentStep.EMERGENCY -> {
            SystemHeader("J", "Emergency configuration", "Emergency access always remains available.")
            listOf(5, 15, 30).forEach { value -> AssessmentOptionCard("$value minute override", draft.emergencySetup.overrideDurationMinutes == value) { dispatch(AssessmentAction.SetEmergencyDuration(value)) } }
            SafetyNoticeCard("Phone and configured emergency applications are never part of generated restriction proposals.")
        }
        AssessmentStep.REVIEW -> AssessmentSummaryPanel("Calibration complete", draft.basicProfile.displayName, "${draft.goals.size} goals · ${draft.strictnessMode.name} mode · ${draft.emergencySetup.overrideDurationMinutes} minute override")
    }
}

@Composable
private fun ProtocolReviewScreen(nav: NavHostController, viewModel: AssessmentViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val protocol by viewModel.protocol.collectAsStateWithLifecycle()
    LaunchedEffect(state.loading) { if (!state.loading) viewModel.refreshProtocol() }
    SystemPage {
        SystemHeader("Proposal only", "Personal protocol review", "No restriction activates before contract acceptance.")
        protocol?.let {
            ProtocolRuleCard("Wake time", it.wakeSchedule.wakeMinute.asTime()) { nav.navigate(Route.ASSESSMENT.value) }
            ProtocolRuleCard("Sleep lock", it.sleepProtocol.lockMinute.asTime()) { nav.navigate(Route.ASSESSMENT.value) }
            ProtocolRuleCard("Focus block", "${it.focusBlocks.firstOrNull()?.durationMinutes ?: 0} minutes") { nav.navigate(Route.ASSESSMENT.value) }
            ProtocolRuleCard("Blocked applications", "${it.appRestrictions.size} proposed") { nav.navigate(Route.ASSESSMENT.value) }
            ProtocolRuleCard("Corrective quests", if (it.correctiveQuestPolicy.physicalAllowed) "Physical alternatives allowed" else "Non-physical only") { nav.navigate(Route.ASSESSMENT.value) }
            ProtocolRuleCard("Emergency override", "${it.emergencyPolicy.overrideMinutes} minutes") { nav.navigate(Route.ASSESSMENT.value) }
            ProtocolRuleCard("Strictness", it.strictnessMode.name) { nav.navigate(Route.ASSESSMENT.value) }
        } ?: CircularProgressIndicator()
        SafetyNoticeCard("Existing rules remain authoritative. Conflicts require an explicit choice.")
        PrimarySystemButton("Continue to contract") { nav.navigate(Route.CONTRACT.value) }
    }
}

@Composable
private fun SystemContractScreen(nav: NavHostController, viewModel: AssessmentViewModel = hiltViewModel()) = SystemPage {
    SystemHeader("Contract v1", "System contract", "Hold for three seconds to accept the reviewed proposal.")
    SystemPanel {
        Text("I understand that consumer restrictions are best-effort, permissions remain revocable, and Emergency Override remains available.", color = AscendColors.Text)
    }
    HoldToActivateButton(onActivated = {
        viewModel.approveProposal { result ->
            if (result.isSuccess) nav.navigate(Route.DASHBOARD.value) { popUpTo(Route.ONBOARDING.value) { inclusive = true } }
        }
    })
    EmergencyOverrideButton { nav.navigate(Route.OVERRIDE.value) }
}

@Composable
private fun SystemPage(content: @Composable ColumnScope.() -> Unit) {
    AscendBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AscendSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AscendSpacing.md),
            content = content
        )
    }
}

@Composable
private fun AssessmentSummaryPanel(title: String, value: String, note: String) = SystemPanel(Modifier.fillMaxWidth()) {
    Text(title.uppercase(), color = AscendColors.Cyan, style = MaterialTheme.typography.labelMedium)
    Text(value, color = AscendColors.Text, style = MaterialTheme.typography.titleMedium)
    Text(note, color = AscendColors.Muted)
}

@Composable
private fun SystemAlertCard(title: String, body: String, critical: Boolean) = SystemPanel(accent = if (critical) AscendColors.Critical else AscendColors.Amber) {
    Text(title.uppercase(), color = if (critical) AscendColors.Critical else AscendColors.Amber)
    Text(body)
}

private fun Int.asTime() = "%02d:%02d".format(this / 60, this % 60)

private fun Context.hasUsageAccess(): Boolean {
    val appOps = getSystemService(AppOpsManager::class.java)
    return appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        packageName
    ) == AppOpsManager.MODE_ALLOWED
}
