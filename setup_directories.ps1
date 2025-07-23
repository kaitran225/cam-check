# PowerShell script to create Flutter project directory structure
# Navigate to the project root
cd $PSScriptRoot

# Create main directory structure
$dirs = @(
    ".flutter\stcam\lib\screens\streaming",
    ".flutter\stcam\lib\screens\settings",
    ".flutter\stcam\assets\images",
    ".flutter\stcam\assets\icons",
    ".flutter\stcam\assets\fonts"
)

# Create each directory
foreach ($dir in $dirs) {
    if (-not (Test-Path $dir)) {
        Write-Host "Creating directory: $dir"
        New-Item -Path $dir -ItemType Directory -Force
    } else {
        Write-Host "Directory already exists: $dir"
    }
}

# Create API directory structure
$apiDirs = @(
    ".flutter\stcam\lib\api\api_client.dart",
    ".flutter\stcam\lib\api\auth_service.dart",
    ".flutter\stcam\lib\api\device_service.dart",
    ".flutter\stcam\lib\api\session_service.dart",
    ".flutter\stcam\lib\api\webrtc_service.dart"
)

# Create empty files for API services
foreach ($file in $apiDirs) {
    if (-not (Test-Path $file)) {
        Write-Host "Creating file: $file"
        New-Item -Path $file -ItemType File -Force
    } else {
        Write-Host "File already exists: $file"
    }
}

# Create config files
$configFiles = @(
    ".flutter\stcam\lib\config\app_config.dart",
    ".flutter\stcam\lib\config\theme.dart"
)

# Create empty config files
foreach ($file in $configFiles) {
    if (-not (Test-Path $file)) {
        Write-Host "Creating file: $file"
        New-Item -Path $file -ItemType File -Force
    } else {
        Write-Host "File already exists: $file"
    }
}

Write-Host "Directory structure setup complete!" 