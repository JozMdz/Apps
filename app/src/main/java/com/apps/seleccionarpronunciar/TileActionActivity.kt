package com.apps.seleccionarpronunciar

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Actividad invisible lanzada desde el icono de Ajustes rápidos. */
class TileActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val service = SelectToSpeakService.instance
        if (service == null) {
            // Servicio desactivado: abrimos la app para que el usuario lo active.
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            service.runTileAction()
        }
        finish()
    }
}
