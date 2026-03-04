import { useState, useEffect } from 'react';
import './Podcasts.css';

const API_BASE_URL = 'http://86.84.18.58:3000'; // Your radio server API

interface PodcastEpisode {
  id: string;
  title: string;
  description: string;
  audioUrl: string;
  duration: string;
  publishDate: string;
  coverImage?: string;
}

export default function Podcasts() {
  const [episodes, setEpisodes] = useState<PodcastEpisode[]>([]);
  const [currentEpisode, setCurrentEpisode] = useState<PodcastEpisode | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [audioElement, setAudioElement] = useState<HTMLAudioElement | null>(null);
  const [progress, setProgress] = useState(0);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);

  // Fetch and parse podcast feed
  useEffect(() => {
    fetchPodcasts();
  }, []);

  const fetchPodcasts = async () => {
    setIsLoading(true);
    try {
      // Your server proxies the podcast feed as XML
      const response = await fetch(`${API_BASE_URL}/api/podcasts`);
      if (response.ok) {
        const xmlText = await response.text();
        const parsedEpisodes = parseRSSFeed(xmlText);
        setEpisodes(parsedEpisodes);
        setError(null);
      } else {
        throw new Error('Failed to fetch podcast feed');
      }
    } catch (err) {
      console.error('Error fetching podcasts:', err);
      setError('Failed to load podcasts. Please try again later.');
      setEpisodes([]);
    } finally {
      setIsLoading(false);
    }
  };

  // Parse RSS/XML feed into episode objects
  const parseRSSFeed = (xmlText: string): PodcastEpisode[] => {
    const parser = new DOMParser();
    const xmlDoc = parser.parseFromString(xmlText, 'text/xml');
    
    // Check for parsing errors
    const parserError = xmlDoc.querySelector('parsererror');
    if (parserError) {
      console.error('XML parsing error');
      return [];
    }

    const items = xmlDoc.querySelectorAll('item');
    const episodes: PodcastEpisode[] = [];

    items.forEach((item, index) => {
      const title = item.querySelector('title')?.textContent || 'Untitled';
      const description = item.querySelector('description')?.textContent || 
                        item.querySelector('itunes\\:summary')?.textContent || 
                        item.querySelector('summary')?.textContent || '';
      const audioUrl = item.querySelector('enclosure')?.getAttribute('url') || '';
      const duration = item.querySelector('itunes\\:duration')?.textContent || 
                       item.querySelector('duration')?.textContent || '';
      const pubDate = item.querySelector('pubDate')?.textContent || '';
      const coverImage = item.querySelector('itunes\\:image')?.getAttribute('href') || 
                         xmlDoc.querySelector('channel > itunes\\:image')?.getAttribute('href') ||
                         xmlDoc.querySelector('channel image url')?.textContent || '';

      if (audioUrl) {
        episodes.push({
          id: `episode-${index}`,
          title,
          description: stripHtmlTags(description),
          audioUrl,
          duration: formatDuration(duration),
          publishDate: pubDate,
          coverImage,
        });
      }
    });

    return episodes;
  };

  // Strip HTML tags from description
  const stripHtmlTags = (html: string): string => {
    const tmp = document.createElement('div');
    tmp.innerHTML = html;
    return tmp.textContent || tmp.innerText || '';
  };

  // Format duration (convert seconds to MM:SS or keep as is)
  const formatDuration = (duration: string): string => {
    if (!duration) return '';
    
    // If it's just a number (seconds), convert to MM:SS
    const seconds = parseInt(duration, 10);
    if (!isNaN(seconds)) {
      const mins = Math.floor(seconds / 60);
      const secs = seconds % 60;
      return `${mins}:${secs.toString().padStart(2, '0')}`;
    }
    
    // If it already has a colon, assume it's formatted
    return duration;
  };

  // Handle audio playback with Media Session and background support
  useEffect(() => {
    if (currentEpisode && currentEpisode.audioUrl) {
      const audio = new Audio(currentEpisode.audioUrl);
      
      // Configure audio for background playback
      audio.preload = 'metadata';
      
      // @ts-ignore - AudioSession API
      if ('audioSession' in audio) {
        // @ts-ignore
        audio.audioSession = { type: 'playback' };
      }
      
      audio.addEventListener('timeupdate', () => {
        setCurrentTime(audio.currentTime);
        setDuration(audio.duration || 0);
        setProgress((audio.currentTime / (audio.duration || 1)) * 100);
      });
      audio.addEventListener('ended', () => {
        setIsPlaying(false);
        setProgress(0);
      });
      audio.addEventListener('error', () => {
        setError('Failed to load audio');
        setIsPlaying(false);
      });
      
      // Handle system audio interruptions
      audio.addEventListener('pause', () => {
        setIsPlaying(false);
      });
      audio.addEventListener('play', () => {
        setIsPlaying(true);
      });
      
      setAudioElement(audio);

      return () => {
        audio.pause();
        audio.src = '';
      };
    }
  }, [currentEpisode]);

  // Media Session API for podcast episodes
  useEffect(() => {
    if ('mediaSession' in navigator && currentEpisode) {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: currentEpisode.title,
        artist: 'Kringloop Verhalen Podcast',
        album: 'Radio Goed Voor Goed',
        artwork: currentEpisode.coverImage ? [
          { src: currentEpisode.coverImage, sizes: '512x512', type: 'image/jpeg' },
        ] : [
          { src: '/radio-logo.svg', sizes: '512x512', type: 'image/svg+xml' },
        ],
      });

      // Set up media session action handlers
      navigator.mediaSession.setActionHandler('play', () => {
        if (audioElement && !isPlaying) {
          audioElement.play()
            .then(() => setIsPlaying(true))
            .catch(console.error);
        }
      });

      navigator.mediaSession.setActionHandler('pause', () => {
        if (audioElement && isPlaying) {
          audioElement.pause();
          setIsPlaying(false);
        }
      });

      navigator.mediaSession.setActionHandler('seekbackward', (details) => {
        if (audioElement) {
          const skipTime = details.seekOffset || 10;
          audioElement.currentTime = Math.max(0, audioElement.currentTime - skipTime);
        }
      });

      navigator.mediaSession.setActionHandler('seekforward', (details) => {
        if (audioElement) {
          const skipTime = details.seekOffset || 10;
          audioElement.currentTime = Math.min(
            audioElement.duration || Infinity,
            audioElement.currentTime + skipTime
          );
        }
      });

      navigator.mediaSession.setActionHandler('previoustrack', () => {
        // Play previous episode if available
        if (currentEpisode && episodes.length > 0) {
          const currentIndex = episodes.findIndex(e => e.id === currentEpisode.id);
          if (currentIndex > 0) {
            playEpisode(episodes[currentIndex - 1]);
          }
        }
      });

      navigator.mediaSession.setActionHandler('nexttrack', () => {
        // Play next episode if available
        if (currentEpisode && episodes.length > 0) {
          const currentIndex = episodes.findIndex(e => e.id === currentEpisode.id);
          if (currentIndex < episodes.length - 1) {
            playEpisode(episodes[currentIndex + 1]);
          }
        }
      });

      navigator.mediaSession.playbackState = isPlaying ? 'playing' : 'paused';
    }

    return () => {
      if ('mediaSession' in navigator) {
        navigator.mediaSession.setActionHandler('play', null);
        navigator.mediaSession.setActionHandler('pause', null);
        navigator.mediaSession.setActionHandler('seekbackward', null);
        navigator.mediaSession.setActionHandler('seekforward', null);
        navigator.mediaSession.setActionHandler('previoustrack', null);
        navigator.mediaSession.setActionHandler('nexttrack', null);
      }
    };
  }, [isPlaying, currentEpisode, audioElement, episodes]);

  // Page Visibility API - keep playing when tab is hidden
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.hidden) {
        console.log('Podcast continuing in background...');
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, []);

  const togglePlay = () => {
    if (!audioElement) return;

    if (isPlaying) {
      audioElement.pause();
      setIsPlaying(false);
    } else {
      audioElement.play()
        .then(() => setIsPlaying(true))
        .catch((err) => {
          console.error('Playback failed:', err);
          setError('Failed to play audio');
        });
    }
  };

  const handleSeek = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!audioElement || !duration) return;
    const newTime = (parseFloat(e.target.value) / 100) * duration;
    audioElement.currentTime = newTime;
    setCurrentTime(newTime);
    setProgress(parseFloat(e.target.value));
  };

  const formatTime = (time: number) => {
    if (isNaN(time)) return '0:00';
    const minutes = Math.floor(time / 60);
    const seconds = Math.floor(time % 60);
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  };

  const formatPublishDate = (dateStr: string) => {
    try {
      const date = new Date(dateStr);
      return date.toLocaleDateString('nl-NL', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      });
    } catch {
      return dateStr;
    }
  };

  const playEpisode = (episode: PodcastEpisode) => {
    if (currentEpisode?.id === episode.id) {
      togglePlay();
    } else {
      if (audioElement) {
        audioElement.pause();
      }
      setCurrentEpisode(episode);
      setIsPlaying(true);
      setProgress(0);
      setCurrentTime(0);
      setError(null);
    }
  };

  return (
    <div className="podcasts">
      {error && <div className="info-banner">{error}</div>}

      {/* Player Bar */}
      {currentEpisode && (
        <div className="player-bar">
          <div className="player-bar-content">
            <div className="player-info">
              {currentEpisode.coverImage && (
                <img src={currentEpisode.coverImage} alt="" className="player-cover" />
              )}
              <div className="player-text">
                <h4>{currentEpisode.title}</h4>
                <span className="duration">{formatTime(currentTime)} / {formatTime(duration)}</span>
              </div>
            </div>
            
            <div className="player-controls-bar">
              <button className={`play-btn-bar ${isPlaying ? 'playing' : ''}`} onClick={togglePlay}>
                {isPlaying ? (
                  <svg viewBox="0 0 24 24" fill="currentColor">
                    <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>
                  </svg>
                ) : (
                  <svg viewBox="0 0 24 24" fill="currentColor">
                    <path d="M8 5v14l11-7z"/>
                  </svg>
                )}
              </button>
              
              <div className="progress-container">
                <input
                  type="range"
                  min="0"
                  max="100"
                  value={progress}
                  onChange={handleSeek}
                  className="progress-bar"
                />
              </div>
            </div>
          </div>
        </div>
      )}

      <div className="podcasts-container">
        <div className="podcasts-header">
          <h2>Kringloop Verhalen Podcast</h2>
          <button className="refresh-btn" onClick={fetchPodcasts} disabled={isLoading}>
            {isLoading ? 'Loading...' : 'Refresh'}
          </button>
        </div>
        
        {isLoading ? (
          <div className="loading-state">Loading episodes...</div>
        ) : episodes.length === 0 ? (
          <div className="empty-state">
            <p>No episodes found. Check your connection and try refreshing.</p>
          </div>
        ) : (
          <div className="episodes-grid">
            {episodes.map((episode) => (
              <div key={episode.id} className="episode-card">
                <div className="episode-cover">
                  {episode.coverImage ? (
                    <img src={episode.coverImage} alt={episode.title} />
                  ) : (
                    <div className="cover-placeholder">
                      <svg viewBox="0 0 24 24" fill="currentColor">
                        <path d="M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z"/>
                      </svg>
                    </div>
                  )}
                  <button 
                    className={`play-overlay ${currentEpisode?.id === episode.id && isPlaying ? 'playing' : ''}`}
                    onClick={() => playEpisode(episode)}
                  >
                    {currentEpisode?.id === episode.id && isPlaying ? (
                      <svg viewBox="0 0 24 24" fill="currentColor">
                        <path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/>
                      </svg>
                    ) : (
                      <svg viewBox="0 0 24 24" fill="currentColor">
                        <path d="M8 5v14l11-7z"/>
                      </svg>
                    )}
                  </button>
                </div>
                
                <div className="episode-info">
                  <h3 className="episode-title">{episode.title}</h3>
                  <p className="episode-description">{episode.description}</p>
                  <div className="episode-meta">
                    <span className="episode-date">{formatPublishDate(episode.publishDate)}</span>
                    {episode.duration && (
                      <span className="episode-duration">{episode.duration}</span>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
