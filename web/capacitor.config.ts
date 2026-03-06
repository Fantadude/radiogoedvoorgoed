import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'nl.radiogoedvoorgoed.app',
  appName: 'Radio GoedvoorGoed',
  webDir: 'dist',
  server: {
    androidScheme: 'https',
    iosScheme: 'https',
    cleartext: true,
  },
  ios: {
    contentInset: 'always',
    allowsLinkPreview: false,
    scrollEnabled: true,
    // Disable background audio restrictions
    backgroundColor: '#87CEEB',
  },
  plugins: {
    // Enable background audio
    CapacitorHttp: {
      enabled: true,
    },
  },
  // Ensure the audio keeps playing in background
  cordova: {
    preferences: {
      KeepRunning: 'true',
      BackgroundAudio: 'true',
    },
  },
};

export default config;
