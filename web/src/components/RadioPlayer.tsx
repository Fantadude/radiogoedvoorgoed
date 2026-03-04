import { useState, useRef, useEffect } from 'react';
import './RadioPlayer.css';

const RADIO_CONFIG = {
  streamUrl: 'https://ex52.voordeligstreamen.nl/8154/stream',
  nowPlayingUrl: 'https://ex52.voordeligstreamen.nl/8154/status-json.xsl',
};

export default function RadioPlayer() {
  const [isPlaying, setIsPlaying] = useState(false);
  const [volume, setVolume] = useState(0.8);
  const [currentTrack, setCurrentTrack] = useState('radiogoedvoorgoed');
  const [currentArtist, setCurrentArtist] = useState('Live Stream');
  const [listeners, setListeners] = useState(0);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Initialize audio element with settings for continuous playback
  useEffect(() => {
    // Create audio element with settings that allow continuous streaming
    const audio = new Audio();
    audio.src = RADIO_CONFIG.streamUrl;
    audio.volume = volume;

    // CRITICAL: Use 'none' preload and set audio type for streaming
    audio.preload = 'none';

    // Set audio type hint for streaming
    audio.setAttribute('type', 'audio/mpeg');

    // Prevent browser from pausing audio when it thinks it's "done"
    audio.loop = false; // Stream handles its own looping
    audio.autoplay = false;

    // @ts-ignore - AudioSession API is not yet fully standardized in TypeScript
    if ('audioSession' in audio) {
      // @ts-ignore
      audio.audioSession = { type: 'playback' };
    }

    audioRef.current = audio;

    return () => {
      if (audioRef.current) {
        audioRef.current.pause();
        audioRef.current.src = '';
        audioRef.current.load();
        audioRef.current = null;
      }
    };
  }, []);

  // Update volume when changed
  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.volume = volume;
    }
  }, [volume]);

  // Fetch current track info periodically
  useEffect(() => {
    const fetchTrackInfo = async () => {
      try {
        const response = await fetch(RADIO_CONFIG.nowPlayingUrl);
        if (response.ok) {
          const data = await response.json();
          // Icecast JSON format
          if (data.icestats && data.icestats.source) {
            const source = Array.isArray(data.icestats.source)
              ? data.icestats.source[0]
              : data.icestats.source;

            if (source.title) {
              const titleParts = source.title.split(' - ');
              if (titleParts.length >= 2) {
                setCurrentArtist(titleParts[0]);
                setCurrentTrack(titleParts.slice(1).join(' - '));
              } else {
                setCurrentTrack(source.title);
                setCurrentArtist('Unknown Artist');
              }
            }
            if (source.listeners) {
              setListeners(source.listeners);
            }
          }
        }
      } catch (err) {
        console.error('Failed to fetch track info:', err);
      }
    };

    fetchTrackInfo();
    const interval = setInterval(fetchTrackInfo, 15000); // Update every 15 seconds
    return () => clearInterval(interval);
  }, []);

  // Media Session API - for lock screen/notification controls
  useEffect(() => {
    if ('mediaSession' in navigator) {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: currentTrack,
        artist: currentArtist,
        album: 'radiogoedvoorgoed',
        artwork: [
          { src: '/radio-logo.svg', sizes: '512x512', type: 'image/svg+xml' },
        ],
      });

      // Handle media session actions (headset button, Bluetooth controls)
      navigator.mediaSession.setActionHandler('play', () => {
        if (audioRef.current && !isPlaying) {
          audioRef.current.play()
            .then(() => setIsPlaying(true))
            .catch(console.error);
        }
      });

      navigator.mediaSession.setActionHandler('pause', () => {
        if (audioRef.current && isPlaying) {
          audioRef.current.pause();
          setIsPlaying(false);
        }
      });

      navigator.mediaSession.setActionHandler('stop', () => {
        if (audioRef.current) {
          audioRef.current.pause();
          audioRef.current.src = RADIO_CONFIG.streamUrl;
          audioRef.current.load();
          setIsPlaying(false);
        }
      });

      // Update playback state
      navigator.mediaSession.playbackState = isPlaying ? 'playing' : 'paused';
    }

    return () => {
      // Clean up media session handlers when component unmounts
      if ('mediaSession' in navigator) {
        navigator.mediaSession.setActionHandler('play', null);
        navigator.mediaSession.setActionHandler('pause', null);
        navigator.mediaSession.setActionHandler('stop', null);
      }
    };
  }, [isPlaying, currentTrack, currentArtist]);

  // Handle audio interruptions and errors
  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;

    const handlePause = () => {
      // Check if audio actually stopped (ended) or just paused
      if (audio.ended || audio.error) {
        setIsPlaying(false);
      }
    };

    const handlePlay = () => {
      setIsPlaying(true);
      setError(null);
    };

    const handleWaiting = () => {
      setIsLoading(true);
    };

    const handleCanPlay = () => {
      setIsLoading(false);
    };

    const handleError = (e: Event) => {
      console.error('Audio error:', e);
      setError('Stream connection lost. Try playing again.');
      setIsPlaying(false);
      setIsLoading(false);
    };

    const handleStalled = () => {
      console.warn('Audio stalled - waiting for data');
      setIsLoading(true);
    };

    // Listen for system audio events (important for Bluetooth and interruptions)
    audio.addEventListener('pause', handlePause);
    audio.addEventListener('play', handlePlay);
    audio.addEventListener('waiting', handleWaiting);
    audio.addEventListener('canplay', handleCanPlay);
    audio.addEventListener('error', handleError);
    audio.addEventListener('stalled', handleStalled);

    return () => {
      audio.removeEventListener('pause', handlePause);
      audio.removeEventListener('play', handlePlay);
      audio.removeEventListener('waiting', handleWaiting);
      audio.removeEventListener('canplay', handleCanPlay);
      audio.removeEventListener('error', handleError);
      audio.removeEventListener('stalled', handleStalled);
    };
  }, []);

  // Page Visibility API - keep playing when tab is hidden
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.hidden) {
        console.log('Radio continuing in background...');
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, []);

  const togglePlay = () => {
    if (!audioRef.current) return;

    if (isPlaying) {
      audioRef.current.pause();
      setIsPlaying(false);
    } else {
      setIsLoading(true);
      setError(null);

      // Reset src and reload to ensure fresh connection
      audioRef.current.src = RADIO_CONFIG.streamUrl;
      audioRef.current.load();

      // Use a promise to handle play with proper error catching
      const playPromise = audioRef.current.play();

      if (playPromise !== undefined) {
        playPromise
          .then(() => {
            setIsPlaying(true);
            setIsLoading(false);
          })
          .catch((err) => {
            console.error('Failed to play:', err);
            setError('Failed to connect to radio stream. Please check your connection.');
            setIsLoading(false);
          });
      }
    }
  };

  return (
    <div className="radio-player">
      <div className="player-container">
        <div className="now-playing">
          <div className="album-art">
            <div className={`album-art-placeholder ${isPlaying ? 'playing' : ''}`}>
              {isPlaying ? (
                <div className="visualizer">
                  <span></span>
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
              ) : (
                <div className="album-art-static">
                  <div className="visualizer-static">
                    <span></span>
                    <span></span>
                    <span></span>
                    <span></span>
                  </div>
                </div>
              )}
            </div>
          </div>

          <div className="track-info">
            <h3 className="track-name">{currentTrack}</h3>
            <p className="artist-name">{currentArtist}</p>
            <p className="radio-status">
              {isPlaying ? (
                <><span className="live-indicator"></span> Live • {listeners} listeners</>
              ) : (
                '○ Paused'
              )}
            </p>
          </div>
        </div>

        {error && <div className="player-error">{error}</div>}

        <div className="player-controls">
          <button
            className={`play-btn ${isPlaying ? 'playing' : ''}`}
            onClick={togglePlay}
            disabled={isLoading}
          >
            {isLoading ? (
              <span className="spinner"></span>
            ) : isPlaying ? (
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
            />
            <span className="volume-value">{Math.round(volume * 100)}%</span>
          </div>
        </div>

        <div className="station-info">
          <p>radiogoedvoorgoed - 24/7 Non-stop muziek</p>
          <p className="stream-quality">Stream: 128kbps MP3</p>
        </div>
      </div>
    </div>
  );
}
