import { Play, Pause, Volume2, VolumeX, Radio } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Slider } from "@/components/ui/slider";
import { useAudio } from "@/contexts/AudioContext";

const RadioPlayer = () => {
  const {
    isPlaying,
    isLoading,
    volume,
    isMuted,
    nowPlaying,
    togglePlay,
    toggleMute,
    setVolume,
  } = useAudio();

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
          onValueChange={(value) => setVolume(value[0])}
          max={100}
          step={1}
          className="flex-1"
        />
        <span className="text-sm text-muted-foreground w-8 text-right">
          {isMuted ? 0 : volume}%
        </span>
      </div>
    </div>
  );
};

export default RadioPlayer;
