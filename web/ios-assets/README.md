# iOS App Icon Generator Script

This script generates all required iOS app icon sizes from `radio_logo.png`.

## Prerequisites

You need one of these tools installed:
- **ImageMagick** (recommended): Download from https://imagemagick.org/script/download.php#windows
- **GIMP**: Free alternative
- **Online tool**: Use https://appicon.co/ and manually place files

## Option 1: Using ImageMagick (Automated)

If you have ImageMagick installed, run these commands in PowerShell from the `web` folder:

```powershell
# Create the output directory
mkdir -p ios/App/App/Assets.xcassets/AppIcon.appiconset

# Define the source image
$source = "public/radio_logo.png"

# Generate all iOS icon sizes
magick $source -resize 40x40 ios/App/App/Assets.xcassets/AppIcon.appiconset/AppIcon-20x20@2x.png
magick $source -resize 60x60 ios/App/App/Assets.xcassets/AppIcon.appiconset/AppIcon-20x20@3x.png
magick $source -resize 58x58 ios/App/App/Assets.xcassets/AppIcon.appiconset/AppIcon-29x29@2x.png
magick $source -resize 87x87 ios/App/App/Assets.xcassets/AppIcon.appiconset/AppIcon-29x29@3x.png
magick $source -resize 80x80 ios/App/App/Assets.xcassets/AppIcon.appiconset/AppIcon-40x40@2x.png
magick $source -resize 120x120 ios/App/App/Assets.xcassets/AppIcon.appiconset/AppIcon-40x40@3x.png
magick $source -resize 120x120 ios/App/App/Assets.xcassets/AppIcon.appiconset/AppIcon-60x60@2x.png
magick $source -resize 180x180 ios/App/App/Assets.xcassets/AppIcon.appiconset/AppIcon-60x60@3x.png
magick $source -resize 152x152 ios/App/App/Assets.xcassets/AppIcon.appiconset/AppIcon-76x76@2x.png
magick $source -resize 167x167 ios/App/App/Assets.xcassets/AppIcon.appiconset/AppIcon-83.5x83.5@2x.png
magick $source -resize 1024x1024 ios/App/App/Assets.xcassets/AppIcon.appiconset/AppIcon-1024x1024@1x.png

# Copy the Contents.json
copy ios-assets/AppIcon.appiconset/Contents.json ios/App/App/Assets.xcassets/AppIcon.appiconset/Contents.json
```

## Option 2: Online Tool (No Installation Required)

1. Go to https://appicon.co/
2. Upload `web/public/radio_logo.png`
3. Select "iOS" platform
4. Download the generated icons
5. Extract the zip file
6. Copy all `.png` files to `ios/App/App/Assets.xcassets/AppIcon.appiconset/`
7. Replace the `Contents.json` with the one from `web/ios-assets/AppIcon.appiconset/`

## Option 3: Manual with Paint/Photoshop

Create these exact files with these exact sizes:

| Filename | Size | Purpose |
|----------|------|---------|
| AppIcon-20x20@2x.png | 40x40 | iPhone Notification |
| AppIcon-20x20@3x.png | 60x60 | iPhone Notification |
| AppIcon-29x29@2x.png | 58x58 | iPhone Settings |
| AppIcon-29x29@3x.png | 87x87 | iPhone Settings |
| AppIcon-40x40@2x.png | 80x80 | iPhone Spotlight |
| AppIcon-40x40@3x.png | 120x120 | iPhone Spotlight |
| AppIcon-60x60@2x.png | 120x120 | iPhone App Store |
| AppIcon-60x60@3x.png | 180x180 | iPhone App Store |
| AppIcon-76x76@2x.png | 152x152 | iPad App |
| AppIcon-83.5x83.5@2x.png | 167x167 | iPad Pro App |
| AppIcon-1024x1024@1x.png | 1024x1024 | App Store |

Place all files in: `ios/App/App/Assets.xcassets/AppIcon.appiconset/`

## Complete Setup Steps

1. **Add iOS platform** (if not done):
   ```bash
   cd web
   npm run build
   npx cap add ios
   ```

2. **Generate icons** using one of the options above

3. **Sync and open in Xcode**:
   ```bash
   npx cap sync ios
   npx cap open ios
   ```

4. **In Xcode**: Verify the icons appear in Assets.xcassets → AppIcon

5. **Build and run** on your device

## The White/Blue Icon Issue

The "white background with blue diagonal subjects" icon you're seeing is the **default Capacitor icon**. This will be replaced once you:
1. Generate the proper icon files as shown above
2. Rebuild the iOS project
3. Re-install the app on your device

**Important**: The old icon may be cached. After installing the new version:
- Delete the old app from your home screen
- Restart your device
- Install the new IPA
