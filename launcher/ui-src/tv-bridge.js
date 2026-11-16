/* PureTV on the TV. Loaded after the Home screen's own script (the same one as the browser demo),
   it swaps the demo's sample data for the real TV through window.PureTV (Bridge.java):
   the installed apps, inputs, Settings screens, memory, weather, saved settings and software updates.
   Nothing here changes, clears or signs out of any app. */
(() => {
  const B = window.PureTV;
  if (!B) return;
  const J = (s, d) => { try { const v = JSON.parse(s); return v == null ? d : v; } catch (e) { return d; } };
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
  document.body.classList.add("ontv");

  /* ---------- the screen: laid out at 1920 x 1080, drawn at the panel's size by the WebView ---------- */
  const oldFit = fit; removeEventListener("resize", oldFit);
  fit = function () { const k = innerWidth / 1920; tv.style.transform = Math.abs(k - 1) > .01 ? `scale(${k})` : "none"; };
  addEventListener("resize", fit); fit();

  /* ---------- focus never falls off the screen: Home is always the bottom layer ---------- */
  const demoPop = pop;
  pop = function () { if (layers.length > 1) return demoPop(); cur = null; focus(items()[0]); };

  /* ---------- saved settings: profiles, top row, wallpaper, glass… kept in the WebView's storage ---------- */
  const KEY = "puretv.state.v1";
  let setupDone = false, parentPin = "1234", pinSetting = false;
  function snapshot() {
    return { v: 1, profiles: PROFILES, prof, scene, rotate, glassMode, wxMode, city, dockSize, topRow, hidden: [...hidden],
      liteMode, reduceMotion, sounds, recents, homeOn, helperOn, setupDone, pin: parentPin };
  }
  function applyState(s) {
    if (!s || s.v !== 1) return false;
    if (Array.isArray(s.profiles) && s.profiles.length) PROFILES.splice(0, PROFILES.length, ...s.profiles);
    prof = Math.max(0, Math.min(s.prof | 0, PROFILES.length - 1));
    if (typeof s.scene === "number") scene = s.scene;
    const bool = (k, d) => typeof s[k] === "boolean" ? s[k] : d;
    rotate = bool("rotate", rotate); reduceMotion = bool("reduceMotion", reduceMotion); sounds = bool("sounds", sounds);
    homeOn = bool("homeOn", homeOn); helperOn = bool("helperOn", helperOn); setupDone = bool("setupDone", setupDone);
    if (/^\d{4}$/.test(s.pin || "")) parentPin = s.pin;
    if (s.glassMode) glassMode = s.glassMode;
    if (s.wxMode) wxMode = s.wxMode;
    if (typeof s.city === "number") city = s.city;
    if (s.dockSize) dockSize = s.dockSize;
    if (Array.isArray(s.topRow)) topRow = s.topRow;
    if (Array.isArray(s.hidden)) hidden = new Set(s.hidden);
    if (s.liteMode) liteMode = s.liteMode;
    if (Array.isArray(s.recents)) recents = s.recents;
    PROFILES.forEach((p, i) => { if (!CW[i]) CW[i] = []; });
    setGlass(glassMode); applyLite(); stage.classList.toggle("calm", reduceMotion);
    $("#ccSnd").classList.toggle("on", sounds);
    return true;
  }
  function save() {
    try { const j = JSON.stringify(snapshot()); if (j !== save.last) { localStorage.setItem(KEY, j); save.last = j; } } catch (e) { }
  }
  setInterval(save, 2000);
  addEventListener("keyup", () => setTimeout(save, 50));

  /* ---------- apps: what is installed on this TV ---------- */
  const ART = new Set(["com.amazon.amazonvideo.livingroom", "com.apple.atve.androidtv.appletv", "com.cbs.ca", "com.crunchyroll.webapp", "com.disney.disneyplus",
    "com.google.android.youtube.tv", "com.netflix.ninja", "com.spotify.tv.android", "com.wbd.stream", "tv.twitch.android.app"]);
  const FAVS = ["com.google.android.youtube.tv", "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "in.startv.hotstar", "com.disney.disneyplus",
    "com.jio.media.stb.ondemand", "com.graymatrix.did", "com.sonyliv", "com.apple.atve.androidtv.appletv", "com.spotify.tv.android", "com.google.android.youtube.tvmusic"];
  const DEMO = new Map(APPS.map((a) => [a.pkg, a]));
  let appSig = "";
  function loadApps() {
    const list = J(B.apps(), []);
    const sig = list.map((x) => x.pkg).join(",");
    if (sig === appSig) return false;
    appSig = sig;
    const rank = (x) => { const f = FAVS.indexOf(x.pkg); return f >= 0 ? f : x.tv ? 100 : 200; };
    list.sort((a, b) => rank(a) - rank(b) || a.name.localeCompare(b.name));
    APPS.splice(0, APPS.length, ...list.map((x) => {
      const d = DEMO.get(x.pkg);
      if (d && ART.has(x.pkg)) return Object.assign({}, d, { name: x.name || d.name });
      return { pkg: x.pkg, name: x.name, bg: "#24262d", logo: `/logo/${x.pkg}.png`, dlogo: `/logo/${x.pkg}.png`, dark: true, drawn: true };
    }));
    const have = new Set(APPS.map((a) => a.pkg));
    topRow = topRow.filter((p) => have.has(p));
    for (const a of APPS) { if (topRow.length >= 7) break; if (!topRow.includes(a.pkg)) topRow.push(a.pkg); }
    recents = recents.filter((p) => have.has(p));
    hidden = new Set([...hidden].filter((p) => have.has(p)));
    setKids();
    return true;
  }
  artHTML = (a) => a.drawn ? `<img class="art" src="/icon/${a.pkg}.png" alt="" onerror="this.remove()">` : `<img class="art" src="demo/icons-tv/${a.pkg}.jpg" alt="">`;

  /* ---------- Kids: approved apps only. YouTube opens YouTube Kids ---------- */
  const YTK = "com.google.android.youtube.tvkids";
  function setKids() {
    Object.keys(KIDS).forEach((k) => delete KIDS[k]);
    const has = (p) => APPS.some((a) => a.pkg === p);
    if (has(YTK)) KIDS[YTK] = "YouTube Kids"; else if (has("com.google.android.youtube.tv")) KIDS["com.google.android.youtube.tv"] = "YouTube Kids";
    [["com.netflix.ninja", "Netflix"], ["com.disney.disneyplus", "Disney+"], ["in.startv.hotstar", "Hotstar"], ["com.amazon.amazonvideo.livingroom", "Prime Video"]]
      .forEach(([p, n]) => { if (has(p)) KIDS[p] = n; });
  }

  /* ---------- open an app: the icon grows, then Android opens it ---------- */
  const demoLaunch = launch;
  launch = function (el) {
    const pkg = el.dataset.app || el.dataset.rapp, a = app(pkg);
    if (!a) return;
    let target = pkg;
    if (isKids() && pkg === "com.google.android.youtube.tv") {
      if (!B.has(YTK)) { toast("YouTube Kids opens here once it is added from the Play Store"); return B.store(YTK); }
      target = YTK;
    }
    demoLaunch(el);
    $("#launch .note").textContent = "";
    setTimeout(() => { if (!B.launch(target)) { closeNow(); toast(`${a.name} can't be opened`); } }, liteOn() ? 120 : 300);
  };
  function closeNow() { const L = $("#launch"); L.className = ""; L.style.opacity = 0; launched = false; }

  /* ---------- Recent Apps and memory ---------- */
  renderMem = function () {
    const m = J(B.mem(), null); if (!m || !m.total) return;
    const used = (m.total - m.free) / 1024, tot = m.total / 1024, pct = Math.round(used / tot * 100);
    $("#ccMemV").textContent = `${pct}% used`; $("#ccMemBar").style.width = pct + "%";
    $("#ramV").textContent = `RAM ${used.toFixed(1)} GB used of ${tot.toFixed(1)} GB`; $("#ramBar").style.width = pct + "%";
  };
  const demoCloseRecent = closeRecent;
  closeRecent = function (card) { B.close(card.dataset.rapp); demoCloseRecent(card); };
  const demoRecentSelect = recentSelect;
  recentSelect = function (e) {
    if (e.id !== "closeAll") return demoRecentSelect(e);
    const before = J(B.mem(), {}).free || 0, n = recents.length;
    recents.forEach((p) => B.close(p));
    $$(".rcard").forEach((c, i) => setTimeout(() => c.classList.add("gone"), i * 70));
    recents = []; snd("back");
    setTimeout(() => {
      closeRecents(); renderMem();
      const freed = Math.max(0, (J(B.mem(), {}).free || 0) - before);
      if (n) toast(`Closed ${n} app${n > 1 ? "s" : ""}${freed > 20 ? ` · ${freed} MB of memory freed` : ""}`);
    }, 420 + n * 70);
  };

  /* ---------- the TV's own screens ---------- */
  openGtv = function (page) { B.settings(page === "appinfo" ? "appinfo:" + appInfoPkg : page === "settings" ? "" : page || ""); };
  let INPUT_IDS = [];
  function loadInputs() {
    const list = J(B.inputs(), []);
    INPUT_IDS = list.map((x) => x.id);
    INPUTS.splice(0, INPUTS.length, ...list.map((x) => [x.name, x.pass ? "Input" : "TV channels"]));
    if (!INPUTS.length) INPUTS.push(["Inputs", "Open the TV's input list"]);
  }
  function openLive() {
    const list = J(B.inputs(), []), tuner = list.find((x) => !x.pass) || list[0];
    if (tuner) B.input(tuner.id); else toast("This TV has no tuner or inputs to show");
  }
  const demoSelect = select;
  select = function () {
    const e = cur;
    if (e && e.closest("#home") && !e.closest("#menu")) {
      if (e.dataset.act === "search") return B.search();
      if (e.dataset.act === "tab-live") return openLive();
    }
    return demoSelect();
  };
  let sleepTimer = null;
  const demoCC = ccSelect;
  ccSelect = function (e) {
    if (e.dataset.pick !== undefined && ccPage && ccPage.kind === "inputs") {
      closeCC(); const id = INPUT_IDS[+e.dataset.pick];
      return id ? B.input(id) : B.settings("");
    }
    if (e.dataset.pick !== undefined && ccPage && ccPage.kind === "audio") { closeCC(); return B.settings("sound"); }
    const c = e.dataset.cc;
    if (c === "inputs") loadInputs();
    if (c === "audio") { closeCC(); return B.settings("sound"); }
    if (c === "bt") { closeCC(); return B.settings("bluetooth"); }
    if (c === "search") { closeCC(); return B.search(); }
    if (c === "power") { closeCC(); if (!B.sleep()) toast("Turn on Settings › Home Screen › Remote Helper to switch the TV off from here"); return; }
    demoCC(e);
    if (c === "sleep") {
      clearTimeout(sleepTimer);
      const mins = [0, 15, 30, 60, 120][sleepIx] || 0;
      if (mins) sleepTimer = setTimeout(() => { if (!B.sleep()) toast("Sleep timer: time to switch the TV off"); }, mins * 60000);
    }
  };

  /* ---------- Settings pages that read the real TV ---------- */
  PAGES.network.groups = () => [[null, [{ k: "wifi", t: "Wi-Fi & Network", sub: "Opens the TV's network settings", go: null, act: "tv", arg: "network" }]]];
  PAGES.bluetooth.groups = () => [[null, [{ k: "bt", t: "Remotes & Accessories", sub: "Pair a remote, keyboard or headphones", act: "tv", arg: "bluetooth" }]]];
  PAGES.display.groups = () => [[null, [{ k: "pic", t: "Picture & Display", act: "tv", arg: "display" }, { k: "snd", t: "Sound", act: "tv", arg: "sound" },
    { k: "tvapps", t: "Apps", act: "tv", arg: "apps" }, { k: "acc", t: "Accessibility", act: "tv", arg: "access" }, { k: "date", t: "Date & Time", act: "tv", arg: "date" },
    { k: "all", t: "All TV Settings", act: "tv", arg: "settings" }]]];
  const demoHome = PAGES.homescreen.groups;
  PAGES.homescreen.groups = () => demoHome().filter(([h]) => h !== "Shelf").map(([h, rows]) => h !== "Home Button" ? [h, rows] : [h, [
    { k: "hm", t: "Home Button Opens PureTV", sub: B.isHome() ? "PureTV is this TV's Home app" : B.helperOn() ? "Through the Remote Helper" : "Needs the Remote Helper below", v: onOff(B.isHome() || B.takeHome()), act: "homeOn" },
    { k: "hh", t: "Remote Helper", sub: "Settings › System › Accessibility › PureTV Remote Helper", v: onOff(B.helperOn()), act: "helper" }]]);
  PAGES.switched.groups = () => {
    const off = J(B.disabled(), []), m = J(B.mem(), {});
    const rows = [{ k: "h-off", t: "Apps switched off", v: String(off.length), act: "info" }];
    if (m.total) rows.push({ k: "h-mem", t: "Memory in use", v: `${((m.total - m.free) / 1024).toFixed(1)} GB of ${(m.total / 1024).toFixed(1)} GB`, act: "info" });
    return [["This TV", rows], ...(off.length ? [["Switched Off", off.map((x) => ({ k: "x-" + x.pkg, t: x.name, sub: x.pkg, v: "Off", act: "offinfo" }))]] : [])];
  };
  PAGES.switched.foot = "The PureTV agent on your computer switches apps off. It never uninstalls them, and their data stays on the TV.";
  PAGES.profile.foot = "Backups hold your profiles, top row, hidden apps and settings, and stay on this TV.";

  const demoSet = setSelect;
  setSelect = function (e) {
    const k = e.dataset.set, r = ROWS[k];
    if (k === "auto") { B.setAutoUpdate(!B.autoUpdate()); return renderSettings(); }
    if (r) switch (r.act) {
      case "homeOn": {
        const on = !B.takeHome(); B.setTakeHome(on); homeOn = on; renderSettings();
        return toast(on ? (B.isHome() || B.helperOn() ? "The Home button opens PureTV" : "Now turn on the Remote Helper, so the Home button opens PureTV") : "The Home button opens Google TV's home");
      }
      case "helper": return helperPrompt(true);
      case "backup": { const ok = B.backup(JSON.stringify(snapshot())); if (ok) lastBackup = "Just now"; renderSettings(); return toast(ok ? "Backed up: profiles, top row, hidden apps and settings" : "The backup could not be saved"); }
      case "brestore": {
        const s = J(B.restoreBackup(), null); if (!s) return toast("There is no backup yet");
        applyState(s); loadApps(); save(); rebuildForProfile(); showScene(scene); renderSide(); renderSettings(); return toast("Restored from your backup");
      }
      case "recheck": { const n = J(B.disabled(), []).length; return toast(n ? `${n} apps are switched off. After a TV update, ask the PureTV agent to check again` : "Nothing is switched off on this TV yet"); }
      case "offinfo": return toast("Switched off, not uninstalled. The PureTV agent turns it back on");
      case "confirm":
        if (armed !== k) break;
        armed = null; renderSettings();
        if (k === "gtvhome") { if (!B.googleHome()) toast("Google TV's home is switched off on this TV. Ask the PureTV agent to restore it"); return; }
        return toast("Restoring is done by the PureTV agent on your computer: ask it to “restore my TV”. Apps and sign-ins are kept");
    }
    return demoSet(e);
  };
  const demoLevel = renderSettings;
  renderSettings = function () { if (setLevel === "profile") { const t = B.backupTime(); lastBackup = t ? new Date(t).toLocaleString("en-GB", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" }) : "Never"; } return demoLevel(); };

  /* ---------- weather: open-meteo.com (no account), the city from your connection or the one you pick ---------- */
  const COORDS = { "New Delhi": [28.61, 77.21], Mumbai: [19.08, 72.88], London: [51.51, -0.13], "New York": [40.71, -74.01], Tokyo: [35.68, 139.69], Sydney: [-33.87, 151.21] };
  let wxOK = false, wxAt = 0, wxFor = "", here = null;
  const kind = (c) => c <= 1 ? "sun" : c === 2 ? "partly" : c === 3 || c === 45 || c === 48 ? "cloud" : "rain";
  const word = (c) => c === 0 ? "Clear" : c <= 2 ? "Partly Cloudy" : c === 3 ? "Cloudy" : c <= 48 ? "Fog" : c <= 57 ? "Drizzle" : c <= 67 ? "Rain" : c <= 77 ? "Snow" : c <= 82 ? "Showers" : "Thunderstorms";
  async function loadWeather(force) {
    if (wxMode === "off") return;
    const want = wxMode === "city" ? CITIES[city] : "auto";
    if (!force && want === wxFor && Date.now() - wxAt < 30 * 60000) return;
    try {
      let at = COORDS[want];
      if (!at) {
        if (!here) { const g = await (await fetch("https://ipapi.co/json/")).json(); if (typeof g.latitude === "number") here = [g.latitude, g.longitude]; }
        at = here;
      }
      if (!at) throw new Error("no place");
      const u = `https://api.open-meteo.com/v1/forecast?latitude=${at[0]}&longitude=${at[1]}&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=5`;
      const j = await (await fetch(u)).json();
      WX.now = [Math.round(j.current.temperature_2m), kind(j.current.weather_code), word(j.current.weather_code)];
      WX.days = j.daily.time.map((t, i) => [i ? new Date(t + "T12:00").toLocaleDateString("en-GB", { weekday: "short" }) : "Today",
        Math.round(j.daily.temperature_2m_max[i]), Math.round(j.daily.temperature_2m_min[i]), kind(j.daily.weather_code[i])]);
      wxOK = true; wxAt = Date.now(); wxFor = want; renderAvatar(); tick();
    } catch (e) { wxOK = false; tick(); }
  }
  const demoTick = tick;
  tick = function () {   // no weather is shown until a real reading has arrived
    const m = wxMode; if (!wxOK) wxMode = "off";
    try { demoTick(); } finally { wxMode = m; }
  };
  setInterval(() => loadWeather(false), 10 * 60000);
  const demoSetup = setupSelect;
  setupSelect = function (e) { const r = demoSetup(e); if (e.dataset.wx) loadWeather(true); return r; };
  const demoSet2 = setSelect;
  setSelect = function (e) { const r = demoSet2(e); const row = ROWS[e.dataset.set]; if (row && row.act === "wx") loadWeather(true); return r; };

  /* ---------- wallpapers: only the one on screen and the next one are kept in memory ---------- */
  const sceneEls = $$(".scene");
  function keep(i, on) {
    $$("img", sceneEls[i]).forEach((im) => {
      if (on && !im.getAttribute("src") && im.dataset.src) im.src = im.dataset.src;
      if (!on && im.getAttribute("src")) { im.dataset.src = im.getAttribute("src"); im.removeAttribute("src"); }
    });
    const f = $(".fill", sceneEls[i]);
    if (f) { if (on && f.dataset.bg) { f.style.backgroundImage = f.dataset.bg; delete f.dataset.bg; } if (!on && f.style.backgroundImage) { f.dataset.bg = f.style.backgroundImage; f.style.backgroundImage = "none"; } }
  }
  const demoShow = showScene;
  showScene = function (i) {
    const n = SCENES.length, to = (i + n) % n;
    keep(to, true); keep((to + 1) % n, true);   // the next one decodes now, so its crossfade is smooth
    demoShow(i);
    clearTimeout(showScene.t);   // once the crossfade is over, the others let go of their memory
    showScene.t = setTimeout(() => sceneEls.forEach((_, k) => { if (k !== scene && k !== (scene + 1) % n) keep(k, false); }), 3500);
  };
  window.tvLowMemory = () => sceneEls.forEach((_, k) => { if (k !== scene) keep(k, false); });

  /* ---------- software updates: found automatically, installed only when the owner says yes ---------- */
  let U = { state: "idle" }, fromAuto = false, waiting = null, installAfterAllow = false;
  const SNOOZE = "puretv.update.snooze";
  VERSION = B.version();
  renderUpdates = function () {
    const s = U.state, busy = s === "checking" || s === "downloading" || s === "installing";
    const val = s === "checking" ? '<span class="spin"></span>' : s === "latest" ? "Up to date" : s === "available" ? `Install PureTV ${esc(U.version)}`
      : s === "downloading" ? `<span>${U.pct || 0}%</span><span class="pbar"><i style="width:${U.pct || 0}%"></i></span>` : s === "installing" ? 'Installing<span class="spin"></span>'
      : s === "failed" ? "Try Again" : "";
    const title = s === "available" ? `PureTV ${esc(U.version)} is available` : s === "latest" ? `PureTV ${esc(VERSION)} is up to date` : "Check for Updates";
    const auto = B.autoUpdate();
    const rows = [[title, val, "upd"], ["Automatically Look for Updates", auto ? "On" : "Off", "auto"], ["Update Source", "GitHub Releases", "src"]];
    const had = cur && cur.dataset.set; let d = 0;
    $("#setList").innerHTML = rows.map(([t, v, act]) => `<div class="sr" data-f data-set="${act}" style="--d:${d++}"><span class="t">${t}</span><span class="v" style="display:flex;align-items:center">${v}</span>${CHEV}</div>`).join("")
      + (s === "available" && U.notes ? `<p class="foot upnotes">${esc(U.notes)}</p>` : "")
      + `<p class="foot">${auto ? "Whenever the TV is switched on, PureTV looks for a new version on its GitHub page and asks you before installing it." : "PureTV looks for a new version only when you ask."} Android then shows its own confirmation. Your apps, sign-ins, profiles and settings stay as they are.</p>`;
    if (had && inScope("#tvset")) { cur = null; focus($(`[data-set="${had}"]`)); }
    if (busy) $("#setList").classList.add("busy"); else $("#setList").classList.remove("busy");
  };
  runUpdate = function () {
    if (["checking", "downloading", "installing"].includes(U.state)) return;
    if (U.state === "available") return installUpdate();
    fromAuto = false; U = { state: "checking" }; renderSettings(); B.checkUpdate(false);
  };
  function installUpdate() {
    if (!B.canInstall()) {
      installAfterAllow = true;
      toast("Allow PureTV to install updates: switch it on, then press Back");
      return B.settings("unknown");
    }
    installAfterAllow = false;
    U = Object.assign({}, U, { state: "downloading", pct: 0 }); if (setLevel === "updates") renderSettings();
    toast(`Downloading PureTV ${U.version}…`);
    B.checkUpdate(true);
  }
  window.tvUpdate = function (json) {
    const u = J(json, {}); if (!u.state) return;
    if (u.state === "failed") toast(u.error === "signature" ? "Update refused: it was not signed by PureTV" : "The update could not be downloaded. PureTV will try again later");
    if (u.state === "installing") toast(`Installing PureTV ${u.version}. Confirm on the screen that opens`);
    U = Object.assign({}, u.state === "downloading" ? U : {}, u);
    if (u.state === "available" && fromAuto) askToUpdate();
    if ($("#tvset").classList.contains("on") && setLevel === "updates") renderSettings();
  };
  function snoozed(v) { try { const s = J(localStorage.getItem(SNOOZE), {}); return s.v === v && Date.now() < s.until; } catch (e) { return false; } }
  function askToUpdate() {
    if (snoozed(U.version)) return;
    if (!inScope("#home") || launched || isKids() || saver) { waiting = U; return; }
    waiting = null; held = null;
    const m = $("#menu");
    m.innerHTML = `<div class="uicon"><svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 4v11M7 10.5l5 5 5-5M5 20h14"/></svg></div>`
      + `<div class="mh pinh">PureTV ${esc(U.version)}</div><p class="upn">A new version of PureTV is ready to install. Your apps, sign-ins, profiles and settings stay exactly as they are.</p>`
      + (U.notes ? `<p class="upnotes">${esc(U.notes.split("\n").filter(Boolean).slice(0, 4).join(" · "))}</p>` : "")
      + `<div class="mi upb" data-f data-upd="now">Install Now</div><div class="mi upb" data-f data-upd="later">Later</div>`;
    m.style.left = "660px"; m.style.top = "230px"; m.style.bottom = "auto"; m.style.setProperty("--ox", "50%"); m.style.setProperty("--oy", "50%");
    stage.classList.add("menuon"); m.classList.add("on", "pinbox", "updbox"); push(m, $(".mi", m));
  }
  const demoMenu = menuSelect;
  menuSelect = function (e) {
    if (e.dataset.upd) {
      closeMenu();
      if (e.dataset.upd === "now") return installUpdate();
      try { localStorage.setItem(SNOOZE, JSON.stringify({ v: U.version, until: Date.now() + 20 * 3600000 })); } catch (x) { }
      return toast("Later it is. Settings › Software Updates installs it any time");
    }
    if (e.dataset.helper) { closeMenu(); if (e.dataset.helper === "open") { B.setTakeHome(true); B.openHelper(); } return; }
    return demoMenu(e);
  };
  const demoCloseMenu = closeMenu;
  closeMenu = function (keep) { if (!$("#menu").classList.contains("on")) return; demoCloseMenu(keep); const m = $("#menu"); setTimeout(() => { if (!m.classList.contains("on")) m.classList.remove("pinbox", "updbox"); }, 380); };
  function autoCheck() { if (["checking", "downloading", "installing"].includes(U.state)) return; if (B.updateDue()) { fromAuto = true; B.checkUpdate(false); } else if (waiting) askToUpdate(); }

  /* ---------- the Remote Helper: the Home button opens PureTV, with no computer ---------- */
  function helperPrompt(fromSettings) {
    if (B.helperOn()) { if (fromSettings) { toast("The Remote Helper is on. To turn it off: Accessibility › PureTV Remote Helper"); B.openHelper(); } return; }
    if (inScope("#tvset")) closeSettings();
    held = null; const m = $("#menu");
    m.innerHTML = `<div class="uicon home"><svg viewBox="0 0 24 24" fill="#fff"><path d="M12 3.2 2.8 11h2.7v9h5v-6h3v6h5v-9h2.7z"/></svg></div>`
      + `<div class="mh pinh">Make Home Open PureTV</div><p class="upn">Turn on <b>PureTV Remote Helper</b> in the screen that opens:<br>System › Accessibility › PureTV Remote Helper › On.<br>It only notices the Home button and which app is on screen.</p>`
      + `<div class="mi upb" data-f data-helper="open">Open Accessibility</div><div class="mi upb" data-f data-helper="later">Not Now</div>`;
    m.style.left = "660px"; m.style.top = "220px"; m.style.bottom = "auto"; m.style.setProperty("--ox", "50%"); m.style.setProperty("--oy", "50%");
    stage.classList.add("menuon"); m.classList.add("on", "pinbox", "updbox"); push(m, $(".mi", m));
  }
  const demoEnd = endSetup;
  endSetup = function () {
    demoEnd(); setupDone = true; B.setTakeHome(homeOn); save();
    if (homeOn && helperOn && !B.isHome() && !B.helperOn()) setTimeout(() => helperPrompt(false), 900);
  };

  /* ---------- parent PIN for the Kids profile (1234 until the owner changes it) ---------- */
  const demoOpenPin = openPin;
  openPin = function () {
    demoOpenPin();
    $("#menu .pinh").textContent = pinSetting ? "New Parent PIN" : "Parent PIN";
    $("#menu .pinhint").textContent = pinSetting ? "Choose four numbers" : parentPin === "1234" ? "The PIN is 1234 until you change it in Settings › Profile" : "Enter the parent PIN";
  };
  pinKey = function (k) {
    pin = k === "⌫" ? pin.slice(0, -1) : (pin + k).slice(0, 4);
    $$("#menu .pindots i").forEach((d, i) => d.classList.toggle("on", i < pin.length));
    if (pin.length < 4) return;
    if (pinSetting) { parentPin = pin; pinSetting = false; save(); closeMenu(); return toast("Parent PIN changed"); }
    if (pin === parentPin) { closeMenu(); return openSettings(); }
    const dots = $("#menu .pindots"); dots.classList.remove("shake"); void dots.offsetWidth; dots.classList.add("shake"); snd("edge");
    setTimeout(() => { pin = ""; $$("#menu .pindots i").forEach((d) => d.classList.remove("on")); }, 450);
  };
  const demoProfile = PAGES.profile.groups;
  PAGES.profile.groups = () => [...demoProfile(), ["Kids", [{ k: "setpin", t: "Parent PIN", sub: "Needed to open Settings from the Kids profile", v: "Change", act: "setpin" }]]];
  const demoSet3 = setSelect;
  setSelect = function (e) { const r = ROWS[e.dataset.set]; if (r && r.act === "setpin") { closeSettings(); pinSetting = true; return openPin(); } return demoSet3(e); };

  /* ---------- the remote, and coming back to Home ---------- */
  const key = (k) => dispatchEvent(new KeyboardEvent("keydown", { key: k, bubbles: true }));
  window.tvKey = function (k) {
    wake();
    if (k === "back") return key("Escape");
    if (k === "menu") return inScope("#home") || inScope("#cc") ? key("m") : key("Escape");
    if (k === "home") {
      if (launched) closeNow();
      if (saver) stopSaver();
      if (inScope("#setup")) return;
      closeOverlays(); if (full) exitFull();
      const h = $("#home"); layers = [{ root: h }]; cur = null; focus($("#row1 .tile"));
    }
  };
  window.tvResume = function () {
    if (launched) closeNow();
    if (loadApps()) { $("#home").classList.add("noanim"); buildHome(); if (inScope("#home")) { cur = null; focus($("#row1 .tile")); } }
    if (installAfterAllow && B.canInstall()) installUpdate();
    loadWeather(false); autoCheck(); wake();
  };
  window.tvPause = function () { clearTimeout(idle); clearTimeout(idleA); };
  window.tvWake = function () { if (saver) stopSaver(); autoCheck(); loadWeather(false); };

  /* ---------- start ---------- */
  CW.forEach((l) => l.splice(0, l.length)); cwOn = false;
  recents = [];
  let saved = null; try { saved = J(localStorage.getItem(KEY), null); } catch (e) { }
  applyState(saved);
  loadApps(); loadInputs();
  renderAvatar(); showScene(scene); buildHome(); tick();
  if (setupDone) demoEnd(); else runSetup(0);   // the Remote Helper prompt only follows setup itself
  loadWeather(true);
  setTimeout(autoCheck, 8000);
})();
