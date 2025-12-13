import { useState, useEffect, useRef } from "react";
import { Podcast, Play, Pause, Loader2, ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useToast } from "@/hooks/use-toast";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Slider } from "@/components/ui/slider";
import { useAudio } from "@/contexts/AudioContext";

interface PodcastEpisode {
  id: string;
  title: string;
  description: string;
  audioUrl: string;
  link: string;
  pubDate: string;
  author: string;
}

const EPISODES_PER_PAGE = 5;

const PodcastBrowser = () => {
  const { toast } = useToast();
  const { isPlaying: radioIsPlaying, togglePlay: toggleRadio, stopPodcastCallback } = useAudio();
  const [episodes, setEpisodes] = useState<PodcastEpisode[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [currentPage, setCurrentPage] = useState(1);
  
  // Audio player state
  const [playingEpisode, setPlayingEpisode] = useState<PodcastEpisode | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const isMountedRef = useRef(true);

  const fetchPodcasts = async () => {
    setIsLoading(true);
    try {
      const response = await fetch(
        `${import.meta.env.VITE_SUPABASE_URL}/functions/v1/podcasts`
      );
      
      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.error || 'Failed to fetch podcasts');
      }
      
      const result = await response.json();
      
      if (result.error) {
        throw new Error(result.error);
      }
      
      setEpisodes(result.episodes || []);
    } catch (error: any) {
      console.error('Error fetching podcasts:', error);
      toast({
        title: "Kon podcasts niet laden",
        description: error.message || "Probeer het later nog eens",
        variant: "destructive",
      });
      setEpisodes([]);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    isMountedRef.current = true;
    fetchPodcasts();
    
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  // Audio player setup - only create once
  useEffect(() => {
    const audio = new Audio();
    audioRef.current = audio;
    
    const handleTimeUpdate = () => {
      if (isMountedRef.current) {
        setCurrentTime(audio.currentTime);
      }
    };
    
    const handleLoadedMetadata = () => {
      if (isMountedRef.current) {
        setDuration(audio.duration);
      }
    };
    
    const handleEnded = () => {
      if (isMountedRef.current) {
        setIsPlaying(false);
      }
    };
    
    const handleError = (e: Event) => {
      // Only show error if component is mounted and audio has a source
      if (isMountedRef.current && audio.src && audio.src !== '') {
        console.error('Podcast audio error:', e);
      }
    };
    
    audio.addEventListener('timeupdate', handleTimeUpdate);
    audio.addEventListener('loadedmetadata', handleLoadedMetadata);
    audio.addEventListener('ended', handleEnded);
    audio.addEventListener('error', handleError);
    
    return () => {
      // Clean up without triggering error
      audio.removeEventListener('timeupdate', handleTimeUpdate);
      audio.removeEventListener('loadedmetadata', handleLoadedMetadata);
      audio.removeEventListener('ended', handleEnded);
      audio.removeEventListener('error', handleError);
      audio.pause();
      audio.src = "";
    };
  }, []);

  // Register stop callback so radio can stop podcast
  useEffect(() => {
    stopPodcastCallback.current = () => {
      if (audioRef.current) {
        audioRef.current.pause();
        setIsPlaying(false);
      }
    };
    
    return () => {
      stopPodcastCallback.current = null;
    };
  }, [stopPodcastCallback]);

  // Stop podcast when radio starts playing
  useEffect(() => {
    if (radioIsPlaying && isPlaying && audioRef.current) {
      audioRef.current.pause();
      setIsPlaying(false);
    }
  }, [radioIsPlaying, isPlaying]);

  const handlePlayEpisode = async (episode: PodcastEpisode) => {
    if (!audioRef.current) return;
    
    // Stop radio if it's playing
    if (radioIsPlaying) {
      await toggleRadio();
    }
    
    if (playingEpisode?.id === episode.id) {
      // Toggle play/pause for same episode
      if (isPlaying) {
        audioRef.current.pause();
        setIsPlaying(false);
      } else {
        await audioRef.current.play();
        setIsPlaying(true);
      }
    } else {
      // Play new episode
      audioRef.current.src = episode.audioUrl;
      setPlayingEpisode(episode);
      setCurrentTime(0);
      setDuration(0);
      try {
        await audioRef.current.play();
        setIsPlaying(true);
      } catch (error) {
        console.error('Error playing podcast:', error);
      }
    }
  };

  const handleSeek = (value: number[]) => {
    if (audioRef.current) {
      audioRef.current.currentTime = value[0];
      setCurrentTime(value[0]);
    }
  };

  const formatTime = (seconds: number) => {
    if (!seconds || isNaN(seconds)) return "0:00";
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  const formatDate = (dateStr: string) => {
    try {
      const date = new Date(dateStr);
      return date.toLocaleDateString('nl-NL', { 
        day: 'numeric', 
        month: 'long', 
        year: 'numeric' 
      });
    } catch {
      return dateStr;
    }
  };

  // Pagination
  const totalPages = Math.ceil(episodes.length / EPISODES_PER_PAGE);
  const startIndex = (currentPage - 1) * EPISODES_PER_PAGE;
  const currentEpisodes = episodes.slice(startIndex, startIndex + EPISODES_PER_PAGE);

  return (
    <div className="p-4 pb-8 space-y-4">
      <div className="text-center space-y-2 mb-6">
        <h1 className="text-2xl font-bold">Podcasts</h1>
        <p className="text-muted-foreground">Kringloop Verhalen</p>
      </div>

      <Card className="glass-card">
        <CardHeader className="pb-3">
          <CardTitle className="text-lg flex items-center gap-2">
            <Podcast className="w-5 h-5 text-primary" />
            Afleveringen
          </CardTitle>
          <CardDescription>
            Verhalen uit Kringloopcentrum GoedvoorGoed
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* Now Playing Mini Player */}
          {playingEpisode && (
            <div className="p-3 rounded-lg bg-primary/10 border border-primary/20 space-y-2">
              <div className="flex items-center gap-2">
                <Button
                  size="sm"
                  variant="ghost"
                  className="shrink-0"
                  onClick={() => handlePlayEpisode(playingEpisode)}
                >
                  {isPlaying ? (
                    <Pause className="w-4 h-4" />
                  ) : (
                    <Play className="w-4 h-4" />
                  )}
                </Button>
                <span className="text-sm font-medium truncate flex-1">
                  {playingEpisode.title}
                </span>
              </div>
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <span className="w-10">{formatTime(currentTime)}</span>
                <Slider
                  value={[currentTime]}
                  max={duration || 100}
                  step={1}
                  onValueChange={handleSeek}
                  className="flex-1"
                />
                <span className="w-10 text-right">{formatTime(duration)}</span>
              </div>
            </div>
          )}

          {/* Episodes List */}
          <div className="border rounded-lg bg-background/50 min-h-[300px] max-h-[400px] overflow-hidden">
            <ScrollArea className="h-[350px]">
              {isLoading ? (
                <div className="flex items-center justify-center h-[350px]">
                  <Loader2 className="w-8 h-8 animate-spin text-primary" />
                </div>
              ) : episodes.length === 0 ? (
                <div className="flex flex-col items-center justify-center h-[350px] text-muted-foreground p-4">
                  <Podcast className="w-12 h-12 mb-2 opacity-50" />
                  <p className="font-medium">Geen podcasts gevonden</p>
                  <p className="text-sm text-center">
                    Probeer het later nog eens
                  </p>
                </div>
              ) : (
                <div className="divide-y divide-border">
                  {currentEpisodes.map((episode) => (
                    <button
                      key={episode.id}
                      onClick={() => handlePlayEpisode(episode)}
                      className={`w-full p-4 text-left hover:bg-muted/50 transition-colors ${
                        playingEpisode?.id === episode.id ? 'bg-primary/5' : ''
                      }`}
                    >
                      <div className="flex items-start gap-3">
                        <div className={`shrink-0 w-10 h-10 rounded-full flex items-center justify-center ${
                          playingEpisode?.id === episode.id && isPlaying 
                            ? 'bg-primary text-primary-foreground' 
                            : 'bg-muted'
                        }`}>
                          {playingEpisode?.id === episode.id && isPlaying ? (
                            <Pause className="w-4 h-4" />
                          ) : (
                            <Play className="w-4 h-4 ml-0.5" />
                          )}
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="font-medium line-clamp-2">{episode.title}</p>
                          <p className="text-xs text-muted-foreground mt-1">
                            {formatDate(episode.pubDate)} • {episode.author}
                          </p>
                          <p className="text-sm text-muted-foreground mt-2 line-clamp-2">
                            {episode.description}
                          </p>
                        </div>
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </ScrollArea>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
                disabled={currentPage === 1}
              >
                <ChevronLeft className="w-4 h-4" />
              </Button>
              
              <div className="flex items-center gap-1">
                {Array.from({ length: totalPages }, (_, i) => i + 1).map((page) => (
                  <Button
                    key={page}
                    variant={currentPage === page ? "default" : "ghost"}
                    size="sm"
                    className={`min-w-[32px] ${currentPage === page ? 'radio-gradient' : ''}`}
                    onClick={() => setCurrentPage(page)}
                  >
                    {page}
                  </Button>
                ))}
              </div>
              
              <Button
                variant="outline"
                size="sm"
                onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
                disabled={currentPage === totalPages}
              >
                <ChevronRight className="w-4 h-4" />
              </Button>
            </div>
          )}
          
          <p className="text-xs text-muted-foreground text-center">
            {episodes.length} afleveringen beschikbaar
          </p>
        </CardContent>
      </Card>
    </div>
  );
};

export default PodcastBrowser;
