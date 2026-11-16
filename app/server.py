"""PureTV local server: serves the UI on http://127.0.0.1:8765 and runs TV jobs one at a time.
Listens on this computer only."""
import json
import os
import sys
import threading
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import core  # noqa: E402
from demo import DemoTV  # noqa: E402

UI = Path(__file__).resolve().parent / "ui"
PORT = int(os.environ.get("PURETV_PORT", "8765"))
TYPES = {".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".svg": "image/svg+xml",
         ".png": "image/png", ".jpg": "image/jpeg", ".webp": "image/webp", ".woff2": "font/woff2"}

state = {"tv": None, "job": {"name": "", "running": False, "log": [], "result": None, "error": None}}
lock = threading.Lock()


def tv():
    if not state["tv"]:
        raise core.TVError("Not connected to a TV yet.")
    return state["tv"]


def start_job(name, fn):
    """Run fn(log) in the background; the UI polls /api/job for the log and the result."""
    with lock:
        if state["job"]["running"]:
            raise core.TVError("PureTV is still busy with the last step.")
        job = state["job"] = {"name": name, "running": True, "log": [], "result": None, "error": None}

    def run():
        try:
            job["result"] = fn(job["log"].append)
        except core.TVError as e:
            job["error"] = str(e)
        except Exception as e:  # a bug, not the TV: say so rather than hang the UI
            job["error"] = f"Unexpected error: {e.__class__.__name__}: {e}"
        finally:
            job["running"] = False

    threading.Thread(target=run, daemon=True).start()
    return {"started": name}


def api(path, body):
    if path == "/api/connect":
        if state["tv"]:
            state["tv"].close()
            state["tv"] = None
        if body.get("demo"):
            # The demo keeps its backups apart from real ones.
            core.DATA = Path(os.environ.get("PURETV_HOME") or Path.home() / ".puretv") / "demo"
            state["tv"] = core.TV("demo", DemoTV(), demo=True)
            rec = core.record_path("DEMO-TV-0001")
            for f in (rec, rec.with_suffix(".settings.txt")):
                if f.exists():
                    f.unlink()
        else:
            core.DATA = Path(os.environ.get("PURETV_HOME") or Path.home() / ".puretv")
            state["tv"] = core.TV.connect(body.get("host", ""))
        return state["tv"].info()
    if path == "/api/scan":
        return core.scan(tv())
    if path == "/api/job":
        return state["job"]
    if path == "/api/backup":
        return start_job("backup", lambda log: core.record_summary(core.backup(tv(), log)))
    if path == "/api/clean":
        names = [str(n) for n in body.get("packages", [])]
        pause = 0.3 if tv().demo else core.PAUSE
        return start_job("clean", lambda log: core.clean(tv(), names, bool(body.get("animations")), log, pause))
    if path == "/api/cache":
        return start_job("cache", lambda log: core.trim_caches(tv(), log))
    if path == "/api/launcher":
        return start_job("launcher", lambda log: core.install_launcher(tv(), bool(body.get("disableSetup")), log, bool(body.get("keepStock"))))
    if path == "/api/restore":
        return start_job("restore", lambda log: core.restore(tv(), log))
    if path == "/api/discover":
        return core.discover()
    if path == "/api/check":
        return start_job("check", lambda log: core.check_update(tv(), bool(body.get("fix", True)), log))
    if path == "/api/dns":
        return start_job("dns", lambda log: core.set_dns(tv(), bool(body.get("on")), log))
    if path == "/api/adbclosed":
        if tv().demo:
            return {"closed": True, "why": "demo TV: on a real TV this checks that port 5555 refuses connections"}
        return core.adb_closed(tv().host)
    if path == "/api/disconnect":
        if state["tv"]:
            state["tv"].close()
            state["tv"] = None
        return {}
    raise KeyError(path)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def _local(self):
        # Only this computer's own browser: refuse another site's page or a DNS-rebound name.
        host = (self.headers.get("Host") or "").split(":")[0]
        origin = self.headers.get("Origin")
        return host in ("127.0.0.1", "localhost") and (
            not origin or origin in (f"http://127.0.0.1:{PORT}", f"http://localhost:{PORT}"))

    def _send(self, code, data, ctype="application/json"):
        raw = data if isinstance(data, bytes) else json.dumps(data).encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype + ("; charset=utf-8" if ctype.startswith(("text/", "application/json", "image/svg")) else ""))
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        if not self._local():
            return self._send(403, {"error": "local only"})
        path = self.path.split("?")[0]
        if path.startswith("/api/"):
            return self._api(path, {})
        f = (UI / (path.lstrip("/") or "index.html")).resolve()
        if UI not in f.parents or not f.is_file():
            return self._send(404, {"error": "not found"})
        self._send(200, f.read_bytes(), TYPES.get(f.suffix, "application/octet-stream"))

    def do_POST(self):
        if not self._local() or "application/json" not in (self.headers.get("Content-Type") or ""):
            return self._send(403, {"error": "local only"})
        try:
            body = json.loads(self.rfile.read(int(self.headers.get("Content-Length") or 0)) or b"{}")
        except ValueError:
            return self._send(400, {"error": "bad request"})
        self._api(self.path, body)

    def _api(self, path, body):
        try:
            self._send(200, api(path, body))
        except core.TVError as e:
            self._send(400, {"error": str(e)})
        except KeyError:
            self._send(404, {"error": "not found"})
        except Exception as e:
            self._send(500, {"error": f"{e.__class__.__name__}: {e}"})


def main():
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    url = f"http://127.0.0.1:{PORT}"
    print(f"PureTV is running at {url}  (close this window to stop it)")
    if "--no-browser" not in sys.argv:
        threading.Timer(0.6, lambda: webbrowser.open(url)).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
