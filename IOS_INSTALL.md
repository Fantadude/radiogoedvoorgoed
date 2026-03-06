# iOS Installation Guide

This guide explains how to install the unsigned IPA of Radio GoedvoorGoed on your iOS device **without** an Apple Developer account.

## ⚠️ Important Notice

The IPA file provided in GitHub Releases is **unsigned**, meaning it requires sideloading. Apple restricts installation of unsigned apps, so you'll need one of the methods below.

---

## Method 1: AltStore (Recommended) ⭐

**Best for:** Most users, works on iOS 12.2+ (including iOS 17+)
**Cost:** Free (no Apple Developer account needed)
**Renewal:** Every 7 days (automatic with AltStore running)

### Steps:

1. **Install AltStore on your computer:**
   - Windows/Mac: Download from [altstore.io](https://altstore.io)
   - Linux: Use [AltServer-Linux](https://github.com/NyaMisty/AltServer-Linux)

2. **Install AltStore on your iPhone:**
   - Connect iPhone to computer via USB
   - Run AltServer on your computer
   - Install AltStore app on your iPhone
   - Follow prompts to trust the developer profile

3. **Install the Radio GoedvoorGoed IPA:**
   - Open AltStore on your iPhone
   - Go to "My Apps" tab
   - Tap the "+" button
   - Select the downloaded `RadioGoedvoorGoed-unsigned.ipa` file
   - Enter your Apple ID when prompted (it's only sent to Apple for signing)
   - Wait for installation to complete

4. **Trust the app:**
   - Go to Settings → General → VPN & Device Management
   - Tap your Apple ID under "Developer App"
   - Tap "Trust"

5. **Keep it running:**
   - Keep AltServer running on your computer
   - Connect to same WiFi as your iPhone
   - AltStore will auto-refresh the app every 7 days

---

## Method 2: Sideloadly

**Best for:** One-time installs, users who don't want to keep AltServer running
**Cost:** Free
**Renewal:** Every 7 days (manual re-install required)

### Steps:

1. **Download Sideloadly:**
   - Get it from [iosgods.com](https://sideloadly.io) or [sideloadly.io](https://sideloadly.io)

2. **Install the IPA:**
   - Connect iPhone to computer via USB
   - Open Sideloadly
   - Drag and drop the IPA file into Sideloadly
   - Enter your Apple ID
   - Click "Start" and follow instructions

3. **Trust the app:**
   - Go to Settings → General → VPN & Device Management
   - Find your Apple ID and tap "Trust"

---

## Method 3: TrollStore (iOS 14.0 - 17.0 only)

**Best for:** Permanent installation, no re-signing needed
**Requirements:** Specific iOS versions (not iOS 17.0.1+)
**Cost:** Free

### Check Compatibility:

TrollStore works on:
- iOS 14.0 - 16.6.1
- iOS 16.7 RC (but not 16.7 final)
- iOS 17.0 (but NOT 17.0.1 or later)

### Steps:

1. **Check if your iOS version is supported** at [ios.cfw.guide](https://ios.cfw.guide/installing-trollstore/)

2. **Install TrollStore:**
   - Follow the guide for your specific iOS version
   - Usually involves installing a helper app

3. **Install the IPA:**
   - Open TrollStore
   - Tap "Install IPA"
   - Select `RadioGoedvoorGoed-unsigned.ipa`
   - App installs permanently with no 7-day renewal!

---

## Method 4: Build and Run on Simulator (macOS only)

**Best for:** Testing without an iPhone
**Cost:** Free
**Requirements:** macOS with Xcode installed

### Steps:

1. **Clone the repository:**
   ```bash
   git clone https://github.com/yourusername/radiogvg.git
   cd radiogvg/web
   ```

2. **Install dependencies:**
   ```bash
   npm install
   npm run build
   npx cap sync ios
   ```

3. **Open in Xcode:**
   ```bash
   npx cap open ios
   ```

4. **Run on Simulator:**
   - In Xcode, select an iPhone simulator
   - Click the Play button ▶️

---

## Troubleshooting

### "Unable to Install" Error
- Make sure you're using a supported iOS version
- Try a different sideloading method
- Restart your iPhone and try again

### App Crashes on Launch
- Delete the app and reinstall
- Make sure you trusted the developer profile in Settings
- Try re-downloading the IPA file

### "This app is no longer available" (after 7 days)
- This is normal for free Apple IDs
- Re-install using the same method
- For AltStore: make sure AltServer is running on your computer

### No Sound / Audio Stops When Screen Locks
- This is a known limitation with sideloaded apps
- Keep the app open in foreground, or
- Use the Control Center to play/pause (it helps keep audio alive)
- Some background audio issues may occur due to iOS restrictions on unsigned apps

---

## Features

The iOS app includes all features from the Android version:
- ✅ Radio streaming (128kbps MP3)
- ✅ Now playing with artist/title
- ✅ Listener count display
- ✅ Recently played history
- ✅ Song requests
- ✅ Podcasts playback
- ✅ Volume control
- ✅ Background playback (with limitations on unsigned builds)

---

## Alternative: Web App

If sideloading doesn't work for you, the web app is always available at:
**https://radiogoedvoorgoed.nl** (or your hosted URL)

Simply open it in Safari and add to Home Screen for an app-like experience.

---

## Support

For issues with:
- **AltStore:** Visit [altstore.io/faq](https://altstore.io/faq)
- **Sideloadly:** Check [Sideloadly Discord](https://discord.gg/sideloading)
- **TrollStore:** See [r/Trollstore](https://reddit.com/r/Trollstore)
- **This app:** Open an issue on GitHub

---

## ⚖️ Legal Notice

Sideloading unsigned apps is legal and permitted by Apple for personal use. However:
- You are responsible for complying with Apple's Terms of Service
- This app is provided as-is for personal use
- No warranty is provided
