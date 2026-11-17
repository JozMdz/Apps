/* Juegos de inglés, números y «Mi nombre». Usan el mismo motor de rondas de app.js
 * (5 preguntas, estrellas, estampas y niveles). */
(function () {
  'use strict';
  var L = window.LEO, $ = L.$, el = L.el, azar = L.azar, mezclar = L.mezclar, contiene = L.contiene;
  var D = L.D, P = L.P, Q = L.PREGUNTAS, EST = L.EST;

  function imagen(k, clase) {
    var i = el('img', clase || '');
    i.src = 'img/' + k + '.svg';
    i.alt = '';
    return i;
  }
  function bocina(voz) {
    var b = el('button', 'altavoz-grande');
    b.appendChild(imagen('_altavoz'));
    L.prepararFoco(b, function () { L.hablar(voz); });
    return b;
  }
  /* Agrega un botón de respuesta. vozBien/vozMal: lo que se dice al acertar o fallar. */
  function opcion(b, correcta, vozBien, vozMal, alEnfocar, alAcertar) {
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
  function sinRepetir(r, lista, clave) {
    var libres = lista.filter(function (x) { return !contiene(r.usadas, clave ? x[clave] : x); });
    if (!libres.length) { r.usadas = []; libres = lista; }
    var x = azar(libres);
    r.usadas.push(clave ? x[clave] : x);
    return x;
  }
  function otros(lista, correcta, n) {
    return mezclar(lista.filter(function (x) { return x !== correcta; })).slice(0, n);
  }
  function entre(a, b) { return a + Math.floor(Math.random() * (b - a + 1)); }
  // Números cercanos a n (para que las opciones se parezcan), entre min y max.
  function cercanos(n, cuantos, min, max) {
    var lista = [];
    for (var d = 1; lista.length < cuantos + 2 && d < 30; d++) {
      if (n - d >= min) lista.push(n - d);
      if (n + d <= max) lista.push(n + d);
    }
    return mezclar(lista.slice(0, cuantos + 1)).slice(0, cuantos);
  }
  function botonTexto(t, clase) { return el('button', 'opcion' + (clase ? ' ' + clase : ''), t); }
  function bienIngles() { return 'ef_bien' + entre(1, 3); }

  /* ================= INGLÉS ================= */
  var EN = P.filter(function (w) { return w.en; });
  var EN_NIVEL = {
    1: EN.filter(function (w) { return w.g === 'a'; }),
    2: EN.filter(function (w) { return w.g !== 'a'; }),
    3: EN
  };

  /* Words: oye la palabra en inglés y busca el dibujo. */
  Q.en_palabras = function (r) {
    var lista = EN_NIVEL[r.nivel];
    var w = sinRepetir(r, lista, 'k');
    var voz = ['e_' + w.k];
    $('pregunta').appendChild(bocina(voz));
    $('pregunta').appendChild(el('div', 'palabra-grande ingles', w.en));
    mezclar([w].concat(otros(lista, w, r.nivel >= 3 ? 3 : 2))).forEach(function (x) {
      var b = el('button', 'opcion');
      b.appendChild(imagen(x.k));
      opcion(b, x === w, ['e_' + w.k, 'w_' + w.k, bienIngles()], ['e_' + x.k, 'ef_mal'],
        r.nivel === 1 ? function () { L.hablar(['e_' + x.k]); } : null);
    });
    r.repetir = voz;
    r.voz = (r.n === 0 ? ['f_ingles'] : []).concat(voz);
  };

  /* Colors: oye el color en inglés y lo busca. */
  Q.en_colores = function (r) {
    var lista = r.nivel === 1 ? D.colores.slice(0, 6) : D.colores;
    var col = sinRepetir(r, lista, 'k');
    var voz = ['ec_' + col.k];
    $('pregunta').appendChild(bocina(voz));
    $('pregunta').appendChild(el('div', 'palabra-grande ingles', col.k));
    mezclar([col].concat(otros(lista, col, r.nivel >= 3 ? 3 : 2))).forEach(function (x) {
      var b = el('button', 'opcion color');
      var circulo = el('span', 'circulo');
      circulo.style.background = x.hex;
      b.setAttribute('data-color', x.hex);
      b.appendChild(circulo);
      opcion(b, x === col, ['ec_' + col.k, 'c_' + col.k, bienIngles()], ['ec_' + x.k, 'ef_mal'],
        r.nivel === 1 ? function () { L.hablar(['ec_' + x.k]); } : null);
    });
    r.repetir = voz;
    r.voz = (r.n === 0 ? ['f_ingles'] : []).concat(voz);
  };

  /* Numbers: oye el número en inglés y lo busca. */
  Q.en_numeros = function (r) {
    var max = r.nivel === 1 ? 5 : 10;
    var numeros = [];
    for (var i = 1; i <= max; i++) numeros.push(i);
    var n = sinRepetir(r, numeros);
    var voz = ['en_' + n];
    $('pregunta').appendChild(bocina(voz));
    mezclar([n].concat(otros(numeros, n, r.nivel >= 3 ? 3 : 2))).forEach(function (x) {
      opcion(botonTexto(x), x === n, ['en_' + n, 'm_' + n, bienIngles()], ['en_' + x, 'ef_mal'],
        r.nivel === 1 ? function () { L.hablar(['en_' + x]); } : null);
    });
    r.repetir = voz;
    r.voz = (r.n === 0 ? ['f_ingles'] : []).concat(voz);
  };

  /* ================= NÚMEROS ================= */
  var COSAS = ['manzana', '_estrella', 'pelota', 'pato', 'flor', 'pez', 'globo', 'fresa', 'dona', 'abeja'];
  function grupo(n, cosa, clase) {
    var g = el('div', 'grupo' + (clase ? ' ' + clase : '') + (n > 6 ? ' muchos' : ''));
    for (var i = 0; i < n; i++) g.appendChild(imagen(cosa));
    return g;
  }

  /* Cuenta: ¿cuántos hay? */
  Q.cuenta = function (r) {
    var n = r.nivel === 1 ? entre(1, 5) : r.nivel === 2 ? entre(3, 10) : entre(6, 12);
    while (contiene(r.usadas, n)) n = r.nivel === 1 ? entre(1, 5) : r.nivel === 2 ? entre(3, 10) : entre(6, 12);
    r.usadas.push(n);
    $('pregunta').appendChild(grupo(n, azar(COSAS), 'panel'));
    mezclar([n].concat(cercanos(n, 2, 1, 15))).forEach(function (x) {
      opcion(botonTexto(x), x === n, ['m_' + n, L.felicitar()]);
    });
    r.repetir = ['f_cuantos'];
    r.voz = ['f_cuantos'];
  };

  /* Números: oye el número y busca cómo se escribe. */
  Q.numeros = function (r) {
    var max = r.nivel === 1 ? 5 : r.nivel === 2 ? 10 : 20;
    var todos = [];
    for (var i = 1; i <= max; i++) todos.push(i);
    var n = sinRepetir(r, r.nivel === 3 ? todos.slice(9) : todos);
    var voz = ['f_dondenum', 'm_' + n];
    $('pregunta').appendChild(bocina(voz));
    mezclar([n].concat(otros(todos, n, r.nivel >= 3 ? 3 : 2))).forEach(function (x) {
      opcion(botonTexto(x), x === n, ['m_' + n, L.felicitar()], [L.animar(), 'm_' + n]);
    });
    r.repetir = voz;
    r.voz = voz;
  };

  /* Sumas: con dibujos (niveles 1 y 2) o solo con números (nivel 3). */
  Q.sumas = function (r) {
    var tope = r.nivel === 1 ? 5 : 10;
    var a, b;
    do { a = entre(1, tope - 1); b = entre(1, Math.min(5, tope - a)); } while (contiene(r.usadas, a + '+' + b) && r.usadas.length < 8);
    r.usadas.push(a + '+' + b);
    var cosa = azar(COSAS);
    var fila = el('div', 'suma');
    if (r.nivel < 3) {
      fila.appendChild(grupo(a, cosa, 'panel chico'));
      fila.appendChild(el('span', 'signo', '+'));
      fila.appendChild(grupo(b, cosa, 'panel chico'));
    } else {
      fila.appendChild(el('span', 'hueco', a));
      fila.appendChild(el('span', 'signo', '+'));
      fila.appendChild(el('span', 'hueco', b));
    }
    fila.appendChild(el('span', 'signo', '='));
    var resultado = el('span', 'hueco vacio', a + b);
    fila.appendChild(resultado);
    $('pregunta').appendChild(fila);
    mezclar([a + b].concat(cercanos(a + b, 2, 1, 12))).forEach(function (x) {
      opcion(botonTexto(x), x === a + b, ['m_' + a, 'f_mas', 'm_' + b, 'm_' + (a + b), L.felicitar()], null, null,
        function () { resultado.className = 'hueco lleno'; });
    });
    r.repetir = ['f_cuantoes', 'm_' + a, 'f_mas', 'm_' + b];
    r.voz = r.repetir;
  };

  /* Más o menos: ¿dónde hay más?, ¿dónde hay menos?, ¿cuál número es más grande? */
  Q.mas = function (r) {
    var menos = r.nivel === 2 && Math.random() < 0.5;
    var a, b, c, valores;
    if (r.nivel === 3) {
      a = entre(1, 20);
      do { b = entre(1, 20); } while (b === a);
      do { c = entre(1, 20); } while (c === a || c === b);
      valores = [a, b, c];
    } else {
      a = entre(1, r.nivel === 1 ? 6 : 9);
      do { b = entre(1, r.nivel === 1 ? 6 : 9); } while (Math.abs(a - b) < (r.nivel === 1 ? 2 : 1));
      valores = [a, b];
    }
    var meta = menos ? Math.min.apply(null, valores) : Math.max.apply(null, valores);
    var clave = r.nivel === 3 ? 'f_mayor' : menos ? 'f_dondemenos' : 'f_dondemas';
    var texto = r.nivel === 3 ? '¿Cuál es más grande?' : menos ? '¿Dónde hay menos?' : '¿Dónde hay más?';
    $('pregunta').appendChild(el('div', 'palabra-grande pregunta-texto', texto));
    var cosa = azar(COSAS);
    valores.forEach(function (x) {
      var boton;
      if (r.nivel === 3) boton = botonTexto(x);
      else { boton = el('button', 'opcion panel-opcion'); boton.appendChild(grupo(x, cosa)); }
      opcion(boton, x === meta, ['m_' + x, L.felicitar()]);
    });
    r.repetir = [clave];
    r.voz = [clave];
  };

  /* ================= MI NOMBRE ================= */
  var ABC = 'abcdefghijklmnñopqrstuvwxyz'.split('');
  function letraVista(l, i) { return EST.mayus || i === 0 ? l.toUpperCase() : l; }

  Q.nombre = function (r) {
    r.total = 3;
    var letras = (EST.nombre || '').toLowerCase().split('');
    // Ayuda: en el nivel 1 primero se copia el nombre, luego solo la primera letra, luego nada.
    var ayuda = r.nivel === 1 ? ['todo', 'primera', 'nada'][r.n] : r.nivel === 2 ? ['primera', 'nada', 'nada'][r.n] : 'nada';
    var fila = el('div', 'palabra-huecos' + (letras.length > 5 ? ' chicos' : ''));
    var cajas = letras.map(function (l, i) {
      var conGuia = ayuda === 'todo' || (ayuda === 'primera' && i === 0);
      var caja = el('div', 'hueco vacio' + (conGuia ? ' guia' : ''), letraVista(l, i));
      fila.appendChild(caja);
      return caja;
    });
    $('pregunta').appendChild(fila);
    var extra = mezclar(ABC.filter(function (l) { return !contiene(letras, l); })).slice(0, r.nivel >= 3 ? 3 : 2);
    var fichas = mezclar(letras.map(function (l, i) { return { l: l, i: i }; })
      .concat(extra.map(function (l) { return { l: l, i: 9 }; })));
    if (fichas.length > 6) $('opciones').className = 'muchas';
    var pos = 0;
    fichas.forEach(function (f) {
      // La mayúscula solo se muestra en la ficha de la primera letra.
      var b = botonTexto(letraVista(f.l, f.i === 0 ? 0 : 1));
      var nombreLetra = ['n_' + L.clave(f.l)];
      L.prepararFoco(b, function () {
        if (b.classList.contains('usada')) return;
        // La ficha con mayúscula solo sirve para la primera letra del nombre.
        if (f.l === letras[pos] && (EST.mayus || (pos === 0) === (f.i === 0))) {
          cajas[pos].className = 'hueco lleno';
          pos++;
          b.classList.add('usada');
          if (pos >= letras.length) {
            L.lluvia(30);
            L.preguntaLista(['f_tunombre', L.felicitar()]);
          } else {
            var resto = L.focos().filter(function (x) { return x.parentNode.id === 'opciones'; });
            L.enfocar(resto[0], true);
            L.hablar(nombreLetra);
          }
        } else {
          L.equivocado(b, null, true);
        }
      }, function () { L.hablar(nombreLetra); });
      $('opciones').appendChild(b);
    });
    r.repetir = ['f_nombre'];
    r.voz = r.n === 0 ? ['f_nombre'] : [];
  };

  /* ================= MENÚS ================= */
  L.agregarJuego('leer', {
    id: 'nombre', nombre: 'Mi nombre', icono: 'Yo', tinta: '#ef6c00', color: '#fff9c4', ronda: true,
    // La primera vez, papá o mamá escribe el nombre del niño.
    antes: function (seguir) { if (EST.nombre) seguir(); else L.escribirNombre(seguir); }
  });
  L.categoria('ingles', 'Inglés');
  L.agregarJuego('ingles', { id: 'en_palabras', nombre: 'Words', icono: 'cat', tinta: '#1565c0', color: '#bbdefb', ronda: true });
  L.agregarJuego('ingles', { id: 'en_colores', nombre: 'Colors', img: '_paleta_colores', color: '#ffe0b2', ronda: true });
  L.agregarJuego('ingles', { id: 'en_numeros', nombre: 'Numbers', icono: '1 2 3', tinta: '#6a1b9a', color: '#e1bee7', ronda: true });
  L.categoria('mates', 'Números');
  L.agregarJuego('mates', { id: 'cuenta', nombre: 'Cuenta', img: 'manzana', color: '#ffcdd2', ronda: true });
  L.agregarJuego('mates', { id: 'numeros', nombre: 'Números', icono: '123', tinta: '#2e7d32', color: '#c8e6c9', ronda: true });
  L.agregarJuego('mates', { id: 'sumas', nombre: 'Sumas', icono: '2+1', tinta: '#1565c0', color: '#bbdefb', ronda: true });
  L.agregarJuego('mates', { id: 'mas', nombre: 'Más o menos', icono: '< >', tinta: '#6a1b9a', color: '#e1bee7', ronda: true });
})();
