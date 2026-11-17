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
  /* ---------- Lo que se manda al celular: solo en qué pantalla está ---------- */
  var ultimo = '';
  function enviar(forzar) {
    if (!conexiones.length) return;
    var p = window.LEO.pantalla();
    var datos = { t: 'pantalla', id: p ? p.id : '', titulo: '' };
    if (p) {
      var tit = p.querySelector('[data-cel-titulo]') || p.querySelector('.barra h2');
      datos.titulo = p.id === 'inicio' ? 'Inicio' : p.id === 'juego' ? '¡Mira la tele!'
        : p.id === 'fin' ? '¡Terminaste!' : tit ? tit.textContent : '';
      datos.repetir = p.id === 'juego';
    }
    var json = JSON.stringify(datos);
    if (json === ultimo && !forzar) return;
    ultimo = json;
    conexiones.forEach(function (c) { try { c.send(json); } catch (e) { /* nada */ } });
  }
  setInterval(enviar, 300);

  /* ---------- Puntero (flecha en la TV que se mueve con el dedo) ---------- */
  var esc = document.getElementById('escenario');
  var flecha = document.createElement('div');
  flecha.id = 'puntero';
  flecha.innerHTML = '<svg viewBox="0 0 32 32" width="72" height="72"><path d="M5 3 L5 26 L11 20 L15 29 L19 27 L15 18 L24 18 Z" fill="#fff" stroke="#263238" stroke-width="2.2" stroke-linejoin="round"/></svg>';
  esc.appendChild(flecha);
  var px = 960, py = 540, ocultar = null;

  function mostrarFlecha() {
    flecha.className = 'visible';
    flecha.style.left = px + 'px';
    flecha.style.top = py + 'px';
    clearTimeout(ocultar);
    ocultar = setTimeout(function () { flecha.className = ''; }, 6000);
  }
  // Botón que está debajo de la punta de la flecha.
  function bajoFlecha() {
    var r = esc.getBoundingClientRect();
    var s = r.width / 1920;
    var e = document.elementFromPoint(r.left + px * s, r.top + py * s);
    while (e && e !== esc && !(e.classList && e.classList.contains('foco'))) e = e.parentNode;
    if (!e || e === esc) return null;
    var p = window.LEO.pantalla();
    return p && p.contains(e) && !e.classList.contains('usada') ? e : null;
  }
  function moverFlecha(dx, dy) {
    px = Math.max(0, Math.min(1910, px + dx));
    py = Math.max(0, Math.min(1070, py + dy));
    mostrarFlecha();
    var e = bajoFlecha();
    if (e && e !== window.LEO.enfocado()) window.LEO.enfocar(e);
  }
  function clic() {
    mostrarFlecha();
    var e = bajoFlecha();
    if (e) window.LEO.elegir(e);
    else window.LEO.tecla(13);   // fuera de un botón: como OK (sirve para saltar)
  }

  function recibir(msg) {
    var m;
    try { m = typeof msg === 'string' ? JSON.parse(msg) : msg; } catch (e) { return; }
    if (!m) return;
    if (m.t === 'tecla') window.LEO.tecla(m.k);
    else if (m.t === 'puntero') moverFlecha(m.dx || 0, m.dy || 0);
    else if (m.t === 'clic') clic();
    else if (m.t === 'atras') window.LEO.atras();
    else if (m.t === 'repetir') window.LEO.repetir();
    setTimeout(function () { enviar(); }, 60);
  }

  iniciar();
})();
