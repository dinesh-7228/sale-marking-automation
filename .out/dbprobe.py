import pymysql, os

OUT = "/home/dinesh/Documents/sale-marking-automation/.out/dbprobe.txt"
lines = []


def log(s):
    lines.append(str(s))


def probe(host, user, pw, schema=None):
    try:
        cn = pymysql.connect(host=host, port=3306, user=user, password=pw,
                             database=schema, connect_timeout=8)
        cur = cn.cursor()
        cur.execute("SELECT VERSION(), CURRENT_USER()")
        ver, usr = cur.fetchone()
        log("  OK version=%s user=%s schema=%s" % (ver, usr, schema))
        cn.close()
        return True
    except Exception as e:
        log("  FAIL %s" % e)
        return False


log("=== primary RDS (beejapuri / complaint) ===")
probe("non-prod-apps-dbs.cxmdwl4djaa6.ap-south-1.rds.amazonaws.com", "dinesh",
      "pjq4gry4ir6QSGh", "beejapuri_QA")

log("=== rapid RDS ===")
probe("non-prod-app-rapid-dbs.cxmdwl4djaa6.ap-south-1.rds.amazonaws.com", "dinesh",
      "3GGscfBMBH3LP4v", "rapiddelivery_QA")

log("=== schema list on primary RDS ===")
try:
    cn = pymysql.connect(host="non-prod-apps-dbs.cxmdwl4djaa6.ap-south-1.rds.amazonaws.com",
                         port=3306, user="dinesh", password="pjq4gry4ir6QSGh",
                         connect_timeout=8)
    cur = cn.cursor()
    cur.execute("SHOW DATABASES")
    for (d,) in cur.fetchall():
        log("  db: %s" % d)
    cn.close()
except Exception as e:
    log("  FAIL %s" % e)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(lines) + "\n")
print("WROTE", OUT)
