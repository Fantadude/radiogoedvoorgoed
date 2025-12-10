import { useState, useEffect } from "react";
import { Search, Music, Send, Loader2, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { useToast } from "@/hooks/use-toast";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogFooter } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { supabase } from "@/integrations/supabase/client";

interface Song {
  ID: number;
  artist: string;
  title: string;
  album: string;
  duration: number;
}

const SongBrowser = () => {
  const { toast } = useToast();
  const [songs, setSongs] = useState<Song[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedSong, setSelectedSong] = useState<Song | null>(null);
  const [requestName, setRequestName] = useState("");
  const [requestMessage, setRequestMessage] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const fetchSongs = async (search = "") => {
    setIsLoading(true);
    try {
      const response = await fetch(
        `${import.meta.env.VITE_SUPABASE_URL}/functions/v1/radiodj?action=songs&limit=200&search=${encodeURIComponent(search)}`
      );
      
      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.error || 'Failed to fetch songs');
      }
      
      const result = await response.json();
      
      if (result.error) {
        throw new Error(result.error);
      }
      
      setSongs(result.songs || []);
    } catch (error: any) {
      console.error('Error fetching songs:', error);
      toast({
        title: "Kon nummers niet laden",
        description: error.message || "Probeer het later nog eens",
        variant: "destructive",
      });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchSongs();
  }, []);

  const handleSearch = () => {
    fetchSongs(searchQuery);
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  const formatDuration = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  const handleRequestSong = async () => {
    if (!selectedSong) return;
    
    setIsSubmitting(true);
    try {
      const response = await fetch(
        `${import.meta.env.VITE_SUPABASE_URL}/functions/v1/radiodj?action=request`,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            songId: selectedSong.ID,
            name: requestName || 'App User',
            message: requestMessage,
          }),
        }
      );
      
      const result = await response.json();
      
      if (result.error) {
        throw new Error(result.error);
      }
      
      toast({
        title: "Verzoek verzonden! 🎵",
        description: `"${selectedSong.title}" van ${selectedSong.artist} is aangevraagd.`,
      });
      
      setSelectedSong(null);
      setRequestName("");
      setRequestMessage("");
    } catch (error: any) {
      console.error('Error submitting request:', error);
      toast({
        title: "Er ging iets mis",
        description: error.message || "Probeer het later nog eens",
        variant: "destructive",
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="p-4 pb-8 space-y-4">
      <div className="text-center space-y-2 mb-6">
        <h1 className="text-2xl font-bold">Verzoekhoek</h1>
        <p className="text-muted-foreground">Kies een nummer uit onze bibliotheek</p>
      </div>

      <Card className="glass-card">
        <CardHeader className="pb-3">
          <CardTitle className="text-lg flex items-center gap-2">
            <Music className="w-5 h-5 text-primary" />
            Nummers zoeken
          </CardTitle>
          <CardDescription>
            Zoek op artiest of titel
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex gap-2">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
              <Input
                placeholder="Zoek artiest of nummer..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onKeyPress={handleKeyPress}
                className="pl-10 bg-background"
              />
            </div>
            <Button onClick={handleSearch} disabled={isLoading}>
              <Search className="w-4 h-4" />
            </Button>
            <Button variant="outline" onClick={() => fetchSongs(searchQuery)} disabled={isLoading}>
              <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
            </Button>
          </div>

          <ScrollArea className="h-[400px] rounded-md border border-border">
            {isLoading ? (
              <div className="flex items-center justify-center h-full">
                <Loader2 className="w-8 h-8 animate-spin text-primary" />
              </div>
            ) : songs.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-full text-muted-foreground p-4">
                <Music className="w-12 h-12 mb-2 opacity-50" />
                <p>Geen nummers gevonden</p>
                <p className="text-sm">Probeer een andere zoekopdracht</p>
              </div>
            ) : (
              <div className="divide-y divide-border">
                {songs.map((song) => (
                  <button
                    key={song.ID}
                    onClick={() => setSelectedSong(song)}
                    className="w-full p-3 text-left hover:bg-muted/50 transition-colors"
                  >
                    <div className="flex justify-between items-start gap-2">
                      <div className="flex-1 min-w-0">
                        <p className="font-medium truncate">{song.title}</p>
                        <p className="text-sm text-muted-foreground truncate">{song.artist}</p>
                      </div>
                      <span className="text-xs text-muted-foreground shrink-0">
                        {formatDuration(song.duration)}
                      </span>
                    </div>
                  </button>
                ))}
              </div>
            )}
          </ScrollArea>
          
          <p className="text-xs text-muted-foreground text-center">
            {songs.length} nummers gevonden
          </p>
        </CardContent>
      </Card>

      <Dialog open={!!selectedSong} onOpenChange={() => setSelectedSong(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Nummer aanvragen</DialogTitle>
            <DialogDescription>
              {selectedSong && (
                <span className="block mt-2 font-medium text-foreground">
                  "{selectedSong.title}" - {selectedSong.artist}
                </span>
              )}
            </DialogDescription>
          </DialogHeader>
          
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="name">Jouw naam</Label>
              <Input
                id="name"
                placeholder="Bijv. Jan"
                value={requestName}
                onChange={(e) => setRequestName(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="message">Bericht (optioneel)</Label>
              <Textarea
                id="message"
                placeholder="Groetjes aan..."
                value={requestMessage}
                onChange={(e) => setRequestMessage(e.target.value)}
                rows={3}
              />
            </div>
          </div>
          
          <DialogFooter>
            <Button variant="outline" onClick={() => setSelectedSong(null)}>
              Annuleren
            </Button>
            <Button onClick={handleRequestSong} disabled={isSubmitting} className="radio-gradient">
              {isSubmitting ? (
                <Loader2 className="w-4 h-4 animate-spin mr-2" />
              ) : (
                <Send className="w-4 h-4 mr-2" />
              )}
              Versturen
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default SongBrowser;
