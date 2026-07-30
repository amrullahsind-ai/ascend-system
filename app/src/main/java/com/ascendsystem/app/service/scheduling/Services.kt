package com.ascendsystem.app.service.scheduling

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ascendsystem.app.MainActivity
import com.ascendsystem.app.core.domain.Quest
import com.ascendsystem.app.core.domain.QuestRepository
import com.ascendsystem.app.core.domain.QuestStatus
import com.ascendsystem.app.service.restriction.RestrictionMonitorService
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

interface QuestScheduler {
    suspend fun schedule(quest: Quest): Result<Unit>
    suspend fun cancel(questId: String): Result<Unit>
}

interface NotificationGateway {
    suspend fun showQuestReminder(quest: Quest): Result<Unit>
    suspend fun showSleepWarning(message: String): Result<Unit>
}

class AndroidQuestScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : QuestScheduler {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    override suspend fun schedule(quest: Quest): Result<Unit> = runCatching {
        val triggerAt = requireNotNull(quest.scheduledAtMillis) { "Quest tidak memiliki jadwal" }
        require(triggerAt > System.currentTimeMillis()) { "Jadwal quest harus di masa depan" }
        alarms.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            quest.pendingIntent(context)
        )
    }

    override suspend fun cancel(questId: String): Result<Unit> = runCatching {
        alarms.cancel(pendingIntent(context, questId, null, null))
    }
}

class AndroidNotificationGateway @Inject constructor(
    @ApplicationContext private val context: Context
) : NotificationGateway {
    override suspend fun showQuestReminder(quest: Quest) = runCatching {
        show(
            id = quest.id.hashCode(),
            title = "Quest aktif: ${quest.title}",
            message = quest.description.ifBlank { "Selesaikan verifikasi untuk membuka akses kembali." }
        )
    }

    override suspend fun showSleepWarning(message: String) = runCatching {
        show(SLEEP_NOTIFICATION_ID, "Sleep protocol", message)
    }

    private fun show(id: Int, title: String, message: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "ASCEND quests", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            id,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
        )
    }

    companion object {
        const val CHANNEL_ID = "ascend_quests"
        const val SLEEP_NOTIFICATION_ID = 9100
    }
}

@AndroidEntryPoint
class QuestAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var quests: QuestRepository

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Scheduled quest"
        val description = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()
        RestrictionMonitorService.start(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                quests.quests().firstOrNull { it.id == id }?.let {
                    quests.upsert(it.copy(status = QuestStatus.ACTIVE))
                }
            }
            pending.finish()
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(AndroidNotificationGateway.CHANNEL_ID, "ASCEND quests", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(
            context, id.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            id.hashCode(),
            NotificationCompat.Builder(context, AndroidNotificationGateway.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Quest aktif: $title")
                .setContentText(description)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
        )
    }
}

private const val EXTRA_ID = "quest_id"
private const val EXTRA_TITLE = "quest_title"
private const val EXTRA_DESCRIPTION = "quest_description"

private fun Quest.pendingIntent(context: Context) =
    pendingIntent(context, id, title, description)

private fun pendingIntent(context: Context, id: String, title: String?, description: String?) =
    PendingIntent.getBroadcast(
        context,
        id.hashCode(),
        Intent(context, QuestAlarmReceiver::class.java).apply {
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_DESCRIPTION, description)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
