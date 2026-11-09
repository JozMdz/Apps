package com.apps.seleccionarpronunciar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Servicio en primer plano con un aviso fijo. Evita que HyperOS/MIUI detenga la app en segundo plano
 * (lo que hacía que el botón de accesibilidad dejara de responder) y da acceso rápido a leer o detener.
 */
class KeepAliveService : Service() {

    companion object {
        private const val CHANNEL_ID = "activo"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, KeepAliveService::class.java))
            } catch (e: Exception) {
                CrashLog.record(context, e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KeepAliveService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            CrashLog.record(this, e)
            stopSelf()
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.keep_alive_channel), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        fun action(extra: String, code: Int) = PendingIntent.getActivity(
            this, code,
            Intent(this, TileActionActivity::class.java)
                .putExtra(TileActionActivity.EXTRA_ACTION, extra)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_speak)
            .setContentTitle(getString(R.string.keep_alive_title))
            .setContentText(getString(R.string.keep_alive_text))
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, getString(R.string.tile_action_select), action(Prefs.TILE_SELECT, 1)).build())
            .addAction(Notification.Action.Builder(null, getString(R.string.read_all), action(Prefs.TILE_READ_ALL, 2)).build())
            .addAction(Notification.Action.Builder(null, getString(R.string.stop), action(TileActionActivity.ACTION_STOP, 3)).build())
            .build()
    }
}
