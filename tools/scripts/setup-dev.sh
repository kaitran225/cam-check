#!/bin/bash

# Create necessary directories
mkdir -p data
mkdir -p frontend/web
mkdir -p logs

# Copy frontend files if they exist in resources
if [ -d "src/main/resources/static" ]; then
    echo "Copying frontend files from resources..."
    cp -r src/main/resources/static/* frontend/web/
    cp -r src/main/resources/templates/* frontend/web/
fi

# Create data directory for H2 database
mkdir -p data/db

# Set up environment variables for development
export SPRING_PROFILES_ACTIVE=dev
export FRONTEND_PATH=$(pwd)/frontend/web

echo "Development environment setup complete!"
echo "Frontend files are in: $FRONTEND_PATH"
echo "Database files will be in: $(pwd)/data/db" 