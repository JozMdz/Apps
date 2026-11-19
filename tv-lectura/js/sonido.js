/* Todo el sonido de Leo y Juego con Web Audio:
 *
 *  - Voz: los audios de audio/*.mp3 se decodifican y suenan por Web Audio. En la TV, cambiar
 *    el archivo de un <audio> para cada sílaba hacía que se perdiera el principio de cada clip
 *    (solo se oían pedacitos). Con Web Audio la salida de sonido queda abierta y cada clip suena
 *    completo y al instante.
 *  - Música de fondo hecha al momento, que baja sola mientras habla la voz.
 *  - Efectos: interfaz, aciertos, batería, piano, guitarra y minijuegos.
 *
 * Si la TV no tiene Web Audio, la voz usa <audio> (ver hablar() en app.js) y lo demás no suena,
 * sin fallar. Escrito en ES5 para los navegadores de las TV LG.
 */
var SON = (function () {
  'use strict';
  var AC = window.AudioContext || window.webkitAudioContext;
  var ctx = null, salida = null, busVoz = null, busMusica = null, busEfectos = null;
  var ruidoBuf = null, cuerdas = {}, curvaSat = null;

  function montar(contexto) {
    ctx = contexto;
    ruidoBuf = null; cuerdas = {};
    salida = ctx.createGain(); salida.gain.value = 0.9; salida.connect(ctx.destination);
    busVoz = ctx.createGain(); busVoz.gain.value = 1; busVoz.connect(salida);
    busMusica = ctx.createGain(); busMusica.gain.value = 0; busMusica.connect(salida);
    busEfectos = ctx.createGain(); busEfectos.gain.value = 1; busEfectos.connect(salida);
  }
  // Devuelve el contexto (creándolo y despertándolo si hace falta) o null si no hay Web Audio.
  function c() {
    if (!ctx && AC) { try { montar(new AC()); } catch (e) { ctx = null; } }
    if (!ctx) return null;
    if (ctx.state === 'suspended' && ctx.resume) { try { ctx.resume(); } catch (e) { /* nada */ } }
    return ctx;
  }
  // ¿Ya puede sonar? (Las TV muy viejas no tienen ctx.state: se toma como que sí.)
  function corriendo() { return !!ctx && (ctx.state === undefined || ctx.state === 'running'); }

  /* ---------- Voz ---------- */
  var MAX_GUARDADOS = 120;
  var guardados = {}, orden = [], cargando = {}, fallidos = {};

  function guardar(url, buf) {
    guardados[url] = buf;
    orden.push(url);
    while (orden.length > MAX_GUARDADOS) delete guardados[orden.shift()];
  }
  /* Descarga y decodifica un audio; listo(buffer) o listo(null) si no se pudo. */
  function cargar(url, listo) {
    listo = listo || function () {};
    if (!c() || fallidos[url]) { listo(null); return; }
    if (guardados[url]) { listo(guardados[url]); return; }
    if (cargando[url]) { cargando[url].push(listo); return; }
    cargando[url] = [listo];
    var hecho = false;
    function fin(buf) {
      if (hecho) return;
      hecho = true;
      var esperan = cargando[url];
      delete cargando[url];
      if (buf) guardar(url, buf); else fallidos[url] = true;
      for (var i = 0; i < esperan.length; i++) esperan[i](buf);
    }
    try {
      var x = new XMLHttpRequest();
      x.open('GET', url, true);
      x.responseType = 'arraybuffer';
      x.onload = function () {
        if (!x.response || !x.response.byteLength) { fin(null); return; }
        try {
          var p = ctx.decodeAudioData(x.response, fin, function () { fin(null); });
          if (p && p.then) p.then(null, function () { fin(null); });
        } catch (e) { fin(null); }
      };
      x.onerror = function () { fin(null); };
      x.send();
    } catch (e) { fin(null); }
  }

  var vozActual = null;
  function decir(buf, alTerminar) {
    callarVoz();
    if (!c()) { if (alTerminar) alTerminar(); return; }
    var s = ctx.createBufferSource();
    s.buffer = buf;
    s.connect(busVoz);
    var hecho = false;
    // Por si el audio no avisa que terminó.
    var limite = setTimeout(fin, (buf.duration + 1) * 1000);
    function fin() {
      if (hecho) return;
      hecho = true;
      clearTimeout(limite);
      if (vozActual === s) { vozActual = null; agachar(false); }
      if (alTerminar) alTerminar();
    }
    s.onended = fin;
    s.cortar = function () { hecho = true; clearTimeout(limite); };
    vozActual = s;
    agachar(true);
    s.start(ctx.currentTime + 0.02);
  }
  function callarVoz() {
    if (!vozActual) return;
    var s = vozActual;
    vozActual = null;
    s.cortar();
    try { s.stop(); } catch (e) { /* ya había terminado */ }
    agachar(false);
  }

  /* ---------- Piezas para hacer sonidos ---------- */
  function ruido() {
    if (!ruidoBuf) {
      ruidoBuf = ctx.createBuffer(1, ctx.sampleRate, ctx.sampleRate);
      var d = ruidoBuf.getChannelData(0);
      for (var i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    }
    return ruidoBuf;
  }
  // Envolvente: sube rápido a vol y baja hasta casi cero en dur segundos.
  function env(vol, dur, t, destino, ataque) {
    var g = ctx.createGain();
    g.gain.setValueAtTime(0.0001, t);
    g.gain.linearRampToValueAtTime(vol, t + (ataque || 0.005));
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    g.connect(destino || busEfectos);
    return g;
  }
  function osc(tipo, f0, f1, dur, t, destino) {
    var o = ctx.createOscillator();
    o.type = tipo;
    o.frequency.setValueAtTime(f0, t);
    if (f1 && f1 !== f0) o.frequency.exponentialRampToValueAtTime(f1, t + dur);
    o.connect(destino);
    o.start(t);
    o.stop(t + dur + 0.05);
    return o;
  }
  function tono(tipo, f0, f1, vol, dur, cuando, destino) {
    if (!c()) return;
    var t = ctx.currentTime + (cuando || 0);
    osc(tipo, f0, f1, dur, t, env(vol, dur, t, destino));
  }
  function soplo(filtro, frec, vol, dur, cuando, destino, q) {
    if (!c()) return;
    var t = ctx.currentTime + (cuando || 0);
    var s = ctx.createBufferSource();
    s.buffer = ruido();
    var f = ctx.createBiquadFilter();
    f.type = filtro;
    f.frequency.value = frec;
    if (q) f.Q.value = q;
    s.connect(f);
    f.connect(env(vol, dur, t, destino));
    s.start(t, Math.random() * 0.5);
    s.stop(t + dur + 0.05);
  }
  // Saturación suave: agrega armónicos para que los tonos graves se oigan en las bocinas de la TV.
  function saturador() {
    if (!curvaSat) {
      var n = 1024;
      curvaSat = new Float32Array(n);
      for (var i = 0; i < n; i++) {
        var x = i * 2 / (n - 1) - 1;
        curvaSat[i] = Math.tanh ? Math.tanh(3 * x) : Math.max(-1, Math.min(1, 3 * x));
      }
    }
    var w = ctx.createWaveShaper();
    w.curve = curvaSat;
    return w;
  }
  // Cuerpo de un tambor: tono que baja de f0 a f1, saturado (vol 0 a 1).
  function parche(f0, f1, vol, dur) {
    if (!c()) return;
    var t = ctx.currentTime;
    var sat = saturador();
    var pre = ctx.createGain();
    pre.gain.value = 1.6;
    pre.connect(sat);
    sat.connect(env(vol, dur, t));
    osc('sine', f0, f1, dur, t, pre);
  }

  /* ---------- Batería ----------
   * Las bocinas de la TV casi no dan sonidos de menos de ~150 Hz; antes el bombo y el tom
   * grave eran solo eso y no se oían. Ahora cada tambor tiene golpe (ataque) y armónicos. */
  var bateria = {
    bombo: function () {
      parche(190, 55, 0.9, 0.45);
      tono('sine', 380, 150, 0.8, 0.08);             // el «toc» del mazo, se oye en bocinas chicas
      soplo('bandpass', 2600, 0.5, 0.025, 0, null, 1.2);
    },
    tarola: function () {
      soplo('highpass', 1800, 0.75, 0.22);
      soplo('bandpass', 4200, 0.35, 0.12, 0, null, 0.8);
      parche(330, 190, 0.5, 0.12);
    },
    hihat: function () {
      soplo('highpass', 6500, 0.95, 0.09);
      soplo('bandpass', 9500, 0.6, 0.06, 0, null, 1);
      [317, 465, 587, 845].forEach(function (f) { tono('square', f * 4, f * 4, 0.035, 0.05); });
    },
    tom: function () {
      parche(330, 200, 0.85, 0.35);
      soplo('bandpass', 1500, 0.3, 0.03, 0, null, 1);
    },
    tomgrave: function () {
      parche(220, 120, 0.9, 0.5);
      tono('sine', 440, 250, 0.4, 0.06);
      soplo('bandpass', 1200, 0.3, 0.03, 0, null, 1);
    },
    platillo: function () {
      soplo('highpass', 5000, 0.6, 1.3);
      soplo('bandpass', 9000, 0.35, 0.9, 0, null, 0.7);
      soplo('bandpass', 3500, 0.2, 0.4, 0, null, 0.8);
    }
  };

  /* ---------- Piano ---------- */
  function piano(f) {
    tono('triangle', f, f, 0.5, 1.3);
    tono('sine', f * 2, f * 2, 0.18, 0.8);
    tono('sine', f * 3, f * 3, 0.06, 0.4);
  }

  /* ---------- Guitarra (cuerda pulsada, Karplus-Strong) ---------- */
  function cuerda(f) {
    if (cuerdas[f]) return cuerdas[f];
    var sr = ctx.sampleRate;
    var n = Math.floor(sr * 1.8);
    var buf = ctx.createBuffer(1, n, sr);
    var d = buf.getChannelData(0);
    var p = Math.round(sr / f);
    var i;
    for (i = 0; i < p; i++) d[i] = Math.random() * 2 - 1;
    for (i = p; i < n; i++) d[i] = (d[i - p] + d[i - p + 1]) * 0.5 * 0.996;
    cuerdas[f] = buf;
    return buf;
  }
  function pulsar(f, cuando, vol) {
    if (!c()) return;
    var t = ctx.currentTime + (cuando || 0);
    var s = ctx.createBufferSource();
    s.buffer = cuerda(f);
    var g = ctx.createGain();
    g.gain.value = vol || 0.5;
    s.connect(g);
    g.connect(busEfectos);
    s.start(t);
  }
  function acorde(frecs) {
    for (var i = 0; i < frecs.length; i++) pulsar(frecs[i], i * 0.028, 0.42);
  }

  /* ---------- Efectos ---------- */
  var efecto = {
    // Interfaz
    mover: function () { tono('sine', 1050, 1150, 0.07, 0.05); },
    elegir: function () { tono('triangle', 520, 820, 0.2, 0.09); tono('sine', 1040, 1300, 0.06, 0.07, 0.02); },
    bien: function () {
      [784, 988, 1175, 1568].forEach(function (f, i) { tono('sine', f, f, 0.16, 0.25, i * 0.06); });
    },
    mal: function () { tono('triangle', 330, 240, 0.16, 0.22); },
    // Minijuegos
    salto: function () { tono('square', 300, 700, 0.18, 0.18); },
    punto: function () { tono('sine', 880, 880, 0.3, 0.1); tono('sine', 1320, 1320, 0.3, 0.14, 0.09); },
    golpe: function () { tono('sawtooth', 200, 60, 0.4, 0.3); soplo('lowpass', 800, 0.4, 0.2); },
    voltear: function () { tono('sine', 520, 620, 0.2, 0.08); },
    ganar: function () {
      [523, 659, 784, 1047].forEach(function (f, i) { tono('triangle', f, f, 0.35, 0.3, i * 0.13); });
    },
    perder: function () { tono('triangle', 400, 200, 0.3, 0.5); }
  };

  /* ---------- Música de fondo ----------
   * Melodías propias, sencillas, en do mayor. Cada compás tiene un acorde y 8 corcheas de
   * melodía (null = silencio). Las notas son números MIDI (60 = do central). */
  function hz(n) { return 440 * Math.pow(2, (n - 69) / 12); }
  var DO = [48, 52, 55], SOL = [43, 47, 50], LAM = [45, 48, 52], FA = [41, 45, 48], REM = [50, 53, 57];
  // Arpegio de caja de música sobre un acorde: 8 corcheas.
  function arpegio(a) {
    var n = [a[0] + 24, a[1] + 24, a[2] + 24, a[0] + 36];
    return [n[0], n[1], n[2], n[3], n[2], n[1], n[2], null];
  }
  var TEMAS = {
    // Menús, álbum y minijuegos: alegre.
    alegre: {
      bpm: 104, vol: 0.4, sonaja: true,
      acordes: [DO, SOL, LAM, FA, DO, SOL, FA, DO],
      melodia: [
        [76, null, 79, null, 81, 79, 76, null],
        [74, null, 79, null, 71, null, 74, null],
        [72, null, 76, null, 81, null, 79, 76],
        [77, null, 81, null, 79, 77, 76, null],
        [76, 79, 84, null, 79, null, 76, null],
        [74, null, 71, null, 74, 79, null, null],
        [81, null, 77, null, 74, null, 72, 74],
        [72, null, null, null, 67, null, 72, null]
      ]
    },
    // Mientras juega a leer: tranquila, para no distraer de la voz.
    tranquila: {
      bpm: 80, vol: 0.26, sonaja: false,
      acordes: [DO, FA, LAM, SOL, DO, FA, REM, SOL],
      melodia: null
    }
  };
  TEMAS.tranquila.melodia = TEMAS.tranquila.acordes.map(arpegio);

  var musicaPrendida = true;
  var tema = null, nombreTema = null, paso = 0, siguienteT = 0, reloj = null;
  var hablando = false, soltar = null;

  function nivelMusica() {
    if (!nombreTema || !tema || !musicaPrendida) return 0;
    return tema.vol * (hablando ? 0.35 : 1);
  }
  function rampaMusica(seg) {
    if (!ctx) return;
    var t = ctx.currentTime, g = busMusica.gain;
    g.cancelScheduledValues(t);
    g.setValueAtTime(g.value, t);
    g.linearRampToValueAtTime(nivelMusica(), t + seg);
  }
  // Baja la música mientras habla la voz (y la sube un poco después de que se calla).
  function agachar(si) {
    clearTimeout(soltar);
    if (si) {
      if (!hablando) { hablando = true; rampaMusica(0.15); }
    } else {
      soltar = setTimeout(function () { hablando = false; rampaMusica(0.8); }, 500);
    }
  }

  function nota(tipo, f, vol, dur, t, ataque) {
    osc(tipo, f, f, dur, t, env(vol, dur, t, busMusica, ataque));
  }
  function tocarPaso(T, n, t, corchea) {
    var compas = Math.floor(n / 8) % T.acordes.length, i = n % 8;
    var a = T.acordes[compas];
    var m = T.melodia[compas][i];
    if (m) {                                           // melodía: campanita
      nota('sine', hz(m), 0.32, 0.55, t);
      nota('sine', hz(m) * 2, 0.07, 0.25, t);
    }
    if (i === 0) {                                     // acorde suave todo el compás
      for (var k = 0; k < 3; k++) nota('triangle', hz(a[k] + 12), 0.05, corchea * 8, t, 0.25);
    }
    if (i === 0 || i === 4) nota('triangle', hz(a[0]), 0.22, corchea * 3, t, 0.01);   // bajo
    if (T.sonaja && i % 2 === 1) {                     // sonaja en los contratiempos
      var s = ctx.createBufferSource();
      s.buffer = ruido();
      var f = ctx.createBiquadFilter();
      f.type = 'highpass';
      f.frequency.value = 6500;
      s.connect(f);
      f.connect(env(0.06, 0.05, t, busMusica));
      s.start(t, Math.random() * 0.5);
      s.stop(t + 0.1);
    }
  }
  function programar() {
    if (!tema || !ctx) return;
    var corchea = 60 / tema.bpm / 2;
    // Si la TV se atrasó (pestaña dormida), retoma desde ahora en vez de amontonar notas.
    if (siguienteT < ctx.currentTime - 0.2) siguienteT = ctx.currentTime + 0.05;
    while (siguienteT < ctx.currentTime + 0.3) {
      tocarPaso(tema, paso, siguienteT, corchea);
      paso = (paso + 1) % (tema.acordes.length * 8);
      siguienteT += corchea;
    }
  }
  /* Pone la música de fondo: 'alegre', 'tranquila' o null para silencio. */
  function musica(nombre) {
    if (nombre && !TEMAS[nombre]) nombre = 'alegre';
    if (nombre === nombreTema) return;
    nombreTema = nombre || null;
    if (!c()) return;
    if (!nombreTema) {
      rampaMusica(0.5);
      setTimeout(function () { if (!nombreTema) tema = null; }, 600);
      return;
    }
    if (tema !== TEMAS[nombreTema]) {
      tema = TEMAS[nombreTema];
      paso = 0;
      siguienteT = ctx.currentTime + 0.15;
    }
    rampaMusica(1.2);
    if (!reloj) reloj = setInterval(programar, 70);
  }
  function prenderMusica(si) {
    musicaPrendida = !!si;
    rampaMusica(0.4);
  }
  // Si la app pasa a segundo plano, silencio total; al volver, sigue.
  document.addEventListener('visibilitychange', function () {
    if (!ctx || !ctx.suspend) return;
    try { if (document.hidden) ctx.suspend(); else ctx.resume(); } catch (e) { /* nada */ }
  });

  return {
    activar: function () { return !!c(); },
    estado: function () { return ctx ? (ctx.state || 'running') : 'sin audio'; },
    corriendo: corriendo,
    cargar: cargar, decir: decir, callarVoz: callarVoz,
    musica: musica, prenderMusica: prenderMusica,
    tema: function () { return nombreTema; },
    bateria: bateria, piano: piano, acorde: acorde, pulsar: pulsar, efecto: efecto,
    // Solo para pruebas: usar otro contexto (p. ej. OfflineAudioContext) para medir sonidos.
    _usar: function (contexto) { montar(contexto); },
    _componer: function (nombre, seg) {   // agenda seg segundos de un tema (para grabarlo)
      var T = TEMAS[nombre], corchea = 60 / T.bpm / 2;
      busMusica.gain.value = T.vol;
      for (var n = 0, t = 0.05; t < seg; n++, t += corchea) tocarPaso(T, n, t, corchea);
    },
    _busMusica: function () { return busMusica; }
  };
})();
