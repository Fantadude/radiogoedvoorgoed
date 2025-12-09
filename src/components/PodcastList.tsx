import { useState, useEffect } from "react";
import { Play, Pause, Clock, Calendar, Headphones, ExternalLink } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

interface PodcastEpisode {
  id: string;
  title: string;
  description: string;
  duration: string;
  date: string;
  audioUrl: string;
  imageUrl?: string;
}

// Mock data - in production this would come from the podcast RSS feed
const MOCK_EPISODES: PodcastEpisode[] = [
  {
    id: "1",
    title: "Kringloopverhalen - Aflevering 12",
    description: "In deze aflevering bespreken we de mooiste vondsten van deze maand en vertellen bezoekers hun verhalen.",
    duration: "45:30",
    date: "2024-01-15",
    audioUrl: "https://radiogoedvoorgoed.nl/podcast/episode12.mp3",
  },
  {
    id: "2",
    title: "Kringloopverhalen - Aflevering 11",
    description: "Speciale kersteditie met prachtige verhalen over tweedehands cadeaus die het hart raken.",
    duration: "52:15",
    date: "2024-01-08",
    audioUrl: "https://radiogoedvoorgoed.nl/podcast/episode11.mp3",
  },
  {
    id: "3",
    title: "Kringloopverhalen - Aflevering 10",
    description: "Deze week: een bijzonder interview met de vrijwilligers die het kringloopwerk mogelijk maken.",
    duration: "38:45",
    date: "2024-01-01",
    audioUrl: "https://radiogoedvoorgoed.nl/podcast/episode10.mp3",
  },
  {
    id: "4",
    title: "Kringloopverhalen - Aflevering 9",
    description: "Verhalen over meubels met een geschiedenis en de mensen die er nieuwe herinneringen mee maken.",
    duration: "41:20",
    date: "2023-12-25",
    audioUrl: "https://radiogoedvoorgoed.nl/podcast/episode9.mp3",
  },
];

const PodcastList = () => {
  const [episodes, setEpisodes] = useState<PodcastEpisode[]>([]);
  const [loading, setLoading] = useState(true);
  const [playingId, setPlayingId] = useState<string | null>(null);
  const [audioElement, setAudioElement] = useState<HTMLAudioElement | null>(null);

  useEffect(() => {
    // Simulate fetching podcast feed
    const fetchEpisodes = async () => {
      try {
        // In production, this would fetch from the RSS feed
        // const response = await fetch('https://radiogoedvoorgoed.nl/podcast/feed.xml');
        // const data = await parseRSS(response);
        await new Promise(resolve => setTimeout(resolve, 1000));
        setEpisodes(MOCK_EPISODES);
      } catch (error) {
        console.error("Failed to fetch episodes:", error);
      } finally {
        setLoading(false);
      }
    };

    fetchEpisodes();
  }, []);

  const togglePlay = (episode: PodcastEpisode) => {
    if (playingId === episode.id) {
      audioElement?.pause();
      setPlayingId(null);
    } else {
      if (audioElement) {
        audioElement.pause();
      }
      const audio = new Audio(episode.audioUrl);
      audio.play().catch(console.error);
      audio.onended = () => setPlayingId(null);
      setAudioElement(audio);
      setPlayingId(episode.id);
    }
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('nl-NL', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    });
  };

  if (loading) {
    return (
      <div className="p-4 space-y-4">
        <div className="text-center space-y-2 mb-6">
          <Skeleton className="h-8 w-48 mx-auto" />
          <Skeleton className="h-4 w-64 mx-auto" />
        </div>
        {[1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-32 w-full rounded-xl" />
        ))}
      </div>
    );
  }

  return (
    <div className="p-4 pb-8 space-y-4">
      <div className="text-center space-y-2 mb-6">
        <h1 className="text-2xl font-bold flex items-center justify-center gap-2">
          <Headphones className="w-6 h-6 text-primary" />
          Podcasts
        </h1>
        <p className="text-muted-foreground">Kringloopverhalen Podcast</p>
      </div>

      <Button
        variant="outline"
        className="w-full mb-4"
        onClick={() => window.open("https://radiogoedvoorgoed.nl/podcast/", "_blank")}
      >
        <ExternalLink className="w-4 h-4 mr-2" />
        Bekijk alle afleveringen
      </Button>

      <div className="space-y-3">
        {episodes.map((episode, index) => (
          <Card 
            key={episode.id} 
            className="glass-card overflow-hidden animate-slide-up"
            style={{ animationDelay: `${index * 100}ms` }}
          >
            <CardContent className="p-4">
              <div className="flex gap-4">
                <Button
                  size="icon"
                  className="shrink-0 w-12 h-12 rounded-xl radio-gradient"
                  onClick={() => togglePlay(episode)}
                >
                  {playingId === episode.id ? (
                    <Pause className="w-5 h-5" fill="currentColor" />
                  ) : (
                    <Play className="w-5 h-5 ml-0.5" fill="currentColor" />
                  )}
                </Button>
                
                <div className="flex-1 min-w-0">
                  <h3 className="font-semibold truncate">{episode.title}</h3>
                  <p className="text-sm text-muted-foreground line-clamp-2 mt-1">
                    {episode.description}
                  </p>
                  <div className="flex items-center gap-4 mt-2 text-xs text-muted-foreground">
                    <span className="flex items-center gap-1">
                      <Clock className="w-3 h-3" />
                      {episode.duration}
                    </span>
                    <span className="flex items-center gap-1">
                      <Calendar className="w-3 h-3" />
                      {formatDate(episode.date)}
                    </span>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
};

export default PodcastList;
