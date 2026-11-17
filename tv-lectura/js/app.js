/* Leo y Juego: juegos para aprender a leer en la TV.
 * Escrito en JavaScript sencillo (ES5) para que funcione también en televisores LG
 * con navegadores antiguos. Se maneja con las flechas y OK del control remoto,
 * con el puntero del Magic Remote, con teclado o con el dedo.
 */
(function () {
  'use strict';

  var D = window.DATOS;
  var POR_RONDA = 5;
  var NIVEL_MAX = 3;
  var JUEGOS = ['letras', 'escucha', 'silabas', 'palabras', 'arma'];

  function $(id) { return document.getElementById(id); }
  function el(tag, clase, texto) {
    var e = document.createElement(tag);
    if (clase) e.className = clase;
    if (texto != null) e.textContent = texto;
    return e;
  }
  function azar(lista) { return lista[Math.floor(Math.random() * lista.length)]; }
  function mezclar(lista) {
    var a = lista.slice();
    for (var i = a.length - 1; i > 0; i--) {
      var j = Math.floor(Math.random() * (i + 1));
      var t = a[i]; a[i] = a[j]; a[j] = t;
    }
    return a;
  }
  function contiene(lista, x) { return lista.indexOf(x) >= 0; }
  function sinAcento(t) {
    return t.replace(/[áà]/g, 'a').replace(/[éè]/g, 'e').replace(/[íì]/g, 'i')
      .replace(/[óò]/g, 'o').replace(/[úùü]/g, 'u');
  }
  function clave(t) { return sinAcento(t.toLowerCase().replace(/ñ/g, 'nn')).replace(/[^a-z0-9]/g, '_'); }

  /* ---------- Estado guardado ---------- */
  var EST = (function () {
    var base = { niveles: {}, estampas: [], mayus: false, voz: true };
    try {
      var g = JSON.parse(localStorage.getItem('leoyjuego') || 'null');
      if (g) for (var k in g) base[k] = g[k];
    } catch (e) { /* sin almacenamiento: se juega igual */ }
    JUEGOS.forEach(function (j) { if (!base.niveles[j]) base.niveles[j] = 1; });
    return base;
  })();
  function guardar() {
    try { localStorage.setItem('leoyjuego', JSON.stringify(EST)); } catch (e) { /* nada */ }
  }
  function txt(t) { return EST.mayus ? t.toUpperCase() : t; }

  /* ---------- Escalar el escenario de 1920x1080 a la pantalla ---------- */
  function ajustar() {
    var w = window.innerWidth, h = window.innerHeight;
    var s = Math.min(w / 1920, h / 1080);
    var e = $('escenario');
    e.style.transform = 'scale(' + s + ')';
    e.style.webkitTransform = 'scale(' + s + ')';
    e.style.left = Math.round((w - 1920 * s) / 2) + 'px';
    e.style.top = Math.round((h - 1080 * s) / 2) + 'px';
  }
  window.addEventListener('resize', ajustar);

  /* ---------- Voz ---------- */
  var HAY_AUDIO = {};
  D.audios.forEach(function (a) { HAY_AUDIO[a] = true; });
  var reproductor = new Audio();
  var turnoVoz = 0;
  var cache = {};

  function precargar(claves) {
    claves.forEach(function (k) {
      if (HAY_AUDIO[k] && !cache[k]) {
        var a = new Audio();
        a.preload = 'auto';
        a.src = 'audio/' + k + '.mp3';
        cache[k] = a;
      }
    });
  }

  /* Reproduce una lista de audios uno tras otro. Llama a alTerminar al final
   * (aunque falle el audio o la voz esté apagada). */
  function hablar(claves, alTerminar) {
    var turno = ++turnoVoz;
    var lista = (claves || []).filter(function (k) { return HAY_AUDIO[k]; });
    var terminado = false;
    function fin() {
      if (terminado || turno !== turnoVoz) return;
      terminado = true;
      if (alTerminar) alTerminar();
    }
    try { reproductor.pause(); } catch (e) { /* nada */ }
    if (!EST.voz || !lista.length) { setTimeout(fin, 700); return; }
    var i = 0;
    // Por si el audio no avisa que terminó.
    var limite = setTimeout(fin, 2500 * lista.length + 1500);
    function siguiente() {
      if (turno !== turnoVoz) { clearTimeout(limite); return; }
      if (i >= lista.length) { clearTimeout(limite); fin(); return; }
      reproductor.onended = siguiente;
      reproductor.onerror = siguiente;
      reproductor.src = 'audio/' + lista[i++] + '.mp3';
      var p = reproductor.play();
      if (p && p.catch) p.catch(function () { setTimeout(siguiente, 300); });
    }
    siguiente();
  }
  function callar() { turnoVoz++; try { reproductor.pause(); } catch (e) { /* nada */ } }
  function felicitar() { return 'f_bien' + (1 + Math.floor(Math.random() * 5)); }
  function animar() { return 'f_mal' + (1 + Math.floor(Math.random() * 2)); }

  /* ---------- Navegación con el control remoto ---------- */
  var pantalla = null;
  var enfocado = null;

  function focosVisibles() {
    if (!pantalla) return [];
    return [].filter.call(pantalla.querySelectorAll('.foco'), function (e) {
      return e.offsetParent !== null && !e.disabled && !e.classList.contains('usada');
    });
  }
  function enfocar(e, sinVoz) {
    if (enfocado === e) return;
    if (enfocado) enfocado.classList.remove('enfocado');
    enfocado = e;
    if (!e) return;
    e.classList.add('enfocado');
    if (e.scrollIntoView && e.parentNode && e.parentNode.id === 'album-rejilla') {
      e.scrollIntoView({ block: 'nearest' });
    }
    if (e.alEnfocar && !sinVoz) e.alEnfocar();
  }
  function centro(e) {
    var r = e.getBoundingClientRect();
    return { x: r.left + r.width / 2, y: r.top + r.height / 2, r: r };
  }
  function mover(dx, dy) {
    var todos = focosVisibles();
    if (!todos.length) return;
    if (!enfocado || !contiene(todos, enfocado)) { enfocar(todos[0]); return; }
    var o = centro(enfocado);
    var mejor = null, mejorPuntos = Infinity;
    todos.forEach(function (c) {
      if (c === enfocado) return;
      var p = centro(c);
      var vx = p.x - o.x, vy = p.y - o.y;
      var principal = dx ? vx * dx : vy * dy;   // avance en la dirección pedida
      var lateral = dx ? Math.abs(vy) : Math.abs(vx);
      if (principal <= 10) return;
      var puntos = principal + lateral * 2.5;
      if (puntos < mejorPuntos) { mejorPuntos = puntos; mejor = c; }
    });
    if (mejor) enfocar(mejor);
  }

  var bloqueado = false;
  function activar(e) {
    if (!e || bloqueado) return;
    if (e.alElegir) e.alElegir(); else e.click();
  }

  document.addEventListener('keydown', function (ev) {
    var k = ev.keyCode;
    var manejada = true;
    if (k === 37) mover(-1, 0);
    else if (k === 39) mover(1, 0);
    else if (k === 38) mover(0, -1);
    else if (k === 40) mover(0, 1);
    else if (k === 13) activar(enfocado);
    else if (k === 461 || k === 27 || k === 8 || k === 10009) atras();
    else if (k === 403 || k === 415) repetir();       // botón rojo o Play: repetir voz
    else manejada = false;
    if (manejada) ev.preventDefault();
  });

  function prepararFoco(e, alElegir, alEnfocar) {
    e.classList.add('foco');
    e.alElegir = alElegir;
    e.alEnfocar = alEnfocar || null;
    e.addEventListener('click', function (ev) {
      ev.preventDefault();
      enfocar(e, true);
      if (!bloqueado && e.alElegir) e.alElegir();
    });
    e.addEventListener('mouseover', function () { enfocar(e); });
    return e;
  }

  function mostrar(id, foco) {
    callar();
    bloqueado = false;
    var nueva = $(id);
    if (pantalla) pantalla.classList.remove('activa');
    nueva.classList.add('activa');
    pantalla = nueva;
    enfocado = null;
    [].forEach.call(document.querySelectorAll('.enfocado'), function (x) { x.classList.remove('enfocado'); });
    enfocar(foco || focosVisibles()[0], true);
  }

  function atras() {
    if (!pantalla) return;
    if (pantalla.id === 'inicio') { salir(); return; }
    if (pantalla.id === 'juego') ronda = null;
    irInicio();
  }
  function salir() {
    try {
      if (window.webOS && window.webOS.platformBack) { window.webOS.platformBack(); return; }
      if (window.PalmSystem && window.PalmSystem.platformBack) { window.PalmSystem.platformBack(); return; }
    } catch (e) { /* nada */ }
    window.close();
  }

  /* ---------- Palabras y letras ---------- */
  var SILABA_SIMPLE = /^(ch|ll|qu|rr|[bcdfghjklmnñprstvwxyz])?[aeiouáéíóú]$/;
  var P = D.palabras;
  P.forEach(function (w) {
    w.simple = w.s.length === 2 && w.s.every(function (s) { return SILABA_SIMPLE.test(s); });
    var dos = w.p.substr(0, 2);
    w.inicial = (dos === 'ch' || dos === 'll') ? null : sinAcento(w.p.charAt(0));
  });
  var SIMPLES = P.filter(function (w) { return w.simple; });
  var DOS = P.filter(function (w) { return w.s.length === 2; });
  var CORTAS = P.filter(function (w) { return w.s.length <= 2; });
  var LARGAS = P.filter(function (w) { return w.s.length >= 3; });
  var TRES = P.filter(function (w) { return w.s.length === 3; });
  var TODAS_SILABAS = [];
  P.forEach(function (w) { w.s.forEach(function (s) { if (w.s.length > 1 && !contiene(TODAS_SILABAS, s)) TODAS_SILABAS.push(s); }); });

  // Letras que suenan parecido: no se ponen juntas como opciones.
  var GRUPOS = ['ckqsz', 'bv', 'gj', 'ly', 'haeiou'];
  function parecidas(a, b) {
    if (a === b) return true;
    return GRUPOS.some(function (g) { return g.indexOf(a) >= 0 && g.indexOf(b) >= 0; });
  }
  var LETRAS_NIVEL = {
    1: ['a', 'e', 'i', 'o', 'u'],
    2: ['a', 'e', 'i', 'o', 'u', 'm', 'p', 's', 'l', 't', 'd', 'n'],
    3: Object.keys(D.letras)
  };

  function img(w) {
    var i = el('img');
    i.src = 'img/' + w.k + '.svg';
    i.alt = w.p;
    return i;
  }
  function dibujoGrande(w) {
    var d = el('div', 'dibujo-grande');
    d.appendChild(img(w));
    return d;
  }
  // Escoge n elementos de la lista que cumplan la condición, sin repetir.
  function escoger(lista, n, condicion) {
    return mezclar(lista.filter(condicion || function () { return true; })).slice(0, n);
  }

  /* ---------- Rondas ---------- */
  var ronda = null;

  function empezar(juego) {
    ronda = { juego: juego, nivel: EST.niveles[juego], n: 0, aciertos: 0, marcas: [], usadas: [] };
    mostrar('juego', $('volver'));
    siguientePregunta();
  }

  function dibujarProgreso() {
    var p = $('progreso');
    p.innerHTML = '';
    for (var i = 0; i < POR_RONDA; i++) {
      var c = el('i');
      if (ronda.marcas[i] === 'ok') c.className = 'ok';
      else if (ronda.marcas[i] === 'medio') c.className = 'medio';
      else if (i === ronda.n) c.className = 'actual';
      p.appendChild(c);
    }
  }

  function siguientePregunta() {
    if (!ronda) return;
    if (ronda.n >= POR_RONDA) { terminarRonda(); return; }
    ronda.fallo = false;
    bloqueado = false;
    $('pregunta').innerHTML = '';
    $('opciones').innerHTML = '';
    dibujarProgreso();
    ronda.voz = [];
    PREGUNTAS[ronda.juego](ronda);
    var ops = $('opciones').querySelectorAll('.foco');
    enfocar(ops[0] || $('repetir'), true);
    hablar(ronda.voz);
  }

  function repetir() {
    if (pantalla && pantalla.id === 'juego' && ronda && ronda.voz && !bloqueado) hablar(ronda.repetir || ronda.voz);
  }

  // Marca la respuesta de la pregunta actual y pasa a la siguiente.
  function preguntaLista(voz) {
    bloqueado = true;
    ronda.marcas[ronda.n] = ronda.fallo ? 'medio' : 'ok';
    if (!ronda.fallo) ronda.aciertos++;
    ronda.n++;
    dibujarProgreso();
    var r = ronda;
    hablar(voz, function () { if (ronda === r) setTimeout(siguientePregunta, 350); });
  }
  function equivocado(boton, voz) {
    ronda.fallo = true;
    boton.classList.remove('mal');
    void boton.offsetWidth;  // reinicia la animación
    boton.classList.add('mal');
    hablar(voz || [animar()]);
  }

  function opcionDibujo(w, correcta, conVoz, alAcertar) {
    var b = el('button', 'opcion');
    b.appendChild(img(w));
    prepararFoco(b, function () {
      if (b.classList.contains('bien')) return;
      if (w === correcta) {
        b.classList.add('bien');
        lluviaChica();
        alAcertar(b);
      } else {
        equivocado(b, ['w_' + w.k, animar()]);
      }
    }, conVoz ? function () { hablar(['w_' + w.k]); } : null);
    return b;
  }

  var PREGUNTAS = {
    /* Letras: ve la letra y busca el dibujo que empieza con ella. */
    letras: function (r) {
      var posibles = LETRAS_NIVEL[r.nivel].filter(function (l) {
        return !contiene(r.usadas, l) && P.some(function (w) { return w.inicial === l; });
      });
      if (!posibles.length) { r.usadas = []; posibles = LETRAS_NIVEL[r.nivel]; }
      var letra = azar(posibles);
      r.usadas.push(letra);
      var correcta = azar(P.filter(function (w) { return w.inicial === letra; }));
      var cuantas = r.nivel >= 3 ? 4 : 3;
      var otras = [], iniciales = [];
      mezclar(P).forEach(function (w) {
        if (otras.length >= cuantas - 1 || !w.inicial) return;
        if (parecidas(w.inicial, letra)) return;
        if (iniciales.some(function (i) { return parecidas(i, w.inicial); })) return;
        otras.push(w); iniciales.push(w.inicial);
      });
      var t = el('div', 'letra-grande', letra.toUpperCase() + ' ' + letra);
      $('pregunta').appendChild(t);
      mezclar([correcta].concat(otras)).forEach(function (w) {
        $('opciones').appendChild(opcionDibujo(w, correcta, true, function () {
          preguntaLista(['w_' + w.k, felicitar()]);
        }));
      });
      r.voz = ['l_' + clave(letra)];
      precargar(['w_' + correcta.k]);
    },

    /* Escucha: oye una sílaba y busca cómo se escribe. */
    escucha: function (r) {
      var base = D.escucha;
      var correcta, otras;
      var cuantas = r.nivel >= 3 ? 4 : 3;
      do { correcta = azar(base); } while (contiene(r.usadas, correcta) && r.usadas.length < base.length);
      r.usadas.push(correcta);
      if (r.nivel === 1) {          // m, p, s, l como en kínder; misma consonante, distinta vocal: ma / me / mo
        while (!contiene(['m', 'p', 's', 'l'], correcta.charAt(0))) correcta = azar(base);
        r.usadas[r.usadas.length - 1] = correcta;
        otras = escoger(base, cuantas - 1, function (s) { return s !== correcta && s.charAt(0) === correcta.charAt(0); });
      } else if (r.nivel === 2) {   // misma vocal, distinta consonante: ma / pa / sa
        otras = escoger(base, cuantas - 1, function (s) { return s !== correcta && s.slice(-1) === correcta.slice(-1); });
      } else {
        otras = escoger(base, cuantas - 1, function (s) { return s !== correcta; });
      }
      var bocina = el('button', 'altavoz-grande');
      var ib = el('img'); ib.src = 'img/_altavoz.svg'; bocina.appendChild(ib);
      prepararFoco(bocina, function () { hablar(['s_' + clave(correcta)]); });
      $('pregunta').appendChild(bocina);
      mezclar([correcta].concat(otras)).forEach(function (s) {
        var b = el('button', 'opcion', txt(s));
        prepararFoco(b, function () {
          if (b.classList.contains('bien')) return;
          if (s === correcta) {
            b.classList.add('bien');
            lluviaChica();
            preguntaLista(['s_' + clave(s), felicitar()]);
          } else {
            equivocado(b, [animar(), 's_' + clave(correcta)]);
          }
        });
        $('opciones').appendChild(b);
      });
      r.repetir = ['s_' + clave(correcta)];
      r.voz = (r.n === 0 ? ['f_escucha'] : []).concat(r.repetir);
    },

    /* Sílabas: completa la palabra con la sílaba que falta. */
    silabas: function (r) {
      var lista = r.nivel === 1 ? SIMPLES : r.nivel === 2 ? DOS : LARGAS;
      var w = nuevaPalabra(r, lista);
      var hueco = r.nivel === 1 ? 0 : Math.floor(Math.random() * w.s.length);
      var falta = w.s[hueco];
      var cuantas = r.nivel >= 3 ? 4 : 3;
      var otras = escoger(TODAS_SILABAS, cuantas - 1, function (s) {
        return sinAcento(s) !== sinAcento(falta) && (r.nivel < 3 || s.charAt(0) === falta.charAt(0) || s.slice(-1) === falta.slice(-1));
      });
      if (otras.length < cuantas - 1) {
        otras = otras.concat(escoger(TODAS_SILABAS, cuantas - 1 - otras.length, function (s) {
          return sinAcento(s) !== sinAcento(falta) && !contiene(otras, s);
        }));
      }
      $('pregunta').appendChild(dibujoGrande(w));
      var fila = el('div', 'palabra-huecos');
      var cajas = w.s.map(function (s, i) {
        var c = el('div', 'hueco' + (i === hueco ? ' vacio' : ''), txt(s));
        fila.appendChild(c);
        return c;
      });
      $('pregunta').appendChild(fila);
      mezclar([falta].concat(otras)).forEach(function (s) {
        var b = el('button', 'opcion', txt(s));
        prepararFoco(b, function () {
          if (b.classList.contains('bien')) return;
          if (s === falta) {
            b.classList.add('bien');
            cajas[hueco].className = 'hueco lleno';
            lluviaChica();
            preguntaLista(['w_' + w.k, felicitar()]);
          } else {
            equivocado(b);
          }
        }, r.nivel === 1 ? function () { hablar(['s_' + clave(s)]); } : null);
        $('opciones').appendChild(b);
      });
      r.repetir = ['w_' + w.k];
      r.voz = (r.n === 0 ? ['f_silabas'] : []).concat(r.repetir);
    },

    /* Palabras: lee la palabra y busca su dibujo (sin ayuda de voz). */
    palabras: function (r) {
      var lista = r.nivel === 1 ? SIMPLES : r.nivel === 2 ? CORTAS : LARGAS;
      var w = nuevaPalabra(r, lista);
      var cuantas = r.nivel >= 3 ? 4 : 3;
      var otras = [];
      if (r.nivel >= 2) {  // distractores que se parecen: misma primera letra o sílaba
        otras = escoger(P, cuantas - 1, function (x) {
          return x !== w && (x.s[0] === w.s[0] || x.p.charAt(0) === w.p.charAt(0) || x.p.slice(-2) === w.p.slice(-2));
        });
      }
      if (otras.length < cuantas - 1) {
        otras = otras.concat(escoger(r.nivel === 1 ? SIMPLES : P, cuantas - 1 - otras.length, function (x) {
          return x !== w && !contiene(otras, x);
        }));
      }
      $('pregunta').appendChild(el('div', 'palabra-grande', txt(w.p)));
      mezclar([w].concat(otras)).forEach(function (x) {
        $('opciones').appendChild(opcionDibujo(x, w, false, function () {
          preguntaLista(['w_' + w.k, felicitar()]);
        }));
      });
      r.repetir = ['f_palabras'];
      r.voz = r.n === 0 ? ['f_palabras'] : [];
    },

    /* Arma: elige las sílabas en orden para formar la palabra del dibujo. */
    arma: function (r) {
      var lista = r.nivel === 1 ? SIMPLES : r.nivel === 2 ? TRES : LARGAS;
      var w = nuevaPalabra(r, lista);
      var extra = escoger(TODAS_SILABAS, r.nivel >= 3 ? 2 : 1, function (s) { return !contiene(w.s, s); });
      $('pregunta').appendChild(dibujoGrande(w));
      var fila = el('div', 'palabra-huecos');
      var cajas = w.s.map(function (s) {
        var c = el('div', 'hueco vacio', txt(s));
        fila.appendChild(c);
        return c;
      });
      $('pregunta').appendChild(fila);
      var pos = 0;
      mezclar(w.s.concat(extra)).forEach(function (s) {
        var b = el('button', 'opcion', txt(s));
        prepararFoco(b, function () {
          if (b.classList.contains('usada')) return;
          if (s === w.s[pos]) {
            cajas[pos].className = 'hueco lleno';
            pos++;
            b.classList.add('usada');
            var resto = focosVisibles().filter(function (x) { return x.parentNode.id === 'opciones'; });
            if (pos >= w.s.length) {
              lluviaChica();
              preguntaLista(['w_' + w.k, felicitar()]);
            } else {
              enfocar(masCercano(b, resto), true);
              hablar(['s_' + clave(s)]);
            }
          } else {
            equivocado(b);
          }
        }, function () { hablar(['s_' + clave(s)]); });
        $('opciones').appendChild(b);
      });
      r.repetir = ['w_' + w.k];
      r.voz = (r.n === 0 ? ['f_arma'] : []).concat(r.repetir);
    }
  };

  function nuevaPalabra(r, lista) {
    var libres = lista.filter(function (w) { return !contiene(r.usadas, w.k); });
    if (!libres.length) libres = lista;
    var w = azar(libres);
    r.usadas.push(w.k);
    return w;
  }
  function masCercano(desde, lista) {
    var o = centro(desde), mejor = lista[0], d = Infinity;
    lista.forEach(function (x) {
      var p = centro(x), dd = Math.abs(p.x - o.x) + Math.abs(p.y - o.y);
      if (dd < d) { d = dd; mejor = x; }
    });
    return mejor;
  }

  function terminarRonda() {
    var r = ronda;
    ronda = null;
    var cont = $('fin-estrellas');
    cont.innerHTML = '';
    for (var i = 0; i < POR_RONDA; i++) {
      var s = el('img', i < r.aciertos ? '' : 'apagada');
      s.src = 'img/_estrella.svg';
      s.style.animationDelay = (0.15 * i) + 's';
      cont.appendChild(s);
    }
    var voz = ['f_fin'];
    var premio = $('fin-premio');
    premio.innerHTML = '';
    var mensaje = '';
    if (r.aciertos >= 3) {
      var libres = P.filter(function (w) { return !contiene(EST.estampas, w.k); });
      if (libres.length) {
        var nueva = azar(libres);
        EST.estampas.push(nueva.k);
        var c = el('div', 'estampa-nueva');
        c.appendChild(img(nueva));
        premio.appendChild(c);
        premio.appendChild(el('span', '', txt(nueva.p)));
        voz.push('f_estampa', 'w_' + nueva.k);
      }
      lluvia();
    }
    if (r.aciertos >= 4 && EST.niveles[r.juego] < NIVEL_MAX) {
      EST.niveles[r.juego]++;
      mensaje = '¡Subiste al nivel ' + EST.niveles[r.juego] + '!';
      voz.push('f_nivel');
    } else if (r.aciertos < 3) {
      mensaje = '¡Sigue practicando!';
      voz.push('f_otra');
    }
    guardar();
    $('fin-mensaje').textContent = mensaje;
    $('otra-vez').alElegir = function () { empezar(r.juego); };
    mostrar('fin', $('otra-vez'));
    hablar(voz);
  }

  /* ---------- Confeti ---------- */
  var COLORES = ['#e53935', '#fdd835', '#43a047', '#1e88e5', '#8e24aa', '#fb8c00'];
  function lluvia(cuantos) {
    var c = $('confeti');
    for (var i = 0; i < (cuantos || 80); i++) {
      var p = el('i');
      p.style.left = Math.random() * 1920 + 'px';
      p.style.background = azar(COLORES);
      p.style.animationDelay = Math.random() * 0.8 + 's';
      p.style.animationDuration = 1.6 + Math.random() * 1.4 + 's';
      c.appendChild(p);
    }
    setTimeout(function () { c.innerHTML = ''; }, 4000);
  }
  function lluviaChica() { lluvia(30); }

  /* ---------- Inicio ---------- */
  function irInicio() {
    [].forEach.call(document.querySelectorAll('.tarjeta'), function (t) {
      t.querySelector('.t-nivel').textContent = 'Nivel ' + EST.niveles[t.getAttribute('data-juego')];
    });
    $('cuenta-estampas').textContent = EST.estampas.length + ' / ' + P.length;
    mostrar('inicio', ultimaTarjeta || document.querySelector('.tarjeta'));
  }
  var ultimaTarjeta = null;

  [].forEach.call(document.querySelectorAll('.tarjeta'), function (t) {
    prepararFoco(t, function () { ultimaTarjeta = t; empezar(t.getAttribute('data-juego')); });
  });
  prepararFoco($('ir-album'), function () { abrirAlbum(); });
  prepararFoco($('ir-papas'), function () { abrirPapas(); });
  prepararFoco($('volver'), function () { atras(); });
  prepararFoco($('repetir'), function () { repetir(); });
  prepararFoco($('otra-vez'), function () { /* se asigna al terminar la ronda */ });
  prepararFoco($('fin-inicio'), function () { irInicio(); });
  prepararFoco($('album-volver'), function () { irInicio(); });
  prepararFoco($('papas-volver'), function () { irInicio(); });

  /* ---------- Álbum de estampas ---------- */
  function abrirAlbum() {
    var rej = $('album-rejilla');
    rej.innerHTML = '';
    $('album-palabra').textContent = '';
    var tengo = P.filter(function (w) { return contiene(EST.estampas, w.k); });
    var faltan = P.filter(function (w) { return !contiene(EST.estampas, w.k); });
    tengo.concat(faltan).forEach(function (w) {
      var mia = contiene(EST.estampas, w.k);
      var b = el('button', 'estampa' + (mia ? '' : ' bloqueada'));
      b.appendChild(img(w));
      prepararFoco(b, function () { if (mia) hablar(['w_' + w.k]); }, function () {
        $('album-palabra').textContent = mia ? txt(w.p) : '?';
        if (mia) hablar(['w_' + w.k]);
      });
      rej.appendChild(b);
    });
    rej.scrollTop = 0;
    mostrar('album', rej.firstChild);
    hablar(['f_album']);
  }

  /* ---------- Papás ---------- */
  var confirmarBorrado = false;
  function etiquetasPapas() {
    $('aj-letra').textContent = 'Letra: ' + (EST.mayus ? 'MAYÚSCULAS' : 'minúsculas');
    $('aj-voz').textContent = 'Voz: ' + (EST.voz ? 'encendida' : 'apagada');
    var nombres = { letras: 'Letras', escucha: 'Escucha', silabas: 'Sílabas', palabras: 'Palabras', arma: 'Arma' };
    $('aj-nivel').textContent = 'Nivel: ' + JUEGOS.map(function (j) { return nombres[j] + ' ' + EST.niveles[j]; }).join(' · ');
    $('aj-borrar').textContent = confirmarBorrado ? '¿Seguro? Pulsa OK otra vez para borrar' : 'Borrar progreso y estampas';
  }
  prepararFoco($('aj-letra'), function () { EST.mayus = !EST.mayus; guardar(); etiquetasPapas(); });
  prepararFoco($('aj-voz'), function () { EST.voz = !EST.voz; guardar(); etiquetasPapas(); });
  prepararFoco($('aj-nivel'), function () {
    // Pone todos los juegos en el mismo nivel: 1 → 2 → 3 → 1
    var n = EST.niveles.letras % NIVEL_MAX + 1;
    JUEGOS.forEach(function (j) { EST.niveles[j] = n; });
    guardar(); etiquetasPapas();
  });
  prepararFoco($('aj-borrar'), function () {
    if (!confirmarBorrado) { confirmarBorrado = true; etiquetasPapas(); return; }
    confirmarBorrado = false;
    EST.estampas = [];
    JUEGOS.forEach(function (j) { EST.niveles[j] = 1; });
    guardar(); etiquetasPapas();
  });
  function abrirPapas() { confirmarBorrado = false; etiquetasPapas(); mostrar('papas'); }

  /* ---------- Para el control desde el celular (js/conexion.js) ---------- */
  window.LEO = {
    focos: focosVisibles,
    pantalla: function () { return pantalla; },
    enfocado: function () { return enfocado; },
    elegir: function (e) { enfocar(e, true); activar(e); },
    mover: mover,
    atras: atras,
    repetir: repetir
  };

  /* ---------- Arranque ---------- */
  ajustar();
  precargar(['f_hola', 'f_bien1', 'f_bien2', 'f_bien3', 'f_bien4', 'f_bien5', 'f_mal1', 'f_mal2']);
  irInicio();
  hablar(['f_hola']);
  // Si la TV pone la app en segundo plano, se calla.
  document.addEventListener('visibilitychange', function () { if (document.hidden) callar(); });
  document.addEventListener('webOSRelaunch', function () { irInicio(); });
})();
