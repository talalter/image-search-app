#!/bin/bash

# Docker Startup Script for Image Search Application
# This script validates the setup and starts all services

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=========================================="
echo "Image Search App - Docker Startup"
echo "=========================================="
echo ""

# Function to print colored messages
print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}!${NC} $1"
}

print_info() {
    echo -e "→ $1"
}

# Check Docker installation
print_info "Checking prerequisites..."
if ! command -v docker &> /dev/null; then
    print_error "Docker is not installed. Please install Docker first."
    exit 1
fi
print_success "Docker is installed"

if ! command -v docker-compose &> /dev/null; then
    print_error "Docker Compose is not installed. Please install Docker Compose first."
    exit 1
fi
print_success "Docker Compose is installed"

# Check if Docker daemon is running
if ! docker info &> /dev/null; then
    print_error "Docker daemon is not running. Please start Docker."
    exit 1
fi
print_success "Docker daemon is running"

echo ""

# Check required files
print_info "Validating Docker configuration files..."

required_files=(
    "docker-compose.yml"
    "java-backend/Dockerfile"
    "python-search-service/Dockerfile"
    "python-embedding-worker/Dockerfile"
    "frontend/Dockerfile.frontend"
)

for file in "${required_files[@]}"; do
    if [ ! -f "$file" ]; then
        print_error "Missing required file: $file"
        exit 1
    fi
done
print_success "All required Dockerfiles present"

# Check .dockerignore files
required_dockerignore=(
    "java-backend/.dockerignore"
    "python-search-service/.dockerignore"
    "python-embedding-worker/.dockerignore"
    "frontend/.dockerignore"
)

for file in "${required_dockerignore[@]}"; do
    if [ ! -f "$file" ]; then
        print_warning "Missing .dockerignore: $file (optional but recommended)"
    fi
done
print_success "Dockerignore files validated"

echo ""

# Check for port conflicts
print_info "Checking for port conflicts..."

ports=(3000 5000 5432 5672 8080 15672)
conflicts=0

for port in "${ports[@]}"; do
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1 ; then
        print_warning "Port $port is already in use"
        conflicts=$((conflicts + 1))
    fi
done

if [ $conflicts -gt 0 ]; then
    print_warning "Found $conflicts port conflicts. Services may fail to start."
    echo "You can stop conflicting services or continue anyway."
    read -p "Continue? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
else
    print_success "No port conflicts detected"
fi

echo ""

# Check available disk space
print_info "Checking available disk space..."
available_space=$(df . | tail -1 | awk '{print $4}')
# Convert to GB (assuming KB)
available_gb=$((available_space / 1024 / 1024))

if [ $available_gb -lt 10 ]; then
    print_warning "Low disk space: ${available_gb}GB available (10GB+ recommended)"
else
    print_success "Sufficient disk space: ${available_gb}GB available"
fi

echo ""
echo "=========================================="
echo "Starting Services"
echo "=========================================="
echo ""

# Ask user for build option
echo "Build options:"
echo "  1. Quick start (use existing images if available)"
echo "  2. Full rebuild (build from scratch, recommended for first run)"
echo "  3. Clean build (remove existing volumes and rebuild)"
read -p "Select option (1-3): " -n 1 -r
echo ""

case $REPLY in
    1)
        print_info "Starting services with existing images..."
        docker-compose up -d
        ;;
    2)
        print_info "Building and starting services..."
        docker-compose up --build -d
        ;;
    3)
        print_warning "This will remove all existing data volumes!"
        read -p "Are you sure? (y/n) " -n 1 -r
        echo ""
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            print_info "Stopping and removing existing services..."
            docker-compose down -v
            print_info "Building and starting services..."
            docker-compose up --build -d
        else
            print_info "Cancelled. Starting services normally..."
            docker-compose up --build -d
        fi
        ;;
    *)
        print_error "Invalid option. Exiting."
        exit 1
        ;;
esac

echo ""
print_info "Waiting for services to start..."
sleep 5

echo ""
echo "=========================================="
echo "Service Status"
echo "=========================================="
docker-compose ps

echo ""
echo "=========================================="
echo "Access Points"
echo "=========================================="
echo "Frontend:              http://localhost:3000"
echo "Backend API:           http://localhost:8080"
echo "Backend Health:        http://localhost:8080/actuator/health"
echo "RabbitMQ Management:   http://localhost:15672"
echo "  Username: imageuser"
echo "  Password: imagepass123"
echo ""

echo "=========================================="
echo "Useful Commands"
echo "=========================================="
echo "View logs:           docker-compose logs -f"
echo "Stop services:       docker-compose down"
echo "Restart service:     docker-compose restart [service-name]"
echo "Service shell:       docker-compose exec [service-name] sh"
echo ""

print_success "Setup complete! Application is starting..."
echo ""
print_info "Note: Services may take 1-2 minutes to fully initialize."
print_info "Check service health: docker-compose ps"
print_info "View real-time logs:  docker-compose logs -f"
echo ""
