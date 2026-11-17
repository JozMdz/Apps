/* El celular como control remoto de la TV (ver js/conexion.js, lado de la TV).
 * Dos modos:
 *  - Flechas: cruceta y OK, igual que el control de la tele (se puede dejar presionado).
 *  - Puntero: se desliza el dedo y una flecha se mueve en la TV; un toque elige. */
(function () {
  'use strict';

  function param(nombre) {
    var m = new RegExp('[?&]' + nombre + '=([^&]+)').exec(location.search);
    return m ? decodeURIComponent(m[1]) : null;
  }
  var sala = param('sala');
  var senal = param('senal');
  var $ = function (id) { return document.getElementById(id); };
  var conexion = null;
  var peer = null;

  function estado(t) { $('estado').textContent = t; }
  function aviso(t) { $('aviso').textContent = t || ''; }

  /* ---------- Modo (se recuerda en este celular) ---------- */
  function modo(m) {
    document.body.className = m === 'puntero' ? 'puntero' : '';
    $('modo-flechas').className = m === 'puntero' ? '' : 'activo';
    $('modo-puntero').className = m === 'puntero' ? 'activo' : '';
    try { localStorage.setItem('leoyjuego-modo', m); } catch (e) { /* nada */ }
  }
  var guardado = 'flechas';
  try { guardado = localStorage.getItem('leoyjuego-modo') || 'flechas'; } catch (e) { /* nada */ }
  modo(guardado);
  $('modo-flechas').onclick = function () { modo('flechas'); };
  $('modo-puntero').onclick = function () { modo('puntero'); };

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

  function mandar(m, vibrar) {
    if (conexion && conexion.open) conexion.send(JSON.stringify(m));
    if (vibrar !== false && navigator.vibrate) navigator.vibrate(20);
  }

  /* Toque inmediato (sin esperar al «click»). f se llama al tocar; suelta al levantar el dedo. */
  function alTocar(b, f, suelta) {
    var tocado = 0;
    b.addEventListener('touchstart', function (ev) {
      ev.preventDefault();
      tocado = Date.now();
      b.classList.add('presionado');
      f();
    }, { passive: false });
    function fin() { b.classList.remove('presionado'); if (suelta) suelta(); }
    b.addEventListener('touchend', fin);
    b.addEventListener('touchcancel', fin);
    b.addEventListener('mousedown', function () { if (Date.now() - tocado > 600) { f(); } });
    b.addEventListener('mouseup', function () { if (Date.now() - tocado > 600) fin(); });
    b.addEventListener('mouseleave', function () { if (Date.now() - tocado > 600) fin(); });
  }

  /* ---------- Botones de arriba ---------- */
  alTocar($('atras'), function () { mandar({ t: 'atras' }); });
  alTocar($('repetir'), function () { mandar({ t: 'repetir' }); });

  /* ---------- Cruceta: dejar presionada una flecha la repite ---------- */
  [].forEach.call(document.querySelectorAll('#cruceta button'), function (b) {
    var k = +b.getAttribute('data-k');
    var espera = null, repite = null;
    function parar() { clearTimeout(espera); clearInterval(repite); }
    alTocar(b, function () {
      parar();
      mandar({ t: 'tecla', k: k });
      if (k === 13) return;
      espera = setTimeout(function () {
        repite = setInterval(function () { mandar({ t: 'tecla', k: k }, false); }, 160);
      }, 420);
    }, parar);
  });

  /* ---------- Puntero ---------- */
  var tactil = $('tactil'), dedo = $('dedo');
  var SENSIBILIDAD = 3.2;
  var inicio = null, ultimo = null, recorrido = 0, pendX = 0, pendY = 0, programado = false;

  function enviarMovimiento() {
    programado = false;
    if (!pendX && !pendY) return;
    mandar({ t: 'puntero', dx: Math.round(pendX), dy: Math.round(pendY) }, false);
    pendX = 0; pendY = 0;
  }
  function punto(ev) {
    var t = ev.touches ? ev.touches[0] : ev;
    return { x: t.clientX, y: t.clientY };
  }
  function mostrarDedo(p) {
    var r = tactil.getBoundingClientRect();
    dedo.style.display = 'block';
    dedo.style.left = (p.x - r.left) + 'px';
    dedo.style.top = (p.y - r.top) + 'px';
  }
  function empieza(ev) {
    ev.preventDefault();
    inicio = { p: punto(ev), t: Date.now() };
    ultimo = inicio.p;
    recorrido = 0;
    tactil.classList.add('tocando');
    mostrarDedo(ultimo);
  }
  function mueve(ev) {
    if (!inicio) return;
    ev.preventDefault();
    var p = punto(ev);
    var dx = p.x - ultimo.x, dy = p.y - ultimo.y;
    ultimo = p;
    recorrido += Math.abs(dx) + Math.abs(dy);
    // Un poco de aceleración: movimientos rápidos llegan más lejos.
    var vel = Math.min(2.2, 1 + (Math.abs(dx) + Math.abs(dy)) / 30);
    pendX += dx * SENSIBILIDAD * vel;
    pendY += dy * SENSIBILIDAD * vel;
    mostrarDedo(p);
    if (!programado) { programado = true; requestAnimationFrame(enviarMovimiento); }
  }
  function termina() {
    if (!inicio) return;
    var toque = recorrido < 12 && Date.now() - inicio.t < 350;
    inicio = null;
    tactil.classList.remove('tocando');
    dedo.style.display = 'none';
    enviarMovimiento();
    if (toque) mandar({ t: 'clic' });
  }
  tactil.addEventListener('touchstart', empieza, { passive: false });
  tactil.addEventListener('touchmove', mueve, { passive: false });
  tactil.addEventListener('touchend', termina);
  tactil.addEventListener('touchcancel', termina);
  tactil.addEventListener('mousedown', empieza);
  tactil.addEventListener('mousemove', mueve);
  tactil.addEventListener('mouseup', termina);
  alTocar($('ok-puntero'), function () { mandar({ t: 'clic' }); });

  /* ---------- Lo que manda la TV: en qué pantalla está ---------- */
  function pintar(datos) {
    if (!datos || datos.t !== 'pantalla') return;
    $('titulo').textContent = datos.titulo || '';
    $('repetir').className = 'redondo' + (datos.repetir ? '' : ' apagado');
  }

  /* ---------- Conexión con la TV ---------- */
  function opcionesPeer() {
    if (!senal) return { debug: 0 };
    var hp = senal.split(':');
    return { host: hp[0], port: +hp[1] || 9000, path: '/', secure: location.protocol === 'https:', debug: 0 };
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
      estado('Conectado a la TV');
      aviso('');
    });
    c.on('data', function (d) {
      try { pintar(typeof d === 'string' ? JSON.parse(d) : d); } catch (e) { /* nada */ }
    });
    c.on('close', function () {
      if (conexion === c) conexion = null;
      $('titulo').textContent = '';
      reintentar();
    });
    c.on('error', reintentar);
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
