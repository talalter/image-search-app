#!/bin/bash
# Optimized Java Search Service - Limited memory usage
# Heap: 1GB max (down from default 2GB+)

echo "============================================"
echo "Java Search Service (Memory Optimized)"
echo "============================================"
echo ""
echo "🔧 JVM Heap: 1GB max (was: default ~2-4GB)"
echo "🔧 Parallel GC with limited threads"
echo ""

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"

# Check Elasticsearch
echo "Verifying Elasticsearch..."
if ! curl -s http://localhost:9200 > /dev/null 2>&1; then
    echo "❌ Elasticsearch not accessible. Please start it first:"
    echo "   sudo systemctl start elasticsearch"
    exit 1
fi
echo "✅ Elasticsearch ready"

# Set environment variables
export CLIP_TEXT_MODEL_PATH="${PROJECT_ROOT}/models/clip-vit-base-patch32-text.onnx"
export CLIP_IMAGE_MODEL_PATH="${PROJECT_ROOT}/models/clip-vit-base-patch32-visual.onnx"
export CLIP_VOCAB_PATH="${PROJECT_ROOT}/models/vocab.txt"
export CLIP_MERGES_PATH="${PROJECT_ROOT}/models/merges.txt"

# Create directories
mkdir -p "$PROJECT_ROOT/models"

echo ""
echo "🚀 Starting with optimized JVM settings..."
echo "   Max heap: 1024m"
echo "   Initial heap: 256m"
echo "   Parallel GC threads: 2"
echo ""

cd "$PROJECT_ROOT/java-search-service"

# JVM optimization flags for low-memory systems
export JAVA_OPTS="-Xms256m -Xmx1024m -XX:+UseParallelGC -XX:ParallelGCThreads=2 -XX:MaxMetaspaceSize=256m -Djava.awt.headless=true -XX:+UseCompressedOops -XX:NewRatio=3"

./gradlew bootRun --no-daemon