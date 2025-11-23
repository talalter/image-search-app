#!/bin/bash
# Re-embed all images in PostgreSQL database to FAISS
# This script reads images from PostgreSQL and sends them to the search service for embedding

echo "============================================"
echo "Re-embedding Images from PostgreSQL to FAISS"
echo "============================================"
echo ""

# Database credentials
DB_USER="imageuser"
DB_NAME="imagesearch"
DB_HOST="localhost"
DB_PORT="5432"

# Get all images from database
echo "Fetching images from PostgreSQL..."
IMAGES_JSON=$(PGPASSWORD=imagepass123 psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -t -A -F"," -c "
SELECT
    i.id,
    i.user_id,
    i.folder_id,
    i.filepath
FROM images i
ORDER BY i.folder_id, i.id;
")

if [ -z "$IMAGES_JSON" ]; then
    echo "No images found in database."
    exit 0
fi

echo "Found images. Processing..."

# Group images by folder
declare -A folder_images
declare -A folder_users

while IFS=',' read -r image_id user_id folder_id filepath; do
    # Skip empty lines
    [ -z "$image_id" ] && continue

    # Store user for this folder
    folder_users[$folder_id]=$user_id

    # Append image to folder's list
    if [ -z "${folder_images[$folder_id]}" ]; then
        folder_images[$folder_id]="{\"image_id\":$image_id,\"file_path\":\"$filepath\"}"
    else
        folder_images[$folder_id]="${folder_images[$folder_id]},{\"image_id\":$image_id,\"file_path\":\"$filepath\"}"
    fi
done <<< "$IMAGES_JSON"

# Create indexes and embed images for each folder
for folder_id in "${!folder_images[@]}"; do
    user_id=${folder_users[$folder_id]}
    images="[${folder_images[$folder_id]}]"

    echo "Processing folder $folder_id (user $user_id)..."

    # Step 1: Create FAISS index for this folder
    echo "  Creating index..."
    create_response=$(curl -s -X POST http://localhost:5000/create-index \
        -H "Content-Type: application/json" \
        -d "{
            \"user_id\": $user_id,
            \"folder_id\": $folder_id
        }")

    if echo "$create_response" | grep -q "Created FAISS index"; then
        echo "  ✅ Index created"
    else
        echo "  ⚠️  Index creation response: $create_response"
    fi

    # Step 2: Embed images
    echo "  Embedding images..."
    embed_response=$(curl -s -X POST http://localhost:5000/embed-images \
        -H "Content-Type: application/json" \
        -d "{
            \"user_id\": $user_id,
            \"folder_id\": $folder_id,
            \"images\": $images
        }")

    if echo "$embed_response" | grep -q "Successfully embedded"; then
        echo "  ✅ Successfully embedded folder $folder_id"
    else
        echo "  ❌ Failed to embed folder $folder_id: $embed_response"
    fi
done

echo ""
echo "============================================"
echo "Re-embedding complete!"
echo "============================================"
