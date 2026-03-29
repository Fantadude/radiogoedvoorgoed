import { useState, useEffect, useRef } from 'react';
import './Requests.css';

const API_BASE_URL = 'http://radiogvg.chickenkiller.com:3000'; // Your radio server API

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
  const [, setRequests] = useState<SongRequest[]>([]);
  const [selectedLetter, setSelectedLetter] = useState('A');
  const [searchQuery, setSearchQuery] = useState('');
  const [isSearchMode, setIsSearchMode] = useState(false);
  const [selectedSong, setSelectedSong] = useState<Song | null>(null);
  const [username, setUsername] = useState('');
  const [message, setMessage] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const alphabetScrollRef = useRef<HTMLDivElement>(null);

  // Load songs by letter
  useEffect(() => {
    if (!isSearchMode) {
      fetchSongsByLetter(selectedLetter);
    }
  }, [selectedLetter, isSearchMode]);

  // Search songs when query changes (with debounce)
  useEffect(() => {
    const timeoutId = setTimeout(() => {
      if (isSearchMode && searchQuery.trim().length >= 2) {
        searchSongs(searchQuery);
      } else if (isSearchMode && searchQuery.trim().length === 0) {
        setIsSearchMode(false);
        fetchSongsByLetter(selectedLetter);
      }
    }, 300);
    return () => clearTimeout(timeoutId);
  }, [searchQuery, isSearchMode, selectedLetter]);

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

  const handleSearchChange = (value: string) => {
    setSearchQuery(value);
    setIsSearchMode(value.length > 0);
  };

  const handleLetterSelect = (letter: string) => {
    setSelectedLetter(letter);
    setIsSearchMode(false);
    setSearchQuery('');
    
    // Scroll selected letter into view
    if (alphabetScrollRef.current) {
      const button = alphabetScrollRef.current.querySelector(`[data-letter="${letter}"]`) as HTMLElement;
      if (button) {
        button.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' });
      }
    }
  };

  const handleSongSelect = (song: Song) => {
    setSelectedSong(song);
    setError(null);
    setSuccess(null);
  };

  const handleBackToList = () => {
    setSelectedSong(null);
    setError(null);
    setSuccess(null);
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
    const seconds = parseInt(duration, 10);
    if (isNaN(seconds)) return duration;
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  // Android-style: Full screen request form when song selected
  if (selectedSong) {
    return (
      <div className="requests">
        <div className="request-form-fullscreen">
          {/* Selected Song Card */}
          <div className="selected-song-card">
            <span className="selected-label">Selected Song</span>
            <h3 className="selected-song-title">{selectedSong.title}</h3>
            <p className="selected-song-artist">{selectedSong.artist}</p>
            <button 
              type="button" 
              className="change-song-btn"
              onClick={handleBackToList}
            >
              Change Song
            </button>
          </div>

          {/* Request Form */}
          <div className="request-details-card">
            <h2>Request Details</h2>
            
            {error && <div className="error-message">{error}</div>}
            {success && <div className="success-message">{success}</div>}
            
            <form onSubmit={handleSubmitRequest}>
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
                {isSubmitting ? (
                  <>
                    <span className="spinner-small"></span>
                    Submitting...
                  </>
                ) : (
                  <>
                    <svg viewBox="0 0 24 24" fill="currentColor" className="send-icon">
                      <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
                    </svg>
                    Submit Request
                  </>
                )}
              </button>
            </form>
          </div>
        </div>
      </div>
    );
  }

  // Song list view
  return (
    <div className="requests">
      <div className="songs-section-fullwidth">
        <h2>Select a Song</h2>
        
        {/* Dedicated Search Bar - Always visible */}
        <div className="search-bar-container">
          <div className="search-input-wrapper">
            <svg viewBox="0 0 24 24" fill="currentColor" className="search-icon">
              <path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
            </svg>
            <input
              type="text"
              placeholder="Search songs or artists..."
              value={searchQuery}
              onChange={(e) => handleSearchChange(e.target.value)}
              className="search-input"
            />
            {searchQuery && (
              <button 
                className="clear-search-btn"
                onClick={() => handleSearchChange('')}
                aria-label="Clear search"
              >
                ×
              </button>
            )}
          </div>
        </div>

        {/* Horizontal Scrollable Alphabet - Only show when not searching */}
        {!isSearchMode && (
          <div className="alphabet-scroll-container" ref={alphabetScrollRef}>
            {ALPHABET.map(letter => (
              <button
                key={letter}
                data-letter={letter}
                className={`alphabet-pill ${selectedLetter === letter ? 'active' : ''}`}
                onClick={() => handleLetterSelect(letter)}
              >
                {letter}
              </button>
            ))}
          </div>
        )}

        {/* Search mode indicator */}
        {isSearchMode && (
          <div className="search-mode-indicator">
            <span>Search results for "{searchQuery}"</span>
            <button onClick={() => handleSearchChange('')}>Clear</button>
          </div>
        )}

        {/* Songs List */}
        <div className="songs-list-fullwidth">
          {isLoading ? (
            <div className="loading-state">
              <span className="spinner"></span>
              <p>Loading songs...</p>
            </div>
          ) : songs.length === 0 ? (
            <div className="empty-state">
              {isSearchMode 
                ? 'No songs found. Try a different search.' 
                : `No songs found for "${selectedLetter}".`}
            </div>
          ) : (
            songs.map((song) => (
              <div 
                key={song.ID} 
                className="song-card"
                onClick={() => handleSongSelect(song)}
              >
                <div className="song-card-info">
                  <span className="song-card-title">{song.title}</span>
                  <span className="song-card-artist">{song.artist}</span>
                  {song.album && <span className="song-card-album">{song.album}</span>}
                </div>
                <span className="song-card-duration">{formatDuration(song.duration)}</span>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
