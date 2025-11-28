#!/bin/bash
# OPTIMIZED Java stack launcher - prevents system freezes
# Reduces memory usage and staggers service startup

echo "============================================"
echo "Image Search App - OPTIMIZED Java Stack"
echo "============================================"
echo ""
echo "Optimized for systems with limited RAM"
echo "Reduced JVM heap sizes and better startup sequencing"
echo "Auto-reload enabled (DevTools) - code changes reload automatically"
echo "Preserves existing PostgreSQL data"
echo ""

# Get the project root directory (parent of scripts/)
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"

# Check available memory
AVAILABLE_MEM=$(free -m | awk '/^Mem:/{print $7}')
echo "📊 Available memory: ${AVAILABLE_MEM}MB"

if [ "$AVAILABLE_MEM" -lt 2048 ]; then
    echo "⚠️  WARNING: Less than 2GB available memory detected!"
    echo "⚠️  System may become slow. Consider closing other applications."
    echo ""
    read -p "Continue anyway? (y/N): " -r
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Aborted."
        exit 1
    fi
fi

# Function to check if a service is responding
wait_for_service() {
    local url="$1"
    local service_name="$2"
    local max_attempts=60
    
    echo "⏳ Waiting for $service_name to be ready..."
    for i in $(seq 1 $max_attempts); do
        if curl -s "$url" > /dev/null 2>&1; then
            echo "✅ $service_name ready (${i}s)"
            return 0
        fi
        sleep 1
    done
    echo "❌ $service_name failed to start within ${max_attempts}s"
    return 1
}

# Check prerequisites with optimization
echo "Checking prerequisites..."

# Check Java
if ! command -v java &> /dev/null; then
    echo "❌ Java not found! Please install Java 17+"
    exit 1
fi
echo "✅ Java $(java -version 2>&1 | head -n 1 | cut -d'"' -f2) found"

# Check Node.js
if ! command -v node &> /dev/null; then
    echo "❌ Node.js not found! Please install Node.js 18+"
    exit 1
fi
echo "✅ Node.js $(node --version) found"

# Optimize Elasticsearch settings for low-memory systems
echo ""
echo "🔧 Optimizing Elasticsearch for low memory..."
ES_JVM_OPTIONS="/etc/elasticsearch/jvm.options.d/heap.options"
if [ ! -f "$ES_JVM_OPTIONS" ]; then
    echo "Creating Elasticsearch heap optimization..."
    sudo tee "$ES_JVM_OPTIONS" > /dev/null << 'EOF'
# Optimize for systems with limited RAM
-Xms512m
-Xmx1g
EOF
    echo "✅ Elasticsearch heap limited to 1GB"
fi

# Check/start PostgreSQL
if ! systemctl is-active --quiet postgresql; then
    echo "🔄 Starting PostgreSQL..."
    sudo systemctl start postgresql
    if [ $? -ne 0 ]; then
        echo "❌ Failed to start PostgreSQL"
        exit 1
    fi
fi
echo "✅ PostgreSQL is running"

# Check/start Elasticsearch with memory limits
if ! systemctl is-active --quiet elasticsearch; then
    echo "🔄 Starting Elasticsearch (with memory limits)..."
    sudo systemctl start elasticsearch
    if [ $? -ne 0 ]; then
        echo "❌ Failed to start Elasticsearch"
        exit 1
    fi
    
    # Wait for Elasticsearch to be ready
    if ! wait_for_service "http://localhost:9200" "Elasticsearch"; then
        echo "❌ Elasticsearch startup failed"
        exit 1
    fi
else
    echo "✅ Elasticsearch is running"
fi

# Check database setup (preserves existing data)
if ! psql -U imageuser -d imagesearch -c "\q" 2>/dev/null; then
    echo "🔄 Setting up database (first time only)..."
    "$SCRIPT_DIR/setup-postgres.sh"
    if [ $? -ne 0 ]; then
        echo "❌ Database setup failed"
        exit 1
    fi
    echo "✅ Database configured for first time"
else
    echo "✅ Database ready (existing data preserved)"
fi

echo ""
echo "============================================"
echo "🚀 Starting services with memory optimization..."
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
    echo "Run services manually with these optimized commands:"
    echo "  Terminal 1: ./scripts/run-java-search-service-optimized.sh"
    echo "  Terminal 2: ./scripts/run-java-backend-optimized.sh"
    echo "  Terminal 3: ./scripts/run-frontend-java.sh"
    exit 1
fi

# Function to launch optimized terminals
launch_optimized_terminal() {
    local title="$1"
    local script="$2"

    case "$TERMINAL" in
        gnome-terminal)
            gnome-terminal --title="$title" -- bash -c "cd '$PROJECT_ROOT' && $script; echo 'Press Enter to close...'; read; exit"
            ;;
        konsole)
            konsole --new-tab -p tabtitle="$title" -e bash -c "cd '$PROJECT_ROOT' && $script; echo 'Press Enter to close...'; read; exit" &
            ;;
        xfce4-terminal)
            xfce4-terminal --title="$title" -e "bash -c 'cd $PROJECT_ROOT && $script; echo Press Enter to close...; read; exit'" &
            ;;
        xterm)
            xterm -title "$title" -e "bash -c 'cd $PROJECT_ROOT && $script; echo Press Enter to close...; read; exit'" &
            ;;
    esac
}

# Stage 1: Start Java Search Service with memory limits
echo "1️⃣ Starting Java Search Service (heap: 1GB max)..."
launch_optimized_terminal "Java Search Service (Optimized)" "./scripts/run-java-search-service-optimized.sh"
sleep 3

# Wait for search service to be ready before continuing
if ! wait_for_service "http://localhost:5001/actuator/health" "Java Search Service"; then
    echo "❌ Search service failed to start. Check the terminal for errors."
    exit 1
fi

# Stage 2: Start Java Backend with memory limits
echo "2️⃣ Starting Java Backend (heap: 512MB max)..."
launch_optimized_terminal "Java Backend (Optimized)" "./scripts/run-java-backend-optimized.sh"
sleep 3

# Wait for backend to be ready
if ! wait_for_service "http://localhost:8080/api/health" "Java Backend"; then
    echo "❌ Backend failed to start. Check the terminal for errors."
    exit 1
fi

# Stage 3: Start React Frontend (least resource intensive)
echo "3️⃣ Starting React Frontend..."
launch_optimized_terminal "React Frontend" "./scripts/run-frontend-java.sh"

echo ""
echo "============================================"
echo "✅ All services launched successfully!"
echo "============================================"
echo ""
echo "🌟 Optimized Configuration:"
echo "  🔍 Java Search Service: 1GB heap limit + DevTools auto-reload"
echo "  🚀 Java Backend: 512MB heap limit + DevTools auto-reload"  
echo "  🔍 Elasticsearch: 1GB heap limit"
echo "  🌐 Frontend: Standard Node.js + hot reload"
echo "  💾 PostgreSQL: Preserves existing data"
echo ""
echo "🌐 Access points:"
echo "  Frontend: http://localhost:3000"
echo "  Backend: http://localhost:8080/api/health"
echo "  Search Service: http://localhost:5001/actuator/health"
echo ""
echo "💡 Tips for better performance:"
echo "  • Close unnecessary applications"
echo "  • Avoid running multiple instances"
echo "  • Monitor system resources: htop"
echo ""