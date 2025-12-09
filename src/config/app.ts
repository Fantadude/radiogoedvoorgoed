// App Configuration - Edit these values as needed

export const APP_CONFIG = {
  // Radio stream URL
  streamUrl: "https://ex52.voordeligstreamen.nl/8154/stream",
  
  // Database configuration for song requests
  // Note: Direct MySQL connections from browser are not possible
  // This config is used by the backend edge function
  database: {
    host: "x.x.x.x", // Change this to your MySQL server IP
    port: 3306,
    username: "request",
    password: "user",
    database: "radiodj2006",
  },
  
  // API endpoints
  api: {
    // This will be the Supabase edge function URL once Cloud is enabled
    requestsEndpoint: "/api/requests",
    podcastFeed: "https://radiogoedvoorgoed.nl/podcast/feed.xml",
  },
  
  // App info
  appName: "GoedvoorGoed",
  appDescription: "Radio GoedvoorGoed - Muziek die goed doet",
};
