#!/usr/bin/env python3
"""Genera los datos, los dibujos y las voces de "Leo y Juego".

Uso:
    python3 herramientas/generar.py --twemoji RUTA_SVG --voz MODELO.onnx [--rehacer]

- RUTA_SVG: carpeta con los SVG de Twemoji (npm pack @twemoji/svg).
- MODELO.onnx: voz de Piper en español de México (es_MX-claude-high.onnx); con --voz-en
  se da otra en inglés (en_US-amy-medium.onnx) para el módulo de inglés,
  con su .onnx.json al lado. Requiere `pip install piper-tts` y ffmpeg.

Lee herramientas/palabras.txt y escribe js/datos.js, img/*.svg y audio/*.mp3.
Los audios que ya existen no se vuelven a generar (salvo con --rehacer).
"""
import argparse
import json
import os
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
          "engrane": "⚙️", "casa": "🏠"}

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
}

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


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--twemoji", required=True)
    ap.add_argument("--voz")
    ap.add_argument("--voz-en", dest="voz_en")
    ap.add_argument("--rehacer", action="store_true")
    a = ap.parse_args()

    palabras = leer_palabras()

    # Dibujos
    img = os.path.join(RAIZ, "img")
    os.makedirs(img, exist_ok=True)
    for w in palabras:
        ruta = svg_de_emoji(w["e"], a.twemoji)
        if not ruta:
            sys.exit(f"No hay dibujo para '{w['p']}' ({w['e']})")
        shutil.copyfile(ruta, os.path.join(img, w["k"] + ".svg"))
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
    for k, t in FRASES.items():
        textos["f_" + k] = t

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
    }
    with open(os.path.join(RAIZ, "js", "datos.js"), "w", encoding="utf-8") as f:
        f.write("// Generado por herramientas/generar.py; no editar a mano.\n")
        f.write("var DATOS = " + json.dumps(datos, ensure_ascii=False) + ";\n")
    print(f"{len(palabras)} palabras, {len(iniciales)} letras, {len(textos)} voces")


def inicial(p):
    """Letra con la que empieza la palabra para el juego de letras.
    Las que empiezan con ch o ll no se usan (suenan distinto a su letra)."""
    if p[:2] in ("ch", "ll"):
        return None
    c = unicodedata.normalize("NFD", p[0])[0]
    return c


if __name__ == "__main__":
    main()
