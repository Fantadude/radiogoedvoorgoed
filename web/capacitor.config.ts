import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'nl.radiogoedvoorgoed.app',
  appName: 'Radio GoedvoorGoed',
  webDir: 'dist',
  server: {
    androidScheme: 'http',
    iosScheme: 'http',
    cleartext: true,
    allowNavigation: [
      'ex52.voordeligstreamen.nl',
      'radiogvg.chickenkiller.com',
    ],
  },
  ios: {
    contentInset: 'always',
    allowsLinkPreview: false,
    scrollEnabled: true,
    backgroundColor: '#1a1a2e', // Match dark theme
    // Disable webview bounce for app-like feel
    limitsNavigationsToAppBoundaries: false,
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
      // iOS specific
      StatusBarOverlaysWebView: 'true',
      StatusBarStyle: 'lightcontent',
    },
  },
  // Background mode configuration
  backgroundMode: {
    enable: true,
    silent: true,
  },
};

export default config;
