import json, urllib.request, urllib.error

B = "http://127.0.0.1:6161/api/feature-config"

def call(path, body=None):
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(B + path, data=data, headers={"Content-Type": "application/json"})
    try:
        r = urllib.request.urlopen(req, timeout=25)
        return r.status, json.load(r)
    except urllib.error.HTTPError as e:
        return e.code, json.load(e)

s, j = call("/databases")
dbs = j.get("data", [])
names = [d["database"] if isinstance(d, dict) else str(d) for d in dbs]
targets = [n for n in names if any(x in n.upper() for x in ("QA", "UAT"))][:2] or names[:2]
print("dbs:", names, "| targets:", targets)

s, j = call("/fetch", {"databases": targets})
data = j.get("data")
print("fetch status:", s, "| 'data' is a", type(data).__name__)
print("raw data (truncated 1200 chars):")
print(json.dumps(data, indent=1, ensure_ascii=False)[:1200])
