#!/bin/bash
# Elasticsearch Manager for Image Search App
# Easy commands to view and manage your Elasticsearch indices

ES_HOST="localhost"
ES_PORT="9200"
JAVA_SEARCH_SERVICE="http://localhost:5001"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Helper function to make curl requests to Elasticsearch
es_curl() {
    curl -s -X "$1" "http://$ES_HOST:$ES_PORT/$2" -H "Content-Type: application/json" ${3:+-d "$3"}
}

# Helper function to make requests to Java Search Service
search_service_curl() {
    curl -s -X "$1" "$JAVA_SEARCH_SERVICE/$2" -H "Content-Type: application/json" ${3:+-d "$3"}
}

# Check if Elasticsearch is running
check_elasticsearch() {
    if ! es_curl "GET" "_cluster/health" >/dev/null 2>&1; then
        echo -e "${RED}❌ Elasticsearch is not running on $ES_HOST:$ES_PORT${NC}"
        echo -e "${YELLOW}💡 Start it with: docker-compose up elasticsearch${NC}"
        return 1
    fi
    return 0
}

# Check if Java Search Service is running
check_java_search() {
    if ! search_service_curl "GET" "actuator/health" >/dev/null 2>&1; then
        echo -e "${RED}❌ Java Search Service is not running on $JAVA_SEARCH_SERVICE${NC}"
        echo -e "${YELLOW}💡 Start it with: ./scripts/run-java-search-service.sh${NC}"
        return 1
    fi
    return 0
}

# Display menu
show_menu() {
    echo -e "${PURPLE}================================================${NC}"
    echo -e "${PURPLE}  🔍 Elasticsearch Manager for Image Search${NC}"
    echo -e "${PURPLE}  Elasticsearch: $ES_HOST:$ES_PORT${NC}"
    echo -e "${PURPLE}  Java Search Service: $JAVA_SEARCH_SERVICE${NC}"
    echo -e "${PURPLE}================================================${NC}"
    echo ""
    echo -e "${CYAN}📊 Index Information:${NC}"
    echo "1. List all indices"
    echo "2. Show cluster health"
    echo "3. Show index statistics"
    echo "4. Search indices by pattern"
    echo ""
    echo -e "${CYAN}👥 User & Folder Operations:${NC}"
    echo "5. Show indices for specific user"
    echo "6. Show index details for user/folder"
    echo "7. Count documents in user's indices"
    echo "8. List all user folders (from indices)"
    echo ""
    echo -e "${CYAN}🔧 Index Management:${NC}"
    echo "9. Create index for user/folder"
    echo "10. Delete index for user/folder"
    echo "11. Delete all indices for user"
    echo "12. Reindex folder (delete + recreate)"
    echo ""
    echo -e "${CYAN}🧹 Cleanup Operations:${NC}"
    echo "13. Delete all image indices (keep system indices)"
    echo "14. Delete ALL indices (dangerous!)"
    echo "15. Show orphaned indices (no DB folder)"
    echo ""
    echo -e "${CYAN}🛠️  Advanced:${NC}"
    echo "16. Raw Elasticsearch query"
    echo "17. Show index mapping"
    echo "18. Open Elasticsearch in browser"
    echo ""
    echo -e "${CYAN}🔌 Port Management:${NC}"
    echo "19. Check what's using port $ES_PORT"
    echo "20. Kill process on port $ES_PORT"
    echo "0. Exit"
    echo ""
}

# Function implementations
list_all_indices() {
    echo -e "${GREEN}📋 All Elasticsearch indices:${NC}"
    es_curl "GET" "_cat/indices?v&s=index"
}

show_cluster_health() {
    echo -e "${GREEN}💚 Cluster health:${NC}"
    es_curl "GET" "_cluster/health?pretty"
}

show_index_stats() {
    echo -e "${GREEN}📊 Index statistics:${NC}"
    es_curl "GET" "_stats/docs,store?pretty" | jq '.indices | to_entries | map({index: .key, docs: .value.total.docs.count, size: .value.total.store.size_in_bytes}) | sort_by(.index)'
}

search_indices_pattern() {
    read -p "Enter search pattern (e.g., images-*, images-1-*): " pattern
    echo -e "${GREEN}🔍 Indices matching '$pattern':${NC}"
    es_curl "GET" "_cat/indices/$pattern?v&s=index"
}

show_user_indices() {
    read -p "Enter user ID: " user_id
    if [[ ! "$user_id" =~ ^[0-9]+$ ]]; then
        echo -e "${RED}❌ Invalid user ID. Must be a number.${NC}"
        return
    fi
    
    echo -e "${GREEN}👤 Indices for user $user_id:${NC}"
    es_curl "GET" "_cat/indices/images-$user_id-*?v&s=index"
    
    echo ""
    echo -e "${CYAN}📈 Document counts:${NC}"
    es_curl "GET" "images-$user_id-*/_count?pretty" 2>/dev/null | jq -r '.count // "No indices found"'
}

show_index_details() {
    read -p "Enter user ID: " user_id
    read -p "Enter folder ID: " folder_id
    
    if [[ ! "$user_id" =~ ^[0-9]+$ ]] || [[ ! "$folder_id" =~ ^[0-9]+$ ]]; then
        echo -e "${RED}❌ Invalid IDs. Must be numbers.${NC}"
        return
    fi
    
    index_name="images-$user_id-$folder_id"
    echo -e "${GREEN}📁 Details for index '$index_name':${NC}"
    
    # Check if index exists
    if ! es_curl "HEAD" "$index_name" >/dev/null 2>&1; then
        echo -e "${RED}❌ Index '$index_name' does not exist${NC}"
        return
    fi
    
    echo -e "${BLUE}📊 Index stats:${NC}"
    es_curl "GET" "$index_name/_stats?pretty" | jq '.indices[].total | {docs: .docs.count, size_bytes: .store.size_in_bytes}'
    
    echo -e "${BLUE}🔧 Index settings:${NC}"
    es_curl "GET" "$index_name/_settings?pretty" | jq ".\"$index_name\".settings.index | {shards: .number_of_shards, replicas: .number_of_replicas, refresh_interval: .refresh_interval}"
    
    echo -e "${BLUE}🖼️  Sample documents:${NC}"
    es_curl "GET" "$index_name/_search?size=3&pretty" | jq '.hits.hits[] | {id: ._id, image_id: ._source.image_id, folder_id: ._source.folder_id}'
}

count_user_documents() {
    read -p "Enter user ID: " user_id
    if [[ ! "$user_id" =~ ^[0-9]+$ ]]; then
        echo -e "${RED}❌ Invalid user ID. Must be a number.${NC}"
        return
    fi
    
    echo -e "${GREEN}🔢 Document counts for user $user_id:${NC}"
    
    # Get all indices for user
    indices=$(es_curl "GET" "_cat/indices/images-$user_id-*?h=index" | tr '\n' ',' | sed 's/,$//')
    
    if [ -z "$indices" ]; then
        echo -e "${YELLOW}⚠️  No indices found for user $user_id${NC}"
        return
    fi
    
    # Count documents in each index
    IFS=',' read -ra INDEX_ARRAY <<< "$indices"
    total=0
    for index in "${INDEX_ARRAY[@]}"; do
        count=$(es_curl "GET" "$index/_count" | jq '.count')
        folder_id=$(echo "$index" | sed "s/images-$user_id-//")
        echo "  Folder $folder_id: $count documents"
        total=$((total + count))
    done
    
    echo -e "${CYAN}📊 Total documents for user $user_id: $total${NC}"
}

list_user_folders() {
    read -p "Enter user ID (or press Enter for all users): " user_id
    
    if [ -z "$user_id" ]; then
        pattern="images-*"
        echo -e "${GREEN}📁 All user folders from indices:${NC}"
    else
        if [[ ! "$user_id" =~ ^[0-9]+$ ]]; then
            echo -e "${RED}❌ Invalid user ID. Must be a number.${NC}"
            return
        fi
        pattern="images-$user_id-*"
        echo -e "${GREEN}📁 Folders for user $user_id:${NC}"
    fi
    
    es_curl "GET" "_cat/indices/$pattern?h=index" | while read -r index; do
        if [[ $index =~ images-([0-9]+)-([0-9]+) ]]; then
            uid="${BASH_REMATCH[1]}"
            fid="${BASH_REMATCH[2]}"
            count=$(es_curl "GET" "$index/_count" | jq '.count')
            echo "  User $uid, Folder $fid: $count images"
        fi
    done
}

create_index() {
    read -p "Enter user ID: " user_id
    read -p "Enter folder ID: " folder_id
    
    if [[ ! "$user_id" =~ ^[0-9]+$ ]] || [[ ! "$folder_id" =~ ^[0-9]+$ ]]; then
        echo -e "${RED}❌ Invalid IDs. Must be numbers.${NC}"
        return
    fi
    
    echo -e "${YELLOW}🔧 Creating index for user $user_id, folder $folder_id...${NC}"
    
    response=$(search_service_curl "POST" "api/create-index" "{\"user_id\": $user_id, \"folder_id\": $folder_id}")
    
    if echo "$response" | jq -e '.message' >/dev/null 2>&1; then
        echo -e "${GREEN}✅ $(echo "$response" | jq -r '.message')${NC}"
    else
        echo -e "${RED}❌ Failed to create index: $response${NC}"
    fi
}

delete_index() {
    read -p "Enter user ID: " user_id
    read -p "Enter folder ID: " folder_id
    
    if [[ ! "$user_id" =~ ^[0-9]+$ ]] || [[ ! "$folder_id" =~ ^[0-9]+$ ]]; then
        echo -e "${RED}❌ Invalid IDs. Must be numbers.${NC}"
        return
    fi
    
    index_name="images-$user_id-$folder_id"
    
    echo -e "${YELLOW}⚠️  WARNING: This will delete index '$index_name' permanently!${NC}"
    read -p "Are you sure? Type 'yes' to confirm: " confirm
    
    if [ "$confirm" = "yes" ]; then
        response=$(search_service_curl "DELETE" "api/delete-index/$user_id/$folder_id")
        
        if echo "$response" | jq -e '.message' >/dev/null 2>&1; then
            echo -e "${GREEN}✅ $(echo "$response" | jq -r '.message')${NC}"
        else
            echo -e "${RED}❌ Failed to delete index: $response${NC}"
        fi
    else
        echo -e "${CYAN}Cancelled${NC}"
    fi
}

delete_user_indices() {
    read -p "Enter user ID: " user_id
    
    if [[ ! "$user_id" =~ ^[0-9]+$ ]]; then
        echo -e "${RED}❌ Invalid user ID. Must be a number.${NC}"
        return
    fi
    
    # List indices first
    indices=$(es_curl "GET" "_cat/indices/images-$user_id-*?h=index")
    
    if [ -z "$indices" ]; then
        echo -e "${YELLOW}⚠️  No indices found for user $user_id${NC}"
        return
    fi
    
    echo -e "${YELLOW}⚠️  WARNING: This will delete ALL indices for user $user_id:${NC}"
    echo "$indices"
    read -p "Are you sure? Type 'DELETE USER INDICES' to confirm: " confirm
    
    if [ "$confirm" = "DELETE USER INDICES" ]; then
        echo "$indices" | while read -r index; do
            if [ -n "$index" ]; then
                es_curl "DELETE" "$index" >/dev/null
                echo -e "${GREEN}✅ Deleted $index${NC}"
            fi
        done
        echo -e "${GREEN}🎉 All indices for user $user_id deleted${NC}"
    else
        echo -e "${CYAN}Cancelled${NC}"
    fi
}

reindex_folder() {
    read -p "Enter user ID: " user_id
    read -p "Enter folder ID: " folder_id
    
    if [[ ! "$user_id" =~ ^[0-9]+$ ]] || [[ ! "$folder_id" =~ ^[0-9]+$ ]]; then
        echo -e "${RED}❌ Invalid IDs. Must be numbers.${NC}"
        return
    fi
    
    echo -e "${YELLOW}🔄 Reindexing user $user_id, folder $folder_id (delete + recreate)...${NC}"
    
    # Delete first
    search_service_curl "DELETE" "api/delete-index/$user_id/$folder_id" >/dev/null 2>&1
    
    # Create new
    response=$(search_service_curl "POST" "api/create-index" "{\"user_id\": $user_id, \"folder_id\": $folder_id}")
    
    if echo "$response" | jq -e '.message' >/dev/null 2>&1; then
        echo -e "${GREEN}✅ Reindexing completed${NC}"
    else
        echo -e "${RED}❌ Failed to recreate index: $response${NC}"
    fi
}

delete_all_image_indices() {
    echo -e "${RED}🚨 WARNING: This will delete ALL image indices (images-*) but keep system indices${NC}"
    read -p "Are you SURE? Type 'DELETE ALL IMAGES' to confirm: " confirm
    
    if [ "$confirm" = "DELETE ALL IMAGES" ]; then
        indices=$(es_curl "GET" "_cat/indices/images-*?h=index")
        
        if [ -z "$indices" ]; then
            echo -e "${YELLOW}⚠️  No image indices found${NC}"
            return
        fi
        
        echo "$indices" | while read -r index; do
            if [ -n "$index" ]; then
                es_curl "DELETE" "$index" >/dev/null
                echo -e "${GREEN}✅ Deleted $index${NC}"
            fi
        done
        echo -e "${GREEN}🎉 All image indices deleted${NC}"
    else
        echo -e "${CYAN}Cancelled${NC}"
    fi
}

delete_all_indices() {
    echo -e "${RED}🚨 EXTREME DANGER: This will delete ALL Elasticsearch indices!${NC}"
    echo -e "${RED}This includes system indices and will break Elasticsearch!${NC}"
    read -p "Type 'DESTROY EVERYTHING' to confirm: " confirm
    
    if [ "$confirm" = "DESTROY EVERYTHING" ]; then
        es_curl "DELETE" "_all" >/dev/null
        echo -e "${RED}💥 All indices destroyed${NC}"
    else
        echo -e "${CYAN}Cancelled${NC}"
    fi
}

show_orphaned_indices() {
    echo -e "${GREEN}🔍 Checking for orphaned indices (indices without DB folders)...${NC}"
    echo -e "${YELLOW}Note: This requires database access to verify folders exist${NC}"
    
    # Get all image indices
    indices=$(es_curl "GET" "_cat/indices/images-*?h=index")
    
    if [ -z "$indices" ]; then
        echo -e "${YELLOW}⚠️  No image indices found${NC}"
        return
    fi
    
    echo "$indices" | while read -r index; do
        if [[ $index =~ images-([0-9]+)-([0-9]+) ]]; then
            uid="${BASH_REMATCH[1]}"
            fid="${BASH_REMATCH[2]}"
            echo -e "${CYAN}📁 $index (User: $uid, Folder: $fid)${NC}"
            # Note: Would need database connection to verify if folder exists
            # For now, just list all indices with their user/folder IDs
        fi
    done
}

raw_query() {
    read -p "Enter Elasticsearch endpoint (e.g., _search, _cat/indices): " endpoint
    read -p "Enter HTTP method (GET/POST/DELETE): " method
    read -p "Enter JSON body (or press Enter for none): " body
    
    echo -e "${CYAN}🔍 Executing: $method /$endpoint${NC}"
    
    if [ -n "$body" ]; then
        es_curl "$method" "$endpoint" "$body" | jq '.' 2>/dev/null || es_curl "$method" "$endpoint" "$body"
    else
        es_curl "$method" "$endpoint" | jq '.' 2>/dev/null || es_curl "$method" "$endpoint"
    fi
}

show_index_mapping() {
    read -p "Enter index name (or user_id-folder_id): " input
    
    # If input looks like user-folder IDs, construct index name
    if [[ $input =~ ^[0-9]+-[0-9]+$ ]]; then
        index_name="images-$input"
    else
        index_name="$input"
    fi
    
    echo -e "${GREEN}🗺️  Mapping for index '$index_name':${NC}"
    es_curl "GET" "$index_name/_mapping?pretty"
}

open_in_browser() {
    echo -e "${CYAN}🌐 Opening Elasticsearch in browser...${NC}"
    if command -v xdg-open >/dev/null 2>&1; then
        xdg-open "http://$ES_HOST:$ES_PORT"
    elif command -v open >/dev/null 2>&1; then
        open "http://$ES_HOST:$ES_PORT"
    else
        echo -e "${YELLOW}💡 Open manually: http://$ES_HOST:$ES_PORT${NC}"
    fi
}

# Check what's listening on Elasticsearch port
check_port() {
    echo -e "${GREEN}🔍 Checking port $ES_PORT...${NC}"
    echo ""
    
    if command -v lsof >/dev/null 2>&1; then
        result=$(lsof -i ":$ES_PORT" 2>/dev/null)
        if [ -z "$result" ]; then
            echo -e "${YELLOW}⚠️  Nothing listening on port $ES_PORT${NC}"
        else
            echo "$result"
        fi
    else
        echo -e "${YELLOW}⚠️  lsof not available, trying ss...${NC}"
        result=$(ss -tulnp 2>/dev/null | grep ":$ES_PORT")
        if [ -z "$result" ]; then
            echo -e "${YELLOW}⚠️  Nothing listening on port $ES_PORT${NC}"
        else
            echo "$result"
        fi
    fi
}

# Stop process using Elasticsearch port
stop_port_process() {
    echo -e "${YELLOW}🛑 Killing process on port $ES_PORT...${NC}"
    
    if ! command -v lsof >/dev/null 2>&1; then
        echo -e "${RED}❌ lsof not installed${NC}"
        return
    fi
    
    pid=$(lsof -ti ":$ES_PORT")
    
    if [ -z "$pid" ]; then
        echo -e "${YELLOW}⚠️  Nothing running on port $ES_PORT${NC}"
        return
    fi
    
    echo "Process: $(ps -p $pid -o command=)"
    read -p "Kill PID $pid? (yes/no): " confirm
    
    if [ "$confirm" = "yes" ]; then
        kill -9 "$pid"
        echo -e "${GREEN}✅ Killed PID $pid${NC}"
    fi
}

# Main script logic
case "$1" in
    1|indices) check_elasticsearch && list_all_indices ;;
    2|health) check_elasticsearch && show_cluster_health ;;
    3|stats) check_elasticsearch && show_index_stats ;;
    4|search) check_elasticsearch && search_indices_pattern ;;
    5|user) check_elasticsearch && show_user_indices ;;
    6|details) check_elasticsearch && show_index_details ;;
    7|count) check_elasticsearch && count_user_documents ;;
    8|folders) check_elasticsearch && list_user_folders ;;
    9|create) check_java_search && create_index ;;
    10|delete) check_java_search && delete_index ;;
    11|deleteuser) check_elasticsearch && delete_user_indices ;;
    12|reindex) check_java_search && reindex_folder ;;
    13|clearimages) check_elasticsearch && delete_all_image_indices ;;
    14|destroyall) check_elasticsearch && delete_all_indices ;;
    15|orphaned) check_elasticsearch && show_orphaned_indices ;;
    16|query) check_elasticsearch && raw_query ;;
    17|mapping) check_elasticsearch && show_index_mapping ;;
    18|browser) open_in_browser ;;
    19|checkport) check_port ;;
    20|stopport) stop_port_process ;;
    0|exit) echo -e "${GREEN}Goodbye! 👋${NC}"; exit 0 ;;
    *)
        # Interactive menu mode
        while true; do
            if ! check_elasticsearch; then
                echo ""
                read -p "Press Enter to try again or Ctrl+C to exit..."
                continue
            fi
            
            show_menu
            read -p "Choose an option: " choice
            echo ""
            
            case $choice in
                1) $0 indices ;;
                2) $0 health ;;
                3) $0 stats ;;
                4) $0 search ;;
                5) $0 user ;;
                6) $0 details ;;
                7) $0 count ;;
                8) $0 folders ;;
                9) $0 create ;;
                10) $0 delete ;;
                11) $0 deleteuser ;;
                12) $0 reindex ;;
                13) $0 clearimages ;;
                14) $0 destroyall ;;
                15) $0 orphaned ;;
                16) $0 query ;;
                17) $0 mapping ;;
                18) $0 browser ;;
                19) check_port ;;
                20) stop_port_process ;;
                0) exit 0 ;;
                *) echo -e "${RED}❌ Invalid option!${NC}" ;;
            esac
            echo ""
            read -p "Press Enter to continue..."
        done
        ;;
esac