#!/bin/bash
# Master script to run the complete Java stack:
# - Java Search Service (port 5001)
# - Java Backend (port 8080)
# - React Frontend (port 3000)

echo "============================================"
echo "Image Search App - Full Java Stack Launcher"
echo "============================================"
echo ""
echo "This will start all services in separate terminal windows:"
echo "  1. Java Search Service (ONNX CLIP) - port 5001"
echo "  2. Java Backend (Spring Boot) - port 8080"
echo "  3. React Frontend - port 3000"
echo ""
echo "Prerequisites:"
echo "  ✅ PostgreSQL installed and running"
echo "  ✅ Elasticsearch installed and running"
echo "  ✅ Java 17+ installed"
echo "  ✅ Node.js 18+ installed"
echo ""

# Get the project root directory (parent of scripts/)
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"

# Check prerequisites
echo "Checking prerequisites..."
echo ""

# Check Java
if ! command -v java &> /dev/null; then
    echo "❌ Java not found! Please install Java 17+"
    exit 1
fi
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "❌ Java 17+ required. Found: Java $JAVA_VERSION"
    exit 1
fi
echo "✅ Java $JAVA_VERSION found"

# Check Node.js
if ! command -v node &> /dev/null; then
    echo "❌ Node.js not found! Please install Node.js 18+"
    exit 1
fi
echo "✅ Node.js $(node --version) found"

# Check PostgreSQL
if ! systemctl is-active --quiet postgresql; then
    echo "⚠️  PostgreSQL is not running. Starting it..."
    sudo systemctl start postgresql
    if [ $? -ne 0 ]; then
        echo "❌ Failed to start PostgreSQL"
        echo "Please start it manually: sudo systemctl start postgresql"
        exit 1
    fi
fi
echo "✅ PostgreSQL is running"

# Check Elasticsearch
if ! systemctl is-active --quiet elasticsearch; then
    echo "⚠️  Elasticsearch is not running. Starting it..."
    sudo systemctl start elasticsearch
    if [ $? -ne 0 ]; then
        echo "❌ Failed to start Elasticsearch"
        echo "Please start it manually: sudo systemctl start elasticsearch"
        exit 1
    fi

    # Wait for Elasticsearch to be ready (poll instead of fixed 30s)
    echo "⏳ Waiting for Elasticsearch to initialize..."
    for i in {1..60}; do
        if curl -s http://localhost:9200 > /dev/null 2>&1; then
            echo "✅ Elasticsearch ready in ${i} seconds"
            break
        fi
        if [ $i -eq 60 ]; then
            echo "❌ Elasticsearch did not start within 60 seconds"
            echo "Please check: sudo systemctl status elasticsearch"
            exit 1
        fi
        sleep 1
    done
else
    # Already running, just verify it's accessible
    if ! curl -s http://localhost:9200 > /dev/null 2>&1; then
        echo "❌ Elasticsearch service is active but not accessible on port 9200"
        echo "Please check: sudo systemctl status elasticsearch"
        exit 1
    fi
    echo "✅ Elasticsearch is running"
fi

# Check if database exists
if ! psql -U imageuser -d imagesearch -c "\q" 2>/dev/null; then
    echo ""
    echo "⚠️  Database not set up. Running setup script..."
    echo ""
    "$SCRIPT_DIR/setup-postgres.sh"
    if [ $? -ne 0 ]; then
        echo "❌ Database setup failed"
        exit 1
    fi
fi
echo "✅ Database configured"

echo ""
echo "============================================"
echo "Starting services..."
echo "============================================"
echo ""

# Detect terminal emulator
if command -v gnome-terminal &> /dev/null; then
    TERMINAL="gnome-terminal"
elif command -v konsole &> /dev/null; then
    TERMINAL="konsole"
elif command -v xfce4-terminal &> /dev/null; then
    TERMINAL="xfce4-terminal"
elif command -v xterm &> /dev/null; then
    TERMINAL="xterm"
else
    echo "❌ No supported terminal emulator found"
    echo "Please run the services manually in separate terminals:"
    echo "  Terminal 1: ./scripts/run-java-search-service.sh"
    echo "  Terminal 2: ./scripts/run-java-backend-with-java-search.sh"
    echo "  Terminal 3: ./scripts/run-frontend-java.sh"
    exit 1
fi

echo "Using terminal: $TERMINAL"
echo ""

# Function to launch terminal based on type
launch_terminal() {
    local title="$1"
    local script="$2"

    case "$TERMINAL" in
        gnome-terminal)
            gnome-terminal --title="$title" -- bash -c "cd '$PROJECT_ROOT' && $script; exec bash"
            ;;
        konsole)
            konsole --new-tab -p tabtitle="$title" -e bash -c "cd '$PROJECT_ROOT' && $script; exec bash" &
            ;;
        xfce4-terminal)
            xfce4-terminal --title="$title" -e "bash -c 'cd $PROJECT_ROOT && $script; exec bash'" &
            ;;
        xterm)
            xterm -title "$title" -e "bash -c 'cd $PROJECT_ROOT && $script; exec bash'" &
            ;;
    esac
}

# Launch services in order with delays
echo "1. Starting Java Search Service..."
launch_terminal "Java Search Service (port 5001)" "./scripts/run-java-search-service.sh"
sleep 2

echo "2. Waiting 30 seconds for search service to initialize..."
echo "   (ONNX models may need to download on first run)"
sleep 30

echo "3. Starting Java Backend..."
launch_terminal "Java Backend (port 8080)" "./scripts/run-java-backend-with-java-search.sh"
sleep 15

echo "4. Starting React Frontend..."
launch_terminal "React Frontend (port 3000)" "./scripts/run-frontend-java.sh"

echo ""
echo "============================================"
echo "✅ All services launched!"
echo "============================================"
echo ""
echo "Services:"
echo "  🔍 Java Search Service: http://localhost:5001/actuator/health"
echo "  🚀 Java Backend: http://localhost:8080/api/health"
echo "  🌐 Frontend: http://localhost:3000"
echo ""
echo "Check each terminal window for logs and status."
echo ""
echo "To stop services: Press Ctrl+C in each terminal"
echo ""
echo "First-time setup:"
echo "  1. Register an account at http://localhost:3000"
echo "  2. Upload some images to a folder"
echo "  3. Wait 10-20 seconds for embedding processing"
echo "  4. Search for images with text queries!"
echo ""
