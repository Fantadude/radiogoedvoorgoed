const sharp = require('sharp');
const fs = require('fs');
const path = require('path');

// Get the directory where this script is located
const scriptDir = __dirname;

// iOS App Icon sizes required
const icons = [
  { name: 'AppIcon-20x20@2x.png', size: 40 },
  { name: 'AppIcon-20x20@3x.png', size: 60 },
  { name: 'AppIcon-29x29@2x.png', size: 58 },
  { name: 'AppIcon-29x29@3x.png', size: 87 },
  { name: 'AppIcon-40x40@2x.png', size: 80 },
  { name: 'AppIcon-40x40@3x.png', size: 120 },
  { name: 'AppIcon-60x60@2x.png', size: 120 },
  { name: 'AppIcon-60x60@3x.png', size: 180 },
  { name: 'AppIcon-76x76@2x.png', size: 152 },
  { name: 'AppIcon-83.5x83.5@2x.png', size: 167 },
  { name: 'AppIcon-1024x1024@1x.png', size: 1024 }
];

const sourceImage = path.join(scriptDir, 'public/radio_logo.png');
const outputDir = path.join(scriptDir, 'ios/App/App/Assets.xcassets/AppIcon.appiconset');
const contentsJsonSource = path.join(scriptDir, 'ios-assets/AppIcon.appiconset/Contents.json');

async function generateIcons() {
  console.log('Generating iOS app icons from radio_logo.png...\n');

  // Check if source image exists
  if (!fs.existsSync(sourceImage)) {
    console.error(`ERROR: Source image not found: ${sourceImage}`);
    process.exit(1);
  }

  // Create output directory if it doesn't exist
  if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
    console.log(`Created directory: ${outputDir}\n`);
  }

  // Generate each icon
  for (const icon of icons) {
    const outputPath = path.join(outputDir, icon.name);
    const size = icon.size;

    process.stdout.write(`  Generating ${icon.name} (${size}x${size})... `);

    try {
      await sharp(sourceImage)
        .resize(size, size, {
          fit: 'cover',
          position: 'center'
        })
        .png()
        .toFile(outputPath);

      console.log('OK');
    } catch (err) {
      console.log('FAILED');
      console.error(`    Error: ${err.message}`);
    }
  }

  // Copy Contents.json
  const contentsJsonDest = path.join(outputDir, 'Contents.json');

  console.log('\nCopying Contents.json...');
  if (fs.existsSync(contentsJsonSource)) {
    fs.copyFileSync(contentsJsonSource, contentsJsonDest);
    console.log('OK');
  } else {
    console.log('WARNING: Contents.json not found');
  }

  console.log('\n✅ iOS icon generation complete!');
  console.log('\nNext steps:');
  console.log('  1. Ensure iOS platform is added: npx cap add ios');
  console.log('  2. Sync the project: npx cap sync ios');
  console.log('  3. Open in Xcode: npx cap open ios');
  console.log('  4. Build and run on your device');
  console.log('\n⚠️  IMPORTANT: Delete the old app from your device first to clear the icon cache!');
}

generateIcons().catch(err => {
  console.error('Error:', err);
  process.exit(1);
});
