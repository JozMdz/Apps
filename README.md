# Apps de lectura en voz alta

Dos apps Android independientes, pensadas para escuchar texto mientras se maneja:

| App | Para qué | Carpeta | APK |
| --- | --- | --- | --- |
| **Estudio en audio** | Escuchar documentos PDF o Markdown completos, con índice de apartados y memoria de dónde te quedaste. No necesita permisos especiales. | `lector/` | `lector-release.apk` |
| **Seleccionar para pronunciar** | Leer en voz alta lo que hay en la pantalla de cualquier app (servicio de accesibilidad). | `app/` | `app-release.apk` |

Cada push compila los dos APK en GitHub Actions (pestaña **Actions → Compilar APK → Artifacts**).

---

# Estudio en audio

Convierte un PDF o un archivo Markdown en un "audiolibro" navegable, usando la voz del teléfono
(sin internet).

- **Agregar documentos**: botón *Agregar PDF o Markdown*, o desde otra app (WhatsApp, Claude, Archivos)
  con **Compartir** / **Abrir con** → Estudio en audio. También acepta texto compartido.
- **Índice automático**: sale de los títulos del documento (`#` en Markdown; en PDF, de los marcadores
  o del tamaño/negrita de la letra). Si no hay títulos, se divide en "Parte 1, 2, 3…".
  Tocar un apartado empieza a leerlo.
- **Recuerda dónde te quedaste** en cada documento; el botón **Continuar** retoma el último.
- **Controles**: apartado anterior/siguiente, frase anterior/siguiente, pausa y velocidad.
  Funcionan también desde la pantalla de bloqueo, la barra de avisos y los botones del volante,
  el estéreo o los audífonos (siguiente/anterior = apartado).
- **Audio como un reproductor**: pausa la música mientras lee, se pausa sola en llamadas e
  indicaciones del GPS y continúa después; se detiene si se desconecta el Bluetooth del carro.
- **Limpieza para escuchar**: quita símbolos de Markdown, direcciones web, bloques de código,
  números de página y encabezados repetidos; une los renglones del PDF en frases completas.
- **Sin permisos especiales**: solo pide (una vez) el de notificaciones para mostrar los controles.

Limitaciones: los PDF escaneados (imagen sin texto) no se pueden leer; en PDF con columnas o tablas
el orden puede salir mal. Markdown es lo más confiable.

Pruebas: `./gradlew :lector:testDebugUnitTest` importa un PDF y un Markdown de ejemplo y recorre la
app completa (biblioteca, reproductor, índice) en un Android simulado.

---

# Seleccionar para pronunciar

App Android que funciona como **"Seleccionar para pronunciar"** (Select to Speak): toca un elemento o
arrastra un recuadro sobre cualquier pantalla y la app lo lee en voz alta con el motor de texto a voz del
teléfono.

## Funciones

- **Servicio de accesibilidad** con botón flotante (se puede arrastrar) y compatible con el botón /
  atajo de accesibilidad del sistema.
- **Modo selección**, pensado para usar con una mano:
  - Tocar un texto lee **solo esa frase**, **desde esa frase hasta el final** o **el mensaje completo**
    (configurable).
  - Deslizar el dedo selecciona **renglones completos** (o un recuadro, si se prefiere) y lee solo las
    frases que caen dentro.
  - **Leer todo** lee la pantalla completa.
- **Modo manejo**: el botón se muestra y se oculta solo al conectar/desconectar el Bluetooth del carro.
  También se oculta manteniéndolo pulsado o con el icono **Botón lectura** del panel.
- **Música**: mientras lee, baja el volumen de Spotify (o lo pausa) y usa el canal de audio de
  navegación, como el GPS, para oírse por el carro.
- **Texto en imágenes y PDF (OCR)**: si no hay texto accesible (visores de PDF, fotos…) hace una
  captura y reconoce el texto en el propio teléfono, sin internet (Android 11+). Configurable:
  automático, siempre o nunca.
- **Resaltado** del elemento que se está leyendo.
- **Barra de control** mientras lee: frase anterior, pausa/reanudar, frase siguiente, velocidad y detener.
- **Icono «Pronunciar» en Ajustes rápidos / Centro de control**: configurable para abrir el modo
  selección o leer toda la pantalla; si ya está leyendo, lo detiene. Mantenerlo pulsado abre los ajustes.
- **"Pronunciar" en el menú de texto seleccionado** de cualquier app y como destino de **Compartir**.
- Ajustes de velocidad, tono, resaltado y botón flotante; campo de prueba.
- Sin dependencias externas, sin internet y sin anuncios. Android 8.0 (API 26) o superior.

## Instalar

Descarga `app-release.apk` de los Artifacts de GitHub Actions, ábrelo en el teléfono y permite instalar desde esa fuente.

### Si el teléfono bloquea la instalación ("datos sensibles")

Google Play Protect bloquea apps de fuera de Play Store que usan un servicio de accesibilidad.
Para instalarla:

1. Abre **Play Store** → tu foto de perfil → **Play Protect** → ⚙️ (arriba a la derecha).
2. Desactiva **Analizar apps con Play Protect**.
3. Instala el APK.
4. Vuelve a activar Play Protect.

En Xiaomi/HyperOS, si además aparece el aviso de MIUI/HyperOS, marca la casilla de que conoces los
riesgos y pulsa **Instalar de todas formas** cuando termine la cuenta atrás.

Después:

1. Abre la app y pulsa **Abrir ajustes de accesibilidad**.
2. Entra en **Seleccionar para pronunciar** y actívalo.
3. Aparecerá el botón flotante azul con el altavoz. Tócalo y selecciona lo que quieras oír.
4. (Opcional) Añade el icono **Pronunciar** al panel de Ajustes rápidos (en Xiaomi: Centro de control
   → **Editar**) y, si quieres, oculta el botón flotante desde los ajustes de la app.

> En Android 13+ el sistema puede bloquear los ajustes restringidos de apps instaladas desde fuera de
> Play Store. Si el interruptor sale gris: Ajustes → Aplicaciones → Seleccionar para pronunciar →
> menú ⋮ → **Permitir ajustes restringidos**, y vuelve a intentarlo.

## Compilar en local

Requiere JDK 17 y el SDK de Android (o Android Studio):

```bash
./gradlew :app:assembleRelease :lector:assembleRelease
```

Los APK quedan en `app/build/outputs/apk/release/` y `lector/build/outputs/apk/release/`
(firmados con la clave de depuración).

## Estructura

| Archivo | Qué hace |
| --- | --- |
| `SelectToSpeakService.kt` | Servicio de accesibilidad: botón flotante, modo selección, barra de control y resaltado. |
| `ScreenTextReader.kt` | Recorre el árbol de accesibilidad y extrae el texto visible del punto o recuadro elegido. |
| `OcrReader.kt` | Reconocimiento de texto (ML Kit, en el dispositivo) sobre una captura de pantalla. |
| `Speaker.kt` | Envoltorio de `TextToSpeech`: divide en frases, pausa/reanuda, salta y avisa de cambios. |
| `OverlayViews.kt` | Vistas de selección (toque / arrastre) y de resaltado. |
| `QuickTileService.kt` / `TileActionActivity.kt` | Icono «Pronunciar» del panel de Ajustes rápidos. |
| `ButtonTileService.kt` | Icono «Botón lectura» del panel: muestra u oculta el botón flotante. |
| `TextSplit.kt` | División en frases y elección de la frase tocada. |
| `SpeakTextActivity.kt` | Acción "Pronunciar" del menú de texto y destino de Compartir. |
| `MainActivity.kt` | Pantalla de ajustes e instrucciones. |

## Limitaciones

- El OCR necesita Android 11 o superior y no funciona en pantallas protegidas contra capturas
  (apps de bancos, contenido con DRM).
- Si el botón de accesibilidad del sistema está asignado a esta app, el botón flotante propio se oculta
  para no duplicarlo.
