# Create necessary directories
New-Item -ItemType Directory -Force -Path data
New-Item -ItemType Directory -Force -Path frontend/web
New-Item -ItemType Directory -Force -Path logs

# Copy frontend files if they exist in resources
if (Test-Path "src/main/resources/static") {
    Write-Host "Copying frontend files from resources..."
    Copy-Item -Path "src/main/resources/static/*" -Destination "frontend/web/" -Recurse -Force
    Copy-Item -Path "src/main/resources/templates/*" -Destination "frontend/web/" -Recurse -Force
}

# Create data directory for H2 database
New-Item -ItemType Directory -Force -Path data/db

# Set up environment variables for development
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:FRONTEND_PATH = Join-Path $PWD "frontend/web"

Write-Host "Development environment setup complete!"
Write-Host "Frontend files are in: $env:FRONTEND_PATH"
Write-Host "Database files will be in: $(Join-Path $PWD 'data/db')" 