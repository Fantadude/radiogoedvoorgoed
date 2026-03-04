// Simple Express server for Radio Goed Voor Goed
// Handles song requests and podcast episodes

const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(express.json());

// Data storage paths
const DATA_DIR = path.join(__dirname, 'data');
const REQUESTS_FILE = path.join(DATA_DIR, 'requests.json');
const PODCASTS_FILE = path.join(DATA_DIR, 'podcasts.json');

// Ensure data directory exists
if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

// Initialize data files if they don't exist
if (!fs.existsSync(REQUESTS_FILE)) {
  fs.writeFileSync(REQUESTS_FILE, JSON.stringify([]));
}

if (!fs.existsSync(PODCASTS_FILE)) {
  fs.writeFileSync(PODCASTS_FILE, JSON.stringify([
    {
      id: '1',
      title: 'Welcome to Radio Goed Voor Goed',
      description: 'Introduction episode about our radio station and community.',
      audioUrl: '',
      duration: '10:00',
      publishDate: new Date().toISOString(),
    }
  ]));
}

// Helper functions
const readData = (file) => {
  try {
    const data = fs.readFileSync(file, 'utf8');
    return JSON.parse(data);
  } catch (err) {
    return [];
  }
};

const writeData = (file, data) => {
  fs.writeFileSync(file, JSON.stringify(data, null, 2));
};

// Routes

// Health check
app.get('/api/health', (req, res) => {
  res.json({ status: 'OK', timestamp: new Date().toISOString() });
});

// Get all song requests
app.get('/api/requests', (req, res) => {
  const requests = readData(REQUESTS_FILE);
  res.json(requests);
});

// Add new song request
app.post('/api/requests', (req, res) => {
  const { title, artist, requestedBy, timestamp } = req.body;
  
  if (!title || !artist) {
    return res.status(400).json({ error: 'Title and artist are required' });
  }

  const requests = readData(REQUESTS_FILE);
  
  const newRequest = {
    id: Date.now().toString(),
    title,
    artist,
    requestedBy: requestedBy || 'Anonymous',
    timestamp: timestamp || new Date().toISOString(),
    status: 'pending'
  };

  requests.unshift(newRequest);
  writeData(REQUESTS_FILE, requests);
  
  res.status(201).json(newRequest);
});

// Update request status (for admin use)
app.patch('/api/requests/:id', (req, res) => {
  const { id } = req.params;
  const { status } = req.body;
  
  const requests = readData(REQUESTS_FILE);
  const request = requests.find(r => r.id === id);
  
  if (!request) {
    return res.status(404).json({ error: 'Request not found' });
  }
  
  request.status = status || request.status;
  writeData(REQUESTS_FILE, requests);
  
  res.json(request);
});

// Delete a request
app.delete('/api/requests/:id', (req, res) => {
  const { id } = req.params;
  
  let requests = readData(REQUESTS_FILE);
  requests = requests.filter(r => r.id !== id);
  writeData(REQUESTS_FILE, requests);
  
  res.status(204).send();
});

// Get all podcast episodes
app.get('/api/podcasts', (req, res) => {
  const podcasts = readData(PODCASTS_FILE);
  res.json(podcasts);
});

// Add new podcast episode
app.post('/api/podcasts', (req, res) => {
  const { title, description, audioUrl, duration, coverImage } = req.body;
  
  if (!title || !audioUrl) {
    return res.status(400).json({ error: 'Title and audio URL are required' });
  }

  const podcasts = readData(PODCASTS_FILE);
  
  const newEpisode = {
    id: Date.now().toString(),
    title,
    description: description || '',
    audioUrl,
    duration: duration || '0:00',
    publishDate: new Date().toISOString(),
    coverImage: coverImage || ''
  };

  podcasts.unshift(newEpisode);
  writeData(PODCASTS_FILE, podcasts);
  
  res.status(201).json(newEpisode);
});

// Delete podcast episode
app.delete('/api/podcasts/:id', (req, res) => {
  const { id } = req.params;
  
  let podcasts = readData(PODCASTS_FILE);
  podcasts = podcasts.filter(p => p.id !== id);
  writeData(PODCASTS_FILE, podcasts);
  
  res.status(204).send();
});

// Serve static files (podcast audio files)
app.use('/audio', express.static(path.join(__dirname, 'audio')));

// Start server
app.listen(PORT, () => {
  console.log(`Radio server running on http://localhost:${PORT}`);
  console.log(`Data directory: ${DATA_DIR}`);
});
