/* Más juegos para estudiar (kínder 3):
 *  Números: Formas · ¿Qué sigue? (patrones) · ¿Qué número falta? · Restas · ¿Cuál se fue? (memoria)
 *  Leer:    Rimas · Mayúsculas (pareja mayúscula–minúscula)
 * Todos usan el motor de rondas de app.js: 5 preguntas, estrellas, estampas, bloques y 3 niveles. */
(function () {
  'use strict';
  var L = window.LEO, $ = L.$, el = L.el, azar = L.azar, mezclar = L.mezclar, contiene = L.contiene;
  var P = L.P, Q = L.PREGUNTAS, txt = L.txt;

  function imagen(k, clase) {
    var i = el('img', clase || '');
    i.src = 'img/' + k + '.svg';
    i.alt = '';
    return i;
  }
  function entre(a, b) { return a + Math.floor(Math.random() * (b - a + 1)); }
  function sinRepetir(r, lista, clave) {
    var libres = lista.filter(function (x) { return !contiene(r.usadas, clave ? clave(x) : x); });
    if (!libres.length) { r.usadas = []; libres = lista; }
    var x = azar(libres);
    r.usadas.push(clave ? clave(x) : x);
    return x;
  }
  function opcion(b, correcta, vozBien, vozMal, alEnfocar, alAcertar) {
    b.esCorrecta = !!correcta;
    L.prepararFoco(b, function () {
      if (b.classList.contains('bien')) return;
      if (correcta) {
        b.classList.add('bien');
        L.lluvia(30);
        if (alAcertar) alAcertar();
        L.preguntaLista(vozBien);
      } else {
        L.equivocado(b, vozMal);
      }
    }, alEnfocar || null);
    $('opciones').appendChild(b);
    return b;
  }
  function botonTexto(t, clase) { return el('button', 'opcion' + (clase ? ' ' + clase : ''), t); }
  function decir(v) { return function () { L.hablar(v); }; }
  // Números cercanos a n (para que las opciones se parezcan).
  function cercanos(n, cuantos, min, max) {
    var lista = [];
    for (var d = 1; lista.length < cuantos + 2 && d < 30; d++) {
      if (n - d >= min) lista.push(n - d);
      if (n + d <= max) lista.push(n + d);
    }
    return mezclar(lista.slice(0, cuantos + 1)).slice(0, cuantos);
  }

  /* ================= FORMAS ================= */
  var COLORES = ['#e53935', '#1e88e5', '#fdd835', '#43a047', '#fb8c00', '#8e24aa', '#ec407a'];
  // Dibujo de cada figura en un cuadro de 100 x 100.
  var FIGURAS = {
    circulo: '<circle cx="50" cy="50" r="42"/>',
    cuadrado: '<rect x="12" y="12" width="76" height="76"/>',
    triangulo: '<polygon points="50,8 94,90 6,90"/>',
    rectangulo: '<rect x="4" y="26" width="92" height="48"/>',
    ovalo: '<ellipse cx="50" cy="50" rx="46" ry="30"/>',
    rombo: '<polygon points="50,4 92,50 50,96 8,50"/>',
    estrella: '<polygon points="50,5 61,38 96,38 68,59 78,93 50,72 22,93 32,59 4,38 39,38"/>',
    corazon: '<path d="M50 90 C 18 66 4 48 4 30 C 4 14 16 6 28 6 C 40 6 47 14 50 22 C 53 14 60 6 72 6 C 84 6 96 14 96 30 C 96 48 82 66 50 90 Z"/>',
    pentagono: '<polygon points="50,6 94,38 77,92 23,92 6,38"/>',
    hexagono: '<polygon points="27,8 73,8 96,50 73,92 27,92 4,50"/>'
  };
  var LADOS = { triangulo: 3, cuadrado: 4, rectangulo: 4, rombo: 4, pentagono: 5, hexagono: 6 };
  var FORMAS_NIVEL = {
    1: ['circulo', 'cuadrado', 'triangulo', 'rectangulo'],
    2: ['circulo', 'cuadrado', 'triangulo', 'rectangulo', 'ovalo', 'rombo', 'estrella', 'corazon'],
    3: Object.keys(LADOS)
  };
  function figura(k, color) {
    var d = el('span', 'figura');
    d.innerHTML = '<svg viewBox="0 0 100 100" width="100%" height="100%"><g fill="' + color +
      '" stroke="#263238" stroke-width="4" stroke-linejoin="round">' + FIGURAS[k] + '</g></svg>';
    return d;
  }
  Q.formas = function (r) {
    var lista = FORMAS_NIVEL[r.nivel];
    var k = sinRepetir(r, lista);
    if (r.nivel < 3) {
      // ¿Dónde está el círculo? (los colores cambian para que se fije en la forma, no en el color)
      var otras = mezclar(lista.filter(function (x) { return x !== k; })).slice(0, r.nivel === 1 ? 2 : 3);
      var colores = mezclar(COLORES);
      mezclar([k].concat(otras)).forEach(function (x, i) {
        var b = el('button', 'opcion forma');
        b.appendChild(figura(x, colores[i]));
        opcion(b, x === k, ['fo_' + k, L.felicitar()], ['fo_' + x, L.animar()],
          r.nivel === 1 ? decir(['fo_' + x]) : null);
      });
      r.repetir = ['f_forma_' + k];
      r.voz = r.repetir;
    } else {
      // ¿Cuántos lados tiene?
      var n = LADOS[k];
      var g = el('div', 'dibujo-grande forma-grande');
      g.appendChild(figura(k, azar(COLORES)));
      $('pregunta').appendChild(g);
      mezclar([n].concat(cercanos(n, 2, 3, 8))).forEach(function (x) {
        opcion(botonTexto(x), x === n, ['fo_' + k, 'm_' + n, L.felicitar()], [L.animar()]);
      });
      r.repetir = ['f_lados'];
      r.voz = ['f_lados'];
    }
  };

  /* ================= ¿QUÉ SIGUE? ================= */
  var PATRONES = { 1: ['AB'], 2: ['AAB', 'ABB', 'AABB'], 3: ['ABC', 'AABC', 'ABAC'] };
  var CON_DIBUJO = P.filter(function (w) { return w.s.length <= 3; });
  Q.patron = function (r) {
    var forma = azar(PATRONES[r.nivel]);
    var letras = [];
    forma.split('').forEach(function (l) { if (!contiene(letras, l)) letras.push(l); });
    var cosas = mezclar(CON_DIBUJO).slice(0, letras.length);
    var de = {};
    letras.forEach(function (l, i) { de[l] = cosas[i]; });
    var largo = r.nivel === 1 ? 5 : forma.length * 2 + (r.nivel === 3 ? 1 : 0);
    var serie = [];
    for (var i = 0; i <= largo; i++) serie.push(de[forma.charAt(i % forma.length)]);
    var sigue = serie.pop();
    var fila = el('div', 'serie');
    serie.forEach(function (w) { fila.appendChild(imagen(w.k)); });
    var hueco = el('span', 'serie-hueco', '?');
    fila.appendChild(hueco);
    $('pregunta').appendChild(fila);
    // Opciones: las cosas de la serie (y en el nivel 3 una de más que no está).
    var ops = cosas.slice();
    if (r.nivel === 3 || ops.length < 3) {
      var extra = azar(CON_DIBUJO.filter(function (w) { return !contiene(cosas, w); }));
      ops.push(extra);
    }
    mezclar(ops).forEach(function (w) {
      var b = el('button', 'opcion');
      b.appendChild(imagen(w.k));
      opcion(b, w === sigue, [L.felicitar()], null, null, function () {
        hueco.textContent = '';
        hueco.appendChild(imagen(w.k));
        hueco.className = 'serie-hueco lleno';
      });
    });
    r.repetir = ['f_quesigue'];
    r.voz = ['f_quesigue'];
  };

  /* ================= ¿QUÉ NÚMERO FALTA? ================= */
  Q.falta = function (r) {
    var paso = 1, max = r.nivel === 1 ? 10 : 20, bajando = false;
    if (r.nivel === 3) {
      var tipo = azar(['dos', 'baja', 'normal']);
      if (tipo === 'dos') paso = 2;          // de 2 en 2: 2, 4, 6, _
      if (tipo === 'baja') bajando = true;  // hacia atrás: 9, 8, _, 6
    }
    var cuantos = 5, inicio, n, hueco;
    do {
      inicio = paso === 2 ? 2 * entre(0, 5) : entre(1, max - cuantos + 1);
      hueco = r.nivel === 1 ? entre(1, 3) : entre(0, cuantos - 1);
      n = inicio + hueco * paso;
    } while (contiene(r.usadas, n) && r.usadas.length < 10);
    r.usadas.push(n);
    var fila = el('div', 'recta');
    var numeros = [];
    for (var i = 0; i < cuantos; i++) numeros.push(inicio + i * paso);
    if (bajando) numeros.reverse();
    var vacio = null;
    numeros.forEach(function (x) {
      var c = el('div', 'hueco' + (x === n ? ' vacio' : ''), x);
      if (x === n) vacio = c;
      fila.appendChild(c);
    });
    $('pregunta').appendChild(fila);
    mezclar([n].concat(cercanos(n, r.nivel === 1 ? 2 : 3, 0, 20))).forEach(function (x) {
      opcion(botonTexto(x), x === n, ['m_' + n, L.felicitar()], [L.animar()], null,
        function () { vacio.className = 'hueco lleno'; });
    });
    r.repetir = ['f_numfalta'];
    r.voz = ['f_numfalta'];
  };

  /* ================= RESTAS ================= */
  var COSAS = ['manzana', '_estrella', 'pelota', 'pato', 'globo', 'fresa', 'dona', 'pez'];
  Q.restas = function (r) {
    var tope = r.nivel === 1 ? 5 : 10;
    var a, b;
    do { a = entre(2, tope); b = entre(1, a - 1); } while (contiene(r.usadas, a + '-' + b) && r.usadas.length < 10);
    r.usadas.push(a + '-' + b);
    var fila = el('div', 'suma');
    if (r.nivel < 3) {
      // Los que se van aparecen tachados.
      var cosa = azar(COSAS);
      var g = el('div', 'grupo panel resta' + (a > 6 ? ' muchos' : ''));
      for (var i = 0; i < a; i++) {
        var c = el('span', 'cosa' + (i >= a - b ? ' se-va' : ''));
        c.appendChild(imagen(cosa));
        g.appendChild(c);
      }
      fila.appendChild(g);
    } else {
      fila.appendChild(el('span', 'hueco', a));
      fila.appendChild(el('span', 'signo', '−'));
      fila.appendChild(el('span', 'hueco', b));
      fila.appendChild(el('span', 'signo', '='));
    }
    var res = el('span', 'hueco vacio', a - b);
    if (r.nivel === 3) fila.appendChild(res);
    $('pregunta').appendChild(fila);
    mezclar([a - b].concat(cercanos(a - b, 2, 0, 10))).forEach(function (x) {
      opcion(botonTexto(x), x === a - b, ['m_' + a, 'f_menos', 'm_' + b, 'f_son', 'm_' + (a - b), L.felicitar()], null, null,
        function () { res.className = 'hueco lleno'; });
    });
    r.repetir = r.nivel === 3 ? ['m_' + a, 'f_menos', 'm_' + b] : ['f_quedan'];
    r.voz = r.repetir;
  };

  /* ================= ¿CUÁL SE FUE? (memoria) ================= */
  Q.sefue = function (r) {
    var cuantos = r.nivel + 2;   // 3, 4 o 5 dibujos
    var cosas = mezclar(CON_DIBUJO).slice(0, cuantos);
    var fila = el('div', 'serie memoria-fila');
    var lugares = cosas.map(function (w) {
      var c = el('span', 'serie-hueco lleno');
      c.appendChild(imagen(w.k));
      fila.appendChild(c);
      return c;
    });
    $('pregunta').appendChild(fila);
    var ronda = L.ronda();
    var pregunta = ronda.n;
    // Primero se ven todos un rato; luego uno se va y hay que decir cuál.
    setTimeout(function () {
      if (L.ronda() !== ronda || ronda.n !== pregunta || L.pantalla().id !== 'juego') return;
      var i = Math.floor(Math.random() * cuantos);
      var seFue = cosas[i];
      lugares[i].className = 'serie-hueco';
      lugares[i].innerHTML = '?';
      var otras = mezclar(CON_DIBUJO.filter(function (w) { return !contiene(cosas, w); })).slice(0, 2);
      mezclar([seFue].concat(otras)).forEach(function (w) {
        var b = el('button', 'opcion');
        b.appendChild(imagen(w.k));
        opcion(b, w === seFue, ['w_' + w.k, L.felicitar()], ['w_' + w.k, L.animar()], null, function () {
          lugares[i].className = 'serie-hueco lleno';
          lugares[i].innerHTML = '';
          lugares[i].appendChild(imagen(w.k));
        });
      });
      L.enfocar($('opciones').firstChild, true);
      L.hablar(['f_cualsefue']);
      r.repetir = ['f_cualsefue'];
    }, 2500 + cuantos * 700);
    r.repetir = ['f_fijate'];
    r.voz = ['f_fijate'];
  };

  /* ================= RIMAS ================= */
  // La rima de una palabra: desde la vocal de la sílaba fuerte hasta el final (ga-to → «ato»).
  function rima(w) {
    var s = w.s, fuerte = -1;
    for (var i = 0; i < s.length; i++) if (/[áéíóú]/.test(s[i])) fuerte = i;
    if (fuerte < 0) fuerte = /[aeiouns]$/.test(w.p) ? Math.max(0, s.length - 2) : s.length - 1;
    var resto = s.slice(fuerte).join('');
    var m = /[aeiouáéíóú]+.*$/.exec(resto);
    // Diptongos (hue-vo, gua): la rima empieza en la última vocal del grupo.
    var r = m ? m[0] : resto;
    r = r.replace(/^[aeiouáéíóú](?=[aeiouáéíóú])/, '');
    return L.clave(r);
  }
  var POR_RIMA = {};
  P.forEach(function (w) { var k = rima(w); (POR_RIMA[k] = POR_RIMA[k] || []).push(w); });
  var CON_RIMA = P.filter(function (w) { return POR_RIMA[rima(w)].length > 1; });
  Q.rimas = function (r) {
    var w = sinRepetir(r, CON_RIMA, function (x) { return x.k; });
    var k = rima(w);
    var pareja = azar(POR_RIMA[k].filter(function (x) { return x !== w; }));
    var otras = mezclar(P.filter(function (x) { return rima(x) !== k && x.p.slice(-1) !== w.p.slice(-1); }))
      .slice(0, r.nivel === 1 ? 1 : 2);
    var leer = r.nivel === 3;   // nivel 3: las opciones son palabras escritas
    var arriba = el('div', 'escribe');
    var d = el('button', 'dibujo-grande dibujo-boton');
    d.appendChild(imagen(w.k));
    L.prepararFoco(d, decir(['w_' + w.k]));
    arriba.appendChild(d);
    arriba.appendChild(el('div', 'palabra-grande', txt(w.p)));
    $('pregunta').appendChild(arriba);
    mezclar([pareja].concat(otras)).forEach(function (x) {
      var b;
      if (leer) b = botonTexto(txt(x.p), 'palabra-opcion');
      else { b = el('button', 'opcion'); b.appendChild(imagen(x.k)); }
      opcion(b, x === pareja, ['w_' + w.k, 'w_' + x.k, L.felicitar()], ['w_' + x.k, L.animar()],
        leer ? null : decir(['w_' + x.k]));
    });
    r.repetir = ['f_rima', 'w_' + w.k];
    r.voz = (r.n === 0 ? ['f_rimas'] : []).concat(r.repetir);
  };

  /* ================= MAYÚSCULAS ================= */
  var MAY_NIVEL = {
    1: ['a', 'e', 'i', 'o', 'u', 'm', 's', 'l'],
    2: ['p', 't', 'd', 'n', 'r', 'f', 'g', 'b', 'c', 'h', 'j', 'v'],
    3: ['b', 'd', 'p', 'q', 'n', 'u', 'm', 'w', 'g', 'y', 'ñ', 'e', 'a']
  };
  var PARECIDAS = { b: 'dpq', d: 'bpq', p: 'bdq', q: 'bdp', n: 'umñ', u: 'nv', m: 'nw', w: 'mv', g: 'qy', y: 'gv', 'ñ': 'nm', e: 'ac', a: 'eo' };
  Q.mayus = function (r) {
    var l = sinRepetir(r, MAY_NIVEL[r.nivel]);
    var alReves = r.nivel === 3 && Math.random() < 0.5;   // nivel 3: a veces al revés (minúscula → mayúscula)
    var cerca = (PARECIDAS[l] || '').split('');
    var pool = r.nivel === 3 && cerca.length ? cerca : MAY_NIVEL[r.nivel === 1 ? 1 : 2];
    if (r.nivel === 1) pool = pool.concat(['p', 't', 'n', 'r']);
    var otras = mezclar(pool.filter(function (x) { return x !== l; })).slice(0, r.nivel === 1 ? 2 : 3);
    var nombre = 'n_' + L.clave(l);
    $('pregunta').appendChild(el('div', 'letra-grande', alReves ? l : l.toUpperCase()));
    mezclar([l].concat(otras)).forEach(function (x) {
      var b = botonTexto(alReves ? x.toUpperCase() : x, 'letra-opcion');
      opcion(b, x === l, [nombre, L.felicitar()], ['n_' + L.clave(x), L.animar()], decir(['n_' + L.clave(x)]));
    });
    r.repetir = [alReves ? 'f_mayuscula' : 'f_minuscula', nombre];
    r.voz = r.repetir;
  };

  /* ================= MENÚS ================= */
  L.agregarJuego('mates', { id: 'restas', nombre: 'Restas', icono: '3-1', tinta: '#c62828', color: '#ffcdd2', ronda: true });
  L.agregarJuego('mates', { id: 'falta', nombre: '¿Qué número falta?', icono: '4 _ 6', tinta: '#2e7d32', color: '#dcedc8', ronda: true });
  L.agregarJuego('mates', { id: 'formas', nombre: 'Formas', img: '_formas', color: '#bbdefb', ronda: true });
  L.agregarJuego('mates', { id: 'patron', nombre: '¿Qué sigue?', img: '_patron', color: '#e1bee7', ronda: true });
  L.agregarJuego('mates', { id: 'sefue', nombre: '¿Cuál se fue?', img: '_carta', color: '#fff9c4', ronda: true });
  L.agregarJuego('leer', { id: 'rimas', nombre: 'Rimas', icono: 'gato pato', tinta: '#ef6c00', color: '#ffe0b2', ronda: true });
  L.agregarJuego('leer', { id: 'mayus', nombre: 'Mayúsculas', icono: 'Mm', tinta: '#00838f', color: '#b2ebf2', ronda: true });
})();
