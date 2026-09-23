#!/usr/bin/env bash
# Build the jar and deploy to a 24/7 Linux server (systemd auto-start+restart).
# Usage: ./deploy.sh user@server
set -euo pipefail
JAR=target/sale-marking-automation-1.0.0.jar
SVC=sale-marking-automation.service
TARGET="${1:?usage: ./deploy.sh user@host}"

export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"

echo "== build =="
mvn -q clean package -DskipTests

echo "== push jar + unit =="
scp "$JAR" "$TARGET:/opt/sale-marking-automation.jar"
scp "deploy/$SVC" "$TARGET:/etc/systemd/system/$SVC"

echo "== install unit + env =="
ssh "$TARGET" "mkdir -p /opt/sale-marking-automation;   grep -q '# Sale Marking Automation DB creds' /etc/sale-marking-automation.env 2>/dev/null ||     install -m 600 /dev/null /etc/sale-marking-automation.env"
echo "** edit creds now on the server: sudo nano /etc/sale-marking-automation.env **"

echo "== start =="
ssh "$TARGET" "systemctl daemon-reload && systemctl enable --now $SVC && sleep 2 && systemctl status $SVC --no-pager | head -5"
echo "DONE. Open http://host:6161/"
