#!/bin/bash

################################################################################
# CLIP ONNX Model Download Script
#
# Downloads pre-converted CLIP ONNX models for Java inference.
# No Python required on the client machine!
#
# Usage:
#   ./download-models.sh
#
# Best Practice: Automated model provisioning for reproducible deployments
################################################################################

set -e  # Exit on error

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Configuration
MODELS_DIR="models"
HF_REPO="Xenova/clip-vit-base-patch32"
HF_BASE_URL="https://huggingface.co/${HF_REPO}/resolve/main"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  CLIP ONNX Model Download${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Create models directory
echo -e "${YELLOW}[1/4]${NC} Creating models directory..."
mkdir -p "${MODELS_DIR}"
echo -e "${GREEN}✓${NC} Models directory created: ${MODELS_DIR}"
echo ""

# Download text encoder
echo -e "${YELLOW}[2/4]${NC} Downloading CLIP text encoder (ONNX)..."
TEXT_MODEL="clip-vit-base-patch32-text.onnx"
if [ -f "${MODELS_DIR}/${TEXT_MODEL}" ]; then
    echo -e "${GREEN}✓${NC} Text encoder already exists, skipping download"
else
    wget -q --show-progress \
        "${HF_BASE_URL}/onnx/text_model.onnx" \
        -O "${MODELS_DIR}/${TEXT_MODEL}" || {
        echo -e "${RED}✗${NC} Failed to download text encoder"
        echo -e "${YELLOW}Note:${NC} You may need to download manually from:"
        echo -e "  ${HF_BASE_URL}/onnx/text_model.onnx"
        exit 1
    }
    echo -e "${GREEN}✓${NC} Text encoder downloaded successfully"
fi
echo ""

# Download visual encoder
echo -e "${YELLOW}[3/4]${NC} Downloading CLIP visual encoder (ONNX)..."
IMAGE_MODEL="clip-vit-base-patch32-visual.onnx"
if [ -f "${MODELS_DIR}/${IMAGE_MODEL}" ]; then
    echo -e "${GREEN}✓${NC} Visual encoder already exists, skipping download"
else
    wget -q --show-progress \
        "${HF_BASE_URL}/onnx/vision_model.onnx" \
        -O "${MODELS_DIR}/${IMAGE_MODEL}" || {
        echo -e "${RED}✗${NC} Failed to download visual encoder"
        echo -e "${YELLOW}Note:${NC} You may need to download manually from:"
        echo -e "  ${HF_BASE_URL}/onnx/vision_model.onnx"
        exit 1
    }
    echo -e "${GREEN}✓${NC} Visual encoder downloaded successfully"
fi
echo ""

# Create dummy vocab files (since we use simplified tokenizer)
echo -e "${YELLOW}[4/4]${NC} Creating placeholder vocabulary files..."
touch "${MODELS_DIR}/vocab.txt"
touch "${MODELS_DIR}/merges.txt"
echo -e "${GREEN}✓${NC} Placeholder files created"
echo ""

# Display summary
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}✓ Setup Complete!${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "Downloaded models:"
ls -lh "${MODELS_DIR}"/ | grep -E "\.onnx$" || echo "  (Model files listing unavailable)"
echo ""
echo -e "${GREEN}Next steps:${NC}"
echo -e "  1. Run: ${BLUE}./gradlew bootRun${NC}"
echo -e "  2. Check health: ${BLUE}curl http://localhost:5001/actuator/health${NC}"
echo -e "  3. Test embedding: ${BLUE}curl http://localhost:5001/api/...${NC}"
echo ""
echo -e "${YELLOW}Note:${NC} Models are loaded automatically on application startup."
echo ""
