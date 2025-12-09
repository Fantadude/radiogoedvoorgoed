import { useState } from "react";
import { Send, Music, User, MessageSquare, AlertCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { useToast } from "@/hooks/use-toast";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Alert, AlertDescription } from "@/components/ui/alert";

interface RequestFormData {
  name: string;
  song: string;
  artist: string;
  message: string;
}

const RequestForm = () => {
  const { toast } = useToast();
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formData, setFormData] = useState<RequestFormData>({
    name: "",
    song: "",
    artist: "",
    message: "",
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!formData.name || !formData.song || !formData.artist) {
      toast({
        title: "Vul alle verplichte velden in",
        description: "Naam, nummer en artiest zijn verplicht",
        variant: "destructive",
      });
      return;
    }

    setIsSubmitting(true);
    
    try {
      // TODO: Connect to Supabase Edge Function that proxies to MySQL
      // For now, show success message
      await new Promise(resolve => setTimeout(resolve, 1000));
      
      toast({
        title: "Verzoek verzonden! 🎵",
        description: `Bedankt ${formData.name}! Je verzoek voor "${formData.song}" is ontvangen.`,
      });
      
      setFormData({ name: "", song: "", artist: "", message: "" });
    } catch (error) {
      toast({
        title: "Er ging iets mis",
        description: "Probeer het later nog eens",
        variant: "destructive",
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleChange = (field: keyof RequestFormData, value: string) => {
    setFormData(prev => ({ ...prev, [field]: value }));
  };

  return (
    <div className="p-4 pb-8 space-y-4">
      <div className="text-center space-y-2 mb-6">
        <h1 className="text-2xl font-bold">Verzoekhoek</h1>
        <p className="text-muted-foreground">Vraag je favoriete nummer aan!</p>
      </div>

      <Alert className="bg-muted/50 border-muted">
        <AlertCircle className="h-4 w-4" />
        <AlertDescription>
          Backend connectie vereist. Schakel Lovable Cloud in om verzoeken te versturen naar de database.
        </AlertDescription>
      </Alert>

      <Card className="glass-card">
        <CardHeader className="pb-4">
          <CardTitle className="text-lg flex items-center gap-2">
            <Music className="w-5 h-5 text-primary" />
            Nieuw Verzoek
          </CardTitle>
          <CardDescription>
            Vul het formulier in en we spelen je nummer zo snel mogelijk!
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="name" className="flex items-center gap-2">
                <User className="w-4 h-4" />
                Jouw naam *
              </Label>
              <Input
                id="name"
                placeholder="Bijv. Jan"
                value={formData.name}
                onChange={(e) => handleChange("name", e.target.value)}
                className="bg-background"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="song" className="flex items-center gap-2">
                <Music className="w-4 h-4" />
                Nummer *
              </Label>
              <Input
                id="song"
                placeholder="Bijv. Bohemian Rhapsody"
                value={formData.song}
                onChange={(e) => handleChange("song", e.target.value)}
                className="bg-background"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="artist">Artiest *</Label>
              <Input
                id="artist"
                placeholder="Bijv. Queen"
                value={formData.artist}
                onChange={(e) => handleChange("artist", e.target.value)}
                className="bg-background"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="message" className="flex items-center gap-2">
                <MessageSquare className="w-4 h-4" />
                Bericht (optioneel)
              </Label>
              <Textarea
                id="message"
                placeholder="Groetjes aan..."
                value={formData.message}
                onChange={(e) => handleChange("message", e.target.value)}
                className="bg-background resize-none"
                rows={3}
              />
            </div>

            <Button
              type="submit"
              className="w-full radio-gradient"
              disabled={isSubmitting}
            >
              {isSubmitting ? (
                <div className="flex items-center gap-2">
                  <div className="w-4 h-4 border-2 border-primary-foreground border-t-transparent rounded-full animate-spin" />
                  Verzenden...
                </div>
              ) : (
                <div className="flex items-center gap-2">
                  <Send className="w-4 h-4" />
                  Verstuur Verzoek
                </div>
              )}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
};

export default RequestForm;
