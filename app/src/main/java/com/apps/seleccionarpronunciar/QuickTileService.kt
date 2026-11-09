package com.apps.seleccionarpronunciar

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Icono del panel de Ajustes rápidos / Centro de control. */
class QuickTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val enabled = SelectToSpeakService.instance != null
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                if (!enabled) R.string.tile_subtitle_off
                else when (Prefs.tileAction(this)) {
                    Prefs.TILE_READ_ALL -> R.string.tile_action_read_all
                    else -> R.string.tile_action_select
                }
            )
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { launch() } else launch()
    }

    /** Cierra el panel y abre una actividad invisible que pide al servicio la acción elegida. */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launch() {
        val intent = Intent(this, TileActionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
