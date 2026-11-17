# Apps de lectura

Dos apps Android para escuchar texto mientras se maneja, y un juego para la TV LG para aprender a leer:

| App | Para qué | Carpeta | APK |
| --- | --- | --- | --- |
| **Estudio en audio** | Escuchar documentos PDF o Markdown completos, con índice de apartados y memoria de dónde te quedaste. No necesita permisos especiales. | `lector/` | `lector-release.apk` |
| **Leo y Juego** (TV LG) | Juegos para que un niño aprenda a leer en español: letras, sílabas y palabras, con voz y estampas de premio. | `tv-lectura/` | `com.jozmdz.leoyjuego_1.0.0_all.ipk` |
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
- **Puntos intermedios**: dentro de los apartados largos la app añade sola puntos cada pocos
  párrafos (con las primeras palabras del párrafo), para volver a un lugar cercano sin regresar
  hasta el título.
- **Vista de texto tipo letra de canción**: se ve el documento completo con la frase que se está
  leyendo resaltada y centrada; tocar cualquier frase salta a ella. El índice queda en otra pestaña.
- **Repetir para repasar**: el botón *Repetir* repite con un toque el fragmento actual (el punto del
  índice en que se está), con otro el apartado completo y con otro se apaga. Para un tramo a la
  medida: mantener pulsada la frase inicial y luego la final.
- **Recuerda dónde te quedaste** en cada documento; el botón **Continuar** retoma el último.
- **Controles**: apartado anterior/siguiente, frase anterior/siguiente, pausa y velocidad.
  Funcionan también desde la pantalla de bloqueo, la barra de avisos y los botones del volante,
  el estéreo o los audífonos (siguiente/anterior = apartado).
- **Audio como un reproductor**: pausa la música mientras lee, se pausa sola en llamadas e
  indicaciones del GPS y continúa después; se detiene si se desconecta el Bluetooth del carro.
- **Limpieza para escuchar**: quita símbolos de Markdown, direcciones web, bloques de código,
  números de página y encabezados repetidos; une los renglones del PDF en frases completas.
- **Sin permisos especiales**: solo pide (una vez) el de notificaciones para mostrar los controles.

### Cómo escribir un documento para que el índice salga bien

En Markdown: un solo `#` para el título del documento, `##` para cada apartado y `###` para los
subapartados (uno cada 3 a 5 párrafos). Los títulos deben decir de qué trata la parte, porque se leen
en voz alta al entrar. Mejor frases completas que tablas o listas largas.

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

---

# Leo y Juego (TV LG webOS)

Juego para que un niño aprenda a leer en español, hecho para la tele LG y para jugarse con el
control remoto (flechas + OK, o el puntero del Magic Remote). Todo funciona sin internet una vez
instalado: las voces (español de México) y los dibujos vienen dentro de la app.

**Juegos** (cada ronda tiene 5 preguntas; con 4 o más bien a la primera sube de nivel solo):

| Juego | Qué hace | Niveles |
| --- | --- | --- |
| **Letras** | Ve la letra (y la oye) y elige el dibujo que empieza con ella. Al pasar por cada dibujo se oye su nombre. | 1: vocales · 2: + m p s l t d n · 3: todas |
| **Escucha** | Oye una sílaba y elige cómo se escribe. | 1: ma/me/mo · 2: ma/pa/sa · 3: mezcladas |
| **Sílabas** | Completa la palabra del dibujo con la sílaba que falta. | 1: palabras cortas, falta la primera · 2: falta cualquiera · 3: palabras largas |
| **Palabras** | Lee la palabra (sin ayuda de voz) y elige su dibujo. | 1: palabras fáciles · 2: dibujos parecidos · 3: palabras largas |
| **Arma** | Elige las sílabas en orden para formar la palabra del dibujo. | 1: 2 sílabas · 2: 3 sílabas · 3: largas y más sílabas de sobra |

- **Estampas**: cada ronda con 3 estrellas o más regala una estampa para el álbum (*Mis estampas*).
- **Para papás**: letra en minúsculas o MAYÚSCULAS, voz encendida/apagada, cambiar el nivel y borrar el progreso.
- Botón 🔊 (o el botón rojo del control) repite la instrucción. Atrás regresa al inicio; en el inicio cierra la app.
- **Jugar con el celular** (como los juegos de Netflix): en la pantalla de inicio aparece un código QR.
  Al escanearlo, el celular muestra los dibujos, sílabas y menús de la TV como botones grandes para
  tocar. Usa el servidor público de [PeerJS](https://peerjs.com/) solo para que el celular encuentre a
  la TV; luego se comunican directo por el Wi‑Fi. Necesita internet y que la página
  `control.html` esté publicada en GitHub Pages (ver abajo). Sin internet el QR no aparece y se juega
  con el control remoto. Para probar sin internet: `npx peerjs --port 9000` y abrir
  `index.html?senal=127.0.0.1:9000`.
- **Pensado para kínder 3 (5 años, SEP)**: empieza con vocales y con las sílabas de m, p, s, l
  (*ma me mi mo mu*), letra script en minúsculas, y sube de nivel cuando el niño ya domina.

## Cómo ponerla en la TV

**Opción A — Abrirla en el navegador de la TV (lo más fácil).** Cuando este código esté en la rama
principal del repositorio y GitHub Pages esté activado (*Settings → Pages → Source: GitHub Actions*),
el juego queda en `https://jozmdz.github.io/Apps/`. En la TV abre el **Navegador web** (el globo de la
barra de apps), entra a esa dirección y agrégala a favoritos o fíjala en la página de inicio.
La primera vez necesita internet; el progreso se guarda en la TV.

**Opción B — Instalarla como app (ícono propio en la barra de la TV).**
1. En la TV, instala la app **Developer Mode** desde la LG Content Store e inicia sesión con una
   cuenta gratuita de [LG Developer](https://webostv.developer.lge.com/). Activa *Dev Mode Status* y
   *Key Server*; la TV se reinicia y muestra su dirección IP.
2. En una computadora en la misma red: `npm install -g @webos-tools/cli`, luego
   `ares-setup-device` (agrega la TV con su IP, puerto 9922) y `ares-novacom --device tv --getkey`
   (pide la frase que aparece en la app Developer Mode).
3. Descarga el `.ipk` de **Actions → App para TV LG → Artifacts** (o créalo con
   `ares-package tv-lectura -e herramientas`) e instálalo: `ares-install --device tv com.jozmdz.leoyjuego_1.0.0_all.ipk`.

El modo desarrollador dura 50 horas; para que la app no se borre, abre la app *Developer Mode*
de vez en cuando y pulsa **Extend**.

## Agregar palabras o cambiar las voces

Las palabras están en `tv-lectura/herramientas/palabras.txt` (`palabra | sí-la-bas | emoji`).
Después de editarlo hay que volver a generar dibujos y voces:

```
pip install piper-tts                      # y tener ffmpeg
npm pack @twemoji/svg && tar xzf twemoji-svg-*.tgz      # dibujos en ./package
# Voz: es_MX-claude-high (.onnx y .onnx.json) de los modelos de Piper
python3 tv-lectura/herramientas/generar.py --twemoji package --voz es_MX-claude-high.onnx
```

Para probarla en la computadora: `cd tv-lectura && python3 -m http.server` y abrir
`http://localhost:8000` (las flechas, Enter y Esc hacen de control remoto).

Créditos: conexión con [PeerJS](https://peerjs.com/) (MIT), QR con [qrcode-generator](https://github.com/kazuhikoarase/qrcode-generator) (MIT), dibujos de [Twemoji](https://github.com/jdecked/twemoji) (CC-BY 4.0), letra
[Andika](https://software.sil.org/andika/) (SIL OFL), voz [Piper](https://github.com/rhasspy/piper) es_MX "claude".
