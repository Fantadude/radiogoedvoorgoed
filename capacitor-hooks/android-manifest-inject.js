/**
 * Capacitor hook script to automatically inject required permissions
 * and service declarations into AndroidManifest.xml
 * 
 * This runs after `npx cap sync android` to ensure the manifest has
 * all required entries for background audio playback.
 */

const fs = require('fs');
const path = require('path');

const MANIFEST_PATH = path.join(__dirname, '../android/app/src/main/AndroidManifest.xml');

const REQUIRED_PERMISSIONS = [
  'android.permission.FOREGROUND_SERVICE',
  'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK',
  'android.permission.WAKE_LOCK',
  'android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS',
  'android.permission.INTERNET',
];

const FOREGROUND_SERVICE = `<service android:name="io.capawesome.capacitorjs.plugins.foregroundservice.AndroidForegroundService" 
             android:foregroundServiceType="mediaPlayback" />`;

function injectManifest() {
  if (!fs.existsSync(MANIFEST_PATH)) {
    console.log('AndroidManifest.xml not found. Run "npx cap add android" first.');
    return;
  }

  let manifest = fs.readFileSync(MANIFEST_PATH, 'utf8');
  let modified = false;

  // Inject missing permissions
  for (const permission of REQUIRED_PERMISSIONS) {
    const permissionTag = `<uses-permission android:name="${permission}"`;
    if (!manifest.includes(permissionTag)) {
      // Insert after the first <uses-permission> or after <manifest ...>
      const insertPoint = manifest.indexOf('<uses-permission');
      if (insertPoint !== -1) {
        manifest = manifest.slice(0, insertPoint) + 
          `<uses-permission android:name="${permission}" />\n    ` + 
          manifest.slice(insertPoint);
      } else {
        // Insert after <manifest ...> opening tag
        const manifestTagEnd = manifest.indexOf('>', manifest.indexOf('<manifest')) + 1;
        manifest = manifest.slice(0, manifestTagEnd) + 
          `\n    <uses-permission android:name="${permission}" />` + 
          manifest.slice(manifestTagEnd);
      }
      console.log(`✅ Added permission: ${permission}`);
      modified = true;
    }
  }

  // Inject foreground service if not present
  if (!manifest.includes('io.capawesome.capacitorjs.plugins.foregroundservice.AndroidForegroundService')) {
    const applicationEnd = manifest.lastIndexOf('</application>');
    if (applicationEnd !== -1) {
      manifest = manifest.slice(0, applicationEnd) + 
        `\n        ${FOREGROUND_SERVICE}\n    ` + 
        manifest.slice(applicationEnd);
      console.log('✅ Added ForegroundService declaration');
      modified = true;
    }
  }

  if (modified) {
    fs.writeFileSync(MANIFEST_PATH, manifest, 'utf8');
    console.log('\n✅ AndroidManifest.xml updated successfully!');
  } else {
    console.log('✅ AndroidManifest.xml already has all required entries.');
  }
}

// Run the injection
injectManifest();
