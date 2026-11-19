/* «Mi mundo» y el tema de bloques.
 *  - Al terminar cada ronda gana tantos bloques como estrellas (y 3 por cada cuento leído).
 *  - En «Mi mundo» los pone en una cuadrícula de 10 x 6: se elige un bloque abajo y con OK
 *    (o tocándolo en el celular) se pone; si ya hay uno, se quita y regresa a su inventario.
 *  - Papás → Tema: bloques o clásico.
 * Los dibujos de los bloques salen de herramientas/bloques.py. */
(function () {
  'use strict';
  var L = window.LEO, $ = L.$, el = L.el, EST = L.EST;
  var ANCHO = 10, ALTO = 6;
  var TIPOS = ['pasto', 'tierra', 'piedra', 'tablas', 'tronco', 'hojas', 'arena', 'agua', 'vidrio',
    'ladrillo', 'nieve', 'calabaza', 'oro', 'gema'];
  // Qué tan seguido sale cada bloque de premio; oro y gema solo con 5 estrellas.
  var PESOS = { pasto: 3, tierra: 4, piedra: 4, tablas: 4, tronco: 3, hojas: 3, arena: 3, agua: 2, vidrio: 2,
    ladrillo: 2, nieve: 2, calabaza: 1, oro: 1, gema: 1 };

  function src(t) { return 'img/b_' + t + '.svg'; }
  function nuevoMundo() {
    var m = [];
    for (var i = 0; i < ANCHO * ALTO; i++) m.push(i >= ANCHO * (ALTO - 1) ? 'pasto' : '');
    return m;
  }
  if (!EST.mundo || EST.mundo.length !== ANCHO * ALTO) EST.mundo = nuevoMundo();
  if (!EST.inv) EST.inv = { tierra: 6, piedra: 6, tablas: 6, tronco: 4, hojas: 4, vidrio: 3, ladrillo: 3 };
  if (!EST.elegido) EST.elegido = 'tablas';

  /* ---------- Tema ---------- */
  function aplicarTema() {
    var bloques = EST.tema !== 'clasico';
    document.documentElement.className = bloques ? 'tema-bloques' : '';
    if (window.SON && SON.estilo) SON.estilo(bloques ? '8bit' : 'suave');
  }
  aplicarTema();
  var etiquetasAntes = L.etiquetasPapas;
  L.etiquetasPapas = function () {
    if (etiquetasAntes) etiquetasAntes();
    $('aj-tema').textContent = 'Tema: ' + (EST.tema === 'clasico' ? 'clásico' : 'bloques');
  };
  L.prepararFoco($('aj-tema'), function () {
    EST.tema = EST.tema === 'clasico' ? 'bloques' : 'clasico';
    L.guardar();
    aplicarTema();
    L.etiquetasPapas();
  });

  /* ---------- Premios ---------- */
  function sorteo(estrellas) {
    var lista = TIPOS.filter(function (t) { return estrellas >= 5 || (t !== 'oro' && t !== 'gema'); });
    var total = lista.reduce(function (s, t) { return s + PESOS[t]; }, 0);
    var x = Math.random() * total;
    for (var i = 0; i < lista.length; i++) { x -= PESOS[lista[i]]; if (x < 0) return lista[i]; }
    return lista[0];
  }
  /* Da n bloques (de un tipo al azar) y devuelve el tipo. */
  L.darBloques = function (n, estrellas) {
    var tipo = sorteo(estrellas || n);
    EST.inv[tipo] = (EST.inv[tipo] || 0) + n;
    L.guardar();
    pintarInventario();
    return tipo;
  };
  /* Lo llama app.js al terminar una ronda: muestra los bloques ganados en la pantalla final. */
  L.premio = function (estrellas, caja, voz) {
    if (estrellas < 1) return;
    var tipo = L.darBloques(estrellas, estrellas);
    var c = el('div', 'premio-bloques');
    var i = el('img');
    i.src = src(tipo);
    c.appendChild(i);
    c.appendChild(el('b', '', '+' + estrellas));
    caja.appendChild(c);
    voz.push('f_bloques');
  };

  /* ---------- Pantalla ---------- */
  var p = L.pantallaNueva('mundo', 'Mi mundo');
  p.raiz.setAttribute('data-cel', 'mundo');
  var rejilla = el('div', 'mundo-rejilla');
  var inventario = el('div', 'mundo-inventario');
  p.cuerpo.appendChild(rejilla);
  p.cuerpo.appendChild(inventario);
  L.MUSICA.mundo = 'alegre';

  var celdas = [], ranuras = {};
  function pintarCelda(i) {
    var t = EST.mundo[i], c = celdas[i];
    if (t) c.classList.add('llena'); else c.classList.remove('llena');
    c.style.backgroundImage = t ? 'url(' + src(t) + ')' : '';
    c.setAttribute('data-bloque', t ? src(t) : '');   // para dibujarla igual en el celular
  }
  function pintarInventario() {
    TIPOS.forEach(function (t) {
      var r = ranuras[t];
      if (!r) return;
      var n = EST.inv[t] || 0;
      r.lastChild.textContent = n;
      r.classList.toggle('vacia', n === 0);
      r.classList.toggle('elegida', t === EST.elegido);
    });
    // Dirección completa: si fuera relativa, el CSS la buscaría dentro de css/.
    var base = location.href.replace(/[?#].*$/, '').replace(/[^\/]*$/, '');
    rejilla.classList.toggle('agotado', !((EST.inv[EST.elegido] || 0) > 0));
    rejilla.style.setProperty('--fantasma', 'url("' + base + src(EST.elegido) + '")');
  }
  function migas(celda, tipo) {
    var m = el('div', 'migas');
    m.style.left = celda.offsetLeft + 'px';
    m.style.top = celda.offsetTop + 'px';
    var piezas = [];
    for (var k = 0; k < 6; k++) {
      var i = el('i');
      i.style.backgroundImage = 'url(' + src(tipo) + ')';
      i.style.backgroundPosition = (k * 20) + '% ' + (k * 13 % 100) + '%';
      m.appendChild(i);
      piezas.push(i);
    }
    rejilla.appendChild(m);
    setTimeout(function () {
      piezas.forEach(function (i, k) {
        var a = k / piezas.length * Math.PI * 2;
        i.style.transform = 'translate(' + Math.round(Math.cos(a) * 80) + 'px,' + Math.round(Math.sin(a) * 80 + 40) + 'px)';
        i.style.opacity = '0';
      });
    }, 20);
    setTimeout(function () { if (m.parentNode) m.parentNode.removeChild(m); }, 600);
  }
  function tocar(i) {
    var c = celdas[i], t = EST.mundo[i];
    if (t) {                                         // ya hay bloque: se quita
      EST.mundo[i] = '';
      EST.inv[t] = (EST.inv[t] || 0) + 1;
      if (window.SON) SON.efecto.romper();
      migas(c, t);
    } else if ((EST.inv[EST.elegido] || 0) > 0) {   // se pone el bloque elegido
      EST.mundo[i] = EST.elegido;
      EST.inv[EST.elegido]--;
      if (window.SON) SON.efecto.poner();
    } else {                                         // no le quedan de esos
      if (window.SON) SON.efecto.mal();
      c.classList.remove('golpe');
      void c.offsetWidth;
      c.classList.add('golpe');
      L.hablar(['f_sinbloques']);
      return;
    }
    L.guardar();
    pintarCelda(i);
    pintarInventario();
  }
  for (var i = 0; i < ANCHO * ALTO; i++) {
    (function (i) {
      var c = el('button', 'celda');
      c.sinSonido = true;
      L.prepararFoco(c, function () { tocar(i); });
      rejilla.appendChild(c);
      celdas.push(c);
      pintarCelda(i);
    })(i);
  }
  TIPOS.forEach(function (t) {
    var r = el('button', 'ranura');
    r.sinSonido = true;
    var im = el('img');
    im.src = src(t);
    r.appendChild(im);
    r.appendChild(el('b', '', ''));
    L.prepararFoco(r, function () {
      EST.elegido = t;
      L.guardar();
      if (window.SON) SON.efecto.mover();
      pintarInventario();
    });
    inventario.appendChild(r);
    ranuras[t] = r;
  });
  pintarInventario();

  L.ATRAS.mundo = function () { L.irInicio(); };
  function abrir() {
    pintarInventario();
    L.mostrar('mundo', celdas[ANCHO * (ALTO - 2)]);
    L.hablar(['f_mundo']);
  }
  L.abrirMundo = abrir;
  L.prepararFoco($('ir-mundo'), abrir);
})();
