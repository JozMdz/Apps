# Apps de lectura

Dos apps Android para escuchar texto mientras se maneja, y un juego para la TV LG para aprender a leer:

| App | Para qué | Carpeta | APK |
| --- | --- | --- | --- |
| **Estudio en audio** | Escuchar documentos PDF o Markdown completos, con índice de apartados y memoria de dónde te quedaste. No necesita permisos especiales. | `lector/` | `lector-release.apk` |
| **Leo y Juego** (TV LG) | App para niños de kínder: leer (desde las letras hasta cuentos), inglés, números, música y minijuegos con PIN; se juega con el control o con el celular. | `tv-lectura/` | `com.jozmdz.leoyjuego_1.6.0_all.ipk` |
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

App para la tele LG para que un niño de kínder (5 años) aprenda jugando: leer, inglés, números,
música y unos minijuegos de premio que papá o mamá autorizan con un PIN. Se juega con el control
remoto (flechas + OK, o el puntero del Magic Remote) o con el celular escaneando un QR. Las voces
(español de México e inglés) y los dibujos vienen dentro de la app.

## Qué trae

Los juegos de preguntas tienen rondas de 5; con 4 o más bien a la primera suben de nivel solos
(3 niveles). Con 3 estrellas o más se gana una estampa para el álbum.

| Sección | Juegos |
| --- | --- |
| **Leer** | **Letras** (ve la letra y busca el dibujo que empieza con ella) · **Escucha** (oye una sílaba y la busca) · **Sílabas** (completa la palabra) · **Palabras** (lee y busca el dibujo, sin ayuda de voz) · **Arma** (forma la palabra con sílabas) · **Mi nombre** (arma su nombre letra por letra; papá o mamá lo escriben la primera vez) · **Rimas** (¿cuál rima con gato?; en el nivel 3 las opciones son palabras escritas) · **Mayúsculas** (la pareja de cada letra: M con m; en el nivel 3 letras que se confunden, b/d/p/q) |
| **Ya leo** 🦉 | Para cuando ya empieza a leer. **Frases** (lee «dos peras y un sapo» o «El oso come cuatro fresas.» y busca el dibujo; los dibujos equivocados cambian solo el número o una cosa, así que hay que leer toda la frase) · **Escribe** (ve el dibujo, oye la palabra y la escribe letra por letra) · **Cuentos** (6 cuentos cortos para leer solo: tocar una palabra la dice, *Escuchar* lee la página resaltando cada palabra, y al final hay una pregunta) |
| **Inglés** | **Words** (oye la palabra en inglés y busca el dibujo: animales, comida, cosas) · **Colors** · **Numbers** (1 al 10). Al acertar oye la palabra en inglés y en español. |
| **Inglés → Repaso** | Lo que ven en la escuela (kínder 3): **My words** (hospital, firefighter, teacher, fireworks, candles, lanterns…) · **He / She** (niño u hombre = *He*, niña o mujer = *She*; luego «___ is a doctor.») · **Places** (*Where are you? We're at the library / playground / beach…*) · **There's a** (*There's a candle* / *There are three lanterns*) · **ow ea ee** (completar c-**ow**, l-**ea**-f, s-**ee**-d y elegir la bien escrita: *leaf* o *leef*) · **Recycle** (¿*plastic*, *glass* o *metal*?). Si se equivoca en He/She o There's a, la voz le vuelve a explicar la regla en español. |
| **Números** | **Cuenta** (¿cuántos hay?) · **Números** (oye el número y lo busca, hasta 20) · **Sumas** (con dibujos y luego solo números) · **Más o menos** · **Restas** (los que se van aparecen tachados; luego solo números) · **¿Qué número falta?** (4, 5, _, 7; hasta 20, de 2 en 2 y hacia atrás) · **Formas** (círculo, cuadrado, triángulo… y en el nivel 3 ¿cuántos lados tiene?) · **¿Qué sigue?** (series AB, AAB, ABC con dibujos) · **¿Cuál se fue?** (memoria: ve 3 a 5 dibujos, uno desaparece) |
| **Música** | **Batería** (6 tambores) · **Teclado** (do-re-mi, con la canción *Estrellita* guiada) · **Guitarra** (6 acordes). Se toca con OK, con los números 1-8 del control o desde el celular. |
| **Juegos** 🔒 | **Salta** (el caballito brinca nopales y piedras, estilo circo) · **Atrapa** (la canasta atrapa fruta) · **Memoria** (parejas). Piden el **PIN** y cuánto tiempo (5, 10, 15 o 30 min); al acabarse el tiempo regresan solos al inicio. |

- **Tema de bloques** (mundo de pixeles: cielo en franjas, nubes cuadradas, suelo de pasto y tierra,
  botones con forma de bloque y música de videojuego). Los bloques son dibujos propios
  (`herramientas/bloques.py`). En *Papás → Tema* se puede volver al tema clásico.
- **Mi mundo**: al terminar cada ronda gana un bloque por estrella (oro y gema solo con 5 estrellas)
  y 3 por cada cuento leído. En *Mi mundo* (botón con el pico, en el inicio) elige un bloque de abajo y
  con OK lo pone en un mundo de 10 × 6; si ya hay uno, lo quita y regresa a su inventario. Desde el
  celular se toca el cuadro directamente. Todo queda guardado en la TV.
- **Si se equivoca**: a la segunda, la respuesta correcta brilla para que aprenda cuál era. Si dos
  rondas seguidas le salen con menos de 2 estrellas, el juego baja un nivel (y sube solo otra vez
  con 4 o más estrellas).
- **Música de fondo** suave (alegre en los menús, tranquila mientras juega a leer) que baja sola
  cuando habla la voz, y sonidos al moverse, elegir, acertar y fallar. Se apaga en *Papás*.
- **Para papás** (también con PIN): minúsculas o MAYÚSCULAS, voz, música de fondo, tema, nivel,
  nombre del niño, cambiar el PIN y borrar el progreso. Si se olvida el PIN: pulsar seguidos los botones **rojo, verde,
  amarillo y azul** del control.
- **Jugar con el celular** (como los juegos de Netflix): en el inicio aparece un QR; al escanearlo,
  el celular muestra **los mismos botones que la pantalla de la TV** (dibujos, sílabas, menús,
  tambores) y basta con tocar el que quieras. Arriba tiene *Atrás* y *Escuchar otra vez*. Usa el servidor gratuito de [PeerJS](https://peerjs.com/)
  solo para que el celular encuentre a la TV; después se hablan directo por el Wi‑Fi. Necesita internet
  y que la página esté publicada en GitHub Pages (paso 1 abajo).
- **Pensado para kínder 3 (SEP)**: empieza con vocales y las sílabas de m, p, s, l; letra script.

## Cómo instalarla — paso a paso

### Paso 1. Publicar la app con GitHub Actions (una sola vez)

El archivo `.github/workflows/tv.yml` hace dos cosas cada vez que cambia algo en `tv-lectura/`:
crea el instalador `.ipk` y publica la app como página web en GitHub Pages.

1. En GitHub, abre el repositorio **JozMdz/Apps** → pestaña **Pull requests** → **New pull request**.
   Elige *base*: la rama principal (`claude/jolly-knuth-qcyc82`) y *compare*:
   `claude/lg-tv-educational-app-um2xig` → **Create pull request** → **Merge pull request**.
2. Ve a **Settings → Pages**. En *Build and deployment → Source* elige **GitHub Actions**.
3. Ve a la pestaña **Actions** → a la izquierda **App para TV LG (Leo y Juego)** → botón
   **Run workflow** (rama principal) → **Run workflow**. Espera la palomita verde (1-2 minutos).
4. Listo: la app queda en **https://jozmdz.github.io/Apps/** y el control del celular en
   `https://jozmdz.github.io/Apps/control.html` (al que lleva el QR).

### Paso 2, opción A. Abrirla en el navegador de la TV (lo más fácil, sin computadora)

1. En la TV abre **Navegador web** (el globo en la barra de apps).
2. Escribe `jozmdz.github.io/Apps` y entra.
3. Agrégala a favoritos (o en el menú del navegador, *Agregar a la página de inicio*) para abrirla
   rápido la próxima vez.
4. Si no se oye la voz al principio, pulsa **OK** una vez (el navegador pide un toque antes de sonar).

### Paso 2, opción B. Instalarla como app con su propio ícono (necesita una computadora)

1. **En la TV**: abre la LG Content Store, busca e instala **Developer Mode**. Ábrela e inicia sesión
   con una cuenta gratuita de [LG Developer](https://webostv.developer.lge.com/) (se crea en la página).
2. Activa **Dev Mode Status** (la TV se reinicia) y luego **Key Server**. Anota la **IP** que aparece.
3. **En la computadora** (misma red Wi‑Fi), con [Node.js](https://nodejs.org) instalado, abre una
   terminal y escribe:
   ```
   npm install -g @webos-tools/cli
   ares-setup-device
   ```
   Elige *add*, nombre `tv`, la IP de la TV, puerto `9922`, usuario `prisoner`, sin contraseña.
4. Conéctate: `ares-novacom --device tv --getkey` y escribe la **frase** (*passphrase*) que muestra
   la app Developer Mode.
5. Descarga el instalador: en GitHub → **Actions** → la última ejecución verde de
   **App para TV LG** → abajo, en **Artifacts**, **leo-y-juego-ipk** (llega en un .zip; descomprímelo).
6. Instala: `ares-install --device tv com.jozmdz.leoyjuego_1.6.0_all.ipk`.
   Aparece **Leo y Juego** en la barra de apps de la TV.
7. El modo desarrollador dura **50 horas**: abre la app *Developer Mode* de vez en cuando y pulsa
   **Extend** (si caduca, la app se borra y hay que repetir el paso 6).

### Paso 3. Primera vez que se usa

1. Entra a **Papás** → te pedirá **crear un PIN** de 4 números (lo escribes dos veces).
2. Ahí mismo, **Nombre** → escribe el nombre del niño (para el juego *Mi nombre*).
3. Para jugar con el celular: en el inicio escanea el **QR** con la cámara del celular.

## Agregar palabras, cuentos o cambiar las voces

Las palabras están en `tv-lectura/herramientas/palabras.txt` (`palabra | sí-la-bas | emoji | inglés | grupo`)
los cuentos en `tv-lectura/herramientas/cuentos.txt` y el repaso de inglés en las listas `PERSONAS`,
`LUGARES`, `COSAS_INGLES`, `SONIDOS`, `RECICLA` y `VOCAB` de `generar.py` (título, una frase por página con sus dibujos y
la pregunta del final; las instrucciones están al principio del archivo). Las frases del juego
*Frases* salen de la lista `NOMBRES_FRASES` de `generar.py`. Después de editar hay que volver a
generar dibujos y voces (solo se crean las voces que faltan):

```
pip install piper-tts                      # y tener ffmpeg
npm pack @twemoji/svg && tar xzf twemoji-svg-*.tgz      # dibujos en ./package
# Voz: es_MX-claude-high (.onnx y .onnx.json) de los modelos de Piper
# Inglés: en_US-amy-medium (.onnx y .onnx.json)
python3 tv-lectura/herramientas/generar.py --twemoji package --voz es_MX-claude-high.onnx --voz-en en_US-amy-medium.onnx
```

Para probarla en la computadora: `cd tv-lectura && python3 -m http.server` y abrir
`http://localhost:8000` (las flechas, Enter y Esc hacen de control remoto).

Créditos: conexión con [PeerJS](https://peerjs.com/) (MIT), QR con [qrcode-generator](https://github.com/kazuhikoarase/qrcode-generator) (MIT), dibujos de [Twemoji](https://github.com/jdecked/twemoji) (CC-BY 4.0), letras
[Andika](https://software.sil.org/andika/) y [Pixelify Sans](https://github.com/eifetx/Pixelify-Sans) (SIL OFL; la de pixeles solo en títulos), voces [Piper](https://github.com/rhasspy/piper) es_MX "claude" y en_US "amy". Los sonidos de los instrumentos, los efectos y la música de fondo se generan en la TV (Web Audio).

**TVs LG viejas**: todo el código es ES5; las rejillas (Mi mundo, batería, guitarra, Memoria, PIN,
álbum) tienen un acomodo de respaldo para navegadores sin CSS grid, y `js/peerjs.min.js` está
recompilado con `esbuild --target=chrome53` porque el original usa sintaxis de 2020 y en esas TVs no
aparecía el QR del celular.

**Sobre el sonido en la TV**: las voces se reproducen con Web Audio (`js/sonido.js`), no cambiando el
archivo de un `<audio>`, porque en la TV eso se comía el principio de cada clip y solo se oían
pedacitos de las sílabas. Los tambores graves llevan golpe y armónicos porque las bocinas de la TV no
dan los sonidos de menos de ~150 Hz.
