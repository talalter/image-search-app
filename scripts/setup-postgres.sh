#!/bin/bash
# PostgreSQL Setup Script for Image Search App
# This script creates the database and user needed for the Java backend

echo "============================================"
echo "PostgreSQL Setup for Image Search App"
echo "============================================"
echo ""
echo "This will create:"
echo "  - Database: imagesearch"
echo "  - User: imageuser"
echo "  - Password: imagepass123"
echo ""
echo "You may be prompted for your system password (sudo)."
echo ""

# Check if PostgreSQL is installed
if ! command -v psql &> /dev/null; then
    echo "❌ PostgreSQL is not installed!"
    echo "Install it with: sudo apt install postgresql postgresql-contrib"
    exit 1
fi

# Check if PostgreSQL is running
if ! systemctl is-active --quiet postgresql; then
    echo "⚠️  PostgreSQL is not running. Starting it..."
    sudo systemctl start postgresql
    if [ $? -ne 0 ]; then
        echo "❌ Failed to start PostgreSQL"
        exit 1
    fi
    echo "✅ PostgreSQL started"
fi

echo "Creating database and user..."
echo ""

# Create database and user using sudo
sudo -u postgres psql << EOF
-- Drop existing database and user if they exist (for clean setup)
DROP DATABASE IF EXISTS imagesearch;
DROP USER IF EXISTS imageuser;

-- Create new database and user
CREATE DATABASE imagesearch;
CREATE USER imageuser WITH ENCRYPTED PASSWORD 'imagepass123';

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE imagesearch TO imageuser;

-- Connect to the database and grant schema privileges
\c imagesearch

GRANT ALL ON SCHEMA public TO imageuser;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO imageuser;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO imageuser;

-- Confirm setup
\l imagesearch
\du imageuser
EOF

if [ $? -eq 0 ]; then
    echo ""
    echo "============================================"
    echo "✅ PostgreSQL setup completed successfully!"
    echo "============================================"
    echo ""
    echo "Database credentials:"
    echo "  Database: imagesearch"
    echo "  Username: imageuser"
    echo "  Password: imagepass123"
    echo ""
    echo "You can now run the Java backend with:"
    echo "  cd java-backend"
    echo "  export DB_USERNAME=imageuser"
    echo "  export DB_PASSWORD=imagepass123"
    echo "  ./gradlew bootRun"
    echo ""
    echo "Or use the run script:"
    echo "  ./scripts/run-java.sh"
    echo ""
else
    echo ""
    echo "❌ PostgreSQL setup failed!"
    echo "Please check the error messages above."
    exit 1
fi
