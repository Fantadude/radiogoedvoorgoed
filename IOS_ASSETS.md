# iOS App Assets Setup

This guide explains how to reuse the `radio_logo.png` from the Android app for the iOS app.

## Current Setup

The `radio_logo.png` has already been copied to the web app:
- **Location**: `web/public/radio_logo.png`
- **Usage**: Album art fallback and iOS app icon

## What's Already Configured

### 1. Web App Album Art (Completed)
The `RadioPlayer` component now uses `radio_logo.png` as the fallback album art when no track artwork is available from the streaming server:

```tsx
{radioMetadata.artUrl ? (
  <img src={radioMetadata.artUrl} alt="Album Art" className="album-art-image" />
) : (
  <img src="/radio_logo.png" alt="Radio GoedvoorGoed Logo" className="album-art-logo" />
)}
```

The logo has a subtle pulse animation when the radio is playing.

### 2. Web App Icons (Completed)
The `index.html` has been updated with iOS app icon links:

```html
<link rel="icon" type="image/png" href="/radio_logo.png" />
<link rel="apple-touch-icon" href="/radio_logo.png" />
<link rel="apple-touch-icon" sizes="152x152" href="/radio_logo.png" />
<link rel="apple-touch-icon" sizes="180x180" href="/radio_logo.png" />
<link rel="apple-touch-icon" sizes="167x167" href="/radio_logo.png" />
```

## The White/Blue Icon Issue

The "white background with blue diagonal subjects" icon you're seeing is the **default Capacitor iOS icon**. This appears when the native iOS app icon assets haven't been generated yet.

To fix this:
1. Generate the proper icon files as described below
2. Make sure to **delete the old app from your device** before installing the new version (iOS caches icons aggressively)
3. Rebuild and reinstall the IPA

## Remaining Steps for Full iOS Native App

When you add the iOS platform to Capacitor, you'll need to create properly sized iOS app icons:

### iOS App Icon Sizes Required

Create these icon sizes from `radio_logo.png` and place them in `ios/App/App/Assets.xcassets/AppIcon.appiconset/`:

| Size | Filename | Usage |
|------|----------|-------|
| 20x20@2x | AppIcon-20x20@2x.png | Notification (iPad) |
| 20x20@3x | AppIcon-20x20@3x.png | Notification (iPhone) |
| 29x29@2x | AppIcon-29x29@2x.png | Settings (iPad) |
| 29x29@3x | AppIcon-29x29@3x.png | Settings (iPhone) |
| 40x40@2x | AppIcon-40x40@2x.png | Spotlight (iPad) |
| 40x40@3x | AppIcon-40x40@3x.png | Spotlight (iPhone) |
| 60x60@2x | AppIcon-60x60@2x.png | App Store (iPhone) |
| 60x60@3x | AppIcon-60x60@3x.png | App Store (iPhone) |
| 76x76@2x | AppIcon-76x76@2x.png | Home screen (iPad) |
| 83.5x83.5@2x | AppIcon-83.5x83.5@2x.png | Home screen (iPad Pro) |
| 1024x1024 | AppIcon-1024x1024@1x.png | App Store |

### Contents.json for AppIcon.appiconset

```json
{
  "images": [
    {
      "filename": "AppIcon-20x20@2x.png",
      "idiom": "iphone",
      "scale": "2x",
      "size": "20x20"
    },
    {
      "filename": "AppIcon-20x20@3x.png",
      "idiom": "iphone",
      "scale": "3x",
      "size": "20x20"
    },
    {
      "filename": "AppIcon-29x29@2x.png",
      "idiom": "iphone",
      "scale": "2x",
      "size": "29x29"
    },
    {
      "filename": "AppIcon-29x29@3x.png",
      "idiom": "iphone",
      "scale": "3x",
      "size": "29x29"
    },
    {
      "filename": "AppIcon-40x40@2x.png",
      "idiom": "iphone",
      "scale": "2x",
      "size": "40x40"
    },
    {
      "filename": "AppIcon-40x40@3x.png",
      "idiom": "iphone",
      "scale": "3x",
      "size": "40x40"
    },
    {
      "filename": "AppIcon-60x60@2x.png",
      "idiom": "iphone",
      "scale": "2x",
      "size": "60x60"
    },
    {
      "filename": "AppIcon-60x60@3x.png",
      "idiom": "iphone",
      "scale": "3x",
      "size": "60x60"
    },
    {
      "filename": "AppIcon-20x20@2x.png",
      "idiom": "ipad",
      "scale": "2x",
      "size": "20x20"
    },
    {
      "filename": "AppIcon-29x29@2x.png",
      "idiom": "ipad",
      "scale": "2x",
      "size": "29x29"
    },
    {
      "filename": "AppIcon-40x40@2x.png",
      "idiom": "ipad",
      "scale": "2x",
      "size": "40x40"
    },
    {
      "filename": "AppIcon-76x76@2x.png",
      "idiom": "ipad",
      "scale": "2x",
      "size": "76x76"
    },
    {
      "filename": "AppIcon-83.5x83.5@2x.png",
      "idiom": "ipad",
      "scale": "2x",
      "size": "83.5x83.5"
    },
    {
      "filename": "AppIcon-1024x1024@1x.png",
      "idiom": "ios-marketing",
      "scale": "1x",
      "size": "1024x1024"
    }
  ],
  "info": {
    "author": "xcode",
    "version": 1
  }
}
```

### Creating the Icons

**Option 1: Automated Scripts (Recommended)**

We've included icon generation scripts in `web/ios-assets/`:

1. **Using PowerShell**:
   ```powershell
   cd web/ios-assets
   .\generate-icons.ps1
   ```

2. **Using Command Prompt**:
   ```cmd
   cd web/ios-assets
   generate-icons.bat
   ```

**Requirements**: Install [ImageMagick](https://imagemagick.org/script/download.php#windows) first

**Option 2: Online Tool (No Installation)**:
1. Go to [App Icon Generator](https://appicon.co/) or similar
2. Upload `web/public/radio_logo.png`
3. Download the iOS icon set
4. Extract to `ios/App/App/Assets.xcassets/AppIcon.appiconset/`
5. Replace `Contents.json` with the one from `web/ios-assets/AppIcon.appiconset/`

**Option 3: Image Editor (Photoshop/GIMP/Photopea)**:
1. Open `radio_logo.png`
2. Create square canvas for each size (maintain aspect ratio with padding)
3. Export each size with the correct filename
4. Place in `AppIcon.appiconset/`

See `web/ios-assets/README.md` for detailed instructions.

## Generating iOS Project

Once the assets are ready, generate the iOS project:

```bash
cd web
npm run build
npx cap add ios
npx cap sync ios
```

Then open Xcode and set the app icons:
```bash
npx cap open ios
```

## Splash Screen (Optional)

For a complete iOS app experience, also create a splash screen using the radio logo:

1. Create a storyboard-based launch screen in Xcode
2. Or use a simple splash image in `Assets.xcassets/Splash.imageset/`

Recommended splash screen sizes:
- 2732x2732px (universal, scales down)

## Summary

| Feature | Status | Location |
|---------|--------|----------|
| Album art fallback | ✅ Done | `RadioPlayer.tsx` |
| Web app icon | ✅ Done | `index.html` |
| Native iOS icons | ⏳ Pending | Xcode project |
| Splash screen | ⏳ Optional | Xcode project |

The `radio_logo.png` is now ready to be used across both Android and iOS platforms!
