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

echo ""
echo "Environment Variables Set:"
echo "  DB_HOST: $DB_HOST"
echo "  DB_PORT: $DB_PORT"
echo "  DB_NAME: $DB_NAME"
echo "  DB_USER: $DB_USER"
echo ""

# Build the application
echo "Building application..."
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
