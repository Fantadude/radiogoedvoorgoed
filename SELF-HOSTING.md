# Self-Hosting Guide for GoedvoorGoed Radio

This guide explains how to run this app independently of Lovable.

## Overview

The app has two parts:
1. **Frontend** - React web app (can be hosted anywhere)
2. **Backend** - Two API endpoints (edge functions)

---

## Step 1: Export Your Code

1. In Lovable, click **GitHub → Connect to GitHub**
2. Create a repository and push your code
3. Clone the repository to your computer:
   ```bash
   git clone https://github.com/YOUR_USERNAME/YOUR_REPO.git
   cd YOUR_REPO
   ```

---

## Step 2: Set Up the Backend

The app uses two edge functions that need to be hosted somewhere. Here are your options:

### Option A: Your Own Supabase Project (Recommended)

1. Create a free account at [supabase.com](https://supabase.com)
2. Create a new project
3. Install Supabase CLI:
   ```bash
   npm install -g supabase
   ```
4. Login and link your project:
   ```bash
   supabase login
   supabase link --project-ref YOUR_PROJECT_REF
   ```
5. Deploy the edge functions:
   ```bash
   supabase functions deploy radiodj
   supabase functions deploy podcasts
   ```
6. Note your new function URLs:
   - `https://YOUR_PROJECT_REF.supabase.co/functions/v1/radiodj`
   - `https://YOUR_PROJECT_REF.supabase.co/functions/v1/podcasts`

### Option B: Cloudflare Workers

Convert the Deno edge functions to Cloudflare Workers format. The logic stays the same, just the wrapper changes.

### Option C: Vercel/Netlify Functions

Similar to Cloudflare, you'd need to adapt the function format.

---

## Step 3: Update Frontend API URLs

After setting up your backend, update the API URLs in the frontend code.

### File: `src/components/SongBrowser.tsx`
Find and replace:
```typescript
// OLD
const response = await fetch(
  `https://xjtflbkyqncrkyqqypur.supabase.co/functions/v1/radiodj?action=songs...`
);

// NEW - Replace with your backend URL
const response = await fetch(
  `https://YOUR_PROJECT_REF.supabase.co/functions/v1/radiodj?action=songs...`
);
```

### File: `src/components/PodcastBrowser.tsx`
Find and replace:
```typescript
// OLD
const response = await fetch(
  "https://xjtflbkyqncrkyqqypur.supabase.co/functions/v1/podcasts"
);

// NEW
const response = await fetch(
  "https://YOUR_PROJECT_REF.supabase.co/functions/v1/podcasts"
);
```

### File: `src/contexts/AudioContext.tsx` (Now Playing)
Find and replace the now playing API URL if applicable.

**Tip:** You can use environment variables instead of hardcoding:
```typescript
const API_BASE = import.meta.env.VITE_API_BASE_URL || 'https://YOUR_PROJECT_REF.supabase.co/functions/v1';
```

---

## Step 4: Build the Frontend

```bash
npm install
npm run build
```

This creates a `dist` folder with your static files.

---

## Step 5: Host the Frontend

### Option A: Netlify (Free)
1. Go to [netlify.com](https://netlify.com)
2. Drag and drop your `dist` folder
3. Done! You get a free URL like `your-app.netlify.app`

### Option B: Vercel (Free)
1. Go to [vercel.com](https://vercel.com)
2. Import your GitHub repository
3. It auto-detects Vite and deploys

### Option C: GitHub Pages (Free)
1. Push the `dist` folder to a `gh-pages` branch
2. Enable GitHub Pages in repository settings

### Option D: Any Static Host
Upload the contents of `dist` to any web server (Apache, Nginx, S3, etc.)

---

## Step 6: Build Android APK

After updating the API URLs and testing the web version:

```bash
# Build the web app
npm run build

# Sync with Capacitor
npx cap sync android

# Inject Android manifest permissions
node capacitor-hooks/android-manifest-inject.js

# Build/run the Android app
npx cap run android
```

For a release APK, open Android Studio:
```bash
npx cap open android
```
Then: Build → Generate Signed Bundle / APK

---

## Environment Variables Reference

| Variable | Description | Example |
|----------|-------------|---------|
| `VITE_API_BASE_URL` | Your backend API base URL | `https://xyz.supabase.co/functions/v1` |

---

## Backend Dependencies

### radiodj function
- Connects to MySQL database at `86.84.18.58:3306`
- Database: `radiodj2006`
- Used for: Song list, song requests, now playing

### podcasts function  
- Fetches RSS feed from `https://radiogoedvoorgoed.nl/feed/podcast/`
- Used for: Podcast episode list

---

## Troubleshooting

### "Failed to fetch songs/podcasts"
- Check that your backend functions are deployed and accessible
- Verify the URLs in your frontend code are correct
- Check browser console for CORS errors

### Android app doesn't connect
- Make sure your backend URLs use HTTPS
- Check that `cleartext` is enabled in capacitor.config.ts for development

---

## Need Help?

The edge function code is in:
- `supabase/functions/radiodj/index.ts`
- `supabase/functions/podcasts/index.ts`

These are standard Deno functions that can be adapted to other platforms.
