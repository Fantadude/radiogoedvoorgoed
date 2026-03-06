# iOS Icon Generator PowerShell Script
# This script generates all required iOS app icon sizes from radio_logo.png
# Requires ImageMagick to be installed: https://imagemagick.org/script/download.php#windows

param(
    [string]$SourceImage = "public/radio_logo.png",
    [string]$OutputDir = "ios/App/App/Assets.xcassets/AppIcon.appiconset"
)

# Check if ImageMagick is installed
$magick = Get-Command "magick" -ErrorAction SilentlyContinue
if (-not $magick) {
    Write-Host "ERROR: ImageMagick is not installed or not in PATH." -ForegroundColor Red
    Write-Host "Please download and install ImageMagick from: https://imagemagick.org/script/download.php#windows" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Alternative: Use the online tool at https://appicon.co/" -ForegroundColor Cyan
    exit 1
}

# Check if source image exists
if (-not (Test-Path $SourceImage)) {
    Write-Host "ERROR: Source image not found: $SourceImage" -ForegroundColor Red
    exit 1
}

# Create output directory if it doesn't exist
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
    Write-Host "Created directory: $OutputDir" -ForegroundColor Green
}

Write-Host "Generating iOS app icons from $SourceImage..." -ForegroundColor Cyan
Write-Host ""

# Define icon sizes
$icons = @(
    @{ Name = "AppIcon-20x20@2x.png"; Size = 40 },
    @{ Name = "AppIcon-20x20@3x.png"; Size = 60 },
    @{ Name = "AppIcon-29x29@2x.png"; Size = 58 },
    @{ Name = "AppIcon-29x29@3x.png"; Size = 87 },
    @{ Name = "AppIcon-40x40@2x.png"; Size = 80 },
    @{ Name = "AppIcon-40x40@3x.png"; Size = 120 },
    @{ Name = "AppIcon-60x60@2x.png"; Size = 120 },
    @{ Name = "AppIcon-60x60@3x.png"; Size = 180 },
    @{ Name = "AppIcon-76x76@2x.png"; Size = 152 },
    @{ Name = "AppIcon-83.5x83.5@2x.png"; Size = 167 },
    @{ Name = "AppIcon-1024x1024@1x.png"; Size = 1024 }
)

# Generate each icon
foreach ($icon in $icons) {
    $outputPath = Join-Path $OutputDir $icon.Name
    $size = $icon.Size
    
    Write-Host "  Generating $($icon.Name) (${size}x${size})..." -NoNewline
    
    try {
        # Use ImageMagick to resize the image
        # -resize with ^ ensures minimum dimensions, then -extent crops to exact size centered
        magick $SourceImage -resize "${size}x${size}^" -gravity center -extent "${size}x${size}" -strip $outputPath
        
        if (Test-Path $outputPath) {
            Write-Host " OK" -ForegroundColor Green
        } else {
            Write-Host " FAILED" -ForegroundColor Red
        }
    } catch {
        Write-Host " ERROR: $_" -ForegroundColor Red
    }
}

Write-Host ""

# Copy Contents.json
$contentsJsonSource = "ios-assets/AppIcon.appiconset/Contents.json"
$contentsJsonDest = Join-Path $OutputDir "Contents.json"

if (Test-Path $contentsJsonSource) {
    Copy-Item $contentsJsonSource $contentsJsonDest -Force
    Write-Host "Copied Contents.json" -ForegroundColor Green
} else {
    Write-Host "WARNING: Contents.json not found at $contentsJsonSource" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "iOS icon generation complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  1. Ensure iOS platform is added: npx cap add ios" -ForegroundColor White
Write-Host "  2. Sync the project: npx cap sync ios" -ForegroundColor White
Write-Host "  3. Open in Xcode: npx cap open ios" -ForegroundColor White
Write-Host "  4. Build and run on your device" -ForegroundColor White
Write-Host ""
Write-Host "IMPORTANT: Delete the old app from your device first to clear the icon cache!" -ForegroundColor Yellow
