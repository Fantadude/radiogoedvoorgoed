import { useState, useRef, useEffect } from "react";
import { Play, Pause, Volume2, VolumeX, Radio } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Slider } from "@/components/ui/slider";
import { APP_CONFIG } from "@/config/app";

interface NowPlaying {
  title: string;
  art: string;
  listeners: number;
  ulistener: number;
  bitrate: string;
  djusername: string;
  djprofile: string;
}

const RadioPlayer = () => {
  const audioRef = useRef<HTMLAudioElement>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [volume, setVolume] = useState(80);
  const [isMuted, setIsMuted] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [nowPlaying, setNowPlaying] = useState<NowPlaying | null>(null);

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
      } catch (error) {
        console.error("Failed to fetch now playing:", error);
      }
    };

    fetchNowPlaying();
    const interval = setInterval(fetchNowPlaying, 15000);
    return () => clearInterval(interval);
  }, []);

  const togglePlay = async () => {
    if (!audioRef.current) return;

    setIsLoading(true);
    try {
      if (isPlaying) {
        audioRef.current.pause();
        setIsPlaying(false);
      } else {
        // Reload stream to get fresh audio
        audioRef.current.src = APP_CONFIG.streamUrl;
        await audioRef.current.play();
        setIsPlaying(true);
      }
    } catch (error) {
      console.error("Playback error:", error);
    } finally {
      setIsLoading(false);
    }
  };

  const toggleMute = () => {
    setIsMuted(!isMuted);
  };

  return (
    <div className="flex flex-col items-center gap-6 p-6">
      {/* Album Art / Logo */}
      <div className="relative">
        <div className={`w-48 h-48 rounded-3xl overflow-hidden radio-gradient flex items-center justify-center ${isPlaying ? 'player-glow pulse-animation' : ''}`}>
          {nowPlaying?.art ? (
            <img src={nowPlaying.art} alt="Album art" className="w-full h-full object-cover" />
          ) : (
            <Radio className="w-20 h-20 text-primary-foreground" />
          )}
        </div>
        {isPlaying && (
          <div className="absolute -bottom-3 left-1/2 -translate-x-1/2 bg-card px-4 py-1 rounded-full shadow-lg">
            <div className="audio-wave">
              <span></span>
              <span></span>
              <span></span>
              <span></span>
              <span></span>
            </div>
          </div>
        )}
      </div>

      {/* Now Playing Info */}
      <div className="text-center space-y-1">
        <p className="text-sm text-muted-foreground uppercase tracking-wider">Nu aan het spelen</p>
        <h2 className="text-2xl font-semibold">{nowPlaying?.title || "GoedvoorGoed Radio"}</h2>
        {nowPlaying && (
          <p className="text-sm text-muted-foreground">
            {nowPlaying.listeners} luisteraars • {nowPlaying.bitrate} kbps
          </p>
        )}
      </div>

      {/* Play Button */}
      <Button
        onClick={togglePlay}
        disabled={isLoading}
        size="lg"
        className="w-20 h-20 rounded-full radio-gradient hover:opacity-90 transition-all duration-300 shadow-xl"
      >
        {isLoading ? (
          <div className="w-8 h-8 border-3 border-primary-foreground border-t-transparent rounded-full animate-spin" />
        ) : isPlaying ? (
          <Pause className="w-10 h-10" fill="currentColor" />
        ) : (
          <Play className="w-10 h-10 ml-1" fill="currentColor" />
        )}
      </Button>

      {/* Volume Control */}
      <div className="flex items-center gap-4 w-full max-w-xs">
        <Button
          variant="ghost"
          size="icon"
          onClick={toggleMute}
          className="shrink-0"
        >
          {isMuted || volume === 0 ? (
            <VolumeX className="w-5 h-5" />
          ) : (
            <Volume2 className="w-5 h-5" />
          )}
        </Button>
        <Slider
          value={[isMuted ? 0 : volume]}
          onValueChange={(value) => {
            setVolume(value[0]);
            if (value[0] > 0) setIsMuted(false);
          }}
          max={100}
          step={1}
          className="flex-1"
        />
        <span className="text-sm text-muted-foreground w-8 text-right">
          {isMuted ? 0 : volume}%
        </span>
      </div>

      {/* Hidden Audio Element */}
      <audio
        ref={audioRef}
        preload="none"
        onEnded={() => setIsPlaying(false)}
        onError={() => {
          setIsPlaying(false);
          setIsLoading(false);
        }}
      />
    </div>
  );
};

export default RadioPlayer;
