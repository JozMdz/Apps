/* Cosas de papás: PIN, tiempo de minijuegos y el nombre del niño. */
(function () {
  'use strict';
  var L = window.LEO, $ = L.$, el = L.el, EST = L.EST;

  /* ---------- PIN ---------- */
  var pin = L.pantallaNueva('pin', 'Solo papás');
  var mensaje = el('div', 'pin-mensaje');
  mensaje.setAttribute('data-cel-titulo', '1');
  var puntos = el('div', 'pin-puntos');
  var teclado = el('div', 'pin-teclado');
  pin.cuerpo.appendChild(mensaje);
  pin.cuerpo.appendChild(puntos);
  pin.cuerpo.appendChild(teclado);

  var escrito = '', primero = null, alAcertar = null, creando = false;

  function pintarPin(texto) {
    if (texto) mensaje.textContent = texto;
    puntos.innerHTML = '';
    for (var i = 0; i < 4; i++) puntos.appendChild(el('i', i < escrito.length ? 'lleno' : ''));
  }
  function mal(texto) {
    escrito = '';
    pintarPin(texto);
    puntos.classList.remove('mal');
    void puntos.offsetWidth;
    puntos.classList.add('mal');
    if (window.SON) SON.efecto.perder();
  }
  function tecla(d) {
    if (d === 'borrar') { escrito = escrito.slice(0, -1); pintarPin(); return; }
    if (escrito.length >= 4) return;
    escrito += d;
    pintarPin();
    if (escrito.length < 4) return;
    var valor = escrito;
    setTimeout(function () {
      if (L.pantalla().id !== 'pin') return;
      if (creando) {                        // crear PIN: se escribe dos veces
        if (primero === null) { primero = valor; escrito = ''; pintarPin('Escríbelo otra vez para confirmar'); }
        else if (primero === valor) { EST.pin = valor; L.guardar(); terminar(); }
        else { primero = null; mal('No coincide. Elige un PIN de 4 números'); }
      } else if (valor === EST.pin) terminar();
      else mal('PIN incorrecto. Intenta otra vez');
    }, 250);
  }
  function terminar() {
    var f = alAcertar;
    alAcertar = null;
    if (f) f();
  }
  ['1', '2', '3', '4', '5', '6', '7', '8', '9', 'borrar', '0'].forEach(function (d) {
    var b = el('button', 'pin-tecla' + (d === 'borrar' ? ' borrar' : ''), d === 'borrar' ? 'Borrar' : d);
    L.prepararFoco(b, function () { tecla(d); });
    teclado.appendChild(b);
  });
  // Los números del control remoto también sirven.
  L.TECLAS.pin = function (k) {
    if (k >= 48 && k <= 57) { tecla(String(k - 48)); return true; }
    if (k >= 96 && k <= 105) { tecla(String(k - 96)); return true; }
    return false;
  };
  L.ATRAS.pin = function () { L.irInicio(); };

  /* Pide el PIN (o lo crea si no hay) y luego llama a seguir. */
  L.pedirPIN = function (para, seguir, nuevo) {
    escrito = ''; primero = null; alAcertar = seguir;
    creando = !!nuevo || !EST.pin;
    pin.titulo.textContent = 'Solo papás · ' + para;
    L.mostrar('pin', teclado.children[4]);
    pintarPin(creando ? 'Papás: elijan un PIN de 4 números' : 'Escribe el PIN');
  };

  /* ---------- Tiempo de minijuegos ---------- */
  var tiempo = L.pantallaNueva('tiempo', '¿Cuánto tiempo de juegos?');
  var filaTiempo = el('div', 'fila-tiempo');
  tiempo.cuerpo.appendChild(filaTiempo);
  var permisoHasta = 0;
  [5, 10, 15, 30].forEach(function (min) {
    var b = el('button', 'boton-tiempo');
    b.appendChild(el('b', '', min));
    b.appendChild(el('span', '', 'minutos'));
    L.prepararFoco(b, function () {
      permisoHasta = Date.now() + min * 60000;
      L.abrirMenu('mini');
    });
    filaTiempo.appendChild(b);
  });
  L.ATRAS.tiempo = function () { L.irInicio(); };

  L.permiso = {
    vigente: function () { return Date.now() < permisoHasta; },
    minutos: function () { return Math.max(0, Math.ceil((permisoHasta - Date.now()) / 60000)); },
    quitar: function () { permisoHasta = 0; },
    dar: function (min) { permisoHasta = Date.now() + min * 60000; }
  };
  var mini = L.categoria('mini', 'Juegos');
  mini.antes = function (seguir) {
    if (L.permiso.vigente()) { seguir(); return; }
    L.pedirPIN('Juegos', function () { L.mostrar('tiempo', filaTiempo.children[1]); });
  };
  mini.titulo = function () {
    var m = L.permiso.minutos();
    return 'Juegos · ' + (m === 1 ? 'queda 1 minuto' : 'quedan ' + m + ' minutos');
  };
  // Cuando se acaba el tiempo y está en el menú de juegos, regresa al inicio.
  setInterval(function () {
    var p = L.pantalla();
    if (!p || p.id !== 'menu' || L.permiso.vigente()) return;
    if ($('menu-titulo').textContent.indexOf('Juegos') !== 0) return;
    L.irInicio();
    L.hablar(['f_tiempo']);
  }, 3000);

  /* ---------- Nombre del niño ---------- */
  var esc = L.pantallaNueva('escribir', 'Nombre del niño o niña');
  var campo = el('div', 'campo-nombre');
  campo.setAttribute('data-cel-titulo', '1');
  var letras = el('div', 'letras-teclado');
  esc.cuerpo.appendChild(campo);
  esc.cuerpo.appendChild(letras);
  var nombre = '', alGuardar = null;

  function pintarNombre() { campo.textContent = nombre ? nombre.charAt(0).toUpperCase() + nombre.slice(1) : '…'; }
  'abcdefghijklmnñopqrstuvwxyzáéíóú'.split('').forEach(function (l) {
    var b = el('button', 'letra-tecla', l);
    L.prepararFoco(b, function () { if (nombre.length < 10) { nombre += l; pintarNombre(); } });
    letras.appendChild(b);
  });
  var borrar = el('button', 'letra-tecla ancha', 'Borrar');
  L.prepararFoco(borrar, function () { nombre = nombre.slice(0, -1); pintarNombre(); });
  letras.appendChild(borrar);
  var listo = el('button', 'letra-tecla ancha listo', 'Listo');
  L.prepararFoco(listo, function () {
    if (nombre.length < 2) return;
    EST.nombre = nombre;
    L.guardar();
    var f = alGuardar;
    alGuardar = null;
    if (f) f(); else L.abrirPapas();
  });
  letras.appendChild(listo);
  L.ATRAS.escribir = function () { alGuardar = null; L.irInicio(); };

  L.escribirNombre = function (seguir) {
    nombre = EST.nombre || '';
    alGuardar = seguir || null;
    pintarNombre();
    L.mostrar('escribir', letras.firstChild);
  };

  /* ---------- Botones en «Para papás» ---------- */
  L.etiquetasPapas = function () {
    $('aj-nombre').textContent = 'Nombre: ' + (EST.nombre ? EST.nombre.charAt(0).toUpperCase() + EST.nombre.slice(1) : 'sin escribir') + ' (OK para cambiar)';
    $('aj-pin').textContent = EST.pin ? 'Cambiar el PIN' : 'Crear el PIN de los juegos';
  };
  L.prepararFoco($('aj-nombre'), function () { L.escribirNombre(); });
  L.prepararFoco($('aj-pin'), function () {
    L.pedirPIN('PIN nuevo', L.abrirPapas, true);   // ya entró con el PIN anterior
  });
})();
