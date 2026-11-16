const $ = (id) => document.getElementById(id);
const el = (tag, cls, text) => { const e = document.createElement(tag); if (cls) e.className = cls; if (text != null) e.textContent = text; return e; };
const STEPS = ["connect", "backup", "clean", "launcher", "done"];
const S = { step: "connect", scan: null, picked: new Set(), done: new Set(), stats: { off: 0, kept: 0, launcher: false } };

async function api(path, body) {
  const r = await fetch(path, body === undefined ? {} : { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
  const data = await r.json().catch(() => ({ error: "PureTV stopped answering. Is its window still open?" }));
  if (!r.ok) throw new Error(data.error || "Something went wrong");
  return data;
}

function go(step) {
  S.step = step;
  for (const s of STEPS) $(s).hidden = s !== step;
  document.querySelectorAll("#steps button").forEach((b) => {
    const s = b.dataset.step;
    b.className = (s === step ? "on " : "") + (S.done.has(s) && s !== step ? "ok " : "") + (S.scan && s !== "connect" ? "reach" : "");
    b.querySelector("i").textContent = S.done.has(s) && s !== step ? "✓" : STEPS.indexOf(s) + 1;
  });
  window.scrollTo(0, 0);
  if (step === "done") renderDone();
}
document.querySelectorAll("#steps button").forEach((b) => b.onclick = () => { if (S.scan || b.dataset.step === "connect") go(b.dataset.step); });

/* Run a background job on the TV and show its log until it ends. */
async function job(title, path, body) {
  $("overlay").hidden = false; $("jobTitle").textContent = title; $("jobLog").textContent = "";
  $("jobErr").hidden = true; $("jobOk").hidden = true; $("spin").hidden = false;
  const finish = (err) => new Promise((res) => {
    $("spin").hidden = true; $("jobOk").hidden = false;
    if (err) { $("jobErr").textContent = err; $("jobErr").hidden = false; }
    $("jobOk").onclick = () => { $("overlay").hidden = true; res(); };
    $("jobOk").focus();
  });
  try {
    await api(path, body || {});
    for (;;) {
      const j = await api("/api/job");
      $("jobLog").textContent = j.log.join("\n"); $("jobLog").scrollTop = 1e9;
      if (!j.running) {
        if (j.error) { await finish(j.error); return null; }
        await finish(); return j.result || {};
      }
      await new Promise((r) => setTimeout(r, 500));
    }
  } catch (e) { await finish(e.message); return null; }
}

async function rescan() {
  S.scan = await api("/api/scan");
  const i = S.scan.info;
  $("chip").textContent = `${i.brand} ${i.model}`.trim() + (i.demo ? "" : ` · ${i.host}`); $("chip").hidden = false;
  $("restoreTop").hidden = !S.scan.backup;
  renderBackup(); renderClean(); renderLauncher();
}

/* ---- connect ---- */
async function connect(body) {
  $("connectErr").hidden = true; $("connectBtn").disabled = true; $("connectBtn").textContent = "Look at the TV…";
  try {
    await api("/api/connect", body);
    S.done.clear(); S.stats = { off: 0, kept: 0, launcher: false };
    await rescan(); S.done.add("connect");
    if (S.scan.backup) S.done.add("backup");
    go("backup");
  } catch (e) { $("connectErr").textContent = e.message; $("connectErr").hidden = false; }
  $("connectBtn").disabled = false; $("connectBtn").textContent = "Connect";
}
$("connectForm").onsubmit = (e) => { e.preventDefault(); connect({ host: $("host").value }); };
$("demoBtn").onclick = () => connect({ demo: true });
/* Wireless debugging with a pairing code: pair, connect, open 5555 (cleanyourtv's way), then connect as usual */
$("pairForm").onsubmit = async (e) => {
  e.preventDefault();
  const r = await job("Pairing with your TV", "/api/pair", { pair: $("pAddr").value, code: $("pCode").value, connect: $("cAddr").value });
  if (r && r.host) { $("host").value = r.host; connect({ host: r.host }); }
};
/* cleanyourtv's prompt finds the TV on the Wi-Fi first; so does this */
$("findBtn").onclick = async () => {
  const b = $("findBtn"), f = $("found"); b.disabled = true; b.textContent = "Searching your Wi-Fi…"; f.hidden = false; f.replaceChildren(el("p", "muted", "Looking for TVs with network debugging on. This takes a few seconds."));
  try {
    const r = await api("/api/discover", {}); f.replaceChildren();
    if (!r.tvs.length) f.append(el("p", "muted", `No TV answered on ${r.networks.join(", ") || "this network"}. Check USB debugging (and Network debugging) is on and the TV is awake. Google TV sets that only offer Wireless debugging need pairing first; type the address shown there instead.`));
    else {
      f.append(el("p", "muted", r.tvs.length > 1 ? "Found these. Pick your TV:" : "Found your TV:"));
      for (const t of r.tvs) {
        const c = el("button", "tvchip"); c.type = "button"; c.append(el("b", "", t.name || "Android TV"), el("small", "", `${t.host}:${t.port}`));
        c.onclick = () => { $("host").value = t.port === 5555 ? t.host : `${t.host}:${t.port}`; connect({ host: $("host").value }); };
        f.append(c);
      }
    }
  } catch (e) { f.replaceChildren(el("p", "error", e.message)); }
  b.disabled = false; b.textContent = "Find my TV automatically";
};

/* ---- backup ---- */
function renderBackup() {
  const i = S.scan.info, c = $("deviceCard"); c.replaceChildren();
  const cell = (k, v) => { const d = el("div"); d.append(el("small", "", k), el("b", "", v || "—")); c.append(d); };
  cell("TV", `${i.brand} ${i.model}`); cell("Android", i.android); cell("Apps installed", String(S.scan.installed));
  cell("Home screen", (i.home || "").split("/")[0]); cell("Already off", String(S.scan.disabled)); cell("Address", i.host);
  const b = S.scan.backup;
  $("backupState").hidden = !b;
  if (b) $("backupState").textContent = `Backup saved ${b.created}: ${b.packages} apps and the Home screen. Stored at ${b.path}`;
  $("backupBtn").textContent = b ? "Backup is in place" : "Back up my TV"; $("backupBtn").disabled = !!b;
  $("backupNext").hidden = !b;
}
$("backupBtn").onclick = async () => {
  if (await job("Backing up your TV", "/api/backup")) { await rescan(); S.done.add("backup"); go("clean"); }
};
$("backupNext").onclick = () => go("clean");

/* ---- clean ---- */
const GROUPS = [
  ["recommended", "Recommended", "Ads, tracking and preloaded junk that most tested TVs switch off", true],
  ["optional", "Your call", "Useful to some people: voice assistant, casting, screensavers", true],
  ["app", "Your apps", "Streaming and other apps. Tick only what you never use", false],
  ["protected", "Locked", "Needed for the TV to boot, show HDMI inputs, or open Settings", false],
];
function renderClean() {
  $("listCount").textContent = S.scan.lists;
  S.picked = new Set(S.scan.packages.filter((p) => p.cat === "recommended" && p.state === "enabled").map((p) => p.name));
  const root = $("groups"); root.replaceChildren();
  for (const [cat, title, sub, open] of GROUPS) {
    const rows = S.scan.packages.filter((p) => p.cat === cat || (cat === "protected" && p.cat === "home"));
    if (!rows.length) continue;
    const g = el("details", `group g-${cat}`); g.open = open;
    const sum = el("summary"); const h = el("h3", "", title);
    sum.append(h, el("span", "badge", String(rows.length)), el("span", "sub", sub));
    if (cat !== "protected") {
      const all = el("button", "all", "Select all");
      all.onclick = (e) => {
        e.preventDefault();
        const live = rows.filter((p) => p.state === "enabled"), on = live.every((p) => S.picked.has(p.name));
        live.forEach((p) => on ? S.picked.delete(p.name) : S.picked.add(p.name));
        g.querySelectorAll("input").forEach((i) => { if (!i.disabled) i.checked = !on; }); count();
      };
      sum.append(all);
    }
    g.append(sum);
    for (const p of rows) {
      const r = el("label", "pkg" + (p.state === "disabled" ? " off" : "")); r.dataset.q = (p.name + " " + p.note).toLowerCase();
      if (cat === "protected") r.append(el("span", "lock", "🔒"));
      else {
        const cb = el("input"); cb.type = "checkbox"; cb.checked = S.picked.has(p.name); cb.disabled = p.state === "disabled";
        cb.onchange = () => { cb.checked ? S.picked.add(p.name) : S.picked.delete(p.name); count(); };
        r.append(cb);
      }
      const t = el("div", "t"), title = p.note ? p.note[0].toUpperCase() + p.note.slice(1) : p.name.split(".").slice(-2).join(" ");
      t.append(el("div", "h", title), el("div", "n", p.name));
      if (p.why) t.append(el("div", "d", p.why));
      r.append(t);
      r.append(el("span", "m", p.state === "disabled" ? "already off" : p.lists ? `off in ${p.lists} lists` : ""));
      g.append(r);
    }
    root.append(g);
  }
  count();
}
function count() {
  const n = S.picked.size;
  $("pickCount").textContent = n ? `${n} selected` : "Nothing selected";
  $("cleanBtn").textContent = n ? `Switch off ${n} app${n > 1 ? "s" : ""}` : "Switch off";
  $("cleanBtn").disabled = !n && !$("anim").checked;
}
$("anim").onchange = count;
$("filter").oninput = () => {
  const q = $("filter").value.trim().toLowerCase();
  document.querySelectorAll(".pkg").forEach((r) => r.hidden = q && !r.dataset.q.includes(q));
  if (q) document.querySelectorAll(".group").forEach((g) => g.open = true);
};
$("dns").onchange = () => { $("dnsNote").hidden = !$("dns").checked; };
$("cleanBtn").onclick = async () => {
  const r = await job("Cleaning your TV", "/api/clean", { packages: [...S.picked], animations: $("anim").checked });
  if (!r) return;
  if ($("dns").checked) await job("Turning on ad-blocking DNS", "/api/dns", { on: true });
  S.stats.off += r.disabled.length; S.stats.kept += Object.keys(r.skipped).length + r.failed.length;
  await rescan(); S.done.add("clean"); go("launcher");
};
$("cleanSkip").onclick = () => go("launcher");
$("cacheBtn").onclick = () => job("Clearing app caches", "/api/cache");

/* ---- launcher ---- */
function renderLauncher() {
  const l = S.scan.launcher, n = $("launcherState");
  $("setupOpt").hidden = !l.setup;
  const gtv = l.stock === "com.google.android.apps.tv.launcherx", keep = gtv && $("keepStock").checked;
  $("keepOpt").hidden = !gtv;
  if (keep) $("setupOpt").hidden = true;
  n.hidden = false;
  if (l.isHome) n.textContent = "PureTV Home is already your Home screen. Installing again updates it.";
  else if (!l.apk) n.textContent = "The launcher file has not been built on this computer yet (launcher/dist/purehome.apk).";
  else if (keep) n.textContent = "Google TV's home stays installed and on, so your profiles keep working. PureTV Home is installed and set as Home; nothing is switched off in this step.";
  else if (l.stock) n.textContent = `PureTV Home will take over from ${l.stock}, which is switched off only after the new Home is running. If anything fails it is switched straight back on.`;
  else n.hidden = true;
  $("launcherBtn").disabled = !l.apk;
}
$("launcherBtn").onclick = async () => {
  const keep = !$("keepOpt").hidden && $("keepStock").checked;
  const r = await job("Installing PureTV Home", "/api/launcher", { disableSetup: $("disableSetup").checked && !keep, keepStock: keep });
  await rescan();
  if (r) { S.stats.launcher = true; S.done.add("launcher"); go("done"); }
};
$("launcherSkip").onclick = () => go("done");
$("keepStock").onchange = renderLauncher;

/* ---- done / restore ---- */
function renderDone() {
  const s = $("summary"); s.replaceChildren();
  const stat = (v, k) => { const d = el("div", "stat"); d.append(el("b", "", v), el("small", "", k)); s.append(d); };
  stat(String(S.scan.disabled), "apps now off"); stat(String(S.stats.kept), "kept for safety");
  stat(S.scan.launcher.isHome ? "PureTV" : "Stock", "home screen");
  $("restoreBtn").hidden = !S.scan.backup;
}
async function restore() {
  if (!confirm("Put the TV back exactly as it was when the backup was taken?\nApps that were switched off come back on and the original Home screen returns.")) return;
  if (await job("Restoring your TV", "/api/restore")) { S.stats = { off: 0, kept: 0, launcher: false }; S.done.delete("clean"); S.done.delete("launcher"); await rescan(); go("backup"); }
}
$("restoreBtn").onclick = restore; $("restoreTop").onclick = restore;
$("againBtn").onclick = () => go("clean");
$("checkBtn").onclick = async () => { if (await job("Checking after an update", "/api/check", { fix: true })) { await rescan(); renderDone(); } };
$("adbBtn").onclick = async () => {
  const n = $("adbState"); n.hidden = false; n.textContent = "Checking…";
  try { const r = await api("/api/adbclosed", {}); n.textContent = (r.closed === true ? "✓ " : r.closed === false ? "✗ " : "? ") + r.why; n.className = r.closed === false ? "error" : "note"; }
  catch (e) { n.textContent = e.message; n.className = "error"; }
};

go("connect");
