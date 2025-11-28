#!/bin/bash

# setup-elasticsearch.sh
# Sets up Elasticsearch for the Java search service

set -e

echo "========================================="
echo "Elasticsearch Setup for Image Search App"
echo "========================================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if Elasticsearch is already running
if curl -s http://localhost:9200 > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Elasticsearch is already running${NC}"
    curl -s http://localhost:9200 | jq '.'
    exit 0
fi

echo "Elasticsearch is not running. Choose installation method:"
echo ""
echo "1) Docker (recommended - quick and isolated)"
echo "2) Native installation (Ubuntu/Debian)"
echo "3) Homebrew (macOS)"
echo "4) Skip installation (I'll install manually)"
echo ""
read -p "Enter choice [1-4]: " choice

case $choice in
    1)
        echo ""
        echo "Installing Elasticsearch via Docker..."

        # Check if Docker is installed
        if ! command -v docker &> /dev/null; then
            echo -e "${RED}✗ Docker is not installed. Please install Docker first.${NC}"
            exit 1
        fi

        # Check if container already exists
        if docker ps -a --format '{{.Names}}' | grep -q '^elasticsearch$'; then
            echo "Elasticsearch container exists. Starting it..."
            docker start elasticsearch
        else
            echo "Creating new Elasticsearch container..."
            docker run -d \
              --name elasticsearch \
              -p 9200:9200 \
              -p 9300:9300 \
              -e "discovery.type=single-node" \
              -e "xpack.security.enabled=false" \
              -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
              docker.elastic.co/elasticsearch/elasticsearch:8.11.1
        fi

        echo ""
        echo "Waiting for Elasticsearch to start (this may take 30-60 seconds)..."

        # Wait for Elasticsearch to be ready
        for i in {1..60}; do
            if curl -s http://localhost:9200 > /dev/null 2>&1; then
                echo -e "${GREEN}✓ Elasticsearch is ready!${NC}"
                break
            fi
            echo -n "."
            sleep 1
        done

        echo ""
        ;;

    2)
        echo ""
        echo "Installing Elasticsearch natively (Ubuntu/Debian)..."

        # Import GPG key
        wget -qO - https://artifacts.elastic.co/GPG-KEY-elasticsearch | sudo gpg --dearmor -o /usr/share/keyrings/elasticsearch-keyring.gpg

        # Add repository
        echo "deb [signed-by=/usr/share/keyrings/elasticsearch-keyring.gpg] https://artifacts.elastic.co/packages/8.x/apt stable main" | sudo tee /etc/apt/sources.list.d/elastic-8.x.list

        # Install
        sudo apt update
        sudo apt install -y elasticsearch

        # Configure for development (disable security)
        echo -e "${YELLOW}Configuring Elasticsearch for development (disabling security)...${NC}"
        echo "xpack.security.enabled: false" | sudo tee -a /etc/elasticsearch/elasticsearch.yml

        # Start service
        sudo systemctl daemon-reload
        sudo systemctl enable elasticsearch
        sudo systemctl start elasticsearch

        echo ""
        echo "Waiting for Elasticsearch to start..."
        sleep 10

        for i in {1..30}; do
            if curl -s http://localhost:9200 > /dev/null 2>&1; then
                echo -e "${GREEN}✓ Elasticsearch is ready!${NC}"
                break
            fi
            sleep 1
        done

        echo ""
        ;;

    3)
        echo ""
        echo "Installing Elasticsearch via Homebrew (macOS)..."

        if ! command -v brew &> /dev/null; then
            echo -e "${RED}✗ Homebrew is not installed. Please install Homebrew first.${NC}"
            exit 1
        fi

        brew tap elastic/tap
        brew install elastic/tap/elasticsearch-full
        brew services start elastic/tap/elasticsearch-full

        echo ""
        echo "Waiting for Elasticsearch to start..."
        sleep 10

        for i in {1..30}; do
            if curl -s http://localhost:9200 > /dev/null 2>&1; then
                echo -e "${GREEN}✓ Elasticsearch is ready!${NC}"
                break
            fi
            sleep 1
        done

        echo ""
        ;;

    4)
        echo ""
        echo "Skipping installation. Please install Elasticsearch manually."
        echo "Visit: https://www.elastic.co/downloads/elasticsearch"
        exit 0
        ;;

    *)
        echo -e "${RED}Invalid choice. Exiting.${NC}"
        exit 1
        ;;
esac

# Verify installation
echo ""
echo "========================================="
echo "Verifying Elasticsearch Installation"
echo "========================================="
echo ""

if curl -s http://localhost:9200 > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Elasticsearch is accessible at http://localhost:9200${NC}"
    echo ""
    echo "Cluster information:"
    curl -s http://localhost:9200 | jq '.'
    echo ""
    echo "Cluster health:"
    curl -s http://localhost:9200/_cluster/health?pretty | jq '.'
else
    echo -e "${RED}✗ Elasticsearch is not accessible. Please check the logs.${NC}"
    if [ "$choice" = "1" ]; then
        echo "Docker logs:"
        docker logs elasticsearch --tail 50
    elif [ "$choice" = "2" ]; then
        echo "System logs:"
        sudo journalctl -u elasticsearch --no-pager --lines 50
    fi
    exit 1
fi

echo ""
echo "========================================="
echo "Next Steps"
echo "========================================="
echo ""
echo "1. Build the Java search service:"
echo "   cd java-search-service"
echo "   ./gradlew build"
echo ""
echo "2. Run the Java search service:"
echo "   ./gradlew bootRun"
echo ""
echo "3. Verify health:"
echo "   curl http://localhost:5001/actuator/health | jq"
echo ""
echo "For more information, see ELASTICSEARCH_MIGRATION.md"
echo ""
