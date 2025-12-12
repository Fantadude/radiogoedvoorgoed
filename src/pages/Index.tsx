import { useState } from "react";
import Header from "@/components/Header";
import Navigation from "@/components/Navigation";
import RadioPlayer from "@/components/RadioPlayer";
import SongBrowser from "@/components/SongBrowser";
import PodcastBrowser from "@/components/PodcastBrowser";

type Tab = "radio" | "requests" | "podcasts";

const Index = () => {
  const [activeTab, setActiveTab] = useState<Tab>("radio");

  return (
    <div className="min-h-screen bg-background flex flex-col">
      <Header />
      
      <main className="flex-1 overflow-y-auto pb-24">
        <div className="max-w-md mx-auto">
          {activeTab === "radio" && <RadioPlayer />}
          {activeTab === "requests" && <SongBrowser />}
          {activeTab === "podcasts" && <PodcastBrowser />}
        </div>
      </main>

      <Navigation activeTab={activeTab} onTabChange={setActiveTab} />
    </div>
  );
};

export default Index;
