import { createContext, useContext, useState, useEffect, useCallback, useRef, ReactNode } from 'react';

type PlaybackMode = 'none' | 'radio' | 'podcast';

interface RadioMetadata {
  title: string;
  artist: string;
  listeners: number;
  artUrl: string | null;
}

interface PodcastMetadata {
  id: string;
  title: string;
  artist: string;
  coverUrl: string | null;
  duration: number;
  position: number;
}

interface AudioState {
  // Playback state
  isPlaying: boolean;
  mode: PlaybackMode;
  volume: number;

  // Radio specific
  radioAudio: HTMLAudioElement | null;
  radioMetadata: RadioMetadata;

  // Podcast specific
  podcastAudio: HTMLAudioElement | null;
  podcastMetadata: PodcastMetadata | null;
}

interface AudioContextType extends AudioState {
  // Radio controls
  playRadio: () => void;
  pauseRadio: () => void;
  stopRadio: () => void;
  setRadioMetadata: (metadata: Partial<RadioMetadata>) => void;

  // Podcast controls
  playPodcast: (episode: { id: string; title: string; audioUrl: string; coverImage?: string }) => void;
  pausePodcast: () => void;
  stopPodcast: () => void;
  seekPodcast: (position: number) => void;
  setPodcastMetadata: (metadata: Partial<PodcastMetadata>) => void;

  // Volume control
  setVolume: (volume: number) => void;

  // Utility
  stopAll: () => void;
}

const RADIO_STREAM_URL = 'https://ex52.voordeligstreamen.nl/8154/stream';
const AUDIO_PLAYING_EVENT = 'audioPlaying';

const defaultRadioMetadata: RadioMetadata = {
  title: 'radiogoedvoorgoed',
  artist: 'Live Stream',
  listeners: 0,
  artUrl: null,
};

const AudioContext = createContext<AudioContextType | null>(null);

// Global state that persists outside of React component lifecycle
let globalAudioState: {
  radioAudio: HTMLAudioElement | null;
  podcastAudio: HTMLAudioElement | null;
  isPlaying: boolean;
  mode: PlaybackMode;
  volume: number;
  radioMetadata: RadioMetadata;
  podcastMetadata: PodcastMetadata | null;
} = {
  radioAudio: null,
  podcastAudio: null,
  isPlaying: false,
  mode: 'none',
  volume: 0.8,
  radioMetadata: { ...defaultRadioMetadata },
  podcastMetadata: null,
};

// Store setState callbacks from all provider instances to sync them
const listeners = new Set<(state: AudioState) => void>();

function notifyListeners() {
  const state: AudioState = {
    isPlaying: globalAudioState.isPlaying,
    mode: globalAudioState.mode,
    volume: globalAudioState.volume,
    radioAudio: globalAudioState.radioAudio,
    radioMetadata: globalAudioState.radioMetadata,
    podcastAudio: globalAudioState.podcastAudio,
    podcastMetadata: globalAudioState.podcastMetadata,
  };
  listeners.forEach(listener => listener(state));
}

export function AudioProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AudioState>({
    isPlaying: globalAudioState.isPlaying,
    mode: globalAudioState.mode,
    volume: globalAudioState.volume,
    radioAudio: globalAudioState.radioAudio,
    radioMetadata: globalAudioState.radioMetadata,
    podcastAudio: globalAudioState.podcastAudio,
    podcastMetadata: globalAudioState.podcastMetadata,
  });

  // Register this component's setState to receive updates
  useEffect(() => {
    const listener = (newState: AudioState) => setState({ ...newState });
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  }, []);

  // Listen for podcast events from other components
  useEffect(() => {
    const handleOtherAudioPlaying = (event: CustomEvent) => {
      if (event.detail?.source === 'podcast' && globalAudioState.mode === 'radio') {
        // Stop radio when podcast starts
        stopRadioInternal();
      }
    };

    window.addEventListener(AUDIO_PLAYING_EVENT, handleOtherAudioPlaying as EventListener);
    return () => {
      window.removeEventListener(AUDIO_PLAYING_EVENT, handleOtherAudioPlaying as EventListener);
    };
  }, []);

  // Update Media Session API
  useEffect(() => {
    if ('mediaSession' in navigator) {
      let title = 'radiogoedvoorgoed';
      let artist = '';
      let artwork: MediaImage[] = [{ src: '/radio-logo.svg', sizes: '512x512', type: 'image/svg+xml' }];

      if (state.mode === 'radio') {
        title = state.radioMetadata.title;
        artist = state.radioMetadata.artist;
      } else if (state.mode === 'podcast' && state.podcastMetadata) {
        title = state.podcastMetadata.title;
        artist = state.podcastMetadata.artist;
        if (state.podcastMetadata.coverUrl) {
          artwork = [{ src: state.podcastMetadata.coverUrl, sizes: '512x512', type: 'image/jpeg' }];
        }
      }

      navigator.mediaSession.metadata = new MediaMetadata({
        title,
        artist,
        album: 'radiogoedvoorgoed',
        artwork,
      });

      navigator.mediaSession.playbackState = state.isPlaying ? 'playing' : 'paused';

      // Set up action handlers
      navigator.mediaSession.setActionHandler('play', () => {
        if (state.mode === 'radio') {
          playRadioInternal();
        } else if (state.mode === 'podcast') {
          playPodcastInternal();
        }
      });

      navigator.mediaSession.setActionHandler('pause', () => {
        if (state.mode === 'radio') {
          pauseRadioInternal();
        } else if (state.mode === 'podcast') {
          pausePodcastInternal();
        }
      });

      navigator.mediaSession.setActionHandler('stop', () => {
        stopAllInternal();
      });

      if (state.mode === 'podcast') {
        navigator.mediaSession.setActionHandler('seekto', (details) => {
          if (details.seekTime !== undefined) {
            seekPodcastInternal(details.seekTime * 1000); // Convert to ms
          }
        });

        navigator.mediaSession.setActionHandler('seekbackward', (details) => {
          const currentPos = globalAudioState.podcastAudio?.currentTime || 0;
          const skipTime = details.seekOffset || 10;
          seekPodcastInternal(Math.max(0, (currentPos - skipTime) * 1000));
        });

        navigator.mediaSession.setActionHandler('seekforward', (details) => {
          const currentPos = globalAudioState.podcastAudio?.currentTime || 0;
          const skipTime = details.seekOffset || 10;
          const duration = globalAudioState.podcastAudio?.duration || 0;
          seekPodcastInternal(Math.min(duration, (currentPos + skipTime) * 1000));
        });
      }
    }

    return () => {
      if ('mediaSession' in navigator) {
        navigator.mediaSession.setActionHandler('play', null);
        navigator.mediaSession.setActionHandler('pause', null);
        navigator.mediaSession.setActionHandler('stop', null);
        navigator.mediaSession.setActionHandler('seekto', null);
        navigator.mediaSession.setActionHandler('seekbackward', null);
        navigator.mediaSession.setActionHandler('seekforward', null);
      }
    };
  }, [state.isPlaying, state.mode, state.radioMetadata, state.podcastMetadata]);

  // Internal functions that modify global state
  function stopRadioInternal() {
    if (globalAudioState.radioAudio) {
      globalAudioState.radioAudio.pause();
      globalAudioState.radioAudio.src = '';
      globalAudioState.radioAudio.load();
      globalAudioState.radioAudio = null;
    }
    if (globalAudioState.mode === 'radio') {
      globalAudioState.isPlaying = false;
      globalAudioState.mode = 'none';
    }
    notifyListeners();
  }

  function stopPodcastInternal() {
    if (globalAudioState.podcastAudio) {
      globalAudioState.podcastAudio.pause();
      globalAudioState.podcastAudio.src = '';
      globalAudioState.podcastAudio.load();
      globalAudioState.podcastAudio = null;
    }
    if (globalAudioState.mode === 'podcast') {
      globalAudioState.isPlaying = false;
      globalAudioState.mode = 'none';
    }
    notifyListeners();
  }

  function stopAllInternal() {
    stopRadioInternal();
    stopPodcastInternal();
    globalAudioState.mode = 'none';
    globalAudioState.isPlaying = false;
    notifyListeners();
  }

  function playRadioInternal() {
    // Stop any existing podcast
    stopPodcastInternal();

    // Notify other components
    window.dispatchEvent(new CustomEvent(AUDIO_PLAYING_EVENT, { detail: { source: 'radio' } }));

    // Create new audio element if needed
    if (!globalAudioState.radioAudio) {
      const audio = new Audio();
      audio.src = RADIO_STREAM_URL;
      audio.volume = globalAudioState.volume;
      audio.preload = 'none';

      // @ts-ignore
      if ('audioSession' in audio) {
        // @ts-ignore
        audio.audioSession = { type: 'playback' };
      }

      globalAudioState.radioAudio = audio;
    }

    globalAudioState.radioAudio.volume = globalAudioState.volume;

    globalAudioState.radioAudio.play()
      .then(() => {
        globalAudioState.isPlaying = true;
        globalAudioState.mode = 'radio';
        notifyListeners();
      })
      .catch((err) => {
        console.error('Failed to play radio:', err);
        globalAudioState.isPlaying = false;
        notifyListeners();
      });
  }

  function pauseRadioInternal() {
    if (globalAudioState.radioAudio) {
      globalAudioState.radioAudio.pause();
      globalAudioState.radioAudio.src = '';
      globalAudioState.radioAudio.load();
      globalAudioState.radioAudio = null;
    }
    globalAudioState.isPlaying = false;
    globalAudioState.mode = 'none';
    notifyListeners();
  }

  function playPodcastInternal() {
    if (globalAudioState.podcastAudio) {
      globalAudioState.podcastAudio.play()
        .then(() => {
          globalAudioState.isPlaying = true;
          notifyListeners();
        })
        .catch(console.error);
    }
  }

  function pausePodcastInternal() {
    if (globalAudioState.podcastAudio) {
      globalAudioState.podcastAudio.pause();
      globalAudioState.isPlaying = false;
      notifyListeners();
    }
  }

  function seekPodcastInternal(positionMs: number) {
    if (globalAudioState.podcastAudio) {
      const positionSec = positionMs / 1000;
      globalAudioState.podcastAudio.currentTime = positionSec;
      globalAudioState.podcastMetadata = {
        ...globalAudioState.podcastMetadata!,
        position: positionMs,
      };
      notifyListeners();
    }
  }

  // Public API
  const playRadio = useCallback(() => {
    playRadioInternal();
  }, []);

  const pauseRadio = useCallback(() => {
    pauseRadioInternal();
  }, []);

  const stopRadio = useCallback(() => {
    stopRadioInternal();
  }, []);

  const setRadioMetadata = useCallback((metadata: Partial<RadioMetadata>) => {
    globalAudioState.radioMetadata = { ...globalAudioState.radioMetadata, ...metadata };
    notifyListeners();
  }, []);

  const playPodcast = useCallback((episode: { id: string; title: string; audioUrl: string; coverImage?: string }) => {
    // Stop any existing radio
    stopRadioInternal();

    // Notify other components
    window.dispatchEvent(new CustomEvent(AUDIO_PLAYING_EVENT, { detail: { source: 'podcast' } }));

    // Stop existing podcast if playing a different one
    if (globalAudioState.podcastAudio && globalAudioState.podcastMetadata?.id !== episode.id) {
      stopPodcastInternal();
    }

    // Create new audio element if needed
    if (!globalAudioState.podcastAudio) {
      const audio = new Audio();
      audio.src = episode.audioUrl;
      audio.volume = globalAudioState.volume;
      audio.preload = 'metadata';

      // @ts-ignore
      if ('audioSession' in audio) {
        // @ts-ignore
        audio.audioSession = { type: 'playback' };
      }

      // Set up event listeners
      audio.addEventListener('ended', () => {
        globalAudioState.isPlaying = false;
        notifyListeners();
      });

      audio.addEventListener('error', () => {
        globalAudioState.isPlaying = false;
        notifyListeners();
      });

      globalAudioState.podcastAudio = audio;
      globalAudioState.podcastMetadata = {
        id: episode.id,
        title: episode.title,
        artist: 'Kringloop Verhalen',
        coverUrl: episode.coverImage || null,
        duration: 0,
        position: 0,
      };
    }

    globalAudioState.podcastAudio.volume = globalAudioState.volume;

    globalAudioState.podcastAudio.play()
      .then(() => {
        globalAudioState.isPlaying = true;
        globalAudioState.mode = 'podcast';
        notifyListeners();
      })
      .catch((err) => {
        console.error('Failed to play podcast:', err);
        globalAudioState.isPlaying = false;
        notifyListeners();
      });
  }, []);

  const pausePodcast = useCallback(() => {
    pausePodcastInternal();
  }, []);

  const stopPodcast = useCallback(() => {
    stopPodcastInternal();
  }, []);

  const seekPodcast = useCallback((position: number) => {
    seekPodcastInternal(position);
  }, []);

  const setPodcastMetadata = useCallback((metadata: Partial<PodcastMetadata>) => {
    if (globalAudioState.podcastMetadata) {
      globalAudioState.podcastMetadata = { ...globalAudioState.podcastMetadata, ...metadata };
    }
    notifyListeners();
  }, []);

  const setVolume = useCallback((volume: number) => {
    const safeVolume = Math.max(0, Math.min(1, volume));
    globalAudioState.volume = safeVolume;
    if (globalAudioState.radioAudio) {
      globalAudioState.radioAudio.volume = safeVolume;
    }
    if (globalAudioState.podcastAudio) {
      globalAudioState.podcastAudio.volume = safeVolume;
    }
    notifyListeners();
  }, []);

  const stopAll = useCallback(() => {
    stopAllInternal();
  }, []);

  const value: AudioContextType = {
    ...state,
    playRadio,
    pauseRadio,
    stopRadio,
    setRadioMetadata,
    playPodcast,
    pausePodcast,
    stopPodcast,
    seekPodcast,
    setPodcastMetadata,
    setVolume,
    stopAll,
  };

  return (
    <AudioContext.Provider value={value}>
      {children}
    </AudioContext.Provider>
  );
}

export function useAudio() {
  const context = useContext(AudioContext);
  if (!context) {
    throw new Error('useAudio must be used within an AudioProvider');
  }
  return context;
}
