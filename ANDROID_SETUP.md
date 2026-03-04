# Setting Up Radio Goed Voor Goed Android App

## Overview

This Android app is a WebView wrapper that loads the React web app and provides native background audio playback capabilities.

## Prerequisites

- Android Studio (latest version)
- Node.js and npm
- Android SDK with API 26+ (Android 8.0+)

## Quick Start

### 1. Build the Web App

Navigate to the web folder and build the production bundle:

```bash
cd C:\Users\Administrator\AndroidStudioProjects\radiogvg\web
npm install
npm run build
```

This creates a `dist/` folder with the production files.

### 2. Copy Web Files to Android Assets

Copy the built web files to the Android assets folder:

```bash
xcopy /E /I C:\Users\Administrator\AndroidStudioProjects\radiogvg\web\dist\* C:\Users\Administrator\AndroidStudioProjects\radiogvg\app\src\main\assets\
```

Or manually copy all files from `web/dist/` to `app/src/main/assets/`.

### 3. Build and Run in Android Studio

1. Open the project in Android Studio: `C:\Users\Administrator\AndroidStudioProjects\radiogvg`
2. Sync Gradle files
3. Build the project
4. Run on emulator or device

## Architecture

### Web Layer (React + TypeScript)
- RadioPlayer.tsx - Streams from `https://ex52.voordeligstreamen.nl/8154/stream`
- Requests.tsx - Connects to `http://86.84.18.58:3000/api/songs/*`
- Podcasts.tsx - Fetches from `http://86.84.18.58:3000/api/podcasts`

### Native Layer (Kotlin)
- MainActivity.kt - WebView container with JavaScript bridge
- RadioPlaybackService.kt - Foreground service for background audio
- AndroidManifest.xml - Permissions for internet, background playback, bluetooth

## Key Features

### Background Playback
The `RadioPlaybackService` runs as a foreground service with a notification, allowing audio to continue when the app is backgrounded.

### Bluetooth Support
- `BLUETOOTH_CONNECT` permission for Android 12+
- MediaPlayer routes audio through system (includes Bluetooth speakers/headphones)

### WebView Configuration
- JavaScript enabled
- Media playback without user gesture required
- Mixed content allowed (HTTP API calls)
- Caches web content

## Troubleshooting

### WebView shows blank page
- Verify `dist/` contents were copied to `assets/`
- Check Android Studio logcat for WebView errors
- Ensure `index.html` is at root of assets folder

### Audio stops when app goes background
- Check `RadioPlaybackService` is properly declared in manifest
- Verify `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission
- Check notification permission for Android 13+

### Cannot connect to API server
- Update API_BASE_URL in Requests.tsx and Podcasts.tsx
- Check server is running at `86.84.18.58:3000`
- Verify `usesCleartextTraffic="true"` in AndroidManifest.xml

### Stream not playing
- Test stream URL directly in browser: `https://ex52.voordeligstreamen.nl/8154/stream`
- Check internet permission is granted
- Verify volume is not muted

## Customization

### Change Stream URL
Edit `web/src/components/RadioPlayer.tsx`:
```typescript
const RADIO_CONFIG = {
  streamUrl: 'https://your-stream-url/stream',
  nowPlayingUrl: 'https://your-stream-url/status-json.xsl',
};
```

### Change API Server
Edit both:
- `web/src/components/Requests.tsx`
- `web/src/components/Podcasts.tsx`

```typescript
const API_BASE_URL = 'http://your-server-ip:3000';
```

## Build for Release

1. Build web app: `npm run build` in web folder
2. Copy to assets: `xcopy /E /I web\dist\* app\src\main\assets\`
3. In Android Studio: Build → Generate Signed Bundle/APK
4. Follow prompts to create or use existing keystore

## Permissions Explained

| Permission | Why Needed |
|------------|------------|
| `INTERNET` | Stream audio, fetch API data |
| `FOGROUND_SERVICE` | Keep audio playing in background |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Media playback service type (Android 10+) |
| `WAKE_LOCK` | Prevent device sleep during playback |
| `BLUETOOTH_CONNECT` | Output to Bluetooth speakers (Android 12+) |
| `MODIFY_AUDIO_SETTINGS` | Volume control |
| `POST_NOTIFICATIONS` | Show playback notification (Android 13+) |

## Development Tips

### Testing Web App in Browser
```bash
cd web
npm run dev
# Opens at http://localhost:5173
```

### Testing Web App in Android Emulator
Use `10.0.2.2` to access host machine localhost:
```kotlin
// In MainActivity.kt
loadUrl("http://10.0.2.2:5173") // For dev server
```

### Debug WebView
Enable WebView debugging in Chrome:
1. Enable USB debugging on Android device
2. Connect via USB
3. Chrome DevTools → Remote Devices → Inspect WebView
