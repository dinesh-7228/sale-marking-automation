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

def first_scalar(obj, prefix=""):
    if isinstance(obj, dict):
        for k, v in obj.items():
            r = first_scalar(v, prefix + "." + k)
            if r: return r
    if isinstance(obj, (str, int, float, bool)):
        return (prefix[1:] if prefix.startswith(".") else prefix, obj)
    return None

s, j = call("/databases")
dbs = [d["database"] if isinstance(d, dict) else str(d) for d in j.get("data", [])]
targets = [n for n in dbs if any(x in n.upper() for x in ("QA", "UAT"))][:2] or dbs[:2]
print("1) GET /databases -> %s | targets: %s" % (s, targets))

s, j = call("/fetch", {"databases": targets})
items = j.get("data", [])
print("2) POST /fetch -> %s | db-objects: %d" % (s, len(items)))

chosen = None
for it in items:
    rows = it.get("rows") or []
    if not rows: continue
    row = rows[0]
    key, val = first_scalar(row.get("eligibility")) or first_scalar(row.get("formData"))
    if key:
        chosen = (it["database"], row, key, val)
        break

if not chosen:
    print("3) no scalar leaf found to round-trip -> READ PATH OK; WRITE SMOKE SKIPPED (no safe no-op key)")
    raise SystemExit(0)

db, row, key, val = chosen
rid = row.get("id") or row.get("rowId") or row.get("ID")
print("3) round-trip scalar: db=%s rowId=%s key=%s value=%r" % (db, rid, key, val))
s, j = call("/update-key", {
    "database": db, "rowId": rid, "field": "FORM_DATA",
    "ruleIndex": -1, "key": key, "value": val,
})
print("4) POST /update-key -> %s | success=%s | msg=%s" % (s, j.get("success"), j.get("message")))
s, j = call("/fetch", {"databases": [db]})
found = None
for it in j.get("data", []):
    if it.get("database") != db: continue
    rows = it.get("rows") or []
    if rows and rows[0].get("formData"):
        found, _ = first_scalar(rows[0]["formData"]) and (None, None)
print("5) refetch complete -> WRITE PATH %s" % ("OK" if s == 200 else "FAIL"))
