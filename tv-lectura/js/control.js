/* Control desde el celular: muestra los botones de la pantalla de la TV y manda
 * a la TV el que se toca. Ver js/conexion.js (lado de la TV). */
(function () {
  'use strict';

  function param(nombre) {
    var m = new RegExp('[?&]' + nombre + '=([^&]+)').exec(location.search);
    return m ? decodeURIComponent(m[1]) : null;
  }
  var sala = param('sala');
  var senal = param('senal');
  var $ = function (id) { return document.getElementById(id); };
  var botones = $('botones');
  var conexion = null;
  var peer = null;

  function estado(t) { $('estado').textContent = t; }
  function aviso(t) { $('aviso').textContent = t || ''; }

  if (!sala) {
    estado('Falta el código');
    aviso('Escanea el código QR que aparece en la TV, en la pantalla de inicio de Leo y Juego.');
    return;
  }
  if (!window.Peer || !window.RTCPeerConnection) {
    estado('Este navegador no sirve');
    aviso('Abre el código con Chrome o Safari.');
    return;
  }

  function opcionesPeer() {
    if (!senal) return { debug: 0 };
    var hp = senal.split(':');
    return { host: hp[0], port: +hp[1] || 9000, path: '/', secure: location.protocol === 'https:', debug: 0 };
  }

  function mandar(m) {
    if (conexion && conexion.open) conexion.send(JSON.stringify(m));
    if (navigator.vibrate) navigator.vibrate(25);
  }

  var reintento = null;
  function reintentar() {
    if (reintento) return;
    estado('Buscando la TV…');
    reintento = setTimeout(function () { reintento = null; conectar(); }, 3000);
  }

  function conectar() {
    if (!peer || peer.destroyed) {
      peer = new Peer(opcionesPeer());
      peer.on('open', conectar);
      peer.on('error', function (e) {
        if (e && e.type === 'peer-unavailable') {
          aviso('Abre Leo y Juego en la TV y deja la pantalla de inicio con el código a la vista.');
        }
        reintentar();
      });
      peer.on('disconnected', function () { try { peer.reconnect(); } catch (e) { /* nada */ } });
      return;
    }
    if (!peer.open) return;
    var c = peer.connect('leoyjuego-' + sala, { reliable: true });
    c.on('open', function () {
      conexion = c;
      estado('¡Conectado a la TV!');
      aviso('');
    });
    c.on('data', function (d) {
      try { pintar(typeof d === 'string' ? JSON.parse(d) : d); } catch (e) { /* nada */ }
    });
    c.on('close', function () {
      if (conexion === c) conexion = null;
      botones.innerHTML = '';
      ultimaFirma = '';
      reintentar();
    });
    c.on('error', reintentar);
  }

  $('atras').onclick = function () { mandar({ t: 'atras' }); };
  $('repetir').onclick = function () { mandar({ t: 'repetir' }); };

  var NOMBRES = { inicio: 'Elige un juego', juego: '¡Mira la tele!', fin: '', album: 'Mis estampas', papas: 'Para papás' };
  var ultimaPantalla = '';
  var ultimaFirma = '';
  var ESTADOS = { foco: 1, bien: 1, mal: 1, usada: 1, bloqueada: 1 };

  function pintar(datos) {
    if (!datos || datos.t !== 'pantalla') return;
    var hayAtras = false, hayRepetir = false;
    var lista = datos.items.filter(function (it) {
      if (it.nav === 'atras') { hayAtras = true; return false; }
      if (it.nav === 'repetir') { hayRepetir = true; return false; }
      return true;
    });
    $('atras').className = hayAtras || datos.id === 'fin' ? '' : 'invisible';
    $('repetir').className = hayRepetir ? '' : 'invisible';
    $('titulo').textContent = datos.titulo || NOMBRES[datos.id] || '';
    botones.className = datos.id === 'album' ? 'album'
      : (datos.id === 'inicio' || datos.id === 'papas' || datos.id === 'fin') ? 'lista' : '';

    var nuevaPantalla = datos.id !== ultimaPantalla;
    ultimaPantalla = datos.id;
    // Si solo cambió el estado (foco, bien, mal), se actualizan las clases sin
    // rehacer los botones, para no perder un toque que esté en curso.
    var firma = datos.id + '|' + lista.map(function (it) { return it.n + (it.texto || '') + (it.img || '') + (it.nav || ''); }).join('|');
    if (firma === ultimaFirma && botones.children.length === lista.length) {
      lista.forEach(function (it, k) {
        var b = botones.children[k];
        var base = b.className.split(' ').filter(function (c) { return !ESTADOS[c]; });
        Object.keys(ESTADOS).forEach(function (c) { if (it[c]) base.push(c); });
        b.className = base.join(' ');
      });
      return;
    }
    ultimaFirma = firma;
    botones.innerHTML = '';
    lista.forEach(function (it) {
      var b = document.createElement('button');
      var clases = [];
      if (it.tarjeta) {
        clases.push('tarjeta', 't-' + it.tarjeta);
        if (it.img) { var i1 = document.createElement('img'); i1.src = it.img; b.appendChild(i1); }
        else { var abc = document.createElement('span'); abc.className = 'abc'; abc.textContent = it.tarjeta === 'letras' ? 'Aa' : 'ma'; b.appendChild(abc); }
        b.appendChild(document.createTextNode(it.texto));
      } else if (it.nav === 'escuchar') {
        clases.push('escuchar');
        var i2 = document.createElement('img'); i2.src = 'img/_altavoz.svg'; b.appendChild(i2);
        b.appendChild(document.createTextNode('Escuchar'));
      } else if (it.grande) {
        clases.push('grande');
        if (it.img) { var i3 = document.createElement('img'); i3.src = it.img; b.appendChild(i3); }
        else b.textContent = it.texto;
      } else {
        if (it.icono) { var i4 = document.createElement('img'); i4.src = it.icono; i4.className = 'icono'; b.appendChild(i4); }
        b.appendChild(document.createTextNode(it.texto || ''));
      }
      ['foco', 'bien', 'mal', 'usada', 'bloqueada'].forEach(function (c) { if (it[c]) clases.push(c); });
      b.className = clases.join(' ');
      b.onclick = function () { mandar({ t: 'elegir', n: it.n }); };
      botones.appendChild(b);
    });
    if (nuevaPantalla) document.querySelector('main').scrollTop = 0;
  }

  // Mantener la pantalla del celular encendida mientras juega (si el navegador lo permite).
  function despierto() {
    if (navigator.wakeLock && navigator.wakeLock.request) navigator.wakeLock.request('screen').catch(function () {});
  }
  document.addEventListener('visibilitychange', function () {
    if (!document.hidden) { despierto(); if (!conexion) conectar(); }
  });
  despierto();
  conectar();
})();
