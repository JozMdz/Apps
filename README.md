# Seleccionar para pronunciar

App Android que funciona como **"Seleccionar para pronunciar"** (Select to Speak): toca un elemento o
arrastra un recuadro sobre cualquier pantalla y la app lo lee en voz alta con el motor de texto a voz del
teléfono.

## Funciones

- **Servicio de accesibilidad** con botón flotante (se puede arrastrar) y compatible con el botón /
  atajo de accesibilidad del sistema.
- **Modo selección**: toca un elemento para leerlo, arrastra un recuadro para leer todo lo que hay
  dentro, o pulsa **Leer todo** para leer la pantalla completa.
- **Resaltado** del elemento que se está leyendo.
- **Barra de control** mientras lee: frase anterior, pausa/reanudar, frase siguiente, velocidad y detener.
- **"Pronunciar" en el menú de texto seleccionado** de cualquier app y como destino de **Compartir**.
- Ajustes de velocidad, tono, resaltado y botón flotante; campo de prueba.
- Sin dependencias externas, sin internet y sin anuncios. Android 8.0 (API 26) o superior.

## Instalar

Cada push compila el APK en GitHub Actions (pestaña **Actions → Compilar APK → Artifacts**).
Descarga `app-release.apk`, ábrelo en el teléfono y permite instalar desde esa fuente.

Después:

1. Abre la app y pulsa **Abrir ajustes de accesibilidad**.
2. Entra en **Seleccionar para pronunciar** y actívalo.
3. Aparecerá el botón flotante azul con el altavoz. Tócalo y selecciona lo que quieras oír.

> En Android 13+ el sistema puede bloquear los ajustes restringidos de apps instaladas desde fuera de
> Play Store. Si el interruptor sale gris: Ajustes → Aplicaciones → Seleccionar para pronunciar →
> menú ⋮ → **Permitir ajustes restringidos**, y vuelve a intentarlo.

## Compilar en local

Requiere JDK 17 y el SDK de Android (o Android Studio):

```bash
./gradlew assembleRelease
```

El APK queda en `app/build/outputs/apk/release/app-release.apk` (firmado con la clave de depuración).

## Estructura

| Archivo | Qué hace |
| --- | --- |
| `SelectToSpeakService.kt` | Servicio de accesibilidad: botón flotante, modo selección, barra de control y resaltado. |
| `ScreenTextReader.kt` | Recorre el árbol de accesibilidad y extrae el texto visible del punto o recuadro elegido. |
| `Speaker.kt` | Envoltorio de `TextToSpeech`: divide en frases, pausa/reanuda, salta y avisa de cambios. |
| `OverlayViews.kt` | Vistas de selección (toque / arrastre) y de resaltado. |
| `SpeakTextActivity.kt` | Acción "Pronunciar" del menú de texto y destino de Compartir. |
| `MainActivity.kt` | Pantalla de ajustes e instrucciones. |

## Limitaciones

Lee el texto que las apps exponen a los servicios de accesibilidad. No hace OCR, así que el texto
dentro de imágenes o de apps que dibujan su propio contenido (algunos juegos, PDF renderizados como
imagen) no se puede leer.
