# Multi-stage Dockerfile for Python Search Service with CLIP and FAISS
# Stage 1: Build stage with Python dependencies
FROM python:3.11-slim AS builder

WORKDIR /app

# Install build dependencies (minimal set for compiling Python packages)
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    git \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements first (for layer caching)
COPY requirements.txt .

# Create virtual environment and install dependencies
RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Install Python dependencies
# PyTorch CPU version is specified in requirements.txt with --extra-index-url
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

# Pre-download CLIP model to avoid first-run delay
# This caches the model in the image (~350MB but worth it for faster startup)
RUN python -c "import clip; clip.load('ViT-B/32', device='cpu', download_root='/app/.cache/clip')" || echo "CLIP download skipped"

# Stage 2: Runtime stage with minimal dependencies
FROM python:3.11-slim

# Install runtime dependencies only (libgomp1 for scikit-learn/numpy, curl for health checks, gosu for privilege dropping)
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgomp1 \
    curl \
    gosu \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user with specific UID/GID for consistency across services
RUN groupadd -g 1000 appuser && useradd -u 1000 -g appuser -m -d /home/appuser appuser

WORKDIR /app

# Copy virtual environment from builder
COPY --from=builder /opt/venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Copy pre-downloaded CLIP model cache
COPY --from=builder /app/.cache /app/.cache

# Copy application code
COPY app.py embedding_service.py search_handler.py ./

# Create directories for data volumes and ensure cache is accessible
RUN mkdir -p /app/data/uploads /app/data/indexes /app/.cache/clip /app/.cache/torch /app/.cache/huggingface && \
    chown -R appuser:appuser /app /home/appuser

# Switch to non-root user
USER appuser

# Set HOME and model cache directories
ENV HOME=/app \
    TORCH_HOME=/app/.cache/torch \
    HF_HOME=/app/.cache/huggingface \
    CLIP_CACHE=/app/.cache/clip

# Expose port
EXPOSE 5000

# Health check - using curl for better reliability
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
    CMD curl -f http://localhost:5000/ || exit 1

# Set Python environment variables
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONIOENCODING=utf-8

# Run the application as non-root user
CMD ["python", "app.py"]
