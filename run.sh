#!/bin/bash
# Sale Marking Automation - Quick Start Script

echo "======================================================"
echo "  Sale Marking Automation - Quick Start"
echo "======================================================"

# Set environment variables
export DB_HOST="non-prod-apps-dbs.cxmdwl4djaa6.ap-south-1.rds.amazonaws.com"
export DB_PORT="3306"
export DB_NAME="beejapuri_QA"  # Database schema is selected automatically by environment: QA -> beejapuri_QA, UAT -> beejapuri_UAT
export DB_USER="dinesh"
export DB_PASSWORD="pjq4gry4ir6QSGh"

# Admin panel access (Basic auth). Both must be non-blank or the app returns 503.
export ADMIN_ACCESS_USERNAME="${ADMIN_ACCESS_USERNAME:-admin}"
export ADMIN_ACCESS_PASSWORD="${ADMIN_ACCESS_PASSWORD:-admin123}"

echo ""
echo "Environment Variables Set:"
echo "  DB_HOST: $DB_HOST"
echo "  DB_PORT: $DB_PORT"
echo "  DB_NAME: $DB_NAME"
echo "  DB_USER: $DB_USER"
echo ""

# Prefer a JDK of 11 or higher (project targets Java 11)
for candidate in /usr/lib/jvm/java-11-openjdk-amd64 /usr/lib/jvm/java-21-openjdk-amd64; do
    if [ -x "$candidate/bin/javac" ]; then
        export JAVA_HOME="$candidate"
        export PATH="$JAVA_HOME/bin:$PATH"
        break
    fi
done

# Build the application
echo "Building application (JAVA_HOME=${JAVA_HOME:-<unset>})..."
mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo ""
    echo "✓ Build successful!"
    echo ""
    echo "Starting application..."
    echo "Access at: http://localhost:6161/"
    echo ""
    java -jar target/sale-marking-automation-1.0.0.jar
else
    echo "❌ Build failed. Please check the errors above."
    exit 1
fi
