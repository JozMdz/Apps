/* Minijuegos de premio (se abren con el PIN de papás y por el tiempo que ellos den):
 *  - Salta: el caballito corre solo; hay que brincar los nopales y las piedras.
 *  - Atrapa: mover la canasta para atrapar la fruta que cae.
 *  - Memoria: encontrar las parejas de dibujos.
 */
(function () {
  'use strict';
  var L = window.LEO, $ = L.$, el = L.el, azar = L.azar, mezclar = L.mezclar;
  var META = 15;      // obstáculos o frutas para ganar
  var VIDAS = 3;

  function imagen(k, clase) {
    var i = el('img', clase || '');
    i.src = 'img/' + k + '.svg';
    i.alt = '';
    return i;
  }
  function salirDeJuegos() {
    if (L.permiso.vigente()) L.abrirMenu('mini'); else { L.irInicio(); L.hablar(['f_tiempo']); }
  }
  function marcador(p) {
    var hud = el('div', 'hud');
    var vidas = el('div', 'vidas');
    var puntos = el('div', 'puntos');
    hud.appendChild(vidas);
    hud.appendChild(puntos);
    p.barra.replaceChild(hud, p.barra.lastChild);
    return {
      pintar: function (v, n) {
        vidas.innerHTML = '';
        for (var i = 0; i < VIDAS; i++) vidas.appendChild(imagen('_corazon', i < v ? '' : 'perdida'));
        puntos.textContent = n + ' / ' + META;
      }
    };
  }

  /* ---------- Pantalla de resultado ---------- */
  var fin = el('section', 'pantalla');
  fin.id = 'minifin';
  var finTitulo = el('h2', '', '');
  finTitulo.setAttribute('data-cel-titulo', '1');
  var finBotones = el('div', 'fila-botones');
  var otra = el('button', 'boton-grande', 'Otra vez');
  var salir = el('button', 'boton-grande secundario', 'Salir');
  fin.appendChild(finTitulo);
  fin.appendChild(finBotones);
  finBotones.appendChild(otra);
  finBotones.appendChild(salir);
  $('escenario').insertBefore(fin, $('confeti'));
  L.prepararFoco(otra, function () { if (otra.repetir) otra.repetir(); });
  L.prepararFoco(salir, salirDeJuegos);
  L.ATRAS.minifin = salirDeJuegos;

  function resultado(gano, repetir) {
    var hayTiempo = L.permiso.vigente();
    finTitulo.textContent = gano ? '¡Ganaste!' : '¡Casi!';
    otra.repetir = repetir;
    otra.style.display = hayTiempo ? '' : 'none';
    L.mostrar('minifin', hayTiempo ? otra : salir);
    if (gano) { L.lluvia(); SON.efecto.ganar(); } else SON.efecto.perder();
    L.hablar([gano ? 'f_ganaste' : 'f_otravez'].concat(hayTiempo ? [] : ['f_tiempo']));
  }

  /* Ciclo de animación común: llama a paso(dt) mientras la pantalla esté abierta. */
  function ciclo(id, paso) {
    var antes = 0, vivo = false;
    function cuadro(t) {
      if (!vivo) return;
      if (L.pantalla().id !== id) { vivo = false; return; }
      var dt = Math.min(0.05, (t - antes) / 1000);
      antes = t;
      if (dt > 0) paso(dt);
      if (vivo) requestAnimationFrame(cuadro);
    }
    return {
      empezar: function () { vivo = true; antes = performance.now(); requestAnimationFrame(cuadro); },
      parar: function () { vivo = false; }
    };
  }

  /* ================= SALTA ================= */
  var salta = L.pantallaNueva('salta', 'Salta');
  salta.raiz.setAttribute('data-cel', 'c1');
  var hudSalta = marcador(salta);
  var pista = el('div', 'pista');
  var caballo = imagen('_corre', 'caballo');
  var cosas = el('div', 'cosas');
  var botonSalta = el('button', 'boton-grande boton-salta', '¡Salta!');
  pista.appendChild(el('div', 'suelo'));
  pista.appendChild(cosas);
  pista.appendChild(caballo);
  salta.cuerpo.appendChild(pista);
  salta.cuerpo.appendChild(botonSalta);

  var ANCHO = 1760, X_CABALLO = 240, GRAVEDAD = 2900, IMPULSO = 1150;
  var S = null;

  function saltar() {
    if (!S || S.y > 0) return;
    S.vy = IMPULSO;
    SON.efecto.salto();
  }
  L.prepararFoco(botonSalta, saltar);
  L.TECLAS.salta = function (k) {
    if (k === 13 || k === 38 || k === 32) { saltar(); return true; }
    return k === 37 || k === 39 || k === 40;      // las flechas no mueven el foco aquí
  };

  function nuevoObstaculo() {
    var roca = Math.random() < 0.4;
    var o = { x: ANCHO + 60, ancho: roca ? 110 : 120, alto: roca ? 90 : 130, pasado: false, golpeado: false };
    o.el = imagen(roca ? '_roca' : 'nopal', 'obstaculo');
    o.el.style.width = o.ancho + 'px';
    o.el.style.height = o.alto + 'px';
    cosas.appendChild(o.el);
    S.lista.push(o);
    S.falta = 720 + Math.random() * 420;     // distancia hasta el siguiente
  }

  var cicloSalta = ciclo('salta', function (dt) {
    var vel = 470 + S.puntos * 14;
    // Caballo
    S.vy -= GRAVEDAD * dt;
    S.y = Math.max(0, S.y + S.vy * dt);
    if (S.y === 0) S.vy = 0;
    S.inmune = Math.max(0, S.inmune - dt);
    caballo.style.bottom = (110 + S.y) + 'px';
    caballo.className = 'caballo' + (S.y > 0 ? ' aire' : '') + (S.inmune > 0 ? ' parpadea' : '');
    // Obstáculos
    S.falta -= vel * dt;
    if (S.falta <= 0 && S.puntos + S.lista.filter(function (o) { return !o.pasado; }).length < META) nuevoObstaculo();
    S.lista.forEach(function (o) {
      o.x -= vel * dt;
      o.el.style.left = o.x + 'px';
      var choca = o.x < X_CABALLO + 130 && o.x + o.ancho > X_CABALLO + 55 && S.y < o.alto * 0.7;
      if (choca && !o.golpeado && S.inmune <= 0) {
        o.golpeado = true;
        S.vidas--;
        S.inmune = 1.5;
        SON.efecto.golpe();
        hudSalta.pintar(S.vidas, S.puntos);
      }
      if (!o.pasado && o.x + o.ancho < X_CABALLO) {
        o.pasado = true;
        if (!o.golpeado) { S.puntos++; SON.efecto.punto(); }
        hudSalta.pintar(S.vidas, S.puntos);
      }
    });
    S.lista = S.lista.filter(function (o) {
      if (o.x > -200) return true;
      cosas.removeChild(o.el);
      return false;
    });
    if (S.vidas <= 0) { cicloSalta.parar(); resultado(false, abrirSalta); }
    else if (S.puntos >= META) { cicloSalta.parar(); resultado(true, abrirSalta); }
  });

  function abrirSalta() {
    SON.activar();
    cosas.innerHTML = '';
    S = { y: 0, vy: 0, vidas: VIDAS, puntos: 0, inmune: 0, lista: [], falta: 900 };
    hudSalta.pintar(S.vidas, S.puntos);
    L.mostrar('salta', botonSalta);
    cicloSalta.empezar();
  }
  L.ATRAS.salta = function () { cicloSalta.parar(); salirDeJuegos(); };

  /* ================= ATRAPA ================= */
  var atrapa = L.pantallaNueva('atrapa', 'Atrapa');
  atrapa.raiz.setAttribute('data-cel', 'c2g');
  var hudAtrapa = marcador(atrapa);
  var campo = el('div', 'pista campo');
  var canasta = imagen('_canasta', 'canasta');
  var frutas = el('div', 'cosas');
  campo.appendChild(frutas);
  campo.appendChild(canasta);
  atrapa.cuerpo.appendChild(campo);
  var controles = el('div', 'controles-atrapa');
  var izq = el('button', 'boton-grande', '◀');
  var der = el('button', 'boton-grande', '▶');
  controles.appendChild(izq);
  controles.appendChild(der);
  atrapa.cuerpo.appendChild(controles);

  var CARRILES = 5, ANCHO_CARRIL = 300, ALTO_CAMPO = 700;
  var FRUTAS = ['manzana', 'pera', 'fresa', 'platano', 'uva', 'sandia', 'pinna'];
  var A = null;

  function moverCanasta(d) {
    if (!A) return;
    A.carril = Math.max(0, Math.min(CARRILES - 1, A.carril + d));
    canasta.style.left = (A.carril * ANCHO_CARRIL + 50) + 'px';
  }
  L.prepararFoco(izq, function () { moverCanasta(-1); });
  L.prepararFoco(der, function () { moverCanasta(1); });
  L.TECLAS.atrapa = function (k) {
    if (k === 37) { moverCanasta(-1); return true; }
    if (k === 39) { moverCanasta(1); return true; }
    return k === 38 || k === 40 || k === 13;
  };

  var cicloAtrapa = ciclo('atrapa', function (dt) {
    var vel = 230 + A.puntos * 9;
    A.espera -= dt;
    if (A.espera <= 0 && A.puntos + A.lista.length < META + A.fallos) {
      // Cae cerca de la anterior, para que alcance a llegar.
      var c = Math.max(0, Math.min(CARRILES - 1, A.ultimo + Math.floor(Math.random() * 5) - 2));
      A.ultimo = c;
      var f = { carril: c, y: -120, el: imagen(azar(FRUTAS), 'fruta') };
      f.el.style.left = (c * ANCHO_CARRIL + 90) + 'px';
      frutas.appendChild(f.el);
      A.lista.push(f);
      A.espera = 1.9 - Math.min(0.7, A.puntos * 0.05);
    }
    A.lista = A.lista.filter(function (f) {
      f.y += vel * dt;
      f.el.style.top = f.y + 'px';
      if (f.y > ALTO_CAMPO - 250 && f.y < ALTO_CAMPO - 110 && f.carril === A.carril) {
        A.puntos++;
        SON.efecto.punto();
      } else if (f.y > ALTO_CAMPO - 90) {
        A.vidas--;
        A.fallos++;
        SON.efecto.golpe();
      } else return true;
      frutas.removeChild(f.el);
      hudAtrapa.pintar(A.vidas, A.puntos);
      return false;
    });
    if (A.vidas <= 0) { cicloAtrapa.parar(); resultado(false, abrirAtrapa); }
    else if (A.puntos >= META) { cicloAtrapa.parar(); resultado(true, abrirAtrapa); }
  });

  function abrirAtrapa() {
    SON.activar();
    frutas.innerHTML = '';
    A = { carril: 2, vidas: VIDAS, puntos: 0, fallos: 0, lista: [], espera: 0.8, ultimo: 2 };
    moverCanasta(0);
    hudAtrapa.pintar(A.vidas, A.puntos);
    L.mostrar('atrapa', der);
    cicloAtrapa.empezar();
  }
  L.ATRAS.atrapa = function () { cicloAtrapa.parar(); salirDeJuegos(); };

  /* ================= MEMORIA ================= */
  var memoria = L.pantallaNueva('memoria', 'Memoria');
  memoria.raiz.setAttribute('data-cel', 'c3');
  var mesa = el('div', 'mesa');
  memoria.cuerpo.appendChild(mesa);
  var M = null;

  function abrirMemoria() {
    SON.activar();
    mesa.innerHTML = '';
    var pares = mezclar(L.P).slice(0, 6);
    M = { abiertas: [], hechas: 0, ocupado: false };
    mezclar(pares.concat(pares)).forEach(function (w) {
      var carta = el('button', 'opcion carta');
      carta.appendChild(imagen('_carta', 'reverso'));
      L.prepararFoco(carta, function () {
        if (M.ocupado || carta.classList.contains('vista')) return;
        carta.innerHTML = '';
        carta.appendChild(imagen(w.k));
        carta.classList.add('vista');
        SON.efecto.voltear();
        L.hablar(['w_' + w.k]);
        M.abiertas.push({ carta: carta, k: w.k });
        if (M.abiertas.length < 2) return;
        var a = M.abiertas[0], b = M.abiertas[1];
        M.abiertas = [];
        if (a.k === b.k) {
          a.carta.classList.add('bien');
          b.carta.classList.add('bien');
          M.hechas++;
          SON.efecto.punto();
          if (M.hechas >= pares.length) setTimeout(function () {
            if (L.pantalla().id === 'memoria') resultado(true, abrirMemoria);
          }, 900);
        } else {
          M.ocupado = true;
          setTimeout(function () {
            [a.carta, b.carta].forEach(function (c) {
              c.classList.remove('vista');
              c.innerHTML = '';
              c.appendChild(imagen('_carta', 'reverso'));
            });
            M.ocupado = false;
          }, 1100);
        }
      });
      mesa.appendChild(carta);
    });
    L.mostrar('memoria', mesa.firstChild);
  }
  L.ATRAS.memoria = salirDeJuegos;

  /* ---------- Menú (la sección «Juegos» pide el PIN: ver papas.js) ---------- */
  function conPermiso(seguir) { if (L.permiso.vigente()) seguir(); else salirDeJuegos(); }
  L.agregarJuego('mini', { id: 'salta', nombre: 'Salta', img: '_corre', color: '#ffe0b2', abrir: abrirSalta, antes: conPermiso });
  L.agregarJuego('mini', { id: 'atrapa', nombre: 'Atrapa', img: '_canasta', color: '#c8e6c9', abrir: abrirAtrapa, antes: conPermiso });
  L.agregarJuego('mini', { id: 'memoria', nombre: 'Memoria', img: '_carta', color: '#bbdefb', abrir: abrirMemoria, antes: conPermiso });
})();
