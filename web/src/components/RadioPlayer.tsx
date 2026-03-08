import { useState, useEffect, useCallback } from 'react';
import { useAudio } from '../context/AudioContext';
import './RadioPlayer.css';

const RADIO_CONFIG = {
  streamUrl: 'https://ex52.voordeligstreamen.nl/8154/stream',
  nowPlayingUrl: 'https://ex52.voordeligstreamen.nl/cp/get_info.php?p=8154',
};

// Detect iOS
const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) || 
              (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);

export default function RadioPlayer() {
  const {
    isPlaying,
    mode,
    volume,
    radioMetadata,
    playRadio,
    pauseRadio,
    setVolume,
    setRadioMetadata,
  } = useAudio();

  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [history, setHistory] = useState<string[]>([]);
  const [connectionAttempt, setConnectionAttempt] = useState(0);

  const isRadioPlaying = isPlaying && mode === 'radio';

  // Fetch current track info periodically - optimized for battery
  useEffect(() => {
    let isMounted = true;
    
    const fetchTrackInfo = async () => {
      try {
        // Use AbortController for timeout on iOS
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), isIOS ? 5000 : 10000);
        
        const response = await fetch(RADIO_CONFIG.nowPlayingUrl, {
          signal: controller.signal,
          cache: 'no-cache',
        });
        
        clearTimeout(timeoutId);
        
        if (!isMounted) return;
        
        if (response.ok) {
          const data = await response.json();
          // Centova JSON format
          if (data.title) {
            setRadioMetadata({
              title: data.title,
              artist: data.artist || '',
              artUrl: data.art || null,
              listeners: data.listeners || 0,
            });

            if (data.history && Array.isArray(data.history)) {
              // History comes as [1.) oldest, ..., 20.) current]
              // Reverse to [20.) current, 19., 18., ...], skip current (index 0), take 19, 18, 17
              const filteredHistory = data.history
                .slice()           // Create a copy
                .reverse()          // Reverse: [20.) current, 19., 18., 17., ...]
                .slice(1)           // Skip current song at index 0
                .filter((item: string) => item && !item.includes('RadioGoedvoorGoed'))
                .slice(0, 3);       // Take max 3

              setHistory(filteredHistory);
            }
          }
          if (data.listeners !== undefined) {
            setRadioMetadata({ listeners: data.listeners });
          }
        }
      } catch (err) {
        // Silently fail - don't show error for metadata fetch failures
        if (isMounted && err instanceof Error && err.name !== 'AbortError') {
          console.error('Failed to fetch track info:', err);
        }
      }
    };

    fetchTrackInfo();
    // Longer interval when not playing to save battery
    const interval = setInterval(fetchTrackInfo, isRadioPlaying ? 15000 : 30000);
    
    return () => {
      isMounted = false;
      clearInterval(interval);
    };
  }, [isRadioPlaying, setRadioMetadata]);

  // Sync volume changes
  useEffect(() => {
    // Volume is managed by the context
  }, [volume]);

  const togglePlay = useCallback(() => {
    if (isRadioPlaying) {
      setIsLoading(false);
      setError(null);
      pauseRadio();
    } else {
      setIsLoading(true);
      setError(null);
      setConnectionAttempt(prev => prev + 1);

      // iOS-specific: shorter timeout for faster feedback
      const checkPlaying = setTimeout(() => {
        setIsLoading(false);
      }, isIOS ? 1500 : 2000);

      playRadio();

      return () => clearTimeout(checkPlaying);
    }
  }, [isRadioPlaying, pauseRadio, playRadio]);

  // Listen for radio errors (check periodically)
  useEffect(() => {
    if (!isRadioPlaying) {
      setIsLoading(false);
      return;
    }

    // iOS: faster feedback
    const timeout = setTimeout(() => {
      setIsLoading(false);
    }, isIOS ? 800 : 1000);

    return () => clearTimeout(timeout);
  }, [isRadioPlaying, connectionAttempt]);

  return (
    <div className="radio-player">
      <div className="player-container">
        <div className="now-playing">
          <div className="album-art">
            <div className={`album-art-placeholder ${isRadioPlaying ? 'playing' : ''}`}>
              {radioMetadata.artUrl ? (
                <img
                  src={radioMetadata.artUrl}
                  alt="Album Art"
                  className="album-art-image"
                  loading="lazy"
                />
              ) : (
                <img
                  src="/radio_logo.png"
                  alt="Radio GoedvoorGoed Logo"
                  className="album-art-logo"
                />
              )}
            </div>
          </div>

          <div className="track-info">
            <h3 className="track-name">{radioMetadata.title}</h3>
            <p className="artist-name">{radioMetadata.artist}</p>
            <p className="radio-status">
              {isLoading ? (
                '● Connecting...'
              ) : isRadioPlaying ? (
                <><span className="live-indicator"></span> Live • {radioMetadata.listeners} listeners</>
              ) : (
                '○ Paused'
              )}
            </p>
          </div>
        </div>

        {error && <div className="player-error">{error}</div>}

        {/* Recently Played Section */}
        {history.length > 0 && (
          <div className="recently-played">
            <h4 className="recently-played-title">Recently Played</h4>
            <div className="recently-played-list">
              {history.map((item, index) => {
                // Parse history item (format: "1.) Artist - Title")
                const cleanedItem = item.replace(/^\d+\.\)\s*/, '');
                const parts = cleanedItem.split(' - ');
                const artist = parts.length >= 2 ? parts[0] : '';
                const title = parts.length >= 2 ? parts.slice(1).join(' - ') : cleanedItem;

                return (
                  <div key={index} className="recently-played-item">
                    <span className="recently-played-number">{index + 1}</span>
                    <div className="recently-played-info">
                      <span className="recently-played-title-text">{title}</span>
                      {artist && <span className="recently-played-artist">{artist}</span>}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        <div className="player-controls">
          <button
            className={`play-btn ${isRadioPlaying ? 'playing' : ''}`}
            onClick={togglePlay}
            disabled={isLoading}
            aria-label={isRadioPlaying ? 'Pause' : 'Play'}
          >
            {isLoading ? (
              <span className="spinner"></span>
            ) : isRadioPlaying ? (
              <svg viewBox="0 0 24 24" fill="currentColor">
                <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>
              </svg>
            ) : (
              <svg viewBox="0 0 24 24" fill="currentColor">
                <path d="M8 5v14l11-7z"/>
              </svg>
            )}
          </button>

          <div className="volume-control">
            <svg viewBox="0 0 24 24" fill="currentColor" className="volume-icon">
              <path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z"/>
            </svg>
            <input
              type="range"
              min="0"
              max="1"
              step="0.01"
              value={volume}
              onChange={(e) => setVolume(parseFloat(e.target.value))}
              className="volume-slider"
              aria-label="Volume"
            />
            <span className="volume-value">{Math.round(volume * 100)}%</span>
          </div>
        </div>

        <div className="station-info">
          <p className="stream-quality">Stream: 128kbps MP3 • {radioMetadata.listeners} listeners</p>
        </div>
      </div>
    </div>
  );
}
