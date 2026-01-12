#!/bin/bash

# Script to delete all user data from the image-search-app
# WARNING: This will permanently delete:
# - All database records (users, folders, images, sessions, shares)
# - All uploaded image files in data/uploads/
# - All FAISS indexes in data/indexes/

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Database configuration (use env vars or defaults)
DB_NAME="${DB_NAME:-imagesearch}"
DB_USER="${DB_USERNAME:-imageuser}"
DB_PASSWORD="${DB_PASSWORD:-imagepass123}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"

echo -e "${RED}WARNING: This will permanently delete ALL user data!${NC}"
echo ""
echo "This includes:"
echo "  - All users, folders, images, sessions, and shares from PostgreSQL"
echo "  - All files in data/uploads/"
echo "  - All FAISS indexes in data/indexes/"
echo ""
read -p "Are you sure you want to continue? (type 'yes' to confirm): " confirmation

if [ "$confirmation" != "yes" ]; then
    echo -e "${YELLOW}Aborted.${NC}"
    exit 0
fi

echo ""
read -p "Type 'DELETE ALL DATA' to confirm: " final_confirmation

if [ "$final_confirmation" != "DELETE ALL DATA" ]; then
    echo -e "${YELLOW}Aborted.${NC}"
    exit 0
fi

echo ""
echo -e "${GREEN}Starting data deletion...${NC}"

# Step 1: Clear database tables
echo ""
echo "Step 1/3: Clearing database tables..."

PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME <<EOF
-- Delete in order to respect foreign key constraints
DELETE FROM folder_shares;
DELETE FROM images;
DELETE FROM folders;
DELETE FROM sessions;
DELETE FROM users;

-- Display row counts
SELECT 'users' as table_name, COUNT(*) as remaining_rows FROM users
UNION ALL
SELECT 'folders', COUNT(*) FROM folders
UNION ALL
SELECT 'images', COUNT(*) FROM images
UNION ALL
SELECT 'sessions', COUNT(*) FROM sessions
UNION ALL
SELECT 'folder_shares', COUNT(*) FROM folder_shares;
EOF

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ Database tables cleared${NC}"
else
    echo -e "${RED}✗ Failed to clear database tables${NC}"
    exit 1
fi

# Step 2: Delete uploaded files
echo ""
echo "Step 2/3: Deleting uploaded files..."

if [ -d "data/uploads" ]; then
    file_count=$(find data/uploads -type f 2>/dev/null | wc -l)
    echo "Found $file_count files to delete"
    rm -rf data/uploads/*
    echo -e "${GREEN}✓ Uploaded files deleted${NC}"
else
    echo -e "${YELLOW}No data/uploads directory found (skipping)${NC}"
fi

# Step 3: Delete FAISS indexes
echo ""
echo "Step 3/3: Deleting FAISS indexes..."

if [ -d "data/indexes" ]; then
    index_count=$(find data/indexes -name "*.faiss" 2>/dev/null | wc -l)
    echo "Found $index_count FAISS indexes to delete"
    rm -rf data/indexes/*
    echo -e "${GREEN}✓ FAISS indexes deleted${NC}"
else
    echo -e "${YELLOW}No data/indexes directory found (skipping)${NC}"
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}All user data has been deleted!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "The database is now empty and ready for fresh data."
echo "The data/uploads/ and data/indexes/ directories are cleared."
