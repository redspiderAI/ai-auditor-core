# PowerShell script to build the gateway service

# Create build directory
if (!(Test-Path -Path "build")) {
    New-Item -ItemType Directory -Path "build"
}

# Build the main application
Write-Host "Building gateway-go..."
go build -o build\gateway-go.exe .

Write-Host "Build completed successfully!"