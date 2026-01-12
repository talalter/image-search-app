#!/bin/bash

# Script to run the .NET backend
# Usage: ./scripts/run-dotnet.sh

echo "Starting .NET Backend on port 7000..."
echo ""

# Check if .NET SDK is installed
if ! command -v dotnet &> /dev/null; then
    echo "ERROR: .NET SDK not found!"
    echo ""
    echo "Please install .NET 8 SDK:"
    echo "  Ubuntu/Debian: https://learn.microsoft.com/en-us/dotnet/core/install/linux-ubuntu"
    echo "  Other: https://dotnet.microsoft.com/download/dotnet/8.0"
    exit 1
fi

# Navigate to project directory
cd "$(dirname "$0")/../dotnet-backend/ImageSearch.Api" || exit 1

# Check if project file exists
if [ ! -f "ImageSearch.Api.csproj" ]; then
    echo "ERROR: ImageSearch.Api.csproj not found!"
    echo "Make sure you're running this from the project root directory."
    exit 1
fi

# Set environment variables if needed
export ASPNETCORE_ENVIRONMENT="${ASPNETCORE_ENVIRONMENT:-Development}"
export ConnectionStrings__DefaultConnection="${ConnectionStrings__DefaultConnection:-Host=localhost;Port=5432;Database=imagesearch;Username=imageuser;Password=imagepass123}"
export SearchService__BaseUrl="${SearchService__BaseUrl:-http://localhost:5000}"

echo "Environment: $ASPNETCORE_ENVIRONMENT"
echo "Database: imagesearch@localhost:5432"
echo "Search Service: $SearchService__BaseUrl"
echo ""

# Restore packages if needed
if [ ! -d "bin" ] || [ ! -d "obj" ]; then
    echo "Restoring NuGet packages..."
    dotnet restore
    echo ""
fi

# Run the application
echo "Starting application..."
echo "------------------------------------------------------"
dotnet run
