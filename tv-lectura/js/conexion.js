/* Jugar desde el celular, como los juegos de Netflix.
 *
 * La TV muestra un código QR. El celular lo escanea, abre control.html y se conecta
 * con la TV por WebRTC (PeerJS). El servidor público de PeerJS solo sirve para que
 * se encuentren; después los mensajes van directo por la red de la casa.
 *
 * La TV manda al celular los botones de la pantalla actual (dibujos, sílabas, menús)
 * y el celular manda qué botón se tocó. Si no hay internet o el navegador no tiene
 * WebRTC, el QR no aparece y se sigue jugando con el control remoto.
 */
(function () {
  'use strict';

  // Dirección pública del control (cuando la app está instalada en la TV como .ipk).
  var URL_CONTROL = 'https://jozmdz.github.io/Apps/control.html';

  var caja = document.getElementById('celular');
  if (!window.Peer || !window.RTCPeerConnection || !window.LEO || !caja) return;

  function param(nombre) {
    var m = new RegExp('[?&]' + nombre + '=([^&]+)').exec(location.search);
    return m ? decodeURIComponent(m[1]) : null;
  }
  // ?senal=host:puerto usa otro servidor PeerJS (para pruebas).
  var senal = param('senal');
  function opcionesPeer() {
    if (!senal) return { debug: 0 };
    var hp = senal.split(':');
    return { host: hp[0], port: +hp[1] || 9000, path: '/', secure: location.protocol === 'https:', debug: 0 };
  }

  function nuevaSala() {
    var letras = 'abcdefghjkmnpqrstuvwxyz23456789', s = '';
    for (var i = 0; i < 6; i++) s += letras.charAt(Math.floor(Math.random() * letras.length));
    return s;
  }
  var sala = null;
  try { sala = localStorage.getItem('leoyjuego-sala'); } catch (e) { /* nada */ }
  if (!sala) {
    sala = nuevaSala();
    try { localStorage.setItem('leoyjuego-sala', sala); } catch (e) { /* nada */ }
  }

  function direccionControl() {
    var base = /^https?:$/.test(location.protocol)
      ? location.href.replace(/[?#].*$/, '').replace(/[^\/]*$/, '') + 'control.html'
      : URL_CONTROL;
    return base + '?sala=' + sala + (senal ? '&senal=' + encodeURIComponent(senal) : '');
  }

  function dibujarQR() {
    var q = qrcode(0, 'M');
    q.addData(direccionControl());
    q.make();
    document.getElementById('celular-qr').innerHTML = q.createSvgTag({ cellSize: 6, margin: 2, scalable: true });
  }

  var peer = null;
  var conexiones = [];

  function estado(texto, conectado) {
    caja.className = conectado ? 'conectado' : 'listo';
    document.getElementById('celular-texto').textContent = texto;
  }

  function iniciar() {
    peer = new Peer('leoyjuego-' + sala, opcionesPeer());
    peer.on('open', function () {
      dibujarQR();
      estado('Juega con el celular', false);
    });
    peer.on('connection', function (c) {
      c.on('open', function () {
        conexiones.push(c);
        ultimo = '';
        estado('Celular conectado', true);
        enviar(true);
      });
      c.on('data', recibir);
      c.on('close', function () {
        conexiones = conexiones.filter(function (x) { return x !== c; });
        if (!conexiones.length) estado('Juega con el celular', false);
      });
    });
    peer.on('error', function (err) {
      // El nombre de la sala sigue ocupado (la TV se reinició): reintentar en un rato.
      if (err && err.type === 'unavailable-id') { reintentar(15000); return; }
      if (err && (err.type === 'network' || err.type === 'server-error' || err.type === 'socket-error')) reintentar(20000);
    });
    peer.on('disconnected', function () {
      setTimeout(function () { if (peer && !peer.destroyed && peer.disconnected) peer.reconnect(); }, 3000);
    });
  }
  var esperando = false;
  function reintentar(ms) {
    if (esperando) return;
    esperando = true;
    try { peer.destroy(); } catch (e) { /* nada */ }
    setTimeout(function () { esperando = false; iniciar(); }, ms);
  }

  /* ---------- Lo que se manda al celular ---------- */
  var NAVEGACION = { volver: 'atras', 'album-volver': 'atras', 'papas-volver': 'atras', repetir: 'repetir' };

  function describir(e, i) {
    var d = { n: i };
    var nombre = e.querySelector('.t-nombre');
    var im = e.querySelector('img');
    if (NAVEGACION[e.id]) d.nav = NAVEGACION[e.id];
    else if (nombre) { d.texto = nombre.textContent; if (im) d.img = im.getAttribute('src'); }
    else if (e.classList.contains('opcion') || e.classList.contains('estampa')) {
      if (im) d.img = im.getAttribute('src'); else d.texto = e.textContent;
      d.grande = true;
    } else {
      d.texto = e.textContent.replace(/\s+/g, ' ').trim();
      if (im && !e.classList.contains('altavoz-grande')) d.icono = im.getAttribute('src');
      if (e.classList.contains('altavoz-grande')) d.nav = 'escuchar';
    }
    ['bien', 'mal', 'usada', 'bloqueada'].forEach(function (c) { if (e.classList.contains(c)) d[c] = true; });
    if (e.classList.contains('tarjeta')) d.tarjeta = e.getAttribute('data-juego');
    if (e === window.LEO.enfocado()) d.foco = true;
    return d;
  }

  var focosActuales = [];
  var ultimo = '';
  function enviar(forzar) {
    if (!conexiones.length) return;
    var p = window.LEO.pantalla();
    focosActuales = window.LEO.focos();
    var datos = { t: 'pantalla', id: p ? p.id : '', items: focosActuales.map(describir) };
    if (p && p.id === 'fin') datos.titulo = document.getElementById('fin-mensaje').textContent || '¡Terminaste!';
    var json = JSON.stringify(datos);
    if (json === ultimo && !forzar) return;
    ultimo = json;
    conexiones.forEach(function (c) { try { c.send(json); } catch (e) { /* nada */ } });
  }
  setInterval(enviar, 250);

  function recibir(msg) {
    var m;
    try { m = typeof msg === 'string' ? JSON.parse(msg) : msg; } catch (e) { return; }
    if (!m) return;
    if (m.t === 'elegir') {
      var e = focosActuales[m.n];
      if (e && e.offsetParent !== null) window.LEO.elegir(e);
    } else if (m.t === 'atras') window.LEO.atras();
    else if (m.t === 'repetir') window.LEO.repetir();
    else if (m.t === 'mover') window.LEO.mover(m.dx || 0, m.dy || 0);
    setTimeout(function () { enviar(); }, 60);
  }

  iniciar();
})();
