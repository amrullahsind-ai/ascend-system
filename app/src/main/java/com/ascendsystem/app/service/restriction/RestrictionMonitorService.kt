package com.ascendsystem.app.service.restriction

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ascendsystem.app.MainActivity
import com.ascendsystem.app.core.database.AppControlDao
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*

@AndroidEntryPoint
class RestrictionMonitorService : Service() {
    @Inject lateinit var dao: AppControlDao
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastBlockedPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("ASCEND Strict aktif")
                .setContentText("Memantau aplikasi yang kamu masukkan ke daftar pembatasan.")
                .setOngoing(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this, 0, Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()
        )
        scope.launch { monitor() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun monitor() {
        val usage = getSystemService(UsageStatsManager::class.java)
        while (currentCoroutineContext().isActive) {
            val override = dao.latestOverride()
            val overrideUntil = override?.let { it.timestampEpochMs + it.durationMinutes * 60_000L } ?: 0L
            if (System.currentTimeMillis() >= overrideUntil) {
                val blocked = dao.enabledRestrictions().map { it.packageName }.toSet()
                val foreground = usage.currentForegroundPackage()
                if (foreground != null && foreground in blocked && foreground != packageName) {
                    if (foreground != lastBlockedPackage) {
                        lastBlockedPackage = foreground
                        startActivity(
                            Intent(this, RestrictionBlockingActivity::class.java)
                                .putExtra(RestrictionBlockingActivity.EXTRA_PACKAGE, foreground)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                    }
                } else {
                    lastBlockedPackage = null
                }
            }
            delay(800)
        }
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "ASCEND Strict", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val CHANNEL_ID = "ascend_strict"
        const val NOTIFICATION_ID = 9200
        fun start(context: Context) =
            context.startForegroundService(Intent(context, RestrictionMonitorService::class.java))
        fun stop(context: Context) =
            context.stopService(Intent(context, RestrictionMonitorService::class.java))
    }
}

private fun UsageStatsManager.currentForegroundPackage(): String? {
    val end = System.currentTimeMillis()
    val events = queryEvents(end - 10_000L, end)
    val event = UsageEvents.Event()
    var foreground: String? = null
    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) foreground = event.packageName
    }
    return foreground
}
