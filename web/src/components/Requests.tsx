import { useState, useEffect } from 'react';
import './Requests.css';

const API_BASE_URL = 'http://86.84.18.58:3000'; // Your radio server API

interface Song {
  ID: number;
  artist: string;
  title: string;
  album: string;
  duration: string;
}

interface SongRequest {
  id: string;
  songID: number;
  title: string;
  artist: string;
  username: string;
  message: string;
  requested: string;
  played: number;
}

const ALPHABET = ['#', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'];

export default function Requests() {
  const [songs, setSongs] = useState<Song[]>([]);
  const [requests, setRequests] = useState<SongRequest[]>([]);
  const [selectedLetter, setSelectedLetter] = useState('A');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedSong, setSelectedSong] = useState<Song | null>(null);
  const [username, setUsername] = useState('');
  const [message, setMessage] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'browse' | 'search'>('browse');

  // Load songs by letter
  useEffect(() => {
    if (activeTab === 'browse') {
      fetchSongsByLetter(selectedLetter);
    }
  }, [selectedLetter, activeTab]);

  // Search songs
  useEffect(() => {
    const timeoutId = setTimeout(() => {
      if (activeTab === 'search' && searchQuery.trim()) {
        searchSongs(searchQuery);
      }
    }, 300);
    return () => clearTimeout(timeoutId);
  }, [searchQuery, activeTab]);

  const fetchSongsByLetter = async (letter: string) => {
    setIsLoading(true);
    try {
      const response = await fetch(`${API_BASE_URL}/api/songs/letter/${encodeURIComponent(letter)}`);
      if (response.ok) {
        const data = await response.json();
        setSongs(data.songs || []);
        setError(null);
      } else {
        throw new Error('Failed to fetch songs');
      }
    } catch (err) {
      setError('Failed to load songs from server');
      setSongs([]);
    } finally {
      setIsLoading(false);
    }
  };

  const searchSongs = async (query: string) => {
    setIsLoading(true);
    try {
      const response = await fetch(`${API_BASE_URL}/api/songs/search?q=${encodeURIComponent(query)}`);
      if (response.ok) {
        const data = await response.json();
        setSongs(data.songs || []);
        setError(null);
      } else {
        throw new Error('Search failed');
      }
    } catch (err) {
      setError('Search failed');
      setSongs([]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSubmitRequest = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedSong) {
      setError('Please select a song first');
      return;
    }

    setIsSubmitting(true);
    setError(null);
    setSuccess(null);

    try {
      const response = await fetch(`${API_BASE_URL}/api/requests`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          songID: selectedSong.ID,
          username: username || 'Anonymous',
          message: message || ''
        }),
      });

      if (response.ok) {
        const data = await response.json();
        setSuccess(data.message || 'Request submitted successfully!');
        setSelectedSong(null);
        setMessage('');
        // Add to local requests list
        const newRequest: SongRequest = {
          id: data.requestId?.toString() || Date.now().toString(),
          songID: selectedSong.ID,
          title: selectedSong.title,
          artist: selectedSong.artist,
          username: username || 'Anonymous',
          message: message || '',
          requested: new Date().toISOString(),
          played: 0
        };
        setRequests(prev => [newRequest, ...prev]);
      } else {
        throw new Error('Failed to submit request');
      }
    } catch (err) {
      setError('Failed to submit request. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const formatDuration = (duration: string) => {
    if (!duration) return '0:00';
    // Duration is likely in seconds from MySQL
    const seconds = parseInt(duration, 10);
    if (isNaN(seconds)) return duration;
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  return (
    <div className="requests">
      <div className="requests-container">
        {/* Song Selection */}
        <div className="songs-section">
          <h2>Select a Song</h2>
          
          {/* Tab Navigation */}
          <div className="tab-nav">
            <button 
              className={activeTab === 'browse' ? 'active' : ''}
              onClick={() => setActiveTab('browse')}
            >
              Browse by Letter
            </button>
            <button 
              className={activeTab === 'search' ? 'active' : ''}
              onClick={() => setActiveTab('search')}
            >
              Search
            </button>
          </div>

          {/* Alphabet Filter (Browse mode) */}
          {activeTab === 'browse' && (
            <div className="alphabet-filter">
              {ALPHABET.map(letter => (
                <button
                  key={letter}
                  className={selectedLetter === letter ? 'active' : ''}
                  onClick={() => setSelectedLetter(letter)}
                >
                  {letter}
                </button>
              ))}
            </div>
          )}

          {/* Search Input (Search mode) */}
          {activeTab === 'search' && (
            <div className="search-box">
              <input
                type="text"
                placeholder="Search by artist or song title..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
          )}

          {/* Songs List */}
          <div className="songs-list">
            {isLoading ? (
              <div className="loading-state">Loading songs...</div>
            ) : songs.length === 0 ? (
              <div className="empty-state">
                {activeTab === 'search' 
                  ? 'No songs found. Try a different search.' 
                  : 'No songs found for this letter.'}
              </div>
            ) : (
              songs.map((song) => (
                <div 
                  key={song.ID} 
                  className={`song-item ${selectedSong?.ID === song.ID ? 'selected' : ''}`}
                  onClick={() => setSelectedSong(song)}
                >
                  <div className="song-info">
                    <span className="song-title">{song.title}</span>
                    <span className="song-artist">{song.artist}</span>
                    {song.album && <span className="song-album">{song.album}</span>}
                  </div>
                  <span className="song-duration">{formatDuration(song.duration)}</span>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Request Form */}
        <div className="request-form-section">
          <h2>Request Details</h2>
          
          {error && <div className="error-message">{error}</div>}
          {success && <div className="success-message">{success}</div>}
          
          {selectedSong ? (
            <form onSubmit={handleSubmitRequest}>
              <div className="selected-song-info">
                <h3>Selected Song:</h3>
                <p className="selected-title">{selectedSong.title}</p>
                <p className="selected-artist">{selectedSong.artist}</p>
                <button 
                  type="button" 
                  className="clear-btn"
                  onClick={() => setSelectedSong(null)}
                >
                  Change Song
                </button>
              </div>

              <div className="form-group">
                <label htmlFor="username">Your Name</label>
                <input
                  type="text"
                  id="username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="Enter your name (optional)"
                />
              </div>

              <div className="form-group">
                <label htmlFor="message">Message (optional)</label>
                <textarea
                  id="message"
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                  placeholder="Add a message for the DJ..."
                  rows={3}
                />
              </div>

              <button 
                type="submit" 
                className="submit-btn"
                disabled={isSubmitting}
              >
                {isSubmitting ? 'Submitting...' : 'Submit Request'}
              </button>
            </form>
          ) : (
            <div className="no-selection">
              <p>Select a song from the list to make a request</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
