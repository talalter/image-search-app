# Backend Dockerfile for Image Search App
# Multi-stage build for optimized image size

FROM python:3.12-slim as base

# Set working directory
WORKDIR /app

# Install system dependencies (git needed for CLIP, curl for healthcheck)
RUN apt-get update && apt-get install -y \
    git \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements first for better caching
COPY ../requirements.txt .

# Install Python dependencies
# Note: PyTorch CPU-only would be smaller, but requires extra index URL
RUN pip install --no-cache-dir -r requirements.txt

# Copy backend application code
COPY . .

# Create directories for runtime data
RUN mkdir -p images faisses_indexes

# Expose port
EXPOSE 9999

# Set environment variables
ENV PYTHONUNBUFFERED=1
ENV STORAGE_BACKEND=local

# Health check (using curl instead of requests library)
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:9999/ || exit 1

# Run the application
CMD ["uvicorn", "api:app", "--host", "0.0.0.0", "--port", "9999"]
