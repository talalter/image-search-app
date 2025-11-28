#!/bin/bash
# One-time CLIP Model Download Script
# Downloads ONNX CLIP models to avoid delays on first app startup

echo "============================================"
echo "CLIP ONNX Models Download"
echo "============================================"
echo ""
echo "This downloads the required CLIP AI models (~500MB total):"
echo "  • Text Encoder (~250MB) - processes search queries"
echo "  • Image Encoder (~250MB) - processes images"
echo "  • Tokenizer files (vocab + merges)"
echo ""
echo "✅ Run this ONCE before first use"
echo "✅ Then app starts instantly every time!"
echo ""

# Get the project root directory (parent of scripts/)
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$( cd "$SCRIPT_DIR/.." && pwd )"

# Create models directory
MODELS_DIR="$PROJECT_ROOT/models"
mkdir -p "$MODELS_DIR"

echo "Download location: $MODELS_DIR"
echo ""

# Model URLs from Hugging Face
TEXT_MODEL_URL="https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/main/onnx/text_model_quantized.onnx"
IMAGE_MODEL_URL="https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/main/onnx/vision_model_quantized.onnx"
VOCAB_URL="https://huggingface.co/openai/clip-vit-base-patch32/resolve/main/vocab.json"
MERGES_URL="https://huggingface.co/openai/clip-vit-base-patch32/resolve/main/merges.txt"

# Target files
TEXT_MODEL="$MODELS_DIR/clip-vit-base-patch32-text.onnx"
IMAGE_MODEL="$MODELS_DIR/clip-vit-base-patch32-visual.onnx"
VOCAB_FILE="$MODELS_DIR/vocab.txt"
MERGES_FILE="$MODELS_DIR/merges.txt"

# Function to download file
download_file() {
    local url=$1
    local output=$2
    local name=$3

    if [ -f "$output" ]; then
        echo "✅ $name already exists, skipping..."
        return 0
    fi

    echo "📥 Downloading $name..."
    if command -v wget &> /dev/null; then
        wget -q --show-progress "$url" -O "$output"
    elif command -v curl &> /dev/null; then
        curl -L --progress-bar "$url" -o "$output"
    else
        echo "❌ Neither wget nor curl found. Please install one of them."
        exit 1
    fi

    if [ $? -eq 0 ]; then
        echo "✅ $name downloaded successfully"
        return 0
    else
        echo "❌ Failed to download $name"
        return 1
    fi
}

# Check if all models already exist
if [ -f "$TEXT_MODEL" ] && [ -f "$IMAGE_MODEL" ] && [ -f "$VOCAB_FILE" ] && [ -f "$MERGES_FILE" ]; then
    echo "============================================"
    echo "✅ All models already downloaded!"
    echo "============================================"
    echo ""
    echo "Models location:"
    echo "  Text Model: $TEXT_MODEL"
    echo "  Image Model: $IMAGE_MODEL"
    echo "  Vocab: $VOCAB_FILE"
    echo "  Merges: $MERGES_FILE"
    echo ""
    echo "Total size: $(du -sh $MODELS_DIR | cut -f1)"
    echo ""
    echo "You can now run the application without download delays!"
    echo ""
    exit 0
fi

echo "Starting downloads (this may take 2-5 minutes)..."
echo ""

# Download models
download_file "$TEXT_MODEL_URL" "$TEXT_MODEL" "Text Encoder Model"
echo ""

download_file "$IMAGE_MODEL_URL" "$IMAGE_MODEL" "Image Encoder Model"
echo ""

download_file "$VOCAB_URL" "$VOCAB_FILE" "Vocabulary File"
echo ""

download_file "$MERGES_URL" "$MERGES_FILE" "BPE Merges File"
echo ""

# Verify all files exist
echo "============================================"
echo "Verification"
echo "============================================"
echo ""

missing=0
check_file() {
    local file=$1
    local name=$2

    if [ -f "$file" ]; then
        size=$(du -h "$file" | cut -f1)
        echo "✅ $name ($size)"
    else
        echo "❌ $name - MISSING"
        missing=1
    fi
}

check_file "$TEXT_MODEL" "Text Encoder"
check_file "$IMAGE_MODEL" "Image Encoder"
check_file "$VOCAB_FILE" "Vocabulary"
check_file "$MERGES_FILE" "BPE Merges"

echo ""
echo "Total size: $(du -sh $MODELS_DIR | cut -f1)"
echo ""

if [ $missing -eq 0 ]; then
    echo "============================================"
    echo "✅ All models downloaded successfully!"
    echo "============================================"
    echo ""
    echo "Models are ready at: $MODELS_DIR"
    echo ""
    echo "You can now run the application:"
    echo "  ./scripts/run-all-java-stack.sh"
    echo ""
    echo "The Java Search Service will start immediately"
    echo "without any download delays!"
    echo ""
else
    echo "============================================"
    echo "❌ Some models failed to download"
    echo "============================================"
    echo ""
    echo "Please check your internet connection and try again."
    echo "Or manually download from Hugging Face:"
    echo "  https://huggingface.co/Xenova/clip-vit-base-patch32"
    echo ""
    exit 1
fi
