# Deploy on Fly.io (Vercel-like, but runs your Spring Boot jar 24/7)

## 1. Install flyctl + login
    curl -L https://fly.io/install.sh | sh
    fly auth login

## 2. Give Fly your DB credentials (stored as secrets, never in git)
    fly secrets set \
      db.host=YOUR_APP_RDS_HOST db.port=3306 \
      db.user=dinesh db.password=YOUR_DB_PASSWORD \
      db.rapid.host=YOUR_RAPID_RDS_HOST db.rapid.port=3306 \
      db.rapid.user=dinesh db.rapid.password=YOUR_RAPID_PASSWORD

   (These exact dotted names are what DatabaseUtil reads at startup.)

## 3. Build the jar, then deploy (one command)
    mvn clean package -DskipTests
    fly launch --now     # or: fly deploy

## 4. Your panel is live (no laptop needed)
    fly open             # opens https://*.fly.dev  → port 6161 mapped to https:80

Always-on: fly.toml sets min_machines_running=1 + auto_stop_machines=false,
so it runs around the clock and auto-restarts on crash. Boot autostart included.

---

## The ONE decision you must make: RDS access
Fly machines egress from a pool of region IPs that can change, so you cannot
whitelist Fly by IP (same wall as Vercel). Two real choices:

  A) Open the Security Group inbound rule for port 3306 to 0.0.0.0/0.
     -> Just works everywhere. Safe enough for non-prod since creds are in
        secrets and this is a QA/UAT dashboard. RECOMMENDED for this app.
  B) Keep it locked by IP and instead use an SSH tunnel / bastion.
     -> More work; only worth it if 0.0.0.0/0 is unacceptable to you.

Do NOT leave the old "your home IP" rule AND these in conflict confusion —
just switch the 3306 rule to 0.0.0.0/0 (keep your own laptop rule too if you
want to run it locally occasionally).

## Roll it back / off
    fly destroy sale-marking-automation   # removes machines + releases the fly.dev URL
