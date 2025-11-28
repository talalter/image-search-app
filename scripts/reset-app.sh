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
echo "  • All Lucene vector indices"
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
echo "📊 Step 1/5: Clearing database..."
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
echo "🖼️  Step 2/5: Deleting uploaded images..."

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
echo "🔍 Step 3/5: Deleting FAISS indices..."

# New unified data directory
if [ -d "data/indexes" ]; then
    rm -rf data/indexes/*
    echo "✅ Deleted all FAISS indices from data/indexes/"
else
    echo "ℹ️  Directory data/indexes/ does not exist (skipping)"
fi

# Legacy directories (for backward compatibility)
if [ -d "python-search-service/faiss_indexes" ]; then
    rm -rf python-search-service/faiss_indexes/*
    echo "✅ Deleted indices from legacy python-search-service/faiss_indexes/"
fi

if [ -d "python-backend/faisses_indexes" ]; then
    rm -rf python-backend/faisses_indexes/*
    echo "✅ Deleted indices from legacy python-backend/faisses_indexes/"
fi

if [ -d "backend/faisses_indexes" ]; then
    rm -rf backend/faisses_indexes/*
    echo "✅ Deleted indices from legacy backend/faisses_indexes/"
fi

# 4. Delete Lucene indices
echo ""
echo "🔍 Step 4/6: Deleting Lucene indices..."

if [ -d "data/lucene-indexes" ]; then
    rm -rf data/lucene-indexes/*
    echo "✅ Deleted all Lucene indices from data/lucene-indexes/"
else
    echo "ℹ️  Directory data/lucene-indexes/ does not exist (skipping)"
fi

# 5. Delete Elasticsearch indices
echo ""
echo "🔍 Step 5/6: Deleting Elasticsearch indices..."

# Check if Elasticsearch is running and accessible
if curl -s "localhost:9200" > /dev/null 2>&1; then
    # Get all indices that match the pattern images-* into an array
    INDICES=($(curl -s "localhost:9200/_cat/indices?h=index" 2>/dev/null | grep "^images-"))
    
    if [ ${#INDICES[@]} -gt 0 ]; then
        echo "Found Elasticsearch indices: ${INDICES[@]}"
        # Delete each index individually
        DELETED_COUNT=0
        FAILED_COUNT=0
        
        for index in "${INDICES[@]}"; do
            if [ -n "$index" ]; then
                if curl -s -X DELETE "localhost:9200/$index" > /dev/null 2>&1; then
                    echo "  ✅ Deleted index: $index"
                    ((DELETED_COUNT++))
                else
                    echo "  ❌ Failed to delete index: $index"
                    ((FAILED_COUNT++))
                fi
            fi
        done
        
        if [ $FAILED_COUNT -eq 0 ]; then
            echo "✅ Successfully deleted $DELETED_COUNT Elasticsearch indices"
        else
            echo "⚠️  Warning: Deleted $DELETED_COUNT indices, failed to delete $FAILED_COUNT indices"
        fi
    else
        echo "ℹ️  No Elasticsearch indices found matching 'images-*'"
    fi
else
    echo "ℹ️  Elasticsearch not accessible at localhost:9200 (skipping)"
fi

# 6. Delete temporary/cache files
echo ""
echo "🗑️  Step 6/6: Cleaning temporary files..."

# Clean Python cache
find . -type d -name "__pycache__" -exec rm -rf {} + 2>/dev/null
find . -type f -name "*.pyc" -delete 2>/dev/null

echo "✅ Deleted Python cache files"

echo ""
echo "================================================"
echo "✅ Complete reset finished!"
echo "================================================"
echo ""
echo "Your app is now in a clean state. You can test from scratch."
echo ""
echo "To start fresh:"
echo ""
echo "Python Stack:"
echo "  ./scripts/run-all-python-stack.sh"
echo ""
echo "Java Stack:"
echo "  ./scripts/run-all-java-stack.sh"
echo ""
echo "Or use docker-compose:"
echo "  docker-compose -f docker-compose.python.yml up"
echo "  docker-compose -f docker-compose.java.yml up"
echo ""
