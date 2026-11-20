/* Repaso de inglés: lo que están viendo en la escuela (kínder 3).
 *  - My words: vocabulario de la unidad (hospital, firefighter, fireworks, lanterns…).
 *  - He / She: niño u hombre = He; niña o mujer = She.
 *  - Places: «Where are you? We're at the library.»
 *  - There's a / There are: uno o muchos.
 *  - ow · ea · ee: completar palabras como c-ow, l-ea-f, s-ee-d (y cuál está bien escrita).
 *  - Recycle: ¿plastic, glass o metal?
 * Los datos salen de herramientas/generar.py (PERSONAS, LUGARES, SONIDOS…). Usa el mismo motor de
 * rondas: 5 preguntas, estrellas, estampas y 3 niveles. */
(function () {
  'use strict';
  var L = window.LEO, $ = L.$, el = L.el, azar = L.azar, mezclar = L.mezclar, contiene = L.contiene;
  var D = L.D, Q = L.PREGUNTAS, R = D.repaso, clave = L.clave, txt = L.txt;

  function imagen(k, clase) {
    var i = el('img', clase || '');
    i.src = 'img/' + k + '.svg';
    i.alt = '';
    return i;
  }
  // Dibujo grande; si es un color (#rrggbb) se pinta un círculo de ese color.
  function dibujoGrande(k) {
    var d = el('div', 'dibujo-grande');
    if (k.charAt(0) === '#') {
      var c = el('div', 'muestra-color');
      c.style.background = k;
      d.appendChild(c);
    } else d.appendChild(imagen(k));
    return d;
  }
  function bocina(voz) {
    var b = el('button', 'altavoz-grande');
    b.appendChild(imagen('_altavoz'));
    L.prepararFoco(b, function () { L.hablar(voz); });
    return b;
  }
  function entre(a, b) { return a + Math.floor(Math.random() * (b - a + 1)); }
  function voz(palabra) { return 'i_w_' + clave(palabra); }
  function bien() { return 'ef_bien' + entre(1, 3); }
  // Elige uno de la lista sin repetir en la ronda (k = posición de la clave en cada elemento).
  function sinRepetir(r, lista, k) {
    var libres = lista.filter(function (x) { return !contiene(r.usadas, x[k]); });
    if (!libres.length) { r.usadas = []; libres = lista; }
    var x = azar(libres);
    r.usadas.push(x[k]);
    return x;
  }
  function otros(lista, x, n) { return mezclar(lista.filter(function (y) { return y !== x; })).slice(0, n); }

  /* Agrega un botón de respuesta. vozBien / vozMal: lo que se dice al acertar o fallar. */
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
  function botonTexto(t, clase) { return el('button', 'opcion texto' + (clase ? ' ' + clase : ''), t); }
  function decir(v) { return function () { L.hablar(v); }; }

  /* ================= MY WORDS ================= */
  Q.vocab = function (r) {
    var x = sinRepetir(r, R.vocab, 0);
    // Nivel 3: solo se oye (sin la palabra escrita), con 4 dibujos.
    $('pregunta').appendChild(bocina([voz(x[0])]));
    if (r.nivel < 3) $('pregunta').appendChild(el('div', 'palabra-grande ingles', x[0]));
    mezclar([x].concat(otros(R.vocab, x, r.nivel === 1 ? 2 : 3))).forEach(function (y) {
      var b = el('button', 'opcion');
      b.appendChild(imagen(y[1]));
      opcion(b, y === x, [voz(x[0]), bien()], [voz(y[0]), 'ef_mal'], r.nivel === 1 ? decir([voz(y[0])]) : null);
    });
    r.repetir = [voz(x[0])];
    r.voz = (r.n === 0 ? ['f_ingles'] : []).concat(r.repetir);
  };

  /* ================= HE / SHE ================= */
  var AYUDA_HESHE = ['f_heshe1', 'i_w_he', 'f_heshe2', 'i_w_she'];
  Q.heshe = function (r) {
    var lista = R.personas.filter(function (p) { return r.nivel === 3 || p[3] === r.nivel; });
    var p = sinRepetir(r, lista, 0);
    var frase = 'i_' + p[1] + '_' + clave(p[2]);
    var art = /^[aeiou]/.test(p[2]) ? 'an ' : 'a ';
    $('pregunta').appendChild(dibujoGrande(p[0]));
    if (r.nivel === 2) $('pregunta').appendChild(el('div', 'palabra-grande ingles frase-en', '___ is ' + art + p[2] + '.'));
    ['he', 'she'].forEach(function (g) {
      var t = g.charAt(0).toUpperCase() + g.slice(1);
      var b = r.nivel === 3 ? botonTexto(t + ' is ' + art + p[2] + '.', 'frase-opcion') : botonTexto(t, 'heshe');
      // Al equivocarse se vuelve a explicar: niño u hombre = He; niña o mujer = She.
      opcion(b, g === p[1], [frase, bien()], AYUDA_HESHE, decir(['i_w_' + g]));
    });
    if (r.nivel === 3) $('opciones').className = 'columna';
    r.repetir = AYUDA_HESHE;
    r.voz = r.n === 0 ? AYUDA_HESHE : [];
  };

  /* ================= PLACES ================= */
  Q.lugares = function (r) {
    var l = sinRepetir(r, R.lugares, 0);
    var frase = 'i_at_' + clave(l[0]);
    var ops = mezclar([l].concat(otros(R.lugares, l, r.nivel === 1 ? 1 : 2)));
    if (r.nivel < 3) {
      $('pregunta').appendChild(dibujoGrande(l[1]));
      var caja = el('div', 'palabra-grande ingles frase-en pregunta-ingles');
      caja.appendChild(el('div', '', 'Where are you?'));
      caja.appendChild(el('div', '', "We're at the ___."));
      $('pregunta').appendChild(caja);
      ops.forEach(function (y) {
        opcion(botonTexto(y[0], 'lugar'), y === l, [frase, bien()], [voz(y[0]), 'ef_mal'], decir([voz(y[0])]));
      });
      r.repetir = ['i_where'];
    } else {
      // Nivel 3: solo se oye «We're at the library» y se busca el dibujo.
      $('pregunta').appendChild(bocina(['i_where', frase]));
      $('pregunta').appendChild(el('div', 'palabra-grande ingles frase-en', 'Where are you?'));
      ops.forEach(function (y) {
        var b = el('button', 'opcion');
        b.appendChild(imagen(y[1]));
        opcion(b, y === l, [frase, bien()], [voz(y[0]), 'ef_mal']);
      });
      r.repetir = ['i_where', frase];
    }
    r.voz = (r.n === 0 ? ['f_lugares'] : []).concat(r.repetir);
  };

  /* ================= THERE'S A / THERE ARE ================= */
  var AYUDA_THERE = ['f_there1', 'i_theres_a', 'f_there2', 'i_there_are'];
  function grupo(n, k) {
    var g = el('div', 'grupo panel' + (n <= 2 ? ' pocos' : ''));
    for (var i = 0; i < n; i++) g.appendChild(imagen(k));
    return g;
  }
  Q.there = function (r) {
    var c = sinRepetir(r, R.cosas, 0);
    var n = r.nivel === 1 ? (Math.random() < 0.5 ? 1 : entre(3, 5)) : entre(1, 5);
    var frase = 'i_there_' + n + '_' + clave(c[0]);
    function texto(k) { return k === 1 ? "There's a " + c[0] + '.' : 'There are ' + R.numeros[k] + ' ' + c[1] + '.'; }
    $('pregunta').appendChild(grupo(n, c[2]));
    if (r.nivel === 1) {
      $('pregunta').appendChild(el('div', 'palabra-grande ingles frase-en',
        '___ ' + (n === 1 ? c[0] : R.numeros[n] + ' ' + c[1]) + '.'));
      [["There's a", 1, 'i_theres_a'], ['There are', 2, 'i_there_are']].forEach(function (o) {
        opcion(botonTexto(o[0], 'there'), (n === 1) === (o[1] === 1), [frase, bien()], AYUDA_THERE, decir([o[2]]));
      });
    } else {
      // Nivel 2: dos frases; nivel 3: tres. Hay que contar y fijarse en «There's a» / «There are».
      var otrosN = mezclar([1, 2, 3, 4, 5].filter(function (k) { return k !== n; })).slice(0, r.nivel === 2 ? 1 : 2);
      mezclar([n].concat(otrosN)).forEach(function (k) {
        opcion(botonTexto(texto(k), 'frase-opcion'), k === n, [frase, bien()], ['ef_mal'],
          decir(['i_there_' + k + '_' + clave(c[0])]));
      });
      $('opciones').className = 'columna';
    }
    r.repetir = AYUDA_THERE;
    r.voz = r.n === 0 ? AYUDA_THERE : [];
  };

  /* ================= OW · EA · EE ================= */
  Q.sonidos = function (r) {
    // ea y ee suenan igual: en los niveles 1 y 2 nunca salen juntas como opciones. En el nivel 3
    // (¿cuál está bien escrita?) no van las palabras cuya otra forma también existe (sea/see, meat/meet…).
    var HOMOFONAS = ['sea', 'meat', 'tea', 'beach', 'bean', 'feet'];
    var lista = r.nivel === 1 ? R.sonidos.filter(function (s) { return s[1] !== 'ea'; })
      : r.nivel === 3 ? R.sonidos.filter(function (s) { return !contiene(HOMOFONAS, s[0]); }) : R.sonidos;
    var s = sinRepetir(r, lista, 0);
    var palabra = s[0], d = s[1], i = palabra.indexOf(d);
    var arriba = el('div', 'escribe');
    arriba.appendChild(dibujoGrande(s[2]));
    $('pregunta').appendChild(arriba);
    if (r.nivel < 3) {
      var fila = el('div', 'palabra-huecos' + (palabra.length > 4 ? ' chicos' : ''));
      var huecos = [];
      palabra.split('').forEach(function (l, j) {
        var falta = j === i || j === i + 1;
        var caja = el('div', 'hueco' + (falta ? ' vacio' : ''), txt(l));
        if (falta) huecos.push(caja);
        fila.appendChild(caja);
      });
      arriba.appendChild(fila);
      // Nivel 1: ow o ee (suenan distinto). Nivel 2: también ea, que suena igual que ee.
      var ops = r.nivel === 1 ? ['ow', 'ee'] : d === 'ow' ? ['ow', azar(['ea', 'ee'])] : [d, 'ow'];
      mezclar(ops).forEach(function (o) {
        opcion(botonTexto(txt(o), 'letras'), o === d, [voz(palabra), bien()], ['ef_mal'], null, function () {
          huecos.forEach(function (h) { h.className = 'hueco lleno'; });
        });
      });
    } else {
      // Nivel 3: ¿cuál está bien escrita? leaf / leef, seed / sead, cow / cau (como lo escribiría
      // alguien que habla español: «braan», «braun»).
      var mal = palabra.slice(0, i) + (d === 'ow' ? 'au' : d === 'ea' ? 'ee' : 'ea') + palabra.slice(i + 2);
      mezclar([palabra, mal]).forEach(function (w) {
        opcion(botonTexto(txt(w), 'escrita'), w === palabra, [voz(palabra), bien()], ['ef_mal']);
      });
    }
    r.repetir = [voz(palabra)];
    r.voz = (r.n === 0 ? [r.nivel === 3 ? 'f_sonidos3' : 'f_sonidos'] : []).concat(r.repetir);
  };

  /* ================= RECYCLE ================= */
  var BOTES = [['plastic', '#fdd835'], ['glass', '#81c784'], ['metal', '#90a4ae']];
  Q.recicla = function (r) {
    var botes = r.nivel === 1 ? BOTES.filter(function (b) { return b[0] !== 'glass'; }) : BOTES;
    var lista = R.recicla.filter(function (x) { return botes.some(function (b) { return b[0] === x[1]; }); });
    var x = sinRepetir(r, lista, 0);
    // Nivel 3: solo se oye la cosa en inglés (sin dibujo).
    if (r.nivel === 3) $('pregunta').appendChild(bocina([voz(x[0])]));
    else $('pregunta').appendChild(dibujoGrande(x[2]));
    botes.forEach(function (b) {
      var bt = el('button', 'opcion bote');
      bt.style.background = b[1];
      bt.setAttribute('data-color', b[1]);
      bt.appendChild(el('span', 'bote-icono'));
      bt.appendChild(el('span', 'bote-nombre', b[0].toUpperCase()));
      opcion(bt, b[0] === x[1], [voz(x[0]), 'i_es_' + x[1], bien()], [voz(b[0]), 'ef_mal'], decir([voz(b[0])]));
    });
    r.repetir = [voz(x[0])];
    r.voz = (r.n === 0 ? ['f_recicla', voz('plastic'), voz('glass'), voz('metal')] : []).concat(r.repetir);
  };

  /* ================= MENÚ ================= */
  var rep = L.categoria('repaso', 'Repaso de inglés');
  rep.padre = 'ingles';
  L.agregarJuego('repaso', { id: 'vocab', nombre: 'My words', img: 'i_fireworks', color: '#e1bee7', ronda: true });
  L.agregarJuego('repaso', { id: 'heshe', nombre: 'He / She', img: 'i_girl', color: '#ffcdd2', ronda: true });
  L.agregarJuego('repaso', { id: 'lugares', nombre: 'Places', img: 'i_playground', color: '#c8e6c9', ronda: true });
  L.agregarJuego('repaso', { id: 'there', nombre: "There's a", img: 'i_candle', color: '#fff9c4', ronda: true });
  L.agregarJuego('repaso', { id: 'sonidos', nombre: 'ow ea ee', icono: 'ee', tinta: '#1565c0', color: '#bbdefb', ronda: true });
  L.agregarJuego('repaso', { id: 'recicla', nombre: 'Recycle', img: 'i_recycle', color: '#b2dfdb', ronda: true });
  L.agregarJuego('ingles', { id: 'repaso', nombre: 'Repaso', img: 'i_school', color: '#ffe0b2',
    nota: 'lo de la escuela', abrir: function () { L.abrirMenu('repaso'); } });
})();
