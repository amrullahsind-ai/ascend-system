package com.ascendsystem.app.service.scheduling

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ascendsystem.app.MainActivity
import com.ascendsystem.app.core.domain.QuestRepository
import com.ascendsystem.app.core.domain.QuestStatus
import com.ascendsystem.app.service.restriction.RestrictionMonitorService
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SleepProtocolScheduler {
    private const val PREFS = "sleep_protocol"
    private const val KEY_LOCK = "lock_minute"
    private const val KEY_WAKE = "wake_minute"
    private const val KEY_ENABLED = "enabled"

    fun configure(context: Context, lockMinute: Int, wakeMinute: Int, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_LOCK, lockMinute.coerceIn(0, 1439))
            .putInt(KEY_WAKE, wakeMinute.coerceIn(0, 1439))
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
        cancel(context)
        if (enabled) {
            schedule(context, ACTION_WARNING, (lockMinute - 60 + 1440) % 1440)
            schedule(context, ACTION_LOCK, lockMinute)
            schedule(context, ACTION_WAKE, wakeMinute)
        }
    }

    fun settings(context: Context): Triple<Int, Int, Boolean> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Triple(
            prefs.getInt(KEY_LOCK, 22 * 60 + 30),
            prefs.getInt(KEY_WAKE, 5 * 60),
            prefs.getBoolean(KEY_ENABLED, false)
        )
    }

    fun restore(context: Context) {
        val (lock, wake, enabled) = settings(context)
        if (enabled) configure(context, lock, wake, true)
    }

    private fun schedule(context: Context, action: String, minuteOfDay: Int) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextOccurrence(minuteOfDay),
            pendingIntent(context, action)
        )
    }

    private fun cancel(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        listOf(ACTION_WARNING, ACTION_LOCK, ACTION_WAKE).forEach {
            alarm.cancel(pendingIntent(context, it))
        }
    }

    private fun pendingIntent(context: Context, action: String) = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, SleepProtocolReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun nextOccurrence(minuteOfDay: Int): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            set(Calendar.MINUTE, minuteOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis
    }

    const val ACTION_WARNING = "com.ascendsystem.app.SLEEP_WARNING"
    const val ACTION_LOCK = "com.ascendsystem.app.SLEEP_LOCK"
    const val ACTION_WAKE = "com.ascendsystem.app.SLEEP_WAKE"
}

class SleepProtocolReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            SleepProtocolScheduler.ACTION_WARNING -> notify(context, "Sleep protocol dalam 60 menit", "Selesaikan aktivitas penting dan mulai bersiap tidur.")
            SleepProtocolScheduler.ACTION_LOCK -> {
                notify(context, "Sleep protocol aktif", "Aplikasi dalam daftar pembatasan kini diblokir sampai waktu bangun.")
                RestrictionMonitorService.start(context)
            }
            SleepProtocolScheduler.ACTION_WAKE -> {
                RestrictionMonitorService.stop(context)
                notify(context, "Sleep protocol selesai", "Selamat pagi. Pembatasan malam telah dilepas.")
            }
        }
        SleepProtocolScheduler.restore(context)
    }

    private fun notify(context: Context, title: String, message: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "ASCEND Sleep", NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(
            context, 9300, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            title.hashCode(),
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
        )
    }

    companion object { const val CHANNEL = "ascend_sleep" }
}

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var quests: QuestRepository
    @Inject lateinit var scheduler: QuestScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        SleepProtocolScheduler.restore(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                quests.quests()
                    .filter { it.status == QuestStatus.SCHEDULED && (it.scheduledAtMillis ?: 0L) > System.currentTimeMillis() }
                    .forEach { scheduler.schedule(it) }
            }
            pending.finish()
        }
    }
}
