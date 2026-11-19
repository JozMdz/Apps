/* «Ya leo»: para cuando el niño ya empieza a leer.
 *  - Frases: lee una frase («dos gatos y una pera») y busca el dibujo que dice eso.
 *  - Escribe: ve el dibujo, oye la palabra y la escribe letra por letra.
 *  - Cuentos: cuentos cortos para leer solo; tocar una palabra la dice, la bocina lee la
 *    página (resaltando las palabras) y al final hay una pregunta.
 * Las frases y los cuentos salen de herramientas/generar.py (cuentos.txt). */
(function () {
  'use strict';
  var L = window.LEO, $ = L.$, el = L.el, azar = L.azar, mezclar = L.mezclar, contiene = L.contiene;
  var D = L.D, P = L.P, Q = L.PREGUNTAS, EST = L.EST, txt = L.txt;

  function imagen(k, clase) {
    var i = el('img', clase || '');
    i.src = 'img/' + k + '.svg';
    i.alt = '';
    return i;
  }
  function sinRepetir(r, lista) {
    var libres = lista.filter(function (x) { return !contiene(r.usadas, x.k); });
    if (!libres.length) { r.usadas = []; libres = lista; }
    var x = azar(libres);
    r.usadas.push(x.k);
    return x;
  }

  /* ================= FRASES ================= */
  var FRASES = { 1: [], 2: [], 3: [] };
  D.frases.forEach(function (f) { FRASES[f.n].push(f); });
  var TIPOS = D.tipos;
  var NOMBRES = Object.keys(TIPOS);

  // Dibujo de una frase: un renglón por cada cosa, con tantos dibujos como diga la frase.
  function escena(grupos) {
    var e = el('div', 'escena' + (grupos.length === 1 ? ' una' : ''));
    grupos.forEach(function (g) {
      var fila = el('div', 'escena-fila');
      for (var i = 0; i < g[0]; i++) fila.appendChild(imagen(g[1]));
      e.appendChild(fila);
    });
    return e;
  }
  function mismo(a, b) { return JSON.stringify(a) === JSON.stringify(b); }
  /* Dibujos parecidos pero equivocados: cambia cuántos hay o qué es una de las cosas, así
   * hay que leer toda la frase (el número y las dos palabras) para acertar. */
  function parecidas(f, cuantas) {
    var tope = f.n === 3 ? 4 : 3;
    var lista = [], intentos = 0;
    while (lista.length < cuantas && intentos++ < 300) {
      var g = JSON.parse(JSON.stringify(f.g));
      var i = Math.floor(Math.random() * g.length);
      var sujeto = f.n === 3 && i === 0;   // «El gato…»: siempre es uno
      if (!sujeto && Math.random() < 0.5) {
        g[i][0] += Math.random() < 0.5 ? -1 : 1;
        if (g[i][0] < 1 || g[i][0] > tope) continue;
      } else {
        var usadas = g.map(function (x) { return x[1]; });
        var mismoTipo = NOMBRES.filter(function (n) { return TIPOS[n] === TIPOS[g[i][1]] && !contiene(usadas, n); });
        g[i][1] = azar(mismoTipo);
      }
      if (!mismo(g, f.g) && !lista.some(function (x) { return mismo(x, g); })) lista.push(g);
    }
    return lista;
  }

  Q.frases = function (r) {
    var f = sinRepetir(r, FRASES[r.nivel]);
    $('pregunta').appendChild(el('div', 'palabra-grande frase' + (f.t.length > 20 ? ' larga' : ''), txt(f.t)));
    mezclar([f.g].concat(parecidas(f, 2))).forEach(function (g) {
      var b = el('button', 'opcion escena-opcion');
      b.appendChild(escena(g));
      L.prepararFoco(b, function () {
        if (b.classList.contains('bien')) return;
        if (mismo(g, f.g)) {
          b.classList.add('bien');
          L.lluvia(30);
          L.preguntaLista([f.k, L.felicitar()]);
        } else {
          L.equivocado(b);
        }
      });
      $('opciones').appendChild(b);
    });
    r.repetir = ['f_frases'];
    r.voz = r.n === 0 ? ['f_frases'] : [];
  };

  /* ================= ESCRIBE ================= */
  var ABC = 'abcdefghijklmnñopqrstuvwxyz'.split('');
  var DIGRAFOS = /ll|ch|qu|rr|gu/;
  var ESCRIBE = {
    1: P.filter(function (w) { return w.simple && !DIGRAFOS.test(w.p); }),
    2: P.filter(function (w) { return w.p.length <= 5 && !(w.simple && !DIGRAFOS.test(w.p)); }),
    3: P.filter(function (w) { return w.p.length >= 6; })
  };
  // «á» cuenta como «a» para no poner de relleno una letra que ya está en la palabra.
  function sinTilde(l) { return l === 'ñ' ? l : L.clave(l); }

  Q.escribe = function (r) {
    var w = sinRepetir(r, ESCRIBE[r.nivel]);
    var letras = w.p.split('');
    // Ayuda: en el nivel 1 se ven las letras clarito, en el 2 solo la primera, en el 3 nada.
    var fila = el('div', 'palabra-huecos' + (letras.length > 5 ? ' chicos' : ''));
    var cajas = letras.map(function (l, i) {
      var guia = r.nivel === 1 || (r.nivel === 2 && i === 0);
      var caja = el('div', 'hueco vacio' + (guia ? ' guia' : ''), txt(l));
      fila.appendChild(caja);
      return caja;
    });
    var arriba = el('div', 'escribe');
    var dibujo = el('button', 'dibujo-grande dibujo-boton');
    dibujo.appendChild(imagen(w.k));
    L.prepararFoco(dibujo, function () { L.hablar(['w_' + w.k]); });
    arriba.appendChild(dibujo);
    arriba.appendChild(fila);
    $('pregunta').appendChild(arriba);

    var sinAcentos = letras.map(sinTilde);
    var extra = mezclar(ABC.filter(function (l) { return !contiene(sinAcentos, l); })).slice(0, r.nivel >= 3 ? 3 : 2);
    var fichas = mezclar(letras.concat(extra));
    if (fichas.length > 6) $('opciones').className = 'muchas';
    var pos = 0;
    fichas.forEach(function (l) {
      var b = el('button', 'opcion', txt(l));
      var nombre = ['n_' + L.clave(l)];
      L.prepararFoco(b, function () {
        if (b.classList.contains('usada')) return;
        if (l === letras[pos]) {
          cajas[pos].className = 'hueco lleno';
          pos++;
          b.classList.add('usada');
          if (pos >= letras.length) {
            L.lluvia(30);
            L.preguntaLista(['w_' + w.k, L.felicitar()]);
          } else {
            var resto = L.focos().filter(function (x) { return x.parentNode.id === 'opciones'; });
            L.enfocar(resto[0], true);
            L.hablar(nombre);
          }
        } else {
          L.equivocado(b, null, true);
        }
      }, function () { L.hablar(nombre); });
      $('opciones').appendChild(b);
    });
    r.repetir = ['w_' + w.k];
    r.voz = (r.n === 0 ? ['f_escribe'] : []).concat(r.repetir);
  };

  /* ================= CUENTOS ================= */
  if (!EST.leidos) EST.leidos = {};
  var pc = L.pantallaNueva('cuento', '');
  pc.raiz.setAttribute('data-cel', 'c3');
  var dibujos = el('div', 'cuento-dibujos');
  var texto = el('div', 'cuento-texto');
  var opciones = el('div', 'cuento-opciones');
  var nav = el('div', 'cuento-nav');
  var numero = el('div', 'cuento-numero');
  pc.cuerpo.appendChild(dibujos);
  pc.cuerpo.appendChild(texto);
  pc.cuerpo.appendChild(opciones);
  pc.cuerpo.appendChild(nav);
  pc.barra.replaceChild(numero, pc.barra.lastChild);

  var anterior = el('button', 'boton-grande secundario', '◀');
  var escuchar = el('button', 'boton-grande secundario escuchar');
  escuchar.appendChild(imagen('_altavoz', 'ico'));
  escuchar.appendChild(document.createTextNode(' Escuchar'));
  var siguiente = el('button', 'boton-grande', '▶');
  nav.appendChild(anterior);
  nav.appendChild(escuchar);
  nav.appendChild(siguiente);

  var C = null, pag = 0, fichas = [], marcas = [];
  L.MUSICA.cuento = 'tranquila';

  function quitarMarcas() {
    marcas.forEach(clearTimeout);
    marcas = [];
    fichas.forEach(function (f) { f.classList.remove('leyendo'); });
  }
  function ponerTexto(p) {
    texto.innerHTML = '';
    fichas = p.p.map(function (par) {
      var b = el('button', 'palabra-cuento', txt(par[0]));
      L.prepararFoco(b, function () { quitarMarcas(); L.hablar([par[1]]); });
      texto.appendChild(b);
      return b;
    });
  }
  /* Lee la página y va resaltando cada palabra (el tiempo de cada una se reparte según su
   * largo; si no se sabe cuánto dura el audio, se resalta toda la frase). */
  function leer(p) {
    quitarMarcas();
    var lista = fichas.slice();
    function resaltar(dur) {
      if (!dur) { lista.forEach(function (f) { f.classList.add('leyendo'); }); return; }
      var pesos = p.p.map(function (par) { return par[0].length + (/[.,;:!?]$/.test(par[0]) ? 4 : 1.5); });
      var total = pesos.reduce(function (a, b) { return a + b; }, 0), t = 0.05;
      pesos.forEach(function (w, i) {
        (function (f, desde) {
          marcas.push(setTimeout(function () {
            lista.forEach(function (x) { x.classList.remove('leyendo'); });
            f.classList.add('leyendo');
          }, desde * 1000));
        })(lista[i], t);
        t += dur * w / total;
      });
    }
    var url = 'audio/' + p.v + '.mp3';
    if (window.SON && SON.corriendo()) SON.cargar(url, function (buf) { resaltar(buf && buf.duration); });
    else resaltar(0);
    L.hablar([p.v], quitarMarcas);
  }

  function pintar() {
    quitarMarcas();
    L.callar();
    var total = C.paginas.length;
    opciones.innerHTML = '';
    dibujos.innerHTML = '';
    if (pag < total) {
      var p = C.paginas[pag];
      pc.raiz.classList.remove('pregunta');
      p.d.forEach(function (k) { dibujos.appendChild(imagen(k)); });
      ponerTexto(p);
      numero.textContent = (pag + 1) + ' / ' + total;
      anterior.style.visibility = pag > 0 ? 'visible' : 'hidden';
      escuchar.style.display = '';
      siguiente.style.display = '';
      L.enfocar(siguiente, true);
      if (pag === 0) L.hablar(['f_cuentos']);
      // Adelanta la descarga de la voz de esta página y de la que sigue.
      L.precargar([p.v].concat(C.paginas[pag + 1] ? [C.paginas[pag + 1].v] : [C.pregunta.v]));
    } else {
      // Pregunta del final: se lee en voz alta (aquí se trata de entender, no de descifrar).
      pc.raiz.classList.add('pregunta');
      ponerTexto(C.pregunta);
      numero.textContent = '?';
      anterior.style.visibility = 'visible';
      escuchar.style.display = 'none';
      siguiente.style.display = 'none';
      var correcta = C.pregunta.o[0];
      var primero = null;
      mezclar(C.pregunta.o).forEach(function (k) {
        var b = el('button', 'opcion');
        b.appendChild(imagen(k));
        L.prepararFoco(b, function () {
          if (b.classList.contains('bien') || b.classList.contains('mal')) return;
          if (k === correcta) {
            b.classList.add('bien');
            if (window.SON) SON.efecto.bien();
            L.lluvia();
            var nuevo = !EST.leidos[C.k];
            EST.leidos[C.k] = true;
            L.guardar();
            var c = C;
            if (L.darBloques) L.darBloques(3, 3);   // premio para «Mi mundo»
            L.hablar([L.felicitar(), 'f_leido', 'f_bloques'], function () {
              if (C === c && L.pantalla().id === 'cuento') abrirLista();
            });
            if (nuevo) marcarLeidos();
          } else {
            b.classList.add('mal');
            if (window.SON) SON.efecto.mal();
            L.hablar([L.animar()]);
          }
        });
        opciones.appendChild(b);
        if (!primero) primero = b;
      });
      L.enfocar(primero, true);
      L.hablar([C.pregunta.v]);
    }
  }
  L.prepararFoco(anterior, function () { if (pag > 0) { pag--; pintar(); } });
  L.prepararFoco(siguiente, function () { pag++; pintar(); });
  L.prepararFoco(escuchar, function () { if (pag < C.paginas.length) leer(C.paginas[pag]); });
  L.ATRAS.cuento = function () { quitarMarcas(); abrirLista(); };

  function abrirCuento(c) {
    C = c;
    pag = 0;
    pc.titulo.textContent = c.t;
    L.mostrar('cuento', siguiente);
    pintar();
  }

  /* ---------- Menús ---------- */
  var listaCuentos = L.categoria('cuentos', 'Cuentos');
  listaCuentos.padre = 'yaleo';
  var COLORES = ['#c8e6c9', '#bbdefb', '#ffcdd2', '#fff9c4', '#e1bee7', '#ffe0b2'];
  D.cuentos.forEach(function (c, i) {
    L.agregarJuego('cuentos', { id: c.k, nombre: c.t, img: c.d, color: COLORES[i % COLORES.length], abrir: function () { abrirCuento(c); } });
  });
  function marcarLeidos() {
    listaCuentos.items.forEach(function (it) { it.nota = EST.leidos[it.id] ? '★ Leído' : ''; });
  }
  marcarLeidos();
  function abrirLista() { L.abrirMenu('cuentos'); }

  L.categoria('yaleo', 'Ya leo');
  L.agregarJuego('yaleo', { id: 'frases', nombre: 'Frases', icono: 'el sol', tinta: '#00838f', color: '#b2ebf2', ronda: true });
  L.agregarJuego('yaleo', { id: 'escribe', nombre: 'Escribe', img: 'lapiz', color: '#fff9c4', ronda: true });
  L.agregarJuego('yaleo', { id: 'cuentos', nombre: 'Cuentos', img: '_libros', color: '#ffccbc', nota: D.cuentos.length + ' cuentos', abrir: abrirLista });
})();
