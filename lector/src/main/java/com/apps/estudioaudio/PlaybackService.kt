package com.apps.estudioaudio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock

/**
 * Mantiene la lectura con la pantalla apagada o en otra app, muestra el aviso multimedia
 * (pantalla de bloqueo incluida) y recibe los botones del volante, el estéreo o los audífonos.
 */
class PlaybackService : Service(), Player.Listener {

    companion object {
        private const val CHANNEL_ID = "lectura"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_TOGGLE = "com.apps.estudioaudio.TOGGLE"
        private const val ACTION_PREVIOUS = "com.apps.estudioaudio.PREVIOUS"
        private const val ACTION_NEXT = "com.apps.estudioaudio.NEXT"
        private const val ACTION_CLOSE = "com.apps.estudioaudio.CLOSE"
        private const val CUSTOM_CLOSE = "cerrar"
    }

    private lateinit var session: MediaSession
    private var noisyRegistered = false

    /** Se desconectó el carro o los audífonos: no seguir leyendo por la bocina del teléfono. */
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) Player.pause()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        session = MediaSession(this, "EstudioEnAudio").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = Player.play()
                override fun onPause() = Player.pause()
                override fun onStop() = Player.pause()
                override fun onSkipToNext() = Player.nextSection()
                override fun onSkipToPrevious() = Player.previousSection()
                override fun onFastForward() = Player.nextSentence()
                override fun onRewind() = Player.previousSentence()
                override fun onCustomAction(action: String, extras: Bundle?) {
                    if (action == CUSTOM_CLOSE) Player.close()
                }
            })
            setSessionActivity(openPlayerIntent())
            isActive = true
        }
        Player.addListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Obligatorio poco después de startForegroundService, aunque luego no haya nada que leer.
        showForeground()
        when (intent?.action) {
            ACTION_TOGGLE -> Player.toggle()
            ACTION_PREVIOUS -> Player.previousSection()
            ACTION_NEXT -> Player.nextSection()
            ACTION_CLOSE -> Player.close()
        }
        if (Player.doc == null) stopNow() else onPlayerChanged()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Player.removeListener(this)
        setNoisyReceiver(false)
        session.isActive = false
        session.release()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Si se quita la app de recientes estando en pausa, se cierra del todo.
        if (Player.state != Player.State.PLAYING) Player.close()
        super.onTaskRemoved(rootIntent)
    }

    override fun onPlayerChanged() {
        val doc = Player.doc
        if (doc == null) {
            stopNow()
            return
        }
        val playing = Player.state == Player.State.PLAYING
        setNoisyReceiver(playing)
        val section = doc.sections.getOrNull(Player.section)

        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, section?.title ?: doc.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, doc.title)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, getString(R.string.app_name))
                .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, (Player.section + 1).toLong())
                .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, doc.sections.size.toLong())
                .build()
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND or PlaybackState.ACTION_STOP
                )
                .addCustomAction(
                    PlaybackState.CustomAction.Builder(CUSTOM_CLOSE, getString(R.string.close), R.drawable.ic_close).build()
                )
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (playing) Player.rate else 0f,
                    SystemClock.elapsedRealtime(),
                )
                .build()
        )
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            // Sin permiso de avisos: la lectura sigue igual.
        }
    }

    private fun showForeground() {
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // El sistema no permitió el primer plano ahora; se sigue como servicio normal.
        }
    }

    private fun stopNow() {
        setNoisyReceiver(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun setNoisyReceiver(on: Boolean) {
        if (on == noisyRegistered) return
        if (on) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(noisyReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(noisyReceiver, filter)
            }
        } else {
            unregisterReceiver(noisyReceiver)
        }
        noisyRegistered = on
    }

    private fun openPlayerIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(icon: Int, label: Int, action: String, code: Int): Notification.Action {
        val intent = PendingIntent.getForegroundService(
            this, code, Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), getString(label), intent).build()
    }

    private fun buildNotification(): Notification {
        val doc = Player.doc
        val playing = Player.state == Player.State.PLAYING
        val section = doc?.sections?.getOrNull(Player.section)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_headset)
            .setContentTitle(section?.title ?: doc?.title ?: getString(R.string.app_name))
            .setContentText(doc?.title ?: "")
            .setContentIntent(openPlayerIntent())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(playing)
            .setShowWhen(false)
            .addAction(action(R.drawable.ic_previous, R.string.previous_section, ACTION_PREVIOUS, 1))
            .addAction(
                if (playing) action(R.drawable.ic_pause, R.string.pause, ACTION_TOGGLE, 2)
                else action(R.drawable.ic_play, R.string.play, ACTION_TOGGLE, 2)
            )
            .addAction(action(R.drawable.ic_next, R.string.next_section, ACTION_NEXT, 3))
            .addAction(action(R.drawable.ic_close, R.string.close, ACTION_CLOSE, 4))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }
}
