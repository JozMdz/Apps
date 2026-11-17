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

  var ultimaPantalla = '';
  var ultimaFirma = '';
  var ESTADOS = ['foco', 'bien', 'mal', 'usada', 'bloqueada', 'guia', 'vista'];

  function imagen(src, clase) {
    var i = document.createElement('img');
    i.src = src;
    if (clase) i.className = clase;
    return i;
  }
  // Toque inmediato (para la batería y los minijuegos) sin esperar al «click».
  function alTocar(b, f) {
    var tocado = 0;
    b.addEventListener('touchstart', function (ev) { ev.preventDefault(); tocado = Date.now(); f(); }, { passive: false });
    b.addEventListener('click', function () { if (Date.now() - tocado > 600) f(); });
  }

  function crearBoton(it) {
    var b = document.createElement('button');
    var clases = [];
    if (it.tarjeta) {
      clases.push('tarjeta');
      if (it.img) b.appendChild(imagen(it.img));
      else { var abc = document.createElement('span'); abc.className = 'abc'; abc.textContent = it.abc; b.appendChild(abc); }
      b.appendChild(document.createTextNode(it.texto));
    } else if (it.nav === 'escuchar') {
      clases.push('escuchar');
      b.appendChild(imagen('img/_altavoz.svg'));
      b.appendChild(document.createTextNode('Escuchar'));
    } else if (it.grande) {
      clases.push('grande');
      if (it.circulo) {
        var c = document.createElement('span'); c.className = 'circulo'; c.style.background = it.color; b.appendChild(c);
      } else if (it.veces) {
        clases.push('varios');
        for (var k = 0; k < it.veces; k++) b.appendChild(imagen(it.img));
      } else if (it.img) b.appendChild(imagen(it.img));
      else { b.textContent = it.texto; if (it.pad) clases.push('pad'); else if (it.texto.length > 3) clases.push('largo'); }
    } else {
      if (it.texto && it.texto.length > 2) clases.push('ancho');
      if (it.icono) b.appendChild(imagen(it.icono, 'icono'));
      b.appendChild(document.createTextNode(it.texto || ''));
    }
    if (it.color && !it.circulo) b.style.background = it.color;
    b.setAttribute('data-base', clases.join(' '));
    alTocar(b, function () { mandar({ t: 'elegir', n: it.n }); });
    return b;
  }
  function estados(b, it) {
    var clases = [b.getAttribute('data-base')];
    ESTADOS.forEach(function (c) { if (it[c]) clases.push(c); });
    b.className = clases.join(' ');
  }

  function pintar(datos) {
    if (!datos || datos.t !== 'pantalla') return;
    var hayAtras = false, hayRepetir = false;
    var lista = datos.items.filter(function (it) {
      if (it.nav === 'atras') { hayAtras = true; return false; }
      if (it.nav === 'repetir') { hayRepetir = true; return false; }
      return true;
    });
    $('atras').className = hayAtras || datos.id === 'fin' || datos.id === 'minifin' ? '' : 'invisible';
    $('repetir').className = hayRepetir ? '' : 'invisible';
    $('titulo').textContent = datos.titulo || '';
    botones.className = datos.diseno || 'c2';

    // Si solo cambió el estado (foco, bien, mal), se actualizan las clases sin
    // rehacer los botones, para no perder un toque que esté en curso.
    var firma = datos.id + '|' + lista.map(function (it) {
      return it.n + (it.texto || '') + (it.img || '') + (it.nav || '') + (it.veces || '') + (it.color || '');
    }).join('|');
    if (firma === ultimaFirma && botones.children.length === lista.length) {
      lista.forEach(function (it, k) { estados(botones.children[k], it); });
      return;
    }
    ultimaFirma = firma;
    botones.innerHTML = '';
    lista.forEach(function (it) {
      var b = crearBoton(it);
      estados(b, it);
      botones.appendChild(b);
    });
    if (datos.id !== ultimaPantalla) document.querySelector('main').scrollTop = 0;
    ultimaPantalla = datos.id;
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
