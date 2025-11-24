#!/bin/bash
# Complete Reset Script for Image Search App
# Deletes all data: database records, uploaded images, and FAISS indices

set -e  # Exit on any error

# Get the project root directory (parent of scripts/ directory)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

# Change to project root to ensure relative paths work correctly
cd "$PROJECT_ROOT"

echo "================================================"
echo "  Image Search App - Complete Reset"
echo "================================================"
echo "Working directory: $PROJECT_ROOT"
echo ""
echo "⚠️  WARNING: This will delete ALL data:"
echo "  • All database records (users, images, folders, etc.)"
echo "  • All uploaded image files"
echo "  • All FAISS vector indices"
echo ""
echo "This action cannot be undone!"
echo ""

# Prompt for confirmation
read -p "Are you ABSOLUTELY sure? Type 'RESET' to confirm: " confirm

if [ "$confirm" != "RESET" ]; then
    echo "Cancelled. No changes made."
    exit 0
fi

echo ""
echo "Starting cleanup..."
echo ""

# Database configuration
DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="imagesearch"
DB_USER="imageuser"
DB_PASS="imagepass123"

# 1. Clear database
echo "📊 Step 1/3: Clearing database..."
PGPASSWORD=$DB_PASS psql -h $DB_HOST -U $DB_USER -d $DB_NAME << 'EOF' 2>/dev/null
-- Truncate all tables
TRUNCATE folder_shares, images, sessions, folders, users CASCADE;

-- Reset auto-increment sequences to start from 1
ALTER SEQUENCE users_id_seq RESTART WITH 1;
ALTER SEQUENCE folders_id_seq RESTART WITH 1;
ALTER SEQUENCE images_id_seq RESTART WITH 1;
ALTER SEQUENCE sessions_id_seq RESTART WITH 1;
ALTER SEQUENCE folder_shares_id_seq RESTART WITH 1;
EOF

if [ $? -eq 0 ]; then
    echo "✅ Database cleared and sequences reset"
else
    echo "⚠️  Warning: Could not clear database (might not exist yet)"
fi

# 2. Delete uploaded images
echo ""
echo "🖼️  Step 2/3: Deleting uploaded images..."

# New unified data directory
if [ -d "data/uploads" ]; then
    rm -rf data/uploads/*
    echo "✅ Deleted images from data/uploads/"
else
    echo "ℹ️  Directory data/uploads/ does not exist (skipping)"
fi

# Legacy directories (for backward compatibility)
if [ -d "images" ]; then
    rm -rf images/*
    echo "✅ Deleted images from legacy images/ directory"
fi

# 3. Delete FAISS indices
echo ""
echo "🔍 Step 3/3: Deleting FAISS indices..."

# New unified data directory
if [ -d "data/indexes" ]; then
    rm -rf data/indexes/*
    echo "✅ Deleted all FAISS indices from data/indexes/"
else
    echo "ℹ️  Directory data/indexes/ does not exist (skipping)"
fi

# Legacy directories (for backward compatibility)
if [ -d "search-service/faiss_indexes" ]; then
    rm -rf search-service/faiss_indexes/*
    echo "✅ Deleted indices from legacy search-service/faiss_indexes/"
fi

if [ -d "python-backend/faisses_indexes" ]; then
    rm -rf python-backend/faisses_indexes/*
    echo "✅ Deleted indices from legacy python-backend/faisses_indexes/"
fi

echo ""
echo "================================================"
echo "✅ Complete reset finished!"
echo "================================================"
echo ""
echo "Your app is now in a clean state. You can test from scratch."
echo ""
echo "To start fresh:"
echo "  1. Start the backend: ./scripts/run-python-backend.sh"
echo "  2. Start the search service: ./scripts/run-search-service.sh"
echo "  3. Start the frontend: ./scripts/run-frontend-python.sh"
echo ""
echo "Or use docker-compose:"
echo "  docker-compose -f docker-compose.python.yml up"
echo ""
