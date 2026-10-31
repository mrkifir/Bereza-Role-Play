/* Bereza RP — CEF інтерфейс. Події від сервера: cef.on(name, data); відповіді: cef.emit("br", "...") */
(function () {
  "use strict";
  var SEP = "\x01";
  var $ = function (id) { return document.getElementById(id); };
  var live = typeof window.cef !== "undefined";
  var handlers = {};
  var bridge = live ? window.cef : {
    on: function (n, f) { (handlers[n] = handlers[n] || []).push(f); },
    emit: function () { console.log("emit", [].slice.call(arguments).join(" ")); }
  };
  if (!live) { document.documentElement.classList.add("preview"); window.__fire = function (n, d) { (handlers[n] || []).forEach(function (f) { f(d); }); }; }
  // фокус (курсор + клавіатура) — тримаємо, поки відкрито діалог, телефон або інвентар
  function syncFocus() {
    var want = !!dlg || !$("phone").classList.contains("hidden") || !$("inv").classList.contains("hidden");
    if (live && bridge.set_focus) bridge.set_focus(want);
  }
  var unfocus = syncFocus;

  function send(s) { bridge.emit("br", s); }
  function show(el, on) { el.classList.toggle("hidden", !on); }
  function money(n) { n = parseInt(n, 10) || 0; return (n < 0 ? "-$" : "$") + Math.abs(n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, " "); }
  function esc(s) { return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;"); }
  // SA-MP кольори {RRGGBB} -> <span>
  function samp(s) {
    var out = "", open = false, re = /\{([0-9a-fA-F]{6})\}/g, last = 0, m;
    while ((m = re.exec(s))) {
      out += esc(s.slice(last, m.index));
      if (open) out += "</span>";
      out += '<span style="color:#' + m[1] + '">';
      open = true; last = re.lastIndex;
    }
    out += esc(s.slice(last));
    if (open) out += "</span>";
    return out;
  }
  function plain(s) { return s.replace(/\{[0-9a-fA-F]{6}\}/g, ""); }

  /* ---------------- стан ---------------- */
  var on = function (name, fn) { bridge.on(name, function (d) { try { fn(d == null ? "" : String(d)); } catch (e) { console.error(e); } }); };

  // стандартний HUD GTA (гроші, зброя, здоров'я, годинник, зірки, назви) — ховаємо, бо є свій
  var gtaHidden = null;
  function gtaHud(visible) {
    if (!live || gtaHidden === !visible) return;
    gtaHidden = !visible;
    ["interface", "vehicle_name", "area_name"].forEach(function (c) { bridge.emit("game:hud:setComponentVisible", c, visible); });
  }
  on("reset", function (d) {
    document.body.classList.toggle("off", d !== "1");
    gtaHud(d !== "1");
    if (d !== "1") { closeDialog(); closePanels(); }
  });
  on("auth", function (d) {
    var a = d === "1";
    show($("auth"), a);
    document.body.classList.toggle("authmode", a);
    show($("hud"), !a);
  });

  /* ---------------- HUD ---------------- */
  var lastCash = null;
  on("hud", function (d) {
    var p = d.split("|");
    // HUD приходить лише після входу - ховаємо екран входу
    show($("auth"), false);
    document.body.classList.remove("authmode");
    show($("hud"), true); show($("idCard"), true);
    $("hCash").textContent = money(p[0]).replace("$", "");
    $("hBank").textContent = money(p[1]);
    $("hLvl").textContent = p[2];
    show($("hVip"), p[3] === "1");
    var eat = Math.max(0, Math.min(100, +p[4])), hp = Math.max(0, Math.min(100, +p[6])), ar = Math.max(0, Math.min(100, +p[7])), w = +p[8];
    $("bEat").style.width = eat + "%"; $("tEat").textContent = eat + "%";
    $("bEat").parentNode.parentNode.classList.toggle("low", eat < 25);
    $("bHp").style.width = hp + "%"; $("tHp").textContent = hp + "%";
    $("bAr").style.width = ar + "%"; $("tAr").textContent = ar + "%";
    show($("arRow"), ar > 0);
    var now = new Date(), dd = ("0" + now.getDate()).slice(-2) + "." + ("0" + (now.getMonth() + 1)).slice(-2) + "." + now.getFullYear();
    $("hTime").textContent = dd + " " + p[9];
    $("hName").textContent = p[10] || "";
    $("hId").textContent = p[11];
    var wl = $("hWanted");
    show(wl, w > 0);
    wl.textContent = w > 0 ? "★★★★★★".slice(0, w) : "";
    lastCash = +p[0];
    var wid = +p[12] || 0, ammo = +p[13] || 0;
    $("hWIco").style.setProperty("--i", "url(img/" + weapIco(wid) + ".svg)");
    $("hAmmo").textContent = (wid >= 16 && wid <= 43 && wid !== 40) ? ammo : "";
    $("hWeap").title = WEAP[wid] || "Кулаки";
    gtaHud(false);
  });
  function weapIco(w) {
    if (!w) return "w_fist";
    if (w === 4 || w === 8 || w === 9) return "w_knife";
    if (w <= 15) return "w_melee";
    if (w <= 18 || w === 39) return "w_grenade";
    if (w <= 24) return "w_pistol";
    if (w <= 27) return "w_shotgun";
    if (w === 28 || w === 29 || w === 32) return "w_smg";
    if (w === 30 || w === 31) return "w_rifle";
    if (w === 33 || w === 34) return "w_sniper";
    return "w_rifle";
  }
  var WEAP = {1:"Кастет",2:"Ключка",3:"Кийок",4:"Ніж",5:"Біта",6:"Лопата",7:"Кий",8:"Катана",9:"Бензопила",10:"Дилдо",11:"Дилдо",12:"Вібратор",13:"Вібратор",14:"Квіти",15:"Тростина",
    16:"Граната",17:"Сльозогінний газ",18:"Коктейль Молотова",22:"Пістолет",23:"Пістолет з глушником",24:"Desert Eagle",25:"Дробовик",26:"Обріз",27:"Бойовий дробовик",28:"Micro Uzi",29:"MP5",
    30:"АК-47",31:"M4",32:"Tec-9",33:"Гвинтівка",34:"Снайперська гвинтівка",35:"РПГ",36:"Ракетниця",37:"Вогнемет",38:"Мініган",39:"Сумка C4",40:"Детонатор",41:"Балончик",42:"Вогнегасник",43:"Фотоапарат",44:"ПНБ",45:"Тепловізор",46:"Парашут"};

  /* ---------------- чипи ---------------- */
  // chip|k|on|текст   k: 0 GPS, 1 зміна, 2 зелена зона
  on("chip", function (d) {
    var p = d.split("|"), k = +p[0], vis = p[1] === "1", t = p.slice(2).join("|");
    if (k === 0) { show($("chipGps"), vis); if (vis && t) $("chipGpsT").textContent = t; }
    else if (k === 1) { show($("chipShift"), vis); if (vis && t) { var q = t.split("~"); $("chipShiftJ").textContent = q[0]; $("chipShiftN").textContent = q[1] || ""; } }
    else show($("chipGz"), vis);
  });
  var deltaT;
  on("money", function (d) {
    var n = +d, el = $("hDelta");
    el.className = ""; void el.offsetWidth;
    el.textContent = (n > 0 ? "+" : "−") + money(Math.abs(n));
    el.className = "show " + (n > 0 ? "plus" : "minus");
    clearTimeout(deltaT);
    deltaT = setTimeout(function () { el.className = ""; }, 2700);
  });

  /* ---------------- спідометр ---------------- */
  var arc = $("spArc"), arcLen = 0, MAXS = 240;
  (function ticks() {
    var g = $("spTicks"), ns = "http://www.w3.org/2000/svg";
    // дуга 270°: від 135° до 405° (0 = вправо), центр 100,100, r 80
    for (var v = 0; v <= MAXS; v += 20) {
      var a = (135 + 270 * v / MAXS) * Math.PI / 180, c = Math.cos(a), s = Math.sin(a);
      var big = v % 40 === 0, l = document.createElementNS(ns, "line");
      l.setAttribute("x1", 100 + c * 66); l.setAttribute("y1", 100 + s * 66);
      l.setAttribute("x2", 100 + c * (big ? 59 : 62)); l.setAttribute("y2", 100 + s * (big ? 59 : 62));
      g.appendChild(l);
    }
  })();
  function setSpeed(v) {
    if (!arcLen) { arcLen = arc.getTotalLength(); arc.style.strokeDasharray = arcLen; }
    var k = Math.max(0, Math.min(1, v / MAXS));
    arc.style.strokeDashoffset = arcLen * (1 - k);
    $("spNum").textContent = v;
    $("spNum").classList.toggle("fast", v >= 160);
  }
  on("spdon", function (d) {
    document.body.classList.add("driving");
    var p = d.split("|");
    $("spName").textContent = p[0];
    $("speedo").classList.toggle("bike", p[1] === "1");
    show($("speedo"), true);
  });
  on("spdoff", function () { show($("speedo"), false); document.body.classList.remove("driving"); });
  on("spd", function (d) {
    var p = d.split("|").map(Number);
    show($("speedo"), true);
    setSpeed(p[0]);
    var f = $("spFuel"), h = $("spHp");
    f.style.height = Math.max(0, Math.min(100, p[1])) + "%"; f.classList.toggle("low", p[1] < 15);
    h.style.height = Math.max(0, Math.min(100, p[2])) + "%"; h.classList.toggle("low", p[2] < 35);
    $("sEng").classList.toggle("on", p[3] === 1);
    $("sLig").classList.toggle("on", p[4] === 1);
    $("sLock").classList.toggle("on", p[5] === 1);
    $("sBelt").classList.toggle("on", p[6] === 1);
  });

  /* ---------------- діалоги ---------------- */
  var dlg = null; // {id, style, items:[], sel}
  function closeDialog() { dlg = null; show($("dlgWrap"), false); unfocus(); }
  on("dlgclose", closeDialog);
  on("dlg", function (d) {
    var p = d.split(SEP);
    var id = +p[0], style = +p[1], authScr = p[2] === "1", title = p[3] || "", b1 = p[4] || "", b2 = p[5] || "", body = p.slice(6).join(SEP);
    closePanels(true);
    dlg = { id: id, style: style, items: [], sel: 0 };
    $("dTitle").innerHTML = samp(title);
    var list = style === 2 || style === 4 || style === 5;
    var input = style === 1 || style === 3;
    $("dlg").classList.toggle("wide", style === 4 || style === 5);
    $("dBody").innerHTML = list ? "" : samp(body).replace(/\t/g, "    ");
    show($("dList"), list);
    show($("dInWrap"), input);
    show($("dEye"), style === 3);
    $("dEye").classList.remove("on");
    $("dIn").type = style === 3 ? "password" : "text";
    $("dIn").value = "";
    $("dB1").innerHTML = samp(b1);
    $("dB2").innerHTML = samp(b2);
    show($("dB1"), b1.length > 0);
    show($("dB2"), b2.length > 0);
    if (authScr) { show($("auth"), true); document.body.classList.add("authmode"); show($("hud"), false); }

    if (list) {
      var L = $("dList"); L.innerHTML = "";
      var rows = body.split("\n");
      if (rows.length && rows[rows.length - 1] === "") rows.pop();
      if (style === 5 && rows.length) {
        L.appendChild(makeRow(rows.shift(), -1, true));
      }
      rows.forEach(function (r, i) { dlg.items.push(r); L.appendChild(makeRow(r, i, false)); });
      select(0);
    }
    show($("dlgWrap"), true);
    syncFocus();
    if (input) setTimeout(function () { $("dIn").focus(); }, 30);
  });
  function makeRow(text, i, head) {
    var row = document.createElement("div");
    row.className = "row" + (head ? " head" : "");
    var cols = text.split("\t");
    cols.forEach(function (c) { var s = document.createElement("div"); s.className = "cell"; s.innerHTML = samp(c); row.appendChild(s); });
    if (!head) {
      row.dataset.i = i;
      row.addEventListener("click", function () { select(i); });
      row.addEventListener("dblclick", function () { select(i); respond(1); });
    }
    return row;
  }
  function select(i) {
    if (!dlg || !dlg.items.length) return;
    dlg.sel = (i + dlg.items.length) % dlg.items.length;
    var rows = $("dList").querySelectorAll(".row:not(.head)");
    rows.forEach(function (r, k) { r.classList.toggle("sel", k === dlg.sel); });
    var el = rows[dlg.sel]; if (el && el.scrollIntoView) el.scrollIntoView({ block: "nearest" });
  }
  function respond(r) {
    if (!dlg) return;
    var item = -1, text = "";
    if (dlg.style === 2 || dlg.style === 4 || dlg.style === 5) {
      item = dlg.sel;
      text = plain((dlg.items[item] || "").split("\t")[0]);
    } else if (dlg.style === 1 || dlg.style === 3) {
      text = $("dIn").value.replace(/[\r\n]/g, " ").slice(0, 127);
    }
    var id = dlg.id;
    closeDialog();
    send("dlg " + id + " " + r + " " + item + " " + text);
  }
  $("dB1").addEventListener("click", function () { respond(1); });
  $("dB2").addEventListener("click", function () { respond(0); });
  $("dX").addEventListener("click", function () { respond(0); });
  $("dEye").addEventListener("click", function () {
    var i = $("dIn"), v = i.type === "password";
    i.type = v ? "text" : "password"; this.classList.toggle("on", v); i.focus();
  });

  /* ---------------- телефон ---------------- */
  var APPS = [
    ["Дзвінок", "📞", "#34C759"], ["SMS", "💬", "#0A84FF"], ["Банк", "🏦", "#FFB300"],
    ["GPS", "🧭", "#FF453A"], ["Таксі", "🚕", "#FFCC00"], ["Механік", "🔧", "#8E6E53"],
    ["Квести", "⭐", "#BF5AF2"], ["Профіль", "👤", "#30B0C7"], ["Оголошення", "📢", "#FF6B3D"],
    ["Мої авто", "🚗", "#5E5CE6"], ["Мій номер", "#", "#636366"], ["Налаштування", "⚙️", "#48484A"]
  ];
  (function buildPhone() {
    var g = $("pGrid");
    APPS.forEach(function (a, i) {
      var b = document.createElement("button");
      b.className = "app";
      b.innerHTML = '<span class="tile" style="background:linear-gradient(160deg,' + a[2] + ',' + shade(a[2]) + ')">' + a[1] + "</span>" + a[0];
      b.addEventListener("click", function () { send("ph " + i); closePanels(true); });
      g.appendChild(b);
    });
  })();
  function shade(hex) {
    var n = parseInt(hex.slice(1), 16), r = (n >> 16) * .72 | 0, g = ((n >> 8) & 255) * .72 | 0, b = (n & 255) * .72 | 0;
    return "rgb(" + r + "," + g + "," + b + ")";
  }
  on("phone", function (d) {
    var p = d.split("|");
    closePanels(true);
    $("pTime").textContent = p[0]; $("pClock").textContent = p[0]; $("pDate").textContent = p[1];
    $("pName").textContent = p[2]; $("pNum").textContent = p[3] && p[3] !== "0" ? p[3] : "—"; $("pBank").textContent = money(p[5]);
    show($("phone"), true);
    document.body.classList.add("phone-open");
    syncFocus();
  });
  $("pHome").addEventListener("click", function () { send("ph -1"); closePanels(true); });

  /* ---------------- інвентар ---------------- */
  // [назва, іконка, вага кг, опис, можна викинути/передати, колір]
  var ITEMS = [
    ["Смартфон", "phone", 0.2, "Дзвінки, SMS, банк, GPS і таксі. Відкрити також клавішею H.", 0, "#6FA8FF"],
    ["Паспорт", "passport", 0.05, "Документ громадянина. Показати іншому: /pass [id].", 0, "#6FA8FF"],
    ["Ліцензії", "lic", 0.02, "Посвідчення водія та дозвіл на зброю. Показати: /lic [id].", 0, "#6FA8FF"],
    ["Аптечка", "medkit", 0.5, "Відновлює здоров'я до 100.", 1, "#FF5A6A"],
    ["Бинт", "bandage", 0.1, "Зупиняє кровотечу і трохи лікує.", 1, "#FF8A95"],
    ["Їжа", "food", 0.3, "Знімає голод.", 1, "#FFB547"],
    ["Сигарети", "cig", 0.05, "Пачка сигарет. Шкодить здоров'ю.", 1, "#C9B79C"],
    ["Каністра", "canister", 5, "20 літрів пального. Використайте біля свого авто.", 1, "#FF6B3D"],
    ["Ремкомплект", "repair", 2, "Швидкий ремонт авто прямо на дорозі.", 1, "#FFC94D"],
    ["Вудка", "rod", 1.2, "Рибалка на пірсі: ALT біля води.", 1, "#4FD1C5"],
    ["Риба", "fish", 0.8, "Можна продати в магазині 24/7.", 1, "#4FD1C5"],
    ["Гроші", "money", 0, "Готівка в кишені. Передати: /pay [id] [сума].", 0, "#5BE38E"]
  ];

  var MAXW = 30, SLOTS = 42, inv = null, tab = "all", selKey = null;
  function gi(name, extra) { return '<i class="gi" style="--i:url(img/' + name + '.svg)"></i>' + (extra || ""); }
  function setGi(el, name) { el.style.setProperty("--i", "url(img/" + name + ".svg)"); }
  function cnt(i) { return inv ? (+inv[2 + i] || 0) : 0; }
  function accOn() { return inv && inv[17] === "1"; }
  function weight() { var w = 0; ITEMS.forEach(function (it, i) { if (i !== 11) w += it[2] * cnt(i); }); return w; }
  function cellsFor(t) {
    var out = [];
    if (t === "all") ITEMS.forEach(function (it, i) { if (cnt(i) > 0) out.push({ k: "i" + i, i: i }); });
    else if (t === "docs") [1, 2].forEach(function (i) { if (cnt(i) > 0) out.push({ k: "i" + i, i: i }); });
    else if (t === "wear") {
      out.push({ k: "skin", img: "shirt", name: "Одяг", desc: "Скін #" + inv[14] + ". Змінити — у магазині одягу (/gps).", q: "" });
      if (+inv[15]) out.push({ k: "hat", img: "cap", name: "Головний убір", desc: "Аксесуар. «Використати» — зняти або вдягнути.", q: accOn() ? "вдягнуто" : "знято", acc: 1 });
      if (+inv[16]) out.push({ k: "gl", img: "glasses", name: "Окуляри", desc: "Аксесуар. «Використати» — зняти або вдягнути.", q: accOn() ? "вдягнуто" : "знято", acc: 1 });
    } else {
      out.push({ k: "cash", info: 1, img: "money", name: "Готівка", v: money(inv[0]) });
      out.push({ k: "bank", info: 1, img: "lock", name: "Банк", v: money(inv[1]) });
      out.push({ k: "w", info: 1, img: "bag", name: "Вага", v: weight().toFixed(1) + " кг" });
    }
    return out;
  }
  function eqCell(img, on, label, act) {
    return '<div class="eq' + (on ? "" : " off") + (act ? " click" : "") + '"' + (act ? ' data-act="' + act + '"' : "") + ">" + gi(img) + "<small>" + label + "</small></div>";
  }
  function render() {
    var g = $("iGrid"), cells = cellsFor(tab);
    g.innerHTML = "";
    if (selKey && !cells.some(function (c) { return c.k === selKey; })) { selKey = null; closeMenu(); }
    var n = Math.max(SLOTS, Math.ceil(cells.length / 7) * 7);
    for (var k = 0; k < n; k++) {
      var c = cells[k], b = document.createElement("button");
      if (!c) { b.className = "cell empty"; g.appendChild(b); continue; }
      if (c.info) {
        b.className = "cell info";
        b.innerHTML = "<div><small>" + c.name + "</small><b>" + c.v + "</b></div>" + gi(c.img);
      } else {
        var img = c.i != null ? ITEMS[c.i][1] : c.img, q = c.i != null ? (c.i === 11 ? "" : cnt(c.i)) : "";
        b.className = "cell" + (c.k === selKey ? " sel" : "");
        b.innerHTML = gi(img) + '<span class="q">' + q + "</span>";
        (function (cc, el) {
          el.addEventListener("click", function (e) { e.stopPropagation(); selKey = cc.k; render(); openMenu(cc, $("iGrid").querySelector(".cell.sel")); });
          el.addEventListener("dblclick", function () { selKey = cc.k; doUse(); });
        })(c, b);
      }
      g.appendChild(b);
    }
    var hat = +inv[15], gl = +inv[16];
    $("iEqL").innerHTML = eqCell("cap", hat && accOn(), "Голова", hat ? "acc" : "") + eqCell("glasses", gl && accOn(), "Окуляри", gl ? "acc" : "") +
      eqCell("shirt", 1, "Верх") + eqCell("jeans", 1, "Низ") + eqCell("shoe", 1, "Взуття");
    $("iEqR").innerHTML = eqCell("phone", cnt(0) > 0, "Телефон", cnt(0) ? "u0" : "") + eqCell("passport", cnt(1) > 0, "Паспорт", cnt(1) ? "u1" : "") +
      eqCell("lic", cnt(2) > 0, "Ліцензії", cnt(2) ? "u2" : "") + eqCell("rod", cnt(9) > 0, "Вудка") + eqCell("canister", cnt(7) > 0, "Каністра");
    [$("iEqL"), $("iEqR")].forEach(function (col) {
      col.querySelectorAll(".eq.click").forEach(function (el) {
        el.addEventListener("click", function () {
          var a = el.dataset.act;
          if (a === "acc") send("invacc");
          else { send("inv " + a.slice(1)); closePanels(true); }
        });
      });
    });
    $("iSkin").textContent = "Скін #" + inv[14];
    $("iCash").textContent = money(inv[0]); $("iBank").textContent = money(inv[1]);
    var w = weight(); $("iW").textContent = w.toFixed(1); $("iWBar").style.width = Math.min(100, w / MAXW * 100) + "%";
  }
  var menuFor = null;
  function openMenu(c, el) {
    menuFor = c;
    var it = c.i != null ? ITEMS[c.i] : null, m = $("iMenu");
    setGi($("mIco"), it ? it[1] : c.img);
    $("mName").textContent = it ? it[0] : c.name;
    $("mQ").textContent = it ? (c.i === 11 ? money(cnt(11)) : cnt(c.i) + " шт. · " + (it[2] * cnt(c.i)).toFixed(2).replace(/\.?0+$/, "") + " кг") : (c.q ? c.q : "");
    $("mDesc").textContent = it ? it[3] : c.desc;
    $("aUse").disabled = !(it ? c.i !== 11 && c.i !== 10 : !!c.acc);
    $("aDrop").disabled = $("aGive").disabled = !(it && it[4] === 1);
    show(m, true);
    if (el) {
      var r = el.getBoundingClientRect(), mw = m.offsetWidth, mh = m.offsetHeight;
      var x = r.right + 10, y = r.top;
      if (x + mw > innerWidth - 10) x = r.left - mw - 10;
      if (y + mh > innerHeight - 10) y = innerHeight - mh - 10;
      m.style.left = x + "px"; m.style.top = y + "px";
    }
  }
  function closeMenu() { menuFor = null; show($("iMenu"), false); }
  $("iMenu").addEventListener("click", function (e) { e.stopPropagation(); });
  $("inv").addEventListener("click", function () { if (menuFor) { closeMenu(); selKey = null; render(); } });
  function doUse() {
    var c = menuFor || cellsFor(tab).filter(function (x) { return x.k === selKey; })[0];
    if (!c) return;
    if (c.acc) { send("invacc"); return; }
    if (c.i == null || c.i === 11 || c.i === 10) return;
    if (c.i <= 2) { send("inv " + c.i); closePanels(true); return; }   // телефон / документи - закриваємо інвентар
    send("invuse " + c.i);
  }
  $("aUse").addEventListener("click", doUse);
  $("aDrop").addEventListener("click", function () { if (menuFor && menuFor.i != null) send("invdrop " + menuFor.i); });
  $("aGive").addEventListener("click", function () { if (menuFor && menuFor.i != null) send("invgive " + menuFor.i); });
  $("iTabs").querySelectorAll("button").forEach(function (b) {
    b.addEventListener("click", function (e) {
      e.stopPropagation();
      tab = b.dataset.t; selKey = null; closeMenu();
      $("iTabs").querySelectorAll("button").forEach(function (x) { x.classList.toggle("on", x === b); });
      render();
    });
  });
  // вікно персонажа: сервер малює модель скіна саме в цьому прямокутнику (координати 640x448)
  function sendPreview() {
    var r = $("iChar").getBoundingClientRect(), W = window.innerWidth, H = window.innerHeight;
    if (!r.width) return;
    // «вікно» у фоні, щоб модель персонажа з гри було видно
    var x = Math.round(r.left), y = Math.round(r.top), w = Math.round(r.width), h = Math.round(r.height);
    $("inv").querySelector(".iv-bg").style.clipPath = 'path(evenodd, "M0 0 H' + W + ' V' + H + ' H0 Z M' + x + ' ' + y + ' h' + w + ' v' + h + ' h-' + w + ' Z")';
    send("invpv " + Math.round(r.left / W * 6400) + " " + Math.round(r.top / H * 4480) + " " + Math.round(r.width / W * 6400) + " " + Math.round(r.height * 0.95 / H * 4480));
  }
  on("inv", function (d) {
    var wasOpen = !$("inv").classList.contains("hidden");
    inv = d.split("|");
    if (!wasOpen) { closePanels(true); tab = "all"; selKey = null; closeMenu(); $("iTabs").querySelectorAll("button").forEach(function (x) { x.classList.toggle("on", x.dataset.t === "all"); }); }
    show($("inv"), true);
    document.body.classList.add("inv-open");
    render();
    if (menuFor) { var still = cellsFor(tab).filter(function (x) { return x.k === menuFor.k; })[0]; if (still) openMenu(still, $("iGrid").querySelector(".cell.sel")); else closeMenu(); }
    syncFocus();
    if (!wasOpen) requestAnimationFrame(function () { setTimeout(sendPreview, 260); });
  });
  $("iX").addEventListener("click", function (e) { e.stopPropagation(); send("inv -1"); closePanels(true); });
  $("iList").addEventListener("click", function (e) { e.stopPropagation(); send("inv 99"); closePanels(true); });

  /* ---------------- документи ---------------- */
  var docT;
  on("doc", function (d) {
    var p = d.split("|"), el = $("doc"), h = "";
    if (p[0] === "pass") {
      // pass|ім'я|стать|вік|робота|фракція|номер|рівень|NICK
      var ini = p[1].split(" ").map(function (w) { return w.charAt(0); }).join("").slice(0, 2);
      h = '<div class="doc-head pass"><b>УКРАЇНА</b><span>ПАСПОРТ ГРОМАДЯНИНА</span></div>' +
        '<div class="doc-body"><div class="doc-photo">' + esc(ini) + '</div><div class="doc-fields">' +
        f("Ім'я", p[1]) + f("Стать / вік", p[2] + ", " + p[3]) + f("Робота", p[4]) + f("Організація", p[5]) +
        '</div><div class="doc-fields right">' + f("Номер", "UA " + p[6]) + f("У штаті", p[7] + " р.") + '</div></div>' +
        '<div class="doc-mrz">P&lt;UKR' + esc(p[8].toUpperCase()).replace(/_/g, "&lt;") + '&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;</div>';
    } else {
      // lic|ім'я|водій 0/1|зброя 0/1
      h = '<div class="doc-head lic"><b>ЛІЦЕНЗІЇ</b><span>МВС УКРАЇНИ</span></div>' +
        '<div class="doc-body col"><div class="doc-fields">' + f("Власник", p[1]) + '</div>' +
        row("Посвідчення водія (B)", p[2] === "1", "Дійсне") + row("Дозвіл на зброю", p[3] === "1", "Дійсний") + '</div>';
    }
    el.innerHTML = h;
    show(el, true);
    clearTimeout(docT);
    docT = setTimeout(function () { show(el, false); }, 12000);
  });
  function f(k, v) { return '<div class="fld"><small>' + k + '</small><b>' + esc(v || "—") + '</b></div>'; }
  function row(k, ok, yes) { return '<div class="lic-row"><span>' + k + '</span><b class="' + (ok ? "ok" : "no") + '">' + (ok ? yes : "Немає") + '</b></div>'; }

  function closePanels(silent) { document.body.classList.remove("phone-open", "inv-open"); if ($("iMenu")) show($("iMenu"), false); show($("phone"), false); show($("inv"), false); unfocus(); }
  on("panel", function () { closePanels(true); });

  /* ---------------- клавіатура ---------------- */
  document.addEventListener("keydown", function (e) {
    if (dlg) {
      if (e.key === "Enter") { e.preventDefault(); respond(1); }
      else if (e.key === "Escape") { e.preventDefault(); respond(0); }
      else if ((dlg.style === 2 || dlg.style === 4 || dlg.style === 5) && (e.key === "ArrowDown" || e.key === "ArrowUp")) {
        e.preventDefault(); select(dlg.sel + (e.key === "ArrowDown" ? 1 : -1));
      }
      return;
    }
    if (e.key === "Escape" || e.key === "Backspace") {
      if (!$("phone").classList.contains("hidden")) { send("ph -1"); closePanels(true); }
      else if (!$("inv").classList.contains("hidden")) { send("inv -1"); closePanels(true); }
    }
  });

})();
