import { useState, useEffect } from 'react';
import { Capacitor, CapacitorHttp } from '@capacitor/core';
import { useAudio } from '../context/AudioContext';
import './Podcasts.css';

const API_BASE_URLS = [
  'http://radiogvg.chickenkiller.com:3000',
];
const API_TIMEOUT_MS = 12000;

interface PodcastEpisode {
  id: string;
  title: string;
  description: string;
  audioUrl: string;
  duration: string;
  publishDate: string;
  coverImage?: string;
}

async function fetchWithFallback(path: string, init?: RequestInit): Promise<Response> {
  let lastError: unknown;
  const isNativeIOS = Capacitor.isNativePlatform() && Capacitor.getPlatform() === 'ios';

  for (const baseUrl of API_BASE_URLS) {
    const url = `${baseUrl}${path}`;
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), API_TIMEOUT_MS);

    try {
      if (isNativeIOS) {
        const nativeResponse = await CapacitorHttp.request({
          url,
          method: (init?.method || 'GET') as 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE',
          headers: init?.headers as Record<string, string> | undefined,
          data: init?.body ? JSON.parse(String(init.body)) : undefined,
          readTimeout: API_TIMEOUT_MS,
          connectTimeout: API_TIMEOUT_MS,
        });

        if (nativeResponse.status >= 200 && nativeResponse.status < 300) {
          return new Response(
            typeof nativeResponse.data === 'string' ? nativeResponse.data : JSON.stringify(nativeResponse.data),
            {
              status: nativeResponse.status,
              headers: { 'content-type': String(nativeResponse.headers?.['content-type'] || 'application/json') },
            }
          );
        }

        lastError = new Error(`HTTP ${nativeResponse.status} for ${url}`);
      } else {
        const response = await fetch(url, {
          ...init,
          signal: controller.signal,
        });

        if (response.ok) {
          return response;
        }

        lastError = new Error(`HTTP ${response.status} for ${url}`);
      }
    } catch (err) {
      lastError = err;
    } finally {
      clearTimeout(timeoutId);
    }
  }

  throw lastError || new Error('All API endpoints failed');
}

export default function Podcasts() {
  const {
    isPlaying,
    mode,
    podcastMetadata,
    podcastAudio,
    playPodcast,
    pausePodcast,
    seekPodcast,
  } = useAudio();

  const [episodes, setEpisodes] = useState<PodcastEpisode[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [progress, setProgress] = useState(0);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);

  const isPodcastPlaying = isPlaying && mode === 'podcast';
  const currentEpisode = podcastMetadata ? episodes.find(e => e.id === podcastMetadata.id) : null;

  // Fetch and parse podcast feed
  useEffect(() => {
    fetchPodcasts();
  }, []);

  const fetchPodcasts = async () => {
    setIsLoading(true);
    try {
      const response = await fetchWithFallback('/api/podcasts');
      const contentType = response.headers.get('content-type') || '';

      let parsedEpisodes: PodcastEpisode[] = [];

      if (contentType.includes('application/json')) {
        const data = await response.json();
        parsedEpisodes = Array.isArray(data)
          ? data.map((episode, index) => ({
              id: String(episode.id ?? `episode-${index}`),
              title: episode.title || 'Untitled',
              description: stripHtmlTags(episode.description || ''),
              audioUrl: episode.audioUrl || episode.url || '',
              duration: formatDuration(String(episode.duration || '')),
              publishDate: episode.publishDate || episode.pubDate || '',
              coverImage: episode.coverImage || episode.image || '',
            })).filter((episode) => episode.audioUrl)
          : [];
      } else {
        const xmlText = await response.text();
        parsedEpisodes = parseRSSFeed(xmlText);
      }

      setEpisodes(parsedEpisodes);
      setError(null);
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

  // Track progress when podcast is playing
  useEffect(() => {
    if (!podcastAudio || mode !== 'podcast') {
      return;
    }

    const handleTimeUpdate = () => {
      const current = podcastAudio.currentTime;
      const dur = podcastAudio.duration;
      setCurrentTime(current);
      setDuration(dur);
      setProgress(dur > 0 ? (current / dur) * 100 : 0);
    };

    podcastAudio.addEventListener('timeupdate', handleTimeUpdate);

    // Initial sync
    handleTimeUpdate();

    return () => {
      podcastAudio.removeEventListener('timeupdate', handleTimeUpdate);
    };
  }, [podcastAudio, mode]);

  // Reset progress when podcast stops
  useEffect(() => {
    if (mode !== 'podcast') {
      setProgress(0);
      setCurrentTime(0);
      setDuration(0);
    }
  }, [mode]);

  const togglePlay = () => {
    if (!currentEpisode) return;

    if (isPodcastPlaying) {
      pausePodcast();
    } else if (mode === 'podcast' && podcastAudio) {
      // Resume current podcast
      podcastAudio.play()
        .then(() => {
          // AudioContext will handle state updates
        })
        .catch((err) => {
          console.error('Playback failed:', err);
          setError('Failed to play audio');
        });
    }
  };

  const handleSeek = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!podcastAudio || !duration) return;
    const newProgress = parseFloat(e.target.value);
    const newTime = (newProgress / 100) * duration;
    seekPodcast(newTime * 1000); // Convert to ms for context
    setCurrentTime(newTime);
    setProgress(newProgress);
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
    const isSameEpisode = podcastMetadata?.id === episode.id;

    if (isSameEpisode && isPodcastPlaying) {
      // Toggle pause for same episode
      pausePodcast();
    } else if (isSameEpisode && !isPodcastPlaying) {
      // Resume same episode
      if (podcastAudio) {
        podcastAudio.play().catch(console.error);
      }
    } else {
      // Start new episode
      playPodcast({
        id: episode.id,
        title: episode.title,
        audioUrl: episode.audioUrl,
        coverImage: episode.coverImage,
      });
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
              <button className={`play-btn-bar ${isPodcastPlaying ? 'playing' : ''}`} onClick={togglePlay}>
                {isPodcastPlaying ? (
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
              <div key={episode.id} className="episode-card" onClick={() => playEpisode(episode)}>
                <div className="episode-cover">
                  {episode.coverImage && episode.coverImage.trim() !== '' ? (
                    <img src={episode.coverImage} alt={episode.title} />
                  ) : (
                    <img src="/kv_logo.png" alt="Kringloop Verhalen" className="fallback-logo" />
                  )}
                  <button
                    className={`play-overlay ${podcastMetadata?.id === episode.id && isPodcastPlaying ? 'playing' : ''}`}
                    onClick={(e) => {
                      e.stopPropagation();
                      playEpisode(episode);
                    }}
                  >
                    {podcastMetadata?.id === episode.id && isPodcastPlaying ? (
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
