package com.apps.seleccionarpronunciar

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Actividad invisible lanzada desde el icono de Ajustes rápidos o el aviso de la barra de notificaciones. */
class TileActionActivity : Activity() {

    companion object {
        const val EXTRA_ACTION = "action"
        const val ACTION_STOP = "stop"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val service = SelectToSpeakService.instance
        val action = intent.getStringExtra(EXTRA_ACTION)
        when {
            action == ACTION_STOP -> Speaker.stop()
            service == null -> startActivity(Intent(this, MainActivity::class.java))
            else -> service.runTileAction(action)
        }
        finish()
    }
}
