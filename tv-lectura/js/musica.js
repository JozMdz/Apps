/* Música: batería, teclado y guitarra para tocar libremente.
 * Se toca con OK sobre cada botón, con los números del control (1, 2, 3…),
 * con el puntero o desde el celular. */
(function () {
  'use strict';
  var L = window.LEO, el = L.el;

  /* Crea una pantalla de instrumento con botones que suenan. */
  function instrumento(id, titulo, clase, botones) {
    var p = L.pantallaNueva(id, titulo);
    var caja = el('div', 'instrumento ' + clase);
    p.cuerpo.appendChild(caja);
    var lista = botones.map(function (def, i) {
      var b = el('button', 'pad ' + (def.clase || ''));
      b.style.background = def.color;
      b.setAttribute('data-color', def.color);
      b.appendChild(el('span', 'pad-nombre', def.nombre));
      b.appendChild(el('span', 'pad-numero', i + 1));
      b.tocar = function () {
        def.sonar();
        b.classList.remove('golpe');
        void b.offsetWidth;
        b.classList.add('golpe');
        if (p.alTocar) p.alTocar(i);
      };
      L.prepararFoco(b, b.tocar);
      caja.appendChild(b);
      return b;
    });
    // Números del control: 1 toca el primer botón, 2 el segundo…
    L.TECLAS[id] = function (k) {
      var n = k >= 49 && k <= 57 ? k - 49 : k >= 97 && k <= 105 ? k - 97 : -1;
      if (n < 0 || !lista[n]) return false;
      L.enfocar(lista[n], true);
      lista[n].tocar();
      return true;
    };
    L.ATRAS[id] = function () { L.abrirMenu('musica'); };
    L.MUSICA[id] = null;   // sin música de fondo mientras toca
    p.botones = lista;
    p.caja = caja;
    p.abrir = function () { SON.activar(); L.mostrar(id, lista[0]); };
    return p;
  }

  /* ---------- Batería ---------- */
  var B = SON.bateria;
  var bateria = instrumento('bateria', 'Batería', 'tambores', [
    { nombre: 'Platillo', color: '#fdd835', sonar: B.platillo, clase: 'platillo' },
    { nombre: 'Tom', color: '#ef5350', sonar: B.tom },
    { nombre: 'Tom grave', color: '#ab47bc', sonar: B.tomgrave },
    { nombre: 'Hi-hat', color: '#ffee58', sonar: B.hihat, clase: 'platillo' },
    { nombre: 'Tarola', color: '#42a5f5', sonar: B.tarola },
    { nombre: 'Bombo', color: '#66bb6a', sonar: B.bombo, clase: 'bombo' }
  ]);

  /* ---------- Teclado ---------- */
  var NOTAS = [
    ['do', 261.63, '#ef5350'], ['re', 293.66, '#ffa726'], ['mi', 329.63, '#ffee58'], ['fa', 349.23, '#66bb6a'],
    ['sol', 392.0, '#26c6da'], ['la', 440.0, '#42a5f5'], ['si', 493.88, '#ab47bc'], ['do', 523.25, '#ec407a']
  ];
  var teclado = instrumento('teclado', 'Teclado', 'teclas', NOTAS.map(function (n) {
    return { nombre: n[0], color: n[2], sonar: function () { SON.piano(n[1]); } };
  }));
  // Canción guiada: la tecla que sigue brinca; al tocarla avanza.
  var ESTRELLITA = [0, 0, 4, 4, 5, 5, 4, 3, 3, 2, 2, 1, 1, 0, 4, 4, 3, 3, 2, 2, 1, 4, 4, 3, 3, 2, 2, 1, 0, 0, 4, 4, 5, 5, 4, 3, 3, 2, 2, 1, 1, 0];
  var paso = -1;
  var botonCancion = el('button', 'boton-chico cancion');
  function guia() {
    teclado.botones.forEach(function (b, i) {
      if (paso >= 0 && ESTRELLITA[paso] === i) b.classList.add('guia'); else b.classList.remove('guia');
    });
    botonCancion.textContent = paso >= 0 ? 'Tocar libre' : 'Canción: Estrellita';
  }
  L.prepararFoco(botonCancion, function () { paso = paso >= 0 ? -1 : 0; guia(); });
  teclado.cuerpo.appendChild(botonCancion);
  teclado.alTocar = function (i) {
    if (paso < 0 || ESTRELLITA[paso] !== i) return;
    paso++;
    if (paso >= ESTRELLITA.length) {
      paso = -1;
      L.lluvia();
      setTimeout(function () { SON.efecto.ganar(); }, 500);
    }
    guia();
  };
  var abrirTeclado = teclado.abrir;
  teclado.abrir = function () { paso = -1; guia(); abrirTeclado(); };

  /* ---------- Guitarra ---------- */
  var ACORDES = [
    ['Do', '#ef5350', [130.81, 164.81, 196.0, 261.63, 329.63]],
    ['Sol', '#42a5f5', [98.0, 123.47, 146.83, 196.0, 246.94, 392.0]],
    ['La m', '#ab47bc', [110.0, 164.81, 220.0, 261.63, 329.63]],
    ['Fa', '#66bb6a', [174.61, 220.0, 261.63, 349.23]],
    ['Re', '#ffa726', [146.83, 220.0, 293.66, 369.99]],
    ['Mi m', '#26c6da', [82.41, 123.47, 164.81, 196.0, 246.94, 329.63]]
  ];
  var guitarra = instrumento('guitarra', 'Guitarra', 'acordes', ACORDES.map(function (a) {
    return { nombre: a[0], color: a[1], sonar: function () { SON.acorde(a[2]); } };
  }));
  var dibujo = el('img', 'guitarra-dibujo');
  dibujo.src = 'img/_guitarra.svg';
  guitarra.cuerpo.appendChild(dibujo);

  /* ---------- Menú ---------- */
  L.categoria('musica', 'Música');
  L.agregarJuego('musica', { id: 'bateria', nombre: 'Batería', img: '_bateria', color: '#ffcdd2', abrir: bateria.abrir });
  L.agregarJuego('musica', { id: 'teclado', nombre: 'Teclado', img: '_teclado', color: '#bbdefb', abrir: function () { teclado.abrir(); } });
  L.agregarJuego('musica', { id: 'guitarra', nombre: 'Guitarra', img: '_guitarra', color: '#c8e6c9', abrir: guitarra.abrir });
})();
