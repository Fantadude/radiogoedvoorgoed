# Radio Goed Voor Goed

A clean, independent rebuild of the Radio Goed Voor Goed web application without Lovable.dev dependencies. Built with **React + TypeScript + Vite** frontend that connects to your existing Node.js/MySQL backend.

## Features

- **Radio Player** - Stream live radio with login authentication
- **Song Requests** - Browse/search the RadioDJ song library and submit requests to MySQL
- **Podcasts** - Listen to "Kringloop Verhalen" podcast episodes fetched via RSS

## Architecture

This frontend connects to your existing backend server (`server.js` on your radio machine):

- **Frontend**: React 18, TypeScript, Vite (zero Lovable dependencies)
- **Your Backend**: Node.js, Express, MySQL2 (already running on your radio server)
- **Database**: MySQL (radiodj2006 database with songs and requests tables)
- **Podcasts**: RSS feed from `radiogoedvoorgoed.nl`

## Project Structure

```
web/
├── src/
│   ├── components/
│   │   ├── RadioPlayer.tsx    # Radio streaming with login
│   │   ├── Requests.tsx       # Song browser/search & request form
│   │   └── Podcasts.tsx       # Podcast RSS parser & player
│   ├── App.tsx                # Main app with tab navigation
│   ├── main.tsx               # Entry point
│   └── index.css              # Global styles & CSS variables
├── package.json
├── vite.config.ts
└── index.html
```

## Quick Start

### 1. Install Dependencies

```bash
cd web
npm install
```

### 2. Configure Server URL

Edit the components and update `API_BASE_URL` to point to your radio server:

In `src/components/RadioPlayer.tsx`:
```typescript
const RADIO_CONFIG = {
  streamUrl: 'http://YOUR_RADIO_IP:PORT/stream',
  serverUrl: 'http://YOUR_RADIO_IP:3000',
  // ...
};
```

In `src/components/Requests.tsx` and `Podcasts.tsx`:
```typescript
const API_BASE_URL = 'http://YOUR_RADIO_IP:3000';
```

### 3. Start Frontend Dev Server

```bash
npm run dev
```

Vite dev server runs on `http://localhost:5173`

### 4. Build for Production

```bash
npm run build
```

Output goes to `dist/` folder - deploy these static files to any web server.

## Your Backend API Endpoints

Your existing `server.js` provides these endpoints:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/test` | GET | Health check |
| `/api/podcasts` | GET | Proxy for RSS feed (Kringloop Verhalen) |
| `/api/songs/letter/:letter` | GET | Get songs by first letter (A-Z, # for non-alpha) |
| `/api/songs/search?q=...` | GET | Search songs by artist/title |
| `/api/requests` | POST | Submit song request to MySQL |

### Request Format

**POST `/api/requests`**:
```json
{
  "songID": 12345,
  "username": "John",
  "message": "Love this song!"
}
```

### Response Format

**Song list** (`/api/songs/letter/A` or `/api/songs/search?q=beatles`):
```json
{
  "songs": [
    {
      "ID": 123,
      "artist": "The Beatles",
      "title": "Hey Jude",
      "album": "Hey Jude",
      "duration": "431"
    }
  ]
}
```

## Database Schema

Your MySQL `radiodj2006` database uses these tables:

- **songs** - Song library (ID, artist, title, album, duration, id_subcat)
- **requests** - Song requests (songID, username, userIP, message, requested, played)

## Customization

### Styling

CSS variables in `src/index.css`:

```css
:root {
  --primary-color: #8B5CF6;
  --secondary-color: #EC4899;
  --background-dark: #1a1a2e;
  --background-card: #16213e;
  --text-primary: #ffffff;
  --text-secondary: #a0a0a0;
  --accent-color: #e94560;
  --success-color: #10B981;
  --border-color: #2d3748;
}
```

### Radio Stream Configuration

Update the stream URL in `RadioPlayer.tsx`:

```typescript
const RADIO_CONFIG = {
  streamUrl: 'http://your-radio-server:8000/stream',  // Your Icecast/Shoutcast URL
  serverUrl: 'http://your-radio-server:3000',          // Your API server
  username: '',
  password: '',
};
```

## Production Deployment

1. Build the frontend: `npm run build`
2. Copy `dist/` folder contents to your web server
3. Ensure your backend `server.js` is running on the radio machine
4. Configure firewall to allow port 3000 for API access

## CORS Configuration

Your backend already has CORS enabled:
```javascript
app.use(cors());  // Allows all origins in development
```

For production, restrict to your domain:
```javascript
app.use(cors({ origin: 'https://yourdomain.com' }));
```

## Differences from Lovable Original

| Feature | Lovable Original | This Rebuild |
|---------|-----------------|--------------|
| Dependencies | Heavy (shadcn, Tailwind, many libs) | Minimal (React + Router) |
| Styling | Tailwind + shadcn components | Custom CSS |
| Backend | Lovable-hosted | Your MySQL server |
| Data | Lovable database | Your RadioDJ MySQL |
| Podcasts | Hardcoded list | Live RSS feed |
| Deployment | Lovable platform | Any hosting |
| Song Requests | Simple form | Full song browser + search |

## Troubleshooting

### Cannot connect to backend
- Verify your backend `server.js` is running: `node server.js`
- Check firewall rules for port 3000
- Update `API_BASE_URL` to match your server's IP

### Radio stream not playing
- Check `streamUrl` in RadioPlayer config
- Ensure stream URL is accessible from browser (CORS headers)
- Test stream URL directly in browser

### Podcast feed not loading
- Backend fetches from `https://radiogoedvoorgoed.nl/feed/podcast/kringloop-verhalen/`
- Verify your server has internet access
- Check backend logs for fetch errors

## License

This is a rebuild of the original Radio Goed Voor Goed application. Original concept by Fantadude.
