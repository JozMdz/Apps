package com.apps.seleccionarpronunciar

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Icono del panel para mostrar u ocultar el botón flotante (activar / "cerrar" la app). */
class ButtonTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val show = !Prefs.floatingButton(this)
        Prefs.setFloatingButton(this, show)
        if (!show) Speaker.stop()
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val serviceOn = SelectToSpeakService.instance != null
        val visible = Prefs.floatingButton(this)
        tile.state = when {
            !serviceOn -> Tile.STATE_UNAVAILABLE
            visible -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = getString(R.string.button_tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                when {
                    !serviceOn -> R.string.tile_subtitle_off
                    visible -> R.string.button_tile_visible
                    else -> R.string.button_tile_hidden
                }
            )
        }
        tile.updateTile()
    }
}
