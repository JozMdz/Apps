/* Sonidos hechos al momento con Web Audio (sin archivos): batería, piano, guitarra
 * y efectos de los minijuegos. Si la TV no tiene Web Audio, no suena y no falla. */
var SON = (function () {
  'use strict';
  var AC = window.AudioContext || window.webkitAudioContext;
  var ctx = null;
  var salida = null;
  var ruidoBuf = null;
  var cuerdas = {};

  function c() {
    if (!AC) return null;
    try {
      if (!ctx) {
        ctx = new AC();
        salida = ctx.createGain();
        salida.gain.value = 0.9;
        salida.connect(ctx.destination);
      }
      if (ctx.state === 'suspended' && ctx.resume) ctx.resume();
    } catch (e) { return null; }
    return ctx;
  }

  function ruido() {
    if (!ruidoBuf) {
      ruidoBuf = ctx.createBuffer(1, ctx.sampleRate, ctx.sampleRate);
      var d = ruidoBuf.getChannelData(0);
      for (var i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    }
    return ruidoBuf;
  }

  // Envolvente: sube rápido a vol y baja hasta casi cero en dur segundos.
  function env(vol, dur, t) {
    var g = ctx.createGain();
    g.gain.setValueAtTime(0.0001, t);
    g.gain.linearRampToValueAtTime(vol, t + 0.005);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    g.connect(salida);
    return g;
  }
  function tono(tipo, f0, f1, vol, dur, cuando) {
    if (!c()) return;
    var t = ctx.currentTime + (cuando || 0);
    var o = ctx.createOscillator();
    o.type = tipo;
    o.frequency.setValueAtTime(f0, t);
    if (f1 && f1 !== f0) o.frequency.exponentialRampToValueAtTime(f1, t + dur);
    o.connect(env(vol, dur, t));
    o.start(t);
    o.stop(t + dur + 0.05);
  }
  function soplo(filtro, frec, vol, dur, cuando) {
    if (!c()) return;
    var t = ctx.currentTime + (cuando || 0);
    var s = ctx.createBufferSource();
    s.buffer = ruido();
    var f = ctx.createBiquadFilter();
    f.type = filtro;
    f.frequency.value = frec;
    s.connect(f);
    f.connect(env(vol, dur, t));
    s.start(t);
    s.stop(t + dur + 0.05);
  }

  /* ---- Batería ---- */
  var bateria = {
    bombo: function () { tono('sine', 150, 45, 1, 0.35); },
    tarola: function () { soplo('highpass', 1500, 0.7, 0.2); tono('triangle', 220, 140, 0.5, 0.12); },
    hihat: function () { soplo('highpass', 7000, 0.4, 0.07); },
    tom: function () { tono('sine', 220, 130, 0.9, 0.3); },
    tomgrave: function () { tono('sine', 140, 80, 0.9, 0.4); },
    platillo: function () { soplo('highpass', 5000, 0.5, 1.2); soplo('bandpass', 9000, 0.3, 0.8); }
  };

  /* ---- Piano ---- */
  function piano(f) {
    tono('triangle', f, f, 0.5, 1.3);
    tono('sine', f * 2, f * 2, 0.18, 0.8);
    tono('sine', f * 3, f * 3, 0.06, 0.4);
  }

  /* ---- Guitarra (cuerda pulsada, Karplus-Strong) ---- */
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
    g.connect(salida);
    s.start(t);
  }
  function acorde(frecs) {
    for (var i = 0; i < frecs.length; i++) pulsar(frecs[i], i * 0.028, 0.42);
  }

  /* ---- Efectos de los minijuegos ---- */
  var efecto = {
    salto: function () { tono('square', 300, 700, 0.18, 0.18); },
    punto: function () { tono('sine', 880, 880, 0.3, 0.1); tono('sine', 1320, 1320, 0.3, 0.14, 0.09); },
    golpe: function () { tono('sawtooth', 200, 60, 0.4, 0.3); soplo('lowpass', 800, 0.4, 0.2); },
    voltear: function () { tono('sine', 520, 620, 0.2, 0.08); },
    ganar: function () {
      [523, 659, 784, 1047].forEach(function (f, i) { tono('triangle', f, f, 0.35, 0.3, i * 0.13); });
    },
    perder: function () { tono('triangle', 400, 200, 0.3, 0.5); }
  };

  return {
    activar: function () { return !!c(); },
    estado: function () { return ctx ? ctx.state : 'sin audio'; },
    bateria: bateria, piano: piano, acorde: acorde, pulsar: pulsar, efecto: efecto
  };
})();
