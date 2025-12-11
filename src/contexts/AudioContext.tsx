import { createContext, useContext, useRef, useState, useEffect, ReactNode, useCallback } from "react";
import { APP_CONFIG } from "@/config/app";
import { useForegroundService } from "@/hooks/useForegroundService";

interface NowPlaying {
  title: string;
  art: string;
  listeners: number;
  ulistener: number;
  bitrate: string;
  djusername: string;
  djprofile: string;
}

interface AudioContextType {
  isPlaying: boolean;
  isLoading: boolean;
  volume: number;
  isMuted: boolean;
  nowPlaying: NowPlaying | null;
  togglePlay: () => Promise<void>;
  toggleMute: () => void;
  setVolume: (volume: number) => void;
}

const AudioContext = createContext<AudioContextType | null>(null);

export const useAudio = () => {
  const context = useContext(AudioContext);
  if (!context) {
    throw new Error("useAudio must be used within AudioProvider");
  }
  return context;
};

export const AudioProvider = ({ children }: { children: ReactNode }) => {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [volume, setVolumeState] = useState(80);
  const [isMuted, setIsMuted] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [nowPlaying, setNowPlaying] = useState<NowPlaying | null>(null);
  
  const { startForegroundService, updateForegroundService, stopForegroundService } = useForegroundService();

  // Create audio element once on mount
  useEffect(() => {
    const audio = new Audio();
    audio.preload = "none";
    audioRef.current = audio;

    audio.addEventListener("ended", () => setIsPlaying(false));
    audio.addEventListener("error", () => {
      setIsPlaying(false);
      setIsLoading(false);
    });

    return () => {
      audio.pause();
      audio.src = "";
    };
  }, []);

  // Update volume
  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.volume = isMuted ? 0 : volume / 100;
    }
  }, [volume, isMuted]);

  // Fetch now playing info periodically
  useEffect(() => {
    const fetchNowPlaying = async () => {
      try {
        const response = await fetch("https://ex52.voordeligstreamen.nl/cp/get_info.php?p=8154");
        const data = await response.json();
        setNowPlaying(data);
        
        // Update media session metadata for lock screen controls
        if ("mediaSession" in navigator && data) {
          navigator.mediaSession.metadata = new MediaMetadata({
            title: data.title || "GoedvoorGoed Radio",
            artist: data.djusername || "GoedvoorGoed",
            album: "Live Radio",
            artwork: data.art ? [{ src: data.art, sizes: "512x512", type: "image/jpeg" }] : [],
          });
        }
        
        // Update foreground service notification if playing
        if (isPlaying && data) {
          updateForegroundService(
            data.title || "GoedvoorGoed Radio",
            data.djusername ? `DJ: ${data.djusername}` : "Live Radio"
          );
        }
      } catch (error) {
        console.error("Failed to fetch now playing:", error);
      }
    };

    fetchNowPlaying();
    const interval = setInterval(fetchNowPlaying, 15000);
    return () => clearInterval(interval);
  }, [isPlaying, updateForegroundService]);

  // Setup media session handlers for background playback controls
  useEffect(() => {
    if ("mediaSession" in navigator) {
      navigator.mediaSession.setActionHandler("play", () => togglePlay());
      navigator.mediaSession.setActionHandler("pause", () => togglePlay());
      navigator.mediaSession.setActionHandler("stop", () => {
        if (audioRef.current) {
          audioRef.current.pause();
          setIsPlaying(false);
          stopForegroundService();
        }
      });
    }
  }, [stopForegroundService]);

  const togglePlay = useCallback(async () => {
    if (!audioRef.current) return;

    setIsLoading(true);
    try {
      if (isPlaying) {
        audioRef.current.pause();
        setIsPlaying(false);
        await stopForegroundService();
      } else {
        // Start foreground service first for background playback
        await startForegroundService(
          nowPlaying?.title || "GoedvoorGoed Radio",
          nowPlaying?.djusername ? `DJ: ${nowPlaying.djusername}` : "Live Radio"
        );
        
        // Reload stream to get fresh audio
        audioRef.current.src = APP_CONFIG.streamUrl;
        await audioRef.current.play();
        setIsPlaying(true);
      }
    } catch (error) {
      console.error("Playback error:", error);
      await stopForegroundService();
    } finally {
      setIsLoading(false);
    }
  }, [isPlaying, nowPlaying, startForegroundService, stopForegroundService]);

  const toggleMute = () => {
    setIsMuted(!isMuted);
  };

  const setVolume = (newVolume: number) => {
    setVolumeState(newVolume);
    if (newVolume > 0) setIsMuted(false);
  };

  return (
    <AudioContext.Provider
      value={{
        isPlaying,
        isLoading,
        volume,
        isMuted,
        nowPlaying,
        togglePlay,
        toggleMute,
        setVolume,
      }}
    >
      {children}
    </AudioContext.Provider>
  );
};
