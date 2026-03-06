@echo off
chcp 65001 >nul
TITLE iOS Icon Generator

REM iOS Icon Generator Batch Script
REM This script generates all required iOS app icon sizes from radio_logo.png
REM Requires ImageMagick to be installed: https://imagemagick.org/script/download.php#windows

set SOURCE_IMAGE=public\radio_logo.png
set OUTPUT_DIR=ios\App\App\Assets.xcassets\AppIcon.appiconset

echo.
echo =========================================
echo    iOS App Icon Generator
echo =========================================
echo.

REM Check if ImageMagick is installed
where magick >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: ImageMagick is not installed or not in PATH.
    echo.
    echo Please download and install ImageMagick from:
    echo https://imagemagick.org/script/download.php#windows
    echo.
    echo Alternative: Use the online tool at https://appicon.co/
    echo.
    pause
    exit /b 1
)

REM Check if source image exists
if not exist "%SOURCE_IMAGE%" (
    echo ERROR: Source image not found: %SOURCE_IMAGE%
    pause
    exit /b 1
)

REM Create output directory if it doesn't exist
if not exist "%OUTPUT_DIR%" (
    mkdir "%OUTPUT_DIR%" 2>nul
    if %errorlevel% neq 0 (
        echo ERROR: Could not create directory: %OUTPUT_DIR%
        pause
        exit /b 1
    )
    echo Created directory: %OUTPUT_DIR%
)

echo Generating iOS app icons from %SOURCE_IMAGE%...
echo.

REM Generate icons
echo   Generating AppIcon-20x20@2x.png (40x40)...
magick "%SOURCE_IMAGE%" -resize 40x40^ -gravity center -extent 40x40 -strip "%OUTPUT_DIR%\AppIcon-20x20@2x.png"

echo   Generating AppIcon-20x20@3x.png (60x60)...
magick "%SOURCE_IMAGE%" -resize 60x60^ -gravity center -extent 60x60 -strip "%OUTPUT_DIR%\AppIcon-20x20@3x.png"

echo   Generating AppIcon-29x29@2x.png (58x58)...
magick "%SOURCE_IMAGE%" -resize 58x58^ -gravity center -extent 58x58 -strip "%OUTPUT_DIR%\AppIcon-29x29@2x.png"

echo   Generating AppIcon-29x29@3x.png (87x87)...
magick "%SOURCE_IMAGE%" -resize 87x87^ -gravity center -extent 87x87 -strip "%OUTPUT_DIR%\AppIcon-29x29@3x.png"

echo   Generating AppIcon-40x40@2x.png (80x80)...
magick "%SOURCE_IMAGE%" -resize 80x80^ -gravity center -extent 80x80 -strip "%OUTPUT_DIR%\AppIcon-40x40@2x.png"

echo   Generating AppIcon-40x40@3x.png (120x120)...
magick "%SOURCE_IMAGE%" -resize 120x120^ -gravity center -extent 120x120 -strip "%OUTPUT_DIR%\AppIcon-40x40@3x.png"

echo   Generating AppIcon-60x60@2x.png (120x120)...
magick "%SOURCE_IMAGE%" -resize 120x120^ -gravity center -extent 120x120 -strip "%OUTPUT_DIR%\AppIcon-60x60@2x.png"

echo   Generating AppIcon-60x60@3x.png (180x180)...
magick "%SOURCE_IMAGE%" -resize 180x180^ -gravity center -extent 180x180 -strip "%OUTPUT_DIR%\AppIcon-60x60@3x.png"

echo   Generating AppIcon-76x76@2x.png (152x152)...
magick "%SOURCE_IMAGE%" -resize 152x152^ -gravity center -extent 152x152 -strip "%OUTPUT_DIR%\AppIcon-76x76@2x.png"

echo   Generating AppIcon-83.5x83.5@2x.png (167x167)...
magick "%SOURCE_IMAGE%" -resize 167x167^ -gravity center -extent 167x167 -strip "%OUTPUT_DIR%\AppIcon-83.5x83.5@2x.png"

echo   Generating AppIcon-1024x1024@1x.png (1024x1024)...
magick "%SOURCE_IMAGE%" -resize 1024x1024^ -gravity center -extent 1024x1024 -strip "%OUTPUT_DIR%\AppIcon-1024x1024@1x.png"

echo.

REM Copy Contents.json
if exist "ios-assets\AppIcon.appiconset\Contents.json" (
    copy /Y "ios-assets\AppIcon.appiconset\Contents.json" "%OUTPUT_DIR%\Contents.json" >nul
    echo Copied Contents.json
) else (
    echo WARNING: Contents.json not found at ios-assets\AppIcon.appiconset\Contents.json
)

echo.
echo =========================================
echo    iOS icon generation complete!
echo =========================================
echo.
echo Next steps:
echo   1. Ensure iOS platform is added: npx cap add ios
echo   2. Sync the project: npx cap sync ios
echo   3. Open in Xcode: npx cap open ios
echo   4. Build and run on your device
echo.
echo IMPORTANT: Delete the old app from your device
echo first to clear the icon cache!
echo.
pause
