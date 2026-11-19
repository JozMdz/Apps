#!/usr/bin/env python3
"""Dibuja los bloques de pixeles del tema «bloques» y de «Mi mundo» (img/b_*.svg).

Son dibujos propios: cada bloque es una cuadrícula de 16x16 pixeles hecha con colores y patrones
de aquí (con una semilla fija, así sale igual cada vez).

Uso: python3 herramientas/bloques.py
"""
import os
import random

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
N = 16


def svg(pixeles, opacidad=None):
    """pixeles: lista de 16 renglones de 16 colores (None = transparente). Junta los pixeles
    seguidos del mismo color en un solo rectángulo para que el archivo sea chico."""
    partes = []
    for y, fila in enumerate(pixeles):
        x = 0
        while x < N:
            c = fila[x]
            fin = x
            while fin + 1 < N and fila[fin + 1] == c:
                fin += 1
            if c:
                op = ' fill-opacity="%s"' % opacidad[c] if opacidad and c in opacidad else ""
                partes.append('<rect x="%d" y="%d" width="%d" height="1" fill="%s"%s/>' % (x, y, fin - x + 1, c, op))
            x = fin + 1
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16" shape-rendering="crispEdges">'
            + "".join(partes) + "</svg>\n")


def ruido(azar, colores, pesos=None):
    return [[azar.choices(colores, pesos)[0] for _ in range(N)] for _ in range(N)]


def borde(p, claro, oscuro):
    """Bisel de bloque: orilla de arriba/izquierda clara y de abajo/derecha oscura."""
    for i in range(N):
        p[0][i] = claro
        p[i][0] = claro
        p[N - 1][i] = oscuro
        p[i][N - 1] = oscuro
    return p


def tierra(azar):
    return ruido(azar, ["#8a5a3b", "#7a4e33", "#9b6a47", "#6b432c"], [5, 4, 3, 1])


def pasto(azar):
    p = tierra(azar)
    for x in range(N):
        hondo = azar.choice([3, 4, 4, 5, 6])
        for y in range(hondo):
            p[y][x] = azar.choices(["#5fb043", "#4e9a37", "#71c552"], [4, 3, 2])[0]
    return p


def piedra(azar):
    p = ruido(azar, ["#8e8e8e", "#7c7c7c", "#9d9d9d"], [4, 3, 2])
    for _ in range(7):   # manchas oscuras
        x, y = azar.randrange(N), azar.randrange(N)
        for dx, dy in ((0, 0), (1, 0), (0, 1)):
            if x + dx < N and y + dy < N and azar.random() < 0.8:
                p[y + dy][x + dx] = "#686868"
    return p


def tablas(azar):
    p = []
    tonos = ["#c08a52", "#b07c47", "#ca9660", "#b5814b"]
    for y in range(N):
        tabla = y // 4
        fila = []
        for x in range(N):
            if y % 4 == 3 or (x == (tabla * 5 + 3) % N and y % 4 != 3):
                fila.append("#7f5a32")
            else:
                fila.append(azar.choices([tonos[tabla], "#a8763f"], [9, 1])[0])
        p.append(fila)
    return p


def tronco(azar):
    p = []
    rayas = [azar.choice(["#6b4a2b", "#5c3e24", "#7a5634"]) for _ in range(N)]
    for y in range(N):
        p.append([rayas[x] if azar.random() < 0.85 else "#4a321c" for x in range(N)])
    return p


def hojas(azar):
    p = ruido(azar, ["#3f8f35", "#347d2c", "#4fa843", "#2a6b25"], [4, 3, 2, 1])
    for _ in range(10):
        p[azar.randrange(N)][azar.randrange(N)] = "#1f5219"
    return p


def arena(azar):
    return ruido(azar, ["#e9d9a1", "#dccb8b", "#f2e5b5", "#cfbc78"], [5, 4, 3, 1])


def agua(azar):
    p = ruido(azar, ["#3d7be0", "#3670d1", "#4688ec"], [4, 3, 3])
    for y in (2, 7, 12):
        x0 = azar.randrange(N)
        for dx in range(5):
            p[y][(x0 + dx) % N] = "#86b8ff"
    return p


def vidrio(azar):
    p = [[None] * N for _ in range(N)]
    for i in range(N):
        for j in (0, N - 1):
            p[i][j] = p[j][i] = "#e6f7ff"
    for i in range(1, N - 1):
        for j in range(1, N - 1):
            p[i][j] = "#bfe9f7"
    for k in range(3, 7):   # brillos en diagonal
        p[k][k + 1] = "#ffffff"
        p[k + 5][k + 1] = "#ffffff"
    return p


def ladrillo(azar):
    p = []
    for y in range(N):
        fila = []
        for x in range(N):
            junta = y % 4 == 3 or (x + (4 if (y // 4) % 2 else 0)) % 8 == 7
            fila.append("#d8cfc2" if junta else azar.choices(["#b04a3c", "#a3402f", "#bf5646"], [4, 3, 2])[0])
        p.append(fila)
    return p


def metal(colores, brillo, orilla):
    def pintar(azar):
        p = ruido(azar, colores, [4, 3, 2])
        for x, y in ((3, 3), (4, 3), (3, 4), (10, 9), (11, 9), (10, 10), (6, 12)):
            p[y][x] = brillo
        return borde(p, brillo, orilla)
    return pintar


def nieve(azar):
    return ruido(azar, ["#f5f9fc", "#e6eef5", "#ffffff"], [4, 3, 2])


def calabaza(azar):
    p = ruido(azar, ["#e88a1a", "#d97b10", "#f0982a"], [4, 3, 2])
    for x in (3, 8, 12):   # gajos
        for y in range(N):
            p[y][x] = "#c46a0c"
    return p


BLOQUES = {
    "pasto": pasto, "tierra": tierra, "piedra": piedra, "tablas": tablas, "tronco": tronco,
    "hojas": hojas, "arena": arena, "agua": agua, "vidrio": vidrio, "ladrillo": ladrillo,
    "oro": metal(["#f5c518", "#e8b40e", "#ffd84d"], "#fff3b0", "#b8860b"),
    "gema": metal(["#3fd0d4", "#2fb9be", "#6fe6e8"], "#e0ffff", "#1b8a8e"),
    "nieve": nieve, "calabaza": calabaza,
}

# Pico (para el botón «Mi mundo»): . = nada, M = mango, P = punta, B = brillo, O = orilla
PICO = """
................
.....OOOOOO.....
...OOPPPPPPOO...
..OPPBBPPPPPPO..
.OPPO.OMMOOPPPO.
.OPO...OMMO.OPO.
.OO....OMMO..OO.
.......OMMO.....
.......OMMO.....
.......OMMO.....
.......OMMO.....
.......OMMO.....
.......OMMO.....
.......OMMO.....
.......OOOO.....
................
"""


def main():
    destino = os.path.join(RAIZ, "img")
    for nombre, pintar in BLOQUES.items():
        azar = random.Random(nombre)
        pix = pintar(azar)
        if nombre not in ("vidrio", "oro", "gema"):
            # Bisel suave para que se vea como bloque
            for i in range(N):
                pix[N - 1][i] = oscurecer(pix[N - 1][i])
                pix[i][N - 1] = oscurecer(pix[i][N - 1])
        with open(os.path.join(destino, "b_%s.svg" % nombre), "w") as f:
            f.write(svg(pix, {"#bfe9f7": "0.55"}))
    colores = {".": None, "M": "#8a5a2b", "P": "#9fb4c4", "B": "#e8f2f8", "O": "#2b2b2b"}
    pix = [[colores[c] for c in fila] for fila in PICO.strip().split("\n")]
    with open(os.path.join(destino, "b_pico.svg"), "w") as f:
        f.write(svg(pix))
    print("%d bloques y el pico en img/b_*.svg" % len(BLOQUES))


def oscurecer(c):
    if not c:
        return c
    r, g, b = (int(c[i:i + 2], 16) for i in (1, 3, 5))
    return "#%02x%02x%02x" % (int(r * .78), int(g * .78), int(b * .78))


if __name__ == "__main__":
    main()
