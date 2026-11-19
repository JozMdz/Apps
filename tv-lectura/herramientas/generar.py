#!/usr/bin/env python3
"""Genera los datos, los dibujos y las voces de "Leo y Juego".

Uso:
    python3 herramientas/generar.py --twemoji RUTA_SVG --voz MODELO.onnx [--rehacer]

- RUTA_SVG: carpeta con los SVG de Twemoji (npm pack @twemoji/svg).
- MODELO.onnx: voz de Piper en español de México (es_MX-claude-high.onnx); con --voz-en
  se da otra en inglés (en_US-amy-medium.onnx) para el módulo de inglés,
  con su .onnx.json al lado. Requiere `pip install piper-tts` y ffmpeg.

Lee herramientas/palabras.txt y herramientas/cuentos.txt, y escribe js/datos.js, img/*.svg
y audio/*.mp3.
Los audios que ya existen no se vuelven a generar (salvo con --rehacer).
"""
import argparse
import json
import os
import random
import re
import shutil
import subprocess
import sys
import tempfile
import unicodedata

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

NOMBRES_LETRAS = {
    "a": "a", "b": "be", "c": "ce", "d": "de", "e": "e", "f": "efe", "g": "ge",
    "h": "hache", "i": "i", "j": "jota", "k": "ka", "l": "ele", "m": "eme",
    "n": "ene", "ñ": "eñe", "o": "o", "p": "pe", "q": "cu", "r": "erre",
    "s": "ese", "t": "te", "u": "u", "v": "ve", "w": "doble u", "x": "equis",
    "y": "ye", "z": "zeta",
}

NUMEROS = ["cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
           "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete",
           "dieciocho", "diecinueve", "veinte"]
NUMBERS = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten"]
# clave: (español, inglés, color en pantalla)
COLORES = [("red", "rojo", "#e53935"), ("blue", "azul", "#1e88e5"), ("yellow", "amarillo", "#fdd835"),
           ("green", "verde", "#43a047"), ("orange", "naranja", "#fb8c00"), ("pink", "rosa", "#f06292"),
           ("purple", "morado", "#8e24aa"), ("black", "negro", "#212121"), ("white", "blanco", "#ffffff"),
           ("brown", "café", "#795548")]
FRASES_EN = {"bien1": "Very good!", "bien2": "Great job!", "bien3": "Excellent!", "mal": "Try again."}

ICONOS = {"musica": "🎵", "bateria": "🥁", "teclado": "🎹", "guitarra": "🎸", "control": "🎮",
          "candado": "🔒", "corre": "🐎", "canasta": "🧺", "corazon": "❤️", "roca": "🪨",
          "paleta_colores": "🎨", "carta": "❓", "reloj_arena": "⏳",
          "estrella": "⭐", "altavoz": "🔊", "libro": "📖", "rompecabezas": "🧩",
          "engrane": "⚙️", "casa": "🏠", "buho": "🦉", "libros": "📚"}

# Dibujos que no son palabras del juego (personajes y cosas de los cuentos).
DIBUJOS_EXTRA = {"nino": "👦", "mama": "👩", "mar": "🌊"}

FRASES = {
    "hola": "¡Hola! Vamos a jugar a leer. Elige un juego.",
    "bien1": "¡Muy bien!",
    "bien2": "¡Excelente!",
    "bien3": "¡Bravo!",
    "bien4": "¡Lo lograste!",
    "bien5": "¡Súper!",
    "mal1": "Casi. Intenta otra vez.",
    "mal2": "Mmm, busca otra.",
    "silabas": "¿Qué sílaba falta?",
    "palabras": "Lee la palabra. ¿Cuál es el dibujo?",
    "arma": "Arma la palabra.",
    "escucha": "Escucha y busca la sílaba.",
    "fin": "¡Terminaste!",
    "estampa": "¡Ganaste una estampa nueva!",
    "nivel": "¡Subiste de nivel!",
    "otra": "¡Sigue practicando!",
    "album": "Estas son tus estampas.",
    "ingles": "Escucha en inglés y busca.",
    "cuantos": "¿Cuántos hay?",
    "dondenum": "¿Dónde está el número",
    "cuantoes": "¿Cuánto es?",
    "mas": "más",
    "dondemas": "¿Dónde hay más?",
    "dondemenos": "¿Dónde hay menos?",
    "mayor": "¿Cuál número es más grande?",
    "nombre": "Arma tu nombre.",
    "tunombre": "¡Ese es tu nombre!",
    "tiempo": "Se acabó el tiempo de juegos. ¡Vamos a aprender!",
    "ganaste": "¡Ganaste!",
    "otravez": "¡Casi! Vamos otra vez.",
    "tocar": "¡A tocar!",
    "frases": "Lee la frase. ¿Cuál es el dibujo?",
    "escribe": "Escribe la palabra.",
    "cuentos": "Lee el cuento. Si no sabes una palabra, tócala y la escuchas.",
    "leido": "¡Leíste todo el cuento!",
}

# Palabras para el juego «Frases»: palabra -> (género, plural, tipo)
# tipo: a = animal, c = comida, o = objeto. Deben estar en palabras.txt (por el dibujo).
NOMBRES_FRASES = {
    "gato": ("m", "gatos", "a"), "pato": ("m", "patos", "a"), "sapo": ("m", "sapos", "a"),
    "mono": ("m", "monos", "a"), "oso": ("m", "osos", "a"), "perro": ("m", "perros", "a"),
    "loro": ("m", "loros", "a"), "conejo": ("m", "conejos", "a"), "pez": ("m", "peces", "a"),
    "vaca": ("f", "vacas", "a"), "foca": ("f", "focas", "a"), "abeja": ("f", "abejas", "a"),
    "pera": ("f", "peras", "c"), "uva": ("f", "uvas", "c"), "fresa": ("f", "fresas", "c"),
    "dona": ("f", "donas", "c"), "manzana": ("f", "manzanas", "c"), "paleta": ("f", "paletas", "c"),
    "taco": ("m", "tacos", "c"), "pan": ("m", "panes", "c"), "coco": ("m", "cocos", "c"),
    "helado": ("m", "helados", "c"), "elote": ("m", "elotes", "c"), "tomate": ("m", "tomates", "c"),
    "globo": ("m", "globos", "o"), "dado": ("m", "dados", "o"), "regalo": ("m", "regalos", "o"),
    "pelota": ("f", "pelotas", "o"), "flor": ("f", "flores", "o"), "estrella": ("f", "estrellas", "o"),
    "vela": ("f", "velas", "o"), "llave": ("f", "llaves", "o"), "bota": ("f", "botas", "o"),
}
NUMEROS_FRASE = {2: "dos", 3: "tres", 4: "cuatro"}

# ---------- Repaso de inglés: lo que ven en la escuela (kínder 3) ----------
# Dibujos nuevos para el repaso: se guardan como img/i_<clave>.svg
DIBUJOS_INGLES = {
    "boy": "👦", "girl": "👧", "man": "👨", "woman": "👩", "grandpa": "👴", "grandma": "👵",
    "teacher_m": "👨‍🏫", "teacher_f": "👩‍🏫", "firefighter_m": "👨‍🚒", "firefighter_f": "👩‍🚒",
    "doctor_m": "👨‍⚕️", "doctor_f": "👩‍⚕️", "cook_m": "👨‍🍳", "cook_f": "👩‍🍳",
    "farmer_m": "👨‍🌾", "farmer_f": "👩‍🌾", "police_m": "👮‍♂️", "police_f": "👮‍♀️",
    "prince": "🤴", "princess": "👸",
    "library": "📚", "playground": "🛝", "beach": "🏖️", "garden": "🌻", "sport": "🏟️",
    "hospital": "🏥", "school": "🏫", "park": "🏞️", "supermarket": "🛒",
    "candle": "🕯️", "lantern": "🏮", "fireworks": "🎆", "costume": "🦸",
    "cow": "🐄", "clown": "🤡", "snow": "❄️", "bowl": "🥣", "leaf": "🍃", "peach": "🍑", "tea": "🍵",
    "meat": "🍖", "bean": "🫘", "ear": "👂", "seed": "🌱", "feet": "🦶", "three": "3️⃣", "jeep": "🚙",
    "lotion": "🧴", "cup": "🥤", "babybottle": "🍼", "toothbrush": "🪥", "bucket": "🪣",
    "jar": "🫙", "milkglass": "🥛", "mirror": "🪞", "window": "🪟",
    "can": "🥫", "spoon": "🥄", "coin": "🪙", "paperclip": "📎", "bolt": "🔩", "recycle": "♻️",
}
# He / She: [dibujo, he o she, palabra, nivel]
PERSONAS = [
    ["i_boy", "he", "boy", 1], ["i_girl", "she", "girl", 1], ["i_man", "he", "man", 1],
    ["i_woman", "she", "woman", 1], ["i_grandpa", "he", "grandpa", 1], ["i_grandma", "she", "grandma", 1],
    ["i_prince", "he", "prince", 1], ["i_princess", "she", "princess", 1],
    ["i_teacher_m", "he", "teacher", 2], ["i_teacher_f", "she", "teacher", 2],
    ["i_firefighter_m", "he", "firefighter", 2], ["i_firefighter_f", "she", "firefighter", 2],
    ["i_doctor_m", "he", "doctor", 2], ["i_doctor_f", "she", "doctor", 2],
    ["i_cook_m", "he", "cook", 2], ["i_cook_f", "she", "cook", 2],
    ["i_farmer_m", "he", "farmer", 2], ["i_farmer_f", "she", "farmer", 2],
    ["i_police_m", "he", "police officer", 2], ["i_police_f", "she", "police officer", 2],
]
# Where are you? We're at the…: [lugar, dibujo]
LUGARES = [["library", "i_library"], ["playground", "i_playground"], ["beach", "i_beach"],
           ["community garden", "i_garden"], ["sport center", "i_sport"], ["hospital", "i_hospital"],
           ["school", "i_school"], ["park", "i_park"], ["supermarket", "i_supermarket"]]
# There's a / There are: [singular, plural, dibujo]
COSAS_INGLES = [["candle", "candles", "i_candle"], ["present", "presents", "regalo"],
                ["balloon", "balloons", "globo"], ["lantern", "lanterns", "i_lantern"],
                ["cake", "cakes", "pastel"], ["star", "stars", "estrella"],
                ["apple", "apples", "manzana"], ["ball", "balls", "pelota"]]
NUMBERS_EN = ["zero", "one", "two", "three", "four", "five"]
# OW, EA, EE: [palabra, letras, dibujo (o color #rrggbb)]
SONIDOS = [["cow", "ow", "i_cow"], ["owl", "ow", "_buho"], ["crown", "ow", "corona"],
           ["clown", "ow", "i_clown"], ["snow", "ow", "i_snow"], ["bowl", "ow", "i_bowl"],
           ["brown", "ow", "#795548"], ["flower", "ow", "flor"],
           ["leaf", "ea", "i_leaf"], ["peach", "ea", "i_peach"], ["tea", "ea", "i_tea"],
           ["sea", "ea", "mar"], ["seal", "ea", "foca"], ["meat", "ea", "i_meat"],
           ["bean", "ea", "i_bean"], ["ear", "ea", "i_ear"], ["beach", "ea", "i_beach"],
           ["bee", "ee", "abeja"], ["seed", "ee", "i_seed"], ["tree", "ee", "arbol"],
           ["sheep", "ee", "oveja"], ["cheese", "ee", "queso"], ["three", "ee", "i_three"],
           ["green", "ee", "#43a047"], ["queen", "ee", "i_princess"], ["feet", "ee", "i_feet"],
           ["jeep", "ee", "i_jeep"]]
# Recycle: [cosa, bote, dibujo]
RECICLA = [["bottle", "plastic", "i_lotion"], ["cup", "plastic", "i_cup"],
           ["baby bottle", "plastic", "i_babybottle"], ["toothbrush", "plastic", "i_toothbrush"],
           ["bucket", "plastic", "i_bucket"],
           ["jar", "glass", "i_jar"], ["glass of milk", "glass", "i_milkglass"], ["mirror", "glass", "i_mirror"],
           ["light bulb", "glass", "foco"], ["window", "glass", "i_window"],
           ["can", "metal", "i_can"], ["spoon", "metal", "i_spoon"], ["key", "metal", "llave"],
           ["coin", "metal", "i_coin"], ["paperclip", "metal", "i_paperclip"], ["bolt", "metal", "i_bolt"]]
# My words: vocabulario de la unidad: [palabra, dibujo]
VOCAB = [["hospital", "i_hospital"], ["firefighter", "i_firefighter_m"], ["teacher", "i_teacher_f"],
         ["beach", "i_beach"], ["cake", "pastel"], ["fireworks", "i_fireworks"], ["candles", "i_candle"],
         ["present", "regalo"], ["lanterns", "i_lantern"], ["costume", "i_costume"],
         ["library", "i_library"], ["playground", "i_playground"], ["garden", "i_garden"],
         ["doctor", "i_doctor_f"], ["school", "i_school"], ["balloon", "globo"]]
# Instrucciones en español (con la voz en español); las palabras en inglés van aparte.
FRASES_REPASO = {
    "heshe1": "Para un niño o un señor, decimos",
    "heshe2": "Y para una niña o una señora, decimos",
    "there1": "Si hay uno, decimos",
    "there2": "Si hay muchos, decimos",
    "lugares": "¿Dónde están? Busca el lugar.",
    "sonidos": "Escucha la palabra y elige las letras que faltan.",
    "sonidos3": "¿Cuál está bien escrita?",
    "recicla": "¿En qué bote de reciclaje va?",
}


def textos_repaso():
    """Voces en inglés del repaso. Todas las palabras sueltas usan la clave i_w_<palabra>."""
    t = {"i_w_he": "He.", "i_w_she": "She.", "i_where": "Where are you?",
         "i_theres_a": "There's a.", "i_there_are": "There are."}
    def palabra(w):
        t["i_w_" + clave(w)] = w[0].upper() + w[1:] + "."
    for _, g, w, _ in PERSONAS:
        palabra(w)
        art = "an" if w[0] in "aeiou" else "a"
        t["i_%s_%s" % (g, clave(w))] = "%s is %s %s." % (g.capitalize(), art, w)
    for w, _ in LUGARES:
        palabra(w)
        t["i_at_" + clave(w)] = "We're at the %s." % w
    for uno, varios, _ in COSAS_INGLES:
        t["i_there_1_" + clave(uno)] = "There's a %s." % uno
        for n in range(2, 6):
            t["i_there_%d_%s" % (n, clave(uno))] = "There are %s %s." % (NUMBERS_EN[n], varios)
    for w, _, _ in SONIDOS:
        palabra(w)
    for w, bote, _ in RECICLA:
        palabra(w)
        palabra(bote)
        t["i_es_" + bote] = "It's %s!" % bote
    for w, _ in VOCAB:
        palabra(w)
    return t

ESCUCHA_SILABAS = ["ma", "me", "mi", "mo", "mu", "pa", "pe", "pi", "po", "pu",
                   "sa", "se", "si", "so", "su", "la", "le", "li", "lo", "lu",
                   "ta", "te", "ti", "to", "tu", "da", "de", "di", "do", "du",
                   "na", "ne", "ni", "no", "nu", "ra", "re", "ri", "ro", "ru"]


def clave(texto):
    """Nombre de archivo seguro: sin acentos, ñ -> nn."""
    t = texto.lower().replace("ñ", "nn")
    t = "".join(c for c in unicodedata.normalize("NFD", t)
                if unicodedata.category(c) != "Mn")
    return "".join(c if c.isalnum() else "_" for c in t)


def leer_palabras():
    palabras = []
    with open(os.path.join(RAIZ, "herramientas", "palabras.txt"), encoding="utf-8") as f:
        for linea in f:
            linea = linea.strip()
            if not linea or linea.startswith("#"):
                continue
            partes = [x.strip() for x in linea.split("|")]
            p, s, e = partes[:3]
            ingles = partes[3] if len(partes) > 3 else ""
            grupo = partes[4] if len(partes) > 4 else "-"
            if s.replace("-", "") != p:
                sys.exit(f"Las sílabas de '{p}' no forman la palabra: {s}")
            palabras.append({"p": p, "s": s.split("-"), "e": e, "k": clave(p),
                             "en": ingles, "g": grupo})
    return palabras


def svg_de_emoji(emoji, carpeta):
    codigos = [f"{ord(c):x}" for c in emoji]
    for intento in ("-".join(codigos), "-".join(c for c in codigos if c != "fe0f")):
        ruta = os.path.join(carpeta, intento + ".svg")
        if os.path.exists(ruta):
            return ruta
    return None


_VOZ = {}


def decir(texto, destino, voz, rehacer):
    if os.path.exists(destino) and not rehacer:
        return
    import wave
    from piper import PiperVoice, SynthesisConfig
    if voz not in _VOZ:
        _VOZ[voz] = PiperVoice.load(voz)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, "v.wav")
        with wave.open(wav, "wb") as w:
            _VOZ[voz].synthesize_wav(texto, w, syn_config=SynthesisConfig(length_scale=1.15))
        # Quita el silencio del principio y final, y comprime a MP3 mono.
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-af",
                        "silenceremove=start_periods=1:start_threshold=-45dB,"
                        "areverse,silenceremove=start_periods=1:start_threshold=-45dB,"
                        "areverse,apad=pad_dur=0.1",
                        "-ac", "1", "-ar", "22050", "-b:a", "48k", destino], check=True)


def cantidad(n, palabra):
    """«un gato», «una pera», «dos gatos»…"""
    genero, plural, _ = NOMBRES_FRASES[palabra]
    if n == 1:
        return ("un " if genero == "m" else "una ") + palabra
    return NUMEROS_FRASE[n] + " " + plural


def clave_frase(texto):
    return "fr_" + clave(texto).strip("_")


def hacer_frases():
    """Frases para leer en tres niveles. g = lo que muestra el dibujo: [[cantidad, palabra], …]."""
    azar = random.Random(2026)
    nombres = sorted(NOMBRES_FRASES)
    por_tipo = {t: [p for p in nombres if NOMBRES_FRASES[p][2] == t] for t in "aco"}
    lista = []

    def agregar(nivel, texto, grupos):
        if all(f["t"] != texto for f in lista):
            lista.append({"t": texto, "k": clave_frase(texto), "n": nivel, "g": grupos})

    # Nivel 1: «dos gatos», «una pera»
    for p in nombres:
        for n in (1, 2, 3):
            agregar(1, cantidad(n, p), [[n, p]])
    # Nivel 2: «dos peras y un sapo»
    while sum(f["n"] == 2 for f in lista) < 50:
        a, b = azar.sample(nombres, 2)
        na, nb = azar.randint(1, 3), azar.randint(1, 3)
        agregar(2, cantidad(na, a) + " y " + cantidad(nb, b), [[na, a], [nb, b]])
    # Nivel 3: «El gato tiene tres globos.», «La vaca come dos peras.»
    while sum(f["n"] == 3 for f in lista) < 50:
        animal = azar.choice(por_tipo["a"])
        come = azar.random() < 0.5
        cosa = azar.choice(por_tipo["c"] if come else por_tipo["c"] + por_tipo["o"])
        n = azar.randint(1, 4)
        art = "El " if NOMBRES_FRASES[animal][0] == "m" else "La "
        texto = art + animal + (" come " if come else " tiene ") + cantidad(n, cosa) + "."
        agregar(3, texto, [[1, animal], [n, cosa]])
    return lista


SIGNOS = "¡!¿?.,;:—\"'«»"


def palabras_de(texto, diccionario):
    """Parte una frase en palabras para tocarlas: [[como se ve, audio], …]."""
    fichas = []
    for trozo in texto.split():
        limpia = trozo.strip(SIGNOS).lower()
        if not limpia:
            continue
        audio = "w_" + clave(limpia) if limpia in diccionario else "p_" + clave(limpia)
        fichas.append([trozo, audio])
    return fichas


def leer_cuentos(palabras):
    """Lee herramientas/cuentos.txt."""
    diccionario = {w["p"] for w in palabras}
    dibujos = {w["k"] for w in palabras} | set(DIBUJOS_EXTRA)
    cuentos = []
    with open(os.path.join(RAIZ, "herramientas", "cuentos.txt"), encoding="utf-8") as f:
        for linea in f:
            linea = linea.strip()
            if not linea or linea.startswith("#"):
                continue
            texto, _, resto = [x.strip() for x in linea.partition("|")]
            dib = resto.split()
            for d in dib:
                if d not in dibujos:
                    sys.exit(f"cuentos.txt: no hay dibujo '{d}' (línea: {linea})")
            if texto.startswith("="):
                k = "c%d" % (len(cuentos) + 1)
                cuentos.append({"t": texto[1:].strip(), "k": k, "d": dib[0], "paginas": []})
            elif texto.startswith("?"):
                c = cuentos[-1]
                t = texto[1:].strip()
                c["pregunta"] = {"t": t, "v": "cu_%s_p" % c["k"], "p": palabras_de(t, diccionario),
                                 "o": dib}
            else:
                c = cuentos[-1]
                n = len(c["paginas"]) + 1
                c["paginas"].append({"t": texto, "v": "cu_%s_%d" % (c["k"], n), "d": dib,
                                     "p": palabras_de(texto, diccionario)})
    return cuentos


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--twemoji", required=True)
    ap.add_argument("--voz")
    ap.add_argument("--voz-en", dest="voz_en")
    ap.add_argument("--rehacer", action="store_true")
    a = ap.parse_args()

    palabras = leer_palabras()
    for p in NOMBRES_FRASES:
        if not any(w["p"] == p for w in palabras):
            sys.exit(f"NOMBRES_FRASES: '{p}' no está en palabras.txt")
    frases = hacer_frases()
    cuentos = leer_cuentos(palabras)

    # Dibujos
    img = os.path.join(RAIZ, "img")
    os.makedirs(img, exist_ok=True)
    for w in palabras:
        ruta = svg_de_emoji(w["e"], a.twemoji)
        if not ruta:
            sys.exit(f"No hay dibujo para '{w['p']}' ({w['e']})")
        shutil.copyfile(ruta, os.path.join(img, w["k"] + ".svg"))
    for k, emoji in DIBUJOS_EXTRA.items():
        shutil.copyfile(svg_de_emoji(emoji, a.twemoji), os.path.join(img, k + ".svg"))
    for k, emoji in DIBUJOS_INGLES.items():
        shutil.copyfile(svg_de_emoji(emoji, a.twemoji), os.path.join(img, "i_" + k + ".svg"))
    # Íconos de los botones (como imagen: algunas TVs no tienen fuente de emojis)
    for nombre, emoji in ICONOS.items():
        shutil.copyfile(svg_de_emoji(emoji, a.twemoji), os.path.join(img, "_" + nombre + ".svg"))

    # Voces: palabras, sílabas, letras y frases
    textos = {}
    for w in palabras:
        textos["w_" + w["k"]] = w["p"] + "."
        for s in w["s"]:
            textos["s_" + clave(s)] = s + "."
    for s in ESCUCHA_SILABAS:
        textos["s_" + clave(s)] = s + "."
    iniciales = sorted({inicial(w["p"]) for w in palabras if inicial(w["p"])})
    for l in iniciales:
        n = NOMBRES_LETRAS[l]
        textos["l_" + clave(l)] = f"Esta es la letra {n}. ¿Qué dibujo empieza con {n}?"
    for l, n in NOMBRES_LETRAS.items():
        textos["n_" + clave(l)] = n + "."
    for i, n in enumerate(NUMEROS):
        textos[f"m_{i}"] = n + "."
    for k, es, _ in COLORES:
        textos["c_" + k] = es + "."
    # Inglés (se generan con la voz --voz-en)
    ingles = {}
    for w in palabras:
        if w["en"] and w["g"] != "-":
            ingles["e_" + w["k"]] = w["en"] + "."
    for i, n in enumerate(NUMBERS):
        ingles[f"en_{i}"] = n + "."
    for k, _, _ in COLORES:
        ingles["ec_" + k] = k + "."
    for k, t in FRASES_EN.items():
        ingles["ef_" + k] = t
    ingles.update(textos_repaso())
    for k, t in FRASES_REPASO.items():
        textos["f_" + k] = t
    for k, t in FRASES.items():
        textos["f_" + k] = t
    for fr in frases:
        textos[fr["k"]] = fr["t"][0].upper() + fr["t"][1:] + ("" if fr["t"].endswith(".") else ".")
    for c in cuentos:
        for pag in c["paginas"] + [c["pregunta"]]:
            textos[pag["v"]] = pag["t"]
            for vista, audio in pag["p"]:
                if audio.startswith("p_"):
                    textos[audio] = vista.strip(SIGNOS).lower() + "."

    if a.voz:
        aud = os.path.join(RAIZ, "audio")
        os.makedirs(aud, exist_ok=True)
        for i, (k, t) in enumerate(sorted(textos.items())):
            decir(t, os.path.join(aud, k + ".mp3"), a.voz, a.rehacer)
            print(f"\r voces {i + 1}/{len(textos)}", end="", flush=True)
        print()
    if a.voz_en:
        for k, t in sorted(ingles.items()):
            decir(t, os.path.join(RAIZ, "audio", k + ".mp3"), a.voz_en, a.rehacer)

    datos = {
        "palabras": [dict({"p": w["p"], "s": w["s"], "k": w["k"]},
                          **({"en": w["en"], "g": w["g"]} if w["g"] != "-" else {})) for w in palabras],
        "colores": [{"k": k, "es": es, "hex": h} for k, es, h in COLORES],
        "letras": {l: NOMBRES_LETRAS[l] for l in iniciales},
        "escucha": ESCUCHA_SILABAS,
        "audios": sorted(list(textos.keys()) + list(ingles.keys())),
        "frases": frases,
        "tipos": {p: t[2] for p, t in sorted(NOMBRES_FRASES.items())},
        "cuentos": cuentos,
        "repaso": {"personas": PERSONAS, "lugares": LUGARES, "cosas": COSAS_INGLES, "sonidos": SONIDOS,
                   "recicla": RECICLA, "vocab": VOCAB, "numeros": NUMBERS_EN},
    }
    with open(os.path.join(RAIZ, "js", "datos.js"), "w", encoding="utf-8") as f:
        f.write("// Generado por herramientas/generar.py; no editar a mano.\n")
        f.write("var DATOS = " + json.dumps(datos, ensure_ascii=False) + ";\n")
    print(f"{len(palabras)} palabras, {len(iniciales)} letras, {len(frases)} frases, "
          f"{len(cuentos)} cuentos, {len(textos)} voces")


def inicial(p):
    """Letra con la que empieza la palabra para el juego de letras.
    Las que empiezan con ch o ll no se usan (suenan distinto a su letra)."""
    if p[:2] in ("ch", "ll"):
        return None
    c = unicodedata.normalize("NFD", p[0])[0]
    return c


if __name__ == "__main__":
    main()
