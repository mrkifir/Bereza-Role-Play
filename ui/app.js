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

  on("reset", function (d) {
    document.body.classList.toggle("off", d !== "1");
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
    show($("hud"), $("auth").classList.contains("hidden"));
    $("hCash").textContent = money(p[0]);
    $("hBank").textContent = money(p[1]);
    $("hLvl").textContent = p[2];
    show($("hVip"), p[3] === "1");
    var eat = +p[4], str = Math.min(100, +p[5]), hp = Math.min(100, +p[6]), ar = Math.min(100, +p[7]), w = +p[8];
    $("bEat").style.width = eat + "%";
    $("bEat").parentNode.parentNode.classList.toggle("low", eat < 25);
    $("bStr").style.width = str + "%";
    $("bHp").style.width = hp + "%";
    $("bAr").style.width = ar + "%";
    show($("arRow"), ar > 0);
    $("hTime").textContent = p[9];
    $("hId").textContent = p[11];
    var wl = $("hWanted");
    show(wl, w > 0);
    wl.textContent = w > 0 ? "★★★★★★".slice(0, w) : "";
    lastCash = +p[0];
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
    var p = d.split("|");
    $("spName").textContent = p[0];
    $("speedo").classList.toggle("bike", p[1] === "1");
    show($("speedo"), true);
  });
  on("spdoff", function () { show($("speedo"), false); });
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
  var ITEMS = [
    ["Смартфон", "📱"], ["Паспорт", "🪪"], ["Ліцензії", "📄"], ["Аптечки", "🩹"], ["Бинти", "🧻"], ["Їжа", "🍔"],
    ["Сигарети", "🚬"], ["Каністра", "⛽"], ["Ремкомплект", "🧰"], ["Вудка", "🎣"], ["Риба", "🐟"], ["Гроші", "💵"]
  ];
  on("inv", function (d) {
    var p = d.split("|");
    closePanels(true);
    $("iCash").textContent = money(p[0]); $("iBank").textContent = money(p[1]);
    var g = $("iGrid"); g.innerHTML = "";
    ITEMS.forEach(function (it, i) {
      var c = +p[2 + i] || 0, b = document.createElement("button");
      b.className = "slot" + (c > 0 ? "" : " empty");
      var cnt = i === 11 ? money(c) : (c > 0 ? "×" + c : "");
      b.innerHTML = '<span class="cnt">' + cnt + '</span><span class="emo">' + it[1] + '</span><span class="nm">' + it[0] + "</span>";
      b.addEventListener("click", function () { if (c <= 0 && i !== 11) return; send("inv " + i); closePanels(true); });
      g.appendChild(b);
    });
    show($("inv"), true);
    syncFocus();
  });
  $("iX").addEventListener("click", function () { send("inv -1"); closePanels(true); });
  $("iList").addEventListener("click", function () { send("inv 99"); closePanels(true); });

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

  function closePanels(silent) { document.body.classList.remove("phone-open"); show($("phone"), false); show($("inv"), false); unfocus(); }
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
